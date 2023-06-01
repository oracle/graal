/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver.launcher;

import com.oracle.svm.driver.launcher.configuration.ArgsParser;
import com.oracle.svm.driver.launcher.configuration.EnvironmentParser;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;


public class BundleLauncher {
    private static final String BUNDLE_TEMP_DIR_PREFIX = "bundleRoot-";
    static final String BUNDLE_FILE_EXTENSION = ".nib";
    private static final String AUXILIARY_OUTPUT_DIR_NAME = "other";
    private static final String OUTPUT_DIR_NAME = "output";

    private static Path stageDir;
    private static Path classPathDir;
    private static Path modulePathDir;

    private static Path bundleFilePath;


    public static void main(String[] args) {
        List<String> command = new ArrayList<>();

        bundleFilePath = Paths.get(BundleLauncher.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        unpackBundle(bundleFilePath);

        Path javaExecutable = getJavaExecutable().toAbsolutePath().normalize();
        command.add(javaExecutable.toString());

        List<String> programArgs = parseBundleLauncherOptions(args);
        command.addAll(programArgs);

        List<String> classpath = new ArrayList<>();
        if (Files.isDirectory(classPathDir)) {
            try (Stream<Path> walk = Files.walk(classPathDir, 1)) {
                walk.filter(path -> path.toString().endsWith(".jar") || Files.isDirectory(path))
                        .map(Path::toString)
                        .forEach(classpath::add);
            } catch (IOException e) {
                throw new RuntimeException("Failed to iterate through directory " + classPathDir, e);
            }

            command.add("-cp");
            command.add(String.join(File.pathSeparator, classpath));
        }


        List<String> modulePath = new ArrayList<>();
        if (Files.isDirectory(modulePathDir)) {
            try (Stream<Path> walk = Files.walk(modulePathDir, 1)) {
                walk.filter(Files::isDirectory)
                        .filter(path -> !path.equals(modulePathDir))
                        .map(Path::toString)
                        .forEach(modulePath::add);
            } catch (IOException e) {
                throw new RuntimeException("Failed to iterate through directory " + modulePathDir, e);
            }

            if (!modulePath.isEmpty()) {
                command.add("-p");
                command.add(String.join(File.pathSeparator, modulePath));
            }
        }

        Path buildArgsFile = stageDir.resolve("run.json");
        try (Reader reader = Files.newBufferedReader(buildArgsFile)) {
            List<String> argsFromFile = new ArrayList<>();
            new ArgsParser(argsFromFile).parseAndRegister(reader);
            command.addAll(argsFromFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read bundle-file " + buildArgsFile, e);
        }

        ProcessBuilder pb = new ProcessBuilder(command);

        Path environmentFile = stageDir.resolve("environment.json");
        if (Files.isReadable(environmentFile)) {
            try (Reader reader = Files.newBufferedReader(environmentFile)) {
                Map<String, String> launcherEnvironment = new HashMap<>();
                new EnvironmentParser(launcherEnvironment).parseAndRegister(reader);
                pb.environment().putAll(launcherEnvironment);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read bundle-file " + environmentFile, e);
            }
        }

        if (System.getenv("BUNDLE_LAUNCHER_VERBOSE") != null) {
            List<String> environmentList = pb.environment()
                    .entrySet()
                    .stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .sorted()
                    .toList();
            System.out.println("Executing [");
            System.out.println(String.join(" \\\n", environmentList));
            System.out.println(String.join(" \\\n", command));
            System.out.println("]");
        }

        Process p = null;
        try {
            p = pb.inheritIO().start();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to run bundled application");
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }

    private static List<String> parseBundleLauncherOptions(String[] args) {
        Deque<String> argQueue = new ArrayDeque<>(Arrays.asList(args));
        List<String> programArgs = new ArrayList<>();

        while (!argQueue.isEmpty()) {
            String arg = argQueue.removeFirst();

            if (arg.startsWith("--with-native-image-agent")) {
                String bundleName = bundleFilePath.getFileName().toString().replace(BUNDLE_FILE_EXTENSION, "");
                Path bundlePath = bundleFilePath.getParent();
                Path externalAuxiliaryOutputDir = bundlePath.resolve(bundleName + "." + OUTPUT_DIR_NAME).resolve(AUXILIARY_OUTPUT_DIR_NAME);
                try {
                    Files.createDirectories(externalAuxiliaryOutputDir);
                    System.out.println("Native image agent output written to " + externalAuxiliaryOutputDir);
                    programArgs.add("-agentlib:native-image-agent=config-output-dir=" + externalAuxiliaryOutputDir);
                } catch (IOException e) {
                    System.out.println("Failed to create native image agent output dir");
                }
            } else if (arg.equals("--")) {
                programArgs.addAll(argQueue);
                argQueue.clear();
            } else {
                programArgs.add(arg);
            }
        }

        return programArgs;
    }

    private static final Path buildTimeJavaHome = Paths.get(System.getProperty("java.home"));

    private static Path getJavaExecutable() {
        Path binJava = Paths.get("bin", System.getProperty("os.name").contains("Windows") ? "java.exe" : "java");

        Path javaCandidate = buildTimeJavaHome.resolve(binJava);
        if (Files.isExecutable(javaCandidate)) {
            return javaCandidate;
        }

        javaCandidate = Paths.get(".").resolve(binJava);
        if (Files.isExecutable(javaCandidate)) {
            return javaCandidate;
        }

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            throw new RuntimeException("No " + binJava + " and no environment variable JAVA_HOME");
        }
        try {
            javaCandidate = Paths.get(javaHome).resolve(binJava);
            if (Files.isExecutable(javaCandidate)) {
                return javaCandidate;
            }
        } catch (InvalidPathException e) {
            /* fallthrough */
        }
        throw new RuntimeException("No " + binJava + " and invalid JAVA_HOME=" + javaHome);
    }

    private static final AtomicBoolean deleteBundleRoot = new AtomicBoolean();
    private static Path createBundleRootDir() throws IOException {
        Path bundleRoot = Files.createTempDirectory(BUNDLE_TEMP_DIR_PREFIX);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            deleteBundleRoot.set(true);
            deleteAllFiles(bundleRoot);
        }));
        return bundleRoot;
    }


    private static final String deletedFileSuffix = ".deleted";
    private static boolean isDeletedPath(Path toDelete) {
        return toDelete.getFileName().toString().endsWith(deletedFileSuffix);
    }

    private static void deleteAllFiles(Path toDelete) {
        try {
            Path deletedPath = toDelete;
            if (!isDeletedPath(deletedPath)) {
                deletedPath = toDelete.resolveSibling(toDelete.getFileName() + deletedFileSuffix);
                Files.move(toDelete, deletedPath);
            }
            try (Stream<Path> walk = Files.walk(deletedPath)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        } catch (IOException e) {
            System.out.println("Could not recursively delete path: " + toDelete);
            e.printStackTrace();
        }
    }


    private static void unpackBundle(Path bundleFilePath) {
        Path rootDir;
        Path inputDir;
        try {
            rootDir = createBundleRootDir();
            inputDir = rootDir.resolve("input");

            try (JarFile archive = new JarFile(bundleFilePath.toFile())) {
                Enumeration<JarEntry> jarEntries = archive.entries();
                while (jarEntries.hasMoreElements() && !deleteBundleRoot.get()) {
                    JarEntry jarEntry = jarEntries.nextElement();
                    Path bundleEntry = rootDir.resolve(jarEntry.getName());
                    if (bundleEntry.startsWith(inputDir)) {
                        try {
                            Path bundleFileParent = bundleEntry.getParent();
                            if (bundleFileParent != null) {
                                Files.createDirectories(bundleFileParent);
                            }
                            Files.copy(archive.getInputStream(jarEntry), bundleEntry);
                        } catch (IOException e) {
                            throw new RuntimeException("Unable to copy " + jarEntry.getName() + " from bundle " + bundleEntry + " to " + bundleEntry, e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to expand bundle directory layout from bundle file " + bundleFilePath, e);
        }

        if (deleteBundleRoot.get()) {
            /* Abort image build request without error message and exit with 0 */
            throw new RuntimeException("");
        }

        try {
            inputDir = rootDir.resolve("input");
            stageDir = Files.createDirectories(inputDir.resolve("stage"));
            Path classesDir = inputDir.resolve("classes");
            classPathDir = Files.createDirectories(classesDir.resolve("cp"));
            modulePathDir = Files.createDirectories(classesDir.resolve("p"));
        } catch (IOException e) {
            throw new RuntimeException("Unable to create bundle directory layout", e);
        }
    }
}
