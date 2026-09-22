/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.loop.phases;

import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ReassociationPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.tiers.MidTierContext;

public class CountedStripMiningReassociationPhase extends BasePhase<MidTierContext> {

    private final CanonicalizerPhase canon;

    public CountedStripMiningReassociationPhase(CanonicalizerPhase canon) {
        this.canon = canon;
    }

    public static class Options {
        //@formatter:off
        @Option(help = "Perform counted strip mining reassociation.", type = OptionType.Debug)
        public static final OptionKey<Boolean> StripMiningReassociation = new OptionKey<>(true);
        //@formatter:on
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.unlessRunAfter(this, StageFlag.LOOP_OVERFLOWS_CHECKED, graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.FSA, graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.VALUE_PROXY_REMOVAL, graphState));
    }

    /**
     * Maximum number of reassociation iterations for a given graph.
     */
    private static final int MAX_REASSOC_ITERATIONS = 128;

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, MidTierContext context) {
        List<CountedStripMiningPhase.StampInjectedAdd> stripMiningOffsets = graph.getNodes().filter(CountedStripMiningPhase.StampInjectedAdd.class).snapshot();
        if (stripMiningOffsets.isEmpty()) {
            return;
        }
        EconomicSetNodeEventListener ec = new EconomicSetNodeEventListener();
        boolean change = false;
        try (NodeEventScope nes = graph.trackNodeEvents(ec)) {
            for (var add : stripMiningOffsets) {
                change = reassociateConstant(add, graph) || change;
            }
        }
        if (!change) {
            return;
        }
        for (int reassoc = 0; reassoc < MAX_REASSOC_ITERATIONS; reassoc++) {
            change = false;
            EconomicSet<Node> changed = EconomicSet.create(ec.getNodes());
            for (var add : stripMiningOffsets) {
                if (add.isAlive()) {
                    ec.getNodes().add(add);
                }
            }
            canon.applyIncremental(graph, context, ec.getNodes());
            for (var add : stripMiningOffsets) {
                if (add.isAlive() && add.hasUsages()) {
                    Graph.Mark before = graph.getMark();
                    ValueNode result = graph.addOrUnique(AddNode.create(add.getX(), add.getY(), NodeView.DEFAULT));
                    if (!graph.isNew(before, result) && result.isAlive() && result instanceof AddNode a2 && a2.getX() == add.getX() && a2.getY() == add.getY()) {
                        // GVN with our add with better stamps
                        a2.replaceAndDelete(add);
                    } else if (!graph.isNew(before, result) && result.isAlive()) {
                        // we found a better node, this node should also be found by a regular
                        // canonicalization, thus do nothing and let the later regular canon clean
                        // this up
                    } else {
                        assert result.hasNoUsages();
                        result.safeDelete();
                    }
                }
            }
            ec.getNodes().clear();
            try (NodeEventScope nes = graph.trackNodeEvents(ec)) {
                for (var n : changed) {
                    if (n instanceof BinaryArithmeticNode<?> b) {
                        change = reassociateConstant(b, graph) || change;
                    }
                }
            }
            if (!change) {
                return;
            }
        }
    }

    @SuppressWarnings("try")
    private static boolean reassociateConstant(BinaryArithmeticNode<?> binary, StructuredGraph graph) {
        if (binary.isAlive() && binary.hasUsages() && binary.mayReassociate()) {
            DebugContext debug = graph.getDebug();
            try (DebugContext.Scope s = debug.scope("ReassociateConstants")) {
                ValueNode result = BinaryArithmeticNode.reassociateUnmatchedValues(binary, ValueNode.isConstantPredicate(), NodeView.DEFAULT);
                if (result != binary) {
                    if (!result.isAlive()) {
                        assert !result.isDeleted();
                        result = graph.addOrUniqueWithInputs(result);
                    }
                    binary.replaceAtUsages(result);
                    graph.getOptimizationLog().report(ReassociationPhase.class, "ConstantReassociation", binary);
                    GraphUtil.killWithUnusedFloatingInputs(binary);
                    return true;
                }
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        }
        return false;
    }

}
