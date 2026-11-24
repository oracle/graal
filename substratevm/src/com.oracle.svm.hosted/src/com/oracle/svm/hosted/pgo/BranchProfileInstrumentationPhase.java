/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pgo;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.svm.core.pgo.BranchProfileKey;
import com.oracle.svm.core.pgo.BranchProfileRecorder;
import com.oracle.svm.core.pgo.SwitchProfileKey;
import com.oracle.svm.core.pgo.SwitchProfileRecorder;
import com.oracle.svm.hosted.pgo.ProfilingUtilities.ConditionalSite;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;

/**
 * Instruments every {@link IfNode} and {@link SwitchNode} that has a bytecode position and does
 * not guard an implicit exception (see {@link ProfilingUtilities#relevantConditionalSitesFromGraph}).
 * Each graph copy of a branch or switch gets its own profile key, with a counter node at the start
 * of each successor; {@code -H:-PGOProfileBranches} and {@code -H:-PGOProfileSwitches} turn off
 * either kind. Part of {@link PGOInstrumentationPhases}; the inliner instruments the graphs it
 * expands the same way (see {@link PGOInstrumentationFeature#instrumentExpandedGraph}).
 */
final class BranchProfileInstrumentationPhase extends BasePhase<HighTierContext> {

    private static final AtomicLong INSTRUMENTED_BRANCHES = new AtomicLong();
    private static final AtomicLong SKIPPED_BRANCHES = new AtomicLong();
    private static final AtomicLong INSTRUMENTED_SWITCHES = new AtomicLong();
    private static final AtomicLong SKIPPED_SWITCHES = new AtomicLong();

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        instrumentGraph(graph, null);
    }

    /**
     * Instruments the branches and switches of {@code graph}, with node positions taken relative to
     * {@code inliningContext}, the caller context of an expanded callee, or null for a root.
     */
    static void instrumentGraph(StructuredGraph graph, NodeSourcePosition inliningContext) {
        if (graph.method() == null || ProfilingUtilities.isNativeImageRuntimeCode(graph.method())) {
            return;
        }

        for (ConditionalSite site : ProfilingUtilities.relevantConditionalSitesFromGraph(graph, inliningContext)) {
            if (site.node() instanceof IfNode conditional) {
                if (PGOInstrumentationFeature.Options.PGOProfileBranches.getValue()) {
                    instrument(graph, conditional, site.context());
                }
            } else if (site.node() instanceof SwitchNode switchNode && PGOInstrumentationFeature.Options.PGOProfileSwitches.getValue()) {
                instrumentSwitch(graph, switchNode, site.context());
            }
        }
    }

    private static void instrument(StructuredGraph graph, IfNode conditional, NodeSourcePosition position) {
        AbstractBeginNode trueSuccessor = conditional.trueSuccessor();
        AbstractBeginNode falseSuccessor = conditional.falseSuccessor();
        NodeSourcePosition truePosition = trueSuccessor.getNodeSourcePosition();
        NodeSourcePosition falsePosition = falseSuccessor.getNodeSourcePosition();
        if (position == null || truePosition == null || falsePosition == null) {
            SKIPPED_BRANCHES.incrementAndGet();
            return;
        }

        BranchProfileKey key = BranchProfileRecorder.createUnnumbered(ProfilingUtilities.context(position), truePosition.getBCI(), falsePosition.getBCI(),
                        ProfilingUtilities.conditionKind(conditional), ProfilingUtilities.conditionFingerprint(conditional));
        insertCounter(graph, trueSuccessor, key, true);
        insertCounter(graph, falseSuccessor, key, false);
        INSTRUMENTED_BRANCHES.incrementAndGet();
    }

    private static void instrumentSwitch(StructuredGraph graph, SwitchNode switchNode, NodeSourcePosition position) {
        if (position == null) {
            SKIPPED_SWITCHES.incrementAndGet();
            return;
        }
        List<Node> successors = switchNode.successors().snapshot();
        int[] successorBcis = new int[successors.size()];
        for (int i = 0; i < successors.size(); i++) {
            NodeSourcePosition successorPosition = successors.get(i).getNodeSourcePosition();
            if (successorPosition == null) {
                SKIPPED_SWITCHES.incrementAndGet();
                return;
            }
            successorBcis[i] = successorPosition.getBCI();
        }
        SwitchProfileKey switchKey = SwitchProfileRecorder.createUnnumbered(ProfilingUtilities.context(position), successorBcis);
        for (int i = 0; i < successors.size(); i++) {
            AbstractBeginNode successor = (AbstractBeginNode) successors.get(i);
            CallCountProfileCounterNode counter = graph.add(new CallCountProfileCounterNode(switchKey.successorSlot(i)));
            counter.setNodeSourcePosition(successor.getNodeSourcePosition());
            graph.addAfterFixed(successor, counter);
        }
        INSTRUMENTED_SWITCHES.incrementAndGet();
    }

    private static void insertCounter(StructuredGraph graph, AbstractBeginNode successor, BranchProfileKey key, boolean onTrueSuccessor) {
        BranchProfileCounterNode counterNode = graph.add(new BranchProfileCounterNode(key, onTrueSuccessor));
        counterNode.setNodeSourcePosition(successor.getNodeSourcePosition());
        graph.addAfterFixed(successor, counterNode);
    }

    static long instrumentedBranches() {
        return INSTRUMENTED_BRANCHES.get();
    }

    static long skippedBranches() {
        return SKIPPED_BRANCHES.get();
    }

    static long instrumentedSwitches() {
        return INSTRUMENTED_SWITCHES.get();
    }

    static long skippedSwitches() {
        return SKIPPED_SWITCHES.get();
    }
}
