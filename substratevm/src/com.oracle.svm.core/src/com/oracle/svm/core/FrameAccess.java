/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;

/**
 * This class can be used to access physical Java frames. It cannot be used to access virtual Java
 * frames, and it must not be used to access physical native frames (we can't assume a specific
 * layout for native frames, especially on platforms such as aarch64).
 * <p>
 * When accessing a return address, note that the return address belongs to a different frame than
 * the stack pointer (SP) because the return address is located at a negative offset relative to SP.
 * For Java frames that called native code, the return address is located in the native callee frame
 * and can't be accessed because we can't assume a specific layout for native frames.
 */
public abstract class FrameAccess {
    @Fold
    public static FrameAccess singleton() {
        return ImageSingletons.lookup(FrameAccess.class);
    }

    @Fold
    public static int returnAddressSize() {
        return singleton().getReturnAddressSize();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public CodePointer readReturnAddress(IsolateThread thread, Pointer sourceSp) {
        verifyReturnAddressWithinJavaStack(thread, sourceSp);
        return unsafeReadReturnAddress(sourceSp);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void writeReturnAddress(IsolateThread thread, Pointer sourceSp, CodePointer newReturnAddress) {
        verifyReturnAddressWithinJavaStack(thread, sourceSp);
        unsafeReturnAddressLocation(sourceSp).writeWord(0, newReturnAddress);
    }

    /**
     * This method does not return a pointer because the return address may only be accessed via
     * {@link #readReturnAddress} and {@link #writeReturnAddress}. Note that no verification is
     * performed, so the returned value may point to a native frame or even outside the thread's
     * stack.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord getReturnAddressLocation(IsolateThread thread, Pointer sourceSp) {
        assert thread.isNonNull();
        return unsafeReturnAddressLocation(sourceSp);
    }

    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    public CodePointer readReturnAddress(StoredContinuation continuation, Pointer sourceSp) {
        verifyReturnAddressWithinStoredContinuation(continuation, sourceSp);
        return unsafeReadReturnAddress(sourceSp);
    }

    /**
     * This method does not return a pointer because the return address may only be accessed via
     * {@link #readReturnAddress} and {@link #writeReturnAddress}. Note that no verification is
     * performed, so the returned value may point outside the stack of the stored continuation.
     */
    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    public UnsignedWord getReturnAddressLocation(StoredContinuation continuation, Pointer sourceSp) {
        assert continuation != null;
        return unsafeReturnAddressLocation(sourceSp);
    }

    /**
     * Do not use this method unless absolutely necessary as it does not perform any verification.
     * It is very easy to accidentally access a native frame, which can result in hard-to-debug
     * transient failures.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public CodePointer unsafeReadReturnAddress(Pointer sourceSp) {
        return unsafeReturnAddressLocation(sourceSp).readWord(0);
    }

    /**
     * Do not use this method unless absolutely necessary as it does not perform any verification.
     * It is very easy to accidentally access a native frame, which can result in hard-to-debug
     * transient failures.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Pointer unsafeReturnAddressLocation(Pointer sourceSp) {
        return sourceSp.subtract(returnAddressSize());
    }

    @Fold
    protected int getReturnAddressSize() {
        int value = ConfigurationValues.getTarget().arch.getReturnAddressSize();
        assert value > 0;
        return value;
    }

    @Fold
    public static int wordSize() {
        return ConfigurationValues.getTarget().arch.getWordSize();
    }

    @Fold
    public static int uncompressedReferenceSize() {
        return wordSize();
    }

    public static Stamp getWordStamp() {
        return StampFactory.forKind(ConfigurationValues.getTarget().wordJavaKind);
    }

    /**
     * This method is only a best-effort approach as it assumes that the given stack pointer either
     * points into a Java frame, to the stack boundary of a native -> Java call, or to the stack
     * boundary of a Java -> native call. This code does not detect if the stack pointer points to
     * an arbitrary position in a native frame.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void verifyReturnAddressWithinJavaStack(IsolateThread thread, Pointer sourceSp) {
        if (SubstrateOptions.VerifyFrameAccess.getValue()) {
            verifyReturnAddressWithinJavaStack0(thread, sourceSp, true);
        } else {
            assert verifyReturnAddressWithinJavaStack0(thread, sourceSp, false);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private boolean verifyReturnAddressWithinJavaStack0(IsolateThread thread, Pointer sourceSp, boolean verifyReturnAddress) {
        if (SubstrateDiagnostics.isFatalErrorHandlingThread()) {
            return true;
        }

        VMError.guarantee(CurrentIsolate.getCurrentThread() == thread || VMOperation.isInProgressAtSafepoint(), "Unsafe access to IsolateThread");

        UnsignedWord stackBase = VMThreads.StackBase.get(thread);
        UnsignedWord stackEnd = VMThreads.StackEnd.get(thread);

        Pointer returnAddressLocation = unsafeReturnAddressLocation(sourceSp);
        VMError.guarantee(stackBase.equal(0) || returnAddressLocation.belowThan(stackBase), "Access is outside of the stack memory that is reserved for this thread.");
        VMError.guarantee(returnAddressLocation.aboveOrEqual(stackEnd), "Access is outside of the stack memory that is reserved for this thread.");

        Pointer topOfStack = getTopOfStack(thread);
        VMError.guarantee(returnAddressLocation.aboveOrEqual(topOfStack), "Access is outside of the part of the stack that is currently used by the thread.");

        if (verifyReturnAddress) {
            JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor(thread);
            while (anchor.isNonNull()) {
                /*
                 * If the verification runs for the current thread and if that thread has outdated
                 * frame anchors, the guarantee below may fail unexpectedly (e.g., we might be in
                 * the middle of executing safepoint slowpath code for a transition from native to
                 * Java). However, this is still the best that we can do at the moment (the only
                 * alternative is a full stack walk, which may run into even more issues).
                 */
                VMError.guarantee(anchor.getLastJavaSP() != sourceSp, "Potentially accessing a return address that is stored in a native frame.");
                anchor = anchor.getPreviousAnchor();
            }
        }

        return true;
    }

    @NeverInline("Accesses the caller stack pointer")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    private static Pointer getTopOfStack(IsolateThread thread) {
        if (thread == CurrentIsolate.getCurrentThread()) {
            return KnownIntrinsics.readCallerStackPointer();
        }

        JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor(thread);
        VMError.guarantee(anchor.isNonNull(), "When accessing the stack of another thread, the other thread must have a frame anchor.");
        return anchor.getLastJavaSP();
    }

    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    private void verifyReturnAddressWithinStoredContinuation(StoredContinuation continuation, Pointer sourceSP) {
        if (SubstrateOptions.VerifyFrameAccess.getValue()) {
            verifyReturnAddressWithinStoredContinuation0(continuation, sourceSP);
        } else {
            assert verifyReturnAddressWithinStoredContinuation0(continuation, sourceSP);
        }
    }

    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    private boolean verifyReturnAddressWithinStoredContinuation0(StoredContinuation continuation, Pointer sourceSP) {
        if (SubstrateDiagnostics.isFatalErrorHandlingThread()) {
            return true;
        }

        UnsignedWord stackEnd = StoredContinuationAccess.getFramesStart(continuation);
        UnsignedWord stackBase = StoredContinuationAccess.getFramesEnd(continuation);

        Pointer returnAddressLocation = unsafeReturnAddressLocation(sourceSP);
        VMError.guarantee(returnAddressLocation.belowThan(stackBase), "Access is outside of the stack of the stored continuation");
        VMError.guarantee(returnAddressLocation.aboveOrEqual(stackEnd), "Access is outside of the stack of the stored continuation");
        return true;
    }
}
