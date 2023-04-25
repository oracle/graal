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
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.util.ReflectionUtil;

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
        String[] classNames = typesTraceOpt.split(",");
        AnalysisMetaAccess metaAccess = bb.getMetaAccess();
        for (String className : classNames) {
            Class<?> clazz = ReflectionUtil.lookupClass(false, className);
            AnalysisType type = metaAccess.lookupJavaType(clazz);

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
        }
    }

    private static void printTraceForMethodsImpl(String methodsTraceOpt, BigBang bb, PrintWriter writer) {

    }

    private static void printTraceForFieldsImpl(String fieldsTraceOpt, BigBang bb, PrintWriter writer) {

    }
}
