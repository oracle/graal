/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.graph.spi.NodeWithIdentity;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.truffle.phases.TruffleEarlyEscapeAnalysisPhase;

/**
 * Used for anchoring {@link VirtualObjectNode}s produced by {@link TruffleEarlyEscapeAnalysisPhase}
 * to before a loop that is exploded during partial evaluation. This prevents PE from repeatedly
 * decoding and adding duplicate virtual objects to the graph. For example in:
 *
 * <pre>
 * ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
 * EarlyEscapeAnalysis
 * public int interpreterMethod(...) {
 *
 *  Object obj = new ...    // virtualized by EarlyEA
 *
 *  while(...) {            // exploded during PE
 *    switch(...)
 *       case c1:
 *         use(obj.x);
 *         break;
 *       case c2:
 *         use(obj.y);
 *         break;
 *    ...
 * }
 * </pre>
 *
 * This node is automatically removed by the next round of canonicalization after PE.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public class TruffleEarlyEscapeAnchorNode extends FixedWithNextNode implements Canonicalizable, NodeWithIdentity {

    public static final NodeClass<TruffleEarlyEscapeAnchorNode> TYPE = NodeClass.create(TruffleEarlyEscapeAnchorNode.class);

    @Input NodeInputList<VirtualObjectNode> virtualObjects;

    @SuppressWarnings("this-escape")
    public TruffleEarlyEscapeAnchorNode(NodeIterable<VirtualObjectNode> virtualObjects) {
        super(TYPE, StampFactory.forVoid());
        this.virtualObjects = new NodeInputList<>(this, virtualObjects.snapshot());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (virtualObjects.isEmpty() || this.graph().isAfterStage(GraphState.StageFlag.PARTIAL_EVALUATION)) {
            return null;
        }
        return this;
    }
}
