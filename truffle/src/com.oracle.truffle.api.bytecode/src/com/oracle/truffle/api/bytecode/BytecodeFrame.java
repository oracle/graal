/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

import java.util.Arrays;
import java.util.Objects;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;

/**
 * Represents a captured Bytecode DSL frame, including the location metadata needed to access the
 * data in the frame.
 * <p>
 * {@link BytecodeFrame} is intended for use cases where the frame escapes or outlives the root node
 * invocation. Prefer using a built-in operation or {@link LocalAccessor} to access the frame
 * whenever possible.
 * <p>
 * There are a few ways to capture the frame:
 * <ul>
 * <li>{@link BytecodeNode#createCopiedFrame} captures a copy.</li>
 * <li>{@link BytecodeNode#createMaterializedFrame} captures the original frame.</li>
 * <li>{@link BytecodeFrame#get(FrameInstance, FrameAccess)} captures a frame from a
 * {@link FrameInstance}. It captures the original frame if {@link FrameAccess#READ_WRITE} or
 * {@link FrameAccess#MATERIALIZE} is requested. Otherwise, it captures either the original frame or
 * a copy.</li>
 * <li>{@link BytecodeFrame#get(TruffleStackTraceElement)} captures a frame from a
 * {@link TruffleStackTraceElement}. It captures either the original frame or a copy.</li>
 * <li>{@link BytecodeFrame#getNonVirtual(FrameInstance)} captures the original frame from a
 * {@link FrameInstance}, if it is non-virtual.</li>
 * <li>{@link BytecodeFrame#getNonVirtual(TruffleStackTraceElement)} captures the original frame
 * from a {@link TruffleStackTraceElement}, if it is non-virtual and available in the stack
 * trace.</li>
 * </ul>
 * Copied frames do not observe updates made to the original frame.
 * <p>
 * Note: if the interpreter uses {@link GenerateBytecode#enableBlockScoping block scoping}, any
 * non-copied {@link BytecodeFrame} is only valid until the interpreter continues execution. The
 * frame <strong>must not</strong> be used after this point; doing so can cause undefined behaviour.
 * If you need to access the frame after execution continues, you should capture a copy or
 * explicitly {@link #copy()} the captured bytecode frame. This restriction also applies to frames
 * created by methods like {@link BytecodeFrame#get(TruffleStackTraceElement)}, which do not specify
 * whether they capture the original frame or a copy.
 *
 * @since 25.1
 */
public final class BytecodeFrame {
    private final Frame frame;
    private final BytecodeNode bytecode;
    private final int bytecodeIndex;

    BytecodeFrame(Frame frame, BytecodeNode bytecode, int bytecodeIndex) {
        assert frame.getFrameDescriptor() == bytecode.getRootNode().getFrameDescriptor();
        this.frame = Objects.requireNonNull(frame);
        this.bytecode = bytecode;
        this.bytecodeIndex = bytecodeIndex;
    }

    /**
     * Returns a copy of this frame. This method can be used to snapshot the current state of a
     * bytecode frame, in case it may be modified or become invalid in the future.
     *
     * @return a copy of this frame that is always valid and will not observe updates
     * @since 25.1
     */
    public BytecodeFrame copy() {
        return new BytecodeFrame(copyFrame(frame), bytecode, bytecodeIndex);
    }

    /**
     * Returns the bytecode location associated with the captured frame. This location is only valid
     * until the bytecode interpreter resumes execution.
     *
     * @since 25.1
     */
    public BytecodeLocation getLocation() {
        return new BytecodeLocation(bytecode, bytecodeIndex);
    }

    /**
     * Returns the bytecode node associated with the captured frame.
     *
     * @since 25.1
     */
    public BytecodeNode getBytecodeNode() {
        return bytecode;
    }

    /**
     * Returns the bytecode index associated with the captured frame.
     *
     * @since 25.1
     */
    public int getBytecodeIndex() {
        return bytecodeIndex;
    }

    /**
     * Returns the number of live locals in the captured frame.
     *
     * @since 25.1
     */
    public int getLocalCount() {
        return bytecode.getLocalCount(bytecodeIndex);
    }

    /**
     * Returns the value of the local at the given offset. The offset should be between 0 and
     * {@link #getLocalCount()}.
     *
     * @since 25.1
     */
    public Object getLocalValue(int localOffset) {
        return bytecode.getLocalValue(bytecodeIndex, frame, localOffset);
    }

    /**
     * Updates the value of the local at the given offset. The offset should be between 0 and
     * {@link #getLocalCount()}.
     * <p>
     * This method will throw an {@link AssertionError} if the captured frame does not support
     * writes.
     *
     * @since 25.1
     */
    public void setLocalValue(int localOffset, Object value) {
        bytecode.setLocalValue(bytecodeIndex, frame, localOffset, value);
    }

    /**
     * Returns the names associated with the live locals, if provided.
     *
     * @since 25.1
     */
    public Object[] getLocalNames() {
        return bytecode.getLocalNames(bytecodeIndex);
    }

    /**
     * Returns the number of arguments in the captured frame.
     *
     * @since 25.1
     */
    public int getArgumentCount() {
        return frame.getArguments().length;
    }

    /**
     * Returns the value of the argument at the given index. The offset should be between 0 and
     * {@link #getArgumentCount()}.
     *
     * @since 25.1
     */
    public Object getArgument(int argumentIndex) {
        return frame.getArguments()[argumentIndex];
    }

    /**
     * Updates the value of the local at the given offset. The offset should be between 0 and
     * {@link #getArgumentCount()}.
     *
     * @since 25.1
     */
    public void setArgument(int argumentIndex, Object value) {
        frame.getArguments()[argumentIndex] = value;
    }

    /**
     * Returns the {@link FrameDescriptor#getInfo() info} object associated with the frame's
     * descriptor.
     *
     * @since 25.1
     */
    public Object getFrameDescriptorInfo() {
        return frame.getFrameDescriptor().getInfo();
    }

    /**
     * Creates a copy of the given frame.
     */
    static Frame copyFrame(Frame frame) {
        FrameDescriptor fd = frame.getFrameDescriptor();
        Object[] args = frame.getArguments();
        Frame copiedFrame = Truffle.getRuntime().createMaterializedFrame(Arrays.copyOf(args, args.length), fd);
        frame.copyTo(0, copiedFrame, 0, fd.getNumberOfSlots());
        return copiedFrame;
    }

    /**
     * Creates a bytecode frame from the given frame instance.
     *
     * @param frameInstance the frame instance
     * @param access the access mode to use when capturing the frame
     * @return a bytecode frame, or null if the frame instance is missing location info.
     * @since 25.1
     */
    public static BytecodeFrame get(FrameInstance frameInstance, FrameInstance.FrameAccess access) {
        BytecodeNode bytecode = BytecodeNode.get(frameInstance);
        if (bytecode == null) {
            return null;
        }
        Frame frame = bytecode.resolveFrameImpl(frameInstance, access);
        int bytecodeIndex = bytecode.findBytecodeIndex(frameInstance);
        return new BytecodeFrame(frame, bytecode, bytecodeIndex);
    }

    /**
     * Attempts to create a bytecode frame from the given frame instance. Returns null if the
     * corresponding frame is virtual. The frame can be read from, written to, and escaped.
     * <p>
     * This method can be used to probe for a frame that can safely escape without forcing
     * materialization. For example, if a language needs to capture local variables from a stack
     * frame, it's often more efficient to use an existing non-virtual frame rather than create a
     * copy of all variables.
     *
     * @param frameInstance the frame instance
     * @return a bytecode frame or null if the frame is virtual or if the frame instance is missing
     *         location info.
     *
     * @since 25.1
     */
    public static BytecodeFrame getNonVirtual(FrameInstance frameInstance) {
        if (frameInstance.isVirtualFrame()) {
            return null;
        }
        BytecodeNode bytecode = BytecodeNode.get(frameInstance);
        if (bytecode == null) {
            return null;
        }
        /*
         * READ_WRITE returns the original frame. Since it's not virtual it is safe to escape it
         * (either we are in the interpreter, or it is already materialized).
         */
        Frame frame = bytecode.resolveFrameImpl(frameInstance, FrameAccess.READ_WRITE);
        int bytecodeIndex = bytecode.findBytecodeIndex(frameInstance);
        return new BytecodeFrame(frame, bytecode, bytecodeIndex);
    }

    /**
     * Creates a bytecode frame from the given stack trace element.
     * <p>
     * This method will return null unless the interpreter specifies
     * {@link GenerateBytecode#captureFramesForTrace}, which indicates whether frames should be
     * captured.
     *
     * @param element the stack trace element
     * @return a bytecode frame, or null if the frame was not captured or the stack trace element is
     *         missing location information.
     * @since 25.1
     */
    public static BytecodeFrame get(TruffleStackTraceElement element) {
        BytecodeNode bytecode = BytecodeNode.get(element);
        if (bytecode == null) {
            return null;
        }
        Frame frame = bytecode.resolveFrameImpl(element);
        if (frame == null) {
            return null;
        }
        return new BytecodeFrame(frame, bytecode, element.getBytecodeIndex());
    }

    /**
     * Attempts to create a bytecode frame from the given stack trace element. Returns null if the
     * corresponding frame is virtual. The frame can be read from, written to, and escaped.
     * <p>
     * This method can be used to probe for a frame that can safely escape without forcing
     * materialization. For example, if a language needs to capture local variables from a stack
     * frame, it's often more efficient to use an existing non-virtual frame rather than create a
     * copy of all variables.
     *
     * @param element the stack trace element
     * @return a bytecode frame or null if the frame is virtual/unavailable or if the frame instance
     *         is missing location info.
     *
     * @since 25.1
     */
    public static BytecodeFrame getNonVirtual(TruffleStackTraceElement element) {
        BytecodeNode bytecode = BytecodeNode.get(element);
        if (bytecode == null) {
            return null;
        }
        Frame frame = bytecode.resolveNonVirtualFrameImpl(element);
        if (frame == null) {
            return null;
        }
        return new BytecodeFrame(frame, bytecode, element.getBytecodeIndex());
    }
}
