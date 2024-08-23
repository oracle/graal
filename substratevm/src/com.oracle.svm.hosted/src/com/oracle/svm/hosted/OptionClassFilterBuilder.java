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
package com.oracle.svm.hosted;

import java.io.File;
import java.lang.module.ModuleFinder;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicSet;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.OptionClassFilter;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;

public class OptionClassFilterBuilder {
    private final String javaIdentifier = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private final Pattern validOptionValue = Pattern.compile(javaIdentifier + "(\\." + javaIdentifier + ")*");

    private final ImageClassLoader imageClassLoader;
    private final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> baseOption;
    private final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> pathsOption;
    private final Map<URI, Module> uriModuleMap;

    protected final Map<String, Set<OptionOrigin>> requireCompletePackageOrClass = new HashMap<>();
    private final Set<Module> requireCompleteModules = new HashSet<>();
    private boolean requireCompleteAll;

    public static OptionClassFilter createFilter(ImageClassLoader imageClassLoader, HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> baseOption,
                    HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> pathsOption) {
        OptionClassFilterBuilder builder = new OptionClassFilterBuilder(imageClassLoader, baseOption, pathsOption);

        baseOption.getValue().getValuesWithOrigins().forEach(o -> builder.extractBaseOptionValue(o.value(), o.origin()));
        pathsOption.getValue().getValuesWithOrigins().forEach(o -> builder.extractPathsOptionValue(o.value(), o.origin()));

        return builder.build();
    }

    public OptionClassFilterBuilder(ImageClassLoader imageClassLoader, HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> baseOption,
                    HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> pathsOption) {
        this.imageClassLoader = imageClassLoader;
        this.baseOption = baseOption;
        this.pathsOption = pathsOption;

        uriModuleMap = ModuleFinder.of(imageClassLoader.applicationModulePath().toArray(Path[]::new)).findAll().stream()
                        .filter(mRef -> mRef.location().isPresent())
                        .collect(Collectors.toUnmodifiableMap(mRef -> mRef.location().get(), mRef -> imageClassLoader.findModule(mRef.descriptor().name()).get()));
    }

    private void extractBaseOptionValue(String value, OptionOrigin origin) {
        URI container = origin.container();
        if (value.isEmpty()) {
            if (origin.commandLineLike()) {
                requireCompleteAll = true;
                return;
            }
            var originModule = uriModuleMap.get(container);
            if (originModule != null) {
                requireCompleteModules.add(originModule);
                return;
            }
            throw UserError.abort("Using '%s' without args only allowed on module-path. %s not part of module-path.",
                            SubstrateOptionsParser.commandArgument(baseOption, value), origin);
        } else {
            for (String entry : OptionUtils.resolveOptionValuesRedirection(baseOption, value, origin)) {
                if (validOptionValue.matcher(entry).matches()) {
                    if (!origin.commandLineLike() && !imageClassLoader.classes(container).contains(entry) && !imageClassLoader.packages(container).contains(entry)) {
                        throw UserError.abort("Option '%s' provided by %s contains '%s'. No such package or class name found in '%s'.",
                                        SubstrateOptionsParser.commandArgument(baseOption, value), origin, entry, container);
                    }
                    requireCompletePackageOrClass.computeIfAbsent(entry, unused -> new HashSet<>()).add(origin);
                } else {
                    throw UserError.abort("Entry '%s' in option '%s' provided by %s is neither a package nor a fully qualified classname.",
                                    entry, SubstrateOptionsParser.commandArgument(baseOption, value), origin);
                }
            }
        }
    }

    private void extractPathsOptionValue(String value, OptionOrigin origin) {
        if (!origin.commandLineLike()) {
            throw UserError.abort("Using '%s' is only allowed on command line.",
                            SubstrateOptionsParser.commandArgument(pathsOption, value), origin);
        }
        if (value.isEmpty()) {
            throw UserError.abort("Using '%s' requires directory or jar-file path arguments.",
                            SubstrateOptionsParser.commandArgument(pathsOption, value), origin);
        }
        for (String pathStr : SubstrateUtil.split(value, File.pathSeparator)) {
            Path path = Path.of(pathStr);
            EconomicSet<String> packages = imageClassLoader.packages(path.toAbsolutePath().normalize().toUri());
            if (imageClassLoader.noEntryForURI(packages)) {
                throw UserError.abort("Option '%s' provided by %s contains entry '%s'. No such entry exists on class or module-path.",
                                SubstrateOptionsParser.commandArgument(pathsOption, value), origin, pathStr);
            }
            for (String pkg : packages) {
                requireCompletePackageOrClass.put(pkg, Collections.singleton(origin));
            }
        }
    }

    private OptionClassFilter build() {
        return new OptionClassFilter(requireCompletePackageOrClass, requireCompleteModules, requireCompleteAll);
    }
}
