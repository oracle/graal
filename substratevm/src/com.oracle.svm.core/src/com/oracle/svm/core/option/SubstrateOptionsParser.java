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

// Checkstyle: allow reflection

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.CompilationWrapper;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.VMError;

/**
 * This class contains methods for parsing options and matching them against
 * {@link OptionDescriptor}s.
 */
public class SubstrateOptionsParser {

    public static final String HOSTED_OPTION_PREFIX = "-H:";
    public static final String RUNTIME_OPTION_PREFIX = "-R:";
    public static final int PRINT_OPTION_INDENTATION = 2;
    public static final int PRINT_OPTION_WIDTH = 45;
    public static final int PRINT_OPTION_WRAP_WIDTH = 120;

    /**
     * The result of {@link SubstrateOptionsParser#parseOption}.
     */
    static final class OptionParseResult {
        private final EnumSet<OptionType> printFlags;
        private final Set<String> optionNameFilter;
        private final String error;
        private static final String EXTRA_HELP_OPTIONS_WILDCARD = "*";

        private OptionParseResult(EnumSet<OptionType> printFlags, String error, Set<String> optionNameFilter) {
            this.printFlags = printFlags;
            this.error = error;
            this.optionNameFilter = optionNameFilter;
        }

        private OptionParseResult(EnumSet<OptionType> printFlags, String error) {
            this(printFlags, error, new HashSet<>());
        }

        static OptionParseResult error(String message) {
            return new OptionParseResult(EnumSet.noneOf(OptionType.class), message);
        }

        static OptionParseResult correct() {
            return new OptionParseResult(EnumSet.noneOf(OptionType.class), null);
        }

        static OptionParseResult printFlags(EnumSet<OptionType> selectedOptionTypes) {
            return new OptionParseResult(selectedOptionTypes, null);
        }

        static OptionParseResult printFlagsWithExtraHelp(Set<String> optionNameFilter) {
            Set<String> optionNames = optionNameFilter;
            if (optionNames.contains(EXTRA_HELP_OPTIONS_WILDCARD)) {
                optionNames = new HashSet<>();
                optionNames.add(EXTRA_HELP_OPTIONS_WILDCARD);
            }
            return new OptionParseResult(EnumSet.noneOf(OptionType.class), null, optionNames);
        }

        boolean printFlags() {
            return !printFlags.isEmpty();
        }

        boolean printFlagsWithExtraHelp() {
            return !optionNameFilter.isEmpty();
        }

        public boolean isValid() {
            return printFlags.isEmpty() && optionNameFilter.isEmpty() && error == null;
        }

        public String getError() {
            return error;
        }

        private boolean matchesFlags(OptionDescriptor d, boolean svmOption) {
            if (!printFlags.isEmpty()) {
                boolean showAll = printFlags.equals(EnumSet.allOf(OptionType.class));
                return showAll || svmOption && printFlags.contains(d.getOptionType());
            }
            if (!optionNameFilter.isEmpty()) {
                if (optionNameFilter.contains(EXTRA_HELP_OPTIONS_WILDCARD) && !d.getExtraHelp().isEmpty()) {
                    return true;
                }
                return optionNameFilter.contains(d.getName());
            }
            return false;
        }

        boolean matchesFlagsRuntime(OptionDescriptor d) {
            return matchesFlags(d, d.getOptionKey() instanceof RuntimeOptionKey);
        }

        boolean matchesFlagsHosted(OptionDescriptor d) {
            OptionKey<?> key = d.getOptionKey();
            return matchesFlags(d, key instanceof RuntimeOptionKey || key instanceof HostedOptionKey);
        }
    }

    /**
     * Constants denoting supported boolean option formats.
     */
    public enum BooleanOptionFormat {
        NAME_VALUE("<name>=<value>"),
        PLUS_MINUS("+/-<name>");

        BooleanOptionFormat(String help) {
            this.help = help;
        }

        private final String help;

        @Override
        public String toString() {
            return help;
        }
    }

    static OptionParseResult parseOption(SortedMap<String, OptionDescriptor> options, String option, EconomicMap<OptionKey<?>, Object> valuesMap, String optionPrefix,
                    BooleanOptionFormat booleanOptionFormat) {
        if (option.length() == 0) {
            return OptionParseResult.error("Option name must be specified");
        }

        String optionName;
        Object value = null;
        String valueString = null;

        char first = option.charAt(0);
        int eqIndex = option.indexOf('=');
        if (first == '+' || first == '-') {
            if (eqIndex != -1) {
                return OptionParseResult.error("Cannot mix +/- with <name>=<value> format: '" + optionPrefix + option + "'");
            }
            optionName = option.substring(1, option.length());
            if (booleanOptionFormat == BooleanOptionFormat.NAME_VALUE) {
                return OptionParseResult.error("Option '" + optionName + "' must use <name>=<value> format, not +/- prefix");
            }
            value = (first == '+');
        } else {
            if (eqIndex == -1) {
                optionName = option;
                valueString = null;
            } else {
                optionName = option.substring(0, eqIndex);
                valueString = option.substring(eqIndex + 1);
            }
        }

        OptionDescriptor desc = options.get(optionName);
        if (desc == null && value != null) {
            if (eqIndex != -1) {
                optionName = option.substring(1, eqIndex);
                desc = options.get(optionName);
            }
        }

        if (desc == null) {
            List<OptionDescriptor> matches = new ArrayList<>();
            OptionsParser.collectFuzzyMatches(options.values(), optionName, matches);
            StringBuilder msg = new StringBuilder("Could not find option '").append(optionName).append('\'');
            if (!matches.isEmpty()) {
                msg.append(". Did you mean one of these:");
                for (OptionDescriptor match : matches) {
                    msg.append(' ').append(match.getName());
                }
            }
            msg.append(". Use " + optionPrefix + SubstrateOptions.PrintFlags.getName() + "= to list all available options.");
            return OptionParseResult.error(msg.toString());
        }

        Class<?> optionType = desc.getOptionValueType();

        if (value == null) {
            if (optionType == Boolean.class && booleanOptionFormat == BooleanOptionFormat.PLUS_MINUS) {
                return OptionParseResult.error("Boolean option '" + optionName + "' must have +/- prefix");
            }
            if (valueString == null) {
                return OptionParseResult.error("Missing value for option '" + optionName + "'");
            }
            try {
                if (optionType.isArray()) {
                    OptionKey<?> optionKey = desc.getOptionKey();
                    Object addValue = parseValue(optionType.getComponentType(), optionName, valueString);
                    Object previous = valuesMap.get(optionKey);
                    if (previous == null) {
                        value = Array.newInstance(optionType.getComponentType(), 1);
                        ((Object[]) value)[0] = addValue;
                    } else {
                        Object[] previousValues = (Object[]) previous;
                        value = Arrays.copyOf(previousValues, previousValues.length + 1);
                        ((Object[]) value)[previousValues.length] = addValue;
                    }
                } else {
                    value = parseValue(optionType, optionName, valueString);
                }
            } catch (NumberFormatException ex) {
                return OptionParseResult.error("Invalid value for option '" + optionName + "': '" + valueString + "' is not a valid number");
            }

        } else {
            if (optionType != Boolean.class) {
                return OptionParseResult.error("Non-boolean option '" + optionName + "' can not use +/- prefix. Use '" + optionName + "=<value>' format");
            }
        }

        desc.getOptionKey().update(valuesMap, value);

        if (SubstrateOptions.PrintFlags.getName().equals(optionName)) {
            String optionValue = (String) value;
            EnumSet<OptionType> selectedOptionTypes;
            if (optionValue.isEmpty()) {
                selectedOptionTypes = EnumSet.allOf(OptionType.class);
            } else {
                selectedOptionTypes = EnumSet.noneOf(OptionType.class);
                String enumString = null;
                try {
                    String[] enumStrings = SubstrateUtil.split(optionValue, ",");
                    for (int i = 0; i < enumStrings.length; i++) {
                        enumString = enumStrings[i];
                        selectedOptionTypes.add(OptionType.valueOf(enumString));
                    }
                } catch (IllegalArgumentException e) {
                    StringBuilder sb = new StringBuilder();
                    boolean firstValue = true;
                    for (OptionType ot : OptionType.values()) {
                        if (firstValue) {
                            firstValue = false;
                        } else {
                            sb.append(", ");
                        }
                        sb.append(ot.name());
                    }
                    String possibleValues = sb.toString();
                    return OptionParseResult.error("Invalid value for option '" + optionName + ". " + enumString + "' is not one of: " + possibleValues);
                }
            }
            return OptionParseResult.printFlags(selectedOptionTypes);
        }
        if (SubstrateOptions.PrintFlagsWithExtraHelp.getName().equals(optionName)) {
            String optionValue = (String) value;
            String[] optionNames = SubstrateUtil.split(optionValue, ",");
            HashSet<String> selectedOptionNames = new HashSet<>(Arrays.asList(optionNames));
            return OptionParseResult.printFlagsWithExtraHelp(selectedOptionNames);
        }

        return OptionParseResult.correct();
    }

    static Object parseValue(Class<?> optionType, String optionName, String valueString) throws NumberFormatException {
        Object value;
        if (optionType == Integer.class) {
            long longValue = parseLong(valueString);
            if ((int) longValue != longValue) {
                return OptionParseResult.error("Wrong value for option '" + optionName + "': '" + valueString + "' is not a valid number");
            }
            value = (int) longValue;
        } else if (optionType == Long.class) {
            value = parseLong(valueString);
        } else if (optionType == String.class) {
            value = valueString;
        } else if (optionType == Double.class) {
            value = parseDouble(valueString);
        } else if (optionType == Boolean.class) {
            if (valueString.equals("true")) {
                value = true;
            } else if (valueString.equals("false")) {
                value = false;
            } else {
                return OptionParseResult.error("Boolean option '" + optionName + "' must have value 'true' or 'false'");
            }
        } else if (optionType == CompilationWrapper.ExceptionAction.class) {
            value = CompilationWrapper.ExceptionAction.valueOf(valueString);
        } else if (optionType == DebugOptions.PrintGraphTarget.class) {
            value = DebugOptions.PrintGraphTarget.valueOf(valueString);
        } else {
            throw VMError.shouldNotReachHere("Unsupported option value class: " + optionType.getSimpleName());
        }
        return value;
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
    public static boolean parseHostedOption(String optionPrefix, SortedMap<String, OptionDescriptor> options, EconomicMap<OptionKey<?>, Object> valuesMap, BooleanOptionFormat booleanOptionFormat,
                    Set<String> errors, String arg, PrintStream out) {
        if (!arg.startsWith(optionPrefix)) {
            return false;
        }

        OptionParseResult optionParseResult = SubstrateOptionsParser.parseOption(options, arg.substring(optionPrefix.length()), valuesMap, optionPrefix, booleanOptionFormat);
        if (optionParseResult.printFlags() || optionParseResult.printFlagsWithExtraHelp()) {
            SubstrateOptionsParser.printFlags(optionParseResult::matchesFlagsHosted, options, optionPrefix, out, optionParseResult.printFlagsWithExtraHelp());
            throw new InterruptImageBuilding();
        }
        if (!optionParseResult.isValid()) {
            errors.add(optionParseResult.getError());
        }
        return true;
    }

    private static String spaces(int length) {
        return new String(new char[length]).replace('\0', ' ');
    }

    private static String wrap(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        int cursor = 0;
        while (cursor + width < sb.length()) {
            int i = sb.lastIndexOf(" ", cursor + width);
            if (i == -1 || i < cursor) {
                i = sb.indexOf(" ", cursor + width);
            }
            if (i != -1) {
                sb.replace(i, i + 1, System.lineSeparator());
                cursor = i;
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private static void printOption(PrintStream out, String option, String description, int wrap) {
        printOption(out::println, option, description, PRINT_OPTION_INDENTATION, PRINT_OPTION_WIDTH, wrap);
    }

    public static void printOption(Consumer<String> println, String option, String description, int indentation, int optionWidth, int wrapWidth) {
        String indent = spaces(indentation);
        String desc = description != null ? description : "";
        desc = wrapWidth > 0 ? wrap(desc, wrapWidth) : desc;
        String nl = System.lineSeparator();
        String[] descLines = SubstrateUtil.split(desc, nl);
        if (option.length() >= optionWidth && description != null) {
            println.accept(indent + option + nl + indent + spaces(optionWidth) + descLines[0]);
        } else {
            println.accept(indent + option + spaces(optionWidth - option.length()) + descLines[0]);
        }
        for (int i = 1; i < descLines.length; i++) {
            println.accept(indent + spaces(optionWidth) + descLines[i]);
        }
    }

    static void printFlags(Predicate<OptionDescriptor> filter, SortedMap<String, OptionDescriptor> sortedOptions, String prefix, PrintStream out, boolean verbose) {
        for (Entry<String, OptionDescriptor> entry : sortedOptions.entrySet()) {
            OptionDescriptor descriptor = entry.getValue();
            if (!filter.test(descriptor)) {
                continue;
            }
            String helpMsg = verbose && !descriptor.getExtraHelp().isEmpty() ? "" : descriptor.getHelp();
            int helpLen = helpMsg.length();
            if (helpLen > 0 && helpMsg.charAt(helpLen - 1) != '.') {
                helpMsg += '.';
            }
            boolean stringifiedArrayValue = false;
            Object defaultValue = descriptor.getOptionKey().getDefaultValue();
            if (defaultValue != null && defaultValue.getClass().isArray()) {
                Object[] defaultValues = (Object[]) defaultValue;
                if (defaultValues.length == 1) {
                    defaultValue = defaultValues[0];
                } else {
                    List<String> stringList = new ArrayList<>();
                    String optionPrefix = prefix + entry.getKey() + "=";
                    for (Object rawValue : defaultValues) {
                        String value;
                        if (rawValue instanceof String) {
                            value = '"' + String.valueOf(rawValue) + '"';
                        } else {
                            value = String.valueOf(rawValue);
                        }
                        stringList.add(optionPrefix + value);
                    }
                    if (helpLen != 0) {
                        helpMsg += ' ';
                    }
                    helpMsg += "Default: ";
                    if (stringList.isEmpty()) {
                        helpMsg += "None";
                    } else {
                        helpMsg += String.join(" ", stringList);
                    }
                    stringifiedArrayValue = true;
                }
            }
            String verboseHelp = "";
            if (verbose) {
                verboseHelp = System.lineSeparator() + descriptor.getHelp() + System.lineSeparator() + String.join(System.lineSeparator(), descriptor.getExtraHelp());
            } else if (!descriptor.getExtraHelp().isEmpty()) {
                verboseHelp = " [Extra help available]";
            }
            int wrapWidth = verbose ? 0 : PRINT_OPTION_WRAP_WIDTH;
            if (descriptor.getOptionValueType() == Boolean.class) {
                Boolean val = (Boolean) defaultValue;
                if (helpLen != 0) {
                    helpMsg += ' ';
                }
                if (val != null) {
                    if (val) {
                        helpMsg += "Default: + (enabled).";
                    } else {
                        helpMsg += "Default: - (disabled).";
                    }
                }
                printOption(out, prefix + "\u00b1" + entry.getKey(), helpMsg + verboseHelp, wrapWidth);
            } else {
                if (defaultValue == null) {
                    if (helpLen != 0) {
                        helpMsg += ' ';
                    }
                    helpMsg += "Default: None";
                }
                helpMsg += verboseHelp;
                if (stringifiedArrayValue || defaultValue == null) {
                    printOption(out, prefix + entry.getKey() + "=...", helpMsg, wrapWidth);
                } else {
                    if (defaultValue instanceof String) {
                        defaultValue = '"' + String.valueOf(defaultValue) + '"';
                    }
                    printOption(out, prefix + entry.getKey() + "=" + defaultValue, helpMsg, wrapWidth);
                }
            }
        }
    }

    public static long parseLong(String v) {
        String valueString = v.trim().toLowerCase();
        long scale = 1;
        if (valueString.endsWith("k")) {
            scale = 1024L;
        } else if (valueString.endsWith("m")) {
            scale = 1024L * 1024L;
        } else if (valueString.endsWith("g")) {
            scale = 1024L * 1024L * 1024L;
        } else if (valueString.endsWith("t")) {
            scale = 1024L * 1024L * 1024L * 1024L;
        }

        if (scale != 1) {
            /* Remove trailing scale character. */
            valueString = valueString.substring(0, valueString.length() - 1);
        }

        return Long.parseLong(valueString) * scale;
    }

    /**
     * Parses the provide string to a double number, avoiding the JDK dependencies (which pull in a
     * lot of classes, including the regular expression library). Only simple numbers are supported,
     * without fancy exponent styles.
     */
    public static double parseDouble(String v) {
        String valueString = v.trim();

        int dotPos = valueString.indexOf('.');
        if (dotPos == -1) {
            return parseLong(valueString);
        }

        String beforeDot = valueString.substring(0, dotPos);
        String afterDot = valueString.substring(dotPos + 1);

        double sign = 1;
        if (beforeDot.startsWith("-")) {
            sign = -1;
            beforeDot = beforeDot.substring(1);
        } else if (beforeDot.startsWith("+")) {
            beforeDot = beforeDot.substring(1);
        }

        if (beforeDot.startsWith("-") || beforeDot.startsWith("+") || afterDot.startsWith("-") || afterDot.startsWith("+") ||
                        (beforeDot.length() == 0 && afterDot.length() == 0)) {
            throw new NumberFormatException(v);
        }

        double integral = 0;
        if (beforeDot.length() > 0) {
            integral = Long.parseLong(beforeDot);
        }

        double fraction = 0;
        if (afterDot.length() > 0) {
            fraction = Long.parseLong(afterDot) * Math.pow(10, -afterDot.length());
        }

        return sign * (integral + fraction);
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
            assert !apiOption.name().equals(apiOptionName) || apiOption.deprecated().equals("") : "Using the deprecated option in a description: " + apiOption;
        }

        if (option.getDescriptor().getOptionValueType() == Boolean.class) {
            VMError.guarantee(value.equals("+") || value.equals("-"), "Boolean option value can be only + or -");
            for (APIOption apiOption : apiOptions) {
                String apiValue = apiOption.kind() == APIOption.APIOptionKind.Negated ? "-" : "+";
                if (apiValue.equals(value)) {
                    return APIOption.Utils.name(apiOption);
                }
            }
            return HOSTED_OPTION_PREFIX + value + option;
        } else {
            for (APIOption apiOption : apiOptions) {
                String fixedValue = apiOption.fixedValue().length == 0 ? null : apiOption.fixedValue()[0];
                if (apiOption.name().equals(apiOptionName)) {
                    if (fixedValue == null) {
                        return APIOption.Utils.name(apiOption) + "=" + value;
                    } else if (value.equals(fixedValue)) {
                        return APIOption.Utils.name(apiOption);
                    }
                }
            }
            assert apiOptionName == null : "invalid API option name " + apiOptionName;
            return HOSTED_OPTION_PREFIX + option.getName() + "=" + value;
        }
    }
}
