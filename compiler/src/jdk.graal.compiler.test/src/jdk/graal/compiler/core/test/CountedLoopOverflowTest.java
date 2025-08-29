/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.Map;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test to verify that overflowing loops are not detected as counted. This is done by using a
 * speculation for the overflow which ensures a subsequent recompile will not detect a loop as
 * counted.
 *
 */
public class CountedLoopOverflowTest extends GraalCompilerTest {

    public static void snippetDown() {
        int i = Integer.MIN_VALUE + 56;
        while (true) {
            if (i > Integer.MIN_VALUE) {
                GraalDirectives.sideEffect(i);
                i = Math.subtractExact(i, 8);
                continue;
            }
            break;
        }
    }

    public static void snippetDownUnsigned() {
        int i = 56;
        while (true) {
            if (GraalDirectives.injectIterationCount(100, Integer.compareUnsigned(i, 8) >= 0)) {
                GraalDirectives.sideEffect(i);
                i = Math.subtractExact(i, 8);
                continue;
            }
            break;
        }
    }

    public static void snippetUp() {
        int i = Integer.MAX_VALUE - 56;
        while (true) {
            if (i < Integer.MAX_VALUE) {
                GraalDirectives.sideEffect(i);
                i = Math.addExact(i, 8);
                continue;
            }
            break;
        }
    }

    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        return OptimisticOptimizations.ALL;
    }

    private static Map<DeoptimizationReason, Integer> getDeoptCounts(ResolvedJavaMethod method) {
        Map<DeoptimizationReason, Integer> deoptCounts = new EnumMap<>(DeoptimizationReason.class);
        ProfilingInfo profile = method.getProfilingInfo();
        for (DeoptimizationReason reason : DeoptimizationReason.values()) {
            deoptCounts.put(reason, profile.getDeoptimizationCount(reason));
        }
        return deoptCounts;
    }

    private static String deoptsToString(ResolvedJavaMethod method, Map<DeoptimizationReason, Integer> deoptCountsBefore) {
        ProfilingInfo profile = method.getProfilingInfo();
        Formatter buf = new Formatter();
        buf.format("Deoptimization Counts for method %s:", method);
        for (DeoptimizationReason reason : DeoptimizationReason.values()) {
            buf.format("%nDeoptimization count for reason %s=%d (vs before %d)", reason, profile.getDeoptimizationCount(reason), deoptCountsBefore.get(reason));
        }
        return buf.toString();
    }

    @Test
    public void testDownOverflow() {
        try {
            for (int i = 0; i < 10000; i++) {
                snippetDown();
            }
        } catch (Throwable t) {
            fail("Caught exception that should not be thrown %s", t.getMessage());
        }
        ResolvedJavaMethod method = getResolvedJavaMethod("snippetDown");
        // first should deopt with a failed speculation, second not
        Map<DeoptimizationReason, Integer> deoptCountsBefore = getDeoptCounts(method);
        try {
            executeActualCheckDeopt(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false), method, CollectionsUtil.setOf(), null);
            deoptCountsBefore = getDeoptCounts(method);
            executeActualCheckDeopt(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false), method, EnumSet.allOf(DeoptimizationReason.class), null);
        } catch (Throwable t) {
            throw new AssertionError(deoptsToString(method, deoptCountsBefore), t);
        }
    }

    @Test
    public void testDownOverflowUnsigned() {
        try {
            for (int i = 0; i < 10000; i++) {
                snippetDownUnsigned();
            }
        } catch (Throwable t) {
            fail("Caught exception that should not be thrown %s", t.getMessage());
        }
        ResolvedJavaMethod method = getResolvedJavaMethod("snippetDownUnsigned");
        // first should deopt with a failed speculation, second not
        Map<DeoptimizationReason, Integer> deoptCountsBefore = getDeoptCounts(method);
        try {
            executeActualCheckDeopt(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false), method, CollectionsUtil.setOf(), null);
            deoptCountsBefore = getDeoptCounts(method);
            executeActualCheckDeopt(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false), method, EnumSet.allOf(DeoptimizationReason.class), null);
        } catch (Throwable t) {
            throw new AssertionError(deoptsToString(method, deoptCountsBefore), t);
        }
    }

    @Test
    public void testUpOverflow() {
        try {
            for (int i = 0; i < 10000; i++) {
                snippetUp();
            }
        } catch (Throwable t) {
            fail("Caught exception that should not be thrown %s", t.getMessage());
        }
        ResolvedJavaMethod method = getResolvedJavaMethod("snippetUp");
        // first should deopt with a failed speculation, second not
        Map<DeoptimizationReason, Integer> deoptCountsBefore = getDeoptCounts(method);
        try {
            executeActualCheckDeopt(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false), method, CollectionsUtil.setOf(), null);
            deoptCountsBefore = getDeoptCounts(method);
            executeActualCheckDeopt(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false), method, EnumSet.allOf(DeoptimizationReason.class), null);
        } catch (Throwable t) {
            throw new AssertionError(deoptsToString(method, deoptCountsBefore), t);
        }
    }
}
