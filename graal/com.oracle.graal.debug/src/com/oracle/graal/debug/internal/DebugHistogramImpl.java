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

public class DebugHistogramImpl implements DebugHistogram {

    public static final int NumberSize = 10;
    public static final int DefaultNameSize = 50;
    public static final int DefaultBarSize = 100;
    private final String name;
    private HashMap<Object, Integer> map = new HashMap<>();

    public DebugHistogramImpl(String name) {
        this.name = name;
    }

    public void add(Object value) {
        if (!map.containsKey(value)) {
            map.put(value, 1);
        } else {
            map.put(value, map.get(value) + 1);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void print(PrintStream os) {
        print(os, Integer.MAX_VALUE, DefaultNameSize, DefaultBarSize);
    }

    public void print(PrintStream os, int limit, int nameSize, int barSize) {

        List<Object> list = new ArrayList<>(map.keySet());
        if (list.size() == 0) {
            // No elements in the histogram.
            os.printf("%s is empty.\n", name);
            return;
        }

        // Sort from highest to smallest.
        Collections.sort(list, new Comparator<Object>() {

            @Override
            public int compare(Object o1, Object o2) {
                return map.get(o2) - map.get(o1);
            }
        });

        // Sum up the total number of elements.
        int total = 0;
        for (Object o : list) {
            total += map.get(o);
        }

        // Print header.
        os.printf("%s has %d unique elements and %d total elements:\n", name, list.size(), total);

        int max = map.get(list.get(0));
        final int lineSize = nameSize + NumberSize + barSize + 10;
        printLine(os, '-', lineSize);
        String formatString = "| %-" + nameSize + "s | %-" + NumberSize + "d | %-" + barSize + "s |\n";
        for (int i = 0; i < list.size() && i < limit; ++i) {
            Object o = list.get(i);
            int value = map.get(o);
            char[] bar = new char[(int) (((double) value / (double) max) * barSize)];
            Arrays.fill(bar, '=');
            os.printf(formatString, o, value, new String(bar));
        }
        printLine(os, '-', lineSize);
    }

    private static void printLine(PrintStream printStream, char c, int lineSize) {
        char[] charArr = new char[lineSize];
        Arrays.fill(charArr, c);
        printStream.printf("%s\n", new String(charArr));
    }
}
