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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.LoadExceptionObjectNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import com.oracle.svm.core.graal.nodes.ExceptionStateNode;
import com.oracle.svm.core.graal.nodes.ReadExceptionObjectNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.thread.VMThreads;

public class LLVMLoadExceptionObjectLowering implements NodeLoweringProvider<LoadExceptionObjectNode> {

    @Override
    public void lower(LoadExceptionObjectNode node, LoweringTool tool) {
        FrameState exceptionState = node.stateAfter();
        assert exceptionState != null;

        StructuredGraph graph = node.graph();
        FixedWithNextNode readRegNode = graph.add(new ReadExceptionObjectNode(StampFactory.objectNonNull()));
        graph.replaceFixedWithFixed(node, readRegNode);

        /*
         * When libunwind has found an exception handler, it jumps directly to it from native code.
         * We therefore need the CFunctionEpilogueNode to restore the Java state before we handle
         * the exception.
         */
        CFunctionEpilogueNode cFunctionEpilogueNode = new CFunctionEpilogueNode(VMThreads.StatusSupport.STATUS_IN_NATIVE);
        graph.add(cFunctionEpilogueNode);
        graph.addAfterFixed(readRegNode, cFunctionEpilogueNode);
        cFunctionEpilogueNode.setStateAfter(exceptionState);
        cFunctionEpilogueNode.lower(tool);

        graph.addAfterFixed(readRegNode, graph.add(new ExceptionStateNode(exceptionState)));
    }
}
