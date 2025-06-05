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
import java.lang.reflect.AnnotatedElement;
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
import java.util.Set;
import java.util.function.Consumer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.ReachabilityRegistrationNode;
import com.oracle.svm.hosted.substitute.DeletedElementException;
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
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredFeature
public class StrictDynamicAccessInferenceFeature implements InternalFeature {

    public static class Options {

        public enum Mode {
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
        public static final HostedOptionKey<Mode> StrictDynamicAccessInference = new HostedOptionKey<>(Mode.Disable);
    }

    public static boolean isDisabled() {
        return Options.StrictDynamicAccessInference.getValue() == Options.Mode.Disable;
    }

    public static boolean isEnforced() {
        return Options.StrictDynamicAccessInference.getValue() == Options.Mode.Enforce;
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return !isDisabled();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        FeatureImpl.AfterRegistrationAccessImpl accessImpl = (FeatureImpl.AfterRegistrationAccessImpl) access;

        ConstantExpressionAnalyzer analyzer = new ConstantExpressionAnalyzer(GraalAccess.getOriginalProviders(), accessImpl.getImageClassLoader());
        ConstantExpressionRegistry registry = new ConstantExpressionRegistry();
        StrictDynamicAccessInferenceSupport support = new StrictDynamicAccessInferenceSupport(analyzer, registry);

        ImageSingletons.add(ConstantExpressionRegistry.class, registry);
        ImageSingletons.add(StrictDynamicAccessInferenceSupport.class, support);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /*
         * The strict dynamic access inference mode disables constant folding through method
         * inlining, which leads to <clinit> of sun.nio.ch.DatagramChannelImpl throwing a missing
         * reflection registration error. This is a temporary fix until annotation guided analysis
         * is implemented.
         *
         * An alternative to this approach would be creating invocation plugins for the methods
         * defined in jdk.internal.invoke.MhUtil.
         */
        registerFieldForReflectionIfExists(access, "sun.nio.ch.DatagramChannelImpl", "socket");
    }

    @SuppressWarnings("SameParameterValue")
    private void registerFieldForReflectionIfExists(BeforeAnalysisAccess access, String className, String fieldName) {
        Class<?> clazz = ReflectionUtil.lookupClass(true, className);
        if (clazz == null) {
            return;
        }
        Field field = ReflectionUtil.lookupField(true, clazz, fieldName);
        if (field == null) {
            return;
        }
        access.registerReachabilityHandler(a -> RuntimeReflection.register(field), clazz);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        /*
         * No more bytecode parsing should happen after analysis, so we can seal and clean up the
         * registry.
         */
        ConstantExpressionRegistry.singleton().seal();
    }
}

@AutomaticallyRegisteredFeature
class StrictProxyInferenceFeature implements InternalFeature {

    private ConstantExpressionRegistry registry;
    private DynamicAccessInferenceLog inferenceLog;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(StrictDynamicAccessInferenceFeature.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return StrictDynamicAccessInferenceFeature.isEnforced();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        registry = ConstantExpressionRegistry.singleton();
        inferenceLog = ImageSingletons.contains(DynamicAccessInferenceLog.class) ? DynamicAccessInferenceLog.singleton() : null;
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        if (reason != ParsingReason.PointsToAnalysis) {
            return;
        }

        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();

        Method getProxyClass = ReflectionUtil.lookupMethod(Proxy.class, "getProxyClass", ClassLoader.class, Class[].class);
        Method newProxyInstance = ReflectionUtil.lookupMethod(Proxy.class, "newProxyInstance", ClassLoader.class, Class[].class, InvocationHandler.class);

        for (Method method : List.of(getProxyClass, newProxyInstance)) {
            invocationPlugins.register(method.getDeclaringClass(), new InvocationPlugin.RequiredInvocationPlugin(method.getName(), method.getParameterTypes()) {
                @Override
                public boolean isDecorator() {
                    return true;
                }

                @Override
                public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                    Class<?>[] interfaces = registry.getArgument(b.getMethod(), b.bci(), targetMethod, 1, Class[].class);
                    return registerProxy(b, reason, targetMethod, interfaces);
                }
            });
        }
    }

    private boolean registerProxy(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Class<?>[] interfaces) {
        if (interfaces == null) {
            return false;
        }

        b.add(ReachabilityRegistrationNode.create(() -> RuntimeProxyCreation.register(interfaces), reason));
        if (inferenceLog != null) {
            Object ignore = DynamicAccessInferenceLog.ignoreArgument();
            Object[] args = targetMethod.getParameters().length == 2
                            ? new Object[]{ignore, interfaces}
                            : new Object[]{ignore, interfaces, ignore};
            inferenceLog.logRegistration(b, reason, targetMethod, null, args);
        }
        return true;
    }
}

@AutomaticallyRegisteredFeature
class StrictReflectionInferenceFeature implements InternalFeature {

    private ConstantExpressionRegistry registry;
    private ImageClassLoader imageClassLoader;
    private AnalysisUniverse analysisUniverse;
    private DynamicAccessInferenceLog inferenceLog;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(StrictDynamicAccessInferenceFeature.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return StrictDynamicAccessInferenceFeature.isEnforced();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        FeatureImpl.DuringSetupAccessImpl accessImpl = (FeatureImpl.DuringSetupAccessImpl) access;
        registry = ConstantExpressionRegistry.singleton();
        imageClassLoader = accessImpl.getImageClassLoader();
        analysisUniverse = accessImpl.getUniverse();
        inferenceLog = ImageSingletons.contains(DynamicAccessInferenceLog.class) ? DynamicAccessInferenceLog.singleton() : null;
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        if (reason != ParsingReason.PointsToAnalysis) {
            return;
        }

        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        ClassInitializationPlugin initializationPlugin = plugins.getClassInitializationPlugin();

        invocationPlugins.register(Class.class, new InvocationPlugin.RequiredInlineOnlyInvocationPlugin("forName", String.class) {
            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                String className = registry.getArgument(b.getMethod(), b.bci(), targetMethod, 0, String.class);
                ClassLoader classLoader = ClassForNameSupport.respectClassLoader()
                                ? OriginalClassProvider.getJavaClass(b.getMethod().getDeclaringClass()).getClassLoader()
                                : imageClassLoader.getClassLoader();
                return foldClassForName(initializationPlugin, b, reason, targetMethod, className, true, classLoader);
            }
        });

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
                    } else if (ConstantExpressionRegistry.isNull(loader)) {
                        classLoader = null;
                    } else {
                        classLoader = (ClassLoader) loader;
                    }
                } else {
                    classLoader = imageClassLoader.getClassLoader();
                }
                return foldClassForName(initializationPlugin, b, reason, targetMethod, className, initialize, classLoader);
            }
        });

        registerFoldInvocationPlugins(invocationPlugins, reason, Class.class,
                        "getField", "getDeclaredField", "getConstructor",
                        "getDeclaredConstructor", "getMethod", "getDeclaredMethod");

        registerFoldInvocationPlugins(invocationPlugins, reason, MethodHandles.Lookup.class,
                        "findClass", "findVirtual", "findStatic", "findConstructor",
                        "findGetter", "findStaticGetter", "findSetter", "findStaticSetter",
                        "findVarHandle", "findStaticVarHandle", "findSpecial");

        registerBulkQueryRegistrationPlugin(invocationPlugins, reason, "getClasses", RuntimeReflection::registerAllClasses);
        registerBulkQueryRegistrationPlugin(invocationPlugins, reason, "getDeclaredClasses", RuntimeReflection::registerAllDeclaredClasses);
        registerBulkQueryRegistrationPlugin(invocationPlugins, reason, "getConstructors", RuntimeReflection::registerAllConstructors);
        registerBulkQueryRegistrationPlugin(invocationPlugins, reason, "getDeclaredConstructors", RuntimeReflection::registerAllDeclaredConstructors);
        registerBulkQueryRegistrationPlugin(invocationPlugins, reason, "getFields", RuntimeReflection::registerAllFields);
        registerBulkQueryRegistrationPlugin(invocationPlugins, reason, "getDeclaredFields", RuntimeReflection::registerAllDeclaredFields);
        registerBulkQueryRegistrationPlugin(invocationPlugins, reason, "getMethods", RuntimeReflection::registerAllMethods);
        registerBulkQueryRegistrationPlugin(invocationPlugins, reason, "getDeclaredMethods", RuntimeReflection::registerAllDeclaredMethods);
        registerBulkQueryRegistrationPlugin(invocationPlugins, reason, "getNestMembers", RuntimeReflection::registerAllNestMembers);
        registerBulkQueryRegistrationPlugin(invocationPlugins, reason, "getPermittedSubclasses", RuntimeReflection::registerAllPermittedSubclasses);
        registerBulkQueryRegistrationPlugin(invocationPlugins, reason, "getRecordComponents", RuntimeReflection::registerAllRecordComponents);
        registerBulkQueryRegistrationPlugin(invocationPlugins, reason, "getSigners", RuntimeReflection::registerAllSigners);
    }

    private void registerFoldInvocationPlugins(InvocationPlugins plugins, ParsingReason reason, Class<?> declaringClass, String... methodNames) {
        Set<String> methodNamesSet = Set.of(methodNames);
        Arrays.stream(declaringClass.getDeclaredMethods())
                        .filter(m -> methodNamesSet.contains(m.getName()) && !m.isSynthetic())
                        .forEach(m -> registerFoldInvocationPlugin(plugins, reason, m));
    }

    private void registerFoldInvocationPlugin(InvocationPlugins plugins, ParsingReason reason, Method method) {
        List<Class<?>> parameterTypes = new ArrayList<>();
        if (!Modifier.isStatic(method.getModifiers())) {
            parameterTypes.add(InvocationPlugin.Receiver.class);
        }
        parameterTypes.addAll(Arrays.asList(method.getParameterTypes()));

        plugins.register(method.getDeclaringClass(), new InvocationPlugin.RequiredInvocationPlugin(method.getName(), parameterTypes.toArray(new Class<?>[0])) {
            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                Object receiverValue = targetMethod.hasReceiver() ? registry.getReceiver(b.getMethod(), b.bci(), targetMethod) : null;
                Object[] arguments = getArgumentsFromRegistry(b, targetMethod);
                return foldInvocationUsingReflection(b, reason, targetMethod, method, receiverValue, arguments);
            }
        });
    }

    private void registerBulkQueryRegistrationPlugin(InvocationPlugins plugins, ParsingReason reason, String methodName, Consumer<Class<?>> registrationCallback) {
        plugins.register(Class.class, new InvocationPlugin.RequiredInvocationPlugin(methodName, new Class<?>[]{InvocationPlugin.Receiver.class}) {
            @Override
            public boolean isDecorator() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                Class<?> clazz = registry.getReceiver(b.getMethod(), b.bci(), targetMethod, Class.class);
                return registerBulkQuery(b, reason, targetMethod, clazz, registrationCallback);
            }
        });
    }

    private boolean registerBulkQuery(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Class<?> clazz, Consumer<Class<?>> registrationCallback) {
        if (clazz == null) {
            return false;
        }

        b.add(ReachabilityRegistrationNode.create(() -> {
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

    private boolean foldClassForName(ClassInitializationPlugin initializationPlugin, GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, String className, Boolean initialize,
                    ClassLoader classLoader) {
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

    private boolean foldInvocationUsingReflection(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Method reflectionMethod, Object receiverValue, Object[] argValues) {
        boolean isStatic = Modifier.isStatic(reflectionMethod.getModifiers());
        if (isStatic && (receiverValue == null || ConstantExpressionRegistry.isNull(receiverValue)) || argValues == null) {
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

        return pushConstant(b, reason, targetMethod, returnValue, argValues, returnValue) != null;
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
            inferenceLog.logConstant(b, reason, targetMethod, receiver, arguments, returnValue);
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
             * Should not intrinsify. Will fail during the reflective lookup at runtime. @Delete-ed
             * elements are ignored by the reflection plugins regardless of the value of
             * ReportUnsupportedElementsAtRuntime.
             */
            return null;
        }
        return (T) analysisUniverse.replaceObject(element);
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
        return annotated != null && annotated.isAnnotationPresent(Delete.class);
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
}

@AutomaticallyRegisteredFeature
class StrictResourceInferenceFeature implements InternalFeature {

    private ConstantExpressionRegistry registry;
    private DynamicAccessInferenceLog inferenceLog;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(StrictDynamicAccessInferenceFeature.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return StrictDynamicAccessInferenceFeature.isEnforced();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        registry = ConstantExpressionRegistry.singleton();
        inferenceLog = ImageSingletons.contains(DynamicAccessInferenceLog.class) ? DynamicAccessInferenceLog.singleton() : null;
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        if (reason != ParsingReason.PointsToAnalysis) {
            return;
        }

        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        Method resolveResourceName = ReflectionUtil.lookupMethod(Class.class, "resolveName", String.class);

        Method getResource = ReflectionUtil.lookupMethod(Class.class, "getResource", String.class);
        Method getResourceAsStream = ReflectionUtil.lookupMethod(Class.class, "getResourceAsStream", String.class);

        for (Method method : List.of(getResource, getResourceAsStream)) {
            List<Class<?>> parameterTypes = new ArrayList<>();
            parameterTypes.add(InvocationPlugin.Receiver.class);
            parameterTypes.addAll(Arrays.asList(method.getParameterTypes()));
            invocationPlugins.register(method.getDeclaringClass(), new InvocationPlugin.RequiredInvocationPlugin(method.getName(), parameterTypes.toArray(new Class<?>[0])) {
                @Override
                public boolean isDecorator() {
                    return true;
                }

                @Override
                public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                    Class<?> clazz = registry.getReceiver(b.getMethod(), b.bci(), targetMethod, Class.class);
                    String resource = registry.getArgument(b.getMethod(), b.bci(), targetMethod, 0, String.class);
                    return registerResource(b, reason, targetMethod, clazz, resource, resolveResourceName);
                }
            });
        }
    }

    private boolean registerResource(GraphBuilderContext b, ParsingReason reason, ResolvedJavaMethod targetMethod, Class<?> clazz, String resource, Method resolveResourceName) {
        if (clazz == null || resource == null) {
            return false;
        }

        String resourceName;
        try {
            resourceName = (String) resolveResourceName.invoke(clazz, resource);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
        b.add(ReachabilityRegistrationNode.create(() -> RuntimeResourceAccess.addResource(clazz.getModule(), resourceName), reason));
        if (inferenceLog != null) {
            inferenceLog.logRegistration(b, reason, targetMethod, clazz, new String[]{resource});
        }
        return true;
    }
}
