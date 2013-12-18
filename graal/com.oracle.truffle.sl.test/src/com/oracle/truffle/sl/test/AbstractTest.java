/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;

import org.junit.*;

import com.oracle.truffle.sl.*;

public class AbstractTest {

    public static final int REPEATS = 10;
    private static final String NEWLINE = System.getProperty("line.separator");

    private static String concat(String[] string) {
        StringBuilder result = new StringBuilder();
        for (String s : string) {
            result.append(s).append(NEWLINE);
        }
        return result.toString();
    }

    private static String repeat(String s, int count) {
        StringBuilder result = new StringBuilder(s.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(s);
        }
        return result.toString();
    }

    protected static void executeSL(String[] input, String[] expectedOutput, boolean useConsole) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream printer = new PrintStream(useConsole ? new SplitOutputStream(out, System.err) : out);
        PrintStream origErr = System.err;
        System.setErr(printer);

        SimpleLanguage.run("(test)", concat(input), printer, REPEATS, false);

        System.setErr(origErr);
        Assert.assertEquals(repeat(concat(expectedOutput), REPEATS), new String(out.toByteArray()));
    }
}
