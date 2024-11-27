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

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.AnalyzeReflectionUsageSupport;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnalyzeReflectionUsagePhase extends BasePhase<CoreProviders> {
    private static final Map<String, Set<String>> reflectMethodNames = Map.of(
            Class.class.getTypeName(), Set.of(
                    "forName",
                    "getClasses",
                    "getDeclaredClasses",
                    "getConstructor",
                    "getConstructors",
                    "getDeclaredConstructor",
                    "getDeclaredConstructors",
                    "getField",
                    "getFields",
                    "getDeclaredField",
                    "getDeclaredFields",
                    "getMethod",
                    "getMethods",
                    "getDeclaredMethod",
                    "getDeclaredMethods",
                    "getNestMembers",
                    "getPermittedSubclasses",
                    "getRecordComponents",
                    "getSigners",
                    "arrayType",
                    "newInstance"),
            Method.class.getTypeName(), Set.of("invoke"),
            Constructor.class.getTypeName(), Set.of("newInstance"),
            Proxy.class.getTypeName(), Set.of("getProxyClass", "newProxyInstance"),
            "java.lang.reflect.ReflectAccess", Set.of("newInstance"),
            "jdk.internal.access.JavaLangAccess", Set.of("getDeclaredPublicMethods"),
            "sun.misc.Unsafe", Set.of("allocateInstance"));

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        List<MethodCallTargetNode> callTargetNodes = graph.getNodes(MethodCallTargetNode.TYPE).snapshot();
        for (MethodCallTargetNode callTarget : callTargetNodes) {
            String reflectiveMethodName = getReflectiveMethod(graph, callTarget);
            if (reflectiveMethodName != null) {
                NodeSourcePosition nspToShow = callTarget.getNodeSourcePosition();
                if (nspToShow != null) {
                    int bci = nspToShow.getBCI();
                    AnalyzeReflectionUsageSupport.instance().addReflectiveCall(reflectiveMethodName, nspToShow.getMethod().asStackTraceElement(bci).toString());
                }
            }
        }
    }

    private String getReflectiveMethod(StructuredGraph graph, MethodCallTargetNode callTarget) {
        AnalysisType callerClass = (AnalysisType) graph.method().getDeclaringClass();
        if (!containedInJars(callerClass)) {
            return null;
        }
        String methodName = callTarget.targetMethod().getName();
        String declaringClass = callTarget.targetMethod().getDeclaringClass().toJavaName();
        if (reflectMethodNames.containsKey(declaringClass)) {
            if (reflectMethodNames.get(declaringClass).contains(methodName)) {
                return methodName;
            }
        }
        return null;
    }

    private boolean containedInJars(AnalysisType callerClass) {
        try {
            CodeSource jarPathSource = callerClass.getJavaClass().getProtectionDomain().getCodeSource();
            if (jarPathSource == null) {
                return false;
            }

            URL jarPathURL = jarPathSource.getLocation();
            if (jarPathURL == null) {
                return false;
            }

            return AnalyzeReflectionUsageSupport.instance().getJarPaths().contains(jarPathURL.toURI().getPath());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
