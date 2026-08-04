/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases.priorityinline;

import java.util.HashMap;
import java.util.Map;

import com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisPhase.AnalysisResult;

import jdk.graal.compiler.phases.common.priorityinline.CallTree;
import jdk.graal.compiler.phases.common.priorityinline.CallTreeState;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;

/**
 * This class extends state of CallTree with fields required for IPEA runs across rounds. See
 * {@link SubstratePolicyFactory.SubstrateExpanderPolicy}.
 */
public class InterproceduralPartialEscapeAnalysisCallTreeState extends CallTreeState {

    static InterproceduralPartialEscapeAnalysisCallTreeState getIPEACallTreeState(CallTree callTree) {
        return (InterproceduralPartialEscapeAnalysisCallTreeState) callTree.state();
    }

    private int numIPEARounds;
    private boolean shouldRunIPEA;
    private boolean forceIPEA;
    private int numForceIPEA;
    private int roundsSinceForce;

    InterproceduralPartialEscapeAnalysisCallTreeState() {
        super();
        this.shouldRunIPEA = false;
        this.forceIPEA = false;
        this.numForceIPEA = 0;
        this.roundsSinceForce = 0;
        this.numIPEARounds = 0;
        this.analysisResult = null;
    }

    public void setAnalysisResult(AnalysisResult analysisResult) {
        this.analysisResult = analysisResult;
    }

    private AnalysisResult analysisResult;
    private final Map<CallTreeNode, Double> cachedLocalBenefitBoost = new HashMap<>();

    public int numIPEARounds() {
        return numIPEARounds;
    }

    public void incNumIPEARounds() {
        numIPEARounds++;
    }

    public void incNumForceIPEA() {
        numForceIPEA++;
    }

    public void incRoundsSinceForce() {
        roundsSinceForce++;
    }

    public boolean shouldRunIPEA() {
        return shouldRunIPEA;
    }

    public boolean forceIPEA() {
        return forceIPEA;
    }

    public int numForceIPEA() {
        return numForceIPEA;
    }

    public int roundsSinceForce() {
        return roundsSinceForce;
    }

    public void setShouldRunIPEA(boolean shouldRunIPEA) {
        this.shouldRunIPEA = shouldRunIPEA;
    }

    public void setForceIPEA(boolean forceIPEA) {
        this.forceIPEA = forceIPEA;
    }

    public void resetRoundsSinceForce() {
        this.roundsSinceForce = 0;
    }

    public AnalysisResult analysisResult() {
        return analysisResult;
    }

    public Double getCachedLocalBenefitBoost(CallTreeNode node) {
        return cachedLocalBenefitBoost.get(node);
    }

    public void setCachedLocalBenefitBoost(CallTreeNode node, double boost) {
        cachedLocalBenefitBoost.put(node, boost);
    }
}
