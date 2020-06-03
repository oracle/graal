/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.truffle.runtime;

import org.graalvm.compiler.truffle.common.TruffleCallNode;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.impl.DefaultCompilerOptions;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * A call node with a constant {@link CallTarget} that can be optimized by Graal.
 *
 * Note: {@code PartialEvaluator} looks up this class and a number of its methods by name.
 */
@NodeInfo
public final class OptimizedDirectCallNode extends DirectCallNode implements TruffleCallNode {

    private int callCount;
    private boolean inliningForced;
    @CompilationFinal private Class<? extends Throwable> exceptionProfile;
    @CompilationFinal private OptimizedCallTarget splitCallTarget;
    private volatile boolean splitDecided;

    /*
     * Should be instantiated with the runtime.
     */
    OptimizedDirectCallNode(OptimizedCallTarget target) {
        super(target);
        assert target.getSourceCallTarget() == null;
    }

    @Override
    public Object call(Object... arguments) {
        OptimizedCallTarget target = getCurrentCallTarget();
        if (CompilerDirectives.inInterpreter()) {
            target = onInterpreterCall(target);
        }
        try {
            return target.callDirect(this, arguments);
        } catch (Throwable t) {
            Throwable profiledT = profileExceptionType(t);
            GraalRuntimeAccessor.LANGUAGE.onThrowable(this, null, profiledT, null);
            throw OptimizedCallTarget.rethrow(profiledT);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Throwable> T profileExceptionType(T value) {
        Class<? extends Throwable> clazz = exceptionProfile;
        if (clazz != Throwable.class) {
            if (clazz != null && value.getClass() == clazz) {
                if (CompilerDirectives.inInterpreter()) {
                    return value;
                } else {
                    return (T) CompilerDirectives.castExact(value, clazz);
                }
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (clazz == null) {
                    exceptionProfile = value.getClass();
                } else {
                    exceptionProfile = Throwable.class;
                }
            }
        }
        return value;
    }

    @Override
    public boolean isInlinable() {
        CompilerAsserts.neverPartOfCompilation();
        return true;
    }

    @Override
    public void forceInlining() {
        CompilerAsserts.neverPartOfCompilation();
        inliningForced = true;
    }

    @Override
    public boolean isInliningForced() {
        CompilerAsserts.neverPartOfCompilation();
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

    @Override
    public int getCallCount() {
        return callCount;
    }

    public CompilerOptions getCompilerOptions() {
        RootNode rootNode = getRootNode();
        return rootNode != null ? rootNode.getCompilerOptions() : DefaultCompilerOptions.INSTANCE;
    }

    @Override
    public OptimizedCallTarget getCurrentCallTarget() {
        return (OptimizedCallTarget) super.getCurrentCallTarget();
    }

    public int getKnownCallSiteCount() {
        return getCurrentCallTarget().getKnownCallSiteCount();
    }

    @Override
    public OptimizedCallTarget getClonedCallTarget() {
        return splitCallTarget;
    }

    /**
     * @return The current call target (ie. getCurrentCallTarget) In case a splitting decision was
     *         made during this interpreter call, the argument target otherwise.
     */
    private OptimizedCallTarget onInterpreterCall(OptimizedCallTarget target) {
        callCount++;
        if (target.isNeedsSplit() && !splitDecided) {
            // We intentionally avoid locking here because worst case is a double decision printed
            // and preventing that is not worth the performance impact of locking
            splitDecided = true;
            TruffleSplittingStrategy.beforeCall(this, target);
            return getCurrentCallTarget();
        }
        return target;
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
            currentTarget.removeDirectCallNode(this);
            splitTarget.addDirectCallNode(this);
            assert splitTarget.getCallSiteForSplit() == this;

            if (getParent() != null) {
                // dummy replace to report the split, irrelevant if this node is not adopted
                replace(this, "Split call node");
            }
            splitCallTarget = splitTarget;
            OptimizedCallTarget.runtime().getListener().onCompilationSplit(this);
        });
    }

    @Override
    public boolean cloneCallTarget() {
        TruffleSplittingStrategy.forceSplitting(this);
        return true;
    }
}
