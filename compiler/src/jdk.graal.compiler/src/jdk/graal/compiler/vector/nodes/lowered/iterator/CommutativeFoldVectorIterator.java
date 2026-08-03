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
package jdk.graal.compiler.vector.nodes.lowered.iterator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode.AccumulatorNode;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode.BinaryMacroNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.lowered.FinishVectorConsumerNode;
import jdk.graal.compiler.vector.nodes.op.MapVectorNode;
import jdk.graal.compiler.vector.nodes.op.VectorPhi;
import jdk.graal.compiler.vector.nodes.producer.FillVectorNode;
import jdk.graal.compiler.vector.nodes.subgraph.SubGraphUtil;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.FilteredNodeIterable;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * Parallel fold for operations that are associative and commutative: In addition to a scalar
 * accumulator ({@link #current}), there is also one vector accumulator ({@link #currentVector}). If
 * the step length is equal to the current value of the vector accumulator, the next input vector is
 * folded into the vector accumulator. Otherwise, the current vector accumulator is folded into the
 * scalar accumulator, and a new vector accumulator is started.
 */
public class CommutativeFoldVectorIterator extends FoldVectorIterator {

    final int currentVectorLength;
    final VectorNode currentVector;

    public CommutativeFoldVectorIterator(ValueNode index, ValueNode current, VectorIterator[] values, int currentVectorLength, VectorNode currentVector) {
        super(index, current, values);
        this.currentVectorLength = currentVectorLength;
        this.currentVector = currentVector;
    }

    @SuppressWarnings("try")
    private CommutativeFoldVectorIterator finishFold(FoldVectorNode fold, FixedNode position) {
        if (currentVector == null) {
            return this;
        } else {
            StructuredGraph graph = position.graph();
            StructuredGraph partialFoldSubGraph = createPartialFoldSubGraph(fold);

            try (DebugCloseable positionScope = graph.withNodeSourcePosition(fold)) {
                ValueNode stepLengthNode = ConstantNode.forInt(currentVectorLength, graph);
                List<ValueNode> vectorInputs = Collections.singletonList(currentVector.asNode());
                FoldVectorNode partialFold = graph.add(new FoldVectorNode(partialFoldSubGraph, current, stepLengthNode, fold.direction(), vectorInputs, fold.getScalarInputs()));

                graph.addBeforeFixed(position, partialFold);

                CommutativeFoldVectorIterator finishIterator = new CommutativeFoldVectorIterator(index, partialFold, values, 1, null);
                finishIterator.setVectorReadCache(this.readCache);
                finishIterator.setVectorGuardCache(this.guardCache);
                return finishIterator;
            }
        }
    }

    // @formatter:off
    /*
     * For combining the initial value with the vector that contains the partially folded result,
     * we derive a subgraph with the following structure from the fold subgraph:
     *
     *       AccumulatorNode              ParameterNode
     *             |                            |
     * 0..n IntegerConvertNodes     0..n IntegerConvertNodes
     *               \                    /
     *                BinaryArithmeticNode
     *                         |
     *             0..n IntegerConvertNodes
     *                         |
     *                     ReturnNode
     *
     */
    // @formatter:on
    private static StructuredGraph createPartialFoldSubGraph(FoldVectorNode fold) {
        ArrayList<Node> nodesToCopy = selectNodesForPartialFold(fold);
        AccumulatorNode accumulator = (AccumulatorNode) nodesToCopy.get(0);
        return SubGraphUtil.createSubGraph(graph -> {
            // create the left side and the shared part
            ValueNode prevCreatedNode = null;
            int binaryArithmeticNodeIndex = -1;
            BinaryNode arithmeticNode = null;
            for (int i = 0; i < nodesToCopy.size(); i++) {
                ValueNode nodeToCopy = (ValueNode) nodesToCopy.get(i);
                assert nodeToCopy.getNodeClass().getSuccessorEdges().getCount() == 0 : nodesToCopy;

                ValueNode createdNode = (ValueNode) nodeToCopy.copyWithInputs(false);
                createdNode.clearInputs();
                createdNode = graph.addWithoutUnique(createdNode);

                if (prevCreatedNode == null) {
                    assert createdNode.getNodeClass().getInputEdges().getCount() == 0 : createdNode;
                } else if (createdNode instanceof BinaryArithmeticNode || createdNode instanceof BinaryMacroNode) {
                    assert createdNode.getNodeClass().getInputEdges().getCount() == 2 : createdNode;
                    arithmeticNode = (BinaryNode) createdNode;
                    arithmeticNode.setX(prevCreatedNode);
                    binaryArithmeticNodeIndex = i;
                } else {
                    assert createdNode.getNodeClass().getInputEdges().getCount() == 1 : createdNode;
                    UnaryNode unaryNode = (UnaryNode) createdNode;
                    unaryNode.setValue(prevCreatedNode);
                }

                prevCreatedNode = createdNode;
            }

            ValueNode bottomMostNode = prevCreatedNode;

            // create the right side
            assert NumUtil.assertNonNegativeInt(binaryArithmeticNodeIndex);
            assert nodesToCopy.get(0) instanceof AccumulatorNode : "must be an accumulator (it is skipped in the following)";
            prevCreatedNode = graph.getParameter(0);
            for (int i = 1; i < binaryArithmeticNodeIndex; i++) {
                ValueNode nodeToCopy = (ValueNode) nodesToCopy.get(i);
                assert nodeToCopy.getNodeClass().getSuccessorEdges().getCount() == 0 : nodeToCopy;

                ValueNode createdNode = (ValueNode) nodeToCopy.copyWithInputs(false);
                createdNode.clearInputs();
                createdNode = graph.addWithoutUnique(createdNode);

                assert createdNode.getNodeClass().getInputEdges().getCount() == 1 : createdNode;
                UnaryNode unaryNode = (UnaryNode) createdNode;
                unaryNode.setValue(prevCreatedNode);

                prevCreatedNode = createdNode;
            }
            arithmeticNode.setY(prevCreatedNode);

            return bottomMostNode;
        }, fold.graph(), accumulator.stamp(NodeView.DEFAULT));
    }

    private static ArrayList<Node> selectNodesForPartialFold(FoldVectorNode fold) {
        boolean addedBinaryArithmeticNode = false;
        ArrayList<Node> selectedNodes = new ArrayList<>();
        ValueNode accumulator = SubGraphUtil.getAccumulator(fold);

        Node node = accumulator;
        while (!(node instanceof ReturnNode)) {
            if (node instanceof BinaryArithmeticNode || node instanceof BinaryMacroNode) {
                if (!addedBinaryArithmeticNode) {
                    selectedNodes.add(node);
                    addedBinaryArithmeticNode = true;
                }
            } else if (node instanceof ConditionalNode) {
                // Don't select the conditional, and don't go on selecting beyond it.
                break;
            } else {
                assert node instanceof AccumulatorNode || node instanceof ReturnNode || node instanceof UnaryNode : node;
                selectedNodes.add(node);
            }

            if (node.getUsageCount() > 1) {
                FilteredNodeIterable<Node> nonConditionalUsages = node.usages().filter(usage -> !(usage instanceof ConditionalNode));
                assert nonConditionalUsages.count() == 1 : nonConditionalUsages;
                node = nonConditionalUsages.first();
            } else {
                assert node.getUsageCount() == 1 : node.usages().snapshot();
                node = node.usages().first();
            }
        }

        return selectedNodes;
    }

    @Override
    public VectorConsumerIterator next(VectorConsumer consumer, int stepLength, FixedNode position, ConstantReflectionProvider constantReflection) {
        FoldVectorNode fold = (FoldVectorNode) consumer;
        StructuredGraph graph = fold.graph();

        ValueNode stepLengthNode = ConstantNode.forInt(stepLength, graph);
        if (currentVector == null) {
            if (stepLength == 1) {
                FoldVectorIterator ret = (FoldVectorIterator) super.next(consumer, stepLength, position, constantReflection);
                return new CommutativeFoldVectorIterator(ret.index, ret.current, ret.values, 1, null);
            } else if (values.length >= 1) {
                ArrayList<ValueNode> inputVectors = new ArrayList<>();
                for (int i = 0; i < values.length; i++) {
                    ValueNode inputVector = values[i].getVector(fold.getVectorInput(i), this, position, constantReflection, stepLength).asNode();
                    inputVectors.add(inputVector);
                }

                StructuredGraph mapOp = createInitialMapSubGraph(fold);
                MapVectorNode map = graph.unique(new MapVectorNode(mapOp, inputVectors));
                return new CommutativeFoldVectorIterator(getNextIndex(graph, stepLengthNode), current, getNextValues(fold, stepLengthNode), stepLength, map);
            } else {
                assert values.length == 0 : values;
                return new CommutativeFoldVectorIterator(index, current, values, stepLength, createNeutralVector(fold)).next(consumer, stepLength, position, constantReflection);
            }
        } else if (stepLength == currentVectorLength) {
            assert stepLength > 1 : stepLength;
            ArrayList<ValueNode> inputVectors = new ArrayList<>();
            inputVectors.add(currentVector.asNode());
            for (int i = 0; i < values.length; i++) {
                ValueNode inputVector = values[i].getVector(fold.getVectorInput(i), this, position, constantReflection, stepLength).asNode();
                inputVectors.add(inputVector);
            }

            StructuredGraph mapOp = createSubsequentMapSubGraph(fold);
            MapVectorNode map = graph.unique(new MapVectorNode(mapOp, inputVectors));
            return new CommutativeFoldVectorIterator(getNextIndex(graph, stepLengthNode), current, getNextValues(fold, stepLengthNode), stepLength, map);
        } else {
            return finishFold(fold, position).next(consumer, stepLength, position, constantReflection);
        }
    }

    /**
     * Creates a subgraph that is similar to the fold's subgraph but which does not contain a vector
     * accumulator (i.e., all nodes dealing with the vector accumulator are removed and only the
     * logic for combining all the input vectors remains).
     */
    private static StructuredGraph createInitialMapSubGraph(FoldVectorNode fold) {
        StructuredGraph foldOp = fold.getOp();
        StructuredGraph mapOp = new StructuredGraph.Builder(foldOp.getOptions(), foldOp.getDebug(), foldOp.allowAssumptions()).compilationId(foldOp.compilationId()).build();
        mapOp.addDuplicates(foldOp.getNodes(), foldOp, foldOp.getNodeCount(), new Graph.DuplicationReplacement() {
            @Override
            public Node replacement(Node original) {
                if (original == foldOp.start()) {
                    return mapOp.start();
                }
                return original;
            }
        });

        // unlink all graph parts that deal with the accumulator
        Node currentNode = SubGraphUtil.getAccumulator(mapOp);
        do {
            assert currentNode.hasExactlyOneUsage() : currentNode + " must only have one usage but has " + currentNode.usages().snapshot();
            currentNode.markDeleted();
            currentNode = currentNode.usages().first();
        } while (!(currentNode instanceof BinaryArithmeticNode) && !(currentNode instanceof BinaryMacroNode));

        BinaryNode arithmeticNode = (BinaryNode) currentNode;
        ValueNode survivingNode = arithmeticNode.getX().isDeleted() ? arithmeticNode.getY() : arithmeticNode.getX();
        assert !survivingNode.isDeleted() : survivingNode;
        arithmeticNode.replaceAtUsages(survivingNode);
        return mapOp;
    }

    /**
     * Creates a subgraph that is similar to the fold's subgraph that combines the vector
     * accumulator with all vector inputs.
     */
    private static StructuredGraph createSubsequentMapSubGraph(FoldVectorNode fold) {
        StructuredGraph foldOp = fold.getOp();
        StructuredGraph mapOp = new StructuredGraph.Builder(foldOp.getOptions(), foldOp.getDebug(), foldOp.allowAssumptions()).compilationId(foldOp.compilationId()).build();
        mapOp.addDuplicates(foldOp.getNodes(), foldOp, foldOp.getNodeCount(), new Graph.DuplicationReplacement() {
            @Override
            public Node replacement(Node original) {
                if (original instanceof AccumulatorNode) {
                    AccumulatorNode acc = (AccumulatorNode) original;
                    return mapOp.unique(new ParameterNode(0, StampPair.createSingle(acc.stamp(NodeView.DEFAULT))));
                } else if (original instanceof ParameterNode) {
                    ParameterNode p = (ParameterNode) original;
                    return mapOp.unique(new ParameterNode(p.index() + 1, StampPair.create(p.stamp(NodeView.DEFAULT), p.uncheckedStamp())));
                } else if (original == foldOp.start()) {
                    return mapOp.start();
                } else {
                    return original;
                }
            }
        });
        return mapOp;
    }

    public static JavaConstant getNeutralElement(BinaryArithmeticNode<?> binaryArithmeticNode) {
        BinaryOp<?> arithmeticOp = binaryArithmeticNode.getArithmeticOp();
        Stamp arithmeticStamp = binaryArithmeticNode.stamp(NodeView.DEFAULT);
        int bits = PrimitiveStamp.getBits(arithmeticStamp);

        if (arithmeticStamp instanceof IntegerStamp) {
            JavaConstant zero = JavaConstant.forPrimitiveInt(bits, 0);
            if (arithmeticOp.isNeutral(zero)) {
                return zero;
            }
            JavaConstant one = JavaConstant.forPrimitiveInt(bits, 1);
            if (arithmeticOp.isNeutral(one)) {
                return one;
            }
            JavaConstant allOnes = JavaConstant.forPrimitiveInt(bits, CodeUtil.mask(bits));
            if (arithmeticOp.isNeutral(allOnes)) {
                return allOnes;
            }
            JavaConstant signedMin = JavaConstant.forPrimitiveInt(bits, NumUtil.minValue(bits));
            if (arithmeticOp.isNeutral(signedMin)) {
                return signedMin;
            }
            JavaConstant signedMax = JavaConstant.forPrimitiveInt(bits, NumUtil.maxValue(bits));
            if (arithmeticOp.isNeutral(signedMax)) {
                return signedMax;
            }
        } else if (arithmeticStamp instanceof FloatStamp) {
            // Only check for +/- infinity as the neutral elements of min/max.
            if (bits == Float.SIZE) {
                JavaConstant negativeInf = JavaConstant.forFloat(Float.NEGATIVE_INFINITY);
                if (arithmeticOp.isNeutral(negativeInf)) {
                    return negativeInf;
                }
                JavaConstant positiveInf = JavaConstant.forFloat(Float.POSITIVE_INFINITY);
                if (arithmeticOp.isNeutral(positiveInf)) {
                    return positiveInf;
                }
            }
            if (bits == Double.SIZE) {
                JavaConstant negativeInf = JavaConstant.forDouble(Double.NEGATIVE_INFINITY);
                if (arithmeticOp.isNeutral(negativeInf)) {
                    return negativeInf;
                }
                JavaConstant positiveInf = JavaConstant.forDouble(Double.POSITIVE_INFINITY);
                if (arithmeticOp.isNeutral(positiveInf)) {
                    return positiveInf;
                }
            }
        }

        return null;
    }

    private VectorNode createNeutralVector(FoldVectorNode fold) {
        AccumulatorNode accumulator = SubGraphUtil.getAccumulator(fold);
        ValueNode node = SubGraphUtil.findBinaryArithmeticUsage(accumulator);
        GraalError.guarantee(node != null, "fold graph must contain an accumulator and a binary usage");
        if (node instanceof BinaryMacroNode) {
            BinaryMacroNode macro = (BinaryMacroNode) node;
            return macro.initialVector(fold);
        }

        JavaConstant neutralConstant = getNeutralElement((BinaryArithmeticNode<?>) node);
        if (neutralConstant == null) {
            throw GraalError.shouldNotReachHere(node + " doesn't seem to have a neutral element"); // ExcludeFromJacocoGeneratedReport
        }
        // When mixing different types in a fold, make sure the type of the initial value of the
        // accumulator is in sync with the fold's stamp.
        Stamp foldStamp = current.stamp(NodeView.DEFAULT);
        JavaConstant adjustedConstant;
        if (neutralConstant.getJavaKind().isNumericInteger()) {
            int foldBits = PrimitiveStamp.getBits(foldStamp);
            adjustedConstant = JavaConstant.forPrimitiveInt(foldBits, ((PrimitiveConstant) neutralConstant).asLong());
        } else {
            GraalError.guarantee(foldStamp instanceof FloatStamp && neutralConstant.getJavaKind().isNumericFloat() && PrimitiveStamp.getBits(foldStamp) == neutralConstant.getJavaKind().getBitCount(),
                            "expect no implicit conversions in floating-point folds");
            adjustedConstant = neutralConstant;
        }
        ValueNode neutral = ConstantNode.forConstant(foldStamp, adjustedConstant, null, fold.graph());
        return fold.graph().unique(new FillVectorNode(neutral));
    }

    @Override
    public void addPhiInput(VectorConsumer consumer, VectorConsumerIterator input, AbstractEndNode branch) {
        CommutativeFoldVectorIterator it = (CommutativeFoldVectorIterator) input;
        FoldVectorNode fold = (FoldVectorNode) consumer;

        if (this.currentVector != null) {
            VectorPhi phi = (VectorPhi) currentVector;
            if (this.currentVectorLength == it.currentVectorLength) {
                phi.addInput(it.currentVector.asNode());

                super.addPhiInput(fold, it, branch);
                return;

            } else {
                VectorNode neutralVector = createNeutralVector(fold);
                phi.addInput(neutralVector.asNode());
            }
        }

        super.addPhiInput(fold, it.finishFold(fold, branch), branch);
    }

    @Override
    public void finishConsumer(FinishVectorConsumerNode finish) {
        transferUsagesToFinishNode(finish);
        FoldVectorNode fold = (FoldVectorNode) finish.getConsumer();

        Node replacement = finishFold(fold, finish).current;
        finish.replaceAtUsages(replacement);
        if (finish.isAlive()) {
            finish.graph().removeFixed(finish);
        }
    }
}
