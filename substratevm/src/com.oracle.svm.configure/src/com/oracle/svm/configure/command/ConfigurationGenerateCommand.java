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
        return "                  generates configuration file(s) from all inputs.\n" +
                        "    --trace-input=<path>\n" +
                        "                          reads and processes a trace file at the given path.\n" +
                        "    --input-dir=<path>\n" +
                        "                          reads a set of configuration files from the directory\n" +
                        "                          at the given path. This is equivalent to all of:\n" +
                        "                           --reflect-input=<path>/reflect-config.json\n" +
                        "                           --jni-input=<path>/jni-config.json\n" +
                        "                           --proxy-input=<path>/proxy-config.json\n" +
                        "                           --resource-input=<path>/resource-config.json\n" +
                        "    --reflect-input=<path>\n" +
                        "                          reads a reflection configuration file at <path>.\n" +
                        "    --jni-input=<path>\n" +
                        "                          reads a JNI configuration file at <path>.\n" +
                        "    --proxy-input=<path>\n" +
                        "                          reads a dynamic proxy configuration file at <path>.\n" +
                        "    --resource-input=<path>\n" +
                        "                          reads a resource configuration file at <path>.\n" +
                        "    --serialization-input=<path>\n" +
                        "                          reads a serialization configuration file at <path>.\n" +
                        "    --predefined-classes-input=<path>\n" +
                        "                          reads a class predefinition config file at <path>.\n" +
                        "    --output-dir=<path>\n" +
                        "                          writes a set of configuration files to the directory\n" +
                        "                          at the given path. Existing files are replaced. This\n" +
                        "                          option is equivalent to all of:\n" +
                        "                           --reflect-output=<path>/reflect-config.json\n" +
                        "                           --jni-output=<path>/jni-config.json\n" +
                        "                           --proxy-output=<path>/proxy-config.json\n" +
                        "                           --resource-output=<path>/resource-config.json\n" +
                        "    --reflect-output=<path>\n" +
                        "                          write a reflection configuration file to <path>. This\n" +
                        "                          file can be later provided to native-image with\n" +
                        "                          -H:ReflectionConfigurationFiles=<path>.\n" +
                        "    --jni-output=<path>\n" +
                        "                          write a JNI configuration file to <path>. This file\n" +
                        "                          can be later provided to native-image with\n" +
                        "                          -H:JNIConfigurationFiles=<path>.\n" +
                        "    --proxy-output=<path>\n" +
                        "                          write a dynamic proxy configuration file to <path>.\n" +
                        "                          This file can be later provided to native-image with\n" +
                        "                          -H:DynamicProxyConfigurationFiles=<path>.\n" +
                        "    --resource-output=<path>\n" +
                        "                          write a configuration file containing used resources\n" +
                        "                          (getResource) to <path>. This file can later be\n" +
                        "                          provided to native-image with\n" +
                        "                          -H:ResourceConfigurationFiles=<path>.\n" +
                        "                          The paths in the configuration file might need to be\n" +
                        "                          adjusted for the build directories/classpath.\n" +
                        "    --serialization-input=<path>\n" +
                        "                          writes a serialization configuration file to <path>.\n" +
                        "                          This file can be later provided to native-image with\n" +
                        "                          -H:SerializationConfigurationFiles=<path>.\n" +
                        "    --predefined-classes-input=<path>\n" +
                        "                          writes a class predefinition config file to <path>.\n" +
                        "                          This file can be later provided to native-image with\n" +
                        "                          -H:PredefinedClassesConfigurationFiles=<path>.\n" +
                        "    --caller-filter-file=<path>\n" +
                        "                          Provides a custom filter file for excluding usages\n" +
                        "                          of JNI, reflection and resources based on the caller\n" +
                        "                          class (read more below). This option can be provided\n" +
                        "                          more than once, and the filter rules from the files\n" +
                        "                          will be processed in the specified order.\n" +
                        "    --no-builtin-caller-filter\n" +
                        "                          Usages of JNI, reflection and resources that are\n" +
                        "                          internal to the JDK, to GraalVM or to the Java VM do\n" +
                        "                          not need to be configured for native-image builds and\n" +
                        "                          by default, are filtered (removed) from the generated\n" +
                        "                          configurations. This option disables the built-in\n" +
                        "                          filter for such usages based on the caller class.\n" +
                        "    --no-builtin-heuristic-filter\n" +
                        "                          This option disables builtin heuristics that identify\n" +
                        "                          further internal JNI, reflection and resource usages.\n";
    }

    @SuppressWarnings("fallthrough")
    protected static void generate(Iterator<String> argumentsIterator, boolean acceptTraceFileArgs) throws IOException {
        List<URI> traceInputs = new ArrayList<>();
        boolean builtinCallerFilter = true;
        boolean builtinHeuristicFilter = true;
        List<URI> callerFilters = new ArrayList<>();

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
            for (URI uri : callerFilters) {
                try {
                    FilterConfigurationParser parser = new FilterConfigurationParser(callersFilter);
                    parser.parseAndRegister(uri);
                } catch (Exception e) {
                    throw new ConfigurationUsageException("Cannot parse filter file " + uri + ": " + e);
                }
            }
            callersFilter.getHierarchyFilterNode().removeRedundantNodes();
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

            TraceProcessor processor = new TraceProcessor(advisor);
            for (URI uri : traceInputs) {
                try (Reader reader = Files.newBufferedReader(Paths.get(uri))) {
                    processor.process(reader, configurationSet);
                }
            }
        }

        if (outputCollection.isEmpty()) {
            System.err.println("Warning: no outputs specified, validating inputs only.");
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
