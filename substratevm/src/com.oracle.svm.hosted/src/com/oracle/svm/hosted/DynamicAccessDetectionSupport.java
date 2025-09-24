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
package com.oracle.svm.hosted;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.util.ReflectionUtil;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

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
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.loader.BuiltinClassLoader;
import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.ReflectionFactory;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Support class that caches a predetermined set of dynamic-access methods which may require
 * metadata at runtime.
 * <p>
 * Used by {@link com.oracle.svm.hosted.phases.DynamicAccessDetectionPhase} to identify
 * dynamic-access methods during method graph parsing.
 */
public class DynamicAccessDetectionSupport {
    public enum DynamicAccessKind {
        Reflection("reflection-calls.json"),
        Resource("resource-calls.json"),
        Foreign("foreign-calls.json");

        public final String fileName;

        DynamicAccessKind(String fileName) {
            this.fileName = fileName;
        }
    }

    private record MethodSignature(String methodName, Class<?>... parameterTypes) {
    }

    public record MethodInfo(DynamicAccessKind accessKind, String signature) {
    }

    private final EconomicMap<ResolvedJavaType, Set<ResolvedJavaMethod>> reflectionMethods = EconomicMap.create();
    private final EconomicMap<ResolvedJavaType, Set<ResolvedJavaMethod>> resourceMethods = EconomicMap.create();
    private final EconomicMap<ResolvedJavaType, Set<ResolvedJavaMethod>> foreignMethods = EconomicMap.create();

    private final AnalysisMetaAccess metaAccess;

    public static DynamicAccessDetectionSupport instance() {
        return ImageSingletons.lookup(DynamicAccessDetectionSupport.class);
    }

    public DynamicAccessDetectionSupport(AnalysisMetaAccess metaAccess) {
        this.metaAccess = metaAccess;
        boolean jdkUnsupportedModulePresent = ModuleLayer.boot().findModule("jdk.unsupported").isPresent();

        put(reflectionMethods, Class.class, Set.of(
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
        put(reflectionMethods, Field.class, Set.of(
                        new MethodSignature("get", Object.class),
                        new MethodSignature("set", Object.class, Object.class),
                        new MethodSignature("getBoolean", Object.class),
                        new MethodSignature("setBoolean", Object.class, boolean.class),
                        new MethodSignature("getByte", Object.class),
                        new MethodSignature("setByte", Object.class, byte.class),
                        new MethodSignature("getShort", Object.class),
                        new MethodSignature("setShort", Object.class, short.class),
                        new MethodSignature("getChar", Object.class),
                        new MethodSignature("setChar", Object.class, char.class),
                        new MethodSignature("getInt", Object.class),
                        new MethodSignature("setInt", Object.class, int.class),
                        new MethodSignature("getLong", Object.class),
                        new MethodSignature("setLong", Object.class, long.class),
                        new MethodSignature("getFloat", Object.class),
                        new MethodSignature("setFloat", Object.class, float.class),
                        new MethodSignature("getDouble", Object.class),
                        new MethodSignature("setDouble", Object.class, double.class)));
        put(reflectionMethods, Method.class, Set.of(
                        new MethodSignature("invoke", Object.class, Object[].class)));
        put(reflectionMethods, MethodHandles.Lookup.class, Set.of(
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
        put(reflectionMethods, ClassLoader.class, Set.of(
                        new MethodSignature("loadClass", String.class),
                        new MethodSignature("findLoadedClass", String.class),
                        new MethodSignature("findSystemClass", String.class),
                        new MethodSignature("findBootstrapClassOrNull", String.class)));
        put(reflectionMethods, Array.class, Set.of(
                        new MethodSignature("newInstance", Class.class, int.class),
                        new MethodSignature("newInstance", Class.class, int[].class)));
        put(reflectionMethods, Constructor.class, Set.of(
                        new MethodSignature("newInstance", Object[].class)));
        put(reflectionMethods, ConstantBootstraps.class, Set.of(
                        new MethodSignature("getStaticFinal", MethodHandles.Lookup.class, String.class, Class.class, Class.class),
                        new MethodSignature("getStaticFinal", MethodHandles.Lookup.class, String.class, Class.class),
                        new MethodSignature("fieldVarHandle", MethodHandles.Lookup.class, String.class, Class.class, Class.class, Class.class),
                        new MethodSignature("staticFieldVarHandle", MethodHandles.Lookup.class, String.class, Class.class, Class.class, Class.class)));
        put(reflectionMethods, VarHandle.VarHandleDesc.class, Set.of(
                        new MethodSignature("resolveConstantDesc", MethodHandles.Lookup.class)));
        put(reflectionMethods, MethodHandleProxies.class, Set.of(
                        new MethodSignature("asInterfaceInstance", Class.class, MethodHandle.class)));
        put(reflectionMethods, JavaLangAccess.class, Set.of(
                        new MethodSignature("getDeclaredPublicMethods", Class.class, String.class, Class[].class)));
        put(reflectionMethods, Unsafe.class, Set.of(
                        new MethodSignature("allocateInstance", Class.class)));
        if (jdkUnsupportedModulePresent) {
            Class<?> sunMiscUnsafeClass = ReflectionUtil.lookupClass("sun.misc.Unsafe");
            put(reflectionMethods, sunMiscUnsafeClass, Set.of(
                            new MethodSignature("allocateInstance", Class.class)));
        }

        put(reflectionMethods, ObjectOutputStream.class, Set.of(
                        new MethodSignature("writeObject", Object.class),
                        new MethodSignature("writeUnshared", Object.class)));
        put(reflectionMethods, ObjectInputStream.class, Set.of(
                        new MethodSignature("resolveClass", ObjectStreamClass.class),
                        new MethodSignature("resolveProxyClass", String[].class),
                        new MethodSignature("readObject"),
                        new MethodSignature("readUnshared")));
        put(reflectionMethods, ObjectStreamClass.class, Set.of(
                        new MethodSignature("lookup", Class.class)));
        put(reflectionMethods, ReflectionFactory.class, Set.of(
                        new MethodSignature("newConstructorForSerialization", Class.class),
                        new MethodSignature("newConstructorForSerialization", Class.class, Constructor.class)));
        if (jdkUnsupportedModulePresent) {
            Class<?> sunReflectReflectionFactoryClass = ReflectionUtil.lookupClass("sun.reflect.ReflectionFactory");
            put(reflectionMethods, sunReflectReflectionFactoryClass, Set.of(
                            new MethodSignature("newConstructorForSerialization", Class.class),
                            new MethodSignature("newConstructorForSerialization", Class.class, Constructor.class)));
        }

        put(reflectionMethods, Proxy.class, Set.of(
                        new MethodSignature("getProxyClass", ClassLoader.class, Class[].class),
                        new MethodSignature("newProxyInstance", ClassLoader.class, Class[].class, InvocationHandler.class)));

        put(resourceMethods, ClassLoader.class, Set.of(
                        new MethodSignature("getResource", String.class),
                        new MethodSignature("getResources", String.class),
                        new MethodSignature("getResourceAsStream", String.class),
                        new MethodSignature("getSystemResource", String.class),
                        new MethodSignature("getSystemResources", String.class),
                        new MethodSignature("getSystemResourceAsStream", String.class)));
        put(resourceMethods, Module.class, Set.of(
                        new MethodSignature("getResourceAsStream", String.class)));
        put(resourceMethods, Class.class, Set.of(
                        new MethodSignature("getResource", String.class),
                        new MethodSignature("getResourceAsStream", String.class)));
        put(resourceMethods, ResourceBundle.class, Set.of(
                        new MethodSignature("getBundle", String.class),
                        new MethodSignature("getBundle", String.class, ResourceBundle.Control.class),
                        new MethodSignature("getBundle", String.class, Locale.class),
                        new MethodSignature("getBundle", String.class, Module.class),
                        new MethodSignature("getBundle", String.class, Locale.class, Module.class),
                        new MethodSignature("getBundle", String.class, Locale.class, ResourceBundle.Control.class),
                        new MethodSignature("getBundle", String.class, Locale.class, ClassLoader.class),
                        new MethodSignature("getBundle", String.class, Locale.class, ClassLoader.class, ResourceBundle.Control.class)));
        put(resourceMethods, BuiltinClassLoader.class, Set.of(
                        new MethodSignature("findResource", String.class),
                        new MethodSignature("findResource", String.class, String.class),
                        new MethodSignature("findResources", String.class),
                        new MethodSignature("findResourceAsStream", String.class, String.class)));

        put(foreignMethods, Linker.class, Set.of(
                        new MethodSignature("downcallHandle", MemorySegment.class, FunctionDescriptor.class, Linker.Option[].class),
                        new MethodSignature("downcallHandle", FunctionDescriptor.class, Linker.Option[].class),
                        new MethodSignature("upcallStub", MethodHandle.class, FunctionDescriptor.class, Arena.class, Linker.Option[].class)));
        Class<?> abstractLinkerClass = ReflectionUtil.lookupClass("jdk.internal.foreign.abi.AbstractLinker");
        put(foreignMethods, abstractLinkerClass, Set.of(
                        new MethodSignature("downcallHandle", MemorySegment.class, FunctionDescriptor.class, Linker.Option[].class),
                        new MethodSignature("downcallHandle", FunctionDescriptor.class, Linker.Option[].class),
                        new MethodSignature("upcallStub", MethodHandle.class, FunctionDescriptor.class, Arena.class, Linker.Option[].class)));
    }

    private void put(EconomicMap<ResolvedJavaType, Set<ResolvedJavaMethod>> map, Class<?> declaringClass, Set<MethodSignature> methodSignatures) {
        ResolvedJavaType resolvedType = metaAccess.lookupJavaType(declaringClass);

        Set<ResolvedJavaMethod> resolvedMethods = new HashSet<>();
        for (MethodSignature methodSignature : methodSignatures) {
            ResolvedJavaMethod method = metaAccess.lookupJavaMethod(
                            ReflectionUtil.lookupMethod(
                                            declaringClass,
                                            methodSignature.methodName(),
                                            methodSignature.parameterTypes()));
            resolvedMethods.add(method);
        }
        map.put(resolvedType, resolvedMethods);
    }

    /**
     * Looks up whether the given method is part of the predetermined set of dynamic-access methods
     * (reflection, resource, or foreign). If found, returns a {@link MethodInfo} record containing
     * the corresponding {@link DynamicAccessKind} and method signature. Otherwise, returns null.
     */
    public MethodInfo lookupDynamicAccessMethod(ResolvedJavaMethod method) {
        ResolvedJavaType declaringClass = method.getDeclaringClass();

        Set<ResolvedJavaMethod> reflectionSignatures = reflectionMethods.get(declaringClass);
        if (reflectionSignatures != null) {
            if (reflectionSignatures.contains(method)) {
                return new MethodInfo(DynamicAccessKind.Reflection, getMethodSignature(method));
            }
        }

        Set<ResolvedJavaMethod> resourceSignatures = resourceMethods.get(declaringClass);
        if (resourceSignatures != null) {
            if (resourceSignatures.contains(method)) {
                return new MethodInfo(DynamicAccessKind.Resource, getMethodSignature(method));
            }
        }

        Set<ResolvedJavaMethod> foreignSignatures = foreignMethods.get(declaringClass);
        if (foreignSignatures != null) {
            if (foreignSignatures.contains(method)) {
                return new MethodInfo(DynamicAccessKind.Foreign, getMethodSignature(method));
            }
        }

        return null;
    }

    private static String getMethodSignature(ResolvedJavaMethod method) {
        return method.format("%H#%n(%P)").replace('$', '.');
    }

    public void clear() {
        reflectionMethods.clear();
        resourceMethods.clear();
        foreignMethods.clear();
    }
}
