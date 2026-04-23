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
package com.oracle.svm.core.properties;

import java.util.Arrays;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.svm.core.jdk.RuntimeBootModuleLayerSupport;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;

public final class RuntimeSystemPropertyParser {

    private static final String PROPERTY_PREFIX = "-D";

    /**
     * Parses all arguments to find those that set Java system properties (those that start with
     * "-D"). Any such matches are removed from the argument list and the corresponding system
     * property is set. The returned array of arguments are those that should be passed through to
     * the application.
     */
    public static String[] parse(String[] args, String graalOptionPrefix, String legacyGraalOptionPrefix) {
        int newIdx = 0;
        EconomicMap<String, String> properties = EconomicMap.create();
        int oldIdx = 0;
        while (oldIdx < args.length) {
            int consumed = parseModuleOption(args, oldIdx, properties);
            if (consumed > 0) {
                oldIdx += consumed;
                continue;
            }
            String arg = args[oldIdx];
            if (!parseProperty(arg, properties, graalOptionPrefix, legacyGraalOptionPrefix)) {
                assert newIdx <= oldIdx;
                args[newIdx] = arg;
                newIdx++;
            }
            oldIdx++;
        }
        MapCursor<String, String> cursor = properties.getEntries();
        while (cursor.advance()) {
            SystemPropertiesSupport.singleton().initializeProperty(cursor.getKey(), cursor.getValue());
        }
        if (newIdx == args.length) {
            /* We can be allocation free and just return the original arguments. */
            return args;
        } else {
            return Arrays.copyOf(args, newIdx);
        }
    }

    private static boolean parseProperty(String arg, EconomicMap<String, String> properties, String graalOptionPrefix, String legacyGraalOptionPrefix) {
        if (!arg.startsWith(PROPERTY_PREFIX) || arg.startsWith(graalOptionPrefix) || arg.startsWith(legacyGraalOptionPrefix)) {
            return false;
        }
        return parseProperty0(arg, properties);
    }

    private static boolean parseProperty0(String arg, EconomicMap<String, String> parsedProperties) {
        String property = arg.substring(PROPERTY_PREFIX.length());
        int splitIndex = property.indexOf('=');
        if (splitIndex == -1) {
            return false;
        }

        String key = property.substring(0, splitIndex);
        String value = property.substring(splitIndex + 1);
        parsedProperties.put(key, value);
        return true;
    }

    private static int parseModuleOption(String[] args, int index, EconomicMap<String, String> properties) {
        String arg = args[index];
        // The JVM only sees the long "=" forms after launcher/libjli normalization.
        if (arg.startsWith(RuntimeBootModuleLayerSupport.MODULE_PATH_OPTION + "=")) {
            properties.put(RuntimeBootModuleLayerSupport.MODULE_PATH_PROPERTY, arg.substring(RuntimeBootModuleLayerSupport.MODULE_PATH_OPTION.length() + 1));
            return 1;
        }
        if (arg.startsWith(RuntimeBootModuleLayerSupport.ADD_MODULES_OPTION + "=")) {
            addNumberedProperty(RuntimeBootModuleLayerSupport.ADD_MODULES_PROPERTY_PREFIX,
                            arg.substring(RuntimeBootModuleLayerSupport.ADD_MODULES_OPTION.length() + 1), properties);
            return 1;
        }
        if (arg.startsWith(RuntimeBootModuleLayerSupport.ADD_EXPORTS_OPTION + "=")) {
            addNumberedProperty(RuntimeBootModuleLayerSupport.ADD_EXPORTS_PROPERTY_PREFIX,
                            arg.substring(RuntimeBootModuleLayerSupport.ADD_EXPORTS_OPTION.length() + 1), properties);
            return 1;
        }
        if (arg.startsWith(RuntimeBootModuleLayerSupport.ADD_OPENS_OPTION + "=")) {
            addNumberedProperty(RuntimeBootModuleLayerSupport.ADD_OPENS_PROPERTY_PREFIX,
                            arg.substring(RuntimeBootModuleLayerSupport.ADD_OPENS_OPTION.length() + 1), properties);
            return 1;
        }
        return 0;
    }

    private static void addNumberedProperty(String prefix, String value, EconomicMap<String, String> properties) {
        int index = 0;
        while (properties.containsKey(prefix + index)) {
            index++;
        }
        properties.put(prefix + index, value);
    }
}
