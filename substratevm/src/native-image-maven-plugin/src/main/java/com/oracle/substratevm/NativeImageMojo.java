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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.logging.AbstractLogger;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import com.oracle.svm.driver.NativeImage;

@Mojo(name = "native-image", defaultPhase = LifecyclePhase.PACKAGE)
public class NativeImageMojo extends AbstractMojo {
    private static final String svmGroupId = "com.oracle.substratevm";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)//
    private MavenProject project;

    @Parameter(defaultValue = "${plugin}", readonly = true) // Maven 3 only
    private PluginDescriptor plugin;

    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)//
    private File outputDirectory;

    @Parameter(property = "mainClass")//
    private String mainClass;

    @Parameter(property = "buildArgs")//
    private String buildArgs;

    private Logger tarGzLogger = new AbstractLogger(Logger.LEVEL_WARN, "NativeImageMojo.tarGzLogger") {
        @Override
        public void debug(String message, Throwable throwable) {
            if (isDebugEnabled()) {
                getLog().debug(message, throwable);
            }
        }

        @Override
        public void info(String message, Throwable throwable) {
            if (isInfoEnabled()) {
                getLog().info(message, throwable);
            }
        }

        @Override
        public void warn(String message, Throwable throwable) {
            if (isWarnEnabled()) {
                getLog().warn(message, throwable);
            }
        }

        @Override
        public void error(String message, Throwable throwable) {
            if (isErrorEnabled()) {
                getLog().error(message, throwable);
            }
        }

        @Override
        public void fatalError(String message, Throwable throwable) {
            if (isFatalErrorEnabled()) {
                getLog().error(message, throwable);
            }
        }

        @Override
        public Logger getChildLogger(String name) {
            return this;
        }
    };

    private final TarGZipUnArchiver tarGzExtract = new TarGZipUnArchiver();
    private final List<Path> classpath = new ArrayList<>();

    NativeImageMojo() {
        tarGzExtract.enableLogging(tarGzLogger);
    }

    private Stream<Artifact> getSelectedArtifactsStream(String groupId, String... artifactIds) {
        return plugin.getArtifacts().stream()
                        .filter(artifact -> artifact.getGroupId().equals(groupId))
                        .filter(artifact -> {
                            List<String> artifactIdsList = Arrays.asList(artifactIds);
                            if (artifactIdsList.isEmpty()) {
                                return true;
                            }
                            return artifactIdsList.contains(artifact.getArtifactId());
                        });
    }

    public void execute() throws MojoExecutionException {
        classpath.clear();
        List<String> imageClasspathScopes = Arrays.asList(Artifact.SCOPE_COMPILE, Artifact.SCOPE_RUNTIME);
        project.setArtifactFilter(artifact -> imageClasspathScopes.contains(artifact.getScope()));
        for (Artifact dependency : project.getArtifacts()) {
            addClasspath(dependency);
        }
        addClasspath(project.getArtifact());
        String classpathStr = classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));

        Path nativeImageExecutable = getJavaHome().resolve("bin/native-image");
        if (Files.isExecutable(nativeImageExecutable)) {
            String nativeImageExecutableVersion = "Unknown";
            Process versionCheckProcess = null;
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

            if (!nativeImageExecutableVersion.equals(plugin.getVersion())) {
                getLog().warn("Version mismatch between " + plugin.getArtifactId() + " (" + plugin.getVersion() + ") and native-image executable (" + nativeImageExecutableVersion + ")");
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
        } else {
            Path untarDestDirectory = getUntarDestDirectory();
            if (!Files.isDirectory(untarDestDirectory)) {
                try {
                    Files.createDirectories(untarDestDirectory);
                } catch (IOException e) {
                    throw new MojoExecutionException("Unable to create directory " + untarDestDirectory, e);
                }
                plugin.getArtifacts().stream()
                                .filter(artifact -> artifact.getGroupId().equals(svmGroupId))
                                .filter(artifact -> artifact.getArtifactId().startsWith("svm-hosted-native-"))
                                .forEach(artifact -> {
                                    if (artifact.getType().equals("tar.gz")) {
                                        tarGzExtract.setSourceFile(artifact.getFile());
                                        tarGzExtract.setDestDirectory(untarDestDirectory.toFile());
                                        tarGzExtract.extract();
                                    }
                                });
            }

            try {
                MojoBuildConfiguration config = new MojoBuildConfiguration();
                getLog().info("WorkingDirectory: " + config.getWorkingDirectory());
                getLog().info("ImageClasspath: " + classpathStr);
                getLog().info("BuildArgs: " + config.getBuildArgs());
                NativeImage.build(config);
            } catch (NativeImage.NativeImageError e) {
                throw new MojoExecutionException("Error creating native image:", e);
            }
        }
    }

    private void addClasspath(Artifact artifact) throws MojoExecutionException {
        File artifactFile = artifact.getFile();
        if (artifactFile == null) {
            throw new MojoExecutionException("Missing jar-file for " + artifact + ". Ensure " + plugin.getArtifactId() + " runs in package phase.");
        }
        Path jarFilePath = artifactFile.toPath();
        getLog().info("ImageClasspath Entry: " + artifact + " (" + jarFilePath.toUri() + ")");

        URI jarFileURI = URI.create("jar:file:" + jarFilePath);
        try (FileSystem jarFS = FileSystems.newFileSystem(jarFileURI, Collections.emptyMap())) {
            Path nativeImageMetaInfBase = jarFS.getPath("/" + NativeImage.nativeImagePropertiesMetaInf);
            if (Files.isDirectory(nativeImageMetaInfBase)) {
                List<Path> nativeImageProperties = Files.walk(nativeImageMetaInfBase)
                                .filter(p -> p.endsWith(NativeImage.nativeImagePropertiesFilename))
                                .collect(Collectors.toList());

                for (Path nativeImageProperty : nativeImageProperties) {
                    Path relativeSubDir = nativeImageMetaInfBase.relativize(nativeImageProperty).getParent();
                    boolean valid = relativeSubDir != null && (relativeSubDir.getNameCount() == 2);
                    valid = valid && relativeSubDir.getName(0).toString().equals(artifact.getGroupId());
                    valid = valid && relativeSubDir.getName(1).toString().equals(artifact.getArtifactId());
                    if (!valid) {
                        String example = NativeImage.nativeImagePropertiesMetaInf + "/${groupId}/${artifactId}/" + NativeImage.nativeImagePropertiesFilename;
                        getLog().warn(nativeImageProperty.toUri() + " does not match recommended " + example + " layout.");
                    }
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Artifact " + artifact + "cannot be added to image classpath", e);
        }

        classpath.add(jarFilePath);
    }

    private static Path getJavaHome() {
        return Paths.get(System.getProperty("java.home"));
    }

    private Path getWorkingDirectory() {
        return outputDirectory.toPath();
    }

    private boolean consumeConfigurationNodeValue(Consumer<String> consumer, String pluginKey, String... nodeNames) {
        Plugin selectedPlugin = project.getPlugin(pluginKey);
        if (selectedPlugin == null) {
            return false;
        }
        return consumeConfigurationNodeValue(consumer, selectedPlugin, nodeNames);
    }

    private boolean consumeExecutionsNodeValue(Consumer<String> consumer, String pluginKey, String... nodeNames) {
        Plugin selectedPlugin = project.getPlugin(pluginKey);
        if (selectedPlugin == null) {
            return false;
        }
        for (PluginExecution execution : selectedPlugin.getExecutions()) {
            if (consumeConfigurationNodeValue(consumer, execution, nodeNames)) {
                return true;
            }
        }
        return false;
    }

    private boolean consumeConfigurationNodeValue(Consumer<String> consumer, ConfigurationContainer container, String... nodeNames) {
        if (container != null && container.getConfiguration() instanceof Xpp3Dom) {
            Xpp3Dom node = (Xpp3Dom) container.getConfiguration();
            for (String nodeName : nodeNames) {
                node = node.getChild(nodeName);
                if (node == null) {
                    return false;
                }
            }
            consumer.accept(node.getValue());
            return true;
        }
        return false;
    }

    private List<String> getBuildArgs() {
        List<String> list = new ArrayList<>();
        if (buildArgs != null && !buildArgs.isEmpty()) {
            list.addAll(Arrays.asList(buildArgs.split(" ")));
        }
        if (mainClass != null && !mainClass.isEmpty()) {
            list.add(mainClass);
        } else {
            boolean consumed = false;
            if (!consumed) {
                consumed = consumeExecutionsNodeValue(list::add, "org.apache.maven.plugins:maven-shade-plugin", "transformers", "transformer", "mainClass");
            }
            if (!consumed) {
                consumed = consumeConfigurationNodeValue(list::add, "org.apache.maven.plugins:maven-assembly-plugin", "archive", "manifest", "mainClass");
            }
            if (!consumed) {
                consumed = consumeConfigurationNodeValue(list::add, "org.apache.maven.plugins:maven-jar-plugin", "archive", "manifest", "mainClass");
            }
            if (!consumed && !list.contains("--shared")) {
                getLog().warn("Building executable native image requires mainClass to be specified (via maven-{assembly,jar}-plugin) or explicitly");
            }
        }
        return list;
    }

    private Path getUntarDestDirectory() {
        return Paths.get(project.getBuild().getDirectory()).resolve(plugin.getArtifactId()).resolve(plugin.getVersion());
    }

    private final class MojoBuildConfiguration implements NativeImage.BuildConfiguration {
        private final List<Path> jvmciJars;

        MojoBuildConfiguration() throws MojoExecutionException {
            try {
                jvmciJars = Files.list(getJavaHome().resolve("lib/jvmci")).collect(Collectors.toList());
            } catch (IOException e) {
                throw new MojoExecutionException("JVM in " + getJavaHome() + " does not support JVMCI interface", e);
            }
        }

        @Override
        public Path getWorkingDirectory() {
            return NativeImageMojo.this.getWorkingDirectory();
        }

        @Override
        public Path getJavaExecutable() {
            return getJavaHome().resolve("bin/java");
        }

        private List<Path> getSelectedArtifactPaths(String groupId, String... artifactIds) {
            return getSelectedArtifactsStream(groupId, artifactIds)
                            .map(Artifact::getFile)
                            .map(File::toPath)
                            .collect(Collectors.toList());
        }

        @Override
        public List<Path> getBuilderClasspath() {
            return getSelectedArtifactPaths(svmGroupId, "svm", "objectfile", "pointsto");
        }

        @Override
        public List<Path> getBuilderCLibrariesPaths() {
            return Collections.singletonList(getUntarDestDirectory());
        }

        @Override
        public Path getBuilderInspectServerPath() {
            return null;
        }

        @Override
        public List<Path> getImageProvidedClasspath() {
            return getSelectedArtifactPaths(svmGroupId, "library-support");
        }

        @Override
        public List<Path> getBuilderJVMCIClasspath() {
            List<Path> paths = new ArrayList<>();
            paths.addAll(jvmciJars);
            paths.addAll(getBuilderJVMCIClasspathAppend());
            return paths;
        }

        @Override
        public List<Path> getBuilderJVMCIClasspathAppend() {
            return getSelectedArtifactPaths("org.graalvm.compiler", "compiler");
        }

        @Override
        public List<Path> getBuilderBootClasspath() {
            return getSelectedArtifactPaths("org.graalvm.sdk", "graal-sdk");
        }

        @Override
        public List<String> getBuilderJavaArgs() {
            return Collections.emptyList();
        }

        @Override
        public List<Path> getImageClasspath() {
            return classpath;
        }

        @Override
        public List<String> getBuildArgs() {
            return NativeImageMojo.this.getBuildArgs();
        }

        /* Launcher usage currently unsupported in maven plugin */

        @Override
        public List<Path> getLauncherCommonClasspath() {
            return Collections.emptyList();
        }

        @Override
        public String getLauncherClasspathPropertyValue(LinkedHashSet<Path> imageClasspath) {
            return null;
        }

        @Override
        public Stream<Path> getAbsoluteLauncherClassPath(Stream<String> relativeLauncherClassPath) {
            return Stream.empty();
        }
    }
}
