/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.BlockNode.ElementExceptionHandler;
import com.oracle.truffle.api.nodes.BlockNode.GenericElement;
import com.oracle.truffle.api.nodes.BlockNode.TypedElement;
import com.oracle.truffle.api.nodes.BlockNode.VoidElement;
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
        assertFails(() -> BlockNode.create((TestVoidElement[]) null), NullPointerException.class);

        BlockNode<?> invalidBlock = BlockNode.create(new TestVoidElement[1]);
        assertFails(() -> mode.execute(invalidBlock, 0, null), NullPointerException.class);
        assertFails(() -> mode.execute(invalidBlock), NullPointerException.class);

        assertFails(() -> create(createElements(0)), IllegalArgumentException.class);

        BlockNode<TestBlockElement> block1 = create(createElements(1));
        assertFails(() -> mode.execute(block1, 1, null), IllegalArgumentException.class);
        assertFails(() -> mode.execute(block1, -1, null), IllegalArgumentException.class);
        mode.execute(block1, 0, null);
        assertEquals(1, block1.getElements()[0].allCounts);
        mode.assertCount(1, block1.getElements()[0]);

        mode.execute(block1);
        assertEquals(2, block1.getElements()[0].allCounts);
        mode.assertCount(2, block1.getElements()[0]);

        BlockNode<TestBlockElement> block4 = create(createElements(4));
        assertFails(() -> mode.execute(block4, 4, null), IllegalArgumentException.class);
        assertFails(() -> mode.execute(block4, -1, null), IllegalArgumentException.class);

        BlockNode<?> voidBlock = create(createVoidElements(2));
        BlockNode<?> genericBlock = create(createGenericElements(2));
        if (mode.isTyped()) {
            assertFails(() -> mode.execute(voidBlock, 0, null), ClassCastException.class);
            assertFails(() -> mode.execute(voidBlock), ClassCastException.class);
            assertFails(() -> mode.execute(genericBlock, 0, null), ClassCastException.class);
            assertFails(() -> mode.execute(genericBlock), ClassCastException.class);
        } else if (mode.isGeneric()) {
            assertFails(() -> mode.execute(voidBlock, 0, null), ClassCastException.class);
            mode.execute(genericBlock, 0, null);
            mode.execute(genericBlock);
        } else {
            mode.execute(voidBlock, 0, null);
            mode.execute(voidBlock);
            mode.execute(genericBlock, 0, null);
            mode.execute(genericBlock);
        }
    }

    @Test
    public void testStartsWithExecute() {
        for (int blockLength = 1; blockLength < 50; blockLength++) {
            BlockNode<TestBlockElement> block = create(createElements(blockLength));
            TestBlockElement[] elements = block.getElements();
            Object result = mode.getResult(blockLength - 1);

            int[] expectedCounts = new int[blockLength];
            for (int i = 0; i < blockLength; i++) {
                assertEquals(blockLength + ":" + i, result, mode.execute(block, i, null));
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

    static class OtherTestElementExceptionHandler implements ElementExceptionHandler {

        public void onBlockElementException(VirtualFrame frame, Throwable e, int elementIndex) {

        }

    }

    static class TestElementExceptionHandler implements ElementExceptionHandler {

        int exceptionCount;
        int seenElementIndex;
        final Throwable expectedException;

        TestElementExceptionHandler(Throwable expectedException) {
            this.expectedException = expectedException;
        }

        public void onBlockElementException(VirtualFrame frame, Throwable e, int elementIndex) {
            assertSame(expectedException, e);
            this.exceptionCount++;
            this.seenElementIndex = elementIndex;
        }
    }

    @Test
    public void testExceptionHandler() {
        for (int blockLength = 1; blockLength < 50; blockLength++) {
            BlockNode<TestBlockElement> block = create(createElements(blockLength));
            TestBlockElement[] elements = block.getElements();

            for (int startsWith = 0; startsWith < blockLength; startsWith++) {
                TestException expectedException = new TestException();
                for (int j = 0; j < blockLength; j++) {
                    elements[j].exception = expectedException;
                }
                TestElementExceptionHandler e = new TestElementExceptionHandler(expectedException);
                try {
                    mode.execute(block, startsWith, e);
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
    public void testExceptionHandlerErrors0() {
        BlockNode<TestBlockElement> block = create(createElements(14));
        mode.execute(block, 0, null);
        assertFails(() -> mode.execute(block, 0, new TestElementExceptionHandler(null)), IllegalArgumentException.class);
    }

    @Test
    public void testExceptionHandlerErrors1() {
        BlockNode<TestBlockElement> block = create(createElements(11));
        mode.execute(block);
        assertFails(() -> mode.execute(block, 0, new TestElementExceptionHandler(null)), IllegalArgumentException.class);
    }

    @Test
    public void testExceptionHandlerErrors2() {
        BlockNode<TestBlockElement> block = create(createElements(11));
        mode.execute(block, 0, new TestElementExceptionHandler(null));
        assertFails(() -> mode.execute(block, 0, null), IllegalArgumentException.class);
    }

    @Test
    public void testExceptionHandlerErrors3() {
        BlockNode<TestBlockElement> block = create(createElements(11));
        mode.execute(block, 0, new TestElementExceptionHandler(null));
        assertFails(() -> mode.execute(block), IllegalArgumentException.class);
    }

    @Test
    public void testExceptionHandlerErrors4() {
        BlockNode<TestBlockElement> block = create(createElements(11));
        mode.execute(block, 0, new TestElementExceptionHandler(null));
        mode.execute(block, 0, new TestElementExceptionHandler(null));
        assertFails(() -> mode.execute(block, 0, new OtherTestElementExceptionHandler()), IllegalArgumentException.class);
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

    static TestVoidElement[] createVoidElements(int size) {
        TestVoidElement[] elements = new TestVoidElement[size];
        for (int i = 0; i < elements.length; i++) {
            elements[i] = new TestVoidElement();
        }
        return elements;
    }

    static TestGenericElement[] createGenericElements(int size) {
        TestGenericElement[] elements = new TestGenericElement[size];
        for (int i = 0; i < elements.length; i++) {
            elements[i] = new TestGenericElement();
        }
        return elements;
    }

    @SuppressWarnings("unchecked")
    static <T extends Node & VoidElement> BlockNode<T> create(T... elements) {
        DummyRootNode root = new DummyRootNode();
        BlockNode<T> block = BlockNode.create(elements);
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

        GENERIC,
        BYTE,
        SHORT,
        CHARACTER,
        INT,
        VOID,
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
            try {
                switch (this) {
                    case GENERIC:
                        return block.execute(null);
                    case VOID:
                        block.executeVoid(null);
                        return null;
                    case BOOLEAN:
                        return block.executeBoolean(null);
                    case BYTE:
                        return block.executeByte(null);
                    case SHORT:
                        return block.executeShort(null);
                    case INT:
                        return block.executeInt(null);
                    case CHARACTER:
                        return block.executeChar(null);
                    case LONG:
                        return block.executeLong(null);
                    case FLOAT:
                        return block.executeFloat(null);
                    case DOUBLE:
                        return block.executeDouble(null);
                }
            } catch (UnexpectedResultException e) {
                throw new AssertionError(e);
            }
            throw new AssertionError();
        }

        Object execute(BlockNode<?> block, int start, ElementExceptionHandler handler) {
            try {
                switch (this) {
                    case GENERIC:
                        return block.execute(null, start, handler);
                    case VOID:
                        block.executeVoid(null, start, handler);
                        return null;
                    case BOOLEAN:
                        return block.executeBoolean(null, start, handler);
                    case BYTE:
                        return block.executeByte(null, start, handler);
                    case SHORT:
                        return block.executeShort(null, start, handler);
                    case INT:
                        return block.executeInt(null, start, handler);
                    case CHARACTER:
                        return block.executeChar(null, start, handler);
                    case LONG:
                        return block.executeLong(null, start, handler);
                    case FLOAT:
                        return block.executeFloat(null, start, handler);
                    case DOUBLE:
                        return block.executeDouble(null, start, handler);
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

        @Child BlockNode<? extends VoidElement> block;

        @Override
        public Object execute(VirtualFrame frame) {
            return block.execute(frame, 0, null);
        }

    }

    static class TestVoidElement extends Node implements VoidElement {

        public void executeVoid(VirtualFrame frame) {
        }
    }

    static class TestGenericElement extends Node implements GenericElement {

        public Object execute(VirtualFrame frame) {
            return null;
        }

    }

    @SuppressWarnings("serial")
    static class TestException extends ControlFlowException {
    }

    static class TestBlockElement extends Node implements TypedElement {

        final int index;
        final int[] counts = new int[Mode.values().length];

        int allCounts = 0;

        @CompilationFinal TestException exception;

        TestBlockElement(int index) {
            this.index = index;
        }

        public Object execute(VirtualFrame frame) {
            return execute(Mode.GENERIC, Object.class);
        }

        private <T> T execute(Mode mode, Class<T> type) {
            counts[mode.ordinal()]++;
            allCounts++;
            if (exception != null) {
                throw exception;
            }
            return type.cast(mode.getResult(index));
        }

        public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
            return execute(Mode.BOOLEAN, Boolean.class);
        }

        public byte executeByte(VirtualFrame frame) throws UnexpectedResultException {
            return execute(Mode.BYTE, Byte.class);
        }

        public short executeShort(VirtualFrame frame) throws UnexpectedResultException {
            return execute(Mode.SHORT, Short.class);
        }

        public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
            return execute(Mode.INT, Integer.class);
        }

        public char executeChar(VirtualFrame frame) throws UnexpectedResultException {
            return execute(Mode.CHARACTER, Character.class);
        }

        public float executeFloat(VirtualFrame frame) throws UnexpectedResultException {
            return execute(Mode.FLOAT, Float.class);
        }

        public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
            return execute(Mode.DOUBLE, Double.class);
        }

        public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
            return execute(Mode.LONG, Long.class);
        }

        public void executeVoid(VirtualFrame frame) {
            execute(Mode.VOID, Void.class);
        }
    }

}
