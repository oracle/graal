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
package com.oracle.svm.hosted.dynamicaccessinference;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.ReachabilityCallbackNode;
import com.oracle.svm.hosted.substitute.DeletedElementException;
import com.oracle.svm.util.AnnotationUtil;
import com.oracle.svm.util.GraalAccess;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.TypeResult;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.annotation.Annotated;

/**
 * Feature for controlling the optimization independent inference of invocations which would
 * otherwise require an explicit reachability metadata entry. Unlike the graph-level inference
 * scheme, this restricted inference is executed directly on method bytecode and does not depend on
 * the inlining done by {@link com.oracle.graal.pointsto.phases.InlineBeforeAnalysis} and other IR
 * graph optimizations.
 */
@AutomaticallyRegisteredFeature
public final class StrictDynamicAccessInferenceFeature implements InternalFeature {

    static class Options {

        enum Mode {
            Disable,
            Warn,
            Enforce
        }

        @Option(help = """
                        Select the mode for the strict, build-time inference for invocations requiring dynamic access.
                        Possible values are:
                         "Disable" (default): Disable the strict mode and fall back to the optimization dependent inference for dynamic invocations;
                         "Warn": Use optimization dependent inference for dynamic invocations, but print a warning for invocations inferred outside of the strict mode;
                         "Enforce": Infer only dynamic invocations proven to be constant in the strict inference mode.""", stability = OptionStability.EXPERIMENTAL)//
        static final HostedOptionKey<Mode> StrictDynamicAccessInference = new HostedOptionKey<>(Mode.Disable);
    }

    private ClassLoader applicationClassLoader;
    private AnalysisUniverse analysisUniverse;

    private ConstantExpressionRegistry registry;
    private DynamicAccessInferenceLog inferenceLog;

    public static boolean isEnforced() {
        return Options.StrictDynamicAccessInference.getValue() == Options.Mode.Enforce;
    }

    public static boolean shouldWarn() {
        return Options.StrictDynamicAccessInference.getValue() == Options.Mode.Warn;
    }

    public static boolean isActive() {
        return isEnforced() || shouldWarn();
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return isActive();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return shouldWarn() ? List.of(DynamicAccessInferenceLogFeature.class) : List.of();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        FeatureImpl.AfterRegistrationAccessImpl accessImpl = (FeatureImpl.AfterRegistrationAccessImpl) access;
        applicationClassLoader = accessImpl.getApplicationClassLoader();
        ConstantExpressionAnalyzer analyzer = new ConstantExpressionAnalyzer(GraalAccess.getOriginalProviders(), applicationClassLoader);
        registry = new ConstantExpressionRegistry(analyzer);
        ImageSingletons.add(ConstantExpressionRegistry.class, registry);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        FeatureImpl.DuringSetupAccessImpl accessImpl = (FeatureImpl.DuringSetupAccessImpl) access;
        analysisUniverse = accessImpl.getUniverse();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        inferenceLog = DynamicAccessInferenceLog.singletonOrNull();
        /*
         * The strict dynamic access inference mode disables constant folding through method
         * inlining, which leads to <clinit> of sun.nio.ch.DatagramChannelImpl throwing a missing
         * reflection registration error. This is a temporary fix until annotation guided analysis
         * is implemented (GR-66140).
         *
         * An alternative to this approach would be creating invocation plugins for the methods
         * defined in jdk.internal.invoke.MhUtil.
         */
        registerFieldForReflectionIfExists(access, "sun.nio.ch.DatagramChannelImpl", "socket");
    }

    @SuppressWarnings("SameParameterValue")
    private static void registerFieldForReflectionIfExists(BeforeAnalysisAccess access, String className, String fieldName) {
        Class<?> clazz = ReflectionUtil.lookupClass(true, className);
        if (clazz == null) {
            return;
        }
        Field field = ReflectionUtil.lookupField(true, clazz, fieldName);
        if (field == null) {
            return;
        }
        access.registerReachabilityHandler(_ -> RuntimeReflection.register(field), clazz);
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        /*
         * Dynamic access inference should only be restricted during analysis. In other cases, we
         * keep the inference unrestricted, as is done in ReflectionPlugins.
         */
        if (isEnforced() && reason.duringAnalysis() && reason != ParsingReason.JITCompilation) {
            new StrictReflectionInferencePlugins().register(plugins, reason);
            new StrictResourceInferencePlugins().register(plugins, reason);
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (shouldWarn()) {
            warnForNonStrictInference();
        }
        /*
         * No more bytecode parsing should happen after analysis, so we can seal and clean up the
         * registry.
         */
        registry.seal();
    }

    private void warnForNonStrictInference() {
        List<DynamicAccessInferenceLog.LogEntry> unsafeFoldingEntries = StreamSupport.stream(inferenceLog.getEntries().spliterator(), false)
                        .filter(entry -> !entryIsInRegistry(entry, registry))
                        .toList();
        if (!unsafeFoldingEntries.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("The following dynamic access method invocations have been inferred outside of the strict inference mode:").append(System.lineSeparator());
            for (int i = 0; i < unsafeFoldingEntries.size(); i++) {
                sb.append((i + 1)).append(". ").append(unsafeFoldingEntries.get(i)).append(System.lineSeparator());
            }
            sb.delete(sb.length() - System.lineSeparator().length(), sb.length());
            LogUtils.warning(sb.toString());
        }
    }

    private static boolean entryIsInRegistry(DynamicAccessInferenceLog.LogEntry entry, ConstantExpressionRegistry registry) {
        BytecodePosition callLocation = entry.getCallLocation();
        ResolvedJavaMethod targetMethod = entry.getTargetMethod();
        if (targetMethod.hasReceiver()) {
            Object receiver = registry.getReceiver(callLocation.getMethod(), callLocation.getBCI(), targetMethod);
            if (entry.getReceiver() != DynamicAccessInferenceLog.ignoreArgument() && receiver == null) {
                return false;
            }
        }
        Object[] arguments = entry.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            Object argument = registry.getArgument(callLocation.getMethod(), callLocation.getBCI(), targetMethod, i);
            if (arguments[i] != DynamicAccessInferenceLog.ignoreArgument() && argument == null) {
                return false;
            }
        }
        return true;
    }

    private static Class<?>[] getArgumentTypesForPlugin(Method method) {
        ArrayList<Class<?>> argumentTypes = new ArrayList<>();
        if (!Modifier.isStatic(method.getModifiers())) {
            argumentTypes.add(InvocationPlugin.Receiver.class);
        }
        argumentTypes.addAll(Arrays.asList(method.getParameterTypes()));
        return argumentTypes.toArray(new Class<?>[0]);
    }

    private final class StrictReflectionInferencePlugins {

        public void register(GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
            registerClassPlugins(plugins, reason);
            registerMethodHandlePlugins(plugins, reason);
            registerProxyPlugins(plugins, reason);
        }

        private void registerClassPlugins(GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
            InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();

            registerForNameOnePlugin(plugins.getInvocationPlugins(), reason, plugins.getClassInitializationPlugin());
            registerForNameThreePlugin(plugins.getInvocationPlugins(), reason, plugins.getClassInitializationPlugin());

            Method getField = ReflectionUtil.lookupMethod(true, Class.class, "getField", String.class);
            Method getDeclaredField = ReflectionUtil.lookupMethod(true, Class.class, "getDeclaredField", String.class);

            Method getConstructor = ReflectionUtil.lookupMethod(true, Class.class, "getConstructor", Class[].class);
            Method getDeclaredConstructor = ReflectionUtil.lookupMethod(true, Class.class, "getDeclaredConstructor", Class[].class);

            Method getMethod = ReflectionUtil.lookupMethod(true, Class.class, "getMethod", String.class, Class[].class);
            Method getDeclaredMethod = ReflectionUtil.lookupMethod(true, Class.class, "getDeclaredMethod", String.class, Class[].class);

            Stream.of(getField, getDeclaredField, getConstructor, getDeclaredConstructor, getMethod, getDeclaredMethod)
                            .filter(Objects::nonNull)
                            .forEach(m -> registerFoldingPlugin(invocationPlugins, reason, m));

            registerBulkPlugin(invocationPlugins, reason, "getClasses", RuntimeReflection::registerAllClasses);
            registerBulkPlugin(invocationPlugins, reason, "getDeclaredClasses", RuntimeReflection::registerAllDeclaredClasses);
            registerBulkPlugin(invocationPlugins, reason, "getConstructors", RuntimeReflection::registerAllConstructors);
            registerBulkPlugin(invocationPlugins, reason, "getDeclaredConstructors", RuntimeReflection::registerAllDeclaredConstructors);
            registerBulkPlugin(invocationPlugins, reason, "getFields", RuntimeReflection::registerAllFields);
            registerBulkPlugin(invocationPlugins, reason, "getDeclaredFields", RuntimeReflection::registerAllDeclaredFields);
            registerBulkPlugin(invocationPlugins, reason, "getMethods", RuntimeReflection::registerAllMethods);
            registerBulkPlugin(invocationPlugins, reason, "getDeclaredMethods", RuntimeReflection::registerAllDeclaredMethods);
            registerBulkPlugin(invocationPlugins, reason, "getNestMembers", RuntimeReflection::registerAllNestMembers);
            registerBulkPlugin(invocationPlugins, reason, "getPermittedSubclasses", RuntimeReflection::registerAllPermittedSubclasses);
            registerBulkPlugin(invocationPlugins, reason, "getRecordComponents", RuntimeReflection::registerAllRecordComponents);
            registerBulkPlugin(invocationPlugins, reason, "getSigners", RuntimeReflection::registerAllSigners);
        }

        private void registerForNameOnePlugin(InvocationPlugins invocationPlugins, ParsingReason reason, ClassInitializationPlugin initializationPlugin) {
            invocationPlugins.register(Class.class, new InvocationPlugin.RequiredInlineOnlyInvocationPlugin("forName", String.class) {
                @Override
                public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                    String className = registry.getArgument(b.getMethod(), b.bci(), targetMethod, 0, String.class);
                    ClassLoader classLoader = ClassForNameSupport.respectClassLoader()
                                    ? OriginalClassProvider.getJavaClass(b.getMethod().getDeclaringClass()).getClassLoader()
                                    : applicationClassLoader;
                    return tryToFoldClassForName(b, reason, initializationPlugin, targetMethod, className, true, classLoader);
                }
            });
        }

        private void registerForNameThreePlugin(InvocationPlugins invocationPlugins, ParsingReason reason, ClassInitializationPlugin initializationPlugin) {
            invocationPlugins.register(Class.class, new InvocationPlugin.RequiredInlineOnlyInvocationPlugin("forName", String.class, boolean.class, ClassLoader.class) {
                @Override
                public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                    String className = registry.getArgument(b.getMethod(), b.bci(), targetMethod, 0, String.class);
                    Boolean initialize = registry.getArgument(b.getMethod(), b.bci(), targetMethod, 1, Boolean.class);
                    ClassLoader classLoader;
                    if (ClassForNameSupport.respectClassLoader()) {
                        Object loader = registry.getArgument(b.getMethod(), b.bci(), targetMethod, 2);
                        if (loader == null) {
                            return false;
                        }
                        classLoader = ConstantExpressionRegistry.isNull(loader) ? null : (ClassLoader) loader;
                    } else {
                        classLoader = applicationClassLoader;
                    }
                    return tryToFoldClassForName(b, reason, initializationPlugin, targetMethod, className, initialize, classLoader);
                }
            });
        }

        private boolean tryToFoldClassForName(GraphBuilderContext b, ParsingReason reason, ClassInitializationPlugin initializationPlugin, ResolvedJavaMethod targetMethod, String className,
                        Boolean initialize, ClassLoader classLoader) {
            if (className == null || initialize == null) {
                return false;
            }

            Object[] argValues = targetMethod.getParameters().length == 1
                            ? new Object[]{className}
                            : new Object[]{className, initialize, ClassForNameSupport.respectClassLoader() ? classLoader : DynamicAccessInferenceLog.ignoreArgument()};

            TypeResult<Class<?>> type = ImageClassLoader.findClass(className, false, classLoader);
            if (!type.isPresent()) {
                if (RuntimeClassLoading.isSupported()) {
                    return false;
                }
                Throwable e = type.getException();
                return throwException(b, reason, targetMethod, null, argValues, e.getClass(), e.getMessage());
            }

            Class<?> clazz = type.get();
            if (PredefinedClassesSupport.isPredefined(clazz)) {
                return false;
            }

            JavaConstant classConstant = pushConstant(b, reason, targetMethod, null, argValues, clazz);
            if (classConstant == null) {
                return false;
            }

            if (initialize) {
                initializationPlugin.apply(b, b.getMetaAccess().lookupJavaType(clazz), () -> null);
            }

            return true;
        }

        private void registerFoldingPlugin(InvocationPlugins invocationPlugins, ParsingReason reason, Method method) {
            invocationPlugins.register(method.getDeclaringClass(), new InvocationPlugin.RequiredInvocationPlugin(method.getName(), getArgumentTypesForPlugin(method)) {
                @Override
                public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                    Object receiverValue = targetMethod.hasReceiver() ? registry.getReceiver(b.getMethod(), b.bci(), targetMethod) : null;
                    Object[] arguments = getArgumentsFromRegistry(b, targetMethod);
                    return tryToFoldInvocationUsingReflection(b, reason, targetMethod, method, receiverValue, arguments);
                }
            });
        }

        private Object[] getArgumentsFromRegistry(GraphBuilderContext b, ResolvedJavaMethod targetMethod) {
            Object[] argValues = new Object[targetMethod.getSignature().getParameterCount(false)];
            for (int i = 0; i < argValues.length; i++) {
                argValues[i] = registry.getArgument(b.getMethod(), b.bci(), targetMethod, i);
                if (argValues[i] == null) {
                    return null;
                } else if (ConstantExpressionRegistry.isNull(argValues[i])) {
                    argValues[i] = null;
                }
            }
            return argValues;
        }

        private boolean tryToFoldInvocationUsingReflection(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Method reflectionMethod, Object receiverValue,
                        Object[] argValues) {
            if (!targetMethod.isStatic() && (receiverValue == null || ConstantExpressionRegistry.isNull(receiverValue))) {
                return false;
            }

            if (argValues == null) {
                return false;
            }

            Object returnValue;
            try {
                returnValue = reflectionMethod.invoke(receiverValue, argValues);
            } catch (InvocationTargetException e) {
                return throwException(b, reason, targetMethod, receiverValue, argValues, e.getTargetException().getClass(), e.getTargetException().getMessage());
            } catch (Throwable e) {
                return throwException(b, reason, targetMethod, receiverValue, argValues, e.getClass(), e.getMessage());
            }

            return pushConstant(b, reason, targetMethod, receiverValue, argValues, returnValue) != null;
        }

        private JavaConstant pushConstant(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Object receiver, Object[] arguments, Object returnValue) {
            Object intrinsicValue = getIntrinsic(b, returnValue);
            if (intrinsicValue == null) {
                return null;
            }

            JavaKind returnKind = targetMethod.getSignature().getReturnKind();

            JavaConstant intrinsicConstant;
            if (returnKind.isPrimitive()) {
                intrinsicConstant = JavaConstant.forBoxedPrimitive(intrinsicValue);
            } else if (ConstantExpressionRegistry.isNull(returnValue)) {
                intrinsicConstant = JavaConstant.NULL_POINTER;
            } else {
                intrinsicConstant = b.getSnippetReflection().forObject(intrinsicValue);
            }

            b.addPush(returnKind, ConstantNode.forConstant(intrinsicConstant, b.getMetaAccess()));
            if (inferenceLog != null) {
                inferenceLog.logFolding(b, reason, targetMethod, receiver, arguments, returnValue);
            }
            return intrinsicConstant;
        }

        private boolean throwException(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Object receiver, Object[] arguments, Class<? extends Throwable> exceptionClass,
                        String message) {
            /* Get the exception throwing method that has a message parameter. */
            Method exceptionMethod = ExceptionSynthesizer.throwExceptionMethodOrNull(exceptionClass, String.class);
            if (exceptionMethod == null) {
                return false;
            }
            Method intrinsic = getIntrinsic(b, exceptionMethod);
            if (intrinsic == null) {
                return false;
            }

            if (inferenceLog != null) {
                inferenceLog.logException(b, reason, targetMethod, receiver, arguments, exceptionClass);
            }

            ExceptionSynthesizer.throwException(b, exceptionMethod, message);
            return true;
        }

        @SuppressWarnings("unchecked")
        private <T> T getIntrinsic(GraphBuilderContext context, T element) {
            if (isDeleted(element, context.getMetaAccess())) {
                /*
                 * Should not intrinsify. Will fail during the reflective lookup at
                 * runtime. @Delete-ed elements are ignored by the reflection plugins regardless of
                 * the value of ReportUnsupportedElementsAtRuntime.
                 */
                return null;
            }
            return (T) analysisUniverse.replaceObject(element);
        }

        private static <T> boolean isDeleted(T element, MetaAccessProvider metaAccess) {
            Annotated annotated = null;
            try {
                if (element instanceof Executable) {
                    annotated = metaAccess.lookupJavaMethod((Executable) element);
                } else if (element instanceof Field) {
                    annotated = metaAccess.lookupJavaField((Field) element);
                }
            } catch (DeletedElementException ex) {
                /*
                 * If ReportUnsupportedElementsAtRuntime is *not* set looking up a @Delete-ed
                 * element will result in a DeletedElementException.
                 */
                return true;
            }
            /*
             * If ReportUnsupportedElementsAtRuntime is set looking up a @Delete-ed element will
             * return a substitution method that has the @Delete annotation.
             */
            return annotated != null && AnnotationUtil.isAnnotationPresent(annotated, Delete.class);
        }

        private void registerBulkPlugin(InvocationPlugins invocationPlugins, ParsingReason reason, String methodName, Consumer<Class<?>> registrationCallback) {
            Method method = ReflectionUtil.lookupMethod(true, Class.class, methodName);
            if (method != null) {
                registerBulkPlugin(invocationPlugins, reason, method, registrationCallback);
            }
        }

        private void registerBulkPlugin(InvocationPlugins invocationPlugins, ParsingReason reason, Method method, Consumer<Class<?>> registrationCallback) {
            invocationPlugins.register(method.getDeclaringClass(), new InvocationPlugin.RequiredInvocationPlugin(method.getName(), getArgumentTypesForPlugin(method)) {
                @Override
                public boolean isDecorator() {
                    return true;
                }

                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    Class<?> clazz = registry.getReceiver(b.getMethod(), b.bci(), targetMethod, Class.class);
                    return tryToRegisterBulkQuery(b, reason, targetMethod, clazz, registrationCallback);
                }
            });
        }

        private boolean tryToRegisterBulkQuery(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Class<?> clazz, Consumer<Class<?>> registrationCallback) {
            if (clazz == null) {
                return false;
            }
            b.add(ReachabilityCallbackNode.create(() -> {
                try {
                    registrationCallback.accept(clazz);
                } catch (LinkageError e) {
                    // Ignore
                }
            }, reason));
            if (inferenceLog != null) {
                inferenceLog.logRegistration(b, reason, targetMethod, clazz, new Object[]{});
            }
            return true;
        }

        private void registerMethodHandlePlugins(GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
            InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();

            Method findClass = ReflectionUtil.lookupMethod(true, MethodHandles.Lookup.class, "findClass", String.class);

            Method findConstructor = ReflectionUtil.lookupMethod(true, MethodHandles.Lookup.class, "findConstructor", Class.class, MethodType.class);

            Method findVirtual = ReflectionUtil.lookupMethod(true, MethodHandles.Lookup.class, "findVirtual", Class.class, String.class, MethodType.class);
            Method findStatic = ReflectionUtil.lookupMethod(true, MethodHandles.Lookup.class, "findStatic", Class.class, String.class, MethodType.class);
            Method findSpecial = ReflectionUtil.lookupMethod(true, MethodHandles.Lookup.class, "findSpecial", Class.class, String.class, MethodType.class, Class.class);

            Method findGetter = ReflectionUtil.lookupMethod(true, MethodHandles.Lookup.class, "findGetter", Class.class, String.class, Class.class);
            Method findStaticGetter = ReflectionUtil.lookupMethod(true, MethodHandles.Lookup.class, "findStaticGetter", Class.class, String.class, Class.class);
            Method findSetter = ReflectionUtil.lookupMethod(true, MethodHandles.Lookup.class, "findSetter", Class.class, String.class, Class.class);
            Method findStaticSetter = ReflectionUtil.lookupMethod(true, MethodHandles.Lookup.class, "findStaticSetter", Class.class, String.class, String.class);
            Method findVarHandle = ReflectionUtil.lookupMethod(true, MethodHandles.Lookup.class, "findVarHandle", Class.class, String.class, Class.class);
            Method findStaticVarHandle = ReflectionUtil.lookupMethod(true, MethodHandles.Lookup.class, "findStaticVarHandle", Class.class, String.class, Class.class);

            Stream.of(findClass, findConstructor, findVirtual, findStatic, findSpecial, findGetter, findStaticGetter, findSetter, findStaticSetter, findVarHandle, findStaticVarHandle)
                            .filter(Objects::nonNull)
                            .forEach(m -> registerFoldingPlugin(invocationPlugins, reason, m));
        }

        private void registerProxyPlugins(GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
            Method getProxyClass = ReflectionUtil.lookupMethod(true, Proxy.class, "getProxyClass", ClassLoader.class, Class[].class);
            Method newProxyInstance = ReflectionUtil.lookupMethod(true, Proxy.class, "newProxyInstance", ClassLoader.class, Class[].class, InvocationHandler.class);
            Stream.of(getProxyClass, newProxyInstance)
                            .filter(Objects::nonNull)
                            .forEach(m -> registerProxyPlugin(plugins.getInvocationPlugins(), reason, m));
        }

        private void registerProxyPlugin(InvocationPlugins invocationPlugins, ParsingReason reason, Method method) {
            invocationPlugins.register(method.getDeclaringClass(), new InvocationPlugin.RequiredInvocationPlugin(method.getName(), getArgumentTypesForPlugin(method)) {
                @Override
                public boolean isDecorator() {
                    return true;
                }

                @Override
                public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                    Class<?>[] interfaces = registry.getArgument(b.getMethod(), b.bci(), targetMethod, 1, Class[].class);
                    return tryToRegisterProxy(b, reason, targetMethod, interfaces);
                }
            });
        }

        private boolean tryToRegisterProxy(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Class<?>[] interfaces) {
            if (interfaces == null) {
                return false;
            }
            b.add(ReachabilityCallbackNode.create(() -> RuntimeProxyCreation.register(interfaces), reason));
            if (inferenceLog != null) {
                Object[] args = targetMethod.getParameters().length == 2
                                ? new Object[]{DynamicAccessInferenceLog.ignoreArgument(), interfaces}
                                : new Object[]{DynamicAccessInferenceLog.ignoreArgument(), interfaces, DynamicAccessInferenceLog.ignoreArgument()};
                inferenceLog.logRegistration(b, reason, targetMethod, null, args);
            }
            return true;
        }
    }

    private final class StrictResourceInferencePlugins {

        private final Method resourceNameResolver = ReflectionUtil.lookupMethod(Class.class, "resolveName", String.class);

        public void register(GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
            Method getResource = ReflectionUtil.lookupMethod(true, Class.class, "getResource", String.class);
            Method getResourceAsStream = ReflectionUtil.lookupMethod(true, Class.class, "getResourceAsStream", String.class);
            Stream.of(getResource, getResourceAsStream)
                            .filter(Objects::nonNull)
                            .forEach(m -> registerResourcePlugin(plugins.getInvocationPlugins(), reason, m));
        }

        private void registerResourcePlugin(InvocationPlugins invocationPlugins, ParsingReason reason, Method method) {
            invocationPlugins.register(method.getDeclaringClass(), new InvocationPlugin.RequiredInvocationPlugin(method.getName(), getArgumentTypesForPlugin(method)) {
                @Override
                public boolean isDecorator() {
                    return true;
                }

                @Override
                public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                    Class<?> clazz = registry.getReceiver(b.getMethod(), b.bci(), targetMethod, Class.class);
                    String resource = registry.getArgument(b.getMethod(), b.bci(), targetMethod, 0, String.class);
                    return tryToRegisterResource(b, reason, targetMethod, clazz, resource);
                }
            });
        }

        private boolean tryToRegisterResource(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Class<?> clazz, String resource) {
            if (clazz == null || resource == null) {
                return false;
            }
            b.add(ReachabilityCallbackNode.create(() -> RuntimeResourceAccess.addResource(clazz.getModule(), resolveResourceName(clazz, resource)), reason));
            if (inferenceLog != null) {
                inferenceLog.logRegistration(b, reason, targetMethod, clazz, new String[]{resource});
            }
            return true;
        }

        private String resolveResourceName(Class<?> clazz, String name) {
            try {
                return (String) resourceNameResolver.invoke(clazz, name);
            } catch (ReflectiveOperationException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
    }
}
