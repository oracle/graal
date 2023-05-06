/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Alibaba Group Holding Limited. All rights reserved.
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
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.InstrumentConfigurationParser;
import com.oracle.svm.core.configure.InstrumentRegistry;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.hosted.reflect.ReflectionFeature;
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

    /**
     * {@link ReflectionFeature} must come before this feature, because many instrumentation methods
     * are called by reflection, e.g. premain.
     *
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
        InstrumentRegistry registry = new InstrumentRegistryImpl();
        InstrumentConfigurationParser parser = new InstrumentConfigurationParser(registry, ConfigurationFiles.Options.StrictConfiguration.getValue());
        ConfigurationParserUtils.parseAndRegisterConfigurations(parser, a.getImageClassLoader(), "class instrumentation",
                        ConfigurationFiles.Options.InstrumentConfigurationFiles, ConfigurationFiles.Options.InstrumentConfigurationResources,
                        ConfigurationFile.INSTRUMENT.getFileName());
    }

    class InstrumentRegistryImpl implements InstrumentRegistry {

        /**
         * Find the premain method from the given class and register it for runtime usage. According
         * to java.lang.instrument <a
         * href=https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/package-summary.html>API
         * doc</a>, there are two premain methods:
         * <ol>
         * <li>{@code public static void premain(String agentArgs, Instrumentation inst)}</li>
         * <li>{@code public static void premain(String agentArgs)}</li>
         * </ol>
         * The first one is taken with higher priority. The second one is taken only when the first
         * one is absent. <br>
         * So this method looks for them in the same order.
         */
        @Override
        public void add(String premainClass, int index, String options) {
            try {
                Class<?> clazz = Class.forName(premainClass, false, cl);
                Method premain = null;
                List<Object> args = new ArrayList<>();
                args.add(options);
                try {
                    premain = clazz.getDeclaredMethod("premain", String.class, Instrumentation.class);
                    args.add(new PreMainSupport.SVMRuntimeInstrumentImpl());
                } catch (NoSuchMethodException e) {
                    try {
                        premain = clazz.getDeclaredMethod("premain", String.class);
                    } catch (NoSuchMethodException e1) {
                        VMError.shouldNotReachHere("Can't find premain method in " + premainClass, e1);
                    }
                }
                preMainSupport.registerPremainMethod(premainClass, premain, args.toArray(new Object[0]));
            } catch (ClassNotFoundException e) {
                VMError.shouldNotReachHere("Can't register agent premain method, because the declaring class " + premainClass + " is not found", e);
            }
        }

        @Override
        public void add(String transformedClass, String type) {
            /**
             * The transformed classes have been prepend to the native image build time classpath at
             * {@link com.oracle.svm.driver.NativeImage.DriverMetaInfProcessor#processMetaInfResource}.
             * There is nothing need to do here.
             */
        }
    }
}
