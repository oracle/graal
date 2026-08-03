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
import java.util.Iterator;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorGuardNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorLoopNode;
import jdk.graal.compiler.vector.nodes.lowered.FinishVectorConsumerNode;
import jdk.graal.compiler.vector.nodes.producer.VectorReadNode;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * A group of iterators, each one iterating over the corresponding member of a
 * {@link VectorLoopNode}.
 */
public class VectorLoopIterator implements VectorConsumerIterator {

    private final List<VectorConsumerIterator> iterators;
    private EconomicMap<Pair<VectorReadNode, FixedNode>, VectorReadNode> commonReadCache;
    private EconomicMap<Pair<VectorGuardNode, FixedNode>, VectorGuardNode> commonGuardCache;

    public VectorLoopIterator(List<VectorConsumerIterator> iterators) {
        this.iterators = iterators;
        assert !iterators.isEmpty() : iterators;
        this.commonReadCache = EconomicMap.create();
        this.commonGuardCache = EconomicMap.create();
        for (VectorConsumerIterator iterator : iterators) {
            if (iterator instanceof VectorIterationState) {
                ((VectorIterationState) iterator).setVectorReadCache(commonReadCache);
                ((VectorIterationState) iterator).setVectorGuardCache(commonGuardCache);
            }
        }
    }

    @Override
    public VectorConsumerIterator next(VectorConsumer vectorLoop, int stepLength, FixedNode position, ConstantReflectionProvider constantReflection) {
        // Build a new group iterator containing each member's next iterator.
        ArrayList<VectorConsumerIterator> nextIterators = new ArrayList<>(iterators.size());
        EconomicMap<LocationIdentity, MemoryKill> lastLocationAccesses = EconomicMap.create();
        ConsumerIteratorPairIterator pairs = new ConsumerIteratorPairIterator((VectorLoopNode) vectorLoop, iterators);
        while (pairs.hasNext()) {
            Pair<VectorConsumer, VectorConsumerIterator> pair = pairs.next();
            VectorConsumer consumer = pair.getLeft();
            VectorConsumerIterator iterator = pair.getRight();
            if (iterator instanceof VectorWriteIterator) {
                // Multiple write iterators in a group lose lastLocationAccess relations between the
                // partial writes they create. Fix up this iterator's last location access based on
                // partial writes generated for earlier iterators in this group.
                VectorWriteIterator writeIterator = (VectorWriteIterator) iterator;
                LocationIdentity locationIdentity = writeIterator.getLocationIdentity();
                if (!(writeIterator.getLastLocationAccess(locationIdentity) instanceof PhiNode) && lastLocationAccesses.containsKey(locationIdentity)) {
                    writeIterator.setLastLocationAccess(lastLocationAccesses.get(locationIdentity));
                }
            }
            if (iterator instanceof FoldVectorIterator) {
                FoldVectorIterator foldIterator = (FoldVectorIterator) iterator;
                foldIterator.setLastLocationAccesses(lastLocationAccesses);
            }
            VectorConsumerIterator nextIterator = iterator.next(consumer, stepLength, position, constantReflection);
            if (nextIterator instanceof VectorWriteIterator) {
                VectorWriteIterator nextWriteIterator = (VectorWriteIterator) iterator;
                lastLocationAccesses.put(nextWriteIterator.getLocationIdentity(), nextWriteIterator.getPartialWrite());
            }
            nextIterators.add(nextIterator);
        }
        return new VectorLoopIterator(nextIterators);
    }

    @Override
    public LogicNode hasNext(VectorConsumer vectorLoop, int stepLength, ValueNode limit, ConstantReflectionProvider constantReflection) {
        // The iterator has next elements if all members have some.
        ConsumerIteratorPairIterator pairs = new ConsumerIteratorPairIterator((VectorLoopNode) vectorLoop, iterators);
        Pair<VectorConsumer, VectorConsumerIterator> pair = pairs.next();
        VectorConsumer consumer = pair.getLeft();
        VectorConsumerIterator iterator = pair.getRight();
        LogicNode currentHasNext = iterator.hasNext(consumer, stepLength, limit, constantReflection);
        LogicNode hasNext = currentHasNext;
        while (pairs.hasNext()) {
            pair = pairs.next();
            consumer = pair.getLeft();
            iterator = pair.getRight();
            currentHasNext = iterator.hasNext(consumer, stepLength, limit, constantReflection);
            hasNext = LogicNode.and(hasNext, currentHasNext, BranchProbabilityNode.NOT_LIKELY_PROFILE);
        }
        return hasNext;
    }

    @Override
    public ValueNode getAlignCount(VectorConsumer vectorLoop, int align) {
        GraalError.shouldNotReachHere("consumer groups disabled on targets without unaligned accesses, we should never need to align a consumer group iterator"); // ExcludeFromJacocoGeneratedReport
        return null;
    }

    @Override
    public void addPhiInput(VectorConsumer vectorLoop, VectorConsumerIterator input, AbstractEndNode branch) {
        ConsumerIteratorPairIterator pairs = new ConsumerIteratorPairIterator((VectorLoopNode) vectorLoop, iterators);
        VectorLoopIterator inputGroup = (VectorLoopIterator) input;
        assert inputGroup.getIterators().size() == iterators.size() : vectorLoop + " -> " + inputGroup.getIterators() + " " + iterators;
        Iterator<VectorConsumerIterator> inputIterator = inputGroup.getIterators().iterator();
        while (pairs.hasNext() && inputIterator.hasNext()) {
            Pair<VectorConsumer, VectorConsumerIterator> pair = pairs.next();
            VectorConsumer theConsumer = pair.getLeft();
            VectorConsumerIterator iterator = pair.getRight();
            VectorConsumerIterator theInput = inputIterator.next();
            iterator.addPhiInput(theConsumer, theInput, branch);
        }
        assert !pairs.hasNext() && !inputIterator.hasNext() : "Iterators must move together";
    }

    @Override
    public void finishConsumer(FinishVectorConsumerNode finish) {
        VectorLoopNode vectorLoop = (VectorLoopNode) finish.getConsumer();
        ConsumerIteratorPairIterator pairs = new ConsumerIteratorPairIterator(vectorLoop, iterators);
        while (pairs.hasNext()) {
            Pair<VectorConsumer, VectorConsumerIterator> pair = pairs.next();
            VectorConsumer consumer = pair.getLeft();
            VectorConsumerIterator iterator = pair.getRight();
            FinishVectorConsumerNode newFinishNode = finish.graph().add(new FinishVectorConsumerNode(consumer.asNode(), finish.getIterator()));
            finish.graph().addBeforeFixed(finish, newFinishNode);
            iterator.finishConsumer(newFinishNode);
        }
        if (finish.hasUsages()) {
            /*
             * This vector loop has a usage; this is the post loop that needs to know now many
             * elements were processed by the SIMD version of the code.
             */
            GraalError.guarantee(finish.consumedElements() != null, "need number of consumed elements to propagate");
            GraalError.guarantee(vectorLoop.hasPostLoop(), "need post-loop to propagate number of consumed elements");
            /*
             * The SIMD loop counts iterations as long, but the usage expects it as the same type as
             * the original loop counter, typically int. This can only narrow but never lose
             * information since the original loop's counter is guarded against overflow.
             */
            ValueNode consumedElements = finish.graph().addOrUnique(IntegerConvertNode.convert(finish.consumedElements(), finish.stamp(NodeView.DEFAULT), NodeView.DEFAULT));
            finish.replaceAtUsages(consumedElements);
        }
        finish.graph().removeFixed(finish);
    }

    private class ConsumerIteratorPairIterator implements Iterator<Pair<VectorConsumer, VectorConsumerIterator>> {
        private Iterator<ValueNode> consumers;
        private Iterator<VectorConsumerIterator> iterators;

        ConsumerIteratorPairIterator(VectorLoopNode vectorLoop, List<VectorConsumerIterator> iterators) {
            assert vectorLoop.getConsumers().size() == iterators.size() : vectorLoop + " " + vectorLoop.getConsumers() + " " + iterators;
            this.consumers = vectorLoop.getConsumers().iterator();
            this.iterators = iterators.iterator();
        }

        @Override
        public Pair<VectorConsumer, VectorConsumerIterator> next() {
            return Pair.create((VectorConsumer) consumers.next(), iterators.next());
        }

        @Override
        public boolean hasNext() {
            assert consumers.hasNext() == iterators.hasNext() : "Consumer and iterator have to move together ";
            return consumers.hasNext();
        }
    }

    public List<VectorConsumerIterator> getIterators() {
        return iterators;
    }
}
