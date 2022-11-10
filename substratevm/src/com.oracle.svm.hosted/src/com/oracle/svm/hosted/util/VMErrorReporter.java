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

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.VM;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;

@SuppressWarnings("try")
public final class VMErrorReporter {

    public static void generateErrorReport(PrintWriter pw, ImageClassLoader classLoader, Throwable t) {
        pw.println("# GraalVM Native Image Error Report");
        reportStackTrace(t, pw);
        reportBuilderSetup(pw, classLoader);
        reportGraalVMSetup(pw);
    }

    private static void reportStackTrace(Throwable t, PrintWriter pw) {
        pw.println("## Stack Trace");
        pw.println("```java");
        t.printStackTrace(pw);
        pw.println("```");
    }

    private static void reportBuilderSetup(PrintWriter pw, ImageClassLoader classLoader) {
        pw.println("## Builder Setup");
        try (DetailsPrinter p = new DetailsPrinter(pw, "Class path")) {
            for (String entry : DiagnosticUtils.getClassPath(classLoader)) {
                pw.println(entry);
            }
        }
        try (DetailsPrinter p = new DetailsPrinter(pw, "Module path")) {
            for (String entry : DiagnosticUtils.getModulePath(classLoader)) {
                pw.println(entry);
            }
        }
        try (DetailsPrinter p = new DetailsPrinter(pw, "Builder arguments")) {
            for (String entry : DiagnosticUtils.getBuilderArguments(classLoader)) {
                pw.println(entry);
            }
        }
        try (DetailsPrinter p = new DetailsPrinter(pw, "Builder properties")) {
            for (String entry : DiagnosticUtils.getBuilderProperties()) {
                pw.println(entry);
            }
        }
    }

    private static void reportGraalVMSetup(PrintWriter pw) {
        pw.println("## GraalVM Setup");
        String version = ImageSingletons.lookup(VM.class).version;
        String javaVersion = System.getProperty("java.runtime.version");
        pw.println("| Name | Value |");
        pw.println("| ---- | ----- |");
        pw.printf("| GraalVM version | `%s` |%n", version);
        pw.printf("| Java version | `%s` |%n", javaVersion);
        if (ImageSingletons.contains(CCompilerInvoker.class)) {
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

    private static final class DetailsPrinter implements AutoCloseable {
        private final PrintWriter pw;

        private DetailsPrinter(PrintWriter pw, String title) {
            this.pw = pw;
            pw.printf("%n<details>%n<summary>%s</summary>%n%n```%n", title);
        }

        @Override
        public void close() {
            pw.printf("```%n%n</details>%n%n");
        }
    }
}
