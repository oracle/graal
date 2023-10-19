/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.core.phases;

import jdk.compiler.graal.debug.Assertions;
import jdk.compiler.graal.phases.PlaceholderPhase;
import jdk.compiler.graal.phases.common.AddressLoweringPhase;
import jdk.compiler.graal.phases.common.BarrierSetVerificationPhase;
import jdk.compiler.graal.phases.common.CanonicalizerPhase;
import jdk.compiler.graal.phases.common.ExpandLogicPhase;
import jdk.compiler.graal.phases.common.LowTierLoweringPhase;
import jdk.compiler.graal.phases.schedule.SchedulePhase;
import jdk.compiler.graal.phases.tiers.LowTierContext;

public class EconomyLowTier extends BaseTier<LowTierContext> {

    @SuppressWarnings("this-escape")
    public EconomyLowTier() {
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        appendPhase(new LowTierLoweringPhase(canonicalizer));
        appendPhase(new ExpandLogicPhase(canonicalizer));

        if (Assertions.assertionsEnabled()) {
            appendPhase(new BarrierSetVerificationPhase());
        }

        /*
         * This placeholder should be replaced by an instance of {@link AddressLoweringPhase}
         * specific to the target architecture for this compilation. This should be done by the
         * backend or the target specific suites provider.
         */
        appendPhase(new PlaceholderPhase<LowTierContext>(AddressLoweringPhase.class));
        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS));
    }
}
