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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.configure.JsonFileWriter;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.ProxyConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;
import com.oracle.svm.configure.config.ConfigurationFileCollection;
import com.oracle.svm.configure.config.ConfigurationMemberInfo;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.ConfigurationType;
import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jdk.RuntimeSupportFeature;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;

/**
 * Implements reachability metadata tracing during native image execution. Enabling
 * {@link Options#MetadataTracingSupport} at build time will generate code to trace all accesses of
 * reachability metadata, and then the run-time option {@link Options#TraceMetadata} enables
 * tracing.
 */
public final class MetadataTracer {

    public static class Options {
        @Option(help = "Generate an image that supports reachability metadata access tracing. " +
                        "When tracing is supported, use the -XX:TraceMetadata option to enable tracing at run time.")//
        public static final HostedOptionKey<Boolean> MetadataTracingSupport = new HostedOptionKey<>(false);

        static final String TRACE_METADATA_HELP = """
                        Enables metadata tracing at run time. This option is only supported if -H:+MetadataTracingSupport is set when building the image.
                        The value of this option is a comma-separated list of arguments specified as key-value pairs. The following arguments are supported:

                        - path=<trace-output-directory> (required): Specifies the directory to write traced metadata to.
                        - merge=<boolean> (optional): Specifies whether to merge or overwrite metadata with existing files at the output path (default: true).
                        - debug-log=<path> (optional): Specifies a path to write debug output to. This option is meant for debugging; the option name and its
                          output format may change at any time.

                        Example usage:
                            -H:TraceMetadata=path=trace_output_directory
                            -H:TraceMetadata=path=trace_output_directory,merge=false
                        """;

        @Option(help = TRACE_METADATA_HELP, stability = OptionStability.EXPERIMENTAL)//
        public static final RuntimeOptionKey<String> TraceMetadata = new RuntimeOptionKey<>(null);
    }

    private TraceOptions options;
    private JsonFileWriter debugWriter;
    private final ThreadLocal<String> disableTracingReason = new ThreadLocal<>();

    /**
     * The configuration set to trace with. Do not read this field directly when tracing; instead
     * use {@link #getConfigurationSetForTracing()} to acquire it.
     */
    private volatile ConfigurationSet config;

    /**
     * Returns the singleton object, which is only available if tracing is enabled at build time.
     * <p>
     * We use {@code @AlwaysInline} and not {@code @Fold} because the latter eagerly evaluates the
     * method, which fails when the singleton is unavailable.
     */
    @AlwaysInline("avoid null check on singleton")
    public static MetadataTracer singleton() {
        return ImageSingletons.lookup(MetadataTracer.class);
    }

    private void initialize(TraceOptions parsedOptions) {
        this.options = parsedOptions;
        this.debugWriter = initializeDebugWriter(parsedOptions);
        this.config = initializeConfigurationSet(parsedOptions);
    }

    private void shutdown() {
        ConfigurationSet finalConfig = this.config;
        this.config = null; // clear config so that shutdown actions are not traced.

        if (finalConfig != null) {
            try {
                finalConfig.writeConfiguration(configFile -> this.options.path().resolve(configFile.getFileName()));
            } catch (IOException ex) {
                Log log = Log.log();
                log.string("Failed to write out reachability metadata to directory ").string(this.options.path().toString());
                log.string(":").string(ex.getMessage());
                log.newline();
            }
        }

        if (debugWriter != null) {
            debugWriter.close();
        }
    }

    /**
     * Returns whether tracing is enabled. Tracing code should be guarded by this condition.
     * <p>
     * This condition is force-inlined so that when tracing support is not included at build time
     * the condition folds to false and the tracing code itself will fold away.
     */
    @AlwaysInline("tracing should fold away when disabled")
    public static boolean enabled() {
        return Options.MetadataTracingSupport.getValue() && singleton().enabledAtRunTime();
    }

    /**
     * Returns whether tracing is enabled at run time (using {@code -XX:TraceMetadata}).
     */
    private boolean enabledAtRunTime() {
        VMError.guarantee(Options.MetadataTracingSupport.getValue());
        return options != null;
    }

    /**
     * Returns the configuration set to trace with. Returns {@code null} if tracing should not be
     * performed for some reason.
     */
    private ConfigurationSet getConfigurationSetForTracing() {
        if (disableTracingReason.get() != null || VMOperation.isInProgress()) {
            // Do not trace when tracing is disabled or during VM operations.
            return null;
        }
        return config;
    }

    /**
     * Marks the type with the given name as reachable from reflection.
     */
    public void traceReflectionType(String typeName) {
        traceReflectionTypeImpl(new NamedConfigurationTypeDescriptor(typeName));
    }

    /**
     * Marks the given type as reachable from reflection.
     */
    public void traceReflectionType(Class<?> clazz) {
        traceReflectionTypeImpl(ConfigurationTypeDescriptor.fromClass(clazz));
    }

    public void traceReflectionArrayType(Class<?> componentClazz) {
        ConfigurationTypeDescriptor typeDescriptor = ConfigurationTypeDescriptor.fromClass(componentClazz);
        if (typeDescriptor instanceof NamedConfigurationTypeDescriptor(String name)) {
            traceReflectionType(name + "[]");
        } else {
            debug("array type not registered for reflection (component type is not a named type)", typeDescriptor);
        }
    }

    /**
     * Marks the given field as accessible from reflection.
     */
    public void traceFieldAccess(Class<?> declaringClass, String fieldName, ConfigurationMemberInfo.ConfigurationMemberDeclaration declaration) {
        ConfigurationTypeDescriptor typeDescriptor = ConfigurationTypeDescriptor.fromClass(declaringClass);
        ConfigurationType type = traceReflectionTypeImpl(typeDescriptor);
        if (type != null) {
            debugField(typeDescriptor, fieldName);
            type.addField(fieldName, declaration, false);
        }
    }

    /**
     * Marks the given method as accessible from reflection.
     */
    public void traceMethodAccess(Class<?> declaringClass, String methodName, String internalSignature, ConfigurationMemberInfo.ConfigurationMemberDeclaration declaration) {
        ConfigurationTypeDescriptor typeDescriptor = ConfigurationTypeDescriptor.fromClass(declaringClass);
        ConfigurationType type = traceReflectionTypeImpl(typeDescriptor);
        if (type != null) {
            debugMethod(typeDescriptor, methodName, internalSignature);
            type.addMethod(methodName, internalSignature, declaration, ConfigurationMemberInfo.ConfigurationMemberAccessibility.ACCESSED);
        }
    }

    /**
     * Marks the given type as unsafely allocated.
     */
    public void traceUnsafeAllocatedType(Class<?> clazz) {
        ConfigurationTypeDescriptor typeDescriptor = ConfigurationTypeDescriptor.fromClass(clazz);
        ConfigurationType type = traceReflectionTypeImpl(typeDescriptor);
        if (type != null) {
            debug("type marked as unsafely allocated", clazz.getTypeName());
            type.setUnsafeAllocated();
        }
    }

    /**
     * Marks the given proxy type as reachable from reflection.
     */
    public void traceProxyType(Class<?>[] interfaces) {
        List<String> interfaceNames = Arrays.stream(interfaces).map(Class::getTypeName).toList();
        ProxyConfigurationTypeDescriptor descriptor = new ProxyConfigurationTypeDescriptor(interfaceNames);
        traceReflectionTypeImpl(descriptor);
    }

    private ConfigurationType traceReflectionTypeImpl(ConfigurationTypeDescriptor typeDescriptor) {
        assert enabledAtRunTime();
        if (isInternal(typeDescriptor)) {
            debug("type not registered for reflection (uses an internal interface)", typeDescriptor);
            return null;
        }
        ConfigurationSet configurationSet = getConfigurationSetForTracing();
        if (configurationSet != null) {
            debugReflectionType(typeDescriptor, configurationSet);
            return configurationSet.getReflectionConfiguration().getOrCreateType(UnresolvedConfigurationCondition.alwaysTrue(), typeDescriptor);
        }
        return null;
    }

    private static boolean isInternal(ConfigurationTypeDescriptor typeDescriptor) {
        if (typeDescriptor instanceof NamedConfigurationTypeDescriptor(String name)) {
            return isInternal(name);
        } else if (typeDescriptor instanceof ProxyConfigurationTypeDescriptor proxyType) {
            for (String interfaceName : proxyType.interfaceNames()) {
                if (isInternal(interfaceName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isInternal(String typeName) {
        return typeName.startsWith("com.oracle.svm.core");
    }

    /**
     * Marks the type with the given name as reachable from JNI.
     */
    public void traceJNIType(String typeName) {
        traceJNITypeImpl(new NamedConfigurationTypeDescriptor(typeName));
    }

    /**
     * Marks the given type as reachable from JNI.
     */
    public void traceJNIType(Class<?> clazz) {
        traceJNITypeImpl(ConfigurationTypeDescriptor.fromClass(clazz));
    }

    private void traceJNITypeImpl(ConfigurationTypeDescriptor typeDescriptor) {
        assert enabledAtRunTime();
        ConfigurationType type = traceReflectionTypeImpl(typeDescriptor);
        if (type != null && !type.isJniAccessible()) {
            debug("type registered for jni", typeDescriptor);
            type.setJniAccessible();
        }
    }

    /**
     * Marks the given resource within the given (optional) module as reachable. Use this method to
     * trace resource lookups covered by image metadata (including negative queries).
     */
    public void traceResource(String resourceName, String moduleName) {
        assert enabledAtRunTime();
        ConfigurationSet configurationSet = getConfigurationSetForTracing();
        if (configurationSet != null) {
            debugResourceGlob(resourceName, moduleName);
            configurationSet.getResourceConfiguration().addGlobPattern(UnresolvedConfigurationCondition.alwaysTrue(), resourceName, moduleName);
        }
    }

    /**
     * Marks the given resource bundle within the given locale as reachable.
     */
    public void traceResourceBundle(String baseName) {
        assert enabledAtRunTime();
        ConfigurationSet configurationSet = getConfigurationSetForTracing();
        if (configurationSet != null) {
            debug("resource bundle registered", baseName);
            configurationSet.getResourceConfiguration().addBundle(UnresolvedConfigurationCondition.alwaysTrue(), baseName, List.of());
        }
    }

    /**
     * Marks the given type as serializable.
     */
    public void traceSerializationType(Class<?> clazz) {
        assert enabledAtRunTime();
        ConfigurationTypeDescriptor typeDescriptor = ConfigurationTypeDescriptor.fromClass(clazz);
        ConfigurationType result = traceReflectionTypeImpl(typeDescriptor);
        if (result != null && !result.isSerializable()) {
            debug("type registered for serialization", typeDescriptor);
            result.setSerializable();
        }
    }

    /**
     * Main entrypoint for debug logging. Emits a JSON object to the debug log with the given
     * message and element.
     */
    @SuppressWarnings("try")
    private void debug(String message, Object element) {
        if (debugWriter == null) {
            return;
        }
        assert enabledAtRunTime();
        try (var ignored = new DisableTracingImpl("debug logging")) {
            EconomicMap<String, Object> entry = EconomicMap.create();
            entry.put("message", message);
            entry.put("element", element);
            entry.put("stacktrace", debugStackTrace());
            debugWriter.printObject(entry);
        }
    }

    private static StackTraceElement[] debugStackTrace() {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        // Trim the prefix containing "getStackTrace" and the various "debug" methods.
        int i = 0;
        while (i < trace.length && (trace[i].getMethodName().contains("getStackTrace") || trace[i].getMethodName().startsWith("debug"))) {
            i++;
        }
        return Arrays.copyOfRange(trace, i, trace.length);
    }

    /**
     * Debug helper for resource globs. Avoids glob name computations if debug logging is disabled.
     */
    private void debugResourceGlob(String resourceName, String moduleName) {
        if (debugWriter == null) {
            return;
        }
        String element = (moduleName == null) ? resourceName : String.format("%s:%s", moduleName, resourceName);
        debug("resource glob registered", element);
    }

    /**
     * Debug helper for reflective type accesses. Avoids "type is already registered" check if debug
     * logging is disabled.
     */
    private void debugReflectionType(ConfigurationTypeDescriptor typeDescriptor, ConfigurationSet configurationSet) {
        if (debugWriter == null) {
            return;
        }
        if (configurationSet.getReflectionConfiguration().get(UnresolvedConfigurationCondition.alwaysTrue(), typeDescriptor) == null) {
            debug("type registered for reflection", typeDescriptor);
        }
    }

    /**
     * Debug helper for fields. Avoids field name computations if debug logging is disabled.
     */
    private void debugField(ConfigurationTypeDescriptor typeDescriptor, String fieldName) {
        if (debugWriter == null) {
            return;
        }
        debug("field registered for reflection", typeDescriptor + "." + fieldName);
    }

    /**
     * Debug helper for methods. Avoids method name computations if debug logging is disabled.
     */
    private void debugMethod(ConfigurationTypeDescriptor typeDescriptor, String methodName, String internalSignature) {
        if (debugWriter == null) {
            return;
        }
        debug("method registered for reflection", typeDescriptor + "." + methodName + internalSignature);
    }

    /**
     * Disables tracing on the current thread from instantiation until {@link #close}.
     */
    public sealed interface DisableTracing extends AutoCloseable {
        @Override
        void close();
    }

    private final class DisableTracingImpl implements DisableTracing {
        final String oldReason;

        private DisableTracingImpl(String reason) {
            this.oldReason = disableTracingReason.get();
            disableTracingReason.set(reason);
        }

        @Override
        public void close() {
            disableTracingReason.set(oldReason);
        }
    }

    private static final class DisableTracingNoOp implements DisableTracing {
        private static final DisableTracingNoOp INSTANCE = new DisableTracingNoOp();

        @Override
        public void close() {
            // do nothing
        }
    }

    /**
     * Disables tracing on the current thread from instantiation until {@link DisableTracing#close}.
     * Should be used in a try-with-resources block.
     */
    public static DisableTracing disableTracing(String reason) {
        if (Options.MetadataTracingSupport.getValue() && singleton().enabledAtRunTime()) {
            return singleton().new DisableTracingImpl(reason);
        } else {
            // Fallback implementation when tracing is not enabled at build time.
            return DisableTracingNoOp.INSTANCE;
        }
    }

    private static void initializeSingleton(String recordMetadataValue) {
        assert Options.MetadataTracingSupport.getValue();
        MetadataTracer.singleton().initialize(TraceOptions.parse(recordMetadataValue));
    }

    private static JsonFileWriter initializeDebugWriter(TraceOptions options) {
        if (options.debugLog() == null) {
            return null;
        }
        try {
            Path parentDir = options.debugLog().getParent();
            if (parentDir == null) {
                throw new IllegalArgumentException("Invalid debug-log path '" + options.debugLog() + "'.");
            }
            Files.createDirectories(parentDir);
            return new JsonFileWriter(options.debugLog());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Exception occurred preparing the debug log file (" + options.debugLog() + ")", ex);
        }
    }

    private static ConfigurationSet initializeConfigurationSet(TraceOptions options) {
        try {
            Files.createDirectories(options.path());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Exception occurred creating the output directory for tracing (" + options.path() + ")", ex);
        }

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

    private static void shutdownSingleton() {
        assert Options.MetadataTracingSupport.getValue();
        MetadataTracer.singleton().shutdown();
    }

    static RuntimeSupport.Hook initializeMetadataTracingHook() {
        return isFirstIsolate -> {
            if (!isFirstIsolate) {
                return;
            }
            VMError.guarantee(Options.MetadataTracingSupport.getValue());
            if (Options.TraceMetadata.hasBeenSet()) {
                initializeSingleton(Options.TraceMetadata.getValue());
            }
        };
    }

    static RuntimeSupport.Hook shutDownMetadataTracingHook() {
        return isFirstIsolate -> {
            if (!isFirstIsolate) {
                return;
            }
            VMError.guarantee(Options.MetadataTracingSupport.getValue());
            if (Options.TraceMetadata.hasBeenSet()) {
                shutdownSingleton();
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
            if (Options.TraceMetadata.hasBeenSet()) {
                throw new IllegalArgumentException(
                                "The option " + Options.TraceMetadata.getName() + " can only be used if metadata tracing is enabled at build time (using " +
                                                hostedOptionCommandArgument + ").");
            }
        };
    }
}

record TraceOptions(Path path, boolean merge, Path debugLog) {

    private static final int ARGUMENT_PARTS = 2;

    static TraceOptions parse(String traceMetadataValue) {
        if (traceMetadataValue.isEmpty()) {
            throw printHelp("Option " + MetadataTracer.Options.TraceMetadata.getName() + " cannot be empty.");
        } else if (traceMetadataValue.equals("help")) {
            throw printHelp("Option " + MetadataTracer.Options.TraceMetadata.getName() + " value is 'help'. Printing a description and aborting.");
        }

        Map<String, String> parsedArguments = new HashMap<>();
        Set<String> allArguments = new LinkedHashSet<>(List.of("path", "merge", "debug-log"));
        for (String argument : traceMetadataValue.split(",")) {
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

        Path path = requiredArgument(parsedArguments, "path", PATH_PARSER);
        boolean merge = optionalArgument(parsedArguments, "merge", true, BOOLEAN_PARSER);
        Path debugLog = optionalArgument(parsedArguments, "debug-log", null, PATH_PARSER);
        return new TraceOptions(path, merge, debugLog);
    }

    private static IllegalArgumentException printHelp(String errorMessage) {
        throw new IllegalArgumentException("""
                        %s

                        %s description:

                        %s
                        """.formatted(errorMessage, MetadataTracer.Options.TraceMetadata.getName(), MetadataTracer.Options.TRACE_METADATA_HELP));
    }

    private static IllegalArgumentException parseError(String message) {
        return new IllegalArgumentException(message + ". For more information (including usage examples), pass 'help' as an argument to " + MetadataTracer.Options.TraceMetadata.getName() + ".");
    }

    private static IllegalArgumentException badArgumentError(String argument, String message) {
        throw parseError("Bad argument provided for " + MetadataTracer.Options.TraceMetadata.getName() + ": '" + argument + "'. " + message);
    }

    private static IllegalArgumentException badArgumentValueError(String argumentKey, String argumentValue, String message) {
        throw badArgumentError(argumentKey + "=" + argumentValue, message);
    }

    private interface ArgumentParser<T> {
        T parse(String argumentKey, String argumentValue);
    }

    private static final ArgumentParser<Path> PATH_PARSER = ((argumentKey, argumentValue) -> Paths.get(argumentValue).toAbsolutePath());
    private static final ArgumentParser<Boolean> BOOLEAN_PARSER = ((argumentKey, argumentValue) -> switch (argumentValue) {
        case "true" -> true;
        case "false" -> false;
        default -> throw badArgumentValueError(argumentKey, argumentValue, "Value must be a literal 'true' or 'false'");
    });

    private static <T> T requiredArgument(Map<String, String> arguments, String key, ArgumentParser<T> parser) {
        if (arguments.containsKey(key)) {
            return parser.parse(key, arguments.get(key));
        }
        throw parseError(MetadataTracer.Options.TraceMetadata.getName() + " missing required argument '" + key + "'");
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
