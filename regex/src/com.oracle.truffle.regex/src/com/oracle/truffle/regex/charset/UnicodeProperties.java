/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.regex.charset;

import com.oracle.truffle.api.CompilerDirectives;

public class UnicodeProperties {

    public static CodePointSet getProperty(String propertySpec) {
        return getProperty(propertySpec, false);
    }

    public static CodePointSet getProperty(String propertySpec, boolean caseInsensitive) {
        return evaluatePropertySpec(normalizePropertySpec(propertySpec, caseInsensitive));
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

    private static String normalizePropertySpec(String propertySpec, boolean caseInsensitive) {
        int equals = propertySpec.indexOf('=');
        if (equals >= 0) {
            String propertyName = normalizePropertyName(propertySpec.substring(0, equals), caseInsensitive);
            String propertyValue = propertySpec.substring(equals + 1);
            switch (propertyName) {
                case "gc":
                    propertyValue = normalizeGeneralCategoryName(propertyValue, caseInsensitive);
                    break;
                case "sc":
                case "scx":
                    propertyValue = normalizeScriptName(propertyValue, caseInsensitive);
                    break;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalArgumentException(String.format("Binary property %s cannot appear to the left of '=' in a Unicode property escape", propertySpec.substring(0, equals)));
            }
            return propertyName + "=" + propertyValue;
        } else if (isSupportedGeneralCategory(propertySpec, caseInsensitive)) {
            return "gc=" + normalizeGeneralCategoryName(propertySpec, caseInsensitive);
        } else {
            return normalizePropertyName(propertySpec, caseInsensitive);
        }
    }

    private static String normalizePropertyName(String propertyName, boolean caseInsensitive) {
        String caseCorrectPropertyName = propertyName;
        if (caseInsensitive) {
            caseCorrectPropertyName = UnicodePropertyDataRuby.PROPERTY_ALIASES_LOWERCASE.get(propertyName.toLowerCase(), propertyName);
        }
        if (!UnicodePropertyData.PROPERTY_ALIASES.containsKey(caseCorrectPropertyName)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException(String.format("Unsupported Unicode character property '%s'", propertyName));
        }
        return UnicodePropertyData.PROPERTY_ALIASES.get(caseCorrectPropertyName);
    }

    private static String normalizeGeneralCategoryName(String generalCategoryName, boolean caseInsensitive) {
        String caseCorrectGeneralCategoryName = generalCategoryName;
        if (caseInsensitive) {
            caseCorrectGeneralCategoryName = UnicodePropertyDataRuby.GENERAL_CATEGORY_ALIASES_LOWERCASE.get(generalCategoryName.toLowerCase(), generalCategoryName);
        }
        if (!UnicodePropertyData.GENERAL_CATEGORY_ALIASES.containsKey(caseCorrectGeneralCategoryName)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException(String.format("Unknown Unicode character general category '%s'", generalCategoryName));
        }
        return UnicodePropertyData.GENERAL_CATEGORY_ALIASES.get(caseCorrectGeneralCategoryName);
    }

    private static String normalizeScriptName(String scriptName, boolean caseInsensitive) {
        String caseCorrectScriptName = scriptName;
        if (caseInsensitive) {
            caseCorrectScriptName = UnicodePropertyDataRuby.SCRIPT_ALIASES_LOWERCASE.get(scriptName.toLowerCase(), scriptName);
        }
        if (!UnicodePropertyData.SCRIPT_ALIASES.containsKey(caseCorrectScriptName)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException(String.format("Unkown Unicode script name '%s'", scriptName));
        }
        return UnicodePropertyData.SCRIPT_ALIASES.get(caseCorrectScriptName);
    }

    public static boolean isSupportedProperty(String propertyName, boolean caseInsensitive) {
        if (caseInsensitive) {
            return UnicodePropertyDataRuby.PROPERTY_ALIASES_LOWERCASE.containsKey(propertyName.toLowerCase());
        } else {
            return UnicodePropertyData.PROPERTY_ALIASES.containsKey(propertyName);
        }
    }

    public static boolean isSupportedGeneralCategory(String generalCategoryName, boolean caseInsensitive) {
        if (caseInsensitive) {
            return UnicodePropertyDataRuby.GENERAL_CATEGORY_ALIASES_LOWERCASE.containsKey(generalCategoryName.toLowerCase());
        } else {
            return UnicodePropertyData.GENERAL_CATEGORY_ALIASES.containsKey(generalCategoryName);
        }
    }

    public static boolean isSupportedScript(String scriptName, boolean caseInsensitive) {
        if (caseInsensitive) {
            return UnicodePropertyDataRuby.SCRIPT_ALIASES_LOWERCASE.containsKey(scriptName.toLowerCase());
        } else {
            return UnicodePropertyData.SCRIPT_ALIASES.containsKey(scriptName);
        }
    }
}
