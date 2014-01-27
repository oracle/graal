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

class TruffleInliningImpl implements TruffleInlining {

    private static final int MIN_INVOKES_AFTER_INLINING = 2;

    private static final PrintStream OUT = TTY.out().out();

    public int getReprofileCount() {
        return TruffleCompilerOptions.TruffleInliningReprofileCount.getValue();
    }

    public int getInvocationReprofileCount() {
        return MIN_INVOKES_AFTER_INLINING;
    }

    @Override
    public boolean performInlining(OptimizedCallTarget target) {
        final InliningPolicy policy = new InliningPolicy(target);
        if (!policy.continueInlining()) {
            if (TraceTruffleInliningDetails.getValue()) {
                List<InlinableCallSiteInfo> inlinableCallSites = getInlinableCallSites(target);
                if (!inlinableCallSites.isEmpty()) {
                    OUT.printf("[truffle] inlining hit caller size limit (%3d >= %3d).%3d remaining call sites in %s:\n", policy.callerNodeCount, TruffleInliningMaxCallerSize.getValue(),
                                    inlinableCallSites.size(), target.getRootNode());
                    policy.sortByRelevance(inlinableCallSites);
                    printCallSiteInfo(policy, inlinableCallSites, "");
                }
            }
            return false;
        }

        List<InlinableCallSiteInfo> inlinableCallSites = getInlinableCallSites(target);
        if (inlinableCallSites.isEmpty()) {
            return false;
        }

        policy.sortByRelevance(inlinableCallSites);

        boolean inlined = false;
        for (InlinableCallSiteInfo inlinableCallSite : inlinableCallSites) {
            if (!policy.isWorthInlining(inlinableCallSite)) {
                break;
            }
            if (inlinableCallSite.getCallSite().inline(target)) {
                if (TraceTruffleInlining.getValue()) {
                    printCallSiteInfo(policy, inlinableCallSite, "inlined");
                }
                inlined = true;
                break;
            }
        }

        if (inlined) {
            for (InlinableCallSiteInfo callSite : inlinableCallSites) {
                callSite.getCallSite().resetCallCount();
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
        OUT.printf("[truffle] %-9s %-50s |Nodes %6s |Calls %6s %7.3f |%s\n", msg, callSite.getCallSite(), nodes, calls, policy.metric(callSite), callSite.getCallSite().getCallTarget());
    }

    private static final class InliningPolicy {

        private final int callerNodeCount;
        private final int callerInvocationCount;

        public InliningPolicy(OptimizedCallTarget caller) {
            this.callerNodeCount = NodeUtil.countNodes(caller.getRootNode());
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

        public void sortByRelevance(List<InlinableCallSiteInfo> inlinableCallSites) {
            Collections.sort(inlinableCallSites, new Comparator<InlinableCallSiteInfo>() {

                @Override
                public int compare(InlinableCallSiteInfo cs1, InlinableCallSiteInfo cs2) {
                    int result = (isWorthInlining(cs2) ? 1 : 0) - (isWorthInlining(cs1) ? 1 : 0);
                    if (result == 0) {
                        return Double.compare(metric(cs2), metric(cs1));
                    }
                    return result;
                }
            });
        }
    }

    private static final class InlinableCallSiteInfo {

        private final InlinableCallSite callSite;
        private final int callCount;
        private final int nodeCount;
        private final int recursiveDepth;

        public InlinableCallSiteInfo(InlinableCallSite callSite) {
            this.callSite = callSite;
            this.callCount = callSite.getCallCount();
            this.nodeCount = NodeUtil.countNodes(callSite.getInlineTree());
            this.recursiveDepth = calculateRecursiveDepth();
        }

        public int getRecursiveDepth() {
            return recursiveDepth;
        }

        private int calculateRecursiveDepth() {
            int depth = 0;
            Node parent = ((Node) callSite).getParent();
            while (!(parent instanceof RootNode)) {
                assert parent != null;
                if (parent instanceof InlinedCallSite && ((InlinedCallSite) parent).getCallTarget() == callSite.getCallTarget()) {
                    depth++;
                }
                parent = parent.getParent();
            }
            if (((RootNode) parent).getCallTarget() == callSite.getCallTarget()) {
                depth++;
            }
            return depth;
        }

        public InlinableCallSite getCallSite() {
            return callSite;
        }

        public int getCallCount() {
            return callCount;
        }

        public int getInlineNodeCount() {
            return nodeCount;
        }
    }

    static List<InlinableCallSiteInfo> getInlinableCallSites(final RootCallTarget target) {
        final ArrayList<InlinableCallSiteInfo> inlinableCallSites = new ArrayList<>();
        target.getRootNode().accept(new NodeVisitor() {

            @Override
            public boolean visit(Node node) {
                if (node instanceof InlinableCallSite) {
                    inlinableCallSites.add(new InlinableCallSiteInfo((InlinableCallSite) node));
                }
                return true;
            }
        });
        return inlinableCallSites;
    }
}
