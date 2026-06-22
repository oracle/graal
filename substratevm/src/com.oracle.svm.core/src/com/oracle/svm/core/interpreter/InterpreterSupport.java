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

import com.oracle.svm.shared.BuildPhaseProvider;
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
import com.oracle.svm.shared.Uninterruptible;

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
     * Check if a given frame should be processed by {@link #getInterpretedMethodFrameInfo}.
     */
    public abstract boolean isInterpreterRoot(FrameInfoQueryResult frameInfo);

    /**
     * Captured values from one decoded interpreter root frame. The {@link FrameInfoQueryResult}
     * identity scopes the captured values to the frame that produced them, so callers can separate
     * SP-relative reads from later source-info construction without reusing data for a different
     * physical frame.
     */
    public static final class InterpretedFrameData {
        private FrameInfoQueryResult frameInfo;
        private ResolvedJavaMethod interpretedMethod;
        private Object interpreterFrame;
        private int bci;
        private boolean intrinsic;

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void clear() {
            frameInfo = null;
            interpretedMethod = null;
            interpreterFrame = null;
            bci = BytecodeFrame.UNKNOWN_BCI;
            intrinsic = false;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void setInterpreted(FrameInfoQueryResult capturedFrameInfo, ResolvedJavaMethod method, int capturedBCI, Object frame) {
            assert !hasData();

            frameInfo = capturedFrameInfo;
            interpretedMethod = method;
            bci = capturedBCI;
            interpreterFrame = frame;
            intrinsic = false;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void setIntrinsic(FrameInfoQueryResult capturedFrameInfo, ResolvedJavaMethod method, Object frame) {
            assert !hasData();

            frameInfo = capturedFrameInfo;
            interpretedMethod = method;
            bci = BytecodeFrame.UNKNOWN_BCI;
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
     * Transforms an interpreter (root) frame into a frame of the interpreted method. An error is
     * thrown if the passed frame is not an {@link #isInterpreterRoot interpreter root}.
     *
     * @param frameInfo interpreter root frame
     * @param sp stack pointer of the interpreter frame
     * @return a frame representing the interpreted method
     */
    public abstract FrameSourceInfo getInterpretedMethodFrameInfo(FrameInfoQueryResult frameInfo, Pointer sp);

    /**
     * Reads the SP-relative data needed by {@link #getInterpretedMethodFrameInfo(FrameInfoQueryResult, InterpretedFrameData)}
     * while the caller guarantees that {@code sp} is stable.
     */
    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    public abstract void captureInterpretedMethodFrameInfo(FrameInfoQueryResult frameInfo, Pointer sp, InterpretedFrameData data);

    /**
     * Transforms an interpreter root frame using data that was already captured from a stable stack
     * pointer.
     */
    public abstract FrameSourceInfo getInterpretedMethodFrameInfo(FrameInfoQueryResult frameInfo, InterpretedFrameData data);

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
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Used for crash log")
    public abstract void logInterpreterFrame(Log log, FrameInfoQueryResult frameInfo, Pointer sp);

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

    public abstract PreparedSignature prepareJNISignature(Signature signature, boolean hasReceiver, ResolvedJavaType accessingClass);

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
