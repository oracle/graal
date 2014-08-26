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

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ResolvedJavaType.Representation;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;

/**
 * The {@code TypeSwitchNode} performs a lookup based on the type of the input value. The type
 * comparison is an exact type comparison, not an instanceof.
 */
@NodeInfo
public class TypeSwitchNode extends SwitchNode implements LIRLowerable, Simplifiable {

    private final ResolvedJavaType[] keys;

    /**
     * Constructs a type switch instruction. The keyProbabilities array contain key.length + 1
     * entries. The last entry in every array describes the default case.
     *
     * @param value the instruction producing the value being switched on, the object hub
     * @param successors the list of successors
     * @param keys the list of types
     * @param keyProbabilities the probabilities of the keys
     * @param keySuccessors the successor index for each key
     */
    public static TypeSwitchNode create(ValueNode value, BeginNode[] successors, ResolvedJavaType[] keys, double[] keyProbabilities, int[] keySuccessors) {
        return USE_GENERATED_NODES ? new TypeSwitchNodeGen(value, successors, keys, keyProbabilities, keySuccessors) : new TypeSwitchNode(value, successors, keys, keyProbabilities, keySuccessors);
    }

    TypeSwitchNode(ValueNode value, BeginNode[] successors, ResolvedJavaType[] keys, double[] keyProbabilities, int[] keySuccessors) {
        super(value, successors, keySuccessors, keyProbabilities);
        assert successors.length <= keys.length + 1;
        assert keySuccessors.length == keyProbabilities.length;
        this.keys = keys;
        assert assertValues();
        assert assertKeys();
    }

    /**
     * Don't allow duplicate keys
     */
    private boolean assertKeys() {
        for (int i = 0; i < keys.length; i++) {
            for (int j = 0; j < keys.length; j++) {
                if (i == j)
                    continue;
                assert !keys[i].equals(keys[j]);
            }
        }
        return true;
    }

    @Override
    public boolean isSorted() {
        Kind kind = value().getKind();
        if (kind.isNumericInteger()) {
            Constant lastKey = null;
            for (int i = 0; i < keyCount(); i++) {
                Constant key = keyAt(i);
                if (lastKey != null && key.asLong() <= lastKey.asLong()) {
                    return false;
                }
                lastKey = key;
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int keyCount() {
        return keys.length;
    }

    @Override
    public Constant keyAt(int index) {
        return keys[index].getEncoding(Representation.ObjectHub);
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
        if (value() instanceof ConstantNode) {
            Constant constant = value().asConstant();

            int survivingEdge = keySuccessorIndex(keyCount());
            for (int i = 0; i < keyCount(); i++) {
                Constant typeHub = keyAt(i);
                assert constant.getKind() == typeHub.getKind();
                Boolean equal = tool.getConstantReflection().constantEquals(constant, typeHub);
                if (equal == null) {
                    /* We don't know if this key is a match or not, so we cannot simplify. */
                    return;
                } else if (equal.booleanValue()) {
                    survivingEdge = keySuccessorIndex(i);
                }
            }
            for (int i = 0; i < blockSuccessorCount(); i++) {
                if (i != survivingEdge) {
                    tool.deleteBranch(blockSuccessor(i));
                }
            }
            tool.addToWorkList(blockSuccessor(survivingEdge));
            graph().removeSplit(this, blockSuccessor(survivingEdge));
        }
        if (value() instanceof LoadHubNode && ((LoadHubNode) value()).getValue().stamp() instanceof ObjectStamp) {
            ObjectStamp stamp = (ObjectStamp) ((LoadHubNode) value()).getValue().stamp();
            if (stamp.type() != null) {
                int validKeys = 0;
                for (int i = 0; i < keyCount(); i++) {
                    if (stamp.type().isAssignableFrom(keys[i])) {
                        validKeys++;
                    }
                }
                if (validKeys == 0) {
                    tool.addToWorkList(defaultSuccessor());
                    graph().removeSplitPropagate(this, defaultSuccessor());
                } else if (validKeys != keys.length) {
                    ArrayList<BeginNode> newSuccessors = new ArrayList<>(blockSuccessorCount());
                    ResolvedJavaType[] newKeys = new ResolvedJavaType[validKeys];
                    int[] newKeySuccessors = new int[validKeys + 1];
                    double[] newKeyProbabilities = new double[validKeys + 1];
                    double totalProbability = 0;
                    int current = 0;
                    for (int i = 0; i < keyCount() + 1; i++) {
                        if (i == keyCount() || stamp.type().isAssignableFrom(keys[i])) {
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

                    for (int i = 0; i < blockSuccessorCount(); i++) {
                        BeginNode successor = blockSuccessor(i);
                        if (!newSuccessors.contains(successor)) {
                            tool.deleteBranch(successor);
                        }
                        setBlockSuccessor(i, null);
                    }

                    BeginNode[] successorsArray = newSuccessors.toArray(new BeginNode[newSuccessors.size()]);
                    TypeSwitchNode newSwitch = graph().add(TypeSwitchNode.create(value(), successorsArray, newKeys, newKeyProbabilities, newKeySuccessors));
                    ((FixedWithNextNode) predecessor()).setNext(newSwitch);
                    GraphUtil.killWithUnusedFloatingInputs(this);
                }
            }
        }
    }
}
