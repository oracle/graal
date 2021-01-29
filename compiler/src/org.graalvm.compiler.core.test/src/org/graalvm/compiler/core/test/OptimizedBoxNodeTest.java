/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import java.util.ArrayList;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.BoxNodeOptimizationPhase;
import org.graalvm.compiler.phases.util.GraphOrder;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

public class OptimizedBoxNodeTest extends GraalCompilerTest {

    public static Object S;

    public static Integer boxStructural1(Object o) {
        // unbox
        int i = (Integer) o;
        // re-use unbox
        Object o1 = i;
        S = o1;
        // re-use unbox
        return i;
    }

    public static Integer boxStructural2(Object o) {
        // unbox here dominates box below
        int i = (Integer) o;
        S = o;
        // box again with optimized box
        return i;
    }

    public static Integer boxStructural3(int i) {
        Integer box1 = i;
        S = box1;
        // second box can be removed completely
        Integer box2 = i;
        return box2;
    }

    @Test
    public void testStructure() {
        parseOptimizeCheck("boxStructural1", 2, 0);
        parseOptimizeCheck("boxStructural2", 1, 0);
        parseOptimizeCheck("boxStructural3", 0, 1);
    }

    private void parseOptimizeCheck(String boxSnippet, int nrOptimizedAfter, int nrBoxAfter) {
        StructuredGraph g = parseEager(getResolvedJavaMethod(boxSnippet), AllowAssumptions.NO);
        createCanonicalizerPhase().apply(g, getDefaultHighTierContext());
        new BoxNodeOptimizationPhase().apply(g, getDefaultHighTierContext());
        Assert.assertTrue(GraphOrder.assertNonCyclicGraph(g));
        Assert.assertEquals("expected number optimized box nodes", nrOptimizedAfter, g.getNodes().filter(BoxNode.OptimizedAllocatingBoxNode.class).count());
        Assert.assertEquals("expected number of regular box nodes", nrBoxAfter + nrOptimizedAfter, g.getNodes().filter(BoxNode.class).count());
    }

    public static Integer intBoxOptimized(Object o) {
        int i = (Integer) o;
        S = o;
        // box again, reuse if existing
        return i;
    }

    @Test
    public void testInteger() throws InvalidInstalledCodeException {
        testType("int", -1000, 1000, new BoxProducer() {

            @Override
            public Object produceBox(int i) {
                Integer o = i;
                return o;
            }
        }, -128, 127);
    }

    public static Long longBoxOptimized(Object o) {
        long i = (Long) o;
        S = o;
        // box again, reuse if existing
        return i;
    }

    @Test
    public void testLong() throws InvalidInstalledCodeException {
        testType("long", -1000, 1000, new BoxProducer() {

            @Override
            public Object produceBox(int i) {
                Long o = (long) i;
                return o;
            }
        }, -128, 127);
    }

    public static Short shortBoxOptimized(Object o) {
        short i = (Short) o;
        S = o;
        // box again, reuse if existing
        return i;
    }

    @Test
    public void testShort() throws InvalidInstalledCodeException {
        testType("short", -1000, 1000, new BoxProducer() {

            @Override
            public Object produceBox(int i) {
                Short o = (short) i;
                return o;
            }
        }, -128, 127);
    }

    public static Character charBoxOptimized(Object o) {
        char i = (char) o;
        S = o;
        // box again, reuse if existing
        return i;
    }

    @Test
    public void testChar() throws InvalidInstalledCodeException {
        testType("char", 0, 1000, new BoxProducer() {

            @Override
            public Object produceBox(int i) {
                Character o = (char) i;
                return o;
            }
        }, 0, 127);
    }

    @FunctionalInterface
    interface BoxProducer {
        Object produceBox(int i);
    }

    public void testType(String typePrefix, int lowBound, int highBound, BoxProducer producer, long cacheLow, long cacheHigh) throws InvalidInstalledCodeException {
        final int listLength = Math.abs(lowBound) + highBound;
        ArrayList<Object> integersInterpreter = new ArrayList<>();
        for (int i = -listLength; i < listLength; i++) {
            Object boxed = producer.produceBox(i);
            // cache values in range, rebox on return
            integersInterpreter.add(boxed);
        }

        InstalledCode codeReuseExistingBox = getCode(getResolvedJavaMethod(typePrefix + "BoxOptimized"),
                        new OptionValues(getInitialOptions(), BoxNodeOptimizationPhase.Options.ReuseOutOfCacheBoxedValues, true));
        ArrayList<Object> integersReuse = new ArrayList<>();
        for (int i = -listLength; i < listLength; i++) {
            Object boxed = integersInterpreter.get(i + listLength);
            // cache values in range, re-use if out of range
            integersReuse.add(codeReuseExistingBox.executeVarargs(boxed));
        }

        resetCache();

        ArrayList<Object> integersNoReuse = new ArrayList<>();
        InstalledCode codeNoReuse = getCode(getResolvedJavaMethod(typePrefix + "BoxOptimized"),
                        new OptionValues(getInitialOptions(), BoxNodeOptimizationPhase.Options.ReuseOutOfCacheBoxedValues, false));
        for (int i = -listLength; i < listLength; i++) {
            Object boxed = integersInterpreter.get(i + listLength);
            // cache values in range, re-use if out of range
            integersNoReuse.add(codeNoReuse.executeVarargs(boxed));
        }

        for (int i = 0; i < integersInterpreter.size(); i++) {
            Object interpreterObject = integersInterpreter.get(i);
            Object objectReuse = integersReuse.get(i);
            Object objectNoReuse = integersNoReuse.get(i);
            long originalVal;
            if (interpreterObject instanceof Character) {
                originalVal = ((Character) interpreterObject);
            } else {
                Number n = (Number) interpreterObject;
                originalVal = n.longValue();
            }
            if (originalVal >= cacheLow && originalVal <= cacheHigh) {
                // in cache, all must be the same objects
                Assert.assertTrue("val=" + originalVal + " optimized version must remain cached object identities", interpreterObject == objectReuse);
                Assert.assertTrue("val=" + originalVal + " unoptimized version must remain cached object identities", interpreterObject == objectNoReuse);
            } else {
                Assert.assertTrue("val=" + originalVal + " out of cache, unoptimized version must not reuse the argument from the call and thus be different than the interpreter object",
                                interpreterObject != objectNoReuse);
                Assert.assertTrue("val=" + originalVal + " out of cache, optimized version must re-use the argument from the call and thus be the same as the interpreter object",
                                interpreterObject == objectReuse);
            }
        }

    }
}
