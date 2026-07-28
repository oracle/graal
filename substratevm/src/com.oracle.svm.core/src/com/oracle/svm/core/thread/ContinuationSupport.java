/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.nodes.WriteStackPointerNode;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaFrame;
import com.oracle.svm.core.stack.JavaFrames;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.guest.staging.core.UnmanagedMemoryUtil;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class ContinuationSupport {
    @Fold
    public static boolean isSupported() {
        return ContinuationsFeature.isSupported();
    }

    /* See JDK native enum {@code freeze_result}. */
    public static final int FREEZE_OK = 0;
    public static final int FREEZE_PINNED_CS = 2; // critical section
    public static final int FREEZE_PINNED_NATIVE = 3;
    public static final int FREEZE_YIELDING = -2;

    private long ipOffset;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected ContinuationSupport() {
    }

    @Fold
    public static ContinuationSupport singleton() {
        return ImageSingletons.lookup(ContinuationSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setIPOffset(long value) {
        assert ipOffset == 0;
        ipOffset = value;
    }

    public long getIPOffset() {
        assert ipOffset != 0;
        return ipOffset;
    }

    public Object prepareCopy(@SuppressWarnings("unused") StoredContinuation storedCont) {
        return null;
    }

    /**
     * This method reserves the extra stack space for the continuation. Be careful when modifying
     * the code or the arguments of this method because we need the guarantee the following
     * invariants:
     * <ul>
     * <li>The method must not contain any stack accesses as they would be relative to the
     * manipulated (and therefore incorrect) stack pointer.</li>
     * <li>The method must never return because the stack pointer would be incorrect
     * afterwards.</li>
     * <li>Only uninterruptible code may be executed once this method is called.</li>
     * </ul>
     */
    @NeverInline("Modifies the stack pointer manually, which breaks stack accesses.")
    @Uninterruptible(reason = "Manipulates the stack pointer.")
    public static void enter(StoredContinuation storedCont, Pointer topSP, Object preparedData) {
        WriteStackPointerNode.write(topSP);
        enter0(storedCont, topSP, preparedData);
    }

    @NeverInline("The caller modified the stack pointer manually, so we need a new stack frame.")
    @Uninterruptible(reason = "Copies stack frames containing references.")
    private static void enter0(StoredContinuation storedCont, Pointer topSP, Object preparedData) {
        // copyFrames() may do something interruptible before uninterruptibly copying frames.
        // Code must not rely on remaining uninterruptible until after frames were copied.
        CodePointer enterIP = singleton().copyFrames(storedCont, topSP, preparedData);
        patchStackAddressesInCopiedFrames(storedCont, enterIP, topSP);
        KnownIntrinsics.farReturn(FREEZE_OK, topSP, enterIP, false);
    }

    @Uninterruptible(reason = "Copies stack frames containing references.")
    protected CodePointer copyFrames(StoredContinuation storedCont, Pointer topSP, @SuppressWarnings("unused") Object preparedData) {
        int totalSize = StoredContinuationAccess.getFramesSizeInBytes(storedCont);
        assert totalSize % SubstrateTarget.getWordSize() == 0;

        Pointer frameData = StoredContinuationAccess.getFramesStart(storedCont);
        UnmanagedMemoryUtil.copyWordsForward(frameData, topSP, Word.unsigned(totalSize));
        return StoredContinuationAccess.getIP(storedCont);
    }

    @Uninterruptible(reason = "Copies stack frames containing references.")
    public CodePointer copyFrames(StoredContinuation fromCont, StoredContinuation toCont, Object preparedData) {
        return copyFrames(fromCont, StoredContinuationAccess.getFramesStart(toCont), preparedData);
    }

    /**
     * Patches slots in the copied frames that are known to contain addresses into the stack,
     * specifically spilled stack pointers and frame pointers, but not anything beyond that such as
     * {@link UnsafeStackValue} containing values in the address range.
     */
    @Uninterruptible(reason = "Prevent observable unadjusted stack addresses.")
    public static void patchStackAddressesInCopiedFrames(StoredContinuation continuation, CodePointer ip, Pointer newFramesStart) {
        if (!SubstrateOptions.PreserveFramePointer.getValue() && !SubstrateOptions.useFramePointerPhase()) {
            return;
        }

        Pointer originalFramesStart = StoredContinuationAccess.getOriginalCarrierSP(continuation);
        if (newFramesStart.equal(originalFramesStart)) {
            return;
        }
        int framesSize = StoredContinuationAccess.getFramesSizeInBytes(continuation);
        Pointer newFramesEnd = newFramesStart.add(framesSize);

        JavaStackWalk walk = StackValue.get(JavaStackWalker.sizeOfJavaStackWalk());
        IsolateThread thread = CurrentIsolate.getCurrentThread();
        JavaStackWalker.initialize(walk, thread, newFramesStart, newFramesEnd, ip, Word.nullPointer());
        while (JavaStackWalker.advance(walk, thread)) {
            JavaFrame frame = JavaStackWalker.getCurrentFrame(walk);
            VMError.guarantee(!JavaFrames.isEntryPoint(frame), "Entry point frames are not supported");
            VMError.guarantee(!JavaFrames.isUnknownFrame(frame), "Stack walk must not encounter unknown frame");
            VMError.guarantee(!Deoptimizer.checkIsDeoptimized(frame), "Deoptimized frames are not supported");

            Pointer callerSP = JavaFrames.getCallerSP(frame);

            if (SubstrateOptions.PreserveFramePointer.getValue()) {
                Pointer framePointerSlot = FrameAccess.singleton().unsafePreservedFramePointerLocation(callerSP);
                patchStackAddress(originalFramesStart, newFramesStart, newFramesEnd, framePointerSlot, callerSP, true);
            }
            long framePointerSaveAreaOffset = frame.getFramePointerSaveAreaOffset();
            if (framePointerSaveAreaOffset != CodeInfoQueryResult.NO_FRAME_POINTER_SAVE_AREA_OFFSET) {
                Pointer saveAreaAddress = frame.getSP().add(Word.unsigned(framePointerSaveAreaOffset));
                /*
                 * Without PreserveFramePointer, the save area is used only temporarily to spill
                 * stack addresses, and contains garbage otherwise. We don't know the current state,
                 * but overwriting garbage is safe if we don't expect it to be a valid address.
                 */
                boolean assertValid = SubstrateOptions.PreserveFramePointer.getValue();
                patchStackAddress(originalFramesStart, newFramesStart, newFramesEnd, saveAreaAddress, callerSP, assertValid);
            }
        }
    }

    @Uninterruptible(reason = "Prevent observable unadjusted stack addresses.")
    private static void patchStackAddress(Pointer originalFramesStart, Pointer newFramesStart, Pointer newFramesEnd, Pointer addressSlot, Pointer callerSP, boolean assertValid) {
        assert addressSlot.aboveOrEqual(newFramesStart);
        assert addressSlot.belowThan(callerSP);
        assert callerSP.belowOrEqual(newFramesEnd);

        Pointer address = addressSlot.readWord(0);
        Pointer adjustedAddress = newFramesStart.add(address.subtract(originalFramesStart));

        if (assertValid) {
            boolean callerIsEntryFrame = callerSP.equal(newFramesEnd);
            if (callerIsEntryFrame) {
                /*
                 * The caller is the entry frame, which is not copied. With PreserveFramePointer,
                 * the save slots in this frame point into it, and so, outside the copied frames.
                 */
                assert adjustedAddress.aboveOrEqual(newFramesEnd);
            } else {
                assert adjustedAddress.aboveOrEqual(newFramesStart) && adjustedAddress.belowThan(newFramesEnd);
            }
        }

        addressSlot.writeWord(0, adjustedAddress);
    }
}
