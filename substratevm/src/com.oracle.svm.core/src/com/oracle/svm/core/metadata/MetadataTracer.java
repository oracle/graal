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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.ProxyConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;
import com.oracle.svm.configure.config.ConfigurationFileCollection;
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
import jdk.graal.compiler.options.OptionStability;

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

        static final String RECORD_METADATA_HELP = """
                        Enables metadata tracing at run time. This option is only supported if -H:+MetadataTracingSupport is set when building the image.
                        The value of this option is a comma-separated list of arguments specified as key-value pairs. The following arguments are supported:

                        - path=<trace-output-directory> (required): Specifies the directory to write traced metadata to.
                        - merge=<boolean> (optional): Specifies whether to merge or overwrite metadata with existing files at the output path (default: true).

                        Example usage:
                            -H:RecordMetadata=path=trace_output_directory
                            -H:RecordMetadata=path=trace_output_directory,merge=false
                        """;

        @Option(help = RECORD_METADATA_HELP, stability = OptionStability.EXPERIMENTAL)//
        public static final RuntimeOptionKey<String> RecordMetadata = new RuntimeOptionKey<>(null);
    }

    private RecordOptions options;
    private volatile ConfigurationSet config;

    @Fold
    public static MetadataTracer singleton() {
        return ImageSingletons.lookup(MetadataTracer.class);
    }

    /**
     * Returns whether tracing is enabled at run time (using {@code -XX:RecordMetadata}).
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
        return traceReflectionTypeImpl(new NamedConfigurationTypeDescriptor(className));
    }

    /**
     * Marks the given proxy type as reachable from reflection.
     */
    public void traceProxyType(List<String> interfaceNames) {
        traceReflectionTypeImpl(new ProxyConfigurationTypeDescriptor(interfaceNames));
    }

    private ConfigurationType traceReflectionTypeImpl(ConfigurationTypeDescriptor typeDescriptor) {
        assert enabled();
        ConfigurationSet configurationSet = config;
        if (configurationSet != null) {
            return configurationSet.getReflectionConfiguration().getOrCreateType(UnresolvedConfigurationCondition.alwaysTrue(), typeDescriptor);
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
        ConfigurationType result = traceReflectionType(className);
        if (result != null) {
            result.setSerializable();
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

        MetadataTracer singleton = MetadataTracer.singleton();
        singleton.options = parsedOptions;
        singleton.config = initializeConfigurationSet(parsedOptions);
    }

    private static ConfigurationSet initializeConfigurationSet(RecordOptions options) {
        if (options.merge() && Files.exists(options.path())) {
            ConfigurationFileCollection mergeConfigs = new ConfigurationFileCollection();
            mergeConfigs.addDirectory(options.path());
            try {
                return mergeConfigs.loadConfigurationSet(ioexception -> ioexception, null, null);
            } catch (Exception ex) {
                // suppress and fall back on empty configuration set.
                Log.log().string("An exception occurred when loading merge metadata from path " + options.path() + ". ")
                                .string("Any existing metadata may be overwritten.").newline()
                                .string("Exception: ").exception(ex).newline();
            }
        }
        return new ConfigurationSet();
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

record RecordOptions(Path path, boolean merge) {

    private static final int ARGUMENT_PARTS = 2;

    static RecordOptions parse(String recordMetadataValue) {
        if (recordMetadataValue.isEmpty()) {
            throw printHelp("Option " + MetadataTracer.Options.RecordMetadata.getName() + " cannot be empty.");
        } else if (recordMetadataValue.equals("help")) {
            throw printHelp("Option " + MetadataTracer.Options.RecordMetadata.getName() + " value is 'help'. Printing a description and aborting.");
        }

        Map<String, String> parsedArguments = new HashMap<>();
        Set<String> allArguments = new LinkedHashSet<>(List.of("path", "merge"));
        for (String argument : recordMetadataValue.split(",")) {
            String[] parts = SubstrateUtil.split(argument, "=", ARGUMENT_PARTS);
            if (parts.length != ARGUMENT_PARTS) {
                throw badArgumentError(argument, "Argument should be a key-value pair separated by '='");
            } else if (!allArguments.contains(parts[0])) {
                throw badArgumentError(argument, "Argument key should be one of " + allArguments);
            } else if (parsedArguments.containsKey(parts[0])) {
                throw badArgumentError(argument, "Argument '" + parts[0] + "' was already specified with value '" + parsedArguments.get(parts[0]) + "'");
            } else if (parts[1].isEmpty()) {
                throw badArgumentError(argument, "Value cannot be empty");
            }
            parsedArguments.put(parts[0], parts[1]);
        }

        String path = requiredArgument(parsedArguments, "path", IDENTITY_PARSER);
        boolean merge = optionalArgument(parsedArguments, "merge", true, BOOLEAN_PARSER);
        return new RecordOptions(Paths.get(path), merge);
    }

    private static IllegalArgumentException printHelp(String errorMessage) {
        throw new IllegalArgumentException("""
                        %s

                        %s description:

                        %s
                        """.formatted(errorMessage, MetadataTracer.Options.RecordMetadata.getName(), MetadataTracer.Options.RECORD_METADATA_HELP));
    }

    private static IllegalArgumentException parseError(String message) {
        return new IllegalArgumentException(message + ". For more information (including usage examples), pass 'help' as an argument to " + MetadataTracer.Options.RecordMetadata.getName() + ".");
    }

    private static IllegalArgumentException badArgumentError(String argument, String message) {
        throw parseError("Bad argument provided for " + MetadataTracer.Options.RecordMetadata.getName() + ": '" + argument + "'. " + message);
    }

    private static IllegalArgumentException badArgumentValueError(String argumentKey, String argumentValue, String message) {
        throw badArgumentError(argumentKey + "=" + argumentValue, message);
    }

    private interface ArgumentParser<T> {
        T parse(String argumentKey, String argumentValue);
    }

    private static final ArgumentParser<String> IDENTITY_PARSER = ((argumentKey, argumentValue) -> argumentValue);
    private static final ArgumentParser<Boolean> BOOLEAN_PARSER = ((argumentKey, argumentValue) -> switch (argumentValue) {
        case "true" -> true;
        case "false" -> false;
        default -> throw badArgumentValueError(argumentKey, argumentValue, "Value must be a literal 'true' or 'false'");
    });

    private static <T> T requiredArgument(Map<String, String> arguments, String key, ArgumentParser<T> parser) {
        if (arguments.containsKey(key)) {
            return parser.parse(key, arguments.get(key));
        }
        throw parseError(MetadataTracer.Options.RecordMetadata.getName() + " missing required argument '" + key + "'");
    }

    private static <T> T optionalArgument(Map<String, String> options, String key, T defaultValue, ArgumentParser<T> parser) {
        if (options.containsKey(key)) {
            return parser.parse(key, options.get(key));
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
