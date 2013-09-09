/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.debug.test;

import java.io.*;

import org.junit.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;

public class DebugHistogramTest {

    @Test
    public void testEmptyHistogram() {
        DebugHistogram histogram = Debug.createHistogram("TestHistogram");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        new DebugHistogramAsciiPrinter(new PrintStream(outputStream)).print(histogram);
        Assert.assertEquals("TestHistogram is empty.\n", outputStream.toString());
    }

    @Test
    public void testSingleEntryHistogram() {
        DebugHistogram histogram = Debug.createHistogram("TestHistogram");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        histogram.add(new Integer(1));
        histogram.add(new Integer(1));
        new DebugHistogramAsciiPrinter(new PrintStream(outputStream)).print(histogram);
        String[] lines = outputStream.toString().split("\n");
        Assert.assertEquals(4, lines.length);
        Assert.assertEquals("TestHistogram has 1 unique elements and 2 total elements:", lines[0]);
        Assert.assertEquals(
                        "--------------------------------------------------------------------------------------------------------------------------------------------------------------------------",
                        lines[1]);
        Assert.assertEquals(
                        "| 1                                                  | 2          | ==================================================================================================== |",
                        lines[2]);
        Assert.assertEquals(
                        "--------------------------------------------------------------------------------------------------------------------------------------------------------------------------",
                        lines[3]);
    }

    @Test
    public void testMultipleEntryHistogram() {
        DebugHistogram histogram = Debug.createHistogram("TestHistogram");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        histogram.add(new Integer(1));
        histogram.add(new Integer(2));
        histogram.add(new Integer(2));
        new DebugHistogramAsciiPrinter(new PrintStream(outputStream)).print(histogram);
        String[] lines = outputStream.toString().split("\n");
        Assert.assertEquals(5, lines.length);
        Assert.assertEquals("TestHistogram has 2 unique elements and 3 total elements:", lines[0]);
        Assert.assertEquals(
                        "--------------------------------------------------------------------------------------------------------------------------------------------------------------------------",
                        lines[1]);
        Assert.assertEquals(
                        "| 2                                                  | 2          | ==================================================================================================== |",
                        lines[2]);
        Assert.assertEquals(
                        "| 1                                                  | 1          | ==================================================                                                   |",
                        lines[3]);
        Assert.assertEquals(
                        "--------------------------------------------------------------------------------------------------------------------------------------------------------------------------",
                        lines[4]);
    }

    @Test
    public void testTooLongValueString() {
        DebugHistogram histogram = Debug.createHistogram("TestHistogram");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        histogram.add("MyCustomValue");
        new DebugHistogramAsciiPrinter(new PrintStream(outputStream), Integer.MAX_VALUE, 10, 10).print(histogram);
        String[] lines = outputStream.toString().split("\n");
        Assert.assertEquals(4, lines.length);
        Assert.assertEquals("TestHistogram has 1 unique elements and 1 total elements:", lines[0]);
        Assert.assertEquals("----------------------------------------", lines[1]);
        Assert.assertEquals("| MyCusto... | 1          | ========== |", lines[2]);
        Assert.assertEquals("----------------------------------------", lines[3]);
    }
}
