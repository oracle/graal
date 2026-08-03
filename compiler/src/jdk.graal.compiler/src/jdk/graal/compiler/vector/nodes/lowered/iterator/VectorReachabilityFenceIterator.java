/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorReachabilityFenceNode;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public class VectorReachabilityFenceIterator extends StateVectorIterator {

    public VectorReachabilityFenceIterator(ValueNode index, ArrayList<VectorIterator> stateVectors) {
        super(index, stateVectors);
    }

    @Override
    @SuppressWarnings("try")
    public VectorConsumerIterator next(VectorConsumer consumer, int stepLength, FixedNode position, ConstantReflectionProvider constantReflection) {
        VectorReachabilityFenceNode vectorReachabilityFence = (VectorReachabilityFenceNode) consumer;
        StructuredGraph graph = vectorReachabilityFence.graph();
        ArrayList<VectorNode> stateVectors = vectorReachabilityFence.getObjectVectors();

        try (DebugCloseable positionScope = vectorReachabilityFence.withNodeSourcePosition()) {
            ArrayList<VectorNode> nextStateVectors = nextStateVectors(stateVectors, position, constantReflection, stepLength);
            ValueNode[] nextStateVectorNodes = CollectionsUtil.mapToArray(nextStateVectors, vector -> vector.asNode(), ValueNode[]::new);
            VectorReachabilityFenceNode nextVectorReachabilityFence = graph.add(new VectorReachabilityFenceNode(ConstantNode.forInt(stepLength, graph), vectorReachabilityFence.direction(),
                            nextStateVectorNodes));
            graph.addBeforeFixed(position, nextVectorReachabilityFence);
        }

        ValueNode stepLengthNode = ConstantNode.forInt(stepLength, graph);
        return shift(vectorReachabilityFence, stepLengthNode);
    }

    @Override
    public VectorConsumerIterator shift(VectorConsumer consumer, ValueNode shiftAmount) {
        VectorReachabilityFenceNode vectorReachabilityFence = (VectorReachabilityFenceNode) consumer;
        StructuredGraph graph = vectorReachabilityFence.graph();
        ArrayList<VectorNode> stateVectors = vectorReachabilityFence.getObjectVectors();
        ArrayList<VectorIterator> nextStateVectorIterators = nextStateVectorIterators(stateVectors, shiftAmount);
        return new VectorReachabilityFenceIterator(getNextIndex(graph, shiftAmount), nextStateVectorIterators);
    }

    @Override
    public void addPhiInput(VectorConsumer consumer, VectorConsumerIterator input, AbstractEndNode branch) {
        super.addPhiInput(consumer, input, branch);
        addStateVectorPhiInputs((VectorReachabilityFenceIterator) input);
    }

    @Override
    public ValueNode getAlignCount(VectorConsumer consumer, int align) {
        return ConstantNode.forInt(0, consumer.asNode().graph());
    }
}
