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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.IsolateThread;
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
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.graal.nodes.SubstrateNewHybridInstanceNode;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.Continuation;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaKind;

/**
 * Helper class to access a {@link StoredContinuation}, the payload layout of which is:
 *
 * <pre>
 *      +-0x0-|-0x4-|-0x8-|-0xc-+
 * 0x00 | (1) | (a1)|(b1) | (a2)| 1) number of frames n; per-frame headers: a) size of frame,
 *      +-----+-----+-----+-----+                                           b) reference map index of frame
 * 0x10 |(b2) | ..  | ..  | (an)|
 *      +-----+-----+-----+-----+
 * 0x20 |(bn) | c o n t i n u o |
 *  .   :  u s    f r a m e     :
 * 0xnn |  d a t a  +-----------+
 *      +-----------+
 * </pre>
 */
public final class StoredContinuationImpl {
    public interface RawFrameVisitor {
        boolean visitRawFrame(int index, Pointer sp);
    }

    private static final int FRAME_COUNT_OFFSET = 0;
    private static final int FRAME_METAS_START_OFFSET = 4;
    private static final int FRAME_META_SIZE = 8;
    private static final int SIZE_OFFSET_IN_FRAME_META = 0;
    private static final int REFERENCE_MAP_INDEX_OFFSET_IN_FRAME_META = 4;

    private static StoredContinuation allocate(int payloadSize) {
        StoredContinuation f = (StoredContinuation) SubstrateNewHybridInstanceNode.allocate(StoredContinuation.class, byte.class, payloadSize);
        assert readPayloadSize(f) == payloadSize;
        return f;
    }

    @Uninterruptible(reason = "read StoredContinuation")
    private static boolean checkPayloadOffset(StoredContinuation f, int offset) {
        assert offset % 4 == 0;
        assert offset >= 0;
        assert offset < readPayloadSize(f);
        return true;
    }

    @Uninterruptible(reason = "read StoredContinuation")
    private static int readPayloadInt(StoredContinuation f, int offset) {
        assert checkPayloadOffset(f, offset);
        return payloadLocation(f).readInt(offset);
    }

    @Uninterruptible(reason = "write StoredContinuation")
    private static void writePayloadInt(StoredContinuation f, int offset, int value) {
        assert checkPayloadOffset(f, offset);
        payloadLocation(f).writeInt(offset, value);
    }

    @NodeIntrinsic(ArrayLengthNode.class)
    private static native int readPayloadSize(StoredContinuation f);

    // size of raw frame
    @Uninterruptible(reason = "read StoredContinuation")
    public static int readAllFrameSize(StoredContinuation f) {
        return readPayloadSize(f) - readFrameMetasSize(f);
    }

    @Uninterruptible(reason = "read StoredContinuation")
    public static int readFrameCount(StoredContinuation f) {
        return readPayloadInt(f, FRAME_COUNT_OFFSET);
    }

    @Uninterruptible(reason = "read StoredContinuation")
    private static int readFrameMetasSize(StoredContinuation f) {
        return FRAME_METAS_START_OFFSET + readFrameCount(f) * FRAME_META_SIZE;
    }

    @Uninterruptible(reason = "read StoredContinuation")
    public static int readFrameSize(StoredContinuation f, int frameIndex) {
        return readPayloadInt(f, FRAME_METAS_START_OFFSET + frameIndex * FRAME_META_SIZE + SIZE_OFFSET_IN_FRAME_META);
    }

    @Uninterruptible(reason = "read StoredContinuation")
    private static int readReferenceMapIndex(StoredContinuation f, int frameIndex) {
        return readPayloadInt(f, FRAME_METAS_START_OFFSET + frameIndex * FRAME_META_SIZE + REFERENCE_MAP_INDEX_OFFSET_IN_FRAME_META);
    }

    @Uninterruptible(reason = "read/write StoredContinuation")
    private static Pointer payloadLocation(StoredContinuation f) {
        int layout = KnownIntrinsics.readHub(f).getLayoutEncoding();
        UnsignedWord baseOffset = LayoutEncoding.getArrayBaseOffset(layout);
        return Word.objectToUntrackedPointer(f).add(baseOffset);
    }

    @Uninterruptible(reason = "read/write StoredContinuation")
    public static Pointer payloadFrameStart(StoredContinuation f) {
        return payloadLocation(f).add(readFrameMetasSize(f));
    }

    public static int allocateFromCurrentStack(Continuation cont, Pointer rootSp, Pointer leafSp, CodePointer leafIp) {
        return allocateFromStack(cont, rootSp, leafSp, leafIp, WordFactory.nullPointer());
    }

    public static int allocateFromForeignStack(Continuation cont, Pointer rootSp, IsolateThread thread) {
        return allocateFromStack(cont, rootSp, WordFactory.nullPointer(), WordFactory.nullPointer(), thread);
    }

    /**
     * Return value follows the semantic of preempt status and pinned reason, 0 means yielding
     * successfully.
     */
    private static int allocateFromStack(Continuation cont, Pointer rootSp, Pointer leafSP, CodePointer leafIp, IsolateThread otherThread) {
        boolean isCurrentThread = leafSP.isNonNull();

        YieldVisitor visitor = new YieldVisitor(rootSp, leafSP, leafIp);
        Pointer resultLeafSP = leafSP;

        if (isCurrentThread) {
            VMError.guarantee(otherThread.isNull());
            JavaStackWalker.walkCurrentThread(leafSP, visitor);
        } else {
            JavaStackWalker.walkThread(otherThread, visitor);
            resultLeafSP = visitor.leafSP;
        }

        if (visitor.preemptStatus != Continuation.YIELD_SUCCESS) {
            return visitor.preemptStatus;
        }

        if (!isCurrentThread) {
            cont.setIP(visitor.leafIP);
        }
        VMError.guarantee(resultLeafSP.isNonNull());

        int frameCount = visitor.frameSizeReferenceMapIndex.size();
        int payloadSize = FRAME_METAS_START_OFFSET + FRAME_META_SIZE * frameCount + UnsignedUtils.safeToInt(rootSp.subtract(resultLeafSP));

        cont.stored = allocate(payloadSize);
        /*
         * At this point, the object contains all zeros, which includes the frame count, so if we
         * are interrupted by a GC, it will not attempt to make sense of it. Only at the end of this
         * method we uninterruptibly initialize the frame count and copy the stack frames.
         */
        int allFrameSize = 0;
        for (int i = 0; i < frameCount; i++) {
            Pair<Integer, Integer> frameSizeRefMapInxPair = visitor.frameSizeReferenceMapIndex.get(i);
            writePayloadInt(cont.stored, FRAME_METAS_START_OFFSET + i * FRAME_META_SIZE + SIZE_OFFSET_IN_FRAME_META,
                            frameSizeRefMapInxPair.getLeft());
            writePayloadInt(cont.stored, FRAME_METAS_START_OFFSET + i * FRAME_META_SIZE + REFERENCE_MAP_INDEX_OFFSET_IN_FRAME_META,
                            frameSizeRefMapInxPair.getRight());
            allFrameSize += frameSizeRefMapInxPair.getLeft();
        }
        fillUninterruptibly(cont.stored, resultLeafSP, allFrameSize, frameCount);
        return Continuation.YIELD_SUCCESS;
    }

    @Uninterruptible(reason = "Prevent modifications to the stack while copying.")
    private static void fillUninterruptibly(StoredContinuation stored, Pointer sp, int size, int frameCount) {
        Pointer p = payloadLocation(stored);
        p.writeInt(FRAME_COUNT_OFFSET, frameCount);

        VMError.guarantee(size == readAllFrameSize(stored));
        Pointer frameStart = payloadFrameStart(stored);
        UnmanagedMemoryUtil.copy(sp, frameStart, WordFactory.unsigned(size));

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

    /**
     * Copied from {@link InstanceReferenceMapDecoder#walkOffsetsFromPointer}, walk references
     * stored in {@link StoredContinuation} object.
     */
    @AlwaysInline("de-virtualize calls to visitors")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean walkStoredContinuationFromPointer(Pointer baseAddress, RawFrameVisitor frameVisitor, ObjectReferenceVisitor refVisitor, Object holderObject) {
        StoredContinuation f = (StoredContinuation) holderObject;
        assert baseAddress.equal(Word.objectToUntrackedPointer(holderObject));

        int frameCount = StoredContinuationImpl.readFrameCount(f);
        Pointer curFrame = StoredContinuationImpl.payloadFrameStart(f);
        int frameIndex = 0;

        NonmovableArray<Byte> referenceMapEncoding = CodeInfoTable.getImageCodeCache().getStackReferenceMapEncoding();
        for (; frameIndex < frameCount; frameIndex++) {
            int frameSize = StoredContinuationImpl.readFrameSize(f, frameIndex);
            int referenceMapIndex = StoredContinuationImpl.readReferenceMapIndex(f, frameIndex);

            if (frameVisitor != null && !frameVisitor.visitRawFrame(frameIndex, curFrame)) {
                return false;
            }

            if (refVisitor != null && !CodeReferenceMapDecoder.walkOffsetsFromPointer(curFrame, referenceMapEncoding, referenceMapIndex, refVisitor, holderObject)) {
                return false;
            }

            curFrame = curFrame.add(frameSize);
        }

        assert frameIndex == frameCount;
        // NOTE: frameCount can be 0 if the object has just been allocated but not filled yet
        assert frameCount == 0 || curFrame.subtract(payloadLocation(f)).equal(readPayloadSize(f));

        return true;
    }

    private static class YieldVisitor extends StackFrameVisitor {
        int preemptStatus = Continuation.YIELD_SUCCESS;

        Pointer rootSP;
        Pointer leafSP;
        CodePointer leafIP;

        List<Pair<Integer, Integer>> frameSizeReferenceMapIndex = new ArrayList<>();
        final NonmovableArray<Byte> expectedReferenceMap;

        private static boolean startFromNextFrame = false;

        YieldVisitor(Pointer rootSp, Pointer verifyLeafSp, CodePointer leafIp) {
            if (verifyLeafSp.isNonNull() && !verifyLeafSp.belowThan(rootSp)) {
                throw VMError.shouldNotReachHere(String.format("expecting leafSp (%x) < rootSp (%x)", verifyLeafSp.rawValue(), rootSp.rawValue()));
            }
            this.rootSP = rootSp;
            this.leafSP = verifyLeafSp;
            this.leafIP = leafIp;

            this.expectedReferenceMap = CodeInfoAccess.getStackReferenceMapEncoding(CodeInfoTable.getImageCodeInfo());
        }

        @SuppressWarnings("hiding")
        @Override
        protected boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame) {
            FrameInfoQueryResult frameInfo = CodeInfoTable.lookupCodeInfoQueryResult(codeInfo, ip).getFrameInfo();
            if (frameInfo.getSourceClass().equals(StoredContinuationImpl.class) && frameInfo.getSourceMethodName().equals("allocateFromStack")) {
                preemptStatus = Continuation.YIELDING;
                return false;
            }

            if (leafSP.isNull()) {
                // We're preempting a continuation, walk starts from a safepoint.
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
            } else if (frameSizeReferenceMapIndex.isEmpty()) {
                // yielding current thread,
                // `leafSP` and `leafIP` are used for verification purpose.
                VMError.guarantee(leafSP.equal(sp));
                VMError.guarantee(leafIP.equal(ip));
            }

            VMError.guarantee(expectedReferenceMap.equal(CodeInfoAccess.getStackReferenceMapEncoding(codeInfo)));

            long relIp = CodeInfoAccess.relativeIP(codeInfo, ip);
            int frameSize = TypeConversion.asU4(CodeInfoAccess.lookupTotalFrameSize(codeInfo, relIp));
            int referenceMapIndex = TypeConversion.asS4(CodeInfoAccess.lookupStackReferenceMapIndex(codeInfo, relIp));
            frameSizeReferenceMapIndex.add(Pair.create(frameSize, referenceMapIndex));

            Pointer currentFrameEnd = sp.add(frameSize);
            VMError.guarantee(currentFrameEnd.belowOrEqual(rootSP));

            return currentFrameEnd.notEqual(rootSP);
        }
    }

    @Fold
    protected static int getByteArrayBaseOffset() {
        return ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Byte);
    }
}
