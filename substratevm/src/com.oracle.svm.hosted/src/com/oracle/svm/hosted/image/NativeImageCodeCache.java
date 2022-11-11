/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.DataSection;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.infrastructure.WrappedElement;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoDecoder;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.ImageCodeInfo.HostedImageCodeInfo;
import com.oracle.svm.core.code.InstantReferenceAdjuster;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptEntryInfopoint;
import com.oracle.svm.core.graal.code.SubstrateDataBuilder;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.reflect.target.EncodedReflectionMetadataSupplier;
import com.oracle.svm.core.sampler.ProfilingSampler;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.code.HostedImageHeapConstantPatch;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives.DeoptSourceFrameInfo;
import com.oracle.svm.hosted.image.NativeImage.NativeTextSectionImpl;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.reflect.ReflectionHostedSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.VMConstant;

public abstract class NativeImageCodeCache {

    public static class Options {
        @Option(help = "Verify that all possible deoptimization entry points have been properly compiled and registered in the metadata")//
        public static final HostedOptionKey<Boolean> VerifyDeoptimizationEntryPoints = new HostedOptionKey<>(false);
    }

    protected final NativeImageHeap imageHeap;

    private final Map<HostedMethod, CompilationResult> compilations;

    private final List<Pair<HostedMethod, CompilationResult>> orderedCompilations;

    protected final Platform targetPlatform;

    private final DataSection dataSection;

    private final Map<Constant, String> constantReasons = new HashMap<>();

    public NativeImageCodeCache(Map<HostedMethod, CompilationResult> compilationResultMap, NativeImageHeap imageHeap) {
        this(compilationResultMap, imageHeap, ImageSingletons.lookup(Platform.class));
    }

    public void purge() {
        compilations.clear();
        orderedCompilations.clear();
    }

    public NativeImageCodeCache(Map<HostedMethod, CompilationResult> compilations, NativeImageHeap imageHeap, Platform targetPlatform) {
        this.compilations = compilations;
        this.imageHeap = imageHeap;
        this.dataSection = new DataSection();
        this.targetPlatform = targetPlatform;
        this.orderedCompilations = computeCompilationOrder(compilations);
    }

    public abstract int getCodeCacheSize();

    public abstract int getCodeAreaSize();

    public Pair<HostedMethod, CompilationResult> getFirstCompilation() {
        return orderedCompilations.get(0);
    }

    public Pair<HostedMethod, CompilationResult> getLastCompilation() {
        return orderedCompilations.get(orderedCompilations.size() - 1);
    }

    protected List<Pair<HostedMethod, CompilationResult>> computeCompilationOrder(Map<HostedMethod, CompilationResult> compilationMap) {
        return compilationMap.entrySet().stream().map(e -> Pair.create(e.getKey(), e.getValue())).collect(Collectors.toList());
    }

    public List<Pair<HostedMethod, CompilationResult>> getOrderedCompilations() {
        return orderedCompilations;
    }

    public abstract int codeSizeFor(HostedMethod method);

    protected CompilationResult compilationResultFor(HostedMethod method) {
        return compilations.get(method);
    }

    public abstract void layoutMethods(DebugContext debug, BigBang bb, ForkJoinPool threadPool);

    public void layoutConstants() {
        for (Pair<HostedMethod, CompilationResult> pair : getOrderedCompilations()) {
            CompilationResult compilation = pair.getRight();
            for (DataSection.Data data : compilation.getDataSection()) {
                if (data instanceof SubstrateDataBuilder.ObjectData) {
                    JavaConstant constant = ((SubstrateDataBuilder.ObjectData) data).getConstant();
                    constantReasons.put(constant, compilation.getName());
                }
            }

            dataSection.addAll(compilation.getDataSection());

            for (DataPatch patch : compilation.getDataPatches()) {
                if (patch.reference instanceof ConstantReference) {
                    VMConstant constant = ((ConstantReference) patch.reference).getConstant();
                    constantReasons.put(constant, compilation.getName());
                }
            }
        }
        dataSection.close(HostedOptionValues.singleton(), 1);
    }

    public void addConstantsToHeap() {
        for (DataSection.Data data : dataSection) {
            if (data instanceof SubstrateDataBuilder.ObjectData) {
                JavaConstant constant = ((SubstrateDataBuilder.ObjectData) data).getConstant();
                addConstantToHeap(constant, "data section");
            }
        }
        for (Pair<HostedMethod, CompilationResult> pair : getOrderedCompilations()) {
            CompilationResult compilationResult = pair.getRight();
            for (DataPatch patch : compilationResult.getDataPatches()) {
                if (patch.reference instanceof ConstantReference) {
                    addConstantToHeap(((ConstantReference) patch.reference).getConstant(), compilationResult.getName());
                }
            }

            for (CompilationResult.CodeAnnotation codeAnnotation : compilationResult.getCodeAnnotations()) {
                if (codeAnnotation instanceof HostedImageHeapConstantPatch) {
                    addConstantToHeap(((HostedImageHeapConstantPatch) codeAnnotation).constant, compilationResult.getName());
                }
            }
        }
    }

    private void addConstantToHeap(Constant constant, Object reason) {
        if (constant instanceof SubstrateMethodPointerConstant) {
            /*
             * This constant represents a pointer to a method, and as such, should not be added to
             * the heap
             */
            return;
        }

        HostedType hostedType = imageHeap.getMetaAccess().lookupJavaType((JavaConstant) constant);
        if (!hostedType.isInstantiated()) {
            throw shouldNotReachHere("Non-instantiated type referenced by a compiled method: " + hostedType.getName() + "." +
                            (reason != null ? " Method: " + reason : ""));
        }
        imageHeap.addConstant((JavaConstant) constant, false, reason != null ? reason : constantReasons.get(constant));
    }

    protected int getConstantsSize() {
        return dataSection.getSectionSize();
    }

    public int getAlignedConstantsSize() {
        return ConfigurationValues.getObjectLayout().alignUp(getConstantsSize());
    }

    public void buildRuntimeMetadata(CFunctionPointer firstMethod, UnsignedWord codeSize) {
        // Build run-time metadata.
        HostedFrameInfoCustomization frameInfoCustomization = new HostedFrameInfoCustomization();
        CodeInfoEncoder.Encoders encoders = new CodeInfoEncoder.Encoders();
        CodeInfoEncoder codeInfoEncoder = new CodeInfoEncoder(frameInfoCustomization, encoders);
        for (Pair<HostedMethod, CompilationResult> pair : getOrderedCompilations()) {
            encodeMethod(codeInfoEncoder, pair);
        }

        ReflectionMetadataEncoder reflectionMetadataEncoder = ImageSingletons.lookup(ReflectionMetadataEncoderFactory.class).create(encoders);
        ReflectionHostedSupport reflectionSupport = ImageSingletons.lookup(ReflectionHostedSupport.class);
        HostedUniverse hUniverse = imageHeap.getUniverse();
        HostedMetaAccess hMetaAccess = imageHeap.getMetaAccess();

        Map<Class<?>, Set<Class<?>>> innerClasses = reflectionSupport.getReflectionInnerClasses();
        Set<?> heapDynamicHubs = reflectionSupport.getHeapDynamicHubs();
        for (HostedType type : hUniverse.getTypes()) {
            if (type.getWrapped().isReachable() && heapDynamicHubs.contains(type.getHub())) {
                Class<?>[] typeInnerClasses = innerClasses.getOrDefault(type.getJavaClass(), Collections.emptySet()).toArray(new Class<?>[0]);
                reflectionMetadataEncoder.addClassMetadata(hMetaAccess, type, typeInnerClasses);
            }
        }

        Set<HostedField> includedFields = new HashSet<>();
        Set<HostedMethod> includedMethods = new HashSet<>();
        Set<Field> configurationFields = reflectionSupport.getReflectionFields();
        Set<Executable> configurationExecutables = reflectionSupport.getReflectionExecutables();

        for (AccessibleObject object : reflectionSupport.getHeapReflectionObjects()) {
            if (object instanceof Field) {
                HostedField hostedField = hMetaAccess.lookupJavaField((Field) object);
                if (!includedFields.contains(hostedField)) {
                    reflectionMetadataEncoder.addHeapAccessibleObjectMetadata(hMetaAccess, hostedField, object, configurationFields.contains(object));
                    includedFields.add(hostedField);
                }
            } else if (object instanceof Executable) {
                HostedMethod hostedMethod = hMetaAccess.lookupJavaMethod((Executable) object);
                if (!includedMethods.contains(hostedMethod)) {
                    reflectionMetadataEncoder.addHeapAccessibleObjectMetadata(hMetaAccess, hostedMethod, object, configurationExecutables.contains(object));
                    includedMethods.add(hostedMethod);
                }
            }
        }

        for (Field reflectField : configurationFields) {
            HostedField field = hMetaAccess.lookupJavaField(reflectField);
            if (!includedFields.contains(field)) {
                reflectionMetadataEncoder.addReflectionFieldMetadata(hMetaAccess, field, reflectField);
                includedFields.add(field);
            }
        }

        for (Executable reflectMethod : configurationExecutables) {
            HostedMethod method = hMetaAccess.lookupJavaMethod(reflectMethod);
            if (!includedMethods.contains(method)) {
                Object accessor = reflectionSupport.getAccessor(reflectMethod);
                reflectionMetadataEncoder.addReflectionExecutableMetadata(hMetaAccess, method, reflectMethod, accessor);
                includedMethods.add(method);
            }
        }

        for (Object field : reflectionSupport.getHidingReflectionFields()) {
            AnalysisField hidingField = (AnalysisField) field;
            HostedField hostedField = hUniverse.optionalLookup(hidingField);
            if (hostedField == null || !includedFields.contains(hostedField)) {
                HostedType declaringType = hUniverse.lookup(hidingField.getDeclaringClass());
                String name = hidingField.getName();
                HostedType type = hUniverse.lookup(hidingField.getType());
                int modifiers = hidingField.getModifiers();
                reflectionMetadataEncoder.addHidingFieldMetadata(hidingField, declaringType, name, type, modifiers);
                if (hostedField != null) {
                    includedFields.add(hostedField);
                }
            }
        }

        for (Object method : reflectionSupport.getHidingReflectionMethods()) {
            AnalysisMethod hidingMethod = (AnalysisMethod) method;
            HostedMethod hostedMethod = hUniverse.optionalLookup(hidingMethod);
            if (hostedMethod == null || !includedMethods.contains(hostedMethod)) {
                HostedType declaringType = hUniverse.lookup(hidingMethod.getDeclaringClass());
                String name = hidingMethod.getName();
                JavaType[] analysisParameterTypes = hidingMethod.getSignature().toParameterTypes(null);
                HostedType[] parameterTypes = new HostedType[analysisParameterTypes.length];
                for (int i = 0; i < analysisParameterTypes.length; ++i) {
                    parameterTypes[i] = hUniverse.lookup(analysisParameterTypes[i]);
                }
                int modifiers = hidingMethod.getModifiers();
                HostedType returnType = hUniverse.lookup(hidingMethod.getSignature().getReturnType(null));
                reflectionMetadataEncoder.addHidingMethodMetadata(hidingMethod, declaringType, name, parameterTypes, modifiers, returnType);
                if (hostedMethod != null) {
                    includedMethods.add(hostedMethod);
                }
            }
        }

        if (SubstrateOptions.IncludeMethodData.getValue()) {
            for (HostedField field : hUniverse.getFields()) {
                if (field.isAccessed() && !includedFields.contains(field)) {
                    reflectionMetadataEncoder.addReachableFieldMetadata(field);
                }
            }

            for (HostedMethod method : hUniverse.getMethods()) {
                if (method.getWrapped().isReachable() && !method.getWrapped().isIntrinsicMethod() && !includedMethods.contains(method)) {
                    reflectionMetadataEncoder.addReachableExecutableMetadata(method);
                }
            }
        }

        if (NativeImageOptions.PrintMethodHistogram.getValue()) {
            System.out.println("encoded deopt entry points                 ; " + frameInfoCustomization.numDeoptEntryPoints);
            System.out.println("encoded during call entry points           ; " + frameInfoCustomization.numDuringCallEntryPoints);
        }

        HostedImageCodeInfo imageCodeInfo = CodeInfoTable.getImageCodeCache().getHostedImageCodeInfo();
        codeInfoEncoder.encodeAllAndInstall(imageCodeInfo, new InstantReferenceAdjuster());
        reflectionMetadataEncoder.encodeAllAndInstall();
        imageCodeInfo.setCodeStart(firstMethod);
        imageCodeInfo.setCodeSize(codeSize);
        imageCodeInfo.setDataOffset(codeSize);
        imageCodeInfo.setDataSize(WordFactory.zero()); // (only for data immediately after code)
        imageCodeInfo.setCodeAndDataMemorySize(codeSize);

        if (CodeInfoEncoder.Options.CodeInfoEncoderCounters.getValue()) {
            System.out.println("****Start Code Info Encoder Counters****");
            for (Counter counter : ImageSingletons.lookup(CodeInfoEncoder.Counters.class).group.getCounters()) {
                System.out.println(counter.getName() + " ; " + counter.getValue());
            }
            System.out.println("****End Code Info Encoder Counters****");
        }

        if (Options.VerifyDeoptimizationEntryPoints.getValue()) {
            /*
             * Missing deoptimization entry points lead to hard-to-debug transient failures, so we
             * want the verification on all the time and not just when assertions are on.
             */
            verifyDeoptEntries(imageCodeInfo);
        }

        assert verifyMethods(codeInfoEncoder, imageCodeInfo);
    }

    protected void encodeMethod(CodeInfoEncoder codeInfoEncoder, Pair<HostedMethod, CompilationResult> pair) {
        final HostedMethod method = pair.getLeft();
        final CompilationResult compilation = pair.getRight();
        codeInfoEncoder.addMethod(method, compilation, method.getCodeAddressOffset(), codeSizeFor(method));
    }

    private void verifyDeoptEntries(CodeInfo codeInfo) {
        boolean hasError = false;
        List<Entry<AnalysisMethod, Map<Long, DeoptSourceFrameInfo>>> deoptEntries = new ArrayList<>(SubstrateCompilationDirectives.singleton().getDeoptEntries().entrySet());
        deoptEntries.sort((e1, e2) -> e1.getKey().format("%H.%n(%p)").compareTo(e2.getKey().format("%H.%n(%p)")));

        for (Entry<AnalysisMethod, Map<Long, DeoptSourceFrameInfo>> entry : deoptEntries) {
            HostedMethod method = imageHeap.getUniverse().lookup(entry.getKey());

            if (method.hasCalleeSavedRegisters()) {
                System.out.println("DeoptEntry has callee saved registers: " + method.format("%H.%n(%p)"));
                hasError = true;
            }

            List<Entry<Long, DeoptSourceFrameInfo>> sourceFrameInfos = new ArrayList<>(entry.getValue().entrySet());
            sourceFrameInfos.sort(Comparator.comparingLong(Entry::getKey));

            for (Entry<Long, DeoptSourceFrameInfo> sourceFrameInfo : sourceFrameInfos) {
                hasError |= verifyDeoptEntry(codeInfo, method, sourceFrameInfo);
            }
        }
        if (hasError) {
            VMError.shouldNotReachHere("Verification of deoptimization entry points failed");
        }
    }

    private static boolean verifyDeoptEntry(CodeInfo codeInfo, HostedMethod method, Entry<Long, DeoptSourceFrameInfo> sourceFrameInfo) {
        int deoptOffsetInImage = method.getDeoptOffsetInImage();
        long encodedBci = sourceFrameInfo.getKey();

        if (sourceFrameInfo.getValue() == DeoptSourceFrameInfo.INVALID_DEOPT_SOURCE_FRAME) {
            return error(method, encodedBci, "Incompatible source frames; multiple frames with different sizes of locals, locks, and/or stack values exist");
        }

        if (deoptOffsetInImage <= 0) {
            return error(method, encodedBci, "entry point method not compiled");
        }

        CodeInfoQueryResult result = new CodeInfoQueryResult();
        long relativeIP = CodeInfoAccess.lookupDeoptimizationEntrypoint(codeInfo, deoptOffsetInImage, encodedBci, result);
        if (relativeIP < 0) {
            return error(method, encodedBci, "entry point not found");
        }
        FrameInfoQueryResult targetFrame = result.getFrameInfo();
        if (targetFrame == null || !targetFrame.isDeoptEntry() || targetFrame.getEncodedBci() != encodedBci) {
            return error(method, encodedBci, "entry point found, but wrong property");
        }

        /*
         * All DeoptEntries not corresponding to exception objects must have an exception handler.
         */
        boolean hasExceptionHandler = result.getExceptionOffset() != 0;
        if (!targetFrame.duringCall() && !targetFrame.rethrowException()) {
            if (!hasExceptionHandler) {
                return error(method, encodedBci, "no exception handler registered for deopt entry");
            }
        } else if (!targetFrame.duringCall() && targetFrame.rethrowException()) {
            if (hasExceptionHandler) {
                return error(method, encodedBci, "exception handler registered for rethrowException");
            }
        } else if (targetFrame.duringCall() && !targetFrame.rethrowException()) {
            if (!hasExceptionHandler) {
                return error(method, encodedBci, "no exception handler registered for deopt entry");
            }
        } else {
            return error(method, encodedBci, "invalid encoded bci");
        }

        /*
         * Validating the sizes of the source and target frames match.
         */

        DeoptSourceFrameInfo sourceFrame = sourceFrameInfo.getValue();
        FrameInfoQueryResult.ValueInfo[] targetValues = targetFrame.getValueInfos();
        List<JavaKind> sourceKinds = Arrays.asList(sourceFrame.expectedKinds);
        if (targetFrame.getNumLocals() != sourceFrame.numLocals || targetFrame.getNumStack() != sourceFrame.numStack || targetFrame.getNumLocks() != sourceFrame.numLocks) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("Mismatch between number of expected values in target and source.\n");
            errorMessage.append(String.format("Target: locals-%d, stack-%d, locks-%d.\n", targetFrame.getNumLocals(), targetFrame.getNumStack(), targetFrame.getNumLocks()));
            appendFrameInfo(errorMessage, true, Arrays.stream(targetValues).map(FrameInfoQueryResult.ValueInfo::getKind).collect(Collectors.toList()));
            errorMessage.append(String.format("Source: locals-%d, stack-%d, locks-%d.\n", sourceFrame.numLocals, sourceFrame.numStack, sourceFrame.numLocks));
            appendFrameInfo(errorMessage, false, sourceKinds);
            return error(method, encodedBci, errorMessage.toString());
        }

        /*
         * Validating the value kinds expected by the target frame is a subset of the source frame.
         */

        boolean validTarget = true;
        for (int i = 0; i < targetValues.length; i++) {
            JavaKind targetKind = targetValues[i].getKind();
            if (targetKind != JavaKind.Illegal) {
                if (targetKind != sourceKinds.get(i)) {
                    validTarget = false;
                    break;
                }
            }
        }

        if (!validTarget) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("Deoptimization source frame is not a superset of the target frame.\n");
            appendFrameInfo(errorMessage, true, Arrays.stream(targetValues).map(FrameInfoQueryResult.ValueInfo::getKind).collect(Collectors.toList()));
            appendFrameInfo(errorMessage, false, sourceKinds);
            return error(method, encodedBci, errorMessage.toString());
        }
        return false;
    }

    private static void appendFrameInfo(StringBuilder builder, boolean isTarget, List<JavaKind> javaKinds) {
        builder.append(String.format("***%s Frame***\n", isTarget ? "Target" : "Source"));
        for (int i = 0; i < javaKinds.size(); i++) {
            builder.append(String.format("index %d: %s\n", i, javaKinds.get(i)));
        }
    }

    private static boolean error(HostedMethod method, long encodedBci, String msg) {
        System.out.println(method.format("%H.%n(%p)") + ", encodedBci " + encodedBci + " (bci " + FrameInfoDecoder.readableBci(encodedBci) + "): " + msg);
        return true;
    }

    protected boolean verifyMethods(CodeInfoEncoder codeInfoEncoder, CodeInfo codeInfo) {
        for (Pair<HostedMethod, CompilationResult> pair : getOrderedCompilations()) {
            HostedMethod method = pair.getLeft();
            CodeInfoEncoder.verifyMethod(method, pair.getRight(), method.getCodeAddressOffset(), codeSizeFor(method), codeInfo);
        }
        codeInfoEncoder.verifyFrameInfo(codeInfo);
        return true;
    }

    /*
     * Constants and code objects are all assigned offsets in the heap. Reference constants can
     * refer to other heap objects. TODO: is it true that that all code-->data references go via a
     * Constant? It appears so, but I'm not sure. -srk
     */

    public abstract void patchMethods(DebugContext debug, RelocatableBuffer relocs, ObjectFile objectFile);

    public abstract void writeCode(RelocatableBuffer buffer);

    public void writeConstants(NativeImageHeapWriter writer, RelocatableBuffer buffer) {
        ByteBuffer bb = buffer.getByteBuffer();
        dataSection.buildDataSection(bb, (position, constant) -> {
            writer.writeReference(buffer, position, (JavaConstant) constant, "VMConstant: " + constant);
        });
    }

    public abstract NativeTextSectionImpl getTextSectionImpl(RelocatableBuffer buffer, ObjectFile objectFile, NativeImageCodeCache codeCache);

    public Path[] getCCInputFiles(Path tempDirectory, String imageName) {
        return new Path[]{tempDirectory.resolve(imageName + ObjectFile.getFilenameSuffix())};
    }

    public abstract List<ObjectFile.Symbol> getSymbols(ObjectFile objectFile);

    public void printCompilationResults() {
        System.out.println("--- compiled methods");
        for (Pair<HostedMethod, CompilationResult> pair : getOrderedCompilations()) {
            HostedMethod method = pair.getLeft();
            CompilationResult result = pair.getRight();
            System.out.format("%8d %5d %s: frame %d\n", method.getCodeAddressOffset(), result.getTargetCodeSize(), method.format("%H.%n(%p)"), result.getTotalFrameSize());
        }
        System.out.println("--- vtables:");
        for (HostedType type : imageHeap.getUniverse().getTypes()) {
            for (int i = 0; i < type.getVTable().length; i++) {
                HostedMethod method = type.getVTable()[i];
                if (method != null) {
                    CompilationResult comp = compilationResultFor(type.getVTable()[i]);
                    if (comp != null) {
                        System.out.format("%d %s @ %d: %s = 0x%x\n", type.getTypeID(), type.toJavaName(false), i, method.format("%r %n(%p)"), method.getCodeAddressOffset());
                    }
                }
            }
        }
    }

    private static class HostedFrameInfoCustomization extends FrameInfoEncoder.SourceFieldsFromMethod {
        int numDeoptEntryPoints;
        int numDuringCallEntryPoints;

        @Override
        protected Class<?> getDeclaringJavaClass(ResolvedJavaMethod method) {
            HostedType type = (HostedType) method.getDeclaringClass();
            assert type.getWrapped().isReachable() : "Declaring class not marked as used, therefore the DynamicHub is not initialized properly: " + method.format("%H.%n(%p)");
            return type.getJavaClass();
        }

        @Override
        protected boolean storeDeoptTargetMethod() {
            return false;
        }

        @Override
        protected void recordFrame(ResolvedJavaMethod method, Infopoint infopoint, boolean isDeoptEntry) {
            super.recordFrame(method, infopoint, isDeoptEntry);

            if (isDeoptEntry) {
                /* Collect number of entry points for later printing of statistics. */
                if (infopoint instanceof DeoptEntryInfopoint) {
                    numDeoptEntryPoints++;
                } else if (infopoint instanceof Call) {
                    numDuringCallEntryPoints++;
                } else {
                    throw shouldNotReachHere();
                }
            }
        }

        @Override
        protected boolean includeLocalValues(ResolvedJavaMethod method, Infopoint infopoint, boolean isDeoptEntry) {
            if (ImageSingletons.contains(ProfilingSampler.class) && ImageSingletons.lookup(ProfilingSampler.class).isCollectingActive()) {
                return true;
            }

            if (isDeoptEntry || ((HostedMethod) method).compilationInfo.canDeoptForTesting()) {
                /*
                 * Need to restore locals from deoptimization source.
                 */
                return true;
            }

            BytecodeFrame topFrame = infopoint.debugInfo.frame();
            for (BytecodeFrame frame = topFrame; frame != null; frame = frame.caller()) {
                if (SubstrateCompilationDirectives.singleton().isFrameInformationRequired(frame.getMethod())) {
                    /*
                     * Somewhere in the inlining hierarchy is a method for which frame information
                     * was explicitly requested. For simplicity, we output frame information for all
                     * methods in the inlining chain.
                     *
                     * We require frame information, for example, for frames that must be visible to
                     * SubstrateStackIntrospection.
                     */
                    return true;
                }
            }

            return false;
        }

        @Override
        protected boolean isDeoptEntry(ResolvedJavaMethod method, CompilationResult compilation, Infopoint infopoint) {
            BytecodeFrame topFrame = infopoint.debugInfo.frame();
            BytecodeFrame rootFrame = topFrame;
            while (rootFrame.caller() != null) {
                rootFrame = rootFrame.caller();
            }
            assert rootFrame.getMethod().equals(method);

            boolean isBciDeoptEntry = ((HostedMethod) method).compilationInfo.isDeoptEntry(rootFrame.getBCI(), rootFrame.duringCall, rootFrame.rethrowException);
            if (isBciDeoptEntry) {
                /*
                 * When an infopoint's bci corresponds to a deoptimization entrypoint, it does not
                 * necessarily mean that the infopoint itself is for a deoptimization entrypoint.
                 * This is because the infopoint can also be for present debugging purposes and
                 * happen to have the same bci. Further checks are needed to determine actual
                 * deoptimization entrypoints.
                 */
                assert topFrame == rootFrame : "Deoptimization target has inlined frame: " + topFrame;
                if (topFrame.duringCall) {
                    /*
                     * During call entrypoints must always be linked to a call.
                     */
                    VMError.guarantee(infopoint instanceof Call, String.format("Unexpected infopoint type: %s\nFrame: %s", infopoint, topFrame));
                    return compilation.isValidCallDeoptimizationState((Call) infopoint);
                } else {
                    /*
                     * Other deoptimization entrypoints correspond to an DeoptEntryOp.
                     */
                    return infopoint instanceof DeoptEntryInfopoint;
                }
            }

            return false;
        }
    }

    public interface ReflectionMetadataEncoder extends EncodedReflectionMetadataSupplier {
        void addClassMetadata(MetaAccessProvider metaAccess, HostedType type, Class<?>[] reflectionClasses);

        void addReflectionFieldMetadata(MetaAccessProvider metaAccess, HostedField sharedField, Field reflectField);

        void addReflectionExecutableMetadata(MetaAccessProvider metaAccess, HostedMethod sharedMethod, Executable reflectMethod, Object accessor);

        void addHeapAccessibleObjectMetadata(MetaAccessProvider metaAccess, WrappedElement hostedElement, AccessibleObject object, boolean registered);

        void addHidingFieldMetadata(AnalysisField analysisField, HostedType declType, String name, HostedType type, int modifiers);

        void addHidingMethodMetadata(AnalysisMethod analysisMethod, HostedType declType, String name, HostedType[] paramTypes, int modifiers, HostedType returnType);

        void addReachableFieldMetadata(HostedField field);

        void addReachableExecutableMetadata(HostedMethod method);

        void encodeAllAndInstall();

        Method getRoot = ReflectionUtil.lookupMethod(AccessibleObject.class, "getRoot");

        static AccessibleObject getHolder(AccessibleObject accessibleObject) {
            try {
                AccessibleObject root = (AccessibleObject) getRoot.invoke(accessibleObject);
                return root == null ? accessibleObject : root;
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

    }

    public interface ReflectionMetadataEncoderFactory {
        ReflectionMetadataEncoder create(CodeInfoEncoder.Encoders encoders);
    }
}
