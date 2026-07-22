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
package com.oracle.svm.core.interpreter;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.graal.code.PreparedSignature;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.shared.BuildPhaseProvider;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandler;
import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/* Enables unoptimized execution of AOT compiled methods with an interpreter. The SVM
 * constraints apply, e.g. this itself does not enable class loading. */
public abstract class InterpreterSupport {
    @UnknownPrimitiveField(availability = BuildPhaseProvider.AfterCompilation.class) //
    private CFunctionPointer leaveStubPointer;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.AfterCompilation.class) //
    private int leaveStubLength;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.AfterCompilation.class) //
    private CFunctionPointer leaveJNIStubPointer;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.AfterCompilation.class) //
    private int leaveJNIStubLength;

    @Fold
    public static boolean isEnabled() {
        return ImageSingletons.contains(InterpreterSupport.class);
    }

    @Fold
    public static InterpreterSupport singleton() {
        return ImageSingletons.lookup(InterpreterSupport.class);
    }

    /**
     * Returns whether {@code frameInfo} is a physical interpreter root that must be replaced with
     * source information for the interpreted guest method.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public abstract boolean isInterpreterRoot(FrameInfoQueryResult frameInfo);

    /**
     * Returns whether {@code frameInfo} belongs to a bytecode-handler stub generated from a method
     * annotated with {@link BytecodeInterpreterHandler}.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public abstract boolean isInterpreterBytecodeHandlerStub(FrameInfoQueryResult frameInfo);

    /**
     * Returns whether {@code method} is a bytecode-handler stub generated from a method annotated
     * with {@link BytecodeInterpreterHandler}.
     * <p>
     * For details about generated handlers, see the
     * <a href="https://github.com/oracle/graal/blob/master/truffle/docs/OneCompilationPerBytecodeHandler.md">
     * One Compilation per Bytecode Handler documentation</a>.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public abstract boolean isInterpreterBytecodeHandlerStub(ResolvedJavaMethod method);

    /**
     * Reads the current guest BCI from the Java handler frame whose caller is a generated
     * bytecode-handler stub.
     *
     * @param frameInfo Java handler frame containing the current BCI as an argument
     * @param sp stack pointer of the physical frame containing {@code frameInfo}
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public abstract int getInterpreterBytecodeHandlerBCI(FrameInfoQueryResult frameInfo, Pointer sp);

    /**
     * State carried while VM-level frames are translated to source-level interpreter frames.
     * Threaded bytecode execution stores the active guest BCI in the Java handler frame, while the
     * interpreted method and {@link com.oracle.svm.core.interpreter.InterpreterFrameSourceInfo}
     * data remain in the interpreter root frame below the generated handler stub. Translation
     * therefore needs to carry the handler BCI across the following VM-level frame sequence:
     *
     * <pre>
     * Java bytecode handler -> generated handler stub -> interpreter root
     * </pre>
     *
     * The corresponding state transitions are {@code INITIAL -> BYTECODE_HANDLER_SEEN ->
     * BYTECODE_HANDLER_STUB_SEEN -> INITIAL}. Continuation walking can capture the handler BCI
     * before translation, in which case translation starts with {@code BYTECODE_HANDLER_SEEN} and
     * does not read the stack pointer again.
     *
     * The state belongs to one source-level stack walk and is cleared after the matching root is
     * translated.
     */
    public static final class StackWalkState {
        private static final byte INITIAL = 0;
        private static final byte BYTECODE_HANDLER_SEEN = 1;
        private static final byte BYTECODE_HANDLER_STUB_SEEN = 2;

        private byte bytecodeHandlerState = INITIAL;
        private FrameInfoQueryResult bytecodeHandlerStub;
        private int threadedHandlerBCI = BytecodeFrame.UNKNOWN_BCI;

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private boolean isInitial() {
            return bytecodeHandlerState == INITIAL;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private boolean isBytecodeHandlerSeen() {
            return bytecodeHandlerState == BYTECODE_HANDLER_SEEN;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private void setBytecodeHandler(FrameInfoQueryResult frameInfo, int bci) {
            VMError.guarantee(bytecodeHandlerState == INITIAL, "Nested interpreter bytecode-handler frames are not supported");
            VMError.guarantee(bci != BytecodeFrame.UNKNOWN_BCI, "Cannot read the interpreter bytecode-handler BCI");
            FrameInfoQueryResult caller = frameInfo.getCaller();
            VMError.guarantee(caller != null, "Interpreter bytecode handler must be inlined into its generated stub");
            bytecodeHandlerStub = caller;
            threadedHandlerBCI = bci;
            bytecodeHandlerState = BYTECODE_HANDLER_SEEN;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private void consumeBytecodeHandlerStub(FrameInfoQueryResult frameInfo) {
            VMError.guarantee(bytecodeHandlerState == BYTECODE_HANDLER_SEEN, "Interpreter bytecode-handler stub has no matching Java handler frame");
            VMError.guarantee(bytecodeHandlerStub == frameInfo, "Interpreter bytecode-handler frame has a different generated stub caller");
            bytecodeHandlerState = BYTECODE_HANDLER_STUB_SEEN;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private int getThreadedHandlerBCI() {
            VMError.guarantee(bytecodeHandlerState == BYTECODE_HANDLER_STUB_SEEN, "Interpreter root reached before its bytecode-handler stub");
            return threadedHandlerBCI;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private void clearBytecodeHandler() {
            bytecodeHandlerState = INITIAL;
            bytecodeHandlerStub = null;
            threadedHandlerBCI = BytecodeFrame.UNKNOWN_BCI;
        }
    }

    /**
     * Captured values from one decoded interpreter root frame. The {@link FrameInfoQueryResult}
     * identity scopes the captured values to the frame that produced them, so callers can separate
     * SP-relative reads from later source-info construction without reusing data for a different
     * physical frame. If capture occurs during debugger-event delivery, {@code debuggerEventBCI}
     * preserves the BCI being reported even after the event callback clears the live frame state.
     */
    public static final class InterpretedFrameData {
        private FrameInfoQueryResult frameInfo;
        private ResolvedJavaMethod interpretedMethod;
        private Object interpreterFrame;
        private int bci;
        private int debuggerEventBCI;
        private boolean intrinsic;

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void clear() {
            frameInfo = null;
            interpretedMethod = null;
            interpreterFrame = null;
            bci = BytecodeFrame.UNKNOWN_BCI;
            debuggerEventBCI = BytecodeFrame.UNKNOWN_BCI;
            intrinsic = false;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void setInterpreted(FrameInfoQueryResult capturedFrameInfo, ResolvedJavaMethod method, int capturedBCI, int capturedDebuggerEventBCI, Object frame) {
            assert !hasData();

            frameInfo = capturedFrameInfo;
            interpretedMethod = method;
            bci = capturedBCI;
            debuggerEventBCI = capturedDebuggerEventBCI;
            interpreterFrame = frame;
            intrinsic = false;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void setIntrinsic(FrameInfoQueryResult capturedFrameInfo, ResolvedJavaMethod method, Object frame) {
            assert !hasData();

            frameInfo = capturedFrameInfo;
            interpretedMethod = method;
            bci = BytecodeFrame.UNKNOWN_BCI;
            debuggerEventBCI = BytecodeFrame.UNKNOWN_BCI;
            interpreterFrame = frame;
            intrinsic = true;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public boolean isFor(FrameInfoQueryResult queryFrameInfo) {
            assert queryFrameInfo != null;
            return frameInfo == queryFrameInfo;
        }

        public ResolvedJavaMethod getInterpretedMethod() {
            assert hasData() && interpretedMethod != null;
            return interpretedMethod;
        }

        public Object getInterpreterFrame() {
            assert hasData() && interpreterFrame != null;
            return interpreterFrame;
        }

        public int getBCI() {
            assert hasData();
            return bci;
        }

        public int getDebuggerEventBCI() {
            assert hasData();
            return debuggerEventBCI;
        }

        public boolean isIntrinsic() {
            assert hasData();
            return intrinsic;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private boolean hasData() {
            return frameInfo != null;
        }
    }

    /**
     * Captures SP-relative interpreter data while a stored continuation is pinned. The later
     * translation step can then operate without retaining a raw pointer into the continuation. For
     * a threaded handler this captures its BCI; for an interpreter root this captures the normal
     * {@link InterpretedFrameData}.
     *
     * @param frameInfo VM-level frame being captured
     * @param sp stack pointer of the physical frame containing {@code frameInfo}
     * @param interpretedFrameData storage for interpreter-root values
     * @param state state shared by all frames in this stack walk
     */
    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    public final void captureStackWalkFrameData(FrameInfoQueryResult frameInfo, Pointer sp, InterpretedFrameData interpretedFrameData, StackWalkState state) {
        VMError.guarantee(sp.isNonNull(), "Cannot capture interpreter stack-walk data without a stack pointer");
        if (isBytecodeHandlerFrame(frameInfo)) {
            state.setBytecodeHandler(frameInfo, getInterpreterBytecodeHandlerBCI(frameInfo, sp));
        } else if (isInterpreterRoot(frameInfo)) {
            captureInterpretedMethodFrameInfo(frameInfo, sp, interpretedFrameData);
        }
    }

    /**
     * Translates one VM-level frame into its source-level representation. A {@code null} result
     * suppresses a synthetic threaded-handler stub. Handler state is consumed when the matching
     * interpreter root is translated.
     *
     * @param sp physical stack pointer for a live regular frame, or null for a deoptimized or
     *            pre-captured continuation frame
     * @param interpretedFrameData pre-captured root data for a continuation, or {@code null}
     * @param state state shared by all frames in this stack walk
     */
    public final FrameSourceInfo translateStackWalkFrame(FrameInfoQueryResult frameInfo, Pointer sp, InterpretedFrameData interpretedFrameData, StackWalkState state) {
        assert interpretedFrameData == null || sp.isNull() : "SP and interpretedFrameData must not both be set";
        if (frameInfo == null) {
            /* Interpreter leave stubs do not have any FrameInfo at the moment. */
            return null;
        }

        if (isBytecodeHandlerFrame(frameInfo)) {
            if (state.isInitial()) {
                VMError.guarantee(sp.isNonNull(), "Cannot read an interpreter bytecode-handler BCI without a stack pointer");
                state.setBytecodeHandler(frameInfo, getInterpreterBytecodeHandlerBCI(frameInfo, sp));
            } else {
                VMError.guarantee(state.isBytecodeHandlerSeen(), "Unexpected interpreter bytecode-handler frame");
            }
        } else if (isInterpreterBytecodeHandlerStub(frameInfo)) {
            state.consumeBytecodeHandlerStub(frameInfo);
            return null;
        }

        if (isInterpreterRoot(frameInfo)) {
            int threadedHandlerBCI = state.isInitial() ? BytecodeFrame.UNKNOWN_BCI : state.getThreadedHandlerBCI();
            FrameSourceInfo sourceInfo;
            if (interpretedFrameData != null) {
                sourceInfo = getInterpretedMethodFrameInfo(frameInfo, interpretedFrameData, threadedHandlerBCI);
            } else {
                VMError.guarantee(sp.isNonNull(), "Cannot translate interpreter root without a stack pointer");
                sourceInfo = getInterpretedMethodFrameInfo(frameInfo, sp, threadedHandlerBCI);
            }
            state.clearBytecodeHandler();
            return sourceInfo;
        }

        FrameSourceInfo syntheticFrameInfo = getSyntheticMethodFrameInfo(frameInfo);
        return syntheticFrameInfo != null ? syntheticFrameInfo : frameInfo;
    }

    /**
     * Returns whether {@code frameInfo} is a Java bytecode handler executing through a generated
     * bytecode-handler stub. This does not identify bytecode handlers when stub generation is
     * disabled because those handlers have no generated stub caller.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private boolean isBytecodeHandlerFrame(FrameInfoQueryResult frameInfo) {
        return frameInfo.getCaller() != null && isInterpreterBytecodeHandlerStub(frameInfo.getCaller());
    }

    /**
     * Returns the guest BCI when {@code frameInfo} is a threaded Java handler frame, or
     * {@link BytecodeFrame#UNKNOWN_BCI} otherwise. Crash-stack printing carries this best-effort
     * value to the next interpreter root; normal stack walking additionally validates the complete
     * handler-stub-root sequence with {@link StackWalkState}.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public final int getThreadedHandlerBCIForCrashLog(FrameInfoQueryResult frameInfo, Pointer sp) {
        return isBytecodeHandlerFrame(frameInfo) ? getInterpreterBytecodeHandlerBCI(frameInfo, sp) : BytecodeFrame.UNKNOWN_BCI;
    }

    /**
     * Transforms an interpreter root frame, using {@code threadedHandlerBCI} when stack walking
     * observed an active threaded bytecode-handler stub immediately above this root.
     *
     * @param frameInfo interpreter root frame
     * @param sp stack pointer of the interpreter root
     * @param threadedHandlerBCI BCI captured from the active threaded handler, or
     *            {@link BytecodeFrame#UNKNOWN_BCI}
     * @return source information for the interpreted guest method
     */
    public abstract FrameSourceInfo getInterpretedMethodFrameInfo(FrameInfoQueryResult frameInfo, Pointer sp, int threadedHandlerBCI);

    /**
     * Reads the SP-relative data needed by
     * {@link #getInterpretedMethodFrameInfo(FrameInfoQueryResult, InterpretedFrameData, int)} while
     * the caller guarantees that {@code sp} is stable.
     */
    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    public abstract void captureInterpretedMethodFrameInfo(FrameInfoQueryResult frameInfo, Pointer sp, InterpretedFrameData data);

    /**
     * Transforms a captured interpreter root frame, overriding its BCI with the active threaded
     * handler's BCI when one was captured during the same stack walk.
     *
     * @param frameInfo interpreter root frame
     * @param data values captured from {@code frameInfo} while its stack pointer was stable
     * @param threadedHandlerBCI BCI captured from the active threaded handler, or
     *            {@link BytecodeFrame#UNKNOWN_BCI}
     * @return source information for the interpreted guest method
     */
    public abstract FrameSourceInfo getInterpretedMethodFrameInfo(FrameInfoQueryResult frameInfo, InterpretedFrameData data, int threadedHandlerBCI);

    /**
     * Returns synthetic source information only for runtime-compiled Ristretto frames whose
     * metadata preserves the deopt method but omits the normal source-class and source-method
     * fields. Returns {@code null} for frames that already carry encoded source fields and for
     * non-Ristretto frames.
     */
    public abstract FrameSourceInfo getSyntheticMethodFrameInfo(FrameInfoQueryResult frameInfo);

    /**
     * Make a best-effort attempt at logging helpful information about the
     * {@linkplain #isInterpreterRoot interpreter frame}. Avoiding allocations or anything risky
     * during crash logging.
     *
     * @param threadedHandlerBCI BCI recovered from an active threaded handler, or
     *            {@link BytecodeFrame#UNKNOWN_BCI}
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Used for crash log")
    public abstract void logInterpreterFrame(Log log, FrameInfoQueryResult frameInfo, Pointer sp, int threadedHandlerBCI);

    /**
     * Constructs an interpreter-target deoptimized frame for {@code installedCode}.
     */
    public abstract DeoptimizedFrame createInterpreterDeoptimizedFrame(SubstrateInstalledCode installedCode, Deoptimizer deoptimizer, CodePointer pc,
                    FrameInfoQueryResult frameInfo, CodeInfoQueryResult physicalFrame, boolean eager);

    /**
     * Continues execution from an interpreter-target deoptimized frame.
     *
     * @param gpReturnValueObject optional materialized object for {@code gpReturnValue}. It is
     *            non-null only when the deoptimization stub could safely decode the GP register as a
     *            Java object before the compiled frame is torn down. A null value does not prove the
     *            GP register is non-object; implementations must inspect {@code gpReturnValue} when
     *            they still need object-return or exception state.
     */
    @Uninterruptible(reason = "Invoked from deoptimization stubs while transitioning to interpreter execution.")
    public abstract UnsignedWord continueInterpreterDeoptimization(DeoptimizedFrame frame, Pointer originalStackPointer, UnsignedWord gpReturnValue, UnsignedWord fpReturnValue,
                    boolean hasException, Object gpReturnValueObject);

    public PreparedSignature prepareSignature(ResolvedJavaMethod method) {
        return prepareSignature(method.getSignature(), method.hasReceiver(), method.getDeclaringClass());
    }

    public abstract Class<?> toClass(ResolvedJavaType resolvedJavaType);

    public abstract PreparedSignature prepareSignature(Signature signature, boolean hasReceiver, ResolvedJavaType accessingClass);

    public abstract PreparedSignature prepareJNIDowncallSignature(Signature signature, boolean hasReceiver, ResolvedJavaType accessingClass);

    /**
     * Prepares the native ABI signature for a JNI varargs call with {@code signature},
     * {@code hasReceiver}, {@code accessingClass}, and {@code nonVirtual}.
     */
    public abstract PreparedSignature prepareJNIUpcallVarargsSignature(Signature signature, ResolvedJavaType accessingClass, boolean nonVirtual);

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setLeaveStubPointer(CFunctionPointer leaveStubPointer, int length) {
        assert singleton().leaveStubPointer == null : "multiple leave stub methods registered";
        singleton().leaveStubPointer = leaveStubPointer;
        singleton().leaveStubLength = length;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setLeaveJNIStubPointer(CFunctionPointer leaveJNIStubPointer, int length) {
        assert singleton().leaveJNIStubPointer == null : "multiple JNI leave stub methods registered";
        singleton().leaveJNIStubPointer = leaveJNIStubPointer;
        singleton().leaveJNIStubLength = length;
    }

    /**
     * Determines if a given address is within the program code of the interpreter leave stub.
     *
     * Stackslot layout of leaveInterpreterStub:
     * 
     * <pre>
     *     1. base address of outgoing stack args
     *     2. variable stack size
     * </pre>
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isInInterpreterLeaveStub(CodePointer ip) {
        Pointer start = (Pointer) singleton().leaveStubPointer;
        Pointer end = start.add(singleton().leaveStubLength);
        return start.belowOrEqual((UnsignedWord) ip) && end.aboveOrEqual((UnsignedWord) ip);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isInInterpreterJNILeaveStub(CodePointer ip) {
        Pointer start = (Pointer) singleton().leaveJNIStubPointer;
        Pointer end = start.add(singleton().leaveJNIStubLength);
        return start.belowOrEqual((UnsignedWord) ip) && end.aboveOrEqual((UnsignedWord) ip);
    }

    @Uninterruptible(reason = "Bridge between uninterruptible and potentially interruptible code.", mayBeInlined = true, calleeMustBe = false)
    private static void callVisitor(ObjectReferenceVisitor visitor, Pointer firstObjRef, int count) {
        visitor.visitObjectReferences(firstObjRef, false, FrameAccess.uncompressedReferenceSize(), null, count);
    }

    /**
     * Array index is the unique identifier (aka. "sourceMethodId") for a compiled method, where
     * index 0 means "unknown". This is used to build a mapping from compiled methods to interpreter
     * methods.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public abstract void buildMethodIdMapping(ResolvedJavaMethod[] encodedMethods);
}
