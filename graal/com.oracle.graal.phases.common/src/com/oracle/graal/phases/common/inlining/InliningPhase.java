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
import com.oracle.graal.phases.common.inlining.walker.CallsiteHolder;
import com.oracle.graal.phases.common.inlining.walker.InliningData;
import com.oracle.graal.phases.common.inlining.walker.MethodInvocation;
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
     * <p>
     * The space of inlining decisions is explored depth-first with the help of a stack realized by
     * {@link com.oracle.graal.phases.common.inlining.walker.InliningData}. At any point in time,
     * its topmost element consist of:
     * <ul>
     * <li>
     * one or more {@link CallsiteHolder}s of inlining candidates, all of them corresponding to a
     * single callsite (details below). For example, "exact inline" leads to a single candidate.</li>
     * <li>
     * the callsite (for the targets above) is tracked as a {@link MethodInvocation}. The difference
     * between {@link com.oracle.graal.phases.common.inlining.walker.MethodInvocation#totalGraphs()}
     * and {@link MethodInvocation#processedGraphs()} indicates the topmost {@link CallsiteHolder}s
     * that might be delved-into to explore inlining opportunities.</li>
     * </ul>
     * </p>
     *
     * <p>
     * The bottom-most element in the stack consists of:
     * <ul>
     * <li>
     * a single {@link CallsiteHolder} (the root one, for the method on which inlining was called)</li>
     * <li>
     * a single {@link MethodInvocation} (the
     * {@link com.oracle.graal.phases.common.inlining.walker.MethodInvocation#isRoot} one, ie the
     * unknown caller of the root graph)</li>
     * </ul>
     *
     * </p>
     *
     * <p>
     * The stack grows and shrinks as choices are made among the alternatives below:
     * <ol>
     * <li>
     * not worth inlining: pop any remaining graphs not yet delved into, pop the current invocation.
     * </li>
     * <li>
     * process next invoke: delve into one of the callsites hosted in the current candidate graph,
     * determine whether any inlining should be performed in it</li>
     * <li>
     * try to inline: move past the current inlining candidate (remove it from the topmost element).
     * If that was the last one then try to inline the callsite that is (still) in the topmost
     * element of {@link com.oracle.graal.phases.common.inlining.walker.InliningData}, and then
     * remove such callsite.</li>
     * </ol>
     * </p>
     *
     * <p>
     * Some facts about the alternatives above:
     * <ul>
     * <li>
     * the first step amounts to backtracking, the 2nd one to delving, and the 3rd one also involves
     * backtracking (however after may-be inlining).</li>
     * <li>
     * the choice of abandon-and-backtrack or delve-into is depends on
     * {@link InliningPolicy#isWorthInlining} and {@link InliningPolicy#continueInlining}.</li>
     * <li>
     * the 3rd choice is picked when both of the previous ones aren't picked</li>
     * <li>
     * as part of trying-to-inline, {@link InliningPolicy#isWorthInlining} again sees use, but
     * that's another story.</li>
     * </ul>
     * </p>
     *
     */
    @Override
    protected void run(final StructuredGraph graph, final HighTierContext context) {
        final InliningData data = new InliningData(graph, context, maxMethodPerInlining, canonicalizer, inliningPolicy);

        while (data.hasUnprocessedGraphs()) {
            boolean wasInlined = data.moveForward();
            if (wasInlined) {
                inliningCount++;
            }
        }

        assert data.inliningDepth() == 0;
        assert data.graphCount() == 0;
    }

}
