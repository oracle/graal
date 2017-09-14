/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * A call node with a constant {@link CallTarget} that can be optimized by Graal.
 */
@NodeInfo
public final class OptimizedDirectCallNode extends DirectCallNode {

    private int callCount;
    private boolean inliningForced;

    @CompilationFinal private OptimizedCallTarget splitCallTarget;

    private final TruffleSplittingStrategy splittingStrategy;
    private final GraalTruffleRuntime runtime;

    public OptimizedDirectCallNode(GraalTruffleRuntime runtime, OptimizedCallTarget target) {
        super(target);
        assert target.getSourceCallTarget() == null;
        this.runtime = runtime;
        this.splittingStrategy = new DefaultTruffleSplittingStrategy(this);
    }

    @Override
    public Object call(Object[] arguments) {
        if (CompilerDirectives.inInterpreter()) {
            onInterpreterCall(arguments);
        }
        return callProxy(this, getCurrentCallTarget(), arguments, true);
    }

    public static Object callProxy(Node callNode, CallTarget callTarget, Object[] arguments, boolean direct) {
        try {
            if (direct) {
                return ((OptimizedCallTarget) callTarget).callDirect(arguments);
            } else {
                return callTarget.call(arguments);
            }
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert callNode != null & callTarget != null;
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
    public boolean isCallTargetCloningAllowed() {
        return getCallTarget().getRootNode().isCloningAllowed();
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
    public OptimizedCallTarget getClonedCallTarget() {
        return splitCallTarget;
    }

    private void onInterpreterCall(Object[] arguments) {
        int calls = ++callCount;
        if (calls == 1) {
            getCurrentCallTarget().incrementKnownCallSites();
        }
        splittingStrategy.beforeCall(arguments);
    }

    /** Used by the splitting strategy to install new targets. */
    void split() {
        CompilerAsserts.neverPartOfCompilation();

        // Synchronize with atomic() as replace() also takes the same lock
        // and we only want to take one lock to avoid deadlocks.
        atomic(() -> {
            if (splitCallTarget != null) {
                return;
            }

            assert isCallTargetCloningAllowed();
            OptimizedCallTarget currentTarget = getCallTarget();
            OptimizedCallTarget splitTarget = getCallTarget().cloneUninitialized();

            if (callCount >= 1) {
                currentTarget.decrementKnownCallSites();
            }
            splitTarget.incrementKnownCallSites();

            if (getParent() != null) {
                // dummy replace to report the split, irrelevant if this node is not adopted
                replace(this, "Split call node");
            }
            splitCallTarget = splitTarget;
            runtime.getCompilationNotify().notifyCompilationSplit(this);
        });
    }

    @Override
    public boolean cloneCallTarget() {
        splittingStrategy.forceSplitting();
        return true;
    }

}
