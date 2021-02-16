/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.substratevm;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.ToolchainManager;
import org.apache.maven.toolchain.java.DefaultJavaToolChain;
import org.codehaus.plexus.util.xml.Xpp3Dom;

@Mojo(name = "native-image", defaultPhase = LifecyclePhase.PACKAGE)
public class NativeImageMojo extends AbstractMojo {

    private static final String NATIVE_IMAGE_META_INF = "META-INF/native-image";
    private static final String NATIVE_IMAGE_PROPERTIES_FILENAME = "native-image.properties";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)//
    private MavenProject project;

    @Parameter(defaultValue = "${plugin}", readonly = true) // Maven 3 only
    private PluginDescriptor plugin;

    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)//
    private File outputDirectory;

    @Parameter(property = "mainClass")//
    private String mainClass;

    @Parameter(property = "imageName")//
    private String imageName;

    @Parameter(property = "buildArgs")//
    private List<String> buildArgs;

    @Parameter(property = "skip", defaultValue = "false")//
    private boolean skip;

    @Parameter(defaultValue = "${session}", readonly = true)//
    private MavenSession session;

    @Component
    private ToolchainManager toolchainManager;

    private final List<Path> classpath = new ArrayList<>();

    private boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    private final Pattern majorMinorPattern = Pattern.compile("(\\d+\\.\\d+)\\.");

    private String attemptMajorMinor(String input) {
        Matcher matcher = majorMinorPattern.matcher(input);
        if (!matcher.find()) {
            return input;
        }
        return matcher.group(1);
    }

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping native-image generation (parameter 'skip' is true).");
            return;
        }

        classpath.clear();
        List<String> imageClasspathScopes = Arrays.asList(Artifact.SCOPE_COMPILE, Artifact.SCOPE_RUNTIME);
        project.setArtifactFilter(artifact -> imageClasspathScopes.contains(artifact.getScope()));
        for (Artifact dependency : project.getArtifacts()) {
            addClasspath(dependency);
        }
        addClasspath(project.getArtifact());
        String classpathStr = classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));

        Path nativeImageExecutableRelPath = Paths.get("lib", "svm", "bin", "native-image" + (isWindows() ? ".exe" : ""));
        Path nativeImageExecutable = getMojoJavaHome().resolve(nativeImageExecutableRelPath);
        if (!Files.isExecutable(nativeImageExecutable)) {
            nativeImageExecutable = getMojoJavaHome().resolve("jre").resolve(nativeImageExecutableRelPath);
            if (!Files.isExecutable(nativeImageExecutable)) {
                throw new MojoExecutionException("Could not find executable native-image in " + nativeImageExecutable);
            }
        }

        String nativeImageExecutableVersion = "Unknown";
        Process versionCheckProcess;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(nativeImageExecutable.toString(), "--version");
            versionCheckProcess = processBuilder.start();
            try (Scanner scanner = new Scanner(versionCheckProcess.getInputStream())) {
                while (true) {
                    if (scanner.findInLine("GraalVM Version ") != null) {
                        nativeImageExecutableVersion = scanner.next();
                        break;
                    }
                    if (!scanner.hasNextLine()) {
                        break;
                    }
                    scanner.nextLine();
                }

                if (versionCheckProcess.waitFor() != 0) {
                    throw new MojoExecutionException("Execution of " + String.join(" ", processBuilder.command()) + " returned non-zero result");
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Probing version info of native-image executable " + nativeImageExecutable + " failed", e);
        }

        if (!attemptMajorMinor(nativeImageExecutableVersion).equals(attemptMajorMinor(plugin.getVersion()))) {
            getLog().warn("Major.Minor version mismatch between " + plugin.getArtifactId() + " (" + plugin.getVersion() + ") and native-image executable (" + nativeImageExecutableVersion + ")");
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(nativeImageExecutable.toString(), "-cp", classpathStr);
            processBuilder.command().addAll(getBuildArgs());
            processBuilder.directory(getWorkingDirectory().toFile());
            processBuilder.inheritIO();

            String commandString = String.join(" ", processBuilder.command());
            getLog().info("Executing: " + commandString);
            Process imageBuildProcess = processBuilder.start();
            if (imageBuildProcess.waitFor() != 0) {
                throw new MojoExecutionException("Execution of " + commandString + " returned non-zero result");
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Building image with " + nativeImageExecutable + " failed", e);
        }
    }

    private void addClasspath(Artifact artifact) throws MojoExecutionException {
        if (!"jar".equals(artifact.getType())) {
            getLog().warn("Ignoring non-jar type ImageClasspath Entry " + artifact);
            return;
        }
        File artifactFile = artifact.getFile();
        if (artifactFile == null) {
            throw new MojoExecutionException("Missing jar-file for " + artifact + ". Ensure " + plugin.getArtifactId() + " runs in package phase.");
        }
        Path jarFilePath = artifactFile.toPath();
        getLog().info("ImageClasspath Entry: " + artifact + " (" + jarFilePath.toUri() + ")");

        URI jarFileURI = URI.create("jar:" + jarFilePath.toUri());
        try (FileSystem jarFS = FileSystems.newFileSystem(jarFileURI, Collections.emptyMap())) {
            Path nativeImageMetaInfBase = jarFS.getPath("/" + NATIVE_IMAGE_META_INF);
            if (Files.isDirectory(nativeImageMetaInfBase)) {
                List<Path> nativeImageProperties = Files.walk(nativeImageMetaInfBase)
                                .filter(p -> p.endsWith(NATIVE_IMAGE_PROPERTIES_FILENAME))
                                .collect(Collectors.toList());

                for (Path nativeImageProperty : nativeImageProperties) {
                    Path relativeSubDir = nativeImageMetaInfBase.relativize(nativeImageProperty).getParent();
                    boolean valid = relativeSubDir != null && (relativeSubDir.getNameCount() == 2);
                    valid = valid && relativeSubDir.getName(0).toString().equals(artifact.getGroupId());
                    valid = valid && relativeSubDir.getName(1).toString().equals(artifact.getArtifactId());
                    if (!valid) {
                        String example = NATIVE_IMAGE_META_INF + "/${groupId}/${artifactId}/" + NATIVE_IMAGE_PROPERTIES_FILENAME;
                        getLog().warn(nativeImageProperty.toUri() + " does not match recommended " + example + " layout.");
                    }
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Artifact " + artifact + "cannot be added to image classpath", e);
        }

        classpath.add(jarFilePath);
    }

    private Path getMojoJavaHome() {
        return Paths.get(Optional.ofNullable(toolchainManager)
            .map(tm -> tm.getToolchainFromBuildContext("jdk", session))
            .filter(DefaultJavaToolChain.class::isInstance).map(DefaultJavaToolChain.class::cast)
            .map(DefaultJavaToolChain::getJavaHome)
            .orElse(System.getProperty("java.home")));
    }

    private Path getWorkingDirectory() {
        return outputDirectory.toPath();
    }

    private String consumeConfigurationNodeValue(String pluginKey, String... nodeNames) {
        Plugin selectedPlugin = project.getPlugin(pluginKey);
        if (selectedPlugin == null) {
            return null;
        }
        return getConfigurationNodeValue(selectedPlugin, nodeNames);
    }

    private String consumeExecutionsNodeValue(String pluginKey, String... nodeNames) {
        Plugin selectedPlugin = project.getPlugin(pluginKey);
        if (selectedPlugin == null) {
            return null;
        }
        for (PluginExecution execution : selectedPlugin.getExecutions()) {
            String value = getConfigurationNodeValue(execution, nodeNames);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String getConfigurationNodeValue(ConfigurationContainer container, String... nodeNames) {
        if (container != null && container.getConfiguration() instanceof Xpp3Dom) {
            Xpp3Dom node = (Xpp3Dom) container.getConfiguration();
            for (String nodeName : nodeNames) {
                node = node.getChild(nodeName);
                if (node == null) {
                    return null;
                }
            }
            return node.getValue();
        }
        return null;
    }

    private List<String> getBuildArgs() {
        if (mainClass == null) {
            mainClass = consumeExecutionsNodeValue("org.apache.maven.plugins:maven-shade-plugin", "transformers", "transformer", "mainClass");
        }
        if (mainClass == null) {
            mainClass = consumeConfigurationNodeValue("org.apache.maven.plugins:maven-assembly-plugin", "archive", "manifest", "mainClass");
        }
        if (mainClass == null) {
            mainClass = consumeConfigurationNodeValue("org.apache.maven.plugins:maven-jar-plugin", "archive", "manifest", "mainClass");
        }

        List<String> list = new ArrayList<>();
        if (buildArgs != null && !buildArgs.isEmpty()) {
            for (String buildArg : buildArgs) {
                list.addAll(Arrays.asList(buildArg.split("\\s+")));
            }
        }
        if (mainClass != null && !mainClass.equals(".")) {
            list.add("-H:Class=" + mainClass);
        }
        if (imageName != null) {
            list.add("-H:Name=" + imageName);
        }
        return list;
    }
}
