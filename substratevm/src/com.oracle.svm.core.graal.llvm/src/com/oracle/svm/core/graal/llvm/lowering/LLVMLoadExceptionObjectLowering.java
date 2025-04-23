/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.lowering;

import com.oracle.svm.core.graal.nodes.ReadExceptionObjectNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.thread.VMThreads;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.LoadExceptionObjectNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;

public class LLVMLoadExceptionObjectLowering implements NodeLoweringProvider<LoadExceptionObjectNode> {

    @Override
    public void lower(LoadExceptionObjectNode node, LoweringTool tool) {
        FrameState exceptionState = node.stateAfter();
        GraalError.guarantee(exceptionState != null, "StateAfter must not be null for %s", node);

        StructuredGraph graph = node.graph();
        GraalError.guarantee(graph.getGuardsStage().areFrameStatesAtDeopts(), "Should be after FSA %s", node);
        FixedWithNextNode readRegNode = graph.add(new ReadExceptionObjectNode(node.stamp(NodeView.DEFAULT)));
        graph.replaceFixedWithFixed(node, readRegNode);

        /*
         * When libunwind has found an exception handler, it jumps directly to it from native code.
         * We therefore need the CFunctionEpilogueNode to restore the Java state before we handle
         * the exception.
         */
        CFunctionEpilogueNode cFunctionEpilogueNode = new CFunctionEpilogueNode(VMThreads.StatusSupport.STATUS_IN_NATIVE);
        graph.add(cFunctionEpilogueNode);
        graph.addAfterFixed(readRegNode, cFunctionEpilogueNode);
        GraalError.guarantee(exceptionState.rethrowException() && exceptionState.stackSize() == 1 && exceptionState.stackAt(0) == readRegNode,
                        "Unexpected state for node %s: %s", cFunctionEpilogueNode, exceptionState);
        cFunctionEpilogueNode.setStateBefore(exceptionState);
        cFunctionEpilogueNode.setStateAfter(exceptionState);
        cFunctionEpilogueNode.lower(tool);
    }
}
