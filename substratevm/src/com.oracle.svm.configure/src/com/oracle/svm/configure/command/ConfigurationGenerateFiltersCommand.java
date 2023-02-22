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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.svm.configure.ConfigurationUsageException;
import com.oracle.svm.configure.filters.ComplexFilter;
import com.oracle.svm.configure.filters.ConfigurationFilter;
import com.oracle.svm.configure.filters.FilterConfigurationParser;
import com.oracle.svm.configure.filters.HierarchyFilterNode;
import com.oracle.svm.configure.filters.ModuleFilterTools;
import com.oracle.svm.core.util.json.JsonWriter;

public final class ConfigurationGenerateFiltersCommand extends ConfigurationCommand {
    @Override
    public String getName() {
        return "generate-filters";
    }

    @Override
    public void apply(Iterator<String> argumentsIterator) throws IOException {
        Path outputPath = null;
        boolean reduce = false;
        List<String> args = new ArrayList<>();
        while (argumentsIterator.hasNext()) {
            String argument = argumentsIterator.next();
            if (argument.startsWith("--reduce")) {
                String[] optionValue = argument.split(OPTION_VALUE_SEP, OPTION_VALUE_LENGTH);
                reduce = (optionValue.length < OPTION_VALUE_LENGTH) || Boolean.parseBoolean(optionValue[VALUE_INDEX]);
            } else {
                args.add(argument);
            }
        }
        HierarchyFilterNode rootNode = HierarchyFilterNode.createRoot();
        ComplexFilter filter = new ComplexFilter(rootNode);
        boolean filterModified = false;
        for (String arg : args) {
            String[] optionValue = arg.split(OPTION_VALUE_SEP, OPTION_VALUE_LENGTH);
            String option = optionValue[OPTION_INDEX];
            String value = (optionValue.length > 1) ? optionValue[VALUE_INDEX] : null;
            switch (option) {
                case "--include-packages-from-modules":
                case "--exclude-packages-from-modules":
                case "--exclude-unexported-packages-from-modules":
                    if (!ImageInfo.inImageCode()) {
                        if (filterModified) {
                            throw new ConfigurationUsageException(option + " must be specified before other rule-creating arguments");
                        }
                        filterModified = true;
                        String[] moduleNames = (value != null) ? value.split(",") : new String[0];
                        HierarchyFilterNode.Inclusion exportedInclusion = option.startsWith("--include") ? ConfigurationFilter.Inclusion.Include : ConfigurationFilter.Inclusion.Exclude;
                        HierarchyFilterNode.Inclusion unexportedInclusion = exportedInclusion;
                        HierarchyFilterNode.Inclusion rootInclusion = exportedInclusion.invert();
                        if (option.equals("--exclude-unexported-packages-from-modules")) {
                            rootInclusion = ConfigurationFilter.Inclusion.Include;
                            exportedInclusion = ConfigurationFilter.Inclusion.Include;
                            unexportedInclusion = ConfigurationFilter.Inclusion.Exclude;
                        }
                        filter.setHierarchyFilterNode(ModuleFilterTools.generateFromModules(moduleNames, rootInclusion, exportedInclusion, unexportedInclusion, reduce));
                    } else {
                        throw new ConfigurationUsageException(option + " is currently not supported in the native-image build of this tool.");
                    }
                    break;

                case "--input-file":
                    filterModified = true;
                    new FilterConfigurationParser(filter).parseAndRegister(requirePathUri(option, value));
                    break;

                case "--output-file":
                    outputPath = requirePath(option, value);
                    break;

                case "--include-classes":
                    filterModified = true;
                    addSingleRule(filter.getHierarchyFilterNode(), option, value, ConfigurationFilter.Inclusion.Include);
                    break;

                case "--exclude-classes":
                    filterModified = true;
                    addSingleRule(filter.getHierarchyFilterNode(), option, value, ConfigurationFilter.Inclusion.Exclude);
                    break;

                default:
                    throw new ConfigurationUsageException("Unknown argument: " + option);
            }
        }

        filter.getHierarchyFilterNode().removeRedundantNodes();
        if (outputPath != null) {
            try (FileOutputStream os = new FileOutputStream(outputPath.toFile())) {
                printFilterToStream(filter, os);
            }
        } else {
            printFilterToStream(filter, System.out);
        }
    }

    @Override
    protected String getDescription0() {
        return "          builds a class filter according to the parameters.\n" +
                        "                          Filter rules are created according to the order of\n" +
                        "                          these parameters, and filter rules are applied in\n" +
                        "                          their order so that the last matching one \"wins\", so\n" +
                        "                          the order of the parameters is relevant. Filter files\n" +
                        "                          can be used with the caller-filter-file option of\n" +
                        "                          native-image-agent.\n" +
                        "    --include-classes=<class-pattern>\n" +
                        "                          adds a single rule to include a specific class, such\n" +
                        "                          as \"com.oracle.graal.Compiler\", or classes matching a\n" +
                        "                          specified pattern, for example \"com.oracle.graal.*\"\n" +
                        "                          for classes directly in package \"com.oracle.graal\",\n" +
                        "                          or \"com.oracle.graal.** (double asterisks) for all\n" +
                        "                          classes in \"com.oracle.graal\" AND all of its\n" +
                        "                          subpackages and their subpackages, recursively. The\n" +
                        "                          rule can potentially override rules from preceding\n" +
                        "                          parameters. Can be specified several times.\n" +
                        "    --exclude-classes=<class-pattern>\n" +
                        "                          adds a single rule to exclude classes matching the\n" +
                        "                          specified pattern, potentially overriding rules from\n" +
                        "                          preceding parameters. Can be specified several times.\n" +
                        "    --input-file=<path>\n" +
                        "                          reads a file with filter rules from the given path.\n" +
                        "                          Rules are processed in the order in which they occur\n" +
                        "                          in the file, so that subsequent rules potentially\n" +
                        "                          override preceding rules, including those from the\n" +
                        "                          --include and --exclude parameters. Can be specified\n" +
                        "                          several times.\n" +
                        "    --output-file=<path>\n" +
                        "                          specifies a file to which the output file is written.\n" +
                        "                          If this parameter is not provided, the filter is\n" +
                        "                          written to standard output.\n";
    }

    private static void printFilterToStream(ConfigurationFilter filter, OutputStream targetStream) throws IOException {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(targetStream))) {
            filter.printJson(writer);
        }
    }

    private static void addSingleRule(HierarchyFilterNode root, String argName, String qualifiedPkg, HierarchyFilterNode.Inclusion inclusion) {
        if (qualifiedPkg == null || qualifiedPkg.isEmpty()) {
            throw new ConfigurationUsageException("Argument must be provided for: " + argName);
        }
        if (qualifiedPkg.indexOf('*') != -1 && !qualifiedPkg.endsWith(".**") && !qualifiedPkg.endsWith(".*")) {
            throw new ConfigurationUsageException("Rule may only contain '*' at the end, either as .* to include all classes in the package, " +
                            "or as .** to include all classes in the package and all of its subpackages");
        }
        root.addOrGetChildren(qualifiedPkg, inclusion);
    }
}
