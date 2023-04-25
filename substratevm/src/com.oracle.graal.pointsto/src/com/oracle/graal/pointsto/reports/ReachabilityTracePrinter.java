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

public final class ReachabilityTracePrinter {
    public static void printTraceForTypes(BigBang bb, String reportsPath, String imageName) {
        ReportUtils.report("trace for types", reportsPath, "trace_types_" + imageName, "txt",
                writer -> printTraceForTypesImpl(bb, writer));
    }

    public static void printTraceForMethods(BigBang bb, String reportsPath, String imageName) {
        ReportUtils.report("trace for methods", reportsPath, "trace_methods_" + imageName, "txt",
                        writer -> printTraceForMethodsImpl(bb, writer));
    }

    public static void printTraceForFields(BigBang bb, String reportsPath, String imageName) {
        ReportUtils.report("trace for fields", reportsPath, "trace_fields_" + imageName, "txt",
                writer -> printTraceForFieldsImpl(bb, writer));
    }

    private static void printTraceForTypesImpl(BigBang bb, PrintWriter writer) {

    }

    private static void printTraceForMethodsImpl(BigBang bb, PrintWriter writer) {

    }

    private static void printTraceForFieldsImpl(BigBang bb, PrintWriter writer) {

    }
}
