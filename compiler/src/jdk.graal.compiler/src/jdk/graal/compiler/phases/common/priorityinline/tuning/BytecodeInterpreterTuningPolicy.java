/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.tuning;

import jdk.graal.compiler.phases.common.priorityinline.CallTree;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.ParentNode;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class BytecodeInterpreterTuningPolicy extends TuningPolicy {

    private static boolean isInterpreter(TruffleHostEnvironment env, CallTree callTree) {
        final ResolvedJavaMethod method = callTree.root().getReadonlySubgraph().method();
        var hostInfo = env.getHostMethodInfo(method);
        return hostInfo.isBytecodeInterpreterSwitch();
    }

    private static boolean isInterpreterBoundary(TruffleHostEnvironment env, CallTreeNode node) {
        if (node.targetMethod() == null) {
            return true;
        }
        final ResolvedJavaMethod method = node.targetMethod();
        var hostInfo = env.getHostMethodInfo(method);
        return hostInfo.isBytecodeInterpreterSwitchBoundary() || hostInfo.isBytecodeInterpreterSwitch();
    }

    /**
     * Determines whether the bytecode-interpreter tuning should be applied to {@code node}.
     * Subclasses can override this method to reuse the tuning for other forms of bytecode
     * interpreter compilation.
     */
    protected boolean appliesTo(CallTreeNode node) {
        TruffleHostEnvironment env = env(node);
        return env != null && isInterpreter(env, node.callTree()) && !isInterpreterBoundary(env, node);
    }

    @Override
    public double cutoffLocalBenefitAmplifier(CutoffNode node) {
        if (!appliesTo(node)) {
            return 1.0;
        }
        double frequencyAdjustment = 1.0 / Math.min(1.0, Math.max(0.001, node.getFrequency()));
        double codeSizeDiscount = Math.max(1.0, Math.sqrt(node.getCostEstimate() / 50.0));
        return 30.0 * frequencyAdjustment * codeSizeDiscount;
    }

    @Override
    public double parentLocalBenefitAmplifier(ParentNode node) {
        if (!appliesTo(node)) {
            return 1.0;
        }
        double frequencyAdjustment = 1.0 / Math.min(1.0, Math.max(0.001, node.getFrequency()));
        return 10.0 * frequencyAdjustment;
    }

    @Override
    public boolean mustInline(ParentNode node) {
        return false;
    }

    @Override
    public double relativeBenefitThresholdMultiplier(CutoffNode node) {
        if (!appliesTo(node)) {
            return super.relativeBenefitThresholdMultiplier(node);
        }
        return 0.0002;
    }

    @Override
    public double smallRootIrPenaltyMultiplier(CutoffNode node) {
        if (!appliesTo(node)) {
            return super.smallRootIrPenaltyMultiplier(node);
        }
        return 0.0;
    }

    @Override
    public double largeChildrenCountPenaltyMultiplier(CutoffNode node) {
        if (!appliesTo(node)) {
            return super.largeChildrenCountPenaltyMultiplier(node);
        }
        return 0.0;
    }

    @Override
    public double defaultMinimumFrequencyForExpansionMultiplier(CutoffNode node) {
        if (!appliesTo(node)) {
            return super.defaultMinimumFrequencyForExpansionMultiplier(node);
        }
        return 0.0;
    }

    private static TruffleHostEnvironment env(CallTreeNode node) {
        return TruffleHostEnvironment.get(node.callTree().root().getReadonlySubgraph().method());
    }

    @SuppressWarnings("unused")
    @Override
    public double rootSizePenaltyMultiplier(CutoffNode node) {
        if (!appliesTo(node)) {
            return super.rootSizePenaltyMultiplier(node);
        }
        return 0.0;
    }
}
