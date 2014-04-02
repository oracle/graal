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
package com.oracle.graal.nodes.calc;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * An IsNullNode will be true if the supplied value is null, and false if it is non-null.
 */
public final class IsNullNode extends LogicNode implements Canonicalizable, LIRLowerable, Virtualizable, PiPushable {

    @Input private ValueNode object;

    public ValueNode object() {
        return object;
    }

    /**
     * Constructs a new IsNullNode instruction.
     * 
     * @param object the instruction producing the object to check against null
     */
    public IsNullNode(ValueNode object) {
        this.object = object;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // Nothing to do.
    }

    @Override
    public boolean verify() {
        assertTrue(object() != null, "is null input must not be null");
        assertTrue(object().stamp() instanceof ObjectStamp || object().stamp() instanceof IllegalStamp, "is null input must be an object");
        return super.verify();
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        Constant constant = object().asConstant();
        if (constant != null) {
            assert constant.getKind() == Kind.Object;
            return LogicConstantNode.forBoolean(constant.isNull(), graph());
        }
        if (ObjectStamp.isObjectNonNull(object.stamp())) {
            return LogicConstantNode.contradiction(graph());
        }
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (tool.getObjectState(object) != null) {
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
