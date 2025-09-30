/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;

import com.oracle.svm.hosted.DynamicAccessDetectionSupport;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceNode;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceWithExceptionNode;
import org.graalvm.collections.EconomicSet;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.DynamicAccessDetectionFeature;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This phase detects usages of dynamic access calls that might require metadata in reached parts of
 * the project. It does so by analyzing the specified class path entries, modules or packages, and
 * identifying relevant accesses. The phase then outputs and serializes the detected usages to the
 * image-build output. It is an optional phase that happens before
 * {@link com.oracle.graal.pointsto.results.StrengthenGraphs} by using the
 * {@link com.oracle.svm.core.SubstrateOptions#TrackDynamicAccess} option and providing the desired
 * source entries.
 */
public class DynamicAccessDetectionPhase extends BasePhase<CoreProviders> {
    private final DynamicAccessDetectionFeature dynamicAccessDetectionFeature;
    private final DynamicAccessDetectionSupport dynamicAccessDetectionSupport;

    public DynamicAccessDetectionPhase() {
        dynamicAccessDetectionFeature = DynamicAccessDetectionFeature.instance();
        dynamicAccessDetectionSupport = DynamicAccessDetectionSupport.instance();
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        AnalysisType callerClass = (AnalysisType) graph.method().getDeclaringClass();
        String sourceEntry = getSourceEntry(callerClass);
        if (sourceEntry == null) {
            return;
        }

        for (Node node : graph.getNodes()) {
            ResolvedJavaMethod targetMethod = null;
            NodeSourcePosition invokeLocation = null;
            if (node instanceof MethodCallTargetNode callTarget) {
                targetMethod = callTarget.targetMethod();
                invokeLocation = callTarget.getNodeSourcePosition();
            } else if (node instanceof DynamicNewInstanceNode unsafeNode && unsafeNode.getNodeSourcePosition() != null && unsafeNode.isOriginUnsafeAllocateInstance()) {
                /*
                 * Match only DynamicNewInstanceNode intrinsified from Unsafe#allocateInstance. The
                 * NodeSourcePosition of the node preserves both the intrinsified method
                 * (getMethod()) and its original caller (getCaller()).
                 */
                targetMethod = unsafeNode.getNodeSourcePosition().getMethod();
                invokeLocation = unsafeNode.getNodeSourcePosition().getCaller();
            } else if (node instanceof DynamicNewInstanceWithExceptionNode unsafeNodeEx && unsafeNodeEx.getNodeSourcePosition() != null && unsafeNodeEx.isOriginUnsafeAllocateInstance()) {
                /*
                 * Match only DynamicNewInstanceWithExceptionNode intrinsified from
                 * Unsafe#allocateInstance. The NodeSourcePosition of the node preserves both the
                 * intrinsified method (getMethod()) and its original caller (getCaller()).
                 */
                targetMethod = unsafeNodeEx.getNodeSourcePosition().getMethod();
                invokeLocation = unsafeNodeEx.getNodeSourcePosition().getCaller();
            }
            registerDynamicAccessCall(invokeLocation, targetMethod, sourceEntry);
        }
    }

    private void registerDynamicAccessCall(NodeSourcePosition invokeLocation, ResolvedJavaMethod targetMethod, String sourceEntry) {
        if (invokeLocation != null && !dynamicAccessDetectionFeature.containsFoldEntry(invokeLocation.getBCI(), invokeLocation.getMethod())) {
            DynamicAccessDetectionSupport.MethodInfo methodInfo = dynamicAccessDetectionSupport.lookupDynamicAccessMethod(targetMethod);
            if (methodInfo != null) {
                String callLocation = invokeLocation.getMethod().asStackTraceElement(invokeLocation.getBCI()).toString();
                dynamicAccessDetectionFeature.addCall(sourceEntry, methodInfo.accessKind(), methodInfo.signature(), callLocation);
            }
        }
    }

    /**
     * Returns the class path entry, module or package name of the caller class if it is included in
     * the value specified by the option, otherwise returns null.
     */
    private static String getSourceEntry(AnalysisType callerClass) {
        EconomicSet<String> sourceEntries = DynamicAccessDetectionFeature.instance().getSourceEntries();
        try {
            CodeSource entryPathSource = callerClass.getJavaClass().getProtectionDomain().getCodeSource();
            if (entryPathSource != null) {
                URL entryPathURL = entryPathSource.getLocation();
                if (entryPathURL != null) {
                    String classPathEntry = entryPathURL.toURI().getPath();
                    if (classPathEntry.endsWith(File.separator)) {
                        classPathEntry = classPathEntry.substring(0, classPathEntry.length() - 1);
                    }
                    if (sourceEntries.contains(classPathEntry)) {
                        return classPathEntry;
                    }
                }
            }

            String moduleName = callerClass.getJavaClass().getModule().getName();
            if (moduleName != null && sourceEntries.contains(moduleName)) {
                return moduleName;
            }

            String packageName = callerClass.getJavaClass().getPackageName();
            if (sourceEntries.contains(packageName)) {
                return packageName;
            }
            return null;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
