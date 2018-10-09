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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
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

    private TarGZipUnArchiver tarGzExtract = new TarGZipUnArchiver();

    NativeImageMojo() {
        tarGzExtract.enableLogging(tarGzLogger);
    }

    public void execute() throws MojoExecutionException {
        File untarDestDirectory = getUntarDestDirectory();
        if (!untarDestDirectory.exists()) {
            untarDestDirectory.mkdirs();
            plugin.getArtifacts().stream()
                            .filter(artifact -> artifact.getGroupId().equals("com.oracle.substratevm"))
                            .filter(artifact -> artifact.getArtifactId().startsWith("svm-hosted-native"))
                            .forEach(artifact -> {
                                if (artifact.getType().equals("tar.gz")) {
                                    tarGzExtract.setSourceFile(artifact.getFile());
                                    tarGzExtract.setDestDirectory(untarDestDirectory);
                                    tarGzExtract.extract();
                                }
                            });
        }

        try {
            MojoBuildConfiguration config = new MojoBuildConfiguration();
            getLog().info("WorkingDirectory: " + config.getWorkingDirectory());
            getLog().info("ImageClasspath: " + config.getImageClasspath());
            getLog().info("BuildArgs: " + config.getBuildArgs());
            NativeImage.build(config);
        } catch (NativeImage.NativeImageError e) {
            throw new MojoExecutionException("Error creating native image:", e);
        }
    }

    private File getUntarDestDirectory() {
        return Paths.get(project.getBuild().getDirectory()).resolve(plugin.getArtifactId()).resolve(plugin.getVersion()).toFile();
    }

    private final class MojoBuildConfiguration implements NativeImage.BuildConfiguration {
        private final List<String> classpath = new ArrayList<>();
        private final List<Path> jvmciJars;

        MojoBuildConfiguration() throws MojoExecutionException {
            List<String> imageClasspathScopes = Arrays.asList(Artifact.SCOPE_COMPILE, Artifact.SCOPE_RUNTIME);
            project.setArtifactFilter(artifact -> imageClasspathScopes.contains(artifact.getScope()));
            for (Artifact dependency : project.getArtifacts()) {
                getLog().info("Dependency: " + dependency.getGroupId() + ":" + dependency.getArtifactId() + " path: " + dependency.getFile());
                classpath.add(dependency.getFile().toString());
            }
            Artifact artifact = project.getArtifact();
            getLog().info("Artifact: " + artifact.getGroupId() + ":" + artifact.getArtifactId() + " path: " + artifact.getFile());
            classpath.add(artifact.getFile().toString());

            try {
                Path jvmciSubdir = Paths.get("lib", "jvmci");
                jvmciJars = Files.list(Paths.get(System.getProperty("java.home")).resolve(jvmciSubdir))
                                .collect(Collectors.toList());
            } catch (IOException e) {
                throw new MojoExecutionException("JVM does not support JVMCI interface", e);
            }
        }

        @Override
        public Path getWorkingDirectory() {
            return outputDirectory.toPath();
        }

        @Override
        public Path getJavaExecutable() {
            return Paths.get(System.getProperty("java.home")).resolve(Paths.get("bin", "java"));
        }

        @Override
        public List<Path> getBuilderClasspath() {
            String[] builderArtifacts = {"svm", "svm-enterprise", "objectfile", "pointsto"};
            return plugin.getArtifacts().stream()
                            .filter(artifact -> artifact.getGroupId().equals("com.oracle.substratevm"))
                            .filter(artifact -> Arrays.asList(builderArtifacts).contains(artifact.getArtifactId()))
                            .map(Artifact::getFile)
                            .map(File::toPath)
                            .collect(Collectors.toList());
        }

        @Override
        public List<Path> getBuilderCLibrariesPaths() {
            return Collections.singletonList(getUntarDestDirectory().toPath());
        }

        @Override
        public Path getBuilderInspectServerPath() {
            return null;
        }

        @Override
        public List<Path> getImageProvidedClasspath() {
            String[] librarySupportArtifacts = {"library-support", "library-support-enterprise"};
            return plugin.getArtifacts().stream()
                            .filter(artifact -> artifact.getGroupId().equals("com.oracle.substratevm"))
                            .filter(artifact -> Arrays.asList(librarySupportArtifacts).contains(artifact.getArtifactId()))
                            .map(Artifact::getFile)
                            .map(File::toPath)
                            .collect(Collectors.toList());
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
            List<Path> paths = new ArrayList<>();
            paths.addAll(plugin.getArtifacts().stream()
                    .filter(artifact -> artifact.getGroupId().equals("org.graalvm.compiler"))
                    .filter(artifact -> artifact.getArtifactId().equals("compiler"))
                    .map(Artifact::getFile)
                    .map(File::toPath)
                    .collect(Collectors.toList()));
            paths.addAll(plugin.getArtifacts().stream()
                    .filter(artifact -> artifact.getGroupId().equals("com.oracle.compiler"))
                    .filter(artifact -> artifact.getArtifactId().equals("graal-enterprise"))
                    .map(Artifact::getFile)
                    .map(File::toPath)
                    .collect(Collectors.toList()));
            return paths;
        }

        @Override
        public List<Path> getBuilderBootClasspath() {
            return plugin.getArtifacts().stream()
                            .filter(artifact -> artifact.getGroupId().equals("org.graalvm.sdk"))
                            .filter(artifact -> artifact.getArtifactId().equals("graal-sdk"))
                            .map(Artifact::getFile)
                            .map(File::toPath)
                            .collect(Collectors.toList());
        }

        @Override
        public List<String> getBuilderJavaArgs() {
            return Collections.emptyList();
        }

        @Override
        public List<Path> getImageClasspath() {
            return classpath.stream().map(Paths::get).collect(Collectors.toList());
        }

        private boolean consumeConfigurationNodeValue(Consumer<String> consumer, Plugin plugin, String... nodeNames) {
            if (plugin != null && plugin.getConfiguration() instanceof Xpp3Dom) {
                Xpp3Dom node = (Xpp3Dom) plugin.getConfiguration();
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

        @Override
        public List<String> getBuildArgs() {
            List<String> list = new ArrayList<>();
            if (buildArgs != null && !buildArgs.isEmpty()) {
                list.addAll(Arrays.asList(buildArgs.split(" ")));
            }
            if (mainClass != null && !mainClass.isEmpty()) {
                list.add(mainClass);
            } else {
                boolean consumed = false;
                if (!consumed) {
                    consumed = consumeConfigurationNodeValue(list::add, project.getPlugin("org.apache.maven.plugins:maven-assembly-plugin"), "archive", "manifest", "mainClass");
                }
                if (!consumed) {
                    consumed = consumeConfigurationNodeValue(list::add, project.getPlugin("org.apache.maven.plugins:maven-jar-plugin"), "archive", "manifest", "mainClass");
                }
                if (!consumed && !list.contains("--shared")) {
                    getLog().warn("Building executable native image requires mainClass to be specified (via maven-{assembly,jar}-plugin) or explicitly");
                }
            }
            return list;
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
