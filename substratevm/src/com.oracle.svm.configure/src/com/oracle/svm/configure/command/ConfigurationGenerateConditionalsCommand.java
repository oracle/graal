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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.oracle.svm.configure.ConfigurationUsageException;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.conditional.ConditionalConfigurationComputer;
import com.oracle.svm.configure.config.conditional.ConditionalConfigurationPredicate;
import com.oracle.svm.configure.config.conditional.MethodCallNode;
import com.oracle.svm.configure.config.conditional.MethodInfoRepository;
import com.oracle.svm.configure.config.conditional.PartialConfigurationWithOrigins;
import com.oracle.svm.configure.filters.ComplexFilter;
import com.oracle.svm.configure.filters.FilterConfigurationParser;
import com.oracle.svm.configure.filters.HierarchyFilterNode;
import com.oracle.svm.configure.ConfigurationFile;

public final class ConfigurationGenerateConditionalsCommand extends ConfigurationCommand {
    @Override
    public String getName() {
        return "generate-conditional";
    }

    @Override
    public void apply(Iterator<String> argumentsIterator) throws IOException {
        Set<URI> configInputPaths = new HashSet<>();
        Set<URI> configOutputPaths = new HashSet<>();
        URI userCodeFilterUri = null;
        Set<URI> classNameFiltersUri = new HashSet<>();
        while (argumentsIterator.hasNext()) {
            String argument = argumentsIterator.next();
            String[] optionValue = argument.split(OPTION_VALUE_SEP);
            if (optionValue.length != OPTION_VALUE_LENGTH) {
                throw new ConfigurationUsageException(String.format(BAD_OPTION_FORMAT, argument));
            }
            String option = optionValue[OPTION_INDEX];
            String value = optionValue[VALUE_INDEX];
            switch (option) {
                case "--input-dir":
                    Path dir = requirePath(option, value);
                    if (!Files.isDirectory(dir)) {
                        throw new ConfigurationUsageException("Path is not a directory: " + dir);
                    }
                    Path configPath = dir.resolve(ConfigurationFile.PARTIAL_CONFIGURATION_WITH_ORIGINS);
                    if (!Files.isRegularFile(configPath)) {
                        throw new ConfigurationUsageException("Cannot find partial configuration file at " + configPath);
                    }
                    configInputPaths.add(configPath.toUri());
                    break;
                case "--output-dir":
                    Path outputDir = getOrCreateDirectory(option, value);
                    configOutputPaths.add(outputDir.toUri());
                    break;
                case "--user-code-filter":
                    Path userCodeFilter = requirePath(option, value);
                    if (!Files.isRegularFile(userCodeFilter)) {
                        throw new ConfigurationUsageException("Cannot find user code filter file at " + userCodeFilter);
                    }
                    userCodeFilterUri = userCodeFilter.toUri();
                    break;
                case "--class-name-filter":
                    Path classNameFilter = requirePath(option, value);
                    if (!Files.isRegularFile(classNameFilter)) {
                        throw new ConfigurationUsageException("Cannot find user code filter file at " + classNameFilter);
                    }
                    classNameFiltersUri.add(classNameFilter.toUri());
                    break;
                default:
                    throw new ConfigurationUsageException("Unknown option: " + option);
            }
        }

        ComplexFilter userCodeFilter = new ComplexFilter(HierarchyFilterNode.createRoot());
        new FilterConfigurationParser(userCodeFilter).parseAndRegister(userCodeFilterUri);

        ComplexFilter classNameFilter;
        if (classNameFiltersUri.isEmpty()) {
            classNameFilter = new ComplexFilter(HierarchyFilterNode.createInclusiveRoot());
        } else {
            classNameFilter = new ComplexFilter(HierarchyFilterNode.createRoot());
            for (URI classNameFilterUri : classNameFiltersUri) {
                new FilterConfigurationParser(classNameFilter).parseAndRegister(classNameFilterUri);
            }
        }

        MethodCallNode rootNode = MethodCallNode.createRoot();
        MethodInfoRepository registry = new MethodInfoRepository();
        for (URI inputUri : configInputPaths) {
            new PartialConfigurationWithOrigins(rootNode, registry).parseAndRegister(inputUri);
        }

        ConfigurationSet configSet = new ConditionalConfigurationComputer(rootNode, userCodeFilter, new ConditionalConfigurationPredicate(classNameFilter)).computeConditionalConfiguration();
        for (URI outputUri : configOutputPaths) {
            configSet.writeConfiguration(file -> Path.of(outputUri).resolve(file.getFileName()));
        }
    }

    @Override
    protected String getDescription0() {
        return """
                              generates conditional configuration from data
                                                  collected by previous agent runs.
                            --input-dir=<path>
                                                  reads configuration and metadata from a directory that
                                                  was previously populated by a run with the agent in
                                                  the partial configuration mode.
                            --output-dir=<path>
                                                  writes a set of conditional configuration files to
                                                  the given path.
                            --user-code-filter=<path>
                                                  specifies a filter file used to classify classes as
                                                  user application classes. Generated conditions will
                                                  only reference these classes.
                            --class-name-filter=<path>
                                                  specifies a filter file used to exclude classes from
                                                  the computed configuration. Both the configuration
                                                  and the conditions in the configuration will be
                                                  tested against this filter.
                        """.replaceAll("\n", System.lineSeparator());
    }
}
