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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.util.FrequencyEncoder;
import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CodeInfoEncoder.Counters;
import com.oracle.svm.core.code.CodeInfoEncoder.Encoders;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueInfo;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueType;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackLockValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class FrameInfoEncoder {

    public abstract static class Customization {
        /**
         * Returns true if the method's deoptimization target should be saved within the debugInfo.
         */
        protected abstract boolean storeDeoptTargetMethod();

        /**
         * Returns true if the given local values should be encoded within the debugInfo.
         *
         * @param method The method that contains the debugInfo.
         * @param infopoint The infopoint whose debugInfo that is considered for inclusion.
         */
        protected abstract boolean includeLocalValues(ResolvedJavaMethod method, Infopoint infopoint);

        /**
         * Returns true if the given debugInfo is a valid entry point for deoptimization (and not
         * just frame information for the purpose of debugging).
         *
         * @param method The method that contains the debugInfo.
         * @param infopoint The infopoint whose debugInfo that is considered for inclusion.
         */
        protected abstract boolean isDeoptEntry(ResolvedJavaMethod method, Infopoint infopoint);

        /**
         * Fills the FrameInfoQueryResult.source* fields.
         */
        protected abstract void fillSourceFields(BytecodeFrame bytecodeFrame, FrameInfoQueryResult resultFrameInfo);
    }

    public abstract static class SourceFieldsFromMethod extends Customization {
        private final HostedStringDeduplication stringTable = HostedStringDeduplication.singleton();

        @Override
        protected void fillSourceFields(BytecodeFrame bytecodeFrame, FrameInfoQueryResult resultFrameInfo) {
            final ResolvedJavaMethod method = bytecodeFrame.getMethod();

            final StackTraceElement source = method.asStackTraceElement(bytecodeFrame.getBCI());
            resultFrameInfo.sourceClass = getDeclaringJavaClass(method);
            /*
             * There is no need to have method names as interned strings. But at least sometimes the
             * StackTraceElement contains interned strings, so we un-intern these strings and
             * perform our own de-duplication.
             */
            resultFrameInfo.sourceMethodName = stringTable.deduplicate(source.getMethodName(), true);
            resultFrameInfo.sourceLineNumber = source.getLineNumber();
        }

        protected abstract Class<?> getDeclaringJavaClass(ResolvedJavaMethod method);
    }

    public abstract static class SourceFieldsFromImage extends Customization {
        @Override
        protected void fillSourceFields(BytecodeFrame bytecodeFrame, FrameInfoQueryResult resultFrameInfo) {
            final int deoptOffsetInImage = ((SharedMethod) bytecodeFrame.getMethod()).getDeoptOffsetInImage();
            if (deoptOffsetInImage != 0) {
                CodeInfoQueryResult targetCodeInfo = CodeInfoTable.lookupDeoptimizationEntrypoint(deoptOffsetInImage, resultFrameInfo.encodedBci);
                if (targetCodeInfo != null) {
                    final FrameInfoQueryResult targetFrameInfo = targetCodeInfo.getFrameInfo();
                    assert targetFrameInfo != null;

                    resultFrameInfo.sourceClass = targetFrameInfo.sourceClass;
                    resultFrameInfo.sourceMethodName = targetFrameInfo.sourceMethodName;
                    resultFrameInfo.sourceLineNumber = targetFrameInfo.sourceLineNumber;
                }
            }
        }
    }

    private static final int UNCOMPRESSED_FRAME_SLICE_INDEX = -1;

    static class FrameData {
        protected DebugInfo debugInfo;
        protected int totalFrameSize;
        protected ValueInfo[][] virtualObjects;
        protected FrameInfoQueryResult frame;
        protected long encodedFrameInfoIndex;
        protected int frameSliceIndex = UNCOMPRESSED_FRAME_SLICE_INDEX;
    }

    private static class SourceFieldData {
        final Class<?> sourceClass;
        final String sourceMethodName;
        final int sourceLineNumber;
        final boolean isSliceEnd;

        SourceFieldData(Class<?> sourceClass, String sourceMethodName, int sourceLineNumber, boolean isSliceEnd) {
            this.sourceClass = sourceClass;
            this.sourceMethodName = sourceMethodName;
            this.sourceLineNumber = sourceLineNumber;
            this.isSliceEnd = isSliceEnd;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SourceFieldData that = (SourceFieldData) o;
            return sourceLineNumber == that.sourceLineNumber && isSliceEnd == that.isSliceEnd && sourceClass.equals(that.sourceClass) && sourceMethodName.equals(that.sourceMethodName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceClass, sourceMethodName, sourceLineNumber, isSliceEnd);
        }
    }

    /**
     * When the local values are not needed to be saved within the frame encoding, there can be
     * significant space savings via using an alternative encoding.
     *
     * Within the "compressed" encoding, only the following three values are saved for each frame:
     * <ol>
     * <li>index to source class</li>
     * <li>index to method name</li>
     * <li>source line number</li>
     * </ol>
     *
     * Due to inlining, multiple frames may represent a given {@link Infopoint}. Hence, for each
     * Infopoint, we call the frame(s) representing it a *frame slice*. During decoding, within the
     * compressed encoding the last frame of a given frame slice is indicated by reading a negative
     * source line number.
     *
     * Additional space is saved when multiple Infopoints' frame slices are identical. All
     * Infopoints with identical frame slice information will point to the same compressed frame
     * encoding.
     *
     * Within the encoded frame metadata, frame slices stored in both the compressed and the
     * original (i.e., *uncompressed*) frame encoding can coexist. To differentiate between
     * compressed and uncompressed frame slices, uncompressed frame slices start with the
     * {@link FrameInfoDecoder#UNCOMPRESSED_FRAME_SLICE_MARKER}.
     */
    private static class CompressedFrameInfoEncodingMetadata {
        final List<SourceFieldData> framesToEncode = new ArrayList<>();
        final EconomicMap<SourceFieldData, Integer> framesToEncodeIndexMap = EconomicMap.create(Equivalence.DEFAULT);
        final List<List<SourceFieldData>> frameSlices = new ArrayList<>();
        final EconomicMap<List<SourceFieldData>, Integer> frameSliceIndexMap = EconomicMap.create(Equivalence.DEFAULT);
        final FrequencyEncoder<Integer> sliceFrequency = FrequencyEncoder.createEqualityEncoder();

        boolean sealed = false;
        EconomicMap<Integer, Long> encodedSliceIndexMap = EconomicMap.create(Equivalence.DEFAULT);

        void addFrameSlice(FrameData data, List<SourceFieldData> slice) {
            assert !sealed;
            List<SourceFieldData> encodedFrameSlice = new ArrayList<>();
            for (SourceFieldData fieldData : slice) {
                Integer fieldDataIndex = framesToEncodeIndexMap.get(fieldData);
                if (fieldDataIndex == null) {
                    fieldDataIndex = framesToEncode.size();
                    framesToEncode.add(fieldData);
                    framesToEncodeIndexMap.put(fieldData, fieldDataIndex);
                }
                encodedFrameSlice.add(framesToEncode.get(fieldDataIndex));
            }
            Integer frameSliceIndex = frameSliceIndexMap.get(encodedFrameSlice);
            if (frameSliceIndex == null) {
                frameSliceIndex = frameSlices.size();
                frameSlices.add(encodedFrameSlice);
                frameSliceIndexMap.put(encodedFrameSlice, frameSliceIndex);
            }
            data.frameSliceIndex = frameSliceIndex;
            sliceFrequency.addObject(frameSliceIndex);
        }

        void encodeCompressedData(UnsafeArrayTypeWriter encodingBuffer, Encoders encoders) {
            assert !sealed;
            sealed = true;
            Integer[] sliceOrder = sliceFrequency.encodeAll(new Integer[sliceFrequency.getLength()]);
            for (Integer sliceIdx : sliceOrder) {
                assert !encodedSliceIndexMap.containsKey(sliceIdx);
                encodedSliceIndexMap.put(sliceIdx, encodingBuffer.getBytesWritten());

                List<SourceFieldData> slice = frameSlices.get(sliceIdx);
                for (SourceFieldData fieldData : slice) {
                    int classIndex = encoders.sourceClasses.getIndex(fieldData.sourceClass);
                    int methodIndex = encoders.sourceMethodNames.getIndex(fieldData.sourceMethodName);

                    VMError.guarantee(classIndex != FrameInfoDecoder.UNCOMPRESSED_FRAME_SLICE_MARKER);

                    encodingBuffer.putSV(classIndex);
                    encodingBuffer.putSV(methodIndex);
                    encodingBuffer.putSV(encodeCompressedSourceLineNumber(fieldData.sourceLineNumber, fieldData.isSliceEnd));
                }
            }
        }

        long getEncodingOffset(int sliceIndex) {
            assert sealed;
            Long encodedSliceIndex = encodedSliceIndexMap.get(sliceIndex);
            assert encodedSliceIndex != null;
            return encodedSliceIndex;
        }

        /**
         * When verifying the frame encoding, sourceClassIndex and sourceMethodNameIndex must be
         * filled in correctly.
         */
        boolean writeFrameVerificationInfo(FrameData data, Encoders encoders) {
            int curIdx = 0;
            List<SourceFieldData> slice = frameSlices.get(data.frameSliceIndex);
            for (FrameInfoQueryResult cur = data.frame; cur != null; cur = cur.caller) {
                cur.encodedBci = FrameInfoDecoder.NO_LOCAL_INFO_BCI;
                assert cur == data.frame || !cur.isDeoptEntry : "Deoptimization entry information for caller frames is not persisted";

                cur.sourceClassIndex = encoders.sourceClasses.getIndex(cur.sourceClass);
                cur.sourceMethodNameIndex = encoders.sourceMethodNames.getIndex(cur.sourceMethodName);
                boolean isSliceEnd = cur.caller == null;
                SourceFieldData fieldData = new SourceFieldData(cur.sourceClass, cur.sourceMethodName, cur.sourceLineNumber, isSliceEnd);
                assert fieldData.equals(slice.get(curIdx));
                curIdx++;
            }
            assert frameSlices.get(data.frameSliceIndex).size() == curIdx;
            return true;
        }
    }

    private final Customization customization;

    private final List<FrameData> allDebugInfos;
    private final Encoders encoders;
    private final CompressedFrameInfoEncodingMetadata frameMetadata;

    protected FrameInfoEncoder(Customization customization, Encoders encoders) {
        this.customization = customization;
        this.encoders = encoders;
        this.allDebugInfos = new ArrayList<>();
        this.frameMetadata = new CompressedFrameInfoEncodingMetadata();
    }

    protected FrameData addDebugInfo(ResolvedJavaMethod method, Infopoint infopoint, int totalFrameSize) {
        final boolean includeLocalValues = customization.includeLocalValues(method, infopoint);
        final boolean encodeSourceReferences = FrameInfoDecoder.encodeSourceReferences();
        final boolean useCompressedEncoding = SubstrateOptions.UseCompressedFrameEncodings.getValue() && !includeLocalValues;
        if (!includeLocalValues && !encodeSourceReferences) {
            return null;
        }

        final DebugInfo debugInfo = infopoint.debugInfo;
        final FrameData data = new FrameData();
        data.debugInfo = debugInfo;
        data.totalFrameSize = totalFrameSize;
        data.virtualObjects = new ValueInfo[countVirtualObjects(debugInfo)][];
        data.frame = addFrame(data, debugInfo.frame(), customization.isDeoptEntry(method, infopoint), includeLocalValues);

        if (encodeSourceReferences) {
            List<SourceFieldData> frameSlice = useCompressedEncoding ? new ArrayList<>() : null;
            BytecodeFrame bytecodeFrame = data.debugInfo.frame();
            for (FrameInfoQueryResult resultFrame = data.frame; resultFrame != null; resultFrame = resultFrame.caller) {
                assert bytecodeFrame != null;
                customization.fillSourceFields(bytecodeFrame, resultFrame);

                // save source class and method name
                final Class<?> sourceClass = resultFrame.sourceClass;
                final String sourceMethodName = resultFrame.sourceMethodName;
                encoders.sourceClasses.addObject(sourceClass);
                encoders.sourceMethodNames.addObject(sourceMethodName);

                // save encoding metadata
                if (useCompressedEncoding) {
                    assert !resultFrame.hasLocalValueInfo();
                    final boolean isSliceEnd = resultFrame.caller == null;
                    final int sourceLineNumber = resultFrame.sourceLineNumber;
                    SourceFieldData fieldData = new SourceFieldData(sourceClass, sourceMethodName, sourceLineNumber, isSliceEnd);
                    frameSlice.add(fieldData);
                }

                bytecodeFrame = bytecodeFrame.caller();
            }
            if (useCompressedEncoding) {
                frameMetadata.addFrameSlice(data, frameSlice);
            }
        }

        allDebugInfos.add(data);
        return data;
    }

    private static int countVirtualObjects(DebugInfo debugInfo) {
        /*
         * We want to know the highest virtual object id in use in this DebugInfo. For that, we have
         * to recursively visited all VirtualObject in all frames.
         */
        BitSet visitedVirtualObjects = new BitSet();
        for (BytecodeFrame frame = debugInfo.frame(); frame != null; frame = frame.caller()) {
            countVirtualObjects(frame.values, visitedVirtualObjects);
        }
        /* The highest set bit in the bitset is the maximum virtual object ID. */
        return visitedVirtualObjects.length();
    }

    private static void countVirtualObjects(JavaValue[] values, BitSet visitedVirtualObjects) {
        for (JavaValue value : values) {
            if (value instanceof VirtualObject) {
                VirtualObject virtualObject = (VirtualObject) value;
                if (!visitedVirtualObjects.get(virtualObject.getId())) {
                    visitedVirtualObjects.set(virtualObject.getId());
                    countVirtualObjects(virtualObject.getValues(), visitedVirtualObjects);
                }
            }
        }
    }

    private FrameInfoQueryResult addFrame(FrameData data, BytecodeFrame frame, boolean isDeoptEntry, boolean needLocalValues) {
        FrameInfoQueryResult result = new FrameInfoQueryResult();
        if (frame.caller() != null) {
            assert !isDeoptEntry : "Deoptimization entry point information for caller frames is not encoded";
            result.caller = addFrame(data, frame.caller(), false, needLocalValues);
        }
        result.virtualObjects = data.virtualObjects;
        result.encodedBci = encodeBci(frame.getBCI(), frame.duringCall, frame.rethrowException);
        result.isDeoptEntry = isDeoptEntry;

        ValueInfo[] valueInfos = null;
        if (needLocalValues) {
            SharedMethod method = (SharedMethod) frame.getMethod();
            if (customization.storeDeoptTargetMethod()) {
                result.deoptMethod = method;
                encoders.objectConstants.addObject(SubstrateObjectConstant.forObject(method));
            }
            result.deoptMethodOffset = method.getDeoptOffsetInImage();

            result.numLocals = frame.numLocals;
            result.numStack = frame.numStack;
            result.numLocks = frame.numLocks;

            JavaValue[] values = frame.values;
            int numValues = 0;
            for (int i = values.length; --i >= 0;) {
                if (!ValueUtil.isIllegalJavaValue(values[i])) {
                    // Found the last non-illegal value, i.e., the last value we have to encode.
                    numValues = i + 1;
                    break;
                }
            }

            valueInfos = new ValueInfo[numValues];
            for (int i = 0; i < numValues; i++) {
                valueInfos[i] = makeValueInfo(data, getFrameValueKind(frame, i), values[i], isDeoptEntry);
            }
        }
        result.valueInfos = valueInfos;

        ImageSingletons.lookup(Counters.class).frameCount.inc();

        return result;
    }

    public static JavaKind getFrameValueKind(BytecodeFrame frame, int valueIndex) {
        if (valueIndex < frame.numLocals) {
            return frame.getLocalValueKind(valueIndex);
        } else if (valueIndex - frame.numLocals < frame.numStack) {
            return frame.getStackValueKind(valueIndex - frame.numLocals);
        } else {
            assert valueIndex - frame.numLocals - frame.numStack < frame.numLocks;
            return JavaKind.Object;
        }
    }

    private ValueInfo makeValueInfo(FrameData data, JavaKind kind, JavaValue v, boolean isDeoptEntry) {
        JavaValue value = v;

        ValueInfo result = new ValueInfo();
        result.kind = kind;

        if (value instanceof StackLockValue) {
            StackLockValue lock = (StackLockValue) value;
            assert ValueUtil.isIllegal(lock.getSlot());
            if (isDeoptEntry && lock.isEliminated()) {
                throw VMError.shouldNotReachHere("Cannot have an eliminated monitor in a deoptimization entry point: value " + value + " in method " +
                                data.debugInfo.getBytecodePosition().getMethod().format("%H.%n(%p)"));
            }

            result.isEliminatedMonitor = lock.isEliminated();
            value = lock.getOwner();
        }

        if (ValueUtil.isIllegalJavaValue(value)) {
            result.type = ValueType.Illegal;
            assert result.kind == JavaKind.Illegal;

        } else if (value instanceof StackSlot) {
            StackSlot stackSlot = (StackSlot) value;
            result.type = ValueType.StackSlot;
            result.data = stackSlot.getOffset(data.totalFrameSize);
            result.isCompressedReference = isCompressedReference(stackSlot);
            ImageSingletons.lookup(Counters.class).stackValueCount.inc();

        } else if (ReservedRegisters.singleton().isAllowedInFrameState(value)) {
            RegisterValue register = (RegisterValue) value;
            result.type = ValueType.ReservedRegister;
            result.data = ValueUtil.asRegister(register).number;
            result.isCompressedReference = isCompressedReference(register);
            ImageSingletons.lookup(Counters.class).registerValueCount.inc();

        } else if (CalleeSavedRegisters.supportedByPlatform() && value instanceof RegisterValue) {
            if (isDeoptEntry) {
                throw VMError.shouldNotReachHere("Cannot encode registers in deoptimization entry point: value " + value + " in method " +
                                data.debugInfo.getBytecodePosition().getMethod().format("%H.%n(%p)"));
            }

            RegisterValue register = (RegisterValue) value;
            result.type = ValueType.Register;
            result.data = CalleeSavedRegisters.singleton().getOffsetInFrame(ValueUtil.asRegister(register));
            result.isCompressedReference = isCompressedReference(register);
            ImageSingletons.lookup(Counters.class).registerValueCount.inc();

        } else if (value instanceof JavaConstant) {
            JavaConstant constant = (JavaConstant) value;
            result.value = constant;
            if (constant.isDefaultForKind()) {
                result.type = ValueType.DefaultConstant;
            } else {
                result.type = ValueType.Constant;
                if (constant.getJavaKind() == JavaKind.Object) {
                    /*
                     * Collect all Object constants, which will be stored in a separate Object[]
                     * array so that the GC can visit them.
                     */
                    encoders.objectConstants.addObject(constant);
                }
            }
            ImageSingletons.lookup(Counters.class).constantValueCount.inc();

        } else if (ValueUtil.isVirtualObject(value)) {
            VirtualObject virtualObject = (VirtualObject) value;
            result.type = ValueType.VirtualObject;
            result.data = virtualObject.getId();
            makeVirtualObject(data, virtualObject, isDeoptEntry);
        } else {
            throw VMError.shouldNotReachHere();
        }
        return result;
    }

    private static boolean isCompressedReference(AllocatableValue value) {
        assert value.getPlatformKind().getVectorLength() == 1 : "Only scalar types supported";
        return value.getValueKind(LIRKind.class).isCompressedReference(0);
    }

    private static final ValueInfo[] MARKER = new ValueInfo[0];

    private void makeVirtualObject(FrameData data, VirtualObject virtualObject, boolean isDeoptEntry) {
        int id = virtualObject.getId();
        if (data.virtualObjects[id] != null) {
            return;
        }
        /* Install a non-null value to support recursive VirtualObjects. */
        data.virtualObjects[id] = MARKER;

        ArrayList<ValueInfo> valueList = new ArrayList<>(virtualObject.getValues().length + 4);
        SharedType type = (SharedType) virtualObject.getType();
        /* The first element is the hub of the virtual object. */
        valueList.add(makeValueInfo(data, JavaKind.Object, SubstrateObjectConstant.forObject(type.getHub()), isDeoptEntry));

        ObjectLayout objectLayout = ConfigurationValues.getObjectLayout();
        assert type.isArray() == LayoutEncoding.isArray(type.getHub().getLayoutEncoding()) : "deoptimization code uses layout encoding to determine if type is an array";
        if (type.isArray()) {
            /* We do not know the final length yet, so add a placeholder. */
            valueList.add(null);
            int length = 0;

            JavaKind kind = ((SharedType) type.getComponentType()).getStorageKind();
            int i = 0;
            while (i < virtualObject.getValues().length) {
                JavaValue value = virtualObject.getValues()[i];
                JavaKind valueKind = virtualObject.getSlotKind(i);
                if (objectLayout.sizeInBytes(kind) == 4 && objectLayout.sizeInBytes(valueKind) == 8) {
                    /*
                     * Truffle uses arrays in a non-standard way: it declares an int[] array and
                     * uses it to also store long and double values. These values span two array
                     * elements - so we have to write this element with the actual value kind and
                     * add 2 to the length.
                     */
                    valueList.add(makeValueInfo(data, valueKind, value, isDeoptEntry));
                    length += 2;

                } else {
                    if (kind == JavaKind.Byte) {
                        /* Escape analysis of byte arrays needs special care. */
                        int byteCount = restoreByteArrayEntryByteCount(virtualObject, i);
                        valueKind = restoreByteArrayEntryValueKind(valueKind, byteCount);
                        valueList.add(makeValueInfo(data, valueKind, value, isDeoptEntry));
                        length += byteCount;
                        i += byteCount - /* loop increment */ 1;
                    } else {
                        assert objectLayout.sizeInBytes(valueKind.getStackKind()) <= objectLayout.sizeInBytes(kind.getStackKind());
                        valueList.add(makeValueInfo(data, kind, value, isDeoptEntry));
                        length++;
                    }
                }

                i++;
                assert objectLayout.getArrayElementOffset(kind, length) == objectLayout.getArrayBaseOffset(kind) + computeOffset(valueList, 2);
            }

            assert valueList.get(1) == null;
            valueList.set(1, makeValueInfo(data, JavaKind.Int, JavaConstant.forInt(length), isDeoptEntry));

        } else {
            /*
             * We must add filling constants for padding, so that values are contiguous. The
             * deoptimization code does not have access to field information.
             */
            SharedField[] fields = (SharedField[]) type.getInstanceFields(true);

            long curOffset = objectLayout.getFirstFieldOffset();
            int fieldIdx = 0;
            int valueIdx = 0;
            while (valueIdx < virtualObject.getValues().length) {
                SharedField field = fields[fieldIdx];
                fieldIdx += 1;
                JavaValue value = virtualObject.getValues()[valueIdx];
                JavaKind valueKind = virtualObject.getSlotKind(valueIdx);
                valueIdx += 1;

                JavaKind kind = field.getStorageKind();
                if (objectLayout.sizeInBytes(kind) == 4 && objectLayout.sizeInBytes(valueKind) == 8) {
                    /*
                     * Truffle uses fields in a non-standard way: it declares a couple of
                     * (consecutive) int fields, and uses them to also store long and double values.
                     * These values span two fields - so we have to ignore a field.
                     */
                    kind = valueKind;
                    assert fields[fieldIdx].getJavaKind() == field.getJavaKind();
                    fieldIdx++;
                }

                if (field.getLocation() >= 0) {
                    assert curOffset <= field.getLocation();
                    while (curOffset + 7 < field.getLocation()) {
                        valueList.add(makeValueInfo(data, JavaKind.Long, JavaConstant.LONG_0, isDeoptEntry));
                        curOffset += 8;
                    }
                    if (curOffset + 3 < field.getLocation()) {
                        valueList.add(makeValueInfo(data, JavaKind.Int, JavaConstant.INT_0, isDeoptEntry));
                        curOffset += 4;
                    }
                    if (curOffset + 1 < field.getLocation()) {
                        valueList.add(makeValueInfo(data, JavaKind.Short, JavaConstant.forShort((short) 0), isDeoptEntry));
                        curOffset += 2;
                    }
                    if (curOffset < field.getLocation()) {
                        valueList.add(makeValueInfo(data, JavaKind.Byte, JavaConstant.forByte((byte) 0), isDeoptEntry));
                        curOffset += 1;
                    }
                    assert curOffset == field.getLocation();
                    assert curOffset - objectLayout.getFirstFieldOffset() == computeOffset(valueList, 1);

                    valueList.add(makeValueInfo(data, kind, value, isDeoptEntry));
                    curOffset += objectLayout.sizeInBytes(kind);
                }
            }
        }

        data.virtualObjects[id] = valueList.toArray(new ValueInfo[valueList.size()]);
        ImageSingletons.lookup(Counters.class).virtualObjectsCount.inc();
    }

    /**
     * Virtualized byte arrays might look like:
     * <p>
     * [b1, b2, INT, ILLEGAL, ILLEGAL, ILLEGAL, b7, b8]
     * <p>
     * This indicates that an int was written over 4 slots of a byte array, and this write was
     * escape analysed.
     *
     * The written int should write over the 3 illegals, and we can then simply ignore them
     * afterwards.
     */
    private static int restoreByteArrayEntryByteCount(VirtualObject vObject, int curIdx) {
        int pos = curIdx + 1;
        while (pos < vObject.getValues().length &&
                        vObject.getSlotKind(pos) == JavaKind.Illegal) {
            pos++;
        }
        return pos - curIdx;
    }

    /**
     * Returns a correctly-sized kind to write at once in the array. Uses the declared kind to
     * decide on whether the kind should be a numeric float (This should not matter).
     */
    private static JavaKind restoreByteArrayEntryValueKind(JavaKind kind, int byteCount) {
        switch (byteCount) {
            case 1:
                return JavaKind.Byte;
            case 2:
                return JavaKind.Short;
            case 4:
                if (kind.isNumericFloat()) {
                    return JavaKind.Float;
                }
                return JavaKind.Int;
            case 8:
                if (kind.isNumericFloat()) {
                    return JavaKind.Double;
                }
                return JavaKind.Long;
            default:
                throw VMError.shouldNotReachHere();
        }
    }

    private static int computeOffset(ArrayList<ValueInfo> valueInfos, int startIndex) {
        int result = 0;
        for (int i = startIndex; i < valueInfos.size(); i++) {
            result += ConfigurationValues.getObjectLayout().sizeInBytes(valueInfos.get(i).kind);
        }
        return result;
    }

    protected void encodeAllAndInstall(CodeInfo target) {
        NonmovableArray<Byte> frameInfoEncodings = encodeFrameDatas();
        install(target, frameInfoEncodings);
    }

    @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed in target.")
    private static void install(CodeInfo target, NonmovableArray<Byte> frameInfoEncodings) {
        CodeInfoAccess.setFrameInfo(target, frameInfoEncodings);
        afterInstallation(target);
    }

    @Uninterruptible(reason = "Safe for GC, but called from uninterruptible code.", calleeMustBe = false)
    private static void afterInstallation(CodeInfo info) {
        ImageSingletons.lookup(Counters.class).frameInfoSize.add(
                        ConfigurationValues.getObjectLayout().getArrayElementOffset(JavaKind.Byte, NonmovableArrays.lengthOf(CodeInfoAccess.getFrameInfoEncodings(info))) +
                                        ConfigurationValues.getObjectLayout().getArrayElementOffset(JavaKind.Object, NonmovableArrays.lengthOf(CodeInfoAccess.getFrameInfoObjectConstants(info))));
    }

    private NonmovableArray<Byte> encodeFrameDatas() {
        UnsafeArrayTypeWriter encodingBuffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        frameMetadata.encodeCompressedData(encodingBuffer, encoders);
        for (FrameData data : allDebugInfos) {
            if (data.frameSliceIndex == UNCOMPRESSED_FRAME_SLICE_INDEX) {
                data.encodedFrameInfoIndex = encodingBuffer.getBytesWritten();
                encodeUncompressedFrameData(data, encodingBuffer);
            } else {
                data.encodedFrameInfoIndex = frameMetadata.getEncodingOffset(data.frameSliceIndex);
                assert frameMetadata.writeFrameVerificationInfo(data, encoders);
            }
        }
        NonmovableArray<Byte> frameInfoEncodings = NonmovableArrays.createByteArray(TypeConversion.asS4(encodingBuffer.getBytesWritten()));
        encodingBuffer.toByteBuffer(NonmovableArrays.asByteBuffer(frameInfoEncodings));
        return frameInfoEncodings;
    }

    private void encodeUncompressedFrameData(FrameData data, UnsafeArrayTypeWriter encodingBuffer) {
        encodingBuffer.putSV(FrameInfoDecoder.UNCOMPRESSED_FRAME_SLICE_MARKER);

        for (FrameInfoQueryResult cur = data.frame; cur != null; cur = cur.caller) {
            assert cur.encodedBci != FrameInfoDecoder.NO_CALLER_BCI : "used as the end marker during decoding";
            final boolean needLocalValues = cur.hasLocalValueInfo();
            if (!needLocalValues) {
                cur.encodedBci = FrameInfoDecoder.NO_LOCAL_INFO_BCI;
            }
            encodingBuffer.putSV(cur.encodedBci);
            assert cur == data.frame || !cur.isDeoptEntry : "Deoptimization entry information for caller frames is not persisted";

            if (needLocalValues) {
                encodingBuffer.putUV(cur.numLocks);
                encodingBuffer.putUV(cur.numLocals);
                encodingBuffer.putUV(cur.numStack);

                int deoptMethodIndex;
                if (cur.deoptMethod != null) {
                    deoptMethodIndex = -1 - encoders.objectConstants.getIndex(SubstrateObjectConstant.forObject(cur.deoptMethod));
                    assert deoptMethodIndex < 0;
                    assert cur.deoptMethodOffset == cur.deoptMethod.getDeoptOffsetInImage();
                } else {
                    deoptMethodIndex = cur.deoptMethodOffset;
                    assert deoptMethodIndex >= 0;
                }
                encodingBuffer.putSV(deoptMethodIndex);

                encodeValues(cur.valueInfos, encodingBuffer);

                if (cur == data.frame) {
                    // Write virtual objects only for first frame.
                    encodingBuffer.putUV(cur.virtualObjects.length);
                    for (ValueInfo[] virtualObject : cur.virtualObjects) {
                        encodeValues(virtualObject, encodingBuffer);
                    }
                }
            }

            if (FrameInfoDecoder.encodeSourceReferences()) {
                final int classIndex = encoders.sourceClasses.getIndex(cur.sourceClass);
                final int methodIndex = encoders.sourceMethodNames.getIndex(cur.sourceMethodName);

                cur.sourceClassIndex = classIndex;
                cur.sourceMethodNameIndex = methodIndex;

                encodingBuffer.putSV(classIndex);
                encodingBuffer.putSV(methodIndex);
                encodingBuffer.putSV(cur.sourceLineNumber);
            }
        }
        encodingBuffer.putSV(FrameInfoDecoder.NO_CALLER_BCI);
    }

    private void encodeValues(ValueInfo[] valueInfos, UnsafeArrayTypeWriter encodingBuffer) {
        encodingBuffer.putUV(valueInfos.length);
        for (ValueInfo valueInfo : valueInfos) {
            if (valueInfo.type == ValueType.Constant) {
                if (valueInfo.kind == JavaKind.Object) {
                    valueInfo.data = encoders.objectConstants.getIndex(valueInfo.value);
                } else {
                    valueInfo.data = encodePrimitiveConstant(valueInfo.value);
                }
            }

            encodingBuffer.putU1(encodeFlags(valueInfo.type, valueInfo.kind, valueInfo.isCompressedReference, valueInfo.isEliminatedMonitor));
            if (valueInfo.type.hasData) {
                encodingBuffer.putSV(valueInfo.data);
            }
        }
    }

    protected static long encodePrimitiveConstant(JavaConstant constant) {
        switch (constant.getJavaKind()) {
            case Float:
                return Float.floatToRawIntBits(constant.asFloat());
            case Double:
                return Double.doubleToRawLongBits(constant.asDouble());
            default:
                return constant.asLong();
        }
    }

    private static int encodeFlags(ValueType type, JavaKind kind, boolean isCompressedReference, boolean isEliminatedMonitor) {
        int kindIndex = isEliminatedMonitor ? FrameInfoDecoder.IS_ELIMINATED_MONITOR_KIND_VALUE : kind.ordinal();
        assert FrameInfoDecoder.KIND_VALUES[kindIndex] == kind;

        return (type.ordinal() << FrameInfoDecoder.TYPE_SHIFT) |
                        (kindIndex << FrameInfoDecoder.KIND_SHIFT) |
                        ((isCompressedReference ? 1 : 0) << FrameInfoDecoder.IS_COMPRESSED_REFERENCE_SHIFT);
    }

    /**
     * Encodes the BCI and the duringCall- and rethrowException flags into a single value.
     */
    public static long encodeBci(int bci, boolean duringCall, boolean rethrowException) {
        return (((long) bci) << FrameInfoDecoder.BCI_SHIFT) | (duringCall ? FrameInfoDecoder.DURING_CALL_MASK : 0) | (rethrowException ? FrameInfoDecoder.RETHROW_EXCEPTION_MASK : 0);
    }

    /**
     * The source line number also encodes whether the is it the end of or a frame slice or not. The
     * original value is incremented to guarantee all source line numbers are greater than 0.
     */
    private static int encodeCompressedSourceLineNumber(int sourceLineNumber, boolean isSliceEnd) {
        int lineNumberWithAddend = sourceLineNumber + FrameInfoDecoder.COMPRESSED_SOURCE_LINE_ADDEND;
        VMError.guarantee(lineNumberWithAddend > 0);
        return isSliceEnd ? -(lineNumberWithAddend) : (lineNumberWithAddend);
    }

    void verifyEncoding(CodeInfo info) {
        for (FrameData expectedData : allDebugInfos) {
            FrameInfoQueryResult actualFrame = FrameInfoDecoder.decodeFrameInfo(expectedData.frame.isDeoptEntry,
                            new ReusableTypeReader(CodeInfoAccess.getFrameInfoEncodings(info), expectedData.encodedFrameInfoIndex),
                            info, FrameInfoDecoder.HeapBasedFrameInfoQueryResultAllocator, FrameInfoDecoder.HeapBasedValueInfoAllocator);
            FrameInfoVerifier.verifyFrames(expectedData, expectedData.frame, actualFrame);
        }
    }
}

class FrameInfoVerifier {
    protected static void verifyFrames(FrameInfoEncoder.FrameData expectedData, FrameInfoQueryResult expectedTopFrame, FrameInfoQueryResult actualTopFrame) {
        FrameInfoQueryResult expectedFrame = expectedTopFrame;
        FrameInfoQueryResult actualFrame = actualTopFrame;
        while (expectedFrame != null) {
            assert actualFrame != null;
            assert expectedFrame.isDeoptEntry == actualFrame.isDeoptEntry;
            assert expectedFrame.hasLocalValueInfo() == actualFrame.hasLocalValueInfo();
            if (expectedFrame.hasLocalValueInfo()) {
                assert expectedFrame.encodedBci == actualFrame.encodedBci;
                assert expectedFrame.deoptMethod == null && actualFrame.deoptMethod == null ||
                                ((expectedFrame.deoptMethod != null) && expectedFrame.deoptMethod.equals(actualFrame.deoptMethod));
                assert expectedFrame.deoptMethodOffset == actualFrame.deoptMethodOffset;
                assert expectedFrame.numLocals == actualFrame.numLocals;
                assert expectedFrame.numStack == actualFrame.numStack;
                assert expectedFrame.numLocks == actualFrame.numLocks;

                verifyValues(expectedFrame.valueInfos, actualFrame.valueInfos);
                assert expectedFrame.virtualObjects == expectedTopFrame.virtualObjects;
                assert actualFrame.virtualObjects == actualTopFrame.virtualObjects;
            }

            assert Objects.equals(expectedFrame.sourceClass, actualFrame.sourceClass);
            assert Objects.equals(expectedFrame.sourceMethodName, actualFrame.sourceMethodName);
            assert expectedFrame.sourceLineNumber == actualFrame.sourceLineNumber;

            assert expectedFrame.sourceClassIndex == actualFrame.sourceClassIndex;
            assert expectedFrame.sourceMethodNameIndex == actualFrame.sourceMethodNameIndex;

            expectedFrame = expectedFrame.caller;
            actualFrame = actualFrame.caller;
        }
        assert actualFrame == null;

        if (actualTopFrame.hasLocalValueInfo()) {
            assert expectedData.virtualObjects.length == actualTopFrame.virtualObjects.length;
            for (int i = 0; i < expectedData.virtualObjects.length; i++) {
                verifyValues(expectedData.virtualObjects[i], actualTopFrame.virtualObjects[i]);
            }
        }
    }

    private static void verifyValues(ValueInfo[] expectedValues, ValueInfo[] actualValues) {
        assert expectedValues.length == actualValues.length;
        for (int i = 0; i < expectedValues.length; i++) {
            ValueInfo expectedValue = expectedValues[i];
            ValueInfo actualValue = actualValues[i];

            assert expectedValue.type == actualValue.type;
            assert expectedValue.kind.equals(actualValue.kind);
            assert expectedValue.isCompressedReference == actualValue.isCompressedReference;
            assert expectedValue.isEliminatedMonitor == actualValue.isEliminatedMonitor;
            assert expectedValue.data == actualValue.data;
            verifyConstant(expectedValue.value, actualValue.value);
        }
    }

    protected static void verifyConstant(JavaConstant expectedConstant, JavaConstant actualConstant) {
        if (expectedConstant != null && expectedConstant.getJavaKind().isPrimitive()) {
            /* During compilation, the kind of a smaller-than-int constant is often Int. */
            assert FrameInfoEncoder.encodePrimitiveConstant(expectedConstant) == FrameInfoEncoder.encodePrimitiveConstant(actualConstant);
        } else {
            assert Objects.equals(expectedConstant, actualConstant);
        }
    }
}
