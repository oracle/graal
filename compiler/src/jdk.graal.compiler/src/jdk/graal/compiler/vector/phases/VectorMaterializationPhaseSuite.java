/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.phases;

import java.util.Optional;

import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;

/**
 * {@link PhaseSuite} that contains {@link VectorMaterializationPhase} and the obligatory
 * {@link VectorSimplificationPhase} that must follow it.
 */
public class VectorMaterializationPhaseSuite extends PhaseSuite<MidTierContext> {
    @SuppressWarnings("this-escape")
    public VectorMaterializationPhaseSuite(CanonicalizerPhase canonicalizer) {
        appendPhase(new VectorMaterializationPhase(canonicalizer));
        appendPhase(new VectorSimplificationPhase(canonicalizer));
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(super.notApplicableTo(graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.GUARD_LOWERING, graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.FSA, graphState));
    }

    @Override
    public boolean mustApply(GraphState graphState) {
        return graphState.requiresFutureStage(StageFlag.VECTOR_MATERIALIZATION) || super.mustApply(graphState);
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(StageFlag.VECTOR_MATERIALIZATION);
        graphState.removeRequirementToStage(StageFlag.VECTOR_MATERIALIZATION);
    }
}
