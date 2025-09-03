/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.js;

import com.oracle.svm.webimage.hightiercodegen.Keyword;

/**
 * Collection of keywords that are available in JavaScript.
 */
public final class JSKeyword extends Keyword {

    public static final JSKeyword MultiLineCommentStart = JSKeyword.create("/*");
    public static final JSKeyword MultiLineCommentEnd = JSKeyword.create("*/");
    public static final JSKeyword Assignment = JSKeyword.create("=");
    public static final JSKeyword SingleQuote = JSKeyword.create("'");
    public static final JSKeyword Quote = JSKeyword.create("\"");
    public static final JSKeyword Semicolon = JSKeyword.create(";");
    public static final JSKeyword COLON = JSKeyword.create(":");
    public static final JSKeyword DOT = JSKeyword.create(".");
    public static final JSKeyword LPAR = JSKeyword.create("(");
    public static final JSKeyword RPAR = JSKeyword.create(")");
    public static final JSKeyword LBRACE = JSKeyword.create("{");
    public static final JSKeyword RBRACE = JSKeyword.create("}");
    public static final JSKeyword LBRACK = JSKeyword.create("[");
    public static final JSKeyword RBRACK = JSKeyword.create("]");
    public static final JSKeyword COMMA = JSKeyword.create(",");
    public static final JSKeyword TRUE = JSKeyword.create("true");
    public static final JSKeyword FALSE = JSKeyword.create("false");
    public static final JSKeyword IF = JSKeyword.create("if");
    public static final JSKeyword ELSE = JSKeyword.create("else");
    public static final JSKeyword WHILE = JSKeyword.create("while");
    public static final JSKeyword BREAK = JSKeyword.create("break");
    public static final JSKeyword DO = JSKeyword.create("do");
    public static final JSKeyword CONTINUE = JSKeyword.create("continue");
    public static final JSKeyword RETURN = JSKeyword.create("return");
    public static final JSKeyword SWITCH = JSKeyword.create("switch");
    public static final JSKeyword NEW = JSKeyword.create("new");
    public static final JSKeyword VAR = JSKeyword.create("var");
    public static final JSKeyword LET = JSKeyword.create("let");
    public static final JSKeyword CONST = JSKeyword.create("const");
    public static final JSKeyword CASE = JSKeyword.create("case");
    public static final JSKeyword DEFAULT = JSKeyword.create("default");
    public static final JSKeyword TRY = JSKeyword.create("try");
    public static final JSKeyword CATCH = JSKeyword.create("catch");
    public static final JSKeyword THROW = JSKeyword.create("throw");
    public static final JSKeyword SHORT_CIRCUITE_OR = JSKeyword.create("||");
    public static final JSKeyword EQ = JSKeyword.create("==");
    public static final JSKeyword EQQ = JSKeyword.create("===");
    public static final JSKeyword CLASS = JSKeyword.create("class");
    public static final JSKeyword EXTENDS = JSKeyword.create("extends");
    public static final JSKeyword FUNCTION = JSKeyword.create("function");
    public static final JSKeyword ARROW = JSKeyword.create("=>");

    // arithmetic symbols
    public static final JSKeyword ADD = JSKeyword.create("+");
    public static final JSKeyword SUB = JSKeyword.create("-");
    public static final JSKeyword MUL = JSKeyword.create("*");
    public static final JSKeyword DIV = JSKeyword.create("/");
    public static final JSKeyword MOD = JSKeyword.create("%");
    public static final JSKeyword NOT = JSKeyword.create("~");
    public static final JSKeyword SL = JSKeyword.create("<<");
    public static final JSKeyword USR = JSKeyword.create(">>>");
    public static final JSKeyword SR = JSKeyword.create(">>");
    public static final JSKeyword AND = JSKeyword.create("&");
    public static final JSKeyword OR = JSKeyword.create("|");
    public static final JSKeyword XOR = JSKeyword.create("^");

    // logic symbols
    public static final JSKeyword LOGIC_NOT = JSKeyword.create("!");

    private JSKeyword(String s) {
        super(s);
    }

    private static JSKeyword create(String s) {
        return new JSKeyword(s);
    }

}
