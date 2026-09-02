/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.compile;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.deopt.VirtualFrame;
import com.oracle.svm.interpreter.InterpreterFrame;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.nodes.FrameState.StackState;
import jdk.vm.ci.meta.JavaKind;

/**
 * Interpreter-side view of one virtual frame reconstructed from a Ristretto deopt infopoint.
 *
 * <p>
 * {@code currentBci} is the bytecode index reported by the compiled frame state, while
 * {@code targetBci} is the actual resume point once compiled stack semantics have been translated
 * back into interpreter semantics. {@code stackState} preserves the compiler frame-state flavor
 * (`BeforePop`, `AfterPop`, or `Rethrow`) so replay can distinguish normal invoke resumption from
 * exception propagation. {@code compiledReturnKind} is only meaningful for the physical top frame,
 * where a completed invoke result may still be sitting in machine return registers when
 * deoptimization begins.
 */
public final class RistrettoVirtualInterpreterFrame extends VirtualFrame {
    /** Materialized interpreter frame that resumes execution for this virtual frame. */
    private final InterpreterFrame frame;
    /** Ristretto method and its interpreter method represented by this frame. */
    private final RistrettoMethod method;
    /** BCI reported by the compiled frame state that triggered deoptimization. */
    private final int currentBci;
    /**
     * Resume BCI after translating compiler FrameState stack semantics back to interpreter form.
     */
    private final int targetBci;
    /** Compiler stack-state flavor that explains how to interpret {@link #currentBci}. */
    private final StackState stackState;
    /** Number of live operand-stack entries materialized into {@link #frame}. */
    private final int numStack;
    /**
     * Pending machine-level invoke result kind for the physical top frame, or
     * {@link JavaKind#Illegal}.
     */
    private final JavaKind compiledReturnKind;
    /** Next inner virtual frame, or {@code null} when this frame is the innermost callee. */
    private final RistrettoVirtualInterpreterFrame callee;
    /** Next outer virtual frame, linked after reconstruction. */
    private RistrettoVirtualInterpreterFrame caller;

    RistrettoVirtualInterpreterFrame(FrameInfoQueryResult frameInfo, InterpreterFrame frame, RistrettoMethod method,
                    int currentBci, int targetBci, StackState stackState, int numStack, JavaKind compiledReturnKind, RistrettoVirtualInterpreterFrame callee) {
        super(frameInfo);
        this.frame = frame;
        this.method = method;
        this.currentBci = currentBci;
        this.targetBci = targetBci;
        this.stackState = stackState;
        this.callee = callee;
        this.numStack = numStack;
        this.compiledReturnKind = compiledReturnKind;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    boolean hasCallee() {
        return callee != null;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    RistrettoVirtualInterpreterFrame getCallee() {
        return callee;
    }

    public InterpreterFrame getFrame() {
        return frame;
    }

    public InterpreterResolvedJavaMethod getMethod() {
        return method.getInterpreterMethod();
    }

    public RistrettoMethod getRistrettoMethod() {
        return method;
    }

    public int getCurrentBci() {
        return currentBci;
    }

    public int getTargetBci() {
        return targetBci;
    }

    public StackState getStackState() {
        return stackState;
    }

    public boolean isAfterPop() {
        return stackState == StackState.AfterPop;
    }

    public boolean isRethrowException() {
        return stackState == StackState.Rethrow;
    }

    public void setCaller(RistrettoVirtualInterpreterFrame caller) {
        assert this.caller == null;
        this.caller = caller;
    }

    @Override
    public void setCaller(VirtualFrame caller) {
        assert caller instanceof RistrettoVirtualInterpreterFrame;
        setCaller((RistrettoVirtualInterpreterFrame) caller);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public VirtualFrame getCaller() {
        return this.caller;
    }

    public int getNumStack() {
        return numStack;
    }

    /**
     * Returns whether this frame still expects a top-level compiled call result to be injected into
     * the reconstructed interpreter operand stack.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean hasPendingCallResult() {
        return compiledReturnKind != JavaKind.Illegal && compiledReturnKind != JavaKind.Void;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public JavaKind getCompiledReturnKind() {
        return compiledReturnKind;
    }
}
