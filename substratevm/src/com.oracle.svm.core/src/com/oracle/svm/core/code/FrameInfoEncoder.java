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

import static com.oracle.svm.core.code.CodeInfoDecoder.FrameInfoState.NO_SUCCESSOR_INDEX_MARKER;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.type.CompressibleConstant;
import org.graalvm.compiler.core.common.util.FrequencyEncoder;
import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.Uninterruptible;
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
import com.oracle.svm.core.sampler.CallStackFrameMethodData;
import com.oracle.svm.core.sampler.CallStackFrameMethodInfo;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Register;
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
         * Hook for when a frame is registered for encoding.
         */
        @SuppressWarnings("unused")
        protected void recordFrame(ResolvedJavaMethod method, Infopoint infopoint, boolean isDeoptEntry) {

        }

        /**
         * Returns true if the method's deoptimization target should be saved within the debugInfo.
         */
        protected abstract boolean storeDeoptTargetMethod();

        /**
         * Returns true if the given local values should be encoded within the debugInfo.
         *
         * @param method The method that contains the debugInfo.
         * @param infopoint The infopoint whose debugInfo that is considered for inclusion.
         * @param isDeoptEntry whether this infopoint is tied to a deoptimization entrypoint.
         */
        protected abstract boolean includeLocalValues(ResolvedJavaMethod method, Infopoint infopoint, boolean isDeoptEntry);

        /**
         * Returns true if the given debugInfo is a valid entry point for deoptimization (and not
         * just frame information for the purpose of debugging).
         *
         * @param method The method that contains the debugInfo.
         * @param compilation method compilation this infopoint is within.
         * @param infopoint The infopoint whose debugInfo that is considered for inclusion.
         */
        protected abstract boolean isDeoptEntry(ResolvedJavaMethod method, CompilationResult compilation, Infopoint infopoint);

        /**
         * Fills the FrameInfoQueryResult.source* fields.
         */
        protected abstract void fillSourceFields(ResolvedJavaMethod method, FrameInfoQueryResult resultFrameInfo);
    }

    public abstract static class SourceFieldsFromMethod extends Customization {
        private final HostedStringDeduplication stringTable = HostedStringDeduplication.singleton();

        @Override
        protected void fillSourceFields(ResolvedJavaMethod method, FrameInfoQueryResult resultFrameInfo) {
            final StackTraceElement source = method.asStackTraceElement(resultFrameInfo.getBci());
            resultFrameInfo.sourceClass = getDeclaringJavaClass(method);
            /*
             * There is no need to have method names as interned strings. But at least sometimes the
             * StackTraceElement contains interned strings, so we un-intern these strings and
             * perform our own de-duplication.
             */
            resultFrameInfo.sourceMethodName = stringTable.deduplicate(source.getMethodName(), true);
            resultFrameInfo.sourceLineNumber = source.getLineNumber();
            resultFrameInfo.methodId = ImageSingletons.lookup(FrameInfoMethodData.class).getMethodId(method);
        }

        protected abstract Class<?> getDeclaringJavaClass(ResolvedJavaMethod method);
    }

    public abstract static class SourceFieldsFromImage extends Customization {
        @Override
        protected void fillSourceFields(ResolvedJavaMethod method, FrameInfoQueryResult resultFrameInfo) {
            final int deoptOffsetInImage = ((SharedMethod) method).getDeoptOffsetInImage();
            if (deoptOffsetInImage != 0) {
                CodeInfoQueryResult targetCodeInfo = CodeInfoTable.lookupDeoptimizationEntrypoint(deoptOffsetInImage, resultFrameInfo.encodedBci);
                if (targetCodeInfo != null) {
                    final FrameInfoQueryResult targetFrameInfo = targetCodeInfo.getFrameInfo();
                    assert targetFrameInfo != null;

                    resultFrameInfo.sourceClass = targetFrameInfo.sourceClass;
                    resultFrameInfo.sourceMethodName = targetFrameInfo.sourceMethodName;
                    resultFrameInfo.sourceLineNumber = targetFrameInfo.sourceLineNumber;
                    resultFrameInfo.methodId = targetFrameInfo.methodId;
                }
            } else {
                /*
                 * GR-6000: Make this a hard error once all frame states are covered by
                 * DeoptEntries.
                 */
            }
        }
    }

    private static final int UNCOMPRESSED_FRAME_SLICE_INDEX = -1;

    static class FrameData {
        protected final DebugInfo debugInfo;
        protected final int totalFrameSize;
        protected final ValueInfo[][] virtualObjects;
        protected final FrameInfoQueryResult frame;
        protected long encodedFrameInfoIndex;
        protected boolean isDefaultFrameData;
        protected int frameSliceIndex = UNCOMPRESSED_FRAME_SLICE_INDEX;

        FrameData(DebugInfo debugInfo, int totalFrameSize, ValueInfo[][] virtualObjects, boolean isDefaultFrameData) {
            assert (virtualObjects != null && debugInfo != null) || (virtualObjects == null && debugInfo == null);
            this.debugInfo = debugInfo;
            this.totalFrameSize = totalFrameSize;
            this.virtualObjects = virtualObjects;
            this.isDefaultFrameData = isDefaultFrameData;
            this.frame = new FrameInfoQueryResult();
        }
    }

    record CompressedFrameData(
                    Class<?> sourceClass,
                    String sourceMethodName,
                    int sourceLineNumber,
                    long encodedBci,
                    int methodId,
                    boolean isSliceEnd) {
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
     * Additional space is saved when multiple Infopoints' share frames and/or frame slices. First,
     * all Infopoints with identical frame slice information will point to the same compressed frame
     * encoding. In addition, if a frame is part of multiple (deduplicated) frame slices, then each
     * frame slice will hold a pointer to a *shared frame* instead of directly encode that frame's
     * information. Finally, if within all frame slices a shared frame always has the same successor
     * (i.e., a *unique successor*), then this information will be encoded within the shared frame
     * as an additional value. Shared frames which also contain a unique successor encode the method
     * name index as a negative value.
     *
     * Within the encoded frame metadata, frame slices stored in both the compressed and the
     * original (i.e., *uncompressed*) frame encoding can coexist. To differentiate between
     * compressed and uncompressed frame slices, uncompressed frame slices start with the
     * {@link FrameInfoDecoder#UNCOMPRESSED_FRAME_SLICE_MARKER}.
     */
    private static class CompressedFrameInfoEncodingMetadata {
        final List<CompressedFrameData> framesToEncode = new ArrayList<>();
        final EconomicMap<CompressedFrameData, Integer> framesToEncodeIndexMap = EconomicMap.create(Equivalence.DEFAULT);
        final List<List<CompressedFrameData>> frameSlices = new ArrayList<>();
        final EconomicMap<List<CompressedFrameData>, Integer> frameSliceIndexMap = EconomicMap.create(Equivalence.DEFAULT);
        final FrequencyEncoder<Integer> sliceFrequency = FrequencyEncoder.createEqualityEncoder();
        final Map<CompressedFrameData, Integer> frameSliceFrequency = new HashMap<>();
        final Map<CompressedFrameData, Set<CompressedFrameData>> frameSuccessorMap = new HashMap<>();
        final Map<CompressedFrameData, Integer> frameMaxHeight = new HashMap<>();

        boolean sealed = false;
        EconomicMap<Integer, Long> encodedSliceIndexMap = EconomicMap.create(Equivalence.DEFAULT);

        void addFrameSlice(FrameData data, List<CompressedFrameData> slice) {
            assert !sealed;
            List<CompressedFrameData> frameSliceToEncode = new ArrayList<>();
            for (CompressedFrameData curFrame : slice) {
                if (!framesToEncodeIndexMap.containsKey(curFrame)) {
                    int frameIndex = framesToEncode.size();
                    framesToEncode.add(curFrame);
                    framesToEncodeIndexMap.put(curFrame, frameIndex);
                }
                CompressedFrameData frame = framesToEncode.get(framesToEncodeIndexMap.get(curFrame));
                frameSliceToEncode.add(frame);
            }
            if (!frameSliceIndexMap.containsKey(frameSliceToEncode)) {
                int frameSliceIndex = frameSlices.size();
                frameSlices.add(frameSliceToEncode);
                frameSliceIndexMap.put(frameSliceToEncode, frameSliceIndex);
                /*
                 * Since this is a newly introduced frame slice, the frameSliceFrequency,
                 * frameSuccessorMap, and frameMaxHeight metadata must be updated.
                 */
                CompressedFrameData prevFrame = null;
                int height = 0;
                for (CompressedFrameData frame : frameSliceToEncode) {
                    frameSliceFrequency.merge(frame, 1, Integer::sum);
                    if (prevFrame != null) {
                        frameSuccessorMap.compute(prevFrame, (k, v) -> {
                            Set<CompressedFrameData> callers;
                            if (v == null) {
                                callers = new HashSet<>();
                            } else {
                                callers = v;
                            }
                            callers.add(frame);
                            return callers;
                        });
                    }
                    prevFrame = frame;
                    frameMaxHeight.put(frame, Integer.max(height, frameMaxHeight.getOrDefault(frame, 0)));
                    height++;
                }
            }
            Integer frameSliceIndex = frameSliceIndexMap.get(frameSliceToEncode);
            data.frameSliceIndex = frameSliceIndex;
            sliceFrequency.addObject(frameSliceIndex);
        }

        void encodeCompressedData(UnsafeArrayTypeWriter encodingBuffer, Encoders encoders) {
            assert !sealed;
            sealed = true;

            /*
             * First encode all shared frames.
             */
            EconomicMap<CompressedFrameData, Long> sharedEncodedFrameIndexMap = EconomicMap.create(Equivalence.DEFAULT);
            List<CompressedFrameData> sharedFrames = framesToEncode.stream().filter((f) -> frameSliceFrequency.get(f) > 1).sorted(
                            /*
                             * We want frames which are referenced frequently to be encoded first.
                             * If two frames have the same frequency, then the frame with the
                             * greater height is encoded first; this allows all unique shared frame
                             * successor values to be directly encoded in one pass.
                             */
                            (f1, f2) -> {
                                int result = -Integer.compare(frameSliceFrequency.get(f1), frameSliceFrequency.get(f2));
                                if (result == 0) {
                                    result = -Integer.compare(frameMaxHeight.get(f1), frameMaxHeight.get(f2));
                                }
                                return result;
                            }).collect(Collectors.toList());
            for (CompressedFrameData frame : sharedFrames) {
                assert !sharedEncodedFrameIndexMap.containsKey(frame);
                sharedEncodedFrameIndexMap.put(frame, encodingBuffer.getBytesWritten());

                // Determining the frame's unique successor index (if any).
                final int uniqueSuccessorIndex;
                CompressedFrameData uniqueSuccessor = getUniqueSuccessor(frame);
                if (uniqueSuccessor != null) {
                    // The unique successor is always encoded first due to sorting by height.
                    assert sharedEncodedFrameIndexMap.containsKey(uniqueSuccessor);
                    uniqueSuccessorIndex = NumUtil.safeToInt(sharedEncodedFrameIndexMap.get(uniqueSuccessor));
                } else {
                    uniqueSuccessorIndex = NO_SUCCESSOR_INDEX_MARKER;
                }
                encodeCompressedFrame(encodingBuffer, encoders, frame, uniqueSuccessorIndex);
            }

            /*
             * Next encode all frame slices. Frames which are shared by multiple slices will be
             * represented by pointers, while frames unique to this frame slice will be directly
             * encoded here.
             */
            Integer[] sliceOrder = sliceFrequency.encodeAll(new Integer[sliceFrequency.getLength()]);
            for (Integer sliceIdx : sliceOrder) {
                assert !encodedSliceIndexMap.containsKey(sliceIdx);

                List<CompressedFrameData> slice = frameSlices.get(sliceIdx);
                assert slice.size() > 0;
                /*
                 * If there does not need to be any unique slice state, i.e., all of the slice's
                 * state is walkable within the shared frame state, then the slice's initial shared
                 * frame can be directly pointed to.
                 */
                boolean directlyPointToSharedFrame = slice.stream().allMatch(frame -> {
                    Set<CompressedFrameData> frameSuccessors = frameSuccessorMap.get(frame);
                    return sharedEncodedFrameIndexMap.containsKey(frame) && (frameSuccessors == null || frameSuccessors.size() == 1);
                });
                if (directlyPointToSharedFrame) {
                    CompressedFrameData frame = slice.get(0);
                    assert sharedEncodedFrameIndexMap.containsKey(frame);
                    encodedSliceIndexMap.put(sliceIdx, sharedEncodedFrameIndexMap.get(frame));
                } else {
                    /* Need to encode unique frames and pointers to shared frames. */
                    encodedSliceIndexMap.put(sliceIdx, encodingBuffer.getBytesWritten());
                    CompressedFrameData prevFrame = null;
                    boolean prevShared = false;
                    for (CompressedFrameData frame : slice) {
                        boolean sharedFrame = sharedEncodedFrameIndexMap.containsKey(frame);
                        if (prevShared && getUniqueSuccessor(prevFrame) != null) {
                            /*
                             * This shared frame is directly pointed for by the prior frame's unique
                             * shared frame successor. Therefore, we don't need to encode anything
                             * here.
                             */
                        } else {
                            if (sharedFrame) {
                                // Encode pointer to shared frame
                                int framePointer = NumUtil.safeToInt(sharedEncodedFrameIndexMap.get(frame));
                                encodingBuffer.putSV(encodeCompressedFirstEntry(framePointer, false));
                            } else {
                                // Encode unique frame
                                encodeCompressedFrame(encodingBuffer, encoders, frame, NO_SUCCESSOR_INDEX_MARKER);
                            }
                        }
                        prevShared = sharedFrame;
                        prevFrame = frame;
                    }
                }
            }
        }

        /**
         * @return frame's single successor, or {@code null} if the frame has either 0 or >1
         *         successor.
         */
        private CompressedFrameData getUniqueSuccessor(CompressedFrameData frame) {
            Set<CompressedFrameData> frameSuccessors = frameSuccessorMap.get(frame);
            if (frameSuccessors != null && frameSuccessors.size() == 1) {
                return frameSuccessors.iterator().next();
            }
            return null;
        }

        private static void encodeCompressedFrame(UnsafeArrayTypeWriter encodingBuffer, Encoders encoders, CompressedFrameData frame, int uniqueSuccessorIndex) {
            int classIndex = encoders.sourceClasses.getIndex(frame.sourceClass);
            int methodIndex = encoders.sourceMethodNames.getIndex(frame.sourceMethodName);

            encodingBuffer.putSV(encodeCompressedFirstEntry(classIndex, true));
            boolean encodeUniqueSuccessor = uniqueSuccessorIndex != NO_SUCCESSOR_INDEX_MARKER;
            encodingBuffer.putSV(encodeCompressedMethodIndex(methodIndex, encodeUniqueSuccessor));
            encodingBuffer.putSV(encodeCompressedSourceLineNumber(frame.sourceLineNumber, frame.isSliceEnd));
            encodingBuffer.putUV(frame.encodedBci);
            encodingBuffer.putSV(frame.methodId);
            if (encodeUniqueSuccessor) {
                encodingBuffer.putSV(uniqueSuccessorIndex);
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
            List<CompressedFrameData> slice = frameSlices.get(data.frameSliceIndex);
            for (FrameInfoQueryResult cur = data.frame; cur != null; cur = cur.caller) {
                assert cur == data.frame || !cur.isDeoptEntry : "Deoptimization entry information for caller frames is not persisted";

                cur.sourceClassIndex = encoders.sourceClasses.getIndex(cur.sourceClass);
                cur.sourceMethodNameIndex = encoders.sourceMethodNames.getIndex(cur.sourceMethodName);
                boolean isSliceEnd = cur.caller == null;
                CompressedFrameData frame = new CompressedFrameData(cur.sourceClass, cur.sourceMethodName, cur.sourceLineNumber, cur.encodedBci, cur.methodId, isSliceEnd);
                assert frame.equals(slice.get(curIdx));
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

    private final CalleeSavedRegisters calleeSavedRegisters;

    protected FrameInfoEncoder(Customization customization, Encoders encoders) {
        this.customization = customization;
        this.encoders = encoders;
        this.allDebugInfos = new ArrayList<>();
        this.frameMetadata = new CompressedFrameInfoEncodingMetadata();

        if (CalleeSavedRegisters.supportedByPlatform()) {
            calleeSavedRegisters = CalleeSavedRegisters.singleton();
        } else {
            calleeSavedRegisters = null;
        }
    }

    protected FrameData addDebugInfo(ResolvedJavaMethod method, CompilationResult compilation, Infopoint infopoint, int totalFrameSize) {
        boolean isDeoptEntry = customization.isDeoptEntry(method, compilation, infopoint);
        customization.recordFrame(method, infopoint, isDeoptEntry);
        boolean includeLocalValues = customization.includeLocalValues(method, infopoint, isDeoptEntry);

        DebugInfo debugInfo = infopoint.debugInfo;
        FrameData data = new FrameData(debugInfo, totalFrameSize, new ValueInfo[countVirtualObjects(debugInfo)][], false);
        initializeFrameInfo(data.frame, data, debugInfo.frame(), isDeoptEntry, includeLocalValues);

        List<CompressedFrameData> frameSlice = includeLocalValues ? null : new ArrayList<>();
        BytecodeFrame bytecodeFrame = data.debugInfo.frame();
        for (FrameInfoQueryResult resultFrame = data.frame; resultFrame != null; resultFrame = resultFrame.caller) {
            assert bytecodeFrame != null;
            customization.fillSourceFields(bytecodeFrame.getMethod(), resultFrame);

            // save source class and method name
            final Class<?> sourceClass = resultFrame.sourceClass;
            final String sourceMethodName = resultFrame.sourceMethodName;
            encoders.sourceClasses.addObject(sourceClass);
            encoders.sourceMethodNames.addObject(sourceMethodName);

            // save encoding metadata
            assert resultFrame.hasLocalValueInfo() == includeLocalValues;
            if (!includeLocalValues) {
                CompressedFrameData frame = new CompressedFrameData(sourceClass, sourceMethodName, resultFrame.sourceLineNumber, resultFrame.encodedBci,
                                resultFrame.methodId, resultFrame.caller == null);
                frameSlice.add(frame);
            }

            bytecodeFrame = bytecodeFrame.caller();
        }
        if (!includeLocalValues) {
            frameMetadata.addFrameSlice(data, frameSlice);
        }

        allDebugInfos.add(data);
        return data;
    }

    protected FrameData addDefaultDebugInfo(ResolvedJavaMethod method, int totalFrameSize) {
        FrameData data = new FrameData(null, totalFrameSize, null, true);
        data.frame.encodedBci = FrameInfoEncoder.encodeBci(0, false, false);
        customization.fillSourceFields(method, data.frame);
        // invalidate source line number
        data.frame.sourceLineNumber = -1;
        // save source class and method name
        Class<?> sourceClass = data.frame.sourceClass;
        String sourceMethodName = data.frame.sourceMethodName;
        encoders.sourceClasses.addObject(sourceClass);
        encoders.sourceMethodNames.addObject(sourceMethodName);

        // save encoding metadata
        CompressedFrameData frame = new CompressedFrameData(sourceClass, sourceMethodName, data.frame.sourceLineNumber, data.frame.encodedBci, data.frame.methodId, true);
        frameMetadata.addFrameSlice(data, List.of(frame));

        allDebugInfos.add(data);
        return data;
    }

    private static int countVirtualObjects(DebugInfo debugInfo) {
        /*
         * We want to know the highest virtual object id in use in this DebugInfo. For that, we have
         * to recursively visit all VirtualObject in all frames.
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

    private void initializeFrameInfo(FrameInfoQueryResult frameInfo, FrameData data, BytecodeFrame frame, boolean isDeoptEntry, boolean needLocalValues) {
        if (frame.caller() != null) {
            assert !isDeoptEntry : "Deoptimization entry point information for caller frames is not encoded";
            frameInfo.caller = new FrameInfoQueryResult();
            initializeFrameInfo(frameInfo.caller, data, frame.caller(), false, needLocalValues);
        }
        frameInfo.virtualObjects = data.virtualObjects;
        frameInfo.encodedBci = encodeBci(frame.getBCI(), frame.duringCall, frame.rethrowException);
        frameInfo.isDeoptEntry = isDeoptEntry;

        ValueInfo[] valueInfos = null;
        SharedMethod method = (SharedMethod) frame.getMethod();
        if (ImageSingletons.contains(CallStackFrameMethodData.class)) {
            frameInfo.methodId = ImageSingletons.lookup(CallStackFrameMethodData.class).getMethodId(method);
            ImageSingletons.lookup(CallStackFrameMethodInfo.class).addMethodInfo(method, frameInfo.methodId);
        }

        if (needLocalValues) {
            if (customization.storeDeoptTargetMethod()) {
                frameInfo.deoptMethod = method;
                encoders.objectConstants.addObject(SubstrateObjectConstant.forObject(method));
            }
            frameInfo.deoptMethodOffset = method.getDeoptOffsetInImage();

            frameInfo.numLocals = frame.numLocals;
            frameInfo.numStack = frame.numStack;
            frameInfo.numLocks = frame.numLocks;

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
        frameInfo.valueInfos = valueInfos;

        ImageSingletons.lookup(Counters.class).frameCount.inc();
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
            // TODO BS GR-42085 The first check is needed when safe-point sampling is on. Is this
            // OK?
            // e.g. stackSlot -> stack:32|V128_SINGLE,
            // stackSlot.getPlatformKind() -> V128_SINGLE,
            // stackSlot.getPlatformKind().getVectorLength() -> 4
            result.isCompressedReference = stackSlot.getPlatformKind().getVectorLength() == 1 && isCompressedReference(stackSlot);
            ImageSingletons.lookup(Counters.class).stackValueCount.inc();

        } else if (ReservedRegisters.singleton().isAllowedInFrameState(value)) {
            RegisterValue register = (RegisterValue) value;
            result.type = ValueType.ReservedRegister;
            result.data = ValueUtil.asRegister(register).number;
            result.isCompressedReference = isCompressedReference(register);
            ImageSingletons.lookup(Counters.class).registerValueCount.inc();

        } else if (calleeSavedRegisters != null && value instanceof RegisterValue) {
            if (isDeoptEntry) {
                throw VMError.shouldNotReachHere("Cannot encode registers in deoptimization entry point: value " + value + " in method " +
                                data.debugInfo.getBytecodePosition().getMethod().format("%H.%n(%p)"));
            }

            RegisterValue registerValue = (RegisterValue) value;
            Register register = ValueUtil.asRegister(registerValue);
            if (!calleeSavedRegisters.calleeSaveable(register)) {
                throw VMError.shouldNotReachHere("Register is not calleeSaveable: register " + registerValue + " in method " + data.debugInfo.getBytecodePosition().getMethod().format("%H.%n(%p)"));
            }
            result.type = ValueType.Register;
            result.data = calleeSavedRegisters.getOffsetInFrame(register);
            // TODO BS GR-42085 The first check is needed when safe-point sampling is on. Is this
            // OK?
            // e.g. registerValue -> xmm0|V128_SINGLE,
            // registerValue.getPlatformKind() -> V128_SINGLE,
            // registerValue.getPlatformKind().getVectorLength() -> 4
            result.isCompressedReference = registerValue.getPlatformKind().getVectorLength() == 1 && isCompressedReference(registerValue);
            ImageSingletons.lookup(Counters.class).registerValueCount.inc();

        } else if (value instanceof JavaConstant) {
            JavaConstant constant = (JavaConstant) value;
            result.value = constant;
            if (constant instanceof CompressibleConstant) {
                result.isCompressedReference = ((CompressibleConstant) constant).isCompressed();
            }
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
            throw VMError.shouldNotReachHereUnexpectedInput(value); // ExcludeFromJacocoGeneratedReport
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
                throw VMError.shouldNotReachHereUnexpectedInput(byteCount); // ExcludeFromJacocoGeneratedReport
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
            assert cur.encodedBci != FrameInfoDecoder.ENCODED_BCI_NO_CALLER : "used as the end marker during decoding";
            assert cur.hasLocalValueInfo() : "Compressed frame info must be used when no local values are needed";
            assert cur == data.frame || !cur.isDeoptEntry : "Deoptimization entry information for caller frames is not persisted";

            encodingBuffer.putUV(cur.encodedBci);
            encodingBuffer.putUV(cur.numLocks);
            encodingBuffer.putUV(cur.numLocals);
            encodingBuffer.putUV(cur.numStack);

            int deoptMethodIndex;
            if (cur.deoptMethod != null) {
                deoptMethodIndex = -1 - encoders.objectConstants.getIndex(SubstrateObjectConstant.forObject(cur.deoptMethod));
                assert deoptMethodIndex < 0;
                assert cur.getDeoptMethodOffset() == cur.deoptMethod.getDeoptOffsetInImage();
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

            final int classIndex = encoders.sourceClasses.getIndex(cur.sourceClass);
            final int methodIndex = encoders.sourceMethodNames.getIndex(cur.sourceMethodName);

            cur.sourceClassIndex = classIndex;
            cur.sourceMethodNameIndex = methodIndex;

            encodingBuffer.putSV(classIndex);
            encodingBuffer.putSV(methodIndex);
            encodingBuffer.putSV(cur.sourceLineNumber);
            encodingBuffer.putUV(cur.methodId);
        }
        encodingBuffer.putUV(FrameInfoDecoder.ENCODED_BCI_NO_CALLER);
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
        long result = (((long) bci + FrameInfoDecoder.ENCODED_BCI_ADDEND) << FrameInfoDecoder.ENCODED_BCI_SHIFT) |
                        (duringCall ? FrameInfoDecoder.ENCODED_BCI_DURING_CALL_MASK : 0) |
                        (rethrowException ? FrameInfoDecoder.ENCODED_BCI_RETHROW_EXCEPTION_MASK : 0);
        VMError.guarantee(result >= 0, "encoded bci is stored as an unsigned value");
        VMError.guarantee(result != FrameInfoDecoder.ENCODED_BCI_NO_CALLER, "Encoding must not return marker value");
        return result;
    }

    /**
     * Encode first value within a compressed frame. This may be either the classIndex, or a pointer
     * to a shared frame.
     *
     * @param isClassIndex whether this value is a class index or a pointer
     */
    private static int encodeCompressedFirstEntry(int value, boolean isClassIndex) {
        VMError.guarantee(value >= 0);
        int encodedValue = isClassIndex ? value : -(value + FrameInfoDecoder.COMPRESSED_FRAME_POINTER_ADDEND);

        VMError.guarantee(encodedValue != FrameInfoDecoder.UNCOMPRESSED_FRAME_SLICE_MARKER);
        return encodedValue;
    }

    /**
     * Encode method index within a compressed frame. If this frame also has a unique shared frame
     * successor, then the method index is encoded as a negative value.
     */
    private static int encodeCompressedMethodIndex(int methodIndex, boolean hasUniqueSharedFrameSuccessor) {
        VMError.guarantee(methodIndex >= 0);
        if (!hasUniqueSharedFrameSuccessor) {
            return methodIndex;
        } else {
            return -(methodIndex + FrameInfoDecoder.COMPRESSED_UNIQUE_SUCCESSOR_ADDEND);
        }
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
            ReusableTypeReader reader = new ReusableTypeReader(CodeInfoAccess.getFrameInfoEncodings(info), expectedData.encodedFrameInfoIndex);
            FrameInfoQueryResult actualFrame = FrameInfoDecoder.decodeFrameInfo(expectedData.frame.isDeoptEntry, reader, info);
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
            assert expectedFrame.isDeoptEntry() == actualFrame.isDeoptEntry();
            assert expectedFrame.hasLocalValueInfo() == actualFrame.hasLocalValueInfo();
            if (expectedFrame.hasLocalValueInfo()) {
                assert expectedFrame.getEncodedBci() == actualFrame.getEncodedBci();
                assert expectedFrame.getMethodId() == actualFrame.getMethodId();
                assert expectedFrame.getDeoptMethod() == null && actualFrame.getDeoptMethod() == null ||
                                (expectedFrame.getDeoptMethod() != null && expectedFrame.getDeoptMethod().equals(actualFrame.getDeoptMethod()));
                assert expectedFrame.getDeoptMethodOffset() == actualFrame.getDeoptMethodOffset();
                assert expectedFrame.getNumLocals() == actualFrame.getNumLocals();
                assert expectedFrame.getNumStack() == actualFrame.getNumStack();
                assert expectedFrame.getNumLocks() == actualFrame.getNumLocks();

                verifyValues(expectedFrame.getValueInfos(), actualFrame.getValueInfos());
                assert expectedFrame.getVirtualObjects() == expectedTopFrame.getVirtualObjects();
                assert actualFrame.getVirtualObjects() == actualTopFrame.getVirtualObjects();
            }

            assert Objects.equals(expectedFrame.getSourceClass(), actualFrame.getSourceClass());
            assert Objects.equals(expectedFrame.getSourceMethodName(), actualFrame.getSourceMethodName());
            assert expectedFrame.getSourceLineNumber() == actualFrame.getSourceLineNumber();
            assert expectedFrame.getMethodId() == actualFrame.getMethodId();

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
            assert Objects.equals(expectedConstant, actualConstant) : " Constants are not equal: expected=" + expectedConstant + " actual=" + actualConstant;
        }
    }
}
