/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.vector.nodes.op.VectorTransformation;

import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public class VectorTransformationIterator implements VectorIterator {

    private final ArrayList<VectorIterator> inputs;

    public static VectorTransformationIterator createInitialIterator(VectorTransformation seq, AnchoringNode anchor, TargetDescription target) {
        return new VectorTransformationIterator(createInitialIterators(seq, anchor, target));
    }

    protected static ArrayList<VectorIterator> createInitialIterators(VectorTransformation seq, AnchoringNode anchor, TargetDescription target) {
        ArrayList<VectorIterator> inputs = new ArrayList<>();
        for (ValueNode input : seq.getVectorInputs()) {
            inputs.add(VectorInitialIteratorNode.createInitialIterator((VectorNode) input, anchor, target));
        }
        return inputs;
    }

    public static VectorTransformationIterator createPhiIterator(VectorTransformation seq, AbstractMergeNode merge, AnchoringNode anchor, TargetDescription target) {
        return new VectorTransformationIterator(createPhiIterators(seq, merge, anchor, target));
    }

    protected static ArrayList<VectorIterator> createPhiIterators(VectorTransformation seq, AbstractMergeNode merge, AnchoringNode anchor, TargetDescription target) {
        ArrayList<VectorIterator> inputs = new ArrayList<>();
        for (ValueNode input : seq.getVectorInputs()) {
            inputs.add(VectorInitialIteratorNode.createPhiIterator(merge, (VectorNode) input, anchor, target));
        }
        return inputs;
    }

    protected VectorTransformationIterator(ArrayList<VectorIterator> inputs) {
        this.inputs = inputs;
    }

    @Override
    public void addPhiInput(VectorIterator input) {
        VectorTransformationIterator inputIterator = (VectorTransformationIterator) input;
        assert inputs.size() == inputIterator.inputs.size() : inputs + " vs " + inputIterator.inputs;
        for (int i = 0; i < inputs.size(); i++) {
            inputs.get(i).addPhiInput(inputIterator.inputs.get(i));
        }
    }

    @Override
    public VectorNode getVector(VectorNode source, VectorIterationState state, FixedNode position, ConstantReflectionProvider constantReflection, int stepLength) {
        VectorTransformation transformation = (VectorTransformation) source;
        ValueNode[] inputVectors = new ValueNode[inputs.size()];

        int i = 0;
        for (ValueNode inputNode : transformation.getVectorInputs()) {
            inputVectors[i] = inputs.get(i).getVector((VectorNode) inputNode, state, position, constantReflection, stepLength).asNode();
            i++;
        }

        return transformation.createCopy(position, inputVectors);
    }

    @Override
    public LogicNode hasNext(VectorNode vector, VectorIterationState state, int stepLength, ValueNode limit) {
        VectorTransformation transformation = (VectorTransformation) vector;
        LogicNode ret = null;
        int i = 0;
        for (ValueNode inputNode : transformation.getVectorInputs()) {
            LogicNode inputHasNext = inputs.get(i++).hasNext((VectorNode) inputNode, state, stepLength, limit);
            if (inputHasNext != null) {
                if (ret == null) {
                    ret = inputHasNext;
                } else {
                    ret = LogicNode.and(ret, inputHasNext, BranchProbabilityData.unknown());
                }
            }
        }
        return ret;
    }

    protected ArrayList<VectorIterator> nextInputs(VectorNode vector, ValueNode stepLength) {
        VectorTransformation transformation = (VectorTransformation) vector;
        ArrayList<VectorIterator> newInputs = new ArrayList<>(inputs.size());

        int i = 0;
        for (ValueNode inputNode : transformation.getVectorInputs()) {
            newInputs.add(inputs.get(i++).next((VectorNode) inputNode, stepLength));
        }
        return newInputs;
    }

    @Override
    public VectorIterator next(VectorNode vector, ValueNode stepLength) {
        return new VectorTransformationIterator(nextInputs(vector, stepLength));
    }
}
