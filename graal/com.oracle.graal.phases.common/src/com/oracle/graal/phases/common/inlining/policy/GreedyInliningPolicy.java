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
package com.oracle.graal.phases.common.inlining.policy;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.spi.Replacements;
import com.oracle.graal.phases.common.inlining.InliningUtil;
import com.oracle.graal.phases.common.inlining.info.InlineInfo;

import java.util.Map;
import java.util.function.ToDoubleFunction;

import static com.oracle.graal.compiler.common.GraalOptions.*;

public class GreedyInliningPolicy extends AbstractInliningPolicy {

    private static final DebugMetric metricInliningStoppedByMaxDesiredSize = Debug.metric("InliningStoppedByMaxDesiredSize");

    public GreedyInliningPolicy(Map<Invoke, Double> hints) {
        super(hints);
    }

    public boolean continueInlining(StructuredGraph currentGraph) {
        if (currentGraph.getNodeCount() >= MaximumDesiredSize.getValue()) {
            InliningUtil.logInliningDecision("inlining is cut off by MaximumDesiredSize");
            metricInliningStoppedByMaxDesiredSize.increment();
            return false;
        }
        return true;
    }

    @Override
    public boolean isWorthInlining(ToDoubleFunction<FixedNode> probabilities, Replacements replacements, InlineInfo info, int inliningDepth, double probability, double relevance,
                    boolean fullyProcessed) {
        if (InlineEverything.getValue()) {
            return InliningUtil.logInlinedMethod(info, inliningDepth, fullyProcessed, "inline everything");
        }

        if (isIntrinsic(replacements, info)) {
            return InliningUtil.logInlinedMethod(info, inliningDepth, fullyProcessed, "intrinsic");
        }

        if (info.shouldInline()) {
            return InliningUtil.logInlinedMethod(info, inliningDepth, fullyProcessed, "forced inlining");
        }

        double inliningBonus = getInliningBonus(info);
        int nodes = determineNodeCount(info);
        int lowLevelGraphSize = previousLowLevelGraphSize(info);

        if (SmallCompiledLowLevelGraphSize.getValue() > 0 && lowLevelGraphSize > SmallCompiledLowLevelGraphSize.getValue() * inliningBonus) {
            return InliningUtil.logNotInlinedMethod(info, inliningDepth, "too large previous low-level graph (low-level-nodes: %d, relevance=%f, probability=%f, bonus=%f, nodes=%d)",
                            lowLevelGraphSize, relevance, probability, inliningBonus, nodes);
        }

        if (nodes < TrivialInliningSize.getValue() * inliningBonus) {
            return InliningUtil.logInlinedMethod(info, inliningDepth, fullyProcessed, "trivial (relevance=%f, probability=%f, bonus=%f, nodes=%d)", relevance, probability, inliningBonus, nodes);
        }

        /*
         * TODO (chaeubl): invoked methods that are on important paths but not yet compiled -> will
         * be compiled anyways and it is likely that we are the only caller... might be useful to
         * inline those methods but increases bootstrap time (maybe those methods are also getting
         * queued in the compilation queue concurrently)
         */
        double invokes = determineInvokeProbability(probabilities, info);
        if (LimitInlinedInvokes.getValue() > 0 && fullyProcessed && invokes > LimitInlinedInvokes.getValue() * inliningBonus) {
            return InliningUtil.logNotInlinedMethod(info, inliningDepth, "callee invoke probability is too high (invokeP=%f, relevance=%f, probability=%f, bonus=%f, nodes=%d)", invokes, relevance,
                            probability, inliningBonus, nodes);
        }

        double maximumNodes = computeMaximumSize(relevance, (int) (MaximumInliningSize.getValue() * inliningBonus));
        if (nodes <= maximumNodes) {
            return InliningUtil.logInlinedMethod(info, inliningDepth, fullyProcessed, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d <= %f)", relevance, probability,
                            inliningBonus, nodes, maximumNodes);
        }

        return InliningUtil.logNotInlinedMethod(info, inliningDepth, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d > %f)", relevance, probability, inliningBonus, nodes,
                        maximumNodes);
    }
}
