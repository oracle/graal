/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.jvmci.meta.*;

/**
 * The {@code InstanceOfDynamicNode} represents a type check where the type being checked is not
 * known at compile time. This is used, for instance, to intrinsify {@link Class#isInstance(Object)}
 * .
 */
@NodeInfo
public class InstanceOfDynamicNode extends LogicNode implements Canonicalizable.Binary<ValueNode>, Lowerable {
    public static final NodeClass<InstanceOfDynamicNode> TYPE = NodeClass.create(InstanceOfDynamicNode.class);

    @Input ValueNode object;
    @Input ValueNode mirror;

    public static LogicNode create(ConstantReflectionProvider constantReflection, ValueNode mirror, ValueNode object) {
        LogicNode synonym = findSynonym(constantReflection, object, mirror);
        if (synonym != null) {
            return synonym;
        }
        return new InstanceOfDynamicNode(mirror, object);

    }

    public InstanceOfDynamicNode(ValueNode mirror, ValueNode object) {
        super(TYPE);
        this.mirror = mirror;
        this.object = object;
        assert mirror.getKind() == Kind.Object : mirror.getKind();
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    private static LogicNode findSynonym(ConstantReflectionProvider constantReflection, ValueNode forObject, ValueNode forMirror) {
        if (forMirror.isConstant()) {
            ResolvedJavaType t = constantReflection.asJavaType(forMirror.asConstant());
            if (t != null) {
                if (t.isPrimitive()) {
                    return LogicConstantNode.contradiction();
                } else {
                    return new InstanceOfNode(t, forObject, null);
                }
            }
        }
        return null;
    }

    public LogicNode canonical(CanonicalizerTool tool, ValueNode forObject, ValueNode forMirror) {
        LogicNode res = findSynonym(tool.getConstantReflection(), forObject, forMirror);
        if (res == null) {
            res = this;
        }
        return res;
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
