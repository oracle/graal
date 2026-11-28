/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pgo;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.loop.phases.LoopSafepointEliminationPhase;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

/**
 * An instrumented image that samples stacks keeps the safepoint polls of counted loops, because the
 * sampler runs only at polls, unless {@code -H:RemoveLoopSafepoints} is given. These tests apply
 * the options in command-line order, in every order that matters, and check the value of
 * {@code RemoveLoopSafepoints} that a compilation of the image sees.
 */
public class PGOLoopSafepointsTest {

    private static final OptionKey<Boolean> REMOVE_LOOP_SAFEPOINTS = LoopSafepointEliminationPhase.Options.RemoveLoopSafepoints;

    private static void instrument(EconomicMap<OptionKey<?>, Object> values, boolean enabled) {
        PGOInstrumentationFeature.Options.PGOInstrument.update(values, enabled);
    }

    private static void sampleStacks(EconomicMap<OptionKey<?>, Object> values, boolean enabled) {
        PGOInstrumentationFeature.Options.PGOSampleStacks.update(values, enabled);
    }

    private static void removeLoopSafepoints(EconomicMap<OptionKey<?>, Object> values, boolean enabled) {
        REMOVE_LOOP_SAFEPOINTS.update(values, enabled);
    }

    /** Whether a compilation of the image built with {@code values} removes the polls of counted loops. */
    private static boolean compilationRemovesLoopSafepoints(EconomicMap<OptionKey<?>, Object> values) {
        return REMOVE_LOOP_SAFEPOINTS.getValue(PGOInstrumentationFeature.compilationOptions(new OptionValues(values)));
    }

    @Test
    public void samplingImageKeepsLoopSafepoints() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        instrument(values, true);
        Assert.assertFalse(compilationRemovesLoopSafepoints(values));

        values = OptionValues.newOptionMap();
        sampleStacks(values, true);
        instrument(values, true);
        Assert.assertFalse(compilationRemovesLoopSafepoints(values));
    }

    @Test
    public void imageWithoutSamplingRemovesLoopSafepoints() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        instrument(values, true);
        sampleStacks(values, false);
        Assert.assertTrue(compilationRemovesLoopSafepoints(values));

        values = OptionValues.newOptionMap();
        sampleStacks(values, false);
        instrument(values, true);
        Assert.assertTrue(compilationRemovesLoopSafepoints(values));
    }

    @Test
    public void imageWithoutInstrumentationRemovesLoopSafepoints() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        sampleStacks(values, true);
        Assert.assertTrue(compilationRemovesLoopSafepoints(values));

        values = OptionValues.newOptionMap();
        instrument(values, true);
        instrument(values, false);
        Assert.assertTrue(compilationRemovesLoopSafepoints(values));
    }

    @Test
    public void removeLoopSafepointsOnTheCommandLineWins() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        removeLoopSafepoints(values, true);
        instrument(values, true);
        sampleStacks(values, true);
        Assert.assertTrue(compilationRemovesLoopSafepoints(values));

        values = OptionValues.newOptionMap();
        instrument(values, true);
        removeLoopSafepoints(values, true);
        sampleStacks(values, true);
        Assert.assertTrue(compilationRemovesLoopSafepoints(values));
    }

    @Test
    public void keepLoopSafepointsOnTheCommandLineWins() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        instrument(values, true);
        removeLoopSafepoints(values, false);
        sampleStacks(values, false);
        Assert.assertFalse(compilationRemovesLoopSafepoints(values));

        values = OptionValues.newOptionMap();
        removeLoopSafepoints(values, false);
        sampleStacks(values, false);
        instrument(values, true);
        Assert.assertFalse(compilationRemovesLoopSafepoints(values));

        values = OptionValues.newOptionMap();
        removeLoopSafepoints(values, false);
        Assert.assertFalse(compilationRemovesLoopSafepoints(values));
    }
}
