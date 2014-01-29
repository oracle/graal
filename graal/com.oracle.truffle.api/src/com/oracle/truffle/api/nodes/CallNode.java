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
import com.oracle.truffle.api.impl.*;

/**
 * This node represents a call to a static {@link CallTarget}. This node should be used whenever a
 * {@link CallTarget} is considered constant at a certain location in the tree. This enables the
 * Truffle runtime to perform inlining or other optimizations for this call-site.
 * 
 * @see #create(CallTarget) to create a CallNode instance.
 */
public abstract class CallNode extends Node {

    protected final CallTarget callTarget;

    private CallNode(CallTarget callTarget) {
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

    /**
     * Returns <code>true</code> if the {@link CallTarget} contained in this {@link CallNode} can be
     * inlined. A {@link CallTarget} is considered inlinable if it was created using
     * {@link TruffleRuntime#createCallTarget(RootNode)} and if the enclosed {@link RootNode}
     * returns <code>true</code> for {@link RootNode#isInlinable()}.
     */
    public abstract boolean isInlinable();

    /**
     * @return true if this {@link CallNode} was already inlined.
     */
    public abstract boolean isInlined();

    /**
     * Enforces an inlining optimization on this {@link CallNode} instance. If not performed
     * manually the Truffle runtime may perform inlining using an heuristic to optimize the
     * performance of the execution. It is recommended to implement an version of
     * {@link RootNode#inline()} that adapts the inlining for possible guest language specific
     * behavior. If the this {@link CallNode} is not inlinable or is already inlined
     * <code>false</code> is returned.
     * 
     * @return <code>true</code> if the inlining operation was successful.
     */
    public abstract boolean inline();

    /**
     * Returns the inlined root node if the call node was inlined. If the {@link CallNode} was not
     * inlined <code>null</code> is returned.
     * 
     * @return the inlined root node returned by {@link RootNode#inline()}
     */
    public RootNode getInlinedRoot() {
        return null;
    }

    /**
     * Creates a new {@link CallNode} using a {@link CallTarget}.
     * 
     * @param target the {@link CallTarget} to call
     * @return a call node that calls the provided target
     */
    public static CallNode create(CallTarget target) {
        if (isInlinable(target)) {
            return new InlinableCallNode(target);
        } else {
            return new DefaultCallNode(target);
        }
    }

    /**
     * Warning: this is internal API and may change without notice.
     */
    public static int internalGetCallCount(CallNode callNode) {
        if (callNode.isInlinable() && !callNode.isInlined()) {
            return ((InlinableCallNode) callNode).getCallCount();
        }
        throw new UnsupportedOperationException();
    }

    /**
     * Warning: this is internal API and may change without notice.
     */
    public static void internalResetCallCount(CallNode callNode) {
        if (callNode.isInlinable() && !callNode.isInlined()) {
            ((InlinableCallNode) callNode).resetCallCount();
            return;
        }
    }

    private static boolean isInlinable(CallTarget callTarget) {
        if (callTarget instanceof DefaultCallTarget) {
            return (((DefaultCallTarget) callTarget).getRootNode()).isInlinable();
        }
        return false;
    }

    @Override
    public String toString() {
        return getParent() != null ? getParent().toString() : super.toString();
    }

    static final class DefaultCallNode extends CallNode {

        public DefaultCallNode(CallTarget target) {
            super(target);
        }

        @Override
        public Object call(PackedFrame caller, Arguments arguments) {
            return callTarget.call(caller, arguments);
        }

        @Override
        public boolean inline() {
            return false;
        }

        @Override
        public boolean isInlinable() {
            return false;
        }

        @Override
        public boolean isInlined() {
            return false;
        }

    }

    static final class InlinableCallNode extends CallNode {

        private int callCount;

        public InlinableCallNode(CallTarget target) {
            super(target);
        }

        @Override
        public Object call(PackedFrame parentFrame, Arguments arguments) {
            if (CompilerDirectives.inInterpreter()) {
                callCount++;
            }
            return callTarget.call(parentFrame, arguments);
        }

        @Override
        public boolean inline() {
            DefaultCallTarget defaultTarget = (DefaultCallTarget) getCallTarget();
            RootNode originalRootNode = defaultTarget.getRootNode();
            if (originalRootNode.isInlinable()) {
                RootNode inlinedRootNode = defaultTarget.getRootNode().inline();
                inlinedRootNode.setCallTarget(callTarget);
                inlinedRootNode.setParentInlinedCall(this);
                replace(new InlinedCallNode(defaultTarget, inlinedRootNode));
                return true;
            }
            return false;
        }

        @Override
        public boolean isInlined() {
            return false;
        }

        @Override
        public boolean isInlinable() {
            return true;
        }

        /* Truffle internal API. */
        int getCallCount() {
            return callCount;
        }

        /* Truffle internal API. */
        void resetCallCount() {
            callCount = 0;
        }

    }

    static final class InlinedCallNode extends CallNode {

        private final RootNode inlinedRoot;

        public InlinedCallNode(DefaultCallTarget callTarget, RootNode inlinedRoot) {
            super(callTarget);
            this.inlinedRoot = inlinedRoot;
        }

        @Override
        public Object call(PackedFrame caller, Arguments arguments) {
            return inlinedRoot.execute(Truffle.getRuntime().createVirtualFrame(caller, arguments, inlinedRoot.getFrameDescriptor()));
        }

        @Override
        public InlinedCallNode copy() {
            return new InlinedCallNode((DefaultCallTarget) getCallTarget(), NodeUtil.cloneNode(inlinedRoot));
        }

        @Override
        public RootNode getInlinedRoot() {
            return inlinedRoot;
        }

        @Override
        public boolean inline() {
            return false;
        }

        @Override
        public boolean isInlinable() {
            return true;
        }

        @Override
        public boolean isInlined() {
            return true;
        }

    }

}
