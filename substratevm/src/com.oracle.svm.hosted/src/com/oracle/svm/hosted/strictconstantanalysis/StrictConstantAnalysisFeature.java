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
package com.oracle.svm.hosted.strictconstantanalysis;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.util.ReflectionUtil;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Feature which enables a graph IR optimization independent analysis of compile-time inferrable
 * method invocations which require dynamic access and would otherwise require a manual reachability
 * registration.
 * <p>
 * The targeted methods are:
 * <ul>
 * <li>{@link java.lang.Class#forName(String)}</li>
 * <li>{@link java.lang.Class#forName(String, boolean, ClassLoader)}</li>
 * <li>{@link java.lang.Class#getField(String)}</li>
 * <li>{@link java.lang.Class#getDeclaredField(String)}</li>
 * <li>{@link java.lang.Class#getConstructor(Class[])}</li>
 * <li>{@link java.lang.Class#getDeclaredConstructor(Class[])}</li>
 * <li>{@link java.lang.Class#getMethod(String, Class[])}</li>
 * <li>{@link java.lang.Class#getDeclaredMethod(String, Class[])}</li>
 * <li>{@link java.lang.Class#getFields()}</li>
 * <li>{@link java.lang.Class#getDeclaredFields()}</li>
 * <li>{@link java.lang.Class#getConstructors()}</li>
 * <li>{@link java.lang.Class#getDeclaredConstructors()}</li>
 * <li>{@link java.lang.Class#getMethods()}</li>
 * <li>{@link java.lang.Class#getDeclaredMethods()}</li>
 * <li>{@link java.lang.Class#getClasses()}</li>
 * <li>{@link java.lang.Class#getDeclaredClasses()}</li>
 * <li>{@link java.lang.Class#getNestMembers()}</li>
 * <li>{@link java.lang.Class#getPermittedSubclasses()}</li>
 * <li>{@link java.lang.Class#getRecordComponents()}</li>
 * <li>{@link java.lang.Class#getSigners()}</li>
 * <li>{@link java.lang.invoke.MethodHandles.Lookup#findClass(String)}}</li>
 * <li>{@link java.lang.invoke.MethodHandles.Lookup#findVirtual(Class, String, MethodType)}}</li>
 * <li>{@link java.lang.invoke.MethodHandles.Lookup#findStatic(Class, String, MethodType)}}</li>
 * <li>{@link java.lang.invoke.MethodHandles.Lookup#findConstructor(Class, MethodType)}}</li>
 * <li>{@link java.lang.invoke.MethodHandles.Lookup#findGetter(Class, String, Class)}}</li>
 * <li>{@link java.lang.invoke.MethodHandles.Lookup#findStaticGetter(Class, String, Class)}}</li>
 * <li>{@link java.lang.invoke.MethodHandles.Lookup#findSetter(Class, String, Class)}}</li>
 * <li>{@link java.lang.invoke.MethodHandles.Lookup#findStaticSetter(Class, String, Class)}}</li>
 * <li>{@link java.lang.invoke.MethodHandles.Lookup#findVarHandle(Class, String, Class)}}</li>
 * <li>{@link java.lang.invoke.MethodHandles.Lookup#findStaticVarHandle(Class, String, Class)}}</li>
 * <li>{@link java.lang.Class#getResource(String)}</li>
 * <li>{@link java.lang.Class#getResourceAsStream(String)}</li>
 * <li>{@link java.io.ObjectInputFilter.Config#createFilter(String)}</li>
 * <li>{@link java.lang.reflect.Proxy#newProxyInstance(ClassLoader, Class[], InvocationHandler)}</li>
 * </ul>
 */
@AutomaticallyRegisteredFeature
public class StrictConstantAnalysisFeature implements InternalFeature {

    public static class Options {

        public enum Mode {
            Disable,
            Warn,
            Enforce
        }

        @Option(help = """
                        Select the mode for the strict, build-time analysis for calls requiring dynamic access.
                        Possible values are:
                         "Disable" (default): Disable the strict mode and fall back to the optimization dependent analysis for inferrable dynamic calls;
                         "Warn": Fold both the calls inferred with the strict mode analysis and the optimization dependant analysis, but print a warning for non-strict call folding;
                         "Enforce": Fold only the calls inferred by the strict analysis mode.""")//
        public static final HostedOptionKey<Mode> StrictConstantAnalysis = new HostedOptionKey<>(Mode.Disable);
    }

    public static boolean isActive() {
        return Options.StrictConstantAnalysis.getValue() != Options.Mode.Disable;
    }

    private ImageClassLoader loader;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess a) {
        return isActive();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        FeatureImpl.AfterRegistrationAccessImpl access = (FeatureImpl.AfterRegistrationAccessImpl) a;
        loader = access.getImageClassLoader();
        ImageSingletons.add(ConstantExpressionRegistry.class, new ConstantExpressionRegistry());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /*
         * The strict analysis mode disables constant folding through method inlining, which leads
         * to <clinit> of sun.nio.ch.DatagramChannelImpl throwing a missing reflection registration
         * error. This is a temporary fix until annotation guided analysis is implemented.
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
    public void registerGraphBuilderPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        ConstantExpressionRegistry registry = ConstantExpressionRegistry.singleton();
        ConstantExpressionAnalyzer analyzer = new ConstantExpressionAnalyzer(providers, loader);
        plugins.appendMethodParsingPlugin((method, intrinsicContext) -> registry.analyzeAndStore(analyzer, method, intrinsicContext));
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        /*
         * No more bytecode parsing should happen after analysis, so we can seal and clean up the
         * registry.
         */
        ConstantExpressionRegistry.singleton().seal();
    }

    /**
     * Utility method which attempts to infer {@code targetMethod} according to the {@code Disable},
     * {@code Warn} and {@code Enforce} options of {@code StrictConstantAnalysis}.
     */
    public static boolean tryToInfer(Predicate<ConstantExpressionRegistry> strictModeRoutine, BooleanSupplier graphModeRoutine, ResolvedJavaMethod targetMethod,
                    Predicate<ResolvedJavaMethod> strictModeTarget) {
        Options.Mode analysisMode = Options.StrictConstantAnalysis.getValue();
        boolean isTarget = strictModeTarget.test(targetMethod);
        if (analysisMode != Options.Mode.Disable && isTarget) {
            ConstantExpressionRegistry registry = ConstantExpressionRegistry.singleton();
            if (strictModeRoutine.test(registry)) {
                return true;
            }
        }
        if (analysisMode != Options.Mode.Enforce || !isTarget) {
            return graphModeRoutine.getAsBoolean();
        }
        return false;
    }

    /**
     * Utility method which attempts to infer {@code targetMethod} according to the {@code Disable},
     * {@code Warn} and {@code Enforce} options of {@code StrictConstantAnalysis}.
     */
    public static boolean tryToInfer(Predicate<ConstantExpressionRegistry> strictModeRoutine, BooleanSupplier graphModeRoutine) {
        return tryToInfer(strictModeRoutine, graphModeRoutine, null, (method) -> true);
    }
}
