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
package org.graalvm.bisect.test;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.graalvm.bisect.core.optimization.Optimization;
import org.graalvm.bisect.matching.optimization.OptimizationMatcher;
import org.graalvm.bisect.matching.optimization.SetBasedOptimizationMatcher;
import org.graalvm.bisect.matching.optimization.OptimizationMatching;
import org.graalvm.bisect.core.optimization.OptimizationImpl;
import org.junit.Test;


public class OptimizationMatcherTest {
    @Test
    public void testSetBasedOptimizationMatcher() {
        List<Optimization> optimizations1 = List.of(
                new OptimizationImpl("LoopTransformation", "PartialUnroll", 2, Map.of("unrollFactor", 1)),
                new OptimizationImpl("LoopTransformation", "PartialUnroll", 2, Map.of("unrollFactor", 2)),
                new OptimizationImpl("LoopTransformation", "PartialUnroll", 3, null),
                new OptimizationImpl("LoopTransformation", "Peeling", 5, null)
        );
        List<Optimization> optimizations2 = List.of(
                new OptimizationImpl("LoopTransformation", "PartialUnroll", 2, Map.of("unrollFactor", 1)),
                new OptimizationImpl("LoopTransformation", "PartialUnroll", 5, null)
        );
        OptimizationMatcher matcher = new SetBasedOptimizationMatcher();
        OptimizationMatching matching = matcher.match(optimizations1, optimizations2);
        assertEquals(1, matching.getMatchedOptimizations().size());
        assertEquals(2, matching.getMatchedOptimizations().get(0).getBCI().intValue());
        assertEquals(2, matching.getExtraOptimizations().size(), 4);
    }
}
