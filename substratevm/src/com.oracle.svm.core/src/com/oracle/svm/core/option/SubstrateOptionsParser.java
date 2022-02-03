/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.option;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.common.option.CommonOptionParser;
import com.oracle.svm.common.option.CommonOptionParser.BooleanOptionFormat;
import com.oracle.svm.common.option.CommonOptionParser.OptionParseResult;
import com.oracle.svm.common.option.UnsupportedOptionClassException;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.VMError;

/**
 * This class contains methods for parsing options and matching them against
 * {@link OptionDescriptor}s.
 */
public class SubstrateOptionsParser {

    public static final String HOSTED_OPTION_PREFIX = CommonOptionParser.HOSTED_OPTION_PREFIX;
    public static final String RUNTIME_OPTION_PREFIX = CommonOptionParser.RUNTIME_OPTION_PREFIX;

    static OptionParseResult parseOption(EconomicMap<String, OptionDescriptor> options, Predicate<OptionKey<?>> isHosted, String option, EconomicMap<OptionKey<?>, Object> valuesMap,
                    String optionPrefix, BooleanOptionFormat booleanOptionFormat) {
        try {
            return CommonOptionParser.parseOption(options, isHosted, option, valuesMap, optionPrefix, booleanOptionFormat);
        } catch (UnsupportedOptionClassException e) {
            VMError.shouldNotReachHere(e.getMessage());
            return null;
        }
    }

    /**
     * Parses a option at image build time. When the PrintFlags option is found prints all options
     * and interrupts compilation.
     *
     * @param optionPrefix prefix used before option name
     * @param options all possible options
     * @param valuesMap all current option values
     * @param booleanOptionFormat help expected for boolean options
     * @param errors a set that contains all error messages
     * @param arg the argument currently processed
     * @return true if {@code arg.startsWith(optionPrefix)}
     */
    public static boolean parseHostedOption(String optionPrefix, EconomicMap<String, OptionDescriptor> options, EconomicMap<OptionKey<?>, Object> valuesMap,
                    BooleanOptionFormat booleanOptionFormat, Set<String> errors, String arg, PrintStream out) {
        if (!arg.startsWith(optionPrefix)) {
            return false;
        }

        Predicate<OptionKey<?>> isHosted = optionKey -> optionKey instanceof HostedOptionKey;
        OptionParseResult optionParseResult = SubstrateOptionsParser.parseOption(options, isHosted, arg.substring(optionPrefix.length()), valuesMap,
                        optionPrefix, booleanOptionFormat);
        if (optionParseResult.printFlags() || optionParseResult.printFlagsWithExtraHelp()) {
            SubstrateOptionsParser.printFlags(d -> {
                OptionKey<?> key = d.getOptionKey();
                return optionParseResult.matchesFlags(d, key instanceof RuntimeOptionKey || key instanceof HostedOptionKey);
            }, options, optionPrefix, out, optionParseResult.printFlagsWithExtraHelp());
            throw new InterruptImageBuilding("");
        }
        if (!optionParseResult.isValid()) {
            errors.add(optionParseResult.getError());
            return true;
        }

        // Print a warning if the option is deprecated.
        OptionKey<?> option = optionParseResult.getOptionKey();
        OptionDescriptor descriptor = option.getDescriptor();
        if (descriptor != null && descriptor.isDeprecated()) {
            String message = "Warning: Option '" + descriptor.getName() + "' is deprecated and might be removed from future versions";
            String deprecationMessage = descriptor.getDeprecationMessage();
            if (deprecationMessage != null && !deprecationMessage.isEmpty()) {
                message += ": " + deprecationMessage;
            }
            System.err.println(message);
        }
        return true;
    }

    public static void collectOptions(ServiceLoader<OptionDescriptors> optionDescriptors, Consumer<OptionDescriptor> optionDescriptorConsumer) {
        CommonOptionParser.collectOptions(optionDescriptors, optionDescriptorConsumer);
    }

    public static void printOption(Consumer<String> println, String option, String description, int indentation, int optionWidth, int wrapWidth) {
        CommonOptionParser.printOption(println, option, description, indentation, optionWidth, wrapWidth);
    }

    /**
     * This method sorts the options before printing them.
     * <p>
     * Sorting the values of an {@link EconomicMap}, i.e., an {@link Iterable}, is not efficient
     * since all elements need to first be copied to a list. A stream could be used or the options
     * could be stored already sorted, however:
     * <ul>
     * <li>using a stream would make a lot of types reachable for even the simplest images</li>
     * <li>storing the options as sorted at run time, i.e., in a {@link java.util.TreeMap}, would be
     * less space efficient; since this method is shared between the hosted and run time worlds
     * options are stored in an {@link EconomicMap}</li>
     * </ul>
     * Since this method is not performance critical and it is only rarely called the tradeoff
     * between space and execution efficiency is acceptable.
     */
    static void printFlags(Predicate<OptionDescriptor> filter, EconomicMap<String, OptionDescriptor> options, String prefix, PrintStream out, boolean verbose) {
        CommonOptionParser.printFlags(filter, options, prefix, out, verbose);
    }

    public static long parseLong(String v) {
        return CommonOptionParser.parseLong(v);
    }

    public static double parseDouble(String v) {
        return CommonOptionParser.parseDouble(v);
    }

    /**
     * Returns a string to be used on command line to set the option to a desirable value. If the
     * option has one or more {@link APIOption} annotations, preference is given to a matching
     * {@link APIOption} syntax.
     *
     * @param option for which the command line argument is created
     * @return recommendation for setting a option value (e.g., for option 'Name' and value 'file'
     *         it returns "-H:Name=file")
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static String commandArgument(OptionKey<?> option, String value) {
        return commandArgument(option, value, null);
    }

    /**
     * Returns a string to be used on command line to set the option to a desirable value. If the
     * option has one or more {@link APIOption} annotations, preference is given to a matching
     * {@link APIOption} syntax.
     *
     * @param option for which the command line argument is created
     * @param apiOptionName name of the API option (in case there are multiple)
     * @return recommendation for setting a option value (e.g., for option 'Name' and value 'file'
     *         it returns "-H:Name=file")
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static String commandArgument(OptionKey<?> option, String value, String apiOptionName) {
        Field field;
        try {
            field = option.getDescriptor().getDeclaringClass().getDeclaredField(option.getDescriptor().getFieldName());
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }

        APIOption[] apiOptions = field.getAnnotationsByType(APIOption.class);

        for (APIOption apiOption : apiOptions) {
            String selected = selectVariant(apiOption, apiOptionName);
            assert selected == null || apiOption.deprecated().equals("") : "Using the deprecated option in a description: " + apiOption;
        }

        if (option.getDescriptor().getOptionValueType() == Boolean.class) {
            VMError.guarantee(value.equals("+") || value.equals("-"), "Boolean option value can be only + or -");
            for (APIOption apiOption : apiOptions) {
                String selected = selectVariant(apiOption, apiOptionName);
                if (selected != null) {
                    String apiValue = apiOption.kind() == APIOption.APIOptionKind.Negated ? "-" : "+";
                    if (apiValue.equals(value)) {
                        return APIOption.Utils.optionName(selected);
                    }
                }
            }
            return HOSTED_OPTION_PREFIX + value + option;
        } else {
            String apiOptionWithValue = null;
            for (APIOption apiOption : apiOptions) {
                String selected = selectVariant(apiOption, apiOptionName);
                if (selected != null) {
                    String optionName = APIOption.Utils.optionName(selected);
                    if (apiOption.fixedValue().length == 0) {
                        if (apiOptionWithValue == null) {
                            /* First APIOption that accepts value is selected as fallback */
                            apiOptionWithValue = optionName + apiOption.valueSeparator()[0] + value;
                        }
                    } else if (apiOption.fixedValue()[0].equals(value)) {
                        /* Return requested option expressed as fixed-value APIOption */
                        return optionName;
                    }
                }
            }
            if (apiOptionWithValue != null) {
                /* Returning APIOption that accepts value is better than raw option */
                return apiOptionWithValue;
            }
            assert apiOptionName == null : "invalid API option name " + apiOptionName;
            /* Return raw option if nothing else matches */
            return HOSTED_OPTION_PREFIX + option.getName() + "=" + value;
        }
    }

    private static String selectVariant(APIOption apiOption, String apiOptionName) {
        VMError.guarantee(apiOption.name().length > 0, "APIOption requires at least one name");
        if (apiOptionName == null) {
            return apiOption.name()[0];
        }
        if (Arrays.asList(apiOption.name()).contains(apiOptionName)) {
            return apiOptionName;
        }
        return null;
    }
}
