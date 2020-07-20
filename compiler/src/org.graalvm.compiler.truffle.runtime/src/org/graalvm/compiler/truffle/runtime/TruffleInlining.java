/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import static org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.runtime;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.graalvm.compiler.truffle.common.TruffleInliningPlan;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.SourceSection;

import jdk.vm.ci.meta.JavaConstant;

public class TruffleInlining implements Iterable<TruffleInliningDecision>, TruffleInliningPlan {

    private final List<TruffleInliningDecision> callSites;

    private final List<CompilableTruffleAST> targets = new ArrayList<>();

    protected TruffleInlining(List<TruffleInliningDecision> callSites) {
        this.callSites = callSites;
    }

    public TruffleInlining(OptimizedCallTarget sourceTarget, TruffleInliningPolicy policy) {
        this(createDecisions(sourceTarget, policy, sourceTarget.getCompilerOptions()));

    }

    private static List<TruffleInliningDecision> createDecisions(OptimizedCallTarget sourceTarget, TruffleInliningPolicy policy, CompilerOptions options) {
        if (!sourceTarget.engine.inlining || sourceTarget.getOptionValue(PolyglotCompilerOptions.LanguageAgnosticInlining)) {
            return Collections.emptyList();
        }
        int[] visitedNodes = {0};
        int nodeCount = sourceTarget.getNonTrivialNodeCount();
        List<TruffleInliningDecision> exploredCallSites = exploreCallSites(new ArrayList<>(Arrays.asList(sourceTarget)), nodeCount, policy, visitedNodes, new HashMap<>());
        return decideInlining(exploredCallSites, policy, nodeCount, options);
    }

    private static List<TruffleInliningDecision> exploreCallSites(List<OptimizedCallTarget> stack, int callStackNodeCount, TruffleInliningPolicy policy, int[] visitedNodes,
                    Map<OptimizedCallTarget, TruffleInliningDecision> rejectedDecisionsCache) {
        List<TruffleInliningDecision> exploredCallSites = new ArrayList<>();
        List<OptimizedCallTarget> toRemoveFromCache = new LinkedList<>();
        OptimizedCallTarget parentTarget = stack.get(stack.size() - 1);
        for (OptimizedDirectCallNode callNode : getCallNodes(parentTarget)) {
            OptimizedCallTarget currentTarget = callNode.getCurrentCallTarget();
            stack.add(currentTarget); // push
            TruffleInliningDecision decision = rejectedDecisionsCache.get(currentTarget);
            if (decision == null) {
                // Cache miss
                decision = exploreCallSite(stack, callStackNodeCount, policy, callNode, visitedNodes, rejectedDecisionsCache);
                if (!policy.isAllowed(decision.getProfile(), callStackNodeCount, callNode.getCompilerOptions())) {
                    rejectedDecisionsCache.put(currentTarget, decision);
                    toRemoveFromCache.add(currentTarget);
                }
            } else {
                // Cache hit!
                TruffleInliningProfile cachedProfile = decision.getProfile();
                TruffleInliningProfile newProfile = new TruffleInliningProfile(callNode, cachedProfile.getNodeCount(), cachedProfile.getDeepNodeCount(), cachedProfile.getFrequency(),
                                cachedProfile.getRecursions());
                newProfile.setCached(cachedProfile);
                TruffleInliningDecision newDecision = new TruffleInliningDecision(decision.getTarget(), newProfile, decision.getCallSites());
                decision = newDecision;
            }
            exploredCallSites.add(decision);
            stack.remove(stack.size() - 1); // pop
        }
        for (OptimizedCallTarget target : toRemoveFromCache) {
            rejectedDecisionsCache.remove(target);
        }
        return exploredCallSites;
    }

    private static List<OptimizedDirectCallNode> getCallNodes(OptimizedCallTarget target) {
        final List<OptimizedDirectCallNode> callNodes = new ArrayList<>();
        target.getRootNode().accept(new NodeVisitor() {
            @Override
            public boolean visit(Node node) {
                if (node instanceof OptimizedDirectCallNode) {
                    callNodes.add((OptimizedDirectCallNode) node);
                }
                return true;
            }
        });
        return callNodes;
    }

    private static TruffleInliningDecision exploreCallSite(List<OptimizedCallTarget> callStack, int callStackNodeCount, TruffleInliningPolicy policy, OptimizedDirectCallNode callNode,
                    int[] visitedNodes, Map<OptimizedCallTarget, TruffleInliningDecision> rejectedDecisionsCache) {

        OptimizedCallTarget parentTarget = callStack.get(callStack.size() - 2);
        OptimizedCallTarget currentTarget = callStack.get(callStack.size() - 1);

        List<TruffleInliningDecision> childCallSites = Collections.emptyList();
        double frequency = calculateFrequency(parentTarget, callNode);
        int nodeCount = callNode.getCurrentCallTarget().getNonTrivialNodeCount();

        int recursions = countRecursions(callStack);
        int deepNodeCount = nodeCount;

        if (visitedNodes[0] < (100 * currentTarget.getOptionValue(PolyglotCompilerOptions.InliningNodeBudget)) &&
                        callStack.size() < 15 &&
                        recursions <= currentTarget.getOptionValue(PolyglotCompilerOptions.InliningRecursionDepth)) {
            /*
             * We make a preliminary optimistic inlining decision with best possible characteristics
             * to avoid the exploration of unnecessary paths in the inlining tree.
             */
            visitedNodes[0]++;
            final CompilerOptions options = callNode.getCompilerOptions();
            if (policy.isAllowed(new TruffleInliningProfile(callNode, nodeCount, nodeCount, frequency, recursions), callStackNodeCount, options)) {
                List<TruffleInliningDecision> exploredCallSites = exploreCallSites(callStack, callStackNodeCount + nodeCount, policy, visitedNodes, rejectedDecisionsCache);
                childCallSites = decideInlining(exploredCallSites, policy, nodeCount, options);
                for (TruffleInliningDecision childCallSite : childCallSites) {
                    if (childCallSite.shouldInline()) {
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
        return (double) Math.max(1, ocn.getCallCount()) / (double) Math.max(1, target.getCallCount());
    }

    private static int countRecursions(List<OptimizedCallTarget> stack) {
        int count = 0;
        OptimizedCallTarget top = stack.get(stack.size() - 1);
        for (int i = 0; i < stack.size() - 1; i++) {
            final OptimizedCallTarget frameTarget = stack.get(i);
            if (frameTarget == top || frameTarget == top.getSourceCallTarget() || top == frameTarget.getSourceCallTarget() ||
                            (frameTarget.getSourceCallTarget() != null && top.getSourceCallTarget() != null && frameTarget.getSourceCallTarget() == top.getSourceCallTarget())) {
                count++;
            }
        }

        return count;
    }

    private static List<TruffleInliningDecision> decideInlining(List<TruffleInliningDecision> callSites, TruffleInliningPolicy policy, int nodeCount, CompilerOptions options) {
        int deepNodeCount = nodeCount;
        int index = 0;

        /* First sort the call sites. */
        Collections.sort(callSites);

        for (TruffleInliningDecision callSite : callSites) {
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
        int sum = 0;
        for (TruffleInliningDecision callSite : getCallSites()) {
            if (callSite.shouldInline()) {
                sum += callSite.getProfile().getDeepNodeCount();
            }
        }
        return sum;
    }

    public int countCalls() {
        int sum = 0;
        for (TruffleInliningDecision callSite : getCallSites()) {
            sum += callSite.shouldInline() ? callSite.countCalls() + 1 : 1;
        }
        return sum;
    }

    public int countInlinedCalls() {
        int sum = 0;
        for (TruffleInliningDecision callSite : getCallSites()) {
            if (callSite.shouldInline()) {
                sum += callSite.countInlinedCalls() + 1;
            }
        }
        return sum;
    }

    public final List<TruffleInliningDecision> getCallSites() {
        return callSites;
    }

    @Override
    public Iterator<TruffleInliningDecision> iterator() {
        return callSites.iterator();
    }

    @Override
    public Decision findDecision(JavaConstant callNodeConstant) {
        OptimizedDirectCallNode callNode = findCallNode(callNodeConstant);
        return findByCall(callNode);
    }

    @Override
    public OptimizedDirectCallNode findCallNode(JavaConstant callNodeConstant) {
        return runtime().asObject(OptimizedDirectCallNode.class, callNodeConstant);
    }

    static class TruffleSourceLanguagePosition implements org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition {

        private final SourceSection sourceSection;

        TruffleSourceLanguagePosition(SourceSection section) {
            this.sourceSection = section;
        }

        @Override
        public String getDescription() {
            return sourceSection.getSource().getURI() + " " + sourceSection.getStartLine() + ":" + sourceSection.getStartColumn();
        }

        @Override
        public int getOffsetEnd() {
            return sourceSection.getCharEndIndex();
        }

        @Override
        public int getOffsetStart() {
            return sourceSection.getCharIndex();
        }

        @Override
        public int getLineNumber() {
            return sourceSection.getStartLine();
        }

        @Override
        public URI getURI() {
            return sourceSection.getSource().getURI();
        }

        @Override
        public String getLanguage() {
            return sourceSection.getSource().getLanguage();
        }
    }

    @Override
    public void addTargetToDequeue(CompilableTruffleAST target) {
        targets.add(target);
    }

    @Override
    public void dequeueTargets() {
        for (CompilableTruffleAST target : targets) {
            target.cancelCompilation("Target inlined into only caller");
        }
    }

    @Override
    public org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition getPosition(JavaConstant node) {
        Node truffleNode = runtime().asObject(Node.class, node);
        if (truffleNode == null) {
            return null;
        }
        SourceSection section = null;
        if (truffleNode instanceof DirectCallNode) {
            section = ((DirectCallNode) truffleNode).getCurrentRootNode().getSourceSection();
        }
        if (section == null) {
            section = truffleNode.getSourceSection();
        }
        if (section == null) {
            Node cur = truffleNode.getParent();
            while (cur != null) {
                section = cur.getSourceSection();
                if (section != null) {
                    break;
                }
                cur = cur.getParent();
            }
        }
        if (section != null) {
            return new TruffleSourceLanguagePosition(section);
        }
        return null;
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
        target.getRootNode().accept(new CallTreeNodeVisitorImpl(visitor));
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

        @Override
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
    private final class CallTreeNodeVisitorImpl implements NodeVisitor {

        protected final List<TruffleInlining> stack = new ArrayList<>();
        private final NodeVisitor visitor;
        private boolean continueTraverse = true;

        CallTreeNodeVisitorImpl(NodeVisitor visitor) {
            stack.add(TruffleInlining.this);
            this.visitor = visitor;
        }

        @Override
        public boolean visit(Node node) {
            if (node instanceof OptimizedDirectCallNode) {
                OptimizedDirectCallNode callNode = (OptimizedDirectCallNode) node;
                TruffleInlining inlining = stack.get(stack.size() - 1);
                if (inlining != null) {
                    TruffleInliningDecision childInlining = inlining.findByCall(callNode);
                    if (childInlining != null) {
                        stack.add(childInlining);
                        continueTraverse = visitNode(node);
                        if (continueTraverse && childInlining.shouldInline()) {
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

    private final class CallTreeNodeIterator implements Iterator<Node> {

        private List<TruffleInlining> inliningDecisionStack = new ArrayList<>();
        private List<Iterator<Node>> iteratorStack = new ArrayList<>();

        CallTreeNodeIterator(OptimizedCallTarget target) {
            inliningDecisionStack.add(TruffleInlining.this);
            iteratorStack.add(NodeUtil.makeRecursiveIterator(target.getRootNode()));
        }

        @Override
        public boolean hasNext() {
            return peekIterator() != null;
        }

        @Override
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
            if (decision != null && decision.shouldInline()) {
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

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
