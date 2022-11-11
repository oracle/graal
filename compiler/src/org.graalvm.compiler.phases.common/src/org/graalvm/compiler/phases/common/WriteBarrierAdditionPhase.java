/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import java.util.Optional;

import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;

public class WriteBarrierAdditionPhase extends BasePhase<CoreProviders> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.ifApplied(this, StageFlag.BARRIER_ADDITION, graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.MID_TIER_LOWERING, graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.FSA, graphState));
    }

    @SuppressWarnings("try")
    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        BarrierSet barrierSet = context.getPlatformConfigurationProvider().getBarrierSet();
        for (FixedAccessNode n : graph.getNodes(FixedAccessNode.TYPE)) {
            try (DebugCloseable scope = n.graph().withNodeSourcePosition(n)) {
                barrierSet.addBarriers(n);
            }
        }
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(StageFlag.BARRIER_ADDITION);
    }

    @Override
    public boolean checkContract() {
        return false;
    }
}
