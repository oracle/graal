/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.sun.max.lang;

import com.sun.max.util.*;

/**
 */
public final class Chars {

    private Chars() {
    }

    public static final int SIZE = 2;

    public static final Range VALUE_RANGE = new Range(Character.MIN_VALUE, Character.MAX_VALUE);

    public static boolean isHexDigit(char c) {
        switch (c) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case 'a':
            case 'b':
            case 'c':
            case 'd':
            case 'e':
            case 'f':
            case 'A':
            case 'B':
            case 'C':
            case 'D':
            case 'E':
            case 'F':
                return true;
        }
        return false;
    }

    public static boolean isOctalDigit(char c) {
        if (c < '0') {
            return false;
        }
        return c <= '7';
    }

    public static String toJavaLiteral(char c) {
        if (c == '\n') {
            return "'\\n'";
        }
        if (c == '\t') {
            return "'\\t'";
        }
        if (c == '\r') {
            return "'\\r'";
        }
        if (c < ' ' || c > 127) {
            return "'\\" + Integer.toOctalString(c) + "'";
        }
        return "'" + c + "'";
    }
}
