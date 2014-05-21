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

import com.oracle.graal.api.meta.ProfilingInfo;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.spi.Replacements;
import com.oracle.graal.phases.common.inlining.InliningUtil;
import com.oracle.graal.phases.common.inlining.info.InlineInfo;
import com.oracle.graal.phases.common.inlining.info.elem.Inlineable;

import java.util.Map;
import java.util.function.ToDoubleFunction;

import static com.oracle.graal.compiler.common.GraalOptions.RelevanceCapForInlining;
import static com.oracle.graal.phases.common.inlining.InliningPhase.Options.AlwaysInlineIntrinsics;

public abstract class AbstractInliningPolicy implements InliningPolicy {

    protected final Map<Invoke, Double> hints;

    public AbstractInliningPolicy(Map<Invoke, Double> hints) {
        this.hints = hints;
    }

    protected double computeMaximumSize(double relevance, int configuredMaximum) {
        double inlineRatio = Math.min(RelevanceCapForInlining.getValue(), relevance);
        return configuredMaximum * inlineRatio;
    }

    protected double getInliningBonus(InlineInfo info) {
        if (hints != null && hints.containsKey(info.invoke())) {
            return hints.get(info.invoke());
        }
        return 1;
    }

    protected boolean isIntrinsic(Replacements replacements, InlineInfo info) {
        if (AlwaysInlineIntrinsics.getValue()) {
            return onlyIntrinsics(replacements, info);
        } else {
            return onlyForcedIntrinsics(replacements, info);
        }
    }

    private static boolean onlyIntrinsics(Replacements replacements, InlineInfo info) {
        for (int i = 0; i < info.numberOfMethods(); i++) {
            if (!InliningUtil.canIntrinsify(replacements, info.methodAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean onlyForcedIntrinsics(Replacements replacements, InlineInfo info) {
        for (int i = 0; i < info.numberOfMethods(); i++) {
            if (!InliningUtil.canIntrinsify(replacements, info.methodAt(i))) {
                return false;
            }
            if (!replacements.isForcedSubstitution(info.methodAt(i))) {
                return false;
            }
        }
        return true;
    }

    protected static int previousLowLevelGraphSize(InlineInfo info) {
        int size = 0;
        for (int i = 0; i < info.numberOfMethods(); i++) {
            ResolvedJavaMethod m = info.methodAt(i);
            ProfilingInfo profile = m.getProfilingInfo();
            int compiledGraphSize = profile.getCompilerIRSize(StructuredGraph.class);
            if (compiledGraphSize > 0) {
                size += compiledGraphSize;
            }
        }
        return size;
    }

    protected static int determineNodeCount(InlineInfo info) {
        int nodes = 0;
        for (int i = 0; i < info.numberOfMethods(); i++) {
            Inlineable elem = info.inlineableElementAt(i);
            if (elem != null) {
                nodes += elem.getNodeCount();
            }
        }
        return nodes;
    }

    protected static double determineInvokeProbability(ToDoubleFunction<FixedNode> probabilities, InlineInfo info) {
        double invokeProbability = 0;
        for (int i = 0; i < info.numberOfMethods(); i++) {
            Inlineable callee = info.inlineableElementAt(i);
            Iterable<Invoke> invokes = callee.getInvokes();
            if (invokes.iterator().hasNext()) {
                for (Invoke invoke : invokes) {
                    invokeProbability += probabilities.applyAsDouble(invoke.asNode());
                }
            }
        }
        return invokeProbability;
    }
}
