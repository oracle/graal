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

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.SimpleCodeInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.nodes.NewStoredContinuationNode;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.Continuation;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodePointer getIP(StoredContinuation s) {
        return s.ip;
    }

    public static int allocateToYield(Continuation c, Pointer baseSp, Pointer sp, CodePointer ip) {
        assert sp.isNonNull() && ip.isNonNull();
        return allocateFromStack(c, baseSp, sp, ip, WordFactory.nullPointer());
    }

    public static int allocateToPreempt(Continuation c, Pointer baseSp, IsolateThread targetThread) {
        return allocateFromStack(c, baseSp, WordFactory.nullPointer(), WordFactory.nullPointer(), targetThread);
    }

    private static int allocateFromStack(Continuation cont, Pointer baseSp, Pointer sp, CodePointer ip, IsolateThread targetThread) {
        boolean yield = sp.isNonNull();
        assert yield == ip.isNonNull() && yield == targetThread.isNull();
        assert baseSp.isNonNull();

        Pointer startSp = sp;
        CodePointer startIp = ip;
        if (!yield) {
            PreemptVisitor visitor = new PreemptVisitor(baseSp);
            JavaStackWalker.walkThread(targetThread, visitor);
            if (visitor.preemptStatus != Continuation.FREEZE_OK) {
                return visitor.preemptStatus;
            }
            startSp = visitor.leafSP;
            startIp = visitor.leafIP;
        }

        VMError.guarantee(startSp.isNonNull());

        int framesSize = UnsignedUtils.safeToInt(baseSp.subtract(startSp));
        StoredContinuation instance = allocate(framesSize);
        fillUninterruptibly(instance, startIp, startSp, framesSize);
        cont.stored = instance;
        return Continuation.FREEZE_OK;
    }

    @Uninterruptible(reason = "Prevent modifications to the stack while initializing instance and copying frames.")
    private static void fillUninterruptibly(StoredContinuation stored, CodePointer ip, Pointer sp, int size) {
        UnmanagedMemoryUtil.copyWordsForward(sp, getFramesStart(stored), WordFactory.unsigned(size));
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
        MembarNode.memoryBarrier(MembarNode.FenceKind.ALLOCATION_INIT);
        cont.ip = ip;
    }

    @AlwaysInline("De-virtualize calls to ObjectReferenceVisitor")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean walkReferences(Object obj, ObjectReferenceVisitor visitor) {
        assert !Heap.getHeap().isInImageHeap(obj) : "StoredContinuations in the image heap are read-only and don't need to be visited";

        StoredContinuation s = (StoredContinuation) obj;
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        if (!initWalk(s, walk)) {
            return true; // uninitialized, ignore
        }

        SimpleCodeInfoQueryResult queryResult = StackValue.get(SimpleCodeInfoQueryResult.class);
        do {
            UntetheredCodeInfo untetheredCodeInfo = walk.getIPCodeInfo();
            Object tether = CodeInfoAccess.acquireTether(untetheredCodeInfo);
            try {
                CodeInfo codeInfo = CodeInfoAccess.convert(untetheredCodeInfo, tether);
                walkFrameReferences(walk, codeInfo, queryResult, visitor, s);
            } finally {
                CodeInfoAccess.releaseTether(untetheredCodeInfo, tether);
            }
        } while (JavaStackWalker.continueWalk(walk, queryResult, null));

        return true;
    }

    @AlwaysInline("De-virtualize calls to visitor.")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean walkFrames(StoredContinuation s, ContinuationStackFrameVisitor visitor, ContinuationStackFrameVisitorData data) {
        assert !Heap.getHeap().isInImageHeap(s) : "StoredContinuations in the image heap are read-only and don't need to be visited";

        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        if (!initWalk(s, walk)) {
            return true; // uninitialized, ignore
        }

        SimpleCodeInfoQueryResult queryResult = StackValue.get(SimpleCodeInfoQueryResult.class);
        do {
            UntetheredCodeInfo untetheredCodeInfo = walk.getIPCodeInfo();
            Object tether = CodeInfoAccess.acquireTether(untetheredCodeInfo);
            try {
                CodeInfo codeInfo = CodeInfoAccess.convert(untetheredCodeInfo);
                queryFrameCodeInfo(walk, codeInfo, queryResult);

                NonmovableArray<Byte> referenceMapEncoding = CodeInfoAccess.getStackReferenceMapEncoding(codeInfo);
                long referenceMapIndex = queryResult.getReferenceMapIndex();
                if (referenceMapIndex != ReferenceMapIndex.NO_REFERENCE_MAP) {
                    visitor.visitFrame(data, walk.getSP(), referenceMapEncoding, referenceMapIndex, visitor);
                }
            } finally {
                CodeInfoAccess.releaseTether(untetheredCodeInfo, tether);
            }
        } while (JavaStackWalker.continueWalk(walk, queryResult, null));

        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void queryFrameCodeInfo(JavaStackWalk walk, CodeInfo codeInfo, SimpleCodeInfoQueryResult queryResult) {
        Pointer sp = walk.getSP();
        CodePointer ip = walk.getPossiblyStaleIP();

        if (codeInfo.isNull()) {
            throw JavaStackWalker.reportUnknownFrameEncountered(sp, ip, null);
        }
        VMError.guarantee(codeInfo.equal(CodeInfoTable.getImageCodeInfo()));
        VMError.guarantee(Deoptimizer.checkDeoptimized(sp) == null);

        CodeInfoAccess.lookupCodeInfo(codeInfo, CodeInfoAccess.relativeIP(codeInfo, ip), queryResult);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean initWalk(StoredContinuation s, JavaStackWalk walk) {
        CodePointer startIp = getIP(s);
        if (startIp.isNull()) {
            return false; // uninitialized
        }
        initWalk(s, walk, startIp);
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void initWalk(StoredContinuation s, JavaStackWalk walk, CodePointer startIp) {
        Pointer startSp = getFramesStart(s);
        Pointer endSp = getFramesStart(s).add(getSizeInBytes(s));
        JavaStackWalker.initWalkStoredContinuation(walk, startSp, endSp, startIp);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void walkFrameReferences(JavaStackWalk walk, CodeInfo codeInfo, SimpleCodeInfoQueryResult queryResult, ObjectReferenceVisitor visitor, Object holderObject) {
        queryFrameCodeInfo(walk, codeInfo, queryResult);

        NonmovableArray<Byte> referenceMapEncoding = CodeInfoAccess.getStackReferenceMapEncoding(codeInfo);
        long referenceMapIndex = queryResult.getReferenceMapIndex();
        if (referenceMapIndex != ReferenceMapIndex.NO_REFERENCE_MAP) {
            CodeReferenceMapDecoder.walkOffsetsFromPointer(walk.getSP(), referenceMapEncoding, referenceMapIndex, visitor, holderObject);
        }
    }

    public abstract static class ContinuationStackFrameVisitor {
        public abstract void visitFrame(ContinuationStackFrameVisitorData data, Pointer sp, NonmovableArray<Byte> referenceMapEncoding, long referenceMapIndex, ContinuationStackFrameVisitor visitor);
    }

    @RawStructure
    public interface ContinuationStackFrameVisitorData extends PointerBase {
    }

    private static final class PreemptVisitor extends StackFrameVisitor {
        private final Pointer endSP;
        private boolean startFromNextFrame = false;

        Pointer leafSP;
        CodePointer leafIP;
        int preemptStatus = Continuation.FREEZE_OK;

        PreemptVisitor(Pointer endSP) {
            this.endSP = endSP;
        }

        @Override
        protected boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame) {
            if (sp.aboveOrEqual(endSP)) {
                return false;
            }

            FrameInfoQueryResult frameInfo = CodeInfoTable.lookupCodeInfoQueryResult(codeInfo, ip).getFrameInfo();
            if (frameInfo.getSourceClass().equals(StoredContinuationAccess.class) && frameInfo.getSourceMethodName().equals("allocateToYield")) {
                // Continuation is already in the process of yielding, cancel preemption.
                preemptStatus = Continuation.YIELDING;
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

            VMError.guarantee(codeInfo.equal(CodeInfoTable.getImageCodeInfo()));

            return true;
        }
    }
}
