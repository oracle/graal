/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.SpectrePHTMitigations.AllTargets;
import static jdk.graal.compiler.core.common.SpectrePHTMitigations.GuardTargets;
import static jdk.graal.compiler.core.common.SpectrePHTMitigations.Options.SpectrePHTBarriers;
import static jdk.graal.compiler.core.common.SpectrePHTMitigations.Options.SpeculativeExecutionBarriers;
import static org.junit.Assume.assumeTrue;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.core.common.SpectrePHTMitigations;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

public class SpectreFenceTest extends GraalCompilerTest {

    @Before
    public void checkArchSupported() {
        Architecture arch = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getTarget().arch;
        assumeTrue("skipping test on unsupported architecture", arch instanceof AMD64 || arch instanceof AArch64);
    }

    public static long[] Memory = new long[]{1, 2};
    public static double SideEffectD;
    public static double SideEffectL;

    private static final long ARRAY_LONG_BASE_OFFSET = Unsafe.ARRAY_LONG_BASE_OFFSET;

    public static long test1Snippet(double a) {
        final Object m = Memory;
        if (a > 0) {
            UNSAFE.putDouble(m, ARRAY_LONG_BASE_OFFSET, a);
        } else {
            SideEffectL = UNSAFE.getLong(m, ARRAY_LONG_BASE_OFFSET);
        }
        GraalDirectives.controlFlowAnchor();
        return UNSAFE.getLong(m, ARRAY_LONG_BASE_OFFSET);
    }

    public static long test2Snippet(double a) {
        final Object m = Memory;
        if (a > 0) {
            if (GraalDirectives.sideEffect((int) a) == 12) {
                UNSAFE.putDouble(m, ARRAY_LONG_BASE_OFFSET, a);
            } else {
                return Memory[1];
            }
            GraalDirectives.controlFlowAnchor();
        }
        GraalDirectives.controlFlowAnchor();
        return Memory[2];
    }

    public static long test3Snippet(double a) {
        final Object m = Memory;
        if (a > 0) {
            if (GraalDirectives.sideEffect((int) a) == 12) {
                return (long) UNSAFE.getDouble(m, ARRAY_LONG_BASE_OFFSET);
            } else {
                return 1;
            }
        }
        GraalDirectives.controlFlowAnchor();
        return 4;
    }

    static OptionValues getFenceOptions() {
        return new OptionValues(getInitialOptions(), SpectrePHTMitigations.Options.SpectrePHTBarriers, SpectrePHTMitigations.GuardTargets);
    }

    private void assertNumberOfFences(String snip, int fences) {
        int computedFences = 0;
        StructuredGraph g = getFinalGraph(getResolvedJavaMethod(snip), getFenceOptions());
        for (AbstractBeginNode beginNode : g.getNodes(AbstractBeginNode.TYPE)) {
            if (beginNode.hasSpeculationFence()) {
                computedFences++;
            }
            GraalDirectives.controlFlowAnchor();
        }
        Assert.assertEquals("Expected fences", fences, computedFences);
    }

    @Test
    public void test01() {
        test(getFenceOptions(), "test1Snippet", 10D);
        assertNumberOfFences("test1Snippet", 3);
    }

    @Test
    public void test02() {
        test(getFenceOptions(), "test2Snippet", 10D);
        assertNumberOfFences("test2Snippet", 3);
    }

    @Test
    public void test03() {
        test(getFenceOptions(), "test3Snippet", 10D);
        assertNumberOfFences("test3Snippet", 1);
    }

    @Test
    public void testOptionConsistency() throws Exception {
        // If one side is unspecified, the options are synchronized
        OptionValues onlyExecutionBarriers = new OptionValues(getInitialOptions(), SpeculativeExecutionBarriers, true);
        Assert.assertTrue(SpeculativeExecutionBarriers.getValue(onlyExecutionBarriers));
        Assert.assertEquals(SpectrePHTBarriers.getValue(onlyExecutionBarriers), AllTargets);
        OptionValues onlyAllTargets = new OptionValues(getInitialOptions(), SpectrePHTMitigations.Options.SpectrePHTBarriers, AllTargets);
        Assert.assertTrue(SpeculativeExecutionBarriers.getValue(onlyAllTargets));
        Assert.assertEquals(SpectrePHTBarriers.getValue(onlyAllTargets), AllTargets);

        // If both sides are set explicitly, they have to be consistent
        try {
            EconomicMap<OptionKey<?>, Object> optionValues = OptionValues.asMap(SpeculativeExecutionBarriers, false);
            SpectrePHTBarriers.update(optionValues, AllTargets);
            Assert.fail("Inconsistent option values are not prevented");
        } catch (IllegalArgumentException e) {
            // this should happen
        }

        try {
            // If both sides are set explicitly, they have to be consistent
            EconomicMap<OptionKey<?>, Object> optionValues = OptionValues.asMap(SpectrePHTBarriers, GuardTargets);
            SpeculativeExecutionBarriers.update(optionValues, true);
            Assert.fail("Inconsistent option values are not prevented");
        } catch (IllegalArgumentException e) {
            // this should happen
        }

        // Explicitly but consistent is fine
        EconomicMap<OptionKey<?>, Object> optionValues = OptionValues.asMap(SpectrePHTBarriers, AllTargets);
        SpeculativeExecutionBarriers.update(optionValues, true);

    }
}
