package com.oracle.truffle.regex.errors;

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
    String UNEXPECTED_END_OF_PATTERN = "unexpected end of pattern";
    String UNTERMINATED_CHARACTER_SET = "unterminated character set";
    String UNTERMINATED_SUBPATTERN = "missing ), unterminated subpattern";
    String UNCLOSED_COUNTED_CLOSURE = "Unclosed counted closure";
    String UNCLOSED_CHARACTER_CLASS = "Unclosed character class";
    String NAMED_CAPTURE_GROUP_REFERENCE_MISSING_BEGIN = "\\k is not followed by '<' for named capturing group";
    String UNKNOWN_INLINE_MODIFIER = "Unknown inline modifier";
    String ILLEGAL_OCT_ESCAPE = "Illegal octal escape sequence";
    String UNESCAPED_TRAILING_BACKSLASH = "Unescaped trailing backslash";

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
}
