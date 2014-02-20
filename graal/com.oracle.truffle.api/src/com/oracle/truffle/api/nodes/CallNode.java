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
 * intended to be implemented by truffle runtime implementors and not by guest languague
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
     * @return the constant {@link CallTarget} that is associated with this {@link CallNode}.
     */
    public CallTarget getCallTarget() {
        return callTarget;
    }

    /**
     * Calls this constant target passing a caller frame and arguments.
     * 
     * @param caller the caller frame
     * @param arguments the arguments that should be passed to the callee
     * @return the return result of the call
     */
    public abstract Object call(PackedFrame caller, Arguments arguments);

    public abstract boolean isInlinable();

    /**
     * @return true if this {@link CallNode} was already inlined.
     */
    public abstract boolean isInlined();

    public abstract void inline();

    public abstract boolean isSplittable();

    public abstract boolean split();

    public abstract CallTarget getSplitCallTarget();

    public abstract RootNode getInlinedRoot();

    /**
     * Creates a new {@link CallNode} using a {@link CallTarget}.
     * 
     * @param target the {@link CallTarget} to call
     * @return a call node that calls the provided target
     * @deprecated use {@link TruffleRuntime#createCallNode(CallTarget)} instead
     */
    @Deprecated
    public static CallNode create(CallTarget target) {
        return Truffle.getRuntime().createCallNode(target);
    }

    protected final void installParentInlinedCall() {
        getInlinedRoot().addParentInlinedCall(this);
    }

}
