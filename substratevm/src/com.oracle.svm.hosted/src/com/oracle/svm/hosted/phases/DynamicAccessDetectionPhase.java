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

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.DynamicAccessDetectionFeature;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.internal.loader.BuiltinClassLoader;
import jdk.internal.loader.Loader;
import sun.invoke.util.ValueConversions;
import sun.invoke.util.VerifyAccess;
import sun.security.x509.X500Name;
import sun.util.locale.provider.LocaleProviderAdapter;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.random.RandomGeneratorFactory;

/**
 * This phase detects usages of dynamic access calls that might require metadata in reached parts of
 * the project. It does so by analyzing the specified class or module path entries and identifying
 * relevant accesses. The phase then outputs and serializes the detected usages to the image-build
 * output. It is an optional phase that happens before
 * {@link com.oracle.graal.pointsto.results.StrengthenGraphs} by using the
 * {@link com.oracle.svm.hosted.DynamicAccessDetectionFeature.Options#TrackDynamicAccess} option and
 * providing the desired class or module path entry/s.
 */
public class DynamicAccessDetectionPhase extends BasePhase<CoreProviders> {
    public enum DynamicAccessKind {
        Reflection("reflection-calls.json"),
        Resource("resource-calls.json");

        public final String fileName;

        DynamicAccessKind(String fileName) {
            this.fileName = fileName;
        }
    }

    public record MethodInfo(DynamicAccessKind accessKind, String name) {
    }

    private static final Map<String, Set<String>> reflectionMethodNames = new HashMap<>();
    private static final Map<String, Set<String>> resourceMethodNames = new HashMap<>();

    private final DynamicAccessDetectionFeature dynamicAccessDetectionFeature;

    static {
        reflectionMethodNames.put(Class.class.getTypeName(), Set.of(
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
                        "newInstance"));
        reflectionMethodNames.put(Method.class.getTypeName(), Set.of("invoke"));
        reflectionMethodNames.put(MethodHandles.Lookup.class.getTypeName(), Set.of(
                        "findClass",
                        "findVirtual",
                        "findStatic",
                        "findConstructor",
                        "findSpecial",
                        "findGetter",
                        "findSetter",
                        "findStaticGetter",
                        "findStaticSetter",
                        "findVarHandle",
                        "findStaticVarHandle",
                        "unreflect",
                        "unreflectSpecial",
                        "unreflectConstructor",
                        "unreflectGetter",
                        "unreflectSetter",
                        "unreflectVarHandle"));
        reflectionMethodNames.put(ClassLoader.class.getTypeName(), Set.of(
                        "loadClass",
                        "findBootstrapClassOrNull",
                        "findLoadedClass",
                        "findSystemClass"));
        reflectionMethodNames.put(URLClassLoader.class.getTypeName(), Set.of("loadClass"));
        reflectionMethodNames.put(Array.class.getTypeName(), Set.of("newInstance"));
        reflectionMethodNames.put(Constructor.class.getTypeName(), Set.of("newInstance"));
        reflectionMethodNames.put(VerifyAccess.class.getTypeName(), Set.of("isTypeVisible"));
        reflectionMethodNames.put(LocaleProviderAdapter.class.getTypeName(), Set.of("forType"));
        reflectionMethodNames.put(ValueConversions.class.getTypeName(), Set.of("boxExact"));
        reflectionMethodNames.put(ConstantBootstraps.class.getTypeName(), Set.of(
                        "getStaticFinal",
                        "staticFieldVarHandle",
                        "fieldVarHandle"));
        reflectionMethodNames.put(VarHandle.VarHandleDesc.class.getTypeName(), Set.of("resolveConstantDesc"));
        reflectionMethodNames.put(RandomGeneratorFactory.class.getTypeName(), Set.of("create"));
        reflectionMethodNames.put(X500Name.class.getTypeName(), Set.of("asX500Principal"));
        reflectionMethodNames.put(MethodHandleProxies.class.getTypeName(), Set.of("asInterfaceInstance"));

        reflectionMethodNames.put(ObjectOutputStream.class.getTypeName(), Set.of("writeObject", "writeUnshared"));
        reflectionMethodNames.put(ObjectInputStream.class.getTypeName(), Set.of(
                        "resolveClass",
                        "resolveProxyClass",
                        "readObject",
                        "readUnshared"));
        reflectionMethodNames.put(ObjectStreamClass.class.getTypeName(), Set.of("lookup"));

        reflectionMethodNames.put(Proxy.class.getTypeName(), Set.of("getProxyClass", "newProxyInstance"));

        resourceMethodNames.put(ClassLoader.class.getTypeName(), Set.of(
                        "getResource",
                        "getResources",
                        "getSystemResource",
                        "getSystemResources"));
        resourceMethodNames.put(BuiltinClassLoader.class.getTypeName(), Set.of("findResource", "findResourceAsStream"));
        resourceMethodNames.put(Loader.class.getTypeName(), Set.of("findResource"));
        resourceMethodNames.put(ResourceBundle.class.getTypeName(), Set.of("getBundleImpl"));
        resourceMethodNames.put(Module.class.getTypeName(), Set.of("getResourceAsStream"));
        resourceMethodNames.put(Class.class.getTypeName(), Set.of("getResource", "getResourceAsStream"));
    }

    public DynamicAccessDetectionPhase() {
        this.dynamicAccessDetectionFeature = DynamicAccessDetectionFeature.instance();
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        List<MethodCallTargetNode> callTargetNodes = graph.getNodes(MethodCallTargetNode.TYPE).snapshot();
        for (MethodCallTargetNode callTarget : callTargetNodes) {
            AnalysisType callerClass = (AnalysisType) graph.method().getDeclaringClass();
            String entryPath = getEntryPath(callerClass);
            MethodInfo methodInfo = getMethod(callTarget);

            if (methodInfo != null && entryPath != null) {
                DynamicAccessKind accessKind = methodInfo.accessKind();
                String methodName = methodInfo.name();

                NodeSourcePosition nspToShow = callTarget.getNodeSourcePosition();
                if (nspToShow != null) {
                    while (nspToShow.getCaller() != null) {
                        nspToShow = nspToShow.getCaller();
                    }
                    int bci = nspToShow.getBCI();
                    if (!dynamicAccessDetectionFeature.containsFoldEntry(bci, nspToShow.getMethod())) {
                        String callLocation = nspToShow.getMethod().asStackTraceElement(bci).toString();
                        dynamicAccessDetectionFeature.addCall(entryPath, accessKind, methodName, callLocation);
                    }
                }
            }
        }
    }

    /*
     * Returns the name and dynamic access kind (reflective or resource) of a
     * method if it exists in the predetermined set, based on its graph and MethodCallTargetNode;
     * otherwise, returns null.
     */
    private static MethodInfo getMethod(MethodCallTargetNode callTarget) {
        String methodName = callTarget.targetMethod().getName();
        String declaringClass = callTarget.targetMethod().getDeclaringClass().toJavaName();

        if (reflectionMethodNames.containsKey(declaringClass) && reflectionMethodNames.get(declaringClass).contains(methodName)) {
            return new MethodInfo(DynamicAccessKind.Reflection, declaringClass + "#" + methodName);
        } else if (resourceMethodNames.containsKey(declaringClass) && resourceMethodNames.get(declaringClass).contains(methodName)) {
            return new MethodInfo(DynamicAccessKind.Resource, declaringClass + "#" + methodName);
        }
        return null;
    }

    /*
     * Returns the class or module path entry path of the caller class if it is included in the path
     * specified by the option, otherwise returns null.
     */
    private static String getEntryPath(AnalysisType callerClass) {
        try {
            CodeSource entryPathSource = callerClass.getJavaClass().getProtectionDomain().getCodeSource();
            if (entryPathSource == null) {
                return null;
            }

            URL entryPathURL = entryPathSource.getLocation();
            if (entryPathURL == null) {
                return null;
            }

            String entryPath = entryPathURL.toURI().getPath();
            if (entryPath.endsWith("/")) {
                entryPath = entryPath.substring(0, entryPath.length() - 1);
            }
            if (DynamicAccessDetectionFeature.instance().getPathEntries().contains(entryPath)) {
                return entryPath;
            }
            return null;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
