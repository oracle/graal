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
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.SimpleCodeInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.nodes.SubstrateNewHybridInstanceNode;
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
    private static final int IP_OFFSET = 0; // instruction pointer of top frame
    private static final int FRAMES_OFFSET = IP_OFFSET + Long.BYTES;

    private StoredContinuationAccess() {
    }

    private static StoredContinuation allocate(int framesSize) {
        // Using long[] to ensure that words are properly aligned.
        int nlongs = Integer.divideUnsigned(FRAMES_OFFSET + framesSize, Long.BYTES);
        StoredContinuation s = (StoredContinuation) SubstrateNewHybridInstanceNode.allocate(StoredContinuation.class, long.class, nlongs);
        assert getFramesSizeInBytes(s) == framesSize;
        return s;
    }

    @NodeIntrinsic(ArrayLengthNode.class)
    private static native int arrayLength(StoredContinuation s);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getSizeInBytes(StoredContinuation s) {
        return arrayLength(s) * Long.BYTES;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getFramesSizeInBytes(StoredContinuation s) {
        return getSizeInBytes(s) - FRAMES_OFFSET;
    }

    @Uninterruptible(reason = "Prevent GC during accesses via object address.", callerMustBe = true)
    private static Pointer arrayAddress(StoredContinuation s) {
        int layout = KnownIntrinsics.readHub(s).getLayoutEncoding();
        UnsignedWord baseOffset = LayoutEncoding.getArrayBaseOffset(layout);
        return Word.objectToUntrackedPointer(s).add(baseOffset);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodePointer getIP(StoredContinuation s) {
        return arrayAddress(s).readWord(IP_OFFSET);
    }

    @Uninterruptible(reason = "Prevent GC during accesses via object address.", callerMustBe = true)
    public static Pointer getFramesStart(StoredContinuation s) {
        return arrayAddress(s).add(FRAMES_OFFSET);
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
        arrayAddress(stored).writeWord(IP_OFFSET, ip);
        UnmanagedMemoryUtil.copy(sp, getFramesStart(stored), WordFactory.unsigned(size));
        afterFill(stored);
    }

    @Uninterruptible(reason = "Prevent modifications to the stack while initializing instance.")
    private static void afterFill(StoredContinuation stored) {
        /*
         * Since its allocation, our StoredContinuation could have already been promoted to the old
         * generation and some references we just copied might point to the young generation and
         * need to be added to the remembered set.
         *
         * To support precise marking and pre-write barriers, we need to check first if the object
         * needs barriers, then, on a slow path, individually copy references from stack frames.
         */
        // Drop type info to not trigger compiler assertions about StoredContinuation in barriers
        Object opaque = GraalDirectives.opaque(stored);
        Heap.getHeap().dirtyAllReferencesOf(opaque);
    }

    public static StoredContinuation clone(StoredContinuation cont) {
        StoredContinuation clone = allocate(getFramesSizeInBytes(cont));
        return fillCloneUninterruptibly(cont, clone);
    }

    @Uninterruptible(reason = "Prevent garbage collection while initializing instance and copying frames.")
    private static StoredContinuation fillCloneUninterruptibly(StoredContinuation cont, StoredContinuation clone) {
        CodePointer ip = ImageSingletons.lookup(ContinuationSupport.class).copyFrames(cont, clone);
        // copyFrames() above may do something interruptible before uninterruptibly copying frames,
        // so set IP only afterwards so that the object is considered uninitialized until then.
        arrayAddress(clone).writeWord(IP_OFFSET, ip);
        afterFill(clone);
        return clone;
    }

    /** Derived from {@link InstanceReferenceMapDecoder#walkOffsetsFromPointer}. */
    @AlwaysInline("De-virtualize calls to ObjectReferenceVisitor")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean walkReferences(Pointer baseAddress, ObjectReferenceVisitor visitor, Object holderObject) {
        assert !Heap.getHeap().isInImageHeap(baseAddress);

        StoredContinuation s = (StoredContinuation) holderObject;
        assert baseAddress.equal(Word.objectToUntrackedPointer(holderObject));

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
                walkFrameReferences(walk, codeInfo, queryResult, visitor, holderObject);
            } finally {
                CodeInfoAccess.releaseTether(untetheredCodeInfo, tether);
            }
        } while (JavaStackWalker.continueWalk(walk, queryResult, null));

        return true;
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
        Pointer endSp = arrayAddress(s).add(getSizeInBytes(s));

        JavaStackWalker.initWalk(walk, startSp, endSp, startIp);
        walk.setAnchor(WordFactory.nullPointer()); // never use an anchor of this platform thread
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void walkFrameReferences(JavaStackWalk walk, CodeInfo codeInfo, SimpleCodeInfoQueryResult queryResult, ObjectReferenceVisitor visitor, Object holderObject) {
        Pointer sp = walk.getSP();
        CodePointer ip = walk.getPossiblyStaleIP();
        if (codeInfo.isNull()) {
            throw JavaStackWalker.reportUnknownFrameEncountered(sp, ip, null);
        }
        VMError.guarantee(codeInfo.equal(CodeInfoTable.getImageCodeInfo()));
        VMError.guarantee(Deoptimizer.checkDeoptimized(sp) == null);

        CodeInfoAccess.lookupCodeInfo(codeInfo, CodeInfoAccess.relativeIP(codeInfo, ip), queryResult);

        NonmovableArray<Byte> referenceMapEncoding = CodeInfoAccess.getStackReferenceMapEncoding(codeInfo);
        long referenceMapIndex = queryResult.getReferenceMapIndex();
        if (referenceMapIndex != ReferenceMapIndex.NO_REFERENCE_MAP) {
            CodeReferenceMapDecoder.walkOffsetsFromPointer(sp, referenceMapEncoding, referenceMapIndex, visitor, holderObject);
        }
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
