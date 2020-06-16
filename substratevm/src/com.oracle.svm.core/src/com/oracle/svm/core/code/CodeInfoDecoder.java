/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.NonmovableByteArrayReader;

/**
 * Decodes the metadata for compiled code. The data is an encoded byte stream to make it as compact
 * as possible, but still allow fast constant time access.
 *
 * The encoding consists of entries with the following structure:
 *
 * <pre>
 * u1 entryFlags
 * u1 deltaIP
 * [s1|s2|s4 frameSizeEncoding]
 * [s1|s2|s4 exceptionOffset]
 * [u2|u4 referenceMapIndex]
 * [s4 deoptFrameInfoIndex]
 * </pre>
 *
 * The first byte, entryFlags, encodes which of the optional data fields are present and what size
 * they have. The size of the whole entry can be computed from just this byte, which allows fast
 * iteration of the table. The deltaIP is the difference of the IP for this entry and the next
 * entry. The first entry always corresponds to IP zero.
 *
 * This table structure allows linear search for the entry of a given IP. An
 * {@linkplain #loadEntryOffset index} is used to turn this into a constant time lookup. The index
 * stores the entry offset for every IP at the given {@linkplain Options#CodeInfoIndexGranularity
 * granularity}.
 */
public final class CodeInfoDecoder {
    public static class Options {
        @Option(help = "The granularity of the index for looking up code metadata. Should be a power of 2. Larger values make the index smaller, but access slower.")//
        public static final HostedOptionKey<Integer> CodeInfoIndexGranularity = new HostedOptionKey<>(256);
    }

    private CodeInfoDecoder() {
    }

    static long lookupCodeInfoEntryOffset(CodeInfo info, long ip) {
        long entryIP = lookupEntryIP(ip);
        long entryOffset = loadEntryOffset(info, ip);
        do {
            int entryFlags = loadEntryFlags(info, entryOffset);
            if (entryIP == ip) {
                return entryOffset;
            }

            entryIP = advanceIP(info, entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (entryIP <= ip);

        return -1;
    }

    static void lookupCodeInfo(CodeInfo info, long ip, CodeInfoQueryResult codeInfoQueryResult) {
        long sizeEncoding = initialSizeEncoding();
        long entryIP = lookupEntryIP(ip);
        long entryOffset = loadEntryOffset(info, ip);
        do {
            int entryFlags = loadEntryFlags(info, entryOffset);
            sizeEncoding = updateSizeEncoding(info, entryOffset, entryFlags, sizeEncoding);
            if (entryIP == ip) {
                codeInfoQueryResult.encodedFrameSize = sizeEncoding;
                codeInfoQueryResult.exceptionOffset = loadExceptionOffset(info, entryOffset, entryFlags);
                codeInfoQueryResult.referenceMapIndex = loadReferenceMapIndex(info, entryOffset, entryFlags);
                codeInfoQueryResult.frameInfo = loadFrameInfo(info, entryOffset, entryFlags);
                return;
            }

            entryIP = advanceIP(info, entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (entryIP <= ip);

        codeInfoQueryResult.encodedFrameSize = sizeEncoding;
        codeInfoQueryResult.exceptionOffset = CodeInfoQueryResult.NO_EXCEPTION_OFFSET;
        codeInfoQueryResult.referenceMapIndex = CodeInfoQueryResult.NO_REFERENCE_MAP;
        codeInfoQueryResult.frameInfo = CodeInfoQueryResult.NO_FRAME_INFO;
    }

    static void lookupCodeInfo(CodeInfo info, long ip, SimpleCodeInfoQueryResult codeInfoQueryResult) {
        long sizeEncoding = initialSizeEncoding();
        long entryIP = lookupEntryIP(ip);
        long entryOffset = loadEntryOffset(info, ip);
        do {
            int entryFlags = loadEntryFlags(info, entryOffset);
            sizeEncoding = updateSizeEncoding(info, entryOffset, entryFlags, sizeEncoding);
            if (entryIP == ip) {
                codeInfoQueryResult.setEncodedFrameSize(sizeEncoding);
                codeInfoQueryResult.setExceptionOffset(loadExceptionOffset(info, entryOffset, entryFlags));
                codeInfoQueryResult.setReferenceMapIndex(loadReferenceMapIndex(info, entryOffset, entryFlags));
                return;
            }

            entryIP = advanceIP(info, entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (entryIP <= ip);

        codeInfoQueryResult.setEncodedFrameSize(sizeEncoding);
        codeInfoQueryResult.setExceptionOffset(CodeInfoQueryResult.NO_EXCEPTION_OFFSET);
        codeInfoQueryResult.setReferenceMapIndex(CodeInfoQueryResult.NO_REFERENCE_MAP);
    }

    static long lookupDeoptimizationEntrypoint(CodeInfo info, long method, long encodedBci, CodeInfoQueryResult codeInfo) {

        long sizeEncoding = initialSizeEncoding();
        long entryIP = lookupEntryIP(method);
        long entryOffset = loadEntryOffset(info, method);
        while (true) {
            int entryFlags = loadEntryFlags(info, entryOffset);
            sizeEncoding = updateSizeEncoding(info, entryOffset, entryFlags, sizeEncoding);
            if (entryIP == method) {
                break;
            }

            entryIP = advanceIP(info, entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
            if (entryIP > method) {
                return -1;
            }
        }

        assert entryIP == method;
        assert decodeMethodStart(loadEntryFlags(info, entryOffset), sizeEncoding);

        do {
            int entryFlags = loadEntryFlags(info, entryOffset);
            sizeEncoding = updateSizeEncoding(info, entryOffset, entryFlags, sizeEncoding);

            if (decodeMethodStart(entryFlags, sizeEncoding) && entryIP != method) {
                /* Advanced to the next method, so we do not have a match. */
                return -1;
            }

            if (isDeoptEntryPoint(info, entryOffset, entryFlags, encodedBci)) {
                codeInfo.encodedFrameSize = sizeEncoding;
                codeInfo.exceptionOffset = loadExceptionOffset(info, entryOffset, entryFlags);
                codeInfo.referenceMapIndex = loadReferenceMapIndex(info, entryOffset, entryFlags);
                codeInfo.frameInfo = loadFrameInfo(info, entryOffset, entryFlags);
                assert codeInfo.frameInfo.isDeoptEntry() && codeInfo.frameInfo.getCaller() == null : "Deoptimization entry must not have inlined frames";
                return entryIP;
            }

            entryIP = advanceIP(info, entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (!endOfTable(entryIP));

        return -1;
    }

    static long lookupReferenceMapIndex(CodeInfo info, long ip) {
        long entryIP = lookupEntryIP(ip);
        long entryOffset = loadEntryOffset(info, ip);
        do {
            int entryFlags = loadEntryFlags(info, entryOffset);
            if (entryIP == ip) {
                return loadReferenceMapIndex(info, entryOffset, entryFlags);
            }

            entryIP = advanceIP(info, entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (entryIP <= ip);

        return CodeInfoQueryResult.NO_REFERENCE_MAP;
    }

    static long indexGranularity() {
        return Options.CodeInfoIndexGranularity.getValue();
    }

    static long lookupEntryIP(long ip) {
        return Long.divideUnsigned(ip, indexGranularity()) * indexGranularity();
    }

    private static long loadEntryOffset(CodeInfo info, long ip) {
        counters().lookupEntryOffsetCount.inc();
        long index = Long.divideUnsigned(ip, indexGranularity());
        return NonmovableByteArrayReader.getU4(CodeInfoAccess.getCodeInfoIndex(info), index * Integer.BYTES);
    }

    @AlwaysInline("Make IP-lookup loop call free")
    static int loadEntryFlags(CodeInfo info, long curOffset) {
        counters().loadEntryFlagsCount.inc();
        return NonmovableByteArrayReader.getU1(CodeInfoAccess.getCodeInfoEncodings(info), curOffset);
    }

    private static final int INVALID_SIZE_ENCODING = 0;

    private static int initialSizeEncoding() {
        return INVALID_SIZE_ENCODING;
    }

    @AlwaysInline("Make IP-lookup loop call free")
    private static long updateSizeEncoding(CodeInfo info, long entryOffset, int entryFlags, long sizeEncoding) {
        switch (extractFS(entryFlags)) {
            case FS_NO_CHANGE:
                return sizeEncoding;
            case FS_SIZE_S1:
                return NonmovableByteArrayReader.getS1(CodeInfoAccess.getCodeInfoEncodings(info), offsetFS(entryOffset, entryFlags));
            case FS_SIZE_S2:
                return NonmovableByteArrayReader.getS2(CodeInfoAccess.getCodeInfoEncodings(info), offsetFS(entryOffset, entryFlags));
            case FS_SIZE_S4:
                return NonmovableByteArrayReader.getS4(CodeInfoAccess.getCodeInfoEncodings(info), offsetFS(entryOffset, entryFlags));
            default:
                throw shouldNotReachHere();
        }
    }

    private static long loadExceptionOffset(CodeInfo info, long entryOffset, int entryFlags) {
        switch (extractEX(entryFlags)) {
            case EX_NO_HANDLER:
                return CodeInfoQueryResult.NO_EXCEPTION_OFFSET;
            case EX_OFFSET_S1:
                return NonmovableByteArrayReader.getS1(CodeInfoAccess.getCodeInfoEncodings(info), offsetEX(entryOffset, entryFlags));
            case EX_OFFSET_S2:
                return NonmovableByteArrayReader.getS2(CodeInfoAccess.getCodeInfoEncodings(info), offsetEX(entryOffset, entryFlags));
            case EX_OFFSET_S4:
                return NonmovableByteArrayReader.getS4(CodeInfoAccess.getCodeInfoEncodings(info), offsetEX(entryOffset, entryFlags));
            default:
                throw shouldNotReachHere();
        }
    }

    private static long loadReferenceMapIndex(CodeInfo info, long entryOffset, int entryFlags) {
        switch (extractRM(entryFlags)) {
            case RM_NO_MAP:
                return CodeInfoQueryResult.NO_REFERENCE_MAP;
            case RM_EMPTY_MAP:
                return CodeInfoQueryResult.EMPTY_REFERENCE_MAP;
            case RM_INDEX_U2:
                return NonmovableByteArrayReader.getU2(CodeInfoAccess.getCodeInfoEncodings(info), offsetRM(entryOffset, entryFlags));
            case RM_INDEX_U4:
                return NonmovableByteArrayReader.getU4(CodeInfoAccess.getCodeInfoEncodings(info), offsetRM(entryOffset, entryFlags));
            default:
                throw shouldNotReachHere();
        }
    }

    static final int FRAME_SIZE_METHOD_START = 0b001;
    static final int FRAME_SIZE_ENTRY_POINT = 0b010;
    static final int FRAME_SIZE_HAS_CALLEE_SAVED_REGISTERS = 0b100;

    static final int FRAME_SIZE_STATUS_MASK = FRAME_SIZE_METHOD_START | FRAME_SIZE_ENTRY_POINT | FRAME_SIZE_HAS_CALLEE_SAVED_REGISTERS;

    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    static boolean decodeIsEntryPoint(long sizeEncoding) {
        assert sizeEncoding != INVALID_SIZE_ENCODING;
        return (sizeEncoding & FRAME_SIZE_ENTRY_POINT) != 0;
    }

    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    static boolean decodeHasCalleeSavedRegisters(long sizeEncoding) {
        assert sizeEncoding != INVALID_SIZE_ENCODING;
        return (sizeEncoding & FRAME_SIZE_HAS_CALLEE_SAVED_REGISTERS) != 0;
    }

    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    static long decodeTotalFrameSize(long sizeEncoding) {
        assert sizeEncoding != INVALID_SIZE_ENCODING;
        return sizeEncoding & ~FRAME_SIZE_STATUS_MASK;
    }

    private static boolean decodeMethodStart(int entryFlags, long sizeEncoding) {
        assert sizeEncoding != initialSizeEncoding();

        switch (extractFS(entryFlags)) {
            case FS_NO_CHANGE:
                /* No change in frame size means we are still in the same method. */
                return false;
            case FS_SIZE_S1:
            case FS_SIZE_S2:
            case FS_SIZE_S4:
                return (sizeEncoding & FRAME_SIZE_METHOD_START) != 0;
            default:
                throw shouldNotReachHere();
        }
    }

    private static boolean isDeoptEntryPoint(CodeInfo info, long entryOffset, int entryFlags, long encodedBci) {
        switch (extractFI(entryFlags)) {
            case FI_NO_DEOPT:
                return false;
            case FI_DEOPT_ENTRY_INDEX_S4:
                int frameInfoIndex = NonmovableByteArrayReader.getS4(CodeInfoAccess.getCodeInfoEncodings(info), offsetFI(entryOffset, entryFlags));
                return FrameInfoDecoder.isFrameInfoMatch(frameInfoIndex, CodeInfoAccess.getFrameInfoEncodings(info), encodedBci);
            case FI_INFO_ONLY_INDEX_S4:
                /*
                 * We have frame information, but only for debugging purposes. This is not a
                 * deoptimization entry point.
                 */
                return false;
            default:
                throw shouldNotReachHere();
        }
    }

    static boolean initFrameInfoReader(CodeInfo info, long entryOffset, ReusableTypeReader frameInfoReader) {
        int entryFlags = loadEntryFlags(info, entryOffset);
        int frameInfoIndex = NonmovableByteArrayReader.getS4(CodeInfoAccess.getCodeInfoEncodings(info), offsetFI(entryOffset, entryFlags));
        frameInfoReader.setByteIndex(frameInfoIndex);
        frameInfoReader.setData(CodeInfoAccess.getFrameInfoEncodings(info));
        return extractFI(entryFlags) != FI_NO_DEOPT;
    }

    private static FrameInfoQueryResult loadFrameInfo(CodeInfo info, long entryOffset, int entryFlags) {

        boolean isDeoptEntry;
        switch (extractFI(entryFlags)) {
            case FI_NO_DEOPT:
                return CodeInfoQueryResult.NO_FRAME_INFO;
            case FI_DEOPT_ENTRY_INDEX_S4:
                isDeoptEntry = true;
                break;
            case FI_INFO_ONLY_INDEX_S4:
                isDeoptEntry = false;
                break;
            default:
                throw shouldNotReachHere();
        }
        int frameInfoIndex = NonmovableByteArrayReader.getS4(CodeInfoAccess.getCodeInfoEncodings(info), offsetFI(entryOffset, entryFlags));
        return FrameInfoDecoder.decodeFrameInfo(isDeoptEntry, new ReusableTypeReader(CodeInfoAccess.getFrameInfoEncodings(info), frameInfoIndex), info,
                        FrameInfoDecoder.HeapBasedFrameInfoQueryResultAllocator, FrameInfoDecoder.HeapBasedValueInfoAllocator, true);
    }

    @AlwaysInline("Make IP-lookup loop call free")
    private static long advanceIP(CodeInfo info, long entryOffset, long entryIP) {
        int deltaIP = NonmovableByteArrayReader.getU1(CodeInfoAccess.getCodeInfoEncodings(info), offsetIP(entryOffset));
        if (deltaIP == DELTA_END_OF_TABLE) {
            return Long.MAX_VALUE;
        } else {
            assert deltaIP > 0;
            return entryIP + deltaIP;
        }
    }

    private static boolean endOfTable(long entryIP) {
        return entryIP == Long.MAX_VALUE;
    }

    /*
     * Low-level structural definition of the entryFlags bit field.
     */

    static final int DELTA_END_OF_TABLE = 0;

    static final int FS_BITS = 2;
    static final int FS_SHIFT = 0;
    static final int FS_MASK_IN_PLACE = ((1 << FS_BITS) - 1) << FS_SHIFT;
    static final int FS_NO_CHANGE = 0;
    static final int FS_SIZE_S1 = 1;
    static final int FS_SIZE_S2 = 2;
    static final int FS_SIZE_S4 = 3;
    static final int[] FS_MEM_SIZE = {0, Byte.BYTES, Short.BYTES, Integer.BYTES};

    static final int EX_BITS = 2;
    static final int EX_SHIFT = FS_SHIFT + FS_BITS;
    static final int EX_MASK_IN_PLACE = ((1 << EX_BITS) - 1) << EX_SHIFT;
    static final int EX_NO_HANDLER = 0;
    static final int EX_OFFSET_S1 = 1;
    static final int EX_OFFSET_S2 = 2;
    static final int EX_OFFSET_S4 = 3;
    static final int[] EX_MEM_SIZE = {0, Byte.BYTES, Short.BYTES, Integer.BYTES};

    static final int RM_BITS = 2;
    static final int RM_SHIFT = EX_SHIFT + EX_BITS;
    static final int RM_MASK_IN_PLACE = ((1 << RM_BITS) - 1) << RM_SHIFT;
    static final int RM_NO_MAP = 0;
    static final int RM_EMPTY_MAP = 1;
    static final int RM_INDEX_U2 = 2;
    static final int RM_INDEX_U4 = 3;
    static final int[] RM_MEM_SIZE = {0, 0, Character.BYTES, Integer.BYTES};

    static final int FI_BITS = 2;
    static final int FI_SHIFT = RM_SHIFT + RM_BITS;
    static final int FI_MASK_IN_PLACE = ((1 << FI_BITS) - 1) << FI_SHIFT;
    static final int FI_NO_DEOPT = 0;
    static final int FI_DEOPT_ENTRY_INDEX_S4 = 1;
    static final int FI_INFO_ONLY_INDEX_S4 = 2;
    static final int[] FI_MEM_SIZE = {0, Integer.BYTES, Integer.BYTES, /* unused */ 0};

    private static final int TOTAL_BITS = FI_SHIFT + FI_BITS;

    private static final byte IP_OFFSET;
    private static final byte FS_OFFSET;
    private static final byte[] EX_OFFSET;
    private static final byte[] RM_OFFSET;
    private static final byte[] FI_OFFSET;
    private static final byte[] MEM_SIZE;

    static {
        assert TOTAL_BITS <= Byte.SIZE;
        int maxFlag = 1 << TOTAL_BITS;

        IP_OFFSET = 1;
        FS_OFFSET = 2;
        EX_OFFSET = new byte[maxFlag];
        RM_OFFSET = new byte[maxFlag];
        FI_OFFSET = new byte[maxFlag];
        MEM_SIZE = new byte[maxFlag];
        for (int i = 0; i < maxFlag; i++) {
            EX_OFFSET[i] = TypeConversion.asU1(FS_OFFSET + FS_MEM_SIZE[extractFS(i)]);
            RM_OFFSET[i] = TypeConversion.asU1(EX_OFFSET[i] + EX_MEM_SIZE[extractEX(i)]);
            FI_OFFSET[i] = TypeConversion.asU1(RM_OFFSET[i] + RM_MEM_SIZE[extractRM(i)]);
            MEM_SIZE[i] = TypeConversion.asU1(FI_OFFSET[i] + FI_MEM_SIZE[extractFI(i)]);
        }
    }

    static int extractFS(int entryFlags) {
        return (entryFlags & FS_MASK_IN_PLACE) >> FS_SHIFT;
    }

    static int extractEX(int entryFlags) {
        return (entryFlags & EX_MASK_IN_PLACE) >> EX_SHIFT;
    }

    static int extractRM(int entryFlags) {
        return (entryFlags & RM_MASK_IN_PLACE) >> RM_SHIFT;
    }

    static int extractFI(int entryFlags) {
        return (entryFlags & FI_MASK_IN_PLACE) >> FI_SHIFT;
    }

    private static long offsetIP(long entryOffset) {
        return entryOffset + IP_OFFSET;
    }

    private static long offsetFS(long entryOffset, int entryFlags) {
        assert extractFS(entryFlags) != FS_NO_CHANGE;
        return entryOffset + FS_OFFSET;
    }

    private static long offsetEX(long entryOffset, int entryFlags) {
        assert extractEX(entryFlags) != EX_NO_HANDLER;
        return entryOffset + ByteArrayReader.getU1(EX_OFFSET, entryFlags);
    }

    private static long offsetRM(long entryOffset, int entryFlags) {
        assert extractRM(entryFlags) != RM_NO_MAP && extractRM(entryFlags) != RM_EMPTY_MAP;
        return entryOffset + ByteArrayReader.getU1(RM_OFFSET, entryFlags);
    }

    private static long offsetFI(long entryOffset, int entryFlags) {
        assert extractFI(entryFlags) != FI_NO_DEOPT;
        return entryOffset + ByteArrayReader.getU1(FI_OFFSET, entryFlags);
    }

    @AlwaysInline("Make IP-lookup loop call free")
    private static long advanceOffset(long entryOffset, int entryFlags) {
        counters().advanceOffset.inc();
        return entryOffset + ByteArrayReader.getU1(MEM_SIZE, entryFlags);
    }

    @Fold
    static CodeInfoDecoderCounters counters() {
        return ImageSingletons.lookup(CodeInfoDecoderCounters.class);
    }
}

class CodeInfoDecoderCounters {
    private final Counter.Group counters = new Counter.Group(CodeInfoTable.Options.CodeCacheCounters, "CodeInfoDecoder");
    final Counter lookupEntryOffsetCount = new Counter(counters, "lookupEntryOffset", "");
    final Counter loadEntryFlagsCount = new Counter(counters, "loadEntryFlags", "");
    final Counter advanceOffset = new Counter(counters, "advanceOffset", "");
}
