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
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.configure.ProxyConfigurationParser;
import com.oracle.svm.core.configure.ReflectionConfigurationParser;
import com.oracle.svm.core.configure.ResourceConfigurationParser;

public class ConfigurationSet {
    public static final Function<IOException, Exception> FAIL_ON_EXCEPTION = e -> e;

    private final Set<URI> jniConfigPaths = new LinkedHashSet<>();
    private final Set<URI> reflectConfigPaths = new LinkedHashSet<>();
    private final Set<URI> proxyConfigPaths = new LinkedHashSet<>();
    private final Set<URI> resourceConfigPaths = new LinkedHashSet<>();

    public void addDirectory(Path path) {
        jniConfigPaths.add(path.resolve(ConfigurationFiles.JNI_NAME).toUri());
        reflectConfigPaths.add(path.resolve(ConfigurationFiles.REFLECTION_NAME).toUri());
        proxyConfigPaths.add(path.resolve(ConfigurationFiles.DYNAMIC_PROXY_NAME).toUri());
        resourceConfigPaths.add(path.resolve(ConfigurationFiles.RESOURCES_NAME).toUri());
    }

    public boolean isEmpty() {
        return jniConfigPaths.isEmpty() && reflectConfigPaths.isEmpty() && proxyConfigPaths.isEmpty() && resourceConfigPaths.isEmpty();
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

    public TypeConfiguration loadJniConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        return loadTypeConfig(jniConfigPaths, exceptionHandler);
    }

    public TypeConfiguration loadReflectConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        return loadTypeConfig(reflectConfigPaths, exceptionHandler);
    }

    public ProxyConfiguration loadProxyConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        ProxyConfiguration proxyConfiguration = new ProxyConfiguration();
        loadConfig(proxyConfigPaths, new ProxyConfigurationParser(types -> proxyConfiguration.add(Arrays.asList(types))), exceptionHandler);
        return proxyConfiguration;
    }

    public ResourceConfiguration loadResourceConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        ResourceConfiguration resourceConfiguration = new ResourceConfiguration();
        loadConfig(resourceConfigPaths, new ResourceConfigurationParser(new ResourceConfiguration.ParserAdapter(resourceConfiguration)), exceptionHandler);
        return resourceConfiguration;
    }

    private static TypeConfiguration loadTypeConfig(Collection<URI> uris, Function<IOException, Exception> exceptionHandler) throws Exception {
        TypeConfiguration configuration = new TypeConfiguration();
        loadConfig(uris, new ReflectionConfigurationParser<>(new ParserConfigurationAdapter(configuration)), exceptionHandler);
        return configuration;
    }

    private static void loadConfig(Collection<URI> configPaths, ConfigurationParser reflectParser, Function<IOException, Exception> exceptionHandler) throws Exception {
        for (URI path : configPaths) {
            try (Reader reader = Files.newBufferedReader(Paths.get(path))) {
                reflectParser.parseAndRegister(reader);
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
}
