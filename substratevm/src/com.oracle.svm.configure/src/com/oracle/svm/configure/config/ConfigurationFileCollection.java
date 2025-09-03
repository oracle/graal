/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.oracle.svm.configure.ConfigurationFile;
import com.oracle.svm.configure.ConfigurationParser;
import com.oracle.svm.configure.ConfigurationParserOption;

import jdk.graal.compiler.phases.common.LazyValue;

public class ConfigurationFileCollection {

    private final EnumSet<ConfigurationParserOption> parserOptions;
    private final EnumSet<ConfigurationParserOption> jniParserOptions;

    public ConfigurationFileCollection() {
        this.parserOptions = EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION, ConfigurationParserOption.TREAT_ALL_TYPE_REACHABLE_CONDITIONS_AS_TYPE_REACHED);
        this.jniParserOptions = parserOptions.clone();
        jniParserOptions.add(ConfigurationParserOption.JNI_PARSER);
    }

    public static final Function<IOException, Exception> FAIL_ON_EXCEPTION = e -> e;

    private final Set<URI> reachabilityMetadataPaths = new LinkedHashSet<>();
    private final Set<URI> jniConfigPaths = new LinkedHashSet<>();
    private final Set<URI> reflectConfigPaths = new LinkedHashSet<>();
    private final Set<URI> proxyConfigPaths = new LinkedHashSet<>();
    private final Set<URI> resourceConfigPaths = new LinkedHashSet<>();
    private final Set<URI> serializationConfigPaths = new LinkedHashSet<>();
    private final Set<URI> predefinedClassesConfigPaths = new LinkedHashSet<>();
    private Set<URI> lockFilePaths;

    public void addDirectory(Path path) {
        reachabilityMetadataPaths.add(path.resolve(ConfigurationFile.REACHABILITY_METADATA.getFileName()).toUri());
        jniConfigPaths.add(path.resolve(ConfigurationFile.JNI.getFileName()).toUri());
        reflectConfigPaths.add(path.resolve(ConfigurationFile.REFLECTION.getFileName()).toUri());
        proxyConfigPaths.add(path.resolve(ConfigurationFile.DYNAMIC_PROXY.getFileName()).toUri());
        resourceConfigPaths.add(path.resolve(ConfigurationFile.RESOURCES.getFileName()).toUri());
        serializationConfigPaths.add(path.resolve(ConfigurationFile.SERIALIZATION.getFileName()).toUri());
        predefinedClassesConfigPaths.add(path.resolve(ConfigurationFile.PREDEFINED_CLASSES_NAME.getFileName()).toUri());
        detectAgentLock(path.resolve(ConfigurationFile.LOCK_FILE_NAME), Files::exists, Path::toUri);
    }

    private <T> void detectAgentLock(T location, Predicate<T> exists, Function<T, URI> toUri) {
        if (exists.test(location)) {
            if (lockFilePaths == null) {
                lockFilePaths = new LinkedHashSet<>();
            }
            lockFilePaths.add(toUri.apply(location));
        }
    }

    public void addDirectory(Function<String, URI> fileResolver) {
        addFile(reachabilityMetadataPaths, fileResolver, ConfigurationFile.REACHABILITY_METADATA);
        addFile(jniConfigPaths, fileResolver, ConfigurationFile.JNI);
        addFile(reflectConfigPaths, fileResolver, ConfigurationFile.REFLECTION);
        addFile(proxyConfigPaths, fileResolver, ConfigurationFile.DYNAMIC_PROXY);
        addFile(resourceConfigPaths, fileResolver, ConfigurationFile.RESOURCES);
        addFile(serializationConfigPaths, fileResolver, ConfigurationFile.SERIALIZATION);
        addFile(predefinedClassesConfigPaths, fileResolver, ConfigurationFile.PREDEFINED_CLASSES_NAME);
        detectAgentLock(fileResolver.apply(ConfigurationFile.LOCK_FILE_NAME), Objects::nonNull, Function.identity());
    }

    private static void addFile(Set<URI> metadataPaths, Function<String, URI> fileResolver, ConfigurationFile configurationFile) {
        URI uri = fileResolver.apply(configurationFile.getFileName());
        if (uri != null) {
            metadataPaths.add(uri);
        }
    }

    public Set<URI> getDetectedAgentLockPaths() {
        return (lockFilePaths != null) ? lockFilePaths : Collections.emptySet();
    }

    public boolean isEmpty() {
        return reachabilityMetadataPaths.isEmpty() && jniConfigPaths.isEmpty() && reflectConfigPaths.isEmpty() && proxyConfigPaths.isEmpty() &&
                        resourceConfigPaths.isEmpty() && serializationConfigPaths.isEmpty() && predefinedClassesConfigPaths.isEmpty();
    }

    public Set<Path> getPaths(ConfigurationFile configurationFile) {
        Set<URI> uris;
        switch (configurationFile) {
            case REACHABILITY_METADATA -> uris = getReachabilityMetadataPaths();
            case DYNAMIC_PROXY -> uris = getProxyConfigPaths();
            case RESOURCES -> uris = getResourceConfigPaths();
            case JNI -> uris = getJniConfigPaths();
            case REFLECTION -> uris = getReflectConfigPaths();
            case SERIALIZATION -> uris = getSerializationConfigPaths();
            case PREDEFINED_CLASSES_NAME -> uris = getPredefinedClassesConfigPaths();
            default -> throw new IllegalArgumentException("Cannot get paths for configuration file " + configurationFile);
        }
        return uris.stream().map(Paths::get).collect(Collectors.toSet());
    }

    public Set<URI> getReachabilityMetadataPaths() {
        return reachabilityMetadataPaths;
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

    public TypeConfiguration loadReflectConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        TypeConfiguration reflectConfig = loadTypeConfig(ConfigurationFile.REFLECTION, reflectConfigPaths, exceptionHandler);
        TypeConfiguration jniConfig = loadTypeConfig(ConfigurationFile.JNI, jniConfigPaths, exceptionHandler);
        return reflectConfig.copyAndMerge(jniConfig);
    }

    public ProxyConfiguration loadProxyConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        ProxyConfiguration proxyConfiguration = new ProxyConfiguration();
        loadConfig(proxyConfigPaths, proxyConfiguration.createParser(false, parserOptions), exceptionHandler);
        return proxyConfiguration;
    }

    public PredefinedClassesConfiguration loadPredefinedClassesConfig(List<LazyValue<Path>> classDestinationDirs, Predicate<String> shouldExcludeClassesWithHash,
                    Function<IOException, Exception> exceptionHandler) throws Exception {
        PredefinedClassesConfiguration predefinedClassesConfiguration = new PredefinedClassesConfiguration(classDestinationDirs, shouldExcludeClassesWithHash);
        loadConfig(predefinedClassesConfigPaths, predefinedClassesConfiguration.createParser(false, parserOptions), exceptionHandler);
        return predefinedClassesConfiguration;
    }

    public ForeignConfiguration loadForeignConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        ForeignConfiguration foreignConfiguration = new ForeignConfiguration();
        loadConfig(reachabilityMetadataPaths, foreignConfiguration.createParser(true, parserOptions), exceptionHandler);
        return foreignConfiguration;
    }

    public ResourceConfiguration loadResourceConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        ResourceConfiguration resourceConfiguration = new ResourceConfiguration();
        loadConfig(reachabilityMetadataPaths, resourceConfiguration.createParser(true, parserOptions), exceptionHandler);
        loadConfig(resourceConfigPaths, resourceConfiguration.createParser(false, parserOptions), exceptionHandler);
        return resourceConfiguration;
    }

    public SerializationConfiguration loadSerializationConfig(Function<IOException, Exception> exceptionHandler) throws Exception {
        SerializationConfiguration serializationConfiguration = new SerializationConfiguration();
        loadConfig(reachabilityMetadataPaths, serializationConfiguration.createParser(true, parserOptions), exceptionHandler);
        loadConfig(serializationConfigPaths, serializationConfiguration.createParser(false, parserOptions), exceptionHandler);
        return serializationConfiguration;
    }

    public ConfigurationSet loadConfigurationSet(Function<IOException, Exception> exceptionHandler, List<LazyValue<Path>> predefinedConfigClassDestinationDirs,
                    Predicate<String> predefinedConfigClassWithHashExclusionPredicate) throws Exception {
        return new ConfigurationSet(loadReflectConfig(exceptionHandler), loadResourceConfig(exceptionHandler), loadProxyConfig(exceptionHandler),
                        loadSerializationConfig(exceptionHandler),
                        loadPredefinedClassesConfig(predefinedConfigClassDestinationDirs, predefinedConfigClassWithHashExclusionPredicate, exceptionHandler), loadForeignConfig(exceptionHandler));
    }

    private TypeConfiguration loadTypeConfig(ConfigurationFile configurationKind, Collection<URI> uris, Function<IOException, Exception> exceptionHandler) throws Exception {
        TypeConfiguration configuration = new TypeConfiguration();
        var specificParserOptions = configurationKind == ConfigurationFile.JNI ? jniParserOptions : parserOptions;
        loadConfig(reachabilityMetadataPaths, configuration.createParser(true, specificParserOptions), exceptionHandler);
        loadConfig(uris, configuration.createParser(false, specificParserOptions), exceptionHandler);
        return configuration;
    }

    private static void loadConfig(Collection<URI> configPaths, ConfigurationParser configurationParser, Function<IOException, Exception> exceptionHandler) throws Exception {
        for (URI uri : configPaths) {
            try {
                configurationParser.parseAndRegister(uri);
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
