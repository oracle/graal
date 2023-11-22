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
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
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
import com.oracle.graal.pointsto.results.AbstractAnalysisResultsBuilder;
import com.oracle.graal.pointsto.results.DefaultResultsBuilder;
import com.oracle.graal.pointsto.results.StaticAnalysisResultsBuilder;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.code.SubstrateMetaAccessExtensionProvider;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.monitor.MultiThreadedMonitorSupport;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.flow.SVMMethodTypeFlowBuilder;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.CompileQueue;
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
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.substitute.UnsafeAutomaticSubstitutionProcessor;

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

            ObjectLayout objectLayout = createObjectLayout();
            ImageSingletons.add(ObjectLayout.class, objectLayout);

            ImageSingletons.add(HybridLayoutSupport.class, new HybridLayoutSupport());
        }
    }

    public static ObjectLayout createObjectLayout() {
        return createObjectLayout(JavaKind.Object, false);
    }

    /**
     * Defines the layout of objects. Identity hash code fields can be optional if the object header
     * allows for it, in which case such a field is appended to individual objects after an identity
     * hash code has been assigned to it (unless there is an otherwise unused gap in the object that
     * can be used).
     *
     * The layout of instance objects is:
     * <ul>
     * <li>object header with hub reference</li>
     * <li>optional: identity hashcode (int)</li>
     * <li>instance fields (references, primitives)</li>
     * <li>if needed, object monitor (reference)</li>
     * </ul>
     *
     * The layout of array objects is:
     * <ul>
     * <li>object header with hub reference</li>
     * <li>optional: identity hashcode (int)</li>
     * <li>array length (int)</li>
     * <li>array elements (length * reference or primitive)</li>
     * </ul>
     */
    public static ObjectLayout createObjectLayout(JavaKind referenceKind, boolean disableOptionalIdentityHash) {
        SubstrateTargetDescription target = ConfigurationValues.getTarget();
        int referenceSize = target.arch.getPlatformKind(referenceKind).getSizeInBytes();
        int headerSize = referenceSize;
        int intSize = target.arch.getPlatformKind(JavaKind.Int).getSizeInBytes();
        int objectAlignment = 8;

        int headerOffset = 0;
        int identityHashCodeOffset;
        int firstFieldOffset;
        if (!disableOptionalIdentityHash && SubstrateOptions.SpawnIsolates.getValue() && headerSize + referenceSize <= objectAlignment) {
            /*
             * References are relative to the heap base, so we should be able to use fewer bits in
             * the object header to reference DynamicHubs which are located near the start of the
             * heap. This means we could be unable to fit forwarding references in those header bits
             * during GC, but every object is large enough to fit a separate forwarding reference
             * outside its header. Therefore, we can avoid reserving an identity hash code field for
             * every object during its allocation and use extra header bits to track if an
             * individual object was assigned an identity hash code after allocation.
             */
            identityHashCodeOffset = -1;
            firstFieldOffset = headerOffset + headerSize;
        } else { // need all object header bits except for lowest-order bits freed up by alignment
            identityHashCodeOffset = headerOffset + referenceSize;
            firstFieldOffset = identityHashCodeOffset + intSize;
        }
        int arrayLengthOffset = firstFieldOffset;
        int arrayBaseOffset = arrayLengthOffset + intSize;

        return new ObjectLayout(target, referenceSize, objectAlignment, headerOffset, firstFieldOffset, arrayLengthOffset, arrayBaseOffset, identityHashCodeOffset);
    }

    public SVMHost createHostVM(OptionValues options, ClassLoader classLoader, ClassInitializationSupport classInitializationSupport,
                    UnsafeAutomaticSubstitutionProcessor automaticSubstitutions, Platform platform) {
        return new SVMHost(options, classLoader, classInitializationSupport, automaticSubstitutions, platform);
    }

    public CompileQueue createCompileQueue(DebugContext debug, FeatureHandler featureHandler, HostedUniverse hostedUniverse, RuntimeConfiguration runtimeConfiguration, boolean deoptimizeAll,
                    SnippetReflectionProvider aSnippetReflection) {

        return new CompileQueue(debug, featureHandler, hostedUniverse, runtimeConfiguration, deoptimizeAll, aSnippetReflection);
    }

    public MethodTypeFlowBuilder createMethodTypeFlowBuilder(PointsToAnalysis bb, PointsToAnalysisMethod method, MethodFlowsGraph flowsGraph, MethodFlowsGraph.GraphKind graphKind) {
        return new SVMMethodTypeFlowBuilder(bb, method, flowsGraph, graphKind);
    }

    public void registerUsedElements(PointsToAnalysis bb, StructuredGraph graph, boolean registerEmbeddedRoots) {
        SVMMethodTypeFlowBuilder.registerUsedElements(bb, graph, registerEmbeddedRoots);
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
        for (ResolvedJavaField javaField : clazz.getWrapped().getInstanceFields(false)) {
            AnalysisField aField = (AnalysisField) javaField;
            HostedField hField = universe.lookup(aField);

            /* Because of @Alias fields, the field lookup might not be declared in our class. */
            if (hField.getDeclaringClass().equals(clazz)) {
                if (HybridLayout.isHybridField(hField)) {
                    /*
                     * The array or bitset field of a hybrid is not materialized, so it needs no
                     * field offset.
                     */
                    allFields.add(hField);
                } else if (hField.isAccessed()) {
                    rawFields.add(hField);
                    allFields.add(hField);
                }
            }
        }
    }

    public AbstractAnalysisResultsBuilder createStaticAnalysisResultsBuilder(Inflation bb, HostedUniverse universe) {
        if (bb instanceof PointsToAnalysis) {
            PointsToAnalysis pta = (PointsToAnalysis) bb;
            if (SubstrateOptions.parseOnce()) {
                return new SubstrateStrengthenGraphs(pta, universe);
            } else {
                return new StaticAnalysisResultsBuilder(pta, universe);
            }
        } else {
            return new DefaultResultsBuilder(bb, universe);
        }
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
        for (Class<?> forceMonitorType : MultiThreadedMonitorSupport.FORCE_MONITOR_SLOT_TYPES) {
            Optional<AnalysisType> aType = bb.getMetaAccess().optionalLookupJavaType(forceMonitorType);
            aType.ifPresent(forceMonitorTypes::add);
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
                return new LIRNativeImageCodeCache(compileQueue.getCompilationResults(), heap);
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
