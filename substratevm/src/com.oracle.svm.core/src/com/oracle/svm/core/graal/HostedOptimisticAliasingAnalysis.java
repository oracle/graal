/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.guards.optimistic.OptimisticFixedGuardNode;
import jdk.graal.compiler.guards.optimistic.memory.OptimisticAliasingAnalysisPhase;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

/**
 * Optimistic aliasing analysis variant for SVM hosted compilations, where aliasing guards must not
 * deoptimize.
 */
public class HostedOptimisticAliasingAnalysis extends OptimisticAliasingAnalysisPhase {

    public HostedOptimisticAliasingAnalysis(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    protected OptimisticFixedGuardNode createOptimisticFixedGuard(LogicNode isAliasing, Speculation speculation) {
        return new HostedOptimisticFixedGuardNode(isAliasing, DeoptimizationReason.Aliasing, DeoptimizationAction.InvalidateRecompile, speculation, true, null);
    }

    /**
     * Special version of a fixed guard used in SVM hosted compilations. Hosted and JIT compilation
     * have different requirements regarding states and that reflects on how we use them in the
     * compiler. A regular JIT compilation needs framestates after every side effect to properly
     * deoptimize. On SVM - for hosted compiles we perform transformations that are out of scope of
     * JIT compilation and that can only be done in hosted compiles. These transforms can be done
     * because we know we never deoptimize hosted compiles. This allows for more flexibility in the
     * AOT compiler version of Graal. However, it opens a discrepancy - the entire compiler is
     * trimmed and programmed in a way that developers cannot create invalid graphs were
     * deoptimization might fail. These verifications do not make sense for a lot of hosted compiles
     * since we never consume framestates on SVM for deoptimization. Thus, many states have
     * {@link FrameState#invalidateForDeoptimization()} set to {@code true}. To pass verification
     * phases that introduce guards, like this one, would need to make sure every insertion point of
     * a potential deopt only consumes valid states for deoptimization. However, a dominating state
     * (dominating the insertion point) on svm often is not valid for deopt. To not hinder any
     * optimization and given we anyway never deopt hosted compiles we bypass verification. We do so
     * by using special guards that cannot deoptimize (and indicate that) on svm. This gives guard
     * hoisting loop duplication more flexibility in choosing insert positions for this node.
     * Because it is not required to find a dominating state that has
     * {@link FrameState#invalidateForDeoptimization()}.
     */
    @NodeInfo
    public class HostedOptimisticFixedGuardNode extends OptimisticFixedGuardNode {

        public static final NodeClass<HostedOptimisticFixedGuardNode> TYPE = NodeClass.create(HostedOptimisticFixedGuardNode.class);

        public HostedOptimisticFixedGuardNode(LogicNode condition, DeoptimizationReason reason, DeoptimizationAction action, Speculation speculation, boolean negated,
                        NodeSourcePosition noDeoptSuccessorPosition) {
            super(TYPE, condition, reason, action, speculation, negated, noDeoptSuccessorPosition);
        }

        @Override
        public boolean canDeoptimize() {
            return false;
        }

    }

}
