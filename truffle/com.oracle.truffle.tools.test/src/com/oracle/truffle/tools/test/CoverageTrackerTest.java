/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.TruffleVM;
import com.oracle.truffle.tools.CoverageTracker;
import com.oracle.truffle.tools.test.ToolTestUtil.ToolTestTag;

public class CoverageTrackerTest {

    @Test
    public void testNoExecution() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final TruffleVM vm = TruffleVM.newVM().build();
        final Field field = TruffleVM.class.getDeclaredField("instrumenter");
        field.setAccessible(true);
        final Instrumenter instrumenter = (Instrumenter) field.get(vm);
        final CoverageTracker tool = new CoverageTracker();
        assertEquals(tool.getCounts().entrySet().size(), 0);
        instrumenter.install(tool);
        assertEquals(tool.getCounts().entrySet().size(), 0);
        tool.setEnabled(false);
        assertEquals(tool.getCounts().entrySet().size(), 0);
        tool.setEnabled(true);
        assertEquals(tool.getCounts().entrySet().size(), 0);
        tool.reset();
        assertEquals(tool.getCounts().entrySet().size(), 0);
        tool.dispose();
        assertEquals(tool.getCounts().entrySet().size(), 0);
    }

    void checkCounts(Source source, CoverageTracker coverage, Long[] expectedCounts) {
        final Map<Source, Long[]> countMap = coverage.getCounts();
        assertEquals(countMap.size(), 1);
        final Long[] resultCounts = countMap.get(source);
        assertTrue(Arrays.equals(resultCounts, expectedCounts));
    }

    @Test
    public void testCountingCoverage() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException {
        final TruffleVM vm = TruffleVM.newVM().build();
        final Field field = TruffleVM.class.getDeclaredField("instrumenter");
        field.setAccessible(true);
        final Instrumenter instrumenter = (Instrumenter) field.get(vm);
        final Source source = ToolTestUtil.createTestSource("testCountingCoverage");

        final CoverageTracker valueCoverage = new CoverageTracker(ToolTestTag.VALUE_TAG);
        final CoverageTracker addCoverage = new CoverageTracker(ToolTestTag.ADD_TAG);

        instrumenter.install(valueCoverage);
        assertTrue(valueCoverage.getCounts().isEmpty());

        assertEquals(vm.eval(source).get(), 13);

        checkCounts(source, valueCoverage, new Long[]{Long.valueOf(1), null, Long.valueOf(1), null});

        instrumenter.install(addCoverage);

        assertEquals(vm.eval(source).get(), 13);

        checkCounts(source, valueCoverage, new Long[]{Long.valueOf(2), null, Long.valueOf(2), null});
        checkCounts(source, addCoverage, new Long[]{null, Long.valueOf(1), null, null});

        valueCoverage.setEnabled(false);
        assertEquals(vm.eval(source).get(), 13);

        checkCounts(source, valueCoverage, new Long[]{Long.valueOf(2), null, Long.valueOf(2), null});
        checkCounts(source, addCoverage, new Long[]{null, Long.valueOf(2), null, null});

        valueCoverage.setEnabled(true);
        assertEquals(vm.eval(source).get(), 13);

        checkCounts(source, valueCoverage, new Long[]{Long.valueOf(3), null, Long.valueOf(3), null});
        checkCounts(source, addCoverage, new Long[]{null, Long.valueOf(3), null, null});

        valueCoverage.dispose();
        assertEquals(vm.eval(source).get(), 13);

        checkCounts(source, addCoverage, new Long[]{null, Long.valueOf(4), null, null});

        addCoverage.dispose();
    }
}
