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
package com.oracle.svm.configure.config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.configure.PredefinedClassesConfigurationParser;
import com.oracle.svm.core.configure.ProxyConfigurationParser;
import com.oracle.svm.core.configure.ReflectionConfigurationParser;
import com.oracle.svm.core.configure.ResourceConfigurationParser;
import com.oracle.svm.core.configure.SerializationConfigurationParser;

public class ConfigurationSet {
    public static final Function<IOException, Exception> FAIL_ON_EXCEPTION = e -> e;

    private final Set<URI> jniConfigPaths = new LinkedHashSet<>();
    private final Set<URI> reflectConfigPaths = new LinkedHashSet<>();
    private final Set<URI> proxyConfigPaths = new LinkedHashSet<>();
    private final Set<URI> resourceConfigPaths = new LinkedHashSet<>();
    private final Set<URI> serializationConfigPaths = new LinkedHashSet<>();
    private final Set<URI> predefinedClassesConfigPaths = new LinkedHashSet<>();

    public void addDirectory(Path path) {
        jniConfigPaths.add(path.resolve(ConfigurationFile.JNI.getFileName()).toUri());
        reflectConfigPaths.add(path.resolve(ConfigurationFile.REFLECTION.getFileName()).toUri());
        proxyConfigPaths.add(path.resolve(ConfigurationFile.DYNAMIC_PROXY.getFileName()).toUri());
        resourceConfigPaths.add(path.resolve(ConfigurationFile.RESOURCES.getFileName()).toUri());
        serializationConfigPaths.add(path.resolve(ConfigurationFile.SERIALIZATION.getFileName()).toUri());
        predefinedClassesConfigPaths.add(path.resolve(ConfigurationFile.PREDEFINED_CLASSES_NAME.getFileName()).toUri());
    }

    public void addDirectory(Function<String, URI> fileResolver) {
        jniConfigPaths.add(fileResolver.apply(ConfigurationFile.JNI.getFileName()));
        reflectConfigPaths.add(fileResolver.apply(ConfigurationFile.REFLECTION.getFileName()));
        proxyConfigPaths.add(fileResolver.apply(ConfigurationFile.DYNAMIC_PROXY.getFileName()));
        resourceConfigPaths.add(fileResolver.apply(ConfigurationFile.RESOURCES.getFileName()));
        serializationConfigPaths.add(fileResolver.apply(ConfigurationFile.SERIALIZATION.getFileName()));
        predefinedClassesConfigPaths.add(fileResolver.apply(ConfigurationFile.PREDEFINED_CLASSES_NAME.getFileName()));
    }

    public boolean isEmpty() {
        return jniConfigPaths.isEmpty() && reflectConfigPaths.isEmpty() && proxyConfigPaths.isEmpty() &&
                        resourceConfigPaths.isEmpty() && serializationConfigPaths.isEmpty() && predefinedClassesConfigPaths.isEmpty();
    }

    public Set<URI> getJniConfigPaths() {
        return jniConfigPaths;
    }

    public Set<URI> getReflectConfigPaths() {
        return reflectConfigPaths;
    }

    public Set<URI> getProxyConfigPaths() {
        return proxyConfigPaths;
    }

    public Set<URI> getResourceConfigPaths() {
        return resourceConfigPaths;
    }

    public Set<URI> getSerializationConfigPaths() {
        return serializationConfigPaths;
    }

    public Set<URI> getPredefinedClassesConfigPaths() {
        return predefinedClassesConfigPaths;
    }

    public TypeConfiguration loadJniConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        return loadTypeConfig(jniConfigPaths, exceptionHandler);
    }

    public TypeConfiguration loadReflectConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        return loadTypeConfig(reflectConfigPaths, exceptionHandler);
    }

    public ProxyConfiguration loadProxyConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        ProxyConfiguration proxyConfiguration = new ProxyConfiguration();
        loadConfig(proxyConfigPaths, new ProxyConfigurationParser(types -> proxyConfiguration.add(types.getCondition(), new ArrayList<>(types.getElement())), true), exceptionHandler);
        return proxyConfiguration;
    }

    public PredefinedClassesConfiguration loadPredefinedClassesConfig(Path[] classDestinationDirs, Predicate<String> shouldExcludeClassesWithHash,
                    Function<IOException, Exception> exceptionHandler) throws Exception {
        PredefinedClassesConfiguration predefinedClassesConfiguration = new PredefinedClassesConfiguration(classDestinationDirs, shouldExcludeClassesWithHash);
        loadConfig(predefinedClassesConfigPaths, new PredefinedClassesConfigurationParser(predefinedClassesConfiguration::add, true), exceptionHandler);
        return predefinedClassesConfiguration;
    }

    public ResourceConfiguration loadResourceConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        ResourceConfiguration resourceConfiguration = new ResourceConfiguration();
        loadConfig(resourceConfigPaths, new ResourceConfigurationParser(new ResourceConfiguration.ParserAdapter(resourceConfiguration), true), exceptionHandler);
        return resourceConfiguration;
    }

    public SerializationConfiguration loadSerializationConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        SerializationConfiguration serializationConfiguration = new SerializationConfiguration();
        loadConfig(serializationConfigPaths, new SerializationConfigurationParser(serializationConfiguration, true), exceptionHandler);
        return serializationConfiguration;
    }

    private static TypeConfiguration loadTypeConfig(Collection<URI> uris, Function<IOException, Exception> exceptionHandler) throws Exception {
        TypeConfiguration configuration = new TypeConfiguration();
        loadConfig(uris, new ReflectionConfigurationParser<>(new ParserConfigurationAdapter(configuration)), exceptionHandler);
        return configuration;
    }

    private static void loadConfig(Collection<URI> configPaths, ConfigurationParser configurationParser, Function<IOException, Exception> exceptionHandler) throws Exception {
        for (URI uri : configPaths) {
            Path path = tryGetPath(uri);
            try {
                if (path != null) {
                    // The path can be needed to find extra files, such as predefined class data
                    configurationParser.parseAndRegister(path);
                } else {
                    try (Reader reader = new InputStreamReader(uri.toURL().openStream())) {
                        configurationParser.parseAndRegister(reader);
                    }
                }
            } catch (IOException ioe) {
                Exception e = ioe;
                if (exceptionHandler != null) {
                    e = exceptionHandler.apply(ioe);
                }
                if (e != null) {
                    throw e;
                }
            }
        }
    }

    private static Path tryGetPath(URI uri) {
        try {
            return Paths.get(uri);
        } catch (Exception e) {
            return null;
        }
    }
}
