/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.nodes.java;

import static jdk.graal.compiler.nodeinfo.InputType.Anchor;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;

import java.util.Objects;

import jdk.graal.compiler.core.common.NativeImageSupport;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Node.NodeIntrinsicFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.UnaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/**
 * The {@code InstanceOfNode} represents an instanceof test.
 * <p>
 * A Java instanceof test normally returns {@code false} when the tested object is {@code null}.
 * However, if the node {@linkplain #allowsNull() allows null}, the test should return {@code true}
 * for {@code null} values.
 */
@NodeInfo(cycles = CYCLES_8, size = SIZE_8)
@NodeIntrinsicFactory
public class InstanceOfNode extends UnaryOpLogicNode implements Lowerable {
    public static final NodeClass<InstanceOfNode> TYPE = NodeClass.create(InstanceOfNode.class);

    private final ObjectStamp checkedStamp;

    private JavaTypeProfile profile;
    @OptionalInput(Anchor) protected AnchoringNode anchor;

    private InstanceOfNode(ObjectStamp checkedStamp, ValueNode object, JavaTypeProfile profile, AnchoringNode anchor) {
        this(TYPE, checkedStamp, object, profile, anchor);
    }

    @SuppressWarnings("this-escape")
    protected InstanceOfNode(NodeClass<? extends InstanceOfNode> c, ObjectStamp checkedStamp, ValueNode object, JavaTypeProfile profile, AnchoringNode anchor) {
        super(c, object);
        this.checkedStamp = checkedStamp;
        this.profile = profile;
        this.anchor = anchor;
        assert NativeImageSupport.inBuildtimeCode() || (profile == null) || (anchor != null) : "profiles must be anchored";
        assert checkedStamp != null;
        assert type() != null;
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
        LogicNode synonym = findSynonym(checkedStamp, object, NodeView.DEFAULT);
        if (synonym != null) {
            return synonym;
        } else {
            return new InstanceOfNode(checkedStamp, object, profile, anchor);
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        NodeView view = NodeView.from(tool);
        LogicNode synonym = findSynonym(checkedStamp, forValue, view);
        if (synonym != null) {
            return synonym;
        } else if (!checkedStamp.isExactType()) {
            TypeReference checkedType = TypeReference.createTrusted(tool.getAssumptions(), checkedStamp.type());
            if (checkedType != null && checkedType.isExact()) {
                // Refine type and exact-ness, preserving other properties of the original stamp.
                ObjectStamp improvedStamp = (ObjectStamp) checkedStamp.tryImproveWith(StampFactory.object(checkedType));
                if (improvedStamp != null) {
                    return createHelper(improvedStamp, forValue, profile, anchor);
                }
            }
        }
        return this;
    }

    public static LogicNode findSynonym(ObjectStamp checkedStamp, ValueNode object, NodeView view) {
        ObjectStamp inputStamp = (ObjectStamp) object.stamp(view);
        ObjectStamp joinedStamp = (ObjectStamp) checkedStamp.join(inputStamp);

        if (joinedStamp.isEmpty()) {
            // The check can never succeed, the intersection of the two stamps is empty.
            return LogicConstantNode.contradiction();
        } else if (joinedStamp.equals(inputStamp)) {
            // The check will always succeed, the intersection of the two stamps is equal to the
            // input stamp.
            return LogicConstantNode.tautology();
        } else if (joinedStamp.alwaysNull()) {
            // The intersection of the two stamps is always null => simplify the check.
            return IsNullNode.create(object);
        } else {
            ObjectStamp meetStamp = (ObjectStamp) checkedStamp.meet(inputStamp);
            if (Objects.equals(checkedStamp.type(), meetStamp.type()) &&
                            checkedStamp.isExactType() == meetStamp.isExactType() &&
                            checkedStamp.alwaysNull() == meetStamp.alwaysNull() &&
                            checkedStamp.isAlwaysArray() == meetStamp.isAlwaysArray()) {
                assert checkedStamp.nonNull() != inputStamp.nonNull() : Assertions.errorMessage(checkedStamp, inputStamp, object);
                // The only difference between the two stamps is their null-ness => simplify the
                // check.
                if (checkedStamp.nonNull()) {
                    return LogicNegationNode.create(IsNullNode.create(object));
                } else {
                    return IsNullNode.create(object);
                }
            }
            assert checkedStamp.type() != null;
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
    public Stamp getSucceedingStampForValue(boolean negated) {
        if (negated) {
            return null;
        } else {
            return checkedStamp;
        }
    }

    @Override
    public TriState tryFold(Stamp valueStamp) {
        if (valueStamp instanceof ObjectStamp inputStamp) {
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

    public void setProfile(JavaTypeProfile typeProfile, AnchoringNode anchor) {
        this.profile = typeProfile;
        updateUsagesInterface(this.anchor, anchor);
        this.anchor = anchor;
        assert NativeImageSupport.inBuildtimeCode() || (profile == null) || (anchor != null) : "profiles must be anchored";
    }

    public AnchoringNode getAnchor() {
        return anchor;
    }

    public ObjectStamp getCheckedStamp() {
        return checkedStamp;
    }

    @NodeIntrinsic
    public static native boolean doInstanceof(@ConstantNodeParameter ResolvedJavaType type, Object object);

    public static boolean intrinsify(GraphBuilderContext b, ResolvedJavaType type, ValueNode object) {
        InstanceOfNode node = new InstanceOfNode(StampFactory.objectNonNull(TypeReference.create(b.getAssumptions(), type)), object, null, null);
        node = b.add(node);
        b.addPush(JavaKind.Int, ConditionalNode.create(node, NodeView.DEFAULT));
        return true;
    }

    @Override
    public TriState implies(boolean thisNegated, LogicNode other) {
        if (other instanceof InstanceOfNode instanceOfNode) {
            if (instanceOfNode.getValue() == getValue()) {
                if (thisNegated) {
                    // !X => Y
                    if (this.getCheckedStamp().meet(instanceOfNode.getCheckedStamp()).equals(this.getCheckedStamp())) {
                        return TriState.get(false);
                    }
                } else {
                    // X => Y
                    if (instanceOfNode.getCheckedStamp().meet(this.getCheckedStamp()).equals(instanceOfNode.getCheckedStamp())) {
                        return TriState.get(true);
                    }
                }
            }
        }
        return super.implies(thisNegated, other);
    }
}
