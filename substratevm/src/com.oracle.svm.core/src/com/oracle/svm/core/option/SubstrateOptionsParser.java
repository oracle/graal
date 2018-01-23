/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;

import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.VMError;

/**
 * This class contains methods for parsing options and matching them against
 * {@link OptionDescriptor}s.
 */
public class SubstrateOptionsParser {

    public static final String PRINT_FLAGS_OPTION_NAME = "PrintFlags";

    static final class OptionParseResult {
        private final boolean printFlags;
        private final String error;

        private OptionParseResult(boolean printFlags, String error) {
            this.printFlags = printFlags;
            this.error = error;
        }

        static OptionParseResult error(String message) {
            return new OptionParseResult(false, message);
        }

        static OptionParseResult correct() {
            return new OptionParseResult(false, null);
        }

        static OptionParseResult printFlags() {
            return new OptionParseResult(true, null);
        }

        boolean shouldPrintFlags() {
            return printFlags;
        }

        public boolean isValid() {
            return !shouldPrintFlags() && error == null;
        }

        public String getError() {
            return error;
        }
    }

    static OptionParseResult parseOption(SortedMap<String, OptionDescriptor> options, String option, EconomicMap<OptionKey<?>, Object> valuesMap) {
        if (option.length() == 0) {
            return OptionParseResult.error("Option name must be specified");
        }

        String optionName;
        Object value = null;
        String valueString = null;

        char first = option.charAt(0);
        if (first == '+' || first == '-') {
            optionName = option.substring(1);
            value = (first == '+');
        } else {
            int index = option.indexOf('=');
            if (index == -1) {
                optionName = option;
                valueString = null;
            } else {
                optionName = option.substring(0, index);
                valueString = option.substring(index + 1);
            }
        }

        if (optionName.equals(PRINT_FLAGS_OPTION_NAME)) {
            return OptionParseResult.printFlags();
        }

        OptionDescriptor desc = options.get(optionName);
        if (desc == null && value != null) {
            int index = option.indexOf('=');
            if (index != -1) {
                optionName = option.substring(1, index);
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
            return OptionParseResult.error(msg.toString());
        }

        Class<?> optionType = desc.getType();

        if (value == null) {
            if (valueString == null) {
                return OptionParseResult.error("Missing value for option '" + optionName + "'");
            }

            try {
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
                    value = Double.parseDouble(valueString);
                } else if (optionType == Boolean.class) {
                    if (valueString.equalsIgnoreCase("true")) {
                        value = true;
                    } else if (valueString.equalsIgnoreCase("false")) {
                        value = false;
                    } else {
                        return OptionParseResult.error("Wrong value for option '" + optionName + "': '" + valueString + "' is not a valid boolean value ('true' or 'false')");
                    }
                } else {
                    throw VMError.shouldNotReachHere("Unsupported option value class: " + optionType.getSimpleName());
                }
            } catch (NumberFormatException ex) {
                return OptionParseResult.error("Wrong value for option '" + optionName + "': '" + valueString + "' is not a valid number");
            }
        } else {
            if (optionType != Boolean.class) {
                return OptionParseResult.error("Non-boolean option '" + optionName + "' can not use +/- prefix. Use '" + optionName + "=<value>' format");
            }
        }

        desc.getOptionKey().update(valuesMap, value);
        return OptionParseResult.correct();
    }

    /**
     * Parses a option at image build time. When the PrintFlags option is found prints all options
     * and interrupts compilation.
     *
     * @param optionPrefix Prefix used before option name
     * @param optionTypePrefix Prefix used for options when printing all possible options
     * @param options all possible options
     * @param valuesMap all current option values
     * @param errors a set that contains all error messages
     * @param arg the argument currently processed
     * @return true if option matches the option process
     */
    public static boolean parseHostedOption(String optionPrefix, String optionTypePrefix, SortedMap<String, OptionDescriptor> options, EconomicMap<OptionKey<?>, Object> valuesMap,
                    Set<String> errors, String arg, PrintStream out) {
        if (!arg.startsWith(optionPrefix)) {
            return false;
        }

        OptionParseResult optionParseResult = SubstrateOptionsParser.parseOption(options, arg.substring(optionPrefix.length()), valuesMap);
        if (optionParseResult.shouldPrintFlags()) {
            SubstrateOptionsParser.printFlags(options, valuesMap, null, optionTypePrefix, out);
            throw new InterruptImageBuilding();
        }
        if (!optionParseResult.isValid()) {
            errors.add(optionParseResult.getError());
        }
        return true;
    }

    /**
     * Wraps some given text to one or more lines of a given maximum width.
     *
     * @param text text to wrap
     * @param width maximum width of an output line, exception for words in {@code text} longer than
     *            this value
     * @return {@code text} broken into lines
     */
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text.length() > width) {
            String[] chunks = text.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String chunk : chunks) {
                if (line.length() + chunk.length() > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() != 0) {
                    line.append(' ');
                }
                String[] embeddedLines = chunk.split("%n", -2);
                if (embeddedLines.length == 1) {
                    line.append(chunk);
                } else {
                    for (int i = 0; i < embeddedLines.length; i++) {
                        line.append(embeddedLines[i]);
                        if (i < embeddedLines.length - 1) {
                            lines.add(line.toString());
                            line.setLength(0);
                        }
                    }
                }
            }
            if (line.length() != 0) {
                lines.add(line.toString());
            }
        } else {
            lines.add(text);
        }
        return lines;
    }

    static void printFlags(SortedMap<String, OptionDescriptor> sortedOptions, EconomicMap<OptionKey<?>, Object> valuesMap, RuntimeOptionValues existingOptionValues, String prefix, PrintStream out) {
        OptionValues optionValues = existingOptionValues != null ? existingOptionValues : new OptionValues(valuesMap);

        out.println("[List of " + prefix + " options]");
        for (Map.Entry<String, OptionDescriptor> e : sortedOptions.entrySet()) {
            e.getKey();
            OptionDescriptor desc = e.getValue();
            Object value = desc.getOptionKey().getValue(optionValues);
            List<String> helpLines = wrap(desc.getHelp(), 70);
            helpLines.addAll(desc.getExtraHelp());

            StringBuilder sb = new StringBuilder();
            sb.append(desc.getType().getSimpleName());
            do {
                sb.append(' ');
            } while (sb.length() < 9);
            sb.append(e.getKey());
            do {
                sb.append(' ');
            } while (sb.length() < 9 + 40);
            sb.append(" = ");
            sb.append(value);
            do {
                sb.append(' ');
            } while (sb.length() < 9 + 40 + 14);
            int helpStart = sb.length();
            sb.append(helpLines.get(0));
            for (int i = 1; i < helpLines.size(); i++) {
                sb.append('\n');
                for (int j = 0; j < helpStart; ++j) {
                    sb.append(' ');
                }
                sb.append(helpLines.get(i));
            }
            out.println(sb.toString());
        }
    }

    static long parseLong(String v) {
        String valueString = v.toLowerCase();
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
}
