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
package com.oracle.graal.truffle;

import java.lang.reflect.Method;

import jdk.vm.ci.code.stack.InspectedFrame;
import jdk.vm.ci.common.JVMCIError;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;

public final class GraalFrameInstance implements FrameInstance {

    private static final int CALL_TARGET_INDEX = 0;
    private static final int CALL_TARGET_FRAME_INDEX = 1;

    private static final int CALL_NODE_NOTIFY_INDEX = 0;
    private static final int CALL_NODE_FRAME_INDEX = 2;

    public static final Method CALL_TARGET_METHOD;
    public static final Method CALL_NODE_METHOD;

    static {
        try {
            CALL_NODE_METHOD = OptimizedDirectCallNode.class.getDeclaredMethod("callProxy", MaterializedFrameNotify.class, CallTarget.class, VirtualFrame.class, Object[].class, boolean.class);
            CALL_TARGET_METHOD = OptimizedCallTarget.class.getDeclaredMethod("callProxy", VirtualFrame.class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new JVMCIError(e);
        }
    }

    private InspectedFrame callTargetFrame;
    private InspectedFrame callNodeFrame;
    private final boolean currentFrame;

    public GraalFrameInstance(boolean currentFrame) {
        this.currentFrame = currentFrame;
    }

    void setCallTargetFrame(InspectedFrame callTargetFrame) {
        this.callTargetFrame = callTargetFrame;
    }

    void setCallNodeFrame(InspectedFrame callNodeFrame) {
        this.callNodeFrame = callNodeFrame;
    }

    @TruffleBoundary
    public Frame getFrame(FrameAccess access, boolean slowPath) {
        if (!slowPath && currentFrame) {
            throw new UnsupportedOperationException("cannot access current frame as fast path");
        }
        if (access == FrameAccess.NONE) {
            return null;
        }
        if (!slowPath && callNodeFrame != null) {
            MaterializedFrameNotify notify = (MaterializedFrameNotify) callNodeFrame.getLocal(CALL_NODE_NOTIFY_INDEX);
            if (notify != null) {
                if (access.ordinal() > notify.getOutsideFrameAccess().ordinal()) {
                    notify.setOutsideFrameAccess(access);
                }
                if (callNodeFrame.isVirtual(CALL_NODE_FRAME_INDEX)) {
                    callNodeFrame.materializeVirtualObjects(true);
                }
            }
        }
        switch (access) {
            case READ_ONLY: {
                Frame frame = (Frame) callTargetFrame.getLocal(CALL_TARGET_FRAME_INDEX);
                // assert that it is really used read only
                assert (frame = new ReadOnlyFrame(frame)) != null;
                return frame;
            }
            case READ_WRITE:
            case MATERIALIZE:
                if (callTargetFrame.isVirtual(CALL_TARGET_FRAME_INDEX)) {
                    callTargetFrame.materializeVirtualObjects(false);
                }
                return (Frame) callTargetFrame.getLocal(CALL_TARGET_FRAME_INDEX);
            default:
                throw JVMCIError.unimplemented();
        }
    }

    public boolean isVirtualFrame() {
        return callTargetFrame.isVirtual(CALL_TARGET_FRAME_INDEX);
    }

    @Override
    public CallTarget getCallTarget() {
        return (CallTarget) callTargetFrame.getLocal(CALL_TARGET_INDEX);
    }

    @Override
    public Node getCallNode() {
        if (callNodeFrame != null) {
            Object receiver = callNodeFrame.getLocal(CALL_NODE_NOTIFY_INDEX);
            if (receiver instanceof DirectCallNode || receiver instanceof IndirectCallNode) {
                return (Node) receiver;
            }
        }
        return null;
    }

}
