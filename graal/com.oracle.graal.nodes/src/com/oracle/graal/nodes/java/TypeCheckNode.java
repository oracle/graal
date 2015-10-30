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

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicConstantNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.UnaryOpLogicNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;

/**
 * The {@code TypeCheckNode} represents a test equivalent to {@code o.getClass() == type}. The node
 * may only be used if {@code o != null} is known to be true as indicated by the object's stamp.
 */
@NodeInfo
public final class TypeCheckNode extends UnaryOpLogicNode implements Lowerable, Virtualizable {
    public static final NodeClass<TypeCheckNode> TYPE = NodeClass.create(TypeCheckNode.class);

    protected final ResolvedJavaType type;

    protected TypeCheckNode(ResolvedJavaType type, ValueNode object) {
        super(TYPE, object);
        this.type = type;
        assert type != null;
        assert type.isConcrete() || type.isArray();
        assert ((ObjectStamp) object.stamp()).nonNull();
    }

    public static LogicNode create(ResolvedJavaType type, ValueNode object) {
        ObjectStamp objectStamp = (ObjectStamp) object.stamp();
        assert objectStamp.nonNull() : object;
        LogicNode constantValue = findSynonym(type, objectStamp.type(), true, objectStamp.isExactType());
        if (constantValue != null) {
            return constantValue;
        } else {
            return new TypeCheckNode(type, object);
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

        ResolvedJavaType stampType = objectStamp.type();
        if (stampType != null) {
            ValueNode result = findSynonym(type(), stampType, true, objectStamp.isExactType());
            if (result != null) {
                return result;
            }

            Assumptions assumptions = graph() == null ? null : graph().getAssumptions();
            AssumptionResult<ResolvedJavaType> leafConcreteSubtype = stampType.findLeafConcreteSubtype();
            if (leafConcreteSubtype != null && leafConcreteSubtype.canRecordTo(assumptions)) {
                result = findSynonym(type(), leafConcreteSubtype.getResult(), true, true);
                if (result != null) {
                    leafConcreteSubtype.recordTo(assumptions);
                    return result;
                }
            }
        }
        return this;
    }

    public static LogicNode findSynonym(ResolvedJavaType type, ResolvedJavaType inputType, boolean nonNull, boolean exactType) {
        if (inputType == null) {
            return null;
        }
        if (type.equals(inputType)) {
            if (nonNull && exactType) {
                // the type matches, so return true
                return LogicConstantNode.tautology();
            }
        } else {
            if (exactType || !inputType.isAssignableFrom(type)) {
                // since this type check failed for an exact type we know that it can never
                // succeed at run time. we also don't care about null values, since they will
                // also make the check fail.
                return LogicConstantNode.contradiction();
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

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getValue());
        TriState state = tryFold(alias.stamp());
        if (state != TriState.UNKNOWN) {
            tool.replaceWithValue(LogicConstantNode.forBoolean(state.isTrue(), graph()));
        }
    }

    @Override
    public Stamp getSucceedingStampForValue(boolean negated) {
        if (negated) {
            return null;
        } else {
            return StampFactory.exactNonNull(type);
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
                if (instanceofType.equals(objectType)) {
                    if (objectStamp.nonNull() && (objectStamp.isExactType() || objectType.isLeaf())) {
                        return TriState.TRUE;
                    }
                } else {
                    if (objectStamp.isExactType()) {
                        return TriState.FALSE;
                    }
                }
            }
        }
        return TriState.UNKNOWN;
    }
}
