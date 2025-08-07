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
package com.oracle.svm.hosted.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;

import com.oracle.svm.core.VM;
import com.oracle.svm.hosted.FeatureHandler;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.ProgressReporter.ANSI;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;

@SuppressWarnings("try")
public final class VMErrorReporter {

    public static void generateErrorReport(PrintWriter pw, StringBuilder buildOutputLog, ImageClassLoader classLoader, Optional<FeatureHandler> featureHandler, Throwable t) {
        pw.println("# GraalVM Native Image Error Report");
        pw.println();
        reportBuildLog(pw, buildOutputLog);
        pw.println();
        reportStackTrace(pw, t);
        pw.println();
        reportGraalVMSetup(pw);
        pw.println();
        reportBuilderSetup(pw, classLoader, featureHandler);
    }

    private static void reportBuildLog(PrintWriter pw, StringBuilder buildOutputLog) {
        pw.println("## Build Output");
        pw.println();
        pw.println("```");
        pw.append(ANSI.strip(buildOutputLog.toString()));
        pw.println("```");
    }

    private static void reportStackTrace(PrintWriter pw, Throwable t) {
        pw.println("## Stack Trace");
        pw.println();
        pw.println("```java");
        t.printStackTrace(pw);
        pw.println("```");
    }

    private static void reportGraalVMSetup(PrintWriter pw) {
        pw.println("## GraalVM Setup");
        pw.println();
        pw.println("| Name | Value |");
        pw.println("| ---- | ----- |");
        pw.printf("| Java version | `%s` |%n", VM.getVersion());
        pw.printf("| Vendor version | `%s` |%n", VM.getVendorVersion());
        pw.printf("| Runtime version | `%s` |%n", System.getProperty("java.runtime.version"));
        if (ImageSingletonsSupport.isInstalled() && ImageSingletons.contains(CCompilerInvoker.class)) {
            pw.printf("| C compiler | `%s` |%n", ImageSingletons.lookup(CCompilerInvoker.class).compilerInfo.getShortDescription());
        }

        String releaseContent = getReleaseFileContent();
        if (releaseContent != null) {
            try (DetailsPrinter d = new DetailsPrinter(pw, "GraalVM <code>release</code> file")) {
                pw.println(releaseContent);
            }
        }
    }

    private static String getReleaseFileContent() {
        Path releaseFile = Path.of(System.getProperty("java.home")).resolve("release");
        if (Files.exists(releaseFile)) {
            try {
                return Files.readString(releaseFile);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    private static void reportBuilderSetup(PrintWriter pw, ImageClassLoader classLoader, Optional<FeatureHandler> featureHandler) {
        pw.println("## Builder Setup");
        pw.println();
        try (DetailsPrinter p = new DetailsPrinter(pw, "Class path")) {
            for (String entry : DiagnosticUtils.getClassPath(classLoader)) {
                pw.println(entry);
            }
        }
        pw.println();
        try (DetailsPrinter p = new DetailsPrinter(pw, "Module path")) {
            for (String entry : DiagnosticUtils.getModulePath(classLoader)) {
                pw.println(entry);
            }
        }
        pw.println();
        try (DetailsPrinter p = new DetailsPrinter(pw, "Builder arguments")) {
            for (String entry : DiagnosticUtils.getBuilderArguments(classLoader)) {
                pw.println(entry);
            }
        }
        pw.println();
        try (DetailsPrinter p = new DetailsPrinter(pw, "Builder properties")) {
            for (String entry : DiagnosticUtils.getBuilderProperties()) {
                pw.println(entry);
            }
        }
        pw.println();
        try (DetailsPrinter p = new DetailsPrinter(pw, "Features enabled")) {
            if (featureHandler.isPresent()) {
                featureHandler.get().dumpAllFeatures(pw);
            } else {
                pw.println("*FeatureHandler not present.*");
            }
        }
    }

    private static final class DetailsPrinter implements AutoCloseable {
        private final PrintWriter pw;

        private DetailsPrinter(PrintWriter pw, String title) {
            this.pw = pw;
            pw.printf("<details>%n<summary>%s</summary>%n%n```%n", title);
        }

        @Override
        public void close() {
            pw.printf("```%n%n</details>%n");
        }
    }
}
