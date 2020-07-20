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

import com.oracle.svm.core.annotate.AutomaticFeature;
import java.io.File;
import org.graalvm.nativeimage.hosted.Feature;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private final ToJson dumper;

    private static ToJson prepareDumper() {
        try {
            Path file = new File(DashboardOptions.DashboardDump.getValue()).getAbsoluteFile().toPath();
            Path folder = file.getParent();
            Path fileName = file.getFileName();
            if (folder == null || fileName == null) {
                throw new IllegalArgumentException("File parameter must be a file, got: " + file);
            }
            Files.createDirectories(folder);
            Files.deleteIfExists(file);
            Files.createFile(file);
            System.out.println("Printing Dashboard dump output to " + file);
            return new ToJson(new PrintWriter(new FileWriter(file.toFile())), DashboardOptions.DashboardPretty.getValue());
        } catch (Exception e) {
            System.out.println("Dashboard Dumper initialization failed with: " + e);
            return null;
        }
    }

    public DashboardDumpFeature() {
        if (isSane()) {
            dumper = prepareDumper();
        } else {
            dumper = null;
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return isSane();
    }

    private static boolean isSane() {
        return DashboardOptions.DashboardDump.getValue() != null && (isHeapBreakdownDumped() || isPointsToDumped() || isCodeBreakdownDumped());
    }

    @Override
    public void onAnalysisExit(OnAnalysisExitAccess access) {
        if (dumper != null && isPointsToDumped()) {
            dumper.put("points-to", new PointsToJsonObject(access));
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        if (dumper != null && isCodeBreakdownDumped()) {
            dumper.put("code-breakdown", new CodeBreakdownJsonObject(access));
        }
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess access) {
        if (dumper != null && isHeapBreakdownDumped()) {
            dumper.put("heap-breakdown", new HeapBreakdownJsonObject(access));
        }
    }

    @Override
    public void cleanup() {
        if (dumper != null) {
            try {
                System.out.println("Print of Dashboard dump output ended.");
                dumper.close();
            } catch (Exception ex) {
                Logger.getLogger(DashboardDumpFeature.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
