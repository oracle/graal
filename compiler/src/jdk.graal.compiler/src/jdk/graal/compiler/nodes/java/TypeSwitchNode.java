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

import java.util.ArrayList;
import java.util.Arrays;

import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ProfileData.SwitchProbabilityData;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The {@code TypeSwitchNode} performs a lookup based on the type of the input value. The type
 * comparison is an exact type comparison, not an instanceof.
 */
@NodeInfo
public final class TypeSwitchNode extends SwitchNode implements LIRLowerable, Simplifiable {

    public static final NodeClass<TypeSwitchNode> TYPE = NodeClass.create(TypeSwitchNode.class);
    protected final ResolvedJavaType[] keys;
    protected final Constant[] hubs;

    public TypeSwitchNode(ValueNode value, AbstractBeginNode[] successors, ResolvedJavaType[] keys, int[] keySuccessors, ConstantReflectionProvider constantReflection,
                    SwitchProbabilityData profileData) {
        super(TYPE, value, successors, keySuccessors, profileData);
        assert keySuccessors.length == keys.length + 1 : "Must have etry key for default " + Assertions.errorMessageContext("keySucc", keySuccessors, "keys", keys);
        assert keySuccessors.length == profileData.getKeyProbabilities().length : Assertions.errorMessageContext("keySucc", keySuccessors, "profiles", profileData.getKeyProbabilities());
        this.keys = keys;
        assert value.stamp(NodeView.DEFAULT) instanceof AbstractPointerStamp : Assertions.errorMessageContext("value", value);
        assert assertKeys();

        hubs = new Constant[keys.length];
        for (int i = 0; i < hubs.length; i++) {
            hubs[i] = constantReflection.asObjectHub(keys[i]);
        }
    }

    /**
     * Don't allow duplicate keys.
     */
    private boolean assertKeys() {
        for (int i = 0; i < keys.length; i++) {
            for (int j = 0; j < keys.length; j++) {
                if (i == j) {
                    continue;
                }
                assert !keys[i].equals(keys[j]);
            }
        }
        return true;
    }

    @Override
    public boolean isSorted() {
        return false;
    }

    @Override
    public int keyCount() {
        return keys == null ? 0 : keys.length;
    }

    @Override
    public Constant keyAt(int index) {
        return hubs[index];
    }

    @Override
    public boolean equalKeys(SwitchNode switchNode) {
        if (!(switchNode instanceof TypeSwitchNode)) {
            return false;
        }
        TypeSwitchNode other = (TypeSwitchNode) switchNode;
        return Arrays.equals(keys, other.keys);
    }

    public ResolvedJavaType typeAt(int index) {
        return keys[index];
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.emitSwitch(this);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        super.simplify(tool);
        if (this.isDeleted()) {
            return;
        }
        if (shouldInjectBranchProbabilities()) {
            injectBranchProbabilities();
        }
        NodeView view = NodeView.from(tool);
        if (value() instanceof ConstantNode) {
            Constant constant = value().asConstant();

            int survivingEdge = keySuccessorIndex(keyCount());
            for (int i = 0; i < keyCount(); i++) {
                Constant typeHub = keyAt(i);
                Boolean equal = tool.getConstantReflection().constantEquals(constant, typeHub);
                if (equal == null) {
                    /* We don't know if this key is a match or not, so we cannot simplify. */
                    return;
                } else if (equal.booleanValue()) {
                    survivingEdge = keySuccessorIndex(i);
                }
            }
            killOtherSuccessors(tool, survivingEdge);
        }
        if (value() instanceof LoadHubNode && ((LoadHubNode) value()).getValue().stamp(view) instanceof ObjectStamp) {
            ObjectStamp objectStamp = (ObjectStamp) ((LoadHubNode) value()).getValue().stamp(view);
            if (objectStamp.type() != null) {
                int validKeys = 0;
                for (int i = 0; i < keyCount(); i++) {
                    if (objectStamp.type().isAssignableFrom(keys[i])) {
                        validKeys++;
                    }
                }
                if (validKeys == 0) {
                    tool.addToWorkList(defaultSuccessor());
                    graph().removeSplitPropagate(this, defaultSuccessor());
                } else if (validKeys != keys.length) {
                    ArrayList<AbstractBeginNode> newSuccessors = new ArrayList<>(blockSuccessorCount());
                    ResolvedJavaType[] newKeys = new ResolvedJavaType[validKeys];
                    int[] newKeySuccessors = new int[validKeys + 1];
                    double[] newKeyProbabilities = new double[validKeys + 1];
                    double totalProbability = 0;
                    int current = 0;
                    for (int i = 0; i < keyCount() + 1; i++) {
                        if (i == keyCount() || objectStamp.type().isAssignableFrom(keys[i])) {
                            int index = newSuccessors.indexOf(keySuccessor(i));
                            if (index == -1) {
                                index = newSuccessors.size();
                                newSuccessors.add(keySuccessor(i));
                            }
                            newKeySuccessors[current] = index;
                            if (i < keyCount()) {
                                newKeys[current] = keys[i];
                            }
                            newKeyProbabilities[current] = keyProbability(i);
                            totalProbability += keyProbability(i);
                            current++;
                        }
                    }
                    if (totalProbability > 0) {
                        for (int i = 0; i < current; i++) {
                            newKeyProbabilities[i] /= totalProbability;
                        }
                    } else {
                        for (int i = 0; i < current; i++) {
                            newKeyProbabilities[i] = 1.0 / current;
                        }
                    }

                    ArrayList<AbstractBeginNode> oldSuccessors = new ArrayList<>();
                    for (int i = 0; i < blockSuccessorCount(); i++) {
                        AbstractBeginNode successor = blockSuccessor(i);
                        oldSuccessors.add(successor);
                        setBlockSuccessor(i, null);
                    }

                    AbstractBeginNode[] successorsArray = newSuccessors.toArray(new AbstractBeginNode[newSuccessors.size()]);
                    TypeSwitchNode newSwitch = graph().add(
                                    new TypeSwitchNode(value(), successorsArray, newKeys, newKeySuccessors, tool.getConstantReflection(),
                                                    SwitchProbabilityData.create(newKeyProbabilities, profileData.getProfileSource())));
                    ((FixedWithNextNode) predecessor()).setNext(newSwitch);
                    GraphUtil.killWithUnusedFloatingInputs(this);

                    for (int i = 0; i < oldSuccessors.size(); i++) {
                        AbstractBeginNode successor = oldSuccessors.get(i);
                        if (!newSuccessors.contains(successor)) {
                            GraphUtil.killCFG(successor);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected Stamp stampAtKeySuccessor(int i) {
        return StampFactory.objectNonNull(TypeReference.createExactTrusted(typeAt(i)));
    }

    @Override
    public Stamp genericSuccessorStamp() {
        if (value instanceof LoadHubNode lh) {
            return lh.getValue().stamp(NodeView.DEFAULT);
        }
        // if we do not see the load hub give up and don't bother computing the default stamp
        return null;
    }
}
