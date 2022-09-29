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
package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.util.GraphUtil;

/**
 * Try to replace box operations with dominating box operations.
 *
 * Consider the following code snippet:
 *
 * <pre>
 * boxedVal1 = box(primitiveVal)
 * ...
 * boxedVal2 = box(primitiveVal)
 * </pre>
 *
 * which can be rewritten to (if the assignment to boxedVal1 dominates the assignment to boxedVal2)
 *
 * <pre>
 * boxedVal1 = box(primitiveVal)
 * ...
 * boxedVal2 = boxedVal1;
 * </pre>
 */
public class BoxNodeOptimizationPhase extends PostRunCanonicalizationPhase<CoreProviders> {

    public BoxNodeOptimizationPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        ControlFlowGraph cfg = null;
        Graph.Mark before = graph.getMark();
        boxLoop: for (BoxNode box : graph.getNodes(BoxNode.TYPE)) {
            if (box.isAlive() && !box.hasIdentity()) {
                final ValueNode primitiveVal = box.getValue();
                assert primitiveVal != null : "Box " + box + " has no value";
                // try to optimize with dominating box of the same value
                boxedValUsageLoop: for (Node usage : primitiveVal.usages().snapshot()) {
                    if (usage == box) {
                        continue;
                    }
                    if (usage instanceof BoxNode) {
                        final BoxNode boxUsageOnBoxedVal = (BoxNode) usage;
                        if (boxUsageOnBoxedVal.getBoxingKind() == box.getBoxingKind()) {
                            if (cfg == null) {
                                cfg = ControlFlowGraph.compute(graph, true, true, true, false);
                            }
                            if (graph.isNew(before, boxUsageOnBoxedVal) || graph.isNew(before, box)) {
                                continue boxedValUsageLoop;
                            }
                            Block boxUsageOnBoxedValBlock = cfg.blockFor(boxUsageOnBoxedVal);
                            Block originalBoxBlock = cfg.blockFor(box);
                            if (boxUsageOnBoxedValBlock.getLoop() != null) {
                                if (originalBoxBlock.getLoop() != boxUsageOnBoxedValBlock.getLoop()) {
                                    // avoid proxy creation for now
                                    continue boxedValUsageLoop;
                                }
                            }
                            if (AbstractControlFlowGraph.dominates(boxUsageOnBoxedValBlock, originalBoxBlock)) {
                                if (boxUsageOnBoxedValBlock == originalBoxBlock) {
                                    // check dominance within one block
                                    for (FixedNode f : boxUsageOnBoxedValBlock.getNodes()) {
                                        if (f == boxUsageOnBoxedVal) {
                                            // we found the usage first, it dominates "box"
                                            break;
                                        } else if (f == box) {
                                            // they are within the same block but the
                                            // usage block does not dominate the box
                                            // block, that scenario will still be
                                            // optimizable but for the usage block node
                                            // later in the outer box loop
                                            continue boxedValUsageLoop;
                                        }
                                    }
                                }
                                box.replaceAtUsages(boxUsageOnBoxedVal);
                                graph.getOptimizationLog().report(getClass(), "BoxUsageReplacement", box);
                                GraphUtil.removeFixedWithUnusedInputs(box);
                                continue boxLoop;
                            }
                        }
                    }
                }
            }
        }
    }

}
