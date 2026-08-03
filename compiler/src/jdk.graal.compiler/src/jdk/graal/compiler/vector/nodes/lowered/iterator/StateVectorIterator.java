/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.lowered.iterator;

import java.util.ArrayList;

import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorInitialIteratorNode;

import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Abstract base iterator class for vector consumers that manage a frame state and the corresponding
 * state vectors.
 */
public abstract class StateVectorIterator extends DefaultVectorConsumerIterator implements ShiftableVectorConsumerIterator {

    private final ArrayList<VectorIterator> stateVectors;

    public StateVectorIterator(ValueNode index, ArrayList<VectorIterator> stateVectors) {
        super(index);
        this.stateVectors = stateVectors;
    }

    public static ArrayList<VectorIterator> createInitialIterators(Iterable<VectorNode> stateVectors, AnchoringNode anchor, TargetDescription target) {
        ArrayList<VectorIterator> stateVectorIterators = new ArrayList<>();
        for (VectorNode stateVector : stateVectors) {
            stateVectorIterators.add(VectorInitialIteratorNode.createInitialIterator(stateVector, anchor, target));
        }
        return stateVectorIterators;
    }

    public static ArrayList<VectorIterator> createPhiIterators(Iterable<VectorNode> stateVectors, AbstractMergeNode merge, AnchoringNode anchor, TargetDescription target) {
        ArrayList<VectorIterator> stateVectorIterators = new ArrayList<>();
        for (VectorNode stateVector : stateVectors) {
            stateVectorIterators.add(VectorInitialIteratorNode.createPhiIterator(merge, stateVector, anchor, target));
        }
        return stateVectorIterators;
    }

    protected ArrayList<VectorNode> nextStateVectors(ArrayList<VectorNode> currentStateVectors, FixedNode position, ConstantReflectionProvider constantReflection, int stepLength) {
        assert stateVectors.size() == currentStateVectors.size() : stateVectors + " vs " + currentStateVectors;
        ArrayList<VectorNode> nextStateVectors = new ArrayList<>();
        for (int i = 0; i < stateVectors.size(); i++) {
            nextStateVectors.add(stateVectors.get(i).getVector(currentStateVectors.get(i), this, position, constantReflection, stepLength));
        }
        return nextStateVectors;
    }

    protected ArrayList<VectorIterator> nextStateVectorIterators(ArrayList<VectorNode> currentStateVectors, ValueNode stepLength) {
        assert stateVectors.size() == currentStateVectors.size() : stateVectors + " vs " + currentStateVectors;
        ArrayList<VectorIterator> iterators = new ArrayList<>();
        for (int i = 0; i < stateVectors.size(); i++) {
            iterators.add(stateVectors.get(i).next(currentStateVectors.get(i), stepLength));
        }
        return iterators;
    }

    protected void addStateVectorPhiInputs(StateVectorIterator otherIterator) {
        assert stateVectors.size() == otherIterator.stateVectors.size() : stateVectors + " vs " + otherIterator.stateVectors;
        for (int i = 0; i < stateVectors.size(); i++) {
            stateVectors.get(i).addPhiInput(otherIterator.stateVectors.get(i));
        }
    }
}
