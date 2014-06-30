/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.inlining;

import java.util.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.policy.GreedyInliningPolicy;
import com.oracle.graal.phases.common.inlining.policy.InliningPolicy;
import com.oracle.graal.phases.common.inlining.walker.InliningData;
import com.oracle.graal.phases.tiers.*;

public class InliningPhase extends AbstractInliningPhase {

    public static class Options {

        // @formatter:off
        @Option(help = "Unconditionally inline intrinsics")
        public static final OptionValue<Boolean> AlwaysInlineIntrinsics = new OptionValue<>(false);
        // @formatter:on
    }

    private final InliningPolicy inliningPolicy;
    private final CanonicalizerPhase canonicalizer;

    private int inliningCount;
    private int maxMethodPerInlining = Integer.MAX_VALUE;

    public InliningPhase(CanonicalizerPhase canonicalizer) {
        this(new GreedyInliningPolicy(null), canonicalizer);
    }

    public InliningPhase(Map<Invoke, Double> hints, CanonicalizerPhase canonicalizer) {
        this(new GreedyInliningPolicy(hints), canonicalizer);
    }

    public InliningPhase(InliningPolicy policy, CanonicalizerPhase canonicalizer) {
        this.inliningPolicy = policy;
        this.canonicalizer = canonicalizer;
    }

    public void setMaxMethodsPerInlining(int max) {
        maxMethodPerInlining = max;
    }

    public int getInliningCount() {
        return inliningCount;
    }

    /**
     *
     * This method sets in motion the inlining machinery.
     *
     * @see InliningData
     * @see InliningData#moveForward()
     *
     */
    @Override
    protected void run(final StructuredGraph graph, final HighTierContext context) {
        final InliningData data = new InliningData(graph, context, maxMethodPerInlining, canonicalizer, inliningPolicy);

        assert data.repOK();
        while (data.hasUnprocessedGraphs()) {
            boolean wasInlined = data.moveForward();
            assert data.repOK();
            if (wasInlined) {
                inliningCount++;
            }
        }

        assert data.inliningDepth() == 0;
        assert data.graphCount() == 0;
    }

}
