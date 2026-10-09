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
package com.oracle.svm.interpreter;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.profile.MethodProfile;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Communication boundary for the optional Ristretto JIT compiler.
 *
 * <p>
 * This interface provides two different capabilities to the interpreter:
 * <ul>
 * <li>Classic JIT compilation, through
 *     {@linkplain #profileMethodEntry(InterpreterResolvedJavaMethod) method-entry profiling} and
 *     {@linkplain #getInstalledCodeEntryPoint(InterpreterResolvedJavaMethod) compiled code
 *     entry-point querying}</li>
 * <li>On-stack replacement (OSR), through
 *     {@linkplain #tryOSR(InterpreterResolvedJavaMethod, MethodProfile, InterpreterFrame, int, int) interpreter back-edge profiling}</li>
 * </ul>
 *
 * <p>
 * Additionally, this interface is also consumed by the deoptimizer, as it provides:
 * <ul>
 * <li>{@linkplain #createInterpreterDeoptimizedFrame(SubstrateInstalledCode, Deoptimizer, CodePointer,
 *     FrameInfoQueryResult, CodeInfoQueryResult, boolean) frame reconstruction capabilities}</li>
 * <li>{@linkplain #continueInterpreterDeoptimization(DeoptimizedFrame, Pointer, UnsignedWord,
 *     UnsignedWord, boolean, Object) resuming a deoptimized frame back to the interpreter.}</li>
 * </ul>
 *
 * <p>
 * Additionally, this interface provides Stack Walking with a way to obtain source information from a compiled
 * frame which does not carry normal source data ({@link #getSyntheticMethodFrameInfo(FrameInfoQueryResult)}).
 *
 * <p>
 * Lastly, this interface also provides handling of the {@code '-Xcomp'} flag for Ristretto JIT compilation.
 */
public interface RistrettoInterpreterSupport {

    @Fold
    static RistrettoInterpreterSupport singleton() {
        return ImageSingletons.lookup(RistrettoInterpreterSupport.class);
    }

    /**
     * Profiles an interpreted method entry and may request compilation when the method becomes
     * hot.
     *
     * @param method the interpreter-resolved method being entered
     * @return the method profile, or {@code null} when unavailable
     */
    MethodProfile profileMethodEntry(InterpreterResolvedJavaMethod method);

    /**
     * Returns the entry point of compiled code installed for {@code method}, if available.
     *
     * @param method the interpreter-resolved method whose installed code is queried
     * @return the compiled entry point, or a null function pointer
     */
    CFunctionPointer getInstalledCodeEntryPoint(InterpreterResolvedJavaMethod method);

    /**
     * Returns whether the startup compilation options enable OSR. An interpreter activation must
     * additionally have a method profile and allow leaving the interpreter before attempting OSR.
     */
    boolean useOSR();

    /**
     * Performs back-edge profiling and may divert execution to OSR-compiled code. The caller must
     * establish that OSR is enabled for this activation and supply its non-null method profile.
     * Returns normally when execution should remain interpreted; otherwise, transfers the compiled
     * result or exception to the interpreter entry boundary through an internal control-flow marker.
     *
     * @param method        the method containing the backward branch
     * @param methodProfile the profile associated with the current interpreter activation
     * @param frame         the active interpreter frame
     * @param targetBCI     the bytecode index reached by the backward branch
     * @param top           the current operand-stack top for the frame
     */
    void tryOSR(InterpreterResolvedJavaMethod method, MethodProfile methodProfile, InterpreterFrame frame, int targetBCI, int top);

    /**
     * Creates a deoptimized frame for resuming compiled execution in the interpreter.
     *
     * @param installedCode the Ristretto code being deoptimized
     * @param deoptimizer   the deoptimizer performing the transition
     * @param pc            the program counter at which deoptimization was requested
     * @param frameInfo     metadata for the source frame
     * @param physicalFrame metadata for the compiled frame
     * @param eager         whether the deoptimization is eager
     * @return a frame representation for interpreter execution
     */
    DeoptimizedFrame createInterpreterDeoptimizedFrame(SubstrateInstalledCode installedCode, Deoptimizer deoptimizer, CodePointer pc, FrameInfoQueryResult frameInfo,
                    CodeInfoQueryResult physicalFrame, boolean eager);

    /**
     * Completes the handoff from compiled code to the interpreter during deoptimization.
     *
     * @param frame                the deoptimized interpreter frame
     * @param originalStackPointer the stack pointer of the compiled frame
     * @param gpReturnValue        the general-purpose return value
     * @param fpReturnValue        the floating-point return value
     * @param hasException         whether the return represents an exception
     * @param gpReturnValueObject  the decoded object return or exception, when available
     * @return the value required by the deoptimization stub
     */
    UnsignedWord continueInterpreterDeoptimization(DeoptimizedFrame frame, Pointer originalStackPointer, UnsignedWord gpReturnValue, UnsignedWord fpReturnValue,
                    boolean hasException, Object gpReturnValueObject);

    /**
     * Reports whether an interpreter-targeted deoptimization has an object return value.
     *
     * @param frameInfo metadata for the deoptimization source frame
     * @return {@code true} if the pending return value is an object
     */
    boolean isInterpreterDeoptReturnValueObject(FrameInfoQueryResult frameInfo);

    /**
     * Supplies source information for a compiled frame without ordinary source metadata.
     *
     * @param frameInfo metadata for the frame being inspected
     * @return reconstructed source information, or {@code null} when none is applicable
     */
    FrameSourceInfo getSyntheticMethodFrameInfo(FrameInfoQueryResult frameInfo);

    /**
     * Returns whether the {@code -Xcomp} mode is enabled for Ristretto.
     *
     * @return {@code true} if {@code -Xcomp} is enabled, otherwise {@code false}
     */
    boolean isJITXCompEnabled();

    /**
     * Attempts to compile a runtime-loaded bytecode method before its first interpreter entry when
     * {@code -Xcomp} is enabled.
     *
     * @param method the runtime-loaded bytecode method to compile
     * @return the resulting installed code, or {@code null} if the method is not compiled
     */
    SubstrateInstalledCode compileImmediatelyForXComp(InterpreterResolvedJavaMethod method);
}
