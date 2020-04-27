/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import com.oracle.svm.hosted.meta.HostedMethod;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;


public class RemoveRedundantClassInitPhase extends Phase {

    private final HostedMethod classInitMethod;
    private final StructuredGraph classInitMethodGraph;

    public RemoveRedundantClassInitPhase(MetaAccessProvider metaAccessProvider) {
        this.classInitMethod = (HostedMethod) metaAccessProvider.lookupJavaMethod(SubstrateClassInitializationPlugin.ENSURE_INITIALIZED_METHOD);
        this.classInitMethodGraph = classInitMethod.compilationInfo.getGraph();
    }

    @Override
    protected void run(StructuredGraph graph) {
        RemoveRedundantClassInitClosure removeRedundantClassInitClosure = new RemoveRedundantClassInitClosure(classInitMethod);
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, false);
        removeRedundantClassInitClosure.removeRedundantNodes(removeRedundantClassInitClosure, cfg.getStartBlock());

        // RemoveRedundantClassInitClosure detects (in run method) which of class initialization nodes are redundant.
        // After closure processing, they are removed (with propagation) in the code below.
        MapCursor<InvokeWithExceptionNode, AbstractBeginNode> cursor = removeRedundantClassInitClosure.getNodesToBeRemoved().getEntries();
        while (cursor.advance()) {
            InvokeWithExceptionNode invokeWithExceptionNode = cursor.getKey();
            AbstractBeginNode survivingSuccessor = invokeWithExceptionNode.killKillingBegin();

            invokeWithExceptionNode.graph().removeSplitPropagate(invokeWithExceptionNode, survivingSuccessor);
        }

        // During the inline phase, class initialization nodes aren't inlined. So in this phase, we need to do it manually.
        // Of course, inlining is done, only on nodes that remain after closure processing and removing redundant nodes.
        cursor = removeRedundantClassInitClosure.getNodesToRemain().getEntries();
        while (cursor.advance()) {
            InvokeWithExceptionNode invokeWithExceptionNode = cursor.getKey();
            InliningUtil.inline(invokeWithExceptionNode, classInitMethodGraph, true, classInitMethod);
        }
    }
}
