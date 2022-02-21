/*
 * Copyright (c) 2022, Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.extended.ArrayFillNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Array filling optimization. Mirror of C2's OptimizeFill phase in loop optimizations.
 * <p>
 * Process all the loops in the loop tree and replace any fill
 * patterns with an intrinsic version.
 */
public class IntrinsifyArrayFillPhase extends BasePhase<CoreProviders> {
    private static final String FAILURE_FORMAT = "intrinsify fill failure: %s";
    private StoreIndexedNode store;

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        DebugContext debug = graph.getDebug();
        if (graph.hasLoops()) {
            final LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            loopsData.detectedCountedLoops();
            List<LoopEx> countedLoops = loopsData.countedLoops();
            for (LoopEx loop : countedLoops) {
                if (matchLoopFill(debug, loop)) {
                    List<LoopExitNode> exits = loop.loopBegin().loopExits().snapshot();
                    if (exits.size() == 1) {
                        replaceLoopWithFillStub(graph, loop, exits.get(0));
                        debug.log("Replace array fill counted loop with fill stub call");
                    } else {
                        debug.log("Multiple exits of counted loop, can not apply array fill optimization");
                    }
                }
            }
        }
    }

    private void replaceLoopWithFillStub(StructuredGraph graph, LoopEx loop, LoopExitNode exit) {
        final InductionVariable iv = loop.counted().getCounter();
        final ValueNode init = iv.initNode();
        final ValueNode limit = loop.counted().getLimit();
        ValueNode count = graph.unique(new SubNode(limit, init));
        // Rewire loop entry and continuation code to newly created stub call node
        ArrayFillNode fill = graph.add(new ArrayFillNode(store.array(), init, store.value(), count, store.elementKind()));
        fill.setStateAfter(graph.start().stateAfter());
        FixedWithNextNode entryPoint = (FixedWithNextNode) loop.entryPoint().predecessor();
        entryPoint.clearSuccessors();
        entryPoint.setNext(fill);
        FixedNode exitNext = exit.next();
        exit.clearSuccessors();
        fill.setNext(exitNext);
        // Mark all node within loop body as dead so that they can be removed by later DCE phase
        GraphUtil.killCFG(loop.entryPoint());
        for (Node node : loop.whole().nodes()) {
            if (node instanceof FixedNode) {
                GraphUtil.killCFG((FixedNode) node);
            }
        }
        store = null;
    }

    private boolean matchLoopFill(DebugContext debug, LoopEx loop) {
        // Must have constant unit stride
        CountedLoopInfo info = loop.counted();
        if (!info.getCounter().isConstantStride() || info.getCounter().constantStride() != 1) {
            debug.log(FAILURE_FORMAT, "stride must be const 1");
            return false;
        }
        // Check that the body only contains a store of a loop invariant
        // value that is indexed by the loop phi.
        NodeBitMap body = loop.whole().nodes();
        for (Node node : body) {
            // Ignore dead data nodes
            if (!(node instanceof FixedNode) && node.usages().isEmpty()) {
                continue;
            }
            if (node instanceof StoreIndexedNode) {
                StoreIndexedNode st = (StoreIndexedNode) node;
                if (store != null) {
                    debug.log(FAILURE_FORMAT, "multiple stores");
                    return false;
                }
                if (!st.elementKind().isPrimitive()) {
                    debug.log(FAILURE_FORMAT, "oop fills not handled");
                    return false;
                }
                // Make sure the address expression can be handled
                ValueNode valueIn = st.value();
                ValueNode index = st.index();
                if (!loop.isOutsideLoop(valueIn)) {
                    debug.log(FAILURE_FORMAT, "variant store value");
                    return false;
                }
                if (index != info.getCounter().valueNode()) {
                    debug.log("store index isn't proper phi");
                    return false;
                }
                store = st;
            } else if (node instanceof ControlSplitNode && node != info.getLimitTest()) {
                debug.log(FAILURE_FORMAT, "extra control flow");
                return false;
            }
        }
        // No store in loop
        if (store == null) {
            return false;
        }
        Set<Node> accepted = new HashSet<>();
        accepted.add(store);
        accepted.add(loop.loopBegin());
        accepted.addAll(loop.loopBegin().loopEnds().snapshot());
        accepted.addAll(loop.loopBegin().loopExits().snapshot());
        accepted.add(info.getLimitTest().condition());
        accepted.add(info.getLimitTest());
        accepted.add(info.getLimitTest().successor(true));
        accepted.add(info.getLimitTest().successor(false));
        accepted.add(info.getCounter().valueNode());
        AddNode incr = null;
        List<Node> strideUse = info.getCounter().strideNode().usages().snapshot();
        for (Node use : strideUse) {
            if (!loop.isOutsideLoop(use) && use.getUsageCount() == 1) {
                Node phi = use.usages().first();
                if (phi == info.getCounter().valueNode()) {
                    // The only use of stride use is connected to counter's phi, so the
                    // use itself must be an increment operation
                    if (use instanceof AddNode) { // valid counted loop for array fill has up loop direction so it must be a AddNode
                        incr = (AddNode) use;
                    }
                }
            }
        }
        accepted.add(incr);
        debug.log("Accept Node Set: %s", accepted.toString());
        body = loop.whole().nodes(); // loop body is already consumed, acquire a brandy new one
        for (Node node : body) {
            if (accepted.contains(node)) {
                if (node != store && !(node instanceof LoopExitNode) && node != incr) {
                    for (Node use : node.usages()) {
                        if (loop.isOutsideLoop(use)) {
                            debug.log("node is used outside loop");
                            return false;
                        }
                    }
                }
                continue;
            }
            if (node instanceof FrameState) {
                continue;
            }
            debug.log("unhandled node");
            return false;
        }
        return true;
    }
}