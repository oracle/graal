/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import java.util.*;

import com.oracle.truffle.api.nodes.*;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

public class OptimizedCallNodeProfile implements TruffleInliningProfile {

    private static final String REASON_RECURSION = "recursion";
    private static final String REASON_FREQUENCY_CUTOFF = "frequency < " + TruffleInliningMinFrequency.getValue();
    private static final String REASON_MAXIMUM_NODE_COUNT = "shallowTargetCount  > " + TruffleInliningMaxCalleeSize.getValue();
    private static final String REASON_MAXIMUM_TOTAL_NODE_COUNT = "deepTargetCount + currentNodeCount > " + TruffleInliningMaxCallerSize.getValue();

    private final OptimizedCallNode callNode;

    private final int targetDeepNodeCount;
    private final int targetShallowNodeCount;
    private final List<OptimizedCallTarget> compilationRoots;
    private final double averageFrequency;
    private final double score;
    private String reason;

    public OptimizedCallNodeProfile(OptimizedCallNode callNode) {
        this.callNode = callNode;
        RootNode inlineRoot = callNode.getExecutedCallTarget().getRootNode();
        this.targetShallowNodeCount = NodeUtil.countNodes(inlineRoot, null, false);
        this.targetDeepNodeCount = NodeUtil.countNodes(inlineRoot, null, true);
        this.compilationRoots = findCompilationRoots(callNode);
        this.averageFrequency = calculateAverageFrequency(compilationRoots);
        this.score = calculateScore();
    }

    public OptimizedCallNode getCallNode() {
        return callNode;
    }

    public double getScore() {
        return score;
    }

    public double calculateScore() {
        return averageFrequency / targetDeepNodeCount;
    }

    public boolean isInliningAllowed() {
        OptimizedCallTarget inlineTarget = callNode.getExecutedCallTarget();
        for (OptimizedCallTarget compilationRoot : compilationRoots) {
            if (compilationRoot == inlineTarget) {
                // recursive call found
                reason = REASON_RECURSION;
                return false;
            }
        }

        // frequency cut-off
        if (averageFrequency < TruffleInliningMinFrequency.getValue() && targetDeepNodeCount > TruffleInliningTrivialSize.getValue()) {
            reason = REASON_FREQUENCY_CUTOFF;
            return false;
        }

        if (targetShallowNodeCount > TruffleInliningMaxCalleeSize.getValue()) {
            reason = REASON_MAXIMUM_NODE_COUNT;
            return false;
        }

        // The maximum total node count cannot be cached since it may change during inlining.
        int currentNodeCount = calculateCurrentNodeCount(compilationRoots);
        if (targetDeepNodeCount + currentNodeCount > TruffleInliningMaxCallerSize.getValue()) {
            reason = REASON_MAXIMUM_TOTAL_NODE_COUNT;
            return false;
        }

        return true;
    }

    private static int calculateCurrentNodeCount(List<OptimizedCallTarget> compilationRoots) {
        int currentNodeCount = 0;
        for (OptimizedCallTarget compilationRoot : compilationRoots) {
            if (compilationRoot.getRootNode().getParentInlinedCalls().isEmpty()) {
                currentNodeCount = Math.max(currentNodeCount, NodeUtil.countNodes(compilationRoot.getRootNode(), null, true));
            }
        }
        return currentNodeCount;
    }

    @SuppressWarnings("unused")
    private double calculateSimpleFrequency() {
        RootNode root = callNode.getRootNode();
        OptimizedCallTarget target = ((OptimizedCallTarget) root.getCallTarget());

        int totalCallCount = target.getCompilationProfile().getCallCount();
        List<CallNode> parentInlined = root.getParentInlinedCalls();
        for (CallNode node : parentInlined) {
            int callCount = ((OptimizedCallNode) node).getCallCount();
            if (callCount >= 0) {
                totalCallCount += callCount;
            }
        }
        return callNode.getCallCount() / (double) totalCallCount;
    }

    private double calculateAverageFrequency(List<OptimizedCallTarget> roots) {
        int compilationRootCallCountSum = 0;
        int compilationRootCount = 0;
        for (OptimizedCallTarget compilationRoot : roots) {
            if (compilationRoot.getRootNode().getParentInlinedCalls().isEmpty()) {
                compilationRootCallCountSum += compilationRoot.getCompilationProfile().getCallCount();
                compilationRootCount++;
            }
        }
        return (callNode.getCallCount() * compilationRootCount) / (double) compilationRootCallCountSum;
    }

    private static List<OptimizedCallTarget> findCompilationRoots(Node call) {
        RootNode root = call.getRootNode();
        if (root == null) {
            return Collections.emptyList();
        }
        List<OptimizedCallTarget> roots = new ArrayList<>();
        roots.add((OptimizedCallTarget) root.getCallTarget());
        for (CallNode callNode : root.getParentInlinedCalls()) {
            roots.addAll(findCompilationRoots(callNode));
        }
        return roots;
    }

    public int compareTo(TruffleInliningProfile o) {
        if (o instanceof OptimizedCallNodeProfile) {
            return Double.compare(((OptimizedCallNodeProfile) o).getScore(), getScore());
        }
        return 0;
    }

    public Map<String, Object> getDebugProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("shallowCount", targetShallowNodeCount);
        properties.put("deepCount", targetDeepNodeCount);
        properties.put("currentCount", calculateCurrentNodeCount(compilationRoots));
        properties.put("score", score);
        properties.put("frequency", averageFrequency);
        properties.put("callCount", callNode.getCallCount());
        properties.put("reason", reason);
        return properties;
    }

}
