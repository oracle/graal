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

import com.oracle.graal.truffle.OptimizedCallUtils.InlinedCallVisitor;
import com.oracle.truffle.api.nodes.*;

public final class TruffleInliningHandler {

    private final ProfileScoreComparator inliningOrder = new ProfileScoreComparator();

    private final OptimizedCallTarget callTarget;
    private final TruffleInliningPolicy policy;
    private final Map<TruffleCallPath, TruffleInliningProfile> profiles;
    private final Map<OptimizedCallTarget, TruffleInliningResult> inliningResultCache;

    public TruffleInliningHandler(OptimizedCallTarget callTarget, TruffleInliningPolicy policy, Map<OptimizedCallTarget, TruffleInliningResult> inliningResultCache) {
        this.callTarget = callTarget;
        this.policy = policy;
        this.profiles = new HashMap<>();
        this.inliningResultCache = inliningResultCache;
    }

    public TruffleInliningResult inline(int originalTotalNodeCount) {
        Set<TruffleCallPath> inlinedPathes = new HashSet<>();
        TruffleInliningResult result = new TruffleInliningResult(callTarget, inlinedPathes);
        TruffleCallPath startPath = new TruffleCallPath(callTarget);

        PriorityQueue<TruffleInliningProfile> queue = new PriorityQueue<>(10, inliningOrder);
        queueCallSitesForInlining(result, startPath, queue);

        int budget = TruffleCompilerOptions.TruffleInliningMaxCallerSize.getValue() - originalTotalNodeCount;
        int index = 0;
        while (!queue.isEmpty()) {
            TruffleInliningProfile profile = queue.poll();
            profile.setQueryIndex(index);
            budget = tryInline(queue, result, inlinedPathes, profile, budget);
            profiles.put(profile.getCallPath(), profile);
            index++;
        }
        return result;
    }

    private int tryInline(PriorityQueue<TruffleInliningProfile> queue, TruffleInliningResult result, Set<TruffleCallPath> inlinedPathes, TruffleInliningProfile profile, int budget) {

        if (policy.isAllowed(result, profile, budget)) {
            int remainingBudget;
            inlinedPathes.add(profile.getCallPath());
            if (policy.isAllowedDeep(result, profile, budget)) {
                if (profile.getRecursiveResult() != null) {
                    TruffleInliningResult inliningResult = profile.getRecursiveResult();
                    for (TruffleCallPath recursiveInlinedPath : inliningResult) {
                        inlinedPathes.add(profile.getCallPath().append(recursiveInlinedPath));
                    }
                }
                remainingBudget = budget - profile.getDeepNodeCount();
            } else {
                remainingBudget = budget - profile.getNodeCount();
            }

            queueCallSitesForInlining(result, profile.getCallPath(), queue);
            return remainingBudget;
        }

        return budget;
    }

    public TruffleInliningPolicy getPolicy() {
        return policy;
    }

    public Map<TruffleCallPath, TruffleInliningProfile> getProfiles() {
        return profiles;
    }

    private void queueCallSitesForInlining(final TruffleInliningResult currentDecision, TruffleCallPath fromPath, final Collection<TruffleInliningProfile> queue) {
        fromPath.getCallTarget().getRootNode().accept(new InlinedCallVisitor(currentDecision, fromPath) {
            @Override
            public boolean visit(TruffleCallPath path, Node node) {
                if (node instanceof OptimizedCallNode) {
                    if (!currentDecision.isInlined(path)) {
                        addToQueue(queue, currentDecision, path);
                    }
                }
                return true;
            }
        });
    }

    private void addToQueue(final Collection<TruffleInliningProfile> queue, final TruffleInliningResult currentDecision, TruffleCallPath path) {
        queue.add(lookupProfile(currentDecision, path));
    }

    public List<TruffleInliningProfile> lookupProfiles(final TruffleInliningResult currentDecision, TruffleCallPath fromPath) {
        final List<TruffleInliningProfile> pathes = new ArrayList<>();
        fromPath.getCallTarget().getRootNode().accept(new InlinedCallVisitor(currentDecision, fromPath) {
            @Override
            public boolean visit(TruffleCallPath path, Node node) {
                if (node instanceof OptimizedCallNode) {
                    pathes.add(lookupProfile(currentDecision, path));
                }
                return true;
            }
        });
        return pathes;
    }

    public TruffleInliningProfile lookupProfile(TruffleInliningResult state, TruffleCallPath callPath) {
        TruffleInliningProfile profile = profiles.get(callPath);
        if (profile != null) {
            return profile;
        }

        int callSites = callPath.getCallTarget().getKnownCallSiteCount();
        int nodeCount = OptimizedCallUtils.countNonTrivialNodes(state, callPath);
        double frequency = calculatePathFrequency(callPath);
        boolean forced = callPath.getCallNode().isInlined();
        TruffleInliningResult recursiveResult = lookupChildResult(callPath, nodeCount);
        int deepNodeCount = OptimizedCallUtils.countNonTrivialNodes(recursiveResult, new TruffleCallPath(callPath.getCallTarget()));

        profile = new TruffleInliningProfile(callPath, callSites, nodeCount, deepNodeCount, frequency, forced, recursiveResult);
        profile.setScore(policy.calculateScore(profile));
        profiles.put(callPath, profile);

        return profile;
    }

    private TruffleInliningResult lookupChildResult(TruffleCallPath callPath, int nodeCount) {
        OptimizedCallTarget target = callPath.getCallTarget();

        TruffleInliningResult recursiveResult = target.getInliningResult();

        if (recursiveResult == null) {
            recursiveResult = inliningResultCache.get(callPath.getCallTarget());

            if (recursiveResult == null) {
                if (inliningResultCache.containsKey(target)) {
                    // cancel on recursion
                    return new TruffleInliningResult(target, new HashSet<TruffleCallPath>());
                }
                inliningResultCache.put(target, null);
                TruffleInliningHandler handler = new TruffleInliningHandler(target, policy, inliningResultCache);
                recursiveResult = handler.inline(nodeCount);
                inliningResultCache.put(callPath.getCallTarget(), recursiveResult);
            }
        }
        return recursiveResult;
    }

    private static double calculatePathFrequency(TruffleCallPath callPath) {
        int parentCallCount = -1;
        double f = 1.0d;
        for (TruffleCallPath path : callPath.toList()) {
            if (parentCallCount != -1) {
                f *= path.getCallNode().getCallCount() / (double) parentCallCount;
            }
            parentCallCount = path.getCallTarget().getCompilationProfile().getCallCount();
        }
        return f;
    }

    private final class ProfileScoreComparator implements Comparator<TruffleInliningProfile> {

        public int compare(TruffleInliningProfile o1, TruffleInliningProfile o2) {
            return Double.compare(o2.getScore(), o1.getScore());
        }

    }
}
