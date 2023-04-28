/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisElement;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import org.graalvm.compiler.debug.MethodFilter;

public final class ReachabilityTracePrinter {
    public static void printTraceForTypes(String typesTraceOpt, BigBang bb, String reportsPath, String imageName) {
        ReportUtils.report("trace for types", reportsPath, "trace_types_" + imageName, "txt",
                        writer -> printTraceForTypesImpl(typesTraceOpt, bb, writer));
    }

    public static void printTraceForMethods(String methodsTraceOpt, BigBang bb, String reportsPath, String imageName) {
        ReportUtils.report("trace for methods", reportsPath, "trace_methods_" + imageName, "txt",
                        writer -> printTraceForMethodsImpl(methodsTraceOpt, bb, writer));
    }

    public static void printTraceForFields(String fieldsTraceOpt, BigBang bb, String reportsPath, String imageName) {
        ReportUtils.report("trace for fields", reportsPath, "trace_fields_" + imageName, "txt",
                        writer -> printTraceForFieldsImpl(fieldsTraceOpt, bb, writer));
    }

    private static void printTraceForTypesImpl(String typesTraceOpt, BigBang bb, PrintWriter writer) {
        String[] patterns = typesTraceOpt.split(",");
        ObjectTreePrinter.SimpleMatcher matcher = new ObjectTreePrinter.SimpleMatcher(patterns);
        for (AnalysisType type : bb.getUniverse().getTypes()) {
            if (!matcher.matches(type.toJavaName(true))) {
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
            } else if (type.isReachable()) {
                String header = "Type " + type.toJavaName() + " is marked as reachable";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, type.getReachableReason(), header);
                writer.println(trace);
            }

            // print the first trace to avoid overwhelming users with information
            break;
        }
    }

    private static void printTraceForMethodsImpl(String methodsTraceOpt, BigBang bb, PrintWriter writer) {
        MethodFilter matcher = MethodFilter.parse(methodsTraceOpt);
        for (AnalysisMethod method : bb.getUniverse().getMethods()) {
            if (!matcher.matches(method)) {
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

            // print the first trace to avoid overwhelming users with information
            break;
        }
    }

    private static void printTraceForFieldsImpl(String fieldsTraceOpt, BigBang bb, PrintWriter writer) {
        String[] patterns = fieldsTraceOpt.split(",");
        ObjectTreePrinter.SimpleMatcher matcher = new ObjectTreePrinter.SimpleMatcher(patterns);
        for (AnalysisField field : bb.getUniverse().getFields()) {
            if (!matcher.matches(field.getWrapped().format("%H.%n"))) {
                continue;
            }

            if (field.isWritten()) {
                String header = "Field " + field.getName() + " is written";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, field.getWrittenReason(), header);
                writer.println(trace);
            }

            if (field.isRead()) {
                String header = "Field " + field.getName() + " is read";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, field.getReadReason(), header);
                writer.println(trace);
            }

            if (field.isAccessed()) {
                String header = "Field " + field.getName() + " is accessed unsafely";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, field.getAccessedReason(), header);
                writer.println(trace);
            }

            if (field.isFolded()) {
                String header = "Field " + field.getName() + " is folded";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, field.getFoldedReason(), header);
                writer.println(trace);
            }

            // print the first trace to avoid overwhelming users with information
            break;
        }
    }
}
