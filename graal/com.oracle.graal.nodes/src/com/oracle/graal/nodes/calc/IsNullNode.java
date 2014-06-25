/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.calc;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * An IsNullNode will be true if the supplied value is null, and false if it is non-null.
 */
public final class IsNullNode extends UnaryOpLogicNode implements LIRLowerable, Virtualizable, PiPushable {

    /**
     * Constructs a new IsNullNode instruction.
     *
     * @param object the instruction producing the object to check against null
     */
    public IsNullNode(ValueNode object) {
        super(object);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // Nothing to do.
    }

    @Override
    public boolean verify() {
        assertTrue(getValue() != null, "is null input must not be null");
        assertTrue(getValue().stamp() instanceof AbstractObjectStamp, "is null input must be an object");
        return super.verify();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        Constant constant = forValue.asConstant();
        if (constant != null) {
            assert constant.getKind() == Kind.Object;
            return LogicConstantNode.forBoolean(constant.isNull());
        }
        if (StampTool.isObjectNonNull(forValue.stamp())) {
            return LogicConstantNode.contradiction();
        }
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (tool.getObjectState(getValue()) != null) {
            tool.replaceWithValue(LogicConstantNode.contradiction(graph()));
        }
    }

    @Override
    public boolean push(PiNode parent) {
        if (parent.stamp() instanceof ObjectStamp && parent.object().stamp() instanceof ObjectStamp) {
            ObjectStamp piStamp = (ObjectStamp) parent.stamp();
            ObjectStamp piValueStamp = (ObjectStamp) parent.object().stamp();
            if (piStamp.nonNull() == piValueStamp.nonNull() && piStamp.alwaysNull() == piValueStamp.alwaysNull()) {
                replaceFirstInput(parent, parent.object());
                return true;
            }
        }
        return false;
    }

    @NodeIntrinsic
    public static native IsNullNode isNull(Object object);
}
