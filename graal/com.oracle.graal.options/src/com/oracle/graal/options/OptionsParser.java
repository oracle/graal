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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * This class contains methods for parsing Graal options and matching them against a set of
 * {@link OptionDescriptors}. The {@link OptionDescriptors} are loaded from Graal jars, either
 * {@linkplain GraalJarsOptionDescriptorsProvider directly} or via a {@link ServiceLoader}.
 */
public class OptionsParser {

    private static final OptionValue<Boolean> PrintFlags = new OptionValue<>(false);
    private static final OptionValue<Boolean> ShowFlags = new OptionValue<>(false);

    /**
     * A service for looking up {@link OptionDescriptor}s.
     */
    public interface OptionDescriptorsProvider {
        /**
         * Gets the {@link OptionDescriptor} matching a given option {@linkplain Option#name() name}
         * or null if no option of that name is provided by this object.
         */
        OptionDescriptor get(String name);
    }

    public interface OptionConsumer {
        void set(OptionDescriptor desc, Object value);
    }

    /**
     * Parses a map representing assignments of values to options.
     *
     * @param optionSettings option settings (i.e., assignments of values to options)
     * @param setter the object to notify of the parsed option and value
     * @param odp if non-null, the service to use for looking up {@link OptionDescriptor}s
     * @param options the options database to use if {@code odp == null}. If
     *            {@code options == null && odp == null}, {@link OptionsLoader#options} is used.
     * @throws IllegalArgumentException if there's a problem parsing {@code option}
     */
    public static void parseOptions(Map<String, String> optionSettings, OptionConsumer setter, OptionDescriptorsProvider odp, Map<String, OptionDescriptor> options) {
        if (optionSettings != null && !optionSettings.isEmpty()) {

            for (Map.Entry<String, String> e : optionSettings.entrySet()) {
                parseOption(e.getKey(), e.getValue(), setter, odp, options);
            }
            if (PrintFlags.getValue() || ShowFlags.getValue()) {
                printFlags(resolveOptions(options), "Graal", System.out, optionSettings.keySet());
                if (PrintFlags.getValue()) {
                    System.exit(0);
                }
            }
        }
    }

    /**
     * Parses a given option setting string to a map of settings.
     *
     * @param optionSetting a string matching the pattern {@code <name>=<value>}
     */
    public static void parseOptionSettingTo(String optionSetting, Map<String, String> dst) {
        int eqIndex = optionSetting.indexOf('=');
        if (eqIndex == -1) {
            throw new InternalError("Option setting has does not match the pattern <name>=<value>: " + optionSetting);
        }
        dst.put(optionSetting.substring(0, eqIndex), optionSetting.substring(eqIndex + 1));
    }

    /**
     * Resolves {@code options} to a non-null value. This ensures {@link OptionsLoader#options} is
     * only loaded if necessary.
     */
    private static Map<String, OptionDescriptor> resolveOptions(Map<String, OptionDescriptor> options) {
        return options != null ? options : OptionsLoader.options;
    }

    /**
     * Parses a given option name and value.
     *
     * @param name the option name
     * @param valueString the option value as a string
     * @param setter the object to notify of the parsed option and value
     * @param odp if non-null, the service to use for looking up {@link OptionDescriptor}s
     * @param options the options database to use if {@code odp == null}. If
     *            {@code options == null && odp == null}, {@link OptionsLoader#options} is used.
     * @throws IllegalArgumentException if there's a problem parsing {@code option}
     */
    private static void parseOption(String name, String valueString, OptionConsumer setter, OptionDescriptorsProvider odp, Map<String, OptionDescriptor> options) {

        OptionDescriptor desc = odp != null ? odp.get(name) : resolveOptions(options).get(name);
        if (desc == null) {
            if (name.equals("PrintFlags")) {
                desc = OptionDescriptor.create("PrintFlags", Boolean.class, "Prints all Graal flags and exits", OptionsParser.class, "PrintFlags", PrintFlags);
            } else if (name.equals("ShowFlags")) {
                desc = OptionDescriptor.create("ShowFlags", Boolean.class, "Prints all Graal flags and continues", OptionsParser.class, "ShowFlags", ShowFlags);
            }
        }
        if (desc == null) {
            List<OptionDescriptor> matches = fuzzyMatch(resolveOptions(options), name);
            Formatter msg = new Formatter();
            msg.format("Could not find option %s", name);
            if (!matches.isEmpty()) {
                msg.format("%nDid you mean one of the following?");
                for (OptionDescriptor match : matches) {
                    msg.format("%n    %s=<value>", match.getName());
                }
            }
            throw new IllegalArgumentException(msg.toString());
        }

        Class<?> optionType = desc.getType();
        Object value;
        if (optionType == Boolean.class) {
            if ("true".equals(valueString)) {
                value = Boolean.TRUE;
            } else if ("false".equals(valueString)) {
                value = Boolean.FALSE;
            } else {
                throw new IllegalArgumentException("Boolean option '" + name + "' must have value \"true\" or \"false\", not \"" + valueString + "\"");
            }
        } else if (optionType == Float.class) {
            value = Float.parseFloat(valueString);
        } else if (optionType == Double.class) {
            value = Double.parseDouble(valueString);
        } else if (optionType == Integer.class) {
            value = Integer.valueOf((int) parseLong(valueString));
        } else if (optionType == Long.class) {
            value = Long.valueOf(parseLong(valueString));
        } else if (optionType == String.class) {
            value = valueString;
        } else {
            throw new IllegalArgumentException("Wrong value for option '" + name + "'");
        }
        if (setter == null) {
            desc.getOptionValue().setValue(value);
        } else {
            setter.set(desc, value);
        }
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

    private static void printFlags(Map<String, OptionDescriptor> options, String prefix, PrintStream out, Set<String> explicitlyAssigned) {
        SortedMap<String, OptionDescriptor> sortedOptions;
        if (options instanceof SortedMap) {
            sortedOptions = (SortedMap<String, OptionDescriptor>) options;
        } else {
            sortedOptions = new TreeMap<>(options);
        }
        out.println("[List of " + prefix + " options]");
        for (Map.Entry<String, OptionDescriptor> e : sortedOptions.entrySet()) {
            OptionDescriptor desc = e.getValue();
            Object value = desc.getOptionValue().getValue();
            List<String> helpLines = wrap(desc.getHelp(), 70);
            String name = e.getKey();
            String assign = explicitlyAssigned.contains(name) ? ":=" : " =";
            out.printf("%9s %-40s %s %-14s %s%n", desc.getType().getSimpleName(), name, assign, value, helpLines.get(0));
            for (int i = 1; i < helpLines.size(); i++) {
                out.printf("%67s %s%n", " ", helpLines.get(i));
            }
        }
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
    private static List<OptionDescriptor> fuzzyMatch(Map<String, OptionDescriptor> options, String optionName) {
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
