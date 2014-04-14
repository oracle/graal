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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;

/**
 * Represents a call to a {@link CallTarget} in the Truffle AST. In addition to calling the
 * {@link CallTarget}, this {@link Node} enables the runtime system to implement further
 * optimizations. Optimizations that can possibly be applied to a {@link CallNode} are inlining and
 * splitting. Inlining inlines this call site into the call graph of the parent {@link CallTarget}.
 * Splitting duplicates the {@link CallTarget} using {@link RootNode#split()} to collect call site
 * sensitive profiling information.
 *
 * Please note: This class is not intended to be subclassed by guest language implementations.
 *
 * @see TruffleRuntime#createCallNode(CallTarget)
 * @see #forceInlining()
 * @see #split()
 */
public abstract class CallNode extends Node {

    protected final CallTarget callTarget;

    protected CallNode(CallTarget callTarget) {
        this.callTarget = callTarget;
    }

    /**
     * Calls the inner {@link CallTarget} returned by {@link #getCurrentCallTarget()}.
     *
     * @param arguments the arguments that should be passed to the callee
     * @return the return result of the call
     */
    public abstract Object call(VirtualFrame frame, Object[] arguments);

    /**
     * Returns the originally supplied {@link CallTarget} when this call node was created. Please
     * note that the returned {@link CallTarget} is not necessarily the {@link CallTarget} that is
     * called. For that use {@link #getCurrentCallTarget()} instead.
     *
     * @return the {@link CallTarget} provided.
     */
    public CallTarget getCallTarget() {
        return callTarget;
    }

    /**
     * Returns <code>true</code> if the underlying runtime system supports inlining for the
     * {@link CallTarget} in this {@link CallNode}.
     *
     * @return true if inlining is supported.
     */
    public abstract boolean isInlinable();

    /**
     * Returns <code>true</code> if the {@link CallTarget} is forced to be inlined. A
     * {@link CallNode} can either be inlined manually by invoking {@link #forceInlining()} or by
     * the runtime system which may at any point decide to inline.
     *
     * @return true if this method was inlined else false.
     */
    public abstract boolean isInliningForced();

    /**
     * Enforces the runtime system to inline the {@link CallTarget} at this call site. If the
     * runtime system does not support inlining or it is already inlined this method has no effect.
     * The runtime system may decide to not inline calls which were forced to inline.
     */
    public abstract void forceInlining();

    /**
     * Returns true if the runtime system has decided to inline this call-site. If the
     * {@link CallNode} was forced to inline then this does not necessarily mean that the
     * {@link CallNode} is really going to be inlined. This depends on whether or not the runtime
     * system supports inlining. The runtime system may also decide to not inline calls which were
     * forced to inline.
     */
    public abstract boolean isInlined();

    /**
     * Returns <code>true</code> if this {@link CallNode} can be split. A {@link CallNode} can only
     * be split if the runtime system supports splitting and if the {@link RootNode} contained the
     * {@link CallTarget} returns <code>true</code> for {@link RootNode#isSplittable()}.
     *
     * @return <code>true</code> if the target can be split
     */
    public abstract boolean isSplittable();

    /**
     * Enforces the runtime system to split the {@link CallTarget}. If the {@link CallNode} is not
     * splittable this methods has no effect.
     */
    public abstract boolean split();

    /**
     * Returns <code>true</code> if the target of the {@link CallNode} was split.
     *
     * @return if the target was split
     */
    public final boolean isSplit() {
        return getSplitCallTarget() != null;
    }

    /**
     * Returns the split {@link CallTarget} if this method is split.
     *
     * @return the split {@link CallTarget}
     */
    public abstract CallTarget getSplitCallTarget();

    /**
     * Returns the used call target when {@link #call(VirtualFrame, Object[])} is invoked. If the
     * {@link CallTarget} was split this method returns the {@link CallTarget} returned by
     * {@link #getSplitCallTarget()}.
     *
     * @return the used {@link CallTarget} when node is called
     */
    public CallTarget getCurrentCallTarget() {
        CallTarget split = getSplitCallTarget();
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
     */
    public final RootNode getCurrentRootNode() {
        CallTarget target = getCurrentCallTarget();
        if (target instanceof RootCallTarget) {
            return ((RootCallTarget) target).getRootNode();
        }
        return null;
    }
}
