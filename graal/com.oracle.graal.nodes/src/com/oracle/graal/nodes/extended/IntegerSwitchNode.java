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
package com.oracle.graal.nodes.extended;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;

/**
 * The {@code IntegerSwitchNode} represents a switch on integer keys, with a sorted array of key
 * values. The actual implementation of the switch will be decided by the backend.
 */
public final class IntegerSwitchNode extends SwitchNode implements LIRLowerable, Simplifiable {

    private final int[] keys;

    /**
     * Constructs a integer switch instruction. The keyProbabilities and keySuccessors array contain
     * key.length + 1 entries, the last entry describes the default (fall through) case.
     * 
     * @param value the instruction producing the value being switched on
     * @param successors the list of successors
     * @param keys the sorted list of keys
     * @param keyProbabilities the probabilities of the keys
     * @param keySuccessors the successor index for each key
     */
    public IntegerSwitchNode(ValueNode value, AbstractBeginNode[] successors, int[] keys, double[] keyProbabilities, int[] keySuccessors) {
        super(value, successors, keySuccessors, keyProbabilities);
        assert keySuccessors.length == keys.length + 1;
        assert keySuccessors.length == keyProbabilities.length;
        this.keys = keys;
        assert assertValues();
        assert assertSorted();
    }

    private boolean assertSorted() {
        for (int i = 1; i < keys.length; i++) {
            assert keys[i - 1] < keys[i];
        }
        return true;
    }

    /**
     * Constructs a integer switch instruction. The keyProbabilities and keySuccessors array contain
     * key.length + 1 entries, the last entry describes the default (fall through) case.
     * 
     * @param value the instruction producing the value being switched on
     * @param successorCount the number of successors
     * @param keys the sorted list of keys
     * @param keyProbabilities the probabilities of the keys
     * @param keySuccessors the successor index for each key
     */
    public IntegerSwitchNode(ValueNode value, int successorCount, int[] keys, double[] keyProbabilities, int[] keySuccessors) {
        this(value, new AbstractBeginNode[successorCount], keys, keyProbabilities, keySuccessors);
    }

    @Override
    public boolean isSorted() {
        return true;
    }

    /**
     * Gets the key at the specified index.
     * 
     * @param i the index
     * @return the key at that index
     */
    @Override
    public Constant keyAt(int i) {
        return Constant.forInt(keys[i]);
    }

    @Override
    public int keyCount() {
        return keys.length;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.emitSwitch(this);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (blockSuccessorCount() == 1) {
            tool.addToWorkList(defaultSuccessor());
            graph().removeSplitPropagate(this, defaultSuccessor());
        } else if (value() instanceof ConstantNode) {
            int constant = value().asConstant().asInt();

            int survivingEdge = keySuccessorIndex(keyCount());
            for (int i = 0; i < keyCount(); i++) {
                if (keys[i] == constant) {
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
        } else if (value().stamp() instanceof IntegerStamp) {
            IntegerStamp stamp = (IntegerStamp) value().stamp();
            if (!stamp.isUnrestricted()) {
                int validKeys = 0;
                for (int i = 0; i < keyCount(); i++) {
                    if (stamp.contains(keys[i])) {
                        validKeys++;
                    }
                }
                if (validKeys == 0) {
                    tool.addToWorkList(defaultSuccessor());
                    graph().removeSplitPropagate(this, defaultSuccessor());
                } else if (validKeys != keys.length) {
                    ArrayList<AbstractBeginNode> newSuccessors = new ArrayList<>(blockSuccessorCount());
                    int[] newKeys = new int[validKeys];
                    int[] newKeySuccessors = new int[validKeys + 1];
                    double[] newKeyProbabilities = new double[validKeys + 1];
                    double totalProbability = 0;
                    int current = 0;
                    for (int i = 0; i < keyCount() + 1; i++) {
                        if (i == keyCount() || stamp.contains(keys[i])) {
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
                        AbstractBeginNode successor = blockSuccessor(i);
                        if (!newSuccessors.contains(successor)) {
                            tool.deleteBranch(successor);
                        }
                        setBlockSuccessor(i, null);
                    }

                    AbstractBeginNode[] successorsArray = newSuccessors.toArray(new AbstractBeginNode[newSuccessors.size()]);
                    IntegerSwitchNode newSwitch = graph().add(new IntegerSwitchNode(value(), successorsArray, newKeys, newKeyProbabilities, newKeySuccessors));
                    ((FixedWithNextNode) predecessor()).setNext(newSwitch);
                    GraphUtil.killWithUnusedFloatingInputs(this);
                }
            }
        }
    }
}
