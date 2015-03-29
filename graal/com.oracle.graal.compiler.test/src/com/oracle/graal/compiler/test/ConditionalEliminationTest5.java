/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.*;

/**
 * Collection of tests for
 * {@link com.oracle.graal.phases.common.DominatorConditionalEliminationPhase} including those that
 * triggered bugs in this phase.
 */
public class ConditionalEliminationTest5 extends ConditionalEliminationTestBase {

    interface A {
    }

    interface B extends A {
    }

    static final class DistinctA {
    }

    static final class DistinctB {
    }

    @SuppressWarnings("all")
    public static int reference1Snippet(A a, B b) {
        if (a instanceof B) {
            return 1;
        }
        return 2;
    }

    @SuppressWarnings("all")
    public static int test1Snippet(A a, B b) {
        if (a instanceof B) {
            if (a instanceof A) {
                return 1;
            }
        }
        return 2;
    }

    @Test
    public void test1() {
        testConditionalElimination("test1Snippet", "reference1Snippet");
    }

    public static int reference2Snippet(A a) {
        if (a instanceof B) {
            return 1;
        }
        return 2;
    }

    public static int test2Snippet(A a) {
        if (a instanceof B) {
            B newVal = (B) a;
            if (newVal != null) {
                return 1;
            }
        }
        return 2;
    }

    @Test
    public void test2() {
        testConditionalElimination("test2Snippet", "reference2Snippet");
    }

    @SuppressWarnings("unused")
    public static int reference3Snippet(Object a, Object b) {
        if (a instanceof DistinctA) {
            DistinctA proxyA = (DistinctA) a;
            if (b instanceof DistinctB) {
                return 1;
            }
        }
        return 2;
    }

    @SuppressWarnings("all")
    public static int test3Snippet(Object a, Object b) {
        if (a instanceof DistinctA) {
            DistinctA proxyA = (DistinctA) a;
            if (b instanceof DistinctB) {
                if (proxyA == b) {
                    return 42;
                }
                return 1;
            }
        }
        return 2;
    }

    @Test
    public void test3() {
        testConditionalElimination("test3Snippet", "reference3Snippet", true);
    }
}
