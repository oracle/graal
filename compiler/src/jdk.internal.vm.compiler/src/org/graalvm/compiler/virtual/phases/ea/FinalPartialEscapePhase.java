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
package org.graalvm.compiler.virtual.phases.ea;

import static org.graalvm.compiler.core.common.GraalOptions.EscapeAnalyzeOnly;

import java.util.Optional;

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;

/**
 * Performs the final {@link PartialEscapePhase}.
 */
public class FinalPartialEscapePhase extends PartialEscapePhase {

    public FinalPartialEscapePhase(boolean iterative, CanonicalizerPhase canonicalizer, BasePhase<CoreProviders> cleanupPhase, OptionValues options) {
        super(iterative, Options.OptEarlyReadElimination.getValue(options), canonicalizer, cleanupPhase, options);
    }

    public FinalPartialEscapePhase(int iterations, CanonicalizerPhase canonicalizer, BasePhase<CoreProviders> cleanupPhase, OptionValues options) {
        super(iterations, Options.OptEarlyReadElimination.getValue(options), canonicalizer, cleanupPhase);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.ifApplied(this, StageFlag.FINAL_PARTIAL_ESCAPE, graphState));
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (VirtualUtil.matches(graph, EscapeAnalyzeOnly.getValue(graph.getOptions()))) {
            graph.getGraphState().setDuringStage(StageFlag.FINAL_PARTIAL_ESCAPE);

            super.run(graph, context);
        }
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(StageFlag.FINAL_PARTIAL_ESCAPE);
    }
}
