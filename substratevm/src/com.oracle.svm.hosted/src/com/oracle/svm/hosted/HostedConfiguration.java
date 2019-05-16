/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

import com.oracle.svm.hosted.classinitialization.ConfigurableClassInitialization;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.results.StaticAnalysisResultsBuilder;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.hosted.classinitialization.ClassInitializationFeature;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.code.SharedRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedUniverse;

public class HostedConfiguration {

    private ClassInitializationSupport classInitializationSupport;

    public HostedConfiguration(ClassInitializationSupport classInitializationSupport) {
        this.classInitializationSupport = classInitializationSupport;
    }

    public static HostedConfiguration instance() {
        return ImageSingletons.lookup(HostedConfiguration.class);
    }

    static void setDefaultIfEmpty(FeatureImpl.AfterRegistrationAccessImpl access) {
        if (!ImageSingletons.contains(HostedConfiguration.class)) {
            ClassInitializationSupport classInitializationSupport = new ConfigurableClassInitialization(access.getMetaAccess(), access.getImageClassLoader());
            ImageSingletons.add(RuntimeClassInitializationSupport.class, classInitializationSupport);
            ImageSingletons.add(HostedConfiguration.class, new HostedConfiguration(classInitializationSupport));
            ClassInitializationFeature.processClassInitializationOptions(classInitializationSupport);

            CompressEncoding compressEncoding = new CompressEncoding(SubstrateOptions.SpawnIsolates.getValue() ? 1 : 0, 0);
            ImageSingletons.add(CompressEncoding.class, compressEncoding);

            ObjectLayout objectLayout = new ObjectLayout(ConfigurationValues.getTarget());
            ImageSingletons.add(ObjectLayout.class, objectLayout);
        }
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

    public boolean isUsingAOTProfiles() {
        return false;
    }

    public ClassInitializationSupport getClassInitializationSupport() {
        return classInitializationSupport;
    }
}
