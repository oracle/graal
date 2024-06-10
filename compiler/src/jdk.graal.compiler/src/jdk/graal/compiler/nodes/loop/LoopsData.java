/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.loop;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.util.ReversedList;
import jdk.graal.compiler.debug.DebugContext;

/**
 * A data structure representing all the information about all loops in the given graph. Data about
 * loops is tied to a given {@link ControlFlowGraph cfg}. If the CFG changes, the loops data should
 * be considered invalid.
 */
public class LoopsData {
    /**
     * A mapping of all loop begin nodes to the respective {@link Loop}.
     */
    private final EconomicMap<LoopBeginNode, Loop> loopBeginToEx;
    private final ControlFlowGraph cfg;
    /**
     * Additional loop data for all loops in this graph.
     */
    private final List<Loop> loops;

    /**
     * Compute the loops data for this graph. Not that this will compute the control flow graph
     * first and then collect data about all loops in it.
     */
    static LoopsData compute(final StructuredGraph graph) {
        return new LoopsData(graph, null);
    }

    /**
     * Take the given control flow graph and compute all loop data from it.
     *
     * Note: assumes that the control flow graph reflects the current shape of tha graph. If the CFG
     * was computed and aftwards the fixed nodes in the graph have been altered it must not be used
     * to compute loops data.
     */
    static LoopsData compute(final ControlFlowGraph cfg) {
        return new LoopsData(cfg.graph, cfg);
    }

    protected LoopsData(ControlFlowGraph cfg, List<Loop> loops, EconomicMap<LoopBeginNode, Loop> loopBeginToEx) {
        this.cfg = cfg;
        this.loops = loops;
        this.loopBeginToEx = loopBeginToEx;
    }

    @SuppressWarnings({"try", "this-escape"})
    protected LoopsData(final StructuredGraph graph, ControlFlowGraph preComputedCFG) {
        loopBeginToEx = EconomicMap.create(Equivalence.IDENTITY);
        DebugContext debug = graph.getDebug();
        if (preComputedCFG == null) {
            try (DebugContext.Scope s = debug.scope("ControlFlowGraph")) {
                this.cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeDominators(true).computePostdominators(true).computeFrequency(true).build();
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        } else {
            this.cfg = preComputedCFG;
        }
        assert checkLoopOrder(cfg.getLoops());
        loops = new ArrayList<>(cfg.getLoops().size());
        for (CFGLoop<HIRBlock> loop : cfg.getLoops()) {
            Loop ex = new Loop(loop, this);
            loops.add(ex);
            loopBeginToEx.put(ex.loopBegin(), ex);
        }
    }

    /**
     * Checks that loops are ordered such that outer loops appear first.
     */
    protected static boolean checkLoopOrder(Iterable<CFGLoop<HIRBlock>> loops) {
        EconomicSet<CFGLoop<HIRBlock>> seen = EconomicSet.create(Equivalence.IDENTITY);
        for (CFGLoop<HIRBlock> loop : loops) {
            if (loop.getParent() != null && !seen.contains(loop.getParent())) {
                return false;
            }
            seen.add(loop);
        }
        return true;
    }

    /**
     * Get the {@link Loop} corresponding to {@code loop}.
     */
    public Loop loop(CFGLoop<HIRBlock> loop) {
        return loopBeginToEx.get((LoopBeginNode) loop.getHeader().getBeginNode());
    }

    /**
     * Get the {@link Loop} corresponding to {@code loopBegin}.
     */
    public Loop loop(LoopBeginNode loopBegin) {
        return loopBeginToEx.get(loopBegin);
    }

    /**
     * Get all loops.
     *
     * @return all loops.
     */
    public List<Loop> loops() {
        return loops;
    }

    /**
     * Get all loops, with outer loops ordered before inner loops.
     *
     * @return all loops, with outer loops first.
     */
    public List<Loop> outerFirst() {
        return loops;
    }

    /**
     * Get all loops, with inner loops ordered before outer loops.
     *
     * @return all loops, with inner loops first.
     */
    public List<Loop> innerFirst() {
        return ReversedList.reversed(loops);
    }

    /**
     * Get all non-counted loops. Counted loop detection must have already been performed with
     * {@link #detectCountedLoops()}.
     *
     * @return all loops that are not counted.
     */
    public List<Loop> nonCountedLoops() {
        List<Loop> nonCounted = new ArrayList<>();
        for (Loop loop : loops()) {
            if (!loop.isCounted()) {
                nonCounted.add(loop);
            }
        }
        return nonCounted;
    }

    /**
     * Get all counted loops. Counted loop detection must have already been performed with
     * {@link #detectCountedLoops()}.
     *
     * @return all loops that are counted.
     */
    public List<Loop> countedLoops() {
        List<Loop> counted = new ArrayList<>();
        for (Loop loop : loops()) {
            if (loop.isCounted()) {
                counted.add(loop);
            }
        }
        return counted;
    }

    /**
     * Perform counted loop detection for all loops which have not already been checked.
     */
    public void detectCountedLoops() {
        for (Loop loop : loops()) {
            loop.detectCounted();
        }
    }

    /**
     * Get the CFG this loops data is calculated from.
     */
    public ControlFlowGraph getCFG() {
        return cfg;
    }

    /**
     * Get information for an induction variable, or null if not found in one of the loops.
     */
    public InductionVariable getInductionVariable(ValueNode value) {
        InductionVariable match = null;
        for (Loop loop : loops()) {
            InductionVariable iv = loop.getInductionVariables().get(value);
            if (iv != null) {
                if (match != null) {
                    return null;
                }
                match = iv;
            }
        }
        return match;
    }

    /**
     * Deletes any nodes created within the scope of this object that have no usages.
     */
    public void deleteUnusedNodes() {
        for (Loop loop : loops()) {
            loop.deleteUnusedNodes();
        }
    }
}
