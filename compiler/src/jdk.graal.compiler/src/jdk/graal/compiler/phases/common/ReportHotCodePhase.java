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

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Node;
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
import jdk.graal.compiler.phases.BasePhase;

/**
 * Analyzes and reports performance-critical aspects of a structured graph's intermediate
 * representation (IR).
 * <p>
 * This phase traverses all sections of the code, sorts and identifies the hottest regions of the
 * IR— typically loops or frequently executed blocks—and generates a report highlighting any known
 * performance-critical observations related to those regions (if any are present).
 * </p>
 * <p>
 * The resulting report can be used to guide further performance analysis or optimization efforts.
 * </p>
 */
public class ReportHotCodePhase<C> extends BasePhase<C> {

    public static class Options {
        //@formatter:off
        @Option(help = "", type = OptionType.Debug)
        public static final OptionKey<Boolean> ReportHotCodePartsToIGV = new OptionKey<>(false);
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

    private static String blocksToString(List<HIRBlock> blocks, BlockToStringMode mode) {
        StringBuilder sb = new StringBuilder();
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

    private static final int REPORT_HOT_FIRST_N = 3;

    @Override
    protected void run(StructuredGraph graph, C c) {
        if (!(c instanceof CoreProviders)) {
            // context is generic in base phase, but we can only run this phase if the context is
            // supported
            TTY.println("Aborting hot code reporting because context " + c + " is not supported...");
            return;
        }

        CoreProviders context = (CoreProviders) c;

        final LoopsData ld = context.getLoopsDataProvider().getLoopsData(graph);
        final ControlFlowGraph cfg = ld.getCFG();
        // report the 3 hottest blocks and the 3 hottest loops and 3 hottest loops by local loop
        // frequency
        final List<HIRBlock> hottestBlocks = new ArrayList<>();
        final List<Loop> hottestLocalLoops = new ArrayList<>();
        final List<Loop> hottestGlobalLoops = new ArrayList<>();

        Collections.addAll(hottestBlocks, cfg.reversePostOrder());
        hottestBlocks.sort((x, y) -> Double.compare(y.getRelativeFrequency(), x.getRelativeFrequency()));

        ld.detectCountedLoops();
        hottestLocalLoops.addAll(ld.loops());
        hottestLocalLoops.sort((x, y) -> Double.compare(y.localLoopFrequency(), x.localLoopFrequency()));

        hottestGlobalLoops.addAll(ld.loops());
        hottestGlobalLoops.sort((x, y) -> Double.compare(y.getCFGLoop().getHeader().getRelativeFrequency(), x.getCFGLoop().getHeader().getRelativeFrequency()));

        final List<HIRBlock> hottestFirstBlocks = takeUntil(hottestBlocks, REPORT_HOT_FIRST_N);
        String hottestGlobalBlocksString = String.format("Hottest 3 blocks are %s %s %s", hottestFirstBlocks,
                        blocksToString(hottestFirstBlocks, BlockToStringMode.BEGIN_NODE),
                        blocksToString(hottestFirstBlocks, BlockToStringMode.GLOBAL_FREQUENCY));
        TTY.printf("[Hot Code Warning] %s\n", hottestGlobalBlocksString);
        if (Options.ReportHotCodePartsToIGV.getValue(graph.getOptions())) {
            graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, hottestGlobalBlocksString);
        }

        final List<Loop> hottestFirstLocalLoops = takeUntil(hottestLocalLoops, REPORT_HOT_FIRST_N);
        String hottestLocalLoopString = String.format("Hottest 3 local loops are %s %s",
                        loopBlocksToString(hottestFirstLocalLoops, LoopToStringMode.BLOCK),
                        loopBlocksToString(hottestFirstLocalLoops, LoopToStringMode.LOCAL_FREQUENCY));
        TTY.printf("[Hot Code Warning] %s\n", hottestLocalLoopString);
        if (Options.ReportHotCodePartsToIGV.getValue(graph.getOptions())) {
            graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, hottestLocalLoopString);
        }

        final List<Loop> hottestFirstGlobalLoops = takeUntil(hottestGlobalLoops, REPORT_HOT_FIRST_N);
        String hottestGlobalLoopString = String.format("Hottest 3 global loops are %s %s",
                        loopBlocksToString(hottestFirstGlobalLoops, LoopToStringMode.BLOCK),
                        loopBlocksToString(hottestFirstGlobalLoops, LoopToStringMode.GLOBAL_FREQUENCY));
        TTY.printf("[Hot Code Warning] %s\n", hottestGlobalLoopString);
        if (Options.ReportHotCodePartsToIGV.getValue(graph.getOptions())) {
            graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, hottestGlobalLoopString);
        }

        reportHotLoopGuardsInside(hottestGlobalLoops);

        for (Loop l : hottestGlobalLoops) {
            for (Node inside : l.inside().nodes()) {
                reportUnknownProfile(l, inside, cfg);
                reportInvariantLoopIf(l, inside);
                reportMemoryKillANYInLoop(l, inside, cfg);
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
                TTY.printf("[Hot Code Warning] Unknown profile for %s with f=%s in hot loop %s, nsp is %n\t%s%n", ifNode, cfg.blockFor(inside).getRelativeFrequency(), l,
                                ifNode.getNodeSourcePosition());
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
                TTY.printf("[Hot Code Warning] If %s with condition %s is inside loop %s while condition is not%n", ifNode, logicNode, l);
            }
        }
    }

    /**
     * Reports memory kill operations within a specified hot loop that target
     * {@link org.graalvm.word.LocationIdentity.AnyLocationIdentity}.
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
                        // else we dont have a cfg position
                        TTY.printf("[Hot Code Warning] Node %s kills any and has relative f=%s  in loop %s %n", inside, cfg.blockFor(inside).getRelativeFrequency(), l);
                    } else {
                        TTY.printf("[Hot Code Warning] Node %s kills any in loop %s %n", inside, l);
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
     * {@link jdk.graal.compiler.loop.phases.SpeculativeGuardMovementPhase.SpeculativeGuardMovement},
     * attempts to optimize execution by moving eligible guards outside the loop when safe.
     * Typically, such guards should be speculatively optimized and floated before the loop to
     * reduce overhead inside hot code regions.
     * </p>
     * <p>
     * This report helps identify guards that remain inside loops and may represent missed
     * opportunities for speculative optimization.
     * </p>
     */
    private static void reportHotLoopGuardsInside(List<Loop> hottestGlobalLoops) {
        for (Loop l : hottestGlobalLoops) {
            for (Node inside : l.inside().nodes()) {
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
                    if (l.getInductionVariables().get(x) != null && l.isOutsideLoop(y)) {
                        iv = l.getInductionVariables().get(x);
                        limit = y;
                    } else if (l.getInductionVariables().get(y) != null && l.isOutsideLoop(x)) {
                        iv = l.getInductionVariables().get(y);
                        limit = x;
                    }

                    if (iv != null && limit != null) {
                        TTY.printf("[Hot Code Warning] Guard %s condition %s inside loop with iv %s and limit %s%n", inside, compare, iv, limit);
                    }
                }
            }
        }
    }
}