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
 * Fixture for primitive-value tracking and predicate-based reachability in standalone analysis.
 */
public class StandalonePrimitivePredicateCase {

    /**
     * Entry point that drives exact primitive propagation, merged primitive propagation, and two
     * simple predicate eliminations.
     */
    public static void main(String[] args) {
        int exact = get42();
        consumeExact(exact);
        if (exact == 42) {
            reachable1();
        } else {
            unreachable1();
        }

        consumeMerged(choose(saturatedBoolean()));

        Object maybeNull = getNull();
        if (maybeNull == null) {
            reachable2();
        } else {
            unreachable2();
        }
    }

    /**
     * Returns either one of two primitive constants so the merged state becomes imprecise.
     */
    public static int choose(boolean flag) {
        if (flag) {
            return get42();
        }
        return get21();
    }

    public static void consumeExact(@SuppressWarnings("unused") int value) {
    }

    public static void consumeMerged(@SuppressWarnings("unused") int value) {
    }

    public static void reachable1() {
    }

    public static void unreachable1() {
    }

    public static void reachable2() {
    }

    public static void unreachable2() {
    }

    private static int get42() {
        return 42;
    }

    private static int get21() {
        return 21;
    }

    private static int getAnyInt() {
        return get42() + 1;
    }

    private static boolean saturatedBoolean() {
        return getAnyInt() == 42;
    }

    private static Object getNull() {
        return null;
    }
}
