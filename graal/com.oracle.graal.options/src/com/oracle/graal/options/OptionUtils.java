/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.options;

import java.util.*;

public class OptionUtils {

    public interface OptionConsumer {
        void set(OptionDescriptor desc, Object value);
    }

    /**
     * Parses a given option value specification.
     *
     * @param option the specification of an option and its value
     * @param setter the object to notify of the parsed option and value. If null, the
     *            {@link OptionValue#setValue(Object)} method of the specified option is called
     *            instead.
     */
    public static boolean parseOption(SortedMap<String, OptionDescriptor> options, String option, String prefix, OptionConsumer setter) {
        if (option.length() == 0) {
            return false;
        }

        Object value = null;
        String optionName = null;
        String valueString = null;

        if (option.equals("+PrintFlags")) {
            printFlags(options, prefix);
            return true;
        }

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

        OptionDescriptor desc = options.get(optionName);
        if (desc == null) {
            printNoMatchMessage(options, optionName, prefix);
            return false;
        }

        Class<?> optionType = desc.getType();

        if (value == null) {
            if (optionType == Boolean.TYPE || optionType == Boolean.class) {
                System.err.println("Value for boolean option '" + optionName + "' must use '" + prefix + "+" + optionName + "' or '" + prefix + "-" + optionName + "' format");
                return false;
            }

            if (valueString == null) {
                System.err.println("Value for option '" + optionName + "' must use '" + prefix + optionName + "=<value>' format");
                return false;
            }

            if (optionType == Float.class) {
                value = Float.parseFloat(valueString);
            } else if (optionType == Double.class) {
                value = Double.parseDouble(valueString);
            } else if (optionType == Integer.class) {
                value = Integer.valueOf((int) parseLong(valueString));
            } else if (optionType == Long.class) {
                value = Long.valueOf(parseLong(valueString));
            } else if (optionType == String.class) {
                value = valueString;
            }
        } else {
            if (optionType != Boolean.class) {
                System.err.println("Value for option '" + optionName + "' must use '" + prefix + optionName + "=<value>' format");
                return false;
            }
        }

        if (value != null) {
            if (setter != null) {
                setter.set(desc, value);
            } else {
                OptionValue<?> optionValue = desc.getOptionValue();
                optionValue.setValue(value);
                // System.err.println("Set option " + desc.getName() + " to " + value);
            }
        } else {
            System.err.println("Wrong value \"" + valueString + "\" for option " + optionName);
            return false;
        }

        return true;
    }

    private static long parseLong(String v) {
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

    public static void printNoMatchMessage(SortedMap<String, OptionDescriptor> options, String optionName, String prefix) {
        OptionDescriptor desc = options.get(optionName);
        if (desc != null) {
            if (desc.getType() == Boolean.class) {
                System.err.println("Boolean option " + optionName + " must be prefixed with '+' or '-'");
            } else {
                System.err.println(desc.getType().getSimpleName() + " option " + optionName + " must not be prefixed with '+' or '-'");
            }
        } else {
            System.err.println("Could not find option " + optionName + " (use " + prefix + "+PrintFlags to see options)");
            List<OptionDescriptor> matches = fuzzyMatch(options, optionName);
            if (!matches.isEmpty()) {
                System.err.println("Did you mean one of the following?");
                for (OptionDescriptor match : matches) {
                    boolean isBoolean = match.getType() == Boolean.class;
                    System.err.println(String.format("    %s%s%s", isBoolean ? "(+/-)" : "", match.getName(), isBoolean ? "" : "=<value>"));
                }
            }
        }
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
        List<String> lines = Collections.singletonList(text);
        if (text.length() > width) {
            String[] chunks = text.split("\\s+");
            lines = new ArrayList<>();
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
        }
        return lines;
    }

    public static void printFlags(SortedMap<String, OptionDescriptor> options, String prefix) {
        System.out.println("[List of " + prefix + " options]");
        SortedMap<String, OptionDescriptor> sortedOptions = options;
        for (Map.Entry<String, OptionDescriptor> e : sortedOptions.entrySet()) {
            e.getKey();
            OptionDescriptor desc = e.getValue();
            Object value = desc.getOptionValue().getValue();
            List<String> helpLines = wrap(desc.getHelp(), 70);
            System.out.println(String.format("%9s %-40s = %-14s %s", desc.getType().getSimpleName(), e.getKey(), value, helpLines.get(0)));
            for (int i = 1; i < helpLines.size(); i++) {
                System.out.println(String.format("%67s %s", " ", helpLines.get(i)));
            }
        }

        System.exit(0);
    }

    /**
     * Compute string similarity based on Dice's coefficient.
     *
     * Ported from str_similar() in globals.cpp.
     */
    static float stringSimiliarity(String str1, String str2) {
        int hit = 0;
        for (int i = 0; i < str1.length() - 1; ++i) {
            for (int j = 0; j < str2.length() - 1; ++j) {
                if ((str1.charAt(i) == str2.charAt(j)) && (str1.charAt(i + 1) == str2.charAt(j + 1))) {
                    ++hit;
                    break;
                }
            }
        }
        return 2.0f * hit / (str1.length() + str2.length());
    }

    private static final float FUZZY_MATCH_THRESHOLD = 0.7F;

    /**
     * Returns the set of options that fuzzy match a given option name.
     */
    private static List<OptionDescriptor> fuzzyMatch(SortedMap<String, OptionDescriptor> options, String optionName) {
        List<OptionDescriptor> matches = new ArrayList<>();
        for (Map.Entry<String, OptionDescriptor> e : options.entrySet()) {
            float score = stringSimiliarity(e.getKey(), optionName);
            if (score >= FUZZY_MATCH_THRESHOLD) {
                matches.add(e.getValue());
            }
        }
        return matches;
    }
}
