/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.driver;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.util.json.JSONParserException;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.util.json.JsonPrinter;
import com.oracle.svm.core.util.json.JsonWriter;
import com.oracle.svm.util.ClassUtil;

final class ReplaySupport {

    static final String REPLAY_OPTION = "--replay";

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

    Map<Path, Path> pathCanonicalizations = new HashMap<>();
    Map<Path, Path> pathSubstitutions = new HashMap<>();

    private final List<String> buildArgs;

    private static final String replayTempDirPrefix = "replayRoot-";

    static ReplaySupport create(NativeImage nativeImage, String replayArg, NativeImage.ArgumentQueue args) {
        if (!nativeImage.userConfigProperties.isEmpty()) {
            throw NativeImage.showError("Replay-bundle support cannot be combined with " + NativeImage.CONFIG_FILE_ENV_VAR_KEY + " environment variable use.");
        }

        ReplaySupport.ReplayStatus replayStatus;
        if (replayArg.equals(REPLAY_OPTION)) {
            /* Handle short form of --replay-apply */
            replayStatus = ReplaySupport.ReplayStatus.apply;
        } else {
            String replayVariant = replayArg.substring(REPLAY_OPTION.length() + 1);
            try {
                replayStatus = ReplaySupport.ReplayStatus.valueOf(replayVariant);
            } catch (IllegalArgumentException e) {
                String suggestedVariants = Arrays.stream(ReplaySupport.ReplayStatus.values())
                                .filter(ReplaySupport.ReplayStatus::show)
                                .map(v -> REPLAY_OPTION + "-" + v)
                                .collect(Collectors.joining(", "));
                throw NativeImage.showError("Unknown option " + replayArg + ". Valid variants are: " + suggestedVariants + ".");
            }
        }
        ReplaySupport replaySupport;
        if (replayStatus.loadBundle) {
            String replayBundleFilename = args.poll();
            replaySupport = new ReplaySupport(nativeImage, replayStatus, replayBundleFilename);
            List<String> buildArgs = replaySupport.getBuildArgs();
            for (int i = buildArgs.size() - 1; i >= 0; i--) {
                String buildArg = buildArgs.get(i);
                if (buildArg.startsWith(REPLAY_OPTION)) {
                    assert !ReplayStatus.valueOf(buildArg.substring(REPLAY_OPTION.length() + 1)).loadBundle;
                    continue;
                }
                if (buildArg.startsWith("-Dllvm.bin.dir=")) {
                    Optional<String> existing = nativeImage.config.getBuildArgs().stream().filter(arg -> arg.startsWith("-Dllvm.bin.dir=")).findFirst();
                    if (existing.isPresent() && !existing.get().equals(buildArg)) {
                        throw NativeImage.showError("Replay-bundle native-image argument '" + buildArg + "' conflicts with existing '" + existing.get() + "'.");
                    }
                    continue;
                }
                args.push(buildArg);
            }
        } else {
            replaySupport = new ReplaySupport(nativeImage, replayStatus);
        }
        return replaySupport;
    }

    private ReplaySupport(NativeImage nativeImage, ReplayStatus status) {
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
        this.buildArgs = Collections.unmodifiableList(nativeImage.config.getBuildArgs());
    }

    private ReplaySupport(NativeImage nativeImage, ReplayStatus status, String replayBundleFilename) {
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

        Path pathCanonicalizationsFile = stageDir.resolve("path_canonicalizations.json");
        try (Reader reader = Files.newBufferedReader(pathCanonicalizationsFile)) {
            new PathMapParser(pathCanonicalizations).parseAndRegister(reader);
        } catch (IOException e) {
            throw NativeImage.showError("Failed to read bundle-file " + pathCanonicalizationsFile, e);
        }
        Path pathSubstitutionsFile = stageDir.resolve("path_substitutions.json");
        try (Reader reader = Files.newBufferedReader(pathSubstitutionsFile)) {
            new PathMapParser(pathSubstitutions).parseAndRegister(reader);
        } catch (IOException e) {
            throw NativeImage.showError("Failed to read bundle-file " + pathSubstitutionsFile, e);
        }
        Path buildArgsFile = stageDir.resolve("build.json");
        try (Reader reader = Files.newBufferedReader(buildArgsFile)) {
            List<String> buildArgsFromFile = new ArrayList<>();
            new BuildArgsParser(buildArgsFromFile).parseAndRegister(reader);
            buildArgs = Collections.unmodifiableList(buildArgsFromFile);
        } catch (IOException e) {
            throw NativeImage.showError("Failed to read bundle-file " + pathSubstitutionsFile, e);
        }
    }

    public List<String> getBuildArgs() {
        return buildArgs;
    }

    Path recordCanonicalization(Path before, Path after) {
        if (before.startsWith(replayRootDir)) {
            if (nativeImage.isVerbose()) {
                System.out.println(("RecordCanonicalization Skip: " + before));
            }
            return before;
        }
        if (after.startsWith(nativeImage.config.getJavaHome())) {
            return after;
        }
        if (nativeImage.isVerbose()) {
            System.out.println("RecordCanonicalization src: " + before + ", dst: " + after);
        }
        pathCanonicalizations.put(before, after);
        return after;
    }

    Path restoreCanonicalization(Path before) {
        Path after = pathCanonicalizations.get(before);
        if (after != null && nativeImage.isVerbose()) {
            System.out.println("RestoreCanonicalization src: " + before + ", dst: " + after);
        }
        return after;
    }

    Path substituteAuxiliaryPath(Path origPath) {
        return substitutePath(origPath, auxiliaryDir);
    }

    Path substituteClassPath(Path origPath) {
        try {
            return substitutePath(origPath, classPathDir);
        } catch (ReplayPathSubstitutionError error) {
            throw NativeImage.showError("Failed to prepare class-path entry '" + error.origPath + "' for replay bundle inclusion.", error);
        }
    }

    Path substituteModulePath(Path origPath) {
        try {
            return substitutePath(origPath, modulePathDir);
        } catch (ReplayPathSubstitutionError error) {
            throw NativeImage.showError("Failed to prepare module-path entry '" + error.origPath + "' for replay bundle inclusion.", error);
        }
    }

    @SuppressWarnings("serial")
    static final class ReplayPathSubstitutionError extends Error {
        public final Path origPath;

        ReplayPathSubstitutionError(String message, Path origPath) {
            super(message);
            this.origPath = origPath;
        }

        ReplayPathSubstitutionError(String message, Path origPath, Throwable cause) {
            super(message, cause);
            this.origPath = origPath;
        }
    }

    @SuppressWarnings("try")
    private Path substitutePath(Path origPath, Path destinationDir) {
        assert destinationDir.startsWith(replayRootDir);

        if (origPath.startsWith(replayRootDir)) {
            if (nativeImage.isVerbose()) {
                System.out.println(("RecordSubstitution/RestoreSubstitution Skip: " + origPath));
            }
            return origPath;
        }

        Path previousRelativeSubstitutedPath = pathSubstitutions.get(origPath);
        if (previousRelativeSubstitutedPath != null) {
            if (nativeImage.isVerbose()) {
                System.out.println("RestoreSubstitution src: " + origPath + ", dst: " + previousRelativeSubstitutedPath);
            }
            return replayRootDir.resolve(previousRelativeSubstitutedPath);
        }

        if (origPath.startsWith(nativeImage.config.getJavaHome())) {
            /* If origPath comes from native-image itself, substituting is not needed. */
            return origPath;
        }

        boolean forbiddenPath = false;
        if (!OS.WINDOWS.isCurrent()) {
            for (Path path : ClassUtil.CLASS_MODULE_PATH_EXCLUDE_DIRECTORIES) {
                if (origPath.startsWith(path)) {
                    forbiddenPath = true;
                    break;
                }
            }
        }
        for (Path rootDirectory : FileSystems.getDefault().getRootDirectories()) {
            /* Refuse /, C:, D:, ... */
            if (origPath.equals(rootDirectory)) {
                forbiddenPath = true;
            }
        }
        if (forbiddenPath) {
            throw new ReplayPathSubstitutionError("Replay bundles do not allow inclusion of directory " + origPath, origPath);
        }

        if (!Files.isReadable(origPath)) {
            /* Prevent subsequent retries to substitute invalid paths */
            pathSubstitutions.put(origPath, origPath);
            return origPath;
        }

        // TODO Report error if overlapping dir-trees are passed in
        // TODO add .endsWith(ClasspathUtils.cpWildcardSubstitute) handling (copy whole directory)
        String origFileName = origPath.getFileName().toString();
        int extensionPos = origFileName.lastIndexOf('.');
        String baseName;
        String extension;
        if (extensionPos > 0) {
            baseName = origFileName.substring(0, extensionPos);
            extension = origFileName.substring(extensionPos);
        } else {
            baseName = origFileName;
            extension = "";
        }
        String substitutedPathFilename = baseName + "_" + SubstrateUtil.digest(origPath.toString()) + extension;
        Path substitutedPath = destinationDir.resolve(substitutedPathFilename);
        if (Files.exists(substitutedPath)) {
            /* If we ever see this, we have to implement substitutedPath collision-handling */
            throw new ReplayPathSubstitutionError("Failed to create a unique path-name in " + destinationDir + ". " + substitutedPath + " already exists", origPath);
        }

        if (Files.isDirectory(origPath)) {
            try (Stream<Path> walk = Files.walk(origPath)) {
                walk.forEach(sourcePath -> copyFile(sourcePath, substitutedPath.resolve(origPath.relativize(sourcePath))));
            } catch (IOException e) {
                throw new ReplayPathSubstitutionError("Failed to iterate through directory " + origPath, origPath, e);
            }
        } else {
            copyFile(origPath, substitutedPath);
        }
        Path relativeSubstitutedPath = replayRootDir.relativize(substitutedPath);
        if (nativeImage.isVerbose()) {
            System.out.println("RecordSubstitution src: " + origPath + ", dst: " + relativeSubstitutedPath);
        }
        pathSubstitutions.put(origPath, relativeSubstitutedPath);
        return substitutedPath;
    }

    private void copyFile(Path source, Path target) {
        try {
            if (nativeImage.isVerbose()) {
                System.out.println("> Copy to bundle: " + nativeImage.config.workDir.relativize(source));
            }
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
        Path pathCanonicalizationsFile = stageDir.resolve("path_canonicalizations.json");
        try (JsonWriter writer = new JsonWriter(pathCanonicalizationsFile)) {
            /* Printing as list with defined sort-order ensures useful diffs are possible */
            JsonPrinter.printCollection(writer, pathCanonicalizations.entrySet(), Map.Entry.comparingByKey(), ReplaySupport::printPathMapping);
        } catch (IOException e) {
            throw NativeImage.showError("Failed to write bundle-file " + pathCanonicalizationsFile, e);
        }
        Path pathSubstitutionsFile = stageDir.resolve("path_substitutions.json");
        try (JsonWriter writer = new JsonWriter(pathSubstitutionsFile)) {
            /* Printing as list with defined sort-order ensures useful diffs are possible */
            JsonPrinter.printCollection(writer, pathSubstitutions.entrySet(), Map.Entry.comparingByKey(), ReplaySupport::printPathMapping);
        } catch (IOException e) {
            throw NativeImage.showError("Failed to write bundle-file " + pathSubstitutionsFile, e);
        }

        Path buildArgsFile = stageDir.resolve("build.json");
        try (JsonWriter writer = new JsonWriter(buildArgsFile)) {
            /* Printing as list with defined sort-order ensures useful diffs are possible */
            JsonPrinter.printCollection(writer, buildArgs, null, ReplaySupport::printBuildArg);
        } catch (IOException e) {
            throw NativeImage.showError("Failed to write bundle-file " + pathSubstitutionsFile, e);
        }

        Path bundleFile = Path.of(nativeImage.imagePath).resolve(nativeImage.imageName + ".replay");
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

    private static void printPathMapping(Map.Entry<Path, Path> entry, JsonWriter w) throws IOException {
        w.append('{').quote(substitutionMapSrcField).append(" : ").quote(entry.getKey());
        w.append(", ").quote(substitutionMapDstField).append(" : ").quote(entry.getValue());
        w.append('}');
    }

    private static void printBuildArg(String entry, JsonWriter w) throws IOException {
        w.quote(entry);
    }

    private static final class PathMapParser extends ConfigurationParser {

        private final Map<Path, Path> pathMap;

        private PathMapParser(Map<Path, Path> pathMap) {
            super(true);
            this.pathMap = pathMap;
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
                pathMap.put(Path.of(srcPathString.toString()), Path.of(dstPathString.toString()));
            }
        }
    }

    private static final class BuildArgsParser extends ConfigurationParser {

        private final List<String> args;

        private BuildArgsParser(List<String> args) {
            super(true);
            this.args = args;
        }

        @Override
        public void parseAndRegister(Object json, URI origin) throws IOException {
            for (var arg : asList(json, "Expected a list of arguments")) {
                args.add(arg.toString());
            }
        }
    }
}
