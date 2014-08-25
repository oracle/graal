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
package com.oracle.graal.nodes.java;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code InstanceOfDynamicNode} represents a type check where the type being checked is not
 * known at compile time. This is used, for instance, to intrinsify {@link Class#isInstance(Object)}
 * .
 */
@NodeInfo
public class InstanceOfDynamicNode extends LogicNode implements Canonicalizable.Binary<ValueNode>, Lowerable {

    @Input ValueNode object;
    @Input ValueNode mirror;

    /**
     * @param mirror the {@link Class} value representing the target target type of the test
     * @param object the object being tested
     */
    public static InstanceOfDynamicNode create(ValueNode mirror, ValueNode object) {
        return USE_GENERATED_NODES ? new InstanceOfDynamicNodeGen(mirror, object) : new InstanceOfDynamicNode(mirror, object);
    }

    InstanceOfDynamicNode(ValueNode mirror, ValueNode object) {
        this.mirror = mirror;
        this.object = object;
        assert mirror.getKind() == Kind.Object : mirror.getKind();
        assert StampTool.isExactType(mirror);
        assert StampTool.typeOrNull(mirror).getName().equals("Ljava/lang/Class;");
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public ValueNode canonical(CanonicalizerTool tool, ValueNode forObject, ValueNode forMirror) {
        if (forMirror.isConstant()) {
            ResolvedJavaType t = tool.getConstantReflection().asJavaType(forMirror.asConstant());
            if (t != null) {
                if (t.isPrimitive()) {
                    return LogicConstantNode.contradiction();
                } else {
                    return InstanceOfNode.create(t, forObject, null);
                }
            }
        }
        return this;
    }

    public ValueNode object() {
        return object;
    }

    public ValueNode mirror() {
        return mirror;
    }

    public ValueNode getX() {
        return object;
    }

    public ValueNode getY() {
        return mirror;
    }
}
