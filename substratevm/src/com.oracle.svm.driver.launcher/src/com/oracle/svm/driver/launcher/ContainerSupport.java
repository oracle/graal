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

import com.oracle.svm.driver.launcher.configuration.BundleContainerSettingsParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class ContainerSupport {
    public String containerTool;
    public String bundleContainerTool;
    public String containerToolVersion;
    public String bundleContainerToolVersion;
    public String containerImage;
    public String bundleContainerImage;
    public Path dockerfile;

    public static final List<String> SUPPORTED_CONTAINER_TOOLS = List.of("podman", "docker");
    public static final String CONTAINER_TOOL_JSON_KEY = "containerTool";
    public static final String CONTAINER_TOOL_VERSION_JSON_KEY = "containerToolVersion";
    public static final String CONTAINER_IMAGE_JSON_KEY = "containerImage";

    private static final String BUNDLE_INFO_MESSAGE_PREFIX = "Native Image Bundles: ";

    public static final Path CONTAINER_GRAAL_VM_HOME = Path.of("/graalvm");

    private final BiFunction<String, Throwable, Error> errorFunction;
    private final Consumer<String> warningPrinter;
    private final Consumer<String> messagePrinter;

    public ContainerSupport(Path dockerfile, Path bundleStageDir) {
        this(dockerfile, bundleStageDir, Error::new, (msg) -> System.out.println("Warning: " + msg), System.out::println);
    }

    public ContainerSupport(Path dockerfile, Path bundleStageDir, BiFunction<String, Throwable, Error> errorFunction, Consumer<String> warningPrinter, Consumer<String> messagePrinter) {
        this.dockerfile = dockerfile;
        this.errorFunction = errorFunction;
        this.warningPrinter = warningPrinter;
        this.messagePrinter = messagePrinter;

        if (bundleStageDir != null) {
            Path containerFile = bundleStageDir.resolve("container.json");
            if (Files.exists(containerFile)) {
                try (Reader reader = Files.newBufferedReader(containerFile)) {
                    Map<String, String> containerSettings = new HashMap<>();
                    new BundleContainerSettingsParser(containerSettings).parseAndRegister(reader);
                    bundleContainerImage = containerSettings.getOrDefault(CONTAINER_IMAGE_JSON_KEY, bundleContainerImage);
                    bundleContainerTool = containerSettings.getOrDefault(CONTAINER_TOOL_JSON_KEY, bundleContainerTool);
                    bundleContainerToolVersion = containerSettings.getOrDefault(CONTAINER_TOOL_VERSION_JSON_KEY, bundleContainerToolVersion);
                } catch (IOException e) {
                    throw errorFunction.apply("Failed to read bundle-file " + containerFile, e);
                }
                if (bundleContainerTool != null) {
                    String containerToolVersionString = bundleContainerToolVersion == null ? "" : String.format(" (%s)", bundleContainerToolVersion);
                    messagePrinter.accept(String.format("%sBundled native-image was created in a container with %s%s.%n", BUNDLE_INFO_MESSAGE_PREFIX, bundleContainerTool, containerToolVersionString));
                }
            }
        }
    }

    public int initializeContainerImage() {
        try {
            containerImage = BundleLauncherUtil.digest(Files.readString(dockerfile));
        } catch (IOException e) {
            throw errorFunction.apply("Could not read Dockerfile " + dockerfile, e);
        }

        if (bundleContainerImage != null && !bundleContainerImage.equals(containerImage)) {
            warningPrinter.accept("The bundled image was created with a different dockerfile.");
        }

        if (bundleContainerTool != null && containerTool == null) {
            containerTool = bundleContainerTool;
        }

        if (containerTool != null) {
            if (!isToolAvailable(containerTool)) {
                throw errorFunction.apply("Configured container tool not available.", null);
            } else if (containerTool.equals("docker") && !isRootlessDocker()) {
                throw errorFunction.apply("Only rootless docker is supported for containerized builds.", null);
            }
            containerToolVersion = getContainerToolVersion(containerTool);

            if (bundleContainerTool != null) {
                if (!containerTool.equals(bundleContainerTool)) {
                    warningPrinter.accept(String.format("The bundled image was created with container tool '%s' (using '%s').%n", bundleContainerTool, containerTool));
                } else if (containerToolVersion != null && bundleContainerToolVersion != null && !containerToolVersion.equals(bundleContainerToolVersion)) {
                    warningPrinter.accept(String.format("The bundled image was created with different %s version '%s' (installed '%s').%n", containerTool, bundleContainerToolVersion, containerToolVersion));
                }
            }
        } else {
            for (String tool : SUPPORTED_CONTAINER_TOOLS) {
                if (isToolAvailable(tool)) {
                    if (tool.equals("docker") && !isRootlessDocker()) {
                        messagePrinter.accept(BUNDLE_INFO_MESSAGE_PREFIX + "Rootless context missing for docker.");
                        continue;
                    }
                    containerTool = tool;
                    containerToolVersion = getContainerToolVersion(tool);
                    break;
                }
            }
            if (containerTool == null) {
                throw errorFunction.apply(String.format("Please install one of the following tools before running containerized native image builds: %s", SUPPORTED_CONTAINER_TOOLS), null);
            }
        }

        return createContainer();
    }

    private int createContainer() {
        ProcessBuilder pbCheckForImage = new ProcessBuilder(containerTool, "images", "-q", containerImage + ":latest");
        ProcessBuilder pb = new ProcessBuilder(containerTool, "build", "-f", dockerfile.toString(), "-t", containerImage, ".");

        String imageId = getFirstProcessResultLine(pbCheckForImage);
        if (imageId == null) {
            pb.inheritIO();
        } else {
            messagePrinter.accept(String.format("%sReusing container image %s.%n", BUNDLE_INFO_MESSAGE_PREFIX, containerImage));
        }

        Process p = null;
        try {
            p = pb.start();
            int status = p.waitFor();
            if (status == 0 && imageId != null && !imageId.equals(getFirstProcessResultLine(pbCheckForImage))) {
                try (var processResult = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    messagePrinter.accept(String.format("%sUpdated container image %s.%n", BUNDLE_INFO_MESSAGE_PREFIX, containerImage));
                    processResult.lines().forEach(messagePrinter);
                }
            }
            return status;
        } catch (IOException | InterruptedException e) {
            throw errorFunction.apply(e.getMessage(), e);
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }

    private boolean isToolAvailable(String tool) {
        return Arrays.stream(System.getenv("PATH").split(":"))
                .map(str -> Path.of(str).resolve(tool))
                .anyMatch(Files::isExecutable);
    }

    private String getContainerToolVersion(String tool) {
        ProcessBuilder pb = new ProcessBuilder(tool, "--version");
        return getFirstProcessResultLine(pb);
    }

    private boolean isRootlessDocker() {
        ProcessBuilder pb = new ProcessBuilder("docker", "context", "show");
        return getFirstProcessResultLine(pb).equals("rootless");
    }

    private String getFirstProcessResultLine(ProcessBuilder pb) {
        Process p = null;
        try {
            p = pb.start();
            p.waitFor();
            try (var processResult = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                return processResult.readLine();
            }
        } catch (IOException | InterruptedException e) {
            throw errorFunction.apply(e.getMessage(), e);
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }

    public record TargetPath(Path path, boolean readonly) {
        public static TargetPath readonly(Path target) {
            return of(target, true);
        }

        public static TargetPath of(Path target, boolean readonly) {
            return new TargetPath(target, readonly);
        }
    }

    public static Map<Path, TargetPath> mountMappingFor(Path javaHome, Path inputDir, Path outputDir) {
        Map<Path, TargetPath> mountMapping = new HashMap<>();
        Path containerRoot = Paths.get("/");
        mountMapping.put(javaHome, TargetPath.readonly(containerRoot.resolve(CONTAINER_GRAAL_VM_HOME)));
        mountMapping.put(inputDir, ContainerSupport.TargetPath.readonly(containerRoot.resolve(BundleLauncher.INPUT_DIR_NAME)));
        mountMapping.put(outputDir, ContainerSupport.TargetPath.of(containerRoot.resolve(BundleLauncher.OUTPUT_DIR_NAME), false));
        return mountMapping;
    }

    public List<String> createContainerCommand(Map<String, String> containerEnvironment, Map<Path, TargetPath> mountMapping) {
        List<String> containerCommand = new ArrayList<>();

        // run docker tool without network access and remove container after image build is finished
        containerCommand.add(containerTool);
        containerCommand.add("run");
        containerCommand.add("--network=none");
        containerCommand.add("--rm");

        // inject environment variables into container
        containerEnvironment.forEach((key, value) -> {
                    containerCommand.add("-e");
                    containerCommand.add(key + "=" + BundleLauncherUtil.quoteShellArg(value));
                });

        // mount java home, input and output directories and argument files for native image build
        mountMapping.forEach((source, target) -> {
            containerCommand.add("--mount");
            List<String> mountArgs = new ArrayList<>();
            mountArgs.add("type=bind");
            mountArgs.add("source=" + source);
            mountArgs.add("target=" + target.path);
            if (target.readonly) {
                mountArgs.add("readonly");
            }
            containerCommand.add(BundleLauncherUtil.quoteShellArg(String.join(",", mountArgs)));
        });

        // specify container name
        containerCommand.add(containerImage);

        return containerCommand;
    }

    public static void replaceContainerPaths(List<String> arguments, Path javaHome, Path bundleRoot) {
        arguments.replaceAll(arg -> arg
                .replace(javaHome.toString(), CONTAINER_GRAAL_VM_HOME.toString())
                .replace(bundleRoot.toString(), "")
        );
    }
}
