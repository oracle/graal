/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

public class UnboxNode extends FloatingNode implements Virtualizable, Lowerable, Canonicalizable {

    @Input private ValueNode value;
    private final Kind boxingKind;

    public UnboxNode(ValueNode value, Kind boxingKind) {
        super(StampFactory.forKind(boxingKind.getStackKind()));
        this.value = value;
        this.boxingKind = boxingKind;
    }

    public Kind getBoxingKind() {
        return boxingKind;
    }

    public ValueNode getValue() {
        return value;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(value);
        if (state != null && state.getState() == EscapeState.Virtual) {
            tool.replaceWithValue(state.getEntry(0));
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (value.isConstant()) {
            Constant constant = value.asConstant();
            Object o = constant.asObject();
            if (o != null) {
                switch (boxingKind) {
                    case Boolean:
                        return ConstantNode.forBoolean((Boolean) o, graph());
                    case Byte:
                        return ConstantNode.forByte((Byte) o, graph());
                    case Char:
                        return ConstantNode.forChar((Character) o, graph());
                    case Short:
                        return ConstantNode.forShort((Short) o, graph());
                    case Int:
                        return ConstantNode.forInt((Integer) o, graph());
                    case Long:
                        return ConstantNode.forLong((Long) o, graph());
                    case Float:
                        return ConstantNode.forFloat((Float) o, graph());
                    case Double:
                        return ConstantNode.forDouble((Double) o, graph());
                    default:
                        ValueNodeUtil.shouldNotReachHere();
                }
            }
        } else if (value instanceof BoxNode) {
            return ((BoxNode) value).getValue();
        }
        if (usages().isEmpty()) {
            return null;
        }
        return this;
    }

    @NodeIntrinsic
    public static native boolean unbox(Boolean value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native byte unbox(Byte value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native char unbox(Character value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native double unbox(Double value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native float unbox(Float value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native int unbox(Integer value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native long unbox(Long value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native short unbox(Short value, @ConstantNodeParameter Kind kind);
}
