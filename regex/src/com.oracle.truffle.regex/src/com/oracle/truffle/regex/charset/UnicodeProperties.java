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

import org.graalvm.collections.EconomicMap;

public class UnicodeProperties {

    private static final EconomicMap<String, CodePointSet> CATEGORY_UNIONS = EconomicMap.create(8);

    public static CodePointSet getProperty(String propertySpec) {
        return evaluatePropertySpec(normalizePropertySpec(propertySpec));
    }

    /**
     * @param propertySpec *Normalized* Unicode character property specification (i.e. only
     *            abbreviated properties and property values)
     */
    private static CodePointSet evaluatePropertySpec(String propertySpec) {
        switch (propertySpec) {
            // The following aggregate general categories are defined in Unicode Standard Annex 44,
            // Section 5.7.1. (http://www.unicode.org/reports/tr44/#GC_Values_Table).
            case "gc=LC":
                return getUnionOfCategories("gc=LC", "Lu", "Ll", "Lt");
            case "gc=L":
                return getUnionOfCategories("gc=L", "Lu", "Ll", "Lt", "Lm", "Lo");
            case "gc=M":
                return getUnionOfCategories("gc=M", "Mn", "Mc", "Me");
            case "gc=N":
                return getUnionOfCategories("gc=N", "Nd", "Nl", "No");
            case "gc=P":
                return getUnionOfCategories("gc=P", "Pc", "Pd", "Ps", "Pe", "Pi", "Pf", "Po");
            case "gc=S":
                return getUnionOfCategories("gc=S", "Sm", "Sc", "Sk", "So");
            case "gc=Z":
                return getUnionOfCategories("gc=Z", "Zs", "Zl", "Zp");
            case "gc=C":
                return getUnionOfCategories("gc=C", "Cc", "Cf", "Cs", "Co", "Cn");
        }
        return UnicodePropertyData.retrieveProperty(propertySpec);
    }

    private static CodePointSet getUnionOfCategories(String name, String... unionNames) {
        if (!CATEGORY_UNIONS.containsKey(name)) {
            CATEGORY_UNIONS.put(name, unionOfGeneralCategories(unionNames));
        }
        return CATEGORY_UNIONS.get(name);
    }

    /**
     * @param generalCategoryNames *Abbreviated* names of general categories
     */
    private static CodePointSet unionOfGeneralCategories(String... generalCategoryNames) {
        CodePointSet set = CodePointSet.getEmpty();
        for (String generalCategoryName : generalCategoryNames) {
            set = set.union(evaluatePropertySpec("gc=" + generalCategoryName));
        }
        return set;
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
