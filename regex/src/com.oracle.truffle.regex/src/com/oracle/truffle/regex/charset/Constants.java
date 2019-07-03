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

public final class Constants {

    public static final CodePointSet BMP_RANGE = CodePointSet.createNoDedup(Character.MIN_VALUE, Character.MAX_VALUE);
    public static final CodePointSet TRAIL_SURROGATE_RANGE = CodePointSet.createNoDedup(0xDC00, 0xDFFF);

    public static final CodePointSet BMP_WITHOUT_SURROGATES = CodePointSet.createNoDedup(
                    0x0000, 0xd7ff,
                    0xe000, 0xffff);

    public static final CodePointSet ASTRAL_SYMBOLS = CodePointSet.createNoDedup(0x10000, 0x10ffff);

    public static final CodePointSet LEAD_SURROGATES = CodePointSet.createNoDedup(0xd800, 0xdbff);

    public static final CodePointSet TRAIL_SURROGATES = CodePointSet.createNoDedup(0xdc00, 0xdfff);

    public static final CodePointSet DIGITS = CodePointSet.createNoDedup('0', '9');

    // inverse of DIGITS
    public static final CodePointSet NON_DIGITS = CodePointSet.createNoDedup(
                    0x0000, 0x002f,
                    0x003a, 0x10ffff);

    // [A-Za-z0-9_]
    public static final CodePointSet WORD_CHARS = CodePointSet.createNoDedup(
                    0x0030, 0x0039,
                    0x0041, 0x005a,
                    0x005f, 0x005f,
                    0x0061, 0x007a);

    // inverse of WORD_CHARS
    public static final CodePointSet NON_WORD_CHARS = CodePointSet.createNoDedup(
                    0x0000, 0x002f,
                    0x003a, 0x0040,
                    0x005b, 0x005e,
                    0x0060, 0x0060,
                    0x007b, 0x10ffff);

    // If we want to store negations of basic character classes, then we also need to store their
    // case-folded variants because one must apply case-folding *before* inverting the character
    // class. The WORD_CHARS (\w) character class is the only one of the basic classes (\w, \d, \s)
    // that is affected by case-folding and only so when both the Unicode and IgnoreCase flags are
    // set.
    public static final CodePointSet WORD_CHARS_UNICODE_IGNORE_CASE = CodePointSet.createNoDedup(
                    0x0030, 0x0039,
                    0x0041, 0x005a,
                    0x005f, 0x005f,
                    0x0061, 0x007a,
                    0x017f, 0x017f,
                    0x212a, 0x212a);

    public static final CodePointSet NON_WORD_CHARS_UNICODE_IGNORE_CASE = CodePointSet.createNoDedup(
                    0x0000, 0x002f,
                    0x003a, 0x0040,
                    0x005b, 0x005e,
                    0x0060, 0x0060,
                    0x007b, 0x017e,
                    0x0180, 0x2129,
                    0x212b, 0x10ffff);

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
    public static final CodePointSet WHITE_SPACE = CodePointSet.createNoDedup(
                    0x0009, 0x000d,
                    0x0020, 0x0020,
                    0x00a0, 0x00a0,
                    0x1680, 0x1680,
                    0x2000, 0x200a,
                    0x2028, 0x2029,
                    0x202f, 0x202f,
                    0x205f, 0x205f,
                    0x3000, 0x3000,
                    0xfeff, 0xfeff);

    // inverse of WHITE_SPACE
    public static final CodePointSet NON_WHITE_SPACE = CodePointSet.createNoDedup(
                    0x0000, 0x0008,
                    0x000e, 0x001f,
                    0x0021, 0x009f,
                    0x00a1, 0x167f,
                    0x1681, 0x1fff,
                    0x200b, 0x2027,
                    0x202a, 0x202e,
                    0x2030, 0x205e,
                    0x2060, 0x2fff,
                    0x3001, 0xfefe,
                    0xff00, 0x10ffff);

    // Equal to WHITE_SPACE plus 0x180E.
    // In versions of Unicode older than 6.3, 0x180E MONGOLIAN VOWEL SEPARATOR is also part of
    // the 'Zs' category, and therefore considered WhiteSpace. Such versions of Unicode are used by
    // ECMAScript 6 and older.
    public static final CodePointSet LEGACY_WHITE_SPACE = CodePointSet.createNoDedup(
                    0x0009, 0x000d,
                    0x0020, 0x0020,
                    0x00a0, 0x00a0,
                    0x1680, 0x1680,
                    0x180e, 0x180e,
                    0x2000, 0x200a,
                    0x2028, 0x2029,
                    0x202f, 0x202f,
                    0x205f, 0x205f,
                    0x3000, 0x3000,
                    0xfeff, 0xfeff);

    // inverse of LEGACY_WHITE_SPACE
    public static final CodePointSet LEGACY_NON_WHITE_SPACE = CodePointSet.createNoDedup(
                    0x0000, 0x0008,
                    0x000e, 0x001f,
                    0x0021, 0x009f,
                    0x00a1, 0x167f,
                    0x1681, 0x180d,
                    0x180f, 0x1fff,
                    0x200b, 0x2027,
                    0x202a, 0x202e,
                    0x2030, 0x205e,
                    0x2060, 0x2fff,
                    0x3001, 0xfefe,
                    0xff00, 0x10ffff);

    // \r, \n, 0x2028, 0x2029
    public static final CodePointSet LINE_TERMINATOR = CodePointSet.createNoDedup(
                    0x000a, 0x000a,
                    0x000d, 0x000d,
                    0x2028, 0x2029);

    // inverse of LINE_TERMINATOR
    public static final CodePointSet DOT = CodePointSet.createNoDedup(
                    0x0000, 0x0009,
                    0x000b, 0x000c,
                    0x000e, 0x2027,
                    0x202a, 0x10ffff);

    public static final CodePointSet DOT_ALL = CodePointSet.createNoDedup(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);

    // [A-Fa-f0-9]
    public static final CodePointSet HEX_CHARS = CodePointSet.createNoDedup(
                    0x0030, 0x0039,
                    0x0041, 0x0046,
                    0x0061, 0x0066);

    /**
     * Used for deduplication in {@link CodePointSet} and {@link CharSet}.
     */
    public static final CodePointSet[] CONSTANT_CODE_POINT_SETS = new CodePointSet[]{
                    DIGITS,
                    NON_DIGITS,
                    WORD_CHARS,
                    NON_WORD_CHARS,
                    WHITE_SPACE,
                    NON_WHITE_SPACE,
                    LINE_TERMINATOR,
                    DOT,
                    HEX_CHARS
    };
}
