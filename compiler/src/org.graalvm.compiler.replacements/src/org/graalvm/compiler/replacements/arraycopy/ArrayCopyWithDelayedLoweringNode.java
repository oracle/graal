/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.arraycopy;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.UnreachableBeginNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.loop.LoopExpandableNode;
import org.graalvm.compiler.nodes.spi.Simplifiable;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.replacements.nodes.BasicArrayCopyNode;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;

/**
 * A version of {@link BasicArrayCopyNode} that delays lowering.
 *
 * The {@link #snippet} which will be used for lowering is already known up front, but applying it
 * is delayed to avoid unfavorable interaction with other phases (floating guards, frame state
 * assignment, etc.).
 *
 * @see ArrayCopyNode
 * @see ArrayCopySnippets
 */
@NodeInfo(allowedUsageTypes = InputType.Memory)
public final class ArrayCopyWithDelayedLoweringNode extends BasicArrayCopyNode implements Simplifiable, LoopExpandableNode {

    public static final NodeClass<ArrayCopyWithDelayedLoweringNode> TYPE = NodeClass.create(ArrayCopyWithDelayedLoweringNode.class);

    private final ArrayCopySnippets.WorkSnippetID snippet;
    private final GraphState.GuardsStage delayUntil;
    private final boolean canThrow;

    public ArrayCopyWithDelayedLoweringNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, ArrayCopySnippets.WorkSnippetID snippet,
                    GraphState.GuardsStage delayUntil, JavaKind elementKind, boolean canThrow) {
        super(TYPE, src, srcPos, dest, destPos, length, elementKind, BytecodeFrame.INVALID_FRAMESTATE_BCI);
        assert StampTool.isPointerNonNull(src) && StampTool.isPointerNonNull(dest) : "must have been null checked";
        this.snippet = snippet;
        this.delayUntil = delayUntil;
        this.canThrow = canThrow;
    }

    public static void arraycopy(Object nonNullSrc, int srcPos, Object nonNullDest, int destPos, int length, @ConstantNodeParameter ArrayCopySnippets.WorkSnippetID snippet,
                    @ConstantNodeParameter GraphState.GuardsStage delayUntil, @ConstantNodeParameter JavaKind elementKind) {
        arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, snippet, delayUntil, elementKind, true);
    }

    public static void arraycopyNonThrowing(Object nonNullSrc, int srcPos, Object nonNullDest, int destPos, int length, @ConstantNodeParameter ArrayCopySnippets.WorkSnippetID snippet,
                    @ConstantNodeParameter GraphState.GuardsStage delayUntil, @ConstantNodeParameter JavaKind elementKind) {
        arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, snippet, delayUntil, elementKind, false);
    }

    @NodeIntrinsic
    private static native void arraycopy(Object nonNullSrc, int srcPos, Object nonNullDest, int destPos, int length, @ConstantNodeParameter ArrayCopySnippets.WorkSnippetID snippet,
                    @ConstantNodeParameter GraphState.GuardsStage delayUntil, @ConstantNodeParameter JavaKind elementKind, @ConstantNodeParameter boolean canThrow);

    public ArrayCopySnippets.WorkSnippetID getSnippet() {
        return snippet;
    }

    public boolean reachedRequiredLoweringStage() {
        return graph().getGuardsStage().reachedGuardsStage(delayUntil);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (!canThrow && !(exceptionEdge() instanceof UnreachableBeginNode)) {
            replaceWithNonThrowing();
        }
    }

    @Override
    public boolean mayExpandToLoop() {
        switch (snippet) {
            case checkcastArraycopySnippet:
            case genericArraycopySnippet:
                // will be a call
                return false;
            case exactArraycopyWithExpandedLoopSnippet:
                // will become a loop
                return true;
            default:
                throw GraalError.shouldNotReachHere("Unkown snippet type " + snippet);
        }
    }
}
