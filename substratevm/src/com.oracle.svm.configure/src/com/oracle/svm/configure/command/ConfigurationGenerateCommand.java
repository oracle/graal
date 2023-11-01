/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.configure.command;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.oracle.svm.configure.ConfigurationUsageException;
import com.oracle.svm.configure.config.ConfigurationFileCollection;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.filters.ComplexFilter;
import com.oracle.svm.configure.filters.FilterConfigurationParser;
import com.oracle.svm.configure.filters.HierarchyFilterNode;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.configure.trace.TraceProcessor;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.util.json.JsonWriter;
import com.oracle.svm.util.LogUtils;

public class ConfigurationGenerateCommand extends ConfigurationCommand {
    @Override
    public String getName() {
        return "generate";
    }

    @Override
    public void apply(Iterator<String> argumentsIterator) throws IOException {
        generate(argumentsIterator, false);
    }

    @Override
    protected String getDescription0() {
        return """
                                          generates configuration file(s) from all inputs.
                            --trace-input=<path>
                                                  reads and processes a trace file at the given path.
                            --input-dir=<path>
                                                  reads a set of configuration files from the directory
                                                  at the given path. This is equivalent to all of:
                                                   --reflect-input=<path>/reflect-config.json
                                                   --jni-input=<path>/jni-config.json
                                                   --proxy-input=<path>/proxy-config.json
                                                   --resource-input=<path>/resource-config.json
                            --reflect-input=<path>
                                                  reads a reflection configuration file at <path>.
                            --jni-input=<path>
                                                  reads a JNI configuration file at <path>.
                            --proxy-input=<path>
                                                  reads a dynamic proxy configuration file at <path>.
                            --resource-input=<path>
                                                  reads a resource configuration file at <path>.
                            --serialization-input=<path>
                                                  reads a serialization configuration file at <path>.
                            --predefined-classes-input=<path>
                                                  reads a class predefinition config file at <path>.
                            --output-dir=<path>
                                                  writes a set of configuration files to the directory
                                                  at the given path. Existing files are replaced. This
                                                  option is equivalent to all of:
                                                   --reflect-output=<path>/reflect-config.json
                                                   --jni-output=<path>/jni-config.json
                                                   --proxy-output=<path>/proxy-config.json
                                                   --resource-output=<path>/resource-config.json
                            --reflect-output=<path>
                                                  write a reflection configuration file to <path>. This
                                                  file can be later provided to native-image with
                                                  -H:ReflectionConfigurationFiles=<path>.
                            --jni-output=<path>
                                                  write a JNI configuration file to <path>. This file
                                                  can be later provided to native-image with
                                                  -H:JNIConfigurationFiles=<path>.
                            --proxy-output=<path>
                                                  write a dynamic proxy configuration file to <path>.
                                                  This file can be later provided to native-image with
                                                  -H:DynamicProxyConfigurationFiles=<path>.
                            --resource-output=<path>
                                                  write a configuration file containing used resources
                                                  (getResource) to <path>. This file can later be
                                                  provided to native-image with
                                                  -H:ResourceConfigurationFiles=<path>.
                                                  The paths in the configuration file might need to be
                                                  adjusted for the build directories/classpath.
                            --serialization-input=<path>
                                                  writes a serialization configuration file to <path>.
                                                  This file can be later provided to native-image with
                                                  -H:SerializationConfigurationFiles=<path>.
                            --predefined-classes-input=<path>
                                                  writes a class predefinition config file to <path>.
                                                  This file can be later provided to native-image with
                                                  -H:PredefinedClassesConfigurationFiles=<path>.
                            --caller-filter-file=<path>
                                                  Provides a custom filter file for excluding usages
                                                  of JNI, reflection and resources based on the caller
                                                  class (read more below). This option can be provided
                                                  more than once, and the filter rules from the files
                                                  will be processed in the specified order.
                            --no-builtin-caller-filter
                                                  Usages of JNI, reflection and resources that are
                                                  internal to the JDK, to GraalVM or to the Java VM do
                                                  not need to be configured for native-image builds and
                                                  by default, are filtered (removed) from the generated
                                                  configurations. This option disables the built-in
                                                  filter for such usages based on the caller class.
                            --no-builtin-heuristic-filter
                                                  This option disables builtin heuristics that identify
                                                  further internal JNI, reflection and resource usages.
                        """.replaceAll("\n", System.lineSeparator());
    }

    @SuppressWarnings("fallthrough")
    protected static void generate(Iterator<String> argumentsIterator, boolean acceptTraceFileArgs) throws IOException {
        List<URI> traceInputs = new ArrayList<>();
        boolean builtinCallerFilter = true;
        boolean builtinHeuristicFilter = true;
        List<URI> callerFilters = new ArrayList<>();
        List<URI> accessFilters = new ArrayList<>();

        ConfigurationFileCollection omittedCollection = new ConfigurationFileCollection();
        ConfigurationFileCollection inputCollection = new ConfigurationFileCollection();
        ConfigurationFileCollection outputCollection = new ConfigurationFileCollection();
        while (argumentsIterator.hasNext()) {
            String[] optionValue = argumentsIterator.next().split(OPTION_VALUE_SEP, OPTION_VALUE_LENGTH);
            String option = optionValue[OPTION_INDEX];
            String value = (optionValue.length > 1) ? optionValue[VALUE_INDEX] : null;
            ConfigurationFileCollection collection = outputCollection;
            switch (option) {
                case "--input-dir":
                    inputCollection.addDirectory(requirePath(option, value));
                    break;
                case "--output-dir":
                    Path directory = getOrCreateDirectory(option, value);
                    outputCollection.addDirectory(directory);
                    break;

                case "--omit-from-input-dir":
                    omittedCollection.addDirectory(requirePath(option, value));
                    break;

                case "--reflect-input":
                    collection = inputCollection; // fall through
                case "--reflect-output":
                    collection.getReflectConfigPaths().add(requirePathUri(option, value));
                    break;

                case "--jni-input":
                    collection = inputCollection; // fall through
                case "--jni-output":
                    collection.getJniConfigPaths().add(requirePathUri(option, value));
                    break;

                case "--proxy-input":
                    collection = inputCollection; // fall through
                case "--proxy-output":
                    collection.getProxyConfigPaths().add(requirePathUri(option, value));
                    break;

                case "--resource-input":
                    collection = inputCollection; // fall through
                case "--resource-output":
                    collection.getResourceConfigPaths().add(requirePathUri(option, value));
                    break;

                case "--serialization-input":
                    collection = inputCollection; // fall through
                case "--serialization-output":
                    collection.getSerializationConfigPaths().add(requirePathUri(option, value));
                    break;

                case "--predefined-classes-input":
                    collection = inputCollection; // fall through
                case "--predefined-classes-output":
                    collection.getPredefinedClassesConfigPaths().add(requirePathUri(option, value));
                    break;

                case "--trace-input":
                    traceInputs.add(requirePathUri(option, value));
                    break;
                case "--no-filter": // legacy
                    builtinCallerFilter = false;
                    builtinHeuristicFilter = false;
                    break;
                case "--no-builtin-caller-filter":
                    builtinCallerFilter = false;
                    break;
                case "--no-builtin-heuristic-filter":
                    builtinHeuristicFilter = false;
                    break;
                case "--caller-filter-file":
                    callerFilters.add(requirePathUri(option, value));
                    break;
                case "--access-filter-file":
                    accessFilters.add(requirePathUri(option, value));
                    break;
                case "--":
                    if (acceptTraceFileArgs) {
                        argumentsIterator.forEachRemaining(arg -> traceInputs.add(Paths.get(arg).toUri()));
                    } else {
                        throw new ConfigurationUsageException("Unknown argument: " + option);
                    }
                    break;
                default:
                    if (!acceptTraceFileArgs || option.startsWith("-")) {
                        throw new ConfigurationUsageException("Unknown argument: " + option);
                    }
                    traceInputs.add(Paths.get(option).toUri());
                    break;
            }
        }
        failIfAgentLockFilesPresent(inputCollection, omittedCollection, outputCollection);

        HierarchyFilterNode callersFilterHierarchyFilterNode = null;
        ComplexFilter callersFilter = null;
        if (!builtinCallerFilter) {
            callersFilterHierarchyFilterNode = HierarchyFilterNode.createInclusiveRoot();
            callersFilter = new ComplexFilter(callersFilterHierarchyFilterNode);
        }
        if (!callerFilters.isEmpty()) {
            if (callersFilterHierarchyFilterNode == null) {
                callersFilterHierarchyFilterNode = AccessAdvisor.copyBuiltinCallerFilterTree();
                callersFilter = new ComplexFilter(callersFilterHierarchyFilterNode);
            }
            parseFilterFiles(callersFilter, callerFilters);
        }
        ComplexFilter accessFilter = null;
        if (!accessFilters.isEmpty()) {
            accessFilter = new ComplexFilter(AccessAdvisor.copyBuiltinAccessFilterTree());
            parseFilterFiles(accessFilter, accessFilters);
        }

        ConfigurationSet configurationSet;
        ConfigurationSet omittedConfigurationSet;

        try {
            omittedConfigurationSet = omittedCollection.loadConfigurationSet(ConfigurationFileCollection.FAIL_ON_EXCEPTION, null, null);
            List<Path> predefinedClassDestDirs = new ArrayList<>();
            for (URI pathUri : outputCollection.getPredefinedClassesConfigPaths()) {
                Path subdir = Files.createDirectories(Paths.get(pathUri).getParent().resolve(ConfigurationFile.PREDEFINED_CLASSES_AGENT_EXTRACTED_SUBDIR));
                subdir = Files.createDirectories(subdir);
                predefinedClassDestDirs.add(subdir);
            }
            Predicate<String> shouldExcludeClassesWithHash = omittedConfigurationSet.getPredefinedClassesConfiguration()::containsClassWithHash;
            configurationSet = inputCollection.loadConfigurationSet(ConfigurationFileCollection.FAIL_ON_EXCEPTION, predefinedClassDestDirs.toArray(new Path[0]), shouldExcludeClassesWithHash);
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        if (traceInputs.isEmpty() && inputCollection.isEmpty()) {
            throw new ConfigurationUsageException("No inputs specified.");
        }

        if (!traceInputs.isEmpty()) {
            AccessAdvisor advisor = new AccessAdvisor();
            advisor.setHeuristicsEnabled(builtinHeuristicFilter);
            if (callersFilter != null) {
                advisor.setCallerFilterTree(callersFilter);
            }
            if (accessFilter != null) {
                advisor.setAccessFilterTree(accessFilter);
            }

            TraceProcessor processor = new TraceProcessor(advisor);
            for (URI uri : traceInputs) {
                try (Reader reader = Files.newBufferedReader(Paths.get(uri))) {
                    processor.process(reader, configurationSet);
                }
            }
        }

        if (outputCollection.isEmpty()) {
            LogUtils.warning("No outputs specified, validating inputs only.");
        }
        for (URI uri : outputCollection.getReflectConfigPaths()) {
            try (JsonWriter writer = new JsonWriter(Paths.get(uri))) {
                configurationSet.getReflectionConfiguration().printJson(writer);
            }
        }
        for (URI uri : outputCollection.getJniConfigPaths()) {
            try (JsonWriter writer = new JsonWriter(Paths.get(uri))) {
                configurationSet.getJniConfiguration().printJson(writer);
            }
        }
        for (URI uri : outputCollection.getProxyConfigPaths()) {
            try (JsonWriter writer = new JsonWriter(Paths.get(uri))) {
                configurationSet.getProxyConfiguration().printJson(writer);
            }
        }
        for (URI uri : outputCollection.getResourceConfigPaths()) {
            try (JsonWriter writer = new JsonWriter(Paths.get(uri))) {
                configurationSet.getResourceConfiguration().printJson(writer);
            }
        }
        for (URI uri : outputCollection.getSerializationConfigPaths()) {
            try (JsonWriter writer = new JsonWriter(Paths.get(uri))) {
                configurationSet.getSerializationConfiguration().printJson(writer);
            }
        }
        for (URI uri : outputCollection.getPredefinedClassesConfigPaths()) {
            try (JsonWriter writer = new JsonWriter(Paths.get(uri))) {
                configurationSet.getPredefinedClassesConfiguration().printJson(writer);
            }
        }
    }

    private static void parseFilterFiles(ComplexFilter filter, List<URI> filterFiles) {
        for (URI uri : filterFiles) {
            try {
                new FilterConfigurationParser(filter).parseAndRegister(uri);
            } catch (Exception e) {
                throw new ConfigurationUsageException("Cannot parse filter file " + uri + ": " + e);
            }
        }
        filter.getHierarchyFilterNode().removeRedundantNodes();
    }

    @SuppressWarnings("fallthrough")
    private static void failIfAgentLockFilesPresent(ConfigurationFileCollection... collections) {
        Set<String> paths = null;
        for (ConfigurationFileCollection coll : collections) {
            for (URI path : coll.getDetectedAgentLockPaths()) {
                if (paths == null) {
                    paths = new HashSet<>();
                }
                paths.add(path.toString());
            }
        }
        if (paths != null && !paths.isEmpty()) {
            throw new ConfigurationUsageException("The following agent lock files were found in specified configuration directories, which means an agent is currently writing to them. " +
                            "The agent must finish execution before its configuration can be safely accessed. " +
                            "Unless a lock file is a leftover from an earlier process that terminated abruptly, it is unsafe to delete it." + System.lineSeparator() +
                            String.join(System.lineSeparator(), paths));
        }
    }
}
