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

package com.oracle.graal.pointsto.standalone.test;

import org.junit.Test;

import com.oracle.graal.pointsto.standalone.test.classes.StandaloneAnalyzerReachabilityCase;
import com.oracle.graal.pointsto.standalone.test.classes.StandaloneAnalyzerReachabilityCase.C;
import com.oracle.graal.pointsto.standalone.test.classes.StandaloneAnalyzerReachabilityCase.C1;
import com.oracle.graal.pointsto.standalone.test.classes.StandaloneAnalyzerReachabilityCase.C2;
import com.oracle.graal.pointsto.standalone.test.classes.StandaloneAnalyzerReachabilityCase.D;

/**
 * Verifies reachable and unreachable analysis elements for
 * {@link StandaloneAnalyzerReachabilityCase}.
 */
public class StandaloneAnalyzerReachabilityTest extends StandaloneAnalysisTest {

    /**
     * Verifies that analyzing {@link StandaloneAnalyzerReachabilityCase} preserves the original
     * reachable and unreachable class/field split.
     *
     * The test checks that the reachable branch keeps {@link C}, {@link C1}, {@link C2}, and
     * {@link C#val}, while {@link D} and {@link D#val} remain unreachable.
     */
    @Test
    public void testPointstoAnalyzer() {
        runAnalysis(StandaloneAnalyzerReachabilityCase.class);
        Class<StandaloneAnalyzerReachabilityCase.C> classC = StandaloneAnalyzerReachabilityCase.C.class;
        Class<StandaloneAnalyzerReachabilityCase.D> classD = StandaloneAnalyzerReachabilityCase.D.class;
        assertReachable(findClass(classC));
        assertReachable(findClass(StandaloneAnalyzerReachabilityCase.C1.class));
        assertReachable(findClass(StandaloneAnalyzerReachabilityCase.C2.class));
        assertNotReachable(findClass(classD));
        assertReachable(findField(classC, "val"));
        assertNotReachable(findField(classD, "val"));
    }

    /**
     * Verifies that repeatedly analyzing {@link StandaloneAnalyzerReachabilityCase} on the same
     * test instance still produces stable results.
     *
     * This exists to guard the setup and cleanup paths in {@link StandaloneAnalysisTest}: each
     * iteration must reset the previous analysis state instead of leaking results across runs.
     */
    @Test
    public void testMultipleAnalysis() {
        int times = 5;
        int i = 0;
        while (i++ < times) {
            testPointstoAnalyzer();
        }
    }
}
