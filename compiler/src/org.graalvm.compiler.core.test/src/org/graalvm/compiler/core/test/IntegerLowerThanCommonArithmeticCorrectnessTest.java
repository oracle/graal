/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IntegerLowerThanCommonArithmeticCorrectnessTest extends IntegerLowerThanCommonArithmeticTestBase {

    /** Some interesting values. */
    private static final int[] values = {
                    Integer.MIN_VALUE,
                    -1,
                    0,
                    1,
                    Integer.SIZE,
                    Long.SIZE,
                    Integer.MAX_VALUE,
    };

    /** Some interesting stamps. */
    private static final IntegerStamp[] stamps = {
                    IntegerStamp.create(32, Integer.MIN_VALUE, 0),
                    IntegerStamp.create(32, Integer.MIN_VALUE / 2, 0),
                    IntegerStamp.create(32, 0, Integer.MAX_VALUE),
                    IntegerStamp.create(32, 0, Integer.MAX_VALUE / 2),
    };

    @Parameterized.Parameters(name = "{0} ({3}), {1} ({4}), {2}")
    public static Collection<Object[]> data() {
        List<Object[]> d = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            int x = values[i];
            for (int j = 0; j < values.length; j++) {
                int y = values[j];
                for (int k = 0; k < values.length; k++) {
                    int z = values[k];
                    for (IntegerStamp xStamp : stamps) {
                        for (IntegerStamp yStamp : stamps) {
                            if (xStamp.contains(x) && yStamp.contains(y)) {
                                d.add(new Object[]{x, y, z, xStamp, yStamp});
                            }
                        }
                    }
                }
            }
        }
        return d;
    }

    @Parameterized.Parameter(value = 0) public int x;
    @Parameterized.Parameter(value = 1) public int y;
    @Parameterized.Parameter(value = 2) public int z;
    @Parameterized.Parameter(value = 3) public IntegerStamp xStamp;
    @Parameterized.Parameter(value = 4) public IntegerStamp yStamp;

    @Override
    protected Object[] getBindArgs(Object[] args) {
        return new Object[]{xStamp, yStamp, z};
    }

    public static boolean lessThanAddSnippetInt(int x, int y, int c) {
        if (x + c < y + c) {
            GraalDirectives.controlFlowAnchor();
            return true;
        } else {
            return false;
        }
    }

    @Test
    public void runAdd() {
        runTest("lessThanAddSnippetInt", x, y, z);
    }

    public static boolean lessThanShiftSnippetInt(int x, int y, int c) {
        if (x << c < y << c) {
            GraalDirectives.controlFlowAnchor();
            return true;
        } else {
            return false;
        }
    }

    @Test
    public void runShift() {
        runTest("lessThanShiftSnippetInt", x, y, z);
    }

    public static boolean lessThanAddUnsignedSnippetInt(int x, int y, int c) {
        if (Integer.compareUnsigned(x + c, y + c) < 0) {
            GraalDirectives.controlFlowAnchor();
            return true;
        } else {
            return false;
        }
    }

    @Test
    public void runAddUnsigned() {
        runTest("lessThanAddUnsignedSnippetInt", x, y, z);
    }

    public static boolean lessThanShiftUnsignedSnippetInt(int x, int y, int c) {
        if (Integer.compareUnsigned(x << c, y << c) < 0) {
            GraalDirectives.controlFlowAnchor();
            return true;
        } else {
            return false;
        }
    }

    @Test
    public void runShiftUnsigned() {
        runTest("lessThanShiftUnsignedSnippetInt", x, y, z);
    }
}
