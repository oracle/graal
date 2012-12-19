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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code InstanceOfNode} represents an instanceof test.
 */
public final class InstanceOfNode extends BooleanNode implements Canonicalizable, Lowerable, Virtualizable {

    @Input private ValueNode object;
    private final ResolvedJavaType type;
    private final JavaTypeProfile profile;

    /**
     * Constructs a new InstanceOfNode.
     *
     * @param type the target type of the instanceof check
     * @param object the object being tested by the instanceof
     */
    public InstanceOfNode(ResolvedJavaType type, ValueNode object, JavaTypeProfile profile) {
        super(StampFactory.condition());
        this.type = type;
        this.object = object;
        this.profile = profile;
        assert type != null;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        assert object() != null : this;

        ObjectStamp stamp = object().objectStamp();
        ResolvedJavaType stampType = stamp.type();

        if (stamp.isExactType()) {
            boolean subType = type().isAssignableFrom(stampType);

            if (subType) {
                if (stamp.nonNull()) {
                    // the instanceOf matches, so return true
                    return ConstantNode.forBoolean(true, graph());
                } else {
                    // the instanceof matches if the object is non-null, so return true depending on the null-ness.
                    negateUsages();
                    return graph().unique(new IsNullNode(object()));
                }
            } else {
                // since this type check failed for an exact type we know that it can never succeed at run time.
                // we also don't care about null values, since they will also make the check fail.
                return ConstantNode.forBoolean(false, graph());
            }
        } else if (stampType != null) {
            boolean subType = type().isAssignableFrom(stampType);

            if (subType) {
                if (stamp.nonNull()) {
                    // the instanceOf matches, so return true
                    return ConstantNode.forBoolean(true, graph());
                } else {
                    // the instanceof matches if the object is non-null, so return true depending on the null-ness.
                    negateUsages();
                    return graph().unique(new IsNullNode(object()));
                }
            } else {
                // since the subtype comparison was only performed on a declared type we don't really know if it might be true at run time...
            }
        }
        if (object().objectStamp().alwaysNull()) {
            return ConstantNode.forBoolean(false, graph());
        }
        return this;
    }

    public ValueNode object() {
        return object;
    }

    /**
     * Gets the type being tested.
     */
    public ResolvedJavaType type() {
        return type;
    }

    public JavaTypeProfile profile() {
        return profile;
    }

    @Override
    public boolean verify() {
        for (Node usage : usages()) {
            assertTrue(usage instanceof IfNode || usage instanceof ConditionalNode, "unsupported usage: ", usage);
        }
        return super.verify();
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        VirtualObjectNode virtual = tool.getVirtualState(object());
        if (virtual != null) {
            tool.replaceWithValue(ConstantNode.forBoolean(type().isAssignableFrom(virtual.type()), graph()));
        }
    }
}
