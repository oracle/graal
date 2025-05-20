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
package com.oracle.svm.core.metadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.ConfigurationType;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jdk.RuntimeSupportFeature;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;

/**
 * Implements reachability metadata tracing during native image execution. Enabling
 * {@link Options#MetadataTracingSupport} at build time will generate code to trace all accesses of
 * reachability metadata, and then the run-time option {@link Options#RecordMetadata} enables
 * tracing.
 */
public final class MetadataTracer {

    public static class Options {
        @Option(help = "Generate an image that supports reachability metadata access tracing. " +
                        "When tracing is supported, use the -XX:RecordMetadata option to enable tracing at run time.")//
        public static final HostedOptionKey<Boolean> MetadataTracingSupport = new HostedOptionKey<>(false);

        @Option(help = "file:doc-files/RecordMetadataHelp.txt")//
        public static final RuntimeOptionKey<String> RecordMetadata = new RuntimeOptionKey<>(null);
    }


    private RecordOptions options;
    private volatile ConfigurationSet config;


    @Fold
    public static MetadataTracer singleton() {
        return ImageSingletons.lookup(MetadataTracer.class);
    }

    /**
     * Returns whether tracing is enabled at run time (using {@code -XX:RecordMetadata=path}).
     */
    public boolean enabled() {
        VMError.guarantee(Options.MetadataTracingSupport.getValue());
        return options != null;
    }

    /**
     * Marks the given type as reachable from reflection.
     *
     * @return the corresponding {@link ConfigurationType} or {@code null} if tracing is not active
     *         (e.g., during shutdown).
     */
    public ConfigurationType traceReflectionType(String className) {
        assert enabled();
        ConfigurationSet configurationSet = config;
        if (configurationSet != null) {
            return configurationSet.getReflectionConfiguration().getOrCreateType(UnresolvedConfigurationCondition.alwaysTrue(), new NamedConfigurationTypeDescriptor(className));
        }
        return null;
    }

    /**
     * Marks the given type as reachable from JNI.
     *
     * @return the corresponding {@link ConfigurationType} or {@code null} if tracing is not active
     *         (e.g., during shutdown).
     */
    public ConfigurationType traceJNIType(String className) {
        assert enabled();
        ConfigurationType result = traceReflectionType(className);
        if (result != null) {
            result.setJniAccessible();
        }
        return result;
    }

    /**
     * Marks the given resource within the given (optional) module as reachable.
     */
    public void traceResource(String resourceName, String moduleName) {
        assert enabled();
        ConfigurationSet configurationSet = config;
        if (configurationSet != null) {
            configurationSet.getResourceConfiguration().addGlobPattern(UnresolvedConfigurationCondition.alwaysTrue(), resourceName, moduleName);
        }
    }

    /**
     * Marks the given resource bundle within the given locale as reachable.
     */
    public void traceResourceBundle(String baseName) {
        assert enabled();
        ConfigurationSet configurationSet = config;
        if (configurationSet != null) {
            configurationSet.getResourceConfiguration().addBundle(UnresolvedConfigurationCondition.alwaysTrue(), baseName, List.of());
        }
    }

    /**
     * Marks the given type as serializable.
     */
    public void traceSerializationType(String className) {
        assert enabled();
        ConfigurationSet configurationSet = config;
        if (configurationSet != null) {
            configurationSet.getReflectionConfiguration().getOrCreateType(UnresolvedConfigurationCondition.alwaysTrue(), new NamedConfigurationTypeDescriptor(className)).setSerializable();
        }
    }

    private static void initialize(String recordMetadataValue) {
        assert Options.MetadataTracingSupport.getValue();

        RecordOptions parsedOptions = RecordOptions.parse(recordMetadataValue);
        try {
            Files.createDirectories(parsedOptions.path());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Exception occurred creating the output directory for tracing (" + parsedOptions.path() + ")", ex);
        }
        if (parsedOptions.mode() != RecordMode.DEFAULT) {
            throw new IllegalArgumentException("Mode " + parsedOptions.mode() + " is not yet supported.");
        }

        MetadataTracer singleton = MetadataTracer.singleton();
        singleton.options = parsedOptions;
        singleton.config = new ConfigurationSet();
    }

    private static void shutdown() {
        assert Options.MetadataTracingSupport.getValue();
        MetadataTracer singleton = MetadataTracer.singleton();
        ConfigurationSet config = singleton.config;
        singleton.config = null; // clear config so that shutdown events are not traced.
        if (config != null) {
            try {
                config.writeConfiguration(configFile -> singleton.options.path().resolve(configFile.getFileName()));
            } catch (IOException ex) {
                Log log = Log.log();
                log.string("Failed to write out reachability metadata to directory ").string(singleton.options.path().toString());
                log.string(":").string(ex.getMessage());
                log.newline();
            }
        }
    }

    static RuntimeSupport.Hook initializeMetadataTracingHook() {
        return isFirstIsolate -> {
            if (!isFirstIsolate) {
                return;
            }
            VMError.guarantee(Options.MetadataTracingSupport.getValue());
            if (Options.RecordMetadata.hasBeenSet()) {
                initialize(Options.RecordMetadata.getValue());
            }
        };
    }

    static RuntimeSupport.Hook shutDownMetadataTracingHook() {
        return isFirstIsolate -> {
            if (!isFirstIsolate) {
                return;
            }
            VMError.guarantee(Options.MetadataTracingSupport.getValue());
            if (Options.RecordMetadata.hasBeenSet()) {
                shutdown();
            }
        };
    }

    static RuntimeSupport.Hook checkImproperOptionUsageHook() {
        // Compute argument at build time (hosted option should not be reached in image code)
        String hostedOptionCommandArgument = SubstrateOptionsParser.commandArgument(Options.MetadataTracingSupport, "+");

        return isFirstIsolate -> {
            if (!isFirstIsolate) {
                return;
            }
            VMError.guarantee(!Options.MetadataTracingSupport.getValue());
            if (Options.RecordMetadata.hasBeenSet()) {
                throw new IllegalArgumentException(
                                "The option " + Options.RecordMetadata.getName() + " can only be used if metadata tracing is enabled at build time (using " +
                                                hostedOptionCommandArgument + ").");
            }
        };
    }
}

enum RecordMode {
    DEFAULT,
    CONDITIONAL
}

record RecordOptions(Path path, RecordMode mode) {

    static RecordOptions parse(String recordMetadataOptions) {
        if (recordMetadataOptions.isEmpty()) {
            throw parseError("Empty string provided for " + MetadataTracer.Options.RecordMetadata.getName() + ".");
        }

        Map<String, String> parsedOptions = new HashMap<>();
        Set<String> allOptions = Set.of("path", "mode");
        for (String option : recordMetadataOptions.split(",")) {
            String[] parts = SubstrateUtil.split(option, "=", 2);
            if (parts.length != 2) {
                throw badOptionError(option, "Option should be a key-value pair separated by '='.");
            } else if (!allOptions.contains(parts[0])) {
                throw badOptionError(option, "Option should be one of " + allOptions);
            } else if (parsedOptions.containsKey(parts[0])) {
                throw badOptionError(option, "Option was already specified with value " + parsedOptions.get(parts[0]));
            }
            parsedOptions.put(parts[0], parts[1]);
        }

        String path = requiredOption(parsedOptions, "path");
        String mode = optionalOption(parsedOptions, "mode", "default");
        return new RecordOptions(Paths.get(path), RecordMode.valueOf(mode.toUpperCase()));
    }

    private static IllegalArgumentException parseError(String message) {
        return new IllegalArgumentException(message + " Sample usage: -XX:" + MetadataTracer.Options.RecordMetadata.getName() + "=path=<trace-output-directory>[,mode=<mode>]");
    }

    private static IllegalArgumentException badOptionError(String option, String message) {
        throw parseError("Bad option provided for " + MetadataTracer.Options.RecordMetadata.getName() + ": " + option + ". " + message);
    }

    private static String requiredOption(Map<String, String> options, String optionKey) {
        if (options.containsKey(optionKey)) {
            return options.get(optionKey);
        }
        throw parseError(MetadataTracer.Options.RecordMetadata.getName() + " missing required option '" + optionKey + "'");
    }

    private static String optionalOption(Map<String, String> options, String optionKey, String defaultValue) {
        if (options.containsKey(optionKey)) {
            return options.get(optionKey);
        }
        return defaultValue;
    }
}

@AutomaticallyRegisteredFeature
class MetadataTracerFeature implements InternalFeature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(RuntimeSupportFeature.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (MetadataTracer.Options.MetadataTracingSupport.getValue()) {
            ImageSingletons.add(MetadataTracer.class, new MetadataTracer());
            RuntimeSupport.getRuntimeSupport().addInitializationHook(MetadataTracer.initializeMetadataTracingHook());
            RuntimeSupport.getRuntimeSupport().addTearDownHook(MetadataTracer.shutDownMetadataTracingHook());
        } else {
            RuntimeSupport.getRuntimeSupport().addInitializationHook(MetadataTracer.checkImproperOptionUsageHook());
        }
    }
}
