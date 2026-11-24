/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccessExtensionProvider;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.results.StrengthenGraphs;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.MissingRegistrationSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.shared.config.ObjectLayout;
import com.oracle.svm.shared.config.ObjectLayout.IdentityHashMode;
import com.oracle.svm.core.config.ObjectLayoutCompilerSupport;
import com.oracle.svm.core.graal.code.SubstrateMetaAccessExtensionProvider;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.monitor.MultiThreadedMonitorSupport;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.flow.SVMMethodTypeFlowBuilder;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.config.DynamicHubLayout;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.image.LIRNativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageCodeCacheFactory;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.ObjectFileFactory;
import com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.shared.option.HostedOptionValues;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.util.GuestAnnotationAccess;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.options.OptionValues;
import jdk.internal.ValueBased;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public class HostedConfiguration {

    public HostedConfiguration() {
    }

    public static HostedConfiguration instance() {
        return ImageSingletons.lookup(HostedConfiguration.class);
    }

    public static void setInstanceIfEmpty(HostedConfiguration config) {
        if (!ImageSingletons.contains(HostedConfiguration.class)) {
            ImageSingletons.add(HostedConfiguration.class, config);
        }
    }

    static void setDefaultIfEmpty() {
        setInstanceIfEmpty(new HostedConfiguration());

        if (!ImageSingletons.contains(CompressEncoding.class)) {
            ImageSingletons.add(CompressEncoding.class, createCompressEncoding());
        }

        if (!ImageSingletons.contains(ObjectLayout.class)) {
            ObjectLayout objectLayout = createObjectLayout();
            ImageSingletons.add(ObjectLayout.class, objectLayout);
        }
    }

    private static CompressEncoding createCompressEncoding() {
        int compressBase = 1; // actual base is kept in a register
        int compressShift = 0;
        if (SubstrateOptions.useCompressedReferences() && SubstrateOptions.ConcealedOptions.UseCompressedReferenceShift.getValue()) {
            // 8-byte object alignment, three object header bits
            compressShift = 3;
        }
        return new CompressEncoding(compressBase, compressShift);
    }

    /**
     * Defines the serial/epsilon GC object layout.
     *
     * The identity hash code field is optional by default (see
     * {@link SubstrateOptions#OptionalIdentityHashCodes}) and it is only materialized
     * during garbage collection. The field materialization may change the object size (unless there
     * is an otherwise unused gap in the object that can be used instead) and writes a valid
     * identity hash code into the field. Note that non-GC code may only access the identity hash
     * code field after it was materialized (regardless if the field is placed in an unused gap or
     * not).
     *
     * @see #createObjectLayout(JavaKind, IdentityHashMode)
     */
    public static ObjectLayout createObjectLayout() {
        boolean useOptionalIdentityHashField = SubstrateOptions.canUseOptionalIdentityHashCodes() &&
                        !Boolean.FALSE.equals(SubstrateOptions.OptionalIdentityHashCodes.getValue());
        IdentityHashMode identityHashMode = useOptionalIdentityHashField ? IdentityHashMode.OPTIONAL : IdentityHashMode.TYPE_SPECIFIC;
        JavaKind referenceKind = JavaKind.Object;
        if (SubstrateOptions.useCompressedReferences()) {
            referenceKind = JavaKind.Int;
        }
        return createObjectLayout(referenceKind, identityHashMode);
    }

    /**
     * Defines the serial/epsilon GC object layout, using the given identity hash mode. The monitor
     * slot is appended to instance objects unless there is an otherwise unused gap in the object
     * that can be used.
     *
     * The layout of instance objects is:
     * <ul>
     * <li>32/64 bit hub reference</li>
     * <li>instance fields (references, primitives)</li>
     * <li>32/64 bit object monitor reference (if needed; may be placed in a gap between instance
     * fields instead)</li>
     * <li>32 bit identity hashcode (if needed; may be added at runtime or placed in a gap between
     * instance fields instead)</li>
     * </ul>
     *
     * The layout of array objects is:
     * <ul>
     * <li>32/64 bit hub reference</li>
     * <li>32 bit identity hashcode</li>
     * <li>32 bit array length</li>
     * <li>array elements (length * elementSize)</li>
     * </ul>
     */
    public static ObjectLayout createObjectLayout(JavaKind referenceKind, IdentityHashMode identityHashMode) {
        SubstrateTarget target = SubstrateTarget.singleton();
        int referenceSize = target.arch.getPlatformKind(referenceKind).getSizeInBytes();
        int intSize = target.arch.getPlatformKind(JavaKind.Int).getSizeInBytes();
        int objectAlignment = 8;

        int hubSize = referenceSize;

        int hubOffset = 0;
        int headerSize = hubOffset + hubSize;

        int extraArrayHeaderSize = 0;
        int headerIdentityHashOffset = headerSize;
        if (identityHashMode == IdentityHashMode.OBJECT_HEADER) {
            headerSize += intSize;
        } else if (identityHashMode == IdentityHashMode.TYPE_SPECIFIC) {
            extraArrayHeaderSize = intSize;
        } else {
            assert identityHashMode == IdentityHashMode.OPTIONAL;
            headerIdentityHashOffset = -1;
        }

        headerSize += SubstrateOptions.AdditionalHeaderBytes.getValue();

        int firstFieldOffset = headerSize;
        int arrayLengthOffset = headerSize + extraArrayHeaderSize;
        int arrayBaseOffset = arrayLengthOffset + intSize;

        /* There is a dedicated 32-bit field that stores the 31-bit identity hashcode. */
        int identityHashNumBits = Integer.SIZE;
        int identityHashShift = 0;

        return new ObjectLayout(referenceSize, objectAlignment, hubSize, hubOffset, firstFieldOffset, arrayLengthOffset, arrayBaseOffset,
                        headerIdentityHashOffset, identityHashMode, identityHashNumBits, identityHashShift);
    }

    public static void initializeDynamicHubLayout(HostedMetaAccess hMeta) {
        ImageSingletons.add(DynamicHubLayout.class, createDynamicHubLayout(hMeta));
    }

    private static DynamicHubLayout createDynamicHubLayout(HostedMetaAccess hMetaAccess) {
        var dynamicHubType = hMetaAccess.lookupJavaType(Class.class);

        ObjectLayout layout = ObjectLayout.singleton();
        var vtableField = hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "vtable"));
        JavaKind vTableSlotStorageKind = vtableField.getType().getComponentType().getStorageKind();
        int vTableSlotSize = ObjectLayoutCompilerSupport.sizeInBytes(layout, vTableSlotStorageKind);

        var closedTypeWorldTypeCheckSlotsField = hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "closedTypeWorldTypeCheckSlots"));
        int closedTypeWorldTypeCheckSlotsOffset;
        int closedTypeWorldTypeCheckSlotSize;

        Set<HostedField> ignoredFields;
        if (SubstrateOptions.useClosedTypeWorldHubLayout()) {
            closedTypeWorldTypeCheckSlotsOffset = layout.getArrayLengthOffset() + ObjectLayoutCompilerSupport.sizeInBytes(layout, JavaKind.Int);
            closedTypeWorldTypeCheckSlotSize = ObjectLayoutCompilerSupport.sizeInBytes(layout, closedTypeWorldTypeCheckSlotsField.getType().getComponentType().getStorageKind());

            ignoredFields = Set.of(
                            closedTypeWorldTypeCheckSlotsField,
                            vtableField,
                            hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "typeIDDepth")),
                            hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "numClassTypes")),
                            hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "numIterableInterfaceTypes")),
                            hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "openTypeWorldTypeCheckSlots")),
                            hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "openTypeWorldInterfaceHashParam")),
                            hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "openTypeWorldInterfaceHashTable")));
        } else {
            closedTypeWorldTypeCheckSlotsOffset = -1;
            closedTypeWorldTypeCheckSlotSize = -1;

            ignoredFields = Set.of(
                            closedTypeWorldTypeCheckSlotsField,
                            vtableField,
                            hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "typeCheckStart")),
                            hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "typeCheckRange")),
                            hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "typeCheckSlot")));

        }
        return new DynamicHubLayout(layout, dynamicHubType, closedTypeWorldTypeCheckSlotsField, closedTypeWorldTypeCheckSlotsOffset, closedTypeWorldTypeCheckSlotSize, vtableField,
                        vTableSlotStorageKind,
                        vTableSlotSize, ignoredFields);
    }

    public static boolean isArrayLikeLayout(HostedType clazz) {
        return HybridLayout.isHybrid(clazz) || DynamicHubLayout.singleton().isDynamicHub(clazz);
    }

    /** The type fields of the dynamic hub are directly inlined into the object. */
    public static boolean isInlinedField(HostedField field) {
        return DynamicHubLayout.singleton().isInlinedField(field);
    }

    public SVMHost createHostVM(OptionValues options, ImageClassLoader loader, ClassInitializationSupport classInitializationSupport, AnnotationSubstitutionProcessor annotationSubstitutions,
                    MissingRegistrationSupport missingRegistrationSupport) {
        return new SVMHost(options, loader, classInitializationSupport, annotationSubstitutions, missingRegistrationSupport);
    }

    public SVMImageLayerSnapshotUtil createSVMImageLayerSnapshotUtil(ImageClassLoader imageClassLoader) {
        return new SVMImageLayerSnapshotUtil(imageClassLoader);
    }

    public CompileQueue createCompileQueue(DebugContext debug, FeatureHandler featureHandler, HostedUniverse hostedUniverse, RuntimeConfiguration runtimeConfiguration, boolean deoptimizeAll) {
        return new CompileQueue(debug, featureHandler, hostedUniverse, runtimeConfiguration, deoptimizeAll, Collections.emptyList());
    }

    /**
     * Handles source mappings after a successful normal compile task, including fallback results.
     * Preserves derived data and releases the originals unless a later phase needs them. Not called
     * for caller-owned {@link CompileQueue#compileAdditionalMethod} results.
     */
    public final void handleSourceMappingsAfterCompilation(HostedMethod method, CompilationResult result) {
        if (!requiresSourceMappingsAfterCompilation()) {
            preserveSourceMappingData(method, result);
            result.clearSourceMappings();
        }
    }

    /**
     * Describes readers of the original mappings after compilation. New late readers must extend
     * this policy before using the mappings; the build report alone only needs a compact snapshot.
     * The policy must remain stable while compilation workers are active.
     */
    protected boolean requiresSourceMappingsAfterCompilation() {
        /* Default native debug info uses infopoints, not source mappings. */
        return SubstrateOptions.useDebugInfoGeneration() && SubstrateOptions.DebugCodeInfoUseSourceMappings.getValue(HostedOptionValues.singleton().get());
    }

    /**
     * Preserves derived data without retaining the mappings or their source positions. Invoked only
     * when no late reader needs the originals, avoiding duplicate storage for retaining builds.
     * Concurrent calls operate on distinct compilation results, but implementations must protect
     * shared aggregation state and leave the result's mappings intact on error.
     */
    protected void preserveSourceMappingData(@SuppressWarnings("unused") HostedMethod method, @SuppressWarnings("unused") CompilationResult result) {
    }

    /**
     * Invoked in a separate active executor pass after the normal compile queue and before later
     * image-building phases consume compilation results. Implementations may compile additional
     * methods.
     */
    public void afterCompileQueue(@SuppressWarnings("unused") DebugContext debug, @SuppressWarnings("unused") CompileQueue compileQueue) {
    }

    /**
     * Selects providers for post-analysis hosted graph preparation and compilation of a method variant.
     * Implementations must be thread-safe, leave the supplied providers unchanged, and preserve
     * backend compatibility.
     *
     * Selected providers must be compatible with previously prepared graphs and snippets, including
     * template-cache sharing. This hook cannot undo earlier folding or specialization.
     *
     * Hosted inlining requires identical selected provider services and graph-builder plugins for
     * caller and selected inlinee. Copies sharing those services remain compatible. Importing an
     * incompatible graph fails, including for forced inlining. Each backend may retain its original
     * calling-convention code cache.
     *
     * @return the supplied providers, or a compatible bundle with selected services replaced
     */
    public HostedProviders selectCompilationProviders(@SuppressWarnings("unused") HostedMethod method, HostedProviders providers) {
        return providers;
    }

    /**
     * Requests hosted preparation of this analyzed method variant for separate compilation or
     * inlining, without making it a root of normal image compilation. Clients must create and
     * analyze all required variants before hosted parsing; this hook does not add analysis roots.
     * Register every possible inlinee variant, including ones selected only after earlier inlining
     * exposes a new caller/callee pair.
     */
    public boolean isAdditionalParseRoot(@SuppressWarnings("unused") HostedMethod method) {
        return false;
    }

    /**
     * Selects the method variant whose graph may be inlined into {@code caller}. The selection does
     * not change the destination of a call that remains uninlined. Return the callee itself by
     * default, or another variant of the same Java method with compatible analysis and providers.
     *
     * Clients must create and analyze every possible variant, and request its hosted preparation
     * through {@link #isAdditionalParseRoot} unless it is already a normal parse root. Selection can
     * occur for calls exposed by earlier inlining; this hook neither prepares graphs nor repairs
     * earlier specialization.
     * Implementations must be thread-safe and return a consistent selection for each caller/callee.
     */
    public HostedMethod selectInlineeMethod(@SuppressWarnings("unused") HostedMethod caller, HostedMethod callee) {
        return callee;
    }

    public MethodTypeFlowBuilder createMethodTypeFlowBuilder(PointsToAnalysis bb, PointsToAnalysisMethod method, MethodFlowsGraph flowsGraph, MethodFlowsGraph.GraphKind graphKind) {
        return new SVMMethodTypeFlowBuilder(bb, method, flowsGraph, graphKind);
    }

    public MetaAccessExtensionProvider createAnalysisMetaAccessExtensionProvider(AnalysisUniverse aUniverse) {
        return new AnalysisMetaAccessExtensionProvider(aUniverse);
    }

    public MetaAccessExtensionProvider createCompilationMetaAccessExtensionProvider(@SuppressWarnings("unused") MetaAccessProvider metaAccess) {
        return new SubstrateMetaAccessExtensionProvider();
    }

    public void findAllFieldsForLayout(HostedUniverse universe, @SuppressWarnings("unused") HostedMetaAccess metaAccess,
                    @SuppressWarnings("unused") Map<AnalysisField, HostedField> universeFields,
                    ArrayList<HostedField> rawFields, ArrayList<HostedField> allFields, HostedInstanceClass clazz) {
        DynamicHubLayout dynamicHubLayout = DynamicHubLayout.singleton();
        for (ResolvedJavaField javaField : clazz.getWrapped().getInstanceFields(false)) {
            AnalysisField aField = (AnalysisField) javaField;
            HostedField hField = universe.lookup(aField);

            /* Because of @Alias fields, the field lookup might not be declared in our class. */
            if (hField.getDeclaringClass().equals(clazz)) {
                if (dynamicHubLayout.isIgnoredField(hField)) {
                    /*
                     * Ignored fields do not need a field offset.
                     */
                    allFields.add(hField);
                } else if (hField.isAccessed()) {
                    rawFields.add(hField);
                    allFields.add(hField);
                }
            }
        }
    }

    public StrengthenGraphs createStrengthenGraphs(Inflation bb, HostedUniverse universe) {
        return new SubstrateStrengthenGraphs(bb, universe);
    }

    public void collectMonitorFieldInfo(BigBang bb, HostedUniverse hUniverse, EconomicSet<AnalysisType> immutableTypes) {
        /* First set the monitor field for types that always need it. */
        for (AnalysisType type : getForceMonitorSlotTypes(bb)) {
            assert !immutableTypes.contains(type);
            setMonitorField(hUniverse, type);
        }

        boolean sharedLayer = ImageLayerBuildingSupport.buildingSharedLayer();
        if (!sharedLayer) {
            /*
             * Single-image builds and final application layers consider all instantiated types
             * for monitor slots. PGO can narrow this selection using recorded monitor-enter counts.
             */
            processedSynchronizedTypes(bb, hUniverse, immutableTypes);
        }

        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            /*
             * In shared layers, reserve monitor slots for reachable, non-abstract classes because
             * later layers may instantiate them or synchronize on their instances. Arrays omit slots to keep their
             * headers small. Interfaces and primitives have no instances. Known immutable types
             * must not gain mutable slots. @ValueBased classes omit slots to save space because
             * synchronization on them is discouraged.
             *
             * In the final application layer, apply the same reservation only to imported
             * classes so their layouts still match the shared layer.
             */
            for (HostedType hostedType : hUniverse.getTypes()) {
                AnalysisType type = hostedType.getWrapped();
                if (type.isReachable() && (sharedLayer || type.isInSharedLayer())) {
                    maybeSetMonitorField(hUniverse, immutableTypes, type);
                }
            }
        }
    }

    private static EconomicSet<AnalysisType> getForceMonitorSlotTypes(BigBang bb) {
        EconomicSet<AnalysisType> forceMonitorTypes = EconomicSet.create();
        for (var entry : MultiThreadedMonitorSupport.FORCE_MONITOR_SLOT_TYPES.entrySet()) {
            Optional<AnalysisType> optionalType = bb.getMetaAccess().optionalLookupJavaType(entry.getKey());
            if (optionalType.isPresent()) {
                AnalysisType aType = optionalType.get();
                forceMonitorTypes.add(aType);
                if (entry.getValue()) {
                    forceMonitorTypes.addAll(aType.getAllSubtypes());
                }
            }
        }
        return forceMonitorTypes;
    }

    /** Process the types that the analysis found as needing synchronization. */
    protected void processedSynchronizedTypes(BigBang bb, HostedUniverse hUniverse, EconomicSet<AnalysisType> immutableTypes) {
        for (AnalysisType type : bb.getAllSynchronizedTypes()) {
            maybeSetMonitorField(hUniverse, immutableTypes, type);
        }
    }

    /**
     * Only concrete instance classes receive optional monitor fields. Abstract classes cannot be
     * instantiated, and their synthetic monitor fields are not inherited by subclasses.
     *
     * Monitor fields on arrays would enlarge the array header too much.
     *
     * Also, never burden @ValueBased classes with a monitor field, which in particular reduces
     * sizes of boxed primitives. Using monitors with those types is discouraged (instances are
     * often cached) and not guaranteed to work in the future.
     *
     * Types that must be immutable cannot have a monitor field.
     */
    protected static void maybeSetMonitorField(HostedUniverse hUniverse, EconomicSet<AnalysisType> immutableTypes, AnalysisType type) {
        if (type.isInstanceClass() && !type.isAbstract() && !immutableTypes.contains(type) && !GuestAnnotationAccess.isAnnotationPresent(type, ValueBased.class)) {
            setMonitorField(hUniverse, type);
        }
    }

    private static void setMonitorField(HostedUniverse hUniverse, AnalysisType type) {
        HostedType hostedType = hUniverse.lookup(type);
        if (hostedType instanceof HostedInstanceClass hostedInstanceClass) {
            hostedInstanceClass.setNeedMonitorField();
        } else {
            assert false : "Attempted setting of monitor field on a non-instance-class type.";
        }
    }

    public NativeImageCodeCacheFactory newCodeCacheFactory() {
        return new DefaultNativeImageCodeCacheFactory();
    }

    public ObjectFileFactory newObjectFileFactory() {
        return new DefaultObjectFileFactory();
    }

    public HeapBreakdownProvider createHeapBreakdownProvider() {
        return new HeapBreakdownProvider();
    }

    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
    private static final class DefaultNativeImageCodeCacheFactory extends NativeImageCodeCacheFactory {
        @Override
        public NativeImageCodeCache newCodeCache(CompileQueue compileQueue, NativeImageHeap heap, Platform targetPlatform, Path tempDir) {
            return new LIRNativeImageCodeCache(compileQueue.getCompilationResults(), heap);
        }
    }

    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
    private static final class DefaultObjectFileFactory implements ObjectFileFactory {
        @Override
        public ObjectFile newObjectFile(int pageSize, Path tempDir, BigBang bb) {
            return ObjectFile.getNativeObjectFile(pageSize);
        }
    }
}
