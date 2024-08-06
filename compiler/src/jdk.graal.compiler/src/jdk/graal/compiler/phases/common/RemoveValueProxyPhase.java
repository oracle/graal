/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodes.GraphState.FrameStateVerification.ALL_EXCEPT_LOOP_EXIT;

import java.util.Optional;

import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.FrameStateVerification;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;

public class RemoveValueProxyPhase extends PostRunCanonicalizationPhase<CoreProviders> {

    public RemoveValueProxyPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public Optional<BasePhase.NotApplicable> notApplicableTo(GraphState graphState) {
        return BasePhase.NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        BasePhase.NotApplicable.ifApplied(this, StageFlag.VALUE_PROXY_REMOVAL, graphState),
                        BasePhase.NotApplicable.unlessRunBefore(this, StageFlag.MID_TIER_LOWERING, graphState),
                        BasePhase.NotApplicable.unlessRunBefore(this, StageFlag.FSA, graphState),
                        BasePhase.NotApplicable.when(!graphState.canWeakenFrameStateVerification(ALL_EXCEPT_LOOP_EXIT),
                                        "Cannot apply %s because the frame state verification has already been weakened to %s",
                                        getName(), graphState.getFrameStateVerification()));
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        for (LoopExitNode exit : graph.getNodes(LoopExitNode.TYPE)) {
            exit.removeProxies();
            FrameState frameState = exit.stateAfter();
            if (frameState != null && frameState.isExceptionHandlingBCI()) {
                // The parser will create loop exits with such BCIs on the exception handling path.
                // Loop optimizations must avoid duplicating such exits
                // We clean them up here otherwise they could survive until code generation
                exit.setStateAfter(null);
                GraphUtil.tryKillUnused(frameState);
            }
        }

    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(StageFlag.VALUE_PROXY_REMOVAL);
        graphState.weakenFrameStateVerification(FrameStateVerification.ALL_EXCEPT_LOOP_EXIT);
    }
}
