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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import com.oracle.svm.core.option.LayerVerification;
import com.oracle.svm.core.option.LayerVerification.Kind;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.NativeImageClassLoaderSupport;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport.OptionLayerVerificationRequests;
import com.oracle.svm.hosted.imagelayer.LoadLayerArchiveSupport.ArgumentOrigin.NameValue;
import com.oracle.svm.hosted.util.DiffTool;
import com.oracle.svm.util.LogUtils;

public class LoadLayerArchiveSupport extends LayerArchiveSupport {

    @SuppressWarnings("this-escape")
    public LoadLayerArchiveSupport(String layerName, Path layerFile, Path tempDir, ArchiveSupport archiveSupport) {
        super(layerName, layerFile, tempDir.resolve(LAYER_TEMP_DIR_PREFIX + "load"), archiveSupport);
        this.archiveSupport.expandJarToDir(layerFile, layerDir);
        layerProperties.loadAndVerify();
        loadBuilderArgumentsFile();
    }

    private void loadBuilderArgumentsFile() {
        try (Stream<String> lines = Files.lines(getBuilderArgumentsFilePath())) {
            lines.forEach(builderArguments::add);
        } catch (IOException e) {
            throw UserError.abort("Unable to load builder arguments from file " + getBuilderArgumentsFilePath());
        }
    }

    @Override
    protected void validateLayerFile() {
        super.validateLayerFile();

        if (!Files.isReadable(layerFile)) {
            throw UserError.abort("The given layer file " + layerFile + " cannot be read.");
        }
    }

    public void verifyCompatibility(NativeImageClassLoaderSupport classLoaderSupport, Map<String, OptionLayerVerificationRequests> allRequests, boolean strict) {
        Function<String, String> filterFunction = argument -> splitArgumentOrigin(argument).argument;
        verifyCompatibility(builderArguments, classLoaderSupport.getHostedOptionParser().getArguments(), filterFunction, allRequests, strict, true);
        verifyCompatibility(builderArguments, classLoaderSupport.getHostedOptionParser().getArguments(), filterFunction, allRequests, strict, false);
    }

    private static void verifyCompatibility(List<String> parentArgs, List<String> currentArgs, Function<String, String> filterFunction,
                    Map<String, OptionLayerVerificationRequests> allRequests, boolean strict, boolean positional) {

        List<String> left;
        List<String> right;
        if (positional) {
            left = parentArgs;
            right = currentArgs;
        } else {
            // Use sorted lists for position-independent diff results
            left = parentArgs.stream().sorted().toList();
            right = currentArgs.stream().sorted().toList();
        }

        List<String> filteredLeft = left.stream().map(filterFunction).toList();
        List<String> filteredRight = right.stream().map(filterFunction).toList();
        var diffResults = DiffTool.diffResults(filteredLeft, filteredRight);
        for (var diffResult : diffResults) {
            Set<Kind> verificationKinds = switch (diffResult.kind()) {
                case Removed -> Set.of(Kind.Removed, Kind.Changed);
                case Added -> Set.of(Kind.Added, Kind.Changed);
                default -> Set.of();
            };
            for (Kind verificationKind : verificationKinds) {
                ArgumentOrigin argumentOrigin = splitArgumentOrigin(diffResult.getEntry(left, right));
                NameValue argumentNameAndValue = argumentOrigin.nameValue();
                var perOptionVerifications = allRequests.get(argumentNameAndValue.name);
                if (perOptionVerifications == null) {
                    continue;
                }
                LayerVerification request = perOptionVerifications.requests().get(verificationKind);
                if (request == null || request.Positional() != positional) {
                    continue;
                }

                OptionOrigin origin = OptionOrigin.from(argumentOrigin.origin);
                String argument = SubstrateOptionsParser.commandArgument(perOptionVerifications.option().getOptionKey(), argumentNameAndValue.value);
                String message = switch (diffResult.kind()) {
                    case Removed -> "Parent layer was";
                    case Added -> "Current layer gets";
                    case Equal -> throw VMError.shouldNotReachHere("diff for equal");
                } + " built with option argument '" + argument + "' from " + origin + ".";
                String suffix;
                if (!request.Message().isEmpty()) {
                    suffix = request.Message();
                } else {
                    /* fallback to generic verification message */
                    suffix = "This is also required to be specified for the " + switch (diffResult.kind()) {
                        case Removed -> "current layered image build";
                        case Added -> "parent layer build";
                        case Equal -> throw VMError.shouldNotReachHere("diff for equal");
                    };
                }
                message += " " + suffix + (positional ? " at the same position." : ".");
                switch (request.Severity()) {
                    case Info -> LogUtils.info(message);
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
        }
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
