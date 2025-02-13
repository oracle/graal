/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageClassLoaderSupport;
import com.oracle.svm.hosted.imagelayer.LayerOptionsSupport;

import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

public class PreserveSupport {

    public static final String PRESERVE_ALL = "all";
    public static final String PRESERVE_NONE = "none";

    public static final String PRESERVE_POSSIBLE_OPTIONS;
    static {
        String msg = "[" + PRESERVE_ALL + ", " + PRESERVE_NONE + ", ";
        msg += Stream.of(LayerOptionsSupport.ExtendedOption.MODULE_OPTION, LayerOptionsSupport.ExtendedOption.PACKAGE_OPTION, LayerOptionsSupport.ExtendedOption.PATH_OPTION)
                        .map(option -> option + "=" + "<" + option + ">")
                        .collect(Collectors.joining(", "));
        msg += "]";
        PRESERVE_POSSIBLE_OPTIONS = msg;
    }

    public static void parsePreserveOption(EconomicMap<OptionKey<?>, Object> hostedValues, NativeImageClassLoaderSupport classLoaderSupport) {
        AccumulatingLocatableMultiOptionValue.Strings preserve = SubstrateOptions.Preserve.getValue(new OptionValues(hostedValues));
        Stream<LocatableMultiOptionValue.ValueWithOrigin<String>> valuesWithOrigins = preserve.getValuesWithOrigins();
        valuesWithOrigins.forEach(valueWithOrigin -> {
            String optionValue = valueWithOrigin.value();
            OptionOrigin optionOrigin = valueWithOrigin.origin();
            if (!valueWithOrigin.origin().commandLineLike()) {
                throw UserError.abort("Using '%s' is only allowed on command line.",
                                SubstrateOptionsParser.commandArgument(SubstrateOptions.Preserve, optionValue), optionOrigin);
            }

            var options = Arrays.stream(valueWithOrigin.value().split(",")).toList();
            for (String option : options) {
                UserError.guarantee(!option.isEmpty(), "Option %s from %s cannot be passed an empty string. The possible options are: %s",
                                SubstrateOptionsParser.commandArgument(SubstrateOptions.Preserve, optionValue), optionOrigin, PRESERVE_POSSIBLE_OPTIONS);
                if (option.equals(PRESERVE_ALL)) {
                    classLoaderSupport.setPreserveAll();
                } else if (option.equals(PRESERVE_NONE)) {
                    classLoaderSupport.clearPreserveEntries();
                } else {
                    LayerOptionsSupport.ExtendedOption subOption = LayerOptionsSupport.ExtendedOption.parse(option);
                    switch (subOption.key()) {
                        case LayerOptionsSupport.ExtendedOption.MODULE_OPTION -> {
                            UserError.guarantee(subOption.value() != null, "Option %s in %s from %s requires a module name argument, e.g., %s=module-name.",
                                            subOption.key(), optionValue, optionOrigin, subOption.key());
                            classLoaderSupport.addJavaModuleToPreserve(subOption.value());
                        }
                        case LayerOptionsSupport.ExtendedOption.PACKAGE_OPTION -> {
                            UserError.guarantee(subOption.value() != null, "Option %s in %s from %s requires a package name argument, e.g., %s=package-name.",
                                            subOption.key(), optionValue, optionOrigin, subOption.key());
                            classLoaderSupport.addJavaPackageToPreserve(Objects.requireNonNull(LayerOptionsSupport.PackageOptionValue.from(subOption)));
                        }
                        case LayerOptionsSupport.ExtendedOption.PATH_OPTION -> {
                            UserError.guarantee(subOption.value() != null, "Option %s in %s from %s requires a class-path entry, e.g., %s=path/to/jar-file.",
                                            subOption.key(), optionValue, optionOrigin, subOption.key());
                            classLoaderSupport.addClassPathEntryToPreserve(subOption.value());
                        }

                        default -> throw UserError.abort("Unknown option %s used on %s. The possible options are: %s", subOption.key(), optionOrigin, PRESERVE_POSSIBLE_OPTIONS);
                    }
                }

            }
        });
    }
}
