/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import java.util.Optional;

import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool.StandardLoweringStage;

/**
 * A {@link LoweringPhase} used to lower {@link Lowerable} nodes when the graph is in
 * {@link StandardLoweringStage#MID_TIER} stage.
 */
public class MidTierLoweringPhase extends LoweringPhase {

    public MidTierLoweringPhase(CanonicalizerPhase canonicalizer, boolean lowerOptimizableMacroNodes) {
        super(canonicalizer, StandardLoweringStage.MID_TIER, lowerOptimizableMacroNodes, StageFlag.MID_TIER_LOWERING);
    }

    public MidTierLoweringPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer, StandardLoweringStage.MID_TIER, StageFlag.MID_TIER_LOWERING);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.ifApplied(this, StageFlag.MID_TIER_LOWERING, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.FSA, graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.GUARD_LOWERING, graphState));
    }
}
