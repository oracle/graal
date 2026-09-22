/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.ObjectAccess;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.genscavenge.AuxiliaryImageHeap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.stack.JavaFrame;
import com.oracle.svm.core.stack.JavaFrames;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;

/**
 * Copies stored continuation frames, typically to a platform thread's stack, or alternatively to a
 * newly allocated object.
 * <p>
 * Stored continuations in the auxiliary image heap need special treatment: their uncompressed
 * references were compressed while persisting so that they become base-relative, therefore we need
 * to uncompress these references again here, see also {@link AuxiliaryImagePersistence}.
 * <p>
 * Also, when loading an auxiliary image in a different process, AOT-compiled code can have been
 * loaded at a different address, particularly with shared library images. Therefore, we also need to
 * patch code pointers and return addresses.
 * <p>
 * We do this lazily while copying to the platform thread's stack because we don't know if a stored
 * continuation will actually be continued, to avoid copy-on-write on pages which will likely be
 * accessed only once, and to avoid races from lazily patching a stored continuation in more than
 * one thread at the same time.
 * <p>
 * Since this makes stored continuations immutable and garbage collection ignores immutable image
 * heap objects, having invalid references and code pointers in them is not an issue.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
final class PersistedContinuationSupport extends ContinuationSupport {
    @Platforms(Platform.HOSTED_ONLY.class)
    PersistedContinuationSupport() {
    }

    @Override
    public Object prepareCopy(StoredContinuation storedCont) {
        if (!AuxiliaryImageHeap.isPresent() || !AuxiliaryImageHeap.singleton().containsObject(Word.objectToUntrackedPointer(storedCont))) {
            return super.prepareCopy(storedCont);
        }

        Word imageCodeOffset = getImageCodeOffset();
        CodePointer newIP = (CodePointer) (Pointer) imageCodeOffset.add((Word) (Pointer) StoredContinuationAccess.getIP(storedCont));
        FrameMap map = new FrameMap(newIP, StoredContinuationAccess.getFramesSizeInBytes(storedCont));
        FrameReferenceVisitor visitor = new FrameReferenceVisitor(map);
        scan(storedCont, map, visitor, imageCodeOffset);
        return map;
    }

    @Override
    @Uninterruptible(reason = "Copies stack frames containing references.")
    protected CodePointer copyFrames(StoredContinuation storedCont, Pointer to, Object preparedData) {
        if (!AuxiliaryImageHeap.isPresent() || !AuxiliaryImageHeap.singleton().containsObject(Word.objectToUntrackedPointer(storedCont))) {
            return super.copyFrames(storedCont, to, preparedData);
        }

        Word imageCodeOffset = getImageCodeOffset();
        return doCopy(storedCont, to, imageCodeOffset, preparedData);
    }

    @Uninterruptible(reason = "Copies stack frames containing references.")
    private CodePointer doCopy(StoredContinuation storedCont, Pointer to, Word imageCodeOffset, Object preparedData) {
        super.copyFrames(storedCont, to, preparedData);

        FrameMap frameMap = (FrameMap) preparedData;
        Pointer frameData = StoredContinuationAccess.getFramesStart(storedCont);
        int offset = frameMap.nextActionOffset(0);
        while (offset >= 0) {
            int entry = frameMap.getEntry(offset);
            if (entry == FrameMap.ENTRY_CODE_POINTER) {
                Pointer address = frameData.readWord(offset);
                to.writeWord(offset, address.add(imageCodeOffset));
            } else if (entry == FrameMap.ENTRY_UNCOMPRESSED_REF) {
                Object target = ObjectAccess.readObject(frameData.add(offset), 0);
                to.writeObject(offset, target);
            }
            offset = frameMap.nextActionOffset(offset + FrameMap.bytesCoveredByEntry());
        }
        return frameMap.newIP;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Word getImageCodeOffset() {
        CodePointer imageCodeStart = CodeInfoTable.getImageCodeCacheForLayer(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER).getCodeStart();
        return ((Word) (Pointer) imageCodeStart).subtract((Word) (Pointer) AuxiliaryImageLoader.getLoadedMetadata().originalImageCodeStart);
    }

    @Override
    @Uninterruptible(reason = "Copies stack frames containing references.")
    public CodePointer copyFrames(StoredContinuation fromCont, StoredContinuation toCont, Object preparedData) {
        if (!AuxiliaryImageHeap.isPresent() || !AuxiliaryImageHeap.singleton().containsObject(Word.objectToUntrackedPointer(fromCont))) {
            return super.copyFrames(fromCont, toCont, preparedData);
        }

        Word imageCodeOffset = getImageCodeOffset();
        Pointer to = StoredContinuationAccess.getFramesStart(toCont);
        return doCopy(fromCont, to, imageCodeOffset, preparedData);
    }

    /**
     * Scan the stack of the stored continuation for compressed references and return addresses that
     * need to be patched later on. Note that this code needs to do a fully custom stack walk.
     */
    @Uninterruptible(reason = "Uses pointers to access StoredContinuation frames.")
    private static void scan(StoredContinuation storedCont, FrameMap map, FrameReferenceVisitor visitor, Word imageCodeOffset) {
        JavaStackWalk walk = StackValue.get(JavaStackWalker.sizeOfJavaStackWalk());
        JavaStackWalker.initializeForContinuation(walk, storedCont, map.newIP);
        assert JavaStackWalker.getFrameAnchor(walk).isNull();

        Pointer startSP = JavaStackWalker.getStartSP(walk);
        Pointer endSP = JavaStackWalker.getEndSP(walk);
        visitor.topSP = startSP;

        /* Start the walk (the IP of the first frame is already correct). */
        boolean hasFrame = JavaStackWalker.advanceForContinuation(walk, storedCont);
        assert hasFrame;

        JavaFrame frame = JavaStackWalker.getCurrentFrame(walk);
        assert frame.getSP() == startSP;
        assert frame.getIP() == map.newIP;

        /* Manually iterate over all other stack frames. */
        while (frame.getSP().belowThan(endSP)) {
            VMError.guarantee(!JavaFrames.isEntryPoint(frame), "Entry point frames are not supported");
            VMError.guarantee(!JavaFrames.isUnknownFrame(frame), "Stack walk must not encounter unknown frame");
            VMError.guarantee(!Deoptimizer.checkIsDeoptimized(frame), "Deoptimized frames are not supported");

            UntetheredCodeInfo untetheredCodeInfo = frame.getIPCodeInfo();
            Object tether = CodeInfoAccess.acquireTether(untetheredCodeInfo);
            try {
                CodeInfo codeInfo = CodeInfoAccess.convert(untetheredCodeInfo, tether);
                VMError.guarantee(codeInfo.equal(CodeInfoTable.getFirstImageCodeInfo()), "Currently, this supports only code of the first (or single) image CodeInfo. " +
                                "Support for multiple CodeInfo might require tracking where each of them has been mapped and will be mapped in a process that loads an auxiliary image.");
                StoredContinuationAccess.walkFrameReferences(frame, codeInfo, visitor, null);
            } finally {
                CodeInfoAccess.releaseTether(untetheredCodeInfo, tether);
            }

            Pointer callerSP = JavaFrames.getCallerSP(frame);
            UnsignedWord relativeIP = (UnsignedWord) FrameAccess.singleton().readReturnAddress(storedCont, callerSP);
            CodePointer newIP = (CodePointer) (Pointer) imageCodeOffset.add(relativeIP);

            UnsignedWord returnAddressOffset = FrameAccess.singleton().getReturnAddressLocation(storedCont, callerSP).subtract(startSP);
            map.mark(returnAddressOffset, FrameMap.ENTRY_CODE_POINTER, FrameAccess.returnAddressSize());

            JavaFrames.setData(frame, callerSP, newIP);
        }
    }

    /** Fixed-size bitmap which can be modified uninterruptibly. */
    static final class FrameMap {
        @Fold
        static int bytesCoveredByEntry() {
            return Math.min(SubstrateTarget.getWordSize(), FrameAccess.uncompressedReferenceSize());
        }

        static final int BITS_PER_ENTRY = 2; // must be power of 2
        static final int ENTRY_BITS_MASK = (BITS_PER_ENTRY << 1) - 1;
        static final int ENTRY_UNCOMPRESSED_REF = 0b01;
        static final int ENTRY_CODE_POINTER = 0b10;

        final int framesSizeInBytes;
        final CodePointer newIP;
        final long[] values;

        FrameMap(CodePointer newIP, int framesSizeInBytes) {
            this.newIP = newIP;
            this.framesSizeInBytes = framesSizeInBytes;

            int nbits = NumUtil.divideAndRoundUp(framesSizeInBytes, bytesCoveredByEntry()) * FrameMap.BITS_PER_ENTRY;
            int nlongs = NumUtil.divideAndRoundUp(nbits, Long.SIZE);
            this.values = new long[nlongs];
        }

        @Uninterruptible(reason = "Used while patching the stack frames.")
        int nextActionOffset(int offset) {
            assert offset >= 0 && offset % bytesCoveredByEntry() == 0;
            int bitIndex = offset / (bytesCoveredByEntry() / BITS_PER_ENTRY);
            int index = bitIndex / Long.SIZE;
            if (index >= values.length) {
                return -1;
            }
            long v = values[index] & (~0L << bitIndex); // NOTE: shift uses only lower 6 bits
            for (;;) {
                if (v != 0) {
                    return (index * Long.SIZE + Long.numberOfTrailingZeros(v)) / BITS_PER_ENTRY * bytesCoveredByEntry();
                }
                index++;
                if (index >= values.length) {
                    return -1;
                }
                v = values[index];
            }
        }

        @Uninterruptible(reason = "Used while copying the stack frames.")
        int getEntry(int offset) {
            assert offset >= 0 && offset < framesSizeInBytes;
            int bitIndex = offset / (bytesCoveredByEntry() / BITS_PER_ENTRY);
            int index = bitIndex / Long.SIZE;
            int shift = bitIndex % Long.SIZE;
            return (int) ((values[index] >>> shift) & ENTRY_BITS_MASK);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void mark(UnsignedWord offset, int value, int size) {
            assert size > 0 && size % bytesCoveredByEntry() == 0 && offset.add(size).belowOrEqual(framesSizeInBytes);
            markEntry(offset, value);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private void markEntry(UnsignedWord offset, long kind) {
            UnsignedWord bitIndex = offset.unsignedDivide(bytesCoveredByEntry() / BITS_PER_ENTRY);
            int index = UnsignedUtils.safeToInt(bitIndex.unsignedDivide(Long.SIZE));
            int shift = UnsignedUtils.safeToInt(bitIndex.unsignedRemainder(Long.SIZE));
            assert (values[index] & ((long) ENTRY_BITS_MASK << shift)) == 0;
            values[index] |= (kind << shift);
        }
    }

    private static final class FrameReferenceVisitor implements ObjectReferenceVisitor {
        private final FrameMap frameMap;
        Pointer topSP;

        FrameReferenceVisitor(FrameMap frameMap) {
            this.frameMap = frameMap;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count) {
            Pointer pos = firstObjRef;
            Pointer end = firstObjRef.add(Word.unsigned(count).multiply(referenceSize));
            while (pos.belowThan(end)) {
                visitObjectReference(pos, compressed);
                pos = pos.add(referenceSize);
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private void visitObjectReference(Pointer objRef, boolean compressed) {
            if (!compressed) {
                UnsignedWord offset = objRef.subtract(topSP);
                frameMap.mark(offset, FrameMap.ENTRY_UNCOMPRESSED_REF, FrameAccess.uncompressedReferenceSize());
            }
        }
    }
}

/**
 * When a continuation is part of an auxiliary image heap, its code pointers (e.g. return addresses)
 * use the code base address of the isolate which persisted the continuation, and its uncompressed
 * references are actually in compressed base-relative form. We clone these objects for persisting,
 * which patches them so that code pointers are valid for the current isolate and object references
 * can be walked.
 */
final class PersistedContinuationReplacer implements AuxiliaryImageObjectReplacer {
    @Override
    public Object replace(Object obj, Access access) {
        if (ContinuationSupport.isSupported() && obj instanceof StoredContinuation && AuxiliaryImageHeap.isPresent()) {
            if (AuxiliaryImageHeap.singleton().containsObject(Word.objectToUntrackedPointer(obj))) {
                return StoredContinuationAccess.clone((StoredContinuation) obj);
            }
        }
        return obj;
    }
}
