/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes;

import com.oracle.graal.cri.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.DeoptimizeNode.DeoptAction;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

public final class FixedGuardNode extends FixedWithNextNode implements Simplifiable, Lowerable, LIRLowerable, Node.IterableNodeType {

    @Input private final NodeInputList<BooleanNode> conditions;
    private final long leafGraphId;

    public FixedGuardNode(BooleanNode condition, long leafGraphId) {
        super(StampFactory.illegal());
        this.leafGraphId = leafGraphId;
        this.conditions = new NodeInputList<>(this, new BooleanNode[] {condition});
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        for (BooleanNode condition : conditions()) {
            gen.emitGuardCheck(condition, leafGraphId);
        }
    }

    public void addCondition(BooleanNode x) {
        conditions.add(x);
    }

    public NodeInputList<BooleanNode> conditions() {
        return conditions;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        for (BooleanNode n : conditions.snapshot()) {
            if (n instanceof ConstantNode) {
                ConstantNode c = (ConstantNode) n;
                if (c.asConstant().asBoolean()) {
                    conditions.remove(n);
                } else {
                    FixedNode next = this.next();
                    if (next != null) {
                        tool.deleteBranch(next);
                    }
                    setNext(graph().add(new DeoptimizeNode(DeoptAction.InvalidateRecompile, leafGraphId)));
                    return;
                }
            }
        }
        if (conditions.isEmpty()) {
            ((StructuredGraph) graph()).removeFixed(this);
        }
    }

    @Override
    public void lower(CiLoweringTool tool) {
        AnchorNode newAnchor = graph().add(new AnchorNode());
        for (BooleanNode b : conditions) {
            newAnchor.addGuard((GuardNode) tool.createGuard(b, leafGraphId));
        }
        ((StructuredGraph) graph()).replaceFixedWithFixed(this, newAnchor);
    }
}
