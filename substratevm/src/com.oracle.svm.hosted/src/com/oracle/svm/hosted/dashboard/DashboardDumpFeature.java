/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.graalvm.graphio.GraphOutput;
import org.graalvm.graphio.GraphStructure;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.hosted.FeatureImpl.AfterCompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterHeapLayoutAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.OnAnalysisExitAccessImpl;

@AutomaticallyRegisteredFeature
public class DashboardDumpFeature implements InternalFeature {

    private static boolean isHeapBreakdownDumped() {
        return DashboardOptions.DashboardAll.getValue() || DashboardOptions.DashboardHeap.getValue();
    }

    private static boolean isPointsToDumped() {
        return DashboardOptions.DashboardAll.getValue() || DashboardOptions.DashboardPointsTo.getValue();
    }

    private static boolean isCodeBreakdownDumped() {
        return DashboardOptions.DashboardAll.getValue() || DashboardOptions.DashboardCode.getValue();
    }

    private static boolean isBgvFormat() {
        return DashboardOptions.DashboardBgv.getValue();
    }

    private static boolean isJsonFormat() {
        return DashboardOptions.DashboardJson.getValue() || isPretty();
    }

    private static boolean isPretty() {
        return DashboardOptions.DashboardPretty.getValue();
    }

    private final ToJson dumper;

    private static Path getFile(String extension) {
        String fileName = DashboardOptions.DashboardDump.getValue();
        if (fileName == null) {
            fileName = SubstrateOptions.Name.getValue(); // Use image name by default.
        }
        return new File(fileName + "." + extension).getAbsoluteFile().toPath();
    }

    public DashboardDumpFeature() {
        if (isSane()) {
            if (isJsonFormat()) {
                this.dumper = new ToJson(isPretty());
                ReportUtils.report("Dashboard JSON dump header", getFile("dump"), false, os -> {
                    try (PrintWriter pw = new PrintWriter(os)) {
                        DashboardDumpFeature.this.dumper.printHeader(pw);
                    }
                });
            } else {
                this.dumper = null;
            }
            if (isBgvFormat()) {
                ReportUtils.report("Dashboard BGV dump header", getFile("bgv"), false, os -> {
                    try {
                        GraphOutput.newBuilder(VoidGraphStructure.INSTANCE).build(Channels.newChannel(os)).close();
                    } catch (IOException ex) {
                        Logger.getLogger(DashboardDumpFeature.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            }
        } else {
            this.dumper = null;
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return isSane();
    }

    private static boolean isSane() {
        return isHeapBreakdownDumped() || isPointsToDumped() || isCodeBreakdownDumped();
    }

    @Override
    public void onAnalysisExit(OnAnalysisExitAccess access) {
        if (isPointsToDumped()) {
            if (isJsonFormat()) {
                ReportUtils.report(
                                "Dashboard PointsTo analysis JSON dump",
                                getFile("dump"),
                                true,
                                os -> {
                                    try (PrintWriter pw = new PrintWriter(os)) {
                                        dumper.put(pw, "points-to", new PointsToJsonObject(access));
                                    }
                                });
            }
            if (isBgvFormat()) {
                ReportUtils.report(
                                "Dashboard PointsTo analysis BGV dump",
                                getFile("bgv"),
                                true,
                                os -> {
                                    try (GraphOutput<?, ?> out = GraphOutput.newBuilder(VoidGraphStructure.INSTANCE).embedded(true).build(Channels.newChannel(os))) {
                                        out.beginGroup(null, "points-to", null, null, 0, Collections.emptyMap());
                                        new PointsToJsonObject(access).dump(out);
                                        out.endGroup();
                                    } catch (IOException ex) {
                                        ((OnAnalysisExitAccessImpl) access).getDebugContext().log("Dump of PointsTo analysis failed with: %s", ex);
                                    }
                                });
            }
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        if (isCodeBreakdownDumped() || isPointsToDumped()) {
            CodeBreakdownJsonObject dump = new CodeBreakdownJsonObject(access);
            if (isJsonFormat()) {
                ReportUtils.report(
                                "Dashboard Code-Breakdown JSON dump",
                                getFile("dump"),
                                true,
                                os -> {
                                    try (PrintWriter pw = new PrintWriter(os)) {
                                        dumper.put(pw, "code-breakdown", dump);
                                    }
                                });
            }
            if (isBgvFormat()) {
                ReportUtils.report(
                                "Dashboard Code-Breakdown BGV dump",
                                getFile("bgv"),
                                true,
                                os -> {
                                    try (GraphOutput<?, ?> out = GraphOutput.newBuilder(VoidGraphStructure.INSTANCE).embedded(true).build(Channels.newChannel(os))) {
                                        dump.build();
                                        out.beginGroup(null, "code-breakdown", null, null, 0, dump.getData());
                                        out.endGroup();
                                    } catch (IOException ex) {
                                        ((AfterCompilationAccessImpl) access).getDebugContext().log("Dump of Code-Breakdown failed with: %s", ex);
                                    }
                                });
            }
        }
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess access) {
        if (isHeapBreakdownDumped()) {
            HeapBreakdownJsonObject dump = new HeapBreakdownJsonObject(access);
            if (isJsonFormat()) {
                ReportUtils.report(
                                "Dashboard Heap-Breakdown JSON dump",
                                getFile("dump"),
                                true,
                                os -> {
                                    try (PrintWriter pw = new PrintWriter(os)) {
                                        dumper.put(pw, "heap-breakdown", dump);
                                    }
                                });
            }
            if (isBgvFormat()) {
                ReportUtils.report(
                                "Dashboard Heap-Breakdown BGV dump",
                                getFile("bgv"),
                                true,
                                os -> {
                                    try (GraphOutput<?, ?> out = GraphOutput.newBuilder(VoidGraphStructure.INSTANCE).embedded(true).build(Channels.newChannel(os))) {
                                        dump.build();
                                        out.beginGroup(null, "heap-breakdown", null, null, 0, dump.getData());
                                        out.endGroup();
                                    } catch (IOException ex) {
                                        ((AfterHeapLayoutAccessImpl) access).getDebugContext().log("Dump of Heap-Breakdown failed with: %s", ex);
                                    }
                                });
            }
        }
    }

    @Override
    public void cleanup() {
        if (isJsonFormat()) {
            ReportUtils.report(
                            "Dashboard JSON dump end",
                            getFile("dump"),
                            true,
                            os -> {
                                try (PrintWriter pw = new PrintWriter(os, true)) {
                                    dumper.close(pw);
                                }
                            });
        }
        System.out.println("Print of Dashboard dump output ended.");
    }

    public static final class VoidGraphStructure implements GraphStructure<Void, Void, Void, Void> {

        public static final GraphStructure<Void, Void, Void, Void> INSTANCE = new VoidGraphStructure();

        private VoidGraphStructure() {
        }

        @Override
        public Void graph(Void currentGraph, Object obj) {
            return null;
        }

        @Override
        public Iterable<? extends Void> nodes(Void graph) {
            return Collections.emptyList();
        }

        @Override
        public int nodesCount(Void graph) {
            return 0;
        }

        @Override
        public int nodeId(Void node) {
            return 0;
        }

        @Override
        public boolean nodeHasPredecessor(Void node) {
            return false;
        }

        @Override
        public void nodeProperties(Void graph, Void node, Map<String, ? super Object> properties) {
        }

        @Override
        public Void node(Object obj) {
            return null;
        }

        @Override
        public Void nodeClass(Object obj) {
            return null;
        }

        @Override
        public Void classForNode(Void node) {
            return null;
        }

        @Override
        public String nameTemplate(Void nodeClass) {
            return null;
        }

        @Override
        public Object nodeClassType(Void nodeClass) {
            return null;
        }

        @Override
        public Void portInputs(Void nodeClass) {
            return null;
        }

        @Override
        public Void portOutputs(Void nodeClass) {
            return null;
        }

        @Override
        public int portSize(Void port) {
            return 0;
        }

        @Override
        public boolean edgeDirect(Void port, int index) {
            return false;
        }

        @Override
        public String edgeName(Void port, int index) {
            return null;
        }

        @Override
        public Object edgeType(Void port, int index) {
            return null;
        }

        @Override
        public Collection<? extends Void> edgeNodes(Void graph, Void node, Void port, int index) {
            return null;
        }
    }
}
