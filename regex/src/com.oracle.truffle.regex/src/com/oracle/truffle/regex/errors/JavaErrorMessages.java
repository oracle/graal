/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.tregex.parser.Token;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public interface JavaErrorMessages {
    String EXPECTED_BRACE = "expected }";
    String EXPECTED_PAREN = "expected )";
    String MISSING_DASH_COLON_PAREN = "missing -, : or )";
    String MISSING_FLAG_DASH_COLON_PAREN = "missing flag, -, : or )";
    String INVALID_GROUP_NAME_START = "capturing group name does not start with a Latin letter";
    String INVALID_GROUP_NAME_REST = "named capturing group is missing trailing '>'";
    String ILLEGAL_REPETITION = "Illegal repetition range";
    String ILLEGAL_CHARACTER_RANGE = "Illegal character range";
    String ILLEGAL_HEX_ESCAPE = "Illegal hexadecimal escape sequence";
    String TOO_BIG_NUMBER = "too big number";
    String UNBALANCED_PARENTHESIS = "unbalanced parenthesis";
    String UNDEFINED_GROUP_OPTION = "undefined group option";
    String UNCLOSED_GROUP = "Unclosed group";
    String UNEXPECTED_END_OF_PATTERN = "unexpected end of pattern";
    String UNTERMINATED_CHARACTER_SET = "unterminated character set";
    String UNTERMINATED_SUBPATTERN = "missing ), unterminated subpattern";
    String UNCLOSED_COUNTED_CLOSURE = "Unclosed counted closure";
    String UNCLOSED_CHARACTER_CLASS = "Unclosed character class";
    String NAMED_CAPTURE_GROUP_REFERENCE_MISSING_BEGIN = "\\k is not followed by '<' for named capturing group";
    String UNKNOWN_INLINE_MODIFIER = "Unknown inline modifier";
    String ILLEGAL_OCT_ESCAPE = "Illegal octal escape sequence";
    String UNESCAPED_TRAILING_BACKSLASH = "Unescaped trailing backslash";
    String BAD_CLASS_SYNTAX = "Bad class syntax";
    String BAD_INTERSECTION_SYNTAX = "Bad intersection syntax";

    String UNCLOSED_CHAR_FAMILY = "Unclosed character family";
    String EMPTY_CHAR_FAMILY = "Empty character family";

    String HEX_TOO_BIG = "Hexadecimal codepoint is too big";

    String UNCLOSED_HEX = "Unclosed hexadecimal escape sequence";

    String UNCLOSED_CHAR_NAME = "Unclosed character name escape sequence";

    String ILLEGAL_CHARACTER_NAME = "Illegal character name escape sequence";

    String ILLEGAL_CTRL_SEQ = "Illegal control escape sequence";

    String ILLEGAL_UNICODE_ESC_SEQ = "Illegal Unicode escape sequence";

    String ILLEGAL_ESCAPE_SEQUENCE = "Illegal/unsupported escape sequence";

    @TruffleBoundary
    static String badCharacterRange(String range) {
        return "bad character range " + range;
    }

    @TruffleBoundary
    static String badEscape(String code) {
        return "bad escape \\u" + code;
    }

    @TruffleBoundary
    static String incompleteEscape(String code) {
        return "incomplete escape \\u" + code;
    }

    @TruffleBoundary
    static String invalidUnicodeEscape(String code) {
        return "unicode escape value " + code + " outside of range 0-0x10FFFF";
    }

    @TruffleBoundary
    static String unknownExtension(int c) {
        return "unknown extension ?" + new String(Character.toChars(c));
    }

    @TruffleBoundary
    static String unterminatedName(char terminator) {
        return "missing " + terminator + ", unterminated name";
    }

    @TruffleBoundary
    static String groupRedefinition(String name) {
        return "Named capturing group <" + name + "> is already defined";
    }

    @TruffleBoundary
    static String unknownGroupReference(String name) {
        return "named capturing group <" + name + "> does not exist";
    }

    @TruffleBoundary
    static String unknownUnicodeProperty(String name, String value) {
        return "Unknown Unicode property {name=<" + name + ">, " + "value=<" + value + ">}";
    }

    @TruffleBoundary
    static String unknownUnicodeCharacterProperty(String name) {
        return "Unknown character property name {" + name + "}";
    }

    @TruffleBoundary
    static String unknownCharacterName(String name) {
        return "Unknown character name [" + name + "]";
    }

    @TruffleBoundary
    static String danglingMetaCharacter(Token.Quantifier quantifier) {
        return "Dangling meta character '" + quantifier.toStringNoSuffix() + "'";
    }
}
