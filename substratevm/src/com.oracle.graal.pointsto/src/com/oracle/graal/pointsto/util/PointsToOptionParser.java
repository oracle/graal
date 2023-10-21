/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.util;

import static com.oracle.svm.common.option.CommonOptionParser.BooleanOptionFormat.PLUS_MINUS;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

import com.oracle.svm.common.option.CommonOptionParser;
import com.oracle.svm.common.option.CommonOptionParser.BooleanOptionFormat;
import com.oracle.svm.common.option.CommonOptionParser.OptionParseResult;
import com.oracle.svm.common.option.UnsupportedOptionClassException;

public final class PointsToOptionParser {

    private static PointsToOptionParser instance = new PointsToOptionParser();

    private OptionValues optionValues = null;
    private EconomicMap<OptionKey<?>, Object> analysisValues = OptionValues.newOptionMap();
    private EconomicMap<String, OptionDescriptor> allAnalysisOptions = EconomicMap.create();

    public static PointsToOptionParser getInstance() {
        return instance;
    }

    private PointsToOptionParser() {
        ClassLoader appClassLoader = PointsToOptionParser.class.getClassLoader();
        CommonOptionParser.collectOptions(ServiceLoader.load(OptionDescriptors.class, appClassLoader), descriptor -> {
            String name = descriptor.getName();
            if (descriptor.getOptionKey() != null) {
                OptionDescriptor existing = allAnalysisOptions.put(name, descriptor);
                if (existing != null) {
                    AnalysisError.shouldNotReachHere("Option name \"" + name + "\" has multiple definitions: " + existing.getLocation() + " and " + descriptor.getLocation());
                }
            }
        });
    }

    public OptionValues parse(String[] args) {
        List<String> remainingArgs = new ArrayList<>();
        Set<String> errors = new HashSet<>();
        /*
         * The standalone pointsto analysis can be programmatically invoked multiple times. Each
         * invocation should have its own options which are parsed independently, but all
         * invocations can share with the same allAnalysisOptions.
         */
        analysisValues.clear();
        for (String arg : args) {
            boolean isAnalysisOption = false;
            isAnalysisOption |= parseOption(CommonOptionParser.HOSTED_OPTION_PREFIX, allAnalysisOptions, analysisValues, PLUS_MINUS, errors, arg, System.out);
            if (!isAnalysisOption) {
                remainingArgs.add(arg);
            }
        }
        optionValues = new OptionValues(analysisValues);
        if (!remainingArgs.isEmpty()) {
            AnalysisError.interruptAnalysis(String.format("Unknown options: %s", Arrays.toString(remainingArgs.toArray(new String[0]))));
        }
        if (!errors.isEmpty()) {
            StringBuilder errMsg = new StringBuilder("Option format error:\n");
            for (String err : errors) {
                errMsg.append(err).append("\n");
            }
            AnalysisError.interruptAnalysis(errMsg.toString());
        }
        return optionValues;
    }

    private static boolean parseOption(String optionPrefix, EconomicMap<String, OptionDescriptor> options, EconomicMap<OptionKey<?>, Object> valuesMap,
                    BooleanOptionFormat booleanOptionFormat, Set<String> errors, String arg, PrintStream out) {
        if (!arg.startsWith(optionPrefix)) {
            return false;
        }
        try {
            Predicate<OptionKey<?>> optionKeyPredicate = optionKey -> {
                Class<?> clazz = optionKey.getClass();
                // All classes from com.oracle.graal.pointsto.api.PointstoOptions are taken as
                // non-hosted options.
                if (clazz.getName().startsWith("com.oracle.graal.pointsto.api.PointstoOptions")) {
                    return false;
                }
                if (!clazz.equals(OptionKey.class) && OptionKey.class.isAssignableFrom(clazz)) {
                    return true;
                } else {
                    return false;
                }
            };
            OptionParseResult optionParseResult = CommonOptionParser.parseOption(options, optionKeyPredicate, arg.substring(optionPrefix.length()), valuesMap,
                            optionPrefix, booleanOptionFormat);
            if (optionParseResult.printFlags() || optionParseResult.printFlagsWithExtraHelp()) {
                CommonOptionParser.printFlags(d -> optionParseResult.matchesFlags(d, true), options, optionPrefix, out, optionParseResult.printFlagsWithExtraHelp());
                System.out.println("Abort analysis due to print flags are requested");
                System.exit(1);
            }
            if (!optionParseResult.isValid()) {
                errors.add(optionParseResult.getError());
            }
        } catch (UnsupportedOptionClassException e) {
            AnalysisError.shouldNotReachHere(e);
        }
        return true;
    }
}
