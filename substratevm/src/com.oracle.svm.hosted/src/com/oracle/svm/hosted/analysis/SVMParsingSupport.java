/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Function;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.phases.InlineBeforeAnalysisPolicyUtils;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * {@link com.oracle.graal.pointsto.api.HostVM} methods which may be overwritten by substratevm
 * features.
 */
public interface SVMParsingSupport {

    Object parseGraph(BigBang bb, DebugContext debug, AnalysisMethod method);

    GraphBuilderConfiguration updateGraphBuilderConfiguration(GraphBuilderConfiguration config, AnalysisMethod method);

    boolean validateGraph(PointsToAnalysis bb, StructuredGraph graph);

    void afterParsingHook(AnalysisMethod method, StructuredGraph graph);

    boolean allowAssumptions(AnalysisMethod method);

    boolean recordInlinedMethods(AnalysisMethod method);

    HostedProviders getHostedProviders(MultiMethod.MultiMethodKey key);

    void initializeInlineBeforeAnalysisPolicy(SVMHost svmHost, InlineBeforeAnalysisPolicyUtils inliningUtils);

    InlineBeforeAnalysisPolicy inlineBeforeAnalysisPolicy(MultiMethod.MultiMethodKey multiMethodKey, InlineBeforeAnalysisPolicy defaultPolicy);

    Function<AnalysisType, ResolvedJavaType> getStrengthenGraphsToTargetFunction(MultiMethod.MultiMethodKey key);
}
