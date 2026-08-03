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
package jdk.graal.compiler.vector.nodes.op;

import static jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.graalvm.collections.UnmodifiableEconomicMap;

import jdk.graal.compiler.vector.nodes.AbstractVectorNode;
import jdk.graal.compiler.vector.nodes.LowerableVectorNode;
import jdk.graal.compiler.vector.nodes.ShiftableVectorNode;
import jdk.graal.compiler.vector.nodes.SimdifyableVectorOperation;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorTransformationIterator;
import jdk.graal.compiler.vector.nodes.producer.FillVectorNode;
import jdk.graal.compiler.vector.nodes.subgraph.SubGraphNode;
import jdk.graal.compiler.vector.nodes.subgraph.SubGraphUtil;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph.DuplicationReplacement;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AbsNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MinMaxNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.NarrowableArithmeticNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.calc.OrNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.UnaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdBroadcastNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo
public final class MapVectorNode extends AbstractVectorNode implements VectorTransformation, SubGraphNode, ShiftableVectorNode, SimdifyableVectorOperation, LowerableVectorNode, Canonicalizable {
    public static final NodeClass<MapVectorNode> TYPE = NodeClass.create(MapVectorNode.class);

    protected StructuredGraph op;
    @Input NodeInputList<ValueNode> vectorInputs;
    @Input NodeInputList<ValueNode> scalarInputs;

    public MapVectorNode(Stamp stamp, StructuredGraph graph) {
        this(stamp, graph, Collections.emptyList(), Collections.emptyList());
    }

    public MapVectorNode(StructuredGraph op, List<? extends ValueNode> inputs) {
        this(op, inputs, Collections.emptyList());
    }

    public MapVectorNode(StructuredGraph op, List<? extends ValueNode> vectorInputs, List<? extends ValueNode> scalarInputs) {
        this(getReturnStamp(op), op, vectorInputs, scalarInputs);
        assert vectorInputs.size() > 0 : vectorInputs;
    }

    public MapVectorNode(Stamp stamp, StructuredGraph op, List<? extends ValueNode> vectorInputs, List<? extends ValueNode> scalarInputs) {
        super(TYPE, stamp);
        this.op = op;
        this.vectorInputs = new NodeInputList<>(this, vectorInputs);
        this.scalarInputs = new NodeInputList<>(this, scalarInputs);
    }

    public static MapVectorNode map(StructuredGraph graph, Function<StructuredGraph, ValueNode> op, ValueNode... inputs) {
        ArrayList<ValueNode> inputValues = new ArrayList<>(inputs.length);
        Stamp[] stamps = new Stamp[inputs.length];

        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] instanceof VectorNode) {
                inputValues.add(inputs[i]);
                stamps[i] = ((VectorNode) inputs[i]).getVectorStamp().getElementStamp();
            } else {
                inputValues.add(graph.unique(new FillVectorNode(inputs[i])));
                stamps[i] = inputs[i].stamp(NodeView.DEFAULT);
            }
        }

        StructuredGraph subgraph = SubGraphUtil.createSubGraph(op, graph, stamps);
        return graph.unique(new MapVectorNode(subgraph, inputValues));
    }

    private static VectorStamp getReturnStamp(StructuredGraph graph) {
        ReturnNode ret = (ReturnNode) graph.start().next();
        return new VectorStamp(ret.result().stamp(NodeView.DEFAULT));
    }

    @Override
    public StructuredGraph getOp() {
        return op;
    }

    @Override
    public List<ValueNode> getVectorInputs() {
        return vectorInputs;
    }

    @Override
    public List<ValueNode> getScalarInputs() {
        return scalarInputs;
    }

    @Override
    public boolean verifyNode() {
        assertTrue(vectorInputs.size() < SubGraphUtil.SCALAR_OFFSET, "too many vector inputs");
        for (ParameterNode param : op.getNodes(ParameterNode.TYPE)) {
            int idx = param.index();
            if (!SubGraphUtil.isScalarInput(param)) {
                assertTrue(idx < vectorInputs.size() && vectorInputs.get(idx) != null, "missing vector input for MapVector: %s", idx, param);
            } else {
                idx -= SubGraphUtil.SCALAR_OFFSET;
                assertTrue(idx < scalarInputs.size() && scalarInputs.get(idx) != null, "missing scalar input for MapVector: %s", idx, param);
            }
        }
        return super.verifyNode();
    }

    @Override
    public ValueNode simdify(VectorArchitecture arch, final ValueNode... simdInputs) {
        // propagate simd stamps in inner graph
        for (ParameterNode param : op.getNodes(ParameterNode.TYPE)) {
            int idx = param.index();
            if (idx < vectorInputs.size()) {
                param.setStamp(simdInputs[idx].stamp(NodeView.DEFAULT));
            }
        }

        propagateStamps(arch, SubGraphUtil.getResult(this), getOp().createNodeBitMap());

        // inline inner graph into main graph
        NodeIterable<FloatingNode> nodes = op.getNodes().filter(FloatingNode.class);
        UnmodifiableEconomicMap<Node, Node> duplicates = graph().addDuplicates(nodes, op, op.getNodeCount() - 2, new DuplicationReplacement() {

            @Override
            public Node replacement(Node original) {
                if (original instanceof ParameterNode p) {
                    int idx = p.index();
                    if (!SubGraphUtil.isScalarInput(p)) {
                        return simdInputs[idx];
                    } else {
                        return scalarInputs.get(idx - SubGraphUtil.SCALAR_OFFSET);
                    }
                } else {
                    return original;
                }
            }
        });

        return (ValueNode) duplicates.get(SubGraphUtil.getResult(this));
    }

    private void propagateStamps(VectorArchitecture arch, ValueNode node, NodeBitMap visited) {
        /*
         * We know the inner graph contains no phi, so the simple recursive algorithm terminates.
         * However, we need to avoid exponential traversals of large graphs with lots of sharing.
         */
        if (visited.isMarked(node)) {
            return;
        }

        for (ValueNode input : node.inputs().filter(ValueNode.class)) {
            propagateStamps(arch, input, visited);
        }
        expandSimdInnerGraph(arch, node);
        if (node.isAlive()) {
            visited.mark(node);
        }
    }

    /*
     * The shape of the scalar operation may be different from that of a vector one
     */
    private void expandSimdInnerGraph(VectorArchitecture arch, ValueNode value) {
        if (value instanceof AbsNode abs && abs.getValue().stamp(NodeView.DEFAULT) instanceof IntegerStamp narrowStamp && narrowStamp.getBits() < Integer.SIZE) {
            /*
             * Loop vectorization can narrow operations to subword sizes. In the scalar tail
             * consumer or the scalar alignment loop we then perform scalar operations on subword
             * sizes. This is fine for normal arithmetic operations where we only care about the low
             * bits. But abs is special: Our targets don't have narrowed abs operations, and the
             * code patterns we generate (shifting sign bits right, or using a signed compare)
             * expect values to be correctly sign extended.
             *
             * Therefore, when expanding this scalar subword abs, explicitly sign extend its input
             * and narrow the result back.
             */
            ValueNode extendedInput = SignExtendNode.create(abs.getValue(), Integer.SIZE, NodeView.DEFAULT);
            ValueNode extendedAbs = UnaryArithmeticNode.unaryIntegerOp(extendedInput, NodeView.DEFAULT, IntegerStamp.OPS.getAbs());
            ValueNode narrowedResult = op.addOrUniqueWithInputs(NarrowNode.create(extendedAbs, narrowStamp.getBits(), NodeView.DEFAULT));
            abs.replaceAndDelete(narrowedResult);
            return;
        } else if (value instanceof NarrowableArithmeticNode &&
                        ((ValueNode) value.inputs().first()).stamp(NodeView.DEFAULT) instanceof IntegerStamp integerStamp &&
                        integerStamp.getBits() < Integer.SIZE) {
            /* Sanity checking for all other known narrowable nodes in vector subgraphs. */
            boolean hasKnownCorrectNarrowSemantics = false;
            if (value instanceof AddNode || value instanceof SubNode || value instanceof MulNode || value instanceof NegateNode) {
                /* Basic arithmetic operations that only care about low bits. */
                hasKnownCorrectNarrowSemantics = true;
            } else if (value instanceof AndNode || value instanceof OrNode || value instanceof XorNode || value instanceof NotNode) {
                /* Bitwise operations that only care about low bits. */
                hasKnownCorrectNarrowSemantics = true;
            } else if (value instanceof LeftShiftNode) {
                /* Left shift only cares about low bits. */
                hasKnownCorrectNarrowSemantics = true;
            } else if (value instanceof RightShiftNode || value instanceof UnsignedRightShiftNode) {
                /* Right shift cares about high bits, but should never be narrowed to subwords. */
                throw GraalError.shouldNotReachHere("Unexpected narrowed right shift %s, stamp %s in %s".formatted(value, value.stamp(NodeView.DEFAULT), this));
            } else if (value instanceof MinMaxNode) {
                /*
                 * Min/max nodes use comparisons that care about high bits. Their expansion is
                 * careful to check the target's lowest compare width and perform the appropriate
                 * sign or zero extension as needed.
                 */
                hasKnownCorrectNarrowSemantics = true;
            }
            if (!hasKnownCorrectNarrowSemantics) {
                throw GraalError.shouldNotReachHere("Unexpected narrowed node %s, stamp %s in %s".formatted(value, value.stamp(NodeView.DEFAULT), this));
            }
        }
        if (value instanceof ShiftNode<?> s) {
            if (!(s.getX().stamp(NodeView.DEFAULT) instanceof SimdStamp xStamp)) {
                value.inferStamp();
                return;
            }
            IntegerStamp eStamp = (IntegerStamp) xStamp.getComponent(0);

            if (s.getY().stamp(NodeView.DEFAULT) instanceof IntegerStamp intYStamp && arch.shouldBroadcastVectorShiftCount(xStamp, intYStamp)) {
                // Broadcast the shift count of a shift if it is desirable
                ValueNode newY = s.getY();
                if (intYStamp.getBits() != eStamp.getBits()) {
                    newY = IntegerConvertNode.convert(newY, eStamp.unrestricted(), true, op, NodeView.DEFAULT);
                    newY.inferStamp();
                }
                newY = op.unique(new SimdBroadcastNode(newY, xStamp.getVectorLength()));
                newY.inferStamp();
                s.setY(newY);
            } else if (s.getY().stamp(NodeView.DEFAULT) instanceof SimdStamp && xStamp.getComponent(0).getStackKind() == JavaKind.Long) {
                // A scalar shift has an int as its shift count while a vector one may have a long
                ZeroExtendNode cast = op.unique(new ZeroExtendNode(s.getY(), Integer.SIZE, Long.SIZE));
                s.setY(cast);
                cast.inferStamp();
            }
        }
        value.inferStamp();
    }

    @Override
    public VectorNode shift(ValueNode index, GuardingNode guard, FixedNode insertBefore, ConstantReflectionProvider constantReflection) {
        List<ValueNode> newInputs = AbstractVectorNode.shift(vectorInputs, index, guard, insertBefore, constantReflection);
        MapVectorNode ret = graph().unique(new MapVectorNode((StructuredGraph) op.copy(op.getDebug()), newInputs, scalarInputs));
        return ret;
    }

    @Override
    public VectorNode simplify(VectorSimplifier simplifier) {
        for (int i = 0; i < vectorInputs.count(); i++) {
            VectorNode input = (VectorNode) vectorInputs.get(i);
            VectorNode simplified = simplifier.simplify(input);

            if (input != simplified) {
                vectorInputs.set(i, simplified.asNode());
            }

            if (simplified instanceof ConcatVectorNode) {
                VectorNode ret = pushThroughConcat((ConcatVectorNode) simplified, simplifier.getConstantReflection());
                return simplifier.simplify(ret);
            }
        }

        if (graph().allowAssumptions() == AllowAssumptions.YES && op.allowAssumptions() == AllowAssumptions.NO) {
            /*
             * Rare case: The outer graph has assumptions, but the inner one doesn't. This can
             * happen when this map node was inlined from a snippet. When we try to inline other map
             * nodes into this one, this can cause an error to be raised because a graph with
             * assumptions must not be inlined into one without assumptions.
             */
            boolean mustUseAssumptions = false;
            for (ValueNode vectorInput : vectorInputs) {
                if (vectorInput instanceof MapVectorNode otherMap && otherMap.op.allowAssumptions() == AllowAssumptions.YES) {
                    mustUseAssumptions = true;
                    break;
                }
            }
            if (mustUseAssumptions) {
                op = op.copyWithAssumptions(graph().getAssumptions(), getDebug());
            }
        }

        SubGraphUtil.simplifyInputs(this, true);

        simplifier.canonicalize(op);

        SubGraphUtil.removeUnusedInputs(this);
        SubGraphUtil.outlineConstants(this);
        SubGraphUtil.outlineUnaryFills(this);

        if (SubGraphUtil.getResult(this) instanceof ParameterNode result) {
            int idx = result.index();
            if (!SubGraphUtil.isScalarInput(result)) {
                return (VectorNode) vectorInputs.get(result.index());
            } else {
                return graph().addOrUnique(new FillVectorNode(scalarInputs.get(idx - SubGraphUtil.SCALAR_OFFSET)));
            }
        }

        return this;
    }

    private VectorNode pushThroughConcat(ConcatVectorNode concat, ConstantReflectionProvider constantReflection) {
        List<ValueNode> xInputs = new ArrayList<>(vectorInputs.size());
        List<ValueNode> yInputs = new ArrayList<>(vectorInputs.size());

        for (ValueNode input : vectorInputs) {
            if (input == concat) {
                xInputs.add(concat.x().asNode());
                yInputs.add(concat.y().asNode());
            } else {
                xInputs.add(input);
                yInputs.add(AbstractVectorNode.shift((VectorNode) input, concat.getXLength(), null, constantReflection).asNode());
            }
        }

        MapVectorNode xMap = graph().unique(new MapVectorNode((StructuredGraph) op.copy(op.getDebug()), xInputs, scalarInputs));
        MapVectorNode yMap = graph().unique(new MapVectorNode((StructuredGraph) op.copy(op.getDebug()), yInputs, scalarInputs));
        return graph().unique(new ConcatVectorNode(xMap, concat.getXLength(), yMap.asNode(), concat.getYLength()));
    }

    @Override
    public VectorIterator createInitialIterator(AnchoringNode anchor, TargetDescription target) {
        return VectorTransformationIterator.createInitialIterator(this, anchor, target);
    }

    @Override
    public VectorIterator createPhiIterator(AbstractMergeNode merge, AnchoringNode anchor, TargetDescription target) {
        return VectorTransformationIterator.createPhiIterator(this, merge, anchor, target);
    }

    @Override
    public VectorTransformation createCopy(FixedNode insertBefore, ValueNode... newInputs) {
        return graph().unique(new MapVectorNode((StructuredGraph) op.copy(op.getDebug()), Arrays.asList(newInputs), scalarInputs));
    }

    @Override
    protected void afterClone(Node node) {
        MapVectorNode other = (MapVectorNode) node;
        op = (StructuredGraph) other.op.copy(getDebug());
        super.afterClone(other);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (SubGraphUtil.getResult(this) instanceof ParameterNode param && !SubGraphUtil.isScalarInput(param)) {
            /* This is an identity map that returns a parameter directly. */
            return vectorInputs.get(param.index());
        }
        return this;
    }
}
