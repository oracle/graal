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

import java.util.*;

import com.oracle.graal.hotspot.logging.*;
import com.oracle.graal.options.*;

public class HotSpotOptions {

    private static final Map<String, OptionProvider> options = new HashMap<>();

    static {
        ServiceLoader<OptionProvider> sl = ServiceLoader.loadInstalled(OptionProvider.class);
        for (OptionProvider provider : sl) {
            if (provider.getClass().getName().startsWith("com.oracle.graal")) {
                String name = provider.getName();
                OptionProvider existing = options.put(name, provider);
                assert existing == null : name + " option has multiple definitions: " + existing.getClass() + " and " + provider.getClass();
            }
        }
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

        OptionProvider optionProvider = options.get(optionName);
        if (optionProvider == null) {
            Logger.info("Could not find option " + optionName + " (use -G:+PrintFlags to see Graal options)");
            return false;
        }

        Class<?> optionType = optionProvider.getType();

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
            OptionValue<?> optionValue = optionProvider.getOptionValue();
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
        SortedMap<String, OptionProvider> sortedOptions = new TreeMap<>(options);
        for (Map.Entry<String, OptionProvider> e : sortedOptions.entrySet()) {
            e.getKey();
            OptionProvider opt = e.getValue();
            Object value = opt.getOptionValue().getValue();
            Logger.info(String.format("%9s %-40s = %-14s %s", opt.getType().getSimpleName(), e.getKey(), value, opt.getHelp()));
        }

        System.exit(0);
    }
}
