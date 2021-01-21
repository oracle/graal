/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyIterable;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyIterator;
import org.junit.Before;
import org.junit.Test;

public class IteratorTest extends AbstractPolyglotTest {

    private VerifyingHandler verifyingHandler;

    @Before
    public void setUp() {
        verifyingHandler = new VerifyingHandler();
    }

    @Test
    public void testIterable() {
        testImpl((items) -> new SimpleIterable(items));
    }

    @Test
    public void testArray() {
        testImpl((items) -> new Array(items));
    }

    private void testImpl(Function<Object[], Object> factory) {
        String[] items = {"one", "two", "three", "four"};
        setupEnv(createContext(verifyingHandler), new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return createAST(languageInstance, factory.apply(items));
            }
        });
        verifyingHandler.expect(items);
        context.eval(ProxyLanguage.ID, "Test");
    }

    @Test
    public void testArrayWithUnreadableElements() {
        String[] items = new String[10];
        String[] expected = new String[items.length / 2];
        BitSet readable = new BitSet(items.length);
        for (int i = 0; i < items.length; i++) {
            items[i] = Integer.toString(i);
            if (i % 2 == 0) {
                readable.set(i);
                expected[i / 2] = items[i];
            }
        }
        setupEnv(createContext(verifyingHandler), new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return createAST(languageInstance, new Array(items, readable));
            }
        });
        verifyingHandler.expect(expected);
        context.eval(ProxyLanguage.ID, "Test");
    }

    @Test
    public void testValues() {
        String[] values = {"a", "b"};
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        return new SimpleIterable((Object[]) values);
                    }
                });
            }
        });
        Value iterable = context.eval(ProxyLanguage.ID, "Test");
        verifyIterable(iterable, values);
    }

    @Test
    public void testHostObjectArray() {
        Object[] values = {"a", "b"};
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value array = context.asValue(values);
        verifyIterable(array, values);
    }

    @Test
    public void testHostObjectList() {
        Object[] values = {"a", "b"};
        List<Object> valuesList = Arrays.asList(values);
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value list = context.asValue(new ArrayList<>(valuesList));
        verifyIterable(list, values);
    }

    @Test
    public void testHostObjectIterable() {
        Object[] values = {"a", "b"};
        List<Object> valuesList = Arrays.asList(values);
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterable = context.asValue(new Iterable<Object>() {

            private final List<Object> elements = new ArrayList<>(valuesList);

            @Override
            public Iterator<Object> iterator() {
                return elements.iterator();
            }
        });
        verifyIterable(iterable, values);
    }

    @Test
    public void testHostObjectIterator() {
        Object[] values = {"a", "b"};
        List<Object> valuesList = Arrays.asList(values);
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterator = context.asValue(valuesList.iterator());
        verifyIterator(iterator, values);
        iterator = context.asValue(valuesList.iterator());
        verifyIterator(iterator.as(Iterator.class), values);
    }

    @Test
    public void testProxyArray() {
        Object[] values = {"a", "b"};
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value array = context.asValue(ProxyArray.fromArray(values));
        verifyIterable(array, values);
    }

    @Test
    public void testProxyList() {
        Object[] values = {"a", "b"};
        List<Object> valuesList = new ArrayList<>();
        Collections.addAll(valuesList, values);
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value array = context.asValue(ProxyArray.fromList(valuesList));
        verifyIterable(array, values);
    }

    @Test
    public void testProxyIterable() {
        String[] values = {"a", "b"};
        Collection<Object> valuesIterable = new HashSet<>();
        Collections.addAll(valuesIterable, values);
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterable = context.asValue(ProxyIterable.from(valuesIterable));
        verifyIterable(iterable, values);
    }

    @Test
    public void testProxyIterableList() {
        String[] values = {"a", "b"};
        List<Object> valuesList = new ArrayList<>();
        Collections.addAll(valuesList, values);
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterable = context.asValue(ProxyIterable.from(valuesList));
        verifyIterable(iterable, values);
    }

    @Test
    public void testExecutableProxyIterable() {
        String[] values = {"a", "b"};
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterable = context.asValue(new ExecutableProxyIterableImpl(values));
        verifyIterable(iterable, values);
        assertTrue(iterable.canExecute());
        assertEquals(42, iterable.execute(42).asInt());
        assertEquals(42, iterable.as(new TypeLiteral<Function<Object, Object>>() {
        }).apply(42));
    }

    @Test
    public void testProxyIterator() {
        Object[] values = {"a", "b"};
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterator = context.asValue(ProxyIterator.from(Arrays.asList(values).iterator()));
        verifyIterator(iterator, values);
        iterator = context.asValue(ProxyIterator.from(Arrays.asList(values).iterator()));
        verifyIterator(iterator.as(Iterator.class), values);
    }

    @Test
    public void testExecutableProxyIterator() {
        Object[] values = {"a", "b"};
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterator = context.asValue(new ExecutableProxyIteratorImpl(Arrays.asList(values).iterator()));
        verifyIterator(iterator, values);
        assertTrue(iterator.canExecute());
        assertEquals(42, iterator.execute(42).asInt());
        iterator = context.asValue(new ExecutableProxyIteratorImpl(Arrays.asList(values).iterator()));
        verifyIterator(iterator.as(Iterator.class), values);
        assertEquals(42, iterator.as(new TypeLiteral<Function<Object, Object>>() {
        }).apply(42));
    }

    private static void verifyIterable(Value iterable, Object[] values) {
        assertTrue(iterable.hasIterator());
        assertFalse(iterable.isIterator());
        verifyIterator(iterable.getIterator(), values);
        verifyIterator(iterable.getIterator().as(Iterator.class), values);
        verifyIterator(iterable.as(Iterable.class).iterator(), values);
    }

    private static void verifyIterator(Value iterator, Object[] values) {
        assertFalse(iterator.hasIterator());
        assertTrue(iterator.isIterator());
        for (int i = 0; i < values.length; i++) {
            assertTrue(iterator.hasIteratorNextElement());
            assertTrue(iterator.hasIteratorNextElement());
            Value element = iterator.getIteratorNextElement();
            assertEquals(values[i], element.asString());
        }
        assertFalse(iterator.hasIteratorNextElement());
        assertFails(() -> iterator.getIteratorNextElement(), NoSuchElementException.class);
    }

    private static void verifyIterator(Iterator<?> iterator, Object[] values) {
        for (int i = 0; i < values.length; i++) {
            assertTrue(iterator.hasNext());
            assertTrue(iterator.hasNext());
            Object element = iterator.next();
            assertEquals(values[i], element);
        }
        assertFalse(iterator.hasNext());
        assertFails(() -> iterator.next(), NoSuchElementException.class);
    }

    private static CallTarget createAST(TruffleLanguage<?> lang, Object iterable) {
        FrameDescriptor fd = new FrameDescriptor();
        FrameSlot iterableSlot = fd.addFrameSlot("iterable", FrameSlotKind.Object);
        FrameSlot itemSlot = fd.addFrameSlot("item", FrameSlotKind.Object);
        StatementNode log = new LogStatement(IteratorTestFactory.ReadVariableNodeGen.create(itemSlot));
        StatementNode main = new BlockStatement(
                        new ExpressionStatement(IteratorTestFactory.WriteVariableNodeGen.create(new ConstantNode(iterable), iterableSlot)),
                        new ForEachStatement(iterableSlot, itemSlot, log));
        return Truffle.getRuntime().createCallTarget(new TestRootNode(lang, fd, main));
    }

    private static Context createContext(VerifyingHandler handler) {
        return Context.newBuilder().option(String.format("log.%s.level", handler.loggerName), "FINE").logHandler(handler).build();
    }

    @ExportLibrary(InteropLibrary.class)
    static class SimpleIterable implements TruffleObject {

        private final List<Object> items;

        SimpleIterable(Object... items) {
            this.items = new ArrayList<>(items.length);
            Collections.addAll(this.items, items);
        }

        @ExportMessage
        boolean hasIterator() {
            return true;
        }

        @ExportMessage
        Object getIterator() {
            return new SimpleIterator(items);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class SimpleIterator implements TruffleObject {

        private final List<Object> items;
        private int index;

        SimpleIterator(List<Object> items) {
            this.items = items;
            this.index = 0;
        }

        @ExportMessage
        boolean isIterator() {
            return true;
        }

        @ExportMessage
        boolean hasIteratorNextElement() {
            return index < items.size();
        }

        @ExportMessage
        Object getIteratorNextElement() throws StopIterationException {
            if (!hasIteratorNextElement()) {
                throw StopIterationException.create();
            }
            return items.get(index++);
        }
    }

    private static final class ExecutableProxyIterableImpl implements ProxyIterable, ProxyExecutable {

        private final List<Object> storage;

        ExecutableProxyIterableImpl(Object[] data) {
            this.storage = new ArrayList<>();
            Collections.addAll(this.storage, data);
        }

        @Override
        public Object getIterator() {
            return new ExecutableProxyIteratorImpl(storage.iterator());
        }

        @Override
        public Object execute(Value... arguments) {
            if (arguments.length != 1) {
                throw new UnsupportedOperationException();
            }
            return arguments[0];
        }
    }

    private static final class ExecutableProxyIteratorImpl implements ProxyIterator, ProxyExecutable {

        private final Iterator<Object> delegate;

        ExecutableProxyIteratorImpl(Iterator<Object> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Object getNext() {
            return delegate.next();
        }

        @Override
        public Object execute(Value... arguments) {
            if (arguments.length != 1) {
                throw new UnsupportedOperationException();
            }
            return arguments[0];
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class Array implements TruffleObject {

        private final Object[] items;
        private final BitSet readable;

        Array(Object... items) {
            this(items, null);
        }

        Array(Object[] items, BitSet readable) {
            this.items = items;
            this.readable = readable;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return items.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < items.length && (readable == null || readable.get((int) index));
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return items[(int) index];
        }
    }

    @SuppressWarnings("serial")
    static final class TypeError extends AbstractTruffleException {

        TypeError(String expected, Node location) {
            super("Type error, expected: " + expected, location);
        }
    }

    static class TestRootNode extends RootNode {

        private @Child StatementNode body;

        TestRootNode(TruffleLanguage<?> lang, FrameDescriptor fd, StatementNode body) {
            super(lang, fd);
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            body.executeVoid(frame);
            return true;
        }
    }

    abstract static class ExpressionNode extends Node {
        abstract Object execute(VirtualFrame frame);
    }

    static class ConstantNode extends ExpressionNode {

        private final Object constant;

        ConstantNode(Object constant) {
            this.constant = constant;
        }

        @Override
        Object execute(VirtualFrame frame) {
            return constant;
        }
    }

    @NodeField(name = "slot", type = FrameSlot.class)
    abstract static class ReadVariableNode extends ExpressionNode {

        protected abstract FrameSlot getSlot();

        @Specialization
        protected Object readObject(VirtualFrame frame) {
            try {
                return frame.getObject(getSlot());
            } catch (FrameSlotTypeException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @NodeChild("valueNode")
    @NodeField(name = "slot", type = FrameSlot.class)
    abstract static class WriteVariableNode extends ExpressionNode {

        protected abstract FrameSlot getSlot();

        @Specialization
        protected Object write(VirtualFrame frame, Object value) {
            frame.getFrameDescriptor().setFrameSlotKind(getSlot(), FrameSlotKind.Object);
            frame.setObject(getSlot(), value);
            return value;
        }
    }

    abstract static class StatementNode extends Node {
        abstract void executeVoid(VirtualFrame frame);
    }

    private static class BlockStatement extends StatementNode {

        @Children StatementNode[] statements;

        BlockStatement(StatementNode... statements) {
            this.statements = statements;
        }

        @Override
        @ExplodeLoop
        void executeVoid(VirtualFrame frame) {
            for (StatementNode statement : statements) {
                statement.executeVoid(frame);
            }
        }
    }

    private static class ExpressionStatement extends StatementNode {

        private @Child ExpressionNode expression;

        ExpressionStatement(ExpressionNode expression) {
            this.expression = expression;
        }

        @Override
        void executeVoid(VirtualFrame frame) {
            this.expression.execute(frame);
        }
    }

    private static class LogStatement extends StatementNode {

        private static final TruffleLogger LOG = TruffleLogger.getLogger(ProxyLanguage.ID, IteratorTest.class);

        private @Child ExpressionNode valueNode;
        private @Child InteropLibrary strings;

        LogStatement(ExpressionNode valueNode) {
            this.valueNode = valueNode;
            this.strings = InteropLibrary.getFactory().createDispatched(5);
        }

        @Override
        void executeVoid(VirtualFrame frame) {
            try {
                LOG.fine(strings.asString(valueNode.execute(frame)));
            } catch (UnsupportedMessageException ume) {
                CompilerDirectives.shouldNotReachHere(ume);
            }
        }
    }

    private static class IterateNode extends Node {

        private @Child WriteVariableNode setLocal;
        private @Child StatementNode repeat;
        private @Child InteropLibrary iterators;

        IterateNode(FrameSlot localVariable, StatementNode repeat) {
            this.setLocal = IteratorTestFactory.WriteVariableNodeGen.create(null, localVariable);
            this.repeat = repeat;
            this.iterators = InteropLibrary.getFactory().createDispatched(1);
        }

        void execute(VirtualFrame frame, Object iterator) throws UnsupportedMessageException, StopIterationException {
            if (!iterators.isIterator(iterator)) {
                throw new TypeError("iterator", this);
            }
            while (iterators.hasIteratorNextElement(iterator)) {
                try {
                    Object item = iterators.getIteratorNextElement(iterator);
                    setLocal.write(frame, item);
                    repeat.executeVoid(frame);
                } catch (UnsupportedMessageException ume) {
                    continue;
                }
            }
        }
    }

    private static class ForEachStatement extends StatementNode {

        private @Child ReadVariableNode readIterable;
        private @Child IterateNode iterate;
        private @Child InteropLibrary iterables;

        ForEachStatement(FrameSlot iterableVariable, FrameSlot localVariable, StatementNode repeat) {
            this.readIterable = IteratorTestFactory.ReadVariableNodeGen.create(iterableVariable);
            this.iterate = new IterateNode(localVariable, repeat);
            this.iterables = InteropLibrary.getFactory().createDispatched(1);
        }

        @Override
        void executeVoid(VirtualFrame frame) {
            Object iterable = readIterable.execute(frame);
            if (!iterables.hasIterator(iterable)) {
                throw new TypeError("iterable", this);
            }
            try {
                iterate.execute(frame, iterables.getIterator(iterable));
            } catch (UnsupportedMessageException | StopIterationException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    private static final class VerifyingHandler extends Handler {

        final String loggerName;
        private Queue<String> expected = new ArrayDeque<>();

        VerifyingHandler() {
            loggerName = String.format("%s.%s", ProxyLanguage.ID, IteratorTest.class.getName());
        }

        void expect(String... messages) {
            Collections.addAll(expected, messages);
        }

        @Override
        public void publish(LogRecord lr) {
            if (loggerName.equals(lr.getLoggerName())) {
                String head = expected.remove();
                assertEquals(head, lr.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            assertTrue("All expected events must be consumed. Remaining events: " + String.join(", ", expected), expected.isEmpty());
        }
    }
}
