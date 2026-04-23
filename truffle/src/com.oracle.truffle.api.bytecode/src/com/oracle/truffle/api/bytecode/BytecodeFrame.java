/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Function;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Represents a captured Bytecode DSL frame, including the location metadata needed to access the
 * data in the frame.
 * <p>
 * {@link BytecodeFrame} is intended for use cases where the frame escapes or outlives the root node
 * invocation. Prefer using a built-in operation or {@link LocalAccessor} to access the frame
 * whenever possible.
 * <p>
 * {@link BytecodeFrame} should be used instead of {@link TruffleStackTraceElement} or
 * {@link FrameInstance} to access frame data because the frame captured in these abstractions is
 * not always the actual frame used in execution (e.g., it may be different for a resumed
 * continuation).
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
        this.bytecode = Objects.requireNonNull(bytecode);
        this.bytecodeIndex = bytecodeIndex;
        assert bytecode.validateBytecodeIndex(bytecodeIndex);
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
        assert bytecodeIndex != -1;
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
        assert bytecodeIndex != -1;
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
     * @throws IllegalArgumentException if the element has an invalid bytecode index.
     *
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
        int bytecodeIndex = element.getBytecodeIndex();
        if (bytecodeIndex < 0) {
            throw new IllegalArgumentException("Bytecode index of TruffleStackTraceElement cannot be negative.");
        }
        return new BytecodeFrame(frame, bytecode, bytecodeIndex);
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
     * @throws IllegalArgumentException if the element has an invalid bytecode index.
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
        int bytecodeIndex = element.getBytecodeIndex();
        if (bytecodeIndex < 0) {
            throw new IllegalArgumentException("Bytecode index of TruffleStackTraceElement cannot be negative.");
        }
        return new BytecodeFrame(frame, bytecode, bytecodeIndex);
    }

    /**
     * Returns the current top stack activation as a {@link BytecodeFrame bytecode frame}.
     * <p>
     * The top frame's location is derived using {@code topLocation} and {@code topBytecodeIndex}.
     * If {@code topBytecodeIndex} is {@code -1}, the bytecode index is resolved from
     * {@code topLocation} and the resolved top {@link Frame}.
     *
     * @param access the access mode used when resolving the top frame
     * @param topLocation a node in the current top bytecode activation
     * @param topBytecodeIndex the bytecode index of the current top bytecode activation, or
     *            {@code -1} to resolve it from {@code topLocation} and the resolved frame
     * @return the current top bytecode frame
     * @throws IllegalArgumentException if {@code topLocation} and {@code topBytecodeIndex} do not
     *             identify a valid location for the top frame, or if the top frame is not a
     *             bytecode frame.
     * @since 25.1
     */
    public static BytecodeFrame getTop(FrameInstance.FrameAccess access, Node topLocation, int topBytecodeIndex) {
        return iterateBytecodeFrames(frame -> frame, access, topLocation, topBytecodeIndex, 0);
    }

    /**
     * Iterates the current stack as {@link BytecodeFrame bytecode frames}, starting with the
     * current top activation.
     * <p>
     * This method uses {@link com.oracle.truffle.api.TruffleRuntime#iterateFrames} to perform a
     * stack walk, constructing each {@link BytecodeFrame} instance from an underlying
     * {@link FrameInstance}. Frame instances that do not correspond to bytecode frames are ignored.
     * <p>
     * The visitor is invoked for each available bytecode frame until it returns a non-{@code null}
     * value, which is then returned from this method. If the visitor always returns {@code null},
     * this method returns {@code null}.
     * <p>
     * The stack walk begins with the current (top) activation. The top frame's location is derived
     * using {@code topLocation} and {@code topBytecodeIndex}. These parameters are necessary
     * because the {@link FrameInstance} for the top activation does not necessarily provide
     * location information.
     * <p>
     * If {@code skipBytecodeFrames > 0}, the top frame is skipped and {@code topLocation} and
     * {@code topBytecodeIndex} are ignored. Otherwise, {@code topLocation} must identify a node in
     * the current top bytecode activation, and {@code topBytecodeIndex} must be a valid bytecode
     * index or {@code -1}. When {@code topBytecodeIndex} is {@code -1}, the bytecode index of the
     * top frame is computed using {@code topLocation} and the resolved top {@link Frame}.
     *
     * @param visitor the visitor applied to each bytecode frame
     * @param access the access mode used when resolving frames from runtime stack frames
     * @param topLocation a node in the current top bytecode activation; ignored if
     *            {@code skipBytecodeFrames > 0}
     * @param topBytecodeIndex the bytecode index of the current top bytecode activation, or
     *            {@code -1} to resolve it from {@code topLocation} and the resolved frame; ignored
     *            if {@code skipBytecodeFrames > 0}
     * @param skipBytecodeFrames the number of bytecode frames to skip before invoking the visitor
     * @return the first non-{@code null} value returned by {@code visitor}, or {@code null} if no
     *         visited frame produces one
     * @throws IllegalArgumentException if the top frame is not skipped and {@code topLocation} and
     *             {@code topBytecodeIndex} do not identify a valid location for the top frame, or
     *             if the top frame is not a bytecode frame.
     * @since 25.1
     */
    public static <T> T iterateBytecodeFrames(Function<BytecodeFrame, T> visitor, FrameInstance.FrameAccess access, Node topLocation, int topBytecodeIndex,
                    @SuppressWarnings("unused") int skipBytecodeFrames) {
        if (skipBytecodeFrames < 0) {
            throw new IllegalArgumentException("The skipBytecodeFrames parameter must be >= 0.");
        }

        return Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<>() {
            int remainingSkips = skipBytecodeFrames;
            boolean first = true;

            @Override
            public T visitFrame(FrameInstance frameInstance) {
                if (first) {
                    first = false;
                    if (remainingSkips > 0) {
                        remainingSkips--;
                        return null;
                    }
                    return visitor.apply(createTopFrame(frameInstance, access, topLocation, topBytecodeIndex));
                }

                BytecodeFrame frame = BytecodeFrame.get(frameInstance, access);
                if (frame == null) {
                    // not a bytecode frame.
                    return null;
                } else if (remainingSkips > 0) {
                    remainingSkips--;
                    return null;
                }
                return visitor.apply(frame);
            }
        });
    }

    private static BytecodeFrame createTopFrame(FrameInstance frameInstance, FrameAccess access, Node topLocation, int topBytecodeIndex) {
        if (topBytecodeIndex < -1) {
            throw new IllegalArgumentException("The topBytecodeIndex parameter must be >= -1.");
        }
        Objects.requireNonNull(topLocation, "topLocation");
        BytecodeNode bytecode = BytecodeNode.get(topLocation);
        if (bytecode == null) {
            throw new IllegalArgumentException("The topLocation parameter must identify a node in the current top bytecode activation.");
        }
        RootNode frameRootNode = unwrapRootNode(frameInstance);
        if (frameRootNode != bytecode.getRootNode()) {
            throw new IllegalArgumentException(
                            "The top frame activation has a different root node (%s) than the bytecode node resolved from topLocation (%s).".formatted(frameRootNode, bytecode.getRootNode()));
        }
        Frame frame = bytecode.resolveFrameImpl(frameInstance, access);
        int bytecodeIndex = topBytecodeIndex;
        if (bytecodeIndex == -1) {
            bytecodeIndex = bytecode.findBytecodeIndexImpl(frame, topLocation);
            if (bytecodeIndex < 0) {
                throw new IllegalArgumentException("Could not resolve the bytecode index of the current top bytecode activation.");
            }
        }
        return new BytecodeFrame(frame, bytecode, bytecodeIndex);
    }

    private static RootNode unwrapRootNode(FrameInstance frameInstance) {
        if (frameInstance.getCallTarget() instanceof RootCallTarget rct) {
            RootNode root = rct.getRootNode();
            if (root instanceof ContinuationRootNode continuation) {
                return (RootNode) continuation.getSourceRootNode();
            } else {
                return root;
            }
        }
        return null;
    }
}
