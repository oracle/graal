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
package jdk.graal.compiler.nodes;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.StandardOp.DeadEndOp;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.java.AccessArrayNode;
import jdk.graal.compiler.nodes.java.AccessFieldNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;

/**
 * Declares an impossible execution path, rather than a runtime error at a particular program
 * point. Like deoptimization propagation, simplification may discard preceding side effects on
 * that path, but must preserve operations that can exit exceptionally. Unlike deoptimization, no
 * execution state needs to be reconstructed. A surviving marker emits a hardware halt, without
 * calling runtime error machinery.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public final class UnreachablePathNode extends ControlSinkNode implements Simplifiable, LIRLowerable {
    public static final NodeClass<UnreachablePathNode> TYPE = NodeClass.create(UnreachablePathNode.class);

    /** Creates a marker that terminates an impossible execution path. */
    public UnreachablePathNode() {
        super(TYPE, StampFactory.forVoid());
    }

    /** Propagates the marker backward once all node usages are available for simplification. */
    @Override
    public void simplify(SimplifierTool tool) {
        if (tool.allUsagesAvailable()) {
            propagate(this, tool);
        }
    }

    /**
     * Truncates the path leading to {@code from}, stopping at exception boundaries and loop exits.
     * Propagates through merge predecessors and through splits whose successors all reject execution.
     */
    private static void propagate(FixedNode from, SimplifierTool tool) {
        AbstractBeginNode begin = AbstractBeginNode.prevBegin(from);
        for (Node predecessor = from; predecessor != begin;) {
            predecessor = predecessor.predecessor();
            if (predecessor instanceof FixedWithNextNode fixed && isExceptionBoundary(fixed)) {
                moveAfter(fixed, tool);
                return;
            }
        }
        if (begin instanceof MergeNode merge) {
            FixedNode next = merge.next();
            while (merge.isAlive()) {
                propagate(merge.forwardEnds().first(), tool);
            }
            if (next.isAlive()) {
                propagate(next, tool);
            }
            return;
        }

        moveAfter(begin, tool);
        if (!(begin instanceof LoopExitNode) && begin.predecessor() instanceof ControlSplitNode split) {
            for (Node successor : split.successors()) {
                if (!(successor instanceof AbstractBeginNode successorBegin) || successorBegin instanceof LoopExitNode ||
                                !(successorBegin.next() instanceof UnreachablePathNode)) {
                    return;
                }
            }
            // The split is unnecessary only when every successor rejects execution.
            propagate(split, tool);
        }
    }

    /**
     * Preserves implicit exceptional exits as well as checks introduced during lowering. Treat
     * deoptimizing nodes conservatively: deoptimization may resume execution that throws instead of
     * reaching this marker.
     */
    private static boolean isExceptionBoundary(FixedWithNextNode node) {
        return node instanceof DeoptimizingNode || node instanceof AccessArrayNode || node instanceof ArrayLengthNode ||
                        (node instanceof AccessFieldNode field && !field.isStatic() && !StampTool.isPointerNonNull(field.object()));
    }

    /**
     * Places a marker immediately after {@code position} and removes the displaced control flow.
     * The caller must ensure that the removed region contains no exceptional exit to preserve.
     */
    private static void moveAfter(FixedWithNextNode position, SimplifierTool tool) {
        FixedNode next = position.next();
        if (!(next instanceof UnreachablePathNode)) {
            UnreachablePathNode replacement = position.graph().add(new UnreachablePathNode());
            replacement.setNodeSourcePosition(next.getNodeSourcePosition());
            position.setNext(replacement);
            GraphUtil.killCFG(next);
            tool.addToWorkList(replacement);
        }
    }

    /** Emits a hardware halt and marks the LIR path as having no continuation. */
    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitHalt();
        gen.getLIRGeneratorTool().append(new DeadEndOp());
    }

}
