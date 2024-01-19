/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.tregex.string.Encodings;

class JavaCharacter {

    // Character::isIdeographic
    static final CodePointSet IDEOGRAPHIC = JavaPropList.IDEOGRAPHIC;

    // Character::isDefined
    static final CodePointSet DEFINED = JavaGc.UNASSIGNED.createInverse(Encodings.UTF_16);

    // Character::isWhitespace (list from documentation)
    static final CodePointSet WHITESPACE = CodePointSet.createNoDedup(
                    0x9, 0xd,
                    0x1c, 0x20,
                    0x1680, 0x1680,
                    0x2000, 0x2006,
                    0x2008, 0x200a,
                    0x2028, 0x2029,
                    0x205f, 0x205f,
                    0x3000, 0x3000);

    // Character::isMirrored (unclear source, extracted by bruteforce)
    static final CodePointSet MIRRORED = CodePointSet.createNoDedup(
                    0x28, 0x29,
                    0x3c, 0x3c,
                    0x3e, 0x3e,
                    0x5b, 0x5b,
                    0x5d, 0x5d,
                    0x7b, 0x7b,
                    0x7d, 0x7d,
                    0xab, 0xab,
                    0xbb, 0xbb,
                    0xf3a, 0xf3d,
                    0x169b, 0x169c,
                    0x2039, 0x203a,
                    0x2045, 0x2046,
                    0x207d, 0x207e,
                    0x208d, 0x208e,
                    0x2140, 0x2140,
                    0x2201, 0x2204,
                    0x2208, 0x220d,
                    0x2211, 0x2211,
                    0x2215, 0x2216,
                    0x221a, 0x221d,
                    0x221f, 0x2222,
                    0x2224, 0x2224,
                    0x2226, 0x2226,
                    0x222b, 0x2233,
                    0x2239, 0x2239,
                    0x223b, 0x224c,
                    0x2252, 0x2255,
                    0x225f, 0x2260,
                    0x2262, 0x2262,
                    0x2264, 0x226b,
                    0x226e, 0x228c,
                    0x228f, 0x2292,
                    0x2298, 0x2298,
                    0x22a2, 0x22a3,
                    0x22a6, 0x22b8,
                    0x22be, 0x22bf,
                    0x22c9, 0x22cd,
                    0x22d0, 0x22d1,
                    0x22d6, 0x22ed,
                    0x22f0, 0x22ff,
                    0x2308, 0x230b,
                    0x2320, 0x2321,
                    0x2329, 0x232a,
                    0x2768, 0x2775,
                    0x27c0, 0x27c0,
                    0x27c3, 0x27c6,
                    0x27c8, 0x27c9,
                    0x27cb, 0x27cd,
                    0x27d3, 0x27d6,
                    0x27dc, 0x27de,
                    0x27e2, 0x27ef,
                    0x2983, 0x2998,
                    0x299b, 0x29a0,
                    0x29a2, 0x29af,
                    0x29b8, 0x29b8,
                    0x29c0, 0x29c5,
                    0x29c9, 0x29c9,
                    0x29ce, 0x29d2,
                    0x29d4, 0x29d5,
                    0x29d8, 0x29dc,
                    0x29e1, 0x29e1,
                    0x29e3, 0x29e5,
                    0x29e8, 0x29e9,
                    0x29f4, 0x29f9,
                    0x29fc, 0x29fd,
                    0x2a0a, 0x2a1c,
                    0x2a1e, 0x2a21,
                    0x2a24, 0x2a24,
                    0x2a26, 0x2a26,
                    0x2a29, 0x2a29,
                    0x2a2b, 0x2a2e,
                    0x2a34, 0x2a35,
                    0x2a3c, 0x2a3e,
                    0x2a57, 0x2a58,
                    0x2a64, 0x2a65,
                    0x2a6a, 0x2a6d,
                    0x2a6f, 0x2a70,
                    0x2a73, 0x2a74,
                    0x2a79, 0x2aa3,
                    0x2aa6, 0x2aad,
                    0x2aaf, 0x2ad6,
                    0x2adc, 0x2adc,
                    0x2ade, 0x2ade,
                    0x2ae2, 0x2ae6,
                    0x2aec, 0x2aee,
                    0x2af3, 0x2af3,
                    0x2af7, 0x2afb,
                    0x2afd, 0x2afd,
                    0x2bfe, 0x2bfe,
                    0x2e02, 0x2e05,
                    0x2e09, 0x2e0a,
                    0x2e0c, 0x2e0d,
                    0x2e1c, 0x2e1d,
                    0x2e20, 0x2e29,
                    0x2e55, 0x2e5c,
                    0x3008, 0x3011,
                    0x3014, 0x301b,
                    0xfe59, 0xfe5e,
                    0xfe64, 0xfe65,
                    0xff08, 0xff09,
                    0xff1c, 0xff1c,
                    0xff1e, 0xff1e,
                    0xff3b, 0xff3b,
                    0xff3d, 0xff3d,
                    0xff5b, 0xff5b,
                    0xff5d, 0xff5d,
                    0xff5f, 0xff60,
                    0xff62, 0xff63,
                    0x1d6db, 0x1d6db,
                    0x1d715, 0x1d715,
                    0x1d74f, 0x1d74f,
                    0x1d789, 0x1d789,
                    0x1d7c3, 0x1d7c3);

    // Character::isIsoControl (list from documentation)
    static final CodePointSet ISO_CONTROL = CodePointSet.createNoDedup(
                    0x0, 0x1f,
                    0x7f, 0x9f);

    // Character::isIdentifierIgnorable (list from documentation)
    static final CodePointSet IDENTIFIER_IGNORABLE = CodePointSet.createNoDedup(
                    0x0000, 0x0008,
                    0x000E, 0x001B,
                    0x007F, 0x009F).union(JavaGc.FORMAT);

    // Character::isSpaceChar
    static final CodePointSet SPACE_CHAR = JavaGc.SPACE_SEPARATOR.union(JavaGc.LINE_SEPARATOR).union(JavaGc.PARAGRAPH_SEPARATOR);

    // Character::isLowerCase
    static final CodePointSet LOWER_CASE = JavaGc.LOWERCASE_LETTER.union(JavaPropList.OTHER_LOWERCASE);

    // Character::isUpperCase
    static final CodePointSet UPPER_CASE = JavaGc.UPPERCASE_LETTER.union(JavaPropList.OTHER_UPPERCASE);

    // Character::isTitleCase
    static final CodePointSet TITLE_CASE = JavaGc.TITLECASE_LETTER;

    // Character::isAlphabetic
    static final CodePointSet ALPHABETIC = JavaGc.UPPERCASE_LETTER.union(JavaGc.LOWERCASE_LETTER).union(JavaGc.TITLECASE_LETTER).union(JavaGc.MODIFIER_LETTER).union(JavaGc.OTHER_LETTER).union(
                    JavaGc.LETTER_NUMBER).union(JavaPropList.OTHER_ALPHABETIC);

    // Character::isDigit
    static final CodePointSet DIGIT = JavaGc.DECIMAL_DIGIT_NUMBER;

    // Character::isLetter
    static final CodePointSet LETTER = JavaGc.UPPERCASE_LETTER.union(JavaGc.LOWERCASE_LETTER).union(JavaGc.TITLECASE_LETTER).union(JavaGc.MODIFIER_LETTER).union(JavaGc.OTHER_LETTER);

    // Character::isLetterOrDigit
    static final CodePointSet LETTER_OR_DIGIT = LETTER.union(DIGIT);

    // Character::isJavaIdentifierStart
    static final CodePointSet JAVA_IDENTIFIER_START = LETTER.union(JavaGc.LETTER_NUMBER).union(JavaGc.CURRENCY_SYMBOL).union(JavaGc.CONNECTOR_PUNCTUATION);

    // Character::isJavaIdentifierPart
    static final CodePointSet JAVA_IDENTIFIER_PART = LETTER.union(JavaGc.CURRENCY_SYMBOL).union(JavaGc.CONNECTOR_PUNCTUATION).union(DIGIT).union(JavaGc.LETTER_NUMBER).union(
                    JavaGc.COMBINING_SPACING_MARK).union(JavaGc.NON_SPACING_MARK).union(IDENTIFIER_IGNORABLE);

    // Character::isJavaIdentifierStart
    static final CodePointSet UNICODE_IDENTIFIER_START = LETTER.union(JavaGc.LETTER_NUMBER).union(JavaPropList.OTHER_ID_START);

    // Character::isUnicodeIdentifierPart
    static final CodePointSet UNICODE_IDENTIFIER_PART = LETTER.union(JavaGc.CONNECTOR_PUNCTUATION).union(DIGIT).union(JavaGc.LETTER_NUMBER).union(JavaGc.COMBINING_SPACING_MARK).union(
                    JavaGc.NON_SPACING_MARK).union(IDENTIFIER_IGNORABLE).union(JavaPropList.OTHER_ID_START).union(JavaPropList.OTHER_ID_CONTINUE);

}
