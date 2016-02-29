/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * This is an implementation-specific class. Do not use or instantiate it. Instead, use
 * {@link TruffleRuntime#createCallTarget(RootNode)} to create a {@link RootCallTarget}.
 */
public final class DefaultCallTarget implements RootCallTarget {

    private final RootNode rootNode;
    private boolean initialized;

    DefaultCallTarget(RootNode function) {
        this.rootNode = function;
        this.rootNode.adoptChildren();
        this.rootNode.applyInstrumentation();
    }

    @Override
    public String toString() {
        return rootNode.toString();
    }

    public RootNode getRootNode() {
        return rootNode;
    }

    Object callDirectOrIndirect(final Node callNode, Object... args) {
        if (!this.initialized) {
            initialize();
        }
        final DefaultVirtualFrame frame = new DefaultVirtualFrame(getRootNode().getFrameDescriptor(), args);
        getRuntime().pushFrame(new CallNodeFrameInstance(this, frame, callNode));
        try {
            return getRootNode().execute(frame);
        } finally {
            getRuntime().popFrame();
        }
    }

    @Override
    public Object call(Object... args) {
        if (!this.initialized) {
            initialize();
        }
        final DefaultVirtualFrame frame = new DefaultVirtualFrame(getRootNode().getFrameDescriptor(), args);
        getRuntime().pushFrame(new CallTargetFrameInstance(this, frame));
        try {
            return getRootNode().execute(frame);
        } finally {
            getRuntime().popFrame();
        }
    }

    private static DefaultTruffleRuntime getRuntime() {
        return (DefaultTruffleRuntime) Truffle.getRuntime();
    }

    private abstract static class DefaultFrameInstance implements FrameInstance {

        private final CallTarget target;
        private final VirtualFrame frame;

        DefaultFrameInstance(CallTarget target, VirtualFrame frame) {
            this.target = target;
            this.frame = frame;
        }

        public final Frame getFrame(FrameAccess access, boolean slowPath) {
            if (access == FrameAccess.NONE) {
                return null;
            }
            if (access == FrameAccess.MATERIALIZE) {
                return frame.materialize();
            }
            return frame;
        }

        public final boolean isVirtualFrame() {
            return false;
        }

        public final CallTarget getCallTarget() {
            return target;
        }

    }

    private static class CallTargetFrameInstance extends DefaultFrameInstance {

        CallTargetFrameInstance(CallTarget target, VirtualFrame frame) {
            super(target, frame);
        }

        public Node getCallNode() {
            return null;
        }

    }

    private static class CallNodeFrameInstance extends DefaultFrameInstance {

        private final Node callNode;

        CallNodeFrameInstance(CallTarget target, VirtualFrame frame, Node callNode) {
            super(target, frame);
            this.callNode = callNode;
        }

        public Node getCallNode() {
            return callNode;
        }

    }

    private void initialize() {
        synchronized (this) {
            if (!this.initialized) {
                this.initialized = true;
                Accessor accessor = Accessor.INSTRUMENTHANDLER;
                if (accessor != null) {
                    accessor.initializeCallTarget(this);
                }
            }
        }
    }
}
