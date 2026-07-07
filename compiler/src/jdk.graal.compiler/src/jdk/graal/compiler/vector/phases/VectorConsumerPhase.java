/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.phases;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;

import jdk.graal.compiler.vector.nodes.consumer.LowerableVectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorLoopNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorWriteNode;
import jdk.graal.compiler.vector.nodes.lowered.CommitVectorConsumerNode;
import jdk.graal.compiler.vector.nodes.lowered.FinishVectorConsumerNode;
import jdk.graal.compiler.vector.nodes.lowered.PartialVectorConsumerNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorAlignmentNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorHasNextNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorInitialIteratorNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorIteratorNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorLoweringTool;
import jdk.graal.compiler.vector.nodes.lowered.VectorShiftNode;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * This phase expands placeholder nodes inserted by {@link VectorLoweringPhase} to the corresponding
 * vector operations: {@link PartialVectorConsumerNode}s are replaced by fixed-length versions of
 * the original {@link VectorConsumer}. {@link VectorHasNextNode}s are expanded to proper index
 * computations.
 */
public class VectorConsumerPhase extends PostRunCanonicalizationPhase<LowTierContext> {

    public VectorConsumerPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.ADDRESS_LOWERING, graphState));
    }

    @Override
    @SuppressWarnings("try")
    public void run(StructuredGraph graph, LowTierContext context) {
        if (!graph.hasNode(FinishVectorConsumerNode.TYPE)) {
            return;
        }

        EconomicSetNodeEventListener zeroUsages = new EconomicSetNodeEventListener(EnumSet.of(Graph.NodeEvent.ZERO_USAGES));
        try (NodeEventScope nes = graph.trackNodeEvents(zeroUsages)) {
            materializeConsumers(graph, context);
        }
        for (Node n : zeroUsages.getNodes()) {
            if (n instanceof FloatingNode) {
                n.safeDelete();
            }
        }
    }

    @SuppressWarnings("try")
    private static void materializeConsumers(StructuredGraph graph, LowTierContext context) {
        ConsumerMaterializer materializer = new ConsumerMaterializer(graph, context.getTarget(), context.getConstantReflection());
        ArrayList<FixedWithNextNode> removeQueue = new ArrayList<>();
        for (Node node : graph.getNodes()) {
            if (node instanceof CommitVectorConsumerNode commit) {
                VectorConsumer consumer = commit.getConsumer();
                try (DebugCloseable position = consumer.asNode().withNodeSourcePosition()) {
                    commit.lower(materializer);
                }
                removeQueue.add(consumer.asFixedWithNextNode());
                if (consumer instanceof VectorLoopNode) {
                    // The consumer group took care of lowering each of its members, we can now
                    // remove those as well.
                    for (ValueNode member : ((VectorLoopNode) consumer).getConsumers()) {
                        removeQueue.add((FixedWithNextNode) member);
                    }
                }
            } else if (node instanceof VectorHasNextNode) {
                ((VectorHasNextNode) node).lower(materializer);
            } else if (node instanceof VectorAlignmentNode) {
                ((VectorAlignmentNode) node).lower(materializer);
            } else if (node instanceof PartialVectorConsumerNode) {
                removeQueue.add((PartialVectorConsumerNode) node);
            }
        }
        for (FixedWithNextNode consumer : removeQueue) {
            if (consumer.asNode().isAlive()) {
                if (consumer instanceof GuardingNode) {
                    // This consumer might still guard some node that will be deleted below.
                    consumer.asNode().replaceAtUsages(null, InputType.Guard);
                }
                consumer.asNode().replaceAtUsages(null, InputType.Association);
                if (consumer.asNode().hasUsages() && consumer instanceof VectorWriteNode && ((VectorWriteNode) consumer).getLength().isDefaultConstant()) {
                    // Zero-length write: It was expanded to nothing and will now be removed from
                    // the graph. We must first ensure that any memory usages are updated correctly.
                    MemoryKill lla = ((VectorWriteNode) consumer).getLastLocationAccess();
                    while (lla instanceof VectorWriteNode && ((VectorWriteNode) lla).getLength().isDefaultConstant()) {
                        lla = ((VectorWriteNode) lla).getLastLocationAccess();
                    }
                    consumer.asNode().replaceAtUsages(lla.asNode());
                }
                graph.removeFixed(consumer);
            }
        }
    }

    private static class ConsumerMaterializer implements VectorLoweringTool {

        private final TargetDescription target;
        private final ConstantReflectionProvider constantReflection;
        private final NodeMap<VectorConsumerIterator> phiCache;
        private final NodeMap<PhiLength> phiLengthCache;
        private final Queue<PhiNode> phiWorkList;

        ConsumerMaterializer(StructuredGraph graph, TargetDescription target, ConstantReflectionProvider constantReflection) {
            this.target = target;
            this.constantReflection = constantReflection;
            this.phiCache = new NodeMap<>(graph);
            this.phiLengthCache = new NodeMap<>(graph);
            this.phiWorkList = new LinkedList<>();
        }

        @Override
        public TargetDescription getTarget() {
            return target;
        }

        private PhiLength getLength(ValueNode iterator) {
            if (iterator instanceof VectorInitialIteratorNode) {
                int stepLength = ((VectorInitialIteratorNode) iterator).getStepLength();
                assert stepLength == 0 : iterator + " stepLength=" + stepLength;
                return null;
            } else if (iterator instanceof VectorShiftNode shiftNode) {
                IntegerStamp stamp = (IntegerStamp) shiftNode.getShiftAmount().stamp(NodeView.DEFAULT);
                NumUtil.assertNonNegativeLong(stamp.lowerBound());
                return new PhiLength((int) stamp.lowerBound(), (int) stamp.upperBound());
            } else if (iterator instanceof VectorIteratorNode) {
                int length = ((VectorIteratorNode) iterator).getStepLength();
                assert NumUtil.assertNonNegativeInt(length);
                return new PhiLength(length, length);
            } else if (iterator instanceof PhiNode) {
                PhiNode phi = (PhiNode) iterator;
                PhiLength ret = phiLengthCache.get(phi);
                if (ret == null) {
                    ret = new PhiLength();
                    phiLengthCache.put(phi, ret); // to break cycles
                    for (int i = 0; i < phi.valueCount(); i++) {
                        PhiLength newLength = getLength(phi.valueAt(i));
                        if (newLength != null) {
                            ret.combine(newLength);
                        }
                    }
                }
                return ret;
            } else {
                throw GraalError.shouldNotReachHere(iterator.toString()); // ExcludeFromJacocoGeneratedReport
            }
        }

        @Override
        public VectorConsumerIterator getIterator(ValueNode iterator, LowerableVectorConsumer consumer) {
            if (iterator instanceof VectorIteratorNode) {
                return ((VectorIteratorNode) iterator).lower(this);
            } else if (iterator instanceof PhiNode) {
                PhiNode phi = (PhiNode) iterator;
                VectorConsumerIterator ret = phiCache.get(iterator);
                if (ret == null) {
                    PhiLength length = getLength(phi);
                    ret = consumer.createPhiIterator(length.min, length.max, phi, target);
                    phiCache.set(iterator, ret);
                    phiWorkList.add(phi);
                }
                return ret;
            } else {
                throw GraalError.shouldNotReachHere(iterator.toString()); // ExcludeFromJacocoGeneratedReport
            }
        }

        @Override
        public void constructGraph(LowerableVectorConsumer consumer) {
            while (!phiWorkList.isEmpty()) {
                PhiNode phi = phiWorkList.remove();
                VectorConsumerIterator ret = phiCache.get(phi);
                for (int i = 0; i < phi.merge().phiPredecessorCount(); i++) {
                    ret.addPhiInput(consumer, getIterator(phi.valueAt(i), consumer), phi.merge().phiPredecessorAt(i));
                }
                phi.clearInputs();
            }
        }

        @Override
        public ConstantReflectionProvider getConstantReflection() {
            return constantReflection;
        }

        private static class PhiLength {
            int min;
            int max;

            PhiLength() {
                this.min = Integer.MAX_VALUE;
                this.max = 0;
            }

            PhiLength(int min, int max) {
                this.min = min;
                this.max = max;
            }

            void combine(PhiLength newLength) {
                this.min = Math.min(this.min, newLength.min);
                this.max = Math.max(this.max, newLength.max);
            }

            @Override
            public String toString() {
                return "PhiLength(" + min + ", " + max + ")";
            }
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }
}
