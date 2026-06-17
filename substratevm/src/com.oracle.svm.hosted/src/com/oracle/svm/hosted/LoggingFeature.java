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

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.shared.BuildPhaseProvider;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.HostModuleUtil;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.vmaccess.ResolvedJavaModule;

/**
 * Initializes JDK logging support for Native Image and reconstructs the weak
 * {@code sun.util.logging.PlatformLogger.loggers} cache from reachable build-time logger objects.
 * This keeps the runtime cache consistent by rebuilding it from reachable loggers instead of
 * tracking hosted cache mutations directly.
 */
@AutomaticallyRegisteredFeature
public class LoggingFeature implements InternalFeature {

    private static Optional<ResolvedJavaModule> requiredModule() {
        return JVMCIReflectionUtil.bootModuleLayer().findModule("java.logging");
    }

    static class Options {
        @Option(help = "Enable the feature that provides support for logging.")//
        public static final HostedOptionKey<Boolean> EnableLoggingFeature = new HostedOptionKey<>(false);

        @Option(help = "When enabled, logging feature details are printed.", type = OptionType.Debug) //
        public static final HostedOptionKey<Boolean> TraceLoggingFeature = new HostedOptionKey<>(false);
    }

    /**
     * Returns {@code true} if this feature is enabled. We need this helper as querying
     * {@link #requiredModule()} when constructing a default value for {@code EnableLoggingFeature}
     * causes recursive initialization of the {@link jdk.vm.ci.runtime.JVMCIRuntime}.
     */
    private static boolean isLoggingEnabled(boolean hasJavaLoggingModule) {
        if (!Options.EnableLoggingFeature.hasBeenSet()) {
            return hasJavaLoggingModule;
        }
        return Options.EnableLoggingFeature.getValue();
    }

    private ResolvedJavaModule javaLoggingModule;
    private boolean loggingEnabled;

    private final boolean trace = LoggingFeature.Options.TraceLoggingFeature.getValue();

    private Field loggersField;
    private final Map<String, Object> reachablePlatformLoggers = new ConcurrentHashMap<>();

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        javaLoggingModule = requiredModule().orElse(null);
        loggingEnabled = isLoggingEnabled(javaLoggingModule != null);
        if (loggingEnabled && javaLoggingModule == null) {
            throw UserError.abort("Option %s requires JDK module java.logging to be available",
                            SubstrateOptionsParser.commandArgument(Options.EnableLoggingFeature, "+"));
        }
        return javaLoggingModule != null;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        HostModuleUtil.addReads(LoggingFeature.class, javaLoggingModule);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        DuringSetupAccessImpl accessImpl = (DuringSetupAccessImpl) access;
        Class<?> platformLoggerClass = access.findClassByName("sun.util.logging.PlatformLogger");
        loggersField = accessImpl.findField("sun.util.logging.PlatformLogger", "loggers");

        if (loggingEnabled) {
            /*
             * Ensure that the log manager is initialized and the initial configuration is read.
             */
            ReflectionUtil.invokeMethod(ReflectionUtil.lookupMethod(access.findClassByName("java.util.logging.LogManager"), "getLogManager"), null);

            Method platformLoggerGetNameMethod = ReflectionUtil.lookupMethod(platformLoggerClass, "getName");
            /*
             * PlatformLogger.loggers is a weak cache. Loggers that are only reachable through that
             * cache may disappear and be recreated by name, so only otherwise reachable logger
             * objects need to be reconstructed into the image heap cache.
             */
            accessImpl.registerObjectReachableCallback(platformLoggerClass, (_, logger, _) -> collectReachablePlatformLogger(platformLoggerGetNameMethod, logger));
        }
    }

    @SuppressWarnings("unused")
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (loggingEnabled) {
            access.registerFieldValueTransformer(loggersField, new PlatformLoggerCacheTransformer());
            access.registerReachabilityHandler((a1) -> {
                registerForReflection(a1.findClassByName("java.util.logging.ConsoleHandler"));
                registerForReflection(a1.findClassByName("java.util.logging.SimpleFormatter"));
            }, access.findClassByName("java.util.logging.Logger"));
        } else {
            access.registerFieldValueTransformer(loggersField, (receiver, originalValue) -> new HashMap<>());
        }
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

    private final class PlatformLoggerCacheTransformer implements FieldValueTransformerWithAvailability {
        @Override
        public boolean isAvailable() {
            return BuildPhaseProvider.isHostedUniverseBuilt();
        }

        @Override
        public Object transform(Object receiver, Object originalValue) {
            Map<String, WeakReference<Object>> originalLoggers = asPlatformLoggerCache(originalValue);
            HashMap<String, WeakReference<Object>> rebuiltLoggers = new HashMap<>(reachablePlatformLoggers.size());
            reachablePlatformLoggers.forEach((loggerName, logger) -> {
                WeakReference<Object> originalRef = originalLoggers.get(loggerName);
                /*
                 * Rebuild only entries that were already present in the original weak cache. This
                 * preserves the existing PlatformLogger factory semantics and avoids inserting
                 * merely reachable loggers that were created outside PlatformLogger.getLogger().
                 */
                if (originalRef != null && originalRef.get() == logger) {
                    rebuiltLoggers.put(loggerName, new WeakReference<>(logger));
                }
            });
            return rebuiltLoggers;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, WeakReference<Object>> asPlatformLoggerCache(Object originalValue) {
        return (Map<String, WeakReference<Object>>) originalValue;
    }

    private void collectReachablePlatformLogger(Method platformLoggerGetNameMethod, Object logger) {
        String loggerName = ReflectionUtil.invokeMethod(platformLoggerGetNameMethod, logger);
        Object previous = reachablePlatformLoggers.putIfAbsent(loggerName, logger);
        VMError.guarantee(previous == null || previous == logger, "Unexpected duplicate PlatformLogger for name %s", loggerName);
    }
}
