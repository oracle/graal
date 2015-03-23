/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.Node;

public class TruffleInlining implements Iterable<TruffleInliningDecision> {

    private final List<TruffleInliningDecision> callSites;

    protected TruffleInlining(List<TruffleInliningDecision> callSites) {
        this.callSites = callSites;
    }

    public TruffleInlining(OptimizedCallTarget sourceTarget, TruffleInliningPolicy policy) {
        this(createDecisions(sourceTarget, policy, sourceTarget.getRootNode().getCompilerOptions()));

    }

    private static List<TruffleInliningDecision> createDecisions(OptimizedCallTarget sourceTarget, TruffleInliningPolicy policy, CompilerOptions options) {
        int nodeCount = sourceTarget.getNonTrivialNodeCount();
        List<TruffleInliningDecision> exploredCallSites = exploreCallSites(new ArrayList<>(Arrays.asList(sourceTarget)), nodeCount, policy);
        return decideInlining(exploredCallSites, policy, nodeCount, options);
    }

    private static List<TruffleInliningDecision> exploreCallSites(List<OptimizedCallTarget> stack, int callStackNodeCount, TruffleInliningPolicy policy) {
        List<TruffleInliningDecision> exploredCallSites = new ArrayList<>();
        OptimizedCallTarget parentTarget = stack.get(stack.size() - 1);
        for (OptimizedDirectCallNode callNode : parentTarget.getCallNodes()) {
            OptimizedCallTarget currentTarget = callNode.getCurrentCallTarget();
            stack.add(currentTarget); // push
            exploredCallSites.add(exploreCallSite(stack, callStackNodeCount, policy, callNode));
            stack.remove(stack.size() - 1); // pop
        }
        return exploredCallSites;
    }

    private static TruffleInliningDecision exploreCallSite(List<OptimizedCallTarget> callStack, int callStackNodeCount, TruffleInliningPolicy policy, OptimizedDirectCallNode callNode) {
        OptimizedCallTarget parentTarget = callStack.get(callStack.size() - 2);
        OptimizedCallTarget currentTarget = callStack.get(callStack.size() - 1);

        List<TruffleInliningDecision> childCallSites = Collections.emptyList();
        double frequency = calculateFrequency(parentTarget, callNode);
        int nodeCount = callNode.getCurrentCallTarget().getNonTrivialNodeCount();

        int recursions = countRecursions(callStack);
        int deepNodeCount = nodeCount;
        if (callStack.size() < 15 && recursions <= TruffleCompilerOptions.TruffleMaximumRecursiveInlining.getValue()) {
            /*
             * We make a preliminary optimistic inlining decision with best possible characteristics
             * to avoid the exploration of unnecessary paths in the inlining tree.
             */
            final CompilerOptions options = callNode.getRootNode().getCompilerOptions();
            if (policy.isAllowed(new TruffleInliningProfile(callNode, nodeCount, nodeCount, frequency, recursions), callStackNodeCount, options)) {
                List<TruffleInliningDecision> exploredCallSites = exploreCallSites(callStack, callStackNodeCount + nodeCount, policy);
                childCallSites = decideInlining(exploredCallSites, policy, nodeCount, options);
                for (TruffleInliningDecision childCallSite : childCallSites) {
                    if (childCallSite.isInline()) {
                        deepNodeCount += childCallSite.getProfile().getDeepNodeCount();
                    } else {
                        /* we don't need those anymore. */
                        childCallSite.getCallSites().clear();
                    }
                }
            }
        }

        TruffleInliningProfile profile = new TruffleInliningProfile(callNode, nodeCount, deepNodeCount, frequency, recursions);
        profile.setScore(policy.calculateScore(profile));
        return new TruffleInliningDecision(currentTarget, profile, childCallSites);
    }

    private static double calculateFrequency(OptimizedCallTarget target, OptimizedDirectCallNode ocn) {
        return (double) Math.max(1, ocn.getCallCount()) / (double) Math.max(1, target.getCompilationProfile().getInterpreterCallCount());
    }

    private static int countRecursions(List<OptimizedCallTarget> stack) {
        int count = 0;
        OptimizedCallTarget top = stack.get(stack.size() - 1);
        for (int i = 0; i < stack.size() - 1; i++) {
            if (stack.get(i) == top) {
                count++;
            }
        }

        return count;
    }

    private static List<TruffleInliningDecision> decideInlining(List<TruffleInliningDecision> callSites, TruffleInliningPolicy policy, int nodeCount, CompilerOptions options) {
        int deepNodeCount = nodeCount;
        int index = 0;
        for (TruffleInliningDecision callSite : callSites.stream().sorted().collect(Collectors.toList())) {
            TruffleInliningProfile profile = callSite.getProfile();
            profile.setQueryIndex(index++);
            if (policy.isAllowed(profile, deepNodeCount, options)) {
                callSite.setInline(true);
                deepNodeCount += profile.getDeepNodeCount();
            }
        }
        return callSites;
    }

    public int getInlinedNodeCount() {
        return getCallSites().stream().filter(callSite -> callSite.isInline()).mapToInt(callSite -> callSite.getProfile().getDeepNodeCount()).sum();
    }

    public int countCalls() {
        return getCallSites().stream().mapToInt(callSite -> callSite.isInline() ? callSite.countCalls() + 1 : 1).sum();
    }

    public int countInlinedCalls() {
        return getCallSites().stream().filter(TruffleInliningDecision::isInline).mapToInt(callSite -> callSite.countInlinedCalls() + 1).sum();
    }

    public final List<TruffleInliningDecision> getCallSites() {
        return callSites;
    }

    public Iterator<TruffleInliningDecision> iterator() {
        return callSites.iterator();
    }

    public TruffleInliningDecision findByCall(OptimizedDirectCallNode callNode) {
        for (TruffleInliningDecision d : getCallSites()) {
            if (d.getProfile().getCallNode() == callNode) {
                return d;
            }
        }
        return null;
    }

    /**
     * Visits all nodes of the {@link CallTarget} and all of its inlined calls.
     */
    public void accept(OptimizedCallTarget target, NodeVisitor visitor) {
        target.getRootNode().accept(new CallTreeNodeVisitorImpl(target, visitor));
    }

    /**
     * Creates an iterator for all nodes of the {@link CallTarget} and all of its inlined calls.
     */
    public Iterator<Node> makeNodeIterator(OptimizedCallTarget target) {
        return new CallTreeNodeIterator(target);
    }

    /**
     * This visitor extends the {@link NodeVisitor} interface to be usable for traversing the full
     * call tree.
     */
    public interface CallTreeNodeVisitor extends NodeVisitor {

        boolean visit(List<TruffleInlining> decisionStack, Node node);

        default boolean visit(Node node) {
            return visit(null, node);
        }

        static int getNodeDepth(List<TruffleInlining> decisionStack, Node node) {
            int depth = calculateNodeDepth(node);
            if (decisionStack != null) {
                for (int i = decisionStack.size() - 1; i > 0; i--) {
                    TruffleInliningDecision decision = (TruffleInliningDecision) decisionStack.get(i);
                    depth += calculateNodeDepth(decision.getProfile().getCallNode());
                }
            }
            return depth;
        }

        static int calculateNodeDepth(Node node) {
            int depth = 0;
            Node traverseNode = node;
            while (traverseNode != null) {
                depth++;
                traverseNode = traverseNode.getParent();
            }
            return depth;
        }

        static TruffleInliningDecision getCurrentInliningDecision(List<TruffleInlining> decisionStack) {
            if (decisionStack == null || decisionStack.size() <= 1) {
                return null;
            }
            return (TruffleInliningDecision) decisionStack.get(decisionStack.size() - 1);
        }

    }

    /**
     * This visitor wraps an existing {@link NodeVisitor} or {@link CallTreeNodeVisitor} and
     * traverses the full Truffle tree including inlined call sites.
     */
    private static final class CallTreeNodeVisitorImpl implements NodeVisitor {

        protected final List<TruffleInlining> stack = new ArrayList<>();
        private final NodeVisitor visitor;
        private boolean continueTraverse = true;

        public CallTreeNodeVisitorImpl(OptimizedCallTarget target, NodeVisitor visitor) {
            stack.add(target.getInlining());
            this.visitor = visitor;
        }

        public boolean visit(Node node) {
            if (node instanceof OptimizedDirectCallNode) {
                OptimizedDirectCallNode callNode = (OptimizedDirectCallNode) node;
                TruffleInlining inlining = stack.get(stack.size() - 1);
                if (inlining != null) {
                    TruffleInliningDecision childInlining = inlining.findByCall(callNode);
                    if (childInlining != null) {
                        stack.add(childInlining);
                        continueTraverse = visitNode(node);
                        if (continueTraverse && childInlining.isInline()) {
                            childInlining.getTarget().getRootNode().accept(this);
                        }
                        stack.remove(stack.size() - 1);
                    }
                }
                return continueTraverse;
            } else {
                continueTraverse = visitNode(node);
                return continueTraverse;
            }
        }

        private boolean visitNode(Node node) {
            if (visitor instanceof CallTreeNodeVisitor) {
                return ((CallTreeNodeVisitor) visitor).visit(stack, node);
            } else {
                return visitor.visit(node);
            }
        }
    }

    private static final class CallTreeNodeIterator implements Iterator<Node> {

        private List<TruffleInlining> inliningDecisionStack = new ArrayList<>();
        private List<Iterator<Node>> iteratorStack = new ArrayList<>();

        public CallTreeNodeIterator(OptimizedCallTarget target) {
            inliningDecisionStack.add(target.getInlining());
            iteratorStack.add(NodeUtil.makeRecursiveIterator(target.getRootNode()));
        }

        public boolean hasNext() {
            return peekIterator() != null;
        }

        public Node next() {
            Iterator<Node> iterator = peekIterator();
            if (iterator == null) {
                throw new NoSuchElementException();
            }

            Node node = iterator.next();
            if (node instanceof OptimizedDirectCallNode) {
                visitInlinedCall(node);
            }
            return node;
        }

        private void visitInlinedCall(Node node) {
            TruffleInlining currentDecision = inliningDecisionStack.get(inliningDecisionStack.size() - 1);
            if (currentDecision == null) {
                return;
            }
            TruffleInliningDecision decision = currentDecision.findByCall((OptimizedDirectCallNode) node);
            if (decision.isInline()) {
                inliningDecisionStack.add(decision);
                iteratorStack.add(NodeUtil.makeRecursiveIterator(decision.getTarget().getRootNode()));
            }
        }

        private Iterator<Node> peekIterator() {
            int tos = iteratorStack.size() - 1;
            while (tos >= 0) {
                Iterator<Node> iterable = iteratorStack.get(tos);
                if (iterable.hasNext()) {
                    return iterable;
                } else {
                    iteratorStack.remove(tos);
                    inliningDecisionStack.remove(tos--);
                }
            }
            return null;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

}
