/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.virtual.phases.ea;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.debug.Debug.*;
import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.*;

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.cfs.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;

public class IterativeInliningPhase extends AbstractInliningPhase {

    private final CanonicalizerPhase canonicalizer;

    public IterativeInliningPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public static final void trace(String format, Object... obj) {
        if (TraceEscapeAnalysis.getValue() && Debug.isLogEnabled()) {
            Debug.logv(format, obj);
        }
    }

    @Override
    protected void run(final StructuredGraph graph, final HighTierContext context) {
        runIterations(graph, true, context);
        runIterations(graph, false, context);
    }

    private void runIterations(final StructuredGraph graph, final boolean simple, final HighTierContext context) {
        for (int iteration = 0; iteration < EscapeAnalysisIterations.getValue(); iteration++) {
            try (Scope s = Debug.scope(isEnabled() ? "iteration " + iteration : null)) {
                boolean progress = false;
                PartialEscapePhase ea = new PartialEscapePhase(false, canonicalizer);
                boolean eaResult = ea.runAnalysis(graph, context);
                progress |= eaResult;

                Map<Invoke, Double> hints = PEAInliningHints.getValue() ? PartialEscapePhase.getHints(graph) : null;

                InliningPhase inlining = new InliningPhase(hints, new CanonicalizerPhase(true));
                inlining.setMaxMethodsPerInlining(simple ? 1 : Integer.MAX_VALUE);
                inlining.apply(graph, context);
                progress |= inlining.getInliningCount() > 0;

                new DeadCodeEliminationPhase(OPTIONAL).apply(graph);

                boolean reduceOrEliminate = FlowSensitiveReduction.getValue() || ConditionalElimination.getValue();
                if (reduceOrEliminate && OptCanonicalizer.getValue()) {
                    canonicalizer.apply(graph, context);
                    if (FlowSensitiveReduction.getValue()) {
                        new IterativeFlowSensitiveReductionPhase(canonicalizer).apply(graph, context);
                    } else {
                        new IterativeConditionalEliminationPhase(canonicalizer).apply(graph, context);
                    }
                }
                if (!progress) {
                    break;
                }
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }
    }
}
