/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccessExtensionProvider;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.results.StrengthenGraphs;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.MissingRegistrationSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.config.ObjectLayout.IdentityHashMode;
import com.oracle.svm.core.graal.code.SubstrateMetaAccessExtensionProvider;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.monitor.MultiThreadedMonitorSupport;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.flow.SVMMethodTypeFlowBuilder;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.config.DynamicHubLayout;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.config.HybridLayoutSupport;
import com.oracle.svm.hosted.image.LIRNativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageCodeCacheFactory;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.ObjectFileFactory;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.options.OptionValues;
import jdk.internal.ValueBased;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

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

    public static void setDefaultIfEmpty() {
        setInstanceIfEmpty(new HostedConfiguration());
        if (!ImageSingletons.contains(CompressEncoding.class)) {
            CompressEncoding compressEncoding = new CompressEncoding(SubstrateOptions.SpawnIsolates.getValue() ? 1 : 0, 0);
            ImageSingletons.add(CompressEncoding.class, compressEncoding);

            ObjectLayout objectLayout = createObjectLayout(IdentityHashMode.TYPE_SPECIFIC);
            ImageSingletons.add(ObjectLayout.class, objectLayout);

            ImageSingletons.add(HybridLayoutSupport.class, new HybridLayoutSupport());
        }
    }

    public static ObjectLayout createObjectLayout(IdentityHashMode identityHashMode) {
        return createObjectLayout(JavaKind.Object, identityHashMode);
    }

    /**
     * Defines the serial/epsilon GC object layout. The monitor slot and the identity hash code
     * fields are appended to instance objects (unless there is an otherwise unused gap in the
     * object that can be used).
     *
     * The layout of instance objects is:
     * <ul>
     * <li>64 bit hub reference</li>
     * <li>instance fields (references, primitives)</li>
     * <li>64 bit object monitor reference (if needed)</li>
     * <li>32 bit identity hashcode (if needed)</li>
     * </ul>
     *
     * The layout of array objects is:
     * <ul>
     * <li>64 bit hub reference</li>
     * <li>32 bit identity hashcode</li>
     * <li>32 bit array length</li>
     * <li>array elements (length * elementSize)</li>
     * </ul>
     */
    public static ObjectLayout createObjectLayout(JavaKind referenceKind, IdentityHashMode identityHashMode) {
        SubstrateTargetDescription target = ConfigurationValues.getTarget();
        int referenceSize = target.arch.getPlatformKind(referenceKind).getSizeInBytes();
        int intSize = target.arch.getPlatformKind(JavaKind.Int).getSizeInBytes();
        int objectAlignment = 8;

        int hubOffset = 0;
        int headerSize = hubOffset + referenceSize;

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

        return new ObjectLayout(target, referenceSize, objectAlignment, hubOffset, firstFieldOffset, arrayLengthOffset, arrayBaseOffset, headerIdentityHashOffset, identityHashMode);
    }

    public static void initializeDynamicHubLayout(HostedMetaAccess hMeta) {
        ImageSingletons.add(DynamicHubLayout.class, createDynamicHubLayout(hMeta));
    }

    private static DynamicHubLayout createDynamicHubLayout(HostedMetaAccess hMetaAccess) {
        var dynamicHubType = hMetaAccess.lookupJavaType(Class.class);

        ObjectLayout layout = ConfigurationValues.getObjectLayout();
        var vtableField = hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "vtable"));
        JavaKind vTableSlotStorageKind = vtableField.getType().getComponentType().getStorageKind();
        int vTableSlotSize = layout.sizeInBytes(vTableSlotStorageKind);

        var closedTypeWorldTypeCheckSlotsField = hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "closedTypeWorldTypeCheckSlots"));
        int closedTypeWorldTypeCheckSlotsOffset;
        int closedTypeWorldTypeCheckSlotSize;

        Set<HostedField> ignoredFields;
        if (SubstrateOptions.closedTypeWorld()) {
            closedTypeWorldTypeCheckSlotsOffset = layout.getArrayLengthOffset() + layout.sizeInBytes(JavaKind.Int);
            closedTypeWorldTypeCheckSlotSize = layout.sizeInBytes(closedTypeWorldTypeCheckSlotsField.getType().getComponentType().getStorageKind());

            ignoredFields = Set.of(
                            closedTypeWorldTypeCheckSlotsField,
                            vtableField,
                            hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "typeIDDepth")),
                            hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "numClassTypes")),
                            hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "numInterfaceTypes")),
                            hMetaAccess.lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "openTypeWorldTypeCheckSlots")));
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

    /**
     * The hybrid array field and the type fields of the dynamic hub are directly inlined to the
     * object to remove a level of indirection.
     */
    public static boolean isInlinedField(HostedField field) {
        return HybridLayout.isHybridField(field) || DynamicHubLayout.singleton().isInlinedField(field);
    }

    public SVMHost createHostVM(OptionValues options, ImageClassLoader loader, ClassInitializationSupport classInitializationSupport, AnnotationSubstitutionProcessor annotationSubstitutions,
                    MissingRegistrationSupport missingRegistrationSupport) {
        return new SVMHost(options, loader, classInitializationSupport, annotationSubstitutions, missingRegistrationSupport);
    }

    public CompileQueue createCompileQueue(DebugContext debug, FeatureHandler featureHandler, HostedUniverse hostedUniverse, RuntimeConfiguration runtimeConfiguration, boolean deoptimizeAll) {
        return new CompileQueue(debug, featureHandler, hostedUniverse, runtimeConfiguration, deoptimizeAll, Collections.emptyList());
    }

    public MethodTypeFlowBuilder createMethodTypeFlowBuilder(PointsToAnalysis bb, PointsToAnalysisMethod method, MethodFlowsGraph flowsGraph, MethodFlowsGraph.GraphKind graphKind) {
        return new SVMMethodTypeFlowBuilder(bb, method, flowsGraph, graphKind);
    }

    public MetaAccessExtensionProvider createAnalysisMetaAccessExtensionProvider() {
        return new AnalysisMetaAccessExtensionProvider();
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
                } else if (HybridLayout.isHybridField(hField)) {
                    /*
                     * The array field of a hybrid is not materialized, so it needs no field offset.
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

    public void collectMonitorFieldInfo(BigBang bb, HostedUniverse hUniverse, Set<AnalysisType> immutableTypes) {
        /* First set the monitor field for types that always need it. */
        for (AnalysisType type : getForceMonitorSlotTypes(bb)) {
            assert !immutableTypes.contains(type);
            setMonitorField(hUniverse, type);
        }

        /* Then decide what other types may need it. */
        processedSynchronizedTypes(bb, hUniverse, immutableTypes);
    }

    private static Set<AnalysisType> getForceMonitorSlotTypes(BigBang bb) {
        Set<AnalysisType> forceMonitorTypes = new HashSet<>();
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
    protected void processedSynchronizedTypes(BigBang bb, HostedUniverse hUniverse, Set<AnalysisType> immutableTypes) {
        for (AnalysisType type : bb.getAllSynchronizedTypes()) {
            maybeSetMonitorField(hUniverse, immutableTypes, type);
        }
    }

    /**
     * Monitor fields on arrays would enlarge the array header too much.
     *
     * Also, never burden @ValueBased classes with a monitor field, which in particular reduces
     * sizes of boxed primitives. Using monitors with those types is discouraged (instances are
     * often cached) and not guaranteed to work in the future.
     *
     * Types that must be immutable cannot have a monitor field.
     */
    protected static void maybeSetMonitorField(HostedUniverse hUniverse, Set<AnalysisType> immutableTypes, AnalysisType type) {
        if (!type.isArray() && !immutableTypes.contains(type) && !type.isAnnotationPresent(ValueBased.class)) {
            setMonitorField(hUniverse, type);
        }
    }

    private static void setMonitorField(HostedUniverse hUniverse, AnalysisType type) {
        final HostedInstanceClass hostedInstanceClass = (HostedInstanceClass) hUniverse.lookup(type);
        hostedInstanceClass.setNeedMonitorField();
    }

    public NativeImageCodeCacheFactory newCodeCacheFactory() {
        return new NativeImageCodeCacheFactory() {
            @Override
            public NativeImageCodeCache newCodeCache(CompileQueue compileQueue, NativeImageHeap heap, Platform targetPlatform, Path tempDir) {
                return new LIRNativeImageCodeCache(compileQueue.getCompilationResults(), compileQueue.getBaseLayerMethods(), heap);
            }
        };
    }

    public ObjectFileFactory newObjectFileFactory() {
        return new ObjectFileFactory() {
            @Override
            public ObjectFile newObjectFile(int pageSize, Path tempDir, BigBang bb) {
                return ObjectFile.getNativeObjectFile(pageSize);
            }
        };
    }
}
