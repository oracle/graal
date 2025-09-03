/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.phases;

import com.oracle.svm.hosted.webimage.wasm.nodes.WasmIsNonZeroNode;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.vm.ci.meta.JavaKind;

public class WasmSimplification implements CanonicalizerPhase.CustomSimplification {
    @Override
    public void simplify(Node node, SimplifierTool tool) {
        if (introduceIsNonZeroNode(node, tool)) {
            return;
        }

        if (tryIsNullOptimization(node)) {
            return;
        }
    }

    /**
     * Replaces occurrences of x == 0 with !WasmIsNonZero(x).
     *
     * @return true iff a replacement happened
     */
    private static boolean introduceIsNonZeroNode(Node node, SimplifierTool tool) {
        if (node instanceof IntegerEqualsNode integerEqualsNode) {
            ValueNode left = integerEqualsNode.getX();
            ValueNode right = integerEqualsNode.getY();

            if (left.getStackKind() != JavaKind.Int) {
                return false;
            }

            StructuredGraph graph = (StructuredGraph) node.graph();

            LogicNode isNonZeroNode;

            // If either argument is 0, the replacement can happen
            if (left.isDefaultConstant()) {
                isNonZeroNode = new WasmIsNonZeroNode(right);
            } else if (right.isDefaultConstant()) {
                isNonZeroNode = new WasmIsNonZeroNode(left);
            } else {
                return false;
            }

            isNonZeroNode = graph.addOrUniqueWithInputs(LogicNegationNode.create(isNonZeroNode));
            node.replaceAtUsages(isNonZeroNode);

            tool.addToWorkList(isNonZeroNode.usages());
            return true;
        }

        return false;
    }

    /**
     * Optimizes {@link IsNullNode}s that are used in {@link IfNode}s and {@link ConditionalNode}s
     * by replacing it with a {@link WasmIsNonZeroNode} and swapping the branches.
     * <p>
     * The {@link WasmIsNonZeroNode} does not require any cycles when used as a boolean value as it
     * can just forward its inputs as the boolean value (0 = false, non-0 = true).
     */
    private static boolean tryIsNullOptimization(Node node) {
        StructuredGraph graph = (StructuredGraph) node.graph();
        if (node instanceof IfNode ifNode && ifNode.condition() instanceof IsNullNode isNullNode) {
            // For the IfNode, we let the node do the branch swapping for us by replacing IsNull
            // with !WasmIsNonZero
            ifNode.setCondition(graph.addOrUniqueWithInputs(LogicNegationNode.create(new WasmIsNonZeroNode(isNullNode.getValue()))));
            ifNode.eliminateNegation();
            return true;
        } else if (node instanceof ConditionalNode conditional && conditional.condition() instanceof IsNullNode isNullNode) {
            LogicNode newCondition = new WasmIsNonZeroNode(isNullNode.getValue());
            ValueNode newConditional = graph.addOrUniqueWithInputs(ConditionalNode.create(
                            newCondition,
                            conditional.falseValue(),
                            conditional.trueValue(),
                            NodeView.DEFAULT));
            conditional.replaceAtUsages(newConditional);
            GraphUtil.tryKillUnused(conditional);
            return true;
        }
        return false;
    }
}
