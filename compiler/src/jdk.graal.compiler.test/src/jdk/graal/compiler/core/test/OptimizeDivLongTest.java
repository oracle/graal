/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.jtt.JTTTest;

@RunWith(Parameterized.class)
public class OptimizeDivLongTest extends JTTTest {

    private static final String[] methods = {
                    "div",
                    "mod",
                    "divUnsigned",
                    "modUnsigned",
                    "compareUnsigned",
    };

    private static final long[] dividends = {
                    0,
                    1,
                    -1,
                    10,
                    -10,
                    347289,
                    -347289,
                    1068663860,
                    -1068663860,
                    Long.MIN_VALUE,
                    Long.MIN_VALUE + 1,
                    Long.MAX_VALUE,
                    Long.MAX_VALUE - 1,
    };

    private static final long[] divisors = {
                    3,
                    5,
                    7,
                    10,
                    1068663860,
                    -3,
                    -5,
                    -7,
                    -10,
                    -1068663860,
                    Long.MIN_VALUE,
                    Long.MIN_VALUE + 1,
                    Long.MAX_VALUE,
                    Long.MAX_VALUE - 1,
    };

    @Parameters(name = "{1} `{0}` {2}")
    public static Collection<Object[]> data() {
        List<Object[]> ret = new ArrayList<>();
        for (String method : methods) {
            for (long x : dividends) {
                for (long y : divisors) {
                    ret.add(new Object[]{method, x, y});
                }
            }
        }
        return ret;
    }

    @Parameter(value = 0) public String method;
    @Parameter(value = 1) public long x;
    @Parameter(value = 2) public long y;

    private static long a;

    public static long div(long b) {
        return a / b;
    }

    public static long mod(long b) {
        return a % b;
    }

    public static long divUnsigned(long b) {
        return Long.divideUnsigned(a, b);
    }

    public static long modUnsigned(long b) {
        return Long.remainderUnsigned(a, b);
    }

    public static int compareUnsigned(long b) {
        return Long.compareUnsigned(a, b);
    }

    @Test
    public void test() {
        a = x;
        runTest(method, y);
    }
}
