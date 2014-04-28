/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.hotspot;

import java.lang.reflect.*;

import com.oracle.graal.api.code.stack.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

public abstract class HotSpotFrameInstance implements FrameInstance {

    protected final InspectedFrame stackFrame;

    public HotSpotFrameInstance(InspectedFrame stackFrame) {
        this.stackFrame = stackFrame;
    }

    protected abstract int getNotifyIndex();

    protected abstract int getCallTargetIndex();

    protected abstract int getFrameIndex();

    @SlowPath
    public Frame getFrame(FrameAccess access, boolean slowPath) {
        if (access == FrameAccess.NONE) {
            return null;
        }
        if (!slowPath && getNotifyIndex() != -1) {
            MaterializedFrameNotify notify = (MaterializedFrameNotify) stackFrame.getLocal(getNotifyIndex());
            if (notify != null) {
                if (access.ordinal() > notify.getOutsideFrameAccess().ordinal()) {
                    notify.setOutsideFrameAccess(access);
                }
                if (stackFrame.isVirtual(getFrameIndex())) {
                    stackFrame.materializeVirtualObjects(true);
                }
            }
        }
        switch (access) {
            case READ_ONLY: {
                Frame frame = (Frame) stackFrame.getLocal(getFrameIndex());
                // assert that it is really used read only
                assert (frame = new ReadOnlyFrame(frame)) != null;
                return frame;
            }
            case READ_WRITE:
            case MATERIALIZE:
                if (stackFrame.isVirtual(getFrameIndex())) {
                    stackFrame.materializeVirtualObjects(false);
                }
                return (Frame) stackFrame.getLocal(getFrameIndex());
            default:
                throw GraalInternalError.unimplemented();
        }
    }

    public boolean isVirtualFrame() {
        return stackFrame.isVirtual(getFrameIndex());
    }

    public abstract CallTarget getCallTarget();

    public abstract CallTarget getTargetCallTarget();

    public Node getCallNode() {
        Object receiver = stackFrame.getLocal(getNotifyIndex());
        if (receiver instanceof DirectCallNode || receiver instanceof IndirectCallNode) {
            return (Node) receiver;
        }
        return null;
    }

    /**
     * This class represents a frame that is taken from the
     * {@link OptimizedDirectCallNode#callProxy(MaterializedFrameNotify, CallTarget, VirtualFrame, Object[], boolean, boolean)}
     * method.
     */
    public static final class CallNodeFrame extends HotSpotFrameInstance {
        public static final Method METHOD;
        static {
            try {
                METHOD = OptimizedDirectCallNode.class.getDeclaredMethod("callProxy", MaterializedFrameNotify.class, CallTarget.class, VirtualFrame.class, Object[].class, boolean.class, boolean.class);
            } catch (NoSuchMethodException | SecurityException e) {
                throw new GraalInternalError(e);
            }
        }
        private static final int NOTIFY_INDEX = 0;
        private static final int CALL_TARGET_INDEX = 1;
        private static final int FRAME_INDEX = 2;

        public CallNodeFrame(InspectedFrame stackFrame) {
            super(stackFrame);
        }

        @Override
        protected int getNotifyIndex() {
            return NOTIFY_INDEX;
        }

        @Override
        protected int getCallTargetIndex() {
            return CALL_TARGET_INDEX;
        }

        @Override
        protected int getFrameIndex() {
            return FRAME_INDEX;
        }

        @Override
        public CallTarget getCallTarget() {
            return getCallNode().getRootNode().getCallTarget();
        }

        @Override
        public CallTarget getTargetCallTarget() {
            return (CallTarget) stackFrame.getLocal(getCallTargetIndex());
        }
    }

    /**
     * This class represents a frame that is taken from the
     * {@link RootCallTarget#callProxy(VirtualFrame)} method.
     */
    @SuppressWarnings("javadoc")
    public static final class CallTargetFrame extends HotSpotFrameInstance {
        public static final Method METHOD;
        static {
            try {
                METHOD = OptimizedCallTarget.class.getDeclaredMethod("callProxy", VirtualFrame.class);
            } catch (NoSuchMethodException | SecurityException e) {
                throw new GraalInternalError(e);
            }
        }
        private static final int NOTIFY_INDEX = -1;
        private static final int CALL_TARGET_INDEX = 0;
        private static final int FRAME_INDEX = 1;
        private final boolean currentFrame;

        public CallTargetFrame(InspectedFrame stackFrame, boolean currentFrame) {
            super(stackFrame);
            this.currentFrame = currentFrame;
        }

        @Override
        public Frame getFrame(FrameAccess access, boolean slowPath) {
            if (!slowPath && currentFrame) {
                throw new UnsupportedOperationException("cannot access current frame as fast path");
            }
            return super.getFrame(access, slowPath);
        }

        @Override
        protected int getNotifyIndex() {
            return NOTIFY_INDEX;
        }

        @Override
        protected int getCallTargetIndex() {
            return CALL_TARGET_INDEX;
        }

        @Override
        protected int getFrameIndex() {
            return FRAME_INDEX;
        }

        @Override
        public CallTarget getCallTarget() {
            return (CallTarget) stackFrame.getLocal(getCallTargetIndex());
        }

        @Override
        public CallTarget getTargetCallTarget() {
            return null;
        }
    }
}
