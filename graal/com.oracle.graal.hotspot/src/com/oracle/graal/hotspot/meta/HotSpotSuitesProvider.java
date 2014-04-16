/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.phases.GraalOptions.*;

import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

/**
 * HotSpot implementation of {@link SuitesProvider}.
 */
public class HotSpotSuitesProvider implements SuitesProvider {

    protected final Suites defaultSuites;
    protected final PhaseSuite<HighTierContext> defaultGraphBuilderSuite;
    protected final HotSpotGraalRuntime runtime;

    public HotSpotSuitesProvider(HotSpotGraalRuntime runtime) {
        this.runtime = runtime;
        this.defaultGraphBuilderSuite = createGraphBuilderSuite();
        defaultSuites = createSuites();
    }

    public Suites getDefaultSuites() {
        return defaultSuites;
    }

    public PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite() {
        return defaultGraphBuilderSuite;
    }

    public Suites createSuites() {
        Suites ret = Suites.createDefaultSuites();

        if (ImmutableCode.getValue()) {
            // lowering introduces class constants, therefore it must be after lowering
            ret.getHighTier().appendPhase(new LoadJavaMirrorWithKlassPhase(runtime.getConfig().classMirrorOffset));
            if (VerifyPhases.getValue()) {
                ret.getHighTier().appendPhase(new AheadOfTimeVerificationPhase());
            }
        }

        ret.getMidTier().appendPhase(new WriteBarrierAdditionPhase());
        if (VerifyPhases.getValue()) {
            ret.getMidTier().appendPhase(new WriteBarrierVerificationPhase());
        }

        return ret;
    }

    protected PhaseSuite<HighTierContext> createGraphBuilderSuite() {
        PhaseSuite<HighTierContext> suite = new PhaseSuite<>();
        suite.appendPhase(new GraphBuilderPhase(GraphBuilderConfiguration.getDefault()));
        return suite;
    }
}
