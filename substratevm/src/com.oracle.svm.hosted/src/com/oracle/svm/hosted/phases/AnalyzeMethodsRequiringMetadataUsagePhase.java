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
import com.oracle.svm.hosted.AnalyzeMethodsRequiringMetadataUsageFeature;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.internal.loader.BuiltinClassLoader;
import jdk.internal.loader.Loader;
import org.graalvm.collections.Pair;
import sun.invoke.util.ValueConversions;
import sun.invoke.util.VerifyAccess;
import sun.reflect.annotation.AnnotationParser;
import sun.security.x509.X500Name;

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

import static com.oracle.svm.hosted.AnalyzeMethodsRequiringMetadataUsageFeature.METHODTYPE_PROXY;
import static com.oracle.svm.hosted.AnalyzeMethodsRequiringMetadataUsageFeature.METHODTYPE_REFLECTION;
import static com.oracle.svm.hosted.AnalyzeMethodsRequiringMetadataUsageFeature.METHODTYPE_RESOURCE;
import static com.oracle.svm.hosted.AnalyzeMethodsRequiringMetadataUsageFeature.METHODTYPE_SERIALIZATION;

/**
 * This phase detects usages of any calls that might require metadata in reached parts of the
 * project, given the JAR files in which to search, and outputs and serializes them to the
 * image-build output. It is an optional phase that happens before
 * {@link com.oracle.graal.pointsto.results.StrengthenGraphs} by using the
 * {@link AnalyzeMethodsRequiringMetadataUsageFeature.Options#TrackMethodsRequiringMetadata} option
 * and providing the desired JAR path/s.
 */

public class AnalyzeMethodsRequiringMetadataUsagePhase extends BasePhase<CoreProviders> {
    private static final Map<String, Set<String>> reflectMethodNames = new HashMap<>();
    private static final Map<String, Set<String>> resourceMethodNames = new HashMap<>();
    private static final Map<String, Set<String>> serializationMethodNames = new HashMap<>();
    private static final Map<String, Set<String>> proxyMethodNames = new HashMap<>();

    static {
        reflectMethodNames.put(Class.class.getTypeName(), Set.of(
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
        reflectMethodNames.put(Method.class.getTypeName(), Set.of("invoke"));
        reflectMethodNames.put(MethodHandles.Lookup.class.getTypeName(), Set.of(
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
        reflectMethodNames.put(ClassLoader.class.getTypeName(), Set.of(
                        "loadClass",
                        "findBootstrapClassOrNull",
                        "findLoadedClass",
                        "findSystemClass"));
        reflectMethodNames.put(URLClassLoader.class.getTypeName(), Set.of("loadClass"));
        reflectMethodNames.put(Array.class.getTypeName(), Set.of("newInstance"));
        reflectMethodNames.put(Constructor.class.getTypeName(), Set.of("newInstance"));
        reflectMethodNames.put("java.lang.reflect.ReflectAccess", Set.of("newInstance"));
        reflectMethodNames.put("sun.misc.Unsafe", Set.of("allocateInstance"));
        reflectMethodNames.put("java.lang.constant.ReferenceClassDescImpl", Set.of("resolveConstantDesc"));
        reflectMethodNames.put(ObjectInputStream.class.getTypeName(), Set.of("resolveClass", "resolveProxyClass"));
        reflectMethodNames.put("javax.crypto.extObjectInputStream", Set.of("resolveClass"));
        reflectMethodNames.put(VerifyAccess.class.getTypeName(), Set.of("isTypeVisible"));
        reflectMethodNames.put("sun.reflect.generics.factory.CoreReflectionFactory", Set.of("makeNamedType"));
        reflectMethodNames.put("sun.reflect.misc.ReflectUtil", Set.of("forName"));
        reflectMethodNames.put("sun.security.tools.KeyStoreUtil", Set.of("loadProvidedByClass"));
        reflectMethodNames.put("sun.util.locale.provider.LocaleProviderAdapter", Set.of("forType"));
        reflectMethodNames.put("sun.reflect.misc.ConstructorUtil", Set.of("getConstructor", "getConstructors"));
        reflectMethodNames.put("java.lang.invoke.ClassSpecializer", Set.of("reflectConstructor"));
        reflectMethodNames.put("sun.reflect.misc.FieldUtil", Set.of("getField", "getFields"));
        reflectMethodNames.put("sun.reflect.misc.MethodUtil", Set.of("getMethod", "getMethods", "loadClass"));
        reflectMethodNames.put("sun.security.util.KeyStoreDelegator", Set.of("engineLoad", "engineProbe"));
        reflectMethodNames.put(ValueConversions.class.getTypeName(), Set.of("boxExact"));
        reflectMethodNames.put(ConstantBootstraps.class.getTypeName(), Set.of("getStaticFinal", "staticFieldVarHandle", "fieldVarHandle"));
        reflectMethodNames.put(VarHandle.VarHandleDesc.class.getTypeName(), Set.of("resolveConstantDesc"));
        reflectMethodNames.put(RandomGeneratorFactory.class.getTypeName(), Set.of("create"));
        reflectMethodNames.put(X500Name.class.getTypeName(), Set.of("asX500Principal"));
        reflectMethodNames.put(MethodHandleProxies.class.getTypeName(), Set.of("asInterfaceInstance"));
        reflectMethodNames.put(AnnotationParser.class.getTypeName(), Set.of("annotationForMap"));

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

        serializationMethodNames.put(ObjectOutputStream.class.getTypeName(), Set.of("writeObject", "writeUnshared"));
        serializationMethodNames.put(ObjectInputStream.class.getTypeName(), Set.of("readObject", "readUnshared"));
        serializationMethodNames.put(ObjectStreamClass.class.getTypeName(), Set.of("lookup"));
        serializationMethodNames.put("sun.reflect.ReflectionFactory", Set.of("newConstructorForSerialization"));
        serializationMethodNames.put("jdk.internal.reflect.ReflectionFactory", Set.of("newConstructorForSerialization"));

        proxyMethodNames.put(Proxy.class.getTypeName(), Set.of("getProxyClass", "newProxyInstance"));
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        List<MethodCallTargetNode> callTargetNodes = graph.getNodes(MethodCallTargetNode.TYPE).snapshot();
        for (MethodCallTargetNode callTarget : callTargetNodes) {
            Pair<String, String> methodDetails = getMethod(graph, callTarget);
            if (methodDetails != null) {
                String methodType = methodDetails.getLeft();
                String methodName = methodDetails.getRight();

                NodeSourcePosition nspToShow = callTarget.getNodeSourcePosition();
                if (nspToShow != null) {
                    while (nspToShow.getCaller() != null) {
                        nspToShow = nspToShow.getCaller();
                    }
                    int bci = nspToShow.getBCI();
                    if (!AnalyzeMethodsRequiringMetadataUsageFeature.instance().containsFoldEntry(bci, nspToShow.getMethod())) {
                        String callLocation = nspToShow.getMethod().asStackTraceElement(bci).toString();
                        AnalyzeMethodsRequiringMetadataUsageFeature.instance().addCall(methodType, methodName, callLocation);
                    }
                }
            }
        }
    }

    public Pair<String, String> getMethod(StructuredGraph graph, MethodCallTargetNode callTarget) {
        AnalysisType callerClass = (AnalysisType) graph.method().getDeclaringClass();
        if (!containedInJars(callerClass)) {
            return null;
        }
        String methodName = callTarget.targetMethod().getName();
        String declaringClass = callTarget.targetMethod().getDeclaringClass().toJavaName();

        if (reflectMethodNames.containsKey(declaringClass) && reflectMethodNames.get(declaringClass).contains(methodName)) {
            return Pair.create(METHODTYPE_REFLECTION, declaringClass + "#" + methodName);
        } else if (resourceMethodNames.containsKey(declaringClass) && resourceMethodNames.get(declaringClass).contains(methodName)) {
            return Pair.create(METHODTYPE_RESOURCE, declaringClass + "#" + methodName);
        } else if (serializationMethodNames.containsKey(declaringClass) && serializationMethodNames.get(declaringClass).contains(methodName)) {
            return Pair.create(METHODTYPE_SERIALIZATION, declaringClass + "#" + methodName);
        } else if (proxyMethodNames.containsKey(declaringClass) && proxyMethodNames.get(declaringClass).contains(methodName)) {
            return Pair.create(METHODTYPE_PROXY, declaringClass + "#" + methodName);
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

            return AnalyzeMethodsRequiringMetadataUsageFeature.instance().getJarPaths().contains(jarPathURL.toURI().getPath());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
