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

import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

public class ConstantTripCountQueryTest extends GraalCompilerTest {

    public static long invertedLoopWithNarrowedExit(int n) {
        long sum = 1;
        long counter = 0;
        do {
            if ((int) counter == 5) {
                break;
            }
            sum += counter;
            counter++;
        } while (counter <= (n & 15));
        return sum * 31 + counter * 7;
    }

    @Test
    public void testInvertedLoopWithNarrowedExit() throws InvalidInstalledCodeException {
        // Compile before evaluating the Java reference so profiling cannot hide the failure.
        InstalledCode code = getCode(getResolvedJavaMethod("invertedLoopWithNarrowedExit"), null, true, false, getInitialOptions());
        for (int n : new int[]{0, 5}) {
            Assert.assertEquals("Result for limit " + n, invertedLoopWithNarrowedExit(n), (long) code.executeVarargs(n));
        }
    }
}
