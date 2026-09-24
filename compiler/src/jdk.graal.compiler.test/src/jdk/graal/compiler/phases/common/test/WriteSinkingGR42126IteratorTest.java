/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.common.test;

import static org.junit.Assume.assumeTrue;

import java.util.Iterator;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.phases.common.writesinking.WriteSinkingPhase;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.phases.LowTier;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.FixReadsPhase;
import jdk.graal.compiler.phases.common.WriteBarrierAdditionPhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class WriteSinkingGR42126IteratorTest extends GraalCompilerTest {

    @Before
    public void checkOptions() {
        assumeTrue(GraalOptions.OptFloatingReads.getValue(reproducerOptions()));
    }

    public static int arrayIteratorSnippet(Object[] values) {
        int res = 0;
        Iterator<Object> iterator = new ArrayIteratorTest(values);
        while (iterator.hasNext()) {
            Object n = iterator.next();
            res += n.hashCode();
        }
        return res;
    }

    static final class ArrayIteratorTest implements Iterator<Object> {
        final Object[] values;
        int i;

        ArrayIteratorTest(Object[] values) {
            this.values = values;
        }

        @Override
        public boolean hasNext() {
            forward();
            return i < values.length;
        }

        @Override
        public Object next() {
            Object value = values[i];
            i++;
            while (i < values.length && values[i] == null) {
                i++;
            }
            return value;
        }

        private void forward() {
            while (i < values.length && values[i] == null) {
                i++;
            }
        }
    }

    @Test
    public void iteratorSkipsNullsWithLowTierWriteSinking() {
        ResolvedJavaMethod method = getResolvedJavaMethod("arrayIteratorSnippet");
        Result expected = executeExpected(method);
        Result actual = executeActual(method);
        Assert.assertEquals(Integer.valueOf(1), expected.returnValue);
        assertEquals(expected, actual);
    }

    @Test
    public void writeSinkingRunsAfterFixReadsBeforeWriteBarriers() {
        Suites suites = createSuites(reproducerOptions());
        int fixReadsIndex = lowTierPhaseIndex(suites, FixReadsPhase.class);
        int writeSinkingIndex = lowTierPhaseIndex(suites, WriteSinkingPhase.class);
        int writeBarrierIndex = lowTierPhaseIndex(suites, WriteBarrierAdditionPhase.class);

        Assert.assertTrue(writeSinkingIndex > fixReadsIndex);
        Assert.assertTrue(writeSinkingIndex < writeBarrierIndex);
    }

    @Test
    public void writeSinkingCanBeDisabled() {
        Suites suites = createSuites(reproducerOptionsWithoutWriteSinking());
        Assert.assertFalse(hasLowTierPhase(suites, WriteSinkingPhase.class));
    }

    private Result executeExpected(ResolvedJavaMethod method) {
        Object receiver = method.isStatic() ? null : this;
        return executeExpected(method, receiver, newValuesSupplier());
    }

    private Result executeActual(ResolvedJavaMethod method) {
        Object receiver = method.isStatic() ? null : this;
        return executeActual(reproducerOptions(), method, receiver, newValuesSupplier());
    }

    private static GraalCompilerTest.ArgSupplier newValuesSupplier() {
        return () -> new Object[]{null, 1};
    }

    private static int lowTierPhaseIndex(Suites suites, Class<?> phaseClass) {
        List<BasePhase<? super LowTierContext>> phases = suites.getLowTier().getPhases();
        for (int i = 0; i < phases.size(); i++) {
            if (phaseClass.isInstance(phases.get(i))) {
                return i;
            }
        }
        Assert.fail("Could not find low-tier phase " + phaseClass.getName());
        return -1;
    }

    private static boolean hasLowTierPhase(Suites suites, Class<?> phaseClass) {
        List<BasePhase<? super LowTierContext>> phases = suites.getLowTier().getPhases();
        for (BasePhase<? super LowTierContext> phase : phases) {
            if (phaseClass.isInstance(phase)) {
                return true;
            }
        }
        return false;
    }

    private static OptionValues reproducerOptions() {
        return getInitialOptions();
    }

    private static OptionValues reproducerOptionsWithoutWriteSinking() {
        OptionValues options = reproducerOptions();
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create(options.getMap());
        map.put(LowTier.Options.OptWriteSinking, false);
        return new OptionValues(options, map);
    }
}
