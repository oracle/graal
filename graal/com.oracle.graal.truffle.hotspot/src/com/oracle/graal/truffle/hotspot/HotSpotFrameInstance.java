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
import com.oracle.graal.graph.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;

public abstract class HotSpotFrameInstance implements FrameInstance {

    private final InspectedFrame stackFrame;

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
        if (!slowPath) {
            MaterializedFrameNotify notify = (MaterializedFrameNotify) stackFrame.getLocal(getNotifyIndex());
            if (access.ordinal() > notify.getOutsideFrameAccess().ordinal()) {
                notify.setOutsideFrameAccess(access);
            }
            if (stackFrame.isVirtual(getFrameIndex())) {
                stackFrame.materializeVirtualObjects(true);
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

    public CallTarget getCallTarget() {
        return (CallTarget) stackFrame.getLocal(getCallTargetIndex());
    }

    public CallNode getCallNode() {
        Object receiver = stackFrame.getLocal(getNotifyIndex());
        if (receiver instanceof CallNode) {
            return (CallNode) receiver;
        } else {
            return null;
        }
    }

    /**
     * This class represents a frame that is taken from the
     * {@link DefaultCallNode#callProxy(MaterializedFrameNotify, CallTarget, VirtualFrame, Object[])}
     * method.
     */
    public static final class NextFrame extends HotSpotFrameInstance {
        public static final Method METHOD;
        static {
            try {
                METHOD = DefaultCallNode.class.getDeclaredMethod("callProxy", MaterializedFrameNotify.class, CallTarget.class, VirtualFrame.class, Object[].class);
            } catch (NoSuchMethodException | SecurityException e) {
                throw new GraalInternalError(e);
            }
        }
        private static final int NOTIFY_INDEX = 0;
        private static final int CALL_TARGET_INDEX = 1;
        private static final int FRAME_INDEX = 2;

        public NextFrame(InspectedFrame stackFrame) {
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
    }

    /**
     * This class represents a frame that is taken from the
     * {@link RootCallTarget#callProxy(VirtualFrame)} method.
     */
    @SuppressWarnings("javadoc")
    public static final class CurrentFrame extends HotSpotFrameInstance {
        public static final Method METHOD;
        static {
            try {
                METHOD = RootCallTarget.class.getDeclaredMethod("callProxy", VirtualFrame.class);
            } catch (NoSuchMethodException | SecurityException e) {
                throw new GraalInternalError(e);
            }
        }
        private static final int NOTIFY_INDEX = 0;
        private static final int CALL_TARGET_INDEX = 0;
        private static final int FRAME_INDEX = 1;

        public CurrentFrame(InspectedFrame stackFrame) {
            super(stackFrame);
        }

        @Override
        public Frame getFrame(FrameAccess access, boolean slowPath) {
            if (!slowPath) {
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
    }
}
