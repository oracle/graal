/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.common.option;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import jdk.graal.compiler.options.EnumMultiOptionKey;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionsParser;

import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.StringUtil;

public class CommonOptionParser {
    public static final String HOSTED_OPTION_PREFIX = "-H:";
    public static final String RUNTIME_OPTION_PREFIX = "-R:";

    public static final int PRINT_OPTION_INDENTATION = 2;
    public static final int PRINT_OPTION_WIDTH = 45;
    public static final int PRINT_OPTION_WRAP_WIDTH = 120;

    /**
     * The result of {@link CommonOptionParser#parseOption}.
     */
    public static final class OptionParseResult {
        private final EnumSet<OptionType> printFlags;
        private final Set<String> optionNameFilter;
        private final String error;
        private final OptionKey<?> optionKey;
        private final boolean optionUnrecognized;
        private static final String EXTRA_HELP_OPTIONS_WILDCARD = "*";

        OptionParseResult(EnumSet<OptionType> printFlags, String error, Set<String> optionNameFilter, OptionKey<?> optionKey, boolean optionUnrecognized) {
            this.printFlags = printFlags;
            this.error = error;
            this.optionNameFilter = optionNameFilter;
            this.optionKey = optionKey;
            this.optionUnrecognized = optionUnrecognized;
        }

        private OptionParseResult(EnumSet<OptionType> printFlags, String error, OptionKey<?> optionKey) {
            this(printFlags, error, new HashSet<>(), optionKey, false);
        }

        static OptionParseResult error(String message) {
            return new OptionParseResult(EnumSet.noneOf(OptionType.class), message, null);
        }

        static OptionParseResult optionUnrecognizedError(String message) {
            return new OptionParseResult(EnumSet.noneOf(OptionType.class), message, new HashSet<>(), null, true);
        }

        static OptionParseResult correct(OptionKey<?> optionKey) {
            return new OptionParseResult(EnumSet.noneOf(OptionType.class), null, optionKey);
        }

        static OptionParseResult printFlags(EnumSet<OptionType> selectedOptionTypes) {
            return new OptionParseResult(selectedOptionTypes, null, null);
        }

        static OptionParseResult printFlagsWithExtraHelp(Set<String> optionNameFilter) {
            Set<String> optionNames = optionNameFilter;
            if (optionNames.contains(EXTRA_HELP_OPTIONS_WILDCARD)) {
                optionNames = new HashSet<>();
                optionNames.add(EXTRA_HELP_OPTIONS_WILDCARD);
            }
            return new OptionParseResult(EnumSet.noneOf(OptionType.class), null, optionNames, null, false);
        }

        public boolean printFlags() {
            return !printFlags.isEmpty();
        }

        public boolean printFlagsWithExtraHelp() {
            return !optionNameFilter.isEmpty();
        }

        public boolean isValid() {
            boolean result = optionKey != null;
            assert result == (printFlags.isEmpty() && optionNameFilter.isEmpty() && error == null);
            return result;
        }

        public boolean optionUnrecognized() {
            return optionUnrecognized;
        }

        public String getError() {
            return error;
        }

        public OptionKey<?> getOptionKey() {
            return optionKey;
        }

        public boolean matchesFlags(OptionDescriptor d, boolean svmOption) {
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

    public static void collectOptions(ServiceLoader<OptionDescriptors> optionDescriptors, Consumer<OptionDescriptor> optionDescriptorConsumer) {
        for (OptionDescriptors optionDescriptor : optionDescriptors) {
            for (OptionDescriptor descriptor : optionDescriptor) {
                optionDescriptorConsumer.accept(descriptor);
            }
        }
    }

    public static OptionParseResult parseOption(EconomicMap<String, OptionDescriptor> options, Predicate<OptionKey<?>> isHosted, String option, EconomicMap<OptionKey<?>, Object> valuesMap,
                    String optionPrefix, BooleanOptionFormat booleanOptionFormat) throws UnsupportedOptionClassException {
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
            optionName = option.substring(1);
            if (booleanOptionFormat == BooleanOptionFormat.NAME_VALUE) {
                return OptionParseResult.error("Option " + LocatableOption.from(optionName) + " must use <name>=<value> format, not +/- prefix");
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

        LocatableOption current = LocatableOption.from(optionName);
        OptionDescriptor desc = options.get(current.name);
        if (desc == null && value != null) {
            if (eqIndex != -1) {
                optionName = option.substring(1, eqIndex);
                current = LocatableOption.from(optionName);
                desc = options.get(current.name);
            }
        }

        optionName = current.name;

        if (desc == null) {
            List<OptionDescriptor> matches = new ArrayList<>();
            OptionsParser.collectFuzzyMatches(options.getValues(), optionName, matches);
            StringBuilder msg = new StringBuilder("Could not find option ").append(current);
            if (!matches.isEmpty()) {
                msg.append(". Did you mean one of these:");
                for (OptionDescriptor match : matches) {
                    msg.append(' ').append(match.getName());
                }
            }
            msg.append(". Use ").append(optionPrefix).append(CommonOptions.PrintFlags.getName()).append("= to list all available options.");
            return OptionParseResult.optionUnrecognizedError(msg.toString());
        }

        OptionKey<?> optionKey = desc.getOptionKey();
        boolean hostedOption = isHosted.test(optionKey);
        Class<?> optionValueType = getMultiOptionValueElementType(optionKey);
        Class<?> optionType = hostedOption && optionValueType != null ? optionValueType : desc.getOptionValueType();

        if (value == null) {
            if (optionType == Boolean.class && booleanOptionFormat == BooleanOptionFormat.PLUS_MINUS) {
                return OptionParseResult.error("Boolean option " + current + " must have +/- prefix");
            }
            if (valueString == null) {
                return OptionParseResult.error("Missing value for option " + current);
            }
            try {
                value = parseValue(optionType, optionKey, current, valueString);
                if (value instanceof OptionParseResult) {
                    return (OptionParseResult) value;
                }
            } catch (NumberFormatException ex) {
                return OptionParseResult.error("Invalid value for option " + current + ": '" + valueString + "' is not a valid number");
            }
        } else {
            if (optionType != Boolean.class) {
                return OptionParseResult.error("Non-boolean option " + current + " can not use +/- prefix. Use '" + current.name + "=<value>' format");
            }
        }

        optionKey.update(valuesMap, hostedOption ? LocatableOption.value(value, current.origin) : value);

        if (CommonOptions.PrintFlags.getName().equals(optionName)) {
            String optionValue = (String) value;
            EnumSet<OptionType> selectedOptionTypes;
            if (optionValue.isEmpty()) {
                selectedOptionTypes = EnumSet.allOf(OptionType.class);
            } else {
                selectedOptionTypes = EnumSet.noneOf(OptionType.class);
                String enumString = null;
                try {
                    String[] enumStrings = StringUtil.split(optionValue, ",");

                    for (String string : enumStrings) {
                        enumString = string;
                        selectedOptionTypes.add(OptionType.valueOf(enumString));
                    }
                } catch (IllegalArgumentException e) {
                    String possibleValues = StringUtil.joinSingleQuoted(OptionType.values());
                    return OptionParseResult.error("Invalid value for option " + current + ". '" + enumString + "' is not one of " + possibleValues + ".");
                }
            }
            return OptionParseResult.printFlags(selectedOptionTypes);
        }

        if (CommonOptions.PrintFlagsWithExtraHelp.getName().equals(optionName)) {
            String optionValue = (String) value;
            String[] optionNames = StringUtil.split(optionValue, ",");
            HashSet<String> selectedOptionNames = new HashSet<>(Arrays.asList(optionNames));
            return OptionParseResult.printFlagsWithExtraHelp(selectedOptionNames);
        }
        return OptionParseResult.correct(optionKey);
    }

    @SuppressWarnings("unchecked")
    static Object parseValue(Class<?> optionType, OptionKey<?> optionKey, LocatableOption option, String valueString) throws NumberFormatException, UnsupportedOptionClassException {
        Object value;
        if (optionType == Integer.class) {
            long longValue = parseLong(valueString);
            if ((int) longValue != longValue) {
                return OptionParseResult.error("Wrong value for option " + option + ": '" + valueString + "' is not a valid number");
            }
            value = (int) longValue;
        } else if (optionType == Long.class) {
            value = parseLong(valueString);
        } else if (optionType == Double.class) {
            value = parseDouble(valueString);
        } else if (optionType == Boolean.class) {
            if (valueString.equals("true")) {
                value = true;
            } else if (valueString.equals("false")) {
                value = false;
            } else {
                return OptionParseResult.error("Boolean option " + option + " must have value 'true' or 'false'");
            }
        } else if (optionType == String.class || optionType == Path.class) {
            Object defaultValue = optionKey.getDefaultValue();
            String delimiter = defaultValue instanceof MultiOptionValue ? ((MultiOptionValue<?>) defaultValue).getDelimiter() : "";
            boolean multipleValues = !delimiter.isEmpty() && valueString.contains(delimiter);
            String[] valueStrings = multipleValues ? StringUtil.split(valueString, delimiter) : null;
            if (optionType == String.class) {
                value = valueStrings != null ? valueStrings : valueString;
            } else {
                assert optionType == Path.class;
                if (valueStrings != null) {
                    Path[] valuePaths = new Path[valueStrings.length];
                    for (int i = 0; i < valueStrings.length; i++) {
                        valuePaths[i] = Path.of(valueStrings[i]);
                    }
                    value = valuePaths;
                } else {
                    value = Path.of(valueString);
                }
            }
        } else if (optionType.isEnum()) {
            value = Enum.valueOf(optionType.asSubclass(Enum.class), valueString);
        } else if (optionType == EconomicSet.class) {
            value = ((EnumMultiOptionKey<?>) optionKey).valueOf(valueString);
        } else {
            throw new UnsupportedOptionClassException(option + " uses unsupported option value class: " + ClassUtil.getUnqualifiedName(optionType));
        }
        return value;
    }

    private static Class<?> getMultiOptionValueElementType(OptionKey<?> optionKey) {
        Object defaultValue = optionKey.getDefaultValue();
        if (defaultValue instanceof MultiOptionValue) {
            return ((MultiOptionValue<?>) defaultValue).getValueType();
        }
        return null;
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
     * Parses the provided string to a double number, avoiding the JDK dependencies (which pull in a
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

    private static String spaces(int length) {
        return stringFilledWith(length, ' ');
    }

    private static String stringFilledWith(int length, char fillValue) {
        return new String(new char[length]).replace('\0', fillValue);
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

    private static void printOption(PrintStream out, String option, String description, boolean verbose, int wrap) {
        printOption(out::println, option, description, verbose, PRINT_OPTION_INDENTATION, PRINT_OPTION_WIDTH, wrap);
    }

    public static void printOption(Consumer<String> println, String option, String description, boolean verbose, int indentation, int optionWidth, int wrapWidth) {
        String indent = spaces(indentation);
        String desc = description != null ? description : "";
        desc = wrapWidth > 0 ? wrap(desc, wrapWidth) : desc;
        String nl = System.lineSeparator();
        String[] descLines = StringUtil.split(desc, nl);
        String descLinePrefix;
        if (verbose) {
            descLinePrefix = "";
            String border = stringFilledWith(indentation + option.length() + indentation, '=');
            println.accept(border);
            println.accept(indent + option);
            println.accept(border);
            println.accept(descLines[0]);
        } else {
            descLinePrefix = indent + spaces(optionWidth);
            if (option.length() >= optionWidth && description != null) {
                println.accept(indent + option + nl + descLinePrefix + descLines[0]);
            } else {
                println.accept(indent + option + spaces(optionWidth - option.length()) + descLines[0]);
            }
        }
        for (int i = 1; i < descLines.length; i++) {
            println.accept(descLinePrefix + descLines[i]);
        }
        if (verbose) {
            println.accept("");
        }
    }

    public static void printFlags(Predicate<OptionDescriptor> filter, EconomicMap<String, OptionDescriptor> options, String prefix, PrintStream out, boolean verbose) {
        List<OptionDescriptor> sortedDescriptors = new ArrayList<>();
        for (OptionDescriptor option : options.getValues()) {
            if (filter.test(option)) {
                sortedDescriptors.add(option);
            }
        }
        sortedDescriptors.sort(Comparator.comparing(OptionDescriptor::getName));

        for (OptionDescriptor descriptor : sortedDescriptors) {
            String helpMsg = descriptor.getHelp();
            // ensure helpMsg ends with dot
            int helpLen = helpMsg.length();
            if (helpLen > 0 && helpMsg.charAt(helpLen - 1) != '.') {
                helpMsg += '.';
            }
            // determine default value
            boolean stringifiedArrayValue = false;
            Object defaultValue = descriptor.getOptionKey().getDefaultValue();
            if (defaultValue != null && defaultValue.getClass().isArray()) {
                Object[] defaultValues = (Object[]) defaultValue;
                if (defaultValues.length == 1) {
                    defaultValue = defaultValues[0];
                } else {
                    List<String> stringList = new ArrayList<>();
                    String optionPrefix = prefix + descriptor.getName() + "=";
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
            // handle extra help
            String verboseHelp = "";
            if (!descriptor.getExtraHelp().isEmpty()) {
                if (verbose) {
                    verboseHelp = System.lineSeparator() + String.join(System.lineSeparator(), descriptor.getExtraHelp());
                } else {
                    verboseHelp = " [Extra help available]";
                }
            }
            int wrapWidth = verbose ? 0 : PRINT_OPTION_WRAP_WIDTH;
            if (descriptor.getOptionValueType() == Boolean.class) { // print boolean options
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
                printOption(out, prefix + "\u00b1" + descriptor.getName(), helpMsg + verboseHelp, verbose, wrapWidth);
            } else { // print all other options
                if (defaultValue == null) {
                    if (helpLen != 0) {
                        helpMsg += ' ';
                    }
                    helpMsg += "Default: None";
                }
                helpMsg += verboseHelp;
                if (stringifiedArrayValue || defaultValue == null) {
                    printOption(out, prefix + descriptor.getName() + "=...", helpMsg, verbose, wrapWidth);
                } else {
                    if (defaultValue instanceof String) {
                        defaultValue = '"' + String.valueOf(defaultValue) + '"';
                    }
                    printOption(out, prefix + descriptor.getName() + "=" + defaultValue, helpMsg, verbose, wrapWidth);
                }
            }
        }
    }
}
