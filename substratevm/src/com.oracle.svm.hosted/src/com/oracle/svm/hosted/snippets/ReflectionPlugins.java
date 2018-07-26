/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.reflect.ReflectionPluginExceptions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ReflectionPlugins {

    static class ReflectionPluginRegistry {
        /**
         * Contains all the classes, methods, fields intrinsified by this plugin during analysis.
         * Only these elements will be intrinsified during compilation. We cannot intrinsify an
         * element during compilation if it was not intrinsified during analysis since it can lead
         * to compiling code that was not seen during analysis.
         */
        ConcurrentHashMap<Object, Boolean> analysisElements = new ConcurrentHashMap<>();

        public void add(Object element) {
            analysisElements.put(element, Boolean.TRUE);
        }

        public boolean contains(Object element) {
            return analysisElements.containsKey(element);
        }

    }

    static class Options {
        @Option(help = "Enable trace logging for reflection plugins.")//
        static final HostedOptionKey<Boolean> ReflectionPluginTracing = new HostedOptionKey<>(false);
    }

    private static final Method throwClassNotFoundExceptionMethod;
    private static final Method throwNoSuchFieldExceptionMethod;
    private static final Method throwNoSuchMethodExceptionMethod;

    static {
        try {
            throwClassNotFoundExceptionMethod = ReflectionPluginExceptions.class.getDeclaredMethod("throwClassNotFoundException", String.class);
            throwNoSuchFieldExceptionMethod = ReflectionPluginExceptions.class.getDeclaredMethod("throwNoSuchFieldException", String.class);
            throwNoSuchMethodExceptionMethod = ReflectionPluginExceptions.class.getDeclaredMethod("throwNoSuchMethodException", String.class);
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    public static void registerInvocationPlugins(ImageClassLoader imageClassLoader, SnippetReflectionProvider snippetReflection, InvocationPlugins plugins, boolean analysis, boolean hosted) {
        /*
         * Initialize the registry if we are during analysis. If hosted is false, i.e., we are
         * analyzing the static initializers, then we always intrinsify, so don't need a registry.
         */
        if (hosted && analysis) {
            ImageSingletons.add(ReflectionPluginRegistry.class, new ReflectionPluginRegistry());
        }

        registerClassPlugins(imageClassLoader, snippetReflection, plugins, analysis, hosted);
    }

    private static void registerClassPlugins(ImageClassLoader imageClassLoader, SnippetReflectionProvider snippetReflection, InvocationPlugins plugins, boolean analysis, boolean hosted) {
        Registration r = new Registration(plugins, Class.class);

        r.register1("forName", String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode name) {
                return processForName(b, targetMethod, name, imageClassLoader, snippetReflection, analysis, hosted);
            }
        });

        r.register3("forName", String.class, boolean.class, ClassLoader.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode name, ValueNode initialize, ValueNode classLoader) {
                return processForName(b, targetMethod, name, imageClassLoader, snippetReflection, analysis, hosted);
            }
        });

        r.register2("getDeclaredField", Receiver.class, String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode name) {
                return processGetField(b, targetMethod, receiver, name, snippetReflection, true, analysis, hosted);
            }
        });

        r.register2("getField", Receiver.class, String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode name) {
                return processGetField(b, targetMethod, receiver, name, snippetReflection, false, analysis, hosted);
            }
        });

        r.register3("getDeclaredMethod", Receiver.class, String.class, Class[].class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode name, ValueNode parameterTypes) {
                return processGetMethod(b, targetMethod, receiver, name, parameterTypes, snippetReflection, true, analysis, hosted);
            }
        });

        r.register3("getMethod", Receiver.class, String.class, Class[].class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode name, ValueNode parameterTypes) {
                return processGetMethod(b, targetMethod, receiver, name, parameterTypes, snippetReflection, false, analysis, hosted);
            }
        });

        r.register2("getDeclaredConstructor", Receiver.class, Class[].class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode parameterTypes) {
                return processGetConstructor(b, targetMethod, receiver, parameterTypes, snippetReflection, true, analysis, hosted);
            }
        });

        r.register2("getConstructor", Receiver.class, Class[].class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode parameterTypes) {
                return processGetConstructor(b, targetMethod, receiver, parameterTypes, snippetReflection, false, analysis, hosted);
            }
        });
    }

    private static boolean processForName(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode name,
                    ImageClassLoader imageClassLoader, SnippetReflectionProvider snippetReflection, boolean analysis, boolean hosted) {
        if (name.isConstant()) {
            String className = snippetReflection.asObject(String.class, name.asJavaConstant());
            Class<?> clazz = imageClassLoader.findClassByName(className, false);
            if (clazz == null) {
                if (shouldNotIntrinsify(analysis, hosted, throwClassNotFoundExceptionMethod)) {
                    return false;
                }
                throwClassNotFoundException(b, targetMethod, className);
            } else {
                if (shouldNotIntrinsify(analysis, hosted, clazz)) {
                    return false;
                }
                JavaConstant hub = b.getConstantReflection().asJavaClass(b.getMetaAccess().lookupJavaType(clazz));
                pushConstant(b, targetMethod, hub, className);
            }
            return true;
        }
        return false;
    }

    private static boolean processGetField(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode name,
                    SnippetReflectionProvider snippetReflection, boolean declared, boolean analysis, boolean hosted) {
        if (receiver.isConstant() && name.isConstant()) {
            Class<?> clazz = snippetReflection.asObject(Class.class, receiver.get().asJavaConstant());
            String fieldName = snippetReflection.asObject(String.class, name.asJavaConstant());

            String target = clazz.getTypeName() + "." + fieldName;
            try {
                Field field = declared ? clazz.getDeclaredField(fieldName) : clazz.getField(fieldName);
                if (shouldNotIntrinsify(analysis, hosted, field)) {
                    return false;
                }
                pushConstant(b, targetMethod, snippetReflection.forObject(field), target);
            } catch (NoSuchFieldException e) {
                if (shouldNotIntrinsify(analysis, hosted, throwNoSuchFieldExceptionMethod)) {
                    return false;
                }
                throwNoSuchFieldException(b, targetMethod, target);
            }
            return true;
        }
        return false;
    }

    private static boolean processGetMethod(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode name,
                    ValueNode parameterTypes, SnippetReflectionProvider snippetReflection, boolean declared, boolean analysis, boolean hosted) {
        if (receiver.isConstant() && name.isConstant()) {
            Class<?>[] paramTypes = SubstrateGraphBuilderPlugins.extractClassArray(snippetReflection, parameterTypes, true);

            if (paramTypes != null) {
                Class<?> clazz = snippetReflection.asObject(Class.class, receiver.get().asJavaConstant());
                String methodName = snippetReflection.asObject(String.class, name.asJavaConstant());

                String target = clazz.getTypeName() + "." + methodName + "(" + Stream.of(paramTypes).map(Class::getTypeName).collect(Collectors.joining(", ")) + ")";
                try {
                    Method method = declared ? clazz.getDeclaredMethod(methodName, paramTypes) : clazz.getMethod(methodName, paramTypes);
                    if (shouldNotIntrinsify(analysis, hosted, method)) {
                        return false;
                    }
                    pushConstant(b, targetMethod, snippetReflection.forObject(method), target);
                } catch (NoSuchMethodException e) {
                    if (shouldNotIntrinsify(analysis, hosted, throwNoSuchMethodExceptionMethod)) {
                        return false;
                    }
                    throwNoSuchMethodException(b, targetMethod, target);
                }

                return true;
            }
        }
        return false;
    }

    private static boolean processGetConstructor(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode parameterTypes,
                    SnippetReflectionProvider snippetReflection, boolean declared,
                    boolean analysis, boolean hosted) {
        if (receiver.isConstant()) {
            Class<?>[] paramTypes = SubstrateGraphBuilderPlugins.extractClassArray(snippetReflection, parameterTypes, true);

            if (paramTypes != null) {
                Class<?> clazz = snippetReflection.asObject(Class.class, receiver.get().asJavaConstant());

                String target = clazz.getTypeName() + ".<init>(" + Stream.of(paramTypes).map(Class::getTypeName).collect(Collectors.joining(", ")) + ")";
                try {
                    Constructor<?> constructor = declared ? clazz.getDeclaredConstructor(paramTypes) : clazz.getConstructor(paramTypes);
                    if (shouldNotIntrinsify(analysis, hosted, constructor)) {
                        return false;
                    }
                    pushConstant(b, targetMethod, snippetReflection.forObject(constructor), target);
                } catch (NoSuchMethodException e) {
                    if (shouldNotIntrinsify(analysis, hosted, throwNoSuchMethodExceptionMethod)) {
                        return false;
                    }
                    throwNoSuchMethodException(b, targetMethod, target);
                }

                return true;
            }
        }
        return false;
    }

    /** Check if the element should be intrinsified. */
    private static boolean shouldNotIntrinsify(boolean analysis, boolean hosted, Object element) {
        if (!hosted) {
            /* We are analyzing the static initializers and should always intrinsify. */
            return false;
        }
        if (analysis) {
            /* We are during analysis, we should intrinsify and mark the objects as intrinsified. */
            ImageSingletons.lookup(ReflectionPluginRegistry.class).add(element);
            return false;
        }
        /* We are during compilation, we only intrinsify if intrinsified during analysis. */
        return !ImageSingletons.lookup(ReflectionPluginRegistry.class).contains(element);
    }

    private static void pushConstant(GraphBuilderContext b, ResolvedJavaMethod reflectionMethod, JavaConstant constant, String targetElement) {
        b.addPush(JavaKind.Object, ConstantNode.forConstant(constant, b.getMetaAccess(), b.getGraph()));
        traceConstant(b.getMethod(), reflectionMethod, targetElement);
    }

    private static void throwClassNotFoundException(GraphBuilderContext b, ResolvedJavaMethod reflectionMethod, String targetClass) {
        String message = targetClass + ". This exception was synthesized during native image building from a call to " + reflectionMethod.format("%H.%n(%p)") +
                        " with a constant class name argument.";
        throwException(b, message, throwClassNotFoundExceptionMethod);
        traceException(b.getMethod(), reflectionMethod, targetClass, throwClassNotFoundExceptionMethod);
    }

    private static void throwNoSuchFieldException(GraphBuilderContext b, ResolvedJavaMethod reflectionMethod, String targetField) {
        String message = targetField + ". This exception was synthesized during native image building from a call to " + reflectionMethod.format("%H.%n(%p)") +
                        " with a constant field name argument.";
        throwException(b, message, throwNoSuchFieldExceptionMethod);
        traceException(b.getMethod(), reflectionMethod, targetField, throwNoSuchFieldExceptionMethod);
    }

    private static void throwNoSuchMethodException(GraphBuilderContext b, ResolvedJavaMethod reflectionMethod, String targetMethod) {
        String message = targetMethod + ". This exception was synthesized during native image building from a call to " + reflectionMethod.format("%H.%n(%p)") +
                        " with constant method name and parameter types arguments.";
        throwException(b, message, throwNoSuchMethodExceptionMethod);
        traceException(b.getMethod(), reflectionMethod, targetMethod, throwNoSuchMethodExceptionMethod);
    }

    private static void throwException(GraphBuilderContext b, String message, Method reportExceptionMethod) {
        ValueNode messageNode = ConstantNode.forConstant(SubstrateObjectConstant.forObject(message), b.getMetaAccess(), b.getGraph());
        ResolvedJavaMethod exceptionMethod = b.getMetaAccess().lookupJavaMethod(reportExceptionMethod);
        assert exceptionMethod.isStatic();
        b.handleReplacedInvoke(InvokeKind.Static, exceptionMethod, new ValueNode[]{messageNode}, false);
    }

    private static void traceConstant(ResolvedJavaMethod contextMethod, ResolvedJavaMethod reflectionMethod, String targetElement) {
        if (Options.ReflectionPluginTracing.getValue()) {
            System.out.println("Call to " + reflectionMethod.format("%H.%n(%p)") + " reached in " + contextMethod.format("%H.%n(%p)") +
                            " for target " + targetElement + " was reduced to a constant.");
        }
    }

    private static void traceException(ResolvedJavaMethod contextMethod, ResolvedJavaMethod reflectionMethod, String targetElement, Method exceptionMethod) {
        if (Options.ReflectionPluginTracing.getValue()) {
            String exception = exceptionMethod.getExceptionTypes()[0].getName();
            System.out.println("Call to " + reflectionMethod.format("%H.%n(%p)") + " reached in " + contextMethod.format("%H.%n(%p)") +
                            " for target " + targetElement + " was reduced to a \"throw new " + exception + "(...)\"");
        }
    }

}
