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

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;

public class DefaultTruffleSplittingStrategy implements TruffleSplittingStrategy {

    private final OptimizedDirectCallNode call;

    public DefaultTruffleSplittingStrategy(OptimizedDirectCallNode call) {
        this.call = call;
    }

    public void beforeCall(Object[] arguments) {
        if (call.getCallCount() == 2 && !call.isInlined()) {
            if (shouldSplit()) {
                forceSplitting();
            }
        }
    }

    public void forceSplitting() {
        if (call.isSplit()) {
            return;
        }
        call.installSplitCallTarget(call.getCallTarget().split());
    }

    public void afterCall(Object returnValue) {
    }

    private boolean shouldSplit() {
        if (call.getSplitCallTarget() != null) {
            return false;
        }
        if (!TruffleCompilerOptions.TruffleSplitting.getValue()) {
            return false;
        }
        if (!call.isSplittable()) {
            return false;
        }
        OptimizedCallTarget splitTarget = call.getCallTarget();
        int nodeCount = OptimizedCallUtils.countNonTrivialNodes(splitTarget, false);
        if (nodeCount > TruffleCompilerOptions.TruffleSplittingMaxCalleeSize.getValue()) {
            return false;
        }

        // disable recursive splitting for now
        OptimizedCallTarget root = (OptimizedCallTarget) call.getRootNode().getCallTarget();
        if (root == splitTarget || root.getSplitSource() == splitTarget) {
            // recursive call found
            return false;
        }

        // max one child call and callCount > 2 and kind of small number of nodes
        if (isMaxSingleCall(call)) {
            return true;
        }
        return countPolymorphic(call) >= 1;
    }

    private static boolean isMaxSingleCall(OptimizedDirectCallNode call) {
        return NodeUtil.countNodes(call.getCurrentCallTarget().getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return node instanceof DirectCallNode;
            }
        }) <= 1;
    }

    private static int countPolymorphic(OptimizedDirectCallNode call) {
        return NodeUtil.countNodes(call.getCurrentCallTarget().getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                NodeCost cost = node.getCost();
                boolean polymorphic = cost == NodeCost.POLYMORPHIC || cost == NodeCost.MEGAMORPHIC;
                return polymorphic;
            }
        });
    }

}
