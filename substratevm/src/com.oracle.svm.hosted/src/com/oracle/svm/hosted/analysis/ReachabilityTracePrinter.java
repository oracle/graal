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
package com.oracle.svm.hosted.analysis;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionValues;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisElement;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.ObjectTreePrinter;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.SubstrateOptionsParser;

public final class ReachabilityTracePrinter {
    public static final String PATH_MESSAGE_PREFIX = "See the generated report for a complete reachability trace: ";

    public static class Options {
        @Option(help = "Print a trace and abort the build process if any type matching the specified pattern becomes reachable.")//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> AbortOnTypeReachable = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

        @Option(help = "Print a trace and abort the build process if any method matching the specified pattern becomes reachable.")//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> AbortOnMethodReachable = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

        @Option(help = "Print a trace and abort the build process if any field matching the specified pattern becomes reachable.")//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> AbortOnFieldReachable = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());
    }

    public static void report(String imageName, OptionValues options, String reportsPath, BigBang bb) {
        String baseImageName = ReportUtils.extractImageName(imageName);

        StringBuilder consoleMessageBuilder = new StringBuilder();

        List<String> typePatterns = ReachabilityTracePrinter.Options.AbortOnTypeReachable.getValue(options).values();
        if (!typePatterns.isEmpty()) {
            StringWriter stringWriter = new StringWriter();
            int count = ReachabilityTracePrinter.printTraceForTypesImpl(typePatterns, bb, new PrintWriter(stringWriter));
            if (count > 0) {
                String trace = stringWriter.toString();
                Path path = ReportUtils.report("trace for types", reportsPath, "trace_types_" + baseImageName, "txt",
                                writer -> writer.print(trace));
                String abortOnTypeReachableOption = SubstrateOptionsParser.commandArgument(Options.AbortOnTypeReachable, String.join(",", typePatterns));
                String message = "Image building is interrupted as the types specified via " + abortOnTypeReachableOption + " are reachable. " + PATH_MESSAGE_PREFIX + path;
                consoleMessageBuilder.append(message);
            }
        }

        List<String> methodPatterns = ReachabilityTracePrinter.Options.AbortOnMethodReachable.getValue(options).values();
        if (!methodPatterns.isEmpty()) {
            StringWriter stringWriter = new StringWriter();
            int count = ReachabilityTracePrinter.printTraceForMethodsImpl(methodPatterns, bb, new PrintWriter(stringWriter));
            if (count > 0) {
                String trace = stringWriter.toString();
                Path path = ReportUtils.report("trace for methods", reportsPath, "trace_methods_" + baseImageName, "txt",
                                writer -> writer.print(trace));
                String abortOnMethodReachableOption = SubstrateOptionsParser.commandArgument(Options.AbortOnMethodReachable, String.join(",", methodPatterns));
                String message = "Image building is interrupted as the methods specified via " + abortOnMethodReachableOption + " are reachable. " + PATH_MESSAGE_PREFIX + path;
                consoleMessageBuilder.append(message);
            }
        }

        List<String> fieldPatterns = ReachabilityTracePrinter.Options.AbortOnFieldReachable.getValue(options).values();
        if (!fieldPatterns.isEmpty()) {
            StringWriter stringWriter = new StringWriter();
            int count = ReachabilityTracePrinter.printTraceForFieldsImpl(fieldPatterns, bb, new PrintWriter(stringWriter));
            if (count > 0) {
                String trace = stringWriter.toString();
                Path path = ReportUtils.report("trace for fields", reportsPath, "trace_fields_" + baseImageName, "txt",
                                writer -> writer.print(trace));
                String abortOnFieldReachableOption = SubstrateOptionsParser.commandArgument(Options.AbortOnFieldReachable, String.join(",", fieldPatterns));
                String message = "Image building is interrupted as the fields specified via " + abortOnFieldReachableOption + " are reachable. " + PATH_MESSAGE_PREFIX + path;
                consoleMessageBuilder.append(message);
            }
        }

        if (!consoleMessageBuilder.isEmpty()) {
            throw AnalysisError.interruptAnalysis(consoleMessageBuilder.toString());
        }
    }

    private static int printTraceForTypesImpl(List<String> typePatterns, BigBang bb, PrintWriter writer) {
        ObjectTreePrinter.SimpleMatcher matcher = new ObjectTreePrinter.SimpleMatcher(typePatterns.toArray(new String[0]));
        int count = 0;
        for (AnalysisType type : bb.getUniverse().getTypes()) {
            if (!type.isReachable() || !matcher.matches(type.toJavaName(true))) {
                continue;
            }

            if (type.isAllocated()) {
                String header = "Type " + type.toJavaName() + " is marked as allocated";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, type.getAllocatedReason(), header);
                writer.println(trace);
            } else if (type.isInHeap()) {
                String header = "Type " + type.toJavaName() + " is marked as in-heap";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, type.getInHeapReason(), header);
                writer.println(trace);
            } else {
                String header = "Type " + type.toJavaName() + " is marked as reachable";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, type.getReachableReason(), header);
                writer.println(trace);
            }

            count++;
        }

        return count;
    }

    private static int printTraceForMethodsImpl(List<String> methodPatterns, BigBang bb, PrintWriter writer) {
        MethodFilter matcher = MethodFilter.parse(String.join(",", methodPatterns));
        int count = 0;
        for (AnalysisMethod method : bb.getUniverse().getMethods()) {
            if (!method.isReachable() || !matcher.matches(method)) {
                continue;
            }

            if (method.isIntrinsicMethod()) {
                String header = "Method " + method.format("%H.%n(%p)") + " is intrinsic";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, method.getIntrinsicMethodReason(), header);
                writer.println(trace);
            } else {
                String header = "Method " + method.format("%H.%n(%p)") + " is called";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, method.getParsingReason(), header);
                writer.println(trace);
            }

            count++;
        }

        return count;
    }

    private static int printTraceForFieldsImpl(List<String> fieldPatterns, BigBang bb, PrintWriter writer) {
        ObjectTreePrinter.SimpleMatcher matcher = new ObjectTreePrinter.SimpleMatcher(fieldPatterns.toArray(new String[0]));
        int count = 0;

        for (AnalysisField field : bb.getUniverse().getFields()) {
            if (!field.isReachable() || !matcher.matches(field.getWrapped().format("%H.%n"))) {
                continue;
            }

            if (field.isWritten()) {
                String header = "Field " + field.getName() + " is written";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, field.getWrittenReason(), header);
                writer.println(trace);
            } else if (field.isRead()) {
                String header = "Field " + field.getName() + " is read";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, field.getReadReason(), header);
                writer.println(trace);
            } else if (field.isAccessed()) {
                String header = "Field " + field.getName() + " is accessed unsafely";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, field.getAccessedReason(), header);
                writer.println(trace);
            } else {
                assert field.isFolded();
                String header = "Field " + field.getName() + " is folded";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, field.getFoldedReason(), header);
                writer.println(trace);
            }

            count++;
        }

        return count;
    }
}
