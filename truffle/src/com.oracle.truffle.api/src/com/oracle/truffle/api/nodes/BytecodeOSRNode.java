/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Interface for Truffle bytecode nodes which can be on-stack replaced (OSR).
 * <p>
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
 * <li>The node should implement either {@link #executeOSR(VirtualFrame, int, Object)} or
 * {@link #executeOSR(VirtualFrame, long, Object)} (see note below).
 * </ol>
 *
 * <p>
 * For performance reasons, the parent frame may be copied into a new frame used for OSR. If this
 * happens, {@link BytecodeOSRNode#copyIntoOSRFrame(VirtualFrame, VirtualFrame, int, Object)} is
 * used to perform the copy, and {@link BytecodeOSRNode#restoreParentFrame} is used to copy the OSR
 * frame contents back into the parent frame after OSR. A node may override these methods; by
 * default, they perform slot-wise copies.
 *
 * <p>
 * A node may also wish to override {@link BytecodeOSRNode#prepareOSR} to perform initialization.
 * This method will be called before compilation, and can be useful to avoid deoptimizing inside
 * compiled code.
 *
 * <p>
 * Starting in 24.2, Bytecode OSR supports {@code long} values for {@code target} dispatch
 * locations. An interpreter can use {@code long} targets by invoking
 * {@link #tryOSR(BytecodeOSRNode, long, Object, Runnable, VirtualFrame)} (instead of
 * {@link #tryOSR(BytecodeOSRNode, int, Object, Runnable, VirtualFrame)}). Refer to the
 * documentation for the {@code long} overload for more details.
 *
 * @since 21.3
 */
public interface BytecodeOSRNode extends NodeInterface {

    /**
     * Entrypoint to invoke this node through OSR. Implementers must override this method (or its
     * {@link #executeOSR(VirtualFrame, long, Object) long overload}). The implementation should
     * execute bytecode starting from the given {@code target} location (e.g., bytecode index).
     *
     * <p>
     * The {@code osrFrame} may be the parent frame, but for performance reasons could also be a new
     * frame. In case a new frame is created, the frame's {@link Frame#getArguments() arguments}
     * will be provided by {@link #storeParentFrameInArguments}.
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
     *            {@link #tryOSR(BytecodeOSRNode, int, Object, Runnable, VirtualFrame)} for more
     *            details.
     * @return the result of execution.
     * @see #executeOSR(VirtualFrame, long, Object)
     * @since 21.3
     */
    @SuppressWarnings("unused")
    default Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
        throw new AbstractMethodError();
    }

    /**
     * Overload of {@link #executeOSR(VirtualFrame, int, Object)} with a {@code long} target. An
     * interpreter that uses {@code long} targets must override this method.
     *
     * @since 24.2
     * @see #tryOSR(BytecodeOSRNode, long, Object, Runnable, VirtualFrame)
     */
    default Object executeOSR(VirtualFrame osrFrame, long target, Object interpreterState) {
        int intTarget = (int) target;
        if (intTarget != target) {
            throw CompilerDirectives.shouldNotReachHere("long target used without implementing long overload of executeOSR");
        }
        return executeOSR(osrFrame, intTarget, interpreterState);
    }

    /**
     * Gets the OSR metadata for this instance.
     * <p>
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
     * <p>
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
     * @param targetMetadata Additional metadata associated with this {@code target} for the default
     *            frame transfer behavior.
     * @since 22.2
     * @see #copyIntoOSRFrame(VirtualFrame, VirtualFrame, long, Object)
     */
    default void copyIntoOSRFrame(VirtualFrame osrFrame, VirtualFrame parentFrame, int target, Object targetMetadata) {
        NodeAccessor.RUNTIME.transferOSRFrame(this, parentFrame, osrFrame, target, targetMetadata);
    }

    /**
     * Overload of {@link #copyIntoOSRFrame(VirtualFrame, VirtualFrame, int, Object)} with a
     * {@code long} target. An interpreter that uses {@code long} targets must override this method.
     *
     * @since 24.2
     * @see #tryOSR(BytecodeOSRNode, long, Object, Runnable, VirtualFrame)
     */
    default void copyIntoOSRFrame(VirtualFrame osrFrame, VirtualFrame parentFrame, long target, Object targetMetadata) {
        int intTarget = (int) target;
        if (intTarget != target) {
            throw CompilerDirectives.shouldNotReachHere("long target used without implementing long overload of copyIntoOSRFrame");
        }
        copyIntoOSRFrame(osrFrame, parentFrame, intTarget, targetMetadata);
    }

    /**
     * Helper method that can be called by implementations of
     * {@link #copyIntoOSRFrame(VirtualFrame, VirtualFrame, long, Object)}. Should not be
     * overridden.
     *
     * @since 24.2
     * @see #tryOSR(BytecodeOSRNode, long, Object, Runnable, VirtualFrame)
     */
    default void transferOSRFrame(VirtualFrame osrFrame, VirtualFrame parentFrame, long target, Object targetMetadata) {
        NodeAccessor.RUNTIME.transferOSRFrame(this, parentFrame, osrFrame, target, targetMetadata);
    }

    /**
     * Restores the contents of the {@code osrFrame} back into the {@code parentFrame} after OSR. By
     * default, performs a slot-wise copy of the frame.
     * <p>
     * Though a bytecode interpreter might not explicitly use {@code parentFrame} after OSR, it is
     * necessary to restore the state into {@code parentFrame} if it may be accessed through
     * instrumentation.
     *
     * <p>
     * NOTE: This method is only used if the Truffle runtime decided to copy the frame using
     * {@link BytecodeOSRNode#copyIntoOSRFrame(VirtualFrame, VirtualFrame, int, Object)}.
     *
     * @param osrFrame the frame which was used for OSR.
     * @param parentFrame the frame which will be used by the parent after returning from OSR.
     * @since 21.3
     */
    default void restoreParentFrame(VirtualFrame osrFrame, VirtualFrame parentFrame) {
        NodeAccessor.RUNTIME.restoreOSRFrame(this, osrFrame, parentFrame);
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
     * <p>
     * If the field is accessed from compiled OSR code, it may trigger a deoptimization in order to
     * initialize the field. Using {@link BytecodeOSRNode#prepareOSR} to initialize the field can
     * prevent this.
     *
     * @param target the target location OSR will execute from (e.g., bytecode index).
     * @since 21.3
     * @see #prepareOSR(long)
     */
    @SuppressWarnings("unused")
    default void prepareOSR(int target) {
        // do nothing
    }

    /**
     * Overload of {@link #prepareOSR(int)} with a {@code long} target. An interpreter that uses
     * {@code long} targets must override this method.
     *
     * @since 24.2
     * @see #tryOSR(BytecodeOSRNode, long, Object, Runnable, VirtualFrame)
     */
    default void prepareOSR(long target) {
        int intTarget = (int) target;
        if (intTarget != target) {
            throw CompilerDirectives.shouldNotReachHere("long target used without implementing long overload of prepareOSR");
        }
        prepareOSR(intTarget);
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
     * @deprecated use {@link #pollOSRBackEdge(BytecodeOSRNode, int)} instead. It is recommended to
     *             batch up polls in the interpreter implementation. Important note:
     *             {@link #pollOSRBackEdge(BytecodeOSRNode)} did implicitly
     *             {@link TruffleSafepoint#poll(Node) poll} Truffle safepoints. The new method is no
     *             longer doing that. Make sure your bytecode interpreter contains a call to
     *             {@link TruffleSafepoint#poll(Node)} on loop back-edges when migrating.
     */
    @Deprecated(since = "25.0")
    static boolean pollOSRBackEdge(BytecodeOSRNode osrNode) {
        if (!CompilerDirectives.inInterpreter()) {
            return false;
        }
        assert BytecodeOSRValidation.validateNode(osrNode);
        return NodeAccessor.RUNTIME.pollBytecodeOSRBackEdge(osrNode);
    }

    /**
     * Reports a back edge, returning whether to try performing OSR.
     *
     * <p>
     * An interpreter must ensure this method returns {@code true} immediately before calling
     * {@link BytecodeOSRNode#tryOSR}. For example:
     *
     * <pre>
     * if (BytecodeOSRNode.pollOSRBackEdge(this, 1)) {
     *   Object osrResult = BytecodeOSRNode.tryOSR(...);
     *   ...
     * }
     * </pre>
     *
     * @param osrNode the node to report a back-edge for.
     * @param loopCountIncrement the number iterations incremented. Value must be > 0.
     * @return whether to try OSR.
     * @since 25.0
     */
    static boolean pollOSRBackEdge(BytecodeOSRNode osrNode, int loopCountIncrement) {
        if (!CompilerDirectives.inInterpreter()) {
            return false;
        }
        assert loopCountIncrement >= 1 : "invalid loop count increment provided";
        assert BytecodeOSRValidation.validateNode(osrNode);
        return NodeAccessor.RUNTIME.pollBytecodeOSRBackEdge(osrNode, loopCountIncrement);
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
     * <p>
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
     * <p>
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
     * @see #tryOSR(BytecodeOSRNode, long, Object, Runnable, VirtualFrame)
     */
    static Object tryOSR(BytecodeOSRNode osrNode, int target, Object interpreterState, Runnable beforeTransfer, VirtualFrame parentFrame) {
        CompilerAsserts.neverPartOfCompilation();
        return NodeAccessor.RUNTIME.tryBytecodeOSR(osrNode, target, interpreterState, beforeTransfer, parentFrame);
    }

    /**
     * Overload of {@link #tryOSR(BytecodeOSRNode, int, Object, Runnable, VirtualFrame)} with a
     * {@code long} target. An interpreter that uses {@code long} targets should call this method.
     * <p>
     * If an interpreter uses a {@code long} representation, it <strong>must</strong> override the
     * hooks that define {@code long} overloads, namely
     * {@link #executeOSR(VirtualFrame, long, Object)},
     * {@link #copyIntoOSRFrame(VirtualFrame, VirtualFrame, long, Object)}, and
     * {@link #prepareOSR(long)}. Overriding is necessary because the default {@code long} overloads
     * call their {@code int} overloads for backwards compatibility, and these calls will fail for
     * {@code long}-sized targets.
     *
     * @since 24.2
     * @see #tryOSR(BytecodeOSRNode, int, Object, Runnable, VirtualFrame)
     * @see #executeOSR(VirtualFrame, long, Object)
     * @see #copyIntoOSRFrame(VirtualFrame, VirtualFrame, long, Object)
     * @see #prepareOSR(long)
     */
    static Object tryOSR(BytecodeOSRNode osrNode, long target, Object interpreterState, Runnable beforeTransfer, VirtualFrame parentFrame) {
        CompilerAsserts.neverPartOfCompilation();
        return NodeAccessor.RUNTIME.tryBytecodeOSR(osrNode, target, interpreterState, beforeTransfer, parentFrame);
    }

    /**
     * Produce the arguments that will be used to perform the call to the new OSR root. It will
     * become the arguments array of the frame passed into {@link #executeOSR} in case a new frame
     * is generated for the call for performance reasons. The contents are up to language, Truffle
     * only requires that a subsequent call to {@link #restoreParentFrame} with the arguments array
     * will return the same object as was passed into the {@code parentFrame} argument. By default,
     * this method creates a new one-element array, which discards the original frame arguments.
     * Override this method to be able to preserve a subset of the original frame arguments. It is
     * permitted to modify arguments array of {@code parentFrame} and return it. This is called only
     * in the interpreter, therefore the frame is not virtual and it is safe to store it into the
     * arguments array.
     *
     * @param parentFrame the frame object to be stored in the resulting arguments array
     * @return arguments array containing {@code parentFrame}
     * @since 22.2
     */
    default Object[] storeParentFrameInArguments(VirtualFrame parentFrame) {
        CompilerAsserts.neverPartOfCompilation();
        return new Object[]{parentFrame};
    }

    /**
     * Return the parent frame that was stored in an arguments array by a previous call to
     * {@link #storeParentFrameInArguments}.
     *
     * @param arguments frame arguments originally produced by {@link #storeParentFrameInArguments}.
     * @return stored parent frame
     * @since 22.2
     */
    default Frame restoreParentFrameFromArguments(Object[] arguments) {
        return (Frame) arguments[0];
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
