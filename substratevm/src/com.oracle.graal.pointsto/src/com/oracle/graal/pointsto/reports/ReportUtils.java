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
import java.util.HashSet;
import java.util.function.Consumer;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AllInstantiatedTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.InvokeInfo;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ReportUtils {

    public static final String CONNECTING_INDENT = "\u2502   "; // "| "
    public static final String EMPTY_INDENT = "    ";
    public static final String CHILD = "\u251c\u2500\u2500 "; // "|-- "
    public static final String LAST_CHILD = "\u2514\u2500\u2500 "; // "`-- "

    public static final Comparator<ResolvedJavaMethod> methodComparator = Comparator.comparing(m -> m.format("%H.%n(%P):%R"));
    static final Comparator<AnalysisField> fieldComparator = Comparator.comparing(f -> f.format("%H.%n"));
    static final Comparator<InvokeInfo> invokeInfoBCIComparator = Comparator.comparing(i -> i.getPosition().getBCI());
    static final Comparator<InvokeInfo> invokeInfoComparator = invokeInfoBCIComparator.thenComparing(i -> comparingMethodNames(i.getTargetMethod()));
    static final Comparator<BytecodePosition> positionMethodComparator = Comparator.comparing(pos -> pos.getMethod().format("%H.%n(%P):%R"));
    static final Comparator<BytecodePosition> positionComparator = positionMethodComparator.thenComparing(pos -> pos.getBCI());
    static final Comparator<Object> reasonComparator = (o1, o2) -> {
        if (o1 instanceof BytecodePosition p1 && o2 instanceof BytecodePosition p2) {
            return positionComparator.compare(p1, p2);
        }
        return o1.toString().compareTo(o2.toString());
    };

    /**
     *
     * Lambda function names are still not completely deterministic e.g. in name
     * Lambda$7ad16f47b695d909/0x00000007c0b4c630.accept(java.lang.Object):void hash part is not
     * deterministic yet. In order to avoid comparing based on that part, we need to eliminate hash
     * part from name of lambda function. To read more about Lambda names check GH issue
     * https://github.com/openjdk/jdk/pull/10024/.
     *
     */
    private static String comparingMethodNames(AnalysisMethod method) {
        String methodName = method.format("%H.%n(%P):%R");
        return methodName.contains("$$Lambda$") ? methodName.replaceAll("/[0-9a-fA-Fx]*\\.", ".") : methodName;
    }

    public static Path report(String description, String path, String name, String extension, Consumer<PrintWriter> reporter) {
        return report(description, path, name, extension, reporter, true);
    }

    public static Path report(String description, String path, String name, String extension, Consumer<PrintWriter> reporter, boolean enablePrint) {
        return report(description, path, name, extension, reporter, enablePrint, getTimeStampString());
    }

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
     * @param timeStamp from {@link #getTimeStampString}
     */
    public static Path report(String description, String path, String name, String extension, Consumer<PrintWriter> reporter, boolean enablePrint, String timeStamp) {
        String fileName = timeStampedFileName(name, extension, timeStamp);
        Path reportDir = Paths.get(path);
        return reportImpl(enablePrint, description, reportDir, fileName, reporter);
    }

    public static String timeStampedFileName(String name, String extension) {
        return timeStampedFileName(name, extension, getTimeStampString());
    }

    public static String timeStampedFileName(String name, String extension, String timeStamp) {
        String fileName = name + "_" + timeStamp;
        return extension.isEmpty() ? fileName : fileName + "." + extension;
    }

    public static String getTimeStampString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timeStamp = LocalDateTime.now().format(formatter);
        return timeStamp;
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

    public static void report(String description, Path file, Consumer<PrintWriter> reporter) {
        report(description, file, reporter, true);
    }

    /**
     * Print a report in the file given by {@code file} parameter. If the {@code file} is relative
     * it's resolved to the working directory.
     *
     * @param description the description of the report
     * @param file the path (relative to the working directory if the argument represents a relative
     *            path) to file to store a report into.
     * @param reporter a consumer that writes to a PrintWriter
     * @param enablePrint of a notice to stdout
     */
    public static Path report(String description, Path file, Consumer<PrintWriter> reporter, boolean enablePrint) {
        Path folder = file.getParent();
        Path fileName = file.getFileName();
        if (folder == null || fileName == null) {
            throw new IllegalArgumentException("File parameter must be a file, got: " + file);
        }
        return reportImpl(enablePrint, description, folder, fileName.toString(), reporter);
    }

    private static Path reportImpl(boolean enablePrint, String description, Path folder, String fileName, Consumer<PrintWriter> reporter) {
        try {
            Path reportDir = Files.createDirectories(folder);
            Path file = reportDir.resolve(fileName);
            Files.deleteIfExists(file);

            try (FileWriter fw = new FileWriter(Files.createFile(file).toFile())) {
                try (PrintWriter writer = new PrintWriter(fw)) {
                    if (enablePrint) {
                        System.out.println("# Printing " + description + " to: " + file);
                    }
                    reporter.accept(writer);
                }
            }
            return file;
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
        return parsingContext(method, 0, "   ", false);
    }

    public static String parsingContext(AnalysisMethod method, String indent) {
        return parsingContext(method, 0, indent, false);
    }

    public static String parsingContext(BytecodePosition context) {
        return parsingContext((AnalysisMethod) context.getMethod(), context.getBCI(), "   ", true);
    }

    public static String parsingContext(AnalysisMethod method, int bci, String indent, boolean includeTarget) {
        StringBuilder msg = new StringBuilder();
        StackTraceElement[] parsingContext = method.getParsingContext();
        if (parsingContext.length > 0) {
            if (includeTarget) {
                /* Include target method first. */
                msg.append(String.format("%n%sat %s", indent, method.asStackTraceElement(bci)));
            }
            formatParsingContext(parsingContext, indent, msg);
        } else {
            msg.append(String.format(" <no parsing context available> %n"));
        }
        return msg.toString();
    }

    public static void formatParsingContext(StackTraceElement[] parsingContext, String indent, StringBuilder msg) {
        /* Then add the parsing context. */
        for (int i = 0; i < parsingContext.length; i++) {
            StackTraceElement e = parsingContext[i];
            if (isStackTraceTruncationSentinel(e)) {
                msg.append(String.format("%n%s", e.getClassName()));
                assert i == parsingContext.length - 1 : parsingContext;
            } else {
                msg.append(String.format("%n%sat %s", indent, e));
            }
        }
        msg.append(String.format("%n"));
    }

    // Checkstyle: Allow raw info or warning printing - begin
    private static final String stackTraceTruncationSentinel = "Warning: Parsing context is truncated because its depth exceeds a reasonable limit for ";
    // Checkstyle: Allow raw info or warning printing - end

    private static boolean isStackTraceTruncationSentinel(StackTraceElement element) {
        return element.getClassName().startsWith(stackTraceTruncationSentinel);
    }

    public static StackTraceElement truncatedStackTraceSentinel(AnalysisMethod method) {
        return new StackTraceElement(stackTraceTruncationSentinel + method.format("%H.%n(%p)"), "", null, -1);
    }

    public static StackTraceElement rootMethodSentinel(String reason) {
        return new StackTraceElement(reason, "", null, -1);
    }

    public static String typePropagationTrace(PointsToAnalysis bb, TypeFlow<?> flow, AnalysisType type) {
        return typePropagationTrace(bb, flow, type, "   ");
    }

    public static String typePropagationTrace(PointsToAnalysis bb, TypeFlow<?> flow, AnalysisType type, String indent) {
        if (bb.trackTypeFlowInputs()) {
            StringBuilder msg = new StringBuilder(String.format("Propagation trace through type flows for type %s: %n", type.toJavaName()));
            followInput(flow, type, indent, new HashSet<>(), msg);
            return msg.toString();
        } else {
            return String.format("To print the propagation trace through type flows for type %s set the -H:+TrackInputFlows option. %n", type.toJavaName());
        }
    }

    private static void followInput(TypeFlow<?> flow, AnalysisType type, String indent, HashSet<TypeFlow<?>> seen, StringBuilder msg) {
        seen.add(flow);
        if (flow instanceof AllInstantiatedTypeFlow) {
            msg.append(String.format("AllInstantiated(%s)%n", flow.getDeclaredType().toJavaName(true)));
        } else {
            msg.append(String.format("%sat %s: %s%n", indent, flow.formatSource(), flow.format(false, false)));
            for (TypeFlow<?> input : flow.getInputs()) {
                if (!seen.contains(input) && input.getState().containsType(type)) {
                    followInput(input, type, indent, seen, msg);
                    break;
                }
            }
        }
    }
}
