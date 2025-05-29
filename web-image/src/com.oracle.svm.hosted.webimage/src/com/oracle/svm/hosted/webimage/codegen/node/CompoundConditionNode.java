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
package com.oracle.svm.hosted.webimage.codegen.node;

import static jdk.graal.compiler.nodeinfo.InputType.Condition;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;

@NodeInfo(allowedUsageTypes = {Condition}, size = SIZE_1, cycles = CYCLES_1)
public class CompoundConditionNode extends LogicNode implements Canonicalizable.BinaryCommutative<LogicNode> {
    public static final NodeClass<CompoundConditionNode> TYPE = NodeClass.create(CompoundConditionNode.class);
    @Input(InputType.Condition) LogicNode x;
    @Input(InputType.Condition) LogicNode y;
    protected final CompoundOp op;

    public enum CompoundOp {
        // X && Y
        XaaY,
        // X || Y
        XooY
    }

    public CompoundConditionNode(LogicNode x, LogicNode y, CompoundOp op) {
        super(TYPE);
        this.x = x;
        this.y = y;
        this.op = op;
    }

    @Override
    public LogicNode getX() {
        return x;
    }

    @Override
    public LogicNode getY() {
        return y;
    }

    public CompoundOp getOp() {
        return op;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Node maybeCommuteInputs() {
        if (!y.isConstant() && (x.isConstant() || x.getId() > y.getId())) {
            LogicNode tmp = x;
            x = y;
            y = tmp;
            if (graph() != null) {
                // See if this node already exists
                LogicNode duplicate = graph().findDuplicate(this);
                if (duplicate != null) {
                    return duplicate;
                }
            }
        }
        return this;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, LogicNode forX, LogicNode forY) {
        return switch (this.getOp()) {
            case XaaY -> {
                if (forX.isTautology()) {
                    yield forY;
                } else if (forY.isTautology()) {
                    yield forX;
                } else if (forX.isContradiction() || forY.isContradiction()) {
                    yield LogicConstantNode.contradiction();
                } else {
                    yield this;
                }
            }
            case XooY -> {
                if (forX.isContradiction()) {
                    yield forY;
                } else if (forY.isContradiction()) {
                    yield forX;
                } else if (forX.isTautology() || forY.isTautology()) {
                    yield LogicConstantNode.tautology();
                } else {
                    yield this;
                }
            }
        };
    }
}
