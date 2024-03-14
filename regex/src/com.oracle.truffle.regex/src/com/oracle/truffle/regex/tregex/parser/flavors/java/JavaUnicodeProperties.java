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
package com.oracle.truffle.regex.tregex.parser.flavors.java;

import java.util.Locale;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.charset.UnicodeProperties;
import com.oracle.truffle.regex.charset.UnicodePropertyData;
import com.oracle.truffle.regex.charset.UnicodePropertyDataVersion;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.string.Encodings;

final class JavaUnicodeProperties {

    private static final JavaUnicodeProperties[] CACHE = new JavaUnicodeProperties[(RegexOptions.JAVA_JDK_VERSION_MAX - RegexOptions.JAVA_JDK_VERSION_MIN) + 1];

    // 0x000A, LINE FEED (LF), <LF>
    // 0x000D, CARRIAGE RETURN (CR), <CR>
    // 0x0085, NEXT LINE (NEL), <NEL>
    // 0x2028, LINE SEPARATOR, <LS>
    // 0x2029, PARAGRAPH SEPARATOR, <PS>
    public static final CodePointSet JAVA_LINE_TERMINATOR = CodePointSet.createNoDedup(
                    0x000a, 0x000a,
                    0x000d, 0x000d,
                    0x0085, 0x0085,
                    0x2028, 0x2029);
    // inverse of JAVA_LINE_TERMINATOR
    public static final CodePointSet DOT = CodePointSet.createNoDedup(
                    0x0000, 0x0009,
                    0x000b, 0x000c,
                    0x000e, 0x0084,
                    0x0086, 0x2027,
                    0x202a, 0x10ffff);
    // all characters except \n
    public static final CodePointSet DOT_UNIX = CodePointSet.createNoDedup(
                    0x0000, 0x0009,
                    0x000b, 0x10ffff);

    // Character::isWhitespace (list from documentation)
    static final CodePointSet WHITESPACE = CodePointSet.createNoDedup(
                    0x0009, 0x000d,
                    0x001c, 0x0020,
                    0x1680, 0x1680,
                    0x2000, 0x2006,
                    0x2008, 0x200a,
                    0x2028, 0x2029,
                    0x205f, 0x205f,
                    0x3000, 0x3000);

    // Character::isIsoControl (list from documentation)
    static final CodePointSet ISO_CONTROL = CodePointSet.createNoDedup(0x0, 0x1f, 0x7f, 0x9f);

    final UnicodeProperties unicode;

    // Character::isDefined
    final CodePointSet defined;

    // Character::isIdentifierIgnorable (list from documentation)
    final CodePointSet identifierIgnorable;

    // Character::isSpaceChar
    final CodePointSet spaceChar;

    // Character::isLowerCase
    final CodePointSet lowerCase;

    // Character::isUpperCase
    final CodePointSet upperCase;

    final CodePointSet lowerUpperTitleCase;

    // Character::isAlphabetic
    final CodePointSet alphabetic;

    // Character::isLetterOrDigit
    final CodePointSet letterOrDigit;

    // Character::isJavaIdentifierStart
    final CodePointSet javaIdentifierStart;

    // Character::isJavaIdentifierPart
    final CodePointSet javaIdentifierPart;

    // Character::isJavaIdentifierStart
    final CodePointSet unicodeIdentifierStart;

    // Character::isUnicodeIdentifierPart
    final CodePointSet unicodeIdentifierPart;

    final CodePointSet unicodeLetterOrDigit;
    final CodePointSet blank;
    final CodePointSet graph;
    final CodePointSet whiteSpace;

    final CodePointSet digit;
    final CodePointSet nonDigit;
    final CodePointSet word;
    final CodePointSet nonWord;
    final CodePointSet space;
    final CodePointSet nonSpace;

    private JavaUnicodeProperties(UnicodeProperties unicode) {
        this.unicode = unicode;
        defined = unicode.getProperty("General_Category=Unassigned").createInverse(Encodings.UTF_16);

        identifierIgnorable = CodePointSet.createNoDedup(0x0000, 0x0008, 0x000E, 0x001B, 0x007F, 0x009F).union(unicode.getProperty("General_Category=Format"));

        spaceChar = unionOfProperties(
                        "General_Category=Space_Separator",
                        "General_Category=Line_Separator",
                        "General_Category=Paragraph_Separator");

        // Character::isLowerCase
        lowerCase = unionOfProperties(
                        "General_Category=Lowercase_Letter",
                        "Other_Lowercase");

        upperCase = unionOfProperties(
                        "General_Category=Uppercase_Letter",
                        "Other_Uppercase");

        lowerUpperTitleCase = unionOfProperties(lowerCase.union(upperCase),
                        "General_Category=Titlecase_Letter");

        CodePointSet letterLetterNumber = unionOfProperties(
                        "General_Category=Letter",
                        "General_Category=Letter_Number");

        alphabetic = unionOfProperties(letterLetterNumber,
                        "Other_Alphabetic");

        letterOrDigit = unionOfProperties(
                        "General_Category=Letter",
                        "General_Category=Decimal_Number");

        javaIdentifierStart = unionOfProperties(letterLetterNumber,
                        "General_Category=Currency_Symbol",
                        "General_Category=Connector_Punctuation");

        javaIdentifierPart = unionOfProperties(javaIdentifierStart.union(identifierIgnorable),
                        "General_Category=Decimal_Number",
                        "General_Category=Spacing_Mark",
                        "General_Category=Nonspacing_Mark");

        unicodeIdentifierStart = unionOfProperties(letterLetterNumber,
                        "Other_ID_Start");

        unicodeIdentifierPart = unionOfProperties(unicodeIdentifierStart.union(identifierIgnorable),
                        "General_Category=Connector_Punctuation",
                        "General_Category=Decimal_Number",
                        "General_Category=Spacing_Mark",
                        "General_Category=Nonspacing_Mark",
                        "Other_ID_Continue");

        unicodeLetterOrDigit = unionOfProperties(
                        "General_Category=Lowercase_Letter",
                        "General_Category=Uppercase_Letter",
                        "General_Category=Titlecase_Letter",
                        "General_Category=Modifier_Letter",
                        "General_Category=Other_Letter",
                        "General_Category=Decimal_Number");

        blank = unicode.getProperty("General_Category=Space_Separator").union(CodePointSet.create(0x9));
        graph = unionOfProperties(
                        "General_Category=Space_Separator",
                        "General_Category=Line_Separator",
                        "General_Category=Paragraph_Separator",
                        "General_Category=Control",
                        "General_Category=Surrogate",
                        "General_Category=Unassigned").createInverse(Encodings.UTF_16);

        whiteSpace = unionOfProperties(CodePointSet.createNoDedup(0x9, 0xd, 0x85, 0x85),
                        "General_Category=Space_Separator",
                        "General_Category=Line_Separator",
                        "General_Category=Paragraph_Separator");

        digit = unicode.getProperty("General_Category=Decimal_Number");
        nonDigit = digit.createInverse(Encodings.UTF_16);

        word = unionOfProperties(alphabetic, "Join_Control",
                        "General_Category=Nonspacing_Mark",
                        "General_Category=Enclosing_Mark",
                        "General_Category=Spacing_Mark",
                        "General_Category=Decimal_Number",
                        "General_Category=Connector_Punctuation");
        nonWord = word.createInverse(Encodings.UTF_16);

        space = unicode.getProperty("WSpace");
        nonSpace = space.createInverse(Encodings.UTF_16);
    }

    static JavaUnicodeProperties create(RegexOptions options) {
        int jdkVersion = options.getJavaJDKVersion();
        int cacheIndex = jdkVersion - RegexOptions.JAVA_JDK_VERSION_MIN;
        JavaUnicodeProperties cached = CACHE[cacheIndex];
        if (cached != null) {
            return cached;
        }
        UnicodeProperties unicode = new UnicodeProperties(getUnicodePropertyData(jdkVersion), UnicodeProperties.CASE_INSENSITIVE | UnicodeProperties.BLOCKS | UnicodeProperties.OTHER_PROPERTIES);
        JavaUnicodeProperties ret = new JavaUnicodeProperties(unicode);
        CACHE[cacheIndex] = ret;
        return ret;
    }

    private static UnicodePropertyData getUnicodePropertyData(int jdkVersion) {
        switch (jdkVersion) {
            case 21:
                return UnicodePropertyDataVersion.UNICODE_15_0_0;
            case 22:
            case 23:
                return UnicodePropertyDataVersion.UNICODE_15_1_0;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private CodePointSet unionOfProperties(String... properties) {
        return unionOfProperties(null, properties);
    }

    private CodePointSet unionOfProperties(CodePointSet initial, String... properties) {
        CodePointSetAccumulator acc = new CodePointSetAccumulator();
        if (initial != null) {
            acc.addSet(initial);
        }
        for (String property : properties) {
            acc.addSet(unicode.getProperty(property));
        }
        return acc.toCodePointSet();
    }

    CodePointSet getBlock(String name) {
        String normalizedName;
        try {
            normalizedName = Character.UnicodeBlock.forName(name).toString().toLowerCase();
        } catch (IllegalArgumentException iae) {
            return null;
        }
        if (unicode.isSupportedBlock(normalizedName)) {
            return unicode.getProperty("blk=" + normalizedName);
        }
        return null;
    }

    CodePointSet getScript(String name) {
        String normalizedName;
        try {
            normalizedName = Character.UnicodeScript.forName(name).toString().toLowerCase();
        } catch (IllegalArgumentException iae) {
            return null;
        }
        if (unicode.isSupportedScript(normalizedName)) {
            return unicode.getProperty("sc=" + normalizedName);
        }
        return null;
    }

    CodePointSet forUnicodeProperty(String propName, boolean caseIns) {
        String propNameNormalized = propName.toUpperCase(Locale.ROOT);
        CodePointSet p = getUnicodePredicate(propNameNormalized, caseIns);
        if (p != null) {
            return p;
        }
        return getPosixPredicate(propNameNormalized, caseIns);
    }

    CodePointSet forPOSIXName(String propName, boolean caseIns) {
        return getPosixPredicate(propName.toUpperCase(Locale.ENGLISH), caseIns);
    }

    CodePointSet getProperty(String name, boolean caseIns) {
        // Unicode character property aliases, defined in
        // http://www.unicode.org/Public/UNIDATA/PropertyValueAliases.txt
        return switch (name) {
            case "Lu" -> unicode.getProperty("gc=" + (caseIns ? "LC" : "Lu"));
            case "Ll" -> unicode.getProperty("gc=" + (caseIns ? "LC" : "Ll"));
            case "Lt" -> unicode.getProperty("gc=" + (caseIns ? "LC" : "Lt"));
            case "C", "Cc", "Cf", "Cn", "Co", "Cs",
                            "L", "LC", "Lm", "Lo",
                            "M", "Mc", "Me", "Mn",
                            "N", "Nd", "Nl", "No",
                            "P", "Pc", "Pd", "Pe", "Pf", "Pi", "Po", "Ps",
                            "S", "Sc", "Sk", "Sm", "So",
                            "Z", "Zl", "Zp", "Zs" ->
                unicode.getProperty("gc=" + name);
            case "LD" -> unicodeLetterOrDigit;
            case "L1" -> Constants.BYTE_RANGE;
            case "all" -> Constants.DOT_ALL;
            // Posix regular expression character classes, defined in
            // http://www.unix.org/onlinepubs/009695399/basedefs/xbd_chap09.html
            case "ASCII" -> Constants.ASCII_RANGE;    // ASCII
            case "Alnum" -> JavaASCII.ALNUM;   // Alphanumeric characters
            case "Alpha" -> JavaASCII.ALPHA;   // Alphabetic characters
            case "Blank" -> JavaASCII.BLANK;   // Space and tab characters
            case "Cntrl" -> JavaASCII.CNTRL;   // Control characters
            case "Digit" -> CodePointSet.createNoDedup('0', '9');      // Numeric characters
            case "Graph" -> JavaASCII.GRAPH;   // printable and visible
            case "Lower" -> caseIns ? JavaASCII.ALPHA : JavaASCII.LOWER; // Lower-case alphabetic
            case "Print" -> CodePointSet.createNoDedup(0x20, 0x7E);    // Printable characters
            case "Punct" -> JavaASCII.PUNCT;   // Punctuation characters
            case "Space" -> JavaASCII.SPACE;   // Space characters
            case "Upper" -> caseIns ? JavaASCII.ALPHA : JavaASCII.UPPER; // Upper-case alphabetic
            case "XDigit" -> JavaASCII.HEX; // hexadecimal digits

            // Java character properties, defined by methods in Character.java
            case "javaLowerCase" -> caseIns ? lowerUpperTitleCase : lowerCase;
            case "javaUpperCase" -> caseIns ? lowerUpperTitleCase : upperCase;
            case "javaTitleCase" -> caseIns ? lowerUpperTitleCase : unicode.getProperty("General_Category=Titlecase_Letter");
            case "javaAlphabetic" -> alphabetic;
            case "javaIdeographic" -> unicode.getProperty("Ideographic");
            case "javaDigit" -> unicode.getProperty("General_Category=Decimal_Number");
            case "javaDefined" -> defined;
            case "javaLetter" -> unicode.getProperty("General_Category=Letter");
            case "javaLetterOrDigit" -> letterOrDigit;
            case "javaJavaIdentifierStart" -> javaIdentifierStart;
            case "javaJavaIdentifierPart" -> javaIdentifierPart;
            case "javaUnicodeIdentifierStart" -> unicodeIdentifierStart;
            case "javaUnicodeIdentifierPart" -> unicodeIdentifierPart;
            case "javaIdentifierIgnorable" -> identifierIgnorable;
            case "javaSpaceChar" -> spaceChar;
            case "javaWhitespace" -> WHITESPACE;
            case "javaISOControl" -> ISO_CONTROL;
            case "javaMirrored" -> unicode.getProperty("Bidi_Mirrored");
            default -> null;
        };
    }

    // this corresponds to "POSIX character classes" in java.util.regex.Pattern documentation
    private CodePointSet getPosixPredicate(String name, boolean caseIns) {
        return switch (name) {
            case "ALPHA" -> alphabetic;
            case "LOWER" -> caseIns ? lowerUpperTitleCase : lowerCase;
            case "UPPER" -> caseIns ? lowerUpperTitleCase : upperCase;
            case "SPACE" -> whiteSpace;
            case "PUNCT" -> unicode.getProperty("General_Category=Punctuation");
            case "XDIGIT" -> unicode.getProperty("General_Category=Decimal_Number").union(unicode.getProperty("Hex_Digit"));
            case "ALNUM" -> unicode.getProperty("General_Category=Decimal_Number").union(alphabetic);
            case "CNTRL" -> unicode.getProperty("General_Category=Control");
            case "DIGIT" -> unicode.getProperty("General_Category=Decimal_Number");
            case "BLANK" -> blank;
            case "GRAPH" -> graph;
            case "PRINT" -> graph.union(blank).subtract(unicode.getProperty("General_Category=Control"), new CompilationBuffer(Encodings.UTF_16));
            default -> null;
        };
    }

    // this corresponds to "Binary properties" in java.util.regex.Pattern documentation
    private CodePointSet getUnicodePredicate(String name, boolean caseIns) {
        return switch (name) {
            case "ALPHABETIC" -> alphabetic;
            case "ASSIGNED" -> defined;
            case "CONTROL" -> unicode.getProperty("General_Category=Control");
            case "EMOJI" -> unicode.getProperty("Emoji");
            case "EMOJI_PRESENTATION" -> unicode.getProperty("EPres");
            case "EMOJI_MODIFIER" -> unicode.getProperty("EMod");
            case "EMOJI_MODIFIER_BASE" -> unicode.getProperty("EBase");
            case "EMOJI_COMPONENT" -> unicode.getProperty("EComp");
            case "EXTENDED_PICTOGRAPHIC" -> unicode.getProperty("ExtPict");
            case "HEXDIGIT", "HEX_DIGIT" -> unicode.getProperty("General_Category=Decimal_Number").union(unicode.getProperty("Hex_Digit"));
            case "IDEOGRAPHIC" -> unicode.getProperty("Ideographic");
            case "JOINCONTROL", "JOIN_CONTROL" -> unicode.getProperty("Join_Control");
            case "LETTER" -> unicode.getProperty("General_Category=Letter");
            case "LOWERCASE" -> caseIns ? lowerUpperTitleCase : lowerCase;
            case "NONCHARACTERCODEPOINT", "NONCHARACTER_CODE_POINT" -> unicode.getProperty("Noncharacter_Code_Point");
            case "TITLECASE" -> caseIns ? lowerUpperTitleCase : unicode.getProperty("General_Category=Titlecase_Letter");
            case "PUNCTUATION" -> unicode.getProperty("General_Category=Punctuation");
            case "UPPERCASE" -> caseIns ? lowerUpperTitleCase : upperCase;
            case "WHITESPACE", "WHITE_SPACE" -> whiteSpace;
            case "WORD" -> word;
            default -> null;
        };
    }
}
