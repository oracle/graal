/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.charset;

public class UnicodeProperties {

    public static CodePointSet getProperty(String propertySpec) {
        return evaluatePropertySpec(normalizePropertySpec(propertySpec));
    }

    /**
     * @param propertySpec *Normalized* Unicode character property specification (i.e. only
     *            abbreviated properties and property values)
     */
    private static CodePointSet evaluatePropertySpec(String propertySpec) {
        CodePointSet generalCategory = UnicodeGeneralCategories.getGeneralCategory(propertySpec);
        if (generalCategory != null) {
            return generalCategory;
        }
        return UnicodePropertyData.retrieveProperty(propertySpec);
    }

    private static String normalizePropertySpec(String propertySpec) {
        int equals = propertySpec.indexOf('=');
        if (equals >= 0) {
            String propertyName = normalizePropertyName(propertySpec.substring(0, equals));
            String propertyValue = propertySpec.substring(equals + 1);
            switch (propertyName) {
                case "gc":
                    propertyValue = normalizeGeneralCategoryName(propertyValue);
                    break;
                case "sc":
                case "scx":
                    propertyValue = normalizeScriptName(propertyValue);
                    break;
                default:
                    throw new IllegalArgumentException(String.format("Binary property %s cannot appear to the left of '=' in a Unicode property escape", propertySpec.substring(0, equals)));
            }
            return propertyName + "=" + propertyValue;
        } else if (isGeneralCategoryName(propertySpec)) {
            return "gc=" + normalizeGeneralCategoryName(propertySpec);
        } else {
            return normalizePropertyName(propertySpec);
        }
    }

    private static boolean isGeneralCategoryName(String generalCategoryName) {
        return UnicodePropertyData.GENERAL_CATEGORY_ALIASES.containsKey(generalCategoryName);
    }

    private static String normalizePropertyName(String propertyName) {
        if (!UnicodePropertyData.PROPERTY_ALIASES.containsKey(propertyName)) {
            throw new IllegalArgumentException(String.format("Unsupported Unicode character property '%s'", propertyName));
        }
        return UnicodePropertyData.PROPERTY_ALIASES.get(propertyName);
    }

    private static String normalizeGeneralCategoryName(String generalCategoryName) {
        if (!UnicodePropertyData.GENERAL_CATEGORY_ALIASES.containsKey(generalCategoryName)) {
            throw new IllegalArgumentException(String.format("Unknown Unicode character general category '%s'", generalCategoryName));
        }
        return UnicodePropertyData.GENERAL_CATEGORY_ALIASES.get(generalCategoryName);
    }

    private static String normalizeScriptName(String scriptName) {
        if (!UnicodePropertyData.SCRIPT_ALIASES.containsKey(scriptName)) {
            throw new IllegalArgumentException(String.format("Unkown Unicode script name '%s'", scriptName));
        }
        return UnicodePropertyData.SCRIPT_ALIASES.get(scriptName);
    }
}
