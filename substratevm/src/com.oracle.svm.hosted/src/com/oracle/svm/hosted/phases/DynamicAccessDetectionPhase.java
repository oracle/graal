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

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.DynamicAccessDetectionFeature;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * This phase detects usages of dynamic access calls that might require metadata in reached parts of
 * the project. It does so by analyzing the specified class path entries, modules or packages and
 * identifying relevant accesses. The phase then outputs and serializes the detected usages to the
 * image-build output. It is an optional phase that happens before
 * {@link com.oracle.graal.pointsto.results.StrengthenGraphs} by using the
 * {@link com.oracle.svm.core.SubstrateOptions#TrackDynamicAccess} option and providing the desired
 * source entries.
 */
public class DynamicAccessDetectionPhase extends BasePhase<CoreProviders> {
    public enum DynamicAccessKind {
        Reflection("reflection-calls.json"),
        Resource("resource-calls.json"),
        Foreign("foreign-calls.json");

        public final String fileName;

        DynamicAccessKind(String fileName) {
            this.fileName = fileName;
        }
    }

    public record MethodInfo(DynamicAccessKind accessKind, String signature) {
    }

    private static final EconomicMap<Class<?>, Set<MethodSignature>> reflectionMethodSignatures = EconomicMap.create();
    private static final EconomicMap<Class<?>, Set<MethodSignature>> resourceMethodSignatures = EconomicMap.create();
    private static final EconomicMap<Class<?>, Set<MethodSignature>> foreignMethodSignatures = EconomicMap.create();

    private final DynamicAccessDetectionFeature dynamicAccessDetectionFeature;

    static {
        reflectionMethodSignatures.put(Class.class, Set.of(
                        new MethodSignature("forName", String.class),
                        new MethodSignature("forName", String.class, boolean.class, ClassLoader.class),
                        new MethodSignature("forName", Module.class, String.class),
                        new MethodSignature("getClasses"),
                        new MethodSignature("getDeclaredClasses"),
                        new MethodSignature("getConstructor", Class[].class),
                        new MethodSignature("getConstructors"),
                        new MethodSignature("getDeclaredConstructor", Class[].class),
                        new MethodSignature("getDeclaredConstructors"),
                        new MethodSignature("getField", String.class),
                        new MethodSignature("getFields"),
                        new MethodSignature("getDeclaredField", String.class),
                        new MethodSignature("getDeclaredFields"),
                        new MethodSignature("getMethod", String.class, Class[].class),
                        new MethodSignature("getMethods"),
                        new MethodSignature("getDeclaredMethod", String.class, Class[].class),
                        new MethodSignature("getDeclaredMethods"),
                        new MethodSignature("getNestMembers"),
                        new MethodSignature("getPermittedSubclasses"),
                        new MethodSignature("getRecordComponents"),
                        new MethodSignature("getSigners"),
                        new MethodSignature("arrayType"),
                        new MethodSignature("newInstance")));
        reflectionMethodSignatures.put(Method.class, Set.of(
                        new MethodSignature("invoke", Object.class, Object[].class)));
        reflectionMethodSignatures.put(MethodHandles.Lookup.class, Set.of(
                        new MethodSignature("findClass", String.class),
                        new MethodSignature("findVirtual", Class.class, String.class, MethodType.class),
                        new MethodSignature("findStatic", Class.class, String.class, MethodType.class),
                        new MethodSignature("findConstructor", Class.class, MethodType.class),
                        new MethodSignature("findSpecial", Class.class, String.class, MethodType.class, Class.class),
                        new MethodSignature("findGetter", Class.class, String.class, Class.class),
                        new MethodSignature("findSetter", Class.class, String.class, Class.class),
                        new MethodSignature("findStaticGetter", Class.class, String.class, Class.class),
                        new MethodSignature("findStaticSetter", Class.class, String.class, Class.class),
                        new MethodSignature("findVarHandle", Class.class, String.class, Class.class),
                        new MethodSignature("findStaticVarHandle", Class.class, String.class, Class.class),
                        new MethodSignature("unreflect", Method.class),
                        new MethodSignature("unreflectSpecial", Method.class, Class.class),
                        new MethodSignature("unreflectConstructor", Constructor.class),
                        new MethodSignature("unreflectGetter", Field.class),
                        new MethodSignature("unreflectSetter", Field.class),
                        new MethodSignature("unreflectVarHandle", Field.class)));
        reflectionMethodSignatures.put(ClassLoader.class, Set.of(
                        new MethodSignature("loadClass", String.class),
                        new MethodSignature("findLoadedClass", String.class),
                        new MethodSignature("findSystemClass", String.class),
                        new MethodSignature("findBootstrapClassOrNull", String.class)));
        reflectionMethodSignatures.put(Array.class, Set.of(
                        new MethodSignature("newInstance", Class.class, int.class),
                        new MethodSignature("newInstance", Class.class, int[].class)));
        reflectionMethodSignatures.put(Constructor.class, Set.of(
                        new MethodSignature("newInstance", Object[].class)));
        reflectionMethodSignatures.put(ConstantBootstraps.class, Set.of(
                        new MethodSignature("getStaticFinal", MethodHandles.Lookup.class, String.class, Class.class, Class.class),
                        new MethodSignature("getStaticFinal", MethodHandles.Lookup.class, String.class, Class.class),
                        new MethodSignature("fieldVarHandle", MethodHandles.Lookup.class, String.class, Class.class, Class.class, Class.class),
                        new MethodSignature("staticFieldVarHandle", MethodHandles.Lookup.class, String.class, Class.class, Class.class, Class.class)));
        reflectionMethodSignatures.put(VarHandle.VarHandleDesc.class, Set.of(
                        new MethodSignature("resolveConstantDesc", MethodHandles.Lookup.class)));
        reflectionMethodSignatures.put(MethodHandleProxies.class, Set.of(
                        new MethodSignature("asInterfaceInstance", Class.class, MethodHandle.class)));

        reflectionMethodSignatures.put(ObjectOutputStream.class, Set.of(
                        new MethodSignature("writeObject", Object.class),
                        new MethodSignature("writeUnshared", Object.class)));
        reflectionMethodSignatures.put(ObjectInputStream.class, Set.of(
                        new MethodSignature("resolveClass", ObjectStreamClass.class),
                        new MethodSignature("resolveProxyClass", String[].class),
                        new MethodSignature("readObject"),
                        new MethodSignature("readUnshared")));
        reflectionMethodSignatures.put(ObjectStreamClass.class, Set.of(
                        new MethodSignature("lookup", Class.class)));

        reflectionMethodSignatures.put(Proxy.class, Set.of(
                        new MethodSignature("getProxyClass", ClassLoader.class, Class[].class),
                        new MethodSignature("newProxyInstance", ClassLoader.class, Class[].class, InvocationHandler.class)));

        resourceMethodSignatures.put(ClassLoader.class, Set.of(
                        new MethodSignature("getResource", String.class),
                        new MethodSignature("getResources", String.class),
                        new MethodSignature("getResourceAsStream", String.class),
                        new MethodSignature("getSystemResource", String.class),
                        new MethodSignature("getSystemResources", String.class),
                        new MethodSignature("getSystemResourceAsStream", String.class)));
        resourceMethodSignatures.put(Module.class, Set.of(
                        new MethodSignature("getResourceAsStream", String.class)));
        resourceMethodSignatures.put(Class.class, Set.of(
                        new MethodSignature("getResource", String.class),
                        new MethodSignature("getResourceAsStream", String.class)));

        foreignMethodSignatures.put(Linker.class, Set.of(
                        new MethodSignature("downcallHandle", MemorySegment.class, FunctionDescriptor.class, Linker.Option[].class),
                        new MethodSignature("downcallHandle", FunctionDescriptor.class, Linker.Option[].class),
                        new MethodSignature("upcallStub", MethodHandle.class, FunctionDescriptor.class, Arena.class, Linker.Option[].class)));
    }

    public DynamicAccessDetectionPhase() {
        dynamicAccessDetectionFeature = DynamicAccessDetectionFeature.instance();
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        List<MethodCallTargetNode> callTargetNodes = graph.getNodes(MethodCallTargetNode.TYPE).snapshot();
        for (MethodCallTargetNode callTarget : callTargetNodes) {
            AnalysisType callerClass = (AnalysisType) graph.method().getDeclaringClass();
            String sourceEntry = getSourceEntry(callerClass);
            MethodInfo methodInfo = getMethodInfo(callTarget.targetMethod());

            if (methodInfo != null && sourceEntry != null) {
                NodeSourcePosition nspToShow = getRootSourcePosition(callTarget.getNodeSourcePosition());
                if (nspToShow != null && !dynamicAccessDetectionFeature.containsFoldEntry(nspToShow.getBCI(), nspToShow.getMethod())) {
                    String callLocation = nspToShow.getMethod().asStackTraceElement(nspToShow.getBCI()).toString();
                    dynamicAccessDetectionFeature.addCall(sourceEntry, methodInfo.accessKind(), methodInfo.signature(), callLocation);
                }
            }
        }
    }

    /**
     * Returns the name, parameter types and dynamic access kind (reflective or resource) of a
     * method if it exists in the predetermined set, based on its graph and MethodCallTargetNode;
     * otherwise, returns null.
     */
    private static MethodInfo getMethodInfo(ResolvedJavaMethod method) {
        Class<?> declaringClass = OriginalClassProvider.getJavaClass(method.getDeclaringClass());
        if (!reflectionMethodSignatures.containsKey(declaringClass) &&
                        !resourceMethodSignatures.containsKey(declaringClass) &&
                        !foreignMethodSignatures.containsKey(declaringClass)) {
            return null;
        }

        String methodName = method.getName();
        Signature signature = method.getSignature();
        List<Class<?>> paramList = new ArrayList<>();
        for (int i = 0; i < signature.getParameterCount(false); i++) {
            JavaType type = signature.getParameterType(i, method.getDeclaringClass());
            paramList.add(OriginalClassProvider.getJavaClass(type));
        }
        Class<?>[] paramTypes = paramList.toArray(new Class<?>[0]);
        MethodSignature methodSignature = new MethodSignature(methodName, paramTypes);

        if (reflectionMethodSignatures.containsKey(declaringClass) &&
                        reflectionMethodSignatures.get(declaringClass).contains(methodSignature)) {
            return new MethodInfo(DynamicAccessKind.Reflection, declaringClass.getName() + "#" + methodSignature);
        } else if (resourceMethodSignatures.containsKey(declaringClass) &&
                        resourceMethodSignatures.get(declaringClass).contains(methodSignature)) {
            return new MethodInfo(DynamicAccessKind.Resource, declaringClass.getName() + "#" + methodSignature);
        } else if (foreignMethodSignatures.containsKey(declaringClass) &&
                        foreignMethodSignatures.get(declaringClass).contains(methodSignature)) {
            return new MethodInfo(DynamicAccessKind.Foreign, declaringClass.getName() + "#" + methodSignature);
        }

        return null;
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

    private static NodeSourcePosition getRootSourcePosition(NodeSourcePosition nodeSourcePosition) {
        NodeSourcePosition rootNodeSourcePosition = nodeSourcePosition;
        while (rootNodeSourcePosition != null && rootNodeSourcePosition.getCaller() != null) {
            rootNodeSourcePosition = rootNodeSourcePosition.getCaller();
        }
        return rootNodeSourcePosition;
    }

    public static void clearMethodSignatures() {
        reflectionMethodSignatures.clear();
        resourceMethodSignatures.clear();
        foreignMethodSignatures.clear();
    }

    private static class MethodSignature {
        private final String name;
        private final Class<?>[] paramTypes;

        MethodSignature(String name, Class<?>... paramTypes) {
            this.name = name;
            this.paramTypes = paramTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MethodSignature that)) {
                return false;
            }
            return name.equals(that.name) && Arrays.equals(paramTypes, that.paramTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, Arrays.hashCode(paramTypes));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append("(");
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                Class<?> param = paramTypes[i];
                if (param.isArray()) {
                    sb.append(param.getComponentType().getName()).append("[]");
                } else {
                    sb.append(param.getName());
                }
                if (param.getTypeName().contains("?")) {
                    sb.append("<?>");
                }
            }
            sb.append(")");
            return sb.toString();
        }
    }
}
