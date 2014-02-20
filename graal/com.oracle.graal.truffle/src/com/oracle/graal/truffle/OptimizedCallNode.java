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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Call target that is optimized by Graal upon surpassing a specific invocation threshold.
 */
abstract class OptimizedCallNode extends DefaultCallNode {

    protected int callCount;

    private OptimizedCallNode(OptimizedCallTarget target) {
        super(target);
    }

    @Override
    public final boolean isInlinable() {
        return true;
    }

    @Override
    public final boolean isSplittable() {
        return getCallTarget().getRootNode().isSplittable();
    }

    @Override
    public final OptimizedCallTarget getCallTarget() {
        return (OptimizedCallTarget) super.getCallTarget();
    }

    public final int getCallCount() {
        return callCount;
    }

    public TruffleInliningProfile createInliningProfile() {
        return new OptimizedCallNodeProfile(this);
    }

    @Override
    public OptimizedCallTarget getSplitCallTarget() {
        return null;
    }

    final OptimizedCallNode inlineImpl() {
        if (getParent() == null) {
            throw new IllegalStateException("CallNode must be adopted before it is split.");
        }
        return replace(new InlinedOptimizedCallNode(getCallTarget(), getSplitCallTarget(), getExecutedCallTarget().getRootNode(), callCount));
    }

    public final OptimizedCallTarget getExecutedCallTarget() {
        return getSplitCallTarget() != null ? getSplitCallTarget() : getCallTarget();
    }

    public static OptimizedCallNode create(OptimizedCallTarget target) {
        return new DefaultOptimizedCallNode(target);
    }

    private static final class DefaultOptimizedCallNode extends OptimizedCallNode {

        private boolean splitTried;

        DefaultOptimizedCallNode(OptimizedCallTarget target) {
            super(target);
        }

        @Override
        public Object call(PackedFrame caller, Arguments arguments) {
            if (CompilerDirectives.inInterpreter()) {
                callCount++;
                if (!splitTried) {
                    return trySplit(caller, arguments);
                }
            }
            return callTarget.call(caller, arguments);
        }

        private Object trySplit(PackedFrame caller, Arguments arguments) {
            int effectiveCallCount = callCount;
            // we try splitting for the first two invocations
            if (effectiveCallCount == 1 || effectiveCallCount == 2) {
                if (isSplittable() && shouldSplit()) {
                    return splitImpl().call(caller, arguments);
                }
            }
            if (effectiveCallCount >= 2) {
                splitTried = true;
            }
            return callTarget.call(caller, arguments);
        }

        @Override
        public boolean isInlined() {
            return false;
        }

        @Override
        public boolean split() {
            if (!isSplittable()) {
                // split is only allowed once and if the root node supports it
                return false;
            }
            if (getParent() == null) {
                throw new IllegalStateException("CallNode must be adopted before it is split.");
            }
            splitImpl();
            return true;
        }

        private OptimizedCallNode splitImpl() {
            RootNode splittedRoot = getCallTarget().getRootNode().split();
            OptimizedCallTarget splitCallTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(splittedRoot);
            OptimizedCallTarget.logSplit(getCallTarget(), splitCallTarget);
            return replace(new SplitOptimizedCallNode(getCallTarget(), splitCallTarget, callCount));
        }

        private boolean shouldSplit() {
            if (!TruffleCompilerOptions.TruffleSplittingEnabled.getValue()) {
                return false;
            }
            RootNode targetRoot = getCallTarget().getRootNode();
            int nodeCount = NodeUtil.countNodes(targetRoot, null, true);
            if (nodeCount >= TruffleCompilerOptions.TruffleSplittingMaxCalleeSize.getValue()) {
                return false;
            }
            SplitScoreVisitor visitor = new SplitScoreVisitor();
            targetRoot.accept(visitor);
            int genericNess = visitor.getSplitScore();
            return genericNess > 0;
        }

        @Override
        public void inline() {
            inlineImpl();
        }

        @Override
        public OptimizedCallTarget getSplitCallTarget() {
            return null;
        }

    }

    private static final class InlinedOptimizedCallNode extends OptimizedCallNode {

        private final RootNode inlinedRoot;
        private final OptimizedCallTarget splittedTarget;

        public InlinedOptimizedCallNode(OptimizedCallTarget target, OptimizedCallTarget splittedTarget, RootNode inlinedRoot, int callCount) {
            super(target);
            this.inlinedRoot = inlinedRoot;
            this.splittedTarget = splittedTarget;
            this.callCount = callCount;
            installParentInlinedCall();
        }

        @Override
        public Object call(PackedFrame caller, Arguments arguments) {
            if (CompilerDirectives.inInterpreter()) {
                callCount++;
            }
            return inlinedRoot.execute(Truffle.getRuntime().createVirtualFrame(caller, arguments, inlinedRoot.getFrameDescriptor()));
        }

        @Override
        public void inline() {
        }

        @Override
        public boolean split() {
            return false;
        }

        @Override
        public boolean isInlined() {
            return true;
        }

        @Override
        public RootNode getInlinedRoot() {
            return inlinedRoot;
        }

        @Override
        public OptimizedCallTarget getSplitCallTarget() {
            return splittedTarget;
        }
    }

    private static class SplitOptimizedCallNode extends OptimizedCallNode {

        private final OptimizedCallTarget splittedTarget;

        public SplitOptimizedCallNode(OptimizedCallTarget target, OptimizedCallTarget splittedTarget, int callCount) {
            super(target);
            this.callCount = callCount;
            this.splittedTarget = splittedTarget;
        }

        @Override
        public Object call(PackedFrame caller, Arguments arguments) {
            if (CompilerDirectives.inInterpreter()) {
                callCount++;
            }
            return splittedTarget.call(caller, arguments);
        }

        @Override
        public boolean isInlined() {
            return false;
        }

        @Override
        public final boolean split() {
            return false;
        }

        @Override
        public void inline() {
            inlineImpl();
        }

        @Override
        public final OptimizedCallTarget getSplitCallTarget() {
            return splittedTarget;
        }

    }

    private static final class SplitScoreVisitor implements NodeVisitor {

        private int splitScore = 0;

        public boolean visit(Node node) {
            if (node instanceof OptimizedCallNode) {
                OptimizedCallNode call = (OptimizedCallNode) node;
                if (call.getInlinedRoot() != null) {
                    call.getInlinedRoot().accept(this);
                }
            }
            splitScore += splitScore(node);
            return true;
        }

        public int getSplitScore() {
            return splitScore;
        }

        private static int splitScore(Node node) {
            NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
            if (info == null) {
                return 0;
            }
            switch (info.kind()) {
                case GENERIC:
                    return 3;
                case POLYMORPHIC:
                    return 1;
                default:
                    return 0;
            }
        }

    }

}
