/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.regex.tregex.parser.flavors;

import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.SubexpressionCall;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.CopyVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.DepthFirstTraversalRegexASTVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.MarkAsAliveVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RubySubexpressionCalls {

    public static void expandNonRecursiveSubexpressionCalls(RegexAST ast) {
        BuildCallGraphVisitor buildCallGraphVisitor = new BuildCallGraphVisitor(ast);
        buildCallGraphVisitor.run();
        Map<CallGraphNode, List<CallGraphNode>> callGraph = buildCallGraphVisitor.callGraph;
        Map<CallGraphNode, Integer> inDegree = buildCallGraphVisitor.inDegree;

        CopyVisitor copyVisitor = new CopyVisitor(ast);

        ArrayList<CallGraphNode> expansionStack = new ArrayList<>(callGraph.size());
        for (CallGraphNode node : callGraph.keySet()) {
            if (inDegree.getOrDefault(node, 0) == 0) {
                expansionStack.add(node);
            }
        }

        while (!expansionStack.isEmpty()) {
            CallGraphNode node = expansionStack.remove(expansionStack.size() - 1);
            if (node instanceof SubexpressionCallNode) {
                SubexpressionCall subexpressionCall = ((SubexpressionCallNode) node).subexpressionCall;
                replace(subexpressionCall, ast.getGroup(subexpressionCall.getGroupNr()).get(0), copyVisitor);
            }
            if (callGraph.containsKey(node)) {
                for (CallGraphNode dependent : callGraph.get(node)) {
                    int dependentInDegree = inDegree.getOrDefault(dependent, 0);
                    if (dependentInDegree == 1) {
                        expansionStack.add(dependent);
                        inDegree.remove(dependent);
                    } else {
                        inDegree.put(dependent, dependentInDegree - 1);
                    }
                }
                callGraph.remove(node);
            }
        }

        assert callGraph.isEmpty() == inDegree.isEmpty();
        if (!callGraph.isEmpty()) {
            throw new UnsupportedRegexException("recursive subexpression calls are not supported");
        }
    }

    private static void replace(SubexpressionCall caller, Group callee, CopyVisitor copyVisitor) {
        Group copy = (Group) copyVisitor.copy(callee);
        MarkAsAliveVisitor.markAsAlive(copy);
        copy.setQuantifier(caller.getQuantifier());
        Sequence callerSeq = caller.getParent();
        int callerSeqIndex = caller.getSeqIndex();
        callerSeq.replace(callerSeqIndex, copy);
    }

    private abstract static class CallGraphNode {
    }

    private static final class SubexpressionCallNode extends CallGraphNode {

        private final SubexpressionCall subexpressionCall;

        SubexpressionCallNode(SubexpressionCall subexpressionCall) {
            this.subexpressionCall = subexpressionCall;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SubexpressionCallNode && this.subexpressionCall == ((SubexpressionCallNode) obj).subexpressionCall;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(subexpressionCall);
        }
    }

    private static final class CaptureGroupNode extends CallGraphNode {

        private int groupNumber;

        CaptureGroupNode(int groupNumber) {
            this.groupNumber = groupNumber;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CaptureGroupNode && this.groupNumber == ((CaptureGroupNode) obj).groupNumber;
        }

        @Override
        public int hashCode() {
            return groupNumber;
        }
    }

    private static final class BuildCallGraphVisitor extends DepthFirstTraversalRegexASTVisitor {

        public final Map<CallGraphNode, List<CallGraphNode>> callGraph = new HashMap<>();
        public final Map<CallGraphNode, Integer> inDegree = new HashMap<>();

        private final RegexAST ast;
        private final List<Group> enclosingCaptureGroups = new ArrayList<>();

        BuildCallGraphVisitor(RegexAST ast) {
            this.ast = ast;
        }

        public void run() {
            run(ast.getRoot());
        }

        @Override
        protected void visit(Group group) {
            if (group.isCapturing()) {
                enclosingCaptureGroups.add(group);
            }
        }

        @Override
        protected void leave(Group group) {
            if (group.isCapturing()) {
                assert enclosingCaptureGroups.get(enclosingCaptureGroups.size() - 1) == group;
                enclosingCaptureGroups.remove(enclosingCaptureGroups.size() - 1);
            }
        }

        @Override
        protected void visit(SubexpressionCall subexpressionCall) {
            CallGraphNode callNode = new SubexpressionCallNode(subexpressionCall);
            for (Group captureGroup : enclosingCaptureGroups) {
                // Add the subexpression call as a dependency of each enclosing capture group.
                addEdge(callNode, new CaptureGroupNode(captureGroup.getGroupNumber()));
            }
            // Add the called capture group as a dependency of the subexpression call.
            addEdge(new CaptureGroupNode(subexpressionCall.getGroupNr()), callNode);
        }

        private void addEdge(CallGraphNode from, CallGraphNode to) {
            callGraph.computeIfAbsent(from, (key) -> new ArrayList<>());
            callGraph.get(from).add(to);
            inDegree.putIfAbsent(to, 0);
            inDegree.computeIfPresent(to, (key, value) -> value + 1);
        }
    }
}
