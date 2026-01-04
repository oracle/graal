/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import java.util.function.Supplier;
import java.util.stream.IntStream;

import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.BeginNode;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class BeginNodeOptimizationTest extends PartialEvaluationTest {

    /**
     * This test will fail if we are adding too many (temporary) begin nodes during PE. The graph
     * decoder should not add 1 BeginNode per inlined method, exploded loop iteration, folded if.
     */
    @Test
    public void shouldNotAddRedundantBeginNodes() {
        // Note: These limits are dependent on implementation details and might need to be updated
        // in the future.
        final int allowedGraphSize = 500;
        final int allowedBeginCount = 10;
        compileAndCheck(() -> createTreeOfDepth(100), allowedGraphSize, allowedBeginCount);
        compileAndCheck(() -> createTreeWithBlockOfSize(100), allowedGraphSize, allowedBeginCount);
    }

    private void compileAndCheck(Supplier<RootNode> rootNodeFactory, int totalNodeLimit, int beginNodeLimit) {
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("compiler.MaximumGraalGraphSize", Integer.toString(totalNodeLimit)).build());
        RootNode rootNode = rootNodeFactory.get();
        RootCallTarget target = rootNode.getCallTarget();

        class Listener extends Graph.NodeEventListener {
            int beginCount;

            @Override
            public void nodeAdded(jdk.graal.compiler.graph.Node node) {
                if (node instanceof BeginNode) {
                    beginCount++;
                }
            }
        }
        Listener listener = new Listener();
        partialEvalWithNodeEventListener((OptimizedCallTarget) target, new Object[]{}, listener);

        if (listener.beginCount > beginNodeLimit) {
            Assert.fail("PE added more begin nodes to the graph than expected: limit=" + beginNodeLimit + " actual=" + listener.beginCount);
        }
    }

    static TestRootNode createTreeOfDepth(int depth) {
        TestNode body = new LeafNode();
        for (int i = 0; i < depth; i++) {
            body = new WithChildNode(body);
        }
        return new TestRootNode(body);
    }

    static TestRootNode createTreeWithBlockOfSize(int count) {
        return new TestRootNode(new WithChildrenNode(IntStream.range(0, count).mapToObj(_ -> new LeafNode()).toArray(TestNode[]::new)));
    }

    static class TestRootNode extends RootNode {
        @Child TestNode body;

        TestRootNode(TestNode body) {
            super(null);
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return body.execute(frame);
        }
    }

    abstract static class TestNode extends Node {
        public abstract Object execute(VirtualFrame frame);
    }

    static class LeafNode extends TestNode {
        @Override
        public Object execute(VirtualFrame frame) {
            return "leaf";
        }
    }

    static class WithChildNode extends TestNode {
        @Child TestNode child;
        final boolean thisCannotContinue;

        WithChildNode(TestNode child) {
            this.child = child;
            this.thisCannotContinue = child == null;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (thisCannotContinue) {
                return child.execute(frame);
            } else {
                return child.execute(frame);
            }
        }
    }

    static class WithChildrenNode extends TestNode {
        @Children TestNode[] children;

        WithChildrenNode(TestNode[] children) {
            this.children = children;
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {
            Object result = null;
            for (TestNode child : children) {
                result = child.execute(frame);
            }
            return result;
        }
    }
}
