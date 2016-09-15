/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.nodeinfo.InputType.Anchor;
import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_15;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_15;

import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.compiler.common.type.TypeReference;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicConstantNode;
import com.oracle.graal.nodes.LogicNegationNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.UnaryOpLogicNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.extended.AnchoringNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.type.StampTool;

import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.TriState;

/**
 * The {@code InstanceOfNode} represents an instanceof test.
 */
@NodeInfo(cycles = CYCLES_15, size = SIZE_15)
public class InstanceOfNode extends UnaryOpLogicNode implements Lowerable, Virtualizable {
    public static final NodeClass<InstanceOfNode> TYPE = NodeClass.create(InstanceOfNode.class);

    protected final ObjectStamp checkedStamp;

    private JavaTypeProfile profile;
    @OptionalInput(Anchor) protected AnchoringNode anchor;

    private InstanceOfNode(ObjectStamp checkedStamp, ValueNode object, JavaTypeProfile profile, AnchoringNode anchor) {
        this(TYPE, checkedStamp, object, profile, anchor);
    }

    protected InstanceOfNode(NodeClass<? extends InstanceOfNode> c, ObjectStamp checkedStamp, ValueNode object, JavaTypeProfile profile, AnchoringNode anchor) {
        super(c, object);
        this.checkedStamp = checkedStamp;
        this.profile = profile;
        this.anchor = anchor;
        assert checkedStamp != null;
    }

    public static LogicNode createAllowNull(TypeReference type, ValueNode object, JavaTypeProfile profile, AnchoringNode anchor) {
        if (StampTool.isPointerNonNull(object)) {
            return create(type, object, profile, anchor);
        }
        return createHelper(StampFactory.object(type), object, profile, anchor);
    }

    public static LogicNode create(TypeReference type, ValueNode object) {
        return create(type, object, null, null);
    }

    public static LogicNode create(TypeReference type, ValueNode object, JavaTypeProfile profile, AnchoringNode anchor) {
        return createHelper(StampFactory.objectNonNull(type), object, profile, anchor);
    }

    public static LogicNode createHelper(ObjectStamp checkedStamp, ValueNode object, JavaTypeProfile profile, AnchoringNode anchor) {
        LogicNode synonym = findSynonym(checkedStamp, object);
        if (synonym != null) {
            return synonym;
        } else {
            return new InstanceOfNode(checkedStamp, object, profile, anchor);
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        LogicNode synonym = findSynonym(checkedStamp, forValue);
        if (synonym != null) {
            return synonym;
        } else {
            return this;
        }
    }

    public static LogicNode findSynonym(ObjectStamp checkedStamp, ValueNode object) {
        ObjectStamp inputStamp = (ObjectStamp) object.stamp();
        ObjectStamp joinedStamp = (ObjectStamp) checkedStamp.join(inputStamp);

        if (joinedStamp.isEmpty()) {
            // The check can never succeed, the intersection of the two stamps is empty.
            return LogicConstantNode.contradiction();
        } else {
            ObjectStamp meetStamp = (ObjectStamp) checkedStamp.meet(inputStamp);
            if (checkedStamp.equals(meetStamp)) {
                // The check will always succeed, the union of the two stamps is equal to the
                // checked stamp.
                return LogicConstantNode.tautology();
            } else if (checkedStamp.type().equals(meetStamp.type()) && checkedStamp.isExactType() == meetStamp.isExactType() && checkedStamp.alwaysNull() == meetStamp.alwaysNull()) {
                assert checkedStamp.nonNull() != inputStamp.nonNull();
                // The only difference makes the null-ness of the value => simplify the check.
                if (checkedStamp.nonNull()) {
                    return LogicNegationNode.create(IsNullNode.create(object));
                } else {
                    return IsNullNode.create(object);
                }
            }
        }

        return null;
    }

    /**
     * Gets the type being tested.
     */
    public TypeReference type() {
        return StampTool.typeReferenceOrNull(checkedStamp);
    }

    public JavaTypeProfile profile() {
        return profile;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getValue());
        TriState fold = tryFold(alias.stamp());
        if (fold != TriState.UNKNOWN) {
            tool.replaceWithValue(LogicConstantNode.forBoolean(fold.isTrue(), graph()));
        }
    }

    @Override
    public Stamp getSucceedingStampForValue(boolean negated) {
        if (negated) {
            return null;
        } else {
            return checkedStamp;
        }
    }

    @Override
    public TriState tryFold(Stamp valueStamp) {
        if (valueStamp instanceof ObjectStamp) {
            ObjectStamp inputStamp = (ObjectStamp) valueStamp;
            ObjectStamp joinedStamp = (ObjectStamp) checkedStamp.join(inputStamp);

            if (joinedStamp.isEmpty()) {
                // The check can never succeed, the intersection of the two stamps is empty.
                return TriState.FALSE;
            } else {
                ObjectStamp meetStamp = (ObjectStamp) checkedStamp.meet(inputStamp);
                if (checkedStamp.equals(meetStamp)) {
                    // The check will always succeed, the union of the two stamps is equal to the
                    // checked stamp.
                    return TriState.TRUE;
                }
            }
        }
        return TriState.UNKNOWN;
    }

    public boolean allowsNull() {
        return !checkedStamp.nonNull();
    }

    public void setProfile(JavaTypeProfile typeProfile) {
        this.profile = typeProfile;
    }

    public AnchoringNode getAnchor() {
        return anchor;
    }
}
