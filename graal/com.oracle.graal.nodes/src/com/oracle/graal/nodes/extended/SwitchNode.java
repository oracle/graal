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
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

/**
 * The {@code SwitchNode} class is the base of both lookup and table switches.
 */
public abstract class SwitchNode extends ControlSplitNode {

    @Successor private final NodeSuccessorList<BeginNode> successors;
    @Input private ValueNode value;
    private double[] keyProbabilities;
    private int[] keySuccessors;

    /**
     * Constructs a new Switch.
     * 
     * @param value the instruction that provides the value to be switched over
     * @param successors the list of successors of this switch
     */
    public SwitchNode(ValueNode value, BeginNode[] successors, int[] keySuccessors, double[] keyProbabilities) {
        super(StampFactory.forVoid());
        assert value.getKind() == Kind.Int || value.getKind() == Kind.Long || value.getKind() == Kind.Object : value.getKind() + " key not supported by SwitchNode";
        assert keySuccessors.length == keyProbabilities.length;
        this.successors = new NodeSuccessorList<>(this, successors);
        this.value = value;
        this.keySuccessors = keySuccessors;
        this.keyProbabilities = keyProbabilities;
        assert assertProbabilities();
    }

    private boolean assertProbabilities() {
        double total = 0;
        for (double d : keyProbabilities) {
            total += d;
            assert d >= 0.0 : "Cannot have negative probabilities in switch node: " + d;
        }
        assert total > 0.999 && total < 1.001 : "Total " + total;
        return true;
    }

    protected boolean assertValues() {
        Kind kind = value.getKind();
        for (int i = 0; i < keyCount(); i++) {
            Constant key = keyAt(i);
            assert key.getKind() == kind;
        }
        return true;
    }

    @Override
    public double probability(BeginNode successor) {
        double sum = 0;
        for (int i = 0; i < keySuccessors.length; i++) {
            if (successors.get(keySuccessors[i]) == successor) {
                sum += keyProbabilities[i];
            }
        }
        return sum;
    }

    @Override
    public void setProbability(BeginNode successor, double value) {
        double changeInProbability = 0;
        int nonZeroProbabilityCases = 0;
        for (int i = 0; i < keySuccessors.length; i++) {
            if (successors.get(keySuccessors[i]) == successor) {
                changeInProbability += keyProbabilities[i] - value;
                keyProbabilities[i] = value;
            } else if (keyProbabilities[i] > 0) {
                nonZeroProbabilityCases++;
            }
        }

        if (nonZeroProbabilityCases > 0) {
            double changePerEntry = changeInProbability / nonZeroProbabilityCases;
            if (changePerEntry != 0) {
                for (int i = 0; i < keyProbabilities.length; i++) {
                    if (keyProbabilities[i] > 0) {
                        keyProbabilities[i] = keyProbabilities[i] + changePerEntry;
                    }
                }
            }
        }

        assertProbabilities();
    }

    public ValueNode value() {
        return value;
    }

    public abstract boolean isSorted();

    /**
     * The number of distinct keys in this switch.
     */
    public abstract int keyCount();

    /**
     * The key at the specified position, encoded in a Constant.
     */
    public abstract Constant keyAt(int i);

    /**
     * Returns the index of the successor belonging to the key at the specified index.
     */
    public int keySuccessorIndex(int i) {
        return keySuccessors[i];
    }

    /**
     * Returns the successor for the key at the given index.
     */
    public BeginNode keySuccessor(int i) {
        return successors.get(keySuccessors[i]);
    }

    /**
     * Returns the probability of the key at the given index.
     */
    public double keyProbability(int i) {
        return keyProbabilities[i];
    }

    /**
     * Returns the index of the default (fall through) successor of this switch.
     */
    public int defaultSuccessorIndex() {
        return keySuccessors[keySuccessors.length - 1];
    }

    public BeginNode blockSuccessor(int i) {
        return successors.get(i);
    }

    public void setBlockSuccessor(int i, BeginNode s) {
        successors.set(i, s);
    }

    public int blockSuccessorCount() {
        return successors.count();
    }

    /**
     * Gets the successor corresponding to the default (fall through) case.
     * 
     * @return the default successor
     */
    public BeginNode defaultSuccessor() {
        if (defaultSuccessorIndex() == -1) {
            throw new GraalInternalError("unexpected");
        }
        return defaultSuccessorIndex() == -1 ? null : successors.get(defaultSuccessorIndex());
    }

    @Override
    public void afterClone(Node other) {
        SwitchNode oldSwitch = (SwitchNode) other;
        keyProbabilities = Arrays.copyOf(oldSwitch.keyProbabilities, oldSwitch.keyProbabilities.length);
        keySuccessors = Arrays.copyOf(oldSwitch.keySuccessors, oldSwitch.keySuccessors.length);
    }
}
