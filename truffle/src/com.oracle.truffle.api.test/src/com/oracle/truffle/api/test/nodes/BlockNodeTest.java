/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.nodes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.concurrent.Callable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@RunWith(Parameterized.class)
public class BlockNodeTest {

    @Parameters(name = "{0}")
    public static Mode[] data() {
        return Mode.values();
    }

    @Parameter(0) public Mode mode;

    @Test
    public void testErrors() {
        assertFails(() -> BlockNode.create((TestBlockElement[]) null, new TestExecutor()), NullPointerException.class);
        assertFails(() -> BlockNode.create(new TestBlockElement[1], null), NullPointerException.class);

        BlockNode<?> invalidBlock = BlockNode.create(new TestBlockElement[1], new TestExecutor());
        assertFails(() -> mode.execute(invalidBlock, 0), NullPointerException.class);
        assertFails(() -> create(createElements(0)), IllegalArgumentException.class);

        BlockNode<TestBlockElement> block1 = create(createElements(1), new StartsWithExecutor());
        assertFails(() -> mode.execute(block1, 1), IllegalArgumentException.class);
        mode.execute(block1, 0);
        assertEquals(1, block1.getElements()[0].allCounts);
        mode.assertCount(1, block1.getElements()[0]);

        mode.execute(block1);
        assertEquals(2, block1.getElements()[0].allCounts);
        mode.assertCount(2, block1.getElements()[0]);

        BlockNode<TestBlockElement> block4 = create(createElements(4), new StartsWithExecutor());
        assertFails(() -> mode.execute(block4, 4), IllegalArgumentException.class);

        BlockNode<?> block = create(createElements(2));
        assertEquals(mode.getResult(1), mode.execute(block));
    }

    @Test
    public void testStartsWithExecute() {
        for (int blockLength = 1; blockLength < 50; blockLength++) {
            BlockNode<TestBlockElement> block = create(createElements(blockLength), new StartsWithExecutor());
            TestBlockElement[] elements = block.getElements();
            Object result = mode.getResult(blockLength - 1);

            int[] expectedCounts = new int[blockLength];
            for (int i = 0; i < blockLength; i++) {
                assertEquals(blockLength + ":" + i, result, mode.execute(block, i));
                for (int j = i; j < blockLength; j++) {
                    expectedCounts[j]++;
                }
                for (int j = 0; j < blockLength - 1; j++) {
                    assertEquals(blockLength + ":" + i + ":" + j, expectedCounts[j], elements[j].counts[Mode.VOID.ordinal()]);
                    assertEquals(blockLength + ":" + i + ":" + j, expectedCounts[j], elements[j].allCounts);
                }
                mode.assertCount(expectedCounts[blockLength - 1], elements[blockLength - 1]);
                assertEquals(blockLength + ":" + i, expectedCounts[blockLength - 1], elements[blockLength - 1].allCounts);
            }
        }
    }

    @Test
    public void testDefaultExecute() {
        for (int blockLength = 1; blockLength < 50; blockLength++) {
            BlockNode<TestBlockElement> block = create(createElements(blockLength));
            TestBlockElement[] elements = block.getElements();
            Object result = mode.getResult(blockLength - 1);

            assertEquals(blockLength + ":", result, mode.execute(block));
            for (int j = 0; j < blockLength - 1; j++) {
                assertEquals(blockLength + ":" + j, 1, elements[j].counts[Mode.VOID.ordinal()]);
                assertEquals(blockLength + ":" + j, 1, elements[j].allCounts);
            }
            mode.assertCount(1, elements[blockLength - 1]);
            assertEquals(blockLength + ":", 1, elements[blockLength - 1].allCounts);
        }
    }

    static class TestElementExceptionHandler extends StartsWithExecutor {

        int exceptionCount;
        int seenElementIndex;
        final Throwable expectedException;

        TestElementExceptionHandler(Throwable expectedException) {
            this.expectedException = expectedException;
        }

        @Override
        public void executeVoid(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) {
            try {
                super.executeVoid(frame, node, elementIndex, startsWith);
            } catch (Throwable t) {
                onBlockElementException(t, elementIndex);
                throw t;
            }
        }

        @Override
        public Object executeGeneric(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) {
            try {
                return super.executeGeneric(frame, node, elementIndex, startsWith);
            } catch (Throwable t) {
                onBlockElementException(t, elementIndex);
                throw t;
            }
        }

        @Override
        public boolean executeBoolean(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            try {
                return super.executeBoolean(frame, node, elementIndex, startsWith);
            } catch (Throwable t) {
                onBlockElementException(t, elementIndex);
                throw t;
            }
        }

        @Override
        public byte executeByte(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            try {
                return super.executeByte(frame, node, elementIndex, startsWith);
            } catch (Throwable t) {
                onBlockElementException(t, elementIndex);
                throw t;
            }
        }

        @Override
        public short executeShort(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            try {
                return super.executeShort(frame, node, elementIndex, startsWith);
            } catch (Throwable t) {
                onBlockElementException(t, elementIndex);
                throw t;
            }
        }

        @Override
        public char executeChar(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            try {
                return super.executeChar(frame, node, elementIndex, startsWith);
            } catch (Throwable t) {
                onBlockElementException(t, elementIndex);
                throw t;
            }
        }

        @Override
        public int executeInt(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            try {
                return super.executeInt(frame, node, elementIndex, startsWith);
            } catch (Throwable t) {
                onBlockElementException(t, elementIndex);
                throw t;
            }
        }

        @Override
        public long executeLong(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            try {
                return super.executeLong(frame, node, elementIndex, startsWith);
            } catch (Throwable t) {
                onBlockElementException(t, elementIndex);
                throw t;
            }
        }

        @Override
        public float executeFloat(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            try {
                return super.executeFloat(frame, node, elementIndex, startsWith);
            } catch (Throwable t) {
                onBlockElementException(t, elementIndex);
                throw t;
            }
        }

        @Override
        public double executeDouble(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            try {
                return super.executeDouble(frame, node, elementIndex, startsWith);
            } catch (Throwable t) {
                onBlockElementException(t, elementIndex);
                throw t;
            }
        }

        void onBlockElementException(Throwable e, int elementIndex) {
            assertSame(expectedException, e);
            this.exceptionCount++;
            this.seenElementIndex = elementIndex;
        }
    }

    @Test
    public void testExceptionHandler() {
        for (int blockLength = 1; blockLength < 50; blockLength++) {
            for (int startsWith = 0; startsWith < blockLength; startsWith++) {

                TestException expectedException = new TestException();
                TestElementExceptionHandler e = new TestElementExceptionHandler(expectedException);
                BlockNode<TestBlockElement> block = create(createElements(blockLength), e);
                TestBlockElement[] elements = block.getElements();

                for (int j = 0; j < blockLength; j++) {
                    elements[j].exception = expectedException;
                }
                try {
                    mode.execute(block, startsWith);
                } catch (TestException ex) {
                    assertSame(expectedException, ex);
                }
                if (startsWith < blockLength - 1) {
                    assertEquals(blockLength + ":", 1, elements[startsWith].counts[Mode.VOID.ordinal()]);
                } else {
                    assertEquals(blockLength + ":", 1, elements[startsWith].counts[mode.ordinal()]);
                }
                assertEquals(1, elements[startsWith].allCounts);
                assertEquals(1, e.exceptionCount);
                assertSame(startsWith, e.seenElementIndex);
            }
        }
    }

    @Test
    public void testToString() {
        assertNotNull(create(createElements(4)).toString());
    }

    static TestBlockElement[] createElements(int size) {
        TestBlockElement[] elements = new TestBlockElement[size];
        for (int i = 0; i < elements.length; i++) {
            elements[i] = new TestBlockElement(i);
        }
        return elements;
    }

    @SuppressWarnings("unchecked")
    static BlockNode<TestBlockElement> create(TestBlockElement[] elements) {
        return create(elements, new TestExecutor());
    }

    static BlockNode<TestBlockElement> create(TestBlockElement[] elements, BlockNode.ElementExecutor<TestBlockElement> executor) {
        DummyRootNode root = new DummyRootNode();
        BlockNode<TestBlockElement> block = BlockNode.create(elements, executor);
        root.block = block;
        Truffle.getRuntime().createCallTarget(root);
        assertNotNull(root.block.getParent());
        return block;
    }

    protected static void assertFails(Callable<?> callable, Class<? extends Throwable> exceptionType) {
        try {
            callable.call();
        } catch (Throwable t) {
            if (!exceptionType.isInstance(t)) {
                throw new AssertionError("expected instanceof " + exceptionType.getName() + " was " + t.getClass().getName(), t);
            }
            return;
        }
        fail("expected " + exceptionType.getName() + " but no exception was thrown");
    }

    enum Mode {

        VOID,
        GENERIC,
        BYTE,
        SHORT,
        CHARACTER,
        INT,
        FLOAT,
        DOUBLE,
        LONG,
        BOOLEAN;

        public boolean isTyped() {
            switch (this) {
                case BOOLEAN:
                case DOUBLE:
                case INT:
                case LONG:
                case SHORT:
                case BYTE:
                case FLOAT:
                case CHARACTER:
                    return true;
            }
            return false;
        }

        public boolean isGeneric() {
            return this == GENERIC;
        }

        public boolean isVoid() {
            return this == Mode.VOID;
        }

        @TruffleBoundary
        Object getResult(int index) {
            switch (this) {
                case GENERIC:
                    return "" + index;
                case VOID:
                    return null;
                case BOOLEAN:
                    return true;
                case BYTE:
                    return (byte) index;
                case SHORT:
                    return (short) index;
                case CHARACTER:
                    return (char) index;
                case FLOAT:
                    return (float) index;
                case DOUBLE:
                    return (double) index;
                case INT:
                    return index;
                case LONG:
                    return (long) index;
            }
            throw new AssertionError();
        }

        void assertCount(int expectedValue, TestBlockElement element) {
            assertEquals(expectedValue, element.counts[this.ordinal()]);
        }

        Object execute(BlockNode<?> block) {
            return execute(block, BlockNode.NO_ARGUMENT);
        }

        Object execute(BlockNode<?> block, int arg) {
            try {
                switch (this) {
                    case GENERIC:
                        return block.executeGeneric(null, arg);
                    case VOID:
                        block.executeVoid(null, arg);
                        return null;
                    case BOOLEAN:
                        return block.executeBoolean(null, arg);
                    case BYTE:
                        return block.executeByte(null, arg);
                    case SHORT:
                        return block.executeShort(null, arg);
                    case INT:
                        return block.executeInt(null, arg);
                    case CHARACTER:
                        return block.executeChar(null, arg);
                    case LONG:
                        return block.executeLong(null, arg);
                    case FLOAT:
                        return block.executeFloat(null, arg);
                    case DOUBLE:
                        return block.executeDouble(null, arg);
                }
            } catch (UnexpectedResultException e) {
                throw new AssertionError(e);
            }
            throw new AssertionError();
        }

    }

    static class DummyRootNode extends RootNode {

        protected DummyRootNode() {
            super(null);
        }

        @Child BlockNode<TestBlockElement> block;

        @Override
        public Object execute(VirtualFrame frame) {
            return block.executeGeneric(frame, 0);
        }

    }

    @SuppressWarnings("serial")
    static class TestException extends ControlFlowException {
    }

    static class StartsWithExecutor extends TestExecutor {

        @Override
        public void executeVoid(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) {
            if (startsWith >= ((BlockNode<?>) node.getParent()).getElements().length) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalArgumentException();
            }
            if (elementIndex >= startsWith) {
                super.executeVoid(frame, node, elementIndex, startsWith);
            }
        }

        @Override
        public Object executeGeneric(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) {
            if (elementIndex >= startsWith) {
                return super.executeGeneric(frame, node, elementIndex, startsWith);
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException();
        }

        @Override
        public boolean executeBoolean(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            if (elementIndex >= startsWith) {
                return super.executeBoolean(frame, node, elementIndex, startsWith);
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException();
        }

        @Override
        public byte executeByte(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            if (elementIndex >= startsWith) {
                return super.executeByte(frame, node, elementIndex, startsWith);
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException();
        }

        @Override
        public short executeShort(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            if (elementIndex >= startsWith) {
                return super.executeShort(frame, node, elementIndex, startsWith);
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException();
        }

        @Override
        public char executeChar(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            if (elementIndex >= startsWith) {
                return super.executeChar(frame, node, elementIndex, startsWith);
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException();
        }

        @Override
        public int executeInt(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            if (elementIndex >= startsWith) {
                return super.executeInt(frame, node, elementIndex, startsWith);
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException();
        }

        @Override
        public long executeLong(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            if (elementIndex >= startsWith) {
                return super.executeLong(frame, node, elementIndex, startsWith);
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException();
        }

        @Override
        public float executeFloat(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            if (elementIndex >= startsWith) {
                return super.executeFloat(frame, node, elementIndex, startsWith);
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException();
        }

        @Override
        public double executeDouble(VirtualFrame frame, TestBlockElement node, int elementIndex, int startsWith) throws UnexpectedResultException {
            if (elementIndex >= startsWith) {
                return super.executeDouble(frame, node, elementIndex, startsWith);
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException();
        }

    }

    static class TestExecutor implements BlockNode.ElementExecutor<TestBlockElement> {

        public void executeVoid(VirtualFrame frame, TestBlockElement node, int index, int argument) {
            node.execute(Mode.VOID, Object.class);
        }

        public Object executeGeneric(VirtualFrame frame, TestBlockElement node, int index, int argument) {
            return node.execute(Mode.GENERIC, Object.class);
        }

        public boolean executeBoolean(VirtualFrame frame, TestBlockElement node, int index, int argument) throws UnexpectedResultException {
            return node.execute(Mode.BOOLEAN, Boolean.class);
        }

        public byte executeByte(VirtualFrame frame, TestBlockElement node, int index, int argument) throws UnexpectedResultException {
            return node.execute(Mode.BYTE, Byte.class);
        }

        public short executeShort(VirtualFrame frame, TestBlockElement node, int index, int argument) throws UnexpectedResultException {
            return node.execute(Mode.SHORT, Short.class);
        }

        public char executeChar(VirtualFrame frame, TestBlockElement node, int index, int argument) throws UnexpectedResultException {
            return node.execute(Mode.CHARACTER, Character.class);
        }

        public int executeInt(VirtualFrame frame, TestBlockElement node, int index, int argument) throws UnexpectedResultException {
            return node.execute(Mode.INT, Integer.class);
        }

        public long executeLong(VirtualFrame frame, TestBlockElement node, int index, int argument) throws UnexpectedResultException {
            return node.execute(Mode.LONG, Long.class);
        }

        public float executeFloat(VirtualFrame frame, TestBlockElement node, int index, int argument) throws UnexpectedResultException {
            return node.execute(Mode.FLOAT, Float.class);
        }

        public double executeDouble(VirtualFrame frame, TestBlockElement node, int index, int argument) throws UnexpectedResultException {
            return node.execute(Mode.DOUBLE, Double.class);
        }

    }

    @SuppressWarnings("unused")
    static class TestBlockElement extends Node {

        final int index;
        final int[] counts = new int[Mode.values().length];

        int allCounts = 0;

        @CompilationFinal TestException exception;

        TestBlockElement(int index) {
            this.index = index;
        }

        <T> T execute(Mode mode, Class<T> type) {
            counts[mode.ordinal()]++;
            allCounts++;
            if (exception != null) {
                throw exception;
            }
            return type.cast(mode.getResult(index));
        }

    }

}
