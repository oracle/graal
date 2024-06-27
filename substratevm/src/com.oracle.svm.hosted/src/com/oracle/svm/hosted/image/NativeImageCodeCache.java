/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.MissingRegistrationUtils.throwMissingRegistrationErrors;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.shouldNotReachHereUnexpectedInput;

import java.io.PrintWriter;
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
import java.util.stream.Collectors;

import com.oracle.svm.core.meta.CompressedNullConstant;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.graal.pointsto.AbstractAnalysisEngine;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.infrastructure.WrappedElement;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoDecoder;
import com.oracle.svm.core.code.FrameInfoDecoder.ConstantAccess;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.ImageCodeInfo.HostedImageCodeInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.configure.ConditionalRuntimeValue;
import com.oracle.svm.core.deopt.DeoptEntryInfopoint;
import com.oracle.svm.core.graal.code.SubstrateDataBuilder;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.reflect.target.EncodedRuntimeMetadataSupplier;
import com.oracle.svm.core.sampler.CallStackFrameMethodInfo;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.DeadlockWatchdog;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.code.DeoptimizationUtils;
import com.oracle.svm.hosted.code.HostedImageHeapConstantPatch;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives.DeoptSourceFrameInfo;
import com.oracle.svm.hosted.image.NativeImage.NativeTextSectionImpl;
import com.oracle.svm.hosted.imagelayer.PriorLayerSymbolTracker;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.reflect.ReflectionHostedSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.code.DataSection;
import jdk.graal.compiler.core.common.type.CompressibleConstant;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.options.Option;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.VMConstant;

public abstract class NativeImageCodeCache {

    private final Map<Constant, Object> embeddedConstants = new HashMap<>();
    private final Set<HostedMethod> baseLayerMethods;

    public static class Options {
        @Option(help = "Verify that all possible deoptimization entry points have been properly compiled and registered in the metadata")//
        public static final HostedOptionKey<Boolean> VerifyDeoptimizationEntryPoints = new HostedOptionKey<>(false);
    }

    private int codeAreaSize;

    protected final NativeImageHeap imageHeap;

    private final Map<HostedMethod, CompilationResult> compilations;

    private final List<Pair<HostedMethod, CompilationResult>> orderedCompilations;

    protected final Platform targetPlatform;

    private final DataSection dataSection;

    private final Map<Constant, String> constantReasons = new HashMap<>();

    public NativeImageCodeCache(Map<HostedMethod, CompilationResult> compilationResultMap, NativeImageHeap imageHeap) {
        this(compilationResultMap, imageHeap, ImageSingletons.lookup(Platform.class), null);
    }

    public NativeImageCodeCache(Map<HostedMethod, CompilationResult> compilationResultMap, NativeImageHeap imageHeap, Set<HostedMethod> baseLayerMethods) {
        this(compilationResultMap, imageHeap, ImageSingletons.lookup(Platform.class), baseLayerMethods);
    }

    public void purge() {
        compilations.clear();
        orderedCompilations.clear();
    }

    @SuppressWarnings("this-escape")//
    public NativeImageCodeCache(Map<HostedMethod, CompilationResult> compilations, NativeImageHeap imageHeap, Platform targetPlatform) {
        this(compilations, imageHeap, targetPlatform, null);
    }

    @SuppressWarnings("this-escape")//
    public NativeImageCodeCache(Map<HostedMethod, CompilationResult> compilations, NativeImageHeap imageHeap, Platform targetPlatform, Set<HostedMethod> baseLayerMethods) {
        this.compilations = compilations;
        this.imageHeap = imageHeap;
        this.baseLayerMethods = baseLayerMethods;
        this.dataSection = new DataSection();
        this.targetPlatform = targetPlatform;
        this.orderedCompilations = computeCompilationOrder(compilations);
    }

    public abstract int getCodeCacheSize();

    public int getCodeAreaSize() {
        assert codeAreaSize >= 0;
        return codeAreaSize;
    }

    public void setCodeAreaSize(int codeAreaSize) {
        this.codeAreaSize = codeAreaSize;
    }

    public Pair<HostedMethod, CompilationResult> getFirstCompilation() {
        return orderedCompilations.getFirst();
    }

    public Pair<HostedMethod, CompilationResult> getLastCompilation() {
        return orderedCompilations.getLast();
    }

    protected List<Pair<HostedMethod, CompilationResult>> computeCompilationOrder(Map<HostedMethod, CompilationResult> compilationMap) {
        return compilationMap.entrySet().stream() //
                        .map(e -> Pair.create(e.getKey(), e.getValue())) //
                        .sorted(Comparator.comparing(o -> o.getLeft().wrapped.format("%H.%n(%P):%R"))) //
                        .collect(Collectors.toList());
    }

    public List<Pair<HostedMethod, CompilationResult>> getOrderedCompilations() {
        return orderedCompilations;
    }

    public Set<HostedMethod> getBaseLayerMethods() {
        return baseLayerMethods;
    }

    public abstract int codeSizeFor(HostedMethod method);

    protected CompilationResult compilationResultFor(HostedMethod method) {
        return compilations.get(method);
    }

    public abstract void layoutMethods(DebugContext debug, BigBang bb);

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

    /** Get constants embedded in the data section and compilation results. */
    public Map<Constant, Object> initAndGetEmbeddedConstants() {
        VMError.guarantee(BuildPhaseProvider.isCompilationFinished(), "Code cache embedded constants are available only after compilation.");
        VMError.guarantee(embeddedConstants.isEmpty(), "Embedded constants are already computed.");

        for (DataSection.Data data : dataSection) {
            if (data instanceof SubstrateDataBuilder.ObjectData objectData) {
                embeddedConstants.put(objectData.getConstant(), NativeImageHeap.HeapInclusionReason.DataSection);
            }
        }
        for (Pair<HostedMethod, CompilationResult> pair : getOrderedCompilations()) {
            BytecodePosition position = AbstractAnalysisEngine.syntheticSourcePosition(pair.getLeft().getWrapped());
            CompilationResult compilationResult = pair.getRight();
            for (DataPatch patch : compilationResult.getDataPatches()) {
                if (patch.reference instanceof ConstantReference ref) {
                    embeddedConstants.put(ref.getConstant(), position);
                }
            }
            for (CompilationResult.CodeAnnotation codeAnnotation : compilationResult.getCodeAnnotations()) {
                if (codeAnnotation instanceof HostedImageHeapConstantPatch patch) {
                    embeddedConstants.put(patch.constant, position);
                }
            }
        }
        return embeddedConstants;
    }

    public void addConstantsToHeap() {
        VMError.guarantee(!embeddedConstants.isEmpty(), "Embedded constants should already be computed.");
        embeddedConstants.forEach((constant, reason) -> addConstantToHeap(constant, reason instanceof BytecodePosition position ? position.getMethod().getName() : reason));
    }

    private void addConstantToHeap(Constant constant, Object reason) {
        if (constant instanceof SubstrateMethodPointerConstant) {
            /*
             * This constant represents a pointer to a method, and as such, should not be added to
             * the heap
             */
            return;
        }

        HostedType hostedType = imageHeap.hMetaAccess.lookupJavaType((JavaConstant) constant);
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

    public void buildRuntimeMetadata(DebugContext debug, SnippetReflectionProvider snippetReflectionProvider) {
        buildRuntimeMetadata(debug, snippetReflectionProvider, new MethodPointer(getFirstCompilation().getLeft(), true), WordFactory.signed(getCodeAreaSize()));
    }

    static class HostedConstantAccess extends ConstantAccess {
        private final SnippetReflectionProvider snippetReflection;

        HostedConstantAccess(SnippetReflectionProvider snippetReflection) {
            this.snippetReflection = snippetReflection;
        }

        @Override
        public JavaConstant forObject(Object object, boolean isCompressedReference) {
            if (object == null) {
                return isCompressedReference ? CompressedNullConstant.COMPRESSED_NULL : JavaConstant.NULL_POINTER;
            }
            JavaConstant constant = snippetReflection.forObject(object);
            if (constant instanceof CompressibleConstant compressible && isCompressedReference != compressible.isCompressed()) {
                return isCompressedReference ? compressible.compress() : compressible.uncompress();
            }
            return constant;
        }

        @Override
        public Object asObject(JavaConstant constant) {
            return snippetReflection.asObject(Object.class, constant);
        }
    }

    protected void buildRuntimeMetadata(DebugContext debug, SnippetReflectionProvider snippetReflection, CFunctionPointer firstMethod, UnsignedWord codeSize) {
        // Build run-time metadata.
        HostedFrameInfoCustomization frameInfoCustomization = new HostedFrameInfoCustomization();
        CodeInfoEncoder.Encoders encoders = new CodeInfoEncoder.Encoders(true, clazz -> {
            if (clazz != null && !imageHeap.hMetaAccess.optionalLookupJavaType(clazz).isPresent()) {
                throw VMError.shouldNotReachHere("Type added to the runtime metadata without being seen by the analysis: %s", clazz);
            }
        });
        HostedConstantAccess hostedConstantAccess = new HostedConstantAccess(snippetReflection);
        CodeInfoEncoder codeInfoEncoder = new CodeInfoEncoder(frameInfoCustomization, encoders, hostedConstantAccess);
        DeadlockWatchdog watchdog = ImageSingletons.lookup(DeadlockWatchdog.class);
        for (Pair<HostedMethod, CompilationResult> pair : getOrderedCompilations()) {
            encodeMethod(codeInfoEncoder, pair);
            watchdog.recordActivity();
        }

        HostedUniverse hUniverse = imageHeap.hUniverse;
        HostedMetaAccess hMetaAccess = imageHeap.hMetaAccess;
        RuntimeMetadataEncoder runtimeMetadataEncoder = ImageSingletons.lookup(ReflectionMetadataEncoderFactory.class).create(hUniverse.getSnippetReflection(), encoders);
        ReflectionHostedSupport reflectionSupport = ImageSingletons.lookup(ReflectionHostedSupport.class);

        Map<Class<?>, Set<Class<?>>> innerClasses = reflectionSupport.getReflectionInnerClasses();
        Set<?> heapDynamicHubs = reflectionSupport.getHeapDynamicHubs();
        for (HostedType type : hUniverse.getTypes()) {
            if (type.getWrapped().isReachable() && !type.getWrapped().isInBaseLayer() && heapDynamicHubs.contains(type.getHub())) {
                Class<?>[] typeInnerClasses = innerClasses.getOrDefault(type.getJavaClass(), Collections.emptySet()).toArray(new Class<?>[0]);
                runtimeMetadataEncoder.addClassMetadata(hMetaAccess, type, typeInnerClasses);
            }
        }

        reflectionSupport.getClassLookupErrors().forEach((clazz, error) -> {
            HostedType type = hMetaAccess.lookupJavaType(clazz);
            runtimeMetadataEncoder.addClassLookupError(type, error);
        });

        reflectionSupport.getFieldLookupErrors().forEach((clazz, error) -> {
            HostedType type = hMetaAccess.lookupJavaType(clazz);
            runtimeMetadataEncoder.addFieldLookupError(type, error);
        });

        reflectionSupport.getMethodLookupErrors().forEach((clazz, error) -> {
            HostedType type = hMetaAccess.lookupJavaType(clazz);
            runtimeMetadataEncoder.addMethodLookupError(type, error);
        });

        reflectionSupport.getConstructorLookupErrors().forEach((clazz, error) -> {
            HostedType type = hMetaAccess.lookupJavaType(clazz);
            runtimeMetadataEncoder.addConstructorLookupError(type, error);
        });

        Set<AnalysisField> includedFields = new HashSet<>();
        Set<AnalysisMethod> includedMethods = new HashSet<>();
        Map<AnalysisField, ConditionalRuntimeValue<Field>> configurationFields = reflectionSupport.getReflectionFields();
        Map<AnalysisMethod, ConditionalRuntimeValue<Executable>> configurationExecutables = reflectionSupport.getReflectionExecutables();

        reflectionSupport.getHeapReflectionFields().forEach(((analysisField, reflectField) -> {
            if (includedFields.add(analysisField)) {
                HostedField hostedField = hUniverse.lookup(analysisField);
                runtimeMetadataEncoder.addHeapAccessibleObjectMetadata(hMetaAccess, hostedField, reflectField, configurationFields.containsKey(analysisField));
            }
        }));

        reflectionSupport.getHeapReflectionExecutables().forEach(((analysisMethod, reflectMethod) -> {
            if (includedMethods.add(analysisMethod)) {
                HostedMethod hostedMethod = hUniverse.lookup(analysisMethod);
                runtimeMetadataEncoder.addHeapAccessibleObjectMetadata(hMetaAccess, hostedMethod, reflectMethod, configurationExecutables.containsKey(analysisMethod));
            }
        }));

        configurationFields.forEach(((analysisField, reflectField) -> {
            if (includedFields.add(analysisField)) {
                HostedField hostedField = hUniverse.lookup(analysisField);
                runtimeMetadataEncoder.addReflectionFieldMetadata(hMetaAccess, hostedField, reflectField);
            }
        }));

        var symbolTracker = PriorLayerSymbolTracker.singletonOrNull();
        configurationExecutables.forEach(((analysisMethod, reflectMethod) -> {
            if (includedMethods.add(analysisMethod)) {
                HostedMethod method = hUniverse.lookup(analysisMethod);
                if (analysisMethod.isInBaseLayer()) {
                    symbolTracker.registerPriorLayerReference(method);
                }
                Object accessor = reflectionSupport.getAccessor(analysisMethod);
                runtimeMetadataEncoder.addReflectionExecutableMetadata(hMetaAccess, method, reflectMethod, accessor);
            }
        }));

        for (Object field : reflectionSupport.getHidingReflectionFields()) {
            AnalysisField analysisField = (AnalysisField) field;
            if (includedFields.add(analysisField)) {
                HostedType declaringType = hUniverse.lookup(analysisField.getDeclaringClass());
                String name = analysisField.getName();
                HostedType type = hUniverse.lookup(analysisField.getType());
                int modifiers = analysisField.getModifiers();
                runtimeMetadataEncoder.addHidingFieldMetadata(analysisField, declaringType, name, type, modifiers);
            }
        }

        for (Object method : reflectionSupport.getHidingReflectionMethods()) {
            AnalysisMethod analysisMethod = (AnalysisMethod) method;
            if (includedMethods.add(analysisMethod)) {
                HostedType declaringType = hUniverse.lookup(analysisMethod.getDeclaringClass());
                String name = analysisMethod.getName();
                HostedType[] parameterTypes = analysisMethod.getSignature().toParameterList(null).stream() //
                                .map(hUniverse::lookup) //
                                .toArray(HostedType[]::new);
                int modifiers = analysisMethod.getModifiers();
                HostedType returnType = hUniverse.lookup(analysisMethod.getSignature().getReturnType());
                runtimeMetadataEncoder.addHidingMethodMetadata(analysisMethod, declaringType, name, parameterTypes, modifiers, returnType);
            }
        }

        if (SubstrateOptions.IncludeMethodData.getValue()) {
            for (HostedField field : hUniverse.getFields()) {
                if (field.isAccessed() && !field.getWrapped().isInBaseLayer() && !includedFields.contains(field.getWrapped())) {
                    runtimeMetadataEncoder.addReachableFieldMetadata(field);
                }
            }

            for (HostedMethod method : hUniverse.getMethods()) {
                if (method.getWrapped().isReachable() && !method.getWrapped().isInBaseLayer() && !method.getWrapped().isIntrinsicMethod() && !includedMethods.contains(method.getWrapped())) {
                    runtimeMetadataEncoder.addReachableExecutableMetadata(method);
                }
            }
        }

        if (throwMissingRegistrationErrors()) {
            reflectionSupport.getNegativeFieldQueries().forEach((analysisType, fields) -> {
                HostedType hostedType = hUniverse.optionalLookup(analysisType);
                if (hostedType != null) {
                    for (String field : fields) {
                        runtimeMetadataEncoder.addNegativeFieldQueryMetadata(hostedType, field);
                    }
                }
            });

            reflectionSupport.getNegativeMethodQueries().forEach((analysisType, methodSignatures) -> {
                HostedType hostedType = hUniverse.optionalLookup(analysisType);
                if (hostedType != null) {
                    for (AnalysisMethod.Signature methodSignature : methodSignatures) {
                        HostedType[] parameterTypes = hUniverse.optionalLookup(methodSignature.parameterTypes());
                        if (parameterTypes != null) {
                            runtimeMetadataEncoder.addNegativeMethodQueryMetadata(hostedType, methodSignature.name(), parameterTypes);
                        }
                    }
                }
            });

            reflectionSupport.getNegativeConstructorQueries().forEach((analysisType, constructorSignatures) -> {
                HostedType hostedType = hUniverse.optionalLookup(analysisType);
                if (hostedType != null) {
                    for (AnalysisType[] analysisParameterTypes : constructorSignatures) {
                        HostedType[] parameterTypes = hUniverse.optionalLookup(analysisParameterTypes);
                        if (parameterTypes != null) {
                            runtimeMetadataEncoder.addNegativeConstructorQueryMetadata(hostedType, parameterTypes);
                        }
                    }
                }
            });
        }

        if (NativeImageOptions.PrintMethodHistogram.getValue()) {
            System.out.println("encoded deopt entry points                 ; " + frameInfoCustomization.numDeoptEntryPoints);
            System.out.println("encoded during call entry points           ; " + frameInfoCustomization.numDuringCallEntryPoints);
        }

        HostedImageCodeInfo imageCodeInfo = installCodeInfo(snippetReflection, firstMethod, codeSize, codeInfoEncoder, runtimeMetadataEncoder, watchdog);

        if (ImageSingletons.contains(CallStackFrameMethodInfo.class)) {
            ImageSingletons.lookup(CallStackFrameMethodInfo.class).initialize(encoders, hMetaAccess);
        }

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
            verifyDeoptEntries(imageCodeInfo, hostedConstantAccess);
        }

        assert verifyMethods(debug, hUniverse, codeInfoEncoder, imageCodeInfo, hostedConstantAccess);
    }

    protected HostedImageCodeInfo installCodeInfo(SnippetReflectionProvider snippetReflection, CFunctionPointer firstMethod, UnsignedWord codeSize, CodeInfoEncoder codeInfoEncoder,
                    RuntimeMetadataEncoder runtimeMetadataEncoder, DeadlockWatchdog watchdog) {
        HostedImageCodeInfo imageCodeInfo = CodeInfoTable.getImageCodeCache().getHostedImageCodeInfo();
        codeInfoEncoder.encodeAllAndInstall(imageCodeInfo, new HostedInstantReferenceAdjuster(snippetReflection), watchdog::recordActivity);
        runtimeMetadataEncoder.encodeAllAndInstall();
        imageCodeInfo.setCodeStart(firstMethod);
        imageCodeInfo.setCodeSize(codeSize);
        imageCodeInfo.setDataOffset(codeSize);
        imageCodeInfo.setDataSize(WordFactory.zero()); // (only for data immediately after code)
        imageCodeInfo.setCodeAndDataMemorySize(codeSize);
        return imageCodeInfo;
    }

    protected void encodeMethod(CodeInfoEncoder codeInfoEncoder, Pair<HostedMethod, CompilationResult> pair) {
        final HostedMethod method = pair.getLeft();
        final CompilationResult compilation = pair.getRight();
        codeInfoEncoder.addMethod(method, compilation, method.getCodeAddressOffset(), codeSizeFor(method));
    }

    private void verifyDeoptEntries(CodeInfo codeInfo, ConstantAccess constantAccess) {
        boolean hasError = false;
        List<Entry<AnalysisMethod, Map<Long, DeoptSourceFrameInfo>>> deoptEntries = new ArrayList<>(SubstrateCompilationDirectives.singleton().getDeoptEntries().entrySet());
        deoptEntries.sort(Comparator.comparing(e -> e.getKey().format("%H.%n(%p)")));

        for (Entry<AnalysisMethod, Map<Long, DeoptSourceFrameInfo>> entry : deoptEntries) {
            HostedMethod method = imageHeap.hUniverse.lookup(entry.getKey().getMultiMethod(MultiMethod.ORIGINAL_METHOD));

            if (method.hasCalleeSavedRegisters()) {
                System.out.println("DeoptEntry has callee saved registers: " + method.format("%H.%n(%p)"));
                hasError = true;
            }

            List<Entry<Long, DeoptSourceFrameInfo>> sourceFrameInfos = new ArrayList<>(entry.getValue().entrySet());
            sourceFrameInfos.sort(Comparator.comparingLong(Entry::getKey));

            for (Entry<Long, DeoptSourceFrameInfo> sourceFrameInfo : sourceFrameInfos) {
                hasError = verifyDeoptEntry(codeInfo, method, sourceFrameInfo, constantAccess) || hasError;
            }
        }
        if (hasError) {
            VMError.shouldNotReachHere("Verification of deoptimization entry points failed");
        }
    }

    private static boolean verifyDeoptEntry(CodeInfo codeInfo, HostedMethod method, Entry<Long, DeoptSourceFrameInfo> sourceFrameInfo, ConstantAccess constantAccess) {
        int deoptOffsetInImage = method.getImageCodeDeoptOffset();
        long encodedBci = sourceFrameInfo.getKey();

        if (sourceFrameInfo.getValue() == DeoptSourceFrameInfo.INVALID_DEOPT_SOURCE_FRAME) {
            return error(method, encodedBci, "Incompatible source frames; multiple frames with different sizes of locals, locks, and/or stack values exist");
        }

        if (deoptOffsetInImage <= 0) {
            return error(method, encodedBci, "entry point method not compiled");
        }

        CodeInfoQueryResult result = new CodeInfoQueryResult();
        long relativeIP = CodeInfoAccess.lookupDeoptimizationEntrypoint(codeInfo, deoptOffsetInImage, encodedBci, result, constantAccess);
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
        switch (targetFrame.getStackState()) {
            case BeforePop:
            case AfterPop:
                if (!hasExceptionHandler) {
                    return error(method, encodedBci, "no exception handler registered for deopt entry");
                }
                break;
            case Rethrow:
                if (hasExceptionHandler) {
                    return error(method, encodedBci, "exception handler registered for rethrowException");
                }
                break;
            default:
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
            errorMessage.append("Mismatch between number of expected values in target and source.").append(System.lineSeparator());
            errorMessage.append(String.format("Target: locals-%d, stack-%d, locks-%d.%n", targetFrame.getNumLocals(), targetFrame.getNumStack(), targetFrame.getNumLocks()));
            appendFrameInfo(errorMessage, true, Arrays.stream(targetValues).map(FrameInfoQueryResult.ValueInfo::getKind).collect(Collectors.toList()));
            errorMessage.append(String.format("Source: locals-%d, stack-%d, locks-%d.%n", sourceFrame.numLocals, sourceFrame.numStack, sourceFrame.numLocks));
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
            errorMessage.append(String.format("Deoptimization source frame is not a superset of the target frame.%n"));
            appendFrameInfo(errorMessage, true, Arrays.stream(targetValues).map(FrameInfoQueryResult.ValueInfo::getKind).collect(Collectors.toList()));
            appendFrameInfo(errorMessage, false, sourceKinds);
            return error(method, encodedBci, errorMessage.toString());
        }
        return false;
    }

    private static void appendFrameInfo(StringBuilder builder, boolean isTarget, List<JavaKind> javaKinds) {
        builder.append(String.format("***%s Frame***%n", isTarget ? "Target" : "Source"));
        for (int i = 0; i < javaKinds.size(); i++) {
            builder.append(String.format("index %d: %s%n", i, javaKinds.get(i)));
        }
    }

    private static boolean error(HostedMethod method, long encodedBci, String msg) {
        System.out.println(method.format("%H.%n(%p)") + ", encodedBci " + encodedBci + " (bci " + FrameInfoDecoder.readableBci(encodedBci) + "):" + System.lineSeparator() + msg);
        return true;
    }

    protected boolean verifyMethods(DebugContext debug, HostedUniverse hUniverse, CodeInfoEncoder codeInfoEncoder, CodeInfo codeInfo, ConstantAccess constantAccess) {
        /*
         * Run method verification in parallel to reduce computation time.
         */
        BigBang bb = hUniverse.getBigBang();
        CompletionExecutor executor = new CompletionExecutor(debug, bb);
        try {
            executor.init();
            executor.start();
            for (Pair<HostedMethod, CompilationResult> pair : getOrderedCompilations()) {
                HostedMethod method = pair.getLeft();
                executor.execute(ignore -> CodeInfoEncoder.verifyMethod(method, pair.getRight(), method.getCodeAddressOffset(), codeSizeFor(method), codeInfo, constantAccess));
            }
            executor.complete();
        } catch (InterruptedException e) {
            throw VMError.shouldNotReachHere("Failed to verify methods");
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
        dataSection.buildDataSection(bb, (position, constant) -> writer.writeReference(buffer, position, (JavaConstant) constant, "VMConstant: " + constant));
    }

    public abstract NativeTextSectionImpl getTextSectionImpl(RelocatableBuffer buffer, ObjectFile objectFile, NativeImageCodeCache codeCache);

    public Path[] getCCInputFiles(Path tempDirectory, String imageName) {
        return new Path[]{tempDirectory.resolve(imageName + ObjectFile.getFilenameSuffix())};
    }

    public abstract List<ObjectFile.Symbol> getSymbols(ObjectFile objectFile);

    public void printCompilationResults() {
        String reportsPath = SubstrateOptions.reportsPath();
        ReportUtils.report("compilation results", reportsPath, "universe_compilation", "txt", this::printCompilationResults);
    }

    private void printCompilationResults(PrintWriter writer) {

        writer.println("--- compiled methods");
        for (Pair<HostedMethod, CompilationResult> pair : getOrderedCompilations()) {
            HostedMethod method = pair.getLeft();
            CompilationResult result = pair.getRight();
            writer.format("%8d %5d %s: frame %d%n", method.getCodeAddressOffset(), result.getTargetCodeSize(), method.getQualifiedName(), result.getTotalFrameSize());
        }
        writer.println("--- vtables:");
        for (HostedType type : imageHeap.hUniverse.getTypes()) {
            HostedMethod[] vtable = type.getVTable();
            for (int i = 0; i < vtable.length; i++) {
                HostedMethod method = vtable[i];
                if (method != null) {
                    CompilationResult comp = compilationResultFor(vtable[i]);
                    if (comp != null) {
                        writer.format("%d %s @ %d: %s = 0x%x%n", type.getTypeID(), type.toJavaName(false), i, method.format("%r %n(%p)"), method.getCodeAddressOffset());
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
                    throw shouldNotReachHereUnexpectedInput(infopoint); // ExcludeFromJacocoGeneratedReport
                }
            }
        }

        @Override
        protected boolean includeLocalValues(ResolvedJavaMethod method, Infopoint infopoint, boolean isDeoptEntry) {
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
            return DeoptimizationUtils.isDeoptEntry((HostedMethod) method, compilation, infopoint);
        }
    }

    public interface RuntimeMetadataEncoder extends EncodedRuntimeMetadataSupplier {
        void addClassMetadata(MetaAccessProvider metaAccess, HostedType type, Class<?>[] reflectionClasses);

        void addReflectionFieldMetadata(MetaAccessProvider metaAccess, HostedField sharedField, ConditionalRuntimeValue<Field> reflectField);

        void addReflectionExecutableMetadata(MetaAccessProvider metaAccess, HostedMethod sharedMethod, ConditionalRuntimeValue<Executable> reflectMethod, Object accessor);

        void addHeapAccessibleObjectMetadata(MetaAccessProvider metaAccess, WrappedElement hostedElement, AccessibleObject object, boolean registered);

        void addHidingFieldMetadata(AnalysisField analysisField, HostedType declType, String name, HostedType type, int modifiers);

        void addHidingMethodMetadata(AnalysisMethod analysisMethod, HostedType declType, String name, HostedType[] paramTypes, int modifiers, HostedType returnType);

        void addReachableFieldMetadata(HostedField field);

        void addReachableExecutableMetadata(HostedMethod method);

        void addNegativeFieldQueryMetadata(HostedType declaringClass, String fieldName);

        void addNegativeMethodQueryMetadata(HostedType declaringClass, String methodName, HostedType[] parameterTypes);

        void addNegativeConstructorQueryMetadata(HostedType declaringClass, HostedType[] parameterTypes);

        void addClassLookupError(HostedType declaringClass, Throwable exception);

        void addFieldLookupError(HostedType declaringClass, Throwable exception);

        void addMethodLookupError(HostedType declaringClass, Throwable exception);

        void addConstructorLookupError(HostedType declaringClass, Throwable exception);

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
        RuntimeMetadataEncoder create(SnippetReflectionProvider snippetReflection, CodeInfoEncoder.Encoders encoders);
    }
}
