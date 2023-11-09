/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;
import java.util.Optional;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph.NodeEvent;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.loop.LoopEx;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * Rearrange binary arithmetic operations that {@linkplain BinaryArithmeticNode#mayReassociate() may
 * be reassociated} for loop invariants and constants.
 */
public class ReassociationPhase extends BasePhase<CoreProviders> {

    private final CanonicalizerPhase canonicalizer;

    public ReassociationPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return canonicalizer.notApplicableTo(graphState);
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        EconomicSetNodeEventListener changedNodes = new EconomicSetNodeEventListener(EnumSet.of(NodeEvent.NODE_ADDED));
        try (NodeEventScope news = graph.trackNodeEvents(changedNodes)) {
            prepareGraphForReassociation(graph);
            reassociateConstants(graph, context);
            reassociateInvariants(graph, context);
        }
        canonicalizer.applyIncremental(graph, context, changedNodes.getNodes());
    }

    //@formatter:off
    /**
     * Re-associate loop invariant so that invariant parts of the expression can move outside of the
     * loop.
     *
     * For example:
     *     for (int i = 0; i < LENGTH; i++) {         for (int i = 0; i < LENGTH; i++) {
     *         arr[i] = (i * inv1) * inv2;       =>       arr[i] = i * (inv1 * inv2);
     *     }                                          }
     */
    //@formatter:on
    @SuppressWarnings("try")
    private static void reassociateInvariants(StructuredGraph graph, CoreProviders context) {
        DebugContext debug = graph.getDebug();
        LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
        int iterations = 0;
        try (DebugContext.Scope s = debug.scope("ReassociateInvariants")) {
            boolean changed = true;
            // Terminate the loop if there is no change or if the iteration is reached to the upper
            // bound.
            while (changed && iterations < 32) {
                changed = false;
                for (LoopEx loop : loopsData.loops()) {
                    changed |= loop.reassociateInvariants();
                }
                loopsData.deleteUnusedNodes();
                iterations++;
                debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Reassociation: after iteration %d", iterations);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    /**
     * Push constant down the expression tree like "(a + 1) + b" => "(a + b) + 1". It creates more
     * opportunities for optimizations like "(a + 1) + 2" => "(a + 3)", which has been implemented
     * in the {@linkplain CanonicalizerPhase CanonicalizerPhase}. To avoid some unexpected
     * regressions for loop invariants like "i + (inv + 1)" => "(i + inv) + 1", this re-association
     * is applied after {@linkplain ReassociationPhase#reassociateInvariants reassociateInvariants}
     * and only applied to expressions outside a loop.
     */
    @SuppressWarnings("try")
    private static void reassociateConstants(StructuredGraph graph, CoreProviders context) {
        LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
        NodeBitMap loopNodes = graph.createNodeBitMap();
        for (LoopEx loop : loopsData.loops()) {
            loopNodes.union(loop.whole().nodes());
        }
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("ReassociateConstants")) {
            for (BinaryArithmeticNode<?> binary : graph.getNodes().filter(BinaryArithmeticNode.class)) {
                // Skip re-associations to loop variant expressions.
                if (!binary.mayReassociate() || (!loopNodes.isNew(binary) && loopNodes.contains(binary))) {
                    continue;
                }
                ValueNode result = BinaryArithmeticNode.reassociateUnmatchedValues(binary, ValueNode.isConstantPredicate(), NodeView.DEFAULT);
                if (result != binary) {
                    if (!result.isAlive()) {
                        assert !result.isDeleted();
                        result = graph.addOrUniqueWithInputs(result);
                    }
                    binary.replaceAtUsages(result);
                    graph.getOptimizationLog().report(ReassociationPhase.class, "ConstantReassociation", binary);
                    GraphUtil.killWithUnusedFloatingInputs(binary);
                }
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }

    }

    /**
     * Prepare the given graph for reassociation: This means rewriting shift nodes back to
     * multiplication nodes if possible. Left shift is not an associative operation, thus we rewrite
     * all shifts derived from multiplications back to their multiplications and try to re-associate
     * them.
     */
    @SuppressWarnings("try")
    private static void prepareGraphForReassociation(StructuredGraph graph) {
        final DebugContext debug = graph.getDebug();
        EconomicSetNodeEventListener nev = new EconomicSetNodeEventListener(EnumSet.of(NodeEvent.NODE_ADDED));
        try (NodeEventScope news = graph.trackNodeEvents(nev)) {
            for (LeftShiftNode l : graph.getNodes().filter(LeftShiftNode.class)) {
                if (l.tryReplaceWithMulNode()) {
                    graph.getOptimizationLog().withLazyProperty("replacedNodeClass", () -> l.getNodeClass().shortName()).report(ReassociationPhase.class, "ArithmeticWithMulReplacement", l);
                }
            }
        }
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Reassociation: after creating mul nodes from shifts");
        for (Node newNode : nev.getNodes()) {
            if (newNode instanceof MulNode) {
                assert ((MulNode) newNode).getY().isConstant();
                MulNode mul = (MulNode) newNode;
                for (Node usage : newNode.usages()) {
                    boolean replaced = false;
                    if (usage instanceof AddNode) {
                        if (((BinaryArithmeticNode<?>) usage).getX() == mul.getX() && ((BinaryArithmeticNode<?>) usage).getY() == mul ||
                                        ((BinaryArithmeticNode<?>) usage).getY() == mul.getX() && ((BinaryArithmeticNode<?>) usage).getX() == mul) {
                            long i = ((PrimitiveConstant) mul.getY().asConstant()).asLong();
                            MulNode newMul = graph.addOrUnique(new MulNode(mul.getX(), ConstantNode.forIntegerStamp(mul.getY().stamp(NodeView.DEFAULT), i + 1, graph)));
                            usage.replaceAtUsages(newMul);
                            replaced = true;
                        }
                    } else if (usage instanceof SubNode) {
                        if (((BinaryArithmeticNode<?>) usage).getX() == mul && ((BinaryArithmeticNode<?>) usage).getY() == mul.getX()) {
                            long i = ((PrimitiveConstant) mul.getY().asConstant()).asLong();
                            MulNode newMul = graph.addOrUnique(new MulNode(mul.getX(), ConstantNode.forIntegerStamp(mul.getY().stamp(NodeView.DEFAULT), i - 1, graph)));
                            usage.replaceAtUsages(newMul);
                            replaced = true;
                        }
                    }
                    if (replaced) {
                        graph.getOptimizationLog().withLazyProperty("replacedNodeClass", () -> usage.getNodeClass().shortName()).report(ReassociationPhase.class, "ArithmeticWithMulReplacement",
                                        usage);
                    }
                }
            }
        }
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Reassociation: after creating mul from add/sub");
    }

    @Override
    public float codeSizeIncrease() {
        return 2.0f;
    }
}
