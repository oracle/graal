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

import com.oracle.svm.core.PreMainSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.reflect.ReflectionFeature;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * This feature supports instrumentation in native image.
 */
@AutomaticallyRegisteredFeature
public class InstrumentFeature implements InternalFeature {
    private ClassLoader cl;
    private PreMainSupport preMainSupport;

    public static class Options {
        @Option(help = "Specify premain-class list. Multiple classes are separated by comma, and order matters. This is an experimental option.", stability = OptionStability.EXPERIMENTAL)//
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> PremainClasses = new HostedOptionKey<>(
                        AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

    }

    /**
     * {@link ReflectionFeature} must come before this feature, because many instrumentation methods
     * are called by reflection, e.g. premain.
     */
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(ReflectionFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        FeatureImpl.AfterRegistrationAccessImpl a = (FeatureImpl.AfterRegistrationAccessImpl) access;
        cl = a.getImageClassLoader().getClassLoader();
        ImageSingletons.add(PreMainSupport.class, preMainSupport = new PreMainSupport());
        if (Options.PremainClasses.hasBeenSet()) {
            List<String> premains = Options.PremainClasses.getValue().values();
            for (String premain : premains) {
                addPremainClass(premain);
            }
        }
    }

    /**
     * Find the premain method from the given class and register it for runtime usage. According to
     * java.lang.instrument <a
     * href=https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/package-summary.html>API
     * doc</a>, there are two premain methods:
     * <ol>
     * <li>{@code public static void premain(String agentArgs, Instrumentation inst)}</li>
     * <li>{@code public static void premain(String agentArgs)}</li>
     * </ol>
     * The first one is taken with higher priority. The second one is taken only when the first one
     * is absent. <br>
     * So this method looks for them in the same order.
     */
    private void addPremainClass(String premainClass) {
        try {
            Class<?> clazz = Class.forName(premainClass, false, cl);
            Method premain = null;
            List<Object> args = new ArrayList<>();
            args.add(""); // First argument is options which will be set at runtime
            try {
                premain = clazz.getDeclaredMethod("premain", String.class, Instrumentation.class);
                args.add(new PreMainSupport.NativeImageNoOpRuntimeInstrumentation());
            } catch (NoSuchMethodException e) {
                try {
                    premain = clazz.getDeclaredMethod("premain", String.class);
                } catch (NoSuchMethodException e1) {
                    UserError.abort(e1, "Can't register agent premain method, because can't find the premain method from the given class %s. Please check your %s setting.", premainClass,
                                    SubstrateOptionsParser.commandArgument(Options.PremainClasses, ""));
                }
            }
            preMainSupport.registerPremainMethod(premainClass, premain, args.toArray(new Object[0]));
        } catch (ClassNotFoundException e) {
            UserError.abort(e, "Can't register agent premain method, because the given class %s is not found. Please check your %s setting.", premainClass,
                            SubstrateOptionsParser.commandArgument(Options.PremainClasses, ""));
        }
    }
}
