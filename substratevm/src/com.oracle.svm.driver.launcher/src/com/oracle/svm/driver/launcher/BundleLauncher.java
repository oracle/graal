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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.svm.driver.launcher.configuration.BundleArgsParser;
import com.oracle.svm.driver.launcher.configuration.BundleEnvironmentParser;

public class BundleLauncher {

    static final String BUNDLE_INFO_MESSAGE_PREFIX = "Native Image Bundles: ";
    private static final String BUNDLE_TEMP_DIR_PREFIX = "bundleRoot-";
    private static final String BUNDLE_FILE_EXTENSION = ".nib";
    private static final String HELP_TEXT = getResource("/com/oracle/svm/driver/launcher/BundleLauncherHelp.txt");

    private static Path rootDir;
    private static Path inputDir;
    private static Path outputDir;
    private static Path stageDir;
    private static Path classPathDir;
    private static Path modulePathDir;

    private static Path bundleFilePath;
    private static String bundleName;
    private static Path agentOutputDir;

    private static String newBundleName = null;
    private static boolean updateBundle = false;

    private static boolean verbose = false;

    private static ContainerSupport containerSupport;

    private static final List<String> launchArgs = new ArrayList<>();
    private static final List<String> applicationArgs = new ArrayList<>();
    private static final Map<String, String> launcherEnvironment = new HashMap<>();

    static String getResource(String resourceName) {
        try (InputStream input = BundleLauncher.class.getResourceAsStream(resourceName)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public static void main(String[] args) {
        bundleFilePath = Paths.get(BundleLauncher.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        bundleName = bundleFilePath.getFileName().toString().replace(BUNDLE_FILE_EXTENSION, "");
        agentOutputDir = bundleFilePath.getParent().resolve(Paths.get(bundleName + ".output", "launcher"));
        unpackBundle();

        // if we did not create a run.json bundle is not executable, e.g. shared library bundles
        if (!Files.exists(stageDir.resolve("run.json"))) {
            showMessage(BUNDLE_INFO_MESSAGE_PREFIX + "Bundle " + bundleFilePath + " is not executable!");
            System.exit(1);
        }

        parseBundleLauncherArgs(args);

        ProcessBuilder pb = new ProcessBuilder();

        Path environmentFile = stageDir.resolve("environment.json");
        if (Files.isReadable(environmentFile)) {
            try (Reader reader = Files.newBufferedReader(environmentFile)) {
                new BundleEnvironmentParser(launcherEnvironment).parseAndRegister(reader);
                pb.environment().putAll(launcherEnvironment);
            } catch (IOException e) {
                throw new Error("Failed to read bundle-file " + environmentFile, e);
            }
        }
        pb.command(createLaunchCommand());

        if (verbose) {
            List<String> environmentList = pb.environment()
                            .entrySet()
                            .stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .sorted()
                            .toList();
            showMessage("Executing [");
            showMessage(String.join(" \\\n", environmentList));
            showMessage(String.join(" \\\n", pb.command()));
            showMessage("]");
        }

        Process p = null;
        int exitCode;
        try {
            p = pb.inheritIO().start();
            exitCode = p.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new Error("Failed to run bundled application");
        } finally {
            if (p != null) {
                p.destroy();
            }
        }

        if (updateBundle) {
            exitCode = updateBundle();
        }

        System.exit(exitCode);
    }

    private static boolean useContainer() {
        return containerSupport != null;
    }

    private static List<String> createLaunchCommand() {
        List<String> command = new ArrayList<>();

        Path javaExecutable = getJavaExecutable().toAbsolutePath().normalize();

        if (useContainer()) {
            Path javaHome = javaExecutable.getParent().getParent();

            Map<Path, ContainerSupport.TargetPath> mountMapping = ContainerSupport.mountMappingFor(javaHome, inputDir, outputDir);
            if (Files.isDirectory(agentOutputDir)) {
                mountMapping.put(agentOutputDir, ContainerSupport.TargetPath.of(agentOutputDir, false));
                launcherEnvironment.put("LD_LIBRARY_PATH", ContainerSupport.GRAAL_VM_HOME.resolve("lib").toString());
            }

            containerSupport.initializeImage();
            command.addAll(containerSupport.createCommand(launcherEnvironment, mountMapping));
            command.add(ContainerSupport.GRAAL_VM_HOME.resolve(javaHome.relativize(javaExecutable)).toString());
        } else {
            command.add(javaExecutable.toString());
        }

        command.addAll(launchArgs);

        List<String> classpath = new ArrayList<>();
        if (Files.isDirectory(classPathDir)) {
            try (Stream<Path> walk = Files.walk(classPathDir, 1)) {
                walk.filter(path -> path.toString().endsWith(".jar") || Files.isDirectory(path))
                                .map(path -> useContainer() ? Paths.get("/").resolve(rootDir.relativize(path)) : path)
                                .map(Path::toString)
                                .forEach(classpath::add);
            } catch (IOException e) {
                throw new Error("Failed to iterate through directory " + classPathDir, e);
            }

            command.add("-cp");
            command.add(String.join(File.pathSeparator, classpath));
        }

        List<String> modulePath = new ArrayList<>();
        if (Files.isDirectory(modulePathDir)) {
            try (Stream<Path> walk = Files.walk(modulePathDir, 1)) {
                walk.filter(path -> (Files.isDirectory(path) && !path.equals(modulePathDir)) || path.toString().endsWith(".jar"))
                                .map(path -> useContainer() ? Paths.get("/").resolve(rootDir.relativize(path)) : path)
                                .map(Path::toString)
                                .forEach(modulePath::add);
            } catch (IOException e) {
                throw new Error("Failed to iterate through directory " + modulePathDir, e);
            }

            if (!modulePath.isEmpty()) {
                command.add("-p");
                command.add(String.join(File.pathSeparator, modulePath));
            }
        }

        Path argsFile = stageDir.resolve("run.json");
        try (Reader reader = Files.newBufferedReader(argsFile)) {
            List<String> argsFromFile = new ArrayList<>();
            new BundleArgsParser(argsFromFile).parseAndRegister(reader);
            command.addAll(argsFromFile);
        } catch (IOException e) {
            throw new Error("Failed to read bundle-file " + argsFile, e);
        }

        command.addAll(applicationArgs);

        return command;
    }

    private static int updateBundle() {
        List<String> command = new ArrayList<>();

        Path nativeImageExecutable = getNativeImageExecutable().toAbsolutePath().normalize();
        command.add(nativeImageExecutable.toString());

        Path newBundleFilePath = newBundleName == null ? bundleFilePath : bundleFilePath.getParent().resolve(newBundleName + BUNDLE_FILE_EXTENSION);
        command.add("--bundle-apply=" + bundleFilePath);
        command.add("--bundle-create=" + newBundleFilePath + ",dry-run");

        command.add("-cp");
        command.add(agentOutputDir.toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        Process p = null;
        try {
            p = pb.inheritIO().start();
            return p.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new Error("Failed to create updated bundle.");
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }

    private static void parseBundleLauncherArgs(String[] args) {
        Deque<String> argQueue = new ArrayDeque<>(Arrays.asList(args));

        while (!argQueue.isEmpty()) {
            String arg = argQueue.removeFirst();

            if (arg.startsWith("--with-native-image-agent")) {
                if (arg.indexOf(',') >= 0) {
                    String option = arg.substring(arg.indexOf(',') + 1);
                    if (option.startsWith("update-bundle")) {
                        updateBundle = true;
                        if (option.indexOf('=') >= 0) {
                            newBundleName = option.substring(option.indexOf('=')).replace(BUNDLE_FILE_EXTENSION, "");
                        }
                    } else {
                        throw new Error(String.format("Unknown option %s. Valid option is: update-bundle[=<new-bundle-name>].", option));
                    }
                }

                Path configOutputDir = agentOutputDir.resolve(Paths.get("META-INF", "native-image", bundleName + "-agent"));
                try {
                    Files.createDirectories(configOutputDir);
                    showMessage(BUNDLE_INFO_MESSAGE_PREFIX + "Native image agent output written to " + agentOutputDir);
                    launchArgs.add("-agentlib:native-image-agent=config-output-dir=" + configOutputDir);
                } catch (IOException e) {
                    throw new Error("Failed to create native image agent output dir");
                }
            } else if (arg.startsWith("--container")) {
                if (useContainer()) {
                    throw new Error("native-image launcher allows option container to be specified only once.");
                } else if (!System.getProperty("os.name").equals("Linux")) {
                    throw new Error("container option is only supported for Linux");
                }
                containerSupport = new ContainerSupport(stageDir, Error::new, BundleLauncher::showWarning, BundleLauncher::showMessage);
                if (arg.indexOf(',') != -1) {
                    String option = arg.substring(arg.indexOf(',') + 1);
                    arg = arg.substring(0, arg.indexOf(','));

                    if (option.startsWith("dockerfile")) {
                        if (option.indexOf('=') != -1) {
                            containerSupport.dockerfile = Paths.get(option.substring(option.indexOf('=') + 1));
                            if (!Files.isReadable(containerSupport.dockerfile)) {
                                throw new Error(String.format("Dockerfile '%s' is not readable", containerSupport.dockerfile.toAbsolutePath()));
                            }
                        } else {
                            throw new Error("container option dockerfile requires a dockerfile argument. E.g. dockerfile=path/to/Dockerfile.");
                        }
                    } else {
                        throw new Error(String.format("Unknown option %s. Valid option is: dockerfile=path/to/Dockerfile.", option));
                    }
                }
                if (arg.indexOf('=') != -1) {
                    containerSupport.tool = arg.substring(arg.indexOf('=') + 1);
                }
            } else {
                switch (arg) {
                    case "--help" -> {
                        showMessage(HELP_TEXT);
                        System.exit(0);
                    }
                    case "--verbose" -> verbose = true;
                    case "--" -> {
                        applicationArgs.addAll(argQueue);
                        argQueue.clear();
                    }
                    default -> applicationArgs.add(arg);
                }
            }
        }
    }

    // Checkstyle: Allow raw info or warning printing - begin
    private static void showMessage(String msg) {
        System.out.println(msg);
    }

    private static void showWarning(String msg) {
        System.out.println("Warning: " + msg);
    }
    // Checkstyle: Allow raw info or warning printing - end

    private static final Path buildTimeJavaHome = Paths.get(System.getProperty("java.home"));

    private static Path getJavaExecutable() {
        Path binJava = Paths.get("bin", System.getProperty("os.name").contains("Windows") ? "java.exe" : "java");
        if (Files.isExecutable(buildTimeJavaHome.resolve(binJava))) {
            return buildTimeJavaHome.resolve(binJava);
        }

        return getJavaHomeExecutable(binJava);
    }

    private static Path getJavaHomeExecutable(Path executable) {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            throw new Error("Environment variable JAVA_HOME is not set");
        }
        Path javaHomeDir = Paths.get(javaHome);
        if (!Files.isDirectory(javaHomeDir)) {
            throw new Error("Environment variable JAVA_HOME does not refer to a directory");
        }
        if (!Files.isExecutable(javaHomeDir.resolve(executable))) {
            throw new Error("Environment variable JAVA_HOME does not refer to a directory with a " + executable + " executable");
        }
        return javaHomeDir.resolve(executable);
    }

    private static Path getNativeImageExecutable() {
        Path binNativeImage = Paths.get("bin", System.getProperty("os.name").contains("Windows") ? "native-image.exe" : "native-image");
        if (Files.isExecutable(buildTimeJavaHome.resolve(binNativeImage))) {
            return buildTimeJavaHome.resolve(binNativeImage);
        }

        String graalVMHome = System.getenv("GRAALVM_HOME");
        if (graalVMHome != null) {
            Path graalVMHomeDir = Paths.get(graalVMHome);
            if (Files.isDirectory(graalVMHomeDir) && Files.isExecutable(graalVMHomeDir.resolve(binNativeImage))) {
                return graalVMHomeDir.resolve(binNativeImage);
            }
        }

        return getJavaHomeExecutable(binNativeImage);
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
            showMessage("Could not recursively delete path: " + toDelete);
            e.printStackTrace();
        }
    }

    private static void unpackBundle() {
        try {
            rootDir = createBundleRootDir();
            inputDir = rootDir.resolve("input");

            try (JarFile archive = new JarFile(bundleFilePath.toFile())) {
                Enumeration<JarEntry> jarEntries = archive.entries();
                while (jarEntries.hasMoreElements() && !deleteBundleRoot.get()) {
                    JarEntry jarEntry = jarEntries.nextElement();
                    Path bundleEntry = rootDir.resolve(jarEntry.getName());
                    try {
                        Path bundleFileParent = bundleEntry.getParent();
                        if (bundleFileParent != null) {
                            Files.createDirectories(bundleFileParent);
                        }
                        Files.copy(archive.getInputStream(jarEntry), bundleEntry);
                    } catch (IOException e) {
                        throw new Error("Unable to copy " + jarEntry.getName() + " from bundle " + bundleEntry + " to " + bundleEntry, e);
                    }
                }
            }
        } catch (IOException e) {
            throw new Error("Unable to expand bundle directory layout from bundle file " + bundleFilePath, e);
        }

        if (deleteBundleRoot.get()) {
            /* Abort bundle run request without error message and exit with 0 */
            throw new Error(null, null);
        }

        try {
            stageDir = Files.createDirectories(inputDir.resolve("stage"));
            Path classesDir = inputDir.resolve("classes");
            classPathDir = Files.createDirectories(classesDir.resolve("cp"));
            modulePathDir = Files.createDirectories(classesDir.resolve("p"));
            outputDir = Files.createDirectories(rootDir.resolve("output"));
        } catch (IOException e) {
            throw new Error("Unable to create bundle directory layout", e);
        }
    }
}
