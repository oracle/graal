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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Interface for Truffle bytecode nodes which can be on-stack replaced (OSR).
 *
 * There are a few restrictions Bytecode OSR nodes must satisfy in order for OSR to work correctly:
 * <ol>
 * <li>The node must extend {@link Node} or a subclass of {@link Node}.</li>
 * <li>The node must provide storage for the OSR metadata maintained by the runtime using an
 * instance field. The field must be
 * {@link com.oracle.truffle.api.CompilerDirectives.CompilationFinal @CompilationFinal}, and the
 * {@link BytecodeOSRNode#getOSRMetadata} and {@link BytecodeOSRNode#setOSRMetadata} methods must
 * proxy accesses to it.</li>
 * <li>The node should call {@link BytecodeOSRNode#pollOSRBackEdge} and
 * {@link BytecodeOSRNode#tryOSR} as described by their documentation.</li>
 * </ol>
 *
 * <p>
 * For performance reasons, the parent frame may be copied into a new frame used for OSR. If this
 * happens, {@link BytecodeOSRNode#copyIntoOSRFrame} is used to perform the copy, and
 * {@link BytecodeOSRNode#restoreParentFrame} is used to copy the OSR frame contents back into the
 * parent frame after OSR. A node may override these methods; by default, they perform slot-wise
 * copies.
 *
 * <p>
 * A node may also wish to override {@link BytecodeOSRNode#prepareOSR} to perform initialization.
 * This method will be called before compilation, and can be useful to avoid deoptimizing inside
 * compiled code.
 *
 * @since 21.3
 */
public interface BytecodeOSRNode extends NodeInterface {

    /**
     * Entrypoint to invoke this node through OSR. This method should execute from the
     * {@code target} location.
     *
     * <p>
     * The {@code osrFrame} may be the parent frame, but for performance reasons could also be a new
     * frame. The frame's {@link Frame#getArguments() arguments} are undefined and should not be
     * used directly.
     *
     * <p>
     * Typically, a bytecode node's {@link ExecutableNode#execute(VirtualFrame)
     * execute(VirtualFrame)} method already contains a dispatch loop. This loop can be extracted
     * into a separate method which can also be used by this method. For example:
     *
     * <pre>
     * Object execute(VirtualFrame frame) {
     *   return dispatchFromBCI(frame, 0);
     * }
     * Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
     *   return dispatchFromBCI(osrFrame, target);
     * }
     * Object dispatchFromBCI(VirtualFrame frame, int bci) {
     *     // main dispatch loop
     *     while(true) {
     *         switch(instructions[bci]) {
     *             ...
     *         }
     *     }
     * }
     * </pre>
     *
     * @param osrFrame the frame to use for OSR.
     * @param target the target location to execute from (e.g., bytecode index).
     * @param interpreterState other interpreter state used to resume execution. See
     *            {@link BytecodeOSRNode#tryOSR} for more details.
     * @return the result of execution.
     * @since 21.3
     */
    Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState);

    /**
     * Gets the OSR metadata for this instance.
     *
     * The metadata must be stored on a
     * {@link com.oracle.truffle.api.CompilerDirectives.CompilationFinal @CompilationFinal} instance
     * field. Refer to the documentation for this interface for a more complete description.
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
     * field. Refer to the documentation for this interface for a more complete description.
     *
     * @param osrMetadata the OSR metadata.
     * @since 21.3
     */
    void setOSRMetadata(Object osrMetadata);

    /**
     * Copies the contents of the {@code parentFrame} into the {@code osrFrame} used to execute OSR.
     * By default, performs a slot-wise copy of the frame.
     *
     * <p>
     * NOTE: This method is only used if the Truffle runtime decides to copy the frame. OSR may also
     * reuse the parent frame directly.
     *
     * @param osrFrame the frame to use for OSR.
     * @param parentFrame the frame used before performing OSR.
     * @param target the target location OSR will execute from (e.g., bytecode index).
     * @since 21.3
     */
    default void copyIntoOSRFrame(VirtualFrame osrFrame, VirtualFrame parentFrame, int target) {
        NodeAccessor.RUNTIME.transferOSRFrame(this, parentFrame, osrFrame);
    }

    /**
     * Restores the contents of the {@code osrFrame} back into the {@code parentFrame} after OSR. By
     * default, performs a slot-wise copy of the frame.
     *
     * Though a bytecode interpreter might not explicitly use {@code parentFrame} after OSR, it is
     * necessary to restore the state into {@code parentFrame} if it may be accessed through
     * instrumentation.
     *
     * <p>
     * NOTE: This method is only used if the Truffle runtime decided to copy the frame using
     * {@link BytecodeOSRNode#copyIntoOSRFrame}.
     *
     * @param osrFrame the frame which was used for OSR.
     * @param parentFrame the frame which will be used by the parent after returning from OSR.
     * @since 21.3
     */
    default void restoreParentFrame(VirtualFrame osrFrame, VirtualFrame parentFrame) {
        NodeAccessor.RUNTIME.transferOSRFrame(this, osrFrame, parentFrame);
    }

    /**
     * Initialization hook which will be invoked before OSR compilation. This hook can be used to
     * perform any necessary initialization before compilation happens.
     *
     * <p>
     * For example, consider a field which must be initialized in the interpreter:
     *
     * <pre>
     * {@literal @}CompilationFinal Object field;
     * Object getField() {
     *     if (field == null) {
     *         CompilerDirectives.transferToInterpreterAndInvalidate();
     *         field = new Object();
     *     }
     *     return field;
     * }
     * </pre>
     *
     * If the field is accessed from compiled OSR code, it may trigger a deoptimization in order to
     * initialize the field. Using {@link BytecodeOSRNode#prepareOSR} to initialize the field can
     * prevent this.
     *
     * @since 21.3
     * @param target the target location OSR will execute from (e.g., bytecode index).
     */
    default void prepareOSR(int target) {
        // do nothing
    }

    /**
     * Reports a back edge, returning whether to try performing OSR.
     *
     * <p>
     * An interpreter must ensure this method returns {@code true} immediately before calling
     * {@link BytecodeOSRNode#tryOSR}. For example:
     *
     * <pre>
     * if (BytecodeOSRNode.pollOSRBackEdge(this)) {
     *   Object osrResult = BytecodeOSRNode.tryOSR(...);
     *   ...
     * }
     * </pre>
     *
     * @param osrNode the node to report a back-edge for.
     * @return whether to try OSR.
     * @since 21.3
     */
    static boolean pollOSRBackEdge(BytecodeOSRNode osrNode) {
        if (!CompilerDirectives.inInterpreter()) {
            return false;
        }
        assert BytecodeOSRValidation.validateNode(osrNode);
        return NodeAccessor.RUNTIME.pollBytecodeOSRBackEdge(osrNode);

    }

    /**
     * Tries to perform OSR. This method must only be called immediately after a {@code true} result
     * from {@link BytecodeOSRNode#pollOSRBackEdge}.
     *
     * <p>
     * Depending on the Truffle runtime, this method can trigger OSR compilation and then (typically
     * in a subsequent call) transfer to OSR code. If OSR occurs, this method returns the result of
     * OSR execution. The caller of this method can forward the result back to its caller rather
     * than continuing execution from the {@code target}. For example:
     *
     * <pre>
     * if (BytecodeOSRNode.pollOSRBackEdge(this)) {
     *   Object osrResult = BytecodeOSRNode.tryOSR(...);
     *   if (osrResult != null) return osrResult;
     * }
     * </pre>
     *
     * The optional {@code interpreterState} parameter will be forwarded to
     * {@link BytecodeOSRNode#executeOSR} when OSR is performed. It should consist of additional
     * interpreter state (e.g., data pointers) needed to resume execution from {@code target}. The
     * state should be fixed (i.e., final) for the given {@code target}. For example:
     *
     * <pre>
     * // class definition
     * class InterpreterState {
     *     final int dataPtr;
     *     InterpreterState(int dataPtr) { ... }
     * }
     *
     * // call site
     * Object osrResult = BytecodeOSRNode.tryOSR(this, target, new InterpreterState(dataPtr), ...);
     *
     * // executeOSR
     * Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
     *     InterpreterState state = (InterpreterState) interpreterState;
     *     return dispatchFromBCI(osrFrame, target, interpreterState.dataPtr);
     * }
     * </pre>
     *
     * The optional {@code beforeTransfer} callback will be called before transferring control to
     * the OSR target. Since this method may or may not perform a transfer, it is a way to ensure
     * certain actions (e.g., instrumentation events) occur before transferring to OSR code. For
     * example:
     *
     * <pre>
     * // call site
     * Object osrResult = BytecodeNode.tryOSR(this, target, ..., () -> {
     *    instrument.notify(current, target);
     * });
     * </pre>
     *
     * @param osrNode the node to try OSR for.
     * @param target the target location OSR will execute from (e.g., bytecode index).
     * @param interpreterState other interpreter state used to resume execution.
     * @param beforeTransfer a callback invoked before OSR. Can be {@code null}.
     * @param parentFrame frame at the current point of execution.
     * @return the result if OSR was performed, or {@code null} otherwise.
     * @since 21.3
     */
    static Object tryOSR(BytecodeOSRNode osrNode, int target, Object interpreterState, Runnable beforeTransfer, VirtualFrame parentFrame) {
        CompilerAsserts.neverPartOfCompilation();
        return NodeAccessor.RUNTIME.tryBytecodeOSR(osrNode, target, interpreterState, beforeTransfer, parentFrame);
    }
}

final class BytecodeOSRValidation {

    private BytecodeOSRValidation() {
        // no instances
    }

    static boolean validateNode(BytecodeOSRNode node) {
        if (!(node instanceof Node)) {
            throw new ClassCastException(String.format("%s must be of type Node.", node.getClass()));
        }
        Node osrNode = (Node) node;
        RootNode root = osrNode.getRootNode();
        if (root == null) {
            throw new AssertionError(String.format("%s was not adopted but executed.", node.getClass()));
        }
        return true;
    }

}
