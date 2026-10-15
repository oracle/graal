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
package com.oracle.svm.interpreter.ristretto;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.DeoptimizedFrame.DeoptTargetTier;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.interpreter.InterpreterFrameSourceInfo;
import com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl;
import com.oracle.svm.interpreter.InterpreterFrame;
import com.oracle.svm.interpreter.RistrettoInterpreterSupport;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.profile.MethodProfile;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoDeoptimizationSupport;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoDeoptimizedInterpreterFrame;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoInstalledCode;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoDiagnostics;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoProfileSupport;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.JavaKind;

/** Implements the optional Ristretto services consumed by the base interpreter. */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
final class RistrettoInterpreterSupportImpl implements RistrettoInterpreterSupport {
    @Override
    @AlwaysInline("Keep the compilation-disabled interpreter entry call-free")
    public MethodProfile profileMethodEntry(InterpreterResolvedJavaMethod method) {
        return RistrettoProfileSupport.profileMethodEntry(method);
    }

    @Override
    @AlwaysInline("Keep startup OSR configuration checks call-free")
    public boolean useOSR() {
        return RistrettoOptions.useOSR();
    }

    @Override
    @AlwaysInline("Keep the reduced OSR backedge fast path in bytecode-handler stubs")
    public void tryOSR(InterpreterResolvedJavaMethod method, MethodProfile methodProfile, InterpreterFrame frame, int targetBCI, int top) {
        RistrettoOSRSupport.tryOSR(method, methodProfile, frame, targetBCI, top);
    }

    @Override
    @Uninterruptible(reason = "Prevent a safepoint between checking an installed Ristretto entry point and entering it.", callerMustBe = true)
    public CFunctionPointer getInstalledCodeEntryPoint(InterpreterResolvedJavaMethod method) {
        RistrettoMethod rMethod = (RistrettoMethod) method.getRistrettoMethod();
        if (rMethod != null) {
            SubstrateInstalledCodeImpl installedCode = rMethod.installedCode;
            if (installedCode != null && installedCode.getEntryPoint() != 0) {
                return Word.pointer(installedCode.getEntryPoint());
            }
        }
        return Word.nullPointer();
    }

    @Override
    public DeoptimizedFrame createInterpreterDeoptimizedFrame(SubstrateInstalledCode installedCode, Deoptimizer deoptimizer, CodePointer pc, FrameInfoQueryResult frameInfo,
                    CodeInfoQueryResult physicalFrame, boolean eager) {
        if (!(installedCode instanceof RistrettoInstalledCode rCode)) {
            throw VMError.shouldNotReachHere("Must have RistrettoInstalledCode.");
        }
        VMError.guarantee(rCode.getMethod() instanceof RistrettoMethod, "Ristretto installed code must carry a RistrettoMethod");
        if (((RistrettoMethod) rCode.getMethod()).getDeoptTargetTier() != DeoptTargetTier.Interpreter) {
            throw VMError.shouldNotReachHere("Must deopt to interpreter.");
        }
        /*
         * Keep the deopt-only path behind a foldable branch so no-deopt images do not parse the
         * hosted-only Ristretto deoptimization support singleton.
         */
        if (RistrettoOptions.useDeoptimization()) {
            RistrettoDiagnostics.DeoptimizationsTaken.getAndIncrement();
            return RistrettoDeoptimizationSupport.createDeoptimizedFrame(deoptimizer, pc, frameInfo, physicalFrame, eager);
        }
        throw VMError.shouldNotReachHere("Interpreter deoptimization requires deopt support");
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isInterpreterDeoptReturnValueObject(FrameInfoQueryResult frameInfo) {
        if (!RistrettoOptions.useDeoptimization()) {
            return false;
        }
        return RistrettoDeoptimizationSupport.pendingSourceCallReturnKind(frameInfo) == JavaKind.Object;
    }

    /**
     * Bridges the generic deoptimization stub ABI into the Ristretto-specific interpreter handoff.
     *
     * <p>
     * When this hook runs, the raw GP/FP return registers still carry the compiled top-frame
     * result, or the pending exception object if the deopt was taken on an exceptional edge. The
     * Ristretto frame must snapshot that state before the stub tears down the compiled frame and
     * tail-jumps into the typed interpreter entry point.
     *
     * <p>
     * {@code gpReturnValueObject} is a best-effort decoded object value. It can be null even when the
     * raw GP return value denotes an object, so the Ristretto frame keeps the raw register value as
     * the fallback source of truth.
     */
    @Override
    @Uninterruptible(reason = "Invoked from deoptimization stubs while transitioning to interpreter execution.")
    public UnsignedWord continueInterpreterDeoptimization(DeoptimizedFrame frame, Pointer originalStackPointer, UnsignedWord gpReturnValue, UnsignedWord fpReturnValue,
                    boolean hasException, Object gpReturnValueObject) {
        VMError.guarantee(frame instanceof RistrettoDeoptimizedInterpreterFrame, "Unexpected interpreter deoptimized frame implementation");
        return ((RistrettoDeoptimizedInterpreterFrame) frame).continueInterpreterDeoptimization(originalStackPointer, gpReturnValue, fpReturnValue, hasException, gpReturnValueObject);
    }

    @Override
    public FrameSourceInfo getSyntheticMethodFrameInfo(FrameInfoQueryResult frameInfo) {
        if (frameInfo.getSourceClass() != null) {
            return null;
        }
        if (!(frameInfo.getDeoptMethod() instanceof RistrettoMethod rMethod)) {
            return null;
        }
        /*
         * This happens for runtime-compiled Ristretto frames whose encoded frame metadata preserves
         * the method object but does not carry the normal source-class/source-method fields.
         */
        InterpreterResolvedJavaMethod interpretedMethod = rMethod.getInterpreterMethod();
        int flags = FrameSourceInfo.MethodFlags.computeSourceMethodFlags(interpretedMethod.getModifiers(), interpretedMethod.isHidden(), interpretedMethod.isLambdaFormCompiled());
        return InterpreterFrameSourceInfo.forInterpretedMethod(interpretedMethod, frameInfo.getBci(), flags);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isJITXCompEnabled() {
        return RistrettoOptions.JITXComp.getValue();
    }

    @Override
    public SubstrateInstalledCode compileImmediatelyForXComp(InterpreterResolvedJavaMethod method) {
        return RistrettoProfileSupport.compileImmediatelyForXComp(method);
    }
}
