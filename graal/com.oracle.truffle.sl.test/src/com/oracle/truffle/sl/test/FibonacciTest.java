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

import javax.script.*;

import org.junit.*;

import com.oracle.truffle.sl.*;
import com.oracle.truffle.sl.runtime.*;

// @formatter:off
public class FibonacciTest extends AbstractTest{

    private static String[] INPUT = new String[] {
        "function fib(num) { ",
        "  if (num < 1) {return 0;}",
        "  n1 = 0;",
        "  n2 = 1;",
        "  i = 1;",
        "  while (i < num) {",
        "    next = n2 + n1;",
        "    n1 = n2;",
        "    n2 = next;",
        "    i = i + 1;",
        "  }",
        "  return n2;",
        "}",
        "function main(num) {  ",
        "  return fib(num);",
        "}  ",
    };

    // java reference
    private static int test(int num) {
        if (num <= 0) {
            return 0;
        }
        int n1 = 0;
        int n2 = 1;
        for (int i = 1; i < num; i++) {
            final int next = n2 + n1;
            n1 = n2;
            n2 = next;
        }
        return n2;
    }

    private static final int TEST_VALUE = 42;
    private static final int ITERATIONS = 5000;

    @Test
    public void test() throws ScriptException {
        StringBuilder s = new StringBuilder();
        for (String line : INPUT) {
            s.append(line).append("\n");
        }
        SLScript script = SLScript.create(new SLContext(System.out), s.toString());
        Integer reference = test(TEST_VALUE);
        for (int i = 0; i < ITERATIONS; i++) {
            if (!reference.equals(script.run(TEST_VALUE))) {
                throw new AssertionError();
            }
        }
    }
}
