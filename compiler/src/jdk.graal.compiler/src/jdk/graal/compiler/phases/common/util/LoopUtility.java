/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.util;

import java.util.ArrayDeque;
import java.util.EnumSet;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.duplication.util.DuplicationUtil;
import jdk.graal.compiler.graph.Graph.Mark;
import jdk.graal.compiler.graph.Graph.NodeEvent;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardProxyNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MemoryProxyNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.VirtualState.NodePositionClosure;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.FloatingIntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.CaptureStateBeginNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.OpaqueValueNode;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.vector.phases.LoopVectorizationAnalysis;
import jdk.graal.compiler.vector.phases.VectorLoopUtility;

public class LoopUtility {

    /**
     * Determine if the given loop comes from a {@link SnippetTemplate} snippet with a
     * side-effecting body. See {@link CaptureStateBeginNode#verifyNode()} for details.
     */
    public static boolean snippetSideEffectLoop(Loop lex) {
        for (LoopExitNode lexNode : lex.loopBegin().loopExits()) {
            if (lexNode.next() instanceof CaptureStateBeginNode) {
                return true;
            }
        }
        return false;
    }

    private static final TimerKey vectorizationCheck = DebugContext.timer("Time_Peeling_VectorizationCheck");

    @SuppressWarnings("try")
    public static boolean potentialVectorLoop(Loop loop, StructuredGraph graph, CoreProviders providers) {
        if (!VectorLoopUtility.Options.RespectVectorization.getValue(graph.getOptions())) {
            return false;
        }
        try (DebugCloseable dc = vectorizationCheck.start(graph.getDebug())) {
            return LoopVectorizationAnalysis.detectVectorizableLoop(loop, true, providers) != null;
        }
    }

    /**
     * Create {@link LoopExitNode} nodes before {@link Loop#isCounted()} {@linkplain Loop loops}
     * that terminate with {@link DeoptimizeNode}.
     *
     * The {@link CountedLoopInfo} API in Graal supports loops which
     * {@link CountedLoopInfo#getLimitTest()} terminate the loop with either a {@link LoopExitNode}
     * or {@link DeoptimizeNode}. In order for loop optimizations to only support one common case
     * ({@link LoopExitNode}) this method takes counted loop exit paths that terminate the loop with
     * a {@link DeoptimizeNode} and inserts {@link LoopExitNode} before.
     *
     * @return {@code true} if the counted exit path was a {@link DeoptimizeNode} and this method
     *         could insert a {@link LoopExitNode} before it, {@code false} otherwise
     */
    public static boolean createDeoptCountedLoopExitNode(Loop elex) {
        GraalError.guarantee(elex.loopBegin().graph().getGuardsStage().areFrameStatesAtSideEffects(),
                        "Cannot use this method after framestate assignment because Deoptimize nodes have input then potentially requiring proxies");
        assert elex.isCounted() : "Loop must be counted " + elex;
        final CountedLoopInfo cli = elex.counted();
        final AbstractBeginNode abn = cli.getCountedExit();
        final StructuredGraph graph = abn.graph();
        if (!(abn instanceof LoopExitNode)) {
            if (abn.next() instanceof DeoptimizeNode) {
                assert abn.graph().getGuardsStage().areFrameStatesAtSideEffects() : "Must run before FSA";
                insertLoopExitNodeAndBuildState(graph, abn, elex);
                return true;
            }
        }
        return false;
    }

    private static LoopExitNode insertLoopExitNodeAndBuildState(StructuredGraph graph, AbstractBeginNode countedIrregularExit, Loop elex) {
        FrameState lastState = GraphUtil.findLastFrameState(countedIrregularExit);
        assert lastState != null;
        LoopExitNode lex = graph.add(new LoopExitNode(elex.loopBegin()));
        Mark before = graph.getMark();
        lastState = lastState.duplicateWithVirtualState();
        for (Node newNode : graph.getNewNodes(before).snapshot()) {
            for (Position p : newNode.inputPositions()) {
                Node input = p.get(newNode);
                if (input != null && elex.whole().contains(input)) {
                    if (input instanceof VirtualObjectNode) {
                        continue;
                    }
                    input = proxyNode(p, lex, (ValueNode) input);
                    input = graph.addOrUnique(input);
                    p.set(newNode, input);
                }
            }
        }
        lex.setStateAfter(lastState);
        graph.replaceFixedWithFixed(countedIrregularExit, lex);
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating lex %s for prev sink counted loop", lex);
        return lex;
    }

    private static ValueNode proxyNode(Position p, LoopExitNode lex, ValueNode input) {
        switch (p.getInputType()) {
            case Value:
                return new ValueProxyNode(input, lex);
            case Guard:
                return new GuardProxyNode((GuardingNode) input, lex);
            case Memory:
                MemoryKill inputKill = (MemoryKill) input;
                return new MemoryProxyNode(inputKill, lex, DuplicationUtil.getLocationIdentity((Node) inputKill));
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(p.getInputType()); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * Merges all {@link LoopEndNode} of the given loop if there are more than 1. Returns
     * {@code true} if such a merge happened, otherwise {@code false}.
     */
    @SuppressWarnings("try")
    public static boolean mergeLoopEnds(LoopBeginNode loopBegin) {
        if (loopBegin.loopEnds().count() == 1) {
            return false;
        }
        /*
         * We use the NodeSourcePosition of the loop begin node: merge loop ends just creates a new
         * landing pad before the jump to the actual loop begin node. Thus, the loop begin is the
         * best approximation to use.
         */
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
            assert old2New.size() == loopBegin.phis().count() : "Sizes for phi must match - old=" + old2New.size() + " vs " + loopBegin.phis().snapshot();
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
                // the merge only needs a state if we are before FSA
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

    public static boolean isConstantLoopCount(Loop loop, long constantLimit) {
        if (loop.counted() == null) {
            return false;
        }
        CountedLoopInfo counted = loop.counted();
        if (!counted.counterNeverOverflows()) {
            return false;
        }
        if (!counted.countedIntegrityValid()) {
            /*
             * It can be that we are in the middle of a loop optimization process and the caller
             * does not know/care about "intact" IVs, and does not recompute them. In favor of
             * compile time be resilient towards broken IVs.
             */
            return false;
        }
        if (counted.isConstantMaxTripCount()) {
            return counted.constantMaxTripCount().isLessThan(constantLimit);
        }
        ValueNode val = counted.maxTripCountNode();
        /**
         * We are looking here for mostly 2 patterns: constant loops and loops that are bounded by a
         * "hidden constant". Such hidden constant patterns are of various forms. On example is
         * loops that offset a constant from a limit. An example can look like this:
         *
         * <pre>
         * int limit = x.limit;
         * int init = limit - 8;
         * int i = init;
         * for (; i < limit; i++) {
         *     body();
         * }
         * </pre>
         *
         * Where they either do 0 or 8 iterations - both are constant values. We catch those cases
         * by checking that the stamp of the node is in range.
         */
        return nodeStampInRange(val, constantLimit, counted);
    }

    public static boolean nodeStampInRange(ValueNode maxTripCount, long iterationLimit, CountedLoopInfo counted) {
        final Stamp limitStamp = maxTripCount.stamp(NodeView.DEFAULT);
        if (limitStamp instanceof IntegerStamp iS) {
            final long lowerBound = iS.lowerBound();
            final long upperBound = iS.upperBound();
            if (lowerBound == upperBound) {
                /**
                 * maxTripCount is an unsigned value. Even loops that do not overflow their stamp
                 * range (e.g. 32bit for integer, 64bit for long) not necessarily have a trip count
                 * limit that fits in the respective value range.
                 *
                 * For example consider the following loop
                 *
                 * <pre>
                 * for (int i = Integer.MIN_VALUE; i < Integer.MAX_VALUE; i++) {
                 *     body();
                 * }
                 * </pre>
                 *
                 * which has a value range of 4294967295 (signed integer -1) does not fit in signed
                 * integer range yet is perfectly counted. Just check that we are in range
                 */
                return lowerBound >= 0 && lowerBound < iterationLimit;
            }
            if (IntegerStamp.subtractionOverflows(upperBound, lowerBound, 64)) {
                return false;
            }
            try {
                final long distanceLowerUpper = NumUtil.safeAbs(upperBound - lowerBound);
                final InductionVariable counter = counted.getLimitCheckedIV();
                // assume a worst case stride of 1 if we don't know what it is
                final long stride = counter.isConstantStride() ? NumUtil.safeAbs(counter.constantStride()) : 1;
                final long strideRelativeStartToLimitDistance = distanceLowerUpper / stride;
                return strideRelativeStartToLimitDistance <= iterationLimit;
            } catch (ArithmeticException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Policy method for the GraalVM compiler loop optimizer.
     *
     * If for any reason a loop should be left totally untouched by loop optimizations this method
     * returns true.
     */
    public static boolean excludeLoopFromOptimizer(Loop loop) {
        /*
         * Strip mining should be considered a pure "structural" transformation. It rewrites IR to
         * serve the purpose of enabling other optimizations. The outer loop created in this process
         * is mere a means to achieve something else. The optimizer should not have to spend any
         * time optimizing it since it is visited infrequently and only there to enable a more
         * optimal inner loop.
         *
         * Threaded switch recognition relies on special flags in the LoopBeginNode and its
         * following IntegerSwitchNode. Duplicating the loop body can create multiple such pairs,
         * all of which must be tracked by the backend for threaded switch optimization. In general,
         * threaded switch optimization should only be applied to switches with a large number of
         * cases. Loop optimizations in these scenarios will significantly increase code size
         * regardless.
         */
        return loop.loopBegin().isAnyStripMinedOuter() || loop.loopBegin().mayEmitThreadedCode();
    }

    /**
     * Updates the ancestor-created clone factor of every descendant loop nested below
     * {@code rootLoop} by multiplying it with {@code cloneFactor}.
     *
     * A descendant loop is a loop whose loop parent chain contains {@code rootLoop}. For example,
     * if {@code rootLoop} is {@code L0}, both {@code L1} and {@code L2} are descendants below:
     *
     * <pre>
     * {@code
     * for (...) {          // L0, the ancestor being unrolled
     *     for (...) {      // L1, descendant
     *         for (...) {  // L2, descendant
     *         }
     *     }
     * }
     * }
     * </pre>
     *
     * Parent full and partial unrolls can duplicate counted descendants. If later policy checks
     * only look at one descendant loop body, they can miss that an ancestor transform already cloned
     * the same descendant body many times and may fully unroll it again.
     *
     * Running this helper immediately before the parent-loop rewrite updates a separate clone
     * factor on the existing descendant loop begins in place, so both the current descendants and
     * any clones created by the rewrite inherit the same aggregated ancestor-created copy count.
     * The helper updates all descendant loops, including loops that are not counted yet, because a
     * later counted-loop detection pass can make them relevant to full-unroll policy.
     *
     * <pre>
     * {@code
     * pending = [rootLoop]
     * while pending is not empty:
     *     current = pending.remove()
     *     for child in current.children:
     *         child.countedDescendantCloneFactor *= cloneFactor
     *         pending.add(child)
     * }
     * </pre>
     */
    public static void updateDescendantLoopCloneFactors(LoopsData loopsData, Loop rootLoop, int cloneFactor) {
        ArrayDeque<Loop> pendingLoops = new ArrayDeque<>();
        pendingLoops.addLast(rootLoop);
        while (!pendingLoops.isEmpty()) {
            Loop currentLoop = pendingLoops.removeLast();
            for (CFGLoop<HIRBlock> childCfgLoop : currentLoop.getCFGLoop().getChildren()) {
                Loop childLoop = loopsData.loop(childCfgLoop);
                LoopBeginNode childLoopBegin = childLoop.loopBegin();
                childLoopBegin.setCountedDescendantCloneFactor(multiplyUnrollFactors(childLoopBegin.getCountedDescendantCloneFactor(), cloneFactor));
                pendingLoops.addLast(childLoop);
            }
        }
    }

    private static int multiplyUnrollFactors(int currentUnrollFactor, int additionalUnrollFactor) {
        try {
            return Math.multiplyExact(currentUnrollFactor, additionalUnrollFactor);
        } catch (ArithmeticException e) {
            return Integer.MAX_VALUE;
        }
    }

    public static long tripCountSignedExact(CountedLoopInfo loop) {
        ValueNode maxTripCountNode = loop.maxTripCountNode();
        final long maxTripCountAsSigned = maxTripCountNode.asJavaConstant().asLong();
        if (maxTripCountAsSigned < 0) {
            throw new ArithmeticException("Unsigned value " + maxTripCountAsSigned + " overflows signed range");
        }
        return maxTripCountAsSigned;
    }

    public static long addExact(int bits, long a, long b) {
        if (bits == 8) {
            byte ba = NumUtil.safeToByteAE(a);
            byte bb = NumUtil.safeToByteAE(b);
            return addExact(ba, bb);
        } else if (bits == 16) {
            short sa = NumUtil.safeToShortAE(a);
            short sb = NumUtil.safeToShortAE(b);
            return addExact(sa, sb);
        } else if (bits == 32) {
            int ia = NumUtil.safeToIntAE(a);
            int ib = NumUtil.safeToIntAE(b);
            return Math.addExact(ia, ib);
        } else if (bits == 64) {
            return Math.addExact(a, b);
        } else {
            throw GraalError.shouldNotReachHere("Must be one of java's core datatypes but is " + bits);
        }
    }

    public static long subtractExact(int bits, long a, long b) {
        if (bits == 8) {
            byte ba = NumUtil.safeToByteAE(a);
            byte bb = NumUtil.safeToByteAE(b);
            return subExact(ba, bb);
        } else if (bits == 16) {
            short sa = NumUtil.safeToShortAE(a);
            short sb = NumUtil.safeToShortAE(b);
            return subExact(sa, sb);
        } else if (bits == 32) {
            int ia = NumUtil.safeToIntAE(a);
            int ib = NumUtil.safeToIntAE(b);
            return Math.subtractExact(ia, ib);
        } else if (bits == 64) {
            return Math.subtractExact(a, b);
        } else {
            throw GraalError.shouldNotReachHere("Must be one of java's core datatypes but is " + bits);
        }
    }

    public static long multiplyExact(int bits, long a, long b) {
        if (bits == 8) {
            byte ba = NumUtil.safeToByteAE(a);
            byte bb = NumUtil.safeToByteAE(b);
            return mulExact(ba, bb);
        } else if (bits == 16) {
            short sa = NumUtil.safeToShortAE(a);
            short sb = NumUtil.safeToShortAE(b);
            return mulExact(sa, sb);
        } else if (bits == 32) {
            int ia = NumUtil.safeToIntAE(a);
            int ib = NumUtil.safeToIntAE(b);
            return Math.multiplyExact(ia, ib);
        } else if (bits == 64) {
            return Math.multiplyExact(a, b);
        } else {
            throw GraalError.shouldNotReachHere("Must be one of java's core datatypes but is " + bits);
        }
    }

    private static byte addExact(byte x, byte y) {
        int iR = x + y;
        byte bR = (byte) iR;
        if (iR != bR) {
            throw new ArithmeticException("byte overflow");
        }
        return bR;
    }

    private static byte subExact(byte x, byte y) {
        int iR = x - y;
        byte bR = (byte) iR;
        if (iR != bR) {
            throw new ArithmeticException("byte overflow");
        }
        return bR;
    }

    private static byte mulExact(byte x, byte y) {
        int iR = x * y;
        byte bR = (byte) iR;
        if (iR != bR) {
            throw new ArithmeticException("byte overflow");
        }
        return bR;
    }

    private static short addExact(short x, short y) {
        int iR = x + y;
        short bR = (short) iR;
        if (iR != bR) {
            throw new ArithmeticException("short overflow");
        }
        return bR;
    }

    private static short subExact(short x, short y) {
        int iR = x - y;
        short bR = (short) iR;
        if (iR != bR) {
            throw new ArithmeticException("short overflow");
        }
        return bR;
    }

    private static short mulExact(short x, short y) {
        int iR = x * y;
        short bR = (short) iR;
        if (iR != bR) {
            throw new ArithmeticException("short overflow");
        }
        return bR;
    }

    /**
     * Determine if the def can use node {@code use} without the need for value proxies. This means
     * there is no loop exit between the schedule point of def and use that would require a
     * {@link ProxyNode}.
     */
    public static boolean canUseWithoutProxy(ControlFlowGraph cfg, Node def, Node use) {
        if (def.graph() instanceof StructuredGraph g && g.isAfterStage(StageFlag.VALUE_PROXY_REMOVAL)) {
            return true;
        }
        if (!isFixedNode(def) || !isFixedNode(use)) {
            /*
             * If def or use are not fixed nodes we cannot determine the schedule point for them.
             * Without the schedule point we cannot find their basic block in the control flow
             * graph. If we would schedule the graph we could answer the question for floating nodes
             * as well but this is too much overhead. Thus, for floating nodes we give up and assume
             * a proxy is necessary.
             */
            return false;
        }
        HIRBlock useBlock = cfg.blockFor(use);
        HIRBlock defBlock = cfg.blockFor(def);
        CFGLoop<HIRBlock> defLoop = defBlock.getLoop();
        CFGLoop<HIRBlock> useLoop = useBlock.getLoop();
        if (defLoop != null) {
            // the def is inside a loop, either a parent or a disjunct loop
            if (useLoop != null) {
                // we are only safe without proxies if we are included in the def loop,
                // i.e., the def loop is a parent loop
                return useLoop.isAncestorOrSelf(defLoop);
            } else {
                // the use is not in a loop but the def is, needs proxies, fail
                return false;
            }
        }
        return true;
    }

    private static boolean isFixedNode(Node n) {
        return n instanceof FixedNode;
    }

    public static boolean isNumericInteger(ValueNode v) {
        Stamp s = v.stamp(NodeView.DEFAULT);
        return s instanceof IntegerStamp;
    }

    /**
     * Determine if the given node has a 64-bit integer stamp.
     */
    public static boolean isLong(ValueNode v) {
        Stamp s = v.stamp(NodeView.DEFAULT);
        return s instanceof IntegerStamp && IntegerStamp.getBits(s) == 64;
    }

    /**
     * Determine if the given node has a 32-bit integer stamp.
     */
    public static boolean isInt(ValueNode v) {
        Stamp s = v.stamp(NodeView.DEFAULT);
        return s instanceof IntegerStamp && IntegerStamp.getBits(s) == 32;
    }

    /**
     * Remove loop proxies that became obsolete over time, i.e., they proxy a value that already
     * flowed out of a loop and dominates the loop now.
     *
     * @param canonicalizer must not be {@code null}, will be applied incrementally to nodes whose
     *            inputs changed
     */
    public static void removeObsoleteProxies(StructuredGraph graph, CoreProviders context, CanonicalizerPhase canonicalizer) {
        LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
        removeObsoleteProxies(graph, context, canonicalizer, loopsData);
    }

    @SuppressWarnings("try")
    public static void removeObsoleteProxies(StructuredGraph graph, CoreProviders context, CanonicalizerPhase canonicalizer, LoopsData loopsData) {
        final EconomicSetNodeEventListener inputChanges = new EconomicSetNodeEventListener(EnumSet.of(NodeEvent.INPUT_CHANGED, NodeEvent.CONTROL_FLOW_CHANGED));
        try (NodeEventScope s = graph.trackNodeEvents(inputChanges)) {
            for (Loop loop : loopsData.loops()) {
                removeObsoleteProxiesForLoop(loop);
            }
        }
        canonicalizer.applyIncremental(graph, context, inputChanges.getNodes());
    }

    /**
     * Remove obsolete proxies from one loop only. Unlike
     * {@link #removeObsoleteProxies(StructuredGraph, CoreProviders, CanonicalizerPhase)}, this does
     * not apply canonicalization.
     */
    public static void removeObsoleteProxiesForLoop(Loop loop) {
        for (LoopExitNode lex : loop.loopBegin().loopExits()) {
            for (ProxyNode proxy : lex.proxies().snapshot()) {
                if (loop.isOutsideLoop(proxy.value())) {
                    proxy.replaceAtUsagesAndDelete(proxy.getOriginalNode());
                }
            }
        }
    }

    /**
     * Advance all of the loop's induction variables by {@code iterations} strides by modifying the
     * underlying phi's init value.
     */
    public static void stepLoopIVs(StructuredGraph graph, Loop loop, ValueNode iterations) {
        for (InductionVariable iv : loop.getInductionVariables().getValues()) {
            if (!(iv instanceof BasicInductionVariable)) {
                // Only step basic IVs; this will advance derived IVs automatically.
                continue;
            }
            ValuePhiNode phi = ((BasicInductionVariable) iv).valueNode();
            ValueNode convertedIterations = IntegerConvertNode.convert(iterations, iv.strideNode().stamp(NodeView.DEFAULT), NodeView.DEFAULT);
            ValueNode steppedInit = AddNode.create(phi.valueAt(0), MulNode.create(convertedIterations, iv.strideNode(), NodeView.DEFAULT), NodeView.DEFAULT);
            phi.setValueAt(0, graph.addOrUniqueWithInputs(steppedInit));
        }
    }

    /**
     * Ensure that floating div nodes are correct and can be correctly verified after unrolling.
     *
     * A loop variant floating div node means the body of the loop guarantees that the div cannot
     * trap. This guarantee is encoded in the stamps of the div inputs. Whatever iteration space the
     * loop has, the div will not trap.
     *
     * Unrolling a loop does not change the iteration space of a loop nor the values used in the
     * loop body, it just affects the backedge jump frequency. Thus, any div floating and valid to
     * be floating before unrolling must be so after unrolling. However, unrolling copies versions
     * of the loop body which affects stamp computation. The original stamps of loop phis can be set
     * by various optimizations. After unrolling we may not have enough context information about
     * the loop to deduce no trap can happen for the values inside the loop. This is a shortcoming
     * in our stamp system where we do not connect the max trip count of a loop to the inferred
     * stamp of an arithmetic operation. Thus, we manually inject the original stamps via pi nodes
     * into the unrolled versions. This ensures the divs verify correctly.
     */
    public static void preserveCounterStampsForDivAfterUnroll(Loop loop) {
        for (Node n : loop.inside().nodes()) {
            if (n instanceof FloatingIntegerDivRemNode<?> idiv) {

                StructuredGraph graph = idiv.graph();

                ValueNode divisor = idiv.getY();
                IntegerStamp divisorStamp = (IntegerStamp) divisor.stamp(NodeView.DEFAULT);
                ValueNode dividend = idiv.getX();
                IntegerStamp dividendStamp = (IntegerStamp) dividend.stamp(NodeView.DEFAULT);

                GraalError.guarantee(!divisorStamp.contains(0), "Divisor stamp must not contain 0 for floating divs - that could trap %s", idiv);

                boolean xInsideLoop = !loop.isOutsideLoop(dividend);
                boolean yInsideLoop = !loop.isOutsideLoop(divisor);

                if (yInsideLoop) {
                    idiv.setY(piAnchorBeforeLoop(graph, divisor, divisorStamp, loop));
                }
                if (xInsideLoop) {
                    idiv.setX(piAnchorBeforeLoop(graph, dividend, dividendStamp, loop));
                }
            }
        }
        loop.invalidateFragmentsAndIVs();
        loop.loopBegin().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, loop.loopBegin().graph(), "After preserving idiv stamps");
    }

    private static PiNode piAnchorBeforeLoop(StructuredGraph graph, ValueNode v, Stamp s, Loop loop) {
        ValueNode opaqueDivisor = graph.addWithoutUnique(new OpaqueValueNode(v));
        // just anchor the pi before the loop, that dominates the other input
        return graph.addWithoutUnique(new PiNode(opaqueDivisor, s, AbstractBeginNode.prevBegin(loop.loopBegin().forwardEnd())));
    }
}
