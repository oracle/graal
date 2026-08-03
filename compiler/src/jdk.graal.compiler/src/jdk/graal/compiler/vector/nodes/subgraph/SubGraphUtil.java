/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.subgraph;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.graalvm.collections.UnmodifiableEconomicMap;

import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode.AccumulatorNode;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode.BinaryMacroNode;
import jdk.graal.compiler.vector.nodes.op.MapVectorNode;
import jdk.graal.compiler.vector.nodes.producer.FillVectorNode;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph.DuplicationReplacement;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;

/**
 * Utilities for dealing with subgraphs. Subgraphs are graphs contained inside nodes in the main
 * ("outer") graph to model embedded computations. They have parameter and return nodes for
 * communication with the outer graph. Subgraphs are a high-level abstraction that must be
 * eliminated (typically by inlining all the nodes into the outer graph) before code generation.
 * </p>
 *
 * The loop vectorization infrastructure uses subgraphs to capture the computation that is applied
 * to each vector element. For example, the following loop:
 *
 * <pre>
 * for (int i = 0; i < length; i++) {
 *     a[i] = b[i] + c[i];
 * }
 * </pre>
 *
 * is converted by loop vectorization to a {@link MapVectorNode} with vector inputs corresponding to
 * the arrays {@code b} and {@code c}. Its inner graph looks like this:
 *
 * <pre>
 *        P(0)  P(1)
 *           \ /
 *    Start   +
 *        |  /
 *       Return
 * </pre>
 *
 * Parameter nodes inside the subgraph are classified as "vector" or "scalar" parameters depending
 * on whether they correspond to a vector input or a scalar input to the node containing the
 * subgraph. Vector parameters are those with parameter indices in the range up to (excluding)
 * {@link #SCALAR_OFFSET}. Parameters numbered {@link #SCALAR_OFFSET} and above are considered
 * scalar parameters. It is illegal to build a subgraph for a node with more than
 * {@link SubGraphUtil#SCALAR_OFFSET} vector inputs. The number of scalar inputs is not restricted.
 */
public class SubGraphUtil {

    public static final int SCALAR_OFFSET = 500;

    public static StructuredGraph createSubGraph(Function<StructuredGraph, ValueNode> buildGraph, StructuredGraph outerGraph, Stamp... args) {
        StructuredGraph graph = createSubGraph(outerGraph);

        GraalError.guarantee(graph.getNodes(ParameterNode.TYPE).isEmpty(), "fresh graph should not have any parameters yet");
        for (int i = 0; i < args.length; i++) {
            graph.addWithoutUnique(new ParameterNode(i, StampPair.createSingle(args[i])));
        }

        ValueNode result = buildGraph.apply(graph);
        ReturnNode ret = graph.add(new ReturnNode(result));
        graph.addAfterFixed(graph.start(), ret);

        return graph;
    }

    public static StructuredGraph createSubGraph(StructuredGraph outerGraph) {
        StructuredGraph graph = new StructuredGraph.Builder(outerGraph.getOptions(), outerGraph.getDebug(), outerGraph.allowAssumptions()).compilationId(
                        outerGraph.compilationId()).trackNodeSourcePosition(outerGraph.trackNodeSourcePosition()).build();
        return graph;
    }

    public static ValueNode getResult(SubGraphNode subGraphNode) {
        return getResult(subGraphNode.getOp());
    }

    private static ValueNode getResult(StructuredGraph subGraph) {
        ReturnNode ret = (ReturnNode) subGraph.start().next();
        return ret.result();
    }

    private static void propagateGVN(StructuredGraph op, List<ValueNode> inputs, int idxOffset) {
        for (int i = 1; i < inputs.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (inputs.get(i) == inputs.get(j)) {
                    ParameterNode paramI = op.getParameter(idxOffset + i);
                    ParameterNode paramJ = op.getParameter(idxOffset + j);
                    if (paramI != null && paramJ != null) {
                        paramI.replaceAtUsagesAndDelete(paramJ);
                        break;
                    }
                }
            }
        }
    }

    public static void simplifyInputs(SubGraphNode subGraphNode, boolean inlineMaps) {
        StructuredGraph op = subGraphNode.getOp();
        List<ValueNode> vectorInputs = subGraphNode.getVectorInputs();
        List<ValueNode> scalarInputs = subGraphNode.getScalarInputs();

        if (inlineMaps) {
            // inline nested maps
            for (int i = 0; i < vectorInputs.size(); i++) {
                ValueNode input = vectorInputs.get(i);
                if (input instanceof MapVectorNode) {
                    int existingVectorInputs = subGraphNode.getVectorInputs().size();
                    int newVectorInputs = ((MapVectorNode) input).getVectorInputs().size();
                    // Only inline if that does not produce an excessive number of vector inputs.
                    if (existingVectorInputs + newVectorInputs <= SCALAR_OFFSET) {
                        inlineNestedMap(subGraphNode, i, (MapVectorNode) input);
                    }
                }
            }
        }

        // propagate global value numbering
        propagateGVN(op, vectorInputs, 0);
        propagateGVN(op, scalarInputs, SCALAR_OFFSET);

        // propagate vector stamps
        for (int i = 0; i < vectorInputs.size(); i++) {
            ParameterNode param = op.getParameter(i);
            if (param != null) {
                VectorNode input = (VectorNode) vectorInputs.get(i);
                param.setStamp(input.getVectorStamp().getElementStamp());
            }
        }

        // propagate scalar stamps
        for (int i = 0; i < scalarInputs.size(); i++) {
            ParameterNode param = op.getParameter(SCALAR_OFFSET + i);
            if (param != null) {
                ValueNode input = scalarInputs.get(i);
                param.setStamp(input.stamp(NodeView.DEFAULT));
            }
        }
    }

    private static void inlineNestedMap(final SubGraphNode subGraphNode, int i, final MapVectorNode nested) {
        ArrayList<Node> nodes = new ArrayList<>(nested.getOp().getNodeCount() - 2);
        for (Node node : nested.getOp().getNodes()) {
            if (node != nested.getOp().start() && !(node instanceof ReturnNode)) {
                nodes.add(node);
            }
        }

        UnmodifiableEconomicMap<Node, Node> duplicates = subGraphNode.getOp().addDuplicates(nodes, nested.getOp(), nodes.size(), new DuplicationReplacement() {

            @Override
            public Node replacement(Node original) {
                if (original instanceof ParameterNode) {
                    return copyParameterNode(nested, subGraphNode, (ParameterNode) original);
                } else {
                    return original;
                }
            }
        });
        subGraphNode.getOp().getParameter(i).replaceAtUsagesAndDelete(duplicates.get(getResult(nested)));
    }

    public static void removeUnusedInputs(SubGraphNode subGraphNode) {
        StructuredGraph op = subGraphNode.getOp();

        removeUnusedInputs(op, subGraphNode.getVectorInputs(), 0);
        removeUnusedInputs(op, subGraphNode.getScalarInputs(), SCALAR_OFFSET);
    }

    private static void removeUnusedInputs(StructuredGraph op, List<ValueNode> inputs, int offset) {
        int paramIdx = 0;
        int inputCount = inputs.size();
        for (int i = 0; i < inputCount; i++) {
            ParameterNode param = op.getParameter(offset + i);
            if (param != null) {
                if (paramIdx < i) {
                    ParameterNode newParam = op.unique(new ParameterNode(offset + paramIdx, StampPair.createSingle(param.stamp(NodeView.DEFAULT))));
                    param.replaceAtUsagesAndDelete(newParam);
                }
                paramIdx++;
            } else {
                inputs.remove(paramIdx);
            }
        }
    }

    public static void outlineConstants(SubGraphNode subGraphNode) {
        StructuredGraph graph = subGraphNode.asNode().graph();
        StructuredGraph op = subGraphNode.getOp();

        for (Node node : op.getNodes()) {
            if (node instanceof ShiftNode) {
                // special handling: only the x input of a shift is a vector
                outlineConstant(subGraphNode, graph, node, ((ShiftNode<?>) node).getX());
            } else {
                for (Node input : node.inputs()) {
                    outlineConstant(subGraphNode, graph, node, input);
                }
            }
        }

        for (ConstantNode node : op.getNodes().filter(ConstantNode.class)) {
            if (node.usages().isEmpty()) {
                node.safeDelete();
            }
        }
    }

    private static void outlineConstant(SubGraphNode subGraphNode, StructuredGraph graph, Node node, Node input) {
        if (input instanceof ConstantNode) {
            ConstantNode constant = (ConstantNode) input;
            ConstantNode outerConstant = ConstantNode.forConstant(constant.stamp(NodeView.DEFAULT), constant.asJavaConstant(), null, graph);
            FillVectorNode fill = graph.unique(new FillVectorNode(outerConstant));

            ParameterNode param = findOrAddVectorInput(subGraphNode, fill);
            node.replaceFirstInput(input, param);
        }
    }

    /**
     * Outline subgraph operations of the form {@code UnaryNode(param)} where the subgraph input
     * corresponding to {@code param} is of the form {@code FillVector(element)}. Transform this to
     * {@code FillVector(Unary(element))} outside the subgraph.
     */
    public static void outlineUnaryFills(SubGraphNode subGraphNode) {
        StructuredGraph outerGraph = subGraphNode.asNode().graph();
        List<ValueNode> vectorInputs = subGraphNode.getVectorInputs();
        for (int i = 0; i < vectorInputs.size(); i++) {
            ValueNode input = vectorInputs.get(i);
            if (input instanceof FillVectorNode) {
                ParameterNode param = subGraphNode.getOp().getParameter(i);
                if (param != null && param.hasExactlyOneUsage() && param.singleUsage() instanceof UnaryNode) {
                    UnaryNode innerUnary = (UnaryNode) param.singleUsage();
                    /*
                     * copyWithInputs(false) preserves the current input edges. Detach the inner
                     * parameter first so the copy does not keep an input from the inner graph when
                     * we add it to the outer graph below.
                     */
                    param.replaceAtUsages(null);
                    UnaryNode outerUnary = (UnaryNode) innerUnary.copyWithInputs(false);
                    Stamp newParamStamp = outerUnary.stamp(NodeView.DEFAULT);
                    ParameterNode innerReplacement = param;
                    if (!param.stamp(NodeView.DEFAULT).isCompatible(newParamStamp)) {
                        /*
                         * The operation we outlined changes the value's type (e.g., a float->int
                         * reinterpret). We must change the parameter's type as well.
                         */
                        GraalError.guarantee(param.hasNoUsages(), "parameter should only have been used by the outlined unary: %s", param);
                        param.safeDelete();
                        innerReplacement = subGraphNode.getOp().unique(new ParameterNode(i, StampPair.createSingle(newParamStamp)));
                    }
                    innerUnary.replaceAtUsagesAndDelete(innerReplacement);
                    FillVectorNode newFill = outerGraph.addOrUniqueWithInputs(new FillVectorNode(outerUnary));
                    outerUnary.setValue(((FillVectorNode) input).getElement());
                    vectorInputs.set(i, newFill);
                }
            }
        }
    }

    private static Node copyParameterNode(final SubGraphNode source, final SubGraphNode target, ParameterNode param) {
        if (param.index() < SCALAR_OFFSET) {
            return findOrAddVectorInput(target, (VectorNode) source.getVectorInputs().get(param.index()));
        } else {
            return findOrAddScalarInput(target, source.getScalarInputs().get(param.index() - SCALAR_OFFSET));
        }
    }

    public static ParameterNode findOrAddVectorInput(SubGraphNode subGraphNode, VectorNode input) {
        StructuredGraph op = subGraphNode.getOp();
        List<ValueNode> inputs = subGraphNode.getVectorInputs();

        int index = inputs.indexOf(input.asNode());
        if (index < 0) {
            index = inputs.size();
            inputs.add(input.asNode());
        }
        ParameterNode ret = op.getParameter(index);
        if (ret == null) {
            ret = op.unique(new ParameterNode(index, StampPair.createSingle(input.getVectorStamp().getElementStamp())));
        }

        assert inputs.size() < SCALAR_OFFSET : input;
        return ret;
    }

    public static ParameterNode findOrAddScalarInput(SubGraphNode subGraphNode, ValueNode input) {
        StructuredGraph op = subGraphNode.getOp();
        List<ValueNode> inputs = subGraphNode.getScalarInputs();

        int index = inputs.indexOf(input.asNode());
        if (index < 0) {
            index = inputs.size();
            inputs.add(input.asNode());
        }
        int scalarIndex = index + SCALAR_OFFSET;
        ParameterNode ret = op.getParameter(scalarIndex);
        if (ret == null) {
            ret = op.unique(new ParameterNode(scalarIndex, StampPair.createSingle(input.stamp(NodeView.DEFAULT))));
        }

        return ret;
    }

    public static Node copyTransitive(final SubGraphNode source, Node node, final SubGraphNode target) {
        NodeBitMap transitive = new NodeBitMap(source.getOp());
        markInputs(node, transitive);

        UnmodifiableEconomicMap<Node, Node> replacements = target.getOp().addDuplicates(transitive, source.getOp(), transitive.count(), new DuplicationReplacement() {

            @Override
            public Node replacement(Node original) {
                if (original instanceof ParameterNode) {
                    return copyParameterNode(source, target, (ParameterNode) original);
                } else {
                    return original;
                }
            }
        });

        return replacements.get(node);
    }

    private static void markInputs(Node node, NodeBitMap visited) {
        if (visited.contains(node)) {
            return;
        }

        visited.mark(node);
        for (Node input : node.inputs()) {
            markInputs(input, visited);
        }
    }

    public static AccumulatorNode getAccumulator(SubGraphNode subGraphNode) {
        return getAccumulator(subGraphNode.getOp());
    }

    /**
     * Return the accumulator node in {@code subGraph}. The subgraph is expected to contain an
     * accumulator, and this method will raise an error if it does not.
     */
    public static AccumulatorNode getAccumulator(StructuredGraph subGraph) {
        AccumulatorNode result = findAccumulator(subGraph);
        if (result != null) {
            return result;
        }
        throw GraalError.shouldNotReachHere("unexpected null"); // ExcludeFromJacocoGeneratedReport
    }

    /**
     * Variant of {@link #getAccumulator(StructuredGraph)} that returns {@code null} if the subgraph
     * does not contain an accumulator.
     */
    public static AccumulatorNode getAccumulatorAllowNull(StructuredGraph subGraph) {
        return findAccumulator(subGraph);
    }

    /**
     * Checks whether the subgraph contains a valid accumulator for a fold. This must be an
     * {@link AccumulatorNode} that is used by a {@link BinaryArithmeticNode}, otherwise we might
     * not get a valid fold, e.g.:
     *
     * <pre>
     * for (int i = 0; i < n; i++) {
     *     if (array[i] == x) {
     *         found = true;
     *     }
     * }
     * </pre>
     *
     * Here "found" is recognized as the accumulator, but we cannot vectorize the scalar assignment
     * to it.
     *
     * @param subGraph the subgraph to be checked
     * @return {@code true} if the subgraph contains an accumulator used by a binary operation
     */
    public static boolean containsValidAccumulator(StructuredGraph subGraph) {
        AccumulatorNode accumulator = findAccumulator(subGraph);
        if (accumulator != null) {
            return findBinaryArithmeticUsage(accumulator) != null;
        }
        return false;
    }

    private static AccumulatorNode findAccumulator(StructuredGraph subGraph) {
        for (Node node : subGraph.getNodes()) {
            if (node instanceof AccumulatorNode) {
                return (AccumulatorNode) node;
            }
        }
        return null;
    }

    /**
     * Find the unique binary arithmetic (or macro) usage of the accumulator in a fold graph.
     * Returns {@code null} if there is no such binary usage.
     */
    public static ValueNode findBinaryArithmeticUsage(AccumulatorNode accumulator) {
        NodeFlood flood = new NodeFlood(accumulator.graph());
        flood.addAll(accumulator.usages());
        for (Node usage : flood) {
            if (usage instanceof BinaryArithmeticNode || usage instanceof BinaryMacroNode) {
                return (ValueNode) usage;
            }
            flood.addAll(usage.usages());
        }
        return null;
    }

    /**
     * Determine if a node is a scalar (loop invariant).
     */
    public static boolean isScalarInput(ValueNode n) {
        return n.isConstant() || (n instanceof ParameterNode p && p.index() >= SCALAR_OFFSET);
    }
}
