/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.deopt.Deoptimizer.Options.LazyDeoptimization;
import static com.oracle.svm.core.util.VMError.shouldNotReachHereUnexpectedInput;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.code.FrameInfoDecoder.ConstantAccess;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.heap.ReferenceMapIndex;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.NonmovableByteArrayReader;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.util.TypeConversion;
import jdk.graal.compiler.options.Option;

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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long lookupCodeInfoEntryOffset(CodeInfo info, long relativeIP) {
        long entryIP = lookupEntryIP(relativeIP);
        long entryOffset = loadEntryOffset(info, relativeIP);
        do {
            int entryFlags = loadEntryFlags(info, entryOffset);
            if (entryIP == relativeIP) {
                return entryOffset;
            }

            entryIP = advanceIP(info, entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (entryIP <= relativeIP);

        return INVALID_FRAME_INFO_ENTRY_OFFSET;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long lookupCodeInfoEntryOffsetOrDefault(CodeInfo info, long relativeIP) {
        int chunksToSearch = 0;
        while (true) {
            long defaultFIEntryOffset = INVALID_FRAME_INFO_ENTRY_OFFSET;
            long entryIP = UninterruptibleUtils.Math.max(lookupEntryIP(relativeIP) - chunksToSearch * CodeInfoDecoder.indexGranularity(), 0);
            long entryOffset = loadEntryOffset(info, entryIP);
            do {
                int entryFlags = loadEntryFlags(info, entryOffset);
                int frameInfoFlag = extractFI(entryFlags);
                defaultFIEntryOffset = frameInfoFlag == FI_DEFAULT_INFO_INDEX_S4 ? entryOffset : defaultFIEntryOffset;
                if (entryIP == relativeIP) {
                    if (frameInfoFlag == FI_NO_DEOPT) {
                        /* There is no frame info. Try to find a default one. */
                        break;
                    } else {
                        return entryOffset;
                    }
                }

                entryIP = advanceIP(info, entryOffset, entryIP);
                entryOffset = advanceOffset(entryOffset, entryFlags);
            } while (entryIP <= relativeIP);

            if (defaultFIEntryOffset != INVALID_FRAME_INFO_ENTRY_OFFSET) {
                return defaultFIEntryOffset;
            } else {
                /*
                 * We should re-try lookup only in case when nearest chunk to a given IP is a call
                 * instruction i.e. chunk beginning and call have the same IP value. Continue
                 * searching until you find a chunk that is not a call or method start.
                 */
                chunksToSearch++;
                assert entryIP != 0;
            }
        }
    }

    static void lookupCodeInfo(CodeInfo info, long relativeIP, CodeInfoQueryResult codeInfoQueryResult, ConstantAccess constantAccess) {
        long sizeEncoding = INVALID_SIZE_ENCODING;
        long entryIP = lookupEntryIP(relativeIP);
        long entryOffset = loadEntryOffset(info, relativeIP);
        do {
            int entryFlags = loadEntryFlags(info, entryOffset);
            sizeEncoding = updateSizeEncoding(info, entryOffset, entryFlags, sizeEncoding);
            if (entryIP == relativeIP) {
                codeInfoQueryResult.encodedFrameSize = sizeEncoding;
                codeInfoQueryResult.exceptionOffset = loadExceptionOffset(info, entryOffset, entryFlags);
                codeInfoQueryResult.referenceMapIndex = loadReferenceMapIndex(info, entryOffset, entryFlags);
                codeInfoQueryResult.frameInfo = loadFrameInfo(info, entryOffset, entryFlags, constantAccess);
                return;
            }

            entryIP = advanceIP(info, entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (entryIP <= relativeIP);

        codeInfoQueryResult.encodedFrameSize = sizeEncoding;
        codeInfoQueryResult.exceptionOffset = CodeInfoQueryResult.NO_EXCEPTION_OFFSET;
        codeInfoQueryResult.referenceMapIndex = ReferenceMapIndex.NO_REFERENCE_MAP;
        codeInfoQueryResult.frameInfo = CodeInfoQueryResult.NO_FRAME_INFO;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void lookupCodeInfo(CodeInfo info, long relativeIP, SimpleCodeInfoQueryResult codeInfoQueryResult) {
        long sizeEncoding = INVALID_SIZE_ENCODING;
        long entryIP = lookupEntryIP(relativeIP);
        long entryOffset = loadEntryOffset(info, relativeIP);
        do {
            int entryFlags = loadEntryFlags(info, entryOffset);
            sizeEncoding = updateSizeEncoding(info, entryOffset, entryFlags, sizeEncoding);
            if (entryIP == relativeIP) {
                codeInfoQueryResult.setEncodedFrameSize(sizeEncoding);
                codeInfoQueryResult.setExceptionOffset(loadExceptionOffset(info, entryOffset, entryFlags));
                codeInfoQueryResult.setReferenceMapIndex(loadReferenceMapIndex(info, entryOffset, entryFlags));
                return;
            }

            entryIP = advanceIP(info, entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (entryIP <= relativeIP);

        codeInfoQueryResult.setEncodedFrameSize(sizeEncoding);
        codeInfoQueryResult.setExceptionOffset(CodeInfoQueryResult.NO_EXCEPTION_OFFSET);
        codeInfoQueryResult.setReferenceMapIndex(ReferenceMapIndex.NO_REFERENCE_MAP);
    }

    static long lookupDeoptimizationEntrypoint(CodeInfo info, long method, long encodedBci, CodeInfoQueryResult codeInfo, ConstantAccess constantAccess) {
        assert CodeInfoAccess.isAOTImageCode(info);
        long sizeEncoding = INVALID_SIZE_ENCODING;
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

        assert entryIP == method : entryIP;
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
                codeInfo.frameInfo = loadFrameInfo(info, entryOffset, entryFlags, constantAccess);
                if (LazyDeoptimization.getValue()) {
                    codeInfo.deoptReturnValueIsObject = loadDeoptReturnValueIsObject(info, entryOffset, entryFlags) != 0;
                }
                assert codeInfo.frameInfo.isDeoptEntry() && codeInfo.frameInfo.getCaller() == null : "Deoptimization entry must not have inlined frames";
                return entryIP;
            }

            entryIP = advanceIP(info, entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (!endOfTable(entryIP));

        return -1;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static long lookupStackReferenceMapIndex(CodeInfo info, long relativeIP) {
        long entryIP = lookupEntryIP(relativeIP);
        long entryOffset = loadEntryOffset(info, relativeIP);
        do {
            int entryFlags = loadEntryFlags(info, entryOffset);
            if (entryIP == relativeIP) {
                return loadReferenceMapIndex(info, entryOffset, entryFlags);
            }

            entryIP = advanceIP(info, entryOffset, entryIP);
            entryOffset = advanceOffset(entryOffset, entryFlags);
        } while (entryIP <= relativeIP);

        return ReferenceMapIndex.NO_REFERENCE_MAP;
    }

    @Fold
    static long indexGranularity() {
        return Options.CodeInfoIndexGranularity.getValue();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static long lookupEntryIP(long relativeIP) {
        return Long.divideUnsigned(relativeIP, indexGranularity()) * indexGranularity();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long loadEntryOffset(CodeInfo info, long relativeIP) {
        counters().lookupEntryOffsetCount.inc();
        long index = Long.divideUnsigned(relativeIP, indexGranularity());
        return NonmovableByteArrayReader.getU4(CodeInfoAccess.getCodeInfoIndex(info), index * Integer.BYTES);
    }

    @AlwaysInline("Make IP-lookup loop call free")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static int loadEntryFlags(CodeInfo info, long curOffset) {
        counters().loadEntryFlagsCount.inc();
        return NonmovableByteArrayReader.getU1(CodeInfoAccess.getCodeInfoEncodings(info), curOffset);
    }

    private static int loadDeoptReturnValueIsObject(CodeInfo info, long entryOffset, int entryFlags) {
        /*
         * The byte which encodes whether a return value is an object is stored at the end of the
         * codeInfo and is only present for deopt entry points if lazy deoptimization is enabled.
         */
        assert LazyDeoptimization.getValue() : "must have lazy deoptimization enabled to have this information in the code info";
        long rvoOffset = getU1(AFTER_FI_OFFSET, entryFlags);
        return NonmovableByteArrayReader.getU1(CodeInfoAccess.getCodeInfoEncodings(info), entryOffset + rvoOffset);
    }

    public static final int INVALID_SIZE_ENCODING = 0;
    private static final int INVALID_FRAME_INFO_ENTRY_OFFSET = -1;

    @AlwaysInline("Make IP-lookup loop call free")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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
                throw shouldNotReachHereUnexpectedInput(entryFlags); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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
                throw shouldNotReachHereUnexpectedInput(entryFlags); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long loadReferenceMapIndex(CodeInfo info, long entryOffset, int entryFlags) {
        switch (extractRM(entryFlags)) {
            case RM_NO_MAP:
                return ReferenceMapIndex.NO_REFERENCE_MAP;
            case RM_EMPTY_MAP:
                return ReferenceMapIndex.EMPTY_REFERENCE_MAP;
            case RM_INDEX_U2:
                return NonmovableByteArrayReader.getU2(CodeInfoAccess.getCodeInfoEncodings(info), offsetRM(entryOffset, entryFlags));
            case RM_INDEX_U4:
                return NonmovableByteArrayReader.getU4(CodeInfoAccess.getCodeInfoEncodings(info), offsetRM(entryOffset, entryFlags));
            default:
                throw shouldNotReachHereUnexpectedInput(entryFlags); // ExcludeFromJacocoGeneratedReport
        }
    }

    static final int FRAME_SIZE_METHOD_START = 0b001;
    static final int FRAME_SIZE_ENTRY_POINT = 0b010;
    static final int FRAME_SIZE_HAS_CALLEE_SAVED_REGISTERS = 0b100;

    public static final int FRAME_SIZE_STATUS_MASK = FRAME_SIZE_METHOD_START | FRAME_SIZE_ENTRY_POINT | FRAME_SIZE_HAS_CALLEE_SAVED_REGISTERS;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean decodeIsEntryPoint(long sizeEncoding) {
        assert sizeEncoding != INVALID_SIZE_ENCODING;
        return (sizeEncoding & FRAME_SIZE_ENTRY_POINT) != 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean decodeHasCalleeSavedRegisters(long sizeEncoding) {
        assert sizeEncoding != INVALID_SIZE_ENCODING;
        return (sizeEncoding & FRAME_SIZE_HAS_CALLEE_SAVED_REGISTERS) != 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static long decodeTotalFrameSize(long sizeEncoding) {
        assert sizeEncoding != INVALID_SIZE_ENCODING;
        return sizeEncoding & ~FRAME_SIZE_STATUS_MASK;
    }

    private static boolean decodeMethodStart(int entryFlags, long sizeEncoding) {
        assert sizeEncoding != INVALID_SIZE_ENCODING : sizeEncoding;

        switch (extractFS(entryFlags)) {
            case FS_NO_CHANGE:
                /* No change in frame size means we are still in the same method. */
                return false;
            case FS_SIZE_S1:
            case FS_SIZE_S2:
            case FS_SIZE_S4:
                return (sizeEncoding & FRAME_SIZE_METHOD_START) != 0;
            default:
                throw shouldNotReachHereUnexpectedInput(entryFlags); // ExcludeFromJacocoGeneratedReport
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
            case FI_DEFAULT_INFO_INDEX_S4:
                /*
                 * We have frame information, but without bci and line number. This is not a
                 * deoptimization entry point.
                 */
                return false;
            default:
                throw shouldNotReachHereUnexpectedInput(entryFlags); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static FrameInfoQueryResult loadFrameInfo(CodeInfo info, long entryOffset, int entryFlags, ConstantAccess constantAccess) {
        boolean isDeoptEntry;
        switch (extractFI(entryFlags)) {
            case FI_NO_DEOPT:
                return CodeInfoQueryResult.NO_FRAME_INFO;
            case FI_DEOPT_ENTRY_INDEX_S4:
                isDeoptEntry = true;
                break;
            case FI_INFO_ONLY_INDEX_S4:
            case FI_DEFAULT_INFO_INDEX_S4:
                isDeoptEntry = false;
                break;
            default:
                throw shouldNotReachHereUnexpectedInput(entryFlags); // ExcludeFromJacocoGeneratedReport
        }
        int frameInfoIndex = NonmovableByteArrayReader.getS4(CodeInfoAccess.getCodeInfoEncodings(info), offsetFI(entryOffset, entryFlags));
        return FrameInfoDecoder.decodeFrameInfo(isDeoptEntry, new ReusableTypeReader(CodeInfoAccess.getFrameInfoEncodings(info), frameInfoIndex), info, constantAccess);
    }

    /**
     * Looks up the appropriate {@link CodeInfo} for {@link FrameInfoQueryResult#sourceMethodId} and
     * reads its associated method table entry to resolve and fill the source class and method name
     * using the respective other arrays.
     *
     * @see CodeInfoEncoder.Encoders#encodeMethodTable
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void fillSourceFields(FrameInfoQueryResult result) {
        int methodId = result.sourceMethodId;
        CodeInfo info;
        CodeInfo next = CodeInfoTable.getFirstImageCodeInfo();
        assert next.isNonNull() && methodId >= CodeInfoAccess.getMethodTableFirstId(next);
        do {
            info = next;
            next = CodeInfoAccess.getNextImageCodeInfo(info);
            assert next.isNull() || CodeInfoAccess.getMethodTableFirstId(next) >= CodeInfoAccess.getMethodTableFirstId(info);
        } while (next.isNonNull() && methodId >= CodeInfoAccess.getMethodTableFirstId(next));

        boolean shortClass = NonmovableArrays.lengthOf(CodeInfoAccess.getClasses(info)) <= 0xffff;
        boolean shortName = NonmovableArrays.lengthOf(CodeInfoAccess.getMemberNames(info)) <= 0xffff;
        boolean shortSignature = NonmovableArrays.lengthOf(CodeInfoAccess.getOtherStrings(info)) <= 0xffff;
        int classBytes = shortClass ? Short.BYTES : Integer.BYTES;
        int nameBytes = shortName ? Short.BYTES : Integer.BYTES;
        int signatureBytes = shortSignature ? Short.BYTES : Integer.BYTES;
        int modifierBytes = Short.BYTES;

        int classOffset = 0;
        int nameOffset = classOffset + classBytes;
        int signatureOffset = nameOffset + nameBytes;
        int modifierOffset = signatureOffset + signatureBytes;

        int entryBytes = classBytes + nameBytes;
        if (CodeInfoEncoder.shouldEncodeAllMethodMetadata()) {
            entryBytes += signatureBytes + modifierBytes;
        }

        int methodIndex = methodId - CodeInfoAccess.getMethodTableFirstId(info);
        NonmovableArray<Byte> methodEncodings = CodeInfoAccess.getMethodTable(info);
        VMError.guarantee(methodIndex >= 0 && methodIndex < NonmovableArrays.lengthOf(methodEncodings) / entryBytes);

        Pointer p = NonmovableArrays.addressOf(methodEncodings, methodIndex * entryBytes);
        int classIndex = readIndex(p, shortClass, classOffset);
        Class<?> sourceClass = NonmovableArrays.getObject(CodeInfoAccess.getClasses(info), classIndex);
        int methodNameIndex = readIndex(p, shortName, nameOffset);
        String sourceMethodName = NonmovableArrays.getObject(CodeInfoAccess.getMemberNames(info), methodNameIndex);

        String sourceMethodSignature = CodeInfoEncoder.Encoders.INVALID_METHOD_SIGNATURE;
        int sourceSignatureModifiers = CodeInfoEncoder.Encoders.INVALID_METHOD_MODIFIERS;
        if (CodeInfoEncoder.shouldEncodeAllMethodMetadata()) {
            int sourceSignatureIndex = readIndex(p, shortSignature, signatureOffset);
            sourceMethodSignature = NonmovableArrays.getObject(CodeInfoAccess.getOtherStrings(info), sourceSignatureIndex);

            sourceSignatureModifiers = readIndex(p, true, modifierOffset);
        }
        result.setSourceFields(sourceClass, sourceMethodName, sourceMethodSignature, sourceSignatureModifiers);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int readIndex(Pointer p, boolean isShort, int offset) {
        return isShort ? (p.readShort(offset) & 0xffff) : p.readInt(offset);
    }

    @AlwaysInline("Make IP-lookup loop call free")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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
    /*
     * Filler frame value. It is needed since we store the nextIP offset in a one-byte field,
     * regardless of the CodeInfo index granularity. See CodeInfoEncoder#encodeIPData for more
     * information.
     */
    static final int FI_NO_DEOPT = 0;
    static final int FI_DEOPT_ENTRY_INDEX_S4 = 1;
    static final int FI_INFO_ONLY_INDEX_S4 = 2;
    /*
     * Frame value for default frame info. A default frame info contains a method id but without BCI
     * and line number. It is present on each chunk beginning and method starts. See
     * FrameInfoEncoder#addDefaultDebugInfo for more information.
     */
    static final int FI_DEFAULT_INFO_INDEX_S4 = 3;
    static final int[] FI_MEM_SIZE = {0, Integer.BYTES, Integer.BYTES, Integer.BYTES};

    private static final int TOTAL_BITS = FI_SHIFT + FI_BITS;

    private static final byte IP_OFFSET;
    private static final byte FS_OFFSET;
    private static final byte[] EX_OFFSET;
    private static final byte[] RM_OFFSET;
    private static final byte[] FI_OFFSET;
    private static final byte[] AFTER_FI_OFFSET;

    static {
        assert TOTAL_BITS <= Byte.SIZE;
        int maxFlag = 1 << TOTAL_BITS;

        /*
         * With lazy deoptimization, we have an extra byte in the code info, which keeps track of
         * whether each infopoint is at a call that returns an object. This byte is stored after the
         * FI (frameInfo) section. It is accounted for by the advanceOffset() method.
         */
        IP_OFFSET = 1;
        FS_OFFSET = 2;
        EX_OFFSET = new byte[maxFlag];
        RM_OFFSET = new byte[maxFlag];
        FI_OFFSET = new byte[maxFlag];
        AFTER_FI_OFFSET = new byte[maxFlag];
        for (int i = 0; i < maxFlag; i++) {
            EX_OFFSET[i] = TypeConversion.asU1(FS_OFFSET + FS_MEM_SIZE[extractFS(i)]);
            RM_OFFSET[i] = TypeConversion.asU1(EX_OFFSET[i] + EX_MEM_SIZE[extractEX(i)]);
            FI_OFFSET[i] = TypeConversion.asU1(RM_OFFSET[i] + RM_MEM_SIZE[extractRM(i)]);
            AFTER_FI_OFFSET[i] = TypeConversion.asU1(FI_OFFSET[i] + FI_MEM_SIZE[extractFI(i)]);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static int extractFS(int entryFlags) {
        return (entryFlags & FS_MASK_IN_PLACE) >> FS_SHIFT;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static int extractEX(int entryFlags) {
        return (entryFlags & EX_MASK_IN_PLACE) >> EX_SHIFT;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static int extractRM(int entryFlags) {
        return (entryFlags & RM_MASK_IN_PLACE) >> RM_SHIFT;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static int extractFI(int entryFlags) {
        return (entryFlags & FI_MASK_IN_PLACE) >> FI_SHIFT;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long offsetIP(long entryOffset) {
        return entryOffset + IP_OFFSET;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long offsetFS(long entryOffset, int entryFlags) {
        assert extractFS(entryFlags) != FS_NO_CHANGE;
        return entryOffset + FS_OFFSET;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long getU1(byte[] data, long byteIndex) {
        return data[(int) byteIndex] & 0xFF;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long offsetEX(long entryOffset, int entryFlags) {
        assert extractEX(entryFlags) != EX_NO_HANDLER;
        return entryOffset + getU1(EX_OFFSET, entryFlags);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long offsetRM(long entryOffset, int entryFlags) {
        assert extractRM(entryFlags) != RM_NO_MAP && extractRM(entryFlags) != RM_EMPTY_MAP;
        return entryOffset + getU1(RM_OFFSET, entryFlags);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long offsetFI(long entryOffset, int entryFlags) {
        assert extractFI(entryFlags) != FI_NO_DEOPT;
        return entryOffset + getU1(FI_OFFSET, entryFlags);
    }

    @AlwaysInline("Make IP-lookup loop call free")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long advanceOffset(long entryOffset, int entryFlags) {
        counters().advanceOffset.inc();
        long returnValueIsObjectSize = 0;
        if (DeoptimizationSupport.enabled() && LazyDeoptimization.getValue() && extractFI(entryFlags) == FI_DEOPT_ENTRY_INDEX_S4) {
            returnValueIsObjectSize = Byte.BYTES;
        }
        return entryOffset + getU1(AFTER_FI_OFFSET, entryFlags) + returnValueIsObjectSize;
    }

    @Fold
    static CodeInfoDecoderCounters counters() {
        return ImageSingletons.lookup(CodeInfoDecoderCounters.class);
    }

    /**
     * This class can be used to iterate the Java-level stack trace information for a given
     * instruction pointer (IP). A single physical stack frame may correspond to multiple Java-level
     * stack frames. Iteration starts in the deepest inlined method and ends at the compilation
     * root.
     */
    public static class FrameInfoCursor {
        private final ReusableTypeReader frameInfoReader = new ReusableTypeReader();
        private final SingleShotFrameInfoQueryResultAllocator singleShotFrameInfoQueryResultAllocator = new SingleShotFrameInfoQueryResultAllocator();
        private final FrameInfoState state = new FrameInfoState();

        private CodeInfo info;
        private FrameInfoQueryResult result;
        private boolean canDecode;

        public FrameInfoCursor() {
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @SuppressWarnings("hiding")
        public void initialize(CodeInfo info, CodePointer ip, boolean exactIPMatch) {
            this.info = info;
            result = null;
            frameInfoReader.reset();
            state.reset();
            canDecode = initFrameInfoReader(ip, exactIPMatch);
        }

        /**
         * Tries to advance to the next frame. If the method succeeds, it returns {@code true} and
         * invalidates the data of all {@link FrameInfoQueryResult} objects that were previously
         * returned by {@link FrameInfoCursor#get}.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean advance() {
            decodeNextEntry();
            return result != null;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean hasCaller() {
            assert result != null;
            return !state.isDone;
        }

        /**
         * Returns the information for the current frame.
         *
         * Please note there is no caller and no value information present in the
         * {@link FrameInfoQueryResult} object (i.e., the methods
         * {@link FrameInfoQueryResult#getCaller()}, {@link FrameInfoQueryResult#getValueInfos()},
         * and {@link FrameInfoQueryResult#getVirtualObjects()} will return {@code null}).
         *
         * Every {@link FrameInfoCursor} object uses only a single {@link FrameInfoQueryResult}
         * object internally. Therefore, the values of that object are overwritten when
         * {@link #advance()} is called to move to the next frame.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public FrameInfoQueryResult get() {
            return result;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private void decodeNextEntry() {
            if (!canDecode) {
                return;
            }

            singleShotFrameInfoQueryResultAllocator.reload();
            int entryFlags = loadEntryFlags(info, state.entryOffset);
            boolean isDeoptEntry = extractFI(entryFlags) == FI_DEOPT_ENTRY_INDEX_S4;
            result = FrameInfoDecoder.decodeFrameInfo(isDeoptEntry, frameInfoReader, info, singleShotFrameInfoQueryResultAllocator, DummyValueInfoAllocator.SINGLETON,
                            FrameInfoDecoder.SubstrateConstantAccess, state);
            if (result == null) {
                /* No more entries. */
                canDecode = false;
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private boolean initFrameInfoReader(CodePointer ip, boolean exactIPMatch) {
            long relativeIP = CodeInfoAccess.relativeIP(info, ip);
            long entryOffset = exactIPMatch ? lookupCodeInfoEntryOffset(info, relativeIP) : lookupCodeInfoEntryOffsetOrDefault(info, relativeIP);
            if (entryOffset >= 0) {
                int entryFlags = loadEntryFlags(info, entryOffset);
                if (extractFI(entryFlags) == FI_NO_DEOPT) {
                    entryOffset = INVALID_FRAME_INFO_ENTRY_OFFSET;
                } else {
                    int frameInfoIndex = NonmovableByteArrayReader.getS4(CodeInfoAccess.getCodeInfoEncodings(info), offsetFI(entryOffset, entryFlags));
                    frameInfoReader.setByteIndex(frameInfoIndex);
                    frameInfoReader.setData(CodeInfoAccess.getFrameInfoEncodings(info));
                }
            }
            state.entryOffset = entryOffset;
            assert exactIPMatch || entryOffset >= 0;
            return entryOffset >= 0;
        }
    }

    public static class FrameInfoState {
        public static final int NO_SUCCESSOR_INDEX_MARKER = -1;

        long entryOffset;
        boolean isFirstFrame;
        boolean isDone;
        int firstValue;
        int successorIndex;

        @SuppressWarnings("this-escape")
        public FrameInfoState() {
            reset();
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public FrameInfoState reset() {
            entryOffset = INVALID_FRAME_INFO_ENTRY_OFFSET;
            isFirstFrame = true;
            isDone = false;
            firstValue = -1;
            successorIndex = NO_SUCCESSOR_INDEX_MARKER;
            return this;
        }
    }

    private static final class SingleShotFrameInfoQueryResultAllocator implements FrameInfoDecoder.FrameInfoQueryResultAllocator {
        private final FrameInfoQueryResult frameInfoQueryResult = new FrameInfoQueryResult();
        private boolean fired;

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public SingleShotFrameInfoQueryResultAllocator reload() {
            fired = false;
            return this;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public FrameInfoQueryResult newFrameInfoQueryResult() {
            if (fired) {
                return null;
            }
            fired = true;
            frameInfoQueryResult.init();
            return frameInfoQueryResult;
        }
    }

    private static final class DummyValueInfoAllocator implements FrameInfoDecoder.ValueInfoAllocator {
        static final DummyValueInfoAllocator SINGLETON = new DummyValueInfoAllocator();

        @Platforms(Platform.HOSTED_ONLY.class)
        private DummyValueInfoAllocator() {
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public FrameInfoQueryResult.ValueInfo newValueInfo() {
            return null;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public FrameInfoQueryResult.ValueInfo[] newValueInfoArray(int len) {
            return null;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public FrameInfoQueryResult.ValueInfo[][] newValueInfoArrayArray(int len) {
            return null;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void decodeConstant(FrameInfoQueryResult.ValueInfo valueInfo, NonmovableObjectArray<?> frameInfoObjectConstants, ConstantAccess constantAccess) {
        }
    }
}

class CodeInfoDecoderCounters {
    private final Counter.Group counters = new Counter.Group(CodeInfoTable.Options.CodeCacheCounters, "CodeInfoDecoder");
    final Counter lookupEntryOffsetCount = new Counter(counters, "lookupEntryOffset", "");
    final Counter loadEntryFlagsCount = new Counter(counters, "loadEntryFlags", "");
    final Counter advanceOffset = new Counter(counters, "advanceOffset", "");
}
