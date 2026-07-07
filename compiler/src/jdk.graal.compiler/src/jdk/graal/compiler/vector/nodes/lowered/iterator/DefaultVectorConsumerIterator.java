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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.consumer.LowerableVectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorGuardNode;
import jdk.graal.compiler.vector.nodes.lowered.FinishVectorConsumerNode;
import jdk.graal.compiler.vector.nodes.producer.VectorReadNode;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.MultiGuardNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public abstract class DefaultVectorConsumerIterator implements VectorConsumerIterator, VectorIterationState {

    protected final ValueNode index;
    EconomicMap<Pair<VectorReadNode, FixedNode>, VectorReadNode> readCache;
    EconomicMap<Pair<VectorGuardNode, FixedNode>, VectorGuardNode> guardCache;

    protected DefaultVectorConsumerIterator(ValueNode index) {
        this.index = index;
        this.readCache = EconomicMap.create();
        this.guardCache = EconomicMap.create();
    }

    @Override
    public void addPhiInput(VectorConsumer consumer, VectorConsumerIterator input, AbstractEndNode branch) {
        PhiNode phi = (PhiNode) index;
        DefaultVectorConsumerIterator it = (DefaultVectorConsumerIterator) input;

        if (consumer instanceof LowerableVectorConsumer && ((LowerableVectorConsumer) consumer).isPartOfALoop()) {
            /*
             * Inside a vector loop we might share VectorRead operations and their index
             * computations. Lowering different consumers of a read will try to add such shared
             * computations several times to the same phi; avoid that.
             */
            int i = 0;
            for (ValueNode phiInput : phi.values()) {
                if (phiInput == it.index && (i >= phi.merge().forwardEndCount() || phi.merge().forwardEndAt(i) == branch)) {
                    // Don't add this input, it's already present.
                    return;
                }
                i++;
            }
        }
        phi.addInput(it.index);
    }

    @Override
    public ValueNode getIndex() {
        return index;
    }

    @Override
    public MemoryKill getLastLocationAccess(LocationIdentity location) {
        return null;
    }

    protected ValueNode getNextIndex(StructuredGraph graph, ValueNode stepLength) {
        ValueNode stepLengthNode = IntegerConvertNode.convert(stepLength, index.stamp(NodeView.DEFAULT), NodeView.DEFAULT);
        return BinaryArithmeticNode.add(graph, index, stepLengthNode, NodeView.DEFAULT);
    }

    @Override
    public LogicNode hasNext(VectorConsumer consumer, int stepLength, ValueNode limit, ConstantReflectionProvider constantReflection) {
        StructuredGraph graph = consumer.asNode().graph();
        ValueNode clampedLimit = consumer.getLength();
        if (limit != null) {
            LogicNode condition = IntegerLessThanNode.create(limit, clampedLimit, NodeView.DEFAULT);
            clampedLimit = ConditionalNode.create(condition, limit, clampedLimit, NodeView.DEFAULT);
        }
        ValueNode lastIndex = BinaryArithmeticNode.sub(graph, clampedLimit, ConstantNode.forIntegerStamp(index.stamp(NodeView.DEFAULT), stepLength, graph), NodeView.DEFAULT);
        LogicNode atEnd = CompareNode.createCompareNode(graph, CanonicalCondition.LT, lastIndex, index, constantReflection, NodeView.DEFAULT);
        return graph.addOrUniqueWithInputs(LogicNegationNode.create(atEnd));
    }

    protected LogicNode hasNextValue(LogicNode prev, VectorIterator value, VectorNode vector, int stepLength, ValueNode limit) {
        LogicNode hasNextValue = value.hasNext(vector, this, stepLength, limit);
        if (hasNextValue == null) {
            return prev;
        } else {
            return LogicNode.and(prev, hasNextValue, BranchProbabilityNode.LIKELY_PROFILE);
        }
    }

    @Override
    public void finishConsumer(FinishVectorConsumerNode finish) {
        if (finish.isAlive()) {
            finish.graph().removeFixed(finish);
        }
    }

    @Override
    public VectorReadNode getCachedVectorRead(VectorReadNode originalRead, FixedNode position) {
        return readCache.get(Pair.create(originalRead, position));
    }

    @Override
    public void cacheVectorRead(VectorReadNode originalRead, FixedNode position, VectorReadNode newRead) {
        readCache.put(Pair.create(originalRead, position), newRead);
    }

    @Override
    public void setVectorReadCache(EconomicMap<Pair<VectorReadNode, FixedNode>, VectorReadNode> readCache) {
        this.readCache = readCache;
    }

    @Override
    public GuardingNode getCachedVectorGuard(GuardingNode originalGuard, FixedNode position) {
        GuardingNode cachedOrNewGuard;
        if (originalGuard instanceof VectorGuardNode vectorGuard) {
            /* Vector guards must be present in the cache directly. */
            cachedOrNewGuard = guardCache.get(Pair.create(vectorGuard, position));
        } else if (originalGuard instanceof MultiGuardNode multiGuard) {
            /*
             * We often have graph shapes where a vector node is not guarded directly by a
             * VectorGuard but by a MultiGuard that includes other guards too. Duplicate the
             * MultiGuard, replacing all original VectorGuards by their cached lowered versions.
             */
            MultiGuardNode newMultiGuard = multiGuard.graph().addWithoutUnique(new MultiGuardNode());
            for (Node input : multiGuard.inputs()) {
                GuardingNode guard = (GuardingNode) input;
                if (guard instanceof VectorGuardNode vectorGuard) {
                    guard = guardCache.get(Pair.create(vectorGuard, position));
                }
                newMultiGuard.addGuard(guard);
            }
            cachedOrNewGuard = newMultiGuard;
        } else {
            /*
             * Non-vector guards are outside the original vector loop, we can reuse them unchanged.
             */
            cachedOrNewGuard = originalGuard;
        }
        /*
         * Besides tracking original guards, also anchor the usage in place at the position where
         * the vector computation is expanded in the graph. This is necessary in cases where the
         * original guard is outside the vectorized loop. It is also what we want logically: The
         * guarded vector computation should be close to where it is consumed.
         */
        AbstractBeginNode anchor;
        if (position.predecessor() instanceof AbstractBeginNode abstractBegin) {
            anchor = abstractBegin;
        } else {
            anchor = position.graph().add(new BeginNode());
            position.graph().addBeforeFixed(position, anchor);
        }
        if (cachedOrNewGuard != null) {
            return position.graph().addOrUnique(new MultiGuardNode(cachedOrNewGuard.asNode(), anchor));
        } else {
            return anchor;
        }
    }

    @Override
    public void cacheVectorGuard(VectorGuardNode originalGuard, FixedNode position, VectorGuardNode newGuard) {
        guardCache.put(Pair.create(originalGuard, position), newGuard);
    }

    @Override
    public void setVectorGuardCache(EconomicMap<Pair<VectorGuardNode, FixedNode>, VectorGuardNode> guardCache) {
        this.guardCache = guardCache;
    }
}
