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

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

/**
 * Verifies that compact standalone fixtures can drive analysis through large portions of the JDK
 * when the roots are supplied explicitly through entry-points files.
 */
public class LargeReachabilityTest extends StandaloneAnalysisTest {
    private static final String REACH_5000_ENTRY_POINTS = "/resources/large-reachability/reach-5000-entrypoints.txt";
    private static final String REACH_10000_ENTRY_POINTS = "/resources/large-reachability/reach-10000-entrypoints.txt";
    private static final String REACH_20000_ENTRY_POINTS = "/resources/large-reachability/reach-20000-entrypoints.txt";

    @Test
    public void testReachabilityOver5000Methods() {
        assertReachableMethodLowerBound("reach-5000", REACH_5000_ENTRY_POINTS, 5_000);
    }

    @Test
    public void testReachabilityOver10000Methods() {
        assertReachableMethodLowerBound("reach-10000", REACH_10000_ENTRY_POINTS, 10_000);
    }

    @Test
    public void testReachabilityOver20000Methods() {
        assertReachableMethodLowerBound("reach-20000", REACH_20000_ENTRY_POINTS, 20_000);
    }

    private void assertReachableMethodLowerBound(String scenarioName, String entryPointsResourcePath, long expectedMinimum) {
        runAnalysisWithEntryPointsFile(entryPointsResourcePath);
        long reachableMethods = universe().getMethods().stream().filter(AnalysisMethod::isReachable).count();
        assertTrue("Expected at least " + expectedMinimum + " reachable methods for " + scenarioName + ", but found " + reachableMethods,
                        reachableMethods >= expectedMinimum);
    }

}
