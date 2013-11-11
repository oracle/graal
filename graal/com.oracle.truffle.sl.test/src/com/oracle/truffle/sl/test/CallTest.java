/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test;

import org.junit.*;

// @formatter:off
public class CallTest extends AbstractTest {

    private static String[] INPUT = new String[] {
        "function ret(a) { return a; } ",
        "function dub(a) { return a * 2; } ",
        "function inc(a) { return a + 1; } ",
        "function dec(a) { return a - 1; } ",
        "function call(f, v) { return f(v); } ",
        "function main {  ",
        "  print(ret(42));",
        "  print(dub(21));",
        "  print(inc(41));",
        "  print(dec(43));",
        "  print(call(ret, 42));",
        "  print(call(dub, 21));",
        "  print(call(inc, 41));",
        "  print(call(dec, 43));",
        "}  ",
    };

    private static String[] OUTPUT = new String[] {
        "42",
        "42",
        "42",
        "42",
        "42",
        "42",
        "42",
        "42",
    };

    @Test
    public void test() {
        executeSL(INPUT, OUTPUT, false);
    }
}
