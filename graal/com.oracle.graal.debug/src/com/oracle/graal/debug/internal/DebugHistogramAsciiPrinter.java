/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.debug.internal;

import java.io.*;
import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.debug.DebugHistogram.*;

/**
 * Renders a textual representation of a histogram to a given print stream.
 */
public class DebugHistogramAsciiPrinter implements Printer {

    public static final int NumberSize = 10;
    public static final int DefaultNameSize = 50;
    public static final int DefaultBarSize = 100;

    private PrintStream os;
    private int limit;
    private int nameSize;
    private int barSize;

    public DebugHistogramAsciiPrinter(PrintStream os) {
        this(os, Integer.MAX_VALUE, DefaultNameSize, DefaultBarSize);
    }

    /**
     * @param os where to print
     * @param limit limits printing to the {@code limit} most frequent values
     * @param nameSize the width of the value names column
     * @param barSize the width of the value frequency column
     */
    public DebugHistogramAsciiPrinter(PrintStream os, int limit, int nameSize, int barSize) {
        this.os = os;
        this.limit = limit;
        this.nameSize = nameSize;
        this.barSize = barSize;
    }

    public void print(DebugHistogram histogram) {
        print(histogram.getValues(), histogram.getName());
    }

    public void print(List<CountedValue> list, String name) {
        if (list.isEmpty()) {
            os.printf("%s is empty.%n", name);
            return;
        }

        // Sum up the total number of elements.
        int total = 0;
        for (CountedValue cv : list) {
            total += cv.getCount();
        }

        // Print header.
        os.printf("%s has %d unique elements and %d total elements:%n", name, list.size(), total);

        int max = list.get(0).getCount();
        final int lineSize = nameSize + NumberSize + barSize + 10;
        printLine(os, '-', lineSize);
        String formatString = "| %-" + nameSize + "s | %-" + NumberSize + "d | %-" + barSize + "s |\n";
        for (int i = 0; i < list.size() && i < limit; ++i) {
            CountedValue cv = list.get(i);
            int value = cv.getCount();
            char[] bar = new char[(int) (((double) value / (double) max) * barSize)];
            Arrays.fill(bar, '=');
            String objectString = String.valueOf(cv.getValue());
            if (objectString.length() > nameSize) {
                objectString = objectString.substring(0, nameSize - 3) + "...";
            }
            os.printf(formatString, objectString, value, new String(bar));
        }
        printLine(os, '-', lineSize);
    }

    private static void printLine(PrintStream printStream, char c, int lineSize) {
        char[] charArr = new char[lineSize];
        Arrays.fill(charArr, c);
        printStream.printf("%s%n", new String(charArr));
    }
}
