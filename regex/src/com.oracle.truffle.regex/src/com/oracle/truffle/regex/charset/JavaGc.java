/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

// Sets for Unicode general categories
class JavaGc {
    static final CodePointSet UNASSIGNED = category("Cn");
    static final CodePointSet UPPERCASE_LETTER = category("Lu");
    static final CodePointSet LOWERCASE_LETTER = category("Ll");
    static final CodePointSet TITLECASE_LETTER = category("Lt");
    static final CodePointSet MODIFIER_LETTER = category("Lm");
    static final CodePointSet OTHER_LETTER = category("Lo");
    static final CodePointSet NON_SPACING_MARK = category("Mn");
    static final CodePointSet ENCLOSING_MARK = category("Me");
    static final CodePointSet COMBINING_SPACING_MARK = category("Mc");
    static final CodePointSet DECIMAL_DIGIT_NUMBER = category("Nd");
    static final CodePointSet LETTER_NUMBER = category("Nl");
    static final CodePointSet OTHER_NUMBER = category("No");
    static final CodePointSet SPACE_SEPARATOR = category("Zs");
    static final CodePointSet LINE_SEPARATOR = category("Zl");
    static final CodePointSet PARAGRAPH_SEPARATOR = category("Zp");
    static final CodePointSet CONTROL = category("Cc");
    static final CodePointSet FORMAT = category("Cf");
    static final CodePointSet PRIVATE_USE = category("Co");
    static final CodePointSet SURROGATE = category("Cs");
    static final CodePointSet DASH_PUNCTUATION = category("Pd");
    static final CodePointSet START_PUNCTUATION = category("Ps");
    static final CodePointSet END_PUNCTUATION = category("Pe");
    static final CodePointSet CONNECTOR_PUNCTUATION = category("Pc");
    static final CodePointSet OTHER_PUNCTUATION = category("Po");
    static final CodePointSet MATH_SYMBOL = category("Sm");
    static final CodePointSet CURRENCY_SYMBOL = category("Sc");
    static final CodePointSet MODIFIER_SYMBOL = category("Sk");
    static final CodePointSet OTHER_SYMBOL = category("So");
    static final CodePointSet INITIAL_QUOTE_PUNCTUATION = category("Pi");
    static final CodePointSet FINAL_QUOTE_PUNCTUATION = category("Pf");

    public static CodePointSet category(String name) {
        return UnicodePropertyData.retrieveProperty("gc=" + name);
    }
}
