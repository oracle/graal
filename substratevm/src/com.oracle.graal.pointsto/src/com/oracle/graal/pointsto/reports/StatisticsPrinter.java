/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.io.PrintWriter;
import java.util.Map;
import java.util.function.Consumer;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.InstanceOfTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

public final class StatisticsPrinter {

    public static void print(BigBang bb, String reportsPath, String reportName) {
        StatisticsPrinter printer = new StatisticsPrinter(bb);
        Consumer<PrintWriter> consumer = printer::printStats;
        String description = "analysis results stats";
        if (AnalysisReportsOptions.AnalysisStatisticsFile.hasBeenSet(bb.getOptions())) {
            final File file = new File(AnalysisReportsOptions.AnalysisStatisticsFile.getValue(bb.getOptions())).getAbsoluteFile();
            ReportUtils.report(description, file.toPath(), consumer);
        } else {
            ReportUtils.report(description, reportsPath, "analysis_stats_" + reportName, "json", consumer);
        }
    }

    private final BigBang bb;

    public StatisticsPrinter(BigBang bb) {
        this.bb = bb;
    }

    /** Print analysis statistics as JSON formatted String. */
    private void printStats(PrintWriter out) {

        int[] reachableTypes = getNumReachableTypes(bb);
        int[] reachableMethods = getNumReachableMethods(bb);
        int[] reachableFields = getNumReachableFields(bb);
        long[] typeChecksStats = getTypeCheckStats(bb);

        beginObject(out);

        print(out, "total_reachable_types", reachableTypes[0]);
        print(out, "app_reachable_types", reachableTypes[1]);
        print(out, "total_reachable_methods", reachableMethods[0]);
        print(out, "app_reachable_methods", reachableMethods[1]);
        print(out, "total_reachable_fields", reachableFields[0]);
        print(out, "app_reachable_fields", reachableFields[1]);
        print(out, "total_type_checks", typeChecksStats[0]);
        print(out, "total_removable_type_checks", typeChecksStats[1]);
        print(out, "app_type_checks", typeChecksStats[2]);
        print(out, "app_removable_type_checks", typeChecksStats[3]);

        bb.printTimerStatistics(out);

        endObject(out);
    }

    static final String INDENT = "   ";

    private static PrintWriter endObject(PrintWriter out) {
        return out.format("}%n");
    }

    private static PrintWriter beginObject(PrintWriter out) {
        return out.format("{%n");
    }

    public static void print(PrintWriter out, String key, long value) {
        out.format("%s\"%s\": %d,%n", INDENT, key, value);
    }

    public static void print(PrintWriter out, String key, double value) {
        out.format("%s\"%s\": %.2f,%n", INDENT, key, value);
    }

    public static void printLast(PrintWriter out, String key, long value) {
        out.format("%s\"%s\": %d%n", INDENT, key, value);
    }

    private static int[] getNumReachableTypes(BigBang bb) {
        int reachable = 0;
        int appReachable = 0;
        for (AnalysisType type : bb.getUniverse().getTypes()) {
            if (type.isInstantiated()) {
                reachable++;
                if (!isRuntimeLibraryType(type)) {
                    appReachable++;
                }
            }
        }
        return new int[]{reachable, appReachable};
    }

    private static int[] getNumReachableMethods(BigBang bb) {
        int reachable = 0;
        int appReachable = 0;
        for (AnalysisMethod method : bb.getUniverse().getMethods()) {
            if (method.isReachable()) {
                reachable++;
                if (!isRuntimeLibraryType(method.getDeclaringClass())) {
                    appReachable++;
                }
            }
        }
        return new int[]{reachable, appReachable};
    }

    private static int[] getNumReachableFields(BigBang bb) {
        int reachable = 0;
        int appReachable = 0;
        for (AnalysisField field : bb.getUniverse().getFields()) {
            if (field.isAccessed()) {
                reachable++;
                if (!isRuntimeLibraryType(field.getDeclaringClass())) {
                    appReachable++;
                }
            }
        }
        return new int[]{reachable, appReachable};
    }

    private static long[] getTypeCheckStats(BigBang bb) {
        if (!(bb instanceof PointsToAnalysis)) {
            /*- Type check stats are only available if points-to analysis is on. */
            return new long[4];
        }
        PointsToAnalysis pointsToAnalysis = (PointsToAnalysis) bb;
        long totalFilters = 0;
        long totalRemovableFilters = 0;
        long appTotalFilters = 0;
        long appTotalRemovableFilters = 0;

        for (AnalysisMethod method : pointsToAnalysis.getUniverse().getMethods()) {

            boolean runtimeMethod = isRuntimeLibraryType(method.getDeclaringClass());
            MethodTypeFlow methodFlow = method.getTypeFlow();
            MethodFlowsGraph originalFlows = methodFlow.getOriginalMethodFlows();

            for (Map.Entry<Object, InstanceOfTypeFlow> entry : originalFlows.getInstanceOfFlows()) {
                if (BytecodeLocation.isValidBci(entry.getKey())) {
                    totalFilters++;
                    InstanceOfTypeFlow originalInstanceOf = entry.getValue();

                    boolean isSaturated = methodFlow.isSaturated(pointsToAnalysis, originalInstanceOf);
                    TypeState instanceOfTypeState = methodFlow.foldTypeFlow(pointsToAnalysis, originalInstanceOf);
                    if (!isSaturated && instanceOfTypeState.typesCount() < 2) {
                        totalRemovableFilters++;
                    }
                    if (!runtimeMethod) {
                        appTotalFilters++;
                        if (!isSaturated && instanceOfTypeState.typesCount() < 2) {
                            appTotalRemovableFilters++;
                        }
                    }
                }
            }
        }

        return new long[]{totalFilters, totalRemovableFilters, appTotalFilters, appTotalRemovableFilters};
    }

    public static boolean isRuntimeLibraryType(AnalysisType type) {
        String name = type.getName();
        return name.startsWith("Ljava/") ||
                        name.startsWith("Ljavax/") ||
                        name.startsWith("Lsun/") ||
                        name.startsWith("Lcom/sun/") ||
                        name.startsWith("Lcom/oracle/") ||
                        name.startsWith("Lorg/graalvm/") ||
                        name.startsWith("Ljdk/");
    }

}
