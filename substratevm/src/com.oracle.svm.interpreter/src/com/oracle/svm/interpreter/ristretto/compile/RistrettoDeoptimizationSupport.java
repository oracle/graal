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

import static com.oracle.svm.core.FrameAccess.returnAddressSize;
import static com.oracle.svm.core.deopt.Deoptimizer.createRelockObjectData;
import static com.oracle.svm.interpreter.ristretto.compile.InterpreterDeoptEntryPoints.logger;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;

import com.oracle.svm.shared.BuildPhaseProvider;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.deopt.DeoptState;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.interpreter.InterpreterFrameSourceInfo;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.interpreter.CallSiteLink;
import com.oracle.svm.interpreter.Interpreter;
import com.oracle.svm.interpreter.InterpreterFrame;
import com.oracle.svm.interpreter.InterpreterFrameUtil;
import com.oracle.svm.interpreter.InterpreterToVM;
import com.oracle.svm.interpreter.InterpreterUtil;
import com.oracle.svm.interpreter.ResolvedInvokeDynamicConstant;
import com.oracle.svm.interpreter.SuccessfulCallSiteLink;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.Bytecodes;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.Disallowed;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.nodes.FrameState.StackState;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Ristretto equivalent of {@link com.oracle.svm.core.deopt.DeoptimizationSupport} for Ristretto
 * compilations that deoptimize back to the Crema interpreter.
 *
 * High-level flow: {@link Deoptimizer} routes Ristretto frames here, this class rebuilds the
 * interpreter-side frame and invoke metadata needed to resume the exact bytecode state that caused
 * deoptimization, and {@link InterpreterDeoptEntryPoints} consumes the registered entry points to
 * continue execution in the interpreter.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, other = Disallowed.class)
public class RistrettoDeoptimizationSupport {
    /**
     * Call-site layout facts needed while resuming a deoptimized invoke boundary.
     *
     * {@code argumentSlotCount} is interpreted relative to the deopt {@link StackState}:
     * {@code BeforePop} still counts the invoke arguments in {@code callerTop}, while
     * {@code AfterPop} already reflects the post-argument stack shape.
     */
    static final class CallSiteLayout {
        /** Stack-kind of the linked callee result. */
        private final JavaKind returnKind;
        /** Number of caller-frame operand-stack slots consumed by the invoke arguments. */
        private final int argumentSlotCount;

        private CallSiteLayout(JavaKind returnKind, int argumentSlotCount) {
            this.returnKind = returnKind;
            this.argumentSlotCount = argumentSlotCount;
        }

        JavaKind getReturnKind() {
            return returnKind;
        }

        int getArgumentSlotCount() {
            return argumentSlotCount;
        }
    }

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryVoid;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryInt;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryLong;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryFloat;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryDouble;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryObject;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryBoolean;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryByte;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryShort;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryChar;

    @Platforms(Platform.HOSTED_ONLY.class)
    public RistrettoDeoptimizationSupport() {
    }

    @Fold
    static RistrettoDeoptimizationSupport get() {
        return ImageSingletons.lookup(RistrettoDeoptimizationSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void initialize(CFunctionPointer entryVoid, CFunctionPointer entryInt, CFunctionPointer entryLong, CFunctionPointer entryFloat, CFunctionPointer entryDouble,
                    CFunctionPointer entryObject,
                    CFunctionPointer entryBoolean, CFunctionPointer entryByte, CFunctionPointer entryShort, CFunctionPointer entryChar) {
        RistrettoDeoptimizationSupport support = get();
        assert support.interpreterEntryVoid == null : "multiple entry stub methods registered";

        support.interpreterEntryVoid = entryVoid;
        support.interpreterEntryInt = entryInt;
        support.interpreterEntryLong = entryLong;
        support.interpreterEntryFloat = entryFloat;
        support.interpreterEntryDouble = entryDouble;
        support.interpreterEntryObject = entryObject;
        support.interpreterEntryBoolean = entryBoolean;
        support.interpreterEntryByte = entryByte;
        support.interpreterEntryShort = entryShort;
        support.interpreterEntryChar = entryChar;
    }

    private static CFunctionPointer getInterpreterEntry(JavaKind returnKind) {
        RistrettoDeoptimizationSupport support = get();
        CFunctionPointer ptr = switch (returnKind) {
            case Void -> support.interpreterEntryVoid;
            case Int -> support.interpreterEntryInt;
            case Long -> support.interpreterEntryLong;
            case Float -> support.interpreterEntryFloat;
            case Double -> support.interpreterEntryDouble;
            case Object -> support.interpreterEntryObject;
            case Boolean -> support.interpreterEntryBoolean;
            case Byte -> support.interpreterEntryByte;
            case Short -> support.interpreterEntryShort;
            case Char -> support.interpreterEntryChar;
            default -> throw VMError.shouldNotReachHere("Unexpected return kind for interpreter entry: " + returnKind);
        };
        assert ptr.rawValue() != 0 : returnKind;
        return ptr;
    }

    @Uninterruptible(reason = "Prevent the GC from freeing the CodeInfo object.")
    private static long getCodeInfoRelativeIP(CodePointer pc) {
        UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(pc);
        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            CodeInfo tetheredCodeInfo = CodeInfoAccess.convert(untetheredInfo, tether);
            return CodeInfoAccess.relativeIP(tetheredCodeInfo, pc);
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
    }

    /**
     * Builds the interpreter-target deoptimized frame for a Ristretto runtime-compiled method.
     */
    public static DeoptimizedFrame createDeoptimizedFrame(Deoptimizer deoptimizer, CodePointer pc, FrameInfoQueryResult frameInfo, CodeInfoQueryResult physicalFrame, boolean eager) {
        RistrettoInstalledCode rCode = validateAndGetInstalledCode(pc);

        final long infoPointRelativeIp = getCodeInfoRelativeIP(pc);
        final Infopoint compilerInfoPoint = rCode.getInfopointForRelativeIP(NumUtil.safeToInt(infoPointRelativeIp));
        BytecodePosition byteCodeStack = compilerInfoPoint.debugInfo.frame();
        assert verifyInfopointAndStackWalk(frameInfo, byteCodeStack, compilerInfoPoint);
        return buildInterpreterFrame(frameInfo, physicalFrame, compilerInfoPoint, rCode, deoptimizer, pc, eager);
    }

    private static RistrettoInstalledCode validateAndGetInstalledCode(CodePointer pc) {
        SubstrateInstalledCode installedCode = CodeInfoTable.lookupInstalledCode(pc);
        if (installedCode == null) {
            throw VMError.shouldNotReachHere("Could not find Ristretto installed code for pc " + Long.toHexString(pc.rawValue()));
        }

        ResolvedJavaMethod method = installedCode.getMethod();
        if (!(method instanceof RistrettoMethod)) {
            throw VMError.shouldNotReachHere("Could not find Ristretto method for pc " + Long.toHexString(pc.rawValue()));
        }

        if (!(installedCode instanceof RistrettoInstalledCode rCode)) {
            throw VMError.shouldNotReachHere("Found non-Ristretto installed code for pc " + Long.toHexString(pc.rawValue()));
        }
        return rCode;
    }

    /**
     * Rebuilds the interpreter frame chain for one deoptimized compiled frame and patches the
     * resulting deoptimized frame so later stack walking and execution resume see the rebuilt
     * interpreter-frame chain.
     */
    private static DeoptimizedFrame buildInterpreterFrame(FrameInfoQueryResult virtualFrameInfo, CodeInfoQueryResult physicalFrame, Infopoint compilerInfoPoint, RistrettoInstalledCode rCode,
                    Deoptimizer deoptimizer, CodePointer pc, boolean pin) {
        // Remaining compiled-frame metadata being converted into interpreter frames.
        FrameInfoQueryResult compiledFrame = virtualFrameInfo;
        // Most recently rebuilt interpreter frame; becomes the caller of the next frame created.
        RistrettoVirtualInterpreterFrame frameBefore = null;

        BytecodePosition associatedCompiledCodePosition = compilerInfoPoint.debugInfo.getBytecodePosition();

        while (compiledFrame != null) {
            final int virtualFrameBCI = compiledFrame.getBci();
            final int compilerAssociatedPosBCI = associatedCompiledCodePosition.getBCI();
            if (virtualFrameBCI != compilerAssociatedPosBCI) {
                throw VMError.shouldNotReachHere("Frame BCIs must match the virtual frame and compiler info point but were " + virtualFrameBCI + " and " + compilerAssociatedPosBCI);
            }

            RistrettoMethod rMethod = (RistrettoMethod) associatedCompiledCodePosition.getMethod();
            InterpreterResolvedJavaMethod interpreterMethod = rMethod.getInterpreterMethod();
            if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                logger().string("[buf/deopt] create interp frame for method=").string(interpreterMethod.toString()).newline();
            }
            InterpreterFrame reconstructedFrame = createInterpreterFrameFromCompiledFrame(interpreterMethod, compiledFrame, deoptimizer);
            RistrettoVirtualInterpreterFrame currentFrame = createVirtualInterpreterFrame(compiledFrame, interpreterMethod, reconstructedFrame, frameBefore);
            frameBefore = currentFrame;

            // iterate inlining (caller) chain in deoptimized physical frame and associated compiler
            // infopoint
            compiledFrame = compiledFrame.getCaller();
            associatedCompiledCodePosition = associatedCompiledCodePosition.getCaller();
        }

        installBottomFrameReturnAddress(pc, frameBefore);

        long frameSize = physicalFrame.getTotalFrameSize();
        InterpreterResolvedJavaMethod bottomMethod = frameBefore.getMethod();
        JavaKind bottomReturnKind = bottomMethod.getSignature().getReturnKind();
        installStackTraceCallerInfo(frameBefore);
        RistrettoDeoptimizedInterpreterFrame deoptimizedInterpreterFrame = new RistrettoDeoptimizedInterpreterFrame(frameSize, frameBefore, rCode, pc, pin);

        deoptimizedInterpreterFrame.setInterpreterEntry(getInterpreterEntry(bottomReturnKind));
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            logger().string("[buf/deopt] returning from buildInterpreterFrame").newline();
        }
        return deoptimizedInterpreterFrame;
    }

    /**
     * Creates one virtual interpreter frame and links it to the previously built inner callee frame.
     * Only the physical top frame records a pending compiled return kind because only that frame can
     * still own unread GP/FP return registers when deoptimization starts.
     */
    private static RistrettoVirtualInterpreterFrame createVirtualInterpreterFrame(FrameInfoQueryResult compiledFrame, InterpreterResolvedJavaMethod interpreterMethod,
                    InterpreterFrame reconstructedFrame, RistrettoVirtualInterpreterFrame calleeFrame) {
        int currentBci = compiledFrame.getBci();
        int targetBci = computeDeoptTargetBci(interpreterMethod, compiledFrame);
        JavaKind compiledReturnKind = calleeFrame == null ? resolvePendingTopFrameReturnKind(interpreterMethod, compiledFrame) : JavaKind.Illegal;

        RistrettoVirtualInterpreterFrame currentFrame = new RistrettoVirtualInterpreterFrame(compiledFrame, reconstructedFrame, interpreterMethod, currentBci,
                        targetBci, compiledFrame.getStackState(), compiledFrame.getNumStack(), compiledReturnKind, calleeFrame);
        if (calleeFrame != null) {
            calleeFrame.setCaller(currentFrame);
        }
        return currentFrame;
    }

    /**
     * Copies the reconstructed Java source-level caller chain onto each rebuilt interpreter frame in one pass
     * from the outermost caller back to the live top frame. For a compiled stack like
     * {@code compiled:a -> b -> c(deopt)}, only {@code c} resumes execution directly, but stack
     * walking still needs the Java source-level view {@code c -> b -> a}. Each frame therefore stores the
     * already-built caller suffix that should appear after it:
     *
     * <pre>
     * topFrame = c
     * topFrame.syntheticCallerChain = [b, a]
     * b.syntheticCallerChain = [a]
     * a.syntheticCallerChain = []
     * </pre>
     */
    private static void installStackTraceCallerInfo(RistrettoVirtualInterpreterFrame bottomFrame) {
        InterpreterFrameSourceInfo callerInfo = null;
        for (RistrettoVirtualInterpreterFrame current = bottomFrame; current != null; current = current.getCallee()) {
            current.getFrame().setStackTraceCallerInfo(callerInfo);
            callerInfo = createStackTraceCallerInfo(current, callerInfo);
        }
    }

    static InterpreterFrameSourceInfo createStackTraceCallerInfo(RistrettoVirtualInterpreterFrame current, InterpreterFrameSourceInfo callerInfo) {
        InterpreterResolvedJavaMethod interpretedMethod = current.getMethod();
        int bci = current.getCurrentBci();
        return InterpreterFrameSourceInfo.forInterpretedMethod(interpretedMethod, bci, current.getFrame(),
                        callerInfo);
    }

    private static boolean verifyInfopointAndStackWalk(FrameInfoQueryResult virtualFrameInfo, BytecodePosition byteCodeStack, Infopoint infopoint) {
        BytecodePosition currentFrame = byteCodeStack;
        FrameInfoQueryResult virtualFrame = virtualFrameInfo;
        while (virtualFrame != null) {
            int virtualFrameBCI = virtualFrame.getBci();
            int compilerInfopointBCI = currentFrame.getBCI();
            if (virtualFrameBCI != compilerInfopointBCI) {
                throw VMError.shouldNotReachHere("VirtualFrameInfo decoded from installed Ristretto code does not match the compiler infopoint." + "\nCompiler infopoint frame position was " +
                                currentFrame + "\nwhile decoded virtual frame had position " + virtualFrameBCI + "\nAnd the infopoint was " + infopoint);
            }
            currentFrame = currentFrame.getCaller();
            virtualFrame = virtualFrame.getCaller();
        }
        return true;
    }

    /**
     * Records the original compiled return edge on the reconstructed bottom interpreter frame so
     * stack walking and trace logging still see the same caller relationship after the handoff.
     */
    private static void installBottomFrameReturnAddress(CodePointer sourcePC, RistrettoVirtualInterpreterFrame bottomFrame) {
        bottomFrame.setReturnAddress(new DeoptimizedFrame.ReturnAddress(returnAddressSize(), sourcePC.rawValue()));
    }

    /**
     * Determines whether the physical top compiled frame has a completed invoke result that has not
     * yet been copied into the reconstructed interpreter operand stack.
     *
     * <p>
     * Example shape:
     *
     * <pre>
     * caller:
     *   ...
     *   invokestatic callee   // deopt source state is AfterPop here
     *   istore_1              // interpreter still needs the return value in this slot
     *
     * callee:
     *   ...
     *   ireturn
     * </pre>
     *
     * This only happens for {@code AfterPop} invoke states: the compiled frame has already consumed
     * the receiver/arguments and logically moved past the call, but the hardware ABI still exposes
     * the return value only through the machine GP/FP return registers that the deopt stub can
     * read. This helper records only the return kind; the actual bits must be snapshotted later by
     * the deopt stub while the compiled frame still owns those physical return registers, because
     * the Java-side resume path runs only after the stub has restored caller state and no longer
     * has safe access to the machine return-value registers.
     */
    private static JavaKind resolvePendingTopFrameReturnKind(InterpreterResolvedJavaMethod topFrameMethod, FrameInfoQueryResult topCompiledFrame) {
        if (topCompiledFrame.getStackState() != StackState.AfterPop) {
            return JavaKind.Illegal;
        }
        int currentBci = topCompiledFrame.getBci();
        return resolveDeoptInvokeSiteLayout(topFrameMethod, currentBci).getReturnKind();
    }

    /**
     * Resolves the invoke-site layout for the caller-side bytecode state that triggered
     * deoptimization.
     */
    static CallSiteLayout resolveDeoptInvokeSiteLayout(InterpreterResolvedJavaMethod interpreterMethod, int callsiteBci) {
        byte[] compilerCode = interpreterMethod.getCode();
        int opcode = BytecodeStream.opcode(compilerCode, callsiteBci);
        VMError.guarantee(Bytecodes.isInvoke(opcode), "Deopt resume must resolve an invoke bytecode");

        InterpreterResolvedJavaMethod linkedMethod;
        if (opcode == Bytecodes.INVOKEDYNAMIC) {
            int fullCpi = BytecodeStream.readCPI4(compilerCode, callsiteBci);
            int indyCpi = fullCpi >>> 16;
            VMError.guarantee(indyCpi != 0, "Deopt resume expects a compiler-visible invokedynamic CPI");
            Object indyEntry = interpreterMethod.getConstantPool().peekCachedEntry(indyCpi);
            if (!(indyEntry instanceof ResolvedInvokeDynamicConstant)) {
                throw VMError.shouldNotReachHere("Unexpected INVOKEDYNAMIC constant: " + indyEntry);
            }
            CallSiteLink link = ((ResolvedInvokeDynamicConstant) indyEntry).getCallSiteLink(interpreterMethod, callsiteBci);
            VMError.guarantee(link instanceof SuccessfulCallSiteLink,
                            "Deopt resume expects an already-published runtime invokedynamic link");
            linkedMethod = ((SuccessfulCallSiteLink) link).getInvoker();
        } else {
            if (!(opcode == Bytecodes.INVOKEVIRTUAL || opcode == Bytecodes.INVOKESPECIAL || opcode == Bytecodes.INVOKESTATIC || opcode == Bytecodes.INVOKEINTERFACE)) {
                throw VMError.shouldNotReachHere("Deopt resume expected a concrete invoke bytecode, got opcode " + opcode + " at BCI " + callsiteBci);
            }
            char cpi = BytecodeStream.readCPI2(compilerCode, callsiteBci);
            linkedMethod = Interpreter.resolveMethod(interpreterMethod, opcode, cpi);
        }

        boolean hasReceiver = !linkedMethod.isStatic();
        JavaKind returnKind = linkedMethod.getSignature().getReturnKind();
        int argumentSlotCount = linkedMethod.getSignature().slotsForParameters(hasReceiver);
        return new CallSiteLayout(returnKind, argumentSlotCount);
    }

    /**
     * Chooses the interpreter restart BCI that matches the compiler frame state that caused the
     * deopt.
     */
    private static int computeDeoptTargetBci(InterpreterResolvedJavaMethod interpreterMethod, FrameInfoQueryResult compiledFrame) {
        int currentBci = compiledFrame.getBci();
        /*
         * BeforePop and Rethrow frames resume at the exact deopt BCI. AfterPop frames describe an
         * invoke whose arguments were already consumed, so re-entering at the same BCI would execute
         * the invoke again against an already-popped operand stack. Resume those frames at the
         * bytecode after the call instead.
         */
        return switch (compiledFrame.getStackState()) {
            case BeforePop, Rethrow -> currentBci;
            case AfterPop -> BytecodeStream.nextBCI(interpreterMethod.getCode(), currentBci);
            default -> throw VMError.shouldNotReachHere("Unexpected stack state while computing target BCI: " + compiledFrame.getStackState());
        };
    }

    /**
     * Reconstructs a Crema interpreter frame from the matching compiled frame.
     */
    private static InterpreterFrame createInterpreterFrameFromCompiledFrame(InterpreterResolvedJavaMethod interpreterMethod, FrameInfoQueryResult compiledFrame, Deoptimizer deoptimizer) {
        /*
         * Reuse the deoptimizer state so materialized virtual objects stay shared across
         * reconstructed inlined frames and reserved-thread register reads resolve against the deopt
         * target thread.
         */
        DeoptState deoptState = deoptimizer.getDeoptState();

        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            logger().string("[buf/deopt] interpreterMethod.getMaxLocals=").signed(interpreterMethod.getMaxLocals()).newline();
            logger().string("[buf/deopt] interpreterMethod.getMaxStackSize=").signed(interpreterMethod.getMaxStackSize()).newline();
            logger().string("[buf/deopt] compiledFrame.getNumLocals=").signed(compiledFrame.getNumLocals()).newline();
            logger().string("[buf/deopt] compiledFrame.getNumLocks=").signed(compiledFrame.getNumLocks()).newline();
            logger().string("[buf/deopt] compiledFrame.getNumStack=").signed(compiledFrame.getNumStack()).newline();
        }

        VMError.guarantee(interpreterMethod.getMaxLocals() == compiledFrame.getNumLocals());
        Object[] heldMonitorObjects = collectHeldMonitorObjects(compiledFrame, deoptState);
        if (!interpreterMethod.hasBytecodes()) {
            throw VMError.shouldNotReachHere("Ristretto deoptimization requires an interpreter bytecode body for " + interpreterMethod);
        }
        InterpreterFrame interpreterFrame = InterpreterFrameUtil.allocate(interpreterMethod.getMaxLocals(), interpreterMethod.getMaxStackSize());

        final int numLocals = compiledFrame.getNumLocals();
        final int numStack = compiledFrame.getNumStack();

        int slotIdx = 0;
        for (int localIdx = 0; localIdx < numLocals; localIdx++) {
            JavaConstant value = deoptState.readLocalVariable(localIdx, compiledFrame);
            if (value.getJavaKind().equals(JavaKind.Illegal)) {
                if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                    logger().string("[buf/deopt] slot=").signed(localIdx).string(" is illegal").newline();
                }
                continue;
            }
            switch (value.getJavaKind().getStackKind()) {
                case Int -> InterpreterFrameUtil.setLocalInt(interpreterFrame, localIdx, value.asInt());
                case Long -> InterpreterFrameUtil.setLocalLong(interpreterFrame, localIdx, value.asLong());
                case Float -> InterpreterFrameUtil.setLocalFloat(interpreterFrame, localIdx, value.asFloat());
                case Double -> InterpreterFrameUtil.setLocalDouble(interpreterFrame, localIdx, value.asDouble());
                case Object -> InterpreterFrameUtil.setLocalObject(interpreterFrame, localIdx, SubstrateObjectConstant.asObject(value));
                default -> VMError.shouldNotReachHere("createInterpreterFrameFromCompiledFrame: kind not implemented yet: " + value.getJavaKind());
            }
        }
        slotIdx += numLocals;

        for (int stackIdx = 0; stackIdx < numStack; stackIdx++) {
            int valIndex = slotIdx + stackIdx;
            // in crema locals and expression stack are in the same array, stack[0]<==>
            // array[maxLocals]
            int tos = interpreterMethod.getMaxLocals() + stackIdx;
            JavaConstant value = deoptState.readValue(valIndex, compiledFrame);
            if (value.getJavaKind().equals(JavaKind.Illegal)) {
                if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                    logger().string("[buf/deopt] slot=").signed(valIndex).string(" is illegal").newline();
                }
                continue;
            }
            switch (value.getJavaKind().getStackKind()) {
                case Int -> InterpreterFrameUtil.putInt(interpreterFrame, tos, value.asInt());
                case Long -> InterpreterFrameUtil.putLong(interpreterFrame, tos, value.asLong());
                case Float -> InterpreterFrameUtil.putFloat(interpreterFrame, tos, value.asFloat());
                case Double -> InterpreterFrameUtil.putDouble(interpreterFrame, tos, value.asDouble());
                case Object -> InterpreterFrameUtil.putObject(interpreterFrame, tos, SubstrateObjectConstant.asObject(value));
                default -> VMError.shouldNotReachHere("createInterpreterFrameFromCompiledFrame: kind not implemented yet: " + value.getJavaKind());
            }
        }
        int targetBci = computeDeoptTargetBci(interpreterMethod, compiledFrame);
        registerDeoptimizedHeldMonitors(interpreterMethod, compiledFrame, targetBci, heldMonitorObjects, interpreterFrame);

        return interpreterFrame;
    }

    private static void registerDeoptimizedHeldMonitors(InterpreterResolvedJavaMethod interpreterMethod, FrameInfoQueryResult compiledFrame, int targetBci, Object[] heldMonitorObjects,
                    InterpreterFrame interpreterFrame) {
        validateSynchronizedMethodLock(interpreterMethod, compiledFrame, targetBci, heldMonitorObjects);
        if (heldMonitorObjects == null) {
            return;
        }
        int numLocals = compiledFrame.getNumLocals();
        int numStack = compiledFrame.getNumStack();
        for (int lockIdx = 0; lockIdx < heldMonitorObjects.length; lockIdx++) {
            Object lockObject = heldMonitorObjects[lockIdx];
            if (lockObject == null) {
                if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                    int lockSlotIndex = numLocals + numStack + lockIdx;
                    logger().string("[buf/deopt] slot=").signed(lockSlotIndex).string(" is illegal").newline();
                }
                continue;
            }
            InterpreterToVM.registerHeldMonitor(interpreterFrame, lockObject);
        }
    }

    /**
     * Validates the method monitor that compiled code already owned for a deopt-resumed synchronized
     * method.
     *
     * For normal interpreter entry, the monitor object is simply the class mirror for static
     * methods or local 0 for instance methods. After deoptimization, an instance method can resume
     * at a BCI where local 0 is dead, even though the synchronized-method monitor is still held and
     * represented in the frame-state lock slots. Graal pushes the method monitor before parsing the
     * bytecodes, so it is the outermost lock and therefore lock slot 0; nested bytecode monitors are
     * appended after it. The encoded {@link FrameInfoQueryResult} does not retain the original
     * {@code MonitorIdNode}, so this first lock slot is the Ristretto deoptimization ABI for the
     * synchronized-method monitor. Static synchronized methods validate the object identity directly
     * against the declaring class mirror. Instance methods intentionally do not validate against local
     * 0: valid bytecode can overwrite the receiver local after the method monitor is acquired, and
     * normal optimization can also make local 0 dead at the resume BCI. For instance methods, lock slot
     * 0 is therefore the deoptimization ABI for the method monitor.
     */
    private static void validateSynchronizedMethodLock(InterpreterResolvedJavaMethod interpreterMethod, FrameInfoQueryResult compiledFrame, int targetBci, Object[] heldMonitorObjects) {
        if (!interpreterMethod.isSynchronized()) {
            return;
        }
        if (compiledFrame.getNumLocks() == 0) {
            InterpreterUtil.guarantee(targetBci == jdk.vm.ci.code.BytecodeFrame.BEFORE_BCI,
                            "Missing synchronized method monitor in deoptimized frame-state locks for method %s at target BCI %s with lock count %s.",
                            interpreterMethod, targetBci, compiledFrame.getNumLocks());
            return;
        }
        InterpreterUtil.guarantee(heldMonitorObjects != null && heldMonitorObjects.length > 0 && heldMonitorObjects[0] != null,
                        "Missing synchronized method monitor in deoptimized frame-state locks for method %s at target BCI %s with lock count %s.",
                        interpreterMethod, targetBci, compiledFrame.getNumLocks());
        int methodMonitorSlot = compiledFrame.getNumLocals() + compiledFrame.getNumStack();
        InterpreterUtil.guarantee(methodMonitorSlot < compiledFrame.getValueInfos().length && compiledFrame.getValueInfos()[methodMonitorSlot].getKind() == JavaKind.Object,
                        "Unexpected synchronized method monitor slot in deoptimized frame-state locks for method %s at target BCI %s with lock count %s.",
                        interpreterMethod, targetBci, compiledFrame.getNumLocks());
        if (interpreterMethod.isStatic()) {
            Object staticMethodLock = interpreterMethod.getDeclaringClass().getJavaClass();
            VMError.guarantee(heldMonitorObjects[0] == staticMethodLock,
                            "Unexpected static synchronized method monitor in deoptimized frame-state locks.");
            VMError.guarantee(Thread.holdsLock(heldMonitorObjects[0]),
                            "Static synchronized method monitor is not owned by the current thread during deoptimization.");
            return;
        }

        VMError.guarantee(Thread.holdsLock(heldMonitorObjects[0]),
                        "Instance synchronized method monitor is not owned by the current thread during deoptimization.");
    }

    /**
     * Collects the frame-state monitors that are held at the deopt point. During optimization,
     * objects may be virtualized and later materialized again, and monitor state may also be elided
     * for objects that were never virtualized. Only eliminated monitors need an explicit relock here;
     * live monitors are still owned and are registered with the interpreter frame by the caller.
     */
    private static Object[] collectHeldMonitorObjects(FrameInfoQueryResult sourceFrame, DeoptState deoptState) {
        int numLocks = sourceFrame.getNumLocks();
        if (numLocks == 0) {
            return null;
        }

        int slotIdx = sourceFrame.getNumLocals() + sourceFrame.getNumStack();
        Object[] heldMonitorObjects = null;
        for (int lockIdx = 0; lockIdx < numLocks; lockIdx++) {
            int lockSlotIdx = slotIdx + lockIdx;
            JavaConstant value = deoptState.readValue(lockSlotIdx, sourceFrame);
            if (value.getJavaKind().equals(JavaKind.Illegal)) {
                continue;
            }

            Object lockObject = SubstrateObjectConstant.asObject(value);
            if (sourceFrame.getValueInfos()[lockSlotIdx].isEliminatedMonitor()) {
                /*
                 * Only eliminated monitors need an explicit relock here. Live synchronized
                 * method/block locks are still owned at the deopt point and are registered below so
                 * the interpreter can release them without double-counting the acquisition.
                 */
                DeoptimizedFrame.RelockObjectData relockObjectData = createRelockObjectData(value, sourceFrame);
                MonitorSupport.singleton().doRelockObject(relockObjectData.getObject(), relockObjectData.getLockData());
            }
            if (heldMonitorObjects == null) {
                heldMonitorObjects = new Object[numLocks];
            }
            heldMonitorObjects[lockIdx] = lockObject;
        }
        return heldMonitorObjects;
    }
}
