/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.cfs;

import java.util.Map;
import java.util.TreeMap;

public class Histogram extends TreeMap<Integer, Integer> {

    private static final long serialVersionUID = 7188324319057387738L;

    private final String prefix;

    public Histogram(String prefix) {
        this.prefix = prefix;
    }

    public void tick(int bucket) {
        Integer entry = get(bucket);
        put(bucket, entry == null ? 1 : entry + 1);
    }

    public void print() {

        // printing takes time, allow concurrent updates during printing
        Histogram histogram = clone();

        float casesTotal = 0;
        for (int i : histogram.values()) {
            casesTotal += i;
        }
        for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
            int numCases = entry.getValue();
            int percentOut = (int) (numCases / casesTotal * 100);
            String msg = prefix + String.format("%d iters in %4d cases (%2d %%)", entry.getKey(), numCases, percentOut);
            if (entry.getKey() > 3) {
                highlightInRed(msg);
            } else {
                System.out.println(msg);
            }
        }
        System.out.println(prefix + "--------------------------");
    }

    @Override
    public Histogram clone() {
        return (Histogram) super.clone();
    }

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";

    public static void highlightInRed(String msg) {
        System.out.println(ANSI_RED + msg + ANSI_RESET);
    }

}
