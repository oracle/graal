/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.results.StaticAnalysisResultsBuilder;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.monitor.MultiThreadedMonitorSupport;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.code.SharedRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.config.HybridLayoutSupport;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.vm.ci.meta.JavaKind;

public class HostedConfiguration {

    public HostedConfiguration() {
    }

    public static HostedConfiguration instance() {
        return ImageSingletons.lookup(HostedConfiguration.class);
    }

    static void setDefaultIfEmpty() {
        if (!ImageSingletons.contains(HostedConfiguration.class)) {
            ImageSingletons.add(HostedConfiguration.class, new HostedConfiguration());

            CompressEncoding compressEncoding = new CompressEncoding(SubstrateOptions.SpawnIsolates.getValue() ? 1 : 0, 0);
            ImageSingletons.add(CompressEncoding.class, compressEncoding);

            ObjectLayout objectLayout = createObjectLayout();
            ImageSingletons.add(ObjectLayout.class, objectLayout);

            ImageSingletons.add(HybridLayoutSupport.class, new HybridLayoutSupport());
        }
    }

    public static ObjectLayout createObjectLayout() {
        return createObjectLayout(0, JavaKind.Object);
    }

    /**
     * Defines the layout of objects.
     *
     * The layout of instance objects is:
     * <ul>
     * <li>hub (reference)</li>
     * <li>instance fields (references, primitives)</li>
     * <li>optional: identity hashcode (int)</li>
     * </ul>
     * The hashcode is appended after instance fields and is only present if the identity hashcode
     * is used for that type.
     *
     * The layout of array objects is:
     * <ul>
     * <li>hub (reference)</li>
     * <li>array length (int)</li>
     * <li>identity hashcode (int)</li>
     * <li>array elements (length * reference or primitive)</li>
     * </ul>
     * The hashcode is always present in arrays. Note that on 64-bit targets it does not impose any
     * size overhead for arrays with 64-bit aligned elements (e.g. arrays of objects).
     */
    public static ObjectLayout createObjectLayout(int hubOffset, JavaKind referenceKind) {
        SubstrateTargetDescription target = ConfigurationValues.getTarget();
        int referenceSize = target.arch.getPlatformKind(referenceKind).getSizeInBytes();
        int objectAlignment = target.wordSize;
        int firstFieldOffset = hubOffset + referenceSize;
        int arrayLengthOffset = hubOffset + referenceSize;
        int arrayIdentityHashCodeOffset = arrayLengthOffset + target.arch.getPlatformKind(JavaKind.Int).getSizeInBytes();
        int arrayBaseOffset = arrayIdentityHashCodeOffset + target.arch.getPlatformKind(JavaKind.Int).getSizeInBytes();
        boolean useExplicitIdentityHashCodeField = true;
        int instanceIdentityHashCodeOffset = -1; // depends on the hub

        ObjectLayout objectLayout = new ObjectLayout(target, referenceSize, objectAlignment, hubOffset, firstFieldOffset, arrayLengthOffset, arrayBaseOffset,
                        useExplicitIdentityHashCodeField, instanceIdentityHashCodeOffset, arrayIdentityHashCodeOffset);
        return objectLayout;
    }

    public CompileQueue createCompileQueue(DebugContext debug, FeatureHandler featureHandler, HostedUniverse hostedUniverse,
                    SharedRuntimeConfigurationBuilder runtime, boolean deoptimizeAll, SnippetReflectionProvider aSnippetReflection, ForkJoinPool executor) {

        return new CompileQueue(debug, featureHandler, hostedUniverse, runtime, deoptimizeAll, aSnippetReflection, executor);
    }

    public void findAllFieldsForLayout(HostedUniverse universe, @SuppressWarnings("unused") HostedMetaAccess metaAccess,
                    @SuppressWarnings("unused") Map<AnalysisField, HostedField> universeFields,
                    ArrayList<HostedField> rawFields,
                    ArrayList<HostedField> orderedFields, HostedInstanceClass clazz) {
        for (AnalysisField aField : clazz.getWrapped().getInstanceFields(false)) {
            HostedField hField = universe.lookup(aField);

            /* Because of @Alias fields, the field lookup might not be declared in our class. */
            if (hField.getDeclaringClass().equals(clazz)) {
                if (HybridLayout.isHybridField(hField)) {
                    /*
                     * The array or bitset field of a hybrid is not materialized, so it needs no
                     * field offset.
                     */
                    orderedFields.add(hField);
                } else if (hField.isAccessed()) {
                    rawFields.add(hField);
                }
            }
        }
    }

    public StaticAnalysisResultsBuilder createStaticAnalysisResultsBuilder(BigBang bigbang, HostedUniverse universe) {
        return new StaticAnalysisResultsBuilder(bigbang, universe);
    }

    public void collectMonitorFieldInfo(BigBang bb, HostedUniverse hUniverse, Set<AnalysisType> immutableTypes) {
        /* First set the monitor field for types that always need it. */
        getForceMonitorSlotTypes(bb).forEach(type -> setMonitorField(hUniverse, type));

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
        TypeState allSynchronizedTypeState = bb.getAllSynchronizedTypeState();
        for (AnalysisType type : allSynchronizedTypeState.types()) {
            maybeSetMonitorField(hUniverse, immutableTypes, type);
        }
    }

    /**
     * Monitor fields on arrays would increase the array header too much. Also, types that must be
     * immutable cannot have a monitor field.
     */
    protected static void maybeSetMonitorField(HostedUniverse hUniverse, Set<AnalysisType> immutableTypes, AnalysisType type) {
        if (!type.isArray() && !immutableTypes.contains(type)) {
            setMonitorField(hUniverse, type);
        }
    }

    private static void setMonitorField(HostedUniverse hUniverse, AnalysisType type) {
        final HostedInstanceClass hostedInstanceClass = (HostedInstanceClass) hUniverse.lookup(type);
        hostedInstanceClass.setNeedMonitorField();
    }

    public boolean isUsingAOTProfiles() {
        return false;
    }
}
