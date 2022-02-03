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

import static com.oracle.svm.core.code.CodeInfoAccess.FrameInfoState.NO_SUCCESSOR_INDEX_MARKER;

import java.util.Arrays;

import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.TypeReader;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueInfo;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueType;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.NonmovableByteArrayTypeReader;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class FrameInfoDecoder {

    protected static final int BCI_SHIFT = 2;
    protected static final int DURING_CALL_MASK = 2;
    protected static final int RETHROW_EXCEPTION_MASK = 1;

    protected static final int NO_CALLER_BCI = -1;
    protected static final int NO_LOCAL_INFO_BCI = -2;

    /**
     * Differentiates between compressed and uncompressed frame slices. See
     * {@link CompressedFrameDecoderHelper#isCompressedFrameSlice(int)} for more information.
     */
    protected static final int UNCOMPRESSED_FRAME_SLICE_MARKER = -1;
    /**
     * Value added to source line to guarantee the value is greater than zero.
     */
    protected static final int COMPRESSED_FRAME_POINTER_ADDEND = 2;
    /**
     * Value subtracted from the negated method name index when there is a unique shared frame
     * successor to guarantee the value is less than zero.
     */
    protected static final int COMPRESSED_UNIQUE_SUCCESSOR_ADDEND = 1;
    /**
     * Value added to source line to guarantee the value is greater than zero.
     */
    protected static final int COMPRESSED_SOURCE_LINE_ADDEND = 2;

    protected static boolean isFrameInfoMatch(long frameInfoIndex, NonmovableArray<Byte> frameInfoEncodings, long searchEncodedBci) {
        NonmovableByteArrayTypeReader readBuffer = new NonmovableByteArrayTypeReader(frameInfoEncodings, frameInfoIndex);
        int firstValue = readBuffer.getSVInt();
        if (CompressedFrameDecoderHelper.isCompressedFrameSlice(firstValue)) {
            /* Compressed frame slices have no local bci information. */
            return false;
        }

        /* Read encoded bci from uncompressed frame slice. */
        long actualEncodedBci = readBuffer.getSV();
        assert actualEncodedBci != NO_CALLER_BCI;

        return actualEncodedBci == searchEncodedBci;
    }

    public interface FrameInfoQueryResultAllocator {
        @RestrictHeapAccess(reason = "Whitelisted because some implementations can allocate.", access = RestrictHeapAccess.Access.UNRESTRICTED)
        FrameInfoQueryResult newFrameInfoQueryResult();
    }

    static class HeapBasedFrameInfoQueryResultAllocator implements FrameInfoQueryResultAllocator {
        @Override
        public FrameInfoQueryResult newFrameInfoQueryResult() {
            return new FrameInfoQueryResult();
        }
    }

    static final HeapBasedFrameInfoQueryResultAllocator HeapBasedFrameInfoQueryResultAllocator = new HeapBasedFrameInfoQueryResultAllocator();

    public interface ValueInfoAllocator {
        ValueInfo newValueInfo();

        ValueInfo[] newValueInfoArray(int len);

        ValueInfo[][] newValueInfoArrayArray(int len);

        void decodeConstant(ValueInfo valueInfo, NonmovableObjectArray<?> frameInfoObjectConstants);
    }

    static class HeapBasedValueInfoAllocator implements ValueInfoAllocator {
        @Override
        @RestrictHeapAccess(reason = "Whitelisted because some implementations can allocate.", access = RestrictHeapAccess.Access.UNRESTRICTED)
        public ValueInfo newValueInfo() {
            return new ValueInfo();
        }

        @Override
        @RestrictHeapAccess(reason = "Whitelisted because some implementations can allocate.", access = RestrictHeapAccess.Access.UNRESTRICTED)
        public ValueInfo[] newValueInfoArray(int len) {
            return new ValueInfo[len];
        }

        @Override
        @RestrictHeapAccess(reason = "Whitelisted because some implementations can allocate.", access = RestrictHeapAccess.Access.UNRESTRICTED)
        public ValueInfo[][] newValueInfoArrayArray(int len) {
            return new ValueInfo[len][];
        }

        @Override
        @RestrictHeapAccess(reason = "Whitelisted because some implementations can allocate.", access = RestrictHeapAccess.Access.UNRESTRICTED)
        public void decodeConstant(ValueInfo valueInfo, NonmovableObjectArray<?> frameInfoObjectConstants) {
            switch (valueInfo.type) {
                case DefaultConstant:
                    switch (valueInfo.kind) {
                        case Object:
                            valueInfo.value = SubstrateObjectConstant.forObject(null, valueInfo.isCompressedReference);
                            assert valueInfo.value.isDefaultForKind();
                            break;
                        default:
                            valueInfo.value = JavaConstant.defaultForKind(valueInfo.kind);
                    }
                    break;
                case Constant:
                    switch (valueInfo.kind) {
                        case Object:
                            valueInfo.value = SubstrateObjectConstant.forObject(NonmovableArrays.getObject(frameInfoObjectConstants, TypeConversion.asS4(valueInfo.data)),
                                            valueInfo.isCompressedReference);
                            break;
                        case Float:
                            valueInfo.value = JavaConstant.forFloat(Float.intBitsToFloat(TypeConversion.asS4(valueInfo.data)));
                            break;
                        case Double:
                            valueInfo.value = JavaConstant.forDouble(Double.longBitsToDouble(valueInfo.data));
                            break;
                        default:
                            assert valueInfo.kind.isNumericInteger();
                            valueInfo.value = JavaConstant.forIntegerKind(valueInfo.kind, valueInfo.data);
                    }
                    break;
            }
        }
    }

    static final HeapBasedValueInfoAllocator HeapBasedValueInfoAllocator = new HeapBasedValueInfoAllocator();

    private static class CompressedFrameDecoderHelper {
        /**
         * Differentiates between compressed and uncompressed frame slices. Uncompressed frame
         * slices start with {@link #UNCOMPRESSED_FRAME_SLICE_MARKER}.
         */
        private static boolean isCompressedFrameSlice(int firstValue) {
            return firstValue != UNCOMPRESSED_FRAME_SLICE_MARKER;
        }

        /**
         * Determines whether a value is a pointer to a shared frame index. See
         * FrameInfoEncoder.encodeCompressedFirstEntry for more details.
         */
        private static boolean isSharedFramePointer(int value) {
            return value < 0;
        }

        /**
         * Complement of FrameInfoEncoder.encodeCompressedFirstEntry when a shared frame index is
         * encoded.
         */
        private static int decodeSharedFrameIndex(int value) {
            VMError.guarantee(value < UNCOMPRESSED_FRAME_SLICE_MARKER);

            return -(value + COMPRESSED_FRAME_POINTER_ADDEND);
        }

        /**
         * Determines whether the encodedSourceMethodNameIndex signals that this frame also encodes
         * a uniqueSharedFrameSuccessor. See FrameInfoEncoder.encodeCompressedMethodIndex for more
         * details.
         */
        private static boolean hasEncodedUniqueSharedFrameSuccessor(int encodedSourceMethodNameIndex) {
            return encodedSourceMethodNameIndex < 0;
        }

        /**
         * Complement of FrameInfoEncoder.encodeCompressedMethodIndex.
         */
        private static int decodeMethodIndex(int methodIndex) {
            if (methodIndex < 0) {
                return -(methodIndex + COMPRESSED_UNIQUE_SUCCESSOR_ADDEND);
            } else {
                return methodIndex;
            }
        }

        /**
         * See FrameInfoEncoder.encodeCompressedSourceLineNumber for details.
         */
        private static boolean isSliceEnd(int encodedSourceLineNumber) {
            return encodedSourceLineNumber < 0;
        }

        /**
         * Complement of FrameInfoEncode.encodeCompressedSourceLineNumber.
         */
        private static int decodeSourceLineNumber(int sourceLineNumber) {
            return Math.abs(sourceLineNumber) - COMPRESSED_SOURCE_LINE_ADDEND;
        }

    }

    protected static FrameInfoQueryResult decodeFrameInfo(boolean isDeoptEntry, TypeReader readBuffer, CodeInfo info,
                    FrameInfoQueryResultAllocator resultAllocator, ValueInfoAllocator valueInfoAllocator) {
        return decodeFrameInfo(isDeoptEntry, readBuffer, info, resultAllocator, valueInfoAllocator, new CodeInfoAccess.FrameInfoState());
    }

    protected static FrameInfoQueryResult decodeFrameInfo(boolean isDeoptEntry, TypeReader readBuffer, CodeInfo info,
                    FrameInfoQueryResultAllocator resultAllocator, ValueInfoAllocator valueInfoAllocator, CodeInfoAccess.FrameInfoState state) {
        if (state.isFirstFrame) {
            state.firstValue = readBuffer.getSVInt();
        }

        FrameInfoQueryResult result;
        if (CompressedFrameDecoderHelper.isCompressedFrameSlice(state.firstValue)) {
            result = decodeCompressedFrameInfo(isDeoptEntry, readBuffer, info, resultAllocator, state);
        } else {
            result = decodeUncompressedFrameInfo(isDeoptEntry, readBuffer, info, resultAllocator, valueInfoAllocator, state);
        }
        state.isFirstFrame = false;

        return result;
    }

    /*
     * See (FrameInfoEncoder.CompressedFrameInfoEncodingMedata) for more information about the
     * compressed encoding format.
     */
    private static FrameInfoQueryResult decodeCompressedFrameInfo(boolean isDeoptEntry, TypeReader readBuffer, CodeInfo info,
                    FrameInfoQueryResultAllocator resultAllocator, CodeInfoAccess.FrameInfoState state) {
        FrameInfoQueryResult result = null;
        FrameInfoQueryResult prev = null;

        while (!state.isDone) {
            FrameInfoQueryResult cur = resultAllocator.newFrameInfoQueryResult();
            if (cur == null) {
                return result;
            }

            assert encodeSourceReferences();
            cur.encodedBci = NO_LOCAL_INFO_BCI;
            cur.isDeoptEntry = isDeoptEntry;

            long bufferIndexToRestore = -1;
            if (state.successorIndex != NO_SUCCESSOR_INDEX_MARKER) {
                bufferIndexToRestore = readBuffer.getByteIndex();
                readBuffer.setByteIndex(state.successorIndex);
            }

            final int firstEntry;
            if (state.isFirstFrame) {
                firstEntry = state.firstValue;
            } else {
                firstEntry = readBuffer.getSVInt();
                assert !isDeoptEntry : "Deoptimization entry must not have inlined frames";
            }

            if (CompressedFrameDecoderHelper.isSharedFramePointer(firstEntry)) {
                assert state.successorIndex == NO_SUCCESSOR_INDEX_MARKER && bufferIndexToRestore == -1;
                long sharedFrameByteIndex = CompressedFrameDecoderHelper.decodeSharedFrameIndex(firstEntry);

                // save current buffer index
                bufferIndexToRestore = readBuffer.getByteIndex();

                // jump to shared frame index
                readBuffer.setByteIndex(sharedFrameByteIndex);

                int sourceClassIndex = readBuffer.getSVInt();
                VMError.guarantee(!CompressedFrameDecoderHelper.isSharedFramePointer(sourceClassIndex));
                decodeCompressedFrameData(readBuffer, info, state, sourceClassIndex, cur);

                // jump back to frame slice information
                readBuffer.setByteIndex(bufferIndexToRestore);
                bufferIndexToRestore = -1;
            } else {
                decodeCompressedFrameData(readBuffer, info, state, firstEntry, cur);
            }

            if (bufferIndexToRestore != -1) {
                readBuffer.setByteIndex(bufferIndexToRestore);
            }

            if (prev == null) {
                // first frame read during this invocation
                result = cur;
            } else {
                prev.caller = cur;
            }
            prev = cur;

            state.isFirstFrame = false;
        }

        return result;
    }

    private static void decodeCompressedFrameData(TypeReader readBuffer, CodeInfo info, CodeInfoAccess.FrameInfoState state, int sourceClassIndex, FrameInfoQueryResult queryResult) {
        int encodedSourceMethodNameIndex = readBuffer.getSVInt();
        int sourceMethodNameIndex = CompressedFrameDecoderHelper.decodeMethodIndex(encodedSourceMethodNameIndex);
        int encodedSourceLineNumber = readBuffer.getSVInt();
        int sourceLineNumber = CompressedFrameDecoderHelper.decodeSourceLineNumber(encodedSourceLineNumber);

        queryResult.sourceClassIndex = sourceClassIndex;
        queryResult.sourceMethodNameIndex = sourceMethodNameIndex;

        queryResult.sourceClass = NonmovableArrays.getObject(CodeInfoAccess.getFrameInfoSourceClasses(info), sourceClassIndex);
        queryResult.sourceMethodName = NonmovableArrays.getObject(CodeInfoAccess.getFrameInfoSourceMethodNames(info), sourceMethodNameIndex);
        queryResult.sourceLineNumber = sourceLineNumber;

        if (CompressedFrameDecoderHelper.hasEncodedUniqueSharedFrameSuccessor(encodedSourceMethodNameIndex)) {
            state.successorIndex = readBuffer.getSVInt();
        } else {
            state.successorIndex = NO_SUCCESSOR_INDEX_MARKER;
        }

        state.isDone = CompressedFrameDecoderHelper.isSliceEnd(encodedSourceLineNumber);

        assert !state.isDone || state.successorIndex == NO_SUCCESSOR_INDEX_MARKER;
    }

    private static FrameInfoQueryResult decodeUncompressedFrameInfo(boolean isDeoptEntry, TypeReader readBuffer, CodeInfo info,
                    FrameInfoQueryResultAllocator resultAllocator, ValueInfoAllocator valueInfoAllocator, CodeInfoAccess.FrameInfoState state) {
        FrameInfoQueryResult result = null;
        FrameInfoQueryResult prev = null;
        ValueInfo[][] virtualObjects = null;

        while (true) {
            FrameInfoQueryResult cur = resultAllocator.newFrameInfoQueryResult();
            if (cur == null) {
                return result;
            }

            int encodedBci = readBuffer.getSVInt();
            if (encodedBci == NO_CALLER_BCI) {
                return result;
            }

            assert state.isFirstFrame || !isDeoptEntry : "Deoptimization entry must not have inlined frames";

            cur.encodedBci = encodedBci;
            cur.isDeoptEntry = isDeoptEntry;

            final boolean needLocalValues = encodedBci != NO_LOCAL_INFO_BCI;

            if (needLocalValues) {
                cur.numLocks = readBuffer.getUVInt();
                cur.numLocals = readBuffer.getUVInt();
                cur.numStack = readBuffer.getUVInt();

                /*
                 * We either encode a reference to the target method (for runtime compilations) or
                 * just the start offset of the target method (for native image methods, because we
                 * do not want to include unnecessary method metadata in the native image.
                 */
                int deoptMethodIndex = readBuffer.getSVInt();
                if (deoptMethodIndex < 0) {
                    /* Negative number is a reference to the target method. */
                    cur.deoptMethod = (SharedMethod) NonmovableArrays.getObject(CodeInfoAccess.getFrameInfoObjectConstants(info), -1 - deoptMethodIndex);
                    cur.deoptMethodOffset = cur.deoptMethod.getDeoptOffsetInImage();
                } else {
                    /* Positive number is a directly encoded method offset. */
                    cur.deoptMethodOffset = deoptMethodIndex;
                }

                int curValueInfosLength = readBuffer.getUVInt();
                cur.valueInfos = decodeValues(valueInfoAllocator, curValueInfosLength, readBuffer, CodeInfoAccess.getFrameInfoObjectConstants(info));
            }

            if (state.isFirstFrame && needLocalValues) {
                /* This is the first frame, i.e., the top frame that will be returned. */
                int numVirtualObjects = readBuffer.getUVInt();
                virtualObjects = valueInfoAllocator.newValueInfoArrayArray(numVirtualObjects);
                for (int i = 0; i < numVirtualObjects; i++) {
                    int numValues = readBuffer.getUVInt();
                    ValueInfo[] decodedValues = decodeValues(valueInfoAllocator, numValues, readBuffer, CodeInfoAccess.getFrameInfoObjectConstants(info));
                    if (virtualObjects != null) {
                        virtualObjects[i] = decodedValues;
                    }
                }
            }
            cur.virtualObjects = virtualObjects;

            if (encodeSourceReferences()) {
                final int sourceClassIndex = readBuffer.getSVInt();
                final int sourceMethodNameIndex = readBuffer.getSVInt();
                final int sourceLineNumber = readBuffer.getSVInt();

                cur.sourceClassIndex = sourceClassIndex;
                cur.sourceMethodNameIndex = sourceMethodNameIndex;

                cur.sourceClass = NonmovableArrays.getObject(CodeInfoAccess.getFrameInfoSourceClasses(info), sourceClassIndex);
                cur.sourceMethodName = NonmovableArrays.getObject(CodeInfoAccess.getFrameInfoSourceMethodNames(info), sourceMethodNameIndex);
                cur.sourceLineNumber = sourceLineNumber;
            }

            if (prev == null) {
                // first frame read during this invocation
                result = cur;
            } else {
                prev.caller = cur;
            }
            prev = cur;

            state.isFirstFrame = false;
        }
    }

    private static ValueInfo[] decodeValues(ValueInfoAllocator valueInfoAllocator, int numValues, TypeReader readBuffer, NonmovableObjectArray<?> frameInfoObjectConstants) {
        ValueInfo[] valueInfos = valueInfoAllocator.newValueInfoArray(numValues);

        for (int i = 0; i < numValues; i++) {
            ValueInfo valueInfo = valueInfoAllocator.newValueInfo();
            if (valueInfos != null) {
                valueInfos[i] = valueInfo;
            }

            int flags = readBuffer.getU1();
            ValueType valueType = extractType(flags);
            if (valueInfo != null) {
                valueInfo.type = valueType;
                valueInfo.kind = extractKind(flags);
                valueInfo.isCompressedReference = extractIsCompressedReference(flags);
                valueInfo.isEliminatedMonitor = extractIsEliminatedMonitor(flags);
            }
            if (valueType.hasData) {
                long valueInfoData = readBuffer.getSV();
                if (valueInfo != null) {
                    valueInfo.data = valueInfoData;
                }
            }

            valueInfoAllocator.decodeConstant(valueInfo, frameInfoObjectConstants);
        }
        return valueInfos;
    }

    protected static boolean encodeSourceReferences() {
        return SubstrateOptions.StackTrace.getValue();
    }

    protected static int decodeBci(long encodedBci) {
        return TypeConversion.asS4(encodedBci >> BCI_SHIFT);
    }

    protected static boolean decodeDuringCall(long encodedBci) {
        return (encodedBci & DURING_CALL_MASK) != 0;
    }

    protected static boolean decodeRethrowException(long encodedBci) {
        return (encodedBci & RETHROW_EXCEPTION_MASK) != 0;
    }

    public static String readableBci(long encodedBci) {
        return decodeBci(encodedBci) +
                        ((encodedBci & DURING_CALL_MASK) != 0 ? " duringCall" : "") +
                        ((encodedBci & RETHROW_EXCEPTION_MASK) != 0 ? " rethrowException" : "");
    }

    public static void logReadableBci(Log log, long encodedBci) {
        log.signed(decodeBci(encodedBci));
        if ((encodedBci & DURING_CALL_MASK) != 0) {
            log.string(" duringCall");
        }
        if ((encodedBci & RETHROW_EXCEPTION_MASK) != 0) {
            log.string(" rethrowException");
        }
    }

    protected static final int TYPE_BITS = 3;
    protected static final int TYPE_SHIFT = 0;
    protected static final int TYPE_MASK_IN_PLACE = ((1 << TYPE_BITS) - 1) << TYPE_SHIFT;

    protected static final int KIND_BITS = 4;
    protected static final int KIND_SHIFT = TYPE_SHIFT + TYPE_BITS;
    protected static final int KIND_MASK_IN_PLACE = ((1 << KIND_BITS) - 1) << KIND_SHIFT;

    /**
     * Value not used by {@link JavaKind} as a marker for eliminated monitors. The kind of a monitor
     * is always {@link JavaKind#Object}.
     */
    protected static final int IS_ELIMINATED_MONITOR_KIND_VALUE = 15;

    protected static final int IS_COMPRESSED_REFERENCE_BITS = 1;
    protected static final int IS_COMPRESSED_REFERENCE_SHIFT = KIND_SHIFT + KIND_BITS;
    protected static final int IS_COMPRESSED_REFERENCE_MASK_IN_PLACE = ((1 << IS_COMPRESSED_REFERENCE_BITS) - 1) << IS_COMPRESSED_REFERENCE_SHIFT;

    protected static final JavaKind[] KIND_VALUES;

    static {
        KIND_VALUES = Arrays.copyOf(JavaKind.values(), IS_ELIMINATED_MONITOR_KIND_VALUE + 1);
        assert KIND_VALUES[IS_ELIMINATED_MONITOR_KIND_VALUE] == null;
        KIND_VALUES[IS_ELIMINATED_MONITOR_KIND_VALUE] = JavaKind.Object;
    }

    /* Allow allocation-free access to ValueType values */
    private static final ValueType[] ValueTypeValues = ValueType.values();

    private static ValueType extractType(int flags) {
        return ValueTypeValues[(flags & TYPE_MASK_IN_PLACE) >> TYPE_SHIFT];
    }

    private static JavaKind extractKind(int flags) {
        return KIND_VALUES[(flags & KIND_MASK_IN_PLACE) >> KIND_SHIFT];
    }

    private static boolean extractIsCompressedReference(int flags) {
        return (flags & IS_COMPRESSED_REFERENCE_MASK_IN_PLACE) != 0;
    }

    private static boolean extractIsEliminatedMonitor(int flags) {
        return ((flags & KIND_MASK_IN_PLACE) >> KIND_SHIFT) == IS_ELIMINATED_MONITOR_KIND_VALUE;
    }
}
