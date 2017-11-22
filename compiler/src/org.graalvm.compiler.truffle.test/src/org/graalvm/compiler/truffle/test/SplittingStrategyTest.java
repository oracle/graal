/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.GraalTruffleCompilationListener;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.TruffleInlining;
import org.junit.Assert;
import org.junit.Test;

public class SplittingStrategyTest extends TestWithSynchronousCompiling {

    private static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();

    @Test
    public void testDefaultStrategyStabilises() {
        class InnerRootNode extends RootNode {
            OptimizedCallTarget target;
            @Child private DirectCallNode callNode1;

            @Child private Node polymorphic = new Node() {
                @Override
                public NodeCost getCost() {
                    return NodeCost.POLYMORPHIC;
                }
            };

            @Override
            public boolean isCloningAllowed() {
                return true;
            }

            protected InnerRootNode() {
                super(null);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                if (callNode1 == null) {
                    callNode1 = runtime.createDirectCallNode(target);
                    adoptChildren();
                }
                if (frame.getArguments().length > 0) {
                    if ((Integer) frame.getArguments()[0] < 100) {
                        callNode1.call(frame.getArguments());
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "INNER";
            }
        }
        final InnerRootNode innerRootNode = new InnerRootNode();
        final OptimizedCallTarget inner = (OptimizedCallTarget) runtime.createCallTarget(innerRootNode);

        final OptimizedCallTarget mid = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {

            @Child private DirectCallNode callNode = null;

            @Child private Node polymorphic = new Node() {
                @Override
                public NodeCost getCost() {
                    return NodeCost.POLYMORPHIC;
                }
            };

            @Override
            public boolean isCloningAllowed() {
                return true;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                if (callNode == null) {
                    callNode = runtime.createDirectCallNode(inner);
                    adoptChildren();
                }
                Object[] arguments = frame.getArguments();
                if ((Integer) arguments[0] < 100) {
                    callNode.call(new Object[]{((Integer) arguments[0]) + 1});
                }
                return null;
            }

            @Override
            public String toString() {
                return "MID";
            }
        });

        OptimizedCallTarget outside = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {

            @Child private DirectCallNode outsideCallNode = null; // runtime.createDirectCallNode(mid);

            @Override
            public Object execute(VirtualFrame frame) {
                // Emulates builtin i.e. Split immediately
                if (outsideCallNode == null) {
                    outsideCallNode = runtime.createDirectCallNode(mid);
                    adoptChildren();
                    outsideCallNode.cloneCallTarget();
                }
                return outsideCallNode.call(frame.getArguments());
            }

            @Override
            public boolean isCloningAllowed() {
                return true;
            }

            @Override
            public String toString() {
                return "OUTSIDE";
            }
        });
        innerRootNode.target = outside;

        final int[] splitCounter = {0};
        GraalTruffleCompilationListener listener = new GraalTruffleCompilationListener() {
            @Override
            public void notifyCompilationSplit(OptimizedDirectCallNode callNode) {
                splitCounter[0]++;
            }

            @Override
            public void notifyCompilationQueued(OptimizedCallTarget target) {

            }

            @Override
            public void notifyCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {

            }

            @Override
            public void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t) {

            }

            @Override
            public void notifyCompilationStarted(OptimizedCallTarget target) {

            }

            @Override
            public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph) {

            }

            @Override
            public void notifyCompilationGraalTierFinished(OptimizedCallTarget target, StructuredGraph graph) {

            }

            @Override
            public void notifyCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph, CompilationResult result) {

            }

            @Override
            public void notifyCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {

            }

            @Override
            public void notifyCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {

            }

            @Override
            public void notifyShutdown(GraalTruffleRuntime r) {

            }

            @Override
            public void notifyStartup(GraalTruffleRuntime r) {

            }
        };
        runtime.addCompilationListener(listener);
        outside.call(1);

        // Expected 13
        // OUTSIDE MID
        // MID <split> INNER
        // INNER <split> OUTSIDE
        // OUTSIDE <split> MID
        // INNER OUTSIDE
        // OUTSIDE <split> MID
        // MID <split> INNER
        // MID <split> INNER
        // INNER <split> OUTSIDE
        // OUTSIDE <split> MID
        // INNER <split> OUTSIDE
        // OUTSIDE <split> MID
        // MID <split> INNER
        Assert.assertEquals("Not the right number of splits.", 13, splitCounter[0]);
        runtime.removeCompilationListener(listener);
    }
}
