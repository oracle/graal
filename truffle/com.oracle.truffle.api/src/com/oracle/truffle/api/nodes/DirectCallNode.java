/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;

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
    static final VirtualFrame LEGACY_FRAME = Truffle.getRuntime().createVirtualFrame(new Object[0], new FrameDescriptor());

    /** @since 0.8 or earlier */
    protected final CallTarget callTarget;
    @Deprecated @CompilationFinal private VirtualFrame legacyFrame;

    /** @since 0.8 or earlier */
    protected DirectCallNode(CallTarget callTarget) {
        this.callTarget = callTarget;
    }

    /**
     * Calls the inner {@link CallTarget} returned by {@link #getCurrentCallTarget()}.
     *
     * @param arguments the arguments that should be passed to the callee
     * @return the return result of the call
     * @since 0.8 or earlier
     * @deprecated use call without frame instead
     */
    @Deprecated
    public Object call(@SuppressWarnings("unused") VirtualFrame frame, Object[] arguments) {
        return call(arguments);
    }

    /**
     * Calls the inner {@link CallTarget} returned by {@link #getCurrentCallTarget()}.
     *
     * @param arguments the arguments that should be passed to the callee
     * @return the return result of the call
     * @since 0.23
     */
    public Object call(Object[] arguments) {
        // TODO change to varargs as soon as #call(VirtualFrame, Object[] will removed.
        return call(LEGACY_FRAME, arguments);
    }

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
