/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.chardata;

import com.oracle.truffle.regex.tregex.parser.CaseFoldTable;

public class Constants {

    public static final int MIN_CODEPOINT = 0;
    public static final int MAX_CODEPOINT = 0x10FFFF;

    public static final CodePointRange BMP_RANGE = new CodePointRange(MIN_CODEPOINT, 0xFFFF);
    public static final CodePointRange ASTRAL_RANGE = new CodePointRange(0x10000, MAX_CODEPOINT);

    public static final CodePointRange LEAD_SURROGATE_RANGE = new CodePointRange(0xD800, 0xDBFF);
    public static final CodePointRange TRAIL_SURROGATE_RANGE = new CodePointRange(0xDC00, 0xDFFF);

    public static final CodePointRange BMP_BEFORE_SURROGATES_RANGE = new CodePointRange(BMP_RANGE.lo, LEAD_SURROGATE_RANGE.lo - 1);
    public static final CodePointRange BMP_AFTER_SURROGATES_RANGE = new CodePointRange(TRAIL_SURROGATE_RANGE.hi + 1, BMP_RANGE.hi);

    public static final CodePointSet BMP_WITHOUT_SURROGATES = CodePointSet.create(BMP_BEFORE_SURROGATES_RANGE, BMP_AFTER_SURROGATES_RANGE).freeze();
    public static final CodePointSet ASTRAL_SYMBOLS = CodePointSet.create(ASTRAL_RANGE).freeze();
    public static final CodePointSet LEAD_SURROGATES = CodePointSet.create(LEAD_SURROGATE_RANGE).freeze();
    public static final CodePointSet TRAIL_SURROGATES = CodePointSet.create(TRAIL_SURROGATE_RANGE).freeze();

    public static final CodePointSet DIGITS = CodePointSet.create(new CodePointRange('0', '9')).freeze();
    public static final CodePointSet NON_DIGITS = DIGITS.createInverse().freeze();
    public static final CodePointSet WORD_CHARS = CodePointSet.create(
                    new CodePointRange('a', 'z'),
                    new CodePointRange('A', 'Z'),
                    new CodePointRange('0', '9'),
                    new CodePointRange('_')).freeze();
    public static final CodePointSet NON_WORD_CHARS = WORD_CHARS.createInverse().freeze();
    // If we want to store negations of basic character classes, then we also need to store their
    // case-folded variants because one must apply case-folding *before* inverting the character
    // class. The WORD_CHARS (\w) character class is the only one of the basic classes (\w, \d, \s)
    // that is affected by case-folding and only so when both the Unicode and IgnoreCase flags are
    // set.
    public static final CodePointSet WORD_CHARS_UNICODE_IGNORE_CASE = CaseFoldTable.applyCaseFold(WORD_CHARS, true).freeze();
    public static final CodePointSet NON_WORD_CHARS_UNICODE_IGNORE_CASE = WORD_CHARS_UNICODE_IGNORE_CASE.createInverse().freeze();

    // WhiteSpace defined in ECMA-262 2018 11.2
    // 0x0009, CHARACTER TABULATION, <TAB>
    // 0x000B, LINE TABULATION, <VT>
    // 0x000C, FORM FEED (FF), <FF>
    // 0x0020, SPACE, <SP>
    // 0x00A0, NO-BREAK SPACE, <NBSP>
    // 0xFEFF, ZERO WIDTH NO-BREAK SPACE, <ZWNBSP>
    // Unicode 10.0 whitespaces (category 'Zs')
    // 0x0020, SPACE
    // 0x00A0, NO-BREAK SPACE
    // 0x1680, OGHAM SPACE MARK
    // 0x2000, EN QUAD
    // 0x2001, EM QUAD
    // 0x2002, EN SPACE
    // 0x2003, EM SPACE
    // 0x2004, THREE-PER-EM SPACE
    // 0x2005, FOUR-PER-EM SPACE
    // 0x2006, SIX-PER-EM SPACE
    // 0x2007, FIGURE SPACE
    // 0x2008, PUNCTUATION SPACE
    // 0x2009, THIN SPACE
    // 0x200A, HAIR SPACE
    // 0x202F, NARROW NO-BREAK SPACE
    // 0x205F, MEDIUM MATHEMATICAL SPACE
    // 0x3000, IDEOGRAPHIC SPACE
    // LineTerminator defined in ECMA-262 2018 11.3
    // 0x000A, LINE FEED (LF), <LF>
    // 0x000D, CARRIAGE RETURN (CR), <CR>
    // 0x2028, LINE SEPARATOR, <LS>
    // 0x2029, PARAGRAPH SEPARATOR, <PS>
    public static final CodePointSet WHITE_SPACE = CodePointSet.create(
                    new CodePointRange('\t', '\r'),
                    new CodePointRange(' '),
                    new CodePointRange('\u00a0'),
                    new CodePointRange('\u1680'),
                    new CodePointRange('\u2000', '\u200a'),
                    new CodePointRange('\u2028', '\u2029'),
                    new CodePointRange('\u202f'),
                    new CodePointRange('\u205f'),
                    new CodePointRange('\u3000'),
                    new CodePointRange('\ufeff')).freeze();
    public static final CodePointSet NON_WHITE_SPACE = WHITE_SPACE.createInverse().freeze();

    // In versions of Unicode older than 6.3, 0x180E MONGOLIAN VOWEL SEPARATOR is also part of
    // the 'Zs' category, and therefore considered WhiteSpace. Such versions of Unicode are used by
    // ECMAScript 6 and older.
    public static final CodePointSet LEGACY_WHITE_SPACE = WHITE_SPACE.copy().addRange(new CodePointRange('\u180e')).freeze();
    public static final CodePointSet LEGACY_NON_WHITE_SPACE = LEGACY_WHITE_SPACE.createInverse().freeze();

    public static final CodePointSet LINE_TERMINATOR = CodePointSet.create(
                    new CodePointRange('\n'),
                    new CodePointRange('\r'),
                    new CodePointRange('\u2028', '\u2029')).freeze();
    public static final CodePointSet DOT = LINE_TERMINATOR.createInverse().freeze();
    public static final CodePointSet DOT_ALL = CodePointSet.create(new CodePointRange(MIN_CODEPOINT, MAX_CODEPOINT)).freeze();

    public static final CodePointSet HEX_CHARS = CodePointSet.create(
                    new CodePointRange('0', '9'),
                    new CodePointRange('A', 'F'),
                    new CodePointRange('a', 'f')).freeze();
}
