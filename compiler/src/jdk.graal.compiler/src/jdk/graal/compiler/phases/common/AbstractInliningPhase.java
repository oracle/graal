/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.common.inlining.walker.InliningData;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Common superclass for phases that perform inlining.
 */
public abstract class AbstractInliningPhase extends BasePhase<HighTierContext> {
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.unlessRunBefore(this, StageFlag.HIGH_TIER_LOWERING, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.FINAL_CANONICALIZATION, graphState));
    }

    @Override
    protected final void run(StructuredGraph graph, HighTierContext context) {
        Graph.Mark mark = graph.getMark();
        runInlining(graph, context);
        if (shouldVerifyForceInlinedInvokes(graph)) {
            verifyForceInlinedInvokes(graph, context);
        }
        if (!mark.isCurrent() && graph.getSpeculationLog() != null && graph.hasLoops()) {
            /*
             * We may have inlined new loops. We must make sure that counted loops are checked for
             * overflow before we apply the next loop optimization phase. Inlining may run multiple
             * times in different versions, possibly after some loop optimizations, and even on
             * demand (i.e., without explicitly appearing in the phase plan). For such phases the
             * stage flag mechanism isn't strong enough to express the constraint that we must run
             * DisableOverflownCountedLoops before the next loop phase. Therefore, run it
             * explicitly.
             */
            new DisableOverflownCountedLoopsPhase().run(graph);
        }
    }

    /**
     * Returns whether this phase should verify that force-inlined invokes do not remain after
     * inlining. The verification is only active when assertions are enabled. Subclasses may
     * override this method to disable verification for specialized inlining modes, but they may
     * only return {@code true} after ensuring that the superclass implementation returns
     * {@code true}.
     */
    protected boolean shouldVerifyForceInlinedInvokes(@SuppressWarnings("unused") StructuredGraph graph) {
        return Assertions.assertionsEnabled();
    }

    /**
     * Subclasses may override this method to incorporate additional force-inlining mechanisms such
     * as phase-specific filters or constraints that depend on the call site.
     */
    protected boolean isForceInlinedTarget(ResolvedJavaMethod targetMethod, @SuppressWarnings("unused") Invoke invoke) {
        return targetMethod.shouldBeInlined();
    }

    /**
     * Verifies that every invoke that is still required to inline has been removed by the inlining
     * phase.
     */
    private void verifyForceInlinedInvokes(StructuredGraph graph, HighTierContext context) {
        for (Invoke invoke : graph.getInvokes()) {
            if (mustBeInlined(invoke, context)) {
                throw GraalError.shouldNotReachHere("failed to inline force-inlined method: " + invoke + " " + invoke.getTargetMethod());
            }
        }
    }

    /**
     * Determines whether a remaining invoke still refers to a force-inlined target that the current
     * compilation context is capable of inlining.
     */
    private boolean mustBeInlined(Invoke invoke, HighTierContext context) {
        ResolvedJavaMethod targetMethod = forceInlinedTarget(invoke);
        if (targetMethod == null) {
            return false;
        }
        int recursiveInliningDepth = countRecursiveInlining(targetMethod, invoke);
        if (recursiveInliningDepth > 0) {
            return false;
        }
        return canInlineTarget(targetMethod, invoke, context, recursiveInliningDepth);
    }

    /**
     * Resolves the exact force-inlined target of an invoke when that target can be proven at the
     * call site.
     */
    private ResolvedJavaMethod forceInlinedTarget(Invoke invoke) {
        if (InliningUtil.checkInvokeConditions(invoke) != null) {
            return null;
        }
        /*
         * Verification covers force-inlined invokes that can be devirtualized to a monomorphic
         * call, even when that devirtualization relies on class-hierarchy assumptions. Specific
         * inliners may also establish monomorphism using mechanisms other than actual JVMCI
         * Assumption objects.
         */
        boolean mayUseAssumptions = true;
        ResolvedJavaMethod targetMethod = InliningData.resolveDirectOrDevirtualizedTargetMethod(invoke, mayUseAssumptions);
        return targetMethod != null && isForceInlinedTarget(targetMethod, invoke) ? targetMethod : null;
    }

    /**
     * Checks whether the resolved force-inlined target is still inlineable under the constraints of
     * the current graph and inlining context.
     */
    private static boolean canInlineTarget(ResolvedJavaMethod targetMethod, Invoke invoke, HighTierContext context, int recursiveInliningDepth) {
        StructuredGraph graph = invoke.asNode().graph();
        return InliningData.checkTargetConditionsHelper(graph, context, targetMethod, invoke, recursiveInliningDepth) == null;
    }

    /**
     * Returns how often the target method already appears in the surrounding frame-state chain.
     */
    private static int countRecursiveInlining(ResolvedJavaMethod targetMethod, Invoke invoke) {
        int count = 0;
        FrameState frameState = invoke.stateAfter();
        if (frameState == null) {
            frameState = invoke.stateDuring();
        }
        while (frameState != null) {
            if (targetMethod.equals(frameState.getMethod())) {
                count++;
            }
            frameState = frameState.outerFrameState();
        }
        return count;
    }

    /**
     * Returns whether an invoke still belongs directly to the root graph rather than having been
     * introduced from an already inlined callee.
     */
    protected static boolean isRootGraphInvoke(Invoke invoke) {
        FrameState frameState = invoke.stateAfter();
        if (frameState == null) {
            frameState = invoke.stateDuring();
        }
        return frameState == null || frameState.outerFrameState() == null;
    }

    protected abstract void runInlining(StructuredGraph graph, HighTierContext context);
}
