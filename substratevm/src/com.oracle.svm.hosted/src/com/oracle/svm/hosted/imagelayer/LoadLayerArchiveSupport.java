/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import static com.oracle.svm.core.util.EnvVariableUtils.EnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.option.LayerVerifiedOption;
import com.oracle.svm.core.option.LayerVerifiedOption.Kind;
import com.oracle.svm.core.option.LayerVerifiedOption.Severity;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.core.util.EnvVariableUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.NativeImageClassLoaderSupport;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport.OptionLayerVerificationRequests;
import com.oracle.svm.hosted.imagelayer.LoadLayerArchiveSupport.ArgumentOrigin.NameValue;
import com.oracle.svm.hosted.util.DiffTool;
import com.oracle.svm.hosted.util.DiffTool.DiffResult;
import com.oracle.svm.util.LogUtils;

public class LoadLayerArchiveSupport extends LayerArchiveSupport {

    @SuppressWarnings("this-escape")
    public LoadLayerArchiveSupport(String layerName, Path layerFile, Path tempDir, ArchiveSupport archiveSupport, Platform current) {
        super(layerName, layerFile, tempDir.resolve(LAYER_TEMP_DIR_PREFIX + "load"), archiveSupport);
        this.archiveSupport.expandJarToDir(layerFile, layerDir);
        layerProperties.loadAndVerify(current);
        loadBuilderArgumentsFile();
    }

    private void loadBuilderArgumentsFile() {
        try (Stream<String> lines = Files.lines(getBuilderArgumentsFilePath())) {
            lines.forEach(builderArguments::add);
        } catch (IOException e) {
            throw UserError.abort("Unable to load builder arguments from file " + getBuilderArgumentsFilePath());
        }
    }

    private List<EnvironmentVariable> loadEnvironmentVariablesFile() {
        List<EnvironmentVariable> envVariables = new ArrayList<>();
        try (Stream<String> lines = Files.lines(getEnvVariablesFilePath())) {
            lines.map(EnvironmentVariable::of).forEach(envVariables::add);
            return envVariables;
        } catch (IOException e) {
            throw UserError.abort("Unable to load environment variables from file " + getEnvVariablesFilePath());
        }
    }

    @Override
    protected void validateLayerFile() {
        super.validateLayerFile();

        if (!Files.isReadable(layerFile)) {
            throw UserError.abort("The given layer file " + layerFile + " cannot be read.");
        }
    }

    public void verifyCompatibility(NativeImageClassLoaderSupport classLoaderSupport, Map<String, OptionLayerVerificationRequests> allRequests, boolean strict, boolean verbose) {
        Function<String, String> filterFunction = argument -> splitArgumentOrigin(argument).argument;
        boolean violationsFound = false;
        violationsFound |= verifyBuilderArgumentsCompatibility(builderArguments, classLoaderSupport.getHostedOptionParser().getArguments(), filterFunction, allRequests, strict, verbose, true);
        violationsFound |= verifyBuilderArgumentsCompatibility(builderArguments, classLoaderSupport.getHostedOptionParser().getArguments(), filterFunction, allRequests, strict, verbose, false);
        violationsFound |= verifyEnvironmentVariablesCompatibility(loadEnvironmentVariablesFile(), parseEnvVariables(), strict, verbose);
        if (violationsFound && verbose) {
            UserError.abort("Verbose LayerOptionVerification failed.");
        }
    }

    private static boolean verifyBuilderArgumentsCompatibility(List<String> previousArgs, List<String> currentArgs, Function<String, String> filterFunction,
                    Map<String, OptionLayerVerificationRequests> allRequests, boolean strict, boolean verbose, boolean positional) {

        List<String> left;
        List<String> right;
        if (positional) {
            left = previousArgs;
            right = currentArgs;
        } else {
            // Use sorted lists for position-independent diff results
            left = previousArgs.stream().sorted().toList();
            right = currentArgs.stream().sorted().toList();
        }

        List<String> filteredLeft = left.stream().map(filterFunction).toList();
        List<String> filteredRight = right.stream().map(filterFunction).toList();
        List<DiffResult<String>> diffResults = DiffTool.diffResults(filteredLeft, filteredRight);
        Map<DiffResult<String>, Severity> violations = new HashMap<>();
        for (var diffResult : diffResults) {
            DiffResult.Kind diffResultKind = diffResult.kind();
            Set<Kind> verificationKinds = switch (diffResultKind) {
                case Equal -> Set.of();
                case Removed -> Set.of(Kind.Removed, Kind.Changed);
                case Added -> Set.of(Kind.Added, Kind.Changed);
            };
            if (verificationKinds.isEmpty()) {
                continue;
            }

            ArgumentOrigin argumentOrigin = splitArgumentOrigin(diffResult.getEntry(left, right));
            NameValue argumentNameAndValue = argumentOrigin.nameValue();
            var perOptionVerifications = allRequests.get(argumentNameAndValue.name);
            if (perOptionVerifications == null) {
                continue;
            }

            List<LayerVerifiedOption> requests = perOptionVerifications.requests().stream()
                            .filter(request -> request.positional() == positional)
                            .collect(Collectors.toList());
            String argument = SubstrateOptionsParser.commandArgument(perOptionVerifications.option().getOptionKey(), argumentNameAndValue.value);
            List<LayerVerifiedOption> matchingAPIRequest = new ArrayList<>();
            requests.removeIf(request -> {
                if (request.apiOption().isEmpty()) {
                    // Keep all non-API requests
                    return false;
                }
                // Do record matching API requests ...
                if (request.apiOption().equals(argument)) {
                    matchingAPIRequest.add(request);
                }
                // ... but remove all API request entries
                return true;
            });
            if (!matchingAPIRequest.isEmpty()) {
                /*
                 * If we have a @LayerVerifiedOption annotation with a matching apiOption set, we
                 * ignore other @LayerVerifiedOption annotations that do not have apiOption set.
                 */
                requests = matchingAPIRequest;
            }

            requests.stream()
                            .filter(request -> verificationKinds.contains(request.kind()))
                            .forEach(request -> {

                                String message = switch (diffResultKind) {
                                    case Removed -> "Previous layer was";
                                    case Added -> "Current layer gets";
                                    case Equal -> throw VMError.shouldNotReachHere("diff for equal");
                                } + " built with option argument '" + argument + "' from " + OptionOrigin.from(argumentOrigin.origin) + ".";
                                if (!request.message().isEmpty()) {
                                    message += " " + request.message();
                                } else {
                                    /* fallback to generic verification message */
                                    message += " This is also required to be specified for the " + switch (diffResultKind) {
                                        case Removed -> "current layered image build";
                                        case Added -> "previous layer build";
                                        case Equal -> throw VMError.shouldNotReachHere("diff for equal");
                                    } + (positional ? " at the same position." : ".");
                                }
                                Severity severity = request.severity();
                                violations.put(diffResult, severity);
                                if (verbose) {
                                    LogUtils.info("Error: ", message);
                                } else {
                                    switch (severity) {
                                        case Warn -> LogUtils.warning(message);
                                        case Error -> {
                                            if (strict) {
                                                UserError.abort(message);
                                            } else {
                                                LogUtils.warning(message);
                                            }
                                        }
                                    }
                                }
                            });
        }

        boolean violationsFound = !violations.isEmpty();
        if (verbose && violationsFound) {
            System.out.printf("%nDiff view of %s list of previous vs. current layer build options%n%n", positional ? "sequential" : "sorted");
            for (var diffResult : diffResults) {
                String prefix = switch (violations.get(diffResult)) {
                    case null -> " ";
                    case Error -> "E";
                    case Warn -> "W";
                };
                System.out.println(prefix + diffResult.toString(left, right));
            }
            System.out.println();
        }
        return violationsFound;
    }

    /**
     * Verifies that the user-specified environment variables in the previous layered image build
     * are a subset of those used in the current layered image build. The environment variables
     * obtained via {@link System#getenv()} were previously filtered to include only the
     * user-specified variables for this verification. This filtering was performed in
     * {@link LayerArchiveSupport#parseEnvVariables()}, which removed system-dependent variables
     * defined in {@link EnvVariableUtils}. These excluded variables are required for image builds
     * but are not considered by this verification, since mismatches between these don't affect
     * layer compatibility.
     */
    private static boolean verifyEnvironmentVariablesCompatibility(List<EnvironmentVariable> previousEnvVars, List<EnvironmentVariable> currentEnvVars, boolean strict, boolean verbose) {
        Set<EnvironmentVariable> currentEnvVarsSet = new HashSet<>(currentEnvVars);
        boolean violationsFound = false;

        for (EnvironmentVariable previousEnvVar : previousEnvVars) {
            if (currentEnvVarsSet.contains(previousEnvVar)) {
                continue;
            }

            violationsFound = true;
            String message = "Current layered image build must set environment variable " + previousEnvVar + " as it was set in the previous layer build.";
            if (verbose) {
                LogUtils.info("Error: ", message);
            } else {
                if (strict) {
                    throw UserError.abort(message);
                } else {
                    LogUtils.warning(message);
                }
            }
        }

        return violationsFound;
    }

    record ArgumentOrigin(boolean booleanOption, String argument, String origin) {

        record NameValue(String name, String value) {
        }

        NameValue nameValue() {
            String name;
            String value;
            if (booleanOption) {
                // "-R:+BoolFooBar" -> BoolFooBar
                name = argument.substring(4);
                value = String.valueOf(argument.charAt(3));
            } else {
                // "-H:Foo=bar" -> Foo
                int valueSep = argument.indexOf('=');
                name = argument.substring(3, valueSep);
                value = argument.substring(valueSep + 1);
            }
            return new NameValue(name, value);
        }
    }

    private static ArgumentOrigin splitArgumentOrigin(String argumentWithOrigin) {
        int booleanPrefixPos = argumentWithOrigin.indexOf(':') + 1;
        char booleanPrefixChar = argumentWithOrigin.charAt(booleanPrefixPos);
        boolean booleanOption = booleanPrefixChar == '+' || booleanPrefixChar == '-';
        String argument = argumentWithOrigin;
        String origin = "";
        if (booleanOption) {
            // e.g. -R:+Bar@originInfoB -> -R:+Bar, originInfoB
            int originSeperatorPos = argumentWithOrigin.lastIndexOf('@');
            if (originSeperatorPos >= 0) {
                argument = argumentWithOrigin.substring(0, originSeperatorPos);
                origin = argumentWithOrigin.substring(originSeperatorPos + 1);
            }
        } else {
            // e.g. -H:Foo@originInfoA=value1 -> -H:Foo=value1, originInfoA
            int originSeparatorPos = argumentWithOrigin.indexOf('@');
            if (originSeparatorPos >= 0) {
                int keyValueSeparatorPos = argumentWithOrigin.indexOf('=');
                argument = argumentWithOrigin.substring(0, originSeparatorPos) + argumentWithOrigin.substring(keyValueSeparatorPos);
                origin = argumentWithOrigin.substring(originSeparatorPos + 1, keyValueSeparatorPos);
            }
        }
        return new ArgumentOrigin(booleanOption, argument, origin);
    }
}
