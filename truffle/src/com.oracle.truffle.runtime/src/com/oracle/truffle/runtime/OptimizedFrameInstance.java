/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime;

import java.lang.reflect.Method;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.JDKAccessor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.stack.InspectedFrame;

public class OptimizedFrameInstance implements FrameInstance {
    static final int CALL_TARGET_INDEX = 0;
    static final int FRAME_INDEX = 1;
    static final int OPTIMIZATION_TIER_FRAME_INDEX = 2;

    static final int CALL_NODE_NOTIFY_INDEX = 1;
    static final int CALL_NODE_BYTECODE_INDEX_NOTIFY_INDEX = 2;

    static final int CALL_ROOT_ROOT_INDEX = 3;
    /** The frame is the first local variable declared after the callRoot parameters. */
    static final int CALL_ROOT_FRAME_INDEX = 5;

    static final Method CALL_TARGET_METHOD;
    static final Method CALL_DIRECT;
    static final Method CALL_INLINED;
    static final Method CALL_INLINED_CALL;
    static final Method CALL_INDIRECT;
    static final Method CALL_ROOT;

    static {
        try {
            CALL_DIRECT = OptimizedCallTarget.class.getDeclaredMethod("callDirect", Node.class, int.class, Object[].class);
            CALL_INLINED = OptimizedCallTarget.class.getDeclaredMethod("callInlined", Node.class, int.class, Object[].class);
            CALL_INLINED_CALL = OptimizedRuntimeSupport.class.getDeclaredMethod(OptimizedRuntimeSupport.CALL_INLINED_METHOD_NAME, Node.class, CallTarget.class, Object[].class);
            CALL_INDIRECT = OptimizedCallTarget.class.getDeclaredMethod("callIndirect", Node.class, int.class, Object[].class);
            CALL_TARGET_METHOD = OptimizedCallTarget.class.getDeclaredMethod("executeRootNode", VirtualFrame.class, CompilationState.class);
            CALL_ROOT = OptimizedCallTarget.class.getDeclaredMethod("callRoot", Node.class, int.class, RootNode.class, Object[].class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new InternalError(e);
        }
    }

    private final InspectedFrame callTargetFrame;
    private final InspectedFrame callNodeFrame;
    private final int bytecodeIndex;
    private final int frameIndex;

    OptimizedFrameInstance(InspectedFrame callTargetFrame, InspectedFrame callNodeFrame, int bytecodeIndex) {
        this(callTargetFrame, callNodeFrame, bytecodeIndex, FRAME_INDEX);
    }

    OptimizedFrameInstance(InspectedFrame callTargetFrame, InspectedFrame callNodeFrame, int bytecodeIndex, int frameIndex) {
        this.callTargetFrame = callTargetFrame;
        this.callNodeFrame = callNodeFrame;
        this.bytecodeIndex = bytecodeIndex;
        this.frameIndex = frameIndex;
    }

    @TruffleBoundary
    protected Frame getFrameFrom(InspectedFrame inspectedFrame, FrameAccess access) {
        if (access == FrameAccess.READ_WRITE || access == FrameAccess.MATERIALIZE) {
            if (!TruffleOptions.AOT && JDKAccessor.isVirtualThread(Thread.currentThread())) {
                // We throw even if the frame is not virtual to have a deterministic error
                throw new UnsupportedOperationException(
                                "OptimizedFrameInstance#getFrame(READ_WRITE|MATERIALIZE) is not yet supported on virtual threads on HotSpot, " +
                                                "use the default runtime or native mode or avoid using the guest code leading to caller frames access in write/materialize mode.");
            }

            if (inspectedFrame.isVirtual(frameIndex)) {
                final OptimizedCallTarget callTarget = (OptimizedCallTarget) getCallTarget();
                if (callTarget.engine.traceDeoptimizeFrame) {
                    OptimizedTruffleRuntime.StackTraceHelper.logHostAndGuestStacktrace("FrameInstance#getFrame(MATERIALIZE)", callTarget);
                }
                inspectedFrame.materializeVirtualObjects(false);
            }
        }
        Frame frame = (Frame) inspectedFrame.getLocal(frameIndex);
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
        return callTargetFrame.isVirtual(frameIndex);
    }

    private CompilationState getCompilationState() {
        if (frameIndex == CALL_ROOT_FRAME_INDEX) {
            return CompilationState.INTERPRETED;
        }
        return (CompilationState) callTargetFrame.getLocal(OPTIMIZATION_TIER_FRAME_INDEX);
    }

    @TruffleBoundary
    @Override
    public int getCompilationTier() {
        return getCompilationState().getTier();
    }

    @TruffleBoundary
    @Override
    public boolean isCompilationRoot() {
        return getCompilationState().isCompilationRoot();
    }

    @TruffleBoundary
    @Override
    public CallTarget getCallTarget() {
        if (frameIndex == CALL_ROOT_FRAME_INDEX) {
            return ((RootNode) callTargetFrame.getLocal(CALL_ROOT_ROOT_INDEX)).getCallTarget();
        }
        return (CallTarget) callTargetFrame.getLocal(CALL_TARGET_INDEX);
    }

    @TruffleBoundary
    @Override
    public final Node getCallNode() {
        if (callNodeFrame == null) {
            return null;
        }
        Object location = callNodeFrame.getLocal(CALL_NODE_NOTIFY_INDEX);
        return location instanceof Node ? (Node) location : null;
    }

    @TruffleBoundary
    @Override
    public int getBytecodeIndex() {
        if (bytecodeIndex != -1) {
            return bytecodeIndex;
        }
        return FrameInstance.super.getBytecodeIndex();
    }

    int getBytecodeIndex(Frame instrumentableFrame) {
        if (bytecodeIndex != -1) {
            return bytecodeIndex;
        }
        RootCallTarget target = (RootCallTarget) getCallTarget();
        RootNode root = target.getRootNode();
        return OptimizedRuntimeAccessor.NODES.findBytecodeIndex(root, getCallNode(), instrumentableFrame);
    }

    @TruffleBoundary
    @Override
    public Node getInstrumentableCallNode() {
        RootCallTarget target = (RootCallTarget) getCallTarget();
        RootNode root = target.getRootNode();
        Node location = getCallNode();
        Frame instrumentableFrame = OptimizedRuntimeAccessor.NODES.isCaptureFramesForTrace(root, getCompilationTier() > 0) ? getFrame(FrameAccess.READ_ONLY) : null;
        int resolvedBytecodeIndex = bytecodeIndex != -1 ? bytecodeIndex : OptimizedRuntimeAccessor.NODES.findBytecodeIndex(root, location, instrumentableFrame);
        return OptimizedRuntimeAccessor.NODES.findInstrumentableCallNode(root, location, instrumentableFrame, resolvedBytecodeIndex);
    }

}
