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
 * Fixture that drives the richer semantic assertions exposed by {@code StandaloneAnalysisTest}.
 *
 * One analysis run populates field states, parameter states, result states, and invoke dispatch so
 * the test can exercise the shared assertion helpers without repeated setup cost.
 */
public class StandaloneAnalysisAssertionsCase {

    public static Object exactField;
    public static Object nullableField;

    /**
     * Entry point that populates all states observed by the semantic assertion tests.
     */
    public static void main(String[] args) {
        exactField = choose(args.length > 0);
        nullableField = maybeNull(args.length > 0);

        parameterSink(new A(), new C());
        parameterSink(new B(), new D());

        dispatch(new A());
        dispatch(new B());
    }

    /**
     * Produces an exact two-type union for result-state assertions.
     */
    public static Object choose(boolean flag) {
        if (flag) {
            return new A();
        }
        return new B();
    }

    /**
     * Produces a nullable result state for field-state assertions.
     */
    public static Object maybeNull(boolean flag) {
        if (flag) {
            return new A();
        }
        return null;
    }

    /**
     * Records only the first argument so the second one can verify the "not analyzed" assertion
     * path.
     */
    public static void parameterSink(Object value, @SuppressWarnings("unused") Object unusedValue) {
        exactField = value;
    }

    /**
     * Virtual dispatch site used to verify resolved invoke callees and result typing.
     */
    public static Object dispatch(Base value) {
        return value.target();
    }

    /**
     * Common receiver hierarchy for the dispatch assertion.
     */
    public abstract static class Base {
        /**
         * Returns the concrete receiver so dispatch remains visible in the result type state.
         */
        public abstract Object target();
    }

    /**
     * First concrete dispatch target.
     */
    public static class A extends Base {
        /**
         * Returns the concrete receiver instance.
         */
        @Override
        public Object target() {
            return this;
        }
    }

    /**
     * Second concrete dispatch target.
     */
    public static class B extends Base {
        /**
         * Returns the concrete receiver instance.
         */
        @Override
        public Object target() {
            return this;
        }
    }

    /**
     * Marker type used only to verify that the second parameter is left unanalyzed.
     */
    public static class C {
    }

    /**
     * Second marker type used only to verify that the second parameter is left unanalyzed.
     */
    public static class D {
    }
}
