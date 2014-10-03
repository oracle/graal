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
import java.util.stream.*;

import com.oracle.graal.truffle.ContextSensitiveInlining.InliningDecision;

public class ContextSensitiveInlining implements Iterable<InliningDecision> {

    private final List<InliningDecision> callSites;

    private ContextSensitiveInlining(List<InliningDecision> callSites) {
        this.callSites = callSites;
    }

    public ContextSensitiveInlining(OptimizedCallTarget sourceTarget, TruffleInliningPolicy policy) {
        this(decideInlining(OptimizedCallUtils.countNonTrivialNodes(sourceTarget, false), exploreCallSites(new ArrayList<>(Arrays.asList(sourceTarget)), policy), policy));
    }

    private static List<InliningDecision> exploreCallSites(List<OptimizedCallTarget> stack, TruffleInliningPolicy policy) {
        List<InliningDecision> exploredCallSites = new ArrayList<>();
        OptimizedCallTarget parentTarget = stack.get(stack.size() - 1);
        for (OptimizedDirectCallNode callNode : parentTarget.getCallNodes()) {
            OptimizedCallTarget currentTarget = callNode.getCurrentCallTarget();
            stack.add(currentTarget); // push
            exploredCallSites.add(exploreCallSite(stack, policy, callNode));
            stack.remove(stack.size() - 1); // pop
        }
        return exploredCallSites;
    }

    private static InliningDecision exploreCallSite(List<OptimizedCallTarget> callStack, TruffleInliningPolicy policy, OptimizedDirectCallNode callNode) {
        OptimizedCallTarget parentTarget = callStack.get(callStack.size() - 2);
        OptimizedCallTarget currentTarget = callStack.get(callStack.size() - 1);

        boolean recursive = isRecursiveStack(callStack);
        boolean maxDepth = callStack.size() >= 15;

        List<InliningDecision> childCallSites;
        double frequency = TruffleInliningHandler.calculateFrequency(parentTarget, callNode);
        int nodeCount = OptimizedCallUtils.countNonTrivialNodes(callNode.getCurrentCallTarget(), false);
        int deepNodeCount;
        if (recursive || maxDepth) {
            deepNodeCount = nodeCount;
            childCallSites = Collections.emptyList();
        } else {
            childCallSites = decideInlining(nodeCount, exploreCallSites(callStack, policy), policy);
            deepNodeCount = nodeCount;
            for (InliningDecision childCallSite : childCallSites) {
                if (childCallSite.isInline()) {
                    deepNodeCount += childCallSite.getProfile().getDeepNodeCount();
                }
            }
        }

        TruffleInliningProfile profile = new TruffleInliningProfile(callNode, nodeCount, deepNodeCount, frequency, recursive, null);
        profile.setScore(policy.calculateScore(profile));
        return new InliningDecision(currentTarget, profile, childCallSites);
    }

    private static boolean isRecursiveStack(List<OptimizedCallTarget> stack) {
        OptimizedCallTarget top = stack.get(stack.size() - 1);
        for (int i = 0; i < stack.size() - 1; i++) {
            if (stack.get(i) == top) {
                return true;
            }
        }
        return false;
    }

    private static List<InliningDecision> decideInlining(int nodeCount, List<InliningDecision> callSites, TruffleInliningPolicy policy) {
        int deepNodeCount = nodeCount;
        int index = 0;
        for (InliningDecision callSite : callSites.stream().sorted().collect(Collectors.toList())) {
            TruffleInliningProfile profile = callSite.getProfile();
            profile.setQueryIndex(index++);
            if (policy.isAllowed(profile, deepNodeCount)) {
                callSite.setInline(true);
                deepNodeCount += profile.getDeepNodeCount();
            }
        }
        return callSites;
    }

    public boolean isInlined(List<OptimizedDirectCallNode> callNodeTrace) {
        if (callNodeTrace.isEmpty()) {
            return false;
        }

        InliningDecision prev = null;
        for (int i = 0; i < callNodeTrace.size(); i++) {
            if (prev == null) {
                prev = findByCall(callNodeTrace.get(i));
            } else {
                prev = prev.findByCall(callNodeTrace.get(i));
            }
            if (prev == null || !prev.isInline()) {
                return false;
            }
        }
        return true;
    }

    public int countCalls() {
        return callSites.stream().mapToInt(callSite -> callSite.isInline() ? callSite.countCalls() + 1 : 1).sum();
    }

    public int countInlinedCalls() {
        return callSites.stream().filter(InliningDecision::isInline).mapToInt(callSite -> callSite.countInlinedCalls() + 1).sum();
    }

    public List<InliningDecision> getCallSites() {
        return callSites;
    }

    public Iterator<InliningDecision> iterator() {
        return callSites.iterator();
    }

    public InliningDecision findByCall(OptimizedDirectCallNode callNode) {
        return getCallSites().stream().filter(c -> c.getProfile().getCallNode() == callNode).findFirst().orElse(null);
    }

    public static final class InliningDecision extends ContextSensitiveInlining implements Comparable<InliningDecision> {

        private final OptimizedCallTarget target;
        private final TruffleInliningProfile profile;
        private boolean inline;

        public InliningDecision(OptimizedCallTarget target, TruffleInliningProfile profile, List<InliningDecision> children) {
            super(children);
            this.target = target;
            this.profile = profile;
        }

        public OptimizedCallTarget getTarget() {
            return target;
        }

        public void setInline(boolean inline) {
            this.inline = inline;
        }

        public boolean isInline() {
            return inline;
        }

        public TruffleInliningProfile getProfile() {
            return profile;
        }

        public int compareTo(InliningDecision o) {
            return Double.compare(o.getProfile().getScore(), getProfile().getScore());
        }

        public boolean isSameAs(InliningDecision other) {
            if (getTarget() != other.getTarget()) {
                return false;
            } else if (isInline() != other.isInline()) {
                return false;
            } else if (!isInline()) {
                assert !other.isInline();
                return true;
            } else {
                Iterator<InliningDecision> i1 = iterator();
                Iterator<InliningDecision> i2 = other.iterator();
                while (i1.hasNext() && i2.hasNext()) {
                    if (!i1.next().isSameAs(i2.next())) {
                        return false;
                    }
                }
                return !i1.hasNext() && !i2.hasNext();
            }
        }

    }

}
