/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.dashboard;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.shadowed.com.google.gson.Gson;
import com.oracle.shadowed.com.google.gson.GsonBuilder;
import com.oracle.shadowed.com.google.gson.JsonObject;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;

import java.io.File;

@AutomaticFeature
public class DashboardDumpFeature implements Feature {
    private static boolean isHeapBreakdownDumped() {
        return DashboardOptions.DashboardAll.getValue() || DashboardOptions.DashboardHeap.getValue();
    }

    private static boolean isPointsToDumped() {
        return DashboardOptions.DashboardAll.getValue() || DashboardOptions.DashboardPointsTo.getValue();
    }

    private static boolean isCodeBreakdownDumped() {
        return DashboardOptions.DashboardAll.getValue() || DashboardOptions.DashboardCode.getValue();
    }

    private static void dumpToFile(String dumpContent, String dumpPath) {
        final File file = new File(dumpPath).getAbsoluteFile();
        ReportUtils.report("Dashboard dump output", file.toPath(), writer -> writer.write(dumpContent));
    }

    private final JsonObject dumpRoot;

    public DashboardDumpFeature() {
        dumpRoot = new JsonObject();
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return DashboardOptions.DashboardDump.getValue() != null;
    }

    @Override
    public void onAnalysisExit(OnAnalysisExitAccess access) {
        if (isPointsToDumped()) {
            final PointsToDumper pointsToDumper = new PointsToDumper();
            final JsonObject pointsTo = pointsToDumper.dump(access);
            dumpRoot.add("points-to", pointsTo);
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        if (isCodeBreakdownDumped()) {
            final CodeBreakdownDumper codeBreakdownDumper = new CodeBreakdownDumper();
            final JsonObject breakdown = codeBreakdownDumper.dump(access);
            dumpRoot.add("code-breakdown", breakdown);
        }
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess access) {
        if (isHeapBreakdownDumped()) {
            final HeapBreakdownDumper heapBreakdownDumper = new HeapBreakdownDumper();
            final JsonObject breakdown = heapBreakdownDumper.dump(access);
            dumpRoot.add("heap-breakdown", breakdown);
        }
    }

    @Override
    public void cleanup() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        final String dump = gson.toJson(dumpRoot);
        dumpToFile(dump, DashboardOptions.DashboardDump.getValue());
    }
}
