/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 */
package com.oracle.graal.truffle;

import static com.oracle.graal.compiler.common.GraalOptions.PrintIdealGraph;

import com.oracle.graal.debug.DebugDumpHandler;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.GraphPrintVisitor;
import com.oracle.truffle.api.nodes.GraphPrintVisitor.GraphPrintAdapter;
import com.oracle.truffle.api.nodes.GraphPrintVisitor.GraphPrintHandler;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;

public class TruffleTreeDumpHandler implements DebugDumpHandler {

    @Override
    public void dump(Object object, final String message) {
        if (object instanceof RootCallTarget && PrintIdealGraph.getValue()) {
            dumpRootCallTarget(message, (RootCallTarget) object);
        }
    }

    private static void dumpRootCallTarget(final String message, RootCallTarget callTarget) {
        if (callTarget.getRootNode() != null) {
            final GraphPrintVisitor printer = new GraphPrintVisitor();

            printer.beginGroup(callTarget.toString());
            printer.beginGraph(message).visit(callTarget.getRootNode()).endGraph();
            if (callTarget instanceof OptimizedCallTarget) {
                TruffleInlining inlining = new TruffleInlining((OptimizedCallTarget) callTarget, new DefaultInliningPolicy());
                if (inlining.countInlinedCalls() > 0) {
                    dumpInlinedTrees(printer, (OptimizedCallTarget) callTarget, inlining);
                    dumpInlinedCallGraph(printer, (OptimizedCallTarget) callTarget, inlining);
                }
            }
            printer.endGroup();

            printer.printToNetwork(false);
        }
    }

    private static void dumpInlinedTrees(final GraphPrintVisitor printer, final OptimizedCallTarget callTarget, TruffleInlining inlining) {
        for (DirectCallNode callNode : NodeUtil.findAllNodeInstances(callTarget.getRootNode(), DirectCallNode.class)) {
            CallTarget inlinedCallTarget = callNode.getCurrentCallTarget();
            if (inlinedCallTarget instanceof OptimizedCallTarget && callNode instanceof OptimizedDirectCallNode) {
                TruffleInliningDecision decision = inlining.findByCall((OptimizedDirectCallNode) callNode);
                if (decision != null && decision.isInline()) {
                    printer.beginGroup(inlinedCallTarget.toString());
                    printer.beginGraph(inlinedCallTarget.toString()).visit(((RootCallTarget) inlinedCallTarget).getRootNode()).endGraph();
                    dumpInlinedTrees(printer, (OptimizedCallTarget) inlinedCallTarget, decision);
                    printer.endGroup();
                }
            }
        }
    }

    private static void dumpInlinedCallGraph(final GraphPrintVisitor printer, final OptimizedCallTarget rootCallTarget, TruffleInlining inlining) {
        class InliningGraphPrintHandler implements GraphPrintHandler {
            private final TruffleInlining inlining;

            InliningGraphPrintHandler(TruffleInlining inlining) {
                this.inlining = inlining;
            }

            public void visit(Object node, GraphPrintAdapter g) {
                if (g.visited(node)) {
                    return;
                }
                g.createElementForNode(node);
                g.setNodeProperty(node, "name", node.toString());
                for (DirectCallNode callNode : NodeUtil.findAllNodeInstances(((RootCallTarget) node).getRootNode(), DirectCallNode.class)) {
                    CallTarget inlinedCallTarget = callNode.getCurrentCallTarget();
                    if (inlinedCallTarget instanceof OptimizedCallTarget && callNode instanceof OptimizedDirectCallNode) {
                        TruffleInliningDecision decision = inlining.findByCall((OptimizedDirectCallNode) callNode);
                        if (decision != null && decision.isInline()) {
                            g.visit(inlinedCallTarget, new InliningGraphPrintHandler(decision));
                            SourceSection sourceSection = callNode.getEncapsulatingSourceSection();
                            g.connectNodes(node, inlinedCallTarget, sourceSection != null ? sourceSection.toString() : null);
                        }
                    }
                }
            }
        }

        printer.beginGraph("inlined call graph");
        printer.visit(rootCallTarget, new InliningGraphPrintHandler(inlining));
        printer.endGraph();
    }

    public void close() {
        // nothing to do
    }
}
