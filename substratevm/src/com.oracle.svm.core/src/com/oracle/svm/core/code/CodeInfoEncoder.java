/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.shouldNotReachHereUnexpectedInput;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.code.FrameInfoDecoder.ConstantAccess;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueInfo;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueType;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.deopt.DeoptEntryInfopoint;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.CodeReferenceMapEncoder;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceMapEncoder;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.imagelayer.BuildingImageLayerPredicate;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.util.FrequencyEncoder;
import jdk.graal.compiler.core.common.util.TypeConversion;
import jdk.graal.compiler.core.common.util.TypeWriter;
import jdk.graal.compiler.core.common.util.UnsafeArrayTypeWriter;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.options.Option;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackLockValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ExceptionHandler;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CodeInfoEncoder {

    public static class Options {
        @Option(help = "Statistics about code and deoptimization information") //
        public static final HostedOptionKey<Boolean> CodeInfoEncoderCounters = new HostedOptionKey<>(false);
    }

    public static class Counters {
        public final Counter.Group group = new Counter.Group(Options.CodeInfoEncoderCounters, "CodeInfoEncoder");
        final Counter methodCount = new Counter(group, "Number of methods", "Number of methods encoded");
        final Counter codeSize = new Counter(group, "Code size", "Total size of machine code");
        final Counter referenceMapSize = new Counter(group, "Reference map size", "Total size of encoded reference maps");
        final Counter frameInfoSize = new Counter(group, "Frame info size", "Total size of encoded frame information");
        final Counter frameCount = new Counter(group, "Number of frames", "Number of frames encoded");
        final Counter stackValueCount = new Counter(group, "Number of stack values", "Number of stack values encoded");
        final Counter registerValueCount = new Counter(group, "Number of register values", "Number of register values encoded");
        final Counter constantValueCount = new Counter(group, "Number of constant values", "Number of constant values encoded");
        final Counter virtualObjectsCount = new Counter(group, "Number of virtual objects", "Number of virtual objects encoded");

        public void addToReferenceMapSize(long size) {
            referenceMapSize.add(size);
        }
    }

    public static final class Encoders {
        static final Class<?> INVALID_CLASS = null;
        static final String INVALID_METHOD_NAME = "";
        static final int INVALID_METHOD_MODIFIERS = -1;
        static final String INVALID_METHOD_SIGNATURE = null;

        public record Member(ResolvedJavaMethod method, Class<?> clazz, String name, String signature, int modifiers) {
        }

        public final FrequencyEncoder<JavaConstant> objectConstants;
        public final FrequencyEncoder<Class<?>> classes;
        /**
         * Own encoder for method and field name strings because they have different characteristics
         * than most {@linkplain #otherStrings other strings} and can be separated from them without
         * much duplication, which results in lower indexes for both kinds of strings that can in
         * turn be {@linkplain TypeWriter#putUV encoded in fewer bytes}, also in the
         * {@linkplain #encodeMethodTable() method table}.
         */
        public final FrequencyEncoder<String> memberNames;
        /**
         * Encoder for strings other than {@linkplain #memberNames member names} such as class or
         * variable names, signatures and messages. (These might also be separated like
         * {@link #memberNames}, but with less benefit for the added complexity)
         */
        public final FrequencyEncoder<String> otherStrings;
        private final FrequencyEncoder<Member> methods;
        private Member[] encodedMethods;

        public Encoders(boolean imageCode, Consumer<Class<?>> classVerifier) {
            this.objectConstants = FrequencyEncoder.createEqualityEncoder();

            /*
             * Only image code and metadata needs to store this data. Runtime code info should
             * reference only image methods via method ids.
             */
            assert imageCode == SubstrateUtil.HOSTED;
            this.classes = imageCode ? FrequencyEncoder.createVerifyingEqualityEncoder(classVerifier) : null;
            this.memberNames = imageCode ? FrequencyEncoder.createEqualityEncoder() : null;
            this.methods = imageCode ? FrequencyEncoder.createEqualityEncoder() : null;
            this.otherStrings = imageCode ? FrequencyEncoder.createEqualityEncoder() : null;
            if (imageCode) {
                // Ensure that a method id of 0 always means invalid/null.
                this.methods.addObject(null);
                this.classes.addObject(INVALID_CLASS);
                this.memberNames.addObject(INVALID_METHOD_NAME);
                if (shouldEncodeAllMethodMetadata()) {
                    this.otherStrings.addObject(INVALID_METHOD_SIGNATURE);
                }
            }
        }

        public void addMethod(ResolvedJavaMethod method, Class<?> clazz, String name, String signature, int modifiers) {
            VMError.guarantee(SubstrateUtil.HOSTED, "Runtime code info must reference image methods by id");

            Member member = new Member(Objects.requireNonNull(method), clazz, name, signature, modifiers);
            if (methods.addObject(member)) {
                classes.addObject(clazz);
                memberNames.addObject(name);
                if (shouldEncodeAllMethodMetadata()) {
                    otherStrings.addObject(signature);
                }
            }
        }

        public int findMethodIndex(ResolvedJavaMethod method, Class<?> clazz, String name, String signature, int modifiers, boolean optional) {
            VMError.guarantee(SubstrateUtil.HOSTED, "Runtime code info must obtain method ids from image code info");

            Member member = new Member(Objects.requireNonNull(method), clazz, name, signature, modifiers);
            return optional ? methods.findIndex(member) : methods.getIndex(member);
        }

        public ResolvedJavaMethod[] getEncodedMethods() {
            assert encodedMethods != null : "can call only once encoded (and only for image code)";
            return Stream.of(encodedMethods).map(m -> (m != null) ? m.method() : null).toArray(ResolvedJavaMethod[]::new);
        }

        private void encodeAllAndInstall(CodeInfo target, ReferenceAdjuster adjuster) {
            JavaConstant[] objectConstantsArray = encodeArray(objectConstants, JavaConstant[]::new);
            Class<?>[] classesArray = encodeArray(classes, Class[]::new);
            String[] memberNamesArray = encodeArray(memberNames, String[]::new);
            String[] otherStringsArray = encodeArray(otherStrings, String[]::new);

            int methodTableFirstId;
            if (ImageLayerBuildingSupport.buildingImageLayer()) {
                var idTracker = MethodTableFirstIDTracker.singleton();
                methodTableFirstId = idTracker.startingID;
                idTracker.nextStartingId = methodTableFirstId + methods.getLength();
            } else {
                methodTableFirstId = 0;
            }
            NonmovableArray<Byte> methodTable = encodeMethodTable();

            install(target, objectConstantsArray, classesArray, memberNamesArray, otherStringsArray, methodTable, methodTableFirstId, adjuster);
        }

        private static <T> T[] encodeArray(FrequencyEncoder<T> encoder, IntFunction<T[]> allocator) {
            if (encoder == null) {
                return null;
            }
            T[] array = allocator.apply(encoder.getLength());
            return encoder.encodeAll(array);
        }

        /**
         * Encodes the table of {@link #methods} as a byte array. Each table entry has the same size
         * to allow for indexes without gaps that can be {@linkplain TypeWriter#putUV encoded in
         * fewer bytes}. Still, the fields of the entries are dimensioned to not be larger than
         * necessary to index into another array such as {@link #classes}.
         *
         * @see CodeInfoDecoder#fillSourceFields
         */
        private NonmovableArray<Byte> encodeMethodTable() {
            if (methods == null) {
                return NonmovableArrays.nullArray();
            }
            VMError.guarantee(encodedMethods == null, "encoded already");
            encodedMethods = encodeArray(methods, Member[]::new);

            final boolean shortClassIndexes = (classes.getLength() <= 0xffff);
            final boolean shortNameIndexes = (memberNames.getLength() <= 0xffff);
            final boolean shortSignatureIndexes = (otherStrings.getLength() <= 0xffff);
            UnsafeArrayTypeWriter writer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
            assert encodedMethods[0] == null : "id 0 must mean invalid";
            encodeMethod(writer, INVALID_CLASS, INVALID_METHOD_NAME, INVALID_METHOD_SIGNATURE, INVALID_METHOD_MODIFIERS, shortClassIndexes, shortNameIndexes, shortSignatureIndexes);
            for (int id = 1; id < encodedMethods.length; id++) {
                encodeMethod(writer, encodedMethods[id].clazz, encodedMethods[id].name, encodedMethods[id].signature, encodedMethods[id].modifiers, shortClassIndexes, shortNameIndexes,
                                shortSignatureIndexes);
            }
            NonmovableArray<Byte> bytes = NonmovableArrays.createByteArray(NumUtil.safeToInt(writer.getBytesWritten()), NmtCategory.Code);
            writer.toByteBuffer(NonmovableArrays.asByteBuffer(bytes));
            return bytes;
        }

        private void encodeMethod(UnsafeArrayTypeWriter writer, Class<?> clazz, String name, String signature, int modifiers, boolean shortClassIndexes, boolean shortNameIndexes,
                        boolean shortSignatureIndexes) {
            int classIndex = classes.getIndex(clazz);
            if (shortClassIndexes) {
                writer.putU2(classIndex);
            } else {
                writer.putU4(classIndex);
            }
            int memberNamesIndex = memberNames.getIndex(name);
            if (shortNameIndexes) {
                writer.putU2(memberNamesIndex);
            } else {
                writer.putU4(memberNamesIndex);
            }
            if (shouldEncodeAllMethodMetadata()) {
                int signatureNamesIndex = otherStrings.getIndex(signature);
                if (shortSignatureIndexes) {
                    writer.putU2(signatureNamesIndex);
                } else {
                    writer.putU4(signatureNamesIndex);
                }
                writer.putS2(modifiers);
            }
        }

        @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed in target.")
        private static void install(CodeInfo target, JavaConstant[] objectConstantsArray, Class<?>[] classesArray, String[] memberNamesArray,
                        String[] otherStringsArray, NonmovableArray<Byte> methodTable, int methodTableFirstId, ReferenceAdjuster adjuster) {

            NonmovableObjectArray<Object> objectConstants = adjuster.copyOfObjectConstantArray(objectConstantsArray, NmtCategory.Code);
            NonmovableObjectArray<Class<?>> classes = (classesArray != null) ? adjuster.copyOfObjectArray(classesArray, NmtCategory.Code) : NonmovableArrays.nullArray();
            NonmovableObjectArray<String> memberNames = (memberNamesArray != null) ? adjuster.copyOfObjectArray(memberNamesArray, NmtCategory.Code) : NonmovableArrays.nullArray();
            NonmovableObjectArray<String> otherStrings = (otherStringsArray != null) ? adjuster.copyOfObjectArray(otherStringsArray, NmtCategory.Code) : NonmovableArrays.nullArray();

            CodeInfoAccess.setEncodings(target, objectConstants, classes, memberNames, otherStrings, methodTable, methodTableFirstId);
        }
    }

    static class IPData {
        protected long ip;
        protected int frameSizeEncoding;
        protected int exceptionOffset;
        protected ReferenceMapEncoder.Input referenceMap;
        protected long referenceMapIndex;
        protected FrameInfoEncoder.FrameData frameData;
        protected IPData next;
    }

    private final TreeMap<Long, IPData> entries;
    private final Encoders encoders;
    private final FrameInfoEncoder frameInfoEncoder;

    private NonmovableArray<Byte> codeInfoIndex;
    private NonmovableArray<Byte> codeInfoEncodings;
    private NonmovableArray<Byte> referenceMapEncoding;

    public CodeInfoEncoder(FrameInfoEncoder.Customization frameInfoCustomization, Encoders encoders) {
        this(frameInfoCustomization, encoders, FrameInfoDecoder.SubstrateConstantAccess);
    }

    public CodeInfoEncoder(FrameInfoEncoder.Customization frameInfoCustomization, Encoders encoders, ConstantAccess constantAccess) {
        this.entries = new TreeMap<>();
        this.encoders = encoders;
        this.frameInfoEncoder = new FrameInfoEncoder(frameInfoCustomization, encoders, constantAccess);
    }

    public FrameInfoEncoder getFrameInfoEncoder() {
        return frameInfoEncoder;
    }

    public Encoders getEncoders() {
        return encoders;
    }

    @Fold
    public static boolean shouldEncodeAllMethodMetadata() {
        /*
         * We don't support JFR stack traces if JIT compilation is enabled, so there's no need to
         * include extra method metadata. Additionally, including extra metadata would increase the
         * binary size.
         */
        return HasJfrSupport.get() && !RuntimeCompilation.isEnabled();
    }

    public static int getEntryOffset(Infopoint infopoint) {
        if (infopoint instanceof Call || infopoint instanceof DeoptEntryInfopoint) {
            int offset = infopoint.pcOffset;
            if (infopoint instanceof Call) {
                // add size of the Call instruction to get the PCEntry
                offset += ((Call) infopoint).size;
            }
            return offset;
        }
        return -1;
    }

    public void addMethod(SharedMethod method, CompilationResult compilation, int compilationOffset, int compilationSize) {
        int totalFrameSize = compilation.getTotalFrameSize();
        boolean isEntryPoint = method.isEntryPoint();
        boolean hasCalleeSavedRegisters = method.hasCalleeSavedRegisters();

        /* Mark the method start and register the frame size. */
        IPData startEntry = makeEntry(compilationOffset);
        FrameInfoEncoder.FrameData defaultFrameData = frameInfoEncoder.addDefaultDebugInfo(method, totalFrameSize);
        startEntry.frameData = defaultFrameData;
        startEntry.frameSizeEncoding = encodeFrameSize(totalFrameSize, true, isEntryPoint, hasCalleeSavedRegisters);

        /* Register the frame size for all entries that are starting points for the index. */
        long entryIP = CodeInfoDecoder.lookupEntryIP(CodeInfoDecoder.indexGranularity() + compilationOffset);
        while (entryIP <= CodeInfoDecoder.lookupEntryIP(compilationSize + compilationOffset - 1)) {
            IPData entry = makeEntry(entryIP);
            entry.frameData = defaultFrameData;
            entry.frameSizeEncoding = encodeFrameSize(totalFrameSize, false, isEntryPoint, hasCalleeSavedRegisters);
            entryIP += CodeInfoDecoder.indexGranularity();
        }

        EconomicSet<Integer> infopointOffsets = EconomicSet.create(Equivalence.DEFAULT);
        EconomicSet<Long> deoptEntryBcis = EconomicSet.create(Equivalence.DEFAULT);
        /* Make entries for all calls and deoptimization entry points of the method. */
        for (Infopoint infopoint : compilation.getInfopoints()) {
            final DebugInfo debugInfo = infopoint.debugInfo;
            if (debugInfo != null) {
                final int offset = getEntryOffset(infopoint);
                if (offset >= 0) {
                    boolean added = infopointOffsets.add(offset);
                    if (!added) {
                        throw VMError.shouldNotReachHere("Encoding two infopoints at same offset. Conflicting infopoint: " + infopoint);
                    }
                    IPData entry = makeEntry(offset + compilationOffset);
                    assert entry.referenceMap == null && (entry.frameData == null || entry.frameData.isDefaultFrameData) : entry;
                    entry.referenceMap = (ReferenceMapEncoder.Input) debugInfo.getReferenceMap();
                    entry.frameData = frameInfoEncoder.addDebugInfo(method, compilation, infopoint, totalFrameSize);
                    if (entry.frameData != null && entry.frameData.frame.isDeoptEntry) {
                        BytecodeFrame frame = debugInfo.frame();
                        long encodedBci = FrameInfoEncoder.encodeBci(frame.getBCI(), FrameState.StackState.of(frame));
                        added = deoptEntryBcis.add(encodedBci);
                        if (!added) {
                            throw VMError.shouldNotReachHere(String.format("Encoding two deopt entries at same encoded bci: %s (bci %s)%nmethod: %s",
                                            encodedBci, FrameInfoDecoder.readableBci(encodedBci), method));
                        }
                    }
                }
            }
        }

        /* Make entries for all exception handlers. */
        for (ExceptionHandler handler : compilation.getExceptionHandlers()) {
            final IPData entry = makeEntry(handler.pcOffset + compilationOffset);
            assert entry.exceptionOffset == 0 : entry;
            entry.exceptionOffset = handler.handlerPos - handler.pcOffset;
        }

        ImageSingletons.lookup(Counters.class).methodCount.inc();
        ImageSingletons.lookup(Counters.class).codeSize.add(compilationSize);
    }

    private IPData makeEntry(long ip) {
        IPData result = entries.get(ip);
        if (result == null) {
            result = new IPData();
            result.ip = ip;
            entries.put(ip, result);
        }
        return result;
    }

    public void encodeAllAndInstall(CodeInfo target, ReferenceAdjuster adjuster, Runnable recordActivity) {
        encoders.encodeAllAndInstall(target, adjuster);
        encodeReferenceMaps();
        frameInfoEncoder.encodeAllAndInstall(target, recordActivity);
        encodeIPData();

        install(target);
    }

    private void install(CodeInfo target) {
        CodeInfoAccess.setCodeInfo(target, codeInfoIndex, codeInfoEncodings, referenceMapEncoding);
    }

    private void encodeReferenceMaps() {
        CodeReferenceMapEncoder referenceMapEncoder = new CodeReferenceMapEncoder();
        for (IPData data : entries.values()) {
            referenceMapEncoder.add(data.referenceMap);
        }
        referenceMapEncoding = referenceMapEncoder.encodeAll();
        ImageSingletons.lookup(Counters.class).addToReferenceMapSize(referenceMapEncoder.getEncodingSize());
        for (IPData data : entries.values()) {
            data.referenceMapIndex = referenceMapEncoder.lookupEncoding(data.referenceMap);
        }
    }

    /**
     * Inverse of {@link CodeInfoDecoder#decodeTotalFrameSize} and
     * {@link CodeInfoDecoder#decodeMethodStart}.
     */
    protected int encodeFrameSize(int totalFrameSize, boolean methodStart, boolean isEntryPoint, boolean hasCalleeSavedRegisters) {
        VMError.guarantee((totalFrameSize & CodeInfoDecoder.FRAME_SIZE_STATUS_MASK) == 0, "Frame size must be aligned");

        return totalFrameSize |
                        (methodStart ? CodeInfoDecoder.FRAME_SIZE_METHOD_START : 0) |
                        (isEntryPoint ? CodeInfoDecoder.FRAME_SIZE_ENTRY_POINT : 0) |
                        (hasCalleeSavedRegisters ? CodeInfoDecoder.FRAME_SIZE_HAS_CALLEE_SAVED_REGISTERS : 0);
    }

    private void encodeIPData() {
        IPData first = null;
        IPData prev = null;
        for (IPData cur : entries.values()) {
            if (first == null) {
                first = cur;
            } else {
                while (!TypeConversion.isU1(cur.ip - prev.ip)) {
                    final IPData filler = new IPData();
                    filler.ip = prev.ip + 0xFF;
                    prev.next = filler;
                    prev = filler;
                }
                prev.next = cur;
            }
            prev = cur;
        }

        long nextIndexIP = 0;
        UnsafeArrayTypeWriter indexBuffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        UnsafeArrayTypeWriter encodingBuffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        for (IPData data = first; data != null; data = data.next) {
            assert data.ip <= nextIndexIP : data;
            if (data.ip == nextIndexIP) {
                indexBuffer.putU4(encodingBuffer.getBytesWritten());
                nextIndexIP += CodeInfoDecoder.indexGranularity();
            }

            int entryFlags = 0;
            entryFlags = entryFlags | flagsForSizeEncoding(data) << CodeInfoDecoder.FS_SHIFT;
            entryFlags = entryFlags | flagsForExceptionOffset(data) << CodeInfoDecoder.EX_SHIFT;
            entryFlags = entryFlags | flagsForReferenceMapIndex(data) << CodeInfoDecoder.RM_SHIFT;
            entryFlags = entryFlags | flagsForDeoptFrameInfo(data) << CodeInfoDecoder.FI_SHIFT;

            encodingBuffer.putU1(entryFlags);
            encodingBuffer.putU1(data.next == null ? CodeInfoDecoder.DELTA_END_OF_TABLE : (data.next.ip - data.ip));

            writeSizeEncoding(encodingBuffer, data, entryFlags);
            writeExceptionOffset(encodingBuffer, data, entryFlags);
            writeReferenceMapIndex(encodingBuffer, data, entryFlags);
            writeEncodedFrameInfo(encodingBuffer, data, entryFlags);
        }

        codeInfoIndex = NonmovableArrays.createByteArray(TypeConversion.asU4(indexBuffer.getBytesWritten()), NmtCategory.Code);
        indexBuffer.toByteBuffer(NonmovableArrays.asByteBuffer(codeInfoIndex));
        codeInfoEncodings = NonmovableArrays.createByteArray(TypeConversion.asU4(encodingBuffer.getBytesWritten()), NmtCategory.Code);
        encodingBuffer.toByteBuffer(NonmovableArrays.asByteBuffer(codeInfoEncodings));
    }

    /**
     * Inverse of {@link CodeInfoDecoder#updateSizeEncoding}.
     */
    private static int flagsForSizeEncoding(IPData data) {
        if (data.frameSizeEncoding == 0) {
            return CodeInfoDecoder.FS_NO_CHANGE;
        } else if (TypeConversion.isS1(data.frameSizeEncoding)) {
            return CodeInfoDecoder.FS_SIZE_S1;
        } else if (TypeConversion.isS2(data.frameSizeEncoding)) {
            return CodeInfoDecoder.FS_SIZE_S2;
        } else if (TypeConversion.isS4(data.frameSizeEncoding)) {
            return CodeInfoDecoder.FS_SIZE_S4;
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static void writeSizeEncoding(UnsafeArrayTypeWriter writeBuffer, IPData data, int entryFlags) {
        switch (CodeInfoDecoder.extractFS(entryFlags)) {
            case CodeInfoDecoder.FS_SIZE_S1:
                writeBuffer.putS1(data.frameSizeEncoding);
                break;
            case CodeInfoDecoder.FS_SIZE_S2:
                writeBuffer.putS2(data.frameSizeEncoding);
                break;
            case CodeInfoDecoder.FS_SIZE_S4:
                writeBuffer.putS4(data.frameSizeEncoding);
                break;
        }
    }

    /**
     * Inverse of {@link CodeInfoDecoder#loadExceptionOffset}.
     */
    private static int flagsForExceptionOffset(IPData data) {
        if (data.exceptionOffset == 0) {
            return CodeInfoDecoder.EX_NO_HANDLER;
        } else if (TypeConversion.isS1(data.exceptionOffset)) {
            return CodeInfoDecoder.EX_OFFSET_S1;
        } else if (TypeConversion.isS2(data.exceptionOffset)) {
            return CodeInfoDecoder.EX_OFFSET_S2;
        } else if (TypeConversion.isS4(data.exceptionOffset)) {
            return CodeInfoDecoder.EX_OFFSET_S4;
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static void writeExceptionOffset(UnsafeArrayTypeWriter writeBuffer, IPData data, int entryFlags) {
        switch (CodeInfoDecoder.extractEX(entryFlags)) {
            case CodeInfoDecoder.EX_OFFSET_S1:
                writeBuffer.putS1(data.exceptionOffset);
                break;
            case CodeInfoDecoder.EX_OFFSET_S2:
                writeBuffer.putS2(data.exceptionOffset);
                break;
            case CodeInfoDecoder.EX_OFFSET_S4:
                writeBuffer.putS4(data.exceptionOffset);
                break;
        }
    }

    /**
     * Inverse of {@link CodeInfoDecoder#loadReferenceMapIndex}.
     */
    private static int flagsForReferenceMapIndex(IPData data) {
        if (data.referenceMap == null) {
            return CodeInfoDecoder.RM_NO_MAP;
        } else if (data.referenceMap.isEmpty()) {
            return CodeInfoDecoder.RM_EMPTY_MAP;
        } else if (TypeConversion.isU2(data.referenceMapIndex)) {
            return CodeInfoDecoder.RM_INDEX_U2;
        } else if (TypeConversion.isU4(data.referenceMapIndex)) {
            return CodeInfoDecoder.RM_INDEX_U4;
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static void writeReferenceMapIndex(UnsafeArrayTypeWriter writeBuffer, IPData data, int entryFlags) {
        switch (CodeInfoDecoder.extractRM(entryFlags)) {
            case CodeInfoDecoder.RM_INDEX_U2:
                writeBuffer.putU2(data.referenceMapIndex);
                break;
            case CodeInfoDecoder.RM_INDEX_U4:
                writeBuffer.putU4(data.referenceMapIndex);
                break;
        }
    }

    /**
     * Inverse of {@link CodeInfoDecoder#loadFrameInfo}.
     */
    private static int flagsForDeoptFrameInfo(IPData data) {
        if (data.frameData == null) {
            return CodeInfoDecoder.FI_NO_DEOPT;
        } else if (TypeConversion.isS4(data.frameData.encodedFrameInfoIndex)) {
            if (data.frameData.frame.isDeoptEntry) {
                return CodeInfoDecoder.FI_DEOPT_ENTRY_INDEX_S4;
            } else {
                if (data.frameData.isDefaultFrameData) {
                    return CodeInfoDecoder.FI_DEFAULT_INFO_INDEX_S4;
                } else {
                    return CodeInfoDecoder.FI_INFO_ONLY_INDEX_S4;
                }
            }
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static void writeEncodedFrameInfo(UnsafeArrayTypeWriter writeBuffer, IPData data, int entryFlags) {
        switch (CodeInfoDecoder.extractFI(entryFlags)) {
            case CodeInfoDecoder.FI_DEFAULT_INFO_INDEX_S4:
            case CodeInfoDecoder.FI_DEOPT_ENTRY_INDEX_S4:
            case CodeInfoDecoder.FI_INFO_ONLY_INDEX_S4:
                writeBuffer.putS4(data.frameData.encodedFrameInfoIndex);
                break;
        }
    }

    public static boolean verifyMethod(SharedMethod method, CompilationResult compilation, int compilationOffset, int compilationSize, CodeInfo info, ConstantAccess constantAccess) {
        CodeInfoVerifier verifier = new CodeInfoVerifier(constantAccess);
        verifier.verifyMethod(method, compilation, compilationOffset, compilationSize, info);
        return true;
    }

    public boolean verifyFrameInfo(CodeInfo info) {
        frameInfoEncoder.verifyEncoding(info);
        return true;
    }
}

class CodeInfoVerifier {
    private final ConstantAccess constantAccess;

    CodeInfoVerifier(ConstantAccess constantAccess) {
        this.constantAccess = constantAccess;
    }

    void verifyMethod(SharedMethod method, CompilationResult compilation, int compilationOffset, int compilationSize, CodeInfo info) {
        CodeInfoQueryResult queryResult = new CodeInfoQueryResult();
        for (int relativeIP = 0; relativeIP < compilationSize; relativeIP++) {
            int totalIP = relativeIP + compilationOffset;
            CodeInfoAccess.lookupCodeInfo(info, totalIP, queryResult, constantAccess);
            assert queryResult.isEntryPoint() == method.isEntryPoint() : queryResult;
            assert queryResult.hasCalleeSavedRegisters() == method.hasCalleeSavedRegisters() : queryResult;
            assert queryResult.getTotalFrameSize() == compilation.getTotalFrameSize() : queryResult;

            assert CodeInfoAccess.lookupStackReferenceMapIndex(info, totalIP) == queryResult.getReferenceMapIndex() : queryResult;
        }

        for (Infopoint infopoint : compilation.getInfopoints()) {
            if (infopoint.debugInfo != null) {
                int offset = CodeInfoEncoder.getEntryOffset(infopoint);
                if (offset >= 0) {
                    assert offset < compilationSize : infopoint;
                    CodeInfoAccess.lookupCodeInfo(info, offset + compilationOffset, queryResult, constantAccess);

                    CollectingObjectReferenceVisitor visitor = new CollectingObjectReferenceVisitor();
                    CodeReferenceMapDecoder.walkOffsetsFromPointer(WordFactory.zero(), CodeInfoAccess.getStackReferenceMapEncoding(info), queryResult.getReferenceMapIndex(), visitor, null);
                    ReferenceMapEncoder.Input expected = (ReferenceMapEncoder.Input) infopoint.debugInfo.getReferenceMap();
                    visitor.result.verify();
                    assert expected.equals(visitor.result) : infopoint;

                    if (queryResult.frameInfo != CodeInfoQueryResult.NO_FRAME_INFO) {
                        verifyFrame(compilation, infopoint.debugInfo.frame(), queryResult.frameInfo, new BitSet());
                    }
                }
            }
        }

        for (ExceptionHandler handler : compilation.getExceptionHandlers()) {
            int offset = handler.pcOffset;
            assert offset >= 0 && offset < compilationSize : handler;

            CodeInfoAccess.lookupCodeInfo(info, offset + compilationOffset, queryResult, constantAccess);
            long actual = queryResult.getExceptionOffset();
            long expected = handler.handlerPos - handler.pcOffset;
            assert expected != 0 : handler;
            assert expected == actual : handler;
        }
    }

    private void verifyFrame(CompilationResult compilation, BytecodeFrame expectedFrame, FrameInfoQueryResult actualFrame, BitSet visitedVirtualObjects) {
        assert (expectedFrame == null) == (actualFrame == null) : actualFrame;
        if (expectedFrame == null || !actualFrame.hasLocalValueInfo()) {
            return;
        }
        verifyFrame(compilation, expectedFrame.caller(), actualFrame.getCaller(), visitedVirtualObjects);

        for (int i = 0; i < expectedFrame.values.length; i++) {
            JavaValue expectedValue = expectedFrame.values[i];
            if (i >= actualFrame.getValueInfos().length) {
                assert ValueUtil.isIllegalJavaValue(expectedValue) : actualFrame;
                continue;
            }

            ValueInfo actualValue = actualFrame.getValueInfos()[i];

            JavaKind expectedKind = FrameInfoEncoder.getFrameValueKind(expectedFrame, i);
            assert expectedKind == actualValue.getKind() : actualFrame;
            verifyValue(compilation, expectedValue, actualValue, actualFrame, visitedVirtualObjects);
        }
    }

    private void verifyValue(CompilationResult compilation, JavaValue e, ValueInfo actualValue, FrameInfoQueryResult actualFrame, BitSet visitedVirtualObjects) {
        JavaValue expectedValue = e;

        if (expectedValue instanceof StackLockValue lock) {
            assert ValueUtil.isIllegal(lock.getSlot()) : actualValue;
            assert lock.isEliminated() == actualValue.isEliminatedMonitor() : actualValue;
            expectedValue = lock.getOwner();
        } else {
            assert !actualValue.isEliminatedMonitor() : actualValue;
        }

        if (ValueUtil.isIllegalJavaValue(expectedValue)) {
            assert actualValue.getType() == ValueType.Illegal : actualValue;

        } else if (ValueUtil.isConstantJavaValue(expectedValue)) {
            assert actualValue.getType() == ValueType.Constant || actualValue.getType() == ValueType.DefaultConstant : actualValue;
            JavaConstant expectedConstant = ValueUtil.asConstantJavaValue(expectedValue);
            JavaConstant actualConstant = actualValue.getValue();
            FrameInfoVerifier.verifyConstant(expectedConstant, actualConstant);

        } else if (expectedValue instanceof StackSlot) {
            assert actualValue.getType() == ValueType.StackSlot : actualValue;
            int expectedOffset = ((StackSlot) expectedValue).getOffset(compilation.getTotalFrameSize());
            long actualOffset = actualValue.getData();
            assert expectedOffset == actualOffset : actualValue;

        } else if (ReservedRegisters.singleton().isAllowedInFrameState(expectedValue)) {
            assert actualValue.getType() == ValueType.ReservedRegister : actualValue;
            int expectedNumber = ValueUtil.asRegister((RegisterValue) expectedValue).number;
            long actualNumber = actualValue.getData();
            assert expectedNumber == actualNumber : actualValue;

        } else if (CalleeSavedRegisters.supportedByPlatform() && expectedValue instanceof RegisterValue) {
            assert actualValue.getType() == ValueType.Register : actualValue;
            int expectedOffset = CalleeSavedRegisters.singleton().getOffsetInFrame(ValueUtil.asRegister((RegisterValue) expectedValue));
            long actualOffset = actualValue.getData();
            assert expectedOffset == actualOffset : actualValue;
            assert actualOffset < 0 : "Registers are stored in callee saved area of callee frame, i.e., with negative offset";

        } else if (ValueUtil.isVirtualObject(expectedValue)) {
            assert actualValue.getType() == ValueType.VirtualObject : actualValue;
            int expectedId = ValueUtil.asVirtualObject(expectedValue).getId();
            long actualId = actualValue.getData();
            assert expectedId == actualId : actualValue;

            verifyVirtualObject(compilation, ValueUtil.asVirtualObject(expectedValue), actualFrame.getVirtualObjects()[expectedId], actualFrame, visitedVirtualObjects);

        } else {
            throw shouldNotReachHereUnexpectedInput(expectedValue); // ExcludeFromJacocoGeneratedReport
        }
    }

    private void verifyVirtualObject(CompilationResult compilation, VirtualObject expectedObject, ValueInfo[] actualObject, FrameInfoQueryResult actualFrame, BitSet visitedVirtualObjects) {
        if (visitedVirtualObjects.get(expectedObject.getId())) {
            return;
        }
        visitedVirtualObjects.set(expectedObject.getId());

        ObjectLayout objectLayout = ConfigurationValues.getObjectLayout();
        SharedType expectedType = (SharedType) expectedObject.getType();

        // TODO assertion does not hold for now because expectedHub is java.lang.Class, but
        // actualHub is DynamicHub
        // ValueInfo actualHub = actualObject[0];
        // assert actualHub.getType() == ValueType.Constant && actualHub.getKind() ==
        // Kind.Object && expectedType.getObjectHub().equals(actualHub.getValue());

        if (expectedType.isArray()) {
            JavaKind kind = ((SharedType) expectedType.getComponentType()).getStorageKind();
            int expectedLength = 0;
            for (int i = 0; i < expectedObject.getValues().length; i++) {
                JavaValue expectedValue = expectedObject.getValues()[i];
                UnsignedWord expectedOffset = WordFactory.unsigned(objectLayout.getArrayElementOffset(kind, expectedLength));
                ValueInfo actualValue = findActualArrayElement(actualObject, expectedOffset);
                verifyValue(compilation, expectedValue, actualValue, actualFrame, visitedVirtualObjects);

                JavaKind valueKind = expectedObject.getSlotKind(i);
                if (objectLayout.sizeInBytes(kind) == 4 && objectLayout.sizeInBytes(valueKind) == 8) {
                    /*
                     * Truffle uses arrays in a non-standard way: it declares an int[] array and
                     * uses it to also store long and double values. These values span two array
                     * elements - so we have to add 2 to the length.
                     */
                    expectedLength += 2;
                } else {
                    expectedLength++;
                }
            }
            int actualLength = actualObject[1].value.asInt();
            assert expectedLength == actualLength : actualFrame;

        } else {
            SharedField[] expectedFields = (SharedField[]) expectedType.getInstanceFields(true);
            int fieldIdx = 0;
            int valueIdx = 0;
            while (valueIdx < expectedObject.getValues().length) {
                SharedField expectedField = expectedFields[fieldIdx];
                fieldIdx += 1;
                JavaValue expectedValue = expectedObject.getValues()[valueIdx];
                JavaKind valueKind = expectedObject.getSlotKind(valueIdx);
                valueIdx += 1;

                JavaKind kind = expectedField.getStorageKind();
                if (objectLayout.sizeInBytes(kind) == 4 && objectLayout.sizeInBytes(valueKind) == 8) {
                    /*
                     * Truffle uses fields in a non-standard way: it declares a couple of
                     * (consecutive) int fields, and uses them to also store long and double values.
                     * These values span two fields - so we have to ignore a field.
                     */
                    fieldIdx++;
                }

                UnsignedWord expectedOffset = WordFactory.unsigned(expectedField.getLocation());
                ValueInfo actualValue = findActualField(actualObject, expectedOffset);
                verifyValue(compilation, expectedValue, actualValue, actualFrame, visitedVirtualObjects);
            }
        }
    }

    private ValueInfo findActualArrayElement(ValueInfo[] actualObject, UnsignedWord expectedOffset) {
        DynamicHub hub = (DynamicHub) constantAccess.asObject(actualObject[0].getValue());
        ObjectLayout objectLayout = ConfigurationValues.getObjectLayout();
        assert LayoutEncoding.isArray(hub.getLayoutEncoding()) : hub;
        return findActualValue(actualObject, expectedOffset, objectLayout, LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding()), 2);
    }

    private ValueInfo findActualField(ValueInfo[] actualObject, UnsignedWord expectedOffset) {
        DynamicHub hub = (DynamicHub) constantAccess.asObject(actualObject[0].getValue());
        ObjectLayout objectLayout = ConfigurationValues.getObjectLayout();
        assert LayoutEncoding.isPureInstance(hub.getLayoutEncoding()) : hub;
        return findActualValue(actualObject, expectedOffset, objectLayout, WordFactory.unsigned(objectLayout.getFirstFieldOffset()), 1);
    }

    private static ValueInfo findActualValue(ValueInfo[] actualObject, UnsignedWord expectedOffset, ObjectLayout objectLayout, UnsignedWord startOffset, int startIdx) {
        UnsignedWord curOffset = startOffset;
        int curIdx = startIdx;
        while (curOffset.belowThan(expectedOffset)) {
            ValueInfo value = actualObject[curIdx];
            curOffset = curOffset.add(objectLayout.sizeInBytes(value.getKind()));
            curIdx++;
        }
        if (curOffset.equal(expectedOffset)) {
            return actualObject[curIdx];
        }
        /*
         * If we go after the expected offset, return an illegal. Takes care of large byte array
         * accesses, and should raise flags for other cases.
         */
        ValueInfo illegal = new ValueInfo();
        illegal.type = ValueType.Illegal;
        return illegal;
    }
}

@AutomaticallyRegisteredImageSingleton(onlyWith = BuildingImageLayerPredicate.class)
class MethodTableFirstIDTracker implements LayeredImageSingleton {
    public final int startingID;
    public int nextStartingId = -1;

    MethodTableFirstIDTracker() {
        this(0);
    }

    static MethodTableFirstIDTracker singleton() {
        return ImageSingletons.lookup(MethodTableFirstIDTracker.class);
    }

    private MethodTableFirstIDTracker(int id) {
        startingID = id;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        assert nextStartingId > 0 : nextStartingId;
        writer.writeInt("startingID", nextStartingId);
        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        return new MethodTableFirstIDTracker(loader.readInt("startingID"));
    }
}

class CollectingObjectReferenceVisitor implements ObjectReferenceVisitor {
    protected final SubstrateReferenceMap result = new SubstrateReferenceMap();

    @Override
    public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
        return visitObjectReferenceInline(objRef, 0, compressed, holderObject);
    }

    @Override
    public boolean visitObjectReferenceInline(Pointer objRef, int innerOffset, boolean compressed, Object holderObject) {
        int derivedOffset = NumUtil.safeToInt(objRef.rawValue());
        result.markReferenceAtOffset(derivedOffset, derivedOffset - innerOffset, compressed);
        return true;
    }
}
