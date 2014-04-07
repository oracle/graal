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
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code InstanceOfDynamicNode} represents a type check where the type being checked is not
 * known at compile time. This is used, for instance, to intrinsify {@link Class#isInstance(Object)}
 * .
 */
public final class InstanceOfDynamicNode extends LogicNode implements Canonicalizable, Lowerable {

    @Input private ValueNode object;
    @Input private ValueNode mirror;

    /**
     * @param mirror the {@link Class} value representing the target target type of the test
     * @param object the object being tested
     */
    public InstanceOfDynamicNode(ValueNode mirror, ValueNode object) {
        this.mirror = mirror;
        this.object = object;
        assert mirror.getKind() == Kind.Object : mirror.getKind();
        assert ObjectStamp.isExactType(mirror);
        assert ObjectStamp.typeOrNull(mirror).getName().equals("Ljava/lang/Class;");
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        assert object() != null : this;
        if (mirror().isConstant()) {
            ResolvedJavaType t = tool.getConstantReflection().asJavaType(mirror().asConstant());
            if (t != null) {
                if (t.isPrimitive()) {
                    return LogicConstantNode.contradiction(graph());
                } else {
                    return graph().unique(new InstanceOfNode(t, object(), null));
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
}
