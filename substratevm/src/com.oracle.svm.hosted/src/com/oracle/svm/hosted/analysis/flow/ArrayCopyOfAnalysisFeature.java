/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis.flow;

import static com.oracle.svm.shared.util.ReflectionUtil.lookupClass;
import static com.oracle.svm.shared.util.ReflectionUtil.lookupField;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringJoiner;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisField;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;

import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeList;

/** Verifies the array-copy type-flow precision required when building libgraal. */
@AutomaticallyRegisteredFeature
@Platforms(InternalPlatform.NATIVE_ONLY.class)
final class ArrayCopyOfAnalysisFeature implements InternalFeature {
    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        FeatureImpl.AfterAnalysisAccessImpl accessImpl = (FeatureImpl.AfterAnalysisAccessImpl) access;
        ClassLoader libGraalLoader = GuestAccess.get().getSnippetReflection().asObject(ClassLoader.class, accessImpl.getImageClassLoader().classLoaderSupport.getLibGraalLoader());
        if (libGraalLoader != null && accessImpl.getBigBang() instanceof PointsToAnalysis) {
            checkFieldTypes(accessImpl, libGraalLoader);
        }
    }

    /**
     * Checks that precise array-copy modeling proves {@code ArrayList.elementData} is always
     * {@code Object[]} and that {@code Graph.nodes} and {@code NodeList.nodes} are always
     * {@code Node[]} when building libgraal. The check is disabled for the economy compiler
     * configuration because it disables the array-copy plugins.
     */
    private static void checkFieldTypes(FeatureImpl.AfterAnalysisAccessImpl access, ClassLoader classLoader) {
        BigBang bb = access.getBigBang();
        assert bb instanceof PointsToAnalysis : "This check is only meaningful when PTA is enabled, as RTA does not track the flow of types.";
        if (!SubstrateOptions.useEconomyCompilerConfig(bb.getOptions())) {
            assertFieldType(bb, lookupField(ArrayList.class, "elementData"), Object[].class);
            Class<?> nodeArrayClass = lookupClass(false, Node[].class.getName(), classLoader);
            assertFieldType(bb, lookupField(lookupClass(false, Graph.class.getName(), classLoader), "nodes"), nodeArrayClass);
            assertFieldType(bb, lookupField(lookupClass(false, NodeList.class.getName(), classLoader), "nodes"), nodeArrayClass);
            Class<?> economicMapImpl = lookupClass("org.graalvm.collections.EconomicMapImpl");
            assertFieldType(bb, lookupField(economicMapImpl, "entries"), Object[].class);
        }
    }

    /** Verifies that {@code field} has exactly the expected type after analysis. */
    private static void assertFieldType(BigBang bb, Field field, Class<?> expectedClass) {
        PointsToAnalysisField analysisField = (PointsToAnalysisField) bb.getMetaAccess().lookupJavaField(field);
        TypeState typeState = analysisField.getSinkFlow().getState();
        Iterable<AnalysisType> types = typeState.types(bb);
        Iterator<AnalysisType> typesIterator = types.iterator();
        if (typeState.typesCount() != 1 || !typesIterator.hasNext() || typesIterator.next().getJavaClass() != expectedClass) {
            StringJoiner joiner = new StringJoiner(", ");
            types.forEach(type -> joiner.add(type.toJavaName(true)));
            throw VMError.shouldNotReachHere("Failed checking types for %s%nExpected types: %s%nActual types: %s%n".formatted(analysisField.format("%H.%n"), expectedClass.getTypeName(), joiner));
        }
    }
}
