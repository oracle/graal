/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;

/**
 * Analyzes and reports performance-critical aspects of a structured graph's intermediate
 * representation (IR).
 * <p>
 * This phase traverses all sections of the code, sorts and identifies the hottest regions of the IR
 * - typically loops or frequently executed blocks - and generates a report highlighting any known
 * performance-critical observations related to those regions (if any are present).
 * </p>
 * <p>
 * The resulting report can be used to guide further performance analysis or optimization efforts.
 * </p>
 *
 * The phase is written in an agnostic way so it can be applied at any point in time in the
 * compilation pipeline.
 */
public class ReportHotCodePhase<C> extends BasePhase<C> {

    public static class Options {
        //@formatter:off
        @Option(help = "Dumps the hottest code parts to the Ideal Graph Visualizer (IGV) for further analysis and visualization.", type = OptionType.Debug)
        public static final OptionKey<Boolean> ReportHotCodePartsToIGV = new OptionKey<>(false);
        @Option(help = "Specifies the debug level for dumping hottest code parts to the Ideal Graph Visualizer (IGV).", type = OptionType.Debug)
        public static final OptionKey<Integer> ReportHotCodeIGVLevel = new OptionKey<>(1);
        @Option(help = "Specifies the minimum relative frequency for reporting hot code regions.", type = OptionType.Debug)
        public static final OptionKey<Double> ReportHotCodeMinimalFrequencyToReport = new OptionKey<>(1D);
        @Option(help = "Enables printing of informational messages in hot code regions.", type = OptionType.Debug)
        public static final OptionKey<Boolean> ReportHotCodeInfos = new OptionKey<>(false);
        @Option(help = "Enables printing of warning messages about potential performance issues in hot code regions.", type = OptionType.Debug)
        public static final OptionKey<Boolean> ReportHotCodeWarnings = new OptionKey<>(true);
        //@formatter:on
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    enum LoopToStringMode {
        BLOCK,
        LOCAL_FREQUENCY,
        GLOBAL_FREQUENCY
    }

    enum BlockToStringMode {
        BEGIN_NODE,
        GLOBAL_FREQUENCY,
    }

    private static final String INFO_KEY = "[Hot Code Info] ";

    private static final String WARNING_KEY = "[Hot Code Warning] ";

    private static String blocksToString(List<HIRBlock> blocks, BlockToStringMode mode) {
        StringBuilder sb = new StringBuilder();
        switch (mode) {
            case BEGIN_NODE:
                sb.append("BeginNodeIDs=");
                break;
            case GLOBAL_FREQUENCY:
                sb.append("GlobalFrequencies=");
                break;
        }
        sb.append("[");
        for (int i = 0; i < blocks.size(); i++) {
            switch (mode) {
                case BEGIN_NODE:
                    sb.append(blocks.get(i).getBeginNode());
                    break;
                case GLOBAL_FREQUENCY:
                    sb.append(blocks.get(i).getRelativeFrequency());
                    break;
            }
            if (i != blocks.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static String loopBlocksToString(List<Loop> loops, LoopToStringMode mode) {
        StringBuilder sb = new StringBuilder();
        switch (mode) {
            case BLOCK:
                sb.append("Blocks=");
                break;
            case GLOBAL_FREQUENCY:
                sb.append("BasicBlockFrequencies=");
                break;
            case LOCAL_FREQUENCY:
                sb.append("LocalLoopFrequencies=");
                break;
        }
        sb.append("[");
        for (int i = 0; i < loops.size(); i++) {
            switch (mode) {
                case BLOCK:
                    sb.append(loops.get(i));
                    break;
                case GLOBAL_FREQUENCY:
                    sb.append(loops.get(i).getCFGLoop().getHeader().getRelativeFrequency());
                    break;
                case LOCAL_FREQUENCY:
                    sb.append(loops.get(i).localLoopFrequency());
                    break;
            }
            if (i != loops.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static <X> List<X> takeUntil(List<X> list, int length) {
        return list.subList(0, Math.min(length, list.size()));
    }

    /**
     * The number of hottest code regions (blocks or loops) to report.
     */
    private static final int REPORT_HOT_FIRST_N = 3;

    private static int getLengthCap(int len, int cap) {
        return Math.min(len, cap);
    }

    private static void info(OptionValues opt, String format, Object... args) {
        if (!Options.ReportHotCodeInfos.getValue(opt)) {
            return;
        }
        TTY.printf(INFO_KEY + format, args);
    }

    private static void warn(OptionValues opt, String format, Object... args) {
        if (!Options.ReportHotCodeWarnings.getValue(opt)) {
            return;
        }
        TTY.printf(WARNING_KEY + format, args);
    }

    @Override
    protected void run(StructuredGraph graph, C c) {
        if (!(c instanceof CoreProviders)) {
            // context is generic in base phase, but we can only run this phase if the context is
            // supported
            TTY.println("Aborting hot code reporting because context " + c + " is not supported...");
            return;
        }

        CoreProviders context = (CoreProviders) c;
        final OptionValues options = graph.getOptions();

        SchedulePhase.runWithoutContextOptimizations(graph, SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS, true);
        final StructuredGraph.ScheduleResult scheduleResult = graph.getLastSchedule();

        final double minimalReportFrequency = Options.ReportHotCodeMinimalFrequencyToReport.getValue(graph.getOptions());

        final LoopsData ld = context.getLoopsDataProvider().getLoopsData(scheduleResult.getCFG());
        final ControlFlowGraph cfg = ld.getCFG();
        final List<HIRBlock> hottestBlocks = new ArrayList<>();

        Collections.addAll(hottestBlocks, cfg.reversePostOrder());
        hottestBlocks.sort((x, y) -> Double.compare(y.getRelativeFrequency(), x.getRelativeFrequency()));

        ld.detectCountedLoops();
        final List<Loop> hottestLocalLoops = new ArrayList<>(ld.loops());
        hottestLocalLoops.sort((x, y) -> Double.compare(y.localLoopFrequency(), x.localLoopFrequency()));

        final List<Loop> hottestGlobalLoops = new ArrayList<>(ld.loops());
        hottestGlobalLoops.sort((x, y) -> Double.compare(y.getCFGLoop().getHeader().getRelativeFrequency(), x.getCFGLoop().getHeader().getRelativeFrequency()));

        final List<HIRBlock> hottestFirstBlocks = takeUntil(hottestBlocks, REPORT_HOT_FIRST_N);
        if (!hottestFirstBlocks.isEmpty()) {
            String hottestGlobalBlocksString = String.format("Hottest %s blocks are %s %s %s", getLengthCap(hottestFirstBlocks.size(), REPORT_HOT_FIRST_N), hottestFirstBlocks,
                            blocksToString(hottestFirstBlocks, BlockToStringMode.BEGIN_NODE),
                            blocksToString(hottestFirstBlocks, BlockToStringMode.GLOBAL_FREQUENCY));
            info(options, "%s%n", hottestGlobalBlocksString);
            if (Options.ReportHotCodePartsToIGV.getValue(graph.getOptions())) {
                graph.getDebug().dump(Options.ReportHotCodeIGVLevel.getValue(graph.getOptions()), graph, hottestGlobalBlocksString);
            }
        }

        final List<Loop> hottestFirstLocalLoops = takeUntil(hottestLocalLoops, REPORT_HOT_FIRST_N);
        if (!hottestFirstLocalLoops.isEmpty()) {
            String hottestLocalLoopString = String.format("Hottest %s local loops are %s %s", getLengthCap(hottestFirstLocalLoops.size(), REPORT_HOT_FIRST_N),
                            loopBlocksToString(hottestFirstLocalLoops, LoopToStringMode.BLOCK),
                            loopBlocksToString(hottestFirstLocalLoops, LoopToStringMode.LOCAL_FREQUENCY));
            info(options, "%s%n", hottestLocalLoopString);
            if (Options.ReportHotCodePartsToIGV.getValue(graph.getOptions())) {
                graph.getDebug().dump(Options.ReportHotCodeIGVLevel.getValue(graph.getOptions()), graph, hottestLocalLoopString);
            }
        }

        final List<Loop> hottestFirstGlobalLoops = takeUntil(hottestGlobalLoops, REPORT_HOT_FIRST_N);
        if (!hottestGlobalLoops.isEmpty()) {
            String hottestGlobalLoopString = String.format("Hottest %s global loops are %s %s", getLengthCap(hottestGlobalLoops.size(), REPORT_HOT_FIRST_N),
                            loopBlocksToString(hottestFirstGlobalLoops, LoopToStringMode.BLOCK),
                            loopBlocksToString(hottestFirstGlobalLoops, LoopToStringMode.GLOBAL_FREQUENCY));
            info(options, "%s%n", hottestGlobalLoopString);
            if (Options.ReportHotCodePartsToIGV.getValue(graph.getOptions())) {
                graph.getDebug().dump(Options.ReportHotCodeIGVLevel.getValue(graph.getOptions()), graph, hottestGlobalLoopString);
            }
        }

        for (Loop l : hottestGlobalLoops) {
            for (Node inside : l.inside().nodes()) {
                // ignore slow paths in loops
                HIRBlock insideBlock = scheduleResult.blockFor(inside);
                if (insideBlock != null && insideBlock.getRelativeFrequency() < minimalReportFrequency) {
                    continue;
                }
                reportUnknownProfile(l, inside, cfg);
                reportInvariantLoopIf(l, inside);
                reportMemoryKillANYInLoop(l, inside, cfg);
                reportHotLoopGuardsInside(inside, l);
            }
        }
    }

    /**
     * Reports sources of unknown profiles within the specified hot loops of the code.
     * <p>
     * Unknown profiles indicate missing or incomplete runtime profiling data at the user level
     * (either Java or guest code). The presence of unknown profiles in hot code regions (such as
     * loops) can hinder the optimizer's effectiveness, potentially degrading performance.
     * </p>
     * <p>
     * This method identifies and reports instances where profiling data is absent, assisting
     * developers in diagnosing and addressing potential optimization barriers.
     * </p>
     */
    private static void reportUnknownProfile(Loop l, Node inside, ControlFlowGraph cfg) {
        if (inside instanceof IfNode ifNode) {
            if (ifNode.profileSource().isUnknown()) {
                final OptionValues optionValues = inside.getOptions();
                NodeSourcePosition nsp = ifNode.getNodeSourcePosition();
                if (nsp == null) {
                    warn(optionValues,
                                    "Unknown profile for %s with relativeFrequency=%s in hot loop %s, NO NODE SOURCE POSITION%n\tPotential Action Item: Determine lack of node source position and profile.%n",
                                    ifNode,
                                    cfg.blockFor(inside).getRelativeFrequency(), l);
                } else {
                    warn(optionValues,
                                    "Unknown profile for %s with relativeFrequency=%s in hot loop %s, node source position is %n%s%n\tPotential Action Item: Add profile to the top-of-stack source location.%n",
                                    ifNode,
                                    cfg.blockFor(inside).getRelativeFrequency(), l,
                                    ifNode.getNodeSourcePosition().toString("\t"));
                }
            }
        }
    }

    /**
     * Reports occurrences of 'if' constructs within loops that have loop-invariant conditions.
     * <p>
     * Such 'if' statements should typically be optimized by loop unswitching prior to this stage.
     * The presence of these constructs may indicate missed optimization opportunities, as loop
     * unswitching can move invariant branches outside the loop, improving performance.
     * </p>
     */
    private static void reportInvariantLoopIf(Loop l, Node inside) {
        if (inside instanceof IfNode ifNode) {
            LogicNode logicNode = ifNode.condition();
            if (!l.whole().contains(logicNode)) {
                warn(logicNode.getOptions(), "If %s with condition %s is inside loop %s while condition is not%n\tPotential Action Item: Determine why compiler does not unswitch the loop.%n", ifNode,
                                logicNode, l);
            }
        }
    }

    /**
     * Reports memory kill operations within a specified hot loop that target
     * {@link LocationIdentity#any()}.
     * <p>
     * A memory kill to {@code AnyLocationIdentity} signifies a write or invalidation that is
     * treated as affecting any possible memory location. Such "any" kills prevent the compiler from
     * safely floating (reordering or optimizing) memory operations within the intermediate
     * representation (IR) graph, thereby limiting potential optimizations.
     * </p>
     * <p>
     * This report helps identify cases where aggressive memory invalidation may be degrading
     * performance by restricting optimization opportunities in hot loops.
     * </p>
     */
    private static void reportMemoryKillANYInLoop(Loop l, Node inside, ControlFlowGraph cfg) {
        if (MemoryKill.isMemoryKill(inside)) {
            if (MemoryKill.isSingleMemoryKill(inside)) {
                SingleMemoryKill sk = MemoryKill.asSingleMemoryKill(inside);
                if (sk.getKilledLocationIdentity().isAny()) {
                    if (inside instanceof FixedNode) {
                        // else we don't have a cfg position
                        warn(inside.getOptions(),
                                        "Node %s kills any and has relative relativeFrequency=%s  in loop %s %n\tPotential Action Item: Determine if operation is required and replace with less intrusive memory effect if possible.%n",
                                        inside, cfg.blockFor(inside).getRelativeFrequency(), l);
                    } else {
                        warn(inside.getOptions(), "Node %s kills any in loop %s %n", inside, l);
                    }
                }
            }
        }
    }

    /**
     * Reports all guard conditions within a specified hot loop where the guard's {@code x} or
     * {@code y} argument is eligible for speculative guard movement.
     * <p>
     * Speculative guard movement, as described in
     * {@link jdk.graal.compiler.loop.phases.SpeculativeGuardMovementPhase}, attempts to optimize
     * execution by moving eligible guards outside the loop when safe. Typically, such guards should
     * be speculatively optimized and floated before the loop to reduce overhead inside hot code
     * regions.
     * </p>
     * <p>
     * This report helps identify guards that remain inside loops and may represent missed
     * opportunities for speculative optimization.
     * </p>
     */
    private static void reportHotLoopGuardsInside(Node inside, Loop loop) {
        CompareNode compare = null;
        if (inside instanceof FixedGuardNode fg && fg.condition() instanceof CompareNode c1) {
            compare = c1;
        } else if (inside instanceof GuardNode g && g.getCondition() instanceof CompareNode c2) {
            compare = c2;
        }
        if (compare != null) {
            ValueNode x = compare.getX();
            ValueNode y = compare.getY();

            InductionVariable iv = null;
            ValueNode limit = null;
            if (loop.getInductionVariables().get(x) != null && loop.isOutsideLoop(y)) {
                iv = loop.getInductionVariables().get(x);
                limit = y;
            } else if (loop.getInductionVariables().get(y) != null && loop.isOutsideLoop(x)) {
                iv = loop.getInductionVariables().get(y);
                limit = x;
            }

            if (iv != null && limit != null) {
                warn(inside.getOptions(),
                                "Guard %s condition %s inside loop with iv %s and limit %s%n\tPotential Action Item: Determine why speculative guard movement does not consider them for optimization.%n",
                                inside, compare, iv, limit);
            }
        }
    }
}
