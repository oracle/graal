/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package org.graalvm.compiler.hotspot.test;

import static jdk.vm.ci.meta.DeoptimizationReason.BoundsCheckException;
import static jdk.vm.ci.meta.DeoptimizationReason.LoopLimitCheck;
import static org.graalvm.compiler.core.common.GraalOptions.LoopPredication;
import static org.graalvm.compiler.core.common.GraalOptions.LoopPredicationMainPath;
import static org.graalvm.compiler.core.common.GraalOptions.SpeculativeGuardMovement;

import java.util.HashSet;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.phases.LoopPredicationPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

public class RangeCheckPredicatesTest extends GraalCompilerTest {
    @SuppressWarnings("unused") private static int volatileField;
    final HotSpotVMConfigStore configStore = HotSpotJVMCIRuntime.runtime().getConfigStore();
    final HotSpotVMConfigAccess access = new HotSpotVMConfigAccess(configStore);
    final boolean useJVMCICompiler = access.getFlag("UseJVMCICompiler", Boolean.class);

    private final SpeculationLog speculationLog;

    @Override
    protected SpeculationLog getSpeculationLog() {
        speculationLog.collectFailedSpeculations();
        return speculationLog;
    }

    /**
     * Initializes the overrides for the tests in this class which are written specifically for the
     * graph shapes produced by {@link LoopPredicationPhase}.
     */
    private static EconomicMap<OptionKey<?>, Object> initOverrides() {
        EconomicMap<OptionKey<?>, Object> overrides = OptionValues.newOptionMap();
        overrides.put(SpeculativeGuardMovement, false);
        overrides.put(LoopPredication, true);
        return overrides;
    }

    private static OptionValues getOptionsMainPath() {
        return new OptionValues(getInitialOptions(), initOverrides());
    }

    private static OptionValues getOptionsAllPaths() {
        EconomicMap<OptionKey<?>, Object> overrides = initOverrides();
        overrides.put(LoopPredicationMainPath, false);
        return new OptionValues(getInitialOptions(), overrides);
    }

    public RangeCheckPredicatesTest() {
        speculationLog = getCodeCache().createSpeculationLog();
    }

    private void runOutOfBound(String methodName, int size, boolean loopLimitCheck, Object... testParameters) {
        final ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        final int[] array = new int[size];

        ProfilingInfo profile = method.getProfilingInfo();
        int deoptimizationCountBoundsCheck = profile.getDeoptimizationCount(BoundsCheckException);
        int deoptimizationCountLoopLimitCheck = profile.getDeoptimizationCount(LoopLimitCheck);
        int extraBoundsCheck;
        int extraLoopLimitCheck;
        if (!loopLimitCheck) {
            // Running with UseJVMCICompiler off causes hotspot to account for trap twice
            extraBoundsCheck = useJVMCICompiler ? 1 : 2;
            extraLoopLimitCheck = 0;
        } else {
            extraBoundsCheck = useJVMCICompiler ? 0 : 1;
            extraLoopLimitCheck = 1;
        }
        Object[] args = new Object[testParameters.length + 1];
        args[0] = array;
        System.arraycopy(testParameters, 0, args, 1, testParameters.length);
        Result result = executeActual(getOptionsMainPath(), method, null, args);
        Assert.assertNotNull(result.exception);
        Assert.assertTrue(result.exception instanceof ArrayIndexOutOfBoundsException);
        profile = method.getProfilingInfo();
        Assert.assertEquals(deoptimizationCountBoundsCheck + extraBoundsCheck, profile.getDeoptimizationCount(BoundsCheckException));
        Assert.assertEquals(deoptimizationCountLoopLimitCheck + extraLoopLimitCheck, profile.getDeoptimizationCount(LoopLimitCheck));
    }

    private void runNoOutOfBound(String methodName, int size, OptionValues options, Object... testParameters) {
        final ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        final int[] array = new int[size];
        final HashSet<DeoptimizationReason> deoptimizationReasons = new HashSet<>();
        deoptimizationReasons.add(BoundsCheckException);
        deoptimizationReasons.add(LoopLimitCheck);
        Object[] args = new Object[testParameters.length + 1];
        args[0] = array;
        System.arraycopy(testParameters, 0, args, 1, testParameters.length);
        Result result = executeActualCheckDeopt(options, method, deoptimizationReasons, null, args);
        Assert.assertNull(result.exception);
    }

    private boolean noRangeCheckInLoop(String method) {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod(method), getOptionsMainPath());
        final StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        final List<Loop<Block>> loops = schedule.getCFG().getLoops();
        Assert.assertEquals(1, loops.size());
        final Loop<Block> loop = loops.get(0);
        return loop.getBlocks().size() == 2;
    }

    private void verifyNoRangeCheckInLoop(String method) {
        Assert.assertTrue(noRangeCheckInLoop(method));
    }

    private void verifyRangeCheckInLoop(String method) {
        Assert.assertFalse(noRangeCheckInLoop(method));
    }

    public static void rangeCheckPredicatesLoopUpScalePos1(int[] array, int start, int stop) {
        for (int i = start; i < stop; i++) {
            array[i] = i;
        }
    }

    @Test
    public void testLoopUpScalePos1() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos1");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePos1", 1000, getOptionsMainPath(), 0, 1000);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos1", 1000, false, 0, 1001);
    }

    public static void rangeCheckPredicatesLoopUpScalePos2(int[] array, int start, int stop) {
        for (int i = start; i < stop; i++) {
            array[i] = i;
        }
    }

    @Test
    public void testLoopUpScalePos2() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos2");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePos2", 1000, getOptionsMainPath(), 0, 1000);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos2", 1000, false, -1, 1000);
    }

    public static void rangeCheckPredicatesLoopUpScalePos3(int[] array, int start, int stop, int offset) {
        for (int i = start; i < stop; i++) {
            array[i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScalePos3() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos3");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePos3", 1000, getOptionsMainPath(), 5, 1005, -5);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos3", 1000, false, 5, 1006, -5);
    }

    public static void rangeCheckPredicatesLoopUpScalePos4(int[] array, int start, int stop, int offset) {
        for (int i = start; i < stop; i++) {
            array[i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScalePos4() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos4");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePos4", 1000, getOptionsMainPath(), 5, 1005, -5);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos4", 1000, false, 4, 1005, -5);
    }

    public static void rangeCheckPredicatesLoopUpScalePos5(int[] array, int start, int stop, int offset) {
        for (int i = start; i < stop; i++) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScalePos5() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos5");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePos5", 1000, getOptionsMainPath(), -5, 495, 10);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos5", 1000, false, -6, 495, 10);
    }

    public static void rangeCheckPredicatesLoopUpScalePos6(int[] array, int start, int stop, int offset) {
        for (int i = start; i < stop; i++) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScalePos6() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos6");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePos6", 1000, getOptionsMainPath(), -5, 495, 10);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos6", 1000, false, -5, 496, 10);
    }

    public static void rangeCheckPredicatesLoopUpScalePos7(int[] array, int start, int stop) {
        for (int i = start; i < stop; i++) {
            array[2 * i] = i;
        }
    }

    @Test
    public void testLoopUpScalePos7() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos7");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePos7", 1000, getOptionsMainPath(), 0, 500);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos7", 1000, false, 0, 501);
    }

    public static void rangeCheckPredicatesLoopUpScalePos8(int[] array, int start, int stop) {
        for (int i = start; i < stop; i++) {
            array[2 * i] = i;
        }
    }

    @Test
    public void testLoopUpScalePos8() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos8");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePos8", 1000, getOptionsMainPath(), 0, 500);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos8", 1000, false, -1, 500);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg1(int[] array, int start, int stop) {
        for (int i = start; i < stop; i++) {
            array[-i] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg1() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg1");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScaleNeg1", 1000, getOptionsMainPath(), -999, 1);
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg1", 1000, false, -999, 2);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg2(int[] array, int start, int stop) {
        for (int i = start; i < stop; i++) {
            array[-i] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg2() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg2");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScaleNeg2", 1000, getOptionsMainPath(), -999, 1);
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg2", 1000, false, -1000, 1);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg3(int[] array, int start, int stop, int offset) {
        for (int i = start; i < stop; i++) {
            array[-i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg3() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg3");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScaleNeg3", 1000, getOptionsMainPath(), -1004, -4, -5);
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg3", 1000, false, -1004, -3, -5);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg4(int[] array, int start, int stop, int offset) {
        for (int i = start; i < stop; i++) {
            array[-i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg4() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg4");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScaleNeg4", 1000, getOptionsMainPath(), -1004, -4, -5);
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg4", 1000, false, -1005, -4, -5);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg5(int[] array, int start, int stop, int offset) {
        for (int i = start; i < stop; i++) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg5() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg5");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScaleNeg5", 1000, getOptionsMainPath(), -495, 5, 9);
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg5", 1000, false, -495, 6, 9);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg6(int[] array, int start, int stop, int offset) {
        for (int i = start; i < stop; i++) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg6() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg6");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScaleNeg6", 1000, getOptionsMainPath(), -495, 5, 9);
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg6", 1000, false, -496, 5, 9);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg7(int[] array, int start, int stop) {
        for (int i = start; i < stop; i++) {
            array[-2 * i] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg7() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg7");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScaleNeg7", 1000, getOptionsMainPath(), -499, 1);
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg7", 1000, false, -500, 1);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg8(int[] array, int start, int stop) {
        for (int i = start; i < stop; i++) {
            array[-2 * i] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg8() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg8");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScaleNeg8", 1000, getOptionsMainPath(), -499, 1);
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg8", 1000, false, -499, 2);
    }

    public static void rangeCheckPredicatesLoopDownScalePos1(int[] array, int start, int stop) {
        for (int i = stop; i > start; i--) {
            array[i] = i;
        }
    }

    @Test
    public void testLoopDownScalePos1() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos1");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScalePos1", 1000, getOptionsMainPath(), -1, 999);
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos1", 1000, false, -1, 1000);
    }

    public static void rangeCheckPredicatesLoopDownScalePos2(int[] array, int start, int stop) {
        for (int i = stop; i > start; i--) {
            array[i] = i;
        }
    }

    @Test
    public void testLoopDownScalePos2() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos2");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScalePos2", 1000, getOptionsMainPath(), -1, 999);
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos2", 1000, false, -2, 999);
    }

    public static void rangeCheckPredicatesLoopDownScalePos3(int[] array, int start, int stop, int offset) {
        for (int i = stop; i > start; i--) {
            array[i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScalePos3() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos3");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScalePos3", 1000, getOptionsMainPath(), 4, 1004, -5);
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos3", 1000, false, 4, 1005, -5);
    }

    public static void rangeCheckPredicatesLoopDownScalePos4(int[] array, int start, int stop, int offset) {
        for (int i = stop; i > start; i--) {
            array[i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScalePos4() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos4");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScalePos4", 1000, getOptionsMainPath(), 4, 1004, -5);
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos4", 1000, false, 3, 1004, -5);
    }

    public static void rangeCheckPredicatesLoopDownScalePos5(int[] array, int start, int stop, int offset) {
        for (int i = stop; i > start; i--) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScalePos5() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos5");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScalePos5", 1000, getOptionsMainPath(), -6, 494, 10);
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos5", 1000, false, -7, 494, 10);
    }

    public static void rangeCheckPredicatesLoopDownScalePos6(int[] array, int start, int stop, int offset) {
        for (int i = stop; i > start; i--) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScalePos6() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos6");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScalePos6", 1000, getOptionsMainPath(), -6, 494, 10);
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos6", 1000, false, -6, 495, 10);
    }

    public static void rangeCheckPredicatesLoopDownScalePos7(int[] array, int start, int stop) {
        for (int i = stop; i > start; i--) {
            array[2 * i] = i;
        }
    }

    @Test
    public void testLoopDownScalePos7() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos7");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScalePos7", 1000, getOptionsMainPath(), -1, 499);
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos7", 1000, false, -1, 500);
    }

    public static void rangeCheckPredicatesLoopDownScalePos8(int[] array, int start, int stop) {
        for (int i = stop; i > start; i--) {
            array[2 * i] = i;
        }
    }

    @Test
    public void testLoopDownScalePos8() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos8");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScalePos8", 1000, getOptionsMainPath(), -1, 499);
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos8", 1000, false, -2, 499);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg1(int[] array, int start, int stop) {
        for (int i = stop; i > start; i--) {
            array[-i] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg1() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg1");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScaleNeg1", 1000, getOptionsMainPath(), -1000, 0);
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg1", 1000, false, -1000, 1);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg2(int[] array, int start, int stop) {
        for (int i = stop; i > start; i--) {
            array[-i] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg2() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg2");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScaleNeg2", 1000, getOptionsMainPath(), -1000, 0);
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg2", 1000, false, -1001, 0);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg3(int[] array, int start, int stop, int offset) {
        for (int i = stop; i > start; i--) {
            array[-i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg3() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg3");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScaleNeg3", 1000, getOptionsMainPath(), -1005, -5, -5);
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg3", 1000, false, -1005, -4, -5);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg4(int[] array, int start, int stop, int offset) {
        for (int i = stop; i > start; i--) {
            array[-i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg4() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg4");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScaleNeg4", 1000, getOptionsMainPath(), -1005, -5, -5);
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg4", 1000, false, -1006, -5, -5);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg5(int[] array, int start, int stop, int offset) {
        for (int i = stop; i > start; i--) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg5() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg5");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScaleNeg5", 1000, getOptionsMainPath(), -496, 4, 9);
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg5", 1000, false, -496, 5, 9);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg6(int[] array, int start, int stop, int offset) {
        for (int i = stop; i > start; i--) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg6() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg6");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScaleNeg6", 1000, getOptionsMainPath(), -496, 4, 9);
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg6", 1000, false, -497, 4, 9);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg7(int[] array, int start, int stop) {
        for (int i = stop; i > start; i--) {
            array[-2 * i] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg7() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg7");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScaleNeg7", 1000, getOptionsMainPath(), -500, 0);
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg7", 1000, false, -501, 0);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg8(int[] array, int start, int stop) {
        for (int i = stop; i > start; i--) {
            array[-2 * i] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg8() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg8");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScaleNeg8", 1000, getOptionsMainPath(), -500, 0);
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg8", 1000, false, -500, 1);
    }

    // Same with <=, >= loop exit test

    public static void rangeCheckPredicatesLoopUpScalePos9(int[] array, int start, int stop, int offset) {
        for (int i = start; i <= stop; i++) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScalePos9() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos9");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePos9", 1000, getOptionsMainPath(), -5, 494, 10);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos9", 1000, false, -6, 494, 10);
    }

    public static void rangeCheckPredicatesLoopUpScalePos10(int[] array, int start, int stop, int offset) {
        for (int i = start; i <= stop; i++) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScalePos10() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos10");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePos10", 1000, getOptionsMainPath(), -5, 494, 10);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos10", 1000, false, -5, 495, 10);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg9(int[] array, int start, int stop, int offset) {
        for (int i = start; i <= stop; i++) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg9() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg9");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScaleNeg9", 1000, getOptionsMainPath(), -495, 4, 9);
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg9", 1000, false, -495, 5, 9);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg10(int[] array, int start, int stop, int offset) {
        for (int i = start; i <= stop; i++) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg10() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg10");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScaleNeg10", 1000, getOptionsMainPath(), -495, 4, 9);
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg10", 1000, false, -496, 4, 9);
    }

    public static void rangeCheckPredicatesLoopDownScalePos9(int[] array, int start, int stop, int offset) {
        for (int i = stop; i >= start; i--) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScalePos9() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos9");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScalePos9", 1000, getOptionsMainPath(), -5, 494, 10);
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos9", 1000, false, -6, 494, 10);
    }

    public static void rangeCheckPredicatesLoopDownScalePos10(int[] array, int start, int stop, int offset) {
        for (int i = stop; i >= start; i--) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScalePos10() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos10");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScalePos10", 1000, getOptionsMainPath(), -5, 494, 10);
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos10", 1000, false, -5, 495, 10);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg9(int[] array, int start, int stop, int offset) {
        for (int i = stop; i >= start; i--) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg9() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg9");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScaleNeg9", 1000, getOptionsMainPath(), -495, 4, 9);
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg9", 1000, false, -495, 5, 9);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg10(int[] array, int start, int stop, int offset) {
        for (int i = stop; i >= start; i--) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg10() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg10");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScaleNeg10", 1000, getOptionsMainPath(), -495, 4, 9);
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg10", 1000, false, -496, 4, 9);
    }

    // Tests with stride = 3

    public static void rangeCheckPredicatesLoopUpScalePos11(int[] array, int start, int stop, int offset) {
        for (int i = start; i < stop; i += 3) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScalePos11() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos11");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePos11", 1000, getOptionsMainPath(), -5, 495, 10);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos11", 1000, false, -6, 495, 10);
    }

    public static void rangeCheckPredicatesLoopUpScalePos12(int[] array, int start, int stop, int offset) {
        for (int i = start; i < stop; i += 3) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScalePos12() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos12");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePos12", 1000, getOptionsMainPath(), -5, 496, 10);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos12", 1000, false, -5, 497, 10);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg11(int[] array, int start, int stop, int offset) {
        for (int i = start; i < stop; i += 3) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg11() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg11");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScaleNeg11", 1000, getOptionsMainPath(), -495, 5, 9);
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg11", 1000, false, -495, 7, 9);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg12(int[] array, int start, int stop, int offset) {
        for (int i = start; i < stop; i += 3) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg12() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg12");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScaleNeg12", 1000, getOptionsMainPath(), -495, 6, 9);
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg12", 1000, false, -496, 5, 9);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg11(int[] array, int start, int stop, int offset) {
        for (int i = stop; i > start; i -= 3) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg11() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg11");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScaleNeg11", 1000, getOptionsMainPath(), -496, 4, 9);
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg11", 1000, false, -496, 5, 9);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg12(int[] array, int start, int stop, int offset) {
        for (int i = stop; i > start; i -= 3) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg12() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg12");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScaleNeg12", 1000, getOptionsMainPath(), -497, 4, 9);
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg12", 1000, false, -498, 4, 9);
    }

    public static void rangeCheckPredicatesLoopDownScalePos11(int[] array, int start, int stop, int offset) {
        for (int i = stop; i > start; i -= 3) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScalePos11() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos11");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScalePos11", 1000, getOptionsMainPath(), -6, 494, 10);
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos11", 1000, false, -6, 495, 10);
    }

    public static void rangeCheckPredicatesLoopDownScalePos12(int[] array, int start, int stop, int offset) {
        for (int i = stop; i > start; i -= 3) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScalePos12() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos12");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScalePos12", 1000, getOptionsMainPath(), -7, 494, 10);
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos12", 1000, false, -8, 494, 10);
    }

    // Tests with equality loop exit test

    public static void rangeCheckPredicatesLoopUpScalePos13(int[] array, int offset) {
        for (int i = -5; i != 495; i++) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScalePos13() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos13");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePos13", 1000, getOptionsMainPath(), 10);
    }

    public static void rangeCheckPredicatesLoopUpScalePos14(int[] array, int offset) {
        for (int i = -6; i != 495; i++) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScalePos14() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos14");
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos14", 1000, false, 10);
    }

    public static void rangeCheckPredicatesLoopUpScalePos15(int[] array, int offset) {
        for (int i = -5; i != 496; i++) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScalePos15() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePos15");
        runOutOfBound("rangeCheckPredicatesLoopUpScalePos15", 1000, false, 10);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg13(int[] array, int offset) {
        for (int i = -495; i != 5; i++) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg13() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg13");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScaleNeg13", 1000, getOptionsMainPath(), 9);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg14(int[] array, int offset) {
        for (int i = -495; i != 6; i++) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg14() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg14");
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg14", 1000, false, 9);
    }

    public static void rangeCheckPredicatesLoopUpScaleNeg15(int[] array, int offset) {
        for (int i = -496; i != 5; i++) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScaleNeg15() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScaleNeg15");
        runOutOfBound("rangeCheckPredicatesLoopUpScaleNeg15", 1000, false, 9);
    }

    public static void rangeCheckPredicatesLoopDownScalePos13(int[] array, int offset) {
        for (int i = 494; i != -6; i--) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScalePos13() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos13");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScalePos13", 1000, getOptionsMainPath(), 10);
    }

    public static void rangeCheckPredicatesLoopDownScalePos14(int[] array, int offset) {
        for (int i = 494; i != -7; i--) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScalePos14() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos14");
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos14", 1000, false, 10);
    }

    public static void rangeCheckPredicatesLoopDownScalePos15(int[] array, int offset) {
        for (int i = 495; i != -6; i--) {
            array[2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScalePos15() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScalePos15");
        runOutOfBound("rangeCheckPredicatesLoopDownScalePos15", 1000, false, 10);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg13(int[] array, int offset) {
        for (int i = 4; i != -496; i--) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg13() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg13");
        runNoOutOfBound("rangeCheckPredicatesLoopDownScaleNeg13", 1000, getOptionsMainPath(), 9);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg14(int[] array, int offset) {
        for (int i = 5; i != -496; i--) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg14() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg14");
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg14", 1000, false, 9);
    }

    public static void rangeCheckPredicatesLoopDownScaleNeg15(int[] array, int offset) {
        for (int i = 4; i != -497; i--) {
            array[-2 * i + offset] = i;
        }
    }

    @Test
    public void testLoopDownScaleNeg15() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopDownScaleNeg15");
        runOutOfBound("rangeCheckPredicatesLoopDownScaleNeg15", 1000, false, 9);
    }

    // Overflow of the iv
    public static void rangeCheckPredicatesLoopUpScalePosOverflow1(int[] array, int start, int stop, int offset) {
        for (int i = start; i < stop; i += 2) {
            array[i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScalePosOverflow1() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePosOverflow1");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePosOverflow1", 1000, getOptionsMainPath(), 0, 1000, 0);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePosOverflow1", 1000, true, Integer.MAX_VALUE - 3, Integer.MAX_VALUE, -Integer.MAX_VALUE + 3);
    }

    public static void rangeCheckPredicatesLoopUpScalePosOverflow2(int[] array, int start, int stop, int offset) {
        for (int i = start; i > stop; i -= 2) {
            array[i + offset] = i;
        }
    }

    @Test
    public void testLoopUpScalePosOverflow2() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePosOverflow2");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePosOverflow2", 1000, getOptionsMainPath(), 999, -1, 0);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePosOverflow2", 1000, true, Integer.MIN_VALUE + 5, Integer.MIN_VALUE, Integer.MAX_VALUE - 2);
    }

    // Interaction with range check smearing
    public static void rangeCheckPredicatesLoopUpScalePosSmearing1(int[] array, int start, int stop) {
        for (int i = start; i < stop; i++) {
            array[i + 3] = i;
            array[i] = i;
            array[i + 1] = i;
        }
    }

    @Test
    public void testLoopUpScalePosSmearing1() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePosSmearing1");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePosSmearing1", 1000, getOptionsMainPath(), 0, 997);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePosSmearing1", 1000, false, 0, 998);
    }

    public static void rangeCheckPredicatesLoopUpScalePosSmearing2(int[] array, int start, int stop) {
        for (int i = start; i > stop; i--) {
            array[i + 3] = i;
            array[i] = i;
            array[i + 1] = i;
        }
    }

    @Test
    public void testLoopUpScalePosSmearing2() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePosSmearing2");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePosSmearing2", 1000, getOptionsMainPath(), 996, -1);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePosSmearing2", 1000, false, 997, -1);
    }

    // Verify failed speculation

    public static void rangeCheckPredicatesLoopUpScalePosSpeculation1(int[] array, int start, int stop) {
        array[0] = 0; // null check out of loop
        for (int i = start; i < stop; i++) {
            if (i < 1000) {
                array[i] = i;
            }
        }
    }

    @Test
    public void testLoopUpScalePosSpeculation1() {
        boolean[] flag = new boolean[2];
        flag[0] = true;
        flag[1] = false;
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePosSpeculation1", 2000, getOptionsAllPaths(), 0, 2000);
        final ResolvedJavaMethod method = getResolvedJavaMethod("rangeCheckPredicatesLoopUpScalePosSpeculation1");
        StructuredGraph graph = getFinalGraph(method, getOptionsAllPaths());
        assert countRangeChecksInLoop(graph) == 0;
        flag[0] = false;
        final int[] array = new int[1000];

        ProfilingInfo profile = method.getProfilingInfo();
        int deoptimizationCount = profile.getDeoptimizationCount(BoundsCheckException);
        Result result = executeActual(getOptionsAllPaths(), method, null, array, 0, 2000);
        Assert.assertNull(result.exception);
        profile = method.getProfilingInfo();
        Assert.assertEquals(deoptimizationCount + 1, profile.getDeoptimizationCount(BoundsCheckException));

        graph = getFinalGraph(method, getOptionsAllPaths());
        Assert.assertEquals(1, countRangeChecksInLoop(graph));
    }

    // Only optimize guard that dominates the back edge (C2's behavior)
    public static void rangeCheckPredicatesOnlyLoopEndDominators1(int[] array, int start, int stop) {
        array[0] = 42;
        for (int i = start; i < stop; i++) {
            if (i < 1000) {
                array[i] = i;
            }
        }
    }

    @Test
    public void testOnlyLoopEndDominators1() {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckPredicatesOnlyLoopEndDominators1"), getOptionsMainPath());
        Assert.assertEquals(1, countRangeChecksInLoop(graph));
        graph = getFinalGraph(getResolvedJavaMethod("rangeCheckPredicatesOnlyLoopEndDominators1"), getOptionsAllPaths());
        Assert.assertEquals(0, countRangeChecksInLoop(graph));
    }

    private static int countRangeChecksInLoop(StructuredGraph graph) {
        StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        List<Loop<Block>> loops = schedule.getCFG().getLoops();
        Assert.assertEquals(1, loops.size());
        Loop<Block> loop = loops.get(0);
        int rangeChecks = 0;
        for (Block block : loop.getBlocks()) {
            for (Node node : schedule.nodesFor(block)) {
                if (node instanceof IntegerBelowNode) {
                    rangeChecks++;
                }
            }
        }
        return rangeChecks;
    }

    public static void rangeCheckPredicatesOnlyLoopEndDominators2(int[] array, int start, int stop) {
        for (int i = start; i < stop; i++) {
            if (i < 1000) {
                volatileField = 0x42;
            }
            array[i] = i;
        }
    }

    @Test
    public void testOnlyLoopEndDominators2() {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckPredicatesOnlyLoopEndDominators2"), getOptionsMainPath());
        Assert.assertEquals(0, countRangeChecksInLoop(graph));
    }

    public static void rangeCheckPredicatesOnlyLoopEndDominators3(int[] array, int start, int stop) {
        for (int i = start; i < stop; i++) {
            if (i < 1000) {
                array[i] = i;
            } else {
                array[i] = i + 1;
            }
        }
    }

    @Test
    public void testOnlyLoopEndDominators3() {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckPredicatesOnlyLoopEndDominators3"), getOptionsMainPath());
        Assert.assertEquals(0, countRangeChecksInLoop(graph));
    }

    // Loop exit test on increment
    public static void rangeCheckPredicatesLoopUpScalePosTestOnIncr1(int[] array, int start, int stop) {
        int i = start;
        for (;;) {
            i++;
            if (i >= stop) {
                break;
            }
            array[i] = i;
        }
    }

    @Test
    public void testLoopUpScalePosTestOnIncr1() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePosTestOnIncr1");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePosTestOnIncr1", 1000, getOptionsMainPath(), -1, 1000);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePosTestOnIncr1", 1000, false, -1, 1001);
    }

    public static void rangeCheckPredicatesLoopUpScalePosTestOnIncr2(int[] array, int start, int stop) {
        int i = start;
        for (;;) {
            i++;
            if (i >= stop) {
                break;
            }
            array[i] = i;
        }
    }

    @Test
    public void testLoopUpScalePosTestOnIncr2() {
        verifyNoRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePosTestOnIncr2");
        runNoOutOfBound("rangeCheckPredicatesLoopUpScalePosTestOnIncr2", 1000, getOptionsMainPath(), -1, 1000);
        runOutOfBound("rangeCheckPredicatesLoopUpScalePosTestOnIncr2", 1000, false, -2, 1001);
    }

    // Extra iv
    public static void rangeCheckPredicatesLoopUpScalePosExtraIV(int[] array, int start, int stop) {
        int j = 0;
        for (int i = start; i < stop; i++) {
            j += 50;
            array[j] = i;
        }
    }

    @Test
    public void testLoopUpScalePosExtraIV() {
        verifyRangeCheckInLoop("rangeCheckPredicatesLoopUpScalePosExtraIV");
    }

    // Guard above exit test
    public static int rangeCheckPredicatesGuardAboveExitTest(int[] array, int start, int stop) {
        int res = 0;
        int i = start;
        if (i < stop) {
            for (;;) {
                i++;
                res += array[i];
                if (i >= stop) {
                    break;
                }
            }
        }
        return res;
    }

    @Test
    public void testGuardAboveExitTest() {
        verifyRangeCheckInLoop("rangeCheckPredicatesGuardAboveExitTest");
    }
}
