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

import static java.nio.file.Files.*;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

import com.oracle.graal.hotspot.logging.*;
import com.oracle.graal.options.*;

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
                if (desc.getClass().getName().startsWith("com.oracle.graal")) {
                    String name = desc.getName();
                    OptionDescriptor existing = options.put(name, desc);
                    assert existing == null : "Option named \"" + name + "\" has multiple definitions: " + existing.getLocation() + " and " + desc.getLocation();
                }
            }
        }
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
            OptionValue<?> optionValue = desc.getOptionValue();
            optionValue.setValue(value);
            // Logger.info("Set option " + fieldName + " to " + value);
        } else {
            Logger.info("Wrong value \"" + valueString + "\" for option " + optionName);
            return false;
        }

        return true;
    }

    private static void printFlags() {
        Logger.info("[Graal flags]");
        SortedMap<String, OptionDescriptor> sortedOptions = new TreeMap<>(options);
        for (Map.Entry<String, OptionDescriptor> e : sortedOptions.entrySet()) {
            e.getKey();
            OptionDescriptor desc = e.getValue();
            Object value = desc.getOptionValue().getValue();
            Logger.info(String.format("%9s %-40s = %-14s %s", desc.getType().getSimpleName(), e.getKey(), value, desc.getHelp()));
        }

        System.exit(0);
    }
}
