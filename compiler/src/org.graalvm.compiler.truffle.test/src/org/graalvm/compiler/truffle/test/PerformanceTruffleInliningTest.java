/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.DefaultInliningPolicy;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.runtime.TruffleInliningDecision;
import org.graalvm.compiler.truffle.runtime.TruffleInliningPolicy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.test.ReflectionUtils;

public class PerformanceTruffleInliningTest extends TruffleInliningTest {

    @Before
    public void before() {
        // Needed to make some tests actually blow the budget
        setupContext("engine.InliningRecursionDepth", "4");
    }

    @Test
    @Ignore("Test needs to be updated it behaved wrongly when knownCallSites was taken into account. See GR-19271.")
    public void testThreeTangledRecursions() {
        // @formatter:off
        OptimizedCallTarget target = builder.
                target("three").
                    calls("three").
                    calls("two").
                    calls("one").
                target("two").
                    calls("two").
                    calls("one").
                    calls("three").
                target("one").
                    calls("one").
                    calls("two").
                    calls("three").
                buildTarget(false);
        // @formatter:on
        assertRootCallsExplored(target, 2);
        assertBudget(target);
    }

    @Test
    @Ignore("Budget assertion is wrong when knownCallSites are taken into account. See See GR-19271.")
    public void testFourTangledRecursions() {
        // @formatter:off
        OptimizedCallTarget target = builder.
                target("four").
                    calls("four").
                    calls("three").
                    calls("two").
                    calls("one").
                target("three").
                    calls("three").
                    calls("two").
                    calls("one").
                    calls("four").
                target("two").
                    calls("two").
                    calls("one").
                    calls("three").
                    calls("four").
                target("one").
                    calls("one").
                    calls("two").
                    calls("three").
                    calls("four").
                buildTarget();
        // @formatter:on
        assertRootCallsExplored(target, 2);
        assertBudget(target);
    }

    @Test
    public void testTangledGraph() {
        int depth = 15;
        for (int i = 0; i < depth; i++) {
            builder.target(Integer.toString(i));
            for (int j = i; j < depth; j++) {
                builder.calls(Integer.toString(j));
            }
        }
        OptimizedCallTarget target = builder.target("main").calls("0").buildTarget();
        assertRootCallsExplored(target, 1);
        assertBudget(target);
    }

    long targetCount = 0;

    private void hugeGraphBuilderHelper(final int depth, final int width, final String targetIndex) {
        builder.target(targetIndex);
        targetCount++;
        if (depth == 0) {
            return;
        }
        for (int i = 0; i < width; i++) {
            builder.calls(targetIndex + i);
        }
        for (int i = 0; i < width; i++) {
            hugeGraphBuilderHelper(depth - 1, width, targetIndex + i);
        }
    }

    @Test
    public void testHugeGraph() {
        hugeGraphBuilderHelper(10, 4, "1");
        OptimizedCallTarget target = builder.target("main").calls("1").buildTarget();
        assertRootCallsExplored(target, 1);
        assertBudget(target);
    }

    private static void assertRootCallsExplored(OptimizedCallTarget target, int explored) {
        final TruffleInlining truffleInliningDecisions = new TruffleInlining(target, POLICY);
        int knowsCallSites = 0;
        for (TruffleInliningDecision decision : truffleInliningDecisions) {
            if (decision.getCallSites().size() > 0) {
                knowsCallSites++;
            }
        }
        // The exploration budget should be exceeded before exploring the other 2 call sites of the
        // root
        Assert.assertEquals("Only one target should not know about it's call sites!", explored, knowsCallSites);
    }

    private static final DefaultInliningPolicy POLICY = new DefaultInliningPolicy();

    private static void assertBudget(OptimizedCallTarget target) {
        int[] visitedNodes = {0};
        int nodeCount = target.getNonTrivialNodeCount();
        try {
            exploreCallSites.invoke(TruffleInlining.class, new ArrayList<>(Arrays.asList(target)), nodeCount, POLICY, visitedNodes, new HashMap<>());
            Assert.assertEquals("Budget not in effect! Too many nodes visited!", 100 * target.getOptionValue(PolyglotCompilerOptions.InliningNodeBudget), visitedNodes[0]);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Assert.assertFalse("Could not invoke exploreCallSites: " + e, true);
        }
    }

    private static Method exploreCallSites = reflectivelyGetExploreMethod();

    private static Method reflectivelyGetExploreMethod() {
        try {
            final Class<?> truffleInliningClass = Class.forName(TruffleInlining.class.getName());
            final Class<?>[] args = {List.class, int.class, TruffleInliningPolicy.class, int[].class, Map.class};
            final Method exploreCallSitesMethod = truffleInliningClass.getDeclaredMethod("exploreCallSites", args);
            ReflectionUtils.setAccessible(exploreCallSitesMethod, true);
            return exploreCallSitesMethod;
        } catch (ClassNotFoundException e) {
            Assert.assertFalse("Could not find TruffleInlining class", true);
            return null;
        } catch (NoSuchMethodException e) {
            Assert.assertFalse("Could not find exploreCallSites method", true);
            return null;
        }
    }
}
