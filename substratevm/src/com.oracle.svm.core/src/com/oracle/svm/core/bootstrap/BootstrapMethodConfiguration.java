/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.bootstrap;

import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatFactory;
import java.lang.invoke.TypeDescriptor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.runtime.ObjectMethods;
import java.lang.runtime.SwitchBootstraps;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Class storing a list of bootstrap methods that are allowed to be executed at build time. Those
 * methods are trusted methods from the JDK. Additionally used to register methods used by some
 * bootstrap methods for runtime reflection.
 */
@AutomaticallyRegisteredFeature
public class BootstrapMethodConfiguration implements InternalFeature {

    public record BootstrapMethodRecord(int bci, int cpi, ResolvedJavaMethod method) {
    }

    /*
     * Map used to cache the BootstrapMethodInfo and reuse it for duplicated bytecode, avoiding to
     * execute the bootstrap method for the same bci and method pair. This can happen during
     * bytecode parsing as some blocks are duplicated, or for methods that are parsed multiple times
     * (see MultiMethod).
     */
    private final ConcurrentMap<BootstrapMethodRecord, BootstrapMethodInfo> bootstrapMethodInfoCache = new ConcurrentHashMap<>();
    private final Set<Executable> indyBuildTimeAllowList;
    private final Set<Executable> condyBuildTimeAllowList;
    private final Set<Executable> trustedCondy;

    public static BootstrapMethodConfiguration singleton() {
        return ImageSingletons.lookup(BootstrapMethodConfiguration.class);
    }

    public BootstrapMethodConfiguration() {
        /*
         * Bootstrap method used for Lambdas. Executing this method at run time implies defining
         * hidden class at run time, which is unsupported.
         */
        Method metafactory = ReflectionUtil.lookupMethod(LambdaMetafactory.class, "metafactory", MethodHandles.Lookup.class, String.class, MethodType.class, MethodType.class, MethodHandle.class,
                        MethodType.class);
        /* Alternate version of LambdaMetafactory.metafactory. */
        Method altMetafactory = ReflectionUtil.lookupMethod(LambdaMetafactory.class, "altMetafactory", MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class);

        /*
         * Bootstrap method used to optimize String concatenation. Executing it at run time
         * currently causes a StackOverFlow error as it infinitely calls itself.
         */
        Method makeConcat = ReflectionUtil.lookupMethod(StringConcatFactory.class, "makeConcat", MethodHandles.Lookup.class, String.class, MethodType.class);
        /* Alternate version of StringConcatFactory.makeConcat with constant arguments. */
        Method makeConcatWithConstants = ReflectionUtil.lookupMethod(StringConcatFactory.class, "makeConcatWithConstants", MethodHandles.Lookup.class, String.class, MethodType.class, String.class,
                        Object[].class);

        /* Causes deadlock in Permission feature. */
        Method bootstrap = ReflectionUtil.lookupMethod(ObjectMethods.class, "bootstrap", MethodHandles.Lookup.class, String.class, TypeDescriptor.class, Class.class, String.class,
                        MethodHandle[].class);

        /*
         * Bootstrap methods used for switch statements. Executing these methods at run time implies
         * defining hidden classes at run time, which is unsupported.
         */
        Method typeSwitch = ReflectionUtil.lookupMethod(SwitchBootstraps.class, "typeSwitch", MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class);
        Method enumSwitch = ReflectionUtil.lookupMethod(SwitchBootstraps.class, "enumSwitch", MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class);

        indyBuildTimeAllowList = Set.of(metafactory, altMetafactory, makeConcat, makeConcatWithConstants, bootstrap, typeSwitch, enumSwitch);

        /* Bootstrap methods used for various dynamic constants. */
        Method nullConstant = ReflectionUtil.lookupMethod(ConstantBootstraps.class, "nullConstant", MethodHandles.Lookup.class, String.class, Class.class);
        Method primitiveClass = ReflectionUtil.lookupMethod(ConstantBootstraps.class, "primitiveClass", MethodHandles.Lookup.class, String.class, Class.class);
        Method enumConstant = ReflectionUtil.lookupMethod(ConstantBootstraps.class, "enumConstant", MethodHandles.Lookup.class, String.class, Class.class);
        Method getStaticFinal = ReflectionUtil.lookupMethod(ConstantBootstraps.class, "getStaticFinal", MethodHandles.Lookup.class, String.class, Class.class, Class.class);
        Method invoke = ReflectionUtil.lookupMethod(ConstantBootstraps.class, "invoke", MethodHandles.Lookup.class, String.class, Class.class, MethodHandle.class, Object[].class);
        Method explicitCast = ReflectionUtil.lookupMethod(ConstantBootstraps.class, "explicitCast", MethodHandles.Lookup.class, String.class, Class.class, Object.class);

        /* Bootstrap methods used for dynamic constants representing class data. */
        Method classData = ReflectionUtil.lookupMethod(MethodHandles.class, "classData", MethodHandles.Lookup.class, String.class, Class.class);
        Method classDataAt = ReflectionUtil.lookupMethod(MethodHandles.class, "classDataAt", MethodHandles.Lookup.class, String.class, Class.class, int.class);

        /* Set of bootstrap methods for constant dynamic allowed at build time is empty for now */
        condyBuildTimeAllowList = Set.of();
        trustedCondy = Set.of(nullConstant, primitiveClass, enumConstant, getStaticFinal, invoke, explicitCast, classData, classDataAt);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        /*
         * Those methods are used by ObjectMethods.bootstrap to combine the Strings of the records
         * into one String
         */
        Class<?> stringConcatHelper = ReflectionUtil.lookupClass(false, "java.lang.StringConcatHelper");
        Class<?> formatConcatItem = ReflectionUtil.lookupClass(false, "jdk.internal.util.FormatConcatItem");
        RuntimeReflection.register(ReflectionUtil.lookupMethod(stringConcatHelper, "prepend", long.class, byte[].class, formatConcatItem, String.class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(stringConcatHelper, "mix", long.class, formatConcatItem));
    }

    /**
     * Check if the provided method is allowed to be executed at build time.
     */
    public boolean isIndyAllowedAtBuildTime(Executable method) {
        return method != null && indyBuildTimeAllowList.contains(method);
    }

    /**
     * Check if the provided method is allowed to be executed at build time.
     */
    public boolean isCondyAllowedAtBuildTime(Executable method) {
        return method != null && condyBuildTimeAllowList.contains(method);
    }

    /**
     * Check if the provided method is defined in the JDK.
     */
    public boolean isCondyTrusted(Executable method) {
        return method != null && trustedCondy.contains(method);
    }

    public ConcurrentMap<BootstrapMethodRecord, BootstrapMethodInfo> getBootstrapMethodInfoCache() {
        return bootstrapMethodInfoCache;
    }
}
