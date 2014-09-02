/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.test.builtins;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * Generates a given number of dummy nodes and replaces the root of the current method with them.
 * This builtin is guaranteed to be executed only once.
 */
@NodeInfo(shortName = "generateDummyNodes")
public abstract class SLGenerateDummyNodesBuiltin extends SLGraalRuntimeBuiltin {

    @Specialization
    public Object generateNodes(long count) {
        CompilerAsserts.neverPartOfCompilation("generateNodes should never get optimized.");
        FrameInstance callerFrame = Truffle.getRuntime().getCallerFrame();
        SLRootNode root = (SLRootNode) callerFrame.getCallNode().getRootNode();
        root.getBodyNode().replace(createBinaryTree((int) (count - 1)));
        return SLNull.SINGLETON;
    }

    private SLDummyNode createBinaryTree(int nodeCount) {
        if (nodeCount > 3) {
            int leftSize = nodeCount / 2;
            SLDummyNode left = createBinaryTree(leftSize);
            SLDummyNode right = createBinaryTree(nodeCount - leftSize - 1);
            return new SLDummyNode(left, right);
        } else {
            if (nodeCount <= 0) {
                return null;
            }
            SLDummyNode left = null;
            SLDummyNode right = null;
            if (nodeCount > 1) {
                left = new SLDummyNode(null, null);
                if (nodeCount > 2) {
                    right = new SLDummyNode(null, null);
                }
            }
            return new SLDummyNode(left, right);
        }
    }

    @NodeInfo(cost = NodeCost.MONOMORPHIC)
    private static class SLDummyNode extends SLExpressionNode {

        @Child private SLDummyNode left;
        @Child private SLDummyNode right;

        public SLDummyNode(SLDummyNode left, SLDummyNode right) {
            super(null);
            this.left = left;
            this.right = right;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            if (left != null) {
                left.executeGeneric(frame);
            }
            if (right != null) {
                right.executeGeneric(frame);
            }
            return null;
        }

    }

}
