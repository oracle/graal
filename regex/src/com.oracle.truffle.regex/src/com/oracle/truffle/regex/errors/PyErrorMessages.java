/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.errors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.parser.flavors.PythonREMode;

import java.util.regex.Pattern;

public interface PyErrorMessages {

    String BAD_ESCAPE_END_OF_PATTERN = "bad escape (end of pattern)";
    String BAD_GROUP_NUMBER = "bad group number";
    String CANNOT_REFER_TO_AN_OPEN_GROUP = "cannot refer to an open group";
    String CANNOT_REFER_TO_GROUP_DEFINED_IN_THE_SAME_LOOKBEHIND_SUBPATTERN = "cannot refer to group defined in the same lookbehind subpattern";
    String CONDITIONAL_BACKREF_WITH_MORE_THAN_TWO_BRANCHES = "conditional backref with more than two branches";
    String INLINE_FLAGS_CANNOT_TURN_OFF_FLAGS_A_U_AND_L = "bad inline flags: cannot turn off flags 'a', 'u' and 'L'";
    String INLINE_FLAGS_CANNOT_TURN_OFF_GLOBAL_FLAG = "bad inline flags: cannot turn off global flag";
    String INLINE_FLAGS_CANNOT_TURN_ON_GLOBAL_FLAG = "bad inline flags: cannot turn on global flag";
    String INLINE_FLAGS_CANNOT_USE_L_FLAG_WITH_A_STR_PATTERN = "bad inline flags: cannot use 'L' flag with a str pattern";
    String INLINE_FLAGS_CANNOT_USE_U_FLAG_WITH_A_BYTES_PATTERN = "bad inline flags: cannot use 'u' flag with a bytes pattern";
    String INLINE_FLAGS_FLAGS_A_U_AND_L_ARE_INCOMPATIBLE = "bad inline flags: flags 'a', 'u' and 'L' are incompatible";
    String INLINE_FLAGS_FLAG_TURNED_ON_AND_OFF = "bad inline flags: flag turned on and off";
    String LOOK_BEHIND_REQUIRES_FIXED_WIDTH_PATTERN = "look-behind requires fixed-width pattern";
    String MIN_REPEAT_GREATER_THAN_MAX_REPEAT = "min repeat greater than max repeat";
    String MISSING_COLON = "missing :";
    String MISSING_DASH_COLON_PAREN = "missing -, : or )";
    String MISSING_FLAG = "missing flag";
    String MISSING_GROUP_NAME = "missing group name";
    String MULTIPLE_REPEAT = "multiple repeat";
    String NEGATIVE_GROUP_NUMBER = "negative group number";
    String NOTHING_TO_REPEAT = "nothing to repeat";
    String UNBALANCED_PARENTHESIS = "unbalanced parenthesis";
    String UNEXPECTED_END_OF_PATTERN = "unexpected end of pattern";
    String UNKNOWN_FLAG = "unknown flag";
    String UNTERMINATED_CHARACTER_SET = "unterminated character set";
    String UNTERMINATED_COMMENT = "missing ), unterminated comment";
    String UNTERMINATED_NAME = "missing ), unterminated name";
    String UNTERMINATED_NAME_ANGLE_BRACKET = "missing >, unterminated name";
    String UNTERMINATED_SUBPATTERN = "missing ), unterminated subpattern";
    String GLOBAL_FLAGS_NOT_AT_START = "global flags not at the start of the expression";

    @TruffleBoundary
    static String badCharacterInGroupName(String name) {
        return "bad character in group name '" + name + "'";
    }

    @TruffleBoundary
    static String badCharacterRange(String range) {
        return "bad character range " + range;
    }

    @TruffleBoundary
    static String badEscape(int chr) {
        return "bad escape \\" + new String(Character.toChars(chr));
    }

    @TruffleBoundary
    static String incompleteEscape(String code) {
        return "incomplete escape " + code;
    }

    @TruffleBoundary
    static String invalidGroupReference(String ref) {
        return "invalid group reference " + ref;
    }

    @TruffleBoundary
    static String invalidOctalEscape(String code) {
        return "octal escape value " + code + " outside of range 0-0o377";
    }

    @TruffleBoundary
    static String invalidUnicodeEscape(String code) {
        return "bad escape " + code;
    }

    @TruffleBoundary
    static String missing(String name) {
        return "missing " + name;
    }

    @TruffleBoundary
    static String missingUnterminatedName(char terminator) {
        return "missing " + terminator + ", unterminated name";
    }

    @TruffleBoundary
    static String redefinitionOfGroupName(String name, int newId, int oldId) {
        return String.format("redefinition of group name '%s' as group %d; was group %d", name, newId, oldId);
    }

    @TruffleBoundary
    static String undefinedCharacterName(String name) {
        return "undefined character name '" + name + "'";
    }

    @TruffleBoundary
    static String unknownExtensionLt(int chr) {
        return "unknown extension ?<" + new String(Character.toChars(chr));
    }

    @TruffleBoundary
    static String unknownExtensionP(int chr) {
        return "unknown extension ?P" + new String(Character.toChars(chr));
    }

    @TruffleBoundary
    static String unknownExtensionQ(int chr) {
        return "unknown extension ?" + new String(Character.toChars(chr));
    }

    Pattern NON_ASCII_CHARACTERS = Pattern.compile("\\P{ASCII}");
    Pattern NON_PRINTABLE_CHARACTERS = Pattern.compile("\\P{Print}", Pattern.UNICODE_CHARACTER_CLASS);

    @TruffleBoundary
    private static String repr(String str, PythonREMode mode) {
        Pattern charsToReplace = switch (mode) {
            case Bytes -> NON_ASCII_CHARACTERS;
            case Str -> NON_PRINTABLE_CHARACTERS;
        };
        return charsToReplace.matcher(str).replaceAll(res -> {
            int cp = res.group().codePointAt(0);
            if (cp <= 0xff) {
                return String.format("\\\\x%02x", cp);
            } else if (cp <= 0xffff) {
                return String.format("\\\\u%04x", cp);
            } else {
                return String.format("\\\\U%08x", cp);
            }
        });
    }

    @TruffleBoundary
    static String unknownGroupName(String name, PythonREMode mode) {
        return "unknown group name '" + repr(name, mode) + "'";
    }
}
