/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;

public class WriteBarrierAdditionPhase extends BasePhase<CoreProviders> {

    private final StageFlag stage;

    public WriteBarrierAdditionPhase() {
        this(StageFlag.BARRIER_ADDITION);
    }

    public WriteBarrierAdditionPhase(StageFlag stage) {
        this.stage = stage;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.ifApplied(this, stage, graphState),
                        NotApplicable.unlessRunAfter(this, stage == StageFlag.BARRIER_ADDITION ? StageFlag.MID_TIER_LOWERING : StageFlag.LOW_TIER_LOWERING, graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.FSA, graphState));
    }

    @SuppressWarnings("try")
    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        BarrierSet barrierSet = context.getPlatformConfigurationProvider().getBarrierSet();
        if (barrierSet.hasWriteBarrier() && barrierSet.shouldAddBarriersInStage(stage)) {
            for (FixedAccessNode n : graph.getNodes(FixedAccessNode.TYPE)) {
                try (DebugCloseable scope = n.graph().withNodeSourcePosition(n)) {
                    barrierSet.addBarriers(n, context);
                }
            }
        }
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(stage);
    }

    @Override
    public boolean checkContract() {
        return false;
    }
}
