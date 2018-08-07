/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
    String UNMATCHED_LEFT_BRACKET = "Unmatched '['";
    String UNMATCHED_RIGHT_BRACKET = "Unmatched ']'";
    String UNMATCHED_RIGHT_PARENTHESIS = "Unmatched ')'";
    String UNMATCHED_RIGHT_BRACE = "Unmatched '}'";
    String UNTERMINATED_GROUP = "Unterminated group";
    String UNTERMINATED_GROUP_NAME = "Unterminated group name";
}
