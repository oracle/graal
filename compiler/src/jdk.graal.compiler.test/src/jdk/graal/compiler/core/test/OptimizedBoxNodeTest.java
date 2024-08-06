/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.util.ArrayList;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.loop.phases.LoopFullUnrollPhase;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.BoxNodeIdentityPhase;
import jdk.graal.compiler.phases.common.BoxNodeOptimizationPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.util.GraphOrder;
import org.junit.Assert;
import org.junit.Ignore;
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
        // re-use unbox
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
        // can re-use one dominating box node
        parseOptimizeCheck("boxStructural1", 1);
        // can do nothing, the unbox input is a parameter which might not be from valueOf
        // path
        parseOptimizeCheck("boxStructural2", 1);
        // can remove second box
        parseOptimizeCheck("boxStructural3", 1);
    }

    private void parseOptimizeCheck(String boxSnippet, int nrBoxAfter) {
        StructuredGraph g = parseEager(getResolvedJavaMethod(boxSnippet), AllowAssumptions.NO, getInitialOptions());
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        canonicalizer.apply(g, getDefaultHighTierContext());
        new BoxNodeOptimizationPhase(canonicalizer).apply(g, getDefaultHighTierContext());
        Assert.assertTrue(GraphOrder.assertNonCyclicGraph(g));
        Assert.assertEquals("expected number of regular box nodes", nrBoxAfter, g.getNodes().filter(BoxNode.class).count());
    }

    public static Integer intBoxOptimized(Object o) {
        Integer trusted = GraalDirectives.trustedBox((Integer) GraalDirectives.guardingNonNull(o));
        int i = trusted;
        S = trusted;
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
        Long trusted = GraalDirectives.trustedBox((Long) GraalDirectives.guardingNonNull(o));
        long i = trusted;
        S = trusted;
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
        Short trusted = GraalDirectives.trustedBox((Short) GraalDirectives.guardingNonNull(o));
        short i = trusted;
        S = trusted;
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
        Character trusted = GraalDirectives.trustedBox((Character) GraalDirectives.guardingNonNull(o));
        char i = trusted;
        S = trusted;
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

    private InstalledCode compileWithBoxOptimizationPhase(String snippet) {
        StructuredGraph g = parseEager(getResolvedJavaMethod(snippet), AllowAssumptions.YES, getInitialOptions());
        InstalledCode i = getCode(getResolvedJavaMethod(snippet), g);
        return i;
    }

    public void testType(String typePrefix, int lowBound, int highBound, BoxProducer producer, long cacheLow, long cacheHigh) throws InvalidInstalledCodeException {
        final int listLength = Math.abs(lowBound) + highBound;
        ArrayList<Object> integersInterpreter = new ArrayList<>();
        for (int i = -listLength; i < listLength; i++) {
            Object boxed = producer.produceBox(i);
            // cache values in range, rebox on return
            integersInterpreter.add(boxed);
        }

        InstalledCode codeReuseExistingBox = compileWithBoxOptimizationPhase(typePrefix + "BoxOptimized");
        ArrayList<Object> integersReuse = new ArrayList<>();
        for (int i = -listLength; i < listLength; i++) {
            Object boxed = integersInterpreter.get(i + listLength);
            // cache values in range, re-use if out of range
            integersReuse.add(codeReuseExistingBox.executeVarargs(boxed));
        }

        resetCache();

        for (int i = 0; i < integersInterpreter.size(); i++) {
            Object interpreterObject = integersInterpreter.get(i);
            Object objectReuse = integersReuse.get(i);
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
            } else {
                Assert.assertTrue("val=" + originalVal + " out of cache, optimized version must re-use the argument from the call and thus be the same as the interpreter object",
                                interpreterObject == objectReuse);
            }
        }
    }

    static int snippetConstantCompare(int a) {
        int res = a;
        for (int i = -200; i < 200; i++) {
            if (a(i) == b(i + 1)) {
                GraalDirectives.sideEffect(1);
                res = i;
            }
            GraalDirectives.sideEffect(123);
        }
        GraalDirectives.sideEffect(2);
        return res;
    }

    static Integer a(int i) {
        GraalDirectives.sideEffect(3);
        return Integer.valueOf(i);
    }

    static Integer b(int i) {
        GraalDirectives.sideEffect(4);
        return Integer.valueOf(i);
    }

    @Test
    public void testCompare() throws InvalidInstalledCodeException {
        final OptionValues testOptions = new OptionValues(getInitialOptions(), DefaultLoopPolicies.Options.FullUnrollMaxNodes, 10000, DefaultLoopPolicies.Options.ExactFullUnrollMaxNodes, 10000);
        StructuredGraph g = parseEager(getResolvedJavaMethod("snippetConstantCompare"), AllowAssumptions.NO, testOptions);
        new DisableOverflownCountedLoopsPhase().apply(g);
        CanonicalizerPhase.create().apply(g, getDefaultHighTierContext());
        new LoopFullUnrollPhase(createCanonicalizerPhase(), new DefaultLoopPolicies()).apply(g, getDefaultHighTierContext());
        CanonicalizerPhase.create().apply(g, getDefaultHighTierContext());
        Assert.assertEquals("All ifs must be removed", 0, g.getNodes(IfNode.TYPE).count());
        int[] res = new int[200 * 2];
        int[] resCompiled = new int[200 * 2];
        for (int i = -200; i < 200; i++) {
            res[i + 200] = snippetConstantCompare(i);
        }
        InstalledCode code = getCode(getResolvedJavaMethod("snippetConstantCompare"), testOptions);
        for (int i = -200; i < 200; i++) {
            resCompiled[i + 200] = (int) code.executeVarargs(i);
        }
        Assert.assertArrayEquals(res, resCompiled);
    }

    static int testPEASnippet() {
        if (Integer.valueOf(1024) == Integer.valueOf(1024)) {
            return 0;
        }
        return 1;
    }

    @Test
    @Ignore
    public void testPEA() {
        test("testPEASnippet");
    }

    static Integer testNonBoxInput(Integer integer) {
        int i = integer;
        return Integer.valueOf(i);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testBoxCanon() throws InvalidInstalledCodeException {
        final Integer nonCacheZero = new Integer(0);

        final Integer resultInterpreter = testNonBoxInput(nonCacheZero);

        InstalledCode codeNoReuse = getCode(getResolvedJavaMethod("testNonBoxInput"), getInitialOptions());

        final Integer resultGraal = (Integer) codeNoReuse.executeVarargs(nonCacheZero);

        if (nonCacheZero == resultInterpreter) {
            Assert.assertEquals("Objects must be the same", nonCacheZero, resultGraal);
        } else {
            if (nonCacheZero == resultGraal) {
                Assert.fail("Objects must not be the same ctor=" + System.identityHashCode(nonCacheZero) + " boxResult=" + System.identityHashCode(resultGraal));
            }
        }
    }

    public static long maxIntCacheValue() {
        int intCacheMaxValue = -1;
        while (Integer.valueOf(intCacheMaxValue + 1) == Integer.valueOf(intCacheMaxValue + 1)) {
            intCacheMaxValue += 1;
            if (intCacheMaxValue < 0) {
                // Mitigate timeout by terminating here with incorrect answer.
                return -2;
            }
        }
        return intCacheMaxValue;
    }

    public static long minIntCacheValue() {
        int intCacheMinValue = 0;
        while (Integer.valueOf(intCacheMinValue - 1) == Integer.valueOf(intCacheMinValue - 1)) {
            intCacheMinValue -= 1;
            if (intCacheMinValue > 0) {
                // Mitigate timeout by terminating here with incorrect answer.
                return 1;
            }
        }
        return intCacheMinValue;
    }

    public static long maxLongCacheValue() {
        long longCacheMaxValue = -1;
        while (Long.valueOf(longCacheMaxValue + 1) == Long.valueOf(longCacheMaxValue + 1)) {
            longCacheMaxValue += 1;
            if (longCacheMaxValue == Integer.MAX_VALUE) {
                // Mitigate timeout by terminating here with incorrect answer.
                return -2;
            }
        }
        return longCacheMaxValue;
    }

    public static long minLongCacheValue() {
        long longCacheMinValue = 0;
        while (Long.valueOf(longCacheMinValue - 1) == Long.valueOf(longCacheMinValue - 1)) {
            longCacheMinValue -= 1;
            if (longCacheMinValue == Integer.MIN_VALUE) {
                // Mitigate timeout by terminating here with incorrect answer.
                return 1;
            }
        }
        return longCacheMinValue;
    }

    /**
     * A variation on {@link #minLongCacheValue()} where the loop exit is a conditional in the body
     * of the loop.
     */
    public static long minLongCacheValue2() {
        long longCacheMinValue = 0;
        while (true) {
            if (Long.valueOf(longCacheMinValue - 1) != Long.valueOf(longCacheMinValue - 1)) {
                return longCacheMinValue;
            }
            longCacheMinValue -= 1;
        }
    }

    /**
     * Tests {@link BoxNodeIdentityPhase}.
     */
    @Test
    public void testIntCacheMaxProbing() {
        test("maxIntCacheValue");
    }

    /**
     * Tests {@link BoxNodeIdentityPhase}.
     */
    @Test
    public void testIntCacheMinProbing() {
        test("minIntCacheValue");
    }

    /**
     * Tests {@link BoxNodeIdentityPhase}.
     */
    @Test
    public void testLongCacheMaxProbing() {
        test("maxLongCacheValue");
    }

    /**
     * Tests {@link BoxNodeIdentityPhase}.
     */
    @Test
    public void testLongCacheMinProbing() {
        test("minLongCacheValue");
        test("minLongCacheValue2");
    }
}
