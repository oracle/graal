/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.test.classes;

/**
 * Fixture for entry-points-file tests.
 *
 * The methods in this class are intentionally small and distinct so that an entry-points file can
 * select a precise subset of roots and the test can verify that only the matching helper methods
 * become reachable.
 */
public class AnalysisEntryPointsFileCase {
    /**
     * Nested class whose class initializer is selected through the entry-points file.
     */
    public static class C {
        /**
         * Triggers {@link #doC()} when the class is initialized by analysis.
         */
        static {
            doC();
        }

        /**
         * Reachability marker for the nested-class class initializer entry.
         */
        public static void doC() {
        }
    }

    /**
     * Entry method used by the test to select {@link #doFoo()}.
     */
    public static void foo() {
        doFoo();
    }

    /**
     * Reachability marker for the zero-argument entry method.
     */
    public static void doFoo() {
    }

    /**
     * Overload used by the entry-points file to verify method selection by signature.
     */
    @SuppressWarnings("unused")
    public static void bar(String s) {
        doBar1();
    }

    /**
     * Reachability marker for {@link #bar(String)}.
     */
    public static void doBar1() {
    }

    /**
     * Second overload that should stay unreachable when the entry-points file names only the
     * single-argument variant.
     */
    @SuppressWarnings("unused")
    public static void bar(String s, int i) {
        doBar2();
    }

    /**
     * Reachability marker for {@link #bar(String, int)}.
     */
    public static void doBar2() {
    }

    /**
     * Instance entry method used to verify that non-static entries from the file are honored.
     */
    public void foo1() {
        doFoo1();
    }

    /**
     * Reachability marker for {@link #foo1()}.
     */
    public void doFoo1() {
    }
}
