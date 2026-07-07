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

import jdk.graal.compiler.vector.nodes.VectorLogicNode;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorGuardNode;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public class VectorGuardIterator extends StateVectorIterator {

    private final VectorIterator condition;

    public VectorGuardIterator(ValueNode index, VectorIterator condition, ArrayList<VectorIterator> stateVectors) {
        super(index, stateVectors);
        this.condition = condition;
    }

    @Override
    @SuppressWarnings("try")
    public VectorConsumerIterator next(VectorConsumer consumer, int stepLength, FixedNode position, ConstantReflectionProvider constantReflection) {
        VectorGuardNode vectorGuard = (VectorGuardNode) consumer;
        StructuredGraph graph = vectorGuard.graph();
        ArrayList<VectorNode> guardStateVectors = vectorGuard.getStateVectors();

        try (DebugCloseable positionScope = vectorGuard.withNodeSourcePosition()) {
            VectorLogicNode nextCondition = (VectorLogicNode) condition.getVector(vectorGuard.getCondition(), this, position, constantReflection, stepLength);
            ArrayList<VectorNode> nextStateVectors = nextStateVectors(guardStateVectors, position, constantReflection, stepLength);

            VectorGuardNode partialVectorGuard = graph.add(new VectorGuardNode(nextCondition, ConstantNode.forInt(stepLength, graph), vectorGuard.direction(), vectorGuard.getDeoptBranch(),
                            vectorGuard.getDeoptProbability(), vectorGuard.getAction(), vectorGuard.getReason(), vectorGuard.getDebugId(), vectorGuard.getSpeculation(), vectorGuard.stateBefore(),
                            vectorGuard.getVectorPositions(), nextStateVectors));
            graph.addBeforeFixed(position, partialVectorGuard);
            cacheVectorGuard(vectorGuard, position, partialVectorGuard);
        }

        ValueNode stepLengthNode = ConstantNode.forInt(stepLength, graph);
        return shift(vectorGuard, stepLengthNode);
    }

    @Override
    public VectorConsumerIterator shift(VectorConsumer consumer, ValueNode shiftAmount) {
        VectorGuardNode vectorGuard = (VectorGuardNode) consumer;
        StructuredGraph graph = vectorGuard.graph();
        ArrayList<VectorNode> guardStateVectors = vectorGuard.getStateVectors();
        ArrayList<VectorIterator> nextStateVectorIterators = nextStateVectorIterators(guardStateVectors, shiftAmount);
        return new VectorGuardIterator(getNextIndex(graph, shiftAmount), condition.next(vectorGuard.getCondition(), shiftAmount), nextStateVectorIterators);
    }

    @Override
    public void addPhiInput(VectorConsumer consumer, VectorConsumerIterator input, AbstractEndNode branch) {
        super.addPhiInput(consumer, input, branch);

        VectorGuardIterator deoptIterator = (VectorGuardIterator) input;
        condition.addPhiInput(deoptIterator.condition);
        addStateVectorPhiInputs(deoptIterator);
    }

    @Override
    public ValueNode getAlignCount(VectorConsumer consumer, int align) {
        assert consumer instanceof VectorGuardNode && !((VectorGuardNode) consumer).getSupportsAlignment() : consumer;
        return ConstantNode.forInt(0, consumer.asNode().graph());
    }
}
