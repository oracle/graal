/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.features;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.util.AnalysisError;
import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;

import java.lang.reflect.Method;

public class DashboardDumpDelegate {

    /**
     * Options must not be declared inside subclass of
     * {@link org.graalvm.nativeimage.hosted.Feature}, because it may lead to native-image build
     * error. When an application calls {@link OptionsParser#getOptionsLoader()} is native-image
     * built, the pointsto analysis will get {@link org.graalvm.nativeimage.hosted.Feature} as
     * reachable if Options class is an inner class of Feature class. But
     * {@link org.graalvm.nativeimage.hosted.Feature} is annotated as HostedOnly, so an
     * UnsupportedFeatureException will be thrown. An instance is Libgraal.
     */
    public static class Options {
        @Option(help = "Dump pointsto analysis results for GraalVM dashboard to a json file, as a combination of -H:DashboardDump= and -H:+DashboardPointsTo and -H:+DashboardJson.")
//
        public static final OptionKey<String> DumpDashboardPointsto = new OptionKey<>(null) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
                Class<?> dashBoardOptionsClazz = null;
                try {
                    dashBoardOptionsClazz = Class.forName("com.oracle.svm.hosted.dashboard.DashboardOptions");
                } catch (ClassNotFoundException e) {
                    AnalysisError.dependencyNotExist("class DashboardOptions ", "svm.jar", e);
                }
                HostedOptionValueUpdater.registerHostedOptionUpdate(new HostedOptionValueUpdater.HostedOptionValue(newValue, dashBoardOptionsClazz, "DashboardDump"),
                                new HostedOptionValueUpdater.HostedOptionValue(true, dashBoardOptionsClazz, "DashboardPointsTo"),
                                new HostedOptionValueUpdater.HostedOptionValue(true, dashBoardOptionsClazz, "DashboardJson"));
            }
        };
    }

    @DelegateSVMFeature("com.oracle.svm.hosted.dashboard.DashboardDumpFeature")
    public static class Feature extends DelegateFeature {

        public Feature(OptionValues options) {
            super(options);
        }

        @Override
        protected boolean isEnabled(OptionValues options) {
            return Options.DumpDashboardPointsto.getValue(options) != null;
        }

        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            StandaloneAnalysisFeatureImpl.BeforeAnalysisAccessImpl a = (StandaloneAnalysisFeatureImpl.BeforeAnalysisAccessImpl) access;
            BigBang bb = a.getBigBang();

            try {
                Method setBBMethod = original.getClass().getDeclaredMethod("setBB", BigBang.class);
                setBBMethod.invoke(original, bb);
            } catch (ReflectiveOperationException e) {
                AnalysisError.dependencyNotExist("DashboardDumpFeature", "svm.jar", e);
            }
        }

        @Override
        public void onAnalysisExit(OnAnalysisExitAccess access) {
            original.onAnalysisExit(access);
        }
    }
}
