/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis;

import java.util.List;

import com.oracle.graal.pointsto.ClassInclusionPolicy;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.graal.reachability.ReachabilityAnalysisEngine;
import com.oracle.graal.reachability.ReachabilityMethodProcessingHandler;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.CustomTypeFieldHandler;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public class NativeImageReachabilityAnalysisEngine extends ReachabilityAnalysisEngine implements Inflation {

    private final AnnotationSubstitutionProcessor annotationSubstitutionProcessor;
    private final DynamicHubInitializer dynamicHubInitializer;
    private final CustomTypeFieldHandler unknownFieldHandler;

    @SuppressWarnings("this-escape")
    public NativeImageReachabilityAnalysisEngine(OptionValues options, AnalysisUniverse universe, AnalysisMetaAccess metaAccess, SnippetReflectionProvider snippetReflectionProvider,
                    ConstantReflectionProvider constantReflectionProvider, WordTypes wordTypes, AnnotationSubstitutionProcessor annotationSubstitutionProcessor, DebugContext debugContext,
                    TimerCollection timerCollection, ReachabilityMethodProcessingHandler reachabilityMethodProcessingHandler, ClassInclusionPolicy classInclusionPolicy) {
        super(options, universe, universe.hostVM(), metaAccess, snippetReflectionProvider, constantReflectionProvider, wordTypes, new SubstrateUnsupportedFeatures(), debugContext, timerCollection,
                        reachabilityMethodProcessingHandler, classInclusionPolicy);
        this.annotationSubstitutionProcessor = annotationSubstitutionProcessor;
        this.dynamicHubInitializer = new DynamicHubInitializer(this);
        this.unknownFieldHandler = new CustomTypeFieldHandler(this, metaAccess) {
            @Override
            public void injectFieldTypes(AnalysisField aField, List<AnalysisType> declaredTypes, boolean canBeNull) {
                assert aField.getStorageKind().isObject();
                aField.registerAsAccessed("@UnknownObjectField annotated field.");
                for (AnalysisType declaredType : declaredTypes) {
                    declaredType.registerAsReachable("injected field types for unknown annotated field " + aField.format("%H.%n"));
                }
            }
        };
    }

    @Override
    public AnnotationSubstitutionProcessor getAnnotationSubstitutionProcessor() {
        return annotationSubstitutionProcessor;
    }

    @Override
    public void onFieldAccessed(AnalysisField field) {
        unknownFieldHandler.handleField(field);
    }

    @Override
    public void onTypeReachable(AnalysisType type) {
        postTask(d -> type.getInitializeMetaDataTask().ensureDone());
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        unknownFieldHandler.cleanupAfterAnalysis();
    }

    @Override
    public SVMHost getHostVM() {
        return (SVMHost) hostVM;
    }

    @Override
    public void checkUserLimitations() {
        UserLimitationsChecker.check(this);
    }

    @Override
    public void initializeMetaData(AnalysisType type) {
        dynamicHubInitializer.initializeMetaData(universe.getHeapScanner(), type);
    }
}
