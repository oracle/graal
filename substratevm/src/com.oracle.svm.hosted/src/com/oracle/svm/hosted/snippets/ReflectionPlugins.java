/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.TrackDynamicAccessEnabled;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.DynamicAccessDetectionFeature;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageSystemClassLoader;
import com.oracle.svm.hosted.ReachabilityRegistrationNode;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.dynamicaccessinference.DynamicAccessInferenceLog;
import com.oracle.svm.hosted.dynamicaccessinference.StrictDynamicAccessInferenceFeature;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.TypeResult;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInlineOnlyInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.options.Option;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
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
    static class Options {
        @Option(help = "Enable trace logging for reflection plugins.")//
        static final HostedOptionKey<Boolean> ReflectionPluginTracing = new HostedOptionKey<>(false);
    }

    /**
     * Marker value for parameters that are null, to distinguish from "not able to {@link #unbox}".
     */
    private static final Object NULL_MARKER = new Object();

    private final ImageClassLoader imageClassLoader;
    private final AnnotationSubstitutionProcessor annotationSubstitutions;
    private final ClassInitializationPlugin classInitializationPlugin;
    private final AnalysisUniverse aUniverse;
    private final ParsingReason reason;
    private final FallbackFeature fallbackFeature;
    private final ClassInitializationSupport classInitializationSupport;
    private final boolean trackDynamicAccess;
    private final DynamicAccessDetectionFeature dynamicAccessDetectionFeature;
    private final DynamicAccessInferenceLog inferenceLog;
    private final SubstitutionReflectivityFilter reflectivityFilter;

    private ReflectionPlugins(ImageClassLoader imageClassLoader, AnnotationSubstitutionProcessor annotationSubstitutions,
                    ClassInitializationPlugin classInitializationPlugin, AnalysisUniverse aUniverse, ParsingReason reason, FallbackFeature fallbackFeature) {
        this.imageClassLoader = imageClassLoader;
        this.annotationSubstitutions = annotationSubstitutions;
        this.classInitializationPlugin = classInitializationPlugin;
        this.aUniverse = aUniverse;
        this.reason = reason;
        this.fallbackFeature = fallbackFeature;

        trackDynamicAccess = TrackDynamicAccessEnabled.isTrackDynamicAccessEnabled();
        dynamicAccessDetectionFeature = trackDynamicAccess ? DynamicAccessDetectionFeature.instance() : null;

        this.classInitializationSupport = (ClassInitializationSupport) ImageSingletons.lookup(RuntimeClassInitializationSupport.class);

        this.inferenceLog = DynamicAccessInferenceLog.singletonOrNull();

        this.reflectivityFilter = SubstitutionReflectivityFilter.singleton();
    }

    public static void registerInvocationPlugins(ImageClassLoader imageClassLoader, AnnotationSubstitutionProcessor annotationSubstitutions,
                    ClassInitializationPlugin classInitializationPlugin, InvocationPlugins plugins, AnalysisUniverse aUniverse, ParsingReason reason, FallbackFeature fallbackFeature) {
        ReflectionPlugins rp = new ReflectionPlugins(imageClassLoader, annotationSubstitutions, classInitializationPlugin, aUniverse, reason, fallbackFeature);
        rp.registerMethodHandlesPlugins(plugins);
        rp.registerClassPlugins(plugins);
    }

    /**
     * Guards plugin registrations that should be active *only* when the inference strategy for
     * dynamic access invocations is non-strict, i.e., the success of plugin applications may depend
     * on graph optimizations that can uncover more optimization potential. Since the non-strict
     * inference can lead to unstable results, i.e., a change in optimization level may lead to less
     * statically folded dynamic access invocations and subsequently to missing registration errors,
     * the intention is to transition all the reflection plugins to a strict invocation inference.
     * In the meantime, we only enforce the strict inference for the subset of the plugins that can
     * be handled by {@link StrictDynamicAccessInferenceFeature}.
     *
     * @return {@code true} if the inference should be unrestricted.
     */
    private boolean nonStrictDynamicAccessInference() {
        return !(StrictDynamicAccessInferenceFeature.isEnforced() && reason == ParsingReason.PointsToAnalysis);
    }

    private static final Object[] EMPTY_ARGUMENTS = {};

    private static final Class<?> VAR_FORM_CLASS = ReflectionUtil.lookupClass(false, "java.lang.invoke.VarForm");
    private static final Class<?> MEMBER_NAME_CLASS = ReflectionUtil.lookupClass(false, "java.lang.invoke.MemberName");

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
    private static final Set<Class<?>> ALLOWED_CONSTANT_CLASSES = Set.of(
                    Class.class, String.class, ClassLoader.class,
                    Method.class, Constructor.class, Field.class,
                    MethodHandle.class, MethodHandles.Lookup.class, MethodType.class,
                    VarHandle.class, VAR_FORM_CLASS, MEMBER_NAME_CLASS,
                    ByteOrder.class);

    private void registerMethodHandlesPlugins(InvocationPlugins plugins) {
        for (Class<?> clazz : List.of(Boolean.class, Byte.class, Short.class, Character.class, Integer.class, Long.class, Float.class, Double.class)) {
            registerFoldInvocationPlugins(plugins, false, clazz, "toString", "toBinaryString", "toOctalString", "toHexString");
        }
        registerFoldInvocationPlugins(plugins, false, String.class, "valueOf");

        registerFoldInvocationPlugins(plugins, false, MethodHandles.class,
                        "publicLookup", "privateLookupIn",
                        "arrayConstructor", "arrayLength", "arrayElementGetter", "arrayElementSetter", "arrayElementVarHandle",
                        "byteArrayViewVarHandle", "byteBufferViewVarHandle");

        registerFoldInvocationPlugins(plugins, false, MethodHandles.Lookup.class,
                        "in", "accessClass",
                        "unreflect", "unreflectSpecial", "unreflectConstructor",
                        "unreflectGetter", "unreflectSetter");

        if (nonStrictDynamicAccessInference()) {
            registerFoldInvocationPlugins(plugins, true, MethodHandles.Lookup.class,
                            "findStatic", "findVirtual", "findConstructor", "findClass", "findSpecial",
                            "findGetter", "findSetter", "findVarHandle", "findStaticGetter", "findStaticSetter");
        }

        registerFoldInvocationPlugins(plugins, false, MethodType.class,
                        "methodType", "genericMethodType",
                        "changeParameterType", "insertParameterTypes", "appendParameterTypes", "replaceParameterTypes", "dropParameterTypes",
                        "changeReturnType", "erase", "generic", "wrap", "unwrap",
                        "parameterType", "parameterCount", "returnType", "lastParameterType");

        registerFoldInvocationPlugins(plugins, false, MethodHandle.class, "asType");

        registerFoldInvocationPlugins(plugins, false, VAR_FORM_CLASS, "resolveMemberName");

        registerConditionalFoldInvocationPlugins(plugins);

        Registration mh = new Registration(plugins, MethodHandles.class);
        mh.register(new RequiredInlineOnlyInvocationPlugin("lookup") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                return processMethodHandlesLookup(b, targetMethod);
            }
        });

        Registration dmh = new Registration(plugins, "java.lang.invoke.MemberName");
        dmh.register(new RequiredInvocationPlugin("getDeclaringClass", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                JavaConstant constReceiver = receiver.get(false).asJavaConstant();
                if (constReceiver == null || constReceiver.isNull()) {
                    return false;
                }
                /*
                 * The clazz field of MemberName qualifies as stable except when an object is cloned
                 * and the new object's field is nulled. We should not observe it in that state.
                 */
                ResolvedJavaField clazzField = findField(targetMethod.getDeclaringClass(), "clazz");
                JavaConstant clazz = b.getConstantReflection().readFieldValue(clazzField, constReceiver);
                if (clazz == null || clazz.isNull()) {
                    return false;
                }
                b.push(JavaKind.Object, ConstantNode.forConstant(clazz, b.getMetaAccess(), b.getGraph()));
                return true;
            }
        });
    }

    private static ResolvedJavaField findField(ResolvedJavaType type, String name) {
        for (ResolvedJavaField field : type.getInstanceFields(false)) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        throw GraalError.shouldNotReachHere("Required field " + name + " not found in " + type); // ExcludeFromJacocoGeneratedReport
    }

    /**
     * For some methods check if folding an invocation using reflection, i.e., by executing the
     * target method and capturing the result, has undesired side effects, such as triggering
     * initialization of classes that should be initialized at run time. This is based on knowledge
     * about the reflection API methods implementation.
     */
    private void registerConditionalFoldInvocationPlugins(InvocationPlugins plugins) {
        if (nonStrictDynamicAccessInference()) {
            Method methodHandlesLookupFindStaticVarHandle = ReflectionUtil.lookupMethod(MethodHandles.Lookup.class, "findStaticVarHandle", Class.class, String.class, Class.class);
            registerFoldInvocationPlugin(plugins, methodHandlesLookupFindStaticVarHandle, (args) -> {
                /* VarHandles.makeFieldHandle() triggers init of receiver class (JDK-8291065). */
                Object classArg = args[0];
                if (classArg instanceof Class<?>) {
                    if (!classInitializationSupport.maybeInitializeAtBuildTime((Class<?>) classArg)) {
                        /* Skip the folding and register the field for run time reflection. */
                        if (reason.duringAnalysis()) {
                            Field field = ReflectionUtil.lookupField(true, (Class<?>) args[0], (String) args[1]);
                            if (field != null) {
                                RuntimeReflection.register(field);
                            }
                        }
                        return false;
                    }
                }
                return true;
            }, true);
        }

        Method methodHandlesLookupUnreflectVarHandle = ReflectionUtil.lookupMethod(MethodHandles.Lookup.class, "unreflectVarHandle", Field.class);
        registerFoldInvocationPlugin(plugins, methodHandlesLookupUnreflectVarHandle, (args) -> {
            /*
             * VarHandles.makeFieldHandle() triggers init of static field's declaring class
             * (JDK-8291065).
             */
            Object fieldArg = args[0];
            if (fieldArg instanceof Field) {
                Field field = (Field) fieldArg;
                if (isStatic(field) && !classInitializationSupport.maybeInitializeAtBuildTime(field.getDeclaringClass())) {
                    /* Skip the folding and register the field for run time reflection. */
                    if (reason.duringAnalysis()) {
                        RuntimeReflection.register(field);
                    }
                    return false;
                }
            }
            return true;
        }, false);
    }

    private void registerClassPlugins(InvocationPlugins plugins) {
        if (nonStrictDynamicAccessInference()) {
            registerFoldInvocationPlugins(plugins, true, Class.class,
                            "getField", "getMethod", "getConstructor",
                            "getDeclaredField", "getDeclaredMethod", "getDeclaredConstructor");
        }

        /*
         * The class sun.nio.ch.Reflect contains various reflection lookup methods that then pass
         * parameters through to the actual methods in java.lang.Class. But they do additional
         * things like calling setAccessible(true), so method inlining before analysis cannot
         * constant-fold them automatically. So we register them manually here for folding too.
         */
        registerFoldInvocationPlugins(plugins, false, ReflectionUtil.lookupClass(false, "sun.nio.ch.Reflect"),
                        "lookupConstructor", "lookupMethod", "lookupField");

        if (nonStrictDynamicAccessInference() && MissingRegistrationUtils.throwMissingRegistrationErrors() && reason.duringAnalysis() && reason != ParsingReason.JITCompilation) {
            registerBulkInvocationPlugin(plugins, Class.class, "getClasses", RuntimeReflection::registerAllClasses);
            registerBulkInvocationPlugin(plugins, Class.class, "getDeclaredClasses", RuntimeReflection::registerAllDeclaredClasses);
            registerBulkInvocationPlugin(plugins, Class.class, "getConstructors", RuntimeReflection::registerAllConstructors);
            registerBulkInvocationPlugin(plugins, Class.class, "getDeclaredConstructors", RuntimeReflection::registerAllDeclaredConstructors);
            registerBulkInvocationPlugin(plugins, Class.class, "getFields", RuntimeReflection::registerAllFields);
            registerBulkInvocationPlugin(plugins, Class.class, "getDeclaredFields", RuntimeReflection::registerAllDeclaredFields);
            registerBulkInvocationPlugin(plugins, Class.class, "getMethods", RuntimeReflection::registerAllMethods);
            registerBulkInvocationPlugin(plugins, Class.class, "getDeclaredMethods", RuntimeReflection::registerAllDeclaredMethods);
            registerBulkInvocationPlugin(plugins, Class.class, "getNestMembers", RuntimeReflection::registerAllNestMembers);
            registerBulkInvocationPlugin(plugins, Class.class, "getPermittedSubclasses", RuntimeReflection::registerAllPermittedSubclasses);
            registerBulkInvocationPlugin(plugins, Class.class, "getRecordComponents", RuntimeReflection::registerAllRecordComponents);
            registerBulkInvocationPlugin(plugins, Class.class, "getSigners", RuntimeReflection::registerAllSigners);
        }

        Registration r = new Registration(plugins, Class.class);
        if (nonStrictDynamicAccessInference()) {
            r.register(new RequiredInlineOnlyInvocationPlugin("forName", String.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode nameNode) {
                    ClassLoader loader;
                    if (ClassForNameSupport.respectClassLoader()) {
                        Class<?> callerClass = OriginalClassProvider.getJavaClass(b.getMethod().getDeclaringClass());
                        loader = callerClass.getClassLoader();
                    } else {
                        loader = imageClassLoader.getClassLoader();
                    }
                    return processClassForName(b, targetMethod, nameNode, ConstantNode.forBoolean(true), loader);
                }
            });
            r.register(new RequiredInlineOnlyInvocationPlugin("forName", String.class, boolean.class, ClassLoader.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode nameNode, ValueNode initializeNode, ValueNode classLoaderNode) {
                    ClassLoader loader;
                    if (ClassForNameSupport.respectClassLoader()) {
                        if (!classLoaderNode.isJavaConstant()) {
                            return false;
                        }
                        loader = (ClassLoader) unboxObjectConstant(b, classLoaderNode.asJavaConstant());
                        if (loader == NativeImageSystemClassLoader.singleton().defaultSystemClassLoader) {
                            /*
                             * The run time's application class loader is the build time's image
                             * class loader.
                             */
                            loader = imageClassLoader.getClassLoader();
                        }
                    } else {
                        /*
                         * When we ignore the ClassLoader parameter, we only intrinsify class names
                         * that are found by the ImageClassLoader, i.e., the application class
                         * loader at run time. We assume that every class loader used at run time
                         * delegates to the application class loader.
                         */
                        loader = imageClassLoader.getClassLoader();
                    }
                    return processClassForName(b, targetMethod, nameNode, initializeNode, loader);
                }
            });
        }
        r.register(new RequiredInlineOnlyInvocationPlugin("getClassLoader", Receiver.class) {
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
        if (StackTraceUtils.ignoredBySecurityStackWalk(b.getMetaAccess(), b.getMethod())) {
            /*
             * If our immediate caller (which is the only method available at the time the
             * invocation plugin is running) is not the method returned by
             * Reflection.getCallerClass(), we cannot intrinsify.
             */
            return false;
        }
        Class<?> callerClass = OriginalClassProvider.getJavaClass(b.getMethod().getDeclaringClass());
        MethodHandles.Lookup lookup;
        try {
            /* The constructor of Lookup is not public, so we need to invoke it via reflection. */
            lookup = LOOKUP_CONSTRUCTOR.newInstance(callerClass);
        } catch (Throwable ex) {
            return throwException(b, targetMethod, null, EMPTY_ARGUMENTS, ex.getClass(), ex.getMessage(), false);
        }
        return pushConstant(b, targetMethod, null, EMPTY_ARGUMENTS, JavaKind.Object, lookup, false, false) != null;
    }

    /**
     * We cannot invoke Class.forName directly via reflection because we need to use the
     * {@link ImageClassLoader} to look up the class name, not the class loader that loaded the
     * native image generator.
     */
    private boolean processClassForName(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode nameNode, ValueNode initializeNode, ClassLoader loader) {
        Object classNameValue = unbox(b, nameNode, JavaKind.Object);
        Object initializeValue = unbox(b, initializeNode, JavaKind.Boolean);

        if (!(classNameValue instanceof String) || !(initializeValue instanceof Boolean)) {
            return false;
        }
        String className = (String) classNameValue;
        boolean initialize = (Boolean) initializeValue;

        Object[] arguments = targetMethod.getParameters().length == 1
                        ? new Object[]{className}
                        : new Object[]{className, initialize, ClassForNameSupport.respectClassLoader() ? loader : DynamicAccessInferenceLog.ignoreArgument()};

        TypeResult<Class<?>> typeResult = ImageClassLoader.findClass(className, false, loader);
        if (!typeResult.isPresent()) {
            if (RuntimeClassLoading.isSupported()) {
                return false;
            }
            Throwable e = typeResult.getException();
            return throwException(b, targetMethod, null, arguments, e.getClass(), e.getMessage(), true);
        }
        Class<?> clazz = typeResult.get();
        if (PredefinedClassesSupport.isPredefined(clazz)) {
            return false;
        }

        JavaConstant classConstant = pushConstant(b, targetMethod, null, arguments, JavaKind.Object, clazz, false, true);
        if (classConstant == null) {
            return false;
        }

        if (initialize) {
            classInitializationPlugin.apply(b, b.getMetaAccess().lookupJavaType(clazz), () -> null);
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

        // GR-57649 generalize code if needed in more places
        ClassLoader loader = clazz.getClassLoader();
        JavaConstant result;
        if (loader == null) {
            result = JavaConstant.NULL_POINTER;
        } else {
            result = getIntrinsicConstant(b, loader);
        }

        if (result != null) {
            b.addPush(JavaKind.Object, ConstantNode.forConstant(result, b.getMetaAccess()));
            traceConstant(b, targetMethod, clazz, EMPTY_ARGUMENTS, result, false);
            return true;
        }

        return false;
    }

    /**
     * Helper to register all declared methods by name only, to avoid listing all the complete
     * parameter types. It also simplifies handling of different JDK versions, because methods not
     * yet available in JDK 8 (like VarHandle methods) are silently ignored.
     */
    private void registerFoldInvocationPlugins(InvocationPlugins plugins, boolean subjectToStrictDynamicAccessInference, Class<?> declaringClass, String... methodNames) {
        Set<String> methodNamesSet = new HashSet<>(Arrays.asList(methodNames));
        ModuleSupport.accessModuleByClass(ModuleSupport.Access.OPEN, ReflectionPlugins.class, declaringClass);
        for (Method method : declaringClass.getDeclaredMethods()) {
            if (methodNamesSet.contains(method.getName()) && !method.isSynthetic()) {
                registerFoldInvocationPlugin(plugins, method, subjectToStrictDynamicAccessInference);
            }
        }
    }

    private static final Predicate<Object[]> alwaysAllowConstantFolding = _ -> true;

    private void registerFoldInvocationPlugin(InvocationPlugins plugins, Method reflectionMethod, boolean subjectToStrictDynamicAccessInference) {
        registerFoldInvocationPlugin(plugins, reflectionMethod, alwaysAllowConstantFolding, subjectToStrictDynamicAccessInference);
    }

    private void registerFoldInvocationPlugin(InvocationPlugins plugins, Method reflectionMethod, Predicate<Object[]> allowConstantFolding, boolean subjectToStrictDynamicAccessInference) {
        if (!isAllowedReturnType(reflectionMethod.getReturnType())) {
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
                return foldInvocationUsingReflection(b, targetMethod, reflectionMethod, receiver, args, allowConstantFolding, subjectToStrictDynamicAccessInference);
            }
        });
    }

    private static boolean isAllowedReturnType(Class<?> returnType) {
        return ALLOWED_CONSTANT_CLASSES.contains(returnType) || returnType.isPrimitive();
    }

    private boolean foldInvocationUsingReflection(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Method reflectionMethod, Receiver receiver, ValueNode[] args,
                    Predicate<Object[]> allowConstantFolding, boolean subjectToStrictDynamicAccessInference) {
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

        Object returnValue;
        try {
            returnValue = reflectionMethod.invoke(receiverValue, argValues);
        } catch (InvocationTargetException ex) {
            return throwException(b, targetMethod, receiverValue, argValues, ex.getTargetException().getClass(), ex.getTargetException().getMessage(), subjectToStrictDynamicAccessInference);
        } catch (Throwable ex) {
            return throwException(b, targetMethod, receiverValue, argValues, ex.getClass(), ex.getMessage(), subjectToStrictDynamicAccessInference);
        }

        JavaKind returnKind = targetMethod.getSignature().getReturnKind();
        if (returnKind == JavaKind.Void) {
            /*
             * The target method is a side-effect free void method that did not throw an exception.
             */
            traceConstant(b, targetMethod, receiverValue, argValues, JavaKind.Void, subjectToStrictDynamicAccessInference);
            return true;
        }

        return pushConstant(b, targetMethod, receiverValue, argValues, returnKind, returnValue, false, subjectToStrictDynamicAccessInference) != null;
    }

    private <T> void registerBulkInvocationPlugin(InvocationPlugins plugins, Class<T> declaringClass, String methodName, Consumer<T> registrationCallback) {
        plugins.register(declaringClass, new RequiredInvocationPlugin(methodName, new Class<?>[]{Receiver.class}) {
            @Override
            public boolean isDecorator() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                VMError.guarantee(!targetMethod.isStatic(), "Bulk reflection queries are not static");
                return registerConstantBulkReflectionQuery(b, receiver, registrationCallback);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <T> boolean registerConstantBulkReflectionQuery(GraphBuilderContext b, Receiver receiver, Consumer<T> registrationCallback) {
        /*
         * Calling receiver.get(true) can add a null check guard, i.e., modifying the graph in the
         * process. It is an error for invocation plugins that do not replace the call to modify the
         * graph.
         */
        Object receiverValue = unbox(b, receiver.get(false), JavaKind.Object);
        if (receiverValue == null || receiverValue == NULL_MARKER) {
            return false;
        }

        b.add(ReachabilityRegistrationNode.create(() -> {
            registerForRuntimeReflection((T) receiverValue, registrationCallback);
            if (trackDynamicAccess) {
                dynamicAccessDetectionFeature.addFoldEntry(b.bci(), b.getMethod());
            }
        }, reason));
        return true;
    }

    private <T> void registerForRuntimeReflection(T receiver, Consumer<T> registrationCallback) {
        try {
            registrationCallback.accept(receiver);
            if (fallbackFeature != null) {
                fallbackFeature.ignoreReflectionFallback = true;
            }
        } catch (LinkageError e) {
            // Ignore, the call should be registered manually
        }
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
            return SubstrateGraphBuilderPlugins.extractClassArray(b, annotationSubstitutions, arg, true);
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
                throw VMError.shouldNotReachHereUnexpectedInput(argKind); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static Object unboxObjectConstant(GraphBuilderContext b, JavaConstant argConstant) {
        ResolvedJavaType javaType = b.getConstantReflection().asJavaType(argConstant);
        if (javaType != null) {
            /*
             * Get the Class object corresponding to the receiver of the reflective call. If the
             * class is substituted we want the original class, and not the substitution. The
             * reflective call will yield the original member, which will be intrinsified, and
             * subsequent phases are responsible for getting the right substitution.
             */
            return OriginalClassProvider.getJavaClass(javaType);
        }

        /* Any other object that is not a Class. */
        Object result = b.getSnippetReflection().asObject(Object.class, argConstant);
        if (result != null && isAllowedConstant(result.getClass())) {
            return result;
        }
        return null;
    }

    private static boolean isAllowedConstant(Class<?> clazz) {
        for (var allowed : ALLOWED_CONSTANT_CLASSES) {
            if (allowed.isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;
    }

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
    private <T> T getIntrinsic(T element) {
        if (reason == ParsingReason.AutomaticUnsafeTransformation || reason == ParsingReason.EarlyClassInitializerAnalysis) {
            /* We are analyzing the static initializers and should always intrinsify. */
            return element;
        }
        if (element instanceof AnnotatedElement annotatedElement && reflectivityFilter.shouldExcludeElement(annotatedElement)) {
            /*
             * Should not intrinsify. Will fail during the reflective lookup at runtime. @Delete-ed
             * elements are ignored by the reflection plugins regardless of the value of
             * ReportUnsupportedElementsAtRuntime.
             */
            return null;
        }
        return (T) aUniverse.replaceObject(element);
    }

    /**
     * Same as {@link #getIntrinsic}, but returns a {@link JavaConstant}.
     */
    private JavaConstant getIntrinsicConstant(GraphBuilderContext context, Object element) {
        if (reason == ParsingReason.AutomaticUnsafeTransformation || reason == ParsingReason.EarlyClassInitializerAnalysis) {
            /* We are analyzing the static initializers and should always intrinsify. */
            return context.getSnippetReflection().forObject(element);
        }
        if (element instanceof AnnotatedElement annotatedElement && reflectivityFilter.shouldExcludeElement(annotatedElement)) {
            /*
             * Should not intrinsify. Will fail during the reflective lookup at runtime. @Delete-ed
             * elements are ignored by the reflection plugins regardless of the value of
             * ReportUnsupportedElementsAtRuntime.
             */
            return null;
        }
        return aUniverse.replaceObjectWithConstant(element, context.getSnippetReflection()::forObject);
    }

    private JavaConstant pushConstant(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Object receiver, Object[] arguments, JavaKind returnKind, Object returnValue,
                    boolean allowNullReturnValue, boolean subjectToStrictDynamicAccessInference) {
        Object intrinsicValue = getIntrinsic(returnValue == null && allowNullReturnValue ? NULL_MARKER : returnValue);
        if (intrinsicValue == null) {
            return null;
        }

        JavaConstant intrinsicConstant;
        if (returnKind.isPrimitive()) {
            intrinsicConstant = JavaConstant.forBoxedPrimitive(intrinsicValue);
        } else if (intrinsicValue == NULL_MARKER) {
            intrinsicConstant = JavaConstant.NULL_POINTER;
        } else {
            intrinsicConstant = b.getSnippetReflection().forObject(intrinsicValue);
        }

        b.addPush(returnKind, ConstantNode.forConstant(intrinsicConstant, b.getMetaAccess()));
        traceConstant(b, targetMethod, receiver, arguments, intrinsicValue, subjectToStrictDynamicAccessInference);
        return intrinsicConstant;
    }

    private boolean throwException(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Object receiver, Object[] arguments, Class<? extends Throwable> exceptionClass, String originalMessage,
                    boolean subjectToStrictDynamicAccessInference) {
        /* Get the exception throwing method that has a message parameter. */
        Method exceptionMethod = ExceptionSynthesizer.throwExceptionMethodOrNull(exceptionClass, String.class);
        if (exceptionMethod == null) {
            return false;
        }
        Method intrinsic = getIntrinsic(exceptionMethod);
        if (intrinsic == null) {
            return false;
        }

        /*
         * Because tracing can add a ReachabilityRegistrationNode to the graph, it has to happen
         * before exception synthesis.
         */
        traceException(b, targetMethod, receiver, arguments, exceptionClass, subjectToStrictDynamicAccessInference);

        String message = originalMessage + ". This exception was synthesized during native image building from a call to " + targetMethod.format("%H.%n(%p)") +
                        " with constant arguments.";
        ExceptionSynthesizer.throwException(b, exceptionMethod, message);
        return true;
    }

    /**
     * Log successful constant folding of an invocation.
     *
     * @param subjectToStrictDynamicAccessInference if {@code true} log successful folding
     *            information via {@link DynamicAccessInferenceLog} for {@code targetMethod}
     *            invocations that are also handled by {@link StrictDynamicAccessInferenceFeature}.
     *            In that case the reflection plugin registration is also guarded by
     *            {@link ReflectionPlugins#nonStrictDynamicAccessInference()}. In other words: this
     *            produces a report with all dynamic invocations that would be handled by
     *            {@link StrictDynamicAccessInferenceFeature} if it was enabled. Additionally, it
     *            avoids logging and warning for non-strict folding of invocations which are not
     *            reflective, such as {@link Integer#toString()}.
     */
    private void traceConstant(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Object receiver, Object[] arguments, Object value, boolean subjectToStrictDynamicAccessInference) {
        if (subjectToStrictDynamicAccessInference && inferenceLog != null) {
            inferenceLog.logFolding(b, reason, targetMethod, receiver, arguments, value);
        }
        if (Options.ReflectionPluginTracing.getValue()) {
            String receiverAndArguments = buildReceiverAndArgumentsString(receiver, arguments);
            System.out.printf("Call to %s reached in %s with %s was reduced to the constant %s%n",
                            targetMethod.format("%H.%n(%p)"), b.getMethod().format("%H.%n(%p)"), receiverAndArguments, value);
        }
    }

    /**
     * Log constant folding of an invocation which was inferred to throw an exception at run-time.
     */
    private void traceException(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Object receiver, Object[] arguments, Class<? extends Throwable> exceptionClass,
                    boolean subjectToStrictDynamicAccessInference) {
        if (subjectToStrictDynamicAccessInference && inferenceLog != null) {
            inferenceLog.logException(b, reason, targetMethod, receiver, arguments, exceptionClass);
        }
        if (Options.ReflectionPluginTracing.getValue()) {
            String receiverAndArguments = buildReceiverAndArgumentsString(receiver, arguments);
            System.out.printf("Call to %s reached in %s with %s was reduced to a \"throw new %s(...)\"%n",
                            targetMethod.format("%H.%n(%p)"), b.getMethod().format("%H.%n(%p)"), receiverAndArguments, exceptionClass.getName());
        }
    }

    private static String buildReceiverAndArgumentsString(Object receiver, Object[] arguments) {
        String argumentListString = Stream.of(arguments)
                        .map(arg -> arg instanceof Object[] array ? Arrays.toString(array) : Objects.toString(arg))
                        .collect(Collectors.joining(", "));
        return receiver != null
                        ? String.format("receiver \"%s\" and arguments (%s)", receiver, argumentListString)
                        : String.format("arguments (%s)", argumentListString);
    }
}
