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

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.jvmci.meta.*;
import com.oracle.jvmci.meta.Assumptions.AssumptionResult;

/**
 * The {@code InstanceOfNode} represents an instanceof test.
 */
@NodeInfo
public class InstanceOfNode extends UnaryOpLogicNode implements Lowerable, Virtualizable {
    public static final NodeClass<InstanceOfNode> TYPE = NodeClass.create(InstanceOfNode.class);

    protected final ResolvedJavaType type;
    protected JavaTypeProfile profile;

    public InstanceOfNode(ResolvedJavaType type, ValueNode object, JavaTypeProfile profile) {
        this(TYPE, type, object, profile);
    }

    protected InstanceOfNode(NodeClass<? extends InstanceOfNode> c, ResolvedJavaType type, ValueNode object, JavaTypeProfile profile) {
        super(c, object);
        this.type = type;
        this.profile = profile;
        assert type != null;
    }

    public static LogicNode create(ResolvedJavaType type, ValueNode object, JavaTypeProfile profile) {
        ObjectStamp objectStamp = (ObjectStamp) object.stamp();
        LogicNode constantValue = findSynonym(type, objectStamp.type(), objectStamp.nonNull(), objectStamp.isExactType());
        if (constantValue != null) {
            return constantValue;
        } else {
            return new InstanceOfNode(type, object, profile);
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (!(forValue.stamp() instanceof ObjectStamp)) {
            return this;
        }
        ObjectStamp objectStamp = (ObjectStamp) forValue.stamp();
        if (objectStamp.alwaysNull()) {
            return LogicConstantNode.contradiction();
        }

        ResolvedJavaType stampType = objectStamp.type();
        if (stampType != null) {
            ValueNode result = checkInstanceOf(forValue, stampType, objectStamp.nonNull(), objectStamp.isExactType());
            if (result != null) {
                return result;
            }
            Assumptions assumptions = graph() == null ? null : graph().getAssumptions();
            if (assumptions != null) {
                AssumptionResult<ResolvedJavaType> leafConcreteSubtype = stampType.findLeafConcreteSubtype();
                if (leafConcreteSubtype != null) {
                    result = checkInstanceOf(forValue, leafConcreteSubtype.getResult(), objectStamp.nonNull(), true);
                    if (result != null) {
                        assumptions.record(leafConcreteSubtype);
                        return result;
                    }
                }
            }
        }
        return this;
    }

    private ValueNode checkInstanceOf(ValueNode forValue, ResolvedJavaType inputType, boolean nonNull, boolean exactType) {
        ValueNode result = findSynonym(type(), inputType, nonNull, exactType);
        if (result != null) {
            return result;
        }
        if (type().isAssignableFrom(inputType)) {
            if (!nonNull) {
                // the instanceof matches if the object is non-null, so return true
                // depending on the null-ness.
                return LogicNegationNode.create(new IsNullNode(forValue));
            }
        }
        return null;
    }

    public static LogicNode findSynonym(ResolvedJavaType type, ResolvedJavaType inputType, boolean nonNull, boolean exactType) {
        if (inputType == null) {
            return null;
        }
        boolean subType = type.isAssignableFrom(inputType);
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
                boolean superType = inputType.isAssignableFrom(type);
                if (!superType && (type.asExactType() != null || (!isInterfaceOrArrayOfInterface(inputType) && !isInterfaceOrArrayOfInterface(type)))) {
                    return LogicConstantNode.contradiction();
                }
                // since the subtype comparison was only performed on a declared type we don't
                // really know if it might be true at run time...
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

    @Override
    public Stamp getSucceedingStampForValue(boolean negated) {
        if (negated) {
            return null;
        } else {
            return StampFactory.declaredTrustedNonNull(type);
        }
    }

    @Override
    public TriState tryFold(Stamp valueStamp) {
        if (valueStamp instanceof ObjectStamp) {
            ObjectStamp objectStamp = (ObjectStamp) valueStamp;
            if (objectStamp.alwaysNull()) {
                return TriState.FALSE;
            }

            ResolvedJavaType objectType = objectStamp.type();
            if (objectType != null) {
                ResolvedJavaType instanceofType = type;
                if (instanceofType.isAssignableFrom(objectType)) {
                    if (objectStamp.nonNull()) {
                        return TriState.TRUE;
                    }
                } else {
                    if (objectStamp.isExactType()) {
                        return TriState.FALSE;
                    } else {
                        boolean superType = objectType.isAssignableFrom(instanceofType);
                        if (!superType && !objectType.isInterface() && !instanceofType.isInterface()) {
                            return TriState.FALSE;
                        }
                    }
                }
            }
        }
        return TriState.UNKNOWN;
    }

    private static boolean isInterfaceOrArrayOfInterface(ResolvedJavaType t) {
        return t.isInterface() || (t.isArray() && t.getElementalType().isInterface());
    }
}
