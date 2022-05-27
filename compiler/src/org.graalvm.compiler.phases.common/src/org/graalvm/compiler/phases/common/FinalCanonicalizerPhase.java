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

import org.graalvm.compiler.graph.Graph.Mark;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.StageFlag;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.CoreProviders;

/**
 * Final application of {@link CanonicalizerPhase}. After the application of this phase, no further
 * canonicalizations should be made to a {@link StructuredGraph}. See
 * {@link CanonicalizerTool#finalCanonicalization()} for more details.
 */
public class FinalCanonicalizerPhase extends CanonicalizerPhase {
    protected FinalCanonicalizerPhase(EnumSet<CanonicalizerFeature> features) {
        this(null, features);
    }

    protected FinalCanonicalizerPhase(CustomSimplification customSimplification, EnumSet<CanonicalizerFeature> features) {
        super(customSimplification, features);
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
    protected void run(StructuredGraph graph, CoreProviders context) {
        new Instance(graph, context).run(graph);
    }

    @Override
    public void applyIncremental(StructuredGraph graph, CoreProviders context, Mark newNodesMark) {
        new Instance(graph, context, newNodesMark).apply(graph);
    }

    @Override
    public void applyIncremental(StructuredGraph graph, CoreProviders context, Iterable<? extends Node> workingSet) {
        new Instance(graph, context, workingSet).apply(graph);
    }

    private final class Instance extends CanonicalizerPhase.Instance {
        private Instance(StructuredGraph graph, CoreProviders context) {
            super(graph, context, null, true);
        }

        private Instance(StructuredGraph graph, CoreProviders context, Iterable<? extends Node> workingSet) {
            super(graph, context, workingSet, true);
        }

        private Instance(StructuredGraph graph, CoreProviders context, Mark newNodesMark) {
            super(graph, context, newNodesMark.isStart() ? null : graph.getNewNodes(newNodesMark), true);
        }

        @Override
        protected void run(StructuredGraph graph) {
            super.run(graph);
            graph.setAfterStage(StageFlag.FINAL_CANONICALIZATION);
        }

    }

}
