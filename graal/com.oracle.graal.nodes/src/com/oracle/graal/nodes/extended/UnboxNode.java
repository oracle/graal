/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

public class UnboxNode extends UnaryNode implements Virtualizable, Lowerable {

    private final Kind boxingKind;

    public UnboxNode(ValueNode value, Kind boxingKind) {
        super(StampFactory.forKind(boxingKind.getStackKind()), value);
        this.boxingKind = boxingKind;
    }

    public Kind getBoxingKind() {
        return boxingKind;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(getValue());
        if (state != null && state.getState() == EscapeState.Virtual) {
            ResolvedJavaType objectType = state.getVirtualObject().type();
            ResolvedJavaType expectedType = tool.getMetaAccessProvider().lookupJavaType(boxingKind.toBoxedJavaClass());
            if (objectType.equals(expectedType)) {
                tool.replaceWithValue(state.getEntry(0));
            }
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            Constant constant = forValue.asConstant();
            Constant unboxed = tool.getConstantReflection().unboxPrimitive(constant);
            if (unboxed != null && unboxed.getKind() == boxingKind) {
                return ConstantNode.forConstant(unboxed, tool.getMetaAccess());
            }
        } else if (forValue instanceof BoxNode) {
            BoxNode box = (BoxNode) forValue;
            if (boxingKind == box.getBoxingKind()) {
                return box.getValue();
            }
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
