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
package com.oracle.svm.hosted;

import java.util.ListIterator;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.pgo.profiles.PGOProfilesLookup;
import com.oracle.svm.hosted.phases.priorityinline.SubstratePriorityInliningPhase;
import com.oracle.svm.shared.option.HostedOptionValues;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.BoxNodeIdentityPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;

/// Configures the compiler phases used for hosted Native Image compilation.
public class HostedGraalConfiguration extends GraalConfiguration {
    /// Runtime compiler configuration used to parse methods expanded by priority inlining.
    protected RuntimeConfiguration runtimeConfiguration;

    /// Hosted universe containing the methods available to priority inlining.
    protected HostedUniverse hUniverse;

    /// Records the runtime configuration after hosted universe construction.
    public void setRuntimeConfiguration(RuntimeConfiguration runtimeConfiguration) {
        this.runtimeConfiguration = runtimeConfiguration;
    }

    /// Records the hosted universe after analysis.
    public void setHostedUniverse(HostedUniverse hUniverse) {
        this.hUniverse = hUniverse;
    }

    /// Removes priority inlining from suites used to compile deoptimization targets.
    @Override
    public void removeDeoptTargetOptimizations(Suites suites) {
        if (SubstrateOptions.AOTPriorityInline.getValue()) {
            VMError.guarantee(suites.getHighTier().removePhase(SubstratePriorityInliningPhase.class));
        }
    }

    /// Installs the substrate priority inliner when it is enabled for hosted compilation.
    @Override
    public ListIterator<BasePhase<? super HighTierContext>> createHostedInliners(PhaseSuite<HighTierContext> highTier) {
        if (!SubstrateOptions.AOTPriorityInline.getValue()) {
            VMError.guarantee(highTier.findPhase(SubstratePriorityInliningPhase.class) == null);
            return null;
        }

        VMError.guarantee(runtimeConfiguration != null && hUniverse != null, "Hosted compiler configuration must be initialized before creating the priority inliner");
        highTier.removePhase(BoxNodeIdentityPhase.class);
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        highTier.prependPhase(canonicalizer);
        var position = highTier.findPhase(CanonicalizerPhase.class);
        position.add(new BoxNodeIdentityPhase());
        position.add(new SubstratePriorityInliningPhase(canonicalizer, HostedOptionValues.singleton().get(), runtimeConfiguration, CompileQueue.getOptimisticOpts(), hUniverse, highTier,
                        PGOProfilesLookup.singletonOrNull()));
        return position;
    }
}
