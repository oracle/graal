/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;

/**
 * The {@code ArrayLength} instruction gets the length of an array.
 */
@NodeInfo
public class ArrayLengthNode extends FixedWithNextNode implements Canonicalizable.Unary<ValueNode>, Lowerable, Virtualizable {

    @Input private ValueNode array;

    public ValueNode array() {
        return array;
    }

    public ValueNode getValue() {
        return array;
    }

    public ArrayLengthNode(ValueNode array) {
        super(StampFactory.positiveInt());
        this.array = array;
    }

    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode length = readArrayLength(forValue, tool.getConstantReflection());
        if (length != null) {
            return length;
        }
        return this;
    }

    /**
     * Gets the length of an array if possible.
     *
     * @return a node representing the length of {@code array} or null if it is not available
     */
    public static ValueNode readArrayLength(ValueNode originalArray, ConstantReflectionProvider constantReflection) {
        ArrayLengthProvider foundArrayLengthProvider = null;
        ValueNode result = originalArray;
        while (true) {
            if (result instanceof ArrayLengthProvider) {
                foundArrayLengthProvider = (ArrayLengthProvider) result;
                break;
            }
            if (result instanceof ValueProxy) {
                result = ((ValueProxy) result).getOriginalNode();
            } else {
                break;
            }
        }

        if (foundArrayLengthProvider != null) {
            ValueNode length = foundArrayLengthProvider.length();
            if (length != null) {
                return length;
            }
        }
        ValueNode array = GraphUtil.unproxify(originalArray);
        if (constantReflection != null && array.isConstant() && !array.isNullConstant()) {
            Constant constantValue = array.asConstant();
            if (constantValue != null && constantValue.isNonNull()) {
                Integer constantLength = constantReflection.readArrayLength(constantValue);
                if (constantLength != null) {
                    return ConstantNode.forInt(constantLength);
                }
            }
        }
        return null;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @NodeIntrinsic
    public static native int arrayLength(Object array);

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(array());
        if (state != null) {
            assert state.getVirtualObject().type().isArray();
            tool.replaceWithValue(ConstantNode.forInt(state.getVirtualObject().entryCount(), graph()));
        }
    }
}
