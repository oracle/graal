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

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.io.*;
import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.CallNode.*;

class TruffleInliningImpl implements TruffleInlining {

    private static final int MIN_INVOKES_AFTER_INLINING = 2;

    private static final PrintStream OUT = TTY.out().out();

    public int getReprofileCount() {
        return TruffleCompilerOptions.TruffleInliningReprofileCount.getValue();
    }

    public int getInvocationReprofileCount() {
        return MIN_INVOKES_AFTER_INLINING;
    }

    private static void refresh(InliningPolicy policy, List<InlinableCallSiteInfo> infos) {
        for (InlinableCallSiteInfo info : infos) {
            info.refresh(policy);
        }
    }

    @Override
    public boolean performInlining(OptimizedCallTarget target) {
        final InliningPolicy policy = new InliningPolicy(target);
        if (!policy.continueInlining()) {
            if (TraceTruffleInliningDetails.getValue()) {
                List<InlinableCallSiteInfo> inlinableCallSites = getInlinableCallSites(policy, target);
                if (!inlinableCallSites.isEmpty()) {
                    OUT.printf("[truffle] inlining hit caller size limit (%3d >= %3d).%3d remaining call sites in %s:\n", policy.callerNodeCount, TruffleInliningMaxCallerSize.getValue(),
                                    inlinableCallSites.size(), target.getRootNode());
                    InliningPolicy.sortByRelevance(inlinableCallSites);
                    printCallSiteInfo(policy, inlinableCallSites, "");
                }
            }
            return false;
        }

        List<InlinableCallSiteInfo> inlinableCallSites = getInlinableCallSites(policy, target);
        if (inlinableCallSites.isEmpty()) {
            return false;
        }

        InliningPolicy.sortByRelevance(inlinableCallSites);

        boolean inlined = false;
        for (InlinableCallSiteInfo inlinableCallSite : inlinableCallSites) {
            if (!inlinableCallSite.isWorth()) {
                break;
            }
            if (inlinableCallSite.getCallNode().inline()) {
                if (TraceTruffleInlining.getValue()) {
                    printCallSiteInfo(policy, inlinableCallSite, "inlined");
                }
                inlined = true;
                break;
            }
        }

        if (inlined) {
            for (InlinableCallSiteInfo callSite : inlinableCallSites) {
                CompilerCallView callView = callSite.getCallNode().getCompilerCallView();
                if (callView != null) {
                    callView.resetCallCount();
                }
            }
        } else {
            if (TraceTruffleInliningDetails.getValue()) {
                OUT.printf("[truffle] inlining stopped.%3d remaining call sites in %s:\n", inlinableCallSites.size(), target.getRootNode());
                printCallSiteInfo(policy, inlinableCallSites, "");
            }
        }

        return inlined;
    }

    private static void printCallSiteInfo(InliningPolicy policy, List<InlinableCallSiteInfo> inlinableCallSites, String msg) {
        for (InlinableCallSiteInfo candidate : inlinableCallSites) {
            printCallSiteInfo(policy, candidate, msg);
        }
    }

    private static void printCallSiteInfo(InliningPolicy policy, InlinableCallSiteInfo callSite, String msg) {
        String calls = String.format("%4s/%4s", callSite.getCallCount(), policy.callerInvocationCount);
        String nodes = String.format("%3s/%3s", callSite.getInlineNodeCount(), policy.callerNodeCount);
        CallTarget inlined = callSite.getCallNode().getCallTarget();
        OUT.printf("[truffle] %-9s %-50s %08x |Tree %8s |Calls %6s %7.3f @ %s\n", msg, inlined, inlined.hashCode(), nodes, calls, policy.metric(callSite), callSite.getCallNode());
    }

    private static final class InliningPolicy {

        private final int callerNodeCount;
        private final int callerInvocationCount;

        public InliningPolicy(OptimizedCallTarget caller) {
            this.callerNodeCount = NodeUtil.countNodes(caller.getRootNode(), null, true);
            this.callerInvocationCount = caller.getCompilationProfile().getOriginalInvokeCounter();
        }

        public boolean continueInlining() {
            return callerNodeCount < TruffleInliningMaxCallerSize.getValue();
        }

        public boolean isWorthInlining(InlinableCallSiteInfo callSite) {
            return callSite.getInlineNodeCount() <= TruffleInliningMaxCalleeSize.getValue() && callSite.getInlineNodeCount() + callerNodeCount <= TruffleInliningMaxCallerSize.getValue() &&
                            callSite.getCallCount() > 0 && callSite.getRecursiveDepth() < TruffleInliningMaxRecursiveDepth.getValue() &&
                            (frequency(callSite) >= TruffleInliningMinFrequency.getValue() || callSite.getInlineNodeCount() <= TruffleInliningTrivialSize.getValue());
        }

        public double metric(InlinableCallSiteInfo callSite) {
            double cost = callSite.getInlineNodeCount();
            double metric = frequency(callSite) / cost;
            return metric;
        }

        private double frequency(InlinableCallSiteInfo callSite) {
            return (double) callSite.getCallCount() / (double) callerInvocationCount;
        }

        private static void sortByRelevance(List<InlinableCallSiteInfo> inlinableCallSites) {
            Collections.sort(inlinableCallSites, new Comparator<InlinableCallSiteInfo>() {

                @Override
                public int compare(InlinableCallSiteInfo cs1, InlinableCallSiteInfo cs2) {
                    boolean cs1Worth = cs1.isWorth();
                    boolean cs2Worth = cs2.isWorth();
                    if (cs1Worth && cs2Worth) {
                        return Double.compare(cs2.getScore(), cs1.getScore());
                    } else if (cs1Worth ^ cs2Worth) {
                        return cs1Worth ? -1 : 1;
                    }
                    return 0;
                }
            });
        }
    }

    private static final class InlinableCallSiteInfo {

        private final CallNode callNode;
        private final int nodeCount;
        private final int recursiveDepth;

        private int callCount;
        private boolean worth;
        private double score;

        @SuppressWarnings("unused")
        public InlinableCallSiteInfo(InliningPolicy policy, CallNode callNode) {
            assert callNode.isInlinable();
            this.callNode = callNode;
            RootCallTarget target = (RootCallTarget) callNode.getCallTarget();

            this.nodeCount = target.getRootNode().getInlineNodeCount();
            this.recursiveDepth = calculateRecursiveDepth();
        }

        public int getRecursiveDepth() {
            return recursiveDepth;
        }

        public void refresh(InliningPolicy policy) {
            this.callCount = callNode.getCompilerCallView().getCallCount();
            this.worth = policy.isWorthInlining(this);
            if (worth) {
                this.score = policy.metric(this);
            }
            // TODO shall we refresh the node count as well?
        }

        public boolean isWorth() {
            return worth;
        }

        public double getScore() {
            return score;
        }

        private int calculateRecursiveDepth() {
            int depth = 0;

            Node parent = callNode.getParent();
            while (parent != null) {
                if (parent instanceof RootNode) {
                    RootNode root = ((RootNode) parent);
                    if (root.getCallTarget() == callNode.getCallTarget()) {
                        depth++;
                    }
                    parent = root.getParentInlinedCall();
                } else {
                    parent = parent.getParent();
                }
            }
            return depth;
        }

        public CallNode getCallNode() {
            return callNode;
        }

        public int getCallCount() {
            return callCount;
        }

        public int getInlineNodeCount() {
            return nodeCount;
        }
    }

    private static List<InlinableCallSiteInfo> getInlinableCallSites(final InliningPolicy policy, final RootCallTarget target) {
        final ArrayList<InlinableCallSiteInfo> inlinableCallSites = new ArrayList<>();
        target.getRootNode().accept(new NodeVisitor() {

            @Override
            public boolean visit(Node node) {
                if (node instanceof CallNode) {
                    CallNode callNode = (CallNode) node;

                    if (!callNode.isInlined()) {
                        if (callNode.isInlinable()) {
                            CompilerCallView view = callNode.getCompilerCallView();
                            InlinableCallSiteInfo info = (InlinableCallSiteInfo) view.load();
                            if (info == null) {
                                info = new InlinableCallSiteInfo(policy, callNode);
                                view.store(info);
                            }
                            inlinableCallSites.add(info);
                        }
                    } else {
                        callNode.getInlinedRoot().accept(this);
                    }
                }
                return true;
            }
        });
        refresh(policy, inlinableCallSites);
        return inlinableCallSites;
    }

    static List<InlinableCallSiteInfo> getInlinableCallSites(final OptimizedCallTarget target, final RootCallTarget root) {
        return getInlinableCallSites(new InliningPolicy(target), root);
    }
}
