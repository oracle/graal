/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.util.Optional;

import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;

@AutomaticallyRegisteredFeature
public class LoggingFeature implements InternalFeature {

    private static Optional<Module> requiredModule() {
        return ModuleLayer.boot().findModule("java.logging");
    }

    public static class Options {
        @Option(help = "Enable the feature that provides support for logging.")//
        public static final HostedOptionKey<Boolean> EnableLoggingFeature = new HostedOptionKey<>(requiredModule().isPresent());

        @Option(help = "When enabled, logging feature details are printed.", type = OptionType.Debug) //
        public static final HostedOptionKey<Boolean> TraceLoggingFeature = new HostedOptionKey<>(false);
    }

    private final boolean trace = LoggingFeature.Options.TraceLoggingFeature.getValue();

    private Field loggersField;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        Boolean loggingEnabled = Options.EnableLoggingFeature.getValue();
        if (loggingEnabled && requiredModule().isEmpty()) {
            throw UserError.abort("Option %s requires JDK module java.logging to be available",
                            SubstrateOptionsParser.commandArgument(Options.EnableLoggingFeature, "+"));
        }
        return loggingEnabled;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        LoggingFeature.class.getModule().addReads(requiredModule().get());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        try {
            /* Ensure that the log manager is initialized and the initial configuration is read. */
            ReflectionUtil.lookupMethod(access.findClassByName("java.util.logging.LogManager"), "getLogManager").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere("Reflective LogManager initialization failed", e);
        }
        loggersField = ((DuringSetupAccessImpl) access).findField("sun.util.logging.PlatformLogger", "loggers");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler((a1) -> {
            registerForReflection(a1.findClassByName("java.util.logging.ConsoleHandler"));
            registerForReflection(a1.findClassByName("java.util.logging.SimpleFormatter"));
        }, access.findClassByName("java.util.logging.Logger"));
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        access.rescanRoot(loggersField);
    }

    private void registerForReflection(Class<?> clazz) {
        try {
            trace("Registering " + clazz + " for reflection.");
            RuntimeReflection.register(clazz);
            RuntimeReflection.register(clazz.getConstructor());
        } catch (NoSuchMethodException e) {
            VMError.shouldNotReachHere(e);
        }
    }

    private void trace(String msg) {
        if (trace) {
            System.out.println("LoggingFeature: " + msg);
        }
    }
}
