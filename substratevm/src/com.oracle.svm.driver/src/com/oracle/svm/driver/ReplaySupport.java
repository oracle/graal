package com.oracle.svm.driver;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import org.graalvm.util.json.JSONParserException;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.util.json.JsonPrinter;
import com.oracle.svm.core.util.json.JsonWriter;
import com.oracle.svm.util.StringUtil;

class ReplaySupport {

    enum ReplayStatus {
        prepare(false, false),
        create(false, false),
        apply(true, true);

        final boolean hidden;
        final boolean loadBundle;

        ReplayStatus(boolean hidden, boolean loadBundle) {
            this.hidden = hidden;
            this.loadBundle = loadBundle;
        }

        boolean show() {
            return !hidden;
        }
    }

    final NativeImage nativeImage;

    final ReplayStatus status;

    final Path replayRootDir;
    final Path stageDir;
    final Path classPathDir;
    final Path modulePathDir;
    final Path auxiliaryDir;

    Map<Path, Path> pathSubstitutions = new HashMap<>();

    private static final String replayTempDirPrefix = "replayRoot-";

    ReplaySupport(NativeImage nativeImage, ReplayStatus status) {
        assert !status.loadBundle;

        this.nativeImage = nativeImage;
        this.status = status;
        try {
            replayRootDir = Files.createTempDirectory(replayTempDirPrefix);
            Path inputDir = replayRootDir.resolve("input");
            stageDir = Files.createDirectories(inputDir.resolve("stage"));
            auxiliaryDir = Files.createDirectories(inputDir.resolve("auxiliary"));
            Path classesDir = inputDir.resolve("classes");
            classPathDir = Files.createDirectories(classesDir.resolve("cp"));
            modulePathDir = Files.createDirectories(classesDir.resolve("p"));
        } catch (IOException e) {
            throw NativeImage.showError("Unable to create replay-bundle directory layout", e);
        }
    }

    ReplaySupport(NativeImage nativeImage, ReplayStatus status, String replayBundleFilename) {
        assert status.loadBundle;

        this.nativeImage = nativeImage;
        this.status = status;

        Path replayBundlePath = Path.of(replayBundleFilename);
        if (!Files.isReadable(replayBundlePath)) {
            throw NativeImage.showError("The given replay-bundle file " + replayBundleFilename + " cannot be read");
        }

        if (Files.isDirectory(replayBundlePath)) {
            replayRootDir = replayBundlePath;
        } else {
            try {
                replayRootDir = Files.createTempDirectory(replayTempDirPrefix);
                try (JarFile archive = new JarFile(replayBundlePath.toFile())) {
                    archive.stream().forEach(jarEntry -> {
                        Path replayBundleFile = replayRootDir.resolve(jarEntry.getName());
                        try {
                            Path replayBundleFileParent = replayBundleFile.getParent();
                            if (replayBundleFileParent != null) {
                                Files.createDirectories(replayBundleFileParent);
                            }
                            Files.copy(archive.getInputStream(jarEntry), replayBundleFile);
                        } catch (IOException e) {
                            throw NativeImage.showError("Unable to copy " + jarEntry.getName() + " from replay-bundle " + replayBundlePath + " to " + replayBundleFile, e);
                        }
                    });
                }
            } catch (IOException e) {
                throw NativeImage.showError("Unable to create replay-bundle directory layout from replay-file " + replayBundlePath, e);
            }
        }

        Path inputDir = replayRootDir.resolve("input");
        stageDir = inputDir.resolve("stage");
        auxiliaryDir = inputDir.resolve("auxiliary");
        Path classesDir = inputDir.resolve("classes");
        classPathDir = classesDir.resolve("cp");
        modulePathDir = classesDir.resolve("p");

        Path pathSubstitutionsFile = stageDir.resolve("path_substitutions.json");
        try (Reader reader = Files.newBufferedReader(pathSubstitutionsFile)) {
            new PathSubstitutionsMapParser(true).parseAndRegister(reader);
        } catch (IOException e) {
            throw NativeImage.showError("Failed to read bundle-file " + pathSubstitutionsFile, e);
        }
    }

    Path substituteAuxiliaryPath(Path origPath) {
        return substitutePath(origPath, auxiliaryDir);
    }

    Path substituteClassPath(Path origPath) {
        return substitutePath(origPath, classPathDir);
    }

    Path substituteModulePath(Path origPath) {
        return substitutePath(origPath, modulePathDir);
    }

    @SuppressWarnings("try")
    private Path substitutePath(Path origPath, Path destinationDir) {
        assert destinationDir.startsWith(replayRootDir);

        Path alreadySubstitutedPath = pathSubstitutions.get(origPath);
        if (alreadySubstitutedPath != null) {
            return replayRootDir.resolve(alreadySubstitutedPath);
        }

        if (origPath.startsWith(nativeImage.config.getJavaHome())) {
            /* If origPath comes from native-image itself, substituting is not needed. */
            return origPath;
        }

        if (!Files.isReadable(origPath)) {
            /* Prevent subsequent retries to substitute invalid paths */
            pathSubstitutions.put(origPath, origPath);
            return origPath;
        }

        String[] baseNamePlusExtension = StringUtil.split(origPath.getFileName().toString(), ".", 2);
        String baseName = baseNamePlusExtension[0];
        String extension = baseNamePlusExtension.length == 2 ? "." + baseNamePlusExtension[1] : "";
        String substitutedPathFilename = baseName + "_" + SubstrateUtil.digest(origPath.toString()) + extension;
        Path substitutedPath = destinationDir.resolve(substitutedPathFilename);
        if (Files.exists(substitutedPath)) {
            /* If we ever see this, we have to implement substitutedPath collision-handling */
            throw NativeImage.showError("Failed to create a unique path-name in " + destinationDir + ". " + substitutedPath + " already exists");
        }

        if (Files.isDirectory(origPath)) {
            try (Stream<Path> walk = Files.walk(origPath)) {
                walk.forEach(sourcePath -> copyFile(sourcePath, substitutedPath.resolve(origPath.relativize(sourcePath))));
            } catch (IOException e) {
                throw NativeImage.showError("Failed to iterate through directory " + origPath, e);
            }
        } else {
            copyFile(origPath, substitutedPath);
        }
        pathSubstitutions.put(origPath, replayRootDir.relativize(substitutedPath));
        return substitutedPath;
    }

    private void copyFile(Path source, Path target) {
        try {
            System.out.println("> Copy " + nativeImage.config.workDir.relativize(source) + " to " + target);
            Files.copy(source, target);
        } catch (IOException e) {
            throw NativeImage.showError("Failed to copy " + source + " to " + target, e);
        }
    }

    void shutdown() {
        if (!status.loadBundle) {
            writeBundle();
        }

        nativeImage.deleteAllFiles(replayRootDir);
    }

    void writeBundle() {
        Path pathSubstitutionsFile = stageDir.resolve("path_substitutions.json");
        try (JsonWriter writer = new JsonWriter(pathSubstitutionsFile)) {
            /* Printing as list with defined sort-order ensures useful diffs are possible */
            JsonPrinter.printCollection(writer, pathSubstitutions.entrySet(), Map.Entry.comparingByKey(), ReplaySupport::printPathSubstitution);
        } catch (IOException e) {
            throw NativeImage.showError("Failed to write bundle-file " + pathSubstitutionsFile, e);
        }

        byte[] bundleEntryDataBuffer = new byte[16 * 1024];
        Path bundleFile = Path.of(nativeImage.imagePath).resolve(nativeImage.imageName + ".replay.jar");
        try (JarOutputStream jarOutStream = new JarOutputStream(Files.newOutputStream(bundleFile), new Manifest())) {
            try (Stream<Path> walk = Files.walk(replayRootDir)) {
                walk.forEach(bundleEntry -> {
                    if (Files.isDirectory(bundleEntry)) {
                        return;
                    }
                    String jarEntryName = replayRootDir.relativize(bundleEntry).toString();
                    JarEntry entry = new JarEntry(jarEntryName.replace(File.separator, "/"));
                    try {
                        entry.setTime(Files.getLastModifiedTime(bundleEntry).toMillis());
                        jarOutStream.putNextEntry(entry);
                        Files.copy(bundleEntry, jarOutStream);
                        jarOutStream.closeEntry();
                    } catch (IOException e) {
                        throw NativeImage.showError("Failed to copy " + bundleEntry + " into replay-bundle file " + bundleFile, e);
                    }
                });
            }
        } catch (IOException e) {
            throw NativeImage.showError("Failed to create replay-bundle file " + bundleFile, e);
        }
    }

    private static final String substitutionMapSrcField = "src";
    private static final String substitutionMapDstField = "dst";

    private static void printPathSubstitution(Map.Entry<Path, Path> entry, JsonWriter w) throws IOException {
        w.append('{').quote(substitutionMapSrcField).append(" : ").quote(entry.getKey());
        w.append(", ").quote(substitutionMapDstField).append(" : ").quote(entry.getValue());
        w.append('}');
    }

    private class PathSubstitutionsMapParser extends ConfigurationParser {

        private PathSubstitutionsMapParser(boolean strictConfiguration) {
            super(strictConfiguration);
        }

        @Override
        public void parseAndRegister(Object json, URI origin) throws IOException {
            for (var rawEntry : asList(json, "Expected a list of path substitution objects")) {
                var entry = asMap(rawEntry, "Expected a substitution object");
                Object srcPathString = entry.get(substitutionMapSrcField);
                if (srcPathString == null) {
                    throw new JSONParserException("Expected " + substitutionMapSrcField + "-field in substitution object");
                }
                Object dstPathString = entry.get(substitutionMapDstField);
                if (dstPathString == null) {
                    throw new JSONParserException("Expected " + substitutionMapDstField + "-field in substitution object");
                }
                pathSubstitutions.put(Path.of(srcPathString.toString()), Path.of(dstPathString.toString()));
            }
        }
    }
}
