/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * A call node with a constant {@link CallTarget} that can be optimized by Graal.
 *
 * Note: {@code PartialEvaluator} looks up this class and a number of its methods by name.
 */
@NodeInfo
public final class OptimizedDirectCallNode extends DirectCallNode {

    /*
     * Reflectively read by the Truffle compiler. See KnownTruffleTypes.
     */
    private int callCount;
    /*
     * Reflectively read by the Truffle compiler. See KnownTruffleTypes.
     */
    private boolean inliningForced;
    @CompilationFinal private Class<? extends Throwable> exceptionProfile;

    /*
     * Reflectively read by the Truffle compiler. See KnownTruffleTypes.
     */
    @CompilationFinal private OptimizedCallTarget currentCallTarget;
    private volatile boolean splitDecided;

    /*
     * Should be instantiated with the runtime.
     */
    OptimizedDirectCallNode(OptimizedCallTarget target) {
        super(target);
        assert target.isSourceCallTarget();
        this.currentCallTarget = target;
    }

    @Override
    public Object call(Object... arguments) {
        OptimizedCallTarget target = getCurrentCallTarget();
        if (CompilerDirectives.hasNextTier()) {
            incrementCallCount();
        }
        if (HostCompilerDirectives.inInterpreterFastPath()) {
            target = onInterpreterCall(target);
        }
        try {
            return target.callDirect(this, arguments);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    private RuntimeException handleException(Throwable t) {
        Throwable profiledT = profileExceptionType(t);
        OptimizedRuntimeAccessor.LANGUAGE.addStackFrameInfo(this, null, profiledT, null);
        throw OptimizedCallTarget.rethrow(profiledT);
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
        return (OptimizedCallTarget) this.callTarget;
    }

    public int getCallCount() {
        return callCount;
    }

    @Override
    public OptimizedCallTarget getCurrentCallTarget() {
        return currentCallTarget;
    }

    public int getKnownCallSiteCount() {
        return getCurrentCallTarget().getKnownCallSiteCount();
    }

    @Override
    public OptimizedCallTarget getClonedCallTarget() {
        if (currentCallTarget != callTarget) {
            return currentCallTarget;
        }
        return null;
    }

    /**
     * @return The current call target (ie. getCurrentCallTarget) In case a splitting decision was
     *         made during this interpreter call, the argument target otherwise.
     */
    private OptimizedCallTarget onInterpreterCall(OptimizedCallTarget target) {
        if (target.isNeedsSplit() && !splitDecided) {
            // We intentionally avoid locking here because worst case is a double decision printed
            // and preventing that is not worth the performance impact of locking
            splitDecided = true;
            TruffleSplittingStrategy.beforeCall(this, target);
            return getCurrentCallTarget();
        }
        return target;
    }

    private void incrementCallCount() {
        int calls = this.callCount;
        this.callCount = calls == Integer.MAX_VALUE ? calls : ++calls;
    }

    /** Used by the splitting strategy to install new targets. */
    void split() {
        CompilerAsserts.neverPartOfCompilation();

        // Synchronize with atomic() as replace() also takes the same lock
        // and we only want to take one lock to avoid deadlocks.
        atomic(() -> {
            if (currentCallTarget != callTarget) {
                // already split
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
            currentCallTarget = splitTarget;
            OptimizedCallTarget.runtime().getListener().onCompilationSplit(this);
        });
    }

    @Override
    public boolean cloneCallTarget() {
        TruffleSplittingStrategy.forceSplitting(this);
        return true;
    }
}
