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
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;

/**
 * A call node with a constant {@link CallTarget} that can be optimized by Graal.
 */
public final class OptimizedDirectCallNode extends DirectCallNode implements MaterializedFrameNotify {

    private int callCount;
    private boolean trySplit = true;
    private boolean inliningForced;

    @CompilationFinal private boolean inlined;
    @CompilationFinal private OptimizedCallTarget splitCallTarget;
    @CompilationFinal private FrameAccess outsideFrameAccess = FrameAccess.NONE;

    private OptimizedDirectCallNode(OptimizedCallTarget target) {
        super(target);
    }

    @Override
    public Object call(VirtualFrame frame, Object[] arguments) {
        if (CompilerDirectives.inInterpreter()) {
            onInterpreterCall();
        }
        return callProxy(this, getCurrentCallTarget(), frame, arguments, inlined, true);
    }

    public static Object callProxy(MaterializedFrameNotify notify, CallTarget callTarget, VirtualFrame frame, Object[] arguments, boolean inlined, boolean direct) {
        try {
            if (notify.getOutsideFrameAccess() != FrameAccess.NONE) {
                CompilerDirectives.materialize(frame);
            }
            if (inlined) {
                return ((OptimizedCallTarget) callTarget).callInlined(arguments);
            } else if (direct) {
                return ((OptimizedCallTarget) callTarget).callDirect(arguments);
            } else {
                return callTarget.call(arguments);
            }
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert notify != null & callTarget != null & frame != null;
        }
    }

    public void resetInlining() {
        CompilerAsserts.neverPartOfCompilation();
        if (inlined) {
            inlined = false;
            getCurrentCallTarget().invalidateInlining();
        }
    }

    @Override
    public boolean isInlinable() {
        return true;
    }

    @Override
    public void forceInlining() {
        inliningForced = true;
    }

    @Override
    public boolean isInliningForced() {
        return inliningForced;
    }

    @Override
    public FrameAccess getOutsideFrameAccess() {
        return outsideFrameAccess;
    }

    @Override
    public void setOutsideFrameAccess(FrameAccess outsideFrameAccess) {
        this.outsideFrameAccess = outsideFrameAccess;
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
                return node instanceof DirectCallNode;
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

    public static OptimizedDirectCallNode create(OptimizedCallTarget target) {
        return new OptimizedDirectCallNode(target);
    }

}
