/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.phases;

import com.oracle.graal.lir.*;
import com.oracle.graal.lir.alloc.lsra.*;
import com.oracle.graal.lir.constopt.*;
import com.oracle.graal.lir.phases.LowLevelHighTierPhase.LowLevelHighTierContext;
import com.oracle.graal.lir.phases.LowLevelLowTierPhase.LowLevelLowTierContext;
import com.oracle.graal.lir.phases.LowLevelMidTierPhase.LowLevelMidTierContext;
import com.oracle.graal.lir.stackslotalloc.*;

public class DefaultLowLevelCompilerConfiguration implements LowLevelCompilerConfiguration {

    public LowLevelPhaseSuite<LowLevelHighTierContext> createHighTier() {
        LowLevelPhaseSuite<LowLevelHighTierContext> suite = new LowLevelPhaseSuite<>(LowLevelHighTierContext.class);
        if (ConstantLoadOptimization.Options.ConstantLoadOptimization.getValue()) {
            suite.appendPhase(new ConstantLoadOptimization());
        }
        return suite;
    }

    public LowLevelPhaseSuite<LowLevelMidTierContext> createMidTier() {
        LowLevelPhaseSuite<LowLevelMidTierContext> suite = new LowLevelPhaseSuite<>(LowLevelMidTierContext.class);
        suite.appendPhase(new LinearScanPhase());

        // build frame map
        if (LSStackSlotAllocator.Options.LSStackSlotAllocation.getValue()) {
            suite.appendPhase(new LSStackSlotAllocator());
        } else {
            suite.appendPhase(new SimpleStackSlotAllocator());
        }
        // currently we mark locations only if we do register allocation
        suite.appendPhase(new LocationMarker());
        return suite;
    }

    public LowLevelPhaseSuite<LowLevelLowTierContext> createLowTier() {
        LowLevelPhaseSuite<LowLevelLowTierContext> suite = new LowLevelPhaseSuite<>(LowLevelLowTierContext.class);
        suite.appendPhase(new EdgeMoveOptimizer());
        suite.appendPhase(new ControlFlowOptimizer());
        suite.appendPhase(new RedundantMoveElimination());
        suite.appendPhase(new NullCheckOptimizer());
        return suite;
    }

}
