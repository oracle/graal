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

import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.tregex.parser.RegexLexer;

public class JsErrorMessages {

    /* syntax errors */

    public static final String CHAR_CLASS_RANGE_OUT_OF_ORDER = "Range out of order in character class";
    public static final String COMPLEMENT_OF_STRING_SET = "Negated character class may contain strings";
    public static final String EMPTY_GROUP_NAME = "Empty named capture group name";
    public static final String EMPTY_MODIFIER = "No flags in modifier";
    public static final String ENDS_WITH_UNFINISHED_ESCAPE_SEQUENCE = "Ends with an unfinished escape sequence";
    public static final String ENDS_WITH_UNFINISHED_UNICODE_PROPERTY = "Ends with an unfinished Unicode property escape";
    public static final String INCOMPLETE_QUANTIFIER = "Incomplete quantifier";
    public static final String INCOMPLETE_MODIFIER = "Incomplete modifier";
    public static final String INVALID_CHARACTER_CLASS = "Invalid character class";
    public static final String INVALID_CHARACTER_IN_CHARACTER_CLASS = "Invalid character in character class";
    public static final String INVALID_CONTROL_CHAR_ESCAPE = "Invalid control char escape";
    public static final String INVALID_ESCAPE = "Invalid escape";
    public static final String INVALID_GROUP = "Invalid group";
    public static final String INVALID_GROUP_NAME_PART = "Invalid character in group name";
    public static final String INVALID_GROUP_NAME_START = "Invalid character at start of group name";
    public static final String INVALID_MODIFIER = "Invalid modifier";
    public static final String INVALID_UNICODE_ESCAPE = "Invalid Unicode escape";
    public static final String INVALID_UNICODE_PROPERTY = "Invalid Unicode property escape";
    public static final String MISSING_GROUP_FOR_BACKREFERENCE = "Missing capture group for backreference";
    public static final String MISSING_GROUP_NAME = "Missing group name in named capture group reference";
    public static final String MODIFIER_BOTH_ADDING_AND_REMOVING_FLAG = "Modifier is both adding and removing the same flag";
    public static final String MULTIPLE_GROUPS_SAME_NAME = "Multiple named capture groups with the same name";
    public static final String QUANTIFIER_ON_LOOKAHEAD_ASSERTION = "Quantifier on lookahead assertion";
    public static final String QUANTIFIER_ON_LOOKBEHIND_ASSERTION = "Quantifier on lookbehind assertion";
    public static final String QUANTIFIER_ON_QUANTIFIER = "Quantifier on quantifier";
    public static final String QUANTIFIER_OUT_OF_ORDER = "Numbers out of order in {} quantifier";
    public static final String QUANTIFIER_WITHOUT_TARGET = "Quantifier without target";
    public static final String REPEATED_FLAG_IN_MODIFIER = "Repeated regex flag in modifier";
    public static final String UNMATCHED_LEFT_BRACKET = "Unterminated character class";
    public static final String UNMATCHED_RIGHT_BRACKET = "Unmatched ']'";
    public static final String UNMATCHED_RIGHT_PARENTHESIS = "Unmatched ')'";
    public static final String UNMATCHED_RIGHT_BRACE = "Unmatched '}'";
    public static final String UNSUPPORTED_FLAG_IN_MODIFIER = "Invalid regular expression flag in modifier";
    public static final String UNTERMINATED_GROUP = "Unterminated group";
    public static final String UNTERMINATED_GROUP_NAME = "Unterminated group name";
    public static final String UNTERMINATED_STRING_SET = "Unterminated string set";
    public static final String UNTERMINATED_CHARACTER_RANGE = "Unterminated character range";

    public static String unexpectedCharacterInClassSet(int codePoint) {
        return String.format("Unexpected '%s' in class set expression", Character.toString(codePoint));
    }

    public static String unexpectedDoublePunctuatorInClassSet(String punctuator) {
        return String.format("Unexpected '%s%s' in class set expression", punctuator, punctuator);
    }

    public static String mixedOperatorsInClassSet(RegexLexer.ClassSetOperator leftOperator, RegexLexer.ClassSetOperator rightOperator) {
        return String.format("Using both %s and %s operators in one class set expression", leftOperator, rightOperator);
    }

    public static String rangeAsClassSetOperand(RegexLexer.ClassSetOperator operator) {
        return String.format("Character range used directly as an operand of %s operator", operator);
    }

    public static String missingClassSetOperand(RegexLexer.ClassSetOperator operator) {
        return String.format("Missing operand for %s operator", operator);
    }

    public static String invalidRegularExpression(RegexSource source, String message) {
        return String.format("Invalid regular expression: %s: %s", source, message);
    }

    public static String flagNotAllowedInModifier(char flagChar) {
        return String.format("Flag '%s' not allowed in modifier", flagChar);
    }

    /* flag related errors */

    public static final String REPEATED_FLAG = "Repeated regex flag";
    public static final String UNSUPPORTED_FLAG = "Invalid regular expression flags";
    public static final String BOTH_FLAGS_SET_U_V = "Both flags 'u' and 'v' cannot be set at same time";
}
