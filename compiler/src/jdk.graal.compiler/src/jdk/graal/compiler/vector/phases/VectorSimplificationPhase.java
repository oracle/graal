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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.List;
import java.util.Optional;

import jdk.graal.compiler.vector.nodes.SimplifiableVectorNode;
import jdk.graal.compiler.vector.nodes.SimplifiableVectorNode.VectorSimplifier;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.op.VectorPhi;
import jdk.graal.compiler.vector.nodes.subgraph.SubGraphUtil;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph.Mark;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.MaxNode;
import jdk.graal.compiler.nodes.calc.MinMaxNode;
import jdk.graal.compiler.nodes.calc.MinNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.NarrowableArithmeticNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.UnaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.UnsignedMaxNode;
import jdk.graal.compiler.nodes.calc.UnsignedMinNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Simplify the high-level representation of vector operations by calling nodes'
 * {@link VectorConsumer#simplifyTree(VectorSimplifier)} and
 * {@link SimplifiableVectorNode#simplify(VectorSimplifier)} methods.
 * </p>
 *
 * In addition, this tries to push narrowing operations past {@link NarrowableArithmeticNode}s. This
 * can allow us to choose a higher vector length later. For example, this allows vector operations
 * from and to {@code byte} arrays to be performed at a vector length of 32 on AVX2. Without
 * propagating the narrowing, we would have intermediate {@code int} operations and only be able to
 * vectorize with a vector length of 4.
 */
public class VectorSimplificationPhase extends PostRunCanonicalizationPhase<CoreProviders> {

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class VectorNodeHandle extends FixedNode {

        public static final NodeClass<VectorNodeHandle> TYPE = NodeClass.create(VectorNodeHandle.class);
        @Input(InputType.Unchecked) VectorNode vector;

        protected VectorNodeHandle(VectorNode vector) {
            super(TYPE, StampFactory.forVoid());
            this.vector = vector;
        }

        public VectorNode getVector() {
            return vector;
        }

        @Override
        public boolean verifyNode() {
            // Ignore the guarantee that FixedNodes can't float
            return true;
        }
    }

    protected static class CachingSimplifier implements VectorSimplifier {

        private final StructuredGraph graph;
        private final NodeMap<VectorNode> simplifyCache;
        private final CoreProviders context;
        private final VectorArchitecture arch;
        private final CanonicalizerPhase canonicalizer;

        CachingSimplifier(StructuredGraph graph, CoreProviders context, CanonicalizerPhase canonicalizer) {
            this.graph = graph;
            this.context = context;
            simplifyCache = new NodeMap<>(graph);
            this.arch = ((VectorLoweringProvider) context.getLowerer()).getVectorArchitecture();
            this.canonicalizer = canonicalizer;
        }

        private ValueNode narrowInput(StructuredGraph subgraph, IntegerStamp resultStamp, ValueNode input) {
            ValueNode narrowedInput = tryNarrow(subgraph, resultStamp, input);
            if (narrowedInput != null) {
                return narrowedInput;
            }
            return subgraph.unique(new NarrowNode(input, resultStamp.getBits()));
        }

        private ValueNode tryNarrow(StructuredGraph subgraph, IntegerStamp resultStamp, ValueNode node) {
            int resultBits = resultStamp.getBits();
            if (!(node instanceof NarrowableArithmeticNode narrowable)) {
                return null;
            }

            // Don't narrow to illegal instructions. For example, AVX does not have vectorized
            // shift instructions on bytes, so we must not narrow a vectorized shift on ints
            // to a shift on bytes.
            boolean isScalarShift = false;
            if (node instanceof ShiftNode<?> s && SubGraphUtil.isScalarInput(s.getY())) {
                isScalarShift = true;
                if (!arch.narrowedVectorShiftAvailable(s, resultStamp)) {
                    return null;
                }
            } else if (!arch.narrowedVectorInstructionAvailable(narrowable, resultStamp)) {
                return null;
            }

            if (node.hasExactlyOneUsage() && narrowable.isNarrowable(resultBits)) {
                /*
                 * Build a fresh narrowed operation instead of changing the existing node's inputs:
                 * the old inputs have wider stamps than their narrowed replacements, so mutating
                 * the input edges would violate the replacement invariants. NarrowableArithmeticNode
                 * does not provide a common factory API, so use the concrete arithmetic factories.
                 */
                if (node instanceof BinaryArithmeticNode<?> binary) {
                    ValueNode newX = narrowInput(subgraph, resultStamp, binary.getX());
                    ValueNode newY = narrowInput(subgraph, resultStamp, binary.getY());
                    return BinaryArithmeticNode.binaryIntegerOp(subgraph, newX, newY, NodeView.DEFAULT, binary.getArithmeticOp());
                } else if (node instanceof ShiftNode<?> shift) {
                    ValueNode newX = narrowInput(subgraph, resultStamp, shift.getX());
                    ValueNode newY = isScalarShift ? shift.getY() : narrowInput(subgraph, resultStamp, shift.getY());
                    return subgraph.addOrUniqueWithInputs(ShiftNode.shiftOp(newX, newY, NodeView.DEFAULT, shift.getArithmeticOp()));
                } else if (node instanceof UnaryArithmeticNode<?> unary) {
                    ValueNode newValue = narrowInput(subgraph, resultStamp, unary.getValue());
                    return UnaryArithmeticNode.unaryIntegerOp(subgraph, newValue, NodeView.DEFAULT, unary.getArithmeticOp());
                }
                throw GraalError.shouldNotReachHereUnexpectedValue(node); // ExcludeFromJacocoGeneratedReport
            }

            return null;
        }

        private void narrowOperations(StructuredGraph subgraph) {
            for (MinMaxNode<?> minMax : subgraph.getNodes().filter(MinMaxNode.class)) {
                maybeMakeNarrowable(subgraph, minMax);
            }
            for (NarrowNode narrow : subgraph.getNodes().filter(NarrowNode.class)) {
                int narrowBits = narrow.getResultBits();
                if (narrowBits < Byte.SIZE) {
                    // Don't try to narrow boolean operations to less than 8 bits.
                    continue;
                }
                ValueNode narrowedValue = tryNarrow(subgraph, IntegerStamp.create(narrowBits), narrow.getValue());
                if (narrowedValue != null) {
                    narrow.replaceAtUsagesAndDelete(narrowedValue);
                    graph.getOptimizationLog().report(VectorSimplificationPhase.class, "NodeNarrowing", narrow);
                }
            }
        }

        /**
         * Check if the given {@code minMax} node has a sign or zero extend input but could be
         * narrowed to the extend's input width. If yes, add a narrow/extend pair of nodes using
         * this minMax. That will allow our normal narrowing logic to apply. This special handling
         * is needed for min/max nodes because they don't have the same narrow/extend shapes as
         * other arithmetic nodes.
         */
        private void maybeMakeNarrowable(StructuredGraph subgraph, MinMaxNode<?> minMax) {
            IntegerConvertNode<?> extend = null;
            if (minMax.getX() instanceof IntegerConvertNode) {
                extend = (IntegerConvertNode<?>) minMax.getX();
            } else if (minMax.getY() instanceof IntegerConvertNode) {
                extend = (IntegerConvertNode<?>) minMax.getY();
            } else {
                return;
            }
            if (extend instanceof NarrowNode) {
                return;
            }
            int narrowBits = extend.getInputBits();
            IntegerStamp stamp = (IntegerStamp) minMax.stamp(NodeView.DEFAULT);
            boolean canNarrow = false;
            if (minMax instanceof MinNode || minMax instanceof MaxNode) {
                canNarrow = NumUtil.minValue(narrowBits) <= stamp.lowerBound() && stamp.upperBound() <= NumUtil.maxValue(narrowBits);
                if (canNarrow && extend instanceof ZeroExtendNode) {
                    /*
                     * We will want to take this min/max node's signed result, narrow it, then apply
                     * zero extension to the result. If the result is signed, our stamp system won't
                     * be able to represent the zero-extended value. Give up on this
                     * micro-optimization.
                     */
                    if (stamp.lowerBound() < 0 && stamp.upperBound() >= 0) {
                        canNarrow = false;
                    }
                }
            } else if (minMax instanceof UnsignedMinNode || minMax instanceof UnsignedMaxNode) {
                canNarrow = Long.compareUnsigned(stamp.unsignedUpperBound(), NumUtil.maxValueUnsigned(narrowBits)) <= 0;
                if (canNarrow) {
                    /*
                     * We will want to narrow this unsigned min/max, and we now know that this would
                     * be lossless. However, we might not be able to represent this in the graph as
                     * a lossless conversion due to limitations in our stamps. For example,
                     * narrowing an i32 [42 - 65535] stamp to i16 gives an unrestricted i16 stamp
                     * because the value range cannot be represented when interpreting the bounds as
                     * signed i16 values. Give up if the bounds are not unrestricted for the
                     * unsigned range of the target stamp. This is likely not a problem because in
                     * vector folds the stamp is usually unrestricted, and in vector maps this path
                     * should not be needed. Still, we might miss some rare micro-optimizations.
                     */
                    if (stamp.unsignedLowerBound() > 0 || stamp.unsignedUpperBound() < NumUtil.maxValueUnsigned(narrowBits)) {
                        canNarrow = false;
                    }
                }
            } else {
                return;
            }
            if (canNarrow) {
                // The min/max fits into the narrow bits without loss.
                if (arch.narrowedVectorInstructionAvailable(minMax, (IntegerStamp) extend.getValue().stamp(NodeView.DEFAULT))) {
                    ValueNode narrowMinMax = subgraph.addWithoutUnique(new NarrowNode(minMax, narrowBits));
                    boolean zeroExtend = extend instanceof ZeroExtendNode;
                    int wideBits = extend.getResultBits();
                    ValueNode wideMinMax = subgraph.addWithoutUnique(zeroExtend ? new ZeroExtendNode(narrowMinMax, wideBits) : new SignExtendNode(narrowMinMax, wideBits));
                    minMax.replaceAtUsages(wideMinMax, usage -> usage != narrowMinMax);
                }
            }
        }

        @Override
        public void canonicalize(StructuredGraph subgraph) {
            narrowOperations(subgraph);

            /*
             * We need subgraph canonicalization to be as thorough as possible, but canonicalization
             * can sometimes miss opportunities due to the order in which nodes are visited. Compute
             * a schedule so that we can force canonicalization in a topological order, with each
             * node only canonicalized after all of its inputs. Scheduling isn't cheap in general,
             * but our vector subgraphs contain only one block, are acyclic, and are very small.
             */
            if (!subgraph.isLastScheduleValid()) {
                new SchedulePhase(subgraph.getOptions()).apply(subgraph, context);
            }
            /*
             * If the last schedule was valid, the subgraph had the same nodes and edges as the last
             * time we were here. Even if we skipped rescheduling, we still want to canonicalize
             * again because stamps may have changed: Vector simplification propagates improved
             * stamps to parameter nodes, and this can enable new canonicalizations.
             */
            ScheduleResult schedule = subgraph.getLastSchedule();
            GraalError.guarantee(schedule.getCFG().getBlocks().length == 1, "vector subgraphs must contain exactly one block");
            List<Node> scheduleOrder = schedule.nodesFor(schedule.getNodeToBlockMap().get(subgraph.start()));
            canonicalizer.applyIncremental(subgraph, context, scheduleOrder);
        }

        @Override
        public ValueNode getLengthHint() {
            return null;
        }

        @Override
        public VectorNode simplify(VectorNode node) {
            return simplifyLengthHint(node, null);
        }

        @Override
        public VectorNode simplifyLengthHint(VectorNode node, ValueNode lengthHint) {
            VectorNode ret = simplifyCache.getAndGrow(node.asNode());
            if (ret == null) {
                ret = simplifyImpl(node, lengthHint);
                if (node.asNode().isAlive()) {
                    simplifyCache.set(node.asNode(), ret);
                }
            }
            return ret;
        }

        private VectorNode simplifyImpl(VectorNode node, ValueNode lengthHint) {
            Mark mark = graph.getMark();

            VectorNode vector = node;
            if (vector instanceof VectorPhi) {
                // break infinite cycles
                simplifyCache.set(vector.asNode(), vector);
            }

            VectorNode simplified = simplifyNode(vector, lengthHint);

            // keep simplified node alive
            VectorNodeHandle handle = graph.add(new VectorNodeHandle(simplified));

            canonicalizer.applyIncremental(graph, context, mark);
            assert handle.isAlive() : handle + " isn't kept alive";

            simplified = handle.getVector();
            handle.safeDelete();
            return simplified;
        }

        private VectorNode simplifyNode(VectorNode node, final ValueNode lengthHint) {
            if (node instanceof SimplifiableVectorNode) {
                return ((SimplifiableVectorNode) node).simplify(new VectorSimplifier() {

                    @Override
                    public VectorNode simplify(VectorNode childNode) {
                        return CachingSimplifier.this.simplifyLengthHint(childNode, lengthHint);
                    }

                    @Override
                    public VectorNode simplifyLengthHint(VectorNode childNode, ValueNode childLengthHint) {
                        return CachingSimplifier.this.simplifyLengthHint(childNode, childLengthHint);
                    }

                    @Override
                    public void canonicalize(StructuredGraph subgraph) {
                        CachingSimplifier.this.canonicalize(subgraph);
                    }

                    @Override
                    public ValueNode getLengthHint() {
                        return lengthHint;
                    }

                    @Override
                    public ConstantReflectionProvider getConstantReflection() {
                        return CachingSimplifier.this.getConstantReflection();
                    }
                });
            } else {
                return node;
            }
        }

        @Override
        public ConstantReflectionProvider getConstantReflection() {
            return context.getConstantReflection();
        }
    }

    public VectorSimplificationPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.ADDRESS_LOWERING, graphState));
    }

    @Override
    public void run(StructuredGraph graph, CoreProviders context) {
        CachingSimplifier simplifier = new CachingSimplifier(graph, context, canonicalizer.copyWithoutDeadPhiCycleDetection());
        for (Node node : graph.getNodes()) {
            if (node instanceof VectorConsumer) {
                VectorConsumer consumer = (VectorConsumer) node;
                consumer.simplifyTree(simplifier);
            }
        }
        new DeadCodeEliminationPhase().apply(graph);
    }
}
