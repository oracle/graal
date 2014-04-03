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

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.GraphPrintVisitor.ChildSupplier;

public class TruffleTreeDumpHandler implements DebugDumpHandler {

    @Override
    public void dump(Object object, final String message) {
        if (object instanceof RootCallTarget) {
            RootCallTarget callTarget = (RootCallTarget) object;
            dumpRootCallTarget(message, callTarget);
        }
    }

    private static void dumpRootCallTarget(final String message, RootCallTarget callTarget) {
        if (callTarget.getRootNode() != null) {
            final GraphPrintVisitor visitor = new GraphPrintVisitor();

            final OptimizedCallTarget oct = (OptimizedCallTarget) callTarget;

            visitor.beginGroup(callTarget.toString());
            dumpInlinedCalls(visitor, oct);
            dumpFullTree(visitor, message, oct);
            visitor.printToNetwork(false);
        }
    }

    private static void dumpFullTree(final GraphPrintVisitor visitor, final String message, final OptimizedCallTarget oct) {
        visitor.setChildSupplier(new ChildSupplier() {
            private TruffleCallPath currentPath = new TruffleCallPath(oct);

            public Object startNode(Object callNode) {
                if (callNode instanceof OptimizedCallNode) {
                    currentPath = new TruffleCallPath(currentPath, (OptimizedCallNode) callNode);
                    if (oct.getInliningResult() != null && oct.getInliningResult().isInlined(currentPath)) {
                        return ((OptimizedCallNode) callNode).getCurrentRootNode();
                    }
                }
                return null;
            }

            public void endNode(Object callNode) {
                if (callNode instanceof OptimizedCallNode) {
                    currentPath = currentPath.getParent();
                }
            }
        });

        visitor.beginGraph(message).visit(oct.getRootNode());
        visitor.setChildSupplier(null);
    }

    private static void dumpInlinedCalls(final GraphPrintVisitor visitor, final OptimizedCallTarget oct) {
        final Set<OptimizedCallTarget> visitedRoots = new HashSet<>();
        oct.getRootNode().accept(new OptimizedCallUtils.InlinedCallVisitor(oct.getInliningResult(), new TruffleCallPath(oct)) {
            @Override
            public boolean visit(TruffleCallPath path, Node node) {
                if (node instanceof OptimizedCallNode) {
                    OptimizedCallTarget target = ((OptimizedCallNode) node).getCurrentCallTarget();
                    if (!visitedRoots.contains(target)) {
                        visitor.beginGraph("inlined " + target.toString()).visit(target.getRootNode());
                        visitedRoots.add(target);
                    }
                }
                return true;
            }
        });
    }

    public void close() {
        // nothing to do
    }
}
