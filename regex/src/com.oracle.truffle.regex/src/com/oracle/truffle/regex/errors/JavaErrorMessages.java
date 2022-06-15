package com.oracle.truffle.regex.errors;

import com.oracle.truffle.api.CompilerDirectives;

public interface JavaErrorMessages {
    String EXPECTED_BRACE = "expected }";
    String EXPECTED_PAREN = "expected )";
    String MISSING_DASH_COLON_PAREN = "missing -, : or )";
    String MISSING_FLAG_DASH_COLON_PAREN = "missing flag, -, : or )";
    String MISSING_GROUP_NAME = "missing group name";
    String TOO_BIG_NUMBER = "too big number";
    String UNBALANCED_PARENTHESIS = "unbalanced parenthesis";
    String UNDEFINED_GROUP_OPTION = "undefined group option";
    String UNEXPECTED_END_OF_PATTERN = "unexpected end of pattern";
    String UNTERMINATED_CHARACTER_SET = "unterminated character set";
    String UNTERMINATED_SUBPATTERN = "missing ), unterminated subpattern";

    @CompilerDirectives.TruffleBoundary
    static String badCharacterRange(String range) {
        return "bad character range " + range;
    }

    @CompilerDirectives.TruffleBoundary
    static String badEscape(String code) {
        return "bad escape \\u" + code;
    }

    @CompilerDirectives.TruffleBoundary
    static String incompleteEscape(String code) {
        return "incomplete escape \\u" + code;
    }

    @CompilerDirectives.TruffleBoundary
    static String invalidUnicodeEscape(String code) {
        return "unicode escape value " + code + " outside of range 0-0x10FFFF";
    }

    @CompilerDirectives.TruffleBoundary
    static String unknownExtension(int c) {
        return "unknown extension ?" + new String(Character.toChars(c));
    }

    @CompilerDirectives.TruffleBoundary
    static String unterminatedName(char terminator) {
        return "missing " + terminator + ", unterminated name";
    }
}
