/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.snippets;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInlineOnlyInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Performs constant folding of methods that perform reflection lookups when all arguments are
 * compile-time constants, e.g., for {@link Method}, {@link MethodHandle}, or {@code VarHandle}
 * instances. This avoids manual registration of these elements using a reflection configuration
 * file.
 * <p>
 * One important assumption made in this class is that the return types of all folded methods do not
 * have object identity, i.e., it is allowed to return a cached object instead of creating a new
 * object at every invocation. While the types {@link #ALLOWED_CONSTANT_CLASSES we allow} are not
 * explicitly specified in the JDK to have no object identity, there are enough caches also in the
 * JDK so that any code that would rely on object identity is error-prone on any JVM.
 */
public final class ReflectionPlugins extends FoldInvocationUsingReflectionPlugin {
    public static class ReflectionPluginRegistry extends IntrinsificationPluginRegistry {
    }

    static class Options {
        @Option(help = "Enable trace logging for reflection plugins.")//
        static final HostedOptionKey<Boolean> ReflectionPluginTracing = new HostedOptionKey<>(false);
    }

    /**
     * Marker value for parameters that are null, to distinguish from "not able to {@link #unbox}".
     */
    private static final Object NULL_MARKER = new Object();

    /**
     * Classes that are allowed to be constant folded for Object parameters. We must be careful and
     * return only objects of classes that are "immutable enough", i.e., cannot change their
     * meaning. Otherwise, the object could be modified between the intrinsification at image build
     * time and the actual method invocation at run time.
     * <p>
     * Note that many of the classes are not completely immutable because they have lazily
     * initialized caches, or the "accessible" flag of reflection objects. That is OK, because these
     * mutable fields do not affect the outcome of any of the methods that we register for constant
     * folding.
     * <p>
     * Adding an array type of a Java collection class to this list is always wrong, because those
     * are never immutable.
     */
    private static final Set<Class<?>> ALLOWED_CONSTANT_CLASSES = new HashSet<>(Arrays.asList(
                    Class.class, String.class, ClassLoader.class,
                    Method.class, Constructor.class, Field.class,
                    MethodHandle.class, MethodHandles.Lookup.class, MethodType.class,
                    VarHandle.class,
                    ByteOrder.class));

    private final ImageClassLoader imageClassLoader;
    private final ClassInitializationPlugin classInitializationPlugin;

    private ReflectionPlugins(ImageClassLoader imageClassLoader, SnippetReflectionProvider snippetReflection, AnnotationSubstitutionProcessor annotationSubstitutions,
                    ClassInitializationPlugin classInitializationPlugin, AnalysisUniverse aUniverse, ParsingReason reason) {
        super(snippetReflection, annotationSubstitutions, aUniverse, reason, ALLOWED_CONSTANT_CLASSES);
        this.imageClassLoader = imageClassLoader;
        this.classInitializationPlugin = classInitializationPlugin;
    }

    public static void registerInvocationPlugins(ImageClassLoader imageClassLoader, SnippetReflectionProvider snippetReflection, AnnotationSubstitutionProcessor annotationSubstitutions,
                    ClassInitializationPlugin classInitializationPlugin, InvocationPlugins plugins, AnalysisUniverse aUniverse, ParsingReason reason) {
        /*
         * Initialize the registry if we are during analysis. If hosted is false, i.e., we are
         * analyzing the static initializers, then we always intrinsify, so don't need a registry.
         */
        if (reason == ParsingReason.PointsToAnalysis) {
            if (!ImageSingletons.contains(ReflectionPluginRegistry.class)) {
                ImageSingletons.add(ReflectionPluginRegistry.class, new ReflectionPluginRegistry());
            }
        }

        ReflectionPlugins rp = new ReflectionPlugins(imageClassLoader, snippetReflection, annotationSubstitutions, classInitializationPlugin, aUniverse, reason);
        rp.registerMethodHandlesPlugins(plugins);
        rp.registerClassPlugins(plugins);
    }

    private void registerMethodHandlesPlugins(InvocationPlugins plugins) {
        registerFoldInvocationPlugins(plugins, MethodHandles.class,
                        "publicLookup", "privateLookupIn",
                        "arrayConstructor", "arrayLength", "arrayElementGetter", "arrayElementSetter", "arrayElementVarHandle",
                        "byteArrayViewVarHandle", "byteBufferViewVarHandle");

        registerFoldInvocationPlugins(plugins, MethodHandles.Lookup.class,
                        "in",
                        "findStatic", "findVirtual", "findConstructor", "findClass", "accessClass", "findSpecial",
                        "findGetter", "findSetter", "findVarHandle",
                        "findStaticGetter", "findStaticSetter", "findStaticVarHandle",
                        "unreflect", "unreflectSpecial", "unreflectConstructor",
                        "unreflectGetter", "unreflectSetter", "unreflectVarHandle");

        registerFoldInvocationPlugins(plugins, MethodType.class,
                        "methodType", "genericMethodType",
                        "changeParameterType", "insertParameterTypes", "appendParameterTypes", "replaceParameterTypes", "dropParameterTypes",
                        "changeReturnType", "erase", "generic", "wrap", "unwrap",
                        "parameterType", "parameterCount", "returnType", "lastParameterType");

        Registration r = new Registration(plugins, MethodHandles.class);
        r.register(new RequiredInlineOnlyInvocationPlugin("lookup") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                return processMethodHandlesLookup(b, targetMethod);
            }
        });
    }

    private void registerClassPlugins(InvocationPlugins plugins) {
        registerFoldInvocationPlugins(plugins, Class.class,
                        "getField", "getMethod", "getConstructor",
                        "getDeclaredField", "getDeclaredMethod", "getDeclaredConstructor");

        Registration r = new Registration(plugins, Class.class);
        r.register(new RequiredInvocationPlugin("forName", String.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode nameNode) {
                return processClassForName(b, targetMethod, nameNode, ConstantNode.forBoolean(true));
            }
        });
        r.register(new RequiredInvocationPlugin("forName", String.class, boolean.class, ClassLoader.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode nameNode, ValueNode initializeNode, ValueNode classLoaderNode) {
                /*
                 * For now, we ignore the ClassLoader parameter. We only intrinsify class names that
                 * are found by the ImageClassLoader, i.e., the application class loader at run
                 * time. We assume that every class loader used at run time delegates to the
                 * application class loader.
                 */
                return processClassForName(b, targetMethod, nameNode, initializeNode);
            }
        });
        r.register(new RequiredInvocationPlugin("getClassLoader", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                return processClassGetClassLoader(b, targetMethod, receiver);
            }
        });
    }

    private static final Constructor<MethodHandles.Lookup> LOOKUP_CONSTRUCTOR = ReflectionUtil.lookupConstructor(MethodHandles.Lookup.class, Class.class);

    /**
     * We cannot invoke MethodHandles.lookup() directly via reflection because it is a
     * caller-sensitive method, i.e., it uses Reflection.getCallerClass(), and we need to use the
     * caller class based on our {@link GraphBuilderContext#getMethod parsing context}. So we
     * simulate what it is doing: allocating a new Lookup instance and passing the caller class as
     * the constructor parameter.
     */
    private boolean processMethodHandlesLookup(GraphBuilderContext b, ResolvedJavaMethod targetMethod) {
        Supplier<String> targetParameters = () -> "";

        if (StackTraceUtils.ignoredBySecurityStackWalk(b.getMetaAccess(), b.getMethod())) {
            /*
             * If our immediate caller (which is the only method available at the time the
             * invocation plugin is running) is not the method returned by
             * Reflection.getCallerClass(), we cannot intrinsify.
             */
            return false;
        }
        Class<?> callerClass = OriginalClassProvider.getJavaClass(snippetReflection, b.getMethod().getDeclaringClass());
        MethodHandles.Lookup lookup;
        try {
            /* The constructor of Lookup is not public, so we need to invoke it via reflection. */
            lookup = LOOKUP_CONSTRUCTOR.newInstance(callerClass);
        } catch (Throwable ex) {
            return throwException(b, targetMethod, targetParameters, ex.getClass(), ex.getMessage());
        }
        return pushConstant(b, targetMethod, targetParameters, JavaKind.Object, lookup, false) != null;
    }

    /**
     * We cannot invoke Class.forName directly via reflection because we need to use the
     * {@link ImageClassLoader} to look up the class name, not the class loader that loaded the
     * native image generator.
     */
    private boolean processClassForName(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode nameNode, ValueNode initializeNode) {
        Object classNameValue = unbox(b, nameNode, JavaKind.Object);
        Object initializeValue = unbox(b, initializeNode, JavaKind.Boolean);

        if (!(classNameValue instanceof String) || !(initializeValue instanceof Boolean)) {
            return false;
        }
        String className = (String) classNameValue;
        boolean initialize = (Boolean) initializeValue;
        Supplier<String> targetParameters = () -> className + ", " + initialize;

        TypeResult<Class<?>> typeResult = imageClassLoader.findClass(className);
        if (!typeResult.isPresent()) {
            Throwable e = typeResult.getException();
            return throwException(b, targetMethod, targetParameters, e.getClass(), e.getMessage());
        }
        Class<?> clazz = typeResult.get();
        if (PredefinedClassesSupport.isPredefined(clazz)) {
            return false;
        }

        JavaConstant classConstant = pushConstant(b, targetMethod, targetParameters, JavaKind.Object, clazz, false);
        if (classConstant == null) {
            return false;
        }

        if (initialize) {
            classInitializationPlugin.apply(b, b.getMetaAccess().lookupJavaType(clazz), () -> null, null);
        }
        return true;
    }

    /**
     * For {@link PredefinedClassesSupport predefined classes}, the class loader is not known yet at
     * image build time. So we must not constant fold Class.getClassLoader for such classes. But for
     * "normal" classes, it is important to fold it because it unlocks further constant folding of,
     * e.g., Class.forName calls.
     */
    private boolean processClassGetClassLoader(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
        Object classValue = unbox(b, receiver.get(false), JavaKind.Object);
        if (!(classValue instanceof Class)) {
            return false;
        }
        Class<?> clazz = (Class<?>) classValue;
        if (PredefinedClassesSupport.isPredefined(clazz)) {
            return false;
        }
        return pushConstant(b, targetMethod, () -> clazz.getName(), JavaKind.Object, clazz.getClassLoader(), true) != null;
    }

    /**
     * Helper to register all declared methods by name only, to avoid listing all the complete
     * parameter types. It also simplifies handling of different JDK versions, because methods not
     * yet available in JDK 8 (like VarHandle methods) are silently ignored.
     */
    private void registerFoldInvocationPlugins(InvocationPlugins plugins, Class<?> declaringClass, String... methodNames) {
        Set<String> methodNamesSet = new HashSet<>(Arrays.asList(methodNames));
        ModuleSupport.accessModuleByClass(ModuleSupport.Access.OPEN, ReflectionPlugins.class, declaringClass);
        for (Method method : declaringClass.getDeclaredMethods()) {
            if (methodNamesSet.contains(method.getName()) && !method.isSynthetic()) {
                registerFoldInvocationPlugin(plugins, method);
            }
        }
    }

    private void registerFoldInvocationPlugin(InvocationPlugins plugins, Method reflectionMethod) {
        if (!ALLOWED_CONSTANT_CLASSES.contains(reflectionMethod.getReturnType()) && !reflectionMethod.getReturnType().isPrimitive()) {
            throw VMError.shouldNotReachHere("Return type of method " + reflectionMethod + " is not on the allow-list for types that are immutable");
        }
        reflectionMethod.setAccessible(true);

        List<Class<?>> parameterTypes = new ArrayList<>();
        if (!Modifier.isStatic(reflectionMethod.getModifiers())) {
            parameterTypes.add(Receiver.class);
        }
        parameterTypes.addAll(Arrays.asList(reflectionMethod.getParameterTypes()));
        plugins.register(reflectionMethod.getDeclaringClass(), new RequiredInvocationPlugin(reflectionMethod.getName(), parameterTypes.toArray(new Class<?>[0])) {
            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                return foldInvocationUsingReflection(b, targetMethod, reflectionMethod, () -> receiver.get(false), args, false);
            }
        });
    }
}
