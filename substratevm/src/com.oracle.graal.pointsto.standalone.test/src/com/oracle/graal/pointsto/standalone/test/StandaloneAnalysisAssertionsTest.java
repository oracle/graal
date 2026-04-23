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

package com.oracle.graal.pointsto.standalone.test;

import org.junit.Test;

import com.oracle.graal.pointsto.standalone.test.classes.StandaloneAnalysisAssertionsCase;

/**
 * Exercises the shared semantic assertion helpers on a single standalone analysis input.
 */
public class StandaloneAnalysisAssertionsTest extends StandaloneAnalysisTest {

    /**
     * Verifies the richer semantic assertions against {@link StandaloneAnalysisAssertionsCase}.
     *
     * The fixture is intentionally shaped to cover four precision signals in one analysis run:
     * field type sets, parameter states, result type sets, and invoke callee resolution. Keeping
     * them together ensures the stronger assertion API stays usable without paying repeated
     * analysis setup costs for the same input.
     */
    @Test
    public void testAssertions() {
        runAnalysis(StandaloneAnalysisAssertionsCase.class);

        assertFieldState();
        assertParameterState();
        assertResultState();
        assertInvokeState();
    }

    /**
     * Checks field type and nullability assertions against the populated static fields in
     * {@link StandaloneAnalysisAssertionsCase}.
     */
    private void assertFieldState() {
        assertFieldTypes(findField(StandaloneAnalysisAssertionsCase.class, "exactField"),
                        StandaloneAnalysisAssertionsCase.A.class,
                        StandaloneAnalysisAssertionsCase.B.class);
        assertFieldTypes(findField(StandaloneAnalysisAssertionsCase.class, "nullableField"),
                        StandaloneAnalysisAssertionsCase.A.class);
        assertFieldCanBeNull(findField(StandaloneAnalysisAssertionsCase.class, "nullableField"));
    }

    /**
     * Checks parameter-state assertions on the helper method that consumes only its first argument.
     */
    private void assertParameterState() {
        var parameterSink = findMethod(StandaloneAnalysisAssertionsCase.class, "parameterSink", Object.class, Object.class);
        assertParameterTypes(parameterSink, 0, StandaloneAnalysisAssertionsCase.A.class, StandaloneAnalysisAssertionsCase.B.class);
        assertParameterNotAnalyzed(parameterSink, 1);
    }

    /**
     * Checks exact result typing for the branchy helper method in
     * {@link StandaloneAnalysisAssertionsCase}.
     */
    private void assertResultState() {
        assertResultTypes(findMethod(StandaloneAnalysisAssertionsCase.class, "choose", boolean.class),
                        StandaloneAnalysisAssertionsCase.A.class,
                        StandaloneAnalysisAssertionsCase.B.class);
    }

    /**
     * Checks invoke-callee resolution for the virtual dispatch exercised by the fixture.
     */
    private void assertInvokeState() {
        var dispatch = findMethod(StandaloneAnalysisAssertionsCase.class, "dispatch", StandaloneAnalysisAssertionsCase.Base.class);
        assertInvokeCallees(dispatch, findOnlyInvokeBci(dispatch),
                        findMethod(StandaloneAnalysisAssertionsCase.A.class, "target"),
                        findMethod(StandaloneAnalysisAssertionsCase.B.class, "target"));
    }
}
