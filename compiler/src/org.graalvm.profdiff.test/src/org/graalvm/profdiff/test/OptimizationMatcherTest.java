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
package org.graalvm.profdiff.test;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.matching.optimization.OptimizationMatcher;
import org.graalvm.profdiff.matching.optimization.OptimizationMatching;
import org.graalvm.profdiff.matching.optimization.SetBasedOptimizationMatcher;
import org.graalvm.collections.EconomicMap;
import org.junit.Test;

public class OptimizationMatcherTest {
    @Test
    public void testSetBasedOptimizationMatcher() {
        Optimization common1 = new Optimization("foo", "bar", EconomicMap.of("method", 1), EconomicMap.of("prop", 1));
        Optimization common2 = new Optimization("foo", "bar", EconomicMap.of("method", 1), EconomicMap.of("prop", 2));
        Optimization common1Clone = new Optimization("foo", "bar", EconomicMap.of("method", 1), EconomicMap.of("prop", 1));
        Optimization common2Clone = new Optimization("foo", "bar", EconomicMap.of("method", 1), EconomicMap.of("prop", 2));

        Optimization extra1 = new Optimization("foo", "bar", EconomicMap.of("method", 1), null);
        Optimization extra2 = new Optimization("foo", "bar", EconomicMap.of("method", 2), EconomicMap.of("prop", 1));
        Optimization extra3 = new Optimization("foo", "baz", EconomicMap.of("method", 1), EconomicMap.of("prop", 1));
        Optimization extra4 = new Optimization("baz", "bar", EconomicMap.of("method", 1), EconomicMap.of("prop", 1));

        List<Optimization> optimizations1 = List.of(extra1, common1, extra2, common2);
        List<Optimization> optimizations2 = List.of(common1Clone, common2Clone, extra3, extra4);

        List<Optimization> common = List.of(common1, common2);
        List<Optimization> extraLhs = List.of(extra1, extra2);
        List<Optimization> extraRhs = List.of(extra3, extra4);

        OptimizationMatcher matcher = new SetBasedOptimizationMatcher();
        OptimizationMatching matching = matcher.match(optimizations1, optimizations2);
        assertEquals(common, matching.getMatchedOptimizations());
        assertEquals(extraLhs, matching.getUnmatchedOptimizations(ExperimentId.ONE));
        assertEquals(extraRhs, matching.getUnmatchedOptimizations(ExperimentId.TWO));
    }
}
