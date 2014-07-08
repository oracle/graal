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
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code InstanceOfNode} represents an instanceof test.
 */
public class InstanceOfNode extends UnaryOpLogicNode implements Lowerable, Virtualizable {

    private final ResolvedJavaType type;
    private JavaTypeProfile profile;

    /**
     * Constructs a new InstanceOfNode.
     *
     * @param type the target type of the instanceof check
     * @param object the object being tested by the instanceof
     */
    public InstanceOfNode(ResolvedJavaType type, ValueNode object, JavaTypeProfile profile) {
        super(object);
        this.type = type;
        this.profile = profile;
        assert type != null;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        Stamp stamp = forValue.stamp();
        if (!(stamp instanceof ObjectStamp)) {
            return this;
        }
        ObjectStamp objectStamp = (ObjectStamp) stamp;
        if (objectStamp.alwaysNull()) {
            return LogicConstantNode.contradiction();
        }

        ResolvedJavaType stampType = objectStamp.type();
        if (stampType != null) {
            ValueNode result = checkInstanceOf(forValue, stampType, objectStamp.nonNull(), objectStamp.isExactType());
            if (result != null) {
                return result;
            }
            if (tool.assumptions() != null && tool.assumptions().useOptimisticAssumptions()) {
                ResolvedJavaType exact = stampType.findUniqueConcreteSubtype();
                if (exact != null) {
                    result = checkInstanceOf(forValue, exact, objectStamp.nonNull(), true);
                    if (result != null) {
                        tool.assumptions().recordConcreteSubtype(stampType, exact);
                        return result;
                    }
                }
            }
        }
        return this;
    }

    private ValueNode checkInstanceOf(ValueNode forValue, ResolvedJavaType inputType, boolean nonNull, boolean exactType) {
        boolean subType = type().isAssignableFrom(inputType);
        if (subType) {
            if (nonNull) {
                // the instanceOf matches, so return true
                return LogicConstantNode.tautology();
            }
        } else {
            if (exactType) {
                // since this type check failed for an exact type we know that it can never
                // succeed at run time. we also don't care about null values, since they will
                // also make the check fail.
                return LogicConstantNode.contradiction();
            } else {
                boolean superType = inputType.isAssignableFrom(type());
                if (!superType && !inputType.isInterface() && !type().isInterface()) {
                    return LogicConstantNode.contradiction();
                }
                // since the subtype comparison was only performed on a declared type we don't
                // really know if it might be true at run time...
            }
        }
        if (type().isAssignableFrom(inputType)) {
            if (!nonNull) {
                // the instanceof matches if the object is non-null, so return true
                // depending on the null-ness.
                return new LogicNegationNode(new IsNullNode(forValue));
            }
        }
        return null;
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

    public void setProfile(JavaTypeProfile profile) {
        this.profile = profile;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(getValue());
        if (state != null) {
            tool.replaceWithValue(LogicConstantNode.forBoolean(type().isAssignableFrom(state.getVirtualObject().type()), graph()));
        }
    }
}
