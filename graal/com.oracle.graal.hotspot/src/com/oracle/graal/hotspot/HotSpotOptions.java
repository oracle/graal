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
import static com.oracle.graal.hotspot.CompilationQueue.Options.*;
import static com.oracle.graal.hotspot.HotSpotOptionsLoader.*;
import static java.lang.Double.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.common.inlining.*;

/**
 * Sets Graal options from the HotSpot command line. Such options are distinguished by a
 * {@code "-G:"} prefix.
 */
public class HotSpotOptions {

    /**
     * Parses the Graal specific options specified to HotSpot (e.g., on the command line).
     *
     * @return true if the CITime or CITimeEach HotSpot VM options are set
     */
    private static native boolean parseVMOptions();

    static {
        boolean timeCompilations = parseVMOptions();
        if (timeCompilations || PrintCompRate.getValue() != 0) {
            if (timeCompilations && PrintCompRate.getValue() != 0) {
                throw new GraalInternalError("PrintCompRate is incompatible with CITime and CITimeEach");
            }
            unconditionallyEnableTimerOrMetric(InliningUtil.class, "InlinedBytecodes");
            unconditionallyEnableTimerOrMetric(CompilationTask.class, "CompilationTime");
        }
        assert !Debug.Initialization.isDebugInitialized() : "The class " + Debug.class.getName() + " must not be initialized before the Graal runtime has been initialized. " +
                        "This can be fixed by placing a call to " + Graal.class.getName() + ".runtime() on the path that triggers initialization of " + Debug.class.getName();
        if (areDebugScopePatternsEnabled()) {
            System.setProperty(Debug.Initialization.INITIALIZER_PROPERTY_NAME, "true");
        }
    }

    /**
     * Ensures {@link HotSpotOptions} is initialized.
     */
    public static void initialize() {
    }

    interface OptionConsumer {
        void set(OptionDescriptor desc, Object value);
    }

    /**
     * Helper for the VM code called by {@link #parseVMOptions()}.
     *
     * @param name the name of a parsed option
     * @param option the object encapsulating the option
     * @param spec specification of boolean option value, type of option value or action to take
     */
    static void setOption(String name, OptionValue<?> option, char spec, String stringValue, long primitiveValue) {
        switch (spec) {
            case '+':
                option.setValue(Boolean.TRUE);
                break;
            case '-':
                option.setValue(Boolean.FALSE);
                break;
            case '?':
                printFlags();
                break;
            case ' ':
                printNoMatchMessage(name);
                break;
            case 'i':
                option.setValue((int) primitiveValue);
                break;
            case 'f':
                option.setValue((float) longBitsToDouble(primitiveValue));
                break;
            case 'd':
                option.setValue(longBitsToDouble(primitiveValue));
                break;
            case 's':
                option.setValue(stringValue);
                break;
        }
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
            printNoMatchMessage(optionName);
            return false;
        }

        Class<?> optionType = desc.getType();

        if (value == null) {
            if (optionType == Boolean.TYPE || optionType == Boolean.class) {
                System.err.println("Value for boolean option '" + optionName + "' must use '-G:+" + optionName + "' or '-G:-" + optionName + "' format");
                return false;
            }

            if (valueString == null) {
                System.err.println("Value for option '" + optionName + "' must use '-G:" + optionName + "=<value>' format");
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
                System.err.println("Value for option '" + optionName + "' must use '-G:" + optionName + "=<value>' format");
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

    protected static void printNoMatchMessage(String optionName) {
        System.err.println("Could not find option " + optionName + " (use -G:+PrintFlags to see Graal options)");
        List<OptionDescriptor> matches = fuzzyMatch(optionName);
        if (!matches.isEmpty()) {
            System.err.println("Did you mean one of the following?");
            for (OptionDescriptor match : matches) {
                boolean isBoolean = match.getType() == boolean.class;
                System.err.println(String.format("    %s%s%s", isBoolean ? "(+/-)" : "", match.getName(), isBoolean ? "" : "=<value>"));
            }
        }
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
                System.err.println("Overrode value \"" + previous + "\" of system property \"" + propertyName + "\" with \"true\"");
            }
        } catch (Exception e) {
            throw new GraalInternalError(e);
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
        System.out.println("[Graal flags]");
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
