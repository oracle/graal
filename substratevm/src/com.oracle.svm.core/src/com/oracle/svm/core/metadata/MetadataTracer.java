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
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.ProxyConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.ConfigurationType;
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
 * reachability metadata. When {@link Options#RecordMetadata} is specified at run time, the image
 * will trace and emit metadata to the specified path.
 */
public final class MetadataTracer {

    public static class Options {
        @Option(help = "Enables the run-time code to trace reachability metadata accesses in the produced native image by using -XX:RecordMetadata=<path>.")//
        public static final HostedOptionKey<Boolean> MetadataTracingSupport = new HostedOptionKey<>(false);

        @Option(help = "The path of the directory to write traced metadata to. Metadata tracing is enabled only when this option is provided.")//
        public static final RuntimeOptionKey<String> RecordMetadata = new RuntimeOptionKey<>("");
    }

    private ConfigurationSet config;

    private Path recordMetadataPath;

    @Fold
    public static MetadataTracer singleton() {
        return ImageSingletons.lookup(MetadataTracer.class);
    }

    public boolean enabled() {
        VMError.guarantee(Options.MetadataTracingSupport.getValue());
        return config != null;
    }

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
        return config.getReflectionConfiguration().getOrCreateType(UnresolvedConfigurationCondition.alwaysTrue(), new NamedConfigurationTypeDescriptor(className));
    }

    public ConfigurationType traceJNIType(String className) {
        assert enabled();
        ConfigurationType result = traceReflectionType(className);
        result.setJniAccessible();
        return result;
    }

    public void traceResource(String resourceName, String moduleName) {
        assert enabled();
        config.getResourceConfiguration().addGlobPattern(UnresolvedConfigurationCondition.alwaysTrue(), resourceName, moduleName);
    }

    public void traceResourceBundle(String baseName) {
        assert enabled();
        config.getResourceConfiguration().addBundle(UnresolvedConfigurationCondition.alwaysTrue(), baseName, List.of());
    }

    public void traceSerializationType(String className) {
        assert enabled();
        traceReflectionType(className).setSerializable();
    }

    private static void initialize() {
        assert Options.MetadataTracingSupport.getValue();
        MetadataTracer singleton = MetadataTracer.singleton();
        String recordMetadataValue = Options.RecordMetadata.getValue();
        if (recordMetadataValue.isEmpty()) {
            throw new IllegalArgumentException("Empty path provided for " + Options.RecordMetadata.getName() + ".");
        }
        Path recordMetadataPath = Paths.get(recordMetadataValue);
        try {
            Files.createDirectories(recordMetadataPath);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Exception occurred creating the output directory for tracing (" + recordMetadataPath + ")", ex);
        }
        singleton.recordMetadataPath = recordMetadataPath;
        singleton.config = new ConfigurationSet();
    }

    private static void shutdown() {
        assert Options.MetadataTracingSupport.getValue();
        MetadataTracer singleton = MetadataTracer.singleton();
        ConfigurationSet config = singleton.config;
        if (config != null) {
            try {
                config.writeConfiguration(configFile -> singleton.recordMetadataPath.resolve(configFile.getFileName()));
            } catch (IOException ex) {
                Log log = Log.log();
                log.string("Failed to write out reachability metadata to directory ").string(singleton.recordMetadataPath.toString());
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
                initialize();
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
