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

import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatEqualsNode;
import jdk.graal.compiler.nodes.calc.FloatLessThanNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

/**
 * Processes {@link CompareNode}s that have {@link CompareNode#unorderedIsTrue()} set to
 * {@code true}.
 * <p>
 * In that case, the node should produce {@code true}, if the two inputs are not comparable under
 * the particular partial order. This generally only happens if {@code NaN} is involved.
 * <p>
 * WASM has no comparisons where unordered input produces {@code true} (except for {@code fnn.ne},
 * which isn't used by {@link CompareNode}). This phase replaces all {@link CompareNode}s with a
 * counterpart that returns {@code false} for {@link CompareNode#unorderedIsTrue()} by explicitly
 * inserting {@code NaN} checks.
 * <p>
 * The {@code NaN} checks are inserted in the form of (possibly nested) {@link ConditionalNode}s
 * that produce {@code true} if the value is {@code NaN}.
 */
public class UnorderedIsTruePhase extends BasePhase<CoreProviders> {
    private final CanonicalizerPhase canonicalizer;

    public UnorderedIsTruePhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    private static ValueNode logicToValue(ValueNode node) {
        if (node instanceof LogicNode) {
            return new ConditionalNode((LogicNode) node, ConstantNode.forInt(1), ConstantNode.forInt(0));
        } else {
            return node;
        }
    }

    /**
     * Creates nodes to convert the given {@link ValueNode} to a {@link LogicNode}.
     */
    private static LogicNode valueToLogic(ValueNode node) {
        if (node instanceof LogicNode) {
            return (LogicNode) node;
        } else {
            return new WasmIsNonZeroNode(node);
        }
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        for (CompareNode node : graph.getNodes().filter(CompareNode.class)) {
            if (!node.unorderedIsTrue()) {
                continue;
            }

            assert node instanceof FloatLessThanNode || node instanceof FloatEqualsNode : node;

            ValueNode x = node.getX();
            ValueNode y = node.getY();

            FloatStamp stampX = (FloatStamp) x.stamp(NodeView.DEFAULT);
            FloatStamp stampY = (FloatStamp) y.stamp(NodeView.DEFAULT);

            ValueNode replacement;

            if (stampX.isNaN() || stampY.isNaN()) {
                // If either is NaN, they are not comparable and the node produces true
                replacement = LogicConstantNode.tautology();
            } else {
                replacement = CompareNode.createFloatCompareNode(node.condition(), x, y, false, NodeView.DEFAULT);

                /*
                 * If either value can be NaN, we introduce a NaN check and only return the
                 * FloatLessThan result if the value equals itself (if it doesn't, it must be NaN).
                 */
                if (stampX.canBeNaN()) {
                    replacement = new ConditionalNode(new FloatEqualsNode(x, x), logicToValue(replacement), ConstantNode.forInt(1));
                }

                if (stampY.canBeNaN()) {
                    replacement = new ConditionalNode(new FloatEqualsNode(y, y), logicToValue(replacement), ConstantNode.forInt(1));
                }

                replacement = valueToLogic(replacement);
            }

            node.replaceAndDelete(graph.addOrUniqueWithInputs(replacement));
        }

        // Verification
        for (CompareNode node : graph.getNodes().filter(CompareNode.class)) {
            assert !node.unorderedIsTrue() : node;
        }

        canonicalizer.apply(graph, context);
    }
}
