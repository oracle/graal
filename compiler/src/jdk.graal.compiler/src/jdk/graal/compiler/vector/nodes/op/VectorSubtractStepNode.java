/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode;
import jdk.graal.compiler.vector.nodes.producer.VectorSubtractInitNode;
import jdk.graal.compiler.vector.nodes.subgraph.SubGraphUtil;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;

import java.util.ArrayList;
import java.util.List;

import static jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode.onlyNonConditionalUsage;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

/**
 * This node represents a vectorizable subtraction in a fold calculation. Vectorization of
 * subtractions in folds is currently only possible if the path from accumulator to return never
 * traverses a Y-edge to a subtraction (see GR-46024). <br>
 * <br>
 * Let {@code a} be an array of length 4 and let vector length = 2. The loop
 *
 * <pre>
 * int acc = init;
 * for (int i = 0; i < a.length; i++) {
 *     acc -= a[i];
 * }
 * </pre>
 *
 * generates the pattern
 *
 * <pre>
 * result = init - a[0] - a[1] - a[2] - a[3];
 * </pre>
 *
 * We can reorder these subtractions and add 0 between some of them to get the pattern
 *
 * <pre>
 * init + (0 - a[0] - a[2])
 *      + (0 - a[1] - a[3]) = result;
 * </pre>
 *
 * We can now interpret each row as a vector element and each column as a loop iteration. This
 * results in an initial vector &lt;init, 0, ... , 0&gt;, a subtraction inside the loop and finally
 * a combination of the elements of the accumulator vector by addition.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public class VectorSubtractStepNode extends FoldVectorNode.BinaryMacroNode {

    public static final NodeClass<VectorSubtractStepNode> TYPE = NodeClass.create(VectorSubtractStepNode.class);

    public VectorSubtractStepNode(Stamp stamp, ValueNode x, ValueNode y) {
        super(TYPE, stamp, x, y);
    }

    /**
     * Checks if the given fold is a vectorizable computation containing a subtraction along the
     * accumulator path. If this condition is met, the according SubNodes are replaced with
     * SubtractSteps.
     *
     * @param fold FoldVectorNode to analyze
     * @return {@code true} if the computation was found to be vectorizable and nodes have been
     *         replaced
     */
    public static boolean replaceSubNodes(FoldVectorNode fold) {
        FoldVectorNode.AccumulatorNode accumulator = SubGraphUtil.getAccumulator(fold);
        List<SubNode> subtracts = new ArrayList<>();
        Node last = accumulator;
        Node cur = onlyNonConditionalUsage(accumulator);
        if (cur == null) {
            return false;
        }

        boolean isVectorizableIfReplace = true;
        while (isVectorizableIfReplace && !(cur instanceof ReturnNode)) {
            // similar accumulator path traversal to FoldVectorNode#isAssociativeAndCommutative
            if (!cur.hasExactlyOneUsage()) {
                isVectorizableIfReplace = false;
                break;
            }

            if (cur instanceof SubNode sub) {
                if (sub.stamp(NodeView.DEFAULT).isIntegerStamp() && sub.getX() == last) {
                    subtracts.add(sub);
                } else {
                    isVectorizableIfReplace = false;
                }
            } else if (cur instanceof VectorHashStepNode) {
                VectorHashStepNode hash = (VectorHashStepNode) cur;
                ArithmeticOpTable.BinaryOp<?> op = hash.getInnerBinaryOp();
                if (!IntegerStamp.OPS.getAdd().equals(op) && !IntegerStamp.OPS.getSub().equals(op)) {
                    isVectorizableIfReplace = false;
                }
            } else if (cur instanceof ConditionalNode conditional) {
                ValueNode needsToBeAcc = conditional.trueValue() == last ? conditional.falseValue() : conditional.trueValue();
                if (needsToBeAcc != accumulator) {
                    isVectorizableIfReplace = false;
                }
            } else if (!(cur instanceof AddNode) && !(cur instanceof ReturnNode)) {
                isVectorizableIfReplace = false;
            }

            last = cur;
            cur = cur.usages().first();
        }

        if (isVectorizableIfReplace && !subtracts.isEmpty() && cur instanceof ReturnNode) {
            for (SubNode sub : subtracts) {
                Node subStep = fold.getOp().addOrUniqueWithInputs(new VectorSubtractStepNode(sub.stamp(NodeView.DEFAULT), sub.getX(), sub.getY()));
                sub.replaceAndDelete(subStep);
            }
        }

        return isVectorizableIfReplace && !subtracts.isEmpty();
    }

    @Override
    public ArithmeticOpTable.BinaryOp<?> getInnerBinaryOp() {
        return IntegerStamp.OPS.getSub();
    }

    @Override
    public ValueNode expand(int operationLength, int simdLength) {
        if (simdLength == 1 && operationLength != 1) {
            // horizontal combination of elements
            return BinaryArithmeticNode.add(graph(), x, y, NodeView.DEFAULT);
        }
        return BinaryArithmeticNode.sub(graph(), x, y, NodeView.DEFAULT);
    }

    @Override
    public VectorNode initialVector(FoldVectorNode fold) {
        ValueNode initial = fold.getOriginalInitial();
        assert initial != null;
        return fold.graph().addOrUniqueWithInputs(new VectorSubtractInitNode(initial));
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return stampX.join(stampY);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        return this;
    }
}
