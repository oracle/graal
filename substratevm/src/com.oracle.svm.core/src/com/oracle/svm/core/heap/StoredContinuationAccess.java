/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.nodes.NewStoredContinuationNode;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaFrame;
import com.oracle.svm.core.stack.JavaFrames;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.ContinuationInternals;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.Target_jdk_internal_vm_Continuation;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.word.Word;

/** Helper for allocating and accessing {@link StoredContinuation} instances. */
public final class StoredContinuationAccess {
    private StoredContinuationAccess() {
    }

    private static StoredContinuation allocate(int framesSize) {
        // Using Word[] to ensure that words are properly aligned.
        int nwords = Integer.divideUnsigned(framesSize, ConfigurationValues.getTarget().wordSize);
        assert nwords * ConfigurationValues.getTarget().wordSize == framesSize;
        /*
         * There is no need to zero the array part (i.e., the stack data) of the StoredContinuation,
         * because the GC won't visit it if StoredContinuation.ip is null.
         */
        StoredContinuation s = (StoredContinuation) NewStoredContinuationNode.allocate(StoredContinuation.class, Word.class, nwords);
        assert getFramesSizeInBytes(s) == framesSize;
        return s;
    }

    @NodeIntrinsic(ArrayLengthNode.class)
    private static native int arrayLength(StoredContinuation s);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getSizeInBytes(StoredContinuation s) {
        return arrayLength(s) * ConfigurationValues.getTarget().wordSize;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getFramesSizeInBytes(StoredContinuation s) {
        return getSizeInBytes(s);
    }

    @Uninterruptible(reason = "Prevent GC during accesses via object address.", callerMustBe = true)
    public static Pointer getFramesStart(StoredContinuation s) {
        int layout = KnownIntrinsics.readHub(s).getLayoutEncoding();
        UnsignedWord baseOffset = LayoutEncoding.getArrayBaseOffset(layout);
        return Word.objectToUntrackedPointer(s).add(baseOffset);
    }

    @Uninterruptible(reason = "Prevent GC during accesses via object address.", callerMustBe = true)
    public static Pointer getFramesEnd(StoredContinuation s) {
        return getFramesStart(s).add(getFramesSizeInBytes(s));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodePointer getIP(StoredContinuation s) {
        return s.ip;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isInitialized(StoredContinuation s) {
        return s.ip.isNonNull();
    }

    public static int allocateToYield(Target_jdk_internal_vm_Continuation c, Pointer baseSp, Pointer sp, CodePointer ip) {
        assert baseSp.isNonNull() && sp.isNonNull() && ip.isNonNull();

        int framesSize = UnsignedUtils.safeToInt(baseSp.subtract(sp));
        StoredContinuation instance = allocate(framesSize);
        fillUninterruptibly(instance, ip, sp, framesSize);
        ContinuationInternals.setStoredContinuation(c, instance);
        return ContinuationSupport.FREEZE_OK;
    }

    @Uninterruptible(reason = "Prevent modifications to the stack while initializing instance and copying frames.")
    private static void fillUninterruptibly(StoredContinuation stored, CodePointer ip, Pointer sp, int size) {
        UnmanagedMemoryUtil.copyWordsForward(sp, getFramesStart(stored), Word.unsigned(size));
        setIP(stored, ip);
        afterFill(stored);
    }

    @Uninterruptible(reason = "Prevent modifications to the stack while initializing instance.")
    private static void afterFill(StoredContinuation stored) {
        /*
         * Since its allocation, our StoredContinuation could have already been promoted to the old
         * generation and some references we just copied might point to the young generation and
         * need to be added to the remembered set.
         */
        // Drop type info to not trigger compiler assertions about StoredContinuation in barriers
        Object opaque = GraalDirectives.opaque(stored);
        Heap.getHeap().dirtyAllReferencesOf(opaque);
    }

    public static StoredContinuation clone(StoredContinuation cont) {
        StoredContinuation clone = allocate(getFramesSizeInBytes(cont));
        Object preparedData = ImageSingletons.lookup(ContinuationSupport.class).prepareCopy(cont);
        return fillCloneUninterruptibly(cont, clone, preparedData);
    }

    @Uninterruptible(reason = "Prevent garbage collection while initializing instance and copying frames.")
    private static StoredContinuation fillCloneUninterruptibly(StoredContinuation cont, StoredContinuation clone, Object preparedData) {
        CodePointer ip = ImageSingletons.lookup(ContinuationSupport.class).copyFrames(cont, clone, preparedData);
        setIP(clone, ip);
        afterFill(clone);
        return clone;
    }

    @Uninterruptible(reason = "Prevent that the GC sees a partially initialized StoredContinuation.", callerMustBe = true)
    private static void setIP(StoredContinuation cont, CodePointer ip) {
        /*
         * Once the ip is initialized, the GC may visit the object at any time (i.e., even while we
         * are still executing uninterruptible code). Therefore, we must ensure that the store to
         * the ip is only visible after all the stores that fill in the stack data. To guarantee
         * that, we issue a STORE_STORE memory barrier before setting the ip.
         */
        MembarNode.memoryBarrier(MembarNode.FenceKind.ALLOCATION_INIT, LocationIdentity.INIT_LOCATION);
        cont.ip = ip;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean shouldWalkContinuation(StoredContinuation s) {
        /*
         * StoredContinuations in the image heap are read-only and should not be visited. They may
         * contain unresolved uncompressed references which are patched only on a platform thread's
         * stack before resuming the continuation. We should typically not see such objects during
         * GC, but they might be visited due to card marking when they are near an object which has
         * been modified at runtime.
         */
        return !Heap.getHeap().isInImageHeap(s);
    }

    @AlwaysInline("De-virtualize calls to ObjectReferenceVisitor")
    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    public static void walkReferences(StoredContinuation s, ObjectReferenceVisitor visitor) {
        if (!shouldWalkContinuation(s)) {
            return;
        }

        JavaStackWalk walk = StackValue.get(JavaStackWalker.sizeOfJavaStackWalk());
        JavaStackWalker.initializeForContinuation(walk, s);

        while (JavaStackWalker.advanceForContinuation(walk, s)) {
            JavaFrame frame = JavaStackWalker.getCurrentFrame(walk);
            VMError.guarantee(!JavaFrames.isEntryPoint(frame), "Entry point frames are not supported");
            VMError.guarantee(!JavaFrames.isUnknownFrame(frame), "Stack walk must not encounter unknown frame");
            VMError.guarantee(!Deoptimizer.checkIsDeoptimized(frame), "Deoptimized frames are not supported");

            UntetheredCodeInfo untetheredCodeInfo = frame.getIPCodeInfo();
            Object tether = CodeInfoAccess.acquireTether(untetheredCodeInfo);
            try {
                CodeInfo codeInfo = CodeInfoAccess.convert(untetheredCodeInfo, tether);
                walkFrameReferences(frame, codeInfo, visitor, s);
            } finally {
                CodeInfoAccess.releaseTether(untetheredCodeInfo, tether);
            }
        }
    }

    @AlwaysInline("De-virtualize calls to visitor.")
    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    public static void walkFrames(StoredContinuation s, ContinuationStackFrameVisitor visitor, ContinuationStackFrameVisitorData data) {
        if (!shouldWalkContinuation(s)) {
            return;
        }

        JavaStackWalk walk = StackValue.get(JavaStackWalker.sizeOfJavaStackWalk());
        JavaStackWalker.initializeForContinuation(walk, s);

        while (JavaStackWalker.advanceForContinuation(walk, s)) {
            JavaFrame frame = JavaStackWalker.getCurrentFrame(walk);
            VMError.guarantee(!JavaFrames.isEntryPoint(frame), "Entry point frames are not supported");
            VMError.guarantee(!JavaFrames.isUnknownFrame(frame), "Stack walk must not encounter unknown frame");
            VMError.guarantee(!Deoptimizer.checkIsDeoptimized(frame), "Deoptimized frames are not supported");

            UntetheredCodeInfo untetheredCodeInfo = frame.getIPCodeInfo();
            Object tether = CodeInfoAccess.acquireTether(untetheredCodeInfo);
            try {
                CodeInfo codeInfo = CodeInfoAccess.convert(untetheredCodeInfo, tether);
                NonmovableArray<Byte> referenceMapEncoding = CodeInfoAccess.getStackReferenceMapEncoding(codeInfo);
                long referenceMapIndex = frame.getReferenceMapIndex();
                if (referenceMapIndex != ReferenceMapIndex.NO_REFERENCE_MAP) {
                    visitor.visitFrame(data, frame.getSP(), referenceMapEncoding, referenceMapIndex, visitor);
                }
            } finally {
                CodeInfoAccess.releaseTether(untetheredCodeInfo, tether);
            }
        }
    }

    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    public static void walkFrameReferences(JavaFrame frame, CodeInfo codeInfo, ObjectReferenceVisitor visitor, Object holderObject) {
        NonmovableArray<Byte> referenceMapEncoding = CodeInfoAccess.getStackReferenceMapEncoding(codeInfo);
        long referenceMapIndex = frame.getReferenceMapIndex();
        if (referenceMapIndex != ReferenceMapIndex.NO_REFERENCE_MAP) {
            CodeReferenceMapDecoder.walkOffsetsFromPointer(frame.getSP(), referenceMapEncoding, referenceMapIndex, visitor, holderObject);
        }
    }

    public abstract static class ContinuationStackFrameVisitor {
        @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
        public abstract void visitFrame(ContinuationStackFrameVisitorData data, Pointer sp, NonmovableArray<Byte> referenceMapEncoding, long referenceMapIndex, ContinuationStackFrameVisitor visitor);
    }

    @RawStructure
    public interface ContinuationStackFrameVisitorData extends PointerBase {
    }

    @SuppressWarnings("unused")
    private static final class PreemptVisitor extends StackFrameVisitor {
        private final Pointer endSP;
        private boolean startFromNextFrame = false;

        Pointer leafSP;
        CodePointer leafIP;
        int preemptStatus = ContinuationSupport.FREEZE_OK;

        PreemptVisitor(Pointer endSP) {
            this.endSP = endSP;
        }

        @Override
        protected boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo) {
            if (sp.aboveOrEqual(endSP)) {
                return false;
            }

            FrameInfoQueryResult frameInfo = CodeInfoTable.lookupCodeInfoQueryResult(codeInfo, ip).getFrameInfo();
            if (frameInfo.getSourceClass().equals(StoredContinuationAccess.class) && frameInfo.getSourceMethodName().equals("allocateToYield")) {
                // Continuation is already in the process of yielding, cancel preemption.
                preemptStatus = ContinuationSupport.FREEZE_YIELDING;
                return false;
            }

            if (leafSP.isNull()) {
                // Should start from the method calling `enterSlowPathSafepointCheck`.
                if (startFromNextFrame) {
                    leafSP = sp;
                    leafIP = ip;
                } else {
                    if (frameInfo.getSourceClass().equals(Safepoint.class) && frameInfo.getSourceMethodName().equals("enterSlowPathSafepointCheck")) {
                        startFromNextFrame = true;
                    }
                    return true;
                }
            }

            VMError.guarantee(CodeInfoAccess.isAOTImageCode(codeInfo));

            return true;
        }

        @Override
        protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptimizedFrame) {
            throw VMError.shouldNotReachHere("Continuations can't contain JIT compiled code.");
        }
    }
}
