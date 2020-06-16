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
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.filters.FilterConfigurationParser;
import com.oracle.svm.configure.filters.ModuleFilterTools;
import com.oracle.svm.configure.filters.RuleNode;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.configure.trace.TraceProcessor;
import com.oracle.svm.core.SubstrateUtil;
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
            switch (first) {
                case "generate":
                    generate(argsIter, false);
                    break;
                case "process-trace": // legacy
                    generate(argsIter, true);
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

    @SuppressWarnings("fallthrough")
    private static void generate(Iterator<String> argsIter, boolean acceptTraceFileArgs) throws IOException {
        List<URI> traceInputs = new ArrayList<>();
        boolean builtinCallerFilter = true;
        boolean builtinHeuristicFilter = true;
        List<Path> callerFilterFiles = new ArrayList<>();

        ConfigurationSet inputSet = new ConfigurationSet();
        ConfigurationSet outputSet = new ConfigurationSet();
        while (argsIter.hasNext()) {
            String[] parts = argsIter.next().split("=", 2);
            String current = parts[0];
            String value = (parts.length > 1) ? parts[1] : null;
            ConfigurationSet set = outputSet;
            switch (current) {
                case "--input-dir":
                    inputSet.addDirectory(requirePath(current, value));
                    break;
                case "--output-dir":
                    Path directory = requirePath(current, value);
                    if (!Files.exists(directory)) {
                        Files.createDirectory(directory);
                    } else if (!Files.isDirectory(directory)) {
                        throw new NoSuchFileException(value);
                    }
                    outputSet.addDirectory(directory);
                    break;

                case "--reflect-input":
                    set = inputSet; // fall through
                case "--reflect-output":
                    set.getReflectConfigPaths().add(requirePathUri(current, value));
                    break;

                case "--jni-input":
                    set = inputSet; // fall through
                case "--jni-output":
                    set.getJniConfigPaths().add(requirePathUri(current, value));
                    break;

                case "--proxy-input":
                    set = inputSet; // fall through
                case "--proxy-output":
                    set.getProxyConfigPaths().add(requirePathUri(current, value));
                    break;

                case "--resource-input":
                    set = inputSet; // fall through
                case "--resource-output":
                    set.getResourceConfigPaths().add(requirePathUri(current, value));
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
                    callerFilterFiles.add(requirePath(current, value));
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

        RuleNode callersFilter = null;
        if (!builtinCallerFilter) {
            callersFilter = RuleNode.createRoot();
            callersFilter.addOrGetChildren("**", RuleNode.Inclusion.Include);
        }
        if (!callerFilterFiles.isEmpty()) {
            if (callersFilter == null) {
                callersFilter = AccessAdvisor.copyBuiltinCallerFilterTree();
            }
            for (Path path : callerFilterFiles) {
                try {
                    FilterConfigurationParser parser = new FilterConfigurationParser(callersFilter);
                    parser.parseAndRegister(new FileReader(path.toFile()));
                } catch (Exception e) {
                    throw new UsageException("Cannot parse filter file " + path + ": " + e);
                }
            }
            callersFilter.removeRedundantNodes();
        }

        AccessAdvisor advisor = new AccessAdvisor();
        advisor.setHeuristicsEnabled(builtinHeuristicFilter);
        if (callersFilter != null) {
            advisor.setCallerFilterTree(callersFilter);
        }
        TraceProcessor p;
        try {
            p = new TraceProcessor(advisor, inputSet.loadJniConfig(ConfigurationSet.FAIL_ON_EXCEPTION), inputSet.loadReflectConfig(ConfigurationSet.FAIL_ON_EXCEPTION),
                            inputSet.loadProxyConfig(ConfigurationSet.FAIL_ON_EXCEPTION), inputSet.loadResourceConfig(ConfigurationSet.FAIL_ON_EXCEPTION));
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        if (traceInputs.isEmpty() && inputSet.isEmpty()) {
            throw new UsageException("No inputs specified.");
        }
        for (URI uri : traceInputs) {
            try (Reader reader = Files.newBufferedReader(Paths.get(uri))) {
                p.process(reader);
            }
        }

        if (outputSet.isEmpty()) {
            System.err.println("Warning: no outputs specified, validating inputs only.");
        }
        for (URI uri : outputSet.getReflectConfigPaths()) {
            try (JsonWriter writer = new JsonWriter(Paths.get(uri))) {
                p.getReflectionConfiguration().printJson(writer);
            }
        }
        for (URI uri : outputSet.getJniConfigPaths()) {
            try (JsonWriter writer = new JsonWriter(Paths.get(uri))) {
                p.getJniConfiguration().printJson(writer);
            }
        }
        for (URI uri : outputSet.getProxyConfigPaths()) {
            try (JsonWriter writer = new JsonWriter(Paths.get(uri))) {
                p.getProxyConfiguration().printJson(writer);
            }
        }
        for (URI uri : outputSet.getResourceConfigPaths()) {
            try (JsonWriter writer = new JsonWriter(Paths.get(uri))) {
                p.getResourceConfiguration().printJson(writer);
            }
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
        RuleNode rootNode = null;
        for (String arg : args) {
            String[] parts = arg.split("=", 2);
            String current = parts[0];
            String value = (parts.length > 1) ? parts[1] : null;
            switch (current) {
                case "--include-packages-from-modules":
                case "--exclude-packages-from-modules":
                    if (SubstrateUtil.HOSTED) {
                        if (rootNode != null) {
                            throw new UsageException(current + " must be specified before other rule-creating arguments");
                        }
                        RuleNode.Inclusion inclusion = current.startsWith("--include") ? RuleNode.Inclusion.Include : RuleNode.Inclusion.Exclude;
                        String[] moduleNames = (value != null) ? value.split(",") : new String[0];
                        rootNode = ModuleFilterTools.generateFromModules(moduleNames, inclusion, reduce);
                    } else {
                        throw new UsageException(current + " is currently not supported in the native-image build of this tool.");
                    }
                    break;

                case "--input-file":
                    rootNode = maybeCreateRootNode(rootNode);
                    try (FileReader reader = new FileReader(requirePath(current, value).toFile())) {
                        FilterConfigurationParser parser = new FilterConfigurationParser(rootNode);
                        parser.parseAndRegister(reader);
                    }
                    break;

                case "--output-file":
                    outputPath = requirePath(current, value);
                    break;

                case "--include-classes":
                    rootNode = addSingleRule(rootNode, current, value, RuleNode.Inclusion.Include);
                    break;

                case "--exclude-classes":
                    rootNode = addSingleRule(rootNode, current, value, RuleNode.Inclusion.Exclude);
                    break;

                default:
                    throw new UsageException("Unknown argument: " + current);
            }
        }
        rootNode = maybeCreateRootNode(rootNode); // in case of no inputs

        rootNode.removeRedundantNodes();
        if (outputPath != null) {
            try (FileOutputStream os = new FileOutputStream(outputPath.toFile())) {
                rootNode.printJsonTree(os);
            }
        } else {
            rootNode.printJsonTree(System.out);
        }
    }

    private static RuleNode maybeCreateRootNode(RuleNode rootNode) {
        return (rootNode != null) ? rootNode : RuleNode.createRoot();
    }

    private static RuleNode addSingleRule(RuleNode rootNode, String argName, String qualifiedPkg, RuleNode.Inclusion inclusion) {
        RuleNode root = maybeCreateRootNode(rootNode);
        if (qualifiedPkg == null || qualifiedPkg.isEmpty()) {
            throw new UsageException("Argument must be provided for: " + argName);
        }
        if (qualifiedPkg.indexOf('*') != -1 && !qualifiedPkg.endsWith(".**") && !qualifiedPkg.endsWith(".*")) {
            throw new UsageException("Rule may only contain '*' at the end, either as .* to include all classes in the package, " +
                            "or as .** to include all classes in the package and all of its subpackages");
        }
        root.addOrGetChildren(qualifiedPkg, inclusion);
        return root;
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
