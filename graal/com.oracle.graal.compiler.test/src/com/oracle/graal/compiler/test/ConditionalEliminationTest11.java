/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import org.junit.Ignore;
import org.junit.Test;

import com.oracle.graal.api.directives.GraalDirectives;

/**
 * Collection of tests for
 * {@link com.oracle.graal.phases.common.DominatorConditionalEliminationPhase} including those that
 * triggered bugs in this phase.
 */
public class ConditionalEliminationTest11 extends ConditionalEliminationTestBase {

    @SuppressWarnings("all")
    public static int referenceSnippet(int a) {
        if ((a & 15) != 15) {
            GraalDirectives.deoptimize();
        }
        return 0;
    }

    @Test
    public void test1() {
        testConditionalElimination("test1Snippet", "referenceSnippet");
    }

    @SuppressWarnings("all")
    public static int test1Snippet(int a) {
        if ((a & 8) != 8) {
            GraalDirectives.deoptimize();
        }
        if ((a & 15) != 15) {
            GraalDirectives.deoptimize();
        }
        return 0;
    }

    @SuppressWarnings("all")
    public static int test2Snippet(int a) {
        if ((a & 8) == 0) {
            GraalDirectives.deoptimize();
        }
        if ((a & 15) != 15) {
            GraalDirectives.deoptimize();
        }
        return 0;
    }

    @Test
    public void test2() {
        testConditionalElimination("test2Snippet", "referenceSnippet");
    }

    @SuppressWarnings("all")
    public static int test3Snippet(int a) {
        if ((a & 15) != 15) {
            GraalDirectives.deoptimize();
        }
        if ((a & 8) != 8) {
            GraalDirectives.deoptimize();
        }
        return 0;
    }

    @Test
    public void test3() {
        // Test forward elimination of bitwise tests
        testConditionalElimination("test3Snippet", "referenceSnippet");
    }

    @SuppressWarnings("all")
    public static int test4Snippet(int a) {
        if ((a & 15) != 15) {
            GraalDirectives.deoptimize();
        }
        if ((a & 8) == 0) {
            GraalDirectives.deoptimize();
        }
        return 0;
    }

    @Test
    public void test4() {
        // Test forward elimination of bitwise tests
        testConditionalElimination("test4Snippet", "referenceSnippet");
    }

    @SuppressWarnings("all")
    public static int reference5Snippet(int a) {
        if ((a & 15) == 15) {
            GraalDirectives.deoptimize();
        }
        if (a > 0) {
            a |= 32;
        }
        return a;
    }

    @SuppressWarnings("all")
    public static int test5Snippet(int a) {
        if ((a & 8) != 8) {
            GraalDirectives.deoptimize();
        }
        if (a > 0) {
            a |= 32;
        }
        if ((a & 15) == 15) {
            GraalDirectives.deoptimize();
        }
        return a;
    }

    @Ignore
    @Test
    public void test5() {
        testConditionalElimination("test5Snippet", "test5Snippet");
    }

    @SuppressWarnings("all")
    public static int test6Snippet(int a) {
        if ((a & 8) != 0) {
            GraalDirectives.deoptimize();
        }
        if ((a & 15) != 15) {
            GraalDirectives.deoptimize();
        }
        return 0;
    }

    @SuppressWarnings("all")
    public static int reference6Snippet(int a) {
        if ((a & 8) != 0) {
            GraalDirectives.deoptimize();
        }
        GraalDirectives.deoptimize();
        return 0;
    }

    @Test
    public void test6() {
        // Shouldn't be able to optimize this
        testConditionalElimination("test6Snippet", "reference6Snippet");
    }
}
