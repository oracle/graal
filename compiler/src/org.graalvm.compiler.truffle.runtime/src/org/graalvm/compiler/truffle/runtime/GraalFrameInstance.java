/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import java.lang.reflect.Method;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import jdk.vm.ci.code.stack.InspectedFrame;

public class GraalFrameInstance implements FrameInstance {
    static final int CALL_TARGET_INDEX = 0;
    static final int FRAME_INDEX = 1;
    static final int OPTIMIZATION_TIER_FRAME_INDEX = 2;

    static final int CALL_NODE_NOTIFY_INDEX = 1;

    static final Method CALL_TARGET_METHOD;
    static final Method CALL_DIRECT;
    static final Method CALL_INLINED;
    static final Method CALL_INLINED_CALL;
    static final Method CALL_INDIRECT;

    static {
        try {
            CALL_DIRECT = OptimizedCallTarget.class.getDeclaredMethod("callDirect", Node.class, Object[].class);
            CALL_INLINED = OptimizedCallTarget.class.getDeclaredMethod("callInlined", Node.class, Object[].class);
            CALL_INLINED_CALL = GraalRuntimeSupport.class.getDeclaredMethod(GraalRuntimeSupport.CALL_INLINED_METHOD_NAME, Node.class, CallTarget.class, Object[].class);
            CALL_INDIRECT = OptimizedCallTarget.class.getDeclaredMethod("callIndirect", Node.class, Object[].class);
            CALL_TARGET_METHOD = OptimizedCallTarget.class.getDeclaredMethod("executeRootNode", VirtualFrame.class, CompilationState.class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new InternalError(e);
        }
    }

    private final InspectedFrame callTargetFrame;
    private final InspectedFrame callNodeFrame;

    GraalFrameInstance(InspectedFrame callTargetFrame, InspectedFrame callNodeFrame) {
        this.callTargetFrame = callTargetFrame;
        this.callNodeFrame = callNodeFrame;
    }

    @TruffleBoundary
    protected Frame getFrameFrom(InspectedFrame inspectedFrame, FrameAccess access) {
        if (access == FrameAccess.READ_WRITE || access == FrameAccess.MATERIALIZE) {
            if (inspectedFrame.isVirtual(FRAME_INDEX)) {
                final OptimizedCallTarget callTarget = (OptimizedCallTarget) getCallTarget();
                if (callTarget.engine.traceDeoptimizeFrame) {
                    GraalTruffleRuntime.StackTraceHelper.logHostAndGuestStacktrace("FrameInstance#getFrame(MATERIALIZE)", callTarget);
                }
                inspectedFrame.materializeVirtualObjects(false);
            }
        }
        Frame frame = (Frame) inspectedFrame.getLocal(FRAME_INDEX);
        if (access == FrameAccess.MATERIALIZE) {
            frame = frame.materialize();
        }
        return frame;
    }

    @TruffleBoundary
    @Override
    public Frame getFrame(FrameAccess access) {
        return getFrameFrom(callTargetFrame, access);
    }

    @TruffleBoundary
    @Override
    public boolean isVirtualFrame() {
        return callTargetFrame.isVirtual(FRAME_INDEX);
    }

    @TruffleBoundary
    @Override
    public int getCompilationTier() {
        return ((CompilationState) callTargetFrame.getLocal(OPTIMIZATION_TIER_FRAME_INDEX)).getTier();
    }

    @TruffleBoundary
    @Override
    public boolean isCompilationRoot() {
        return ((CompilationState) callTargetFrame.getLocal(OPTIMIZATION_TIER_FRAME_INDEX)).isCompilationRoot();
    }

    @TruffleBoundary
    @Override
    public CallTarget getCallTarget() {
        return (CallTarget) callTargetFrame.getLocal(CALL_TARGET_INDEX);
    }

    @TruffleBoundary
    @Override
    public final Node getCallNode() {
        if (callNodeFrame != null) {
            Object receiver = callNodeFrame.getLocal(CALL_NODE_NOTIFY_INDEX);
            if (receiver instanceof Node) {
                return (Node) receiver;
            }
        }
        return null;
    }

}
