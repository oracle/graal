/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.debug.CSVUtil;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;

public class NodeCounterPhase extends BasePhase<PhaseContext> {

    protected static HashMap<String, LinkedList<CompilationStatistics>> initialCompilationsStatistics;
    protected static HashMap<String, LinkedList<CompilationStatistics>> earlyCompilationsStatistics;
    protected static HashMap<String, LinkedList<CompilationStatistics>> lateCompilationsStatistics;

    protected static Set<String> initialNodeSet;
    protected static Set<String> earlyNodeSet;
    protected static Set<String> lateNodeSet;

    protected Stage stage;

    public NodeCounterPhase(Stage stage) {
        this.stage = stage;
    }

    public enum Stage {
        INIT, EARLY, LATE
    }

    protected Stage getStage() {
        return this.stage;
    }

    public static class Options {
        // @formatter:off
        @Option(help = "Counts the number of instances of each node class.", type = OptionType.Debug)
        public static final OptionKey<Boolean> NodeCounters = new OptionKey<>(false);
        // @formatter:on
    }

    public static void dump(OptionValues options) {
        if (NodeCounterPhase.Options.NodeCounters.getValue(options)) {
            if (initialCompilationsStatistics != null && initialNodeSet != null) {
                dumpCompilationsStatistics(initialCompilationsStatistics, initialNodeSet, "init");
            }
            if (earlyCompilationsStatistics != null && earlyNodeSet != null) {
                dumpCompilationsStatistics(earlyCompilationsStatistics, earlyNodeSet, "early");
            }
            if (lateCompilationsStatistics != null && lateNodeSet != null) {
                dumpCompilationsStatistics(lateCompilationsStatistics, lateNodeSet, "late");
            }
        }
    }

    protected static void dumpCompilationsStatistics(HashMap<String, LinkedList<CompilationStatistics>> compilationsStatistics,
                                                     Set<String> nodeSet,
                                                     String stage) {

        String fileName = stage + "_compilations_statistics.csv";
        File file = new File(fileName);

        TTY.println("Writing " + stage + " compilations statistics to " + file.getAbsolutePath());

        ArrayList<String> csvHeader = new ArrayList<>();
        csvHeader.add("Method_id");
        csvHeader.add("Compilation_count");
        csvHeader.addAll(nodeSet);


        try (PrintStream out = new PrintStream(file)) {
            String format = CSVUtil.buildFormatString("%s", csvHeader.size());
            CSVUtil.Escape.println(out, format, csvHeader.toArray());

            for (HashMap.Entry<String, LinkedList<CompilationStatistics>> entry: compilationsStatistics.entrySet()) {
                String methodId = entry.getKey();
                LinkedList<CompilationStatistics> compilationStats = entry.getValue();
                int compilationCount = 0;

                for (CompilationStatistics compilationStat : compilationStats) {
                    Object[] csvRow = new Object[csvHeader.size()];
                    csvRow[0] = methodId;
                    csvRow[1] = compilationCount;
                    int idx = 2;
                    for (String nodeName : nodeSet) {
                        if (compilationStat.getNodesCount().get(nodeName) != null) {
                            csvRow[idx] = compilationStat.getNodesCount().get(nodeName);
                        } else {
                            csvRow[idx] = 0;
                        }
                        idx++;
                    }
                    CSVUtil.Escape.println(out, format, csvRow);
                    compilationCount++;
                }
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {

        String methodName = "<unknownMethodName>";
        String declaringClass = "<unknownDeclaringClass;>";
        if (graph.method() != null) {
            methodName = graph.method().getName();
            declaringClass = graph.method().getDeclaringClass().getName();
        }

        String[] declaringClassSplitted = declaringClass.split(";");
        String methodId = declaringClassSplitted[0] + "." + methodName;


        CompilationStatistics compilationStats = new CompilationStatistics();
        compilationStats.visit(graph);

        synchronized (this) {

            switch (getStage()) {
                case INIT:
                    if (initialCompilationsStatistics == null) {
                        initialCompilationsStatistics = new HashMap<>();
                    }
                    if (initialNodeSet == null) {
                        initialNodeSet = new LinkedHashSet<>();
                    }
                    addCompilationStatistics(initialCompilationsStatistics, compilationStats, methodId);
                    initialNodeSet.addAll(compilationStats.getNodeSet());
                    break;

                case EARLY:
                    if (earlyCompilationsStatistics == null) {
                        earlyCompilationsStatistics = new HashMap<>();
                    }
                    if (earlyNodeSet == null) {
                        earlyNodeSet = new LinkedHashSet<>();
                    }
                    addCompilationStatistics(earlyCompilationsStatistics, compilationStats, methodId);
                    earlyNodeSet.addAll(compilationStats.getNodeSet());
                    break;

                case LATE:
                    if (lateCompilationsStatistics == null) {
                        lateCompilationsStatistics = new HashMap<>();
                    }
                    if (lateNodeSet == null) {
                        lateNodeSet = new LinkedHashSet<>();
                    }
                    addCompilationStatistics(lateCompilationsStatistics, compilationStats, methodId);
                    lateNodeSet.addAll(compilationStats.getNodeSet());
                    break;

                default:
                    break;
            }
        }
    }

    private void addCompilationStatistics(HashMap<String, LinkedList<CompilationStatistics>> compilationsStatistics,
                                          CompilationStatistics compilationStats,
                                          String methodId) {
        LinkedList<CompilationStatistics> compilationStatsList = compilationsStatistics.get(methodId);
        if (compilationStatsList != null) {
            compilationStatsList.add(compilationStats);
        } else {
            LinkedList<CompilationStatistics> compilationStatisticsList = new LinkedList<>();
            compilationStatisticsList.add(compilationStats);
            compilationsStatistics.put(methodId, compilationStatisticsList);
        }
    }

    private class CompilationStatistics {

        private HashMap<String, Integer> nodesCount;
        private HashSet<String> nodeSet;

        private CompilationStatistics() {
            this.nodesCount = new HashMap<>();
            this.nodeSet = new HashSet<>();
        }

        private void visit(StructuredGraph graph) {
            for (Node node : graph.getNodes()) {
                String nodeName = node.getNodeClass().getClazz().getSimpleName();

                nodeSet.add(nodeName);

                if (this.nodesCount.get(nodeName) != null) {
                    this.nodesCount.put(nodeName, this.nodesCount.get(nodeName) + 1);
                } else {
                    this.nodesCount.put(nodeName, 1);
                }
            }
        }

        public HashMap<String, Integer> getNodesCount() {
            return nodesCount;
        }

        public HashSet<String> getNodeSet() {
            return nodeSet;
        }
    }
}

