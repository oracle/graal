/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;

/**
 * Represents a direct call to a {@link CallTarget}. Direct calls are calls for which the
 * {@link CallTarget} remains the same for each consecutive call. This part of the Truffle API
 * enables the runtime system to perform additional optimizations on direct calls.
 *
 * Optimizations that can be applied to a {@link DirectCallNode} are inlining and call site
 * sensitive AST duplication. Inlining inlines this call site into the call graph of the parent
 * {@link CallTarget}. Call site sensitive AST duplication duplicates the {@link CallTarget} in an
 * uninitialized state to collect call site sensitive profiling information.
 *
 * Please note: This class is not intended to be subclassed by guest language implementations.
 *
 * @see IndirectCallNode for calls with a non-constant target
 * @see TruffleRuntime#createDirectCallNode(CallTarget)
 * @see #forceInlining()
 * @see #cloneCallTarget()
 * @since 0.8 or earlier
 */
public abstract class DirectCallNode extends Node {

    /** @since 0.8 or earlier */
    protected final CallTarget callTarget;

    /** @since 0.8 or earlier */
    protected DirectCallNode(CallTarget callTarget) {
        this.callTarget = callTarget;
    }

    /**
     * Calls the inner {@link CallTarget} returned by {@link #getCurrentCallTarget()}.
     *
     * @param arguments the arguments that should be passed to the callee
     * @return the return result of the call
     * @since 0.23
     */
    public abstract Object call(Object... arguments);

    /**
     * Returns the originally supplied {@link CallTarget} when this call node was created. Please
     * note that the returned {@link CallTarget} is not necessarily the {@link CallTarget} that is
     * called. For that use {@link #getCurrentCallTarget()} instead.
     *
     * @return the {@link CallTarget} provided.
     * @since 0.8 or earlier
     */
    public CallTarget getCallTarget() {
        return callTarget;
    }

    /**
     * Returns <code>true</code> if the underlying runtime system supports inlining for the
     * {@link CallTarget} in this {@link DirectCallNode}.
     *
     * @return true if inlining is supported.
     * @since 0.8 or earlier
     */
    public abstract boolean isInlinable();

    /**
     * Returns <code>true</code> if the {@link CallTarget} is forced to be inlined. A
     * {@link DirectCallNode} can either be inlined manually by invoking {@link #forceInlining()} or
     * by the runtime system which may at any point decide to inline.
     *
     * @return true if this method was inlined else false.
     * @since 0.8 or earlier
     */
    public abstract boolean isInliningForced();

    /**
     * Enforces the runtime system to inline the {@link CallTarget} at this call site. If the
     * runtime system does not support inlining or it is already inlined this method has no effect.
     * The runtime system may decide to not inline calls which were forced to inline.
     *
     * @since 0.8 or earlier
     */
    public abstract void forceInlining();

    /**
     * Returns <code>true</code> if the runtime system supports cloning and the {@link RootNode}
     * returns <code>true</code> in {@link RootNode#isCloningAllowed()}.
     *
     * @return <code>true</code> if the target is allowed to be cloned.
     * @since 0.8 or earlier
     */
    public abstract boolean isCallTargetCloningAllowed();

    /**
     * Clones the {@link CallTarget} instance returned by {@link #getCallTarget()} in an
     * uninitialized state for this {@link DirectCallNode}. This can be sensible to gather call site
     * sensitive profiling information for this {@link DirectCallNode}. If
     * {@link #isCallTargetCloningAllowed()} returns <code>false</code> this method has no effect
     * and returns <code>false</code>.
     *
     * @since 0.8 or earlier
     */
    public abstract boolean cloneCallTarget();

    /**
     * Returns <code>true</code> if the target of the {@link DirectCallNode} was cloned by the
     * runtime system or by the guest language implementation.
     *
     * @return if the target was split
     * @since 0.8 or earlier
     */
    public final boolean isCallTargetCloned() {
        return getClonedCallTarget() != null;
    }

    /**
     * Returns the split {@link CallTarget} if this call site's {@link CallTarget} is cloned.
     *
     * @return the split {@link CallTarget}
     * @since 0.8 or earlier
     */
    public abstract CallTarget getClonedCallTarget();

    /**
     * Returns the used call target when {@link #call(java.lang.Object[])} is invoked. If the
     * {@link CallTarget} was split this method returns the {@link CallTarget} returned by
     * {@link #getClonedCallTarget()}.
     *
     * @return the used {@link CallTarget} when node is called
     * @since 0.8 or earlier
     */
    public CallTarget getCurrentCallTarget() {
        CallTarget split = getClonedCallTarget();
        if (split != null) {
            return split;
        } else {
            return getCallTarget();
        }
    }

    /**
     * Returns the {@link RootNode} associated with {@link CallTarget} returned by
     * {@link #getCurrentCallTarget()}. If the stored {@link CallTarget} does not contain a
     * {@link RootNode} this method returns <code>null</code>.
     *
     * @see #getCurrentCallTarget()
     * @return the root node of the used call target
     * @since 0.8 or earlier
     */
    public final RootNode getCurrentRootNode() {
        CallTarget target = getCurrentCallTarget();
        if (target instanceof RootCallTarget) {
            return ((RootCallTarget) target).getRootNode();
        }
        return null;
    }

    /** @since 0.8 or earlier */
    @Override
    public String toString() {
        return String.format("%s(target=%s)", getClass().getSimpleName(), getCurrentCallTarget());
    }

    /** @since 0.8 or earlier */
    public static DirectCallNode create(CallTarget target) {
        return Truffle.getRuntime().createDirectCallNode(target);
    }

}
