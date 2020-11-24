/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicMap;

/**
 * This class hosts lookup tables for case insensitive Unicode properties. Those are used by the
 * Ruby flavor, which does case insensitive matching on property names. This class was created in
 * order to separate the data from {@link UnicodePropertyData}, which is machine generated, and from
 * {@link UnicodeProperties}, so that the tables are filled in only when needed (i.e. when this
 * class is accessed).
 */
class UnicodePropertyDataRuby {
    static final EconomicMap<String, String> PROPERTY_ALIASES_LOWERCASE = EconomicMap.create(UnicodePropertyData.PROPERTY_ALIASES.size());
    static final EconomicMap<String, String> GENERAL_CATEGORY_ALIASES_LOWERCASE = EconomicMap.create(UnicodePropertyData.GENERAL_CATEGORY_ALIASES.size());
    static final EconomicMap<String, String> SCRIPT_ALIASES_LOWERCASE = EconomicMap.create(UnicodePropertyData.SCRIPT_ALIASES.size());

    static {
        for (String propertyAlias : UnicodePropertyData.PROPERTY_ALIASES.getKeys()) {
            PROPERTY_ALIASES_LOWERCASE.put(propertyAlias.toLowerCase(), propertyAlias);
        }
        for (String generalCategoryAlias : UnicodePropertyData.GENERAL_CATEGORY_ALIASES.getKeys()) {
            GENERAL_CATEGORY_ALIASES_LOWERCASE.put(generalCategoryAlias.toLowerCase(), generalCategoryAlias);
        }
        for (String scriptAlias : UnicodePropertyData.SCRIPT_ALIASES.getKeys()) {
            SCRIPT_ALIASES_LOWERCASE.put(scriptAlias.toLowerCase(), scriptAlias);
        }
    }
}
