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
package com.oracle.graal.pointsto.reports;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.function.Consumer;

import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ReportUtils {

    static final String CONNECTING_INDENT = "\u2502   "; // "| "
    static final String EMPTY_INDENT = "    ";
    static final String CHILD = "\u251c\u2500\u2500 "; // "|-- "
    static final String LAST_CHILD = "\u2514\u2500\u2500 "; // "`-- "

    public static final Comparator<ResolvedJavaMethod> methodComparator = Comparator.comparing(m -> m.format("%H.%n(%p)"));
    static final Comparator<AnalysisField> fieldComparator = Comparator.comparing(f -> f.format("%H.%n"));
    static final Comparator<InvokeTypeFlow> invokeComparator = Comparator.comparing(i -> i.getTargetMethod().format("%H.%n(%p)"));
    static final Comparator<BytecodePosition> positionMethodComparator = Comparator.comparing(pos -> pos.getMethod().format("%H.%n(%p)"));
    static final Comparator<BytecodePosition> positionComparator = positionMethodComparator.thenComparing(pos -> pos.getBCI());

    /**
     * Print a report in the format: path/name_timeStamp.extension. The path is relative to the
     * working directory.
     *
     * @param description the description of the report
     * @param path the path (relative to the working directory if the argument represents a relative
     *            path)
     * @param name the name of the report
     * @param extension the extension of the report
     * @param reporter a consumer that writes to a PrintWriter
     */
    public static void report(String description, String path, String name, String extension, Consumer<PrintWriter> reporter) {
        String fileName = timeStampedFileName(name, extension);
        Path reportDir = Paths.get(path);
        reportImpl(description, reportDir, fileName, reporter);
    }

    public static String timeStampedFileName(String name, String extension) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timeStamp = LocalDateTime.now().format(formatter);
        String fileName = name + "_" + timeStamp;
        return extension.isEmpty() ? fileName : fileName + "." + extension;
    }

    public static File reportFile(String path, String name, String extension) {
        try {
            String fileName = ReportUtils.timeStampedFileName(name, extension);
            Path reportDir = Files.createDirectories(Paths.get(path));
            Path filePath = reportDir.resolve(fileName);
            Files.deleteIfExists(filePath);
            return Files.createFile(filePath).toFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Print a report in the file given by {@code file} parameter. If the {@code file} is relative
     * it's resolved to the working directory.
     *
     * @param description the description of the report
     * @param file the path (relative to the working directory if the argument represents a relative
     *            path) to file to store a report into.
     * @param reporter a consumer that writes to a PrintWriter
     */
    public static void report(String description, Path file, Consumer<PrintWriter> reporter) {
        Path folder = file.getParent();
        Path fileName = file.getFileName();
        if (folder == null || fileName == null) {
            throw new IllegalArgumentException("File parameter must be a file, got: " + file);
        }
        reportImpl(description, folder, fileName.toString(), reporter);
    }

    private static void reportImpl(String description, Path folder, String fileName, Consumer<PrintWriter> reporter) {
        try {
            Path reportDir = Files.createDirectories(folder);
            Path file = reportDir.resolve(fileName);
            Files.deleteIfExists(file);

            try (FileWriter fw = new FileWriter(Files.createFile(file).toFile())) {
                try (PrintWriter writer = new PrintWriter(fw)) {
                    System.out.println("# Printing " + description + " to: " + file);
                    reporter.accept(writer);
                }
            }

        } catch (IOException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
    }

    /** Returns a path relative to the current working directory if possible. */
    public static Path getCWDRelativePath(Path path) {
        Path cwd = Paths.get("").toAbsolutePath();
        try {
            return cwd.relativize(path);
        } catch (IllegalArgumentException e) {
            /* Relativization failed (e.g., the `path` is not absolute or is on another drive). */
            return path;
        }
    }

    /*
     * Extract the actual image file name when the imageName is specified as
     * 'parent-directory/image-name'.
     */
    public static String extractImageName(String imageName) {
        return imageName.substring(imageName.lastIndexOf(File.separatorChar) + 1);
    }

    /**
     * Print a report in the file given by {@code file} parameter. If the {@code file} is relative
     * it's resolved to the working directory.
     *
     * @param description the description of the report
     * @param file the path (relative to the working directory if the argument represents a relative
     *            path) to file to store a report into.
     * @param reporter a consumer that writes to a FileOutputStream
     * @param append flag to append onto file
     */
    public static void report(String description, Path file, boolean append, Consumer<OutputStream> reporter) {
        Path folder = file.getParent();
        Path fileName = file.getFileName();
        if (folder == null || fileName == null) {
            throw new IllegalArgumentException("File parameter must be a file, got: " + file);
        }
        reportImpl(description, folder, fileName.toString(), reporter, append);
    }

    private static void reportImpl(String description, Path folder, String fileName, Consumer<OutputStream> reporter, boolean append) {
        try {
            Path reportDir = Files.createDirectories(folder);
            Path file = reportDir.resolve(fileName);
            try (OutputStream fos = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                            append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
                System.out.println("# Printing " + description + " to: " + file);
                reporter.accept(fos);
                fos.flush();
            }
        } catch (IOException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
    }

    public static String parsingContext(AnalysisMethod method) {
        return parsingContext(method, "   ");
    }

    public static String parsingContext(AnalysisMethod method, String indent) {
        StringBuilder msg = new StringBuilder();
        if (method.getTypeFlow().getParsingContext().length > 0) {
            for (StackTraceElement e : method.getTypeFlow().getParsingContext()) {
                msg.append(String.format("%n%sat %s", indent, e));
            }
            msg.append(String.format("%n"));
        } else {
            msg.append(String.format(" <no parsing context available> %n"));
        }
        return msg.toString();
    }
}
