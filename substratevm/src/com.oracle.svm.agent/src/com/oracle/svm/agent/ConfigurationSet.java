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
package com.oracle.svm.agent;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import com.oracle.svm.agent.restrict.ParserConfigurationAdapter;
import com.oracle.svm.configure.config.ProxyConfiguration;
import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.hosted.config.ConfigurationDirectories;
import com.oracle.svm.hosted.config.ConfigurationParser;
import com.oracle.svm.hosted.config.ProxyConfigurationParser;
import com.oracle.svm.hosted.config.ReflectionConfigurationParser;
import com.oracle.svm.hosted.config.ResourceConfigurationParser;

class ConfigurationSet {
    private final Set<URI> jniConfigPaths = new LinkedHashSet<>();
    private final Set<URI> reflectConfigPaths = new LinkedHashSet<>();
    private final Set<URI> proxyConfigPaths = new LinkedHashSet<>();
    private final Set<URI> resourceConfigPaths = new LinkedHashSet<>();

    public void addDirectory(Path path) {
        jniConfigPaths.add(path.resolve(ConfigurationDirectories.FileNames.JNI_NAME).toUri());
        reflectConfigPaths.add(path.resolve(ConfigurationDirectories.FileNames.REFLECTION_NAME).toUri());
        proxyConfigPaths.add(path.resolve(ConfigurationDirectories.FileNames.DYNAMIC_PROXY_NAME).toUri());
        resourceConfigPaths.add(path.resolve(ConfigurationDirectories.FileNames.RESOURCES_NAME).toUri());
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

    public TypeConfiguration loadJniConfig(boolean skipMissing) throws IOException {
        return loadTypeConfig(jniConfigPaths, skipMissing);
    }

    public TypeConfiguration loadReflectConfig(boolean skipMissing) throws IOException {
        return loadTypeConfig(reflectConfigPaths, skipMissing);
    }

    public ProxyConfiguration loadProxyConfig(boolean skipMissing) throws IOException {
        ProxyConfiguration proxyConfiguration = new ProxyConfiguration();
        loadConfig(proxyConfigPaths, new ProxyConfigurationParser(types -> proxyConfiguration.add(Arrays.asList(types))), skipMissing);
        return proxyConfiguration;
    }

    public ResourceConfiguration loadResourceConfig(boolean skipMissing) throws IOException {
        ResourceConfiguration resourceConfiguration = new ResourceConfiguration();
        loadConfig(resourceConfigPaths, new ResourceConfigurationParser(new ResourceConfiguration.ParserAdapter(resourceConfiguration)), skipMissing);
        return resourceConfiguration;
    }

    private static TypeConfiguration loadTypeConfig(Collection<URI> uris, boolean skipMissing) throws IOException {
        TypeConfiguration configuration = new TypeConfiguration();
        loadConfig(uris, new ReflectionConfigurationParser<>(new ParserConfigurationAdapter(configuration)), skipMissing);
        return configuration;
    }

    private static void loadConfig(Collection<URI> configPaths, ConfigurationParser reflectParser, boolean skipMissing) throws IOException {
        for (URI path : configPaths) {
            try (Reader reader = Files.newBufferedReader(Paths.get(path))) {
                reflectParser.parseAndRegister(reader);
            } catch (NoSuchFileException e) {
                if (!skipMissing) {
                    throw e;
                }
                System.err.println(Agent.MESSAGE_PREFIX + "warning: configuration " + path + " could not be found, skipping");
            }
        }
    }
}
