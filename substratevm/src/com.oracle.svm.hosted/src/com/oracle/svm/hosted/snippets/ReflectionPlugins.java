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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.DeletedElementException;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Performs constant folding of methods that perform reflection lookups when all arguments are
 * compile-time constants, e.g., for {@link Method}, {@link MethodHandle}, or {@code VarHandle}
 * instances. This avoids manual registration of these elements using a reflection configuration
 * file.
 * 
 * One important assumption made in this class is that the return types of all folded methods do not
 * have object identity, i.e., it is allowed to return a cached object instead of creating a new
 * object at every invocation. While the types {@link #ALLOWED_CONSTANT_CLASSES we allow} are not
 * explicitly specified in the JDK to have no object identity, there are enough caches also in the
 * JDK so that any code that would rely on object identity is error-prone on any JVM.
 */
public final class ReflectionPlugins {
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

    private final ImageClassLoader imageClassLoader;
    private final SnippetReflectionProvider snippetReflection;
    private final AnnotationSubstitutionProcessor annotationSubstitutions;
    private final ClassInitializationPlugin classInitializationPlugin;
    private final AnalysisUniverse aUniverse;
    private final ParsingReason reason;

    private ReflectionPlugins(ImageClassLoader imageClassLoader, SnippetReflectionProvider snippetReflection, AnnotationSubstitutionProcessor annotationSubstitutions,
                    ClassInitializationPlugin classInitializationPlugin, AnalysisUniverse aUniverse, ParsingReason reason) {
        this.imageClassLoader = imageClassLoader;
        this.snippetReflection = snippetReflection;
        this.annotationSubstitutions = annotationSubstitutions;
        this.classInitializationPlugin = classInitializationPlugin;
        this.aUniverse = aUniverse;
        this.reason = reason;
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

    /**
     * Classes that are allowed to be constant folded for Object parameters. We must be careful and
     * return only objects of classes that are "immutable enough", i.e., cannot change their
     * meaning. Otherwise, the object could be modified between the intrinsification at image build
     * time and the actual method invocation at run time.
     * 
     * Note that many of the classes are not completely immutable because they have lazily
     * initialized caches, or the "accessible" flag of reflection objects. That is OK, because these
     * mutable fields do not affect the outcome of any of the methods that we register for constant
     * folding.
     * 
     * Adding an array type of a Java collection class to this list is always wrong, because those
     * are never immutable.
     */
    private static final Set<Class<?>> ALLOWED_CONSTANT_CLASSES = new HashSet<>(Arrays.asList(
                    Class.class, String.class, ClassLoader.class,
                    Method.class, Constructor.class, Field.class,
                    MethodHandle.class, MethodHandles.Lookup.class, MethodType.class,
                    VarHandle.class,
                    ByteOrder.class));

    private void registerMethodHandlesPlugins(InvocationPlugins plugins) {

        registerFoldInvocationPlugins(plugins, MethodHandles.class,
                        "publicLookup", "privateLookupIn",
                        "arrayConstructor", "arrayLength", "arrayElementGetter", "arrayElementSetter", "arrayElementVarHandle",
                        "byteArrayViewVarHandle", "byteBufferViewVarHandle");

        registerFoldInvocationPlugins(plugins, MethodHandles.Lookup.class,
                        "in",
                        "findStatic", "findVirtual", "findConstructor", "findClass", "accessClass", "findSpecial",
                        "findGetter", "findSetter", "findVarHandle",
                        "findStaticGetter", "findStaticSetter",
                        "unreflect", "unreflectSpecial", "unreflectConstructor",
                        "unreflectGetter", "unreflectSetter");

        registerFoldInvocationPlugins(plugins, MethodType.class,
                        "methodType", "genericMethodType",
                        "changeParameterType", "insertParameterTypes", "appendParameterTypes", "replaceParameterTypes", "dropParameterTypes",
                        "changeReturnType", "erase", "generic", "wrap", "unwrap",
                        "parameterType", "parameterCount", "returnType", "lastParameterType");

        registerConditionalFoldInvocationPlugins(plugins);

        Registration r = new Registration(plugins, MethodHandles.class);
        r.register(new RequiredInlineOnlyInvocationPlugin("lookup") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                return processMethodHandlesLookup(b, targetMethod);
            }
        });
    }

    /**
     * For some methods check if folding an invocation using reflection, i.e., by executing the
     * target method and capturing the result, has undesired side effects, such as triggering
     * initialization of classes that should be initialized at run time. This is based on knowledge
     * about the reflection API methods implementation.
     */
    private void registerConditionalFoldInvocationPlugins(InvocationPlugins plugins) {
        Method methodHandlesLookupFindStaticVarHandle = ReflectionUtil.lookupMethod(MethodHandles.Lookup.class, "findStaticVarHandle", Class.class, String.class, Class.class);
        registerFoldInvocationPlugin(plugins, methodHandlesLookupFindStaticVarHandle, (args) -> {
            /* VarHandles.makeFieldHandle() triggers init of receiver class (JDK-8291065). */
            Object classArg = args[0];
            if (classArg instanceof Class<?>) {
                if (shouldInitializeAtRuntime((Class<?>) classArg)) {
                    /* Skip the folding and register the field for run time reflection. */
                    if (reason == ParsingReason.PointsToAnalysis) {
                        Field field = ReflectionUtil.lookupField(true, (Class<?>) args[0], (String) args[1]);
                        if (field != null) {
                            RuntimeReflection.register(field);
                        }
                    }
                    return false;
                }
            }
            return true;
        });

        Method methodHandlesLookupUnreflectVarHandle = ReflectionUtil.lookupMethod(MethodHandles.Lookup.class, "unreflectVarHandle", Field.class);
        registerFoldInvocationPlugin(plugins, methodHandlesLookupUnreflectVarHandle, (args) -> {
            /*
             * VarHandles.makeFieldHandle() triggers init of static field's declaring class
             * (JDK-8291065).
             */
            Object fieldArg = args[0];
            if (fieldArg instanceof Field) {
                Field field = (Field) fieldArg;
                if (isStatic(field) && shouldInitializeAtRuntime(field.getDeclaringClass())) {
                    /* Skip the folding and register the field for run time reflection. */
                    if (reason == ParsingReason.PointsToAnalysis) {
                        RuntimeReflection.register(field);
                    }
                    return false;
                }
            }
            return true;
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

        TypeResult<Class<?>> typeResult = imageClassLoader.findClass(className, false);
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

    private static final Predicate<Object[]> alwaysAllowConstantFolding = args -> true;

    private void registerFoldInvocationPlugin(InvocationPlugins plugins, Method reflectionMethod) {
        registerFoldInvocationPlugin(plugins, reflectionMethod, alwaysAllowConstantFolding);
    }

    private void registerFoldInvocationPlugin(InvocationPlugins plugins, Method reflectionMethod, Predicate<Object[]> allowConstantFolding) {
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
                return foldInvocationUsingReflection(b, targetMethod, reflectionMethod, receiver, args, allowConstantFolding);
            }
        });
    }

    private boolean foldInvocationUsingReflection(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Method reflectionMethod, Receiver receiver, ValueNode[] args,
                    Predicate<Object[]> allowConstantFolding) {
        assert b.getMetaAccess().lookupJavaMethod(reflectionMethod).equals(targetMethod) : "Fold method mismatch: " + reflectionMethod + " != " + targetMethod;

        Object receiverValue;
        if (targetMethod.isStatic()) {
            receiverValue = null;
        } else {
            /*
             * Calling receiver.get(true) can add a null check guard, i.e., modifying the graph in
             * the process. It is an error for invocation plugins that do not replace the call to
             * modify the graph.
             */
            receiverValue = unbox(b, receiver.get(false), JavaKind.Object);
            if (receiverValue == null || receiverValue == NULL_MARKER) {
                return false;
            }
        }

        Object[] argValues = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object argValue = unbox(b, args[i], targetMethod.getSignature().getParameterKind(i));
            if (argValue == null) {
                return false;
            } else if (argValue == NULL_MARKER) {
                argValues[i] = null;
            } else {
                argValues[i] = argValue;
            }
        }

        if (!allowConstantFolding.test(argValues)) {
            return false;
        }

        /* String representation of the parameters for debug printing. */
        Supplier<String> targetParameters = () -> (receiverValue == null ? "" : receiverValue.toString() + "; ") +
                        Stream.of(argValues).map(arg -> arg instanceof Object[] ? Arrays.toString((Object[]) arg) : Objects.toString(arg)).collect(Collectors.joining(", "));

        Object returnValue;
        try {
            returnValue = reflectionMethod.invoke(receiverValue, argValues);
        } catch (InvocationTargetException ex) {
            return throwException(b, targetMethod, targetParameters, ex.getTargetException().getClass(), ex.getTargetException().getMessage());
        } catch (Throwable ex) {
            return throwException(b, targetMethod, targetParameters, ex.getClass(), ex.getMessage());
        }

        JavaKind returnKind = targetMethod.getSignature().getReturnKind();
        if (returnKind == JavaKind.Void) {
            /*
             * The target method is a side-effect free void method that did not throw an exception.
             */
            traceConstant(b, targetMethod, targetParameters, JavaKind.Void);
            return true;
        }

        return pushConstant(b, targetMethod, targetParameters, returnKind, returnValue, false) != null;
    }

    private static boolean shouldInitializeAtRuntime(Class<?> classArg) {
        ClassInitializationSupport classInitializationSupport = (ClassInitializationSupport) ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
        return classInitializationSupport.shouldInitializeAtRuntime(classArg);
    }

    private static boolean isStatic(Field field) {
        return Modifier.isStatic(field.getModifiers());
    }

    private Object unbox(GraphBuilderContext b, ValueNode arg, JavaKind argKind) {
        if (!arg.isJavaConstant()) {
            /*
             * If the argument is not a constant, we try to extract a varargs-parameter list for
             * Class[] arrays. This is used in many reflective lookup methods.
             */
            return SubstrateGraphBuilderPlugins.extractClassArray(annotationSubstitutions, snippetReflection, arg, true);
        }

        JavaConstant argConstant = arg.asJavaConstant();
        if (argConstant.isNull()) {
            return NULL_MARKER;
        }
        switch (argKind) {
            case Boolean:
                return argConstant.asInt() != 0L;
            case Byte:
                return (byte) argConstant.asInt();
            case Short:
                return (short) argConstant.asInt();
            case Char:
                return (char) argConstant.asInt();
            case Int:
                return argConstant.asInt();
            case Long:
                return argConstant.asLong();
            case Float:
                return argConstant.asFloat();
            case Double:
                return argConstant.asDouble();
            case Object:
                return unboxObjectConstant(b, argConstant);
            default:
                throw VMError.shouldNotReachHere();
        }
    }

    private Object unboxObjectConstant(GraphBuilderContext b, JavaConstant argConstant) {
        ResolvedJavaType javaType = b.getConstantReflection().asJavaType(argConstant);
        if (javaType != null) {
            /*
             * Get the Class object corresponding to the receiver of the reflective call. If the
             * class is substituted we want the original class, and not the substitution. The
             * reflective call will yield the original member, which will be intrinsified, and
             * subsequent phases are responsible for getting the right substitution.
             */
            return OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(), javaType);
        }

        /* Any other object that is not a Class. */
        Object result = snippetReflection.asObject(Object.class, argConstant);
        if (ALLOWED_CONSTANT_CLASSES.contains(result.getClass())) {
            return result;
        }
        return null;
    }

    private final boolean parseOnce = SubstrateOptions.parseOnce();

    /**
     * This method checks if the element should be intrinsified and returns the cached intrinsic
     * element if found. Caching intrinsic elements during analysis and reusing the same element
     * during compilation is important! For each call to Class.getMethod/Class.getField the JDK
     * returns a copy of the original object. Many of the reflection metadata fields are lazily
     * initialized, therefore the copy is partial. During analysis we use the
     * ReflectionMetadataFeature::replacer to ensure that the reflection metadata is eagerly
     * initialized. Therefore, we want to intrinsify the same, eagerly initialized object during
     * compilation, not a lossy copy of it.
     */
    @SuppressWarnings("unchecked")
    private <T> T getIntrinsic(GraphBuilderContext context, T element) {
        if (reason == ParsingReason.UnsafeSubstitutionAnalysis || reason == ParsingReason.EarlyClassInitializerAnalysis) {
            /* We are analyzing the static initializers and should always intrinsify. */
            return element;
        }
        /* We don't intrinsify if bci is not unique. */
        if (context.bciCanBeDuplicated()) {
            return null;
        }
        if (parseOnce || reason == ParsingReason.PointsToAnalysis) {
            if (isDeleted(element, context.getMetaAccess())) {
                /*
                 * Should not intrinsify. Will fail during the reflective lookup at
                 * runtime. @Delete-ed elements are ignored by the reflection plugins regardless of
                 * the value of ReportUnsupportedElementsAtRuntime.
                 */
                return null;
            }

            Object replaced = aUniverse.replaceObject(element);

            if (parseOnce) {
                /* No separate parsing for compilation, so no need to cache the result. */
                return (T) replaced;
            }

            /* During parsing for analysis we intrinsify and cache the result for compilation. */
            ImageSingletons.lookup(ReflectionPluginRegistry.class).add(context.getMethod(), context.bci(), replaced);
        }
        /* During parsing for compilation we only intrinsify if intrinsified during analysis. */
        return ImageSingletons.lookup(ReflectionPluginRegistry.class).get(context.getMethod(), context.bci());
    }

    private static <T> boolean isDeleted(T element, MetaAccessProvider metaAccess) {
        AnnotatedElement annotated = null;
        try {
            if (element instanceof Executable) {
                annotated = metaAccess.lookupJavaMethod((Executable) element);
            } else if (element instanceof Field) {
                annotated = metaAccess.lookupJavaField((Field) element);
            }
        } catch (DeletedElementException ex) {
            /*
             * If ReportUnsupportedElementsAtRuntime is *not* set looking up a @Delete-ed element
             * will result in a DeletedElementException.
             */
            return true;
        }
        /*
         * If ReportUnsupportedElementsAtRuntime is set looking up a @Delete-ed element will return
         * a substitution method that has the @Delete annotation.
         */
        if (annotated != null && annotated.isAnnotationPresent(Delete.class)) {
            return true;
        }
        return false;
    }

    private JavaConstant pushConstant(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Supplier<String> targetParameters, JavaKind returnKind, Object returnValue,
                    boolean allowNullReturnValue) {
        Object intrinsicValue = getIntrinsic(b, returnValue == null && allowNullReturnValue ? NULL_MARKER : returnValue);
        if (intrinsicValue == null) {
            return null;
        }

        JavaConstant intrinsicConstant;
        if (returnKind.isPrimitive()) {
            intrinsicConstant = JavaConstant.forBoxedPrimitive(intrinsicValue);
        } else if (intrinsicValue == NULL_MARKER) {
            intrinsicConstant = JavaConstant.NULL_POINTER;
        } else {
            intrinsicConstant = snippetReflection.forObject(intrinsicValue);
        }

        b.addPush(returnKind, ConstantNode.forConstant(intrinsicConstant, b.getMetaAccess()));
        traceConstant(b, targetMethod, targetParameters, intrinsicValue);
        return intrinsicConstant;
    }

    private boolean throwException(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Supplier<String> targetParameters, Class<? extends Throwable> exceptionClass, String originalMessage) {
        /* Get the exception throwing method that has a message parameter. */
        Method exceptionMethod = ExceptionSynthesizer.throwExceptionMethodOrNull(exceptionClass, String.class);
        if (exceptionMethod == null) {
            return false;
        }
        Method intrinsic = getIntrinsic(b, exceptionMethod);
        if (intrinsic == null) {
            return false;
        }

        String message = originalMessage + ". This exception was synthesized during native image building from a call to " + targetMethod.format("%H.%n(%p)") +
                        " with constant arguments.";
        ExceptionSynthesizer.throwException(b, exceptionMethod, message);
        traceException(b, targetMethod, targetParameters, exceptionClass);
        return true;
    }

    private static void traceConstant(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Supplier<String> targetParameters, Object value) {
        if (Options.ReflectionPluginTracing.getValue()) {
            System.out.println("Call to " + targetMethod.format("%H.%n(%p)") +
                            " reached in " + b.getMethod().format("%H.%n(%p)") +
                            " with parameters (" + targetParameters.get() + ")" +
                            " was reduced to the constant " + value);
        }
    }

    private static void traceException(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Supplier<String> targetParameters, Class<? extends Throwable> exceptionClass) {
        if (Options.ReflectionPluginTracing.getValue()) {
            System.out.println("Call to " + targetMethod.format("%H.%n(%p)") +
                            " reached in " + b.getMethod().format("%H.%n(%p)") +
                            " with parameters (" + targetParameters.get() + ")" +
                            " was reduced to a \"throw new " + exceptionClass.getName() + "(...)\"");
        }
    }
}
