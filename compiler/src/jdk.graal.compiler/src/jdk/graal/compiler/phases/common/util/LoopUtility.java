/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;

import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph.NodeEvent;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.FloatingIntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.OpaqueValueNode;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

public class LoopUtility {

    public static long addExact(int bits, long a, long b) {
        if (bits == 32) {
            int ia = (int) a;
            int ib = (int) b;
            assert ia == a && ib == b : Assertions.errorMessage("Conversions must be lossless", bits, a, b, ia, ib);
            return Math.addExact(ia, ib);
        } else if (bits == 64) {
            return Math.addExact(a, b);
        } else {
            throw GraalError.shouldNotReachHere("Must be one of java's core datatypes int/long but is " + bits);
        }
    }

    public static long subtractExact(int bits, long a, long b) {
        if (bits == 32) {
            int ia = (int) a;
            int ib = (int) b;
            assert ia == a && ib == b : Assertions.errorMessage("Conversions must be lossless", bits, a, b, ia, ib);
            return Math.subtractExact(ia, ib);
        } else if (bits == 64) {
            return Math.subtractExact(a, b);
        } else {
            throw GraalError.shouldNotReachHere("Must be one of java's core datatypes int/long but is " + bits);
        }
    }

    public static long multiplyExact(int bits, long a, long b) {
        if (bits == 32) {
            int ia = (int) a;
            int ib = (int) b;
            assert ia == a && ib == b : Assertions.errorMessage("Conversions must be lossless", bits, a, b, ia, ib);
            return Math.multiplyExact(ia, ib);
        } else if (bits == 64) {
            return Math.multiplyExact(a, b);
        } else {
            throw GraalError.shouldNotReachHere("Must be one of java's core datatypes int/long but is " + bits);
        }
    }

    public static boolean canTakeAbs(long l, int bits) {
        try {
            abs(l, bits);
            return true;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    /**
     * Compute {@link Math#abs(long)} for the given arguments and the given bit size. Throw a
     * {@link ArithmeticException} if the abs operation would overflow.
     */
    public static long abs(long l, int bits) throws ArithmeticException {
        if (bits == 32) {
            if (l == Integer.MIN_VALUE) {
                throw new ArithmeticException("Abs on Integer.MIN_VALUE would cause an overflow because abs(Integer.MIN_VALUE) = Integer.MAX_VALUE + 1 which does not fit in int (32 bits)");
            } else {
                final int i = (int) l;
                return Math.abs(i);
            }
        } else if (bits == 64) {
            if (l == Long.MIN_VALUE) {
                throw new ArithmeticException("Abs on Long.MIN_VALUE would cause an overflow because abs(Long.MIN_VALUE) = Long.MAX_VALUE + 1 which does not fit in long (64 bits)");
            } else {
                return Math.abs(l);
            }
        } else {
            throw GraalError.shouldNotReachHere("Must be one of java's core datatypes int/long but is " + bits);
        }
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
    @SuppressWarnings("try")
    public static void removeObsoleteProxies(StructuredGraph graph, CoreProviders context, CanonicalizerPhase canonicalizer) {
        LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
        final EconomicSetNodeEventListener inputChanges = new EconomicSetNodeEventListener(EnumSet.of(NodeEvent.INPUT_CHANGED));
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
