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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;


public final class BoxNode extends AbstractStateSplit implements StateSplit, Node.IterableNodeType, Canonicalizable {

    @Input private ValueNode source;
    private int bci;
    private Kind sourceKind;

    public BoxNode(ValueNode value, ResolvedJavaType type, Kind sourceKind, int bci) {
        super(StampFactory.exactNonNull(type));
        this.source = value;
        this.bci = bci;
        this.sourceKind = sourceKind;
        assert value.kind() != Kind.Object : "can only box from primitive type";
    }

    public ValueNode source() {
        return source;
    }


    public Kind getSourceKind() {
        return sourceKind;
    }

    public void expand(BoxingMethodPool pool) {
        ResolvedJavaMethod boxingMethod = pool.getBoxingMethod(sourceKind);
        MethodCallTargetNode callTarget = graph().add(new MethodCallTargetNode(InvokeKind.Static, boxingMethod, new ValueNode[]{source}, boxingMethod.getSignature().getReturnType(boxingMethod.getDeclaringClass())));
        InvokeNode invokeNode = graph().add(new InvokeNode(callTarget, bci, -1));
        invokeNode.setProbability(this.probability());
        invokeNode.setStateAfter(stateAfter());
        ((StructuredGraph) graph()).replaceFixedWithFixed(this, invokeNode);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {

        if (source.isConstant()) {
            Constant sourceConstant = source.asConstant();
            switch (sourceKind) {
                case Boolean:
                    return ConstantNode.forObject(Boolean.valueOf(sourceConstant.asBoolean()), tool.runtime(), graph());
                case Byte:
                    return ConstantNode.forObject(Byte.valueOf((byte) sourceConstant.asInt()), tool.runtime(), graph());
                case Char:
                    return ConstantNode.forObject(Character.valueOf((char) sourceConstant.asInt()), tool.runtime(), graph());
                case Short:
                    return ConstantNode.forObject(Short.valueOf((short) sourceConstant.asInt()), tool.runtime(), graph());
                case Int:
                    return ConstantNode.forObject(Integer.valueOf(sourceConstant.asInt()), tool.runtime(), graph());
                case Long:
                    return ConstantNode.forObject(Long.valueOf(sourceConstant.asLong()), tool.runtime(), graph());
                case Float:
                    return ConstantNode.forObject(Float.valueOf(sourceConstant.asFloat()), tool.runtime(), graph());
                case Double:
                    return ConstantNode.forObject(Double.valueOf(sourceConstant.asDouble()), tool.runtime(), graph());
                default:
                    assert false : "Unexpected source kind for boxing";
                    break;

            }
        }

        for (Node usage : usages()) {
            if (usage != stateAfter()) {
                return this;
            }
        }
        replaceAtUsages(null);
        return null;
    }
}
