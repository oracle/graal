/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.codegen.phase;

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.graal.nodes.ThrowBytecodeExceptionNode;
import com.oracle.svm.webimage.functionintrinsics.ImplicitExceptions;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

/**
 * This phase identifies patterns of runtime checks (e.g., null checks) in method graphs and replace
 * them with method call nodes.
 */
public class OutlineRuntimeChecksPhase extends BasePhase<CoreProviders> {

    private abstract static class Pattern {
        protected final IfNode ifNode;

        /**
         * @param bytecodeExceptionNode Either a {@link BytecodeExceptionNode} or
         *            {@link ThrowBytecodeExceptionNode}.
         */
        Pattern(FixedNode bytecodeExceptionNode) {
            this.ifNode = (IfNode) bytecodeExceptionNode.predecessor().predecessor();
        }

        abstract void replace(CoreProviders providers);
    }

    private static class NullCheckPattern extends Pattern {
        final IsNullNode isNullNode;
        final AbstractBeginNode falseSuccessor;

        NullCheckPattern(FixedNode bytecodeExceptionNode) {
            super(bytecodeExceptionNode);
            this.falseSuccessor = ifNode.falseSuccessor();
            this.isNullNode = (IsNullNode) this.ifNode.condition();
        }

        /**
         * Replace the pattern with a method call.
         * <p>
         * From the following
         *
         * <pre>
         *                       If ---> IsNull  --> obj
         *                      /  \
         *                  Begin  Begin
         *                   /       \
         *                  /         \
         *       ByteCodeException   [...]
         *               /
         *            Unwind
         * </pre>
         *
         * to
         *
         * <pre>
         *        ImplicitExceptions.checkNullPointer(obj)
         *                       |
         *                       |
         *                     [...]
         * </pre>
         */
        @Override
        void replace(CoreProviders providers) {
            if (ifNode.isDeleted()) {
                /*
                 * This case can happen if both branches of an if-node are null-exception unwind.
                 *
                 * It happens for the method slowVerifyAccess in java.lang.reflect.AccessibleObject.
                 */
                return;
            }

            StructuredGraph graph = ifNode.graph();

            /*
             * replace with a foreign call
             */
            ValueNode[] args = new ValueNode[]{isNullNode.getValue()};
            FixedWithNextNode outliningCall = createOutlineCallNode(graph, ImplicitExceptions.CHECK_NULL_POINTER, args);

            replaceIfWith(ifNode, falseSuccessor, outliningCall);
        }

        /**
         * Find patterns of runtime null-checks.
         * <p>
         * The runtime null-checks have the follow pattern in the graph:
         *
         * <pre>
         *                        If ---> IsNull  --> obj
         *                       /  \
         *                   Begin  Begin
         *                    /       \
         *                   /         \
         *        ByteCodeException   [...]
         *                /
         *             Unwind
         *
         * </pre>
         *
         * Also detects the same pattern if the {@link BytecodeExceptionNode} has been replaced by a
         * {@link ThrowBytecodeExceptionNode}.
         *
         * @param node A {@link BytecodeExceptionNode} followed immediately by an {@link UnwindNode}
         *            or just a {@link ThrowBytecodeExceptionNode}
         */
        static void find(FixedNode node, List<Pattern> patterns) {
            Node predecessor = node.predecessor();
            boolean isNullCheck = predecessor instanceof BeginNode;
            Node secondPredecessor = predecessor.predecessor();
            // It has to be an if node where the NPE is thrown in the true-successor
            isNullCheck = isNullCheck && secondPredecessor instanceof IfNode ifNode && ifNode.trueSuccessor() == predecessor;
            isNullCheck = isNullCheck && singleInput(secondPredecessor) instanceof IsNullNode;

            if (isNullCheck) {
                NullCheckPattern pattern = new NullCheckPattern(node);
                patterns.add(pattern);
            }
        }
    }

    private static class ArrayBoundCheckPattern extends Pattern {
        final AbstractBeginNode survivingBranch;
        final LogicNode condition;
        final boolean isTrueBranchSurviving;

        ArrayBoundCheckPattern(FixedNode bytecodeExceptionNode) {
            super(bytecodeExceptionNode);
            this.condition = ifNode.condition();
            this.isTrueBranchSurviving = ifNode.falseSuccessor() == bytecodeExceptionNode.predecessor();
            this.survivingBranch = this.isTrueBranchSurviving ? ifNode.trueSuccessor() : ifNode.falseSuccessor();
        }

        /**
         * Replace the pattern with a method call.
         *
         * From the following
         *
         * <pre>
         *                           If   --->  cond { length == n or length < n }
         *                         /    \
         *                     Begin    Begin
         *                      /          \
         *                     /            \
         *        ByteCodeException#OOB   [...]
         *                  /
         *               Unwind
         *
         * </pre>
         *
         * to
         *
         * <pre>
         *           ImplicitExceptions.checkArrayBound(cond or !cond)
         *                           |
         *                           |
         *                         [...]
         * </pre>
         */
        @Override
        void replace(CoreProviders providers) {
            if (ifNode.isDeleted()) {
                /*
                 * This case can happen when both branches of an if node throw a bytecode exception;
                 * e.g. if (array == null) where the true successor throws a NullPointerException,
                 * and the other throws an ArrayIndexOutOfBoundsException, which is then replaced
                 * with checkNullPointer(array) followed by ThrowBytecodeException(OUT_OF_BOUNDS).
                 */
                return;
            }

            StructuredGraph graph = ifNode.graph();

            /*
             * replace with a foreign call
             */
            LogicNode invalidNode = isTrueBranchSurviving ? graph.unique(LogicNegationNode.create(condition)) : condition;
            ValueNode[] args = new ValueNode[]{graph.unique(new ConditionalNode(invalidNode))};
            FixedWithNextNode outliningCall = createOutlineCallNode(graph, ImplicitExceptions.CHECK_ARRAY_BOUND, args);

            // Prevent the condition node from being removed.
            condition.removeUsage(ifNode);
            replaceIfWith(ifNode, survivingBranch, outliningCall);
        }

        /**
         * Find patterns of runtime array-bound-checks.
         * <p>
         * The runtime array-bound-checks have the follow pattern in the graph:
         *
         * <pre>
         *                          If   --->  cond { length == n or length < n }
         *                        /    \
         *                    Begin    Begin
         *                     /          \
         *                    /            \
         *       ByteCodeException#OOB   [...]
         *                 /
         *              Unwind
         * </pre>
         *
         * Also detects the same pattern if the {@link BytecodeExceptionNode} has been replaced by a
         * {@link ThrowBytecodeExceptionNode}.
         *
         * @param node A {@link BytecodeExceptionNode} followed immediately by an {@link UnwindNode}
         *            or a {@link ThrowBytecodeExceptionNode}
         */
        static void find(FixedNode node, List<Pattern> patterns) {
            boolean isBoundCheck = node.predecessor() instanceof BeginNode;
            isBoundCheck = isBoundCheck && node.predecessor().predecessor() instanceof IfNode;

            if (isBoundCheck) {
                ArrayBoundCheckPattern pattern = new ArrayBoundCheckPattern(node);
                patterns.add(pattern);
            }
        }
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders providers) {
        List<Pattern> patterns = new ArrayList<>();

        for (BytecodeExceptionNode node : graph.getNodes().filter(BytecodeExceptionNode.class)) {
            if (singleSuccessor(node) instanceof UnwindNode) {
                switch (node.getExceptionKind()) {
                    case NULL_POINTER -> NullCheckPattern.find(node, patterns);
                    case OUT_OF_BOUNDS -> ArrayBoundCheckPattern.find(node, patterns);
                    default -> {
                        // Ignore other less common cases
                    }
                }
            }
        }

        for (ThrowBytecodeExceptionNode node : graph.getNodes().filter(ThrowBytecodeExceptionNode.class)) {
            switch (node.getExceptionKind()) {
                case NULL_POINTER -> NullCheckPattern.find(node, patterns);
                case OUT_OF_BOUNDS -> ArrayBoundCheckPattern.find(node, patterns);
                default -> {
                    // Ignore other less common cases
                }
            }
        }

        /*
         * Doing removal after finding the patterns to avoid mutating graphs during traversal.
         */
        for (Pattern pattern : patterns) {
            pattern.replace(providers);
        }

        /*
         * Remove unreachable nodes and Begin nodes.
         */
        if (!patterns.isEmpty()) {
            CanonicalizerPhase.create().apply(graph, providers);
        }
    }

    private static FixedWithNextNode createOutlineCallNode(StructuredGraph graph, ForeignCallDescriptor descriptor, ValueNode[] args) {
        return graph.add(new ForeignCallNode(descriptor, args));
    }

    private static void replaceIfWith(IfNode ifNode, AbstractBeginNode survivingBranch, FixedWithNextNode outliningCall) {
        StructuredGraph graph = ifNode.graph();
        graph.removeSplitPropagate(ifNode, survivingBranch);
        survivingBranch.replaceAtPredecessor(outliningCall);
        outliningCall.setNext(survivingBranch);
    }

    private static Node singleSuccessor(Node node) {
        if (node.successors().count() == 1) {
            return node.successors().first();
        } else {
            return null;
        }
    }

    private static Node singleInput(Node node) {
        if (node.inputs().count() == 1) {
            return node.inputs().first();
        } else {
            return null;
        }
    }
}
