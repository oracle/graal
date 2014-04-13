/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.hsail.test.lambda;

import org.junit.*;

/**
 * Tests calling a method similar to {@code StringUtils.countMatches()} from the Apache commons-lang
 * library. The first argument varies per workitem.
 */
public class StringUtilsCountMatchesTest extends CountMatchesBase {

    public static int countMatches(String str, String sub) {
        if (isEmpty(str) || isEmpty(sub)) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    @Override
    public void runTest() {
        setupArrays();
        String base = "CDE";

        dispatchLambdaKernel(NUM, (gid) -> {
            outArray[gid] = countMatches(inArray[gid], base);
        });
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }

    @Test
    public void testUsingLambdaMethod() {
        testGeneratedHsailUsingLambdaMethod();
    }

}
