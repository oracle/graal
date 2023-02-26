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
package org.graalvm.compiler.phases.common;

import java.util.EnumSet;
import java.util.Optional;

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;

/**
 * Final application of {@link CanonicalizerPhase}. After the application of this phase, no further
 * canonicalizations should be made to a {@link StructuredGraph}. See
 * {@link CanonicalizerTool#finalCanonicalization()} for more details.
 */
public class FinalCanonicalizerPhase extends CanonicalizerPhase {
    protected FinalCanonicalizerPhase(CustomSimplification customSimplification, EnumSet<CanonicalizerFeature> features) {
        super(customSimplification, features);
    }

    @Override
    protected boolean isFinalCanonicalizationPhase() {
        return true;
    }

    @Override
    public FinalCanonicalizerPhase copyWithCustomSimplification(CustomSimplification newSimplification) {
        return new FinalCanonicalizerPhase(newSimplification, features);
    }

    @Override
    public FinalCanonicalizerPhase copyWithoutGVN() {
        EnumSet<CanonicalizerFeature> newFeatures = EnumSet.copyOf(features);
        newFeatures.remove(CanonicalizerFeature.GVN);
        return new FinalCanonicalizerPhase(customSimplification, newFeatures);
    }

    @Override
    public FinalCanonicalizerPhase copyWithoutSimplification() {
        EnumSet<CanonicalizerFeature> newFeatures = EnumSet.copyOf(features);
        newFeatures.remove(CanonicalizerFeature.CFG_SIMPLIFICATION);
        return new FinalCanonicalizerPhase(customSimplification, newFeatures);
    }

    public static FinalCanonicalizerPhase createFromCanonicalizer(CanonicalizerPhase canonicalizer) {
        return new FinalCanonicalizerPhase(canonicalizer.customSimplification, canonicalizer.features);
    }

    @Override
    public boolean mustApply(GraphState graphState) {
        return graphState.requiresFutureStage(StageFlag.FINAL_CANONICALIZATION);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.LOW_TIER_LOWERING, graphState));
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(StageFlag.FINAL_CANONICALIZATION);
        graphState.removeRequirementToStage(StageFlag.FINAL_CANONICALIZATION);
    }
}
