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

import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.hosted.HostedConfiguration;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;

import jdk.vm.ci.meta.ResolvedJavaType;

public class NativeImagePointsToAnalysis extends PointsToAnalysis implements Inflation {

    private final AnnotationSubstitutionProcessor annotationSubstitutionProcessor;
    private final DynamicHubInitializer dynamicHubInitializer;
    private final UnknownFieldHandler unknownFieldHandler;
    private final CallChecker callChecker;

    public NativeImagePointsToAnalysis(OptionValues options, AnalysisUniverse universe, HostedProviders providers, AnnotationSubstitutionProcessor annotationSubstitutionProcessor,
                    ForkJoinPool executor, Runnable heartbeatCallback, UnsupportedFeatures unsupportedFeatures) {
        super(options, universe, providers, universe.hostVM(), executor, heartbeatCallback, unsupportedFeatures, SubstrateOptions.parseOnce());
        this.annotationSubstitutionProcessor = annotationSubstitutionProcessor;

        dynamicHubInitializer = new DynamicHubInitializer(this);
        unknownFieldHandler = new PointsToUnknownFieldHandler(this, metaAccess);
        callChecker = new CallChecker();
    }

    @Override
    public boolean isCallAllowed(PointsToAnalysis bb, AnalysisMethod caller, AnalysisMethod target, NodeSourcePosition srcPosition) {
        return callChecker.isCallAllowed(bb, caller, target, srcPosition);
    }

    @Override
    public MethodTypeFlowBuilder createMethodTypeFlowBuilder(PointsToAnalysis bb, MethodTypeFlow methodFlow) {
        return HostedConfiguration.instance().createMethodTypeFlowBuilder(bb, methodFlow);
    }

    @Override
    public SVMHost getHostVM() {
        return (SVMHost) hostVM;
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        unknownFieldHandler.cleanupAfterAnalysis();
    }

    @Override
    public void checkUserLimitations() {
        super.checkUserLimitations();
        UserLimitationsChecker.check(this);
    }

    @Override
    public AnnotationSubstitutionProcessor getAnnotationSubstitutionProcessor() {
        return annotationSubstitutionProcessor;
    }

    @Override
    public void onFieldAccessed(AnalysisField field) {
        unknownFieldHandler.handleUnknownValueField(field);
    }

    @Override
    public void onTypeInitialized(AnalysisType type) {
        postTask(debug -> initializeMetaData(type));
    }

    public void initializeMetaData(AnalysisType type) {
        dynamicHubInitializer.initializeMetaData(universe.getHeapScanner(), type);
    }

    public static ResolvedJavaType toWrappedType(ResolvedJavaType type) {
        if (type instanceof AnalysisType) {
            return ((AnalysisType) type).getWrappedWithoutResolve();
        } else if (type instanceof HostedType) {
            return ((HostedType) type).getWrapped().getWrappedWithoutResolve();
        } else {
            return type;
        }
    }

    @Override
    public boolean trackConcreteAnalysisObjects(AnalysisType type) {
        /*
         * For classes marked as UnknownClass no context sensitive analysis is done, i.e., no
         * concrete objects are tracked.
         *
         * It is assumed that an object of type C can be of any type compatible with C. At the same
         * type fields of type C can be of any type compatible with their declared type.
         */

        return !SVMHost.isUnknownClass(type);
    }

    @Override
    public SubstrateReplacements getReplacements() {
        return (SubstrateReplacements) super.getReplacements();
    }
}
