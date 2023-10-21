/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.replacements.PEGraphDecoder.Options.MaximumLoopExplosionCount;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

/**
 * Tests compilation of {@link Node#getRootNode()} and the getRootNodeImpl() intrinsic.
 */
public class GetRootNodeCompilationTest extends PartialEvaluationTest {

    @Before
    public void before() {
        setupContext();
    }

    @Test
    public void fromRootNode() {
        compileRootNode(() -> new GetFromRootNode(), 100);
    }

    @Test
    public void stressGetRootNode() {
        compileRootNode(() -> createTreeOfDepth(100), 1000);
    }

    @Test
    public void soManyParents() {
        compileRootNode(() -> createTreeOfDepth(900), 10000);
    }

    @Test
    public void unadoptedNode() {
        compileRootNode(() -> new TestRootNode(new UnadoptedNode()), 10000);
    }

    @Test
    public void receiverNotConstant() {
        // intrinsic is not used if receiver is not constant
        compileRootNode(() -> new TestRootNode(new ReceiverIsNotConstantNode(false)), 10000);
    }

    @Test
    public void receiverNull() {
        // intrinsic is not used if receiver is null
        compileRootNode(() -> new TestRootNode(new ReceiverIsNullNode(false)), 10000);
    }

    @Test
    public void bailoutTooManyParents() {
        int parentLimit = MaximumLoopExplosionCount.getValue(getGraalOptions());
        compileRootNode(() -> new TestRootNode(new TooManyParentsNode(parentLimit - 1)), 10000);
        expectBailout(() -> compileRootNode(() -> new TestRootNode(new TooManyParentsNode(parentLimit)), 10000));
    }

    @Test
    public void bailoutReceiverNotConstant() {
        expectBailout(() -> compileRootNode(() -> new TestRootNode(new ReceiverIsNotConstantNode(true)), 10000));
    }

    @Test
    public void bailoutReceiverNull() {
        expectBailout(() -> compileRootNode(() -> new TestRootNode(new ReceiverIsNullNode(true)), 10000));
    }

    @Test
    public void bailoutGraphTooLarge() {
        // sanity check: make sure we actually bail out if we exceed the maximum node count
        expectBailout(() -> compileRootNode(() -> createTreeOfDepth(100), 120));
    }

    @Override
    protected OptionValues getGraalOptions() {
        // Lower MaximumLoopExplosionCount to prevent stack overflow during adoption.
        return new OptionValues(super.getGraalOptions(), MaximumLoopExplosionCount, 1000);
    }

    private static void expectBailout(Runnable test) {
        try {
            test.run();
        } catch (PermanentBailoutException ignored) {
            // Expected, intentionally ignored
            return;
        } catch (Throwable e) {
            Assert.fail("Unexpected exception caught: " + e);
        }
        Assert.fail("Expected permanent bailout that never happened.");
    }

    private void compileRootNode(Supplier<RootNode> rootNodeFactory, int nodeLimit) {
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").option("compiler.MaximumGraalGraphSize", Integer.toString(nodeLimit)).build());
        RootNode rootNode = rootNodeFactory.get();
        RootCallTarget target = rootNode.getCallTarget();
        Object[] arguments = {};
        StructuredGraph graph = partialEval((OptimizedCallTarget) target, arguments);

        int liveLimit = 50;
        int deletedLimit = 100 + nodeLimit;
        int liveNodes = graph.getNodeCount();
        int totalDeletedNodes = graph.getTotalNodesDeleted();
        assertTrue("Node count higher than expected (<" + liveLimit + "): " + liveNodes, liveNodes < liveLimit);
        assertTrue("Total nodes deleted higher than expected (<" + deletedLimit + "): " + totalDeletedNodes, totalDeletedNodes < deletedLimit);
    }

    static TestRootNode createTreeOfDepth(int depth) {
        TestNode body = new LeafNode();
        for (int i = 0; i < depth; i++) {
            body = new WithChildNode(body);
        }
        return new TestRootNode(body);
    }

    public static class TestRootNode extends RootNode {
        @Child TestNode body;

        protected TestRootNode(TestNode body) {
            super(null);
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return body.execute(frame);
        }

        public int getResult() {
            return 4;
        }
    }

    public abstract static class TestNode extends Node {

        protected TestNode() {
        }

        public abstract Object execute(VirtualFrame frame);
    }

    public static class WithChildNode extends TestNode {
        @Child TestNode child;

        protected WithChildNode(TestNode child) {
            this.child = child;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return child.execute(frame);
        }
    }

    public static class LeafNode extends TestNode {

        protected LeafNode() {
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int result = 2;
            result += ((TestRootNode) getRootNode()).getResult();
            result += ((TestRootNode) getRootNode()).getResult();
            result += ((TestRootNode) getRootNode()).getResult();
            result += ((TestRootNode) getRootNode()).getResult();
            result += ((TestRootNode) getRootNode()).getResult();
            result += ((TestRootNode) getRootNode()).getResult();
            result += ((TestRootNode) getRootNode()).getResult();
            result += ((TestRootNode) getRootNode()).getResult();
            result += ((TestRootNode) getRootNode()).getResult();
            result += ((TestRootNode) getRootNode()).getResult();
            return result;
        }
    }

    public static class UnadoptedNode extends TestNode {
        @CompilationFinal Node unadopted = new LeafNode();

        protected UnadoptedNode() {
        }

        @Override
        public Object execute(VirtualFrame frame) {
            RootNode rootNode = unadopted.getRootNode();
            if (rootNode != null) {
                CompilerDirectives.shouldNotReachHere("wat");
            }
            return rootNode;
        }
    }

    public static class ReceiverIsNotConstantNode extends TestNode {
        final boolean intrinsic;
        Node notConst = createTreeOfDepth(1);

        protected ReceiverIsNotConstantNode(boolean intrinsic) {
            this.intrinsic = intrinsic;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            RootNode rootNode;
            if (intrinsic) {
                rootNode = getRootNodeImpl(notConst);
            } else {
                rootNode = notConst.getRootNode();
            }
            return ((TestRootNode) rootNode).getResult();
        }
    }

    public static class ReceiverIsNullNode extends TestNode {
        final boolean intrinsic;
        @Child Node child;

        protected ReceiverIsNullNode(boolean intrinsic) {
            this.intrinsic = intrinsic;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                if (intrinsic) {
                    getRootNodeImpl(child);
                } else {
                    child.getRootNode();
                }
                return 0;
            } catch (NullPointerException e) {
                // ignore
                return -1;
            }
        }
    }

    public static class GetFromRootNode extends RootNode {

        protected GetFromRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (getRootNode() != this) {
                throw CompilerDirectives.shouldNotReachHere("wat");
            }
            return true;
        }
    }

    public static class TooManyParentsNode extends TestNode {
        final Node tooDeep;

        protected TooManyParentsNode(int depth) {
            TestRootNode root = createTreeOfDepth(depth);
            root.getCallTarget();
            this.tooDeep = NodeUtil.findFirstNodeInstance(root, LeafNode.class);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return tooDeep.getRootNode();
        }
    }

    static RootNode getRootNodeImpl(Node receiver) {
        try {
            return (RootNode) getRootNodeImpl.invokeExact(receiver);
        } catch (NullPointerException e) {
            throw e;
        } catch (Throwable e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    static final MethodHandle getRootNodeImpl;
    static {
        Method getRootNodeImplMethod;
        try {
            getRootNodeImplMethod = Node.class.getDeclaredMethod("getRootNodeImpl");
            getRootNodeImplMethod.setAccessible(true);
            getRootNodeImpl = MethodHandles.lookup().unreflect(getRootNodeImplMethod);
        } catch (NoSuchMethodException | IllegalAccessException | SecurityException e) {
            throw new AssertionError(e);
        }
    }
}
