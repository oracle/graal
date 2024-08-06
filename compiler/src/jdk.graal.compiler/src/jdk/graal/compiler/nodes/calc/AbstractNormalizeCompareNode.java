/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.calc;

import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.options.OptionValues;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Returns -1, 0, or 1 if either x &lt; y, x == y, or x &gt; y.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_2, size = SIZE_2)
public abstract class AbstractNormalizeCompareNode extends BinaryNode implements IterableNodeType {
    public static final NodeClass<AbstractNormalizeCompareNode> TYPE = NodeClass.create(AbstractNormalizeCompareNode.class);

    public AbstractNormalizeCompareNode(NodeClass<? extends BinaryNode> c, JavaKind kind, ValueNode x, ValueNode y) {
        super(c, StampFactory.forInteger(kind, -1, 1), x, y);
    }

    @Override
    public boolean inferStamp() {
        return false;
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return stamp(NodeView.DEFAULT);
    }

    protected static ValueNode tryConstantFold(ValueNode x, ValueNode y, boolean isUnorderedLess, boolean unsigned, JavaKind kind, ConstantReflectionProvider constantReflection) {
        LogicNode result = CompareNode.tryConstantFold(CanonicalCondition.EQ, x, y, null, false);
        if (result instanceof LogicConstantNode) {
            LogicConstantNode logicConstantNode = (LogicConstantNode) result;
            LogicNode resultLT = CompareNode.tryConstantFold(unsigned ? CanonicalCondition.BT : CanonicalCondition.LT, x, y, constantReflection, isUnorderedLess);
            if (resultLT instanceof LogicConstantNode) {
                LogicConstantNode logicConstantNodeLT = (LogicConstantNode) resultLT;
                if (logicConstantNodeLT.getValue()) {
                    return ConstantNode.forIntegerKind(kind, -1);
                } else if (logicConstantNode.getValue()) {
                    return ConstantNode.forIntegerKind(kind, 0);
                } else {
                    return ConstantNode.forIntegerKind(kind, 1);
                }
            }
        }
        return null;
    }

    public abstract LogicNode createEqualComparison();

    public abstract LogicNode createEqualComparison(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth, NodeView view);

    public LogicNode createLowerComparison() {
        return createLowerComparison(false);
    }

    public LogicNode createLowerComparison(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth, NodeView view) {
        return createLowerComparison(false, constantReflection, metaAccess, options, smallestCompareWidth, view);
    }

    public abstract LogicNode createLowerComparison(boolean swapInputs);

    public abstract LogicNode createLowerComparison(boolean swapInputs, ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options,
                    Integer smallestCompareWidth, NodeView view);
}
