/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.VMError;

/**
 * This class is used to generate fallback images in case we are unable to build standalone images.
 *
 * A fallback image is a trivial standalone image that delegates execution of the application that
 * should originally be built to calling the Java executable with the original image classpath and
 * mainClass. System-properties specified during the original image-build get passed to the Java
 * executable that the FallbackExecutor uses to run the application.
 *
 * Control gets transferred to the Java executable by using {code}ProcessProperties.exec(){code}.
 * This ensures that the fallback image behaves as if the original application was started as
 * regular Java application.
 */
public class FallbackExecutor {
    public static class Options {
        @Option(help = "Internal option used to specify system properties for FallbackExecutor.")//
        public static final HostedOptionKey<String[]> FallbackExecutorSystemProperty = new HostedOptionKey<>(null);
        @Option(help = "Internal option used to specify MainClass for FallbackExecutor.")//
        public static final HostedOptionKey<String> FallbackExecutorMainClass = new HostedOptionKey<>(null);
        @Option(help = "Internal option used to specify Classpath for FallbackExecutor.")//
        public static final HostedOptionKey<String> FallbackExecutorClasspath = new HostedOptionKey<>(null);
        @Option(help = "Internal option used to specify java arguments for FallbackExecutor.")//
        public static final HostedOptionKey<String[]> FallbackExecutorJavaArg = new HostedOptionKey<>(null);
        @Option(help = "Internal option used to specify runtime java arguments for FallbackExecutor.")//
        public static final RuntimeOptionKey<String[]> FallbackExecutorRuntimeJavaArg = new RuntimeOptionKey<>(new String[0]);
    }

    public static void main(String[] args) {
        List<String> command = new ArrayList<>();
        Path javaExecutable = getJavaExecutable().toAbsolutePath().normalize();
        command.add(javaExecutable.toString());
        String[] javaArgValues = Options.FallbackExecutorJavaArg.getValue();
        if (javaArgValues != null) {
            for (String arg : javaArgValues) {
                command.add(arg);
            }
        }
        String[] properties = Options.FallbackExecutorSystemProperty.getValue();
        if (properties != null) {
            for (String p : properties) {
                command.add(p);
            }
        }
        String[] runtimeArgs = Options.FallbackExecutorRuntimeJavaArg.getValue();
        if (runtimeArgs != null) {
            for (String arg : runtimeArgs) {
                command.addAll(Arrays.asList(arg.split("\\s+")));
            }
        }
        command.add("-D" + ImageInfo.PROPERTY_IMAGE_KIND_KEY + "=fallback-" + ImageInfo.PROPERTY_IMAGE_KIND_VALUE_EXECUTABLE);
        Path fallbackImageDir = Paths.get(ProcessProperties.getExecutableName()).getParent();
        if (fallbackImageDir == null) {
            VMError.shouldNotReachHere();
        }
        String pathPrefix = fallbackImageDir.toAbsolutePath().normalize().toString();
        String relativeClasspath = Options.FallbackExecutorClasspath.getValue();
        String[] split = SubstrateUtil.split(relativeClasspath, File.pathSeparator);
        for (int i = 0; i < split.length; i++) {
            split[i] = pathPrefix + File.separator + split[i];
        }
        String absoluteClasspath = String.join(File.pathSeparator, split);
        command.add("-cp");
        command.add(absoluteClasspath);
        command.add(Options.FallbackExecutorMainClass.getValue());
        command.addAll(Arrays.asList(args));
        if (System.getenv("FALLBACK_EXECUTOR_VERBOSE") != null) {
            // Checkstyle: stop
            System.out.println("Exec: " + String.join(" ", command));
            // Checkstyle: resume
        }
        ProcessProperties.exec(javaExecutable, command.toArray(new String[0]));
    }

    private static final Path buildTimeJavaHome = Paths.get(System.getProperty("java.home"));

    private static Path getJavaExecutable() {
        Path binJava = Paths.get("bin", OS.getCurrent() == OS.WINDOWS ? "java.exe" : "java");

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
            showError("No " + binJava + " and no environment variable JAVA_HOME");
        }
        try {
            javaCandidate = Paths.get(javaHome).resolve(binJava);
            if (Files.isExecutable(javaCandidate)) {
                return javaCandidate;
            }
        } catch (InvalidPathException e) {
            /* fallthrough */
        }
        showError("No " + binJava + " and invalid JAVA_HOME=" + javaHome);
        return null;
    }

    private static void showError(String s) {
        // Checkstyle: stop
        System.err.println("Error: " + s);
        // Checkstyle: resume
        System.exit(1);
    }
}
