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
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.core.util.Counter;

/**
 * Provides metadata for compile code. The data is an {@link #codeInfoEncodings encoded byte[]
 * array} to make it as compact as possible, but still allow fast constant time access.
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
 * This table structure allows linear search for the entry of a given IP. An {@link #codeInfoIndex
 * index} is used to convert this to constant time lookup. The index stores the entry offset for
 * every IP at the given {@link Options#CodeInfoIndexGranularity granularity}.
 */
class CodeInfoDecoder {

    public static class Options {
        @Option(help = "The granularity of the index for looking up code metadata. Should be a power of 2. Larger values make the index smaller, but access slower.")//
        public static final HostedOptionKey<Integer> CodeInfoIndexGranularity = new HostedOptionKey<>(256);
    }

    @UnknownObjectField(types = {byte[].class}) protected byte[] codeInfoIndex;
    @UnknownObjectField(types = {byte[].class}) protected byte[] codeInfoEncodings;
    @UnknownObjectField(types = {byte[].class}) protected byte[] referenceMapEncoding;
    @UnknownObjectField(types = {byte[].class}) protected byte[] frameInfoEncodings;
    @UnknownObjectField(types = {Object[].class}) protected Object[] frameInfoObjectConstants;
    @UnknownObjectField(types = {String[].class}) protected String[] frameInfoSourceClassNames;
    @UnknownObjectField(types = {String[].class}) protected String[] frameInfoSourceMethodNames;
    @UnknownObjectField(types = {String[].class}) protected String[] frameInfoSourceFileNames;
    @UnknownObjectField(types = {String[].class}) protected String[] frameInfoNames;

    protected void setData(byte[] codeInfoIndex, byte[] codeInfoEncodings, byte[] referenceMapEncoding, byte[] frameInfoEncodings, Object[] frameInfoObjectConstants,
                    String[] frameInfoSourceClassNames, String[] frameInfoSourceMethodNames, String[] frameInfoSourceFileNames, String[] frameInfoNames) {
        this.codeInfoIndex = codeInfoIndex;
        this.codeInfoEncodings = codeInfoEncodings;
        this.referenceMapEncoding = referenceMapEncoding;
        this.frameInfoEncodings = frameInfoEncodings;
        this.frameInfoObjectConstants = frameInfoObjectConstants;
        this.frameInfoSourceClassNames = frameInfoSourceClassNames;
        this.frameInfoSourceMethodNames = frameInfoSourceMethodNames;
        this.frameInfoSourceFileNames = frameInfoSourceFileNames;
        this.frameInfoNames = frameInfoNames;
    }

    protected long lookupCodeInfoEntryOffset(long ip) {
        long entryIP = lookupEntryIP(ip);
        long entryOffset = loadEntryOffset(ip);
        do {
            int entryFlags = loadEntryFlags(entryOffset);
            if (entryIP == ip) {
                return entryOffset;
            }

            entryIP = advanceIP(entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (entryIP <= ip);

        return -1;
    }

    protected void lookupCodeInfo(long ip, CodeInfoQueryResult codeInfo) {
        codeInfo.exceptionOffset = CodeInfoQueryResult.NO_EXCEPTION_OFFSET;
        codeInfo.referenceMapIndex = CodeInfoQueryResult.NO_REFERENCE_MAP;
        codeInfo.frameInfo = CodeInfoQueryResult.NO_FRAME_INFO;
        codeInfo.referenceMapEncoding = referenceMapEncoding;

        long sizeEncoding = initialSizeEncoding();
        long entryIP = lookupEntryIP(ip);
        long entryOffset = loadEntryOffset(ip);
        do {
            int entryFlags = loadEntryFlags(entryOffset);
            sizeEncoding = updateSizeEncoding(entryOffset, entryFlags, sizeEncoding);
            if (entryIP == ip) {
                codeInfo.exceptionOffset = loadExceptionOffset(entryOffset, entryFlags);
                codeInfo.referenceMapIndex = loadReferenceMapIndex(entryOffset, entryFlags);
                codeInfo.frameInfo = loadFrameInfo(entryOffset, entryFlags);
                break;
            }

            entryIP = advanceIP(entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (entryIP <= ip);

        codeInfo.totalFrameSize = decodeTotalFrameSize(sizeEncoding);
    }

    public long lookupDeoptimizationEntrypoint(long method, long encodedBci, CodeInfoQueryResult codeInfo) {
        long sizeEncoding = initialSizeEncoding();
        long entryIP = lookupEntryIP(method);
        long entryOffset = loadEntryOffset(method);
        while (true) {
            int entryFlags = loadEntryFlags(entryOffset);
            sizeEncoding = updateSizeEncoding(entryOffset, entryFlags, sizeEncoding);
            if (entryIP == method) {
                break;
            }

            entryIP = advanceIP(entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
            if (entryIP > method) {
                return -1;
            }
        }

        assert entryIP == method;
        assert decodeMethodStart(loadEntryFlags(entryOffset), sizeEncoding);

        do {
            int entryFlags = loadEntryFlags(entryOffset);
            sizeEncoding = updateSizeEncoding(entryOffset, entryFlags, sizeEncoding);

            if (decodeMethodStart(entryFlags, sizeEncoding) && entryIP != method) {
                /* Advanced to the next method, so we do not have a match. */
                return -1;
            }

            if (isDeoptEntryPoint(entryOffset, entryFlags, encodedBci)) {
                codeInfo.totalFrameSize = decodeTotalFrameSize(sizeEncoding);
                codeInfo.exceptionOffset = loadExceptionOffset(entryOffset, entryFlags);
                codeInfo.referenceMapEncoding = referenceMapEncoding;
                codeInfo.referenceMapIndex = loadReferenceMapIndex(entryOffset, entryFlags);
                codeInfo.frameInfo = loadFrameInfo(entryOffset, entryFlags);
                assert codeInfo.frameInfo.isDeoptEntry() && codeInfo.frameInfo.getCaller() == null : "Deoptimization entry must not have inlined frames";
                return entryIP;
            }

            entryIP = advanceIP(entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (!endOfTable(entryIP));

        return -1;
    }

    protected long lookupTotalFrameSize(long ip) {
        long sizeEncoding = initialSizeEncoding();
        long entryIP = lookupEntryIP(ip);
        long entryOffset = loadEntryOffset(ip);
        do {
            int entryFlags = loadEntryFlags(entryOffset);
            sizeEncoding = updateSizeEncoding(entryOffset, entryFlags, sizeEncoding);

            entryIP = advanceIP(entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (entryIP <= ip);

        return decodeTotalFrameSize(sizeEncoding);
    }

    protected long lookupExceptionOffset(long ip) {
        long entryIP = lookupEntryIP(ip);
        long entryOffset = loadEntryOffset(ip);
        do {
            int entryFlags = loadEntryFlags(entryOffset);
            if (entryIP == ip) {
                return loadExceptionOffset(entryOffset, entryFlags);
            }

            entryIP = advanceIP(entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (entryIP <= ip);

        return CodeInfoQueryResult.NO_EXCEPTION_OFFSET;
    }

    protected byte[] getReferenceMapEncoding() {
        return referenceMapEncoding;
    }

    protected long lookupReferenceMapIndex(long ip) {
        long entryIP = lookupEntryIP(ip);
        long entryOffset = loadEntryOffset(ip);
        do {
            int entryFlags = loadEntryFlags(entryOffset);
            if (entryIP == ip) {
                return loadReferenceMapIndex(entryOffset, entryFlags);
            }

            entryIP = advanceIP(entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (entryIP <= ip);

        return CodeInfoQueryResult.NO_REFERENCE_MAP;
    }

    protected static long indexGranularity() {
        return Options.CodeInfoIndexGranularity.getValue();
    }

    protected static long lookupEntryIP(long ip) {
        return Long.divideUnsigned(ip, indexGranularity()) * indexGranularity();
    }

    private long loadEntryOffset(long ip) {
        counters().lookupEntryOffsetCount.inc();
        long index = Long.divideUnsigned(ip, indexGranularity());
        return ByteArrayReader.getU4(codeInfoIndex, index * Integer.BYTES);
    }

    @AlwaysInline("Make IP-lookup loop call free")
    protected final int loadEntryFlags(long curOffset) {
        counters().loadEntryFlagsCount.inc();
        return ByteArrayReader.getU1(codeInfoEncodings, curOffset);
    }

    private static final int INVALID_SIZE_ENCODING = 0;

    private static int initialSizeEncoding() {
        return INVALID_SIZE_ENCODING;
    }

    @AlwaysInline("Make IP-lookup loop call free")
    private long updateSizeEncoding(long entryOffset, int entryFlags, long sizeEncoding) {
        switch (extractFS(entryFlags)) {
            case FS_NO_CHANGE:
                return sizeEncoding;
            case FS_SIZE_S1:
                return ByteArrayReader.getS1(codeInfoEncodings, offsetFS(entryOffset, entryFlags));
            case FS_SIZE_S2:
                return ByteArrayReader.getS2(codeInfoEncodings, offsetFS(entryOffset, entryFlags));
            case FS_SIZE_S4:
                return ByteArrayReader.getS4(codeInfoEncodings, offsetFS(entryOffset, entryFlags));
            default:
                throw shouldNotReachHere();
        }
    }

    private static long decodeTotalFrameSize(long sizeEncoding) {
        assert sizeEncoding != initialSizeEncoding();
        return Math.abs(sizeEncoding);
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
                return sizeEncoding < 0;
            default:
                throw shouldNotReachHere();
        }
    }

    private long loadExceptionOffset(long entryOffset, int entryFlags) {
        switch (extractEX(entryFlags)) {
            case EX_NO_HANDLER:
                return CodeInfoQueryResult.NO_EXCEPTION_OFFSET;
            case EX_OFFSET_S1:
                return ByteArrayReader.getS1(codeInfoEncodings, offsetEX(entryOffset, entryFlags));
            case EX_OFFSET_S2:
                return ByteArrayReader.getS2(codeInfoEncodings, offsetEX(entryOffset, entryFlags));
            case EX_OFFSET_S4:
                return ByteArrayReader.getS4(codeInfoEncodings, offsetEX(entryOffset, entryFlags));
            default:
                throw shouldNotReachHere();
        }
    }

    private long loadReferenceMapIndex(long entryOffset, int entryFlags) {
        switch (extractRM(entryFlags)) {
            case RM_NO_MAP:
                return CodeInfoQueryResult.NO_REFERENCE_MAP;
            case RM_EMPTY_MAP:
                return CodeInfoQueryResult.EMPTY_REFERENCE_MAP;
            case RM_INDEX_U2:
                return ByteArrayReader.getU2(codeInfoEncodings, offsetRM(entryOffset, entryFlags));
            case RM_INDEX_U4:
                return ByteArrayReader.getU4(codeInfoEncodings, offsetRM(entryOffset, entryFlags));
            default:
                throw shouldNotReachHere();
        }
    }

    private boolean isDeoptEntryPoint(long entryOffset, int entryFlags, long encodedBci) {
        switch (extractFI(entryFlags)) {
            case FI_NO_DEOPT:
                return false;
            case FI_DEOPT_ENTRY_INDEX_S4:
                int frameInfoIndex = ByteArrayReader.getS4(codeInfoEncodings, offsetFI(entryOffset, entryFlags));
                return FrameInfoDecoder.isFrameInfoMatch(frameInfoIndex, frameInfoEncodings, encodedBci);
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

    protected boolean initFrameInfoReader(long entryOffset, ReusableTypeReader frameInfoReader) {
        int entryFlags = loadEntryFlags(entryOffset);
        int frameInfoIndex = ByteArrayReader.getS4(codeInfoEncodings, offsetFI(entryOffset, entryFlags));
        frameInfoReader.setByteIndex(frameInfoIndex);
        frameInfoReader.setData(frameInfoEncodings);
        return extractFI(entryFlags) != FI_NO_DEOPT;
    }

    private FrameInfoQueryResult loadFrameInfo(long entryOffset, int entryFlags) {
        switch (extractFI(entryFlags)) {
            case FI_NO_DEOPT:
                return CodeInfoQueryResult.NO_FRAME_INFO;
            case FI_DEOPT_ENTRY_INDEX_S4:
                return loadFrameInfo(true, entryOffset, entryFlags);
            case FI_INFO_ONLY_INDEX_S4:
                return loadFrameInfo(false, entryOffset, entryFlags);
            default:
                throw shouldNotReachHere();
        }
    }

    private FrameInfoQueryResult loadFrameInfo(boolean isDeoptEntry, long entryOffset, int entryFlags) {
        int frameInfoIndex = ByteArrayReader.getS4(codeInfoEncodings, offsetFI(entryOffset, entryFlags));
        return FrameInfoDecoder.decodeFrameInfo(isDeoptEntry, new ReusableTypeReader(frameInfoEncodings, frameInfoIndex), frameInfoObjectConstants,
                        frameInfoSourceClassNames, frameInfoSourceMethodNames, frameInfoSourceFileNames, frameInfoNames,
                        FrameInfoDecoder.HeapBasedFrameInfoQueryResultAllocator, FrameInfoDecoder.HeapBasedValueInfoAllocator, true);
    }

    @AlwaysInline("Make IP-lookup loop call free")
    private long advanceIP(long entryOffset, long entryIP) {
        int deltaIP = ByteArrayReader.getU1(codeInfoEncodings, offsetIP(entryOffset));
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

    protected static final int DELTA_END_OF_TABLE = 0;

    protected static final int FS_BITS = 2;
    protected static final int FS_SHIFT = 0;
    protected static final int FS_MASK_IN_PLACE = ((1 << FS_BITS) - 1) << FS_SHIFT;
    protected static final int FS_NO_CHANGE = 0;
    protected static final int FS_SIZE_S1 = 1;
    protected static final int FS_SIZE_S2 = 2;
    protected static final int FS_SIZE_S4 = 3;
    protected static final int[] FS_MEM_SIZE = {0, Byte.BYTES, Short.BYTES, Integer.BYTES};

    protected static final int EX_BITS = 2;
    protected static final int EX_SHIFT = FS_SHIFT + FS_BITS;
    protected static final int EX_MASK_IN_PLACE = ((1 << EX_BITS) - 1) << EX_SHIFT;
    protected static final int EX_NO_HANDLER = 0;
    protected static final int EX_OFFSET_S1 = 1;
    protected static final int EX_OFFSET_S2 = 2;
    protected static final int EX_OFFSET_S4 = 3;
    protected static final int[] EX_MEM_SIZE = {0, Byte.BYTES, Short.BYTES, Integer.BYTES};

    protected static final int RM_BITS = 2;
    protected static final int RM_SHIFT = EX_SHIFT + EX_BITS;
    protected static final int RM_MASK_IN_PLACE = ((1 << RM_BITS) - 1) << RM_SHIFT;
    protected static final int RM_NO_MAP = 0;
    protected static final int RM_EMPTY_MAP = 1;
    protected static final int RM_INDEX_U2 = 2;
    protected static final int RM_INDEX_U4 = 3;
    protected static final int[] RM_MEM_SIZE = {0, 0, Character.BYTES, Integer.BYTES};

    protected static final int FI_BITS = 2;
    protected static final int FI_SHIFT = RM_SHIFT + RM_BITS;
    protected static final int FI_MASK_IN_PLACE = ((1 << FI_BITS) - 1) << FI_SHIFT;
    protected static final int FI_NO_DEOPT = 0;
    protected static final int FI_DEOPT_ENTRY_INDEX_S4 = 1;
    protected static final int FI_INFO_ONLY_INDEX_S4 = 2;
    protected static final int[] FI_MEM_SIZE = {0, Integer.BYTES, Integer.BYTES, /* unused */ 0};

    protected static final int TOTAL_BITS = FI_SHIFT + FI_BITS;

    protected static final byte IP_OFFSET;
    protected static final byte FS_OFFSET;
    protected static final byte[] EX_OFFSET;
    protected static final byte[] RM_OFFSET;
    protected static final byte[] FI_OFFSET;
    protected static final byte[] MEM_SIZE;

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

    protected static int extractFS(int entryFlags) {
        return (entryFlags & FS_MASK_IN_PLACE) >> FS_SHIFT;
    }

    protected static int extractEX(int entryFlags) {
        return (entryFlags & EX_MASK_IN_PLACE) >> EX_SHIFT;
    }

    protected static int extractRM(int entryFlags) {
        return (entryFlags & RM_MASK_IN_PLACE) >> RM_SHIFT;
    }

    protected static int extractFI(int entryFlags) {
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
