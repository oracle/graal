/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot;

import static com.oracle.graal.compiler.GraalDebugConfig.*;
import static com.oracle.graal.hotspot.bridge.VMToCompilerImpl.*;
import static java.nio.file.Files.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.logging.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.common.*;

/**
 * Called from {@code graalCompiler.cpp} to parse any Graal specific options. Such options are
 * (currently) distinguished by a {@code "-G:"} prefix.
 */
public class HotSpotOptions {

    private static final Map<String, OptionDescriptor> options = new HashMap<>();

    /**
     * Initializes {@link #options} from {@link Options} services.
     */
    private static void initializeOptions() {
        ServiceLoader<Options> sl = ServiceLoader.loadInstalled(Options.class);
        for (Options opts : sl) {
            for (OptionDescriptor desc : opts) {
                if (isHotSpotOption(desc)) {
                    String name = desc.getName();
                    OptionDescriptor existing = options.put(name, desc);
                    assert existing == null : "Option named \"" + name + "\" has multiple definitions: " + existing.getLocation() + " and " + desc.getLocation();
                }
            }
        }
    }

    /**
     * Determines if a given option is a HotSpot command line option.
     */
    public static boolean isHotSpotOption(OptionDescriptor desc) {
        return desc.getClass().getName().startsWith("com.oracle.graal");
    }

    /**
     * Loads default option value overrides from a {@code graal.options} file if it exists. Each
     * line in this file starts with {@code "#"} and is ignored or must have the format of a Graal
     * command line option without the leading {@code "-G:"} prefix. These option value are set
     * prior to processing of any Graal options present on the command line.
     */
    private static void loadOptionOverrides() throws InternalError {
        String javaHome = System.getProperty("java.home");
        Path graalDotOptions = Paths.get(javaHome, "lib", "graal.options");
        if (!exists(graalDotOptions)) {
            graalDotOptions = Paths.get(javaHome, "jre", "lib", "graal.options");
        }
        if (exists(graalDotOptions)) {
            try {
                for (String line : Files.readAllLines(graalDotOptions, Charset.defaultCharset())) {
                    if (!line.startsWith("#")) {
                        if (!setOption(line)) {
                            throw new InternalError("Invalid option \"" + line + "\" specified in " + graalDotOptions);
                        }
                    }
                }
            } catch (IOException e) {
                throw (InternalError) new InternalError().initCause(e);
            }
        }
    }

    static {
        initializeOptions();
        loadOptionOverrides();
    }

    // Called from VM code
    public static boolean setOption(String option) {
        return parseOption(option, null);
    }

    interface OptionConsumer {
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
    public static boolean parseOption(String option, OptionConsumer setter) {
        if (option.length() == 0) {
            return false;
        }

        Object value = null;
        String optionName = null;
        String valueString = null;

        if (option.equals("+PrintFlags")) {
            printFlags();
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
            Logger.info("Could not find option " + optionName + " (use -G:+PrintFlags to see Graal options)");
            List<OptionDescriptor> matches = fuzzyMatch(optionName);
            if (!matches.isEmpty()) {
                Logger.info("Did you mean one of the following?");
                for (OptionDescriptor match : matches) {
                    boolean isBoolean = match.getType() == boolean.class;
                    Logger.info(String.format("    %s%s%s", isBoolean ? "(+/-)" : "", match.getName(), isBoolean ? "" : "=<value>"));
                }
            }
            return false;
        }

        Class<?> optionType = desc.getType();

        if (value == null) {
            if (optionType == Boolean.TYPE || optionType == Boolean.class) {
                Logger.info("Value for boolean option '" + optionName + "' must use '-G:+" + optionName + "' or '-G:-" + optionName + "' format");
                return false;
            }

            if (valueString == null) {
                Logger.info("Value for option '" + optionName + "' must use '-G:" + optionName + "=<value>' format");
                return false;
            }

            if (optionType == Float.class) {
                value = Float.parseFloat(valueString);
            } else if (optionType == Double.class) {
                value = Double.parseDouble(valueString);
            } else if (optionType == Integer.class) {
                value = Integer.parseInt(valueString);
            } else if (optionType == String.class) {
                value = valueString;
            }
        } else {
            if (optionType != Boolean.class) {
                Logger.info("Value for option '" + optionName + "' must use '-G:" + optionName + "=<value>' format");
                return false;
            }
        }

        if (value != null) {
            if (setter != null) {
                setter.set(desc, value);
            } else {
                OptionValue<?> optionValue = desc.getOptionValue();
                optionValue.setValue(value);
                // Logger.info("Set option " + desc.getName() + " to " + value);
            }
        } else {
            Logger.info("Wrong value \"" + valueString + "\" for option " + optionName);
            return false;
        }

        return true;
    }

    /**
     * Sets the relevant system property such that a {@link DebugTimer} or {@link DebugMetric}
     * associated with a field in a class will be unconditionally enabled when it is created.
     * <p>
     * This method verifies that the named field exists and is of an expected type. However, it does
     * not verify that the timer or metric created has the same name of the field.
     * 
     * @param c the class in which the field is declared
     * @param name the name of the field
     */
    private static void unconditionallyEnableTimerOrMetric(Class<?> c, String name) {
        try {
            Field field = c.getDeclaredField(name);
            String propertyName;
            if (DebugTimer.class.isAssignableFrom(field.getType())) {
                propertyName = Debug.ENABLE_TIMER_PROPERTY_NAME_PREFIX + name;
            } else {
                assert DebugMetric.class.isAssignableFrom(field.getType());
                propertyName = Debug.ENABLE_METRIC_PROPERTY_NAME_PREFIX + name;
            }
            String previous = System.setProperty(propertyName, "true");
            if (previous != null) {
                Logger.info("Overrode value \"" + previous + "\" of system property \"" + propertyName + "\" with \"true\"");
            }
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }

    /**
     * Called from VM code once all Graal command line options have been processed by
     * {@link #setOption(String)}.
     * 
     * @param timeCompilations true if the CITime or CITimeEach HotSpot VM options are set
     */
    public static void finalizeOptions(boolean timeCompilations) {
        if (timeCompilations || PrintCompRate.getValue() != 0) {
            unconditionallyEnableTimerOrMetric(InliningUtil.class, "InlinedBytecodes");
            unconditionallyEnableTimerOrMetric(CompilationTask.class, "CompilationTime");
        }
        if (areDebugScopePatternsEnabled()) {
            assert !Debug.Initialization.isDebugInitialized();
            System.setProperty(Debug.Initialization.INITIALIZER_PROPERTY_NAME, "true");
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

    private static void printFlags() {
        Logger.info("[Graal flags]");
        SortedMap<String, OptionDescriptor> sortedOptions = new TreeMap<>(options);
        for (Map.Entry<String, OptionDescriptor> e : sortedOptions.entrySet()) {
            e.getKey();
            OptionDescriptor desc = e.getValue();
            Object value = desc.getOptionValue().getValue();
            List<String> helpLines = wrap(desc.getHelp(), 70);
            Logger.info(String.format("%9s %-40s = %-14s %s", desc.getType().getSimpleName(), e.getKey(), value, helpLines.get(0)));
            for (int i = 1; i < helpLines.size(); i++) {
                Logger.info(String.format("%67s %s", " ", helpLines.get(i)));
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
    private static List<OptionDescriptor> fuzzyMatch(String optionName) {
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
