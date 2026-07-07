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

import static com.oracle.svm.core.deopt.Deoptimizer.Options.LazyDeoptimization;
import static com.oracle.svm.shared.util.VMError.shouldNotReachHereUnexpectedInput;

import java.util.BitSet;
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
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.code.FrameInfoDecoder.ConstantAccess;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueInfo;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueType;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.deopt.DeoptEntryInfopoint;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
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
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.espresso.classfile.Constants;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

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
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CodeInfoEncoder {

    public static class Options {
        @Option(help = "Statistics about code and deoptimization information") //
        public static final HostedOptionKey<Boolean> CodeInfoEncoderCounters = new HostedOptionKey<>(false);
    }

    @SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class)
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

    public record Encodings(
                    JavaConstant[] objectConstantsArray,
                    Class<?>[] classesArray,
                    String[] memberNamesArray,
                    String[] otherStringsArray) {
    }

    /**
     * Encapsulates {@link FrequencyEncoder}s for values that are referenced by an index, just like
     * Java bytecode instructions reference entries in a constant pool via their index.
     */
    public static final class Encoders {
        static final Class<?> INVALID_CLASS = null;
        static final String INVALID_METHOD_NAME = "";
        // This can never be valid since it's not valid to be both PUBLIC and PRIVATE
        static final int INVALID_METHOD_MODIFIERS = Constants.JVM_RECOGNIZED_METHOD_MODIFIERS;
        static final String INVALID_METHOD_SIGNATURE = null;

        public record Member(ResolvedJavaMethod method, Class<?> clazz, String name, String signature, int flags) {
        }

        public final FrequencyEncoder<JavaConstant> objectConstants;
        public final FrequencyEncoder<Class<?>> classes;
        /**
         * Dedicated encoder for method and field name strings because they have different
         * characteristics than most {@linkplain #otherStrings other strings} and can be separated
         * from them without much duplication, which results in lower indexes for both kinds of
         * strings that can in turn be {@linkplain TypeWriter#putUV encoded in fewer bytes}, also in
         * the {@linkplain #encodeMethodTable() method table}.
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

        public Encoders(boolean imageCode, Consumer<Class<?>> classVerifier, boolean forceEncodeAllMethodMetadata) {
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
                if (forceEncodeAllMethodMetadata || shouldEncodeMethodSignatureAndModifiers()) {
                    this.otherStrings.addObject(INVALID_METHOD_SIGNATURE);
                }
            }
        }

        public void addMethod(ResolvedJavaMethod method, Class<?> clazz, String name, String signature, int flags) {
            VMError.guarantee(SubstrateUtil.HOSTED, "Runtime code info must reference image methods by id");

            Member member = new Member(Objects.requireNonNull(method), clazz, name, signature, flags);
            if (methods.addObject(member)) {
                classes.addObject(clazz);
                memberNames.addObject(name);
                if (shouldEncodeMethodSignatureAndModifiers()) {
                    otherStrings.addObject(signature);
                }
            }
        }

        public int findMethodIndex(ResolvedJavaMethod method, Class<?> clazz, String name, String signature, int flags, boolean optional) {
            VMError.guarantee(SubstrateUtil.HOSTED, "Runtime code info must obtain method ids from image code info");

            Member member = new Member(Objects.requireNonNull(method), clazz, name, signature, flags);
            return optional ? methods.findIndex(member) : methods.getIndex(member);
        }

        public ResolvedJavaMethod[] getEncodedMethods() {
            assert encodedMethods != null : "can call only once encoded (and only for image code)";
            return Stream.of(encodedMethods).map(m -> (m != null) ? m.method() : null).toArray(ResolvedJavaMethod[]::new);
        }

        public Encodings encodeAll() {
            return new Encodings(
                            encodeArray(objectConstants, JavaConstant[]::new),
                            encodeArray(classes, Class[]::new),
                            encodeArray(memberNames, String[]::new),
                            encodeArray(otherStrings, String[]::new));
        }

        private void encodeAllAndInstall(CodeInfo target, ReferenceAdjuster adjuster) {
            Encodings encodings = encodeAll();

            /* Runtime code info references image methods by id and does not encode methods. */
            int methodTableEntryCount = methods == null ? 0 : methods.getLength();
            int methodTableFirstId;
            if (ImageLayerBuildingSupport.buildingImageLayer()) {
                var idTracker = MethodTableFirstIDTracker.singleton();
                methodTableFirstId = idTracker.startingID;
                idTracker.nextStartingId = methodTableFirstId + methodTableEntryCount;
            } else {
                methodTableFirstId = 0;
            }
            NonmovableArray<Byte> methodTable = encodeMethodTable();

            install(target, encodings, methodTable, methodTableFirstId, adjuster, methodTableEntryCount);
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
                encodeMethod(writer, encodedMethods[id].clazz, encodedMethods[id].name, encodedMethods[id].signature, encodedMethods[id].flags, shortClassIndexes, shortNameIndexes,
                                shortSignatureIndexes);
            }
            if (!shouldEncodeMethodSignatureAndModifiers()) {
                encodeMethodFlags(writer);
            }
            NonmovableArray<Byte> bytes = NonmovableArrays.createByteArray(NumUtil.safeToInt(writer.getBytesWritten()), NmtCategory.Code);
            writer.toByteBuffer(NonmovableArrays.asByteBuffer(bytes));
            return bytes;
        }

        private void encodeMethodFlags(UnsafeArrayTypeWriter writer) {
            /*
             * When full modifiers + flags are not encoded in method entries, store a compact bit
             * array of extra flags. FrameSourceInfo.checkConstants ensures that each byte holds a
             * whole number of flag slots.
             */
            int currentByte = 0;
            int bitIndex = FrameSourceInfo.MethodFlags.EXTRA_FLAGS_BITS; // skip method id 0
            for (int id = 1; id < encodedMethods.length; id++) {
                int flags = encodedMethods[id].flags;
                currentByte |= ((flags & FrameSourceInfo.MethodFlags.EXTRA_FLAGS_MASK) >> FrameSourceInfo.MethodFlags.EXTRA_FLAGS_POS) << bitIndex;
                bitIndex += FrameSourceInfo.MethodFlags.EXTRA_FLAGS_BITS;
                assert bitIndex <= Byte.SIZE;
                if (bitIndex == Byte.SIZE) {
                    writer.putU1(currentByte);
                    bitIndex = 0;
                    currentByte = 0;
                }
            }
            if (bitIndex > 0) {
                writer.putU1(currentByte);
            }
        }

        private void encodeMethod(UnsafeArrayTypeWriter writer, Class<?> clazz, String name, String signature, int flags, boolean shortClassIndexes, boolean shortNameIndexes,
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
            if (shouldEncodeMethodSignatureAndModifiers()) {
                int signatureNamesIndex = otherStrings.getIndex(signature);
                if (shortSignatureIndexes) {
                    writer.putU2(signatureNamesIndex);
                } else {
                    writer.putU4(signatureNamesIndex);
                }
                assert TypeConversion.isU2(flags) || flags == INVALID_METHOD_MODIFIERS;
                writer.putU2(flags & 0xffff);
            }
        }

        @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed in target.")
        private static void install(CodeInfo target, Encodings encodings, NonmovableArray<Byte> methodTable, int methodTableFirstId, ReferenceAdjuster adjuster, int methodTableEntryCount) {

            NonmovableObjectArray<Object> objectConstants = adjuster.copyOfObjectConstantArray(encodings.objectConstantsArray, NmtCategory.Code);
            NonmovableObjectArray<Class<?>> classes = (encodings.classesArray != null) ? adjuster.copyOfObjectArray(encodings.classesArray, NmtCategory.Code) : NonmovableArrays.nullArray();
            NonmovableObjectArray<String> memberNames = (encodings.memberNamesArray != null) ? adjuster.copyOfObjectArray(encodings.memberNamesArray, NmtCategory.Code) : NonmovableArrays.nullArray();
            NonmovableObjectArray<String> otherStrings = (encodings.otherStringsArray != null) ? adjuster.copyOfObjectArray(encodings.otherStringsArray, NmtCategory.Code)
                            : NonmovableArrays.nullArray();

            CodeInfoAccess.setEncodings(target, objectConstants, classes, memberNames, otherStrings, methodTable, methodTableFirstId, methodTableEntryCount);
        }
    }

    static class IPData {
        protected long ip;
        protected int frameSizeEncoding;
        protected int exceptionOffset;
        protected ReferenceMapEncoder.Input referenceMap;
        protected long referenceMapIndex;
        protected FrameInfoEncoder.FrameData frameData;
        protected FrameInfoEncoder.FrameData defaultFrameData;
        protected IPData next;
        protected boolean deoptReturnValueIsObject;
    }

    private final TreeMap<Long, IPData> entries;
    private final Encoders encoders;
    private final FrameInfoEncoder frameInfoEncoder;

    private NonmovableArray<Byte> codeInfoIndex;
    private NonmovableArray<Byte> codeInfoEncodings;
    private NonmovableArray<Byte> codeInfoDefaultFrameInfoIndexes;
    private NonmovableArray<Byte> referenceMapEncoding;
    private int codeInfoIndexEntriesPerBlock = 1;
    private final boolean useFinalImageCodeInfoEncoding;

    public CodeInfoEncoder(FrameInfoEncoder.Customization frameInfoCustomization, Encoders encoders) {
        this(frameInfoCustomization, encoders, FrameInfoDecoder.SubstrateConstantAccess);
    }

    public CodeInfoEncoder(FrameInfoEncoder.Customization frameInfoCustomization, Encoders encoders, ConstantAccess constantAccess) {
        this.entries = new TreeMap<>();
        this.encoders = encoders;
        this.frameInfoEncoder = new FrameInfoEncoder(frameInfoCustomization, encoders, constantAccess);
        this.useFinalImageCodeInfoEncoding = useFinalImageCodeInfoEncoding();
    }

    public FrameInfoEncoder getFrameInfoEncoder() {
        return frameInfoEncoder;
    }

    public Encoders getEncoders() {
        return encoders;
    }

    @Fold
    public static boolean shouldEncodeMethodSignatureAndModifiers() {
        /*
         * JFR stack traces need the method signature and modifiers. By default, we don't include
         * this extra metadata as it increases the binary size.
         */
        return HasJfrSupport.get();
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
        startEntry.defaultFrameData = defaultFrameData;
        startEntry.frameData = defaultFrameData;
        startEntry.frameSizeEncoding = encodeFrameSize(totalFrameSize, true, isEntryPoint, hasCalleeSavedRegisters);

        /* Register the frame size for all entries that are starting points for the index. */
        long entryIP = CodeInfoDecoder.lookupEntryIP(CodeInfoDecoder.indexGranularity() + compilationOffset);
        while (entryIP <= CodeInfoDecoder.lookupEntryIP(compilationSize + compilationOffset - 1)) {
            IPData entry = makeEntry(entryIP);
            entry.defaultFrameData = defaultFrameData;
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
                    VMError.guarantee(offset < compilationSize, "Code info entry offset is outside the method code range");
                    boolean added = infopointOffsets.add(offset);
                    if (!added) {
                        throw VMError.shouldNotReachHere("Encoding two infopoints at same offset. Conflicting infopoint: " + infopoint);
                    }
                    IPData entry = makeEntry(offset + compilationOffset);
                    assert entry.referenceMap == null && (entry.frameData == null || entry.frameData.isDefaultFrameData) : entry;
                    entry.referenceMap = (ReferenceMapEncoder.Input) debugInfo.getReferenceMap();
                    entry.frameData = frameInfoEncoder.addDebugInfo(method, compilation, infopoint, totalFrameSize);
                    if (DeoptimizationSupport.enabled() && LazyDeoptimization.getValue() && entry.frameData.frame.isDeoptEntry && infopoint instanceof Call call && call.target != null) {
                        ResolvedJavaMethod invokeTarget = (ResolvedJavaMethod) call.target;
                        JavaType returnType = invokeTarget.getSignature().getReturnType(null);
                        entry.deoptReturnValueIsObject = ((SharedType) returnType).getStorageKind().isObject();
                    }
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
            VMError.guarantee(handler.handlerPos != handler.pcOffset, "Exception handler must have a unique PC");
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
        CodeInfoAccess.setCodeInfo(target, codeInfoIndex, codeInfoEncodings, codeInfoIndexEntriesPerBlock, codeInfoDefaultFrameInfoIndexes, referenceMapEncoding);
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

        VMError.guarantee(prev != null, "Code info encoding requires at least one IP entry");
        int indexEntryCount = NumUtil.safeToInt(Long.divideUnsigned(prev.ip, CodeInfoDecoder.indexGranularity()) + 1);
        long[] indexOffsets = new long[indexEntryCount];
        int[] defaultFrameInfoIndexes = useFinalImageCodeInfoEncoding ? new int[indexEntryCount] : null;
        int nextIndexEntry = 0;
        long nextIndexIP = 0;
        int currentDefaultFrameInfoIndex = -1;
        int chunkDefaultFrameInfoIndex = -1;
        UnsafeArrayTypeWriter encodingBuffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        for (IPData data = first; data != null; data = data.next) {
            assert data.ip <= nextIndexIP : data;
            if (useFinalImageCodeInfoEncoding && data.defaultFrameData != null) {
                currentDefaultFrameInfoIndex = TypeConversion.asS4(data.defaultFrameData.encodedFrameInfoIndex);
            }
            if (data.ip == nextIndexIP) {
                indexOffsets[nextIndexEntry++] = encodingBuffer.getBytesWritten();
                if (useFinalImageCodeInfoEncoding) {
                    VMError.guarantee(currentDefaultFrameInfoIndex >= 0, "Image code index entry is missing default frame info");
                    defaultFrameInfoIndexes[nextIndexEntry - 1] = currentDefaultFrameInfoIndex;
                    chunkDefaultFrameInfoIndex = currentDefaultFrameInfoIndex;
                }
                nextIndexIP += CodeInfoDecoder.indexGranularity();
            }

            int entryFlags = 0;
            entryFlags = entryFlags | flagsForSizeEncoding(data) << CodeInfoDecoder.FS_SHIFT;
            entryFlags = entryFlags | flagsForExceptionOffset(data) << CodeInfoDecoder.EX_SHIFT;
            entryFlags = entryFlags | flagsForReferenceMapIndex(data) << CodeInfoDecoder.RM_SHIFT;
            entryFlags = entryFlags | flagsForDeoptFrameInfo(data) << CodeInfoDecoder.FI_SHIFT;
            if (useFinalImageCodeInfoEncoding) {
                entryFlags = encodeExtendedImageEntryFlags(data, entryFlags, currentDefaultFrameInfoIndex, chunkDefaultFrameInfoIndex);
            }

            encodingBuffer.putU1(encodeFirstByte(entryFlags));
            encodingBuffer.putU1(data.next == null ? CodeInfoDecoder.DELTA_END_OF_TABLE : (data.next.ip - data.ip));
            if (CodeInfoDecoder.isExtendedEntry(entryFlags)) {
                writeExtendedEntryBasicFlags(encodingBuffer, entryFlags);
            }

            writeSizeEncoding(encodingBuffer, data, entryFlags);
            writeExceptionOffset(encodingBuffer, data, entryFlags);
            writeReferenceMapIndex(encodingBuffer, data, entryFlags);
            writeEncodedFrameInfo(encodingBuffer, data, entryFlags, chunkDefaultFrameInfoIndex);

            if (DeoptimizationSupport.enabled() && LazyDeoptimization.getValue() && data.frameData != null && data.frameData.frame.isDeoptEntry) {
                /*
                 * With lazy deoptimization, we have an extra byte in the code info, which keeps
                 * track for each deopt entry point whether it is at a call that returns an object.
                 */
                encodingBuffer.putU1(data.deoptReturnValueIsObject ? 1 : 0);
            }
        }

        VMError.guarantee(nextIndexEntry == indexOffsets.length, "Mismatched code info index entry count");
        codeInfoIndexEntriesPerBlock = selectCodeInfoIndexEntriesPerBlock(indexOffsets, useFinalImageCodeInfoEncoding);
        byte[] encodedIndex = encodeCodeInfoIndex(indexOffsets, codeInfoIndexEntriesPerBlock);
        codeInfoIndex = NonmovableArrays.createByteArray(encodedIndex.length, NmtCategory.Code);
        NonmovableArrays.asByteBuffer(codeInfoIndex).put(encodedIndex);
        if (useFinalImageCodeInfoEncoding) {
            byte[] encodedDefaultFrameInfoIndexes = encodeImageCodeInfoDefaultFrameInfoIndexes(defaultFrameInfoIndexes);
            codeInfoDefaultFrameInfoIndexes = NonmovableArrays.createByteArray(encodedDefaultFrameInfoIndexes.length, NmtCategory.Code);
            NonmovableArrays.asByteBuffer(codeInfoDefaultFrameInfoIndexes).put(encodedDefaultFrameInfoIndexes);
        } else {
            codeInfoDefaultFrameInfoIndexes = NonmovableArrays.nullArray();
        }
        codeInfoEncodings = NonmovableArrays.createByteArray(TypeConversion.asU4(encodingBuffer.getBytesWritten()), NmtCategory.Code);
        encodingBuffer.toByteBuffer(NonmovableArrays.asByteBuffer(codeInfoEncodings));
    }

    private static boolean useFinalImageCodeInfoEncoding() {
        /*
         * The side table is part of the final runtime ImageCodeInfo layout. Non-final layered
         * builds persist metadata for later image generation, so keep their intermediate encoding
         * in the legacy self-contained format.
         */
        return SubstrateUtil.HOSTED && ImageLayerBuildingSupport.lastImageBuild();
    }

    private static int selectCodeInfoIndexEntriesPerBlock(long[] indexOffsets, boolean useFinalImageCodeInfoEncoding) {
        if (useFinalImageCodeInfoEncoding && codeInfoIndexFits(indexOffsets, CodeInfoDecoder.CODE_INFO_INDEX_COMPRESSED_ENTRIES_PER_BLOCK)) {
            return CodeInfoDecoder.CODE_INFO_INDEX_COMPRESSED_ENTRIES_PER_BLOCK;
        }
        return 1;
    }

    private static byte[] encodeCodeInfoIndex(long[] offsets, int entriesPerBlock) {
        UnsafeArrayTypeWriter buffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        if (entriesPerBlock == 1) {
            for (long offset : offsets) {
                buffer.putU4(offset);
            }
        } else {
            VMError.guarantee(entriesPerBlock == CodeInfoDecoder.CODE_INFO_INDEX_COMPRESSED_ENTRIES_PER_BLOCK, "Unexpected code info index block size");
            for (int blockStart = 0; blockStart < offsets.length; blockStart += entriesPerBlock) {
                long base = offsets[blockStart];
                buffer.putU4(base);
                int blockEnd = Math.min(blockStart + entriesPerBlock, offsets.length);
                for (int i = blockStart + 1; i < blockEnd; i++) {
                    buffer.putU2(offsets[i] - base);
                }
            }
        }
        return buffer.toArray();
    }

    private static boolean codeInfoIndexFits(long[] offsets, int entriesPerBlock) {
        if (entriesPerBlock == 1) {
            for (long offset : offsets) {
                if (!TypeConversion.isU4(offset)) {
                    return false;
                }
            }
            return true;
        }

        VMError.guarantee(entriesPerBlock == CodeInfoDecoder.CODE_INFO_INDEX_COMPRESSED_ENTRIES_PER_BLOCK, "Unexpected code info index block size");
        for (int blockStart = 0; blockStart < offsets.length; blockStart += entriesPerBlock) {
            long base = offsets[blockStart];
            if (!TypeConversion.isU4(base)) {
                return false;
            }
            int blockEnd = Math.min(blockStart + entriesPerBlock, offsets.length);
            for (int i = blockStart + 1; i < blockEnd; i++) {
                long residual = offsets[i] - base;
                if (!TypeConversion.isU2(residual)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static byte[] encodeImageCodeInfoDefaultFrameInfoIndexes(int[] values) {
        UnsafeArrayTypeWriter buffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        for (int value : values) {
            buffer.putS4(value);
        }
        return buffer.toArray();
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
            return CodeInfoDecoder.FI_NO_INFO;
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

    private static int encodeFirstByte(int entryFlags) {
        if (!CodeInfoDecoder.isExtendedEntry(entryFlags)) {
            return CodeInfoDecoder.basicEntryFlags(entryFlags);
        }
        return switch (CodeInfoDecoder.extendedEntryMode(entryFlags)) {
            case CodeInfoDecoder.EXTENDED_ENTRY_MODE_LEGACY, CodeInfoDecoder.EXTENDED_ENTRY_MODE_FI_DEFAULT -> CodeInfoDecoder.FIRST_BYTE_MARKER_FOR_EXTENDED_ENTRY_LEGACY;
            case CodeInfoDecoder.EXTENDED_ENTRY_MODE_FI_INFO_ONLY_S1 -> CodeInfoDecoder.FIRST_BYTE_MARKER_FOR_EXTENDED_ENTRY_FI_INFO_ONLY_S1;
            case CodeInfoDecoder.EXTENDED_ENTRY_MODE_FI_INFO_ONLY_S2 -> CodeInfoDecoder.FIRST_BYTE_MARKER_FOR_EXTENDED_ENTRY_FI_INFO_ONLY_S2;
            default -> throw shouldNotReachHereUnexpectedInput(entryFlags);
        };
    }

    private void writeExtendedEntryBasicFlags(UnsafeArrayTypeWriter writeBuffer, int entryFlags) {
        assert useFinalImageCodeInfoEncoding;
        assert CodeInfoDecoder.isExtendedEntry(entryFlags);
        int basicFlags = CodeInfoDecoder.basicEntryFlags(entryFlags);
        switch (CodeInfoDecoder.extendedEntryMode(entryFlags)) {
            case CodeInfoDecoder.EXTENDED_ENTRY_MODE_LEGACY:
                writeBuffer.putU1(basicFlags);
                break;
            case CodeInfoDecoder.EXTENDED_ENTRY_MODE_FI_DEFAULT:
            case CodeInfoDecoder.EXTENDED_ENTRY_MODE_FI_INFO_ONLY_S1:
            case CodeInfoDecoder.EXTENDED_ENTRY_MODE_FI_INFO_ONLY_S2:
                writeBuffer.putU1(basicFlags & ~CodeInfoDecoder.FI_MASK_IN_PLACE);
                break;
            default:
                throw shouldNotReachHereUnexpectedInput(entryFlags);
        }
    }

    private int encodeExtendedImageEntryFlags(IPData data, int entryFlags, int currentDefaultFrameInfoIndex, int chunkDefaultFrameInfoIndex) {
        assert useFinalImageCodeInfoEncoding;
        boolean useChunkDefaultFrameInfo = currentDefaultFrameInfoIndex >= 0 && currentDefaultFrameInfoIndex == chunkDefaultFrameInfoIndex;
        /*
         * For image code, FI_DEFAULT can reuse the chunk side table entry and FI_INFO_ONLY can be
         * encoded as a small delta to that same default when the frame infos stay close.
         */
        if (CodeInfoDecoder.extractFI(entryFlags) == CodeInfoDecoder.FI_DEFAULT_INFO_INDEX_S4 && useChunkDefaultFrameInfo) {
            return entryFlags | CodeInfoDecoder.EXTENDED_ENTRY_FLAG | CodeInfoDecoder.EXTENDED_ENTRY_MODE_FI_DEFAULT;
        }
        if (CodeInfoDecoder.extractFI(entryFlags) == CodeInfoDecoder.FI_INFO_ONLY_INDEX_S4 && useChunkDefaultFrameInfo) {
            long delta = data.frameData.encodedFrameInfoIndex - chunkDefaultFrameInfoIndex;
            if (TypeConversion.isS1(delta)) {
                return entryFlags | CodeInfoDecoder.EXTENDED_ENTRY_FLAG | CodeInfoDecoder.EXTENDED_ENTRY_MODE_FI_INFO_ONLY_S1;
            } else if (TypeConversion.isS2(delta)) {
                return entryFlags | CodeInfoDecoder.EXTENDED_ENTRY_FLAG | CodeInfoDecoder.EXTENDED_ENTRY_MODE_FI_INFO_ONLY_S2;
            }
        }
        if (CodeInfoDecoder.isExtendedEntryMarker(CodeInfoDecoder.basicEntryFlags(entryFlags))) {
            return entryFlags | CodeInfoDecoder.EXTENDED_ENTRY_FLAG | CodeInfoDecoder.EXTENDED_ENTRY_MODE_LEGACY;
        }
        return entryFlags;
    }

    private static int frameInfoDelta(IPData data, int chunkDefaultFrameInfoIndex) {
        VMError.guarantee(chunkDefaultFrameInfoIndex >= 0, "Image code entry is missing chunk default frame info");
        return NumUtil.safeToInt(data.frameData.encodedFrameInfoIndex - chunkDefaultFrameInfoIndex);
    }

    private void writeEncodedFrameInfo(UnsafeArrayTypeWriter writeBuffer, IPData data, int entryFlags, int chunkDefaultFrameInfoIndex) {
        switch (CodeInfoDecoder.extractFI(entryFlags)) {
            case CodeInfoDecoder.FI_DEFAULT_INFO_INDEX_S4:
                if (CodeInfoDecoder.isExtendedFIDefault(entryFlags)) {
                    assert useFinalImageCodeInfoEncoding;
                    break;
                }
                writeBuffer.putS4(data.frameData.encodedFrameInfoIndex);
                break;
            case CodeInfoDecoder.FI_DEOPT_ENTRY_INDEX_S4:
                writeBuffer.putS4(data.frameData.encodedFrameInfoIndex);
                break;
            case CodeInfoDecoder.FI_INFO_ONLY_INDEX_S4:
                if (CodeInfoDecoder.isExtendedFIInfoOnlyS1(entryFlags)) {
                    writeBuffer.putS1(frameInfoDelta(data, chunkDefaultFrameInfoIndex));
                } else if (CodeInfoDecoder.isExtendedFIInfoOnlyS2(entryFlags)) {
                    writeBuffer.putS2(frameInfoDelta(data, chunkDefaultFrameInfoIndex));
                } else {
                    writeBuffer.putS4(data.frameData.encodedFrameInfoIndex);
                }
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
        boolean verifyFrameInfoCursor = CodeInfoAccess.usesFinalImageCodeInfoEncoding(info);
        int expectedRootMethodId = 0;
        CodeInfoDecoder.FrameInfoCursor frameInfoCursor = null;
        if (verifyFrameInfoCursor) {
            CodeInfoAccess.lookupCodeInfo(info, compilationOffset, queryResult, constantAccess);
            expectedRootMethodId = queryResult.getFrameInfo().getSourceMethodId();
            frameInfoCursor = new CodeInfoDecoder.FrameInfoCursor();
        }
        for (int relativeIP = 0; relativeIP < compilationSize; relativeIP++) {
            int totalIP = relativeIP + compilationOffset;
            CodeInfoAccess.lookupCodeInfo(info, totalIP, queryResult, constantAccess);
            assert queryResult.isEntryPoint() == method.isEntryPoint() : queryResult;
            assert queryResult.hasCalleeSavedRegisters() == method.hasCalleeSavedRegisters() : queryResult;
            assert queryResult.getTotalFrameSize() == compilation.getTotalFrameSize() : queryResult;

            assert CodeInfoAccess.lookupStackReferenceMapIndex(info, totalIP) == queryResult.getReferenceMapIndex() : queryResult;
            if (verifyFrameInfoCursor && expectedRootMethodId != 0) {
                verifyFrameInfoCursorRootMethod(info, totalIP, expectedRootMethodId, frameInfoCursor);
            }
        }

        for (Infopoint infopoint : compilation.getInfopoints()) {
            if (infopoint.debugInfo != null) {
                int offset = CodeInfoEncoder.getEntryOffset(infopoint);
                if (offset >= 0) {
                    assert offset < compilationSize : infopoint;
                    CodeInfoAccess.lookupCodeInfo(info, offset + compilationOffset, queryResult, constantAccess);

                    /* Use a non-zero base to avoid negative addresses. */
                    Pointer base = Word.pointer(1024L * 1024L * 1024L);
                    CollectingObjectReferenceVisitor visitor = new CollectingObjectReferenceVisitor(base);
                    CodeReferenceMapDecoder.walkOffsetsFromPointer(base, CodeInfoAccess.getStackReferenceMapEncoding(info), queryResult.getReferenceMapIndex(), visitor, null);
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

    private static void verifyFrameInfoCursorRootMethod(CodeInfo info, int totalIP, int expectedRootMethodId, CodeInfoDecoder.FrameInfoCursor frameInfoCursor) {
        frameInfoCursor.initialize(info, totalIP, false);
        FrameInfoQueryResult rootFrame = null;
        while (frameInfoCursor.advance()) {
            rootFrame = frameInfoCursor.get();
        }
        assert rootFrame != null : "Missing frame info for IP " + totalIP;
        assert rootFrame.getSourceMethodId() == expectedRootMethodId : rootFrame;
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

        ObjectLayout objectLayout = ObjectLayout.singleton();
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
                UnsignedWord expectedOffset = Word.unsigned(objectLayout.getArrayElementOffset(kind, expectedLength));
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

                UnsignedWord expectedOffset = Word.unsigned(expectedField.getLocation());
                ValueInfo actualValue = findActualField(actualObject, expectedOffset);
                verifyValue(compilation, expectedValue, actualValue, actualFrame, visitedVirtualObjects);
            }
        }
    }

    private ValueInfo findActualArrayElement(ValueInfo[] actualObject, UnsignedWord expectedOffset) {
        DynamicHub hub = (DynamicHub) constantAccess.asObject(actualObject[0].getValue());
        ObjectLayout objectLayout = ObjectLayout.singleton();
        assert LayoutEncoding.isArray(hub.getLayoutEncoding()) : hub;
        return findActualValue(actualObject, expectedOffset, objectLayout, LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding()), 2);
    }

    private ValueInfo findActualField(ValueInfo[] actualObject, UnsignedWord expectedOffset) {
        DynamicHub hub = (DynamicHub) constantAccess.asObject(actualObject[0].getValue());
        ObjectLayout objectLayout = ObjectLayout.singleton();
        assert LayoutEncoding.isPureInstance(hub.getLayoutEncoding()) : hub;
        return findActualValue(actualObject, expectedOffset, objectLayout, Word.unsigned(objectLayout.getFirstFieldOffset()), 1);
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
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = MethodTableFirstIDTracker.LayeredCallbacks.class)
class MethodTableFirstIDTracker {
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

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
            return new LayeredCallbacksSingletonTrait(new SingletonLayeredCallbacks<MethodTableFirstIDTracker>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, MethodTableFirstIDTracker singleton) {
                    int nextStartingId = singleton.nextStartingId;
                    assert nextStartingId > 0 : nextStartingId;
                    writer.writeInt("startingID", nextStartingId);
                    return LayeredPersistFlags.CREATE;
                }

                @Override
                public Class<? extends SingletonLayeredCallbacks.LayeredSingletonInstantiator<?>> getSingletonInstantiator() {
                    return SingletonInstantiator.class;
                }
            });
        }
    }

    static class SingletonInstantiator implements SingletonLayeredCallbacks.LayeredSingletonInstantiator<MethodTableFirstIDTracker> {
        @Override
        public MethodTableFirstIDTracker createFromLoader(ImageSingletonLoader loader) {
            return new MethodTableFirstIDTracker(loader.readInt("startingID"));
        }
    }
}

class CollectingObjectReferenceVisitor implements ObjectReferenceVisitor {
    private final Pointer base;
    protected final SubstrateReferenceMap result = new SubstrateReferenceMap();

    CollectingObjectReferenceVisitor(Pointer base) {
        this.base = base;
    }

    @Override
    public void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count) {
        Pointer pos = firstObjRef;
        Pointer end = firstObjRef.add(Word.unsigned(count).multiply(referenceSize));
        while (pos.belowThan(end)) {
            visitObjectReference(pos, compressed);
            pos = pos.add(referenceSize);
        }
    }

    private void visitObjectReference(Pointer objRef, boolean compressed) {
        int offset = NumUtil.safeToInt(objRef.subtract(base).rawValue());
        result.markReferenceAtOffset(offset, compressed);
    }

    @Override
    public void visitDerivedReference(Pointer baseObjRef, Pointer derivedObjRef, boolean compressed, Object holderObject) {
        int baseOffset = NumUtil.safeToInt(baseObjRef.subtract(base).rawValue());
        int derivedOffset = NumUtil.safeToInt(derivedObjRef.subtract(base).rawValue());
        result.markReferenceAtOffset(derivedOffset, baseOffset, compressed);
    }
}
