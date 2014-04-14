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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.frame.FrameInstance.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;

/**
 * A call node with a constant {@link CallTarget} that can be optimized by Graal.
 */
public final class OptimizedCallNode extends DefaultCallNode {

    private int callCount;
    private boolean trySplit = true;

    @CompilationFinal private boolean inlined;
    @CompilationFinal private OptimizedCallTarget splitCallTarget;

    private OptimizedCallNode(OptimizedCallTarget target) {
        super(target);
    }

    @Override
    public boolean isSplittable() {
        return getCallTarget().getRootNode().isSplittable();
    }

    @Override
    public OptimizedCallTarget getCallTarget() {
        return (OptimizedCallTarget) super.getCallTarget();
    }

    public int getCallCount() {
        return callCount;
    }

    @Override
    public OptimizedCallTarget getCurrentCallTarget() {
        return (OptimizedCallTarget) super.getCurrentCallTarget();
    }

    @Override
    public OptimizedCallTarget getSplitCallTarget() {
        return splitCallTarget;
    }

    @Override
    public Object call(VirtualFrame frame, Object[] arguments) {
        if (CompilerDirectives.inInterpreter()) {
            onInterpreterCall();
        }
        return callProxy(this, getCurrentCallTarget(), frame, arguments, inlined);
    }

    public static Object callProxy(MaterializedFrameNotify notify, OptimizedCallTarget callTarget, VirtualFrame frame, Object[] arguments, boolean inlined) {
        try {
            if (notify.getOutsideFrameAccess() != FrameAccess.NONE) {
                CompilerDirectives.materialize(frame);
            }
            if (inlined) {
                return callTarget.callInlined(arguments);
            } else {
                return callTarget.call(arguments);
            }
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert notify != null & callTarget != null & frame != null;
        }
    }

    private void onInterpreterCall() {
        callCount++;
        if (trySplit) {
            if (callCount == 1) {
                // on first call
                getCurrentCallTarget().incrementKnownCallSites();
            }
            if (callCount > 1 && !inlined) {
                trySplit = false;
                if (shouldSplit()) {
                    splitImpl(true);
                }
            }
        }
    }

    /* Called by the runtime system if this CallNode is really going to be inlined. */
    void inline() {
        inlined = true;
    }

    @Override
    public boolean isInlined() {
        return inlined;
    }

    @Override
    public boolean split() {
        splitImpl(false);
        return true;
    }

    private void splitImpl(boolean heuristic) {
        CompilerAsserts.neverPartOfCompilation();

        OptimizedCallTarget splitTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(getCallTarget().getRootNode().split());
        splitTarget.setSplitSource(getCallTarget());
        if (heuristic) {
            OptimizedCallTargetLog.logSplit(this, getCallTarget(), splitTarget);
        }
        if (callCount >= 1) {
            getCallTarget().decrementKnownCallSites();
            splitTarget.incrementKnownCallSites();
        }
        this.splitCallTarget = splitTarget;
    }

    private boolean shouldSplit() {
        if (splitCallTarget != null) {
            return false;
        }
        if (!TruffleCompilerOptions.TruffleSplittingEnabled.getValue()) {
            return false;
        }
        if (!isSplittable()) {
            return false;
        }
        OptimizedCallTarget splitTarget = getCallTarget();
        int nodeCount = OptimizedCallUtils.countNonTrivialNodes(splitTarget, false);
        if (nodeCount > TruffleCompilerOptions.TruffleSplittingMaxCalleeSize.getValue()) {
            return false;
        }

        // disable recursive splitting for now
        OptimizedCallTarget root = (OptimizedCallTarget) getRootNode().getCallTarget();
        if (root == splitTarget || root.getSplitSource() == splitTarget) {
            // recursive call found
            return false;
        }

        // max one child call and callCount > 2 and kind of small number of nodes
        if (isMaxSingleCall()) {
            return true;
        }
        return countPolymorphic() >= 1;
    }

    private boolean isMaxSingleCall() {
        return NodeUtil.countNodes(getCurrentCallTarget().getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return node instanceof CallNode;
            }
        }) <= 1;
    }

    private int countPolymorphic() {
        return NodeUtil.countNodes(getCurrentCallTarget().getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                NodeCost cost = node.getCost();
                boolean polymorphic = cost == NodeCost.POLYMORPHIC || cost == NodeCost.MEGAMORPHIC;
                return polymorphic;
            }
        });
    }

    public static OptimizedCallNode create(OptimizedCallTarget target) {
        return new OptimizedCallNode(target);
    }
}
