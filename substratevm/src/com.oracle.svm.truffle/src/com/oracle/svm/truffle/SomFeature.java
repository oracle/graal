/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.phases.SubstrateIntrinsicGraphBuilder;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.snippets.SubstrateGraphBuilderPlugins;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.truffle.api.staticobject.StaticShape;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

@AutomaticFeature
public final class SomFeature implements GraalFeature {
    private static final String GENERATOR_CLASS_NAME = "com.oracle.truffle.api.staticobject.ArrayBasedShapeGenerator";
    private static final String GENERATOR_CLASS_LOADER_CLASS_NAME = "com.oracle.truffle.api.staticobject.GeneratorClassLoader";
    private static ClassLoader generatorClassLoader;
    private final HashSet<Pair<Class<?>, Class<?>>> interceptedArgs = new HashSet<>();

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, Plugins plugins, ParsingReason reason) {
        if (reason == ParsingReason.PointsToAnalysis) {
            InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins.getInvocationPlugins(), StaticShape.Builder.class);
            r.register3("build", InvocationPlugin.Receiver.class, Class.class, Class.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2) {
                    if (!(receiver instanceof SubstrateIntrinsicGraphBuilder)) {
                        Class<?> superClass = getArgumentClass(b, targetMethod, 1, arg1);
                        Class<?> factoryInterface = getArgumentClass(b, targetMethod, 2, arg2);
                        interceptedArgs.add(Pair.create(superClass, factoryInterface));
                    }
                    return false;
                }
            });
        }
    }

    private static Class<?> getArgumentClass(GraphBuilderContext b, ResolvedJavaMethod targetMethod, int parameterIndex, ValueNode arg) {
        SubstrateGraphBuilderPlugins.checkParameterUsage(arg.isConstant(), b, targetMethod, parameterIndex, "parameter is not a compile time constant");
        return OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(), b.getConstantReflection().asJavaType(arg.asJavaConstant()));
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        for (Pair<Class<?>, Class<?>> args : interceptedArgs) {
            generate(args.getLeft(), args.getRight(), access);
            access.requireAnalysisIteration();
        }
        interceptedArgs.clear();
    }

    private static Class<?> generate(Class<?> storageSuperClass, Class<?> factoryInterface, BeforeAnalysisAccess access) {
        Class<?> shapeGeneratorClass = loadClass(GENERATOR_CLASS_NAME);
        ClassLoader generatorCL = getGeneratorClassLoader(factoryInterface);
        Method generatorMethod = ReflectionUtil.lookupMethod(shapeGeneratorClass, "getShapeGenerator", generatorCL.getClass(), Class.class, Class.class);
        Object generator;
        try {
            generator = generatorMethod.invoke(null, generatorCL, storageSuperClass, factoryInterface);
        } catch (IllegalAccessException | InvocationTargetException | ClassCastException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
        Class<?> storageClass;
        Class<?> factoryClass;
        try {
            Field storageField = shapeGeneratorClass.getDeclaredField("generatedStorageClass");
            Field factoryField = shapeGeneratorClass.getDeclaredField("generatedFactoryClass");
            storageField.setAccessible(true);
            factoryField.setAccessible(true);
            storageClass = Class.class.cast(storageField.get(generator));
            factoryClass = Class.class.cast(factoryField.get(generator));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
        for (Constructor<?> c : factoryClass.getDeclaredConstructors()) {
            RuntimeReflection.register(c);
        }
        for (String fieldName : new String[]{"primitive", "object", "shape"}) {
            access.registerAsUnsafeAccessed(ReflectionUtil.lookupField(storageClass, fieldName));
        }
        return storageClass;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void beforeCompilation(BeforeCompilationAccess config) {
        // Recompute the offset of the byte and object arrays stored in the cached ShapeGenerator
        Unsafe unsafe = GraalUnsafeAccess.getUnsafe();
        Class<?> shapeGeneratorClass = loadClass(GENERATOR_CLASS_NAME);
        long baoFieldOffset = getJvmFieldOffset(unsafe, shapeGeneratorClass, "byteArrayOffset");
        long oaoFieldOffset = getJvmFieldOffset(unsafe, shapeGeneratorClass, "objectArrayOffset");
        long shapeFieldOffset = getJvmFieldOffset(unsafe, shapeGeneratorClass, "shapeOffset");
        ConcurrentHashMap<?, ?> generatorCache = ReflectionUtil.readStaticField(shapeGeneratorClass, "generatorCache");
        for (Entry<?, ?> entry : generatorCache.entrySet()) {
            Object shapeGenerator = entry.getValue();
            Class<?> generatedStorageClass = ReflectionUtil.readField(shapeGeneratorClass, "generatedStorageClass", shapeGenerator);
            unsafe.putInt(shapeGenerator, baoFieldOffset, getNativeImageFieldOffset(config, generatedStorageClass, "primitive"));
            unsafe.putInt(shapeGenerator, oaoFieldOffset, getNativeImageFieldOffset(config, generatedStorageClass, "object"));
            unsafe.putInt(shapeGenerator, shapeFieldOffset, getNativeImageFieldOffset(config, generatedStorageClass, "shape"));
        }
    }

    private static synchronized ClassLoader getGeneratorClassLoader(Class<?> factoryInterface) {
        if (generatorClassLoader == null) {
            Class<?> classLoaderClass = loadClass(GENERATOR_CLASS_LOADER_CLASS_NAME);
            Constructor<?> constructor = ReflectionUtil.lookupConstructor(classLoaderClass, ClassLoader.class, ProtectionDomain.class);
            try {
                generatorClassLoader = ClassLoader.class.cast(constructor.newInstance(factoryInterface.getClassLoader(), factoryInterface.getProtectionDomain()));
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw JVMCIError.shouldNotReachHere(e);
            }
        }
        return generatorClassLoader;
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
    }

    private static long getJvmFieldOffset(Unsafe unsafe, Class<?> declaringClass, String fieldName) {
        return unsafe.objectFieldOffset(ReflectionUtil.lookupField(declaringClass, fieldName));
    }

    private static int getNativeImageFieldOffset(BeforeCompilationAccess config, Class<?> declaringClass, String fieldName) {
        return Math.toIntExact(config.objectFieldOffset(ReflectionUtil.lookupField(declaringClass, fieldName)));
    }
}