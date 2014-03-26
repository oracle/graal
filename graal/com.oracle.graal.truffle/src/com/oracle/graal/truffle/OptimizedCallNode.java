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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;

/**
 * Call target that is optimized by Graal upon surpassing a specific invocation threshold.
 */
abstract class OptimizedCallNode extends DefaultCallNode {

    protected int callCount;

    private OptimizedCallNode(OptimizedCallTarget target) {
        super(target);
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

    public TruffleInliningProfile createInliningProfile(OptimizedCallTarget target) {
        return new OptimizedCallNodeProfile(target, this);
    }

    @SuppressWarnings("unused")
    public void nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
    }

    @Override
    public final OptimizedCallTarget getCurrentCallTarget() {
        return (OptimizedCallTarget) super.getCurrentCallTarget();
    }

    @Override
    public OptimizedCallTarget getSplitCallTarget() {
        return null;
    }

    protected OptimizedCallNode inlineImpl() {
        if (getParent() == null) {
            throw new IllegalStateException("CallNode must be adopted before it is split.");
        }

        return replace(new InlinedOptimizedCallNode(getCallTarget(), getSplitCallTarget(), callCount));
    }

    public static OptimizedCallNode create(OptimizedCallTarget target) {
        return new DefaultOptimizedCallNode(target);
    }

    @Override
    public boolean isInlinable() {
        return true;
    }

    private static final class DefaultOptimizedCallNode extends OptimizedCallNode {

        private boolean trySplit = true;

        DefaultOptimizedCallNode(OptimizedCallTarget target) {
            super(target);
            registerCallTarget(this);
        }

        @Override
        public Object call(PackedFrame caller, Arguments arguments) {
            if (CompilerDirectives.inInterpreter()) {
                callCount++;
                if (trySplit && callCount > 1) {
                    trySplit = false;
                    return trySplit(caller, arguments);
                }
            }
            return callTarget.call(caller, arguments);
        }

        private Object trySplit(PackedFrame caller, Arguments arguments) {
            if (shouldSplit()) {
                return splitImpl(true).call(caller, arguments);
            }
            return callTarget.call(caller, arguments);
        }

        private boolean shouldSplit() {
            if (!TruffleCompilerOptions.TruffleSplittingEnabled.getValue()) {
                return false;
            }
            if (!isSplittable()) {
                return false;
            }
            int nodeCount = NodeUtil.countNodes(getCallTarget().getRootNode(), OptimizedCallNodeProfile.COUNT_FILTER, false);
            if (nodeCount > TruffleCompilerOptions.TruffleSplittingMaxCalleeSize.getValue()) {
                return false;
            }

            // disable recursive splitting for now
            OptimizedCallTarget splitTarget = getCallTarget();
            List<OptimizedCallTarget> compilationRoots = OptimizedCallNodeProfile.findCompilationRoots(this);
            for (OptimizedCallTarget compilationRoot : compilationRoots) {
                if (compilationRoot == splitTarget || compilationRoot.getSplitSource() == splitTarget) {
                    // recursive call found
                    return false;
                }
            }

            // max one child call and callCount > 2 and kind of small number of nodes
            if (isMaxSingleCall()) {
                return true;
            }
            return countPolymorphic() >= 1;
        }

        @Override
        public void nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
            trySplit = true;
        }

        private boolean isMaxSingleCall() {
            return NodeUtil.countNodes(getCurrentCallTarget().getRootNode(), new NodeCountFilter() {
                public boolean isCounted(Node node) {
                    return node instanceof CallNode;
                }
            }) <= 1;
        }

        private int countPolymorphic() {
            return NodeUtil.countNodes(getCallTarget().getRootNode(), new NodeCountFilter() {
                public boolean isCounted(Node node) {
                    NodeCost cost = node.getCost();
                    boolean polymorphic = cost == NodeCost.POLYMORPHIC || cost == NodeCost.MEGAMORPHIC;
                    return polymorphic;
                }
            });
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
            splitImpl(false);
            return true;
        }

        private OptimizedCallNode splitImpl(boolean heuristic) {
            OptimizedCallTarget splitCallTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(getCallTarget().getRootNode().split());
            splitCallTarget.setSplitSource(getCallTarget());
            if (heuristic) {
                OptimizedCallTarget.logSplit(this, getCallTarget(), splitCallTarget);
            }
            return replace(new SplitOptimizedCallNode(getCallTarget(), splitCallTarget, callCount));
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

        private final OptimizedCallTarget splittedTarget;

        public InlinedOptimizedCallNode(OptimizedCallTarget target, OptimizedCallTarget splittedTarget, int callCount) {
            super(target);
            this.splittedTarget = splittedTarget;
            this.callCount = callCount;
        }

        @Override
        public Object call(PackedFrame caller, Arguments arguments) {
            if (CompilerDirectives.inInterpreter()) {
                callCount++;
            }
            return getCurrentCallTarget().callInlined(caller, arguments);
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

}
