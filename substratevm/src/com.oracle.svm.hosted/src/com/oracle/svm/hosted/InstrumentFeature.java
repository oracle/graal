/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.hosted;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.PreMainSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.reflect.ReflectionFeature;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;

/**
 * This feature supports instrumentation in native image.
 */
@AutomaticallyRegisteredFeature
public class InstrumentFeature implements InternalFeature {
    public static class Options {
        @Option(help = "Specify premain-class list. Multiple classes are separated by comma, and order matters. This is an experimental option.", stability = OptionStability.EXPERIMENTAL)//
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> PremainClasses = new HostedOptionKey<>(
                        AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return !Options.PremainClasses.getValue().values().isEmpty();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        /* Many instrumentation methods are called via reflection, e.g. premain. */
        return List.of(ReflectionFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        FeatureImpl.AfterRegistrationAccessImpl a = (FeatureImpl.AfterRegistrationAccessImpl) access;
        ClassLoader cl = a.getImageClassLoader().getClassLoader();
        PreMainSupport support = new PreMainSupport();
        ImageSingletons.add(PreMainSupport.class, support);

        List<String> premainClasses = Options.PremainClasses.getValue().values();
        for (String clazz : premainClasses) {
            addPremainClass(support, cl, clazz);
        }
    }

    private static void addPremainClass(PreMainSupport support, ClassLoader cl, String premainClass) {
        try {
            Class<?> clazz = Class.forName(premainClass, false, cl);
            Method premain = findPremainMethod(premainClass, clazz);

            List<Object> args = new ArrayList<>();
            /* The first argument contains the premain options, which will be set at runtime. */
            args.add("");
            if (premain.getParameterCount() == 2) {
                args.add(new PreMainSupport.NativeImageNoOpRuntimeInstrumentation());
            }

            support.registerPremainMethod(premainClass, premain, args.toArray(new Object[0]));
        } catch (ClassNotFoundException e) {
            UserError.abort("Could not register agent premain method because class %s was not found. Please check your %s setting.", premainClass,
                            SubstrateOptionsParser.commandArgument(Options.PremainClasses, ""));
        }
    }

    /** Find the premain method from the given class. */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+25/src/java.instrument/share/classes/sun/instrument/InstrumentationImpl.java#L481-L548")
    private static Method findPremainMethod(String premainClass, Class<?> javaAgentClass) {
        try {
            return javaAgentClass.getDeclaredMethod("premain", String.class, Instrumentation.class);
        } catch (NoSuchMethodException ignored) {
        }

        try {
            return javaAgentClass.getDeclaredMethod("premain", String.class);
        } catch (NoSuchMethodException ignored) {
        }

        throw UserError.abort("Could not register agent premain method: class %s neither declares 'premain(String, Instrumentation)' nor 'premain(String)'.", premainClass);
    }
}
