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

import java.util.List;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.CompilerDirectives;

public class UnicodeProperties {

    private static final String[] OTHER_PROPERTIES_NAMES = {
                    "OAlpha",  // Other_Alphabetic
                    "OLower",  // Other_Lowercase
                    "OUpper",  // Other_Uppercase
                    "OIDC",    // Other_ID_Continue
                    "OIDS"     // Other_ID_Start
    };
    /**
     * These properties are only exposed in the Java flavor.
     */
    private static final EconomicSet<String> OTHER_PROPERTIES_NAMES_SET = EconomicSet.create(OTHER_PROPERTIES_NAMES.length);
    static {
        OTHER_PROPERTIES_NAMES_SET.addAll(List.of(OTHER_PROPERTIES_NAMES));
    }

    /**
     * Match all unicode property names in case-insensitive mode.
     */
    public static final int CASE_INSENSITIVE = 1;
    /**
     * Expose {@code blk=} unicode block ranges.
     */
    public static final int BLOCKS = 1 << 1;
    /**
     * Expose "Other" unicode properties, see {@code OTHER_PROPERTIES_NAMES}.
     */
    public static final int OTHER_PROPERTIES = 1 << 2;

    private final UnicodePropertyData data;
    private final int flags;

    public UnicodeProperties(UnicodePropertyData data, int flags) {
        this.data = data;
        this.flags = flags;
    }

    private boolean isFlagSet(int flag) {
        return (flags & flag) != 0;
    }

    private boolean isCaseInsensitive() {
        return isFlagSet(CASE_INSENSITIVE);
    }

    private boolean withBlocks() {
        return isFlagSet(BLOCKS);
    }

    private boolean withOtherProperties() {
        return isFlagSet(OTHER_PROPERTIES);
    }

    public CodePointSet getProperty(String propertySpec) {
        return evaluatePropertySpec(normalizePropertySpec(propertySpec));
    }

    public ClassSetContents getPropertyOfStrings(String propertySpec) {
        return evaluatePropertySpecStrings(normalizePropertySpec(propertySpec));
    }

    /**
     * @param propertySpec *Normalized* Unicode character property specification (i.e. only
     *            abbreviated properties and property values)
     */
    private CodePointSet evaluatePropertySpec(String propertySpec) {
        CodePointSet prop = data.retrieveProperty(propertySpec);
        if (prop == null) {
            throw new IllegalArgumentException("Unsupported Unicode character property escape");
        }
        return prop;
    }

    /**
     * @param propertySpec *Normalized* Unicode character property specification (i.e. only
     *            abbreviated properties and property values)
     */
    private ClassSetContents evaluatePropertySpecStrings(String propertySpec) {
        ClassSetContents prop = data.retrievePropertyOfStrings(propertySpec);
        if (prop == null) {
            throw new IllegalArgumentException("Unsupported Unicode character property escape");
        }
        return prop;
    }

    private String normalizePropertySpec(String propertySpec) {
        int equals = propertySpec.indexOf('=');
        if (equals >= 0) {
            String propertyName = normalizePropertyName(propertySpec.substring(0, equals));
            String propertyValue = propertySpec.substring(equals + 1);
            switch (propertyName) {
                case "blk":
                    propertyValue = normalizeBlockName(propertyValue);
                    break;
                case "gc":
                    propertyValue = normalizeGeneralCategoryName(propertyValue);
                    break;
                case "sc":
                case "scx":
                    propertyValue = normalizeScriptName(propertyValue);
                    break;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalArgumentException(String.format("Binary property %s cannot appear to the left of '=' in a Unicode property escape", propertySpec.substring(0, equals)));
            }
            return propertyName + "=" + propertyValue;
        } else if (isSupportedGeneralCategory(propertySpec)) {
            return "gc=" + normalizeGeneralCategoryName(propertySpec);
        } else {
            return normalizePropertyName(propertySpec);
        }
    }

    private String normalizePropertyName(String propertyName) {
        String name = returnOrThrow(propertyName, "character property", data.lookupPropertyAlias(propertyName, isCaseInsensitive()));
        if (!withOtherProperties() && OTHER_PROPERTIES_NAMES_SET.contains(name)) {
            throw new IllegalArgumentException(String.format("Unsupported Unicode character property '%s'", propertyName));
        }
        return name;
    }

    private String normalizeGeneralCategoryName(String generalCategoryName) {
        return returnOrThrow(generalCategoryName, "character general category", data.lookupGeneralCategoryAlias(generalCategoryName, isCaseInsensitive()));
    }

    private String normalizeScriptName(String scriptName) {
        return returnOrThrow(scriptName, "script name", data.lookupScriptAlias(scriptName, isCaseInsensitive()));
    }

    private String normalizeBlockName(String blockName) {
        if (!withBlocks()) {
            throw new IllegalArgumentException("Unsupported Unicode character property escape");
        }
        return returnOrThrow(blockName, "block name", data.lookupBlockAlias(blockName, isCaseInsensitive()));
    }

    public boolean isSupportedProperty(String propertyName) {
        return data.lookupPropertyAlias(propertyName, isCaseInsensitive()) != null;
    }

    public boolean isSupportedGeneralCategory(String generalCategoryName) {
        return data.lookupGeneralCategoryAlias(generalCategoryName, isCaseInsensitive()) != null;
    }

    public boolean isSupportedScript(String scriptName) {
        return data.lookupScriptAlias(scriptName, isCaseInsensitive()) != null;
    }

    public boolean isSupportedBlock(String blockName) {
        assert withBlocks();
        return data.lookupBlockAlias(blockName, isCaseInsensitive()) != null;
    }

    private static String returnOrThrow(String propertyName, String errorName, String name) {
        if (name == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException(String.format("Unsupported Unicode %s '%s'", errorName, propertyName));
        }
        return name;
    }

}
