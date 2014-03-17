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
 * This node represents a call to a static {@link CallTarget}. This node should be used whenever a
 * {@link CallTarget} is considered constant at a certain location in the tree. This enables the
 * Truffle runtime to perform inlining or other optimizations for this call-site. This class is
 * intended to be implemented by truffle runtime implementors and not by guest language
 * implementors.
 * 
 * @see #create(CallTarget) to create a CallNode instance.
 */
public abstract class CallNode extends Node {

    protected final CallTarget callTarget;

    protected CallNode(CallTarget callTarget) {
        this.callTarget = callTarget;
    }

    /**
     * Calls this constant target passing a caller frame and arguments.
     * 
     * @param caller the caller frame
     * @param arguments the arguments that should be passed to the callee
     * @return the return result of the call
     */
    public abstract Object call(PackedFrame caller, Arguments arguments);

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
     * @return true if this {@link CallNode} was already inlined.
     */
    public abstract boolean isInlined();

    public abstract void inline();

    public abstract boolean isSplittable();

    public abstract boolean split();

    public final boolean isSplit() {
        return getSplitCallTarget() != null;
    }

    public abstract CallTarget getSplitCallTarget();

    /**
     * Returns the used call target when {@link #call(PackedFrame, Arguments)} is invoked. If the
     * {@link CallNode} was split this method returns the {@link CallTarget} returned by
     * {@link #getSplitCallTarget()}. If not split this method returns the original supplied
     * {@link CallTarget}.
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

    @Override
    protected void onReplace(Node newNode, String reason) {
        super.onReplace(newNode, reason);

        /*
         * Old call nodes are removed in the old target root node.
         */
        CallNode oldCall = this;
        RootNode oldRoot = getCurrentRootNode();
        if (oldRoot != null) {
            oldRoot.removeCachedCallNode(oldCall);
        }

        registerCallTarget((CallNode) newNode);
    }

    protected static final void registerCallTarget(CallNode newNode) {
        RootNode newRoot = newNode.getCurrentRootNode();
        if (newRoot != null) {
            newRoot.addCachedCallNode(newNode);
        }
    }

    protected void notifyCallNodeAdded() {

    }

    /**
     * Returns the {@link RootNode} associated with {@link CallTarget} returned by
     * {@link #getCurrentCallTarget()}.
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
