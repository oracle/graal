/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.nodes.typesystem;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

public final class CustomTypeCheckNode extends LogicNode implements Lowerable, com.oracle.graal.graph.Node.IterableNodeType {

    @Input private ValueNode condition;
    @Input private ValueNode object;
    private final Object customType;

    public CustomTypeCheckNode(ValueNode condition, ValueNode object, Object customType) {
        this.condition = condition;
        this.object = object;
        this.customType = customType;
    }

    public ValueNode getObject() {
        return object;
    }

    public ValueNode getCondition() {
        return condition;
    }

    public Object getCustomType() {
        return customType;
    }

    public void lower(LoweringTool tool, LoweringType loweringType) {
        if (loweringType == LoweringType.BEFORE_GUARDS) {
            this.replaceAtUsages(graph().unique(new IntegerEqualsNode(condition, ConstantNode.forInt(1, graph()))));
            this.safeDelete();
        }
    }

    @Override
    public LogicNode canonical(CanonicalizerTool tool) {
        return this;
    }
}
