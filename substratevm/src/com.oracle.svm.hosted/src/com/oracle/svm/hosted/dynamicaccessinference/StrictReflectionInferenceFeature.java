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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.ReachabilityRegistrationNode;
import com.oracle.svm.hosted.substitute.DeletedElementException;
import com.oracle.svm.util.TypeResult;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredFeature
public class StrictReflectionInferenceFeature implements InternalFeature {

    private ConstantExpressionRegistry registry;
    private ImageClassLoader imageClassLoader;
    private AnalysisUniverse analysisUniverse;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(StrictDynamicAccessInferenceFeature.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        StrictDynamicAccessInferenceFeature.Options.Mode mode = StrictDynamicAccessInferenceFeature.Options.StrictDynamicAccessInference.getValue();
        return mode == StrictDynamicAccessInferenceFeature.Options.Mode.Enforce;
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        FeatureImpl.DuringSetupAccessImpl accessImpl = (FeatureImpl.DuringSetupAccessImpl) access;
        registry = ConstantExpressionRegistry.singleton();
        imageClassLoader = accessImpl.getImageClassLoader();
        analysisUniverse = accessImpl.getUniverse();
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        if (reason != ParsingReason.PointsToAnalysis) {
            return;
        }

        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        ClassInitializationPlugin initializationPlugin = plugins.getClassInitializationPlugin();

        invocationPlugins.register(Class.class, new InvocationPlugin.RequiredInvocationPlugin("forName", String.class) {
            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                String className = registry.getArgument(b.getMethod(), b.bci(), targetMethod, 0, String.class);
                return foldClassForName(initializationPlugin, b, className, true, imageClassLoader.getClassLoader());
            }
        });

        invocationPlugins.register(Class.class, new InvocationPlugin.RequiredInvocationPlugin("forName", String.class, boolean.class, ClassLoader.class) {
            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {
                String className = registry.getArgument(b.getMethod(), b.bci(), targetMethod, 0, String.class);
                Boolean initialize = registry.getArgument(b.getMethod(), b.bci(), targetMethod, 1, Boolean.class);
                return foldClassForName(initializationPlugin, b, className, initialize, imageClassLoader.getClassLoader());
            }
        });

        registerFoldInvocationPlugins(invocationPlugins, Class.class,
                        "getField", "getDeclaredField", "getConstructor",
                        "getDeclaredConstructor", "getMethod", "getDeclaredMethod");

        registerFoldInvocationPlugins(invocationPlugins, MethodHandles.Lookup.class,
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

    private void registerFoldInvocationPlugins(InvocationPlugins plugins, Class<?> declaringClass, String... methodNames) {
        Set<String> methodNamesSet = Set.of(methodNames);
        Arrays.stream(declaringClass.getDeclaredMethods())
                        .filter(m -> methodNamesSet.contains(m.getName()) && !m.isSynthetic())
                        .forEach(m -> registerFoldInvocationPlugin(plugins, m));
    }

    private void registerFoldInvocationPlugin(InvocationPlugins plugins, Method method) {
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
                return foldInvocationUsingReflection(b, method, receiverValue, arguments);
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
                return registerBulkQuery(b, reason, clazz, registrationCallback);
            }
        });
    }

    private boolean registerBulkQuery(GraphBuilderContext b, ParsingReason reason, Class<?> clazz, Consumer<Class<?>> registrationCallback) {
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
        return true;
    }

    private boolean foldClassForName(ClassInitializationPlugin initializationPlugin, GraphBuilderContext b, String className, Boolean initialize, ClassLoader classLoader) {
        if (className == null || initialize == null) {
            return false;
        }

        TypeResult<Class<?>> type = ImageClassLoader.findClass(className, false, classLoader);
        if (!type.isPresent()) {
            if (RuntimeClassLoading.isSupported()) {
                return false;
            }
            Throwable e = type.getException();
            return throwException(b, e.getClass(), e.getMessage());
        }

        Class<?> clazz = type.get();
        if (PredefinedClassesSupport.isPredefined(clazz)) {
            return false;
        }

        JavaConstant classConstant = pushConstant(b, JavaKind.Object, className);
        if (classConstant == null) {
            return false;
        }

        if (initialize) {
            initializationPlugin.apply(b, b.getMetaAccess().lookupJavaType(clazz), () -> null);
        }

        return true;
    }

    private boolean foldInvocationUsingReflection(GraphBuilderContext b, Method reflectionMethod, Object receiverValue, Object[] argValues) {
        boolean isStatic = Modifier.isStatic(reflectionMethod.getModifiers());
        if (isStatic && (receiverValue == null || ConstantExpressionRegistry.isNull(receiverValue)) || argValues == null) {
            return false;
        }

        Object returnValue;
        try {
            returnValue = reflectionMethod.invoke(receiverValue, argValues);
        } catch (InvocationTargetException e) {
            return throwException(b, e.getTargetException().getClass(), e.getTargetException().getMessage());
        } catch (Throwable e) {
            return throwException(b, e.getClass(), e.getMessage());
        }

        return pushConstant(b, JavaKind.Object, returnValue) != null;
    }

    private JavaConstant pushConstant(GraphBuilderContext b, JavaKind returnKind, Object returnValue) {
        Object intrinsicValue = getIntrinsic(b, returnValue);
        if (intrinsicValue == null) {
            return null;
        }

        JavaConstant intrinsicConstant;
        if (returnKind.isPrimitive()) {
            intrinsicConstant = JavaConstant.forBoxedPrimitive(intrinsicValue);
        } else if (ConstantExpressionRegistry.isNull(returnValue)) {
            intrinsicConstant = JavaConstant.NULL_POINTER;
        } else {
            intrinsicConstant = b.getSnippetReflection().forObject(intrinsicValue);
        }

        b.addPush(returnKind, ConstantNode.forConstant(intrinsicConstant, b.getMetaAccess()));
        return intrinsicConstant;
    }

    private boolean throwException(GraphBuilderContext b, Class<? extends Throwable> exceptionClass, String message) {
        /* Get the exception throwing method that has a message parameter. */
        Method exceptionMethod = ExceptionSynthesizer.throwExceptionMethodOrNull(exceptionClass, String.class);
        if (exceptionMethod == null) {
            return false;
        }
        Method intrinsic = getIntrinsic(b, exceptionMethod);
        if (intrinsic == null) {
            return false;
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
