/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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
 * Fixture for basic reachability checks.
 *
 * The reachable branch instantiates only the {@code C} hierarchy, while {@link D} stays unreachable
 * so the test can assert both positive and negative reachability facts.
 */
public class StandaloneAnalyzerReachabilityCase {
    /**
     * Entry point that selects between the two reachable subclasses and never touches {@link D}.
     */
    public static void main(String[] args) {
        Object c;
        if (args.length > 0) {
            c = new C1("C1");
        } else {
            c = new C2("C2");
        }
        c.toString();
    }

    /**
     * Reachable abstract base class whose field should also become reachable.
     */
    public abstract static class C {
        public String val;

        /**
         * Returns the stored value so both subclasses contribute the same observable behavior.
         */
        @Override
        public abstract String toString();
    }

    /**
     * First reachable subclass.
     */
    public static class C1 extends C {
        /**
         * Initializes the inherited reachable field.
         */
        public C1(String v) {
            val = v;
        }

        /**
         * Returns the stored constructor value.
         */
        @Override
        public String toString() {
            return val;
        }
    }

    /**
     * Second reachable subclass.
     */
    public static class C2 extends C {
        /**
         * Initializes the inherited reachable field.
         */
        public C2(String v) {
            val = v;
        }

        /**
         * Returns the stored constructor value.
         */
        @Override
        public String toString() {
            return val;
        }
    }

    /**
     * Unreachable comparison type used to verify negative reachability assertions.
     */
    public static class D {
        @SuppressWarnings("unused") public String val;

        /**
         * Produces an observable side effect if this unreachable type were ever analyzed.
         */
        @Override
        public String toString() {
            return val = "D";
        }
    }
}
