/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import com.oracle.truffle.sl.runtime.SLStrings;
import org.graalvm.compiler.truffle.runtime.OptimizedBlockNode;
import org.graalvm.compiler.truffle.runtime.OptimizedBlockNode.PartialBlocks;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.BlockNode.ElementExecutor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.sl.runtime.SLContext;

public class OptimizedBlockNodeTest {

    @Test
    public void testExactlyBlockSize() {
        int blockSize = 1;
        for (int i = 0; i < 5; i++) {
            setup(blockSize);
            OptimizedBlockNode<?> block = createBlock(blockSize, 1);
            OptimizedCallTarget target = createTest(block);
            target.computeBlockCompilations();
            target.call();
            target.compile(true);
            // should not trigger and block compilation
            assertNull(block.getPartialBlocks());
            blockSize = blockSize * 2;
        }
    }

    @Test
    public void testFirstBlockElementExceedsLimit() {
        setup(1);
        OptimizedBlockNode<TestElement> block = createBlock(2, 1, null, new TestElementExecutor(), 5);
        OptimizedCallTarget target = createTest(block);
        target.computeBlockCompilations();
        target.call();
        target.compile(true);

        // should not trigger and block compilation
        PartialBlocks<TestElement> partialBlocks = block.getPartialBlocks();
        assertNotNull(partialBlocks);
        assertNotNull(partialBlocks.getBlockRanges());
        assertEquals(1, partialBlocks.getBlockRanges().length);
        assertEquals(1, partialBlocks.getBlockRanges()[0]);
        assertNotNull(partialBlocks.getBlockTargets());
        assertEquals(2, partialBlocks.getBlockTargets().length);
        assertTrue(partialBlocks.getBlockTargets()[0].isValid());
        assertTrue(partialBlocks.getBlockTargets()[1].isValid());

        assertEquals(1, target.call());
    }

    @Test
    public void testBoundaryBlockSize() {
        /*
         * Internal blockRanges and blockSizes arrays grow during partial blocks computation as
         * needed. This test tests that the growth of the arrays works correctly.
         */
        for (int blockSize = 2; blockSize < 20; blockSize++) {
            setup(1);
            OptimizedBlockNode<TestElement> block = createBlock(blockSize, 1);
            OptimizedCallTarget target = createTest(block);
            target.computeBlockCompilations();
            target.call();
            target.compile(true);

            // should not trigger and block compilation
            PartialBlocks<TestElement> partialBlocks = block.getPartialBlocks();
            assertNotNull(partialBlocks);
            assertNotNull(partialBlocks.getBlockRanges());
            assertEquals(blockSize - 1, partialBlocks.getBlockRanges().length);
            for (int i = 0; i < (blockSize - 1); i++) {
                assertEquals(i + 1, partialBlocks.getBlockRanges()[i]);
            }
            assertNotNull(partialBlocks.getBlockTargets());
            assertEquals(blockSize, partialBlocks.getBlockTargets().length);
            for (int i = 0; i < blockSize; i++) {
                assertTrue(partialBlocks.getBlockTargets()[i].isValid());
            }

            assertEquals(blockSize - 1, target.call());
        }
    }

    @Test
    public void testBlockSizePlusOne() {
        int groupSize = 1;
        for (int i = 0; i < 5; i++) {
            setup(groupSize);
            OptimizedBlockNode<TestElement> block = createBlock(groupSize + 1, 1);
            OptimizedCallTarget target = createTest(block);
            assertNull(block.getPartialBlocks());
            target.computeBlockCompilations();
            target.call();
            target.compile(true);

            // should not trigger and block compilation
            PartialBlocks<TestElement> partialBlocks = block.getPartialBlocks();
            assertNotNull(partialBlocks);
            assertNotNull(partialBlocks.getBlockRanges());
            assertEquals(1, partialBlocks.getBlockRanges().length);
            assertEquals(groupSize, partialBlocks.getBlockRanges()[0]);
            assertNotNull(partialBlocks.getBlockTargets());
            assertEquals(2, partialBlocks.getBlockTargets().length);
            assertTrue(partialBlocks.getBlockTargets()[0].isValid());
            assertTrue(partialBlocks.getBlockTargets()[1].isValid());

            assertEquals(groupSize, target.call());

            // stays valid after call
            assertTrue(target.isValid());
            assertTrue(partialBlocks.getBlockTargets()[0].isValid());
            assertTrue(partialBlocks.getBlockTargets()[1].isValid());

            // test explicit invalidations
            partialBlocks.getBlockTargets()[0].invalidate("test invalidation");
            assertTrue(target.isValid());
            assertFalse(partialBlocks.getBlockTargets()[0].isValid());
            assertTrue(partialBlocks.getBlockTargets()[1].isValid());

            target.invalidate("test invalidation");
            assertFalse(target.isValid());
            assertFalse(partialBlocks.getBlockTargets()[0].isValid());
            assertTrue(partialBlocks.getBlockTargets()[1].isValid());
            assertEquals(groupSize, target.call());
            // 0 or 1 might be compiled or not
            assertTrue(partialBlocks.getBlockTargets()[1].isValid());

            // test partial recompilation
            OptimizedCallTarget oldCallTarget = partialBlocks.getBlockTargets()[1];
            long oldAdress = oldCallTarget.getCodeAddress();
            target.compile(true);
            assertSame(partialBlocks, block.getPartialBlocks());
            assertTrue(target.isValid());
            assertTrue(partialBlocks.getBlockTargets()[0].isValid());
            assertTrue(partialBlocks.getBlockTargets()[1].isValid());
            assertSame(oldCallTarget, partialBlocks.getBlockTargets()[1]);
            assertNotEquals(0, oldAdress);
            assertEquals(oldAdress, partialBlocks.getBlockTargets()[1].getCodeAddress());

            groupSize = groupSize * 2;
        }
    }

    @Test
    public void testSimulateReplace() {
        OptimizedBlockNode<TestElement> block;
        OptimizedCallTarget target;
        PartialBlocks<TestElement> partialBlocks;
        int groupSize;
        int expectedResult;

        groupSize = 2;
        setup(groupSize);
        block = createBlock(groupSize * 3, 1);
        target = createTest(block);
        expectedResult = groupSize * 3 - 1;
        assertEquals(expectedResult, target.call());
        target.computeBlockCompilations();
        target.compile(true);
        partialBlocks = block.getPartialBlocks();

        assertEquals(3, partialBlocks.getBlockTargets().length);
        assertValid(target, partialBlocks);
        assertEquals(expectedResult, target.call());

        block.getElements()[0].simulateReplace();
        assertFalse(target.isValid());
        assertFalse(partialBlocks.getBlockTargets()[0].isValid());
        assertTrue(partialBlocks.getBlockTargets()[1].isValid());
        assertTrue(partialBlocks.getBlockTargets()[2].isValid());
        assertEquals(expectedResult, target.call());

        target.compile(true);
        block.getElements()[1].simulateReplace();
        assertFalse(target.isValid());
        assertFalse(partialBlocks.getBlockTargets()[0].isValid());
        assertTrue(partialBlocks.getBlockTargets()[1].isValid());
        assertTrue(partialBlocks.getBlockTargets()[2].isValid());
        assertEquals(expectedResult, target.call());

        target.compile(true);
        block.getElements()[2].simulateReplace();
        assertFalse(target.isValid());
        assertTrue(partialBlocks.getBlockTargets()[0].isValid());
        assertFalse(partialBlocks.getBlockTargets()[1].isValid());
        assertTrue(partialBlocks.getBlockTargets()[2].isValid());
        assertEquals(expectedResult, target.call());

        target.compile(true);
        block.getElements()[3].simulateReplace();
        assertFalse(target.isValid());
        assertTrue(partialBlocks.getBlockTargets()[0].isValid());
        assertFalse(partialBlocks.getBlockTargets()[1].isValid());
        assertTrue(partialBlocks.getBlockTargets()[2].isValid());
        assertEquals(expectedResult, target.call());

        target.compile(true);
        block.getElements()[4].simulateReplace();
        assertFalse(target.isValid());
        assertTrue(partialBlocks.getBlockTargets()[0].isValid());
        assertTrue(partialBlocks.getBlockTargets()[1].isValid());
        assertFalse(partialBlocks.getBlockTargets()[2].isValid());
        assertEquals(expectedResult, target.call());

        target.compile(true);
        block.getElements()[5].simulateReplace();
        assertFalse(target.isValid());
        assertTrue(partialBlocks.getBlockTargets()[0].isValid());
        assertTrue(partialBlocks.getBlockTargets()[1].isValid());
        assertFalse(partialBlocks.getBlockTargets()[2].isValid());
        assertEquals(expectedResult, target.call());

        block.getElements()[1].simulateReplace();
        assertFalse(target.isValid());
        assertFalse(partialBlocks.getBlockTargets()[0].isValid());
        assertTrue(partialBlocks.getBlockTargets()[1].isValid());
        assertEquals(expectedResult, target.call());

        target.compile(true);
        assertValid(target, partialBlocks);
    }

    @Test
    public void testExecuteMethods() throws UnexpectedResultException {
        OptimizedBlockNode<TestElement> block;
        OptimizedCallTarget target;
        PartialBlocks<TestElement> partialBlocks;
        Object expectedResult;
        MaterializedFrame testFrame = Truffle.getRuntime().createMaterializedFrame(new Object[0]);

        setup(3);

        block = createBlock(9, 1, null);
        target = createTest(block);
        expectedResult = 8;
        assertEquals(expectedResult, target.call());
        target.computeBlockCompilations();
        target.compile(true);
        partialBlocks = block.getPartialBlocks();
        assertValid(target, partialBlocks);

        assertEquals(expectedResult, block.executeInt(testFrame, BlockNode.NO_ARGUMENT));
        block.executeVoid(testFrame, BlockNode.NO_ARGUMENT);
        OptimizedBlockNode<TestElement> block0 = block;
        assertUnexpected(() -> block0.executeLong(testFrame, BlockNode.NO_ARGUMENT), expectedResult);
        assertUnexpected(() -> block0.executeDouble(testFrame, BlockNode.NO_ARGUMENT), expectedResult);
        assertUnexpected(() -> block0.executeBoolean(testFrame, BlockNode.NO_ARGUMENT), expectedResult);
        assertValid(target, partialBlocks);

        expectedResult = 42.d;
        block = createBlock(13, 1, expectedResult);
        target = createTest(block);
        assertEquals(expectedResult, target.call());
        target.computeBlockCompilations();
        target.compile(true);
        partialBlocks = block.getPartialBlocks();
        assertValid(target, partialBlocks);

        assertEquals(expectedResult, block.executeDouble(testFrame, BlockNode.NO_ARGUMENT));
        block.executeVoid(testFrame, BlockNode.NO_ARGUMENT);
        OptimizedBlockNode<TestElement> block1 = block;
        assertUnexpected(() -> block1.executeLong(testFrame, BlockNode.NO_ARGUMENT), expectedResult);
        assertUnexpected(() -> block1.executeInt(testFrame, BlockNode.NO_ARGUMENT), expectedResult);
        assertUnexpected(() -> block1.executeBoolean(testFrame, BlockNode.NO_ARGUMENT), expectedResult);
        assertValid(target, partialBlocks);

        expectedResult = 42L;
        block = createBlock(12, 1, expectedResult);
        target = createTest(block);
        assertEquals(expectedResult, target.call());
        target.computeBlockCompilations();
        target.compile(true);
        partialBlocks = block.getPartialBlocks();
        assertValid(target, partialBlocks);

        assertEquals(expectedResult, block.executeLong(testFrame, BlockNode.NO_ARGUMENT));
        block.executeVoid(testFrame, BlockNode.NO_ARGUMENT);
        OptimizedBlockNode<TestElement> block2 = block;
        assertUnexpected(() -> block2.executeDouble(testFrame, BlockNode.NO_ARGUMENT), expectedResult);
        assertUnexpected(() -> block2.executeInt(testFrame, BlockNode.NO_ARGUMENT), expectedResult);
        assertUnexpected(() -> block2.executeBoolean(testFrame, BlockNode.NO_ARGUMENT), expectedResult);
        assertValid(target, partialBlocks);

        expectedResult = false;
        block = createBlock(7, 1, expectedResult);
        target = createTest(block);
        assertEquals(expectedResult, target.call());
        target.computeBlockCompilations();
        target.compile(true);
        partialBlocks = block.getPartialBlocks();
        assertValid(target, partialBlocks);

        assertEquals(expectedResult, block.executeBoolean(testFrame, BlockNode.NO_ARGUMENT));
        block.executeVoid(testFrame, BlockNode.NO_ARGUMENT);
        OptimizedBlockNode<TestElement> block3 = block;
        assertUnexpected(() -> block3.executeDouble(testFrame, BlockNode.NO_ARGUMENT), expectedResult);
        assertUnexpected(() -> block3.executeInt(testFrame, BlockNode.NO_ARGUMENT), expectedResult);
        assertUnexpected(() -> block3.executeLong(testFrame, BlockNode.NO_ARGUMENT), expectedResult);
        assertValid(target, partialBlocks);
    }

    @Test
    public void testStartsWithCompilation() {
        OptimizedBlockNode<TestElement> block;
        OptimizedCallTarget target;
        PartialBlocks<TestElement> partialBlocks;
        Object expectedResult;
        int[] elementExecuted;

        setup(2);

        block = createBlock(5, 1, null, new StartsWithExecutor(), 0);
        target = createTest(block);
        elementExecuted = ((TestRootNode) block.getRootNode()).elementExecuted;
        expectedResult = 4;
        assertEquals(expectedResult, target.call(0));
        target.computeBlockCompilations();
        target.compile(true);
        partialBlocks = block.getPartialBlocks();
        assertValid(target, partialBlocks);
        assertEquals(expectedResult, target.call(1));
        assertEquals(0, elementExecuted[0]);
        assertEquals(1, elementExecuted[1]);
        assertEquals(1, elementExecuted[2]);
        assertEquals(1, elementExecuted[3]);
        assertEquals(1, elementExecuted[4]);
        target.compile(true);
        partialBlocks = block.getPartialBlocks();
        assertValid(target, partialBlocks);
        assertEquals(expectedResult, target.call(3));
        assertEquals(0, elementExecuted[0]);
        assertEquals(0, elementExecuted[1]);
        assertEquals(0, elementExecuted[2]);
        assertEquals(1, elementExecuted[3]);
        assertEquals(1, elementExecuted[4]);
        assertValid(target, partialBlocks);
        target.compile(true);
        assertValid(target, partialBlocks);
        assertEquals(expectedResult, target.call(4));
        assertEquals(0, elementExecuted[0]);
        assertEquals(0, elementExecuted[1]);
        assertEquals(0, elementExecuted[2]);
        assertEquals(0, elementExecuted[3]);
        assertEquals(1, elementExecuted[4]);
        assertValid(target, partialBlocks);
        target.compile(true);
        try {
            target.call(5);
            fail();
        } catch (IllegalArgumentException e) {
        }
        assertEquals(0, elementExecuted[0]);
        assertEquals(0, elementExecuted[1]);
        assertEquals(0, elementExecuted[2]);
        assertEquals(0, elementExecuted[3]);
        assertEquals(0, elementExecuted[4]);
    }

    @Test
    public void testHierarchicalBlocks() {
        OptimizedBlockNode<TestElement> block;
        OptimizedCallTarget target;
        PartialBlocks<TestElement> partialBlocks;

        setup(3);

        block = createBlock(5, 2, null);
        target = createTest(block);
        target.computeBlockCompilations();
        target.call();
        target.compile(true);
        partialBlocks = block.getPartialBlocks();

        assertEquals(2, partialBlocks.getBlockTargets().length);
        assertEquals(1, partialBlocks.getBlockRanges().length);
        assertEquals(3, partialBlocks.getBlockRanges()[0]);

        block = createBlock(5, 3, null);
        target = createTest(block);
        target.computeBlockCompilations();
        target.call();
        target.compile(true);
        partialBlocks = block.getPartialBlocks();

        assertEquals(2, partialBlocks.getBlockTargets().length);
        assertEquals(1, partialBlocks.getBlockRanges().length);
        assertEquals(3, partialBlocks.getBlockRanges()[0]);
    }

    @Test
    public void testHierarchicalUnbalanced() {
        OptimizedBlockNode<TestElement> block;
        OptimizedCallTarget target;
        PartialBlocks<TestElement> partialBlocks;

        setup(50);
        block = createBlock(10, 4, null);
        target = createTest(block);
        target.computeBlockCompilations();
        target.call();
        target.compile(true);
        partialBlocks = block.getPartialBlocks();

        assertEquals(3, partialBlocks.getBlockTargets().length);
        assertEquals(2, partialBlocks.getBlockRanges().length);
        assertEquals(4, partialBlocks.getBlockRanges()[0]);
        assertEquals(8, partialBlocks.getBlockRanges()[1]);
    }

    @Test
    public void testNoCallCompilation() {
        int blockSize = 1;
        for (int i = 0; i < 5; i++) {
            setup(blockSize);
            OptimizedBlockNode<?> block = createBlock(blockSize + 1, 1);
            OptimizedCallTarget target = createTest(block);
            target.computeBlockCompilations();
            target.compile(true);
            assertValid(target, block.getPartialBlocks());
            target.call();
            assertValid(target, block.getPartialBlocks());
            blockSize = blockSize * 2;
        }
    }

    @Test
    public void testSimpleLanguageExample() {
        final int testBlockSize = 128;
        final int targetBlocks = 4;

        setup(testBlockSize);
        int emptyNodeCount = generateSLFunction(context, "empty", BlockNode.NO_ARGUMENT).getNonTrivialNodeCount();
        int singleNodeCount = generateSLFunction(context, "single", 1).getNonTrivialNodeCount();
        int twoNodeCount = generateSLFunction(context, "two", 2).getNonTrivialNodeCount();
        int singleStatementNodeCount = twoNodeCount - singleNodeCount;
        int blockOverhead = singleNodeCount - emptyNodeCount - singleStatementNodeCount;

        context.initialize("sl");

        int statements = Math.floorDiv(((testBlockSize * targetBlocks) - (blockOverhead)), singleStatementNodeCount);
        OptimizedCallTarget target = generateSLFunction(context, "test", statements);
        target.computeBlockCompilations();
        assertEquals((statements - 1) * singleStatementNodeCount + singleNodeCount, target.getNonTrivialNodeCount());
        Value v = context.getBindings("sl").getMember("test");

        // make it compile with threshold
        for (int i = 0; i < TEST_COMPILATION_THRESHOLD; i++) {
            assertEquals(statements, v.execute().asInt());
        }
        assertTrue(target.isValid());
        List<OptimizedBlockNode<TestElement>> blocks = new ArrayList<>();
        target.getRootNode().accept(new NodeVisitor() {

            @SuppressWarnings("unchecked")
            @Override
            public boolean visit(Node node) {
                if (node instanceof OptimizedBlockNode<?>) {
                    blocks.add((OptimizedBlockNode<TestElement>) node);
                }
                return true;
            }
        });
        assertEquals(1, blocks.size());
        OptimizedBlockNode<TestElement> block = blocks.iterator().next();
        PartialBlocks<TestElement> partialBlocks = block.getPartialBlocks();
        assertNotNull(partialBlocks);
        assertEquals(targetBlocks, partialBlocks.getBlockTargets().length);
    }

    @Test
    public void testBlockCompilationTrigger() {
        // the number 200 for the maximum compile limit is heavily dependent
        // on implementation details and might need to be updated the future.
        setup(10, 200);

        // test not triggering the limit
        OptimizedBlockNode<?> block = createBlock(1, 1);
        OptimizedCallTarget target = createTest(block);
        target.compile(true);
        assertNull(block.getPartialBlocks());

        block = createBlock(10 + 1, 1);
        target = createTest(block);
        target.compile(true);
        assertNotNull(block.getPartialBlocks());
    }

    @Test
    public void testSourceSectionsOutOfOrder() {
        setup(3);

        OptimizedBlockNode<TestElement> block = createBlock(5, 1, null);
        OptimizedCallTarget target = createTest(block);

        TestElement[] elements = block.getElements();
        assertEquals(5, elements.length);
        Source source = Source.newBuilder(ProxyLanguage.ID, "a;\nb;\nc;\nd;\ne;", "test source").build();
        elements[0].sourceSection = source.createSection(2);
        elements[1].sourceSection = source.createSection(3);
        elements[2].sourceSection = source.createSection(1);
        elements[3].sourceSection = source.createSection(4);
        elements[4].sourceSection = source.createSection(5);

        target.computeBlockCompilations();
        target.call();
        target.compile(true);

        PartialBlocks<TestElement> partialBlocks = block.getPartialBlocks();
        assertEquals(2, partialBlocks.getBlockTargets().length);
        assertEquals(1, partialBlocks.getBlockRanges().length);
        assertEquals(3, partialBlocks.getBlockRanges()[0]);

        assertEquals(2, partialBlocks.getBlockTargets()[0].getRootNode().getSourceSection().getStartLine());
        assertEquals(2, partialBlocks.getBlockTargets()[0].getRootNode().getSourceSection().getEndLine());
        assertEquals(4, partialBlocks.getBlockTargets()[1].getRootNode().getSourceSection().getStartLine());
        assertEquals(5, partialBlocks.getBlockTargets()[1].getRootNode().getSourceSection().getEndLine());
    }

    private static OptimizedBlockNode<TestElement> createBlock(int blockSize, int depth) {
        return createBlock(blockSize, depth, null);
    }

    private static OptimizedBlockNode<TestElement> createBlock(int blockSize, int depth, Object returnValue) {
        return createBlock(blockSize, depth, returnValue, new TestElementExecutor(), 0);
    }

    private static OptimizedBlockNode<TestElement> createBlock(int blockSize, int depth, Object returnValue, ElementExecutor<TestElement> executor, int extraChildrenOfFirstElement) {
        if (depth == 0) {
            return null;
        }
        TestElement[] elements = new TestElement[blockSize];
        for (int i = 0; i < blockSize; i++) {
            Node[] extraDummyChildren;
            if (i == 0 && extraChildrenOfFirstElement > 0) {
                extraDummyChildren = new Node[extraChildrenOfFirstElement];
                for (int j = 0; j < extraDummyChildren.length; j++) {
                    extraDummyChildren[j] = new Node() {
                    };
                }
            } else {
                extraDummyChildren = new Node[0];
            }
            elements[i] = new TestElement(createBlock(blockSize, depth - 1, returnValue), returnValue == null ? i : returnValue, i, extraDummyChildren);
        }
        return (OptimizedBlockNode<TestElement>) BlockNode.create(elements, executor);
    }

    private static OptimizedCallTarget createTest(BlockNode<?> block) {
        TestRootNode root = new TestRootNode(block, "Block[" + block.getElements().length + "]");
        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        root.accept(new NodeVisitor() {
            @Override
            public boolean visit(Node node) {
                if (node instanceof TestElement) {
                    ((TestElement) node).onAdopt();
                }
                return true;
            }
        });
        return target;
    }

    private static OptimizedCallTarget generateSLFunction(Context context, String name, int statements) {
        StringBuilder b = new StringBuilder("function " + name + "(){");
        if (statements > 0) {
            b.append("i = 0;\n");
        }
        for (int i = 0; i < statements; i++) {
            b.append("i = i + 1;\n");
        }
        if (statements > 0) {
            b.append("return i;\n");
        }
        b.append("}");
        context.eval("sl", b.toString());
        context.getBindings("sl").getMember(name).execute();
        context.enter();
        try {
            OptimizedCallTarget target = ((OptimizedCallTarget) SLContext.get(null).getFunctionRegistry().getFunction(SLStrings.fromJavaString(name)).getCallTarget());
            // we invalidate to make sure the call counts are updated.
            target.invalidate("invalidate for test");
            return target;
        } finally {
            context.leave();
        }
    }

    private static void assertValid(OptimizedCallTarget target, PartialBlocks<?> partialBlocks) {
        assertNotNull(partialBlocks);
        assertTrue(target.isValid());
        for (int i = 0; i < partialBlocks.getBlockTargets().length; i++) {
            OptimizedCallTarget blockTarget = partialBlocks.getBlockTargets()[i];
            assertTrue(String.valueOf(i), blockTarget.isValid());
        }
    }

    private static void assertUnexpected(Callable<?> callable, Object result) {
        try {
            callable.call();
        } catch (UnexpectedResultException t) {
            assertEquals(result, t.getResult());
            return;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        fail("expected unexpected result but no exception was thrown");
    }

    private Context context;

    @After
    public void clearContext() {
        if (context != null) {
            context.leave();
            context.close();
        }
    }

    private static final int TEST_COMPILATION_THRESHOLD = 10;

    private void setup(int blockCompilationSize) {
        setup(blockCompilationSize, 10000);
    }

    private void setup(int blockCompilationSize, int maxGraalNodeCount) {
        clearContext();
        context = Context.newBuilder().allowAllAccess(true)//
                        .option("engine.BackgroundCompilation", "false") //
                        .option("engine.MultiTier", "false") //
                        .option("engine.PartialBlockCompilationSize", String.valueOf(blockCompilationSize))//
                        .option("engine.MaximumGraalNodeCount", String.valueOf(maxGraalNodeCount))//
                        .option("engine.SingleTierCompilationThreshold", String.valueOf(TEST_COMPILATION_THRESHOLD)).build();
        context.enter();
    }

    static class ElementChildNode extends Node {

        @Override
        public NodeCost getCost() {
            // we don't want this to contribute to node costs
            return NodeCost.NONE;
        }

    }

    static class StartsWithExecutor extends TestElementExecutor {

        @Override
        public void executeVoid(VirtualFrame frame, TestElement node, int elementIndex, int startsWith) {
            CompilerAsserts.partialEvaluationConstant(this.getClass());
            CompilerAsserts.partialEvaluationConstant(elementIndex);
            if (elementIndex >= startsWith) {
                node.execute(frame);
            }
        }

        @Override
        public Object executeGeneric(VirtualFrame frame, TestElement node, int elementIndex, int startsWith) {
            CompilerAsserts.partialEvaluationConstant(this.getClass());
            CompilerAsserts.partialEvaluationConstant(elementIndex);
            if (elementIndex >= startsWith) {
                return node.execute(frame);
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException();
        }

    }

    static class TestElementExecutor implements BlockNode.ElementExecutor<TestElement> {

        @Override
        public void executeVoid(VirtualFrame frame, TestElement node, int index, int argument) {
            executeGeneric(frame, node, index, argument);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame, TestElement node, int index, int argument) {
            CompilerAsserts.partialEvaluationConstant(this.getClass());
            CompilerAsserts.partialEvaluationConstant(index);
            return node.execute(frame);
        }

    }

    static class TestElement extends Node {

        @Child BlockNode<?> childBlock;
        @Child ElementChildNode childNode = new ElementChildNode();
        @Children Node[] extraDummyChildren;

        final Object returnValue;
        final int childIndex;

        @CompilationFinal TestRootNode root;
        SourceSection sourceSection;

        TestElement(BlockNode<?> childBlock, Object returnValue, int childIndex, Node[] extraDummyChildren) {
            this.childBlock = childBlock;
            this.returnValue = returnValue;
            this.childIndex = childIndex;
            this.extraDummyChildren = extraDummyChildren;
        }

        void onAdopt() {
            root = (TestRootNode) getRootNode();
        }

        public void simulateReplace() {
            childNode.replace(new ElementChildNode());
        }

        public Object execute(VirtualFrame frame) {
            root.elementExecuted[childIndex]++;
            if (childBlock != null) {
                return childBlock.executeGeneric(frame, BlockNode.NO_ARGUMENT);
            }
            return returnValue;
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

    }

    static class TestRootNode extends RootNode {

        @Child BlockNode<?> block;
        final int[] elementExecuted;

        private final String name;

        TestRootNode(BlockNode<?> block, String name) {
            super(null);
            this.block = block;
            this.name = name;
            this.elementExecuted = new int[block.getElements().length];
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            for (int i = 0; i < elementExecuted.length; i++) {
                elementExecuted[i] = 0;
            }
            int argument;
            if (frame.getArguments().length > 0) {
                argument = (int) frame.getArguments()[0];
            } else {
                argument = 0;
            }
            return block.executeGeneric(frame, argument);
        }

        @Override
        public String toString() {
            return getName();
        }

    }

}
