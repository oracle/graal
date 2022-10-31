/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test.ea;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.core.test.TypeSystemTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.nodes.OptimizationLogImpl;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests that the {@link OptimizationLogImpl} collects virtualized allocations and the number of
 * materializations per allocation.
 */
public class PartialEscapeOptimizationLogTest extends EATestBase {
    @Test
    public void test1() {
        testOptimizationLog("test1Snippet", 1, 1);
    }

    public static Object test1Snippet(int a, int b, Object x, Object y) {
        PartialEscapeAnalysisTest.TestObject2 obj = new PartialEscapeAnalysisTest.TestObject2(x, y);
        if (a < 0) {
            if (b < 0) {
                return obj;
            } else {
                return obj.y;
            }
        } else {
            return obj.x;
        }
    }

    @Test
    public void test2() {
        testOptimizationLog("test2Snippet", 2, 3);
    }

    public static Object test2Snippet(int a, Object x, Object y, Object z) {
        PartialEscapeAnalysisTest.TestObject2 obj = new PartialEscapeAnalysisTest.TestObject2(x, y);
        obj.x = new PartialEscapeAnalysisTest.TestObject2(obj, z);
        if (a < 0) {
            ((PartialEscapeAnalysisTest.TestObject2) obj.x).y = null;
            obj.y = null;
            return obj;
        } else {
            ((PartialEscapeAnalysisTest.TestObject2) obj.x).y = Integer.class;
            ((PartialEscapeAnalysisTest.TestObject2) obj.x).x = null;
            return obj.x;
        }
    }

    @Test
    public void test3() {
        testOptimizationLog("test3Snippet", 2, 1);
    }

    @SuppressWarnings("deprecation")
    public static Object test3Snippet(int a) {
        if (a < 0) {
            PartialEscapeAnalysisTest.TestObject obj = new PartialEscapeAnalysisTest.TestObject(1, 2);
            obj.x = 123;
            obj.y = 234;
            obj.x = 123111;
            obj.y = new Integer(123).intValue();
            return obj;
        } else {
            return null;
        }
    }

    @Test
    public void testBoxLoop() {
        testOptimizationLog("testBoxLoopSnippet", 4, 0);
    }

    public static int testBoxLoopSnippet(int n) {
        Integer sum = 0;
        for (Integer i = 0; i < n; i++) {
            if (sum == null) {
                sum = null;
            } else {
                sum += i;
            }
        }
        return sum;
    }

    /**
     * Escape analyzes the snippet and asserts that the optimization log collects the expected
     * number of virtualized allocations and materializations.
     *
     * @param snippet the snippet to be escape analyzed
     * @param allocationsRemoved the expected number of virtualized allocations
     * @param materializations the expected number of materializations
     */
    private void testOptimizationLog(String snippet, int allocationsRemoved, int materializations) {
        prepareGraph(snippet, false);
        try {
            int actualAllocationsRemoved = 0;
            int actualMaterializations = 0;
            for (OptimizationLogImpl.OptimizationEntryImpl entry : graph.getOptimizationLog().getOptimizationTree().getNodes().filter(OptimizationLogImpl.OptimizationEntryImpl.class)) {
                if (entry.getEventName().equals("AllocationVirtualization")) {
                    actualAllocationsRemoved += 1;
                    actualMaterializations += (Integer) entry.getProperties().get("materializations");
                }
            }
            Assert.assertEquals("unexpected number of removed allocations", allocationsRemoved, actualAllocationsRemoved);
            Assert.assertEquals("unexpected number of materialization", materializations, actualMaterializations);
        } catch (AssertionError e) {
            TypeSystemTest.outputGraph(graph, snippet + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * Gets a {@link DebugContext} object corresponding to {@code options} but with the option
     * {@link DebugOptions#OptimizationLog OptimizationLog} enabled, creating a new one if none
     * currently exists. Debug contexts created by this method will have their
     * {@link DebugDumpHandler}s closed in {@link #afterTest()}.
     *
     * @return {@link DebugContext} with the {@link DebugOptions#OptimizationLog OptimizationLog}
     *         option enabled
     */
    @Override
    protected DebugContext getDebugContext() {
        EconomicMap<OptionKey<?>, Object> extraOptions = EconomicMap.create();
        EconomicSet<DebugOptions.OptimizationLogTarget> optimizationLogTargets = EconomicSet.create();
        optimizationLogTargets.add(DebugOptions.OptimizationLogTarget.Stdout);
        extraOptions.put(DebugOptions.OptimizationLog, optimizationLogTargets);
        OptionValues options = new OptionValues(getInitialOptions(), extraOptions);
        return getDebugContext(options, null, null);
    }
}
