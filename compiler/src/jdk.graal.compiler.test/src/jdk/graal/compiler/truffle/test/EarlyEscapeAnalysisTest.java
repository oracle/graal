/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.EarlyEscapeAnalysis;
import com.oracle.truffle.api.CompilerDirectives.EarlyInline;

public class EarlyEscapeAnalysisTest extends PartialEvaluationTest {

    @Test
    public void testEarlyEscapeAnalysis() {
        assertPartialEvalEquals(() -> 42, EarlyEscapeAnalysisTest::testEarlyEscapeBasic);
        assertPartialEvalEquals(() -> 42, EarlyEscapeAnalysisTest::testEarlyEscapeReassign);
        assertPartialEvalEquals(() -> 42, EarlyEscapeAnalysisTest::testEarlyEscapeEarlyInline);

        assertPartialEvalEquals(EarlyEscapeAnalysisTest::earlyNotEscaping1Expected,
                        EarlyEscapeAnalysisTest::earlyNotEscaping1Actual);
    }

    @EarlyEscapeAnalysis
    private static int testEarlyEscapeBasic() {
        TestEscape v = new TestEscape(42);
        CompilerAsserts.partialEvaluationConstant(v.value);
        return v.value;
    }

    @EarlyEscapeAnalysis
    private static int testEarlyEscapeReassign() {
        TestEscape v = new TestEscape(41);
        v.value = 42;
        CompilerAsserts.partialEvaluationConstant(v.value);
        return v.value;
    }

    @EarlyEscapeAnalysis
    private static int testEarlyEscapeEarlyInline() {
        TestEscape v = new TestEscape(41);
        earlyInline(v);
        CompilerAsserts.partialEvaluationConstant(v.value);
        return v.value;
    }

    @EarlyInline
    private static void earlyInline(TestEscape escape) {
        escape.value = 42;
    }

    private static int earlyNotEscaping1Expected() {
        // same as not escape analysed
        TestEscape v = new TestEscape(42);
        escape(v);
        return v.value;
    }

    @EarlyEscapeAnalysis
    private static int earlyNotEscaping1Actual() {
        TestEscape v = new TestEscape(42);
        escape(v); // escapes the value, hence not escape analysied
        return v.value;
    }

    private static void escape(@SuppressWarnings("unused") TestEscape escape) {
    }

    static class TestEscape {

        int value;

        TestEscape(int value) {
            this.value = value;
        }

    }

}
