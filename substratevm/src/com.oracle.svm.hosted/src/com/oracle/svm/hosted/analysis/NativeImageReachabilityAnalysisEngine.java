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

import java.util.concurrent.ForkJoinPool;

import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.graal.reachability.ReachabilityAnalysisEngine;
import com.oracle.graal.reachability.ReachabilityMethodProcessingHandler;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;

public class NativeImageReachabilityAnalysisEngine extends ReachabilityAnalysisEngine implements Inflation {

    private final AnnotationSubstitutionProcessor annotationSubstitutionProcessor;
    private final DynamicHubInitializer dynamicHubInitializer;
    private final boolean strengthenGraalGraphs;
    private final CustomTypeFieldHandler unknownFieldHandler;

    public NativeImageReachabilityAnalysisEngine(OptionValues options, AnalysisUniverse universe, HostedProviders providers, AnnotationSubstitutionProcessor annotationSubstitutionProcessor,
                    ForkJoinPool executor,
                    Runnable heartbeatCallback, TimerCollection timerCollection, ReachabilityMethodProcessingHandler reachabilityMethodProcessingHandler) {
        super(options, universe, providers, universe.hostVM(), executor, heartbeatCallback, new SubstrateUnsupportedFeatures(), timerCollection, reachabilityMethodProcessingHandler);
        this.annotationSubstitutionProcessor = annotationSubstitutionProcessor;
        this.strengthenGraalGraphs = SubstrateOptions.parseOnce();
        this.dynamicHubInitializer = new DynamicHubInitializer(this);
        this.unknownFieldHandler = new CustomTypeFieldHandler(this, metaAccess) {
            @Override
            protected void injectFieldTypes(AnalysisField aField, AnalysisType... declaredTypes) {
                markFieldAccessed(aField, "@UnknownObjectField annotated field.");
                for (AnalysisType declaredType : declaredTypes) {
                    registerTypeAsReachable(declaredType, "injected field types for unknown annotated field " + aField.format("%H.%n"));
                }
            }
        };
    }

    @Override
    public boolean strengthenGraalGraphs() {
        return strengthenGraalGraphs;
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
    public void onTypeInitialized(AnalysisType type) {
        postTask(d -> initializeMetaData(type));
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        unknownFieldHandler.cleanupAfterAnalysis();
    }

    @Override
    public SubstrateReplacements getReplacements() {
        return (SubstrateReplacements) super.getReplacements();
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
