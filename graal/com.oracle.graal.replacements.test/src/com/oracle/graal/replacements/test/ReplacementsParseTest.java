/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test;

import org.junit.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.runtime.*;

public class ReplacementsParseTest extends GraalCompilerTest {

    static class TestMethods {
        static double next(double v) {
            return Math.nextAfter(v, 1.0);
        }

        static double next2(double v) {
            return Math.nextAfter(v, 1.0);
        }

        static double nextAfter(double x, double d) {
            return Math.nextAfter(x, d);
        }

    }

    @ClassSubstitution(TestMethods.class)
    static class TestMethodsSubstitutions {

        @MethodSubstitution(isStatic = true)
        static double next(double v) {
            return TestMethods.next(v);
        }

        @MethodSubstitution(isStatic = true)
        static double next2(double v) {
            return next2(v);
        }

        @MethodSubstitution(isStatic = true)
        static double nextAfter(double x, double d) {
            double xx = (x == -0.0 ? 0.0 : x);
            assert !Double.isNaN(xx);
            return Math.nextAfter(xx, d);
        }
    }

    private static boolean substitutionsInstalled;

    public ReplacementsParseTest() {
        if (!substitutionsInstalled) {
            Replacements replacements = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getProviders().getReplacements();
            replacements.registerSubstitutions(TestMethods.class, TestMethodsSubstitutions.class);
            substitutionsInstalled = true;
        }
    }

    /**
     * Ensure that calling the original method from the substitution binds correctly.
     */
    @Test
    public void test1() {
        test("test1Snippet", 1.0);
    }

    public double test1Snippet(double d) {
        return TestMethods.next(d);
    }

    /**
     * Ensure that calling the substitution method binds to the original method properly.
     */
    @Test
    public void test2() {
        test("test2Snippet", 1.0);
    }

    public double test2Snippet(double d) {
        return TestMethods.next2(d);
    }

    /**
     * Ensure that substitution methods with assertions in them don't complain when the exception
     * constructor is deleted.
     */

    @Test
    public void testNextAfter() {
        double[] inArray = new double[1024];
        double[] outArray = new double[1024];
        for (int i = 0; i < inArray.length; i++) {
            inArray[i] = -0.0;
        }
        test("doNextAfter", inArray, outArray);
    }

    public void doNextAfter(double[] outArray, double[] inArray) {
        for (int i = 0; i < inArray.length; i++) {
            double direction = (i & 1) == 0 ? Double.POSITIVE_INFINITY : -Double.NEGATIVE_INFINITY;
            outArray[i] = TestMethods.nextAfter(inArray[i], direction);
        }
    }
}
