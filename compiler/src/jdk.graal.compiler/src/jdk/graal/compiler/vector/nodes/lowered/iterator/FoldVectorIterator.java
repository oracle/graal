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

import java.util.Arrays;

import org.graalvm.collections.EconomicMap;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode;
import jdk.graal.compiler.vector.nodes.consumer.LowerableVectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorLoopNode;
import jdk.graal.compiler.vector.nodes.lowered.FinishVectorConsumerNode;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public class FoldVectorIterator extends DefaultVectorConsumerIterator {

    protected ValueNode current;
    protected VectorIterator[] values;
    protected EconomicMap<LocationIdentity, MemoryKill> lastLocationAccesses;

    public FoldVectorIterator(ValueNode index, ValueNode current, VectorIterator[] values) {
        super(index);
        this.current = current;
        this.values = values;
        this.lastLocationAccesses = null;
    }

    @Override
    public void addPhiInput(VectorConsumer consumer, VectorConsumerIterator input, AbstractEndNode branch) {
        super.addPhiInput(consumer, input, branch);

        FoldVectorIterator fold = (FoldVectorIterator) input;

        PhiNode phi = (PhiNode) current;
        phi.addInput(fold.current);

        assert values.length == fold.values.length : input + " -> " + values + " vs " + fold.values;
        for (int i = 0; i < values.length; i++) {
            values[i].addPhiInput(fold.values[i]);
        }
    }

    protected VectorIterator[] getNextValues(FoldVectorNode fold, ValueNode stepLength) {
        VectorIterator[] nextValues = new VectorIterator[values.length];
        for (int i = 0; i < nextValues.length; i++) {
            nextValues[i] = values[i].next(fold.getVectorInput(i), stepLength);
        }
        return nextValues;
    }

    @Override
    @SuppressWarnings("try")
    public VectorConsumerIterator next(VectorConsumer consumer, int stepLength, FixedNode position, ConstantReflectionProvider constantReflection) {
        FoldVectorNode fold = (FoldVectorNode) consumer;
        StructuredGraph graph = fold.graph();

        try (DebugCloseable positionScope = fold.withNodeSourcePosition()) {
            ValueNode[] vectors = new ValueNode[values.length];
            for (int i = 0; i < vectors.length; i++) {
                vectors[i] = values[i].getVector(fold.getVectorInput(i), this, position, constantReflection, stepLength).asNode();
            }

            ValueNode stepLengthNode = ConstantNode.forInt(stepLength, graph);
            StructuredGraph op = fold.getOp();
            FoldVectorNode partialFold = graph.add(new FoldVectorNode((StructuredGraph) op.copy(op.getDebug()), current, stepLengthNode, fold.direction(), Arrays.asList(vectors),
                            fold.getScalarInputs()));

            graph.addBeforeFixed(position, partialFold);

            return new FoldVectorIterator(getNextIndex(graph, stepLengthNode), partialFold, getNextValues(fold, stepLengthNode));
        }
    }

    @Override
    public LogicNode hasNext(VectorConsumer consumer, int stepLength, ValueNode limit, ConstantReflectionProvider constantReflection) {
        LogicNode hasNext = super.hasNext(consumer, stepLength, limit, constantReflection);

        FoldVectorNode fold = (FoldVectorNode) consumer;
        for (int i = 0; i < values.length; i++) {
            hasNext = hasNextValue(hasNext, values[i], fold.getVectorInput(i), stepLength, limit);
        }
        return hasNext;
    }

    @Override
    public ValueNode getAlignCount(VectorConsumer consumer, int align) {
        assert consumer instanceof FoldVectorNode && !((FoldVectorNode) consumer).getSupportsAlignment() : consumer;
        return ConstantNode.forInt(0, consumer.asNode().graph());
    }

    protected static void transferUsagesToFinishNode(FinishVectorConsumerNode finish) {
        LowerableVectorConsumer fold = (FoldVectorNode) finish.getConsumer();
        if (fold.isPartOfALoop()) {
            // Transfer the original fold's usages to the finish node so that they can be
            // transferred to the final replacement of the fold. For folds that are not part of a
            // group, this is already done via the dummy node in VectorSnippets.lower.
            fold.asNode().replaceAtUsages(finish, node -> !(node instanceof VectorLoopNode) && !(node instanceof FinishVectorConsumerNode));
        }
    }

    @Override
    public void finishConsumer(FinishVectorConsumerNode finish) {
        transferUsagesToFinishNode(finish);
        finish.replaceAtUsages(current);
        if (finish.isAlive()) {
            finish.graph().removeFixed(finish);
        }
    }

    public void setLastLocationAccesses(EconomicMap<LocationIdentity, MemoryKill> lastLocationAccesses) {
        this.lastLocationAccesses = lastLocationAccesses;
    }

    @Override
    public MemoryKill getLastLocationAccess(LocationIdentity location) {
        if (lastLocationAccesses != null && lastLocationAccesses.containsKey(location)) {
            return lastLocationAccesses.get(location);
        }
        return null;
    }
}
