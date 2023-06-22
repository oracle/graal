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

import com.oracle.svm.driver.launcher.configuration.BundleArgsParser;
import com.oracle.svm.driver.launcher.configuration.BundleEnvironmentParser;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
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
import java.util.stream.Stream;

import static com.oracle.svm.driver.launcher.ContainerSupport.CONTAINER_GRAAL_VM_HOME;
import static com.oracle.svm.driver.launcher.ContainerSupport.replaceContainerPaths;
import static com.oracle.svm.driver.launcher.ContainerSupport.mountMappingFor;
import static com.oracle.svm.driver.launcher.ContainerSupport.TargetPath;


public class BundleLauncher {

    public static final String BUNDLE_TEMP_DIR_PREFIX = "bundleRoot-";
    public static final String BUNDLE_FILE_EXTENSION = ".nib";

    public static final String INPUT_DIR_NAME = "input";
    public static final String STAGE_DIR_NAME = "stage";
    public static final String AUXILIARY_DIR_NAME = "auxiliary";
    public static final String CLASSES_DIR_NAME = "classes";
    public static final String CLASSPATH_DIR_NAME = "cp";
    public static final String MODULE_PATH_DIR_NAME = "p";
    public static final String OUTPUT_DIR_NAME = "output";
    public static final String IMAGE_PATH_OUTPUT_DIR_NAME = "default";
    public static final String AUXILIARY_OUTPUT_DIR_NAME = "other";

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

    public static boolean verbose = false;

    public static ContainerSupport containerSupport;

    public static Map<String, String> launcherEnvironment = new HashMap<>();


    public static void main(String[] args) {
        bundleFilePath = Paths.get(BundleLauncher.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        bundleName = bundleFilePath.getFileName().toString().replace(BUNDLE_FILE_EXTENSION, "");
        agentOutputDir = bundleFilePath.getParent().resolve(Paths.get(bundleName + "." + OUTPUT_DIR_NAME, "launcher"));
        unpackBundle(bundleFilePath);

        // if we did not create a run.json bundle is not executable, e.g. shared library bundles
        if (!Files.exists(stageDir.resolve("run.json"))) {
            System.out.println("Bundle " + bundleFilePath + " is not executable!");
            System.exit(1);
        }

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
        pb.command(createLaunchCommand(args));

        if (verbose) {
            List<String> environmentList = pb.environment()
                    .entrySet()
                    .stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .sorted()
                    .toList();
            System.out.println("Executing [");
            System.out.println(String.join(" \\\n", environmentList));
            System.out.println(String.join(" \\\n", pb.command()));
            System.out.println("]");
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

    public static boolean useContainer() {
        return containerSupport != null;
    }

    private static List<String> createLaunchCommand(String[] args) {
        List<String> command = new ArrayList<>();

        Path javaExecutable = getJavaExecutable().toAbsolutePath().normalize();

        if (useContainer()) {
            Path javaHome = javaExecutable.getParent().getParent();

            Map<Path, TargetPath> mountMapping = mountMappingFor(javaHome, inputDir, outputDir);
            if (Files.isDirectory(agentOutputDir)) {
                mountMapping.put(agentOutputDir, TargetPath.of(agentOutputDir, false));
            }

            containerSupport.initializeContainerImage();
            command.addAll(containerSupport.createContainerCommand(launcherEnvironment, mountMapping));
            command.add(CONTAINER_GRAAL_VM_HOME.resolve(javaHome.relativize(javaExecutable)).toString());
        } else {
            command.add(javaExecutable.toString());
        }

        List<String> applicationArgs = new ArrayList<>();
        List<String> launchArgs = parseBundleLauncherArgs(args, applicationArgs);
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
                walk.filter(path -> Files.isDirectory(path) && !path.equals(modulePathDir))
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

    private static List<String> parseBundleLauncherArgs(String[] args, List<String> applicationArgs) {
        Deque<String> argQueue = new ArrayDeque<>(Arrays.asList(args));
        List<String> launchArgs = new ArrayList<>();

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

                Path outputDir = agentOutputDir.resolve(Paths.get("META-INF", "native-image", bundleName + "-agent"));
                try {
                    Files.createDirectories(outputDir);
                    System.out.println("Native image agent output written to " + agentOutputDir);
                    launchArgs.add("-agentlib:native-image-agent=config-output-dir=" + outputDir);
                } catch (IOException e) {
                    System.out.println("Failed to create native image agent output dir");
                }
            } else if (arg.startsWith("--container")) {
                if (useContainer()) {
                    throw new Error("native-image launcher allows option container to be specified only once.");
                }
                Path dockerfile;
                if (arg.indexOf(',') != -1) {
                    String option = arg.substring(arg.indexOf(',') + 1);
                    arg = arg.substring(0, arg.indexOf(','));

                    if (option.startsWith("dockerfile")) {
                        if (option.indexOf('=') != -1) {
                            dockerfile = Paths.get(option.substring(option.indexOf('=') + 1));
                            if (!Files.isReadable(dockerfile)) {
                                throw new Error(String.format("Dockerfile '%s' is not readable", dockerfile.toAbsolutePath()));
                            }
                        } else {
                            throw new Error("container option dockerfile requires a dockerfile argument. E.g. dockerfile=path/to/Dockerfile.");
                        }
                    } else {
                        throw new Error(String.format("Unknown option %s. Valid option is: dockerfile=path/to/Dockerfile.", option));
                    }
                } else {
                    dockerfile = stageDir.resolve("Dockerfile");
                }
                containerSupport = new ContainerSupport(dockerfile, stageDir);
                if (arg.indexOf('=') != -1) {
                    containerSupport.containerTool = arg.substring(arg.indexOf('=') + 1);
                }
            } else {
                switch (arg) {
                    case "--verbose" -> verbose = true;
                    case "--" -> {
                        applicationArgs.addAll(argQueue);
                        argQueue.clear();
                    }
                    default -> applicationArgs.add(arg);
                }
            }
        }

        return launchArgs;
    }

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
            System.out.println("Could not recursively delete path: " + toDelete);
            e.printStackTrace();
        }
    }

    private static void unpackBundle(Path bundleFilePath) {
        try {
            rootDir = createBundleRootDir();
            inputDir = rootDir.resolve(INPUT_DIR_NAME);

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
            stageDir = Files.createDirectories(inputDir.resolve(STAGE_DIR_NAME));
            Path classesDir = inputDir.resolve(CLASSES_DIR_NAME);
            classPathDir = Files.createDirectories(classesDir.resolve(CLASSPATH_DIR_NAME));
            modulePathDir = Files.createDirectories(classesDir.resolve(MODULE_PATH_DIR_NAME));
            outputDir = Files.createDirectories(rootDir.resolve(OUTPUT_DIR_NAME));
        } catch (IOException e) {
            throw new Error("Unable to create bundle directory layout", e);
        }
    }
}
