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
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.option.BundleMember;
import com.oracle.svm.core.util.json.JsonPrinter;
import com.oracle.svm.core.util.json.JsonWriter;
import com.oracle.svm.util.ClassUtil;

final class BundleSupport {

    static final String BUNDLE_OPTION = "--bundle";

    enum BundleStatus {
        prepare(false, false),
        create(false, false),
        apply(true, true);

        final boolean hidden;
        final boolean loadBundle;

        BundleStatus(boolean hidden, boolean loadBundle) {
            this.hidden = hidden;
            this.loadBundle = loadBundle;
        }

        boolean show() {
            return !hidden;
        }
    }

    final NativeImage nativeImage;

    final BundleStatus status;

    final Path rootDir;
    final Path stageDir;
    final Path classPathDir;
    final Path modulePathDir;
    final Path auxiliaryDir;
    final Path outputDir;
    final Path imagePathOutputDir;
    final Path auxiliaryOutputDir;

    Map<Path, Path> pathCanonicalizations = new HashMap<>();
    Map<Path, Path> pathSubstitutions = new HashMap<>();

    private final List<String> buildArgs;

    private static final String bundleTempDirPrefix = "bundleRoot-";

    Path bundleFile;

    static BundleSupport create(NativeImage nativeImage, String bundleArg, NativeImage.ArgumentQueue args) {
        if (!nativeImage.userConfigProperties.isEmpty()) {
            throw NativeImage.showError("Bundle support cannot be combined with " + NativeImage.CONFIG_FILE_ENV_VAR_KEY + " environment variable use.");
        }

        BundleStatus bundleStatus;
        if (bundleArg.equals(BUNDLE_OPTION)) {
            /* Handle short form of --bundle-apply */
            bundleStatus = BundleStatus.apply;
        } else {
            String bundleVariant = bundleArg.substring(BUNDLE_OPTION.length() + 1);
            try {
                bundleStatus = BundleStatus.valueOf(bundleVariant);
            } catch (IllegalArgumentException e) {
                String suggestedVariants = Arrays.stream(BundleStatus.values())
                                .filter(BundleStatus::show)
                                .map(v -> BUNDLE_OPTION + "-" + v)
                                .collect(Collectors.joining(", "));
                throw NativeImage.showError("Unknown option " + bundleArg + ". Valid variants are: " + suggestedVariants + ".");
            }
        }
        BundleSupport bundleSupport;
        if (bundleStatus.loadBundle) {
            String bundleFilename = args.poll();
            bundleSupport = new BundleSupport(nativeImage, bundleStatus, bundleFilename);
            List<String> buildArgs = bundleSupport.getBuildArgs();
            for (int i = buildArgs.size() - 1; i >= 0; i--) {
                String buildArg = buildArgs.get(i);
                if (buildArg.startsWith(BUNDLE_OPTION)) {
                    assert !BundleStatus.valueOf(buildArg.substring(BUNDLE_OPTION.length() + 1)).loadBundle;
                    continue;
                }
                if (buildArg.startsWith("-Dllvm.bin.dir=")) {
                    Optional<String> existing = nativeImage.config.getBuildArgs().stream().filter(arg -> arg.startsWith("-Dllvm.bin.dir=")).findFirst();
                    if (existing.isPresent() && !existing.get().equals(buildArg)) {
                        throw NativeImage.showError("Bundle native-image argument '" + buildArg + "' conflicts with existing '" + existing.get() + "'.");
                    }
                    continue;
                }
                args.push(buildArg);
            }
        } else {
            bundleSupport = new BundleSupport(nativeImage, bundleStatus);
        }
        return bundleSupport;
    }

    private BundleSupport(NativeImage nativeImage, BundleStatus status) {
        assert !status.loadBundle;

        this.nativeImage = nativeImage;
        this.status = status;
        try {
            rootDir = Files.createTempDirectory(bundleTempDirPrefix);
            Path inputDir = rootDir.resolve("input");
            stageDir = Files.createDirectories(inputDir.resolve("stage"));
            auxiliaryDir = Files.createDirectories(inputDir.resolve("auxiliary"));
            Path classesDir = inputDir.resolve("classes");
            classPathDir = Files.createDirectories(classesDir.resolve("cp"));
            modulePathDir = Files.createDirectories(classesDir.resolve("p"));
            outputDir = Files.createDirectories(rootDir.resolve("output"));
            imagePathOutputDir = Files.createDirectories(outputDir.resolve("default"));
            auxiliaryOutputDir = Files.createDirectories(outputDir.resolve("other"));
        } catch (IOException e) {
            throw NativeImage.showError("Unable to create bundle directory layout", e);
        }
        this.buildArgs = Collections.unmodifiableList(nativeImage.config.getBuildArgs());
    }

    private BundleSupport(NativeImage nativeImage, BundleStatus status, String bundleFilename) {
        assert status.loadBundle;

        this.nativeImage = nativeImage;
        this.status = status;

        bundleFile = Path.of(bundleFilename);
        if (!Files.isReadable(bundleFile)) {
            throw NativeImage.showError("The given bundle file " + bundleFilename + " cannot be read");
        }

        if (Files.isDirectory(bundleFile)) {
            throw NativeImage.showError("The given bundle file " + bundleFilename + " is a directory and not a file");
        } else {
            try {
                rootDir = Files.createTempDirectory(bundleTempDirPrefix);
                try (JarFile archive = new JarFile(bundleFile.toFile())) {
                    archive.stream().forEach(jarEntry -> {
                        Path bundleEntry = rootDir.resolve(jarEntry.getName());
                        try {
                            Path bundleFileParent = bundleEntry.getParent();
                            if (bundleFileParent != null) {
                                Files.createDirectories(bundleFileParent);
                            }
                            Files.copy(archive.getInputStream(jarEntry), bundleEntry);
                        } catch (IOException e) {
                            throw NativeImage.showError("Unable to copy " + jarEntry.getName() + " from bundle " + bundleEntry + " to " + bundleEntry, e);
                        }
                    });
                }
            } catch (IOException e) {
                throw NativeImage.showError("Unable to create bundle directory layout from file " + bundleFile, e);
            }
        }

        Path inputDir = rootDir.resolve("input");
        stageDir = inputDir.resolve("stage");
        auxiliaryDir = inputDir.resolve("auxiliary");
        Path classesDir = inputDir.resolve("classes");
        classPathDir = classesDir.resolve("cp");
        modulePathDir = classesDir.resolve("p");
        outputDir = rootDir.resolve("output");
        imagePathOutputDir = outputDir.resolve("default");
        auxiliaryOutputDir = outputDir.resolve("other");

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

    public boolean isBundleCreation() {
        return !status.loadBundle;
    }

    public List<String> getBuildArgs() {
        return buildArgs;
    }

    Path recordCanonicalization(Path before, Path after) {
        if (before.startsWith(rootDir)) {
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

    Path substituteAuxiliaryPath(Path origPath, BundleMember.Role bundleMemberRole) {
        Path destinationDir;
        switch (bundleMemberRole) {
            case Input:
                destinationDir = auxiliaryDir;
                break;
            case Output:
                destinationDir = auxiliaryOutputDir;
                break;
            default:
                return origPath;
        }
        return substitutePath(origPath, destinationDir);
    }

    Path substituteImagePath(Path origPath) {
        pathSubstitutions.put(origPath, imagePathOutputDir);
        return imagePathOutputDir;
    }

    Path substituteClassPath(Path origPath) {
        try {
            return substitutePath(origPath, classPathDir);
        } catch (BundlePathSubstitutionError error) {
            throw NativeImage.showError("Failed to prepare class-path entry '" + error.origPath + "' for bundle inclusion.", error);
        }
    }

    Path substituteModulePath(Path origPath) {
        try {
            return substitutePath(origPath, modulePathDir);
        } catch (BundlePathSubstitutionError error) {
            throw NativeImage.showError("Failed to prepare module-path entry '" + error.origPath + "' for bundle inclusion.", error);
        }
    }

    @SuppressWarnings("serial")
    static final class BundlePathSubstitutionError extends Error {
        public final Path origPath;

        BundlePathSubstitutionError(String message, Path origPath) {
            super(message);
            this.origPath = origPath;
        }
    }

    @SuppressWarnings("try")
    private Path substitutePath(Path origPath, Path destinationDir) {
        assert destinationDir.startsWith(rootDir);

        if (origPath.startsWith(rootDir)) {
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
            return rootDir.resolve(previousRelativeSubstitutedPath);
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
            throw new BundlePathSubstitutionError("Bundles do not allow inclusion of directory " + origPath, origPath);
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

        Path substitutedPath = destinationDir.resolve(baseName + extension);
        int collisionIndex = 0;
        while (Files.exists(substitutedPath)) {
            collisionIndex += 1;
            substitutedPath = destinationDir.resolve(baseName + "_" + collisionIndex + extension);
        }

        if (!destinationDir.startsWith(outputDir)) {
            copyFiles(origPath, substitutedPath);
        }

        Path relativeSubstitutedPath = rootDir.relativize(substitutedPath);
        if (nativeImage.isVerbose()) {
            System.out.println("RecordSubstitution src: " + origPath + ", dst: " + relativeSubstitutedPath);
        }
        pathSubstitutions.put(origPath, relativeSubstitutedPath);
        return substitutedPath;
    }

    private void copyFiles(Path source, Path target) {
        if (Files.isDirectory(source)) {
            try (Stream<Path> walk = Files.walk(source)) {
                walk.forEach(sourcePath -> copyFile(sourcePath, target.resolve(source.relativize(sourcePath))));
            } catch (IOException e) {
                throw NativeImage.showError("Failed to iterate through directory " + source, e);
            }
        } else {
            copyFile(source, target);
        }
    }

    private void copyFile(Path sourceFile, Path target) {
        try {
            if (nativeImage.isVerbose() && target.startsWith(rootDir)) {
                System.out.println("> Copy to bundle: " + nativeImage.config.workDir.relativize(sourceFile));
            }
            Files.copy(sourceFile, target);
        } catch (IOException e) {
            throw NativeImage.showError("Failed to copy " + sourceFile + " to " + target, e);
        }
    }

    void shutdown() {
        Path originalImagePath = bundleFile.getParent();
        copyFiles(outputDir, originalImagePath.resolve(outputDir.getFileName()));

        try {
            if (isBundleCreation()) {
                writeBundle();
            }
        } finally {
            nativeImage.deleteAllFiles(rootDir);
        }
    }

    void writeBundle() {
        assert isBundleCreation();

        Path pathCanonicalizationsFile = stageDir.resolve("path_canonicalizations.json");
        try (JsonWriter writer = new JsonWriter(pathCanonicalizationsFile)) {
            /* Printing as list with defined sort-order ensures useful diffs are possible */
            JsonPrinter.printCollection(writer, pathCanonicalizations.entrySet(), Map.Entry.comparingByKey(), BundleSupport::printPathMapping);
        } catch (IOException e) {
            throw NativeImage.showError("Failed to write bundle-file " + pathCanonicalizationsFile, e);
        }
        Path pathSubstitutionsFile = stageDir.resolve("path_substitutions.json");
        try (JsonWriter writer = new JsonWriter(pathSubstitutionsFile)) {
            /* Printing as list with defined sort-order ensures useful diffs are possible */
            JsonPrinter.printCollection(writer, pathSubstitutions.entrySet(), Map.Entry.comparingByKey(), BundleSupport::printPathMapping);
        } catch (IOException e) {
            throw NativeImage.showError("Failed to write bundle-file " + pathSubstitutionsFile, e);
        }

        Path buildArgsFile = stageDir.resolve("build.json");
        try (JsonWriter writer = new JsonWriter(buildArgsFile)) {
            /* Printing as list with defined sort-order ensures useful diffs are possible */
            JsonPrinter.printCollection(writer, buildArgs, null, BundleSupport::printBuildArg);
        } catch (IOException e) {
            throw NativeImage.showError("Failed to write bundle-file " + pathSubstitutionsFile, e);
        }

        /*
         * Provide a fallback to ensure we even get a bundle if there are errors before we are able
         * to determine the final bundle name (see use of BundleSupport.isBundleCreation() in
         * NativeImage.completeImageBuild() to know where this happens).
         */
        if (bundleFile == null) {
            bundleFile = nativeImage.config.getWorkingDirectory().resolve("unnamed.nib");
        }

        try (JarOutputStream jarOutStream = new JarOutputStream(Files.newOutputStream(bundleFile), new Manifest())) {
            try (Stream<Path> walk = Files.walk(rootDir)) {
                walk.forEach(bundleEntry -> {
                    if (Files.isDirectory(bundleEntry)) {
                        return;
                    }
                    String jarEntryName = rootDir.relativize(bundleEntry).toString();
                    JarEntry entry = new JarEntry(jarEntryName.replace(File.separator, "/"));
                    try {
                        entry.setTime(Files.getLastModifiedTime(bundleEntry).toMillis());
                        jarOutStream.putNextEntry(entry);
                        Files.copy(bundleEntry, jarOutStream);
                        jarOutStream.closeEntry();
                    } catch (IOException e) {
                        throw NativeImage.showError("Failed to copy " + bundleEntry + " into bundle file " + bundleFile, e);
                    }
                });
            }
        } catch (IOException e) {
            throw NativeImage.showError("Failed to create bundle file " + bundleFile, e);
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
