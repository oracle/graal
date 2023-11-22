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

import com.oracle.svm.driver.launcher.configuration.BundleContainerSettingsParser;

public class ContainerSupport {
    public String tool;
    public String bundleTool;
    public String toolVersion;
    public String bundleToolVersion;
    public String image;
    public String bundleImage;
    public Path dockerfile;

    public static final List<String> SUPPORTED_TOOLS = List.of("podman", "docker");
    public static final String TOOL_JSON_KEY = "containerTool";
    public static final String TOOL_VERSION_JSON_KEY = "containerToolVersion";
    public static final String IMAGE_JSON_KEY = "containerImage";

    public static final Path GRAAL_VM_HOME = Path.of("/graalvm");

    private final BiFunction<String, Throwable, Error> errorFunction;
    private final Consumer<String> warningPrinter;
    private final Consumer<String> messagePrinter;

    public ContainerSupport(Path bundleStageDir, BiFunction<String, Throwable, Error> errorFunction, Consumer<String> warningPrinter, Consumer<String> messagePrinter) {
        this.errorFunction = errorFunction;
        this.warningPrinter = warningPrinter;
        this.messagePrinter = messagePrinter;

        if (bundleStageDir != null) {
            dockerfile = bundleStageDir.resolve("Dockerfile");
            Path containerFile = bundleStageDir.resolve("container.json");
            if (Files.exists(containerFile)) {
                try (Reader reader = Files.newBufferedReader(containerFile)) {
                    Map<String, String> containerSettings = new HashMap<>();
                    new BundleContainerSettingsParser(containerSettings).parseAndRegister(reader);
                    bundleImage = containerSettings.getOrDefault(IMAGE_JSON_KEY, bundleImage);
                    bundleTool = containerSettings.getOrDefault(TOOL_JSON_KEY, bundleTool);
                    bundleToolVersion = containerSettings.getOrDefault(TOOL_VERSION_JSON_KEY, bundleToolVersion);
                } catch (IOException e) {
                    throw errorFunction.apply("Failed to read bundle-file " + containerFile, e);
                }
                if (bundleTool != null) {
                    String containerToolVersionString = bundleToolVersion == null ? "" : String.format(" (%s)", bundleToolVersion);
                    messagePrinter.accept(
                                    String.format("%sBundled native-image was created in a container with %s%s.%n", BundleLauncher.BUNDLE_INFO_MESSAGE_PREFIX, bundleTool, containerToolVersionString));
                }
            }
        }
    }

    public int initializeImage() {
        try {
            image = BundleLauncherUtil.digest(Files.readString(dockerfile));
        } catch (IOException e) {
            throw errorFunction.apply("Could not read Dockerfile " + dockerfile, e);
        }

        if (bundleImage != null && !bundleImage.equals(image)) {
            warningPrinter.accept("The bundled image was created with a different dockerfile.");
        }

        if (bundleTool != null && tool == null) {
            tool = bundleTool;
        }

        if (tool != null) {
            if (!isToolAvailable(tool)) {
                throw errorFunction.apply("Configured container tool not available.", null);
            } else if (tool.equals("docker") && !isRootlessDocker()) {
                throw errorFunction.apply("Only rootless docker is supported for containerized builds.", null);
            }
            toolVersion = getToolVersion();

            if (bundleTool != null) {
                if (!tool.equals(bundleTool)) {
                    warningPrinter.accept(String.format("The bundled image was created with container tool '%s' (using '%s').%n", bundleTool, tool));
                } else if (toolVersion != null && bundleToolVersion != null && !toolVersion.equals(bundleToolVersion)) {
                    warningPrinter.accept(String.format("The bundled image was created with different %s version '%s' (installed '%s').%n", tool, bundleToolVersion, toolVersion));
                }
            }
        } else {
            for (String supportedTool : SUPPORTED_TOOLS) {
                if (isToolAvailable(supportedTool)) {
                    if (supportedTool.equals("docker") && !isRootlessDocker()) {
                        messagePrinter.accept(BundleLauncher.BUNDLE_INFO_MESSAGE_PREFIX + "Rootless context missing for docker.");
                        continue;
                    }
                    tool = supportedTool;
                    toolVersion = getToolVersion();
                    break;
                }
            }
            if (tool == null) {
                throw errorFunction.apply(String.format("Please install one of the following tools before running containerized native image builds: %s", SUPPORTED_TOOLS), null);
            }
        }

        return createContainer();
    }

    private int createContainer() {
        ProcessBuilder pbCheckForImage = new ProcessBuilder(tool, "images", "-q", image + ":latest");
        ProcessBuilder pb = new ProcessBuilder(tool, "build", "-f", dockerfile.toString(), "-t", image, ".");

        String imageId = getFirstProcessResultLine(pbCheckForImage);
        if (imageId == null) {
            pb.inheritIO();
        } else {
            messagePrinter.accept(String.format("%sReusing container image %s.%n", BundleLauncher.BUNDLE_INFO_MESSAGE_PREFIX, image));
        }

        Process p = null;
        try {
            p = pb.start();
            int status = p.waitFor();
            if (status == 0 && imageId != null && !imageId.equals(getFirstProcessResultLine(pbCheckForImage))) {
                try (var processResult = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    messagePrinter.accept(String.format("%sUpdated container image %s.%n", BundleLauncher.BUNDLE_INFO_MESSAGE_PREFIX, image));
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

    private static boolean isToolAvailable(String toolName) {
        return Arrays.stream(System.getenv("PATH").split(":"))
                        .map(str -> Path.of(str).resolve(toolName))
                        .anyMatch(Files::isExecutable);
    }

    private String getToolVersion() {
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
        mountMapping.put(javaHome, TargetPath.readonly(containerRoot.resolve(GRAAL_VM_HOME)));
        mountMapping.put(inputDir, ContainerSupport.TargetPath.readonly(containerRoot.resolve("input")));
        mountMapping.put(outputDir, ContainerSupport.TargetPath.of(containerRoot.resolve("output"), false));
        return mountMapping;
    }

    public List<String> createCommand(Map<String, String> containerEnvironment, Map<Path, TargetPath> mountMapping) {
        List<String> containerCommand = new ArrayList<>();

        // run docker tool without network access and remove container after image build is finished
        containerCommand.add(tool);
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
        containerCommand.add(image);

        return containerCommand;
    }

    public static void replacePaths(List<String> arguments, Path javaHome, Path bundleRoot) {
        arguments.replaceAll(arg -> arg
                        .replace(javaHome.toString(), GRAAL_VM_HOME.toString())
                        .replace(bundleRoot.toString(), ""));
    }
}
