/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.phases;

import static jdk.graal.compiler.options.OptionType.Debug;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.VirtualState.NodePositionClosure;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;

public final class VectorLoopUtility {

    public static class Options {
        // @formatter:off
        @Option(help = "Avoid loop optimizations on vectorizable loops.", type = Debug)
        public static final OptionKey<Boolean> RespectVectorization = new OptionKey<>(true);
        // @formatter:on
    }

    private static final TimerKey vectorizationCheck = DebugContext.timer("Time_Peeling_VectorizationCheck");

    private VectorLoopUtility() {
    }

    /**
     * Checks whether a loop should be protected from scalar loop optimizations because vector
     * analysis can recognize it.
     */
    @SuppressWarnings("try")
    public static boolean potentialVectorLoop(Loop loop, StructuredGraph graph, CoreProviders providers) {
        if (!Options.RespectVectorization.getValue(graph.getOptions())) {
            return false;
        }
        try (DebugCloseable dc = vectorizationCheck.start(graph.getDebug())) {
            return LoopVectorizationAnalysis.detectVectorizableLoop(loop, true, providers) != null;
        }
    }

    /**
     * Merges all {@link LoopEndNode} nodes of the given loop if there is more than one.
     */
    @SuppressWarnings("try")
    public static boolean mergeLoopEnds(LoopBeginNode loopBegin) {
        if (loopBegin.loopEnds().count() == 1) {
            return false;
        }
        try (DebugCloseable s = loopBegin.withNodeSourcePosition()) {
            MergeNode merge = loopBegin.graph().add(new MergeNode());
            for (LoopEndNode le : loopBegin.loopEnds()) {
                EndNode end = loopBegin.graph().add(new EndNode());
                merge.addForwardEnd(end);
                FixedWithNextNode fwn = (FixedWithNextNode) le.predecessor();
                fwn.setNext(null);
                fwn.setNext(end);
            }
            EconomicMap<PhiNode, PhiNode> old2New = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
            for (PhiNode phi : loopBegin.phis()) {
                PhiNode copy = phi.duplicateOn(merge);
                for (LoopEndNode le : loopBegin.loopEnds()) {
                    copy.addInput(phi.valueAt(le));
                }
                old2New.put(phi, copy);
            }
            GraalError.guarantee(old2New.size() == loopBegin.phis().count(), "Sizes for phi must match - old=%s vs %s", old2New.size(), loopBegin.phis().snapshot());
            LoopEndNode newEnd = loopBegin.graph().add(new LoopEndNode(loopBegin));
            for (PhiNode loopPhi : loopBegin.phis()) {
                PhiNode phi = old2New.get(loopPhi);
                loopPhi.addInput(phi.singleValueOrThis());
            }
            for (LoopEndNode le : loopBegin.loopEnds().snapshot()) {
                if (le == newEnd) {
                    continue;
                }
                loopBegin.removeEnd(le);
                le.safeDelete();
            }
            if (loopBegin.stateAfter() != null) {
                FrameState duplicatedState = loopBegin.stateAfter().duplicateWithVirtualState();
                duplicatedState.applyToNonVirtual(new NodePositionClosure<>() {
                    @Override
                    public void apply(Node from, Position p) {
                        ValueNode usage = (ValueNode) p.get(from);
                        if (loopBegin.isPhiAtMerge(usage)) {
                            Node replacement = old2New.get((PhiNode) usage).singleValueOrThis();
                            p.set(from, replacement);
                        }
                    }
                });
                merge.setStateAfter(duplicatedState);
            }
            merge.setNext(newEnd);
        }
        return true;
    }

    /**
     * Checks constant loop counts and hidden constants represented by a narrow stamp.
     */
    public static boolean isConstantLoopCount(Loop loop, long constantLimit) {
        if (loop.counted() == null) {
            return false;
        }
        CountedLoopInfo counted = loop.counted();
        if (!counted.counterNeverOverflows() || !counted.countedIntegrityValid()) {
            return false;
        }
        if (counted.isConstantMaxTripCount()) {
            return counted.constantMaxTripCount().isLessThan(constantLimit);
        }
        return nodeStampInRange(counted.maxTripCountNode(), constantLimit, counted);
    }

    private static boolean nodeStampInRange(ValueNode maxTripCount, long iterationLimit, CountedLoopInfo counted) {
        final Stamp limitStamp = maxTripCount.stamp(NodeView.DEFAULT);
        if (limitStamp instanceof IntegerStamp iS) {
            final long lowerBound = iS.lowerBound();
            final long upperBound = iS.upperBound();
            if (lowerBound == upperBound) {
                return lowerBound >= 0 && lowerBound < iterationLimit;
            }
            if (IntegerStamp.subtractionOverflows(upperBound, lowerBound, 64)) {
                return false;
            }
            try {
                final long distanceLowerUpper = NumUtil.safeAbs(upperBound - lowerBound);
                final InductionVariable counter = counted.getLimitCheckedIV();
                final long stride = counter.isConstantStride() ? NumUtil.safeAbs(counter.constantStride()) : 1;
                final long strideRelativeStartToLimitDistance = distanceLowerUpper / stride;
                return strideRelativeStartToLimitDistance <= iterationLimit;
            } catch (ArithmeticException e) {
                return false;
            }
        }
        return false;
    }
}
