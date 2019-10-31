/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser;

interface ErrorMessages {

    String CHAR_CLASS_RANGE_OUT_OF_ORDER = "Range out of order in character class";
    String EMPTY_GROUP_NAME = "Empty named capture group name";
    String ENDS_WITH_UNFINISHED_ESCAPE_SEQUENCE = "Ends with an unfinished escape sequence";
    String ENDS_WITH_UNFINISHED_UNICODE_PROPERTY = "Ends with an unfinished Unicode property escape";
    String INCOMPLETE_QUANTIFIER = "Incomplete quantifier";
    String INVALID_CHARACTER_CLASS = "Invalid character class";
    String INVALID_CONTROL_CHAR_ESCAPE = "Invalid control char escape";
    String INVALID_ESCAPE = "Invalid escape";
    String INVALID_GROUP_NAME_PART = "Invalid character in group name";
    String INVALID_GROUP_NAME_START = "Invalid character at start of group name";
    String INVALID_UNICODE_ESCAPE = "Invalid Unicode escape";
    String INVALID_UNICODE_PROPERTY = "Invalid Unicode property escape";
    String MISSING_GROUP_FOR_BACKREFERENCE = "Missing capture group for backreference";
    String MISSING_GROUP_NAME = "Missing group name in named capture group reference";
    String MULTIPLE_GROUPS_SAME_NAME = "Multiple named capture groups with the same name";
    String QUANTIFIER_ON_LOOKAHEAD_ASSERTION = "Quantifier on lookahead assertion";
    String QUANTIFIER_ON_LOOKBEHIND_ASSERTION = "Quantifier on lookbehind assertion";
    String QUANTIFIER_ON_QUANTIFIER = "Quantifier on quantifier";
    String QUANTIFIER_OUT_OF_ORDER = "Numbers out of order in {} quantifier";
    String QUANTIFIER_WITHOUT_TARGET = "Quantifier without target";
    String UNMATCHED_LEFT_BRACKET = "Unterminated character class";
    String UNMATCHED_RIGHT_BRACKET = "Unmatched ']'";
    String UNMATCHED_RIGHT_PARENTHESIS = "Unmatched ')'";
    String UNMATCHED_RIGHT_BRACE = "Unmatched '}'";
    String UNTERMINATED_GROUP = "Unterminated group";
    String UNTERMINATED_GROUP_NAME = "Unterminated group name";
}
