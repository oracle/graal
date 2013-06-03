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

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.hotspot.logging.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.*;

public class HotSpotOptions {

    private static final Map<String, OptionProvider> options = new HashMap<>();

    static {
        ServiceLoader<OptionProvider> sl = ServiceLoader.loadInstalled(OptionProvider.class);
        for (OptionProvider provider : sl) {
            if (provider.getClass().getName().startsWith("com.oracle.graal")) {
                String name = provider.getName();
                options.put(name, provider);
            }
        }
    }

    // Called from VM code
    public static boolean setOption(String option) {
        if (option.length() == 0) {
            return false;
        }

        Object value = null;
        String fieldName = null;
        String valueString = null;

        if (option.equals("+PrintFlags")) {
            printFlags();
            return true;
        }

        char first = option.charAt(0);
        if (first == '+' || first == '-') {
            fieldName = option.substring(1);
            value = (first == '+');
        } else {
            int index = option.indexOf('=');
            if (index == -1) {
                fieldName = option;
                valueString = null;
            } else {
                fieldName = option.substring(0, index);
                valueString = option.substring(index + 1);
            }
        }

        OptionProvider optionProvider = options.get(fieldName);
        if (optionProvider == null) {
            return setOptionLegacy(option, fieldName, value, valueString);
        }

        Class<?> optionType = optionProvider.getType();

        if (value == null) {
            if (optionType == Boolean.TYPE || optionType == Boolean.class) {
                Logger.info("Value for boolean option '" + fieldName + "' must use '-G:+" + fieldName + "' or '-G:-" + fieldName + "' format");
                return false;
            }

            if (valueString == null) {
                Logger.info("Value for option '" + fieldName + "' must use '-G:" + fieldName + "=<value>' format");
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
                Logger.info("Value for option '" + fieldName + "' must use '-G:" + fieldName + "=<value>' format");
                return false;
            }
        }

        if (value != null) {
            optionProvider.getOptionValue().setValue(value);
            // Logger.info("Set option " + fieldName + " to " + value);
        } else {
            Logger.info("Wrong value \"" + valueString + "\" for option " + fieldName);
            return false;
        }

        return true;
    }

    private static boolean setOptionLegacy(String option, String fieldName, Object v, String valueString) {
        Object value = v;
        Field f;
        try {
            f = GraalOptions.class.getDeclaredField(fieldName);
            Class<?> fType = f.getType();

            if (value == null) {
                if (fType == Boolean.TYPE) {
                    Logger.info("Value for boolean option '" + fieldName + "' must use '-G:+" + fieldName + "' or '-G:-" + fieldName + "' format");
                    return false;
                }

                if (valueString == null) {
                    Logger.info("Value for option '" + fieldName + "' must use '-G:" + fieldName + "=<value>' format");
                    return false;
                }

                if (fType == Float.TYPE) {
                    value = Float.parseFloat(valueString);
                } else if (fType == Double.TYPE) {
                    value = Double.parseDouble(valueString);
                } else if (fType == Integer.TYPE) {
                    value = Integer.parseInt(valueString);
                } else if (fType == String.class) {
                    value = valueString;
                }
            } else {
                if (fType != Boolean.TYPE) {
                    Logger.info("Value for option '" + fieldName + "' must use '-G:" + fieldName + "=<value>' format");
                    return false;
                }
            }

            if (value != null) {
                f.setAccessible(true);
                f.set(null, value);
                // Logger.info("Set option " + fieldName + " to " + value);
            } else {
                Logger.info("Wrong value \"" + valueString + "\" for option " + fieldName);
                return false;
            }
        } catch (SecurityException e) {
            Logger.info("Security exception when setting option " + option);
            return false;
        } catch (NoSuchFieldException e) {
            Logger.info("Could not find option " + fieldName + " (use -G:+PrintFlags to see Graal options)");
            return false;
        } catch (IllegalArgumentException e) {
            Logger.info("Illegal value for option " + option);
            return false;
        } catch (IllegalAccessException e) {
            Logger.info("Illegal access exception when setting option " + option);
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

        printFlagsLegacy();

        System.exit(0);
    }

    protected static void printFlagsLegacy() {
        Field[] flags = GraalOptions.class.getDeclaredFields();
        Arrays.sort(flags, new Comparator<Field>() {

            public int compare(Field o1, Field o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        for (Field f : flags) {
            if (Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                try {
                    Object value = f.get(null);
                    Logger.info(String.format("%9s %-40s = %s", f.getType().getSimpleName(), f.getName(), value));
                } catch (Exception e) {
                }
            }
        }
    }
}
