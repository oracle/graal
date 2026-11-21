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
 * sampler runs only at polls. These tests apply the options in command-line order, in every order
 * that matters.
 */
public class PGOLoopSafepointsTest {

    private static final OptionKey<Boolean> REMOVE_LOOP_SAFEPOINTS = LoopSafepointEliminationPhase.Options.RemoveLoopSafepoints;

    private static void instrument(EconomicMap<OptionKey<?>, Object> values) {
        PGOInstrumentationFeature.Options.PGOInstrument.update(values, true);
    }

    private static void sampleStacks(EconomicMap<OptionKey<?>, Object> values, boolean enabled) {
        PGOInstrumentationFeature.Options.PGOSampleStacks.update(values, enabled);
    }

    @Test
    public void samplingImageKeepsLoopSafepoints() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        instrument(values);
        Assert.assertEquals(Boolean.FALSE, values.get(REMOVE_LOOP_SAFEPOINTS));

        values = OptionValues.newOptionMap();
        sampleStacks(values, true);
        instrument(values);
        Assert.assertEquals(Boolean.FALSE, values.get(REMOVE_LOOP_SAFEPOINTS));
    }

    @Test
    public void imageWithoutSamplingRemovesLoopSafepoints() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        instrument(values);
        sampleStacks(values, false);
        Assert.assertFalse(values.containsKey(REMOVE_LOOP_SAFEPOINTS));

        values = OptionValues.newOptionMap();
        sampleStacks(values, false);
        instrument(values);
        Assert.assertFalse(values.containsKey(REMOVE_LOOP_SAFEPOINTS));
    }

    @Test
    public void imageWithoutInstrumentationRemovesLoopSafepoints() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        sampleStacks(values, true);
        Assert.assertFalse(values.containsKey(REMOVE_LOOP_SAFEPOINTS));

        values = OptionValues.newOptionMap();
        instrument(values);
        PGOInstrumentationFeature.Options.PGOInstrument.update(values, false);
        Assert.assertFalse(values.containsKey(REMOVE_LOOP_SAFEPOINTS));
    }

    @Test
    public void removeLoopSafepointsOnTheCommandLineWins() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        REMOVE_LOOP_SAFEPOINTS.update(values, true);
        instrument(values);
        sampleStacks(values, true);
        Assert.assertEquals(Boolean.TRUE, values.get(REMOVE_LOOP_SAFEPOINTS));

        values = OptionValues.newOptionMap();
        instrument(values);
        REMOVE_LOOP_SAFEPOINTS.update(values, true);
        sampleStacks(values, true);
        Assert.assertEquals(Boolean.TRUE, values.get(REMOVE_LOOP_SAFEPOINTS));
    }
}
