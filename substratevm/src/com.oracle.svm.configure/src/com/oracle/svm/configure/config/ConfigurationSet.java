/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.config;

import static com.oracle.svm.core.configure.ConfigurationParser.JNI_KEY;
import static com.oracle.svm.core.configure.ConfigurationParser.REFLECTION_KEY;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.config.conditional.ConditionalConfigurationPredicate;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JsonPrintable;
import com.oracle.svm.core.util.json.JsonWriter;

public class ConfigurationSet {
    @FunctionalInterface
    private interface Mutator {
        <T extends ConfigurationBase<T, ?>> T apply(T first, T other);
    }

    private final TypeConfiguration reflectionConfiguration;
    private final TypeConfiguration jniConfiguration;
    private final ResourceConfiguration resourceConfiguration;
    private final ProxyConfiguration proxyConfiguration;
    private final SerializationConfiguration serializationConfiguration;
    private final PredefinedClassesConfiguration predefinedClassesConfiguration;

    public ConfigurationSet(TypeConfiguration reflectionConfiguration, TypeConfiguration jniConfiguration, ResourceConfiguration resourceConfiguration, ProxyConfiguration proxyConfiguration,
                    SerializationConfiguration serializationConfiguration, PredefinedClassesConfiguration predefinedClassesConfiguration) {
        this.reflectionConfiguration = reflectionConfiguration;
        this.jniConfiguration = jniConfiguration;
        this.resourceConfiguration = resourceConfiguration;
        this.proxyConfiguration = proxyConfiguration;
        this.serializationConfiguration = serializationConfiguration;
        this.predefinedClassesConfiguration = predefinedClassesConfiguration;
    }

    public ConfigurationSet(ConfigurationSet other) {
        this(other.reflectionConfiguration.copy(), other.jniConfiguration.copy(), other.resourceConfiguration.copy(), other.proxyConfiguration.copy(), other.serializationConfiguration.copy(),
                        other.predefinedClassesConfiguration.copy());
    }

    public ConfigurationSet() {
        this(new TypeConfiguration(REFLECTION_KEY), new TypeConfiguration(JNI_KEY), new ResourceConfiguration(), new ProxyConfiguration(), new SerializationConfiguration(),
                        new PredefinedClassesConfiguration(new Path[0], hash -> false));
    }

    private ConfigurationSet mutate(ConfigurationSet other, Mutator mutator) {
        TypeConfiguration reflectionConfig = mutator.apply(this.reflectionConfiguration, other.reflectionConfiguration);
        TypeConfiguration jniConfig = mutator.apply(this.jniConfiguration, other.jniConfiguration);
        ResourceConfiguration resourceConfig = mutator.apply(this.resourceConfiguration, other.resourceConfiguration);
        ProxyConfiguration proxyConfig = mutator.apply(this.proxyConfiguration, other.proxyConfiguration);
        SerializationConfiguration serializationConfig = mutator.apply(this.serializationConfiguration, other.serializationConfiguration);
        PredefinedClassesConfiguration predefinedClassesConfig = mutator.apply(this.predefinedClassesConfiguration, other.predefinedClassesConfiguration);
        return new ConfigurationSet(reflectionConfig, jniConfig, resourceConfig, proxyConfig, serializationConfig, predefinedClassesConfig);
    }

    public ConfigurationSet copyAndMerge(ConfigurationSet other) {
        return mutate(other, ConfigurationBase::copyAndMerge);
    }

    public ConfigurationSet copyAndSubtract(ConfigurationSet other) {
        return mutate(other, ConfigurationBase::copyAndSubtract);
    }

    public ConfigurationSet copyAndIntersectWith(ConfigurationSet other) {
        return mutate(other, ConfigurationBase::copyAndIntersect);
    }

    public ConfigurationSet filter(ConditionalConfigurationPredicate filter) {
        TypeConfiguration reflectionConfig = this.reflectionConfiguration.copyAndFilter(filter);
        TypeConfiguration jniConfig = this.jniConfiguration.copyAndFilter(filter);
        ResourceConfiguration resourceConfig = this.resourceConfiguration.copyAndFilter(filter);
        ProxyConfiguration proxyConfig = this.proxyConfiguration.copyAndFilter(filter);
        SerializationConfiguration serializationConfig = this.serializationConfiguration.copyAndFilter(filter);
        PredefinedClassesConfiguration predefinedClassesConfig = this.predefinedClassesConfiguration.copyAndFilter(filter);
        return new ConfigurationSet(reflectionConfig, jniConfig, resourceConfig, proxyConfig, serializationConfig, predefinedClassesConfig);
    }

    public TypeConfiguration getReflectionConfiguration() {
        return reflectionConfiguration;
    }

    public TypeConfiguration getJniConfiguration() {
        return jniConfiguration;
    }

    public ResourceConfiguration getResourceConfiguration() {
        return resourceConfiguration;
    }

    public ProxyConfiguration getProxyConfiguration() {
        return proxyConfiguration;
    }

    public SerializationConfiguration getSerializationConfiguration() {
        return serializationConfiguration;
    }

    public PredefinedClassesConfiguration getPredefinedClassesConfiguration() {
        return predefinedClassesConfiguration;
    }

    @SuppressWarnings("unchecked")
    public <T extends ConfigurationBase<T, ?>> T getConfiguration(ConfigurationFile configurationFile) {
        switch (configurationFile) {
            case DYNAMIC_PROXY:
                return (T) proxyConfiguration;
            case RESOURCES:
                return (T) resourceConfiguration;
            case JNI:
                return (T) jniConfiguration;
            case REFLECTION:
                return (T) reflectionConfiguration;
            case SERIALIZATION:
                return (T) serializationConfiguration;
            case PREDEFINED_CLASSES_NAME:
                return (T) predefinedClassesConfiguration;
            default:
                throw VMError.shouldNotReachHere("Unsupported configuration in configuration container: " + configurationFile);
        }
    }

    public static List<Path> writeConfiguration(Function<ConfigurationFile, Path> configFilePathResolver, Function<ConfigurationFile, JsonPrintable> configSupplier) throws IOException {
        List<Path> writtenFiles = new ArrayList<>();
        for (ConfigurationFile configFile : ConfigurationFile.agentGeneratedFiles()) {
            Path path = configFilePathResolver.apply(configFile);
            writtenFiles.add(path);
            JsonWriter writer = new JsonWriter(path);
            configSupplier.apply(configFile).printJson(writer);
            writer.newline();
            writer.close();
        }
        return writtenFiles;
    }

    public List<Path> writeConfiguration(Function<ConfigurationFile, Path> configFilePathResolver) throws IOException {
        return writeConfiguration(configFilePathResolver, this::getConfiguration);
    }

    public boolean isEmpty() {
        return reflectionConfiguration.isEmpty() && jniConfiguration.isEmpty() && resourceConfiguration.isEmpty() && proxyConfiguration.isEmpty() && serializationConfiguration.isEmpty() &&
                        predefinedClassesConfiguration.isEmpty();
    }
}
