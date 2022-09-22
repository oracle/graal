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
package com.oracle.svm.configure;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.oracle.svm.configure.config.conditional.ConditionalConfigurationComputer;
import com.oracle.svm.configure.config.conditional.ConditionalConfigurationPredicate;
import com.oracle.svm.configure.config.conditional.MethodCallNode;
import com.oracle.svm.configure.config.conditional.MethodInfoRepository;
import com.oracle.svm.configure.config.conditional.PartialConfigurationWithOrigins;
import org.graalvm.nativeimage.ImageInfo;

import com.oracle.svm.configure.config.ConfigurationFileCollection;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.filters.ComplexFilter;
import com.oracle.svm.configure.filters.ConfigurationFilter;
import com.oracle.svm.configure.filters.FilterConfigurationParser;
import com.oracle.svm.configure.filters.ModuleFilterTools;
import com.oracle.svm.configure.filters.HierarchyFilterNode;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.configure.trace.TraceProcessor;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.util.VMError;

public class ConfigurationTool {

    private static final String HELP_TEXT = getResource("/Help.txt") + System.lineSeparator();

    private static class UsageException extends RuntimeException {
        static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                throw new UsageException("No arguments provided.");
            }
            Iterator<String> argsIter = Arrays.asList(args).iterator();
            String first = argsIter.next();

            if (first.equals("command-file")) {
                argsIter = handleCommandFile(argsIter);
                if (!argsIter.hasNext()) {
                    throw new UsageException("No arguments provided in the command file.");
                }
                first = argsIter.next();
            }

            switch (first) {
                case "generate":
                    generate(argsIter, false);
                    break;
                case "process-trace": // legacy
                    generate(argsIter, true);
                    break;
                case "generate-conditional":
                    createConditionalConfig(argsIter);
                    break;
                case "generate-filters":
                    generateFilterRules(argsIter);
                    break;
                case "help":
                case "--help":
                    System.out.println(HELP_TEXT);
                    break;
                default:
                    throw new UsageException("Unknown subcommand: " + first);
            }
        } catch (UsageException e) {
            System.err.println(e.getMessage() + System.lineSeparator() +
                            "Use 'native-image-configure help' for usage.");
            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static Path requirePath(String current, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new UsageException("Argument must be provided for: " + current);
        }
        return Paths.get(value);
    }

    private static URI requirePathUri(String current, String value) {
        return requirePath(current, value).toUri();
    }

    private static Iterator<String> handleCommandFile(Iterator<String> args) {
        if (!args.hasNext()) {
            throw new UsageException("Path to a command file must be provided.");
        }
        Path filePath = Paths.get(args.next());

        if (args.hasNext()) {
            throw new UsageException("Too many arguments to command-file passed. Expected a single argument: <path to a command file>.");
        }

        try {
            List<String> lines = Files.readAllLines(filePath);
            return lines.iterator();
        } catch (IOException e) {
            throw new UsageException("Failed to read the command file at " + filePath + ". Check if the file exists, you have the required permissions and that the file is actually a text file.");
        }
    }

    @SuppressWarnings("fallthrough")
    private static void generate(Iterator<String> argsIter, boolean acceptTraceFileArgs) throws IOException {
        List<URI> traceInputs = new ArrayList<>();
        boolean builtinCallerFilter = true;
        boolean builtinHeuristicFilter = true;
        List<URI> callerFilters = new ArrayList<>();

        ConfigurationFileCollection omittedCollection = new ConfigurationFileCollection();
        ConfigurationFileCollection inputCollection = new ConfigurationFileCollection();
        ConfigurationFileCollection outputCollection = new ConfigurationFileCollection();
        while (argsIter.hasNext()) {
            String[] parts = argsIter.next().split("=", 2);
            String current = parts[0];
            String value = (parts.length > 1) ? parts[1] : null;
            ConfigurationFileCollection collection = outputCollection;
            switch (current) {
                case "--input-dir":
                    inputCollection.addDirectory(requirePath(current, value));
                    break;
                case "--output-dir":
                    Path directory = getOrCreateDirectory(current, value);
                    outputCollection.addDirectory(directory);
                    break;

                case "--omit-from-input-dir":
                    omittedCollection.addDirectory(requirePath(current, value));
                    break;

                case "--reflect-input":
                    collection = inputCollection; // fall through
                case "--reflect-output":
                    collection.getReflectConfigPaths().add(requirePathUri(current, value));
                    break;

                case "--jni-input":
                    collection = inputCollection; // fall through
                case "--jni-output":
                    collection.getJniConfigPaths().add(requirePathUri(current, value));
                    break;

                case "--proxy-input":
                    collection = inputCollection; // fall through
                case "--proxy-output":
                    collection.getProxyConfigPaths().add(requirePathUri(current, value));
                    break;

                case "--resource-input":
                    collection = inputCollection; // fall through
                case "--resource-output":
                    collection.getResourceConfigPaths().add(requirePathUri(current, value));
                    break;

                case "--serialization-input":
                    collection = inputCollection; // fall through
                case "--serialization-output":
                    collection.getSerializationConfigPaths().add(requirePathUri(current, value));
                    break;

                case "--predefined-classes-input":
                    collection = inputCollection; // fall through
                case "--predefined-classes-output":
                    collection.getPredefinedClassesConfigPaths().add(requirePathUri(current, value));
                    break;

                case "--trace-input":
                    traceInputs.add(requirePathUri(current, value));
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
                    callerFilters.add(requirePathUri(current, value));
                    break;
                case "--":
                    if (acceptTraceFileArgs) {
                        argsIter.forEachRemaining(arg -> traceInputs.add(Paths.get(arg).toUri()));
                    } else {
                        throw new UsageException("Unknown argument: " + current);
                    }
                    break;
                default:
                    if (!acceptTraceFileArgs || current.startsWith("-")) {
                        throw new UsageException("Unknown argument: " + current);
                    }
                    traceInputs.add(Paths.get(current).toUri());
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
                    throw new UsageException("Cannot parse filter file " + uri + ": " + e);
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
            throw new UsageException("No inputs specified.");
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

    private static Path getOrCreateDirectory(String current, String value) throws IOException {
        Path directory = requirePath(current, value);
        if (!Files.exists(directory)) {
            Files.createDirectory(directory);
        } else if (!Files.isDirectory(directory)) {
            throw new NoSuchFileException(value);
        }
        return directory;
    }

    private static void createConditionalConfig(Iterator<String> argsIter) throws IOException {
        Set<URI> configInputPaths = new HashSet<>();
        Set<URI> configOutputPaths = new HashSet<>();
        URI userCodeFilterUri = null;
        Set<URI> classNameFiltersUri = new HashSet<>();
        while (argsIter.hasNext()) {
            String opt = argsIter.next();
            String[] parts = opt.split("=");
            if (parts.length != 2) {
                throw new UsageException("Invalid option " + opt);
            }
            String option = parts[0];
            String value = parts[1];
            switch (option) {
                case "--input-dir":
                    Path dir = requirePath(option, value);
                    if (!Files.isDirectory(dir)) {
                        throw new UsageException("Path is not a directory: " + dir);
                    }
                    Path configPath = dir.resolve(ConfigurationFile.PARTIAL_CONFIGURATION_WITH_ORIGINS);
                    if (!Files.isRegularFile(configPath)) {
                        throw new UsageException("Cannot find partial configuration file at " + configPath);
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
                        throw new UsageException("Cannot find user code filter file at " + userCodeFilter);
                    }
                    userCodeFilterUri = userCodeFilter.toUri();
                    break;
                case "--class-name-filter":
                    Path classNameFilter = requirePath(option, value);
                    if (!Files.isRegularFile(classNameFilter)) {
                        throw new UsageException("Cannot find user code filter file at " + classNameFilter);
                    }
                    classNameFiltersUri.add(classNameFilter.toUri());
                    break;
                default:
                    throw new UsageException("Unknown option: " + option);
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
            throw new UsageException("The following agent lock files were found in specified configuration directories, which means an agent is currently writing to them. " +
                            "The agent must finish execution before its configuration can be safely accessed. " +
                            "Unless a lock file is a leftover from an earlier process that terminated abruptly, it is unsafe to delete it." + System.lineSeparator() +
                            String.join(System.lineSeparator(), paths));
        }
    }

    private static void generateFilterRules(Iterator<String> argsIter) throws IOException {
        Path outputPath = null;
        boolean reduce = false;
        List<String> args = new ArrayList<>();
        while (argsIter.hasNext()) {
            String arg = argsIter.next();
            if (arg.startsWith("--reduce")) {
                String[] parts = arg.split("=", 2);
                reduce = (parts.length < 2) || Boolean.parseBoolean(parts[1]);
            } else {
                args.add(arg);
            }
        }
        HierarchyFilterNode rootNode = HierarchyFilterNode.createRoot();
        ComplexFilter filter = new ComplexFilter(rootNode);
        boolean filterModified = false;
        for (String arg : args) {
            String[] parts = arg.split("=", 2);
            String current = parts[0];
            String value = (parts.length > 1) ? parts[1] : null;
            switch (current) {
                case "--include-packages-from-modules":
                case "--exclude-packages-from-modules":
                case "--exclude-unexported-packages-from-modules":
                    if (!ImageInfo.inImageCode()) {
                        if (filterModified) {
                            throw new UsageException(current + " must be specified before other rule-creating arguments");
                        }
                        filterModified = true;
                        String[] moduleNames = (value != null) ? value.split(",") : new String[0];
                        HierarchyFilterNode.Inclusion exportedInclusion = current.startsWith("--include") ? ConfigurationFilter.Inclusion.Include : ConfigurationFilter.Inclusion.Exclude;
                        HierarchyFilterNode.Inclusion unexportedInclusion = exportedInclusion;
                        HierarchyFilterNode.Inclusion rootInclusion = exportedInclusion.invert();
                        if (current.equals("--exclude-unexported-packages-from-modules")) {
                            rootInclusion = ConfigurationFilter.Inclusion.Include;
                            exportedInclusion = ConfigurationFilter.Inclusion.Include;
                            unexportedInclusion = ConfigurationFilter.Inclusion.Exclude;
                        }
                        filter.setHierarchyFilterNode(ModuleFilterTools.generateFromModules(moduleNames, rootInclusion, exportedInclusion, unexportedInclusion, reduce));
                    } else {
                        throw new UsageException(current + " is currently not supported in the native-image build of this tool.");
                    }
                    break;

                case "--input-file":
                    filterModified = true;
                    new FilterConfigurationParser(filter).parseAndRegister(requirePathUri(current, value));
                    break;

                case "--output-file":
                    outputPath = requirePath(current, value);
                    break;

                case "--include-classes":
                    filterModified = true;
                    addSingleRule(filter.getHierarchyFilterNode(), current, value, ConfigurationFilter.Inclusion.Include);
                    break;

                case "--exclude-classes":
                    filterModified = true;
                    addSingleRule(filter.getHierarchyFilterNode(), current, value, ConfigurationFilter.Inclusion.Exclude);
                    break;

                default:
                    throw new UsageException("Unknown argument: " + current);
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

    private static void printFilterToStream(ConfigurationFilter filter, OutputStream targetStream) throws IOException {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(targetStream))) {
            filter.printJson(writer);
        }
    }

    private static void addSingleRule(HierarchyFilterNode root, String argName, String qualifiedPkg, HierarchyFilterNode.Inclusion inclusion) {
        if (qualifiedPkg == null || qualifiedPkg.isEmpty()) {
            throw new UsageException("Argument must be provided for: " + argName);
        }
        if (qualifiedPkg.indexOf('*') != -1 && !qualifiedPkg.endsWith(".**") && !qualifiedPkg.endsWith(".*")) {
            throw new UsageException("Rule may only contain '*' at the end, either as .* to include all classes in the package, " +
                            "or as .** to include all classes in the package and all of its subpackages");
        }
        root.addOrGetChildren(qualifiedPkg, inclusion);
    }

    private static String getResource(String resourceName) {
        try (InputStream input = ConfigurationTool.class.getResourceAsStream(resourceName)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            VMError.shouldNotReachHere(e);
        }
        return null;
    }
}
