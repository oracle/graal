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
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.string.Encodings;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import java.util.Locale;

public class UnicodeProperties {
    private static final CodePointSet PUNCT = JavaGc.CONNECTOR_PUNCTUATION.union(JavaGc.DASH_PUNCTUATION).union(JavaGc.START_PUNCTUATION).union(JavaGc.END_PUNCTUATION).union(
                    JavaGc.OTHER_PUNCTUATION).union(JavaGc.INITIAL_QUOTE_PUNCTUATION).union(JavaGc.FINAL_QUOTE_PUNCTUATION);
    private static final CodePointSet WHITE_SPACE = CodePointSet.createNoDedup(0x9, 0xd, 0x85, 0x85).union(JavaGc.SPACE_SEPARATOR).union(JavaGc.LINE_SEPARATOR).union(JavaGc.PARAGRAPH_SEPARATOR);

    private static final EconomicMap<String, String> javaScriptAliases = normalizeKeys(UnicodePropertyData.SCRIPT_ALIASES);
    private static final EconomicMap<String, String> javaBlockAliases = normalizeKeys(UnicodePropertyData.BLOCK_ALIASES);
    public static final CodePointSet GRAPH = JavaGc.SPACE_SEPARATOR.union(JavaGc.LINE_SEPARATOR).union(JavaGc.PARAGRAPH_SEPARATOR).union(JavaGc.CONTROL).union(JavaGc.SURROGATE).union(
                    JavaGc.UNASSIGNED).createInverse(Encodings.UTF_16);
    public static final CodePointSet BLANK = JavaGc.SPACE_SEPARATOR.union(CodePointSet.create(0x9));

    private static EconomicMap<String, String> normalizeKeys(EconomicMap<String, String> original) {
        EconomicMap<String, String> res = EconomicMap.create(original.size());
        MapCursor<String, String> cur = original.getEntries();
        while (cur.advance()) {
            res.put(cur.getKey().toLowerCase(), cur.getValue());
        }
        return res;
    }

    public static CodePointSet getProperty(String propertySpec) {
        return getProperty(propertySpec, false);
    }

    public static CodePointSet getProperty(String propertySpec, boolean caseInsensitive) {
        return evaluatePropertySpec(normalizePropertySpec(propertySpec, caseInsensitive));
    }

    public static ClassSetContents getPropertyOfStrings(String propertySpec) {
        return evaluatePropertySpecStrings(normalizePropertySpec(propertySpec, false));
    }

    public static CodePointSet getBlockJava(String name) {
        String normalizedName;

        try {
            normalizedName = Character.UnicodeBlock.forName(name).toString().toLowerCase();
        } catch (IllegalArgumentException iae) {
            return null;
        }

        String alias = javaBlockAliases.get(normalizedName);
        if (alias == null) {
            return null;
        }

        return UnicodePropertyData.retrieveProperty("blk=" + alias);
    }

    public static CodePointSet getScriptJava(String name) {
        String normalizedName;

        try {
            normalizedName = Character.UnicodeScript.forName(name).toString().toLowerCase();
        } catch (IllegalArgumentException iae) {
            return null;
        }

        String alias = javaScriptAliases.get(normalizedName);
        if (alias == null) {
            return null;
        }

        return UnicodePropertyData.retrieveProperty("sc=" + alias);
    }

    public static CodePointSet getPropertyJava(String name, boolean caseIns) {
        // Unicode character property aliases, defined in
        // http://www.unicode.org/Public/UNIDATA/PropertyValueAliases.txt
        return switch (name) {
            case "Cn" -> JavaGc.category("Cn");
            case "Lu" -> caseIns ? categories("Lu", "Ll", "Lt") : JavaGc.category("Lu");
            case "Ll" -> caseIns ? categories("Lu", "Ll", "Lt") : JavaGc.category("Ll");
            case "Lt" -> caseIns ? categories("Lu", "Ll", "Lt") : JavaGc.category("Lt");
            case "Lm" -> JavaGc.category("Lm");
            case "Lo" -> JavaGc.category("Lo");
            case "Mn" -> JavaGc.category("Mn");
            case "Me" -> JavaGc.category("Me");
            case "Mc" -> JavaGc.category("Mc");
            case "Nd" -> JavaGc.category("Nd");
            case "Nl" -> JavaGc.category("Nl");
            case "No" -> JavaGc.category("No");
            case "Zs" -> JavaGc.category("Zs");
            case "Zl" -> JavaGc.category("Zl");
            case "Cc" -> JavaGc.category("Cc");
            case "Cf" -> JavaGc.category("Cf");
            case "Zp" -> JavaGc.category("Zp");
            case "Co" -> JavaGc.category("Co");
            case "Cs" -> JavaGc.category("Cs");
            case "Pd" -> JavaGc.category("Pd");
            case "Ps" -> JavaGc.category("Ps");
            case "Pe" -> JavaGc.category("Pe");
            case "Pc" -> JavaGc.category("Pc");
            case "Po" -> JavaGc.category("Po");
            case "Sm" -> JavaGc.category("Sm");
            case "Sc" -> JavaGc.category("Sc");
            case "Sk" -> JavaGc.category("Sk");
            case "So" -> JavaGc.category("So");
            case "Pi" -> JavaGc.category("Pi");
            case "Pf" -> JavaGc.category("Pf");
            case "L" -> categories("Lu", "Ll", "Lt", "Lm", "Lo");
            case "M" -> categories("Mn", "Me", "Mc");
            case "N" -> categories("Nd", "Nl", "No");
            case "Z" -> categories("Zs", "Zl", "Zp");
            case "C" -> categories("Cc", "Cf", "Co", "Cs", "Cn");
            case "P" -> categories("Pd", "Ps", "Pe", "Pc", "Po", "Pi", "Pf");
            case "S" -> categories("Sm", "Sc", "Sk", "So");
            case "LC" -> categories("Lu", "Ll", "Lt");
            case "LD" -> categories("Lu", "Ll", "Lt", "Lm", "Lo", "Nd");
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
            case "javaLowerCase" -> caseIns ? JavaCharacter.LOWER_CASE.union(JavaCharacter.UPPER_CASE).union(JavaCharacter.TITLE_CASE)
                            : JavaCharacter.LOWER_CASE;
            case "javaUpperCase" -> caseIns ? JavaCharacter.LOWER_CASE.union(JavaCharacter.UPPER_CASE).union(JavaCharacter.TITLE_CASE)
                            : JavaCharacter.UPPER_CASE;
            case "javaAlphabetic" -> JavaCharacter.ALPHABETIC;
            case "javaIdeographic" -> JavaCharacter.IDEOGRAPHIC;
            case "javaTitleCase" -> caseIns ? JavaCharacter.LOWER_CASE.union(JavaCharacter.UPPER_CASE).union(JavaCharacter.TITLE_CASE)
                            : JavaCharacter.TITLE_CASE;
            case "javaDigit" -> JavaCharacter.DIGIT;
            case "javaDefined" -> JavaCharacter.DEFINED;
            case "javaLetter" -> JavaCharacter.LETTER;
            case "javaLetterOrDigit" -> JavaCharacter.LETTER_OR_DIGIT;
            case "javaJavaIdentifierStart" -> JavaCharacter.JAVA_IDENTIFIER_START;
            case "javaJavaIdentifierPart" -> JavaCharacter.JAVA_IDENTIFIER_PART;
            case "javaUnicodeIdentifierStart" -> JavaCharacter.UNICODE_IDENTIFIER_START;
            case "javaUnicodeIdentifierPart" -> JavaCharacter.UNICODE_IDENTIFIER_PART;
            case "javaIdentifierIgnorable" -> JavaCharacter.IDENTIFIER_IGNORABLE;
            case "javaSpaceChar" -> JavaCharacter.SPACE_CHAR;
            case "javaWhitespace" -> JavaCharacter.WHITESPACE;
            case "javaISOControl" -> JavaCharacter.ISO_CONTROL;
            case "javaMirrored" -> JavaCharacter.MIRRORED;
            default -> null;
        };
    }

    // this corresponds to "POSIX character classes" in java.util.regex.Pattern documentation
    private static CodePointSet getPosixPredicateJava(String name, boolean caseIns) {
        return switch (name) {
            case "ALPHA" -> JavaCharacter.ALPHABETIC;
            case "LOWER" -> caseIns ? JavaCharacter.UPPER_CASE.union(JavaCharacter.LOWER_CASE).union(JavaCharacter.TITLE_CASE) : JavaCharacter.LOWER_CASE;
            case "UPPER" -> caseIns ? JavaCharacter.UPPER_CASE.union(JavaCharacter.LOWER_CASE).union(JavaCharacter.TITLE_CASE) : JavaCharacter.UPPER_CASE;
            case "SPACE" -> WHITE_SPACE;
            case "PUNCT" -> PUNCT;
            case "XDIGIT" -> JavaCharacter.DIGIT.union(JavaPropList.HEX_DIGIT);
            case "ALNUM" -> JavaCharacter.DIGIT.union(JavaCharacter.ALPHABETIC);
            case "CNTRL" -> JavaGc.CONTROL;
            case "DIGIT" -> JavaCharacter.DIGIT;
            case "BLANK" -> BLANK;
            case "GRAPH" -> GRAPH;
            case "PRINT" -> GRAPH.union(BLANK).subtract(JavaGc.CONTROL, new CompilationBuffer(Encodings.UTF_16));
            default -> null;
        };
    }

    // this corresponds to "Binary properties" in java.util.regex.Pattern documentation
    private static CodePointSet getUnicodePredicateJava(String name, boolean caseIns) {
        return switch (name) {
            case "ALPHABETIC" -> JavaCharacter.ALPHABETIC;
            case "ASSIGNED" -> JavaCharacter.DEFINED;
            case "CONTROL" -> JavaGc.CONTROL;
            case "EMOJI" -> JavaEmoji.EMOJI;
            case "EMOJI_PRESENTATION" -> JavaEmoji.EMOJI_PRESENTATION;
            case "EMOJI_MODIFIER" -> JavaEmoji.EMOJI_MODIFIER;
            case "EMOJI_MODIFIER_BASE" -> JavaEmoji.EMOJI_MODIFIER_BASE;
            case "EMOJI_COMPONENT" -> JavaEmoji.EMOJI_COMPONENT;
            case "EXTENDED_PICTOGRAPHIC" -> JavaEmoji.EXTENDED_PICTOGRAPHIC;
            case "HEXDIGIT", "HEX_DIGIT" -> JavaCharacter.DIGIT.union(JavaPropList.HEX_DIGIT);
            case "IDEOGRAPHIC" -> JavaCharacter.IDEOGRAPHIC;
            case "JOINCONTROL", "JOIN_CONTROL" -> JavaPropList.JOINT_CONTROL;
            case "LETTER" -> JavaCharacter.LETTER;
            case "LOWERCASE" -> caseIns ? JavaCharacter.LOWER_CASE.union(JavaCharacter.UPPER_CASE).union(JavaCharacter.TITLE_CASE) : JavaCharacter.LOWER_CASE;
            case "NONCHARACTERCODEPOINT", "NONCHARACTER_CODE_POINT" -> JavaPropList.NONCHARACTER_CODE_POINT;
            case "TITLECASE" -> caseIns ? JavaCharacter.LOWER_CASE.union(JavaCharacter.UPPER_CASE).union(JavaCharacter.TITLE_CASE) : JavaCharacter.TITLE_CASE;
            case "PUNCTUATION" -> PUNCT;
            case "UPPERCASE" -> caseIns ? JavaCharacter.LOWER_CASE.union(JavaCharacter.UPPER_CASE).union(JavaCharacter.TITLE_CASE) : JavaCharacter.UPPER_CASE;
            case "WHITESPACE", "WHITE_SPACE" -> WHITE_SPACE;
            case "WORD" -> JavaCharacter.ALPHABETIC.union(JavaPropList.JOINT_CONTROL).union(JavaGc.NON_SPACING_MARK).union(JavaGc.ENCLOSING_MARK).union(JavaGc.COMBINING_SPACING_MARK).union(
                            JavaGc.DECIMAL_DIGIT_NUMBER).union(JavaGc.CONNECTOR_PUNCTUATION);
            default -> null;
        };
    }

    public static CodePointSet forUnicodePropertyJava(String propName, boolean caseIns) {
        String propNameNormalized = propName.toUpperCase(Locale.ROOT);
        CodePointSet p = getUnicodePredicateJava(propNameNormalized, caseIns);
        if (p != null) {
            return p;
        }
        return getPosixPredicateJava(propNameNormalized, caseIns);
    }

    public static CodePointSet forPOSIXNameJava(String propName, boolean caseIns) {
        return getPosixPredicateJava(propName.toUpperCase(Locale.ENGLISH), caseIns);
    }

    private static CodePointSet categories(String... name) {
        CodePointSet res = CodePointSet.getEmpty();
        for (String n : name) {
            res = res.union(JavaGc.category(n));
        }
        return res;
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

    /**
     * @param propertySpec *Normalized* Unicode character property specification (i.e. only
     *            abbreviated properties and property values)
     */
    private static ClassSetContents evaluatePropertySpecStrings(String propertySpec) {
        CodePointSet generalCategory = UnicodeGeneralCategories.getGeneralCategory(propertySpec);
        if (generalCategory != null) {
            return ClassSetContents.createCharacterClass(generalCategory);
        }
        return UnicodePropertyData.retrievePropertyOfStrings(propertySpec);
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
