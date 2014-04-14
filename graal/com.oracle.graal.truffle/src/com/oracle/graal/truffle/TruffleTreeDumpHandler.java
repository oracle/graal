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
            dumpFullTree(visitor, message, oct);
            visitor.printToNetwork(false);
        }
    }

    private static void dumpFullTree(final GraphPrintVisitor visitor, final String message, final OptimizedCallTarget oct) {
        visitor.setChildSupplier(new ChildSupplier() {

            public Object startNode(Object callNode) {
                if (callNode instanceof OptimizedCallNode) {
                    if (((OptimizedCallNode) callNode).isInlined()) {
                        return ((OptimizedCallNode) callNode).getCurrentRootNode();
                    }
                }
                return null;
            }

            public void endNode(Object callNode) {
            }
        });

        visitor.beginGraph(message).visit(oct.getRootNode());
        visitor.setChildSupplier(null);
    }

    public void close() {
        // nothing to do
    }
}
