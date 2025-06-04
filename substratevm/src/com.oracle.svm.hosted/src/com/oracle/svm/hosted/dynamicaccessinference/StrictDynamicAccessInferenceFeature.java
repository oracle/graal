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

import java.lang.reflect.Field;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;

@AutomaticallyRegisteredFeature
public class StrictDynamicAccessInferenceFeature implements InternalFeature {

    public static class Options {

        public enum Mode {
            Disable,
            Warn,
            Enforce
        }

        @Option(help = """
                        Select the mode for the strict, build-time inference for calls requiring dynamic access.
                        Possible values are:
                         "Disable" (default): Disable the strict mode and fall back to the optimization dependent inference for dynamic calls;
                         "Warn": Fold both the calls inferred in the strict mode and the optimization dependent mode, but print a warning for non-strict call folding;
                         "Enforce": Fold only the calls inferred in the strict inference mode.""", stability = OptionStability.EXPERIMENTAL)//
        public static final HostedOptionKey<Mode> StrictDynamicAccessInference = new HostedOptionKey<>(Mode.Disable);
    }

    public static boolean isActive() {
        return Options.StrictDynamicAccessInference.getValue() != Options.Mode.Disable;
    }

    public static boolean isEnforced() {
        return Options.StrictDynamicAccessInference.getValue() == Options.Mode.Enforce;
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return isActive();
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
