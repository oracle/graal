/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.DeoptimizedFrame.DeoptTargetTier;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.graal.code.PreparedSignature;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.interpreter.InterpreterFrameSourceInfo;
import com.oracle.svm.core.interpreter.InterpreterSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.ristretto.RistrettoOptions;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoDeoptimizationSupport;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoDeoptimizedInterpreterFrame;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoInstalledCode;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoDiagnostics;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.Disallowed;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = Disallowed.class)
public final class InterpreterSupportImpl extends InterpreterSupport {
    private static final int MAX_SYMBOL_LOG_LENGTH = 255;

    private final int bciSlot;
    private final int startBCISlot;
    private final int interpretedMethodSlot;
    private final int interpretedFrameSlot;
    private final int intrinsicMethodSlot;
    private final int intrinsicFrameSlot;
    private final int interpreterJNIDowncallMethodSlot;
    private final ConcurrentHashMap<PreparedSignature, PreparedSignature> preparedSignatures;
    private final ConcurrentHashMap<PreparedSignature, PreparedSignature> preparedJNISignatures;

    InterpreterSupportImpl(int bciSlot, int startBCISlot, int interpretedMethodSlot, int interpretedFrameSlot, int intrinsicMethodSlot, int intrinsicFrameSlot, int interpreterJNIDowncallMethodSlot) {
        this.bciSlot = bciSlot;
        this.startBCISlot = startBCISlot;
        this.interpretedMethodSlot = interpretedMethodSlot;
        this.interpretedFrameSlot = interpretedFrameSlot;
        this.intrinsicMethodSlot = intrinsicMethodSlot;
        this.intrinsicFrameSlot = intrinsicFrameSlot;
        this.interpreterJNIDowncallMethodSlot = interpreterJNIDowncallMethodSlot;
        this.preparedSignatures = new ConcurrentHashMap<>();
        this.preparedJNISignatures = new ConcurrentHashMap<>();
    }

    @Override
    public PreparedSignature prepareSignature(Signature signature, boolean hasReceiver, ResolvedJavaType accessingClass) {
        int count = signature.getParameterCount(false);

        InterpreterStubSection stubSection = ImageSingletons.lookup(InterpreterStubSection.class);
        int[] argumentTypes = new int[count + (hasReceiver ? 1 : 0)];

        // The calling convention is always used with a caller perspective, i.e. sp is unmodified.
        SubstrateCallingConventionType callingConventionType = SubstrateCallingConventionKind.Java.toType(true);
        JavaType thisType = hasReceiver ? accessingClass : null;
        JavaType returnType = signature.getReturnType(accessingClass);
        CallingConvention callingConvention = stubSection.registerConfig.getCallingConvention(callingConventionType, returnType, signature.toParameterTypes(thisType), stubSection.valueKindFactory);

        if (hasReceiver) {
            argumentTypes[0] = PreparedSignature.encodeArgumentType(JavaKind.Object, 0, true);
        }
        for (int i = 0; i < count; i++) {
            int index = i + (hasReceiver ? 1 : 0);
            AllocatableValue allocatableValue = callingConvention.getArgument(index);
            JavaKind argKind = signature.getParameterKind(i);
            int value = 0;
            if (allocatableValue instanceof StackSlot stackSlot) {
                // Both, in the enter- and leavestub we want the "outgoing semantics".
                value = stackSlot.getOffset(0);
            }
            boolean isRegister = !(allocatableValue instanceof StackSlot);
            argumentTypes[index] = PreparedSignature.encodeArgumentType(argKind, value, isRegister);
        }
        return preparedSignature(signature.getReturnKind(), argumentTypes, callingConvention.getStackSize());
    }

    public PreparedSignature preparedSignature(JavaKind returnKind, int[] argumentTypes, int stackSize) {
        return preparedSignatures.computeIfAbsent(new PreparedSignature(returnKind, argumentTypes, stackSize), Function.identity());
    }

    @Override
    public PreparedSignature prepareJNISignature(Signature signature, boolean hasReceiver, ResolvedJavaType accessingClass) {
        InterpreterStubSection stubSection = ImageSingletons.lookup(InterpreterStubSection.class);
        ResolvedJavaType wordType = DynamicHub.fromClass(stubSection.target.wordJavaKind.toJavaClass()).getInterpreterType();

        int parameterCount = signature.getParameterCount(false);
        JavaType[] parameterTypes = new JavaType[parameterCount + 2];
        int[] argumentTypes = new int[parameterTypes.length];
        parameterTypes[0] = wordType;
        parameterTypes[1] = wordType;
        for (int i = 0; i < parameterCount; i++) {
            JavaType parameterType = signature.getParameterType(i, accessingClass);
            parameterTypes[i + 2] = parameterType.getJavaKind() == JavaKind.Object ? wordType : parameterType;
        }

        JavaType returnType = signature.getReturnType(accessingClass);
        if (returnType.getJavaKind() == JavaKind.Object) {
            returnType = wordType;
        }

        CallingConvention callingConvention = stubSection.registerConfig.getCallingConvention(SubstrateCallingConventionKind.Native.toType(true), returnType, parameterTypes,
                        stubSection.valueKindFactory);
        for (int i = 0; i < argumentTypes.length; i++) {
            /*
             * We need to keep using signature.getParameterKind here and not use parameterTypes
             * since leaveInterpreterJNI relies on that to decide what to wrap into a handle.
             */
            AllocatableValue allocatableValue = callingConvention.getArgument(i);
            JavaKind argKind = i < 2 ? stubSection.target.wordJavaKind : signature.getParameterKind(i - 2);
            int value = 0;
            if (allocatableValue instanceof StackSlot stackSlot) {
                value = stackSlot.getOffset(0);
            }
            boolean isRegister = !(allocatableValue instanceof StackSlot);
            argumentTypes[i] = PreparedSignature.encodeArgumentType(argKind, value, isRegister);
        }
        return preparedJNISignature(signature.getReturnKind(), argumentTypes, callingConvention.getStackSize());
    }

    public PreparedSignature preparedJNISignature(JavaKind returnKind, int[] argumentTypes, int stackSize) {
        return preparedJNISignatures.computeIfAbsent(new PreparedSignature(returnKind, argumentTypes, stackSize), Function.identity());
    }

    @Override
    public Class<?> toClass(ResolvedJavaType resolvedJavaType) {
        /*
         * A resolved java type, at runtime, will always have a Java class. Hence, the below will
         * never throw the implicit NPE as checked by getJavaClass().
         */
        return ((InterpreterResolvedJavaType) resolvedJavaType).getJavaClass();
    }

    @Override
    public DeoptimizedFrame createInterpreterDeoptimizedFrame(SubstrateInstalledCode installedCode, Deoptimizer deoptimizer, CodePointer pc, FrameInfoQueryResult frameInfo,
                    CodeInfoQueryResult physicalFrame, boolean eager) {
        /*
         * useRistretto() is a hosted fold. Keep the Ristretto-only deopt path behind it so
         * no-Ristretto images do not retain the deopt support types at runtime.
         */
        if (!SubstrateOptions.useRistretto()) {
            throw VMError.shouldNotReachHere("Interpreter deoptimization requires Ristretto.");
        }
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
        if (!SubstrateOptions.useRistretto()) {
            throw VMError.shouldNotReachHere("Interpreter deoptimization requires Ristretto.");
        }
        VMError.guarantee(frame instanceof RistrettoDeoptimizedInterpreterFrame, "Unexpected interpreter deoptimized frame implementation");
        return ((RistrettoDeoptimizedInterpreterFrame) frame).continueInterpreterDeoptimization(originalStackPointer, gpReturnValue, fpReturnValue, hasException, gpReturnValueObject);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isInterpreterRoot(FrameInfoQueryResult frameInfo) {
        return isInterpreterBytecodeRoot(frameInfo) || isInterpreterIntrinsicRoot(frameInfo) || isInterpreterJNIDowncallRoot(frameInfo);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isInterpreterBytecodeRoot(FrameInfoQueryResult frameInfo) {
        return Interpreter.Root.class == frameInfo.getSourceClass();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isInterpreterIntrinsicRoot(FrameInfoQueryResult frameInfo) {
        return Interpreter.IntrinsicRoot.class == frameInfo.getSourceClass();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isInterpreterJNIDowncallRoot(FrameInfoQueryResult frameInfo) {
        return Interpreter.JNIDowncallRoot.class == frameInfo.getSourceClass();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int readInt(Pointer addr, SignedWord offset) {
        return addr.readInt(offset);
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static <T> T readObject(Pointer addr, SignedWord offset, boolean compressed) {
        Word p = ((Word) addr).add(offset);
        Object obj = ReferenceAccess.singleton().readObjectAt(p, compressed);
        return (T) obj;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private InterpreterResolvedJavaMethod readInterpretedMethod(FrameInfoQueryResult frameInfo, Pointer sp) {
        FrameInfoQueryResult.ValueInfo[] valueInfos = frameInfo.getValueInfos();
        if (interpretedMethodSlot >= valueInfos.length) {
            return null;
        }
        FrameInfoQueryResult.ValueInfo valueInfo = valueInfos[interpretedMethodSlot];
        return readObject(sp, Word.signed(valueInfo.getData()), valueInfo.isCompressedReference());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private InterpreterResolvedJavaMethod readIntrinsicMethod(FrameInfoQueryResult frameInfo, Pointer sp) {
        FrameInfoQueryResult.ValueInfo[] valueInfos = frameInfo.getValueInfos();
        if (intrinsicMethodSlot >= valueInfos.length) {
            return null;
        }
        FrameInfoQueryResult.ValueInfo valueInfo = valueInfos[intrinsicMethodSlot];
        return readObject(sp, Word.signed(valueInfo.getData()), valueInfo.isCompressedReference());
    }

    private InterpreterResolvedJavaMethod readInterpreterJNIDowncallMethod(FrameInfoQueryResult frameInfo, Pointer sp) {
        FrameInfoQueryResult.ValueInfo[] valueInfos = frameInfo.getValueInfos();
        if (interpreterJNIDowncallMethodSlot >= valueInfos.length) {
            return null;
        }
        FrameInfoQueryResult.ValueInfo valueInfo = valueInfos[interpreterJNIDowncallMethodSlot];
        return readObject(sp, Word.signed(valueInfo.getData()), valueInfo.isCompressedReference());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int readBCISlot(FrameInfoQueryResult frameInfo, Pointer sp, int slot) {
        FrameInfoQueryResult.ValueInfo[] valueInfos = frameInfo.getValueInfos();
        if (slot >= valueInfos.length) {
            return BytecodeFrame.UNKNOWN_BCI;
        }
        FrameInfoQueryResult.ValueInfo valueInfo = valueInfos[slot];
        return readInt(sp, Word.signed(valueInfo.getData()));
    }

    /**
     * Reads the bytecode index for an interpreter root frame during stack walking.
     *
     * <p>
     * {@code Interpreter.Root.executeBodyFromBCI(...)} writes two BCI-like locals into the root
     * frame:
     *
     * <pre>
     * startBCI = entry bytecode where this root started
     * curBCI   = bytecode currently being executed
     * </pre>
     *
     * Normal execution should publish {@code curBCI}. A narrow reporting-only window exists after
     * the root frame has been created but before that slot has been written. In that window
     * {@code startBCI} still names the same bytecode entry point, so stack walking falls back to it
     * instead of reporting {@link BytecodeFrame#UNKNOWN_BCI}. If neither slot is available, keep
     * the source location unknown rather than inventing a synthetic {@code 0} BCI.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private int readBCI(FrameInfoQueryResult frameInfo, Pointer sp) {
        int bci = readBCISlot(frameInfo, sp, bciSlot);
        if (bci != BytecodeFrame.UNKNOWN_BCI) {
            return bci;
        }

        /*
         * Stack walking can observe executeBodyFromBCI after the root frame exists but before
         * curBCI has been written into it, e.g. on a stack-overflow edge through the interpreter
         * prologue. In that reporting-only window startBCI is still the exact bytecode entry point
         * for the current root frame, so use it instead of propagating UNKNOWN_BCI. This is the
         * narrow workaround that should be revisited in the context of GR-74439.
         */
        return readBCISlot(frameInfo, sp, startBCISlot);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private InterpreterFrame readInterpreterFrame(FrameInfoQueryResult frameInfo, Pointer sp) {
        FrameInfoQueryResult.ValueInfo[] valueInfos = frameInfo.getValueInfos();
        if (interpretedFrameSlot >= valueInfos.length) {
            return null;
        }
        FrameInfoQueryResult.ValueInfo valueInfo = valueInfos[interpretedFrameSlot];
        return readObject(sp, Word.signed(valueInfo.getData()), valueInfo.isCompressedReference());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private InterpreterFrame readIntrinsicFrame(FrameInfoQueryResult frameInfo, Pointer sp) {
        FrameInfoQueryResult.ValueInfo[] valueInfos = frameInfo.getValueInfos();
        if (intrinsicFrameSlot >= valueInfos.length) {
            return null;
        }
        FrameInfoQueryResult.ValueInfo valueInfo = valueInfos[intrinsicFrameSlot];
        return readObject(sp, Word.signed(valueInfo.getData()), valueInfo.isCompressedReference());
    }

    @Override
    public FrameSourceInfo getInterpretedMethodFrameInfo(FrameInfoQueryResult frameInfo, Pointer sp) {
        if (isInterpreterBytecodeRoot(frameInfo)) {
            InterpreterResolvedJavaMethod interpretedMethod = readInterpretedMethod(frameInfo, sp);
            int bci = readBCI(frameInfo, sp);
            InterpreterFrame interpreterFrame = readInterpreterFrame(frameInfo, sp);
            return createInterpretedMethodFrameInfo(frameInfo, interpretedMethod, bci, interpreterFrame);
        }
        if (isInterpreterIntrinsicRoot(frameInfo)) {
            InterpreterResolvedJavaMethod intrinsicMethod = readIntrinsicMethod(frameInfo, sp);
            InterpreterFrame interpreterFrame = readIntrinsicFrame(frameInfo, sp);
            return createIntrinsicMethodFrameInfo(frameInfo, intrinsicMethod, interpreterFrame);
        }
        if (isInterpreterJNIDowncallRoot(frameInfo)) {
            InterpreterResolvedJavaMethod nativeMethod = readInterpreterJNIDowncallMethod(frameInfo, sp);
            return createNativeMethodFrameInfo(frameInfo, nativeMethod);
        }
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    public void captureInterpretedMethodFrameInfo(FrameInfoQueryResult frameInfo, Pointer sp, InterpretedFrameData data) {
        data.clear();
        if (isInterpreterBytecodeRoot(frameInfo)) {
            data.setInterpreted(frameInfo, readInterpretedMethod(frameInfo, sp), readBCI(frameInfo, sp), readInterpreterFrame(frameInfo, sp));
            return;
        }
        if (isInterpreterIntrinsicRoot(frameInfo)) {
            data.setIntrinsic(frameInfo, readIntrinsicMethod(frameInfo, sp), readIntrinsicFrame(frameInfo, sp));
            return;
        }
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public FrameSourceInfo getInterpretedMethodFrameInfo(FrameInfoQueryResult frameInfo, InterpretedFrameData data) {
        VMError.guarantee(data.isFor(frameInfo), "Captured interpreter frame data does not belong to this frame");
        FrameSourceInfo sourceInfo;
        if (data.isIntrinsic()) {
            sourceInfo = createIntrinsicMethodFrameInfo(frameInfo, (InterpreterResolvedJavaMethod) data.getInterpretedMethod(), (InterpreterFrame) data.getInterpreterFrame());
        } else {
            sourceInfo = createInterpretedMethodFrameInfo(frameInfo, (InterpreterResolvedJavaMethod) data.getInterpretedMethod(), data.getBCI(), (InterpreterFrame) data.getInterpreterFrame());
        }
        if (sourceInfo == null) {
            data.clear();
        }
        return sourceInfo;
    }

    private static FrameSourceInfo createInterpretedMethodFrameInfo(FrameInfoQueryResult frameInfo, InterpreterResolvedJavaMethod interpretedMethod, int bci, InterpreterFrame interpreterFrame) {
        if (interpretedMethod == null || interpreterFrame == null || bci == BytecodeFrame.UNKNOWN_BCI) {
            StringBuilder sb = new StringBuilder("Failed to retrieve interpreter frame data (");
            if (interpretedMethod == null) {
                sb.append("no method;");
            }
            if (interpreterFrame == null) {
                sb.append("no frame;");
            }
            if (bci == BytecodeFrame.UNKNOWN_BCI) {
                sb.append("no bci;");
            }
            sb.append(") at ").append(frameInfo.getSourceReference());
            VMError.shouldNotReachHere(sb.toString());
        }
        if (interpreterFrame.isHiddenFromStackWalking()) {
            /*
             * A compiled OSR continuation leaves its replaced interpreter activation on the
             * physical stack. While that compiled continuation is the live logical frame, hide the
             * stale interpreter activation from source-level Java stack walkers.
             */
            return null;
        }
        InterpreterFrameSourceInfo stackTraceCallerInfo = interpreterFrame.getStackTraceCallerInfo();
        int flags = FrameSourceInfo.MethodFlags.computeSourceMethodFlags(interpretedMethod.getModifiers(), interpretedMethod.isHidden());
        return InterpreterFrameSourceInfo.forInterpretedMethod(interpretedMethod, bci, flags, interpreterFrame, stackTraceCallerInfo);
    }

    private static FrameSourceInfo createIntrinsicMethodFrameInfo(FrameInfoQueryResult frameInfo, InterpreterResolvedJavaMethod intrinsicMethod, InterpreterFrame interpreterFrame) {
        if (intrinsicMethod == null || interpreterFrame == null) {
            StringBuilder sb = new StringBuilder("Failed to retrieve interpreter intrinsic frame data (");
            if (intrinsicMethod == null) {
                sb.append("no intrinsic method;");
            }
            if (interpreterFrame == null) {
                sb.append("no frame;");
            }
            sb.append(") at ").append(frameInfo.getSourceReference());
            VMError.shouldNotReachHere(sb.toString());
        }
        int flags = FrameSourceInfo.MethodFlags.computeSourceMethodFlags(intrinsicMethod.getModifiers(), intrinsicMethod.isHidden());
        return InterpreterFrameSourceInfo.forNativeMethod(intrinsicMethod, interpreterFrame, flags);
    }

    private static FrameSourceInfo createNativeMethodFrameInfo(FrameInfoQueryResult frameInfo, InterpreterResolvedJavaMethod nativeMethod) {
        if (nativeMethod == null) {
            VMError.shouldNotReachHere("Failed to retrieve interpreter JNI downcall method at " + frameInfo.getSourceReference());
        }
        return InterpreterFrameSourceInfo.forNativeMethod(nativeMethod, null, frameInfo.getSourceMethodFlags());
    }

    @Override
    public FrameSourceInfo getSyntheticMethodFrameInfo(FrameInfoQueryResult frameInfo) {
        if (!SubstrateOptions.useRistretto() || frameInfo.getSourceClass() != null) {
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
        int flags = FrameSourceInfo.MethodFlags.computeSourceMethodFlags(interpretedMethod.getModifiers(), interpretedMethod.isHidden());
        return InterpreterFrameSourceInfo.forInterpretedMethod(interpretedMethod, frameInfo.getBci(), flags);
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Used for crash log")
    public void logInterpreterFrame(Log log, FrameInfoQueryResult frameInfo, Pointer sp) {
        if (isInterpreterIntrinsicRoot(frameInfo)) {
            logInterpreterIntrinsicFrame(log, frameInfo, sp);
        } else if (isInterpreterBytecodeRoot(frameInfo)) {
            logInterpreterBytecodeFrame(log, frameInfo, sp);
        } else {
            throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    private void logInterpreterBytecodeFrame(Log log, FrameInfoQueryResult frameInfo, Pointer sp) {
        if (!frameInfo.hasLocalValueInfo()) {
            log.string("  missing local value info (bytecode)");
            return;
        }
        InterpreterResolvedJavaMethod interpretedMethod = readInterpretedMethod(frameInfo, sp);
        if (interpretedMethod == null) {
            log.string("  no interpreter method (bytecode)");
            return;
        }
        int bci = readBCI(frameInfo, sp);
        logInterpreterMethod(log, interpretedMethod, bci);
    }

    private void logInterpreterIntrinsicFrame(Log log, FrameInfoQueryResult frameInfo, Pointer sp) {
        if (!frameInfo.hasLocalValueInfo()) {
            log.string("  missing local value info (intrinsic)");
            return;
        }
        InterpreterResolvedJavaMethod intrinsicMethod = readIntrinsicMethod(frameInfo, sp);
        if (intrinsicMethod == null) {
            log.string("  no interpreter method (intrinsic)");
            return;
        }
        logInterpreterMethod(log, intrinsicMethod, -1);
    }

    private static void logInterpreterMethod(Log log, InterpreterResolvedJavaMethod interpretedMethod, int bci) {
        String sourceHolderName = interpretedMethod.getDeclaringClass().getJavaClass().getName();
        Symbol<Name> sourceMethodName = interpretedMethod.getSymbolicName();
        LineNumberTable lineNumberTable = interpretedMethod.getLineNumberTable();
        int sourceLineNumber = -1; // unknown
        if (lineNumberTable != null && bci >= 0) {
            sourceLineNumber = lineNumberTable.getLineNumber(bci);
        }
        log.spaces(2);
        log.string(sourceHolderName);
        log.character('.');
        logSymbol(log, sourceMethodName);
        String sourceFileName = interpretedMethod.getDeclaringClass().getSourceFileName();
        if (sourceFileName == null && sourceLineNumber >= 0) {
            sourceFileName = "Unknown Source";
        }
        if (sourceFileName != null) {
            log.character('(');
            log.string(sourceFileName);
            if (sourceLineNumber >= 0) {
                log.string(":");
                log.signed(sourceLineNumber);
            }
            log.character(')');
        }
        if (bci >= 0) {
            log.spaces(1);
            log.string("@bci ");
            log.signed(bci);
        }
    }

    private static void logSymbol(Log log, ByteSequence byteSequence) {
        int length = Math.min(byteSequence.length(), MAX_SYMBOL_LOG_LENGTH);
        for (int i = 0; i < length; i++) {
            int b = byteSequence.unsignedByteAt(i);
            if (0x20 <= b && b <= 0x7e) {
                // only log printable ascii
                log.character((char) b);
            } else {
                log.character('?');
            }
        }
        if (byteSequence.length() > MAX_SYMBOL_LOG_LENGTH) {
            log.string("...");
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public void buildMethodIdMapping(ResolvedJavaMethod[] encodedMethods) {
        if (InterpreterOptions.DebuggerWithInterpreter.getValue()) {
            assert ImageSingletons.contains(DebuggerSupport.class);
            ImageSingletons.lookup(DebuggerSupport.class).buildMethodIdMapping(encodedMethods);
        }
    }
}
