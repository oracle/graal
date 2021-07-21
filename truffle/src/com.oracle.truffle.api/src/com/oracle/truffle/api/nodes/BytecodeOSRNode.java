/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Interface for Truffle bytecode nodes which can be on-stack replaced (OSR).
 *
 * There are a few restrictions Bytecode OSR nodes must satisfy in order for OSR to work correctly:
 *
 * <ol>
 * <li>The node must extend {@link Node} or a subclass of {@link Node}.</li>
 * <li>The node must provide storage for the OSR metadata maintained by the runtime using an
 * instance field. The field must be
 * {@link com.oracle.truffle.api.CompilerDirectives.CompilationFinal @CompilationFinal}, and the
 * {@link BytecodeOSRNode#getOSRMetadata} and {@link BytecodeOSRNode#setOSRMetadata} methods must
 * proxy accesses to it.</li>
 * <li>{@link Frame Frames} passed to to this node's {@link BytecodeOSRNode#executeOSR} method must
 * be non-materializable.</li>
 * </ol>
 *
 * The node may optionally override {@link BytecodeOSRNode#prepareOSR} to perform any necessary
 * initialization. This method will be called before compilation.
 *
 * @since 21.3
 */
public interface BytecodeOSRNode extends NodeInterface {

    /**
     * Entrypoint to invoke this node through OSR. Typically, this method will:
     * <ul>
     * <li>transfer state from the {@code parentFrame} into the {@code osrFrame} (if necessary)
     * <li>execute this node from the {@code target} location
     * <li>transfer state from the {@code osrFrame} back to the {@code parentFrame} (if necessary)
     * </ul>
     * <p>
     * NOTE: The result of {@link Frame#getArguments()} for {@code osrFrame} is undefined and must
     * not be used. Additionally, since the parent frame could also come from an OSR call (in the
     * situation where an OSR call deoptimizes), the arguments of {@code parentFrame} are also
     * undefined.
     *
     * @param osrFrame the frame to use for OSR.
     * @param parentFrame the frame of the previous invocation (which may itself be an OSR frame).
     * @param target the target location to execute from (e.g., bytecode index).
     * @return the result of execution.
     * @since 21.3
     */
    Object executeOSR(VirtualFrame osrFrame, Frame parentFrame, int target);

    /**
     * Gets the OSR metadata for this instance.
     *
     * The metadata must be stored on a
     * {@link com.oracle.truffle.api.CompilerDirectives.CompilationFinal @CompilationFinal} instance
     * field. Refer to the documentation for {@link BytecodeOSRNode this interface} for a more
     * complete description.
     *
     * @return the OSR metadata.
     * @since 21.3
     */
    Object getOSRMetadata();

    /**
     * Sets the OSR metadata for this instance.
     *
     * The metadata must be stored on a
     * {@link com.oracle.truffle.api.CompilerDirectives.CompilationFinal @CompilationFinal} instance
     * field. Refer to the documentation for {@link BytecodeOSRNode this interface} for a more
     * complete description.
     *
     * @param osrMetadata the OSR metadata.
     * @since 21.3
     */
    void setOSRMetadata(Object osrMetadata);

    /**
     * Optional hook which will be invoked before OSR compilation. This hook can be used to perform
     * any necessary initialization before compilation happens.
     *
     * <p>
     * For example, fields which must be initialized in the interpreter can be initialized this way:
     * 
     * <pre>
     * {@code
     *     &#64;CompilationFinal Object field;
     *     Object getField() {
     *         if (field == null) {
     *             CompilerDirectives.transferToInterpreterAndInvalidate();
     *             field = new Object();
     *         }
     *         return field;
     *     }
     * }
     * </pre>
     * 
     * If the field is accessed from compiled OSR code, it may trigger a deoptimization in order to
     * initialize the field. Using {@link BytecodeOSRNode#prepareOSR} to initialize the field can
     * prevent this.
     */
    default void prepareOSR() {
        // do nothing
    }

    /**
     * Reports a back edge to the target location. This information may be used to trigger on-stack
     * replacement (OSR), if the Truffle runtime supports it.
     *
     * <p>
     * If OSR occurs, this method returns the result of OSR execution. The caller of this method can
     * forward this result back to its caller rather than continuing execution from the
     * {@code target}.
     *
     * @param osrNode the node for which to report a back-edge.
     * @param parentFrame frame at the current point of execution.
     * @param target target location of the jump (e.g., bytecode index).
     * @return the result if OSR was performed, or {@code null} otherwise.
     * @since 21.3
     */
    static Object reportOSRBackEdge(BytecodeOSRNode osrNode, VirtualFrame parentFrame, int target) {
        if (!CompilerDirectives.inInterpreter()) {
            return null;
        }
        return NodeAccessor.RUNTIME.onOSRBackEdge(osrNode, parentFrame, target);
    }

    /**
     * Transfers state from the {@code source} frame into the {@code target} frame. The frames must
     * have the same layout as the frame used to execute the {@code osrNode}.
     * <p>
     * This helper can be used when implementing {@link #executeOSR} to transfer state between OSR
     * and parent frames.
     *
     * <p>
     * NOTE: If a language uses this method to transfer state, the OSR metadata field must be marked
     * {@link com.oracle.truffle.api.CompilerDirectives.CompilationFinal}, since the metadata may be
     * used inside the compiled code to perform the state transfer.
     *
     * @param osrNode the node being on-stack replaced.
     * @param source the frame to transfer state from
     * @param target the frame to transfer state into
     * @throws IllegalArgumentException if either frame has a different descriptor from the frame
     *             used to execute this node.
     * @throws IllegalStateException if a slot in the source frame has not been initialized using
     *             {@link com.oracle.truffle.api.frame.FrameDescriptor#setFrameSlotKind}.
     * @since 21.3
     */
    static void doOSRFrameTransfer(BytecodeOSRNode osrNode, Frame source, Frame target) {
        NodeAccessor.RUNTIME.doOSRFrameTransfer(osrNode, source, target);
    }
}
