/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Consumer;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.Feature;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;

@AutomaticFeature
public class ClassInitializationFeature implements Feature {

    private ClassInitializationSupport classInitializationSupport;

    public static class Options {
        @APIOption(name = "delay-class-initialization-to-runtime")//
        @Option(help = "A comma-separated list of classes (and implicitly all of their subclasses) that are initialized at runtime and not during image building", type = OptionType.User)//
        public static final HostedOptionKey<String[]> DelayClassInitialization = new HostedOptionKey<>(null);

        @APIOption(name = "rerun-class-initialization-at-runtime") //
        @Option(help = "A comma-separated list of classes (and implicitly all of their subclasses) that are initialized both at runtime and during image building", type = OptionType.User)//
        public static final HostedOptionKey<String[]> RerunClassInitialization = new HostedOptionKey<>(null);
    }

    public static void processClassInitializationOptions(FeatureImpl.AfterRegistrationAccessImpl access, ClassInitializationSupport initializationSupport) {
        processOption(access, ClassInitializationFeature.Options.DelayClassInitialization, initializationSupport::delayClassInitialization);
        processOption(access, ClassInitializationFeature.Options.RerunClassInitialization, initializationSupport::rerunClassInitialization);
    }

    private static void processOption(FeatureImpl.AfterRegistrationAccessImpl access, HostedOptionKey<String[]> option, Consumer<Class<?>[]> handler) {
        for (String className : OptionUtils.flatten(",", option.getValue())) {
            if (className.length() > 0) {
                Class<?> clazz = access.findClassByName(className);
                if (clazz == null) {
                    throw UserError.abort("Could not find class " + className +
                                    " that is provided by the option " + SubstrateOptionsParser.commandArgument(option, className));
                }
                handler.accept(new Class<?>[]{clazz});
            }
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        classInitializationSupport = access.getHostVM().getClassInitializationSupport();
        classInitializationSupport.setUnsupportedFeatures(access.getBigBang().getUnsupportedFeatures());
        access.registerObjectReplacer(classInitializationSupport::checkImageHeapInstance);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        /*
         * Check early and often during static analysis if any class that must not have been
         * initialized during image building got initialized. We want to fail as early as possible,
         * even though we cannot pinpoint the exact time and reason why initialization happened.
         */
        classInitializationSupport.checkDelayedInitialization();

        for (AnalysisType type : access.getUniverse().getTypes()) {
            if (type.isInTypeCheck() || type.isInstantiated()) {
                DynamicHub hub = access.getHostVM().dynamicHub(type);
                if (hub.getClassInitializationInfo() == null) {
                    classInitializationSupport.buildClassInitializationInfo(access, type, hub);
                    access.requireAnalysisIteration();
                }
            }
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        classInitializationSupport.setUnsupportedFeatures(null);
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess a) {
        /*
         * This is the final time to check if any class that must not have been initialized during
         * image building got initialized.
         */
        classInitializationSupport.checkDelayedInitialization();
    }

}
