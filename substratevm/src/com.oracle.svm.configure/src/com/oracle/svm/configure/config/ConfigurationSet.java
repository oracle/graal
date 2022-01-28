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

import java.nio.file.Path;
import java.util.function.BiFunction;


import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.trace.TraceProcessor;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.util.VMError;

public class ConfigurationSet {
    private final TypeConfiguration reflectionConfiguration;
    private final TypeConfiguration jniConfiguration;
    private final ResourceConfiguration resourceConfiguration;
    private final ProxyConfiguration proxyConfiguration;
    private final SerializationConfiguration serializationConfiguration;
    private final PredefinedClassesConfiguration predefinedClassesConfiguration;

    private ConfigurationSet(TypeConfiguration reflectionConfiguration, TypeConfiguration jniConfiguration, ResourceConfiguration resourceConfiguration, ProxyConfiguration proxyConfiguration,
                    SerializationConfiguration serializationConfiguration, PredefinedClassesConfiguration predefinedClassesConfiguration) {
        this.reflectionConfiguration = reflectionConfiguration;
        this.jniConfiguration = jniConfiguration;
        this.resourceConfiguration = resourceConfiguration;
        this.proxyConfiguration = proxyConfiguration;
        this.serializationConfiguration = serializationConfiguration;
        this.predefinedClassesConfiguration = predefinedClassesConfiguration;
    }

    public ConfigurationSet(TraceProcessor processor) {
        this(processor.getReflectionConfiguration(), processor.getJniConfiguration(), processor.getResourceConfiguration(), processor.getProxyConfiguration(),
                        processor.getSerializationConfiguration(), processor.getPredefinedClassesConfiguration());
    }

    public ConfigurationSet(ConfigurationSet other) {
        this(new TypeConfiguration(other.reflectionConfiguration), new TypeConfiguration(other.jniConfiguration), new ResourceConfiguration(other.resourceConfiguration),
                        new ProxyConfiguration(other.proxyConfiguration), new SerializationConfiguration(other.serializationConfiguration),
                        new PredefinedClassesConfiguration(other.predefinedClassesConfiguration));
    }

    public ConfigurationSet() {
        this(new TypeConfiguration(), new TypeConfiguration(), new ResourceConfiguration(), new ProxyConfiguration(), new SerializationConfiguration(),
                        new PredefinedClassesConfiguration(new Path[0], hash -> false));
    }

    @SuppressWarnings("rawtypes")
    private ConfigurationSet mutate(ConfigurationSet other, BiFunction<ConfigurationBase, ConfigurationBase, ConfigurationBase> mutator) {
        TypeConfiguration reflectionConfiguration = (TypeConfiguration) mutator.apply(this.reflectionConfiguration, other.reflectionConfiguration);
        TypeConfiguration jniConfiguration = (TypeConfiguration) mutator.apply(this.jniConfiguration, other.jniConfiguration);
        ResourceConfiguration resourceConfiguration = (ResourceConfiguration) mutator.apply(this.resourceConfiguration, other.resourceConfiguration);
        ProxyConfiguration proxyConfiguration = (ProxyConfiguration) mutator.apply(this.proxyConfiguration, other.proxyConfiguration);
        SerializationConfiguration serializationConfiguration = (SerializationConfiguration) mutator.apply(this.serializationConfiguration, other.serializationConfiguration);
        PredefinedClassesConfiguration predefinedClassesConfiguration = (PredefinedClassesConfiguration) mutator.apply(this.predefinedClassesConfiguration, other.predefinedClassesConfiguration);
        return new ConfigurationSet(reflectionConfiguration, jniConfiguration, resourceConfiguration, proxyConfiguration, serializationConfiguration, predefinedClassesConfiguration);
    }

    @SuppressWarnings("unchecked")
    public ConfigurationSet merge(ConfigurationSet other) {
        return mutate(other, ConfigurationBase::copyAndMerge);
    }

    @SuppressWarnings("unchecked")
    public ConfigurationSet subtract(ConfigurationSet other) {
        return mutate(other, ConfigurationBase::copyAndSubtract);
    }

    @SuppressWarnings("unchecked")
    public ConfigurationSet intersectWith(ConfigurationSet other) {
        return mutate(other, ConfigurationBase::copyAndIntersect);
    }

    public ConfigurationSet filter(ConditionalConfigurationFilter filter) {
        TypeConfiguration reflectionConfiguration = this.reflectionConfiguration.copyAndFilter(filter);
        TypeConfiguration jniConfiguration = this.jniConfiguration.copyAndFilter(filter);
        ResourceConfiguration resourceConfiguration = this.resourceConfiguration.copyAndFilter(filter);
        ProxyConfiguration proxyConfiguration = this.proxyConfiguration.copyAndFilter(filter);
        SerializationConfiguration serializationConfiguration = this.serializationConfiguration.copyAndFilter(filter);
        PredefinedClassesConfiguration predefinedClassesConfiguration = this.predefinedClassesConfiguration.copyAndFilter(filter);
        return new ConfigurationSet(reflectionConfiguration, jniConfiguration, resourceConfiguration, proxyConfiguration, serializationConfiguration, predefinedClassesConfiguration);
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

    public ConfigurationBase<?, ?> getConfiguration(ConfigurationFile configurationFile) {
        switch (configurationFile) {
            case DYNAMIC_PROXY:
                return proxyConfiguration;
            case RESOURCES:
                return resourceConfiguration;
            case JNI:
                return jniConfiguration;
            case REFLECTION:
                return reflectionConfiguration;
            case SERIALIZATION:
                return serializationConfiguration;
            case PREDEFINED_CLASSES_NAME:
                return predefinedClassesConfiguration;
            default:
                throw VMError.shouldNotReachHere("Unsupported configuration in configuration container: " + configurationFile);
        }
    }

    public boolean isEmpty() {
        return reflectionConfiguration.isEmpty() && jniConfiguration.isEmpty() && resourceConfiguration.isEmpty() && proxyConfiguration.isEmpty() && serializationConfiguration.isEmpty() &&
                        predefinedClassesConfiguration.isEmpty();
    }

}
