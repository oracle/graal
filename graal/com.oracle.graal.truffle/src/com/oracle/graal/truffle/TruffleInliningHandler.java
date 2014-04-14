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

public final class TruffleInliningHandler {

    private static final int MAXIMUM_RECURSIVE_DEPTH = 15;
    private static final ProfileScoreComparator INLINING_SCORE = new ProfileScoreComparator();
    private final TruffleInliningPolicy policy;
    private final Map<OptimizedCallTarget, TruffleInliningResult> resultCache;

    public TruffleInliningHandler(TruffleInliningPolicy policy) {
        this.policy = policy;
        this.resultCache = new HashMap<>();
    }

    public TruffleInliningResult decideInlining(OptimizedCallTarget target, int depth) {
        if (resultCache.containsKey(target)) {
            return resultCache.get(target);
        }
        resultCache.put(target, null);
        TruffleInliningResult result = decideInliningImpl(target, depth);
        resultCache.put(target, result);
        return result;
    }

    private TruffleInliningResult decideInliningImpl(OptimizedCallTarget target, int depth) {
        List<TruffleInliningProfile> profiles = lookupProfiles(target, depth);
        Set<TruffleInliningProfile> inlined = new HashSet<>();
        Collections.sort(profiles, INLINING_SCORE);
        int budget = TruffleCompilerOptions.TruffleInliningMaxCallerSize.getValue() - OptimizedCallUtils.countNonTrivialNodes(target, true);
        int index = 0;
        for (TruffleInliningProfile profile : profiles) {
            profile.setQueryIndex(index++);
            if (policy.isAllowed(profile, budget)) {
                inlined.add(profile);
                budget -= profile.getDeepNodeCount();
            }
        }

        int deepNodeCount = TruffleCompilerOptions.TruffleInliningMaxCallerSize.getValue() - budget;
        return new TruffleInliningResult(target, profiles, inlined, deepNodeCount);
    }

    private List<TruffleInliningProfile> lookupProfiles(final OptimizedCallTarget target, int depth) {
        final List<OptimizedDirectCallNode> callNodes = new ArrayList<>();
        target.getRootNode().accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (node instanceof OptimizedDirectCallNode) {
                    callNodes.add((OptimizedDirectCallNode) node);
                }
                return true;
            }
        });
        final List<TruffleInliningProfile> profiles = new ArrayList<>();
        for (OptimizedDirectCallNode callNode : callNodes) {
            profiles.add(lookupProfile(target, callNode, depth));
        }
        return profiles;
    }

    public TruffleInliningProfile lookupProfile(OptimizedCallTarget parentTarget, OptimizedDirectCallNode ocn, int depth) {
        OptimizedCallTarget target = ocn.getCurrentCallTarget();

        int callSites = ocn.getCurrentCallTarget().getKnownCallSiteCount();
        int nodeCount = OptimizedCallUtils.countNonTrivialNodes(target, false);
        double frequency = calculateFrequency(parentTarget, ocn);
        boolean forced = ocn.isInliningForced();

        int deepNodeCount;
        TruffleInliningResult recursiveResult;
        boolean recursiveCall = false;
        if (target.inliningPerformed || depth > MAXIMUM_RECURSIVE_DEPTH) {
            deepNodeCount = OptimizedCallUtils.countNonTrivialNodes(target, true);
            recursiveResult = null;
        } else {
            recursiveResult = decideInlining(ocn.getCurrentCallTarget(), depth + 1);
            if (recursiveResult == null) {
                recursiveCall = true;
                deepNodeCount = Integer.MAX_VALUE;
            } else {
                deepNodeCount = recursiveResult.getNodeCount();
            }
        }

        TruffleInliningProfile profile = new TruffleInliningProfile(ocn, callSites, nodeCount, deepNodeCount, frequency, forced, recursiveCall, recursiveResult);
        profile.setScore(policy.calculateScore(profile));
        return profile;
    }

    public TruffleInliningPolicy getPolicy() {
        return policy;
    }

    private static double calculateFrequency(OptimizedCallTarget target, OptimizedDirectCallNode ocn) {
        return (double) Math.max(1, target.getCompilationProfile().getCallCount()) / Math.max(1, ocn.getCallCount());
    }

    private final static class ProfileScoreComparator implements Comparator<TruffleInliningProfile> {

        public int compare(TruffleInliningProfile o1, TruffleInliningProfile o2) {
            return Double.compare(o2.getScore(), o1.getScore());
        }

    }
}
