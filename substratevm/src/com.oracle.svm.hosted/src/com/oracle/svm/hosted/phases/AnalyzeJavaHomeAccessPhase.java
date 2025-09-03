/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import java.util.List;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;

import com.oracle.svm.hosted.AnalyzeJavaHomeAccessFeature;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This phase detects usages of <code>System.getProperty("java.home")</code> and reports an
 * image-build time recommendation to set java.home when running the binary if a usage has been
 * detected. It is an optional phase that happens after
 * {@link com.oracle.graal.pointsto.results.StrengthenGraphs} by using the
 * {@link com.oracle.svm.core.SubstrateOptions#TrackJavaHomeAccess} option. Additionally, using the
 * {@link com.oracle.svm.core.SubstrateOptions#TrackJavaHomeAccessDetailed} option, the phase can
 * print the locations of all the previously found <code>System.getProperty("java.home")</code>
 * calls.
 */
public class AnalyzeJavaHomeAccessPhase extends BasePhase<CoreProviders> {
    public static final String GET_PROPERTY_FUNCTION_NAME = "getProperty";
    public static final String JAVA_HOME = "java.home";
    private final boolean trackJavaHomeLocations;
    private final ResolvedJavaType typeJavaLangSystem;
    private final AnalyzeJavaHomeAccessFeature singleton = AnalyzeJavaHomeAccessFeature.instance();

    public AnalyzeJavaHomeAccessPhase(boolean trackJavaHomeLocations, AnalysisMetaAccess metaAccess) {
        this.trackJavaHomeLocations = trackJavaHomeLocations;
        this.typeJavaLangSystem = metaAccess.lookupJavaType(System.class);
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        List<MethodCallTargetNode> callTargetNodes = graph.getNodes(MethodCallTargetNode.TYPE).snapshot();
        for (MethodCallTargetNode callTarget : callTargetNodes) {
            String argValue = getSystemGetPropertyConstantArgument(callTarget, context);
            if (argValue != null && argValue.equals(JAVA_HOME)) {
                singleton.setJavaHomeUsed();
                if (trackJavaHomeLocations) {
                    NodeSourcePosition nspToShow = callTarget.getNodeSourcePosition();
                    if (nspToShow != null) {
                        int bci = nspToShow.getBCI();
                        singleton.addJavaHomeUsageLocation(nspToShow.getMethod().asStackTraceElement(bci).toString());
                    }
                }
            }
        }
    }

    private String getSystemGetPropertyConstantArgument(MethodCallTargetNode callTarget, CoreProviders context) {
        ResolvedJavaMethod targetMethod = callTarget.targetMethod();
        ResolvedJavaType declaringClass = targetMethod.getDeclaringClass();
        if (declaringClass.equals(this.typeJavaLangSystem) && targetMethod.getName().equals(GET_PROPERTY_FUNCTION_NAME)) {
            ValueNode arg = callTarget.arguments().first();
            if (arg.isJavaConstant()) {
                assert arg.asJavaConstant() != null : "Preventing warnings. Already checked.";
                if (arg.asJavaConstant().getJavaKind().isObject()) {
                    return context.getSnippetReflection().asObject(String.class, arg.asJavaConstant());
                }
            }
        }
        return null;
    }
}
