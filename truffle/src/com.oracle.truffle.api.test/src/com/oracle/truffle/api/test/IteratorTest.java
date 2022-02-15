/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyIterable;
import org.graalvm.polyglot.proxy.ProxyIterator;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
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
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class IteratorTest extends AbstractPolyglotTest {

    private static final TypeLiteral<Function<Object, Object>> FUNCTION_OBJECT_OBJECT = new TypeLiteral<>() {
    };

    private static final TypeLiteral<Iterable<Value>> ITERABLE_VALUE = new TypeLiteral<>() {
    };

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

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
        for (int i = 0; i < items.length; i++) {
            items[i] = Integer.toString(i);
            if (i < expected.length) {
                expected[i] = items[i];
            }
        }
        setupEnv(createContext(verifyingHandler), new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return createAST(languageInstance, new Array(items, expected.length, Array.UNLIMITED));
            }
        });
        verifyingHandler.expect(expected);
        assertFails(() -> context.eval(ProxyLanguage.ID, "Test"), PolyglotException.class,
                        (pe) -> {
                            assertEquals(NonReadableElementError.MESSAGE, pe.getMessage());
                            assertTrue(pe.isGuestException());
                            assertFalse(pe.isInternalError());
                        });
    }

    @Test
    public void testValues() {
        String[] values = {"a", "b"};
        setupEnv(Context.create());
        Value iterable = context.asValue(new SimpleIterable((Object[]) values));
        verifyIterable(iterable, values, false);
    }

    @Test
    public void testValuesWithUnreadableElements() {
        Object[] items = new String[10];
        Object[] expected = new String[items.length / 2];
        for (int i = 0; i < items.length; i++) {
            items[i] = Integer.toString(i);
            if (i < expected.length) {
                expected[i] = items[i];
            }
        }
        setupEnv(Context.create());
        Value iterable = context.asValue(new Array(items, expected.length, Array.UNLIMITED));
        verifyIterable(iterable, expected, true);
        iterable = context.asValue(new ExecutableProxyIterableImpl(items, expected.length));
        verifyIterable(iterable, expected, true);
    }

    @Test
    public void testHostObjectArray() {
        Object[] values = {"a", "b"};
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value array = context.asValue(values);
        verifyIterable(array, values, false);
    }

    @Test
    public void testHostObjectList() {
        Object[] values = {"a", "b"};
        List<Object> valuesList = Arrays.asList(values);
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value list = context.asValue(new ArrayList<>(valuesList));
        verifyIterable(list, values, false);
    }

    @Test
    public void testHostObjectIterable() {
        Object[] values = {"a", "b"};
        List<Object> valuesList = Arrays.asList(values);
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterable = context.asValue(new Iterable<>() {

            private final List<Object> elements = new ArrayList<>(valuesList);

            @Override
            public Iterator<Object> iterator() {
                return elements.iterator();
            }
        });
        verifyIterable(iterable, values, false);
    }

    @Test
    public void testHostObjectIterator() {
        Object[] values = {"a", "b"};
        List<Object> valuesList = Arrays.asList(values);
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterator = context.asValue(valuesList.iterator());
        verifyIterator(iterator, values, false);
        iterator = context.asValue(valuesList.iterator());
        verifyIterator(iterator.as(Iterator.class), values, false);
    }

    @Test
    public void testProxyArray() {
        Object[] values = {"a", "b"};
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value array = context.asValue(ProxyArray.fromArray(values));
        verifyIterable(array, values, false);
    }

    @Test
    public void testProxyList() {
        Object[] values = {"a", "b"};
        List<Object> valuesList = new ArrayList<>();
        Collections.addAll(valuesList, values);
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value array = context.asValue(ProxyArray.fromList(valuesList));
        verifyIterable(array, values, false);
    }

    @Test
    public void testProxyIterable() {
        Object[] values = {"a", "b"};
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterable = context.asValue(ProxyIterable.from(Arrays.asList(values)));
        verifyIterable(iterable, values, false);
        iterable = context.asValue(new ProxyIterable() {
            @Override
            public Object getIterator() {
                return Arrays.asList(values).iterator();
            }
        });
        verifyIterable(iterable, values, false);
        iterable = context.asValue(new ProxyIterable() {
            @Override
            public Object getIterator() {
                return ProxyIterator.from(Arrays.asList(values).iterator());
            }
        });
        verifyIterable(iterable, values, false);
        iterable = context.asValue(new ProxyIterable() {
            @Override
            public Object getIterator() {
                return new SimpleIterator(values);
            }
        });
        verifyIterable(iterable, values, false);
        Value invalidIterable = context.asValue(new ProxyIterable() {
            @Override
            public Object getIterator() {
                return ProxyObject.fromMap(Collections.emptyMap());
            }
        });
        assertFails(() -> invalidIterable.getIterator(), PolyglotException.class,
                        (pe) -> assertTrue(pe.asHostException() instanceof IllegalStateException));
    }

    @Test
    public void testProxyIterableList() {
        String[] values = {"a", "b"};
        List<Object> valuesList = new ArrayList<>();
        Collections.addAll(valuesList, values);
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterable = context.asValue(ProxyIterable.from(valuesList));
        verifyIterable(iterable, values, false);
    }

    @Test
    public void testExecutableProxyIterable() {
        String[] values = {"a", "b"};
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterable = context.asValue(new ExecutableProxyIterableImpl(values));
        verifyIterable(iterable, values, false);
        assertTrue(iterable.canExecute());
        assertEquals(42, iterable.execute(42).asInt());
        assertEquals(42, iterable.as(FUNCTION_OBJECT_OBJECT).apply(42));

        verifyIterator(iterable.as(ITERABLE_VALUE).iterator(), Arrays.stream(values).map(context::asValue).toArray(), false);
    }

    @Test
    public void testProxyIterableIteratorHasMembers() {
        String[] values = {"a", "b"};
        List<Object> valuesList = new ArrayList<>();
        Collections.addAll(valuesList, values);
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterable = context.asValue((ProxyIterable) () -> new ProxyIteratorWithMembersImpl(valuesList.iterator()));
        verifyIterable(iterable, values, false);

        verifyIterator(iterable.as(ITERABLE_VALUE).iterator(), Arrays.stream(values).map(context::asValue).toArray(), false);
    }

    @Test
    public void testIterator() {
        String[] values = {"a", "b", "c", "d"};
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterator = context.asValue(new GuestLanguageIteratorImpl(values));
        verifyIterator(iterator, values, false);
        values = new String[0];
        iterator = context.asValue(new GuestLanguageIteratorImpl(values));
        verifyIterator(iterator, values, false);
    }

    @Test
    public void testProxyIterator() {
        Object[] values = {"a", "b"};
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterator = context.asValue(ProxyIterator.from(Arrays.asList(values).iterator()));
        verifyIterator(iterator, values, false);
        iterator = context.asValue(ProxyIterator.from(Arrays.asList(values).iterator()));
        verifyIterator(iterator.as(Iterator.class), values, false);
    }

    @Test
    public void testExecutableProxyIterator() {
        Object[] values = {"a", "b"};
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Value iterator = context.asValue(new ExecutableProxyIteratorImpl(values));
        verifyIterator(iterator, values, false);
        assertTrue(iterator.canExecute());
        assertEquals(42, iterator.execute(42).asInt());
        iterator = context.asValue(new ExecutableProxyIteratorImpl(values));
        verifyIterator(iterator.as(Iterator.class), values, false);
        assertEquals(42, iterator.as(FUNCTION_OBJECT_OBJECT).apply(42));
    }

    @Test
    public void testConcurrentModifications() {
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Object[] values = {"1", "2", "3"};
        Array array = new Array(values, Array.UNLIMITED, 1);
        Value iterable = context.asValue(array);
        Value iterator = iterable.getIterator();
        assertTrue(iterator.hasIteratorNextElement());
        assertEquals(values[0], iterator.getIteratorNextElement().asString());
        assertFalse(iterator.hasIteratorNextElement());
        array.setLimit(Array.UNLIMITED);
        assertTrue(iterator.hasIteratorNextElement());
        assertEquals(values[1], iterator.getIteratorNextElement().asString());
        assertTrue(iterator.hasIteratorNextElement());
        assertEquals(values[2], iterator.getIteratorNextElement().asString());
        assertFalse(iterator.hasIteratorNextElement());

        array.setLimit(1);
        Iterator<?> javaIterator = iterable.getIterator().as(Iterator.class);
        assertTrue(javaIterator.hasNext());
        assertEquals(values[0], javaIterator.next());
        assertFalse(javaIterator.hasNext());
        array.setLimit(Array.UNLIMITED);
        assertFails(() -> javaIterator.next(), ConcurrentModificationException.class);
        assertFalse(javaIterator.hasNext());
        assertFails(() -> javaIterator.next(), ConcurrentModificationException.class);
        assertFalse(javaIterator.hasNext());

        array.setLimit(1);
        Iterator<?> javaIterator2 = iterable.getIterator().as(Iterator.class);
        assertTrue(javaIterator2.hasNext());
        assertEquals(values[0], javaIterator2.next());
        array.setLimit(Array.UNLIMITED);
        assertTrue(javaIterator2.hasNext());
        assertEquals(values[1], javaIterator2.next());
        assertTrue(javaIterator2.hasNext());
        assertEquals(values[2], javaIterator2.next());
        assertFails(() -> javaIterator2.next(), NoSuchElementException.class);
        assertFalse(javaIterator2.hasNext());
        assertFails(() -> javaIterator2.next(), NoSuchElementException.class);

        array.setLimit(Array.UNLIMITED);
        Iterator<?> javaIterator3 = iterable.getIterator().as(Iterator.class);
        assertTrue(javaIterator3.hasNext());
        assertEquals(values[0], javaIterator3.next());
        array.setLimit(1);
        assertFalse(javaIterator3.hasNext());
        assertFails(() -> javaIterator3.next(), NoSuchElementException.class);

        array.setLimit(Array.UNLIMITED);
        Iterator<?> javaIterator4 = iterable.getIterator().as(Iterator.class);
        assertTrue(javaIterator4.hasNext());
        assertEquals(values[0], javaIterator4.next());
        assertTrue(javaIterator4.hasNext());
        array.setLimit(1);
        assertFails(() -> javaIterator4.next(), ConcurrentModificationException.class);
        assertFalse(javaIterator4.hasNext());
        assertFails(() -> javaIterator4.next(), ConcurrentModificationException.class);

        List<Object> javaList = new ArrayList<>(Arrays.asList("1", "2", "3"));
        iterable = context.asValue(javaList);
        Value iterator2 = iterable.getIterator();
        javaList.add("4");
        assertFails(() -> iterator2.getIteratorNextElement(), PolyglotException.class);
        Iterator<?> javaIterator5 = iterable.getIterator().as(Iterator.class);
        javaList.add("5");
        assertFails(() -> javaIterator5.next(), ConcurrentModificationException.class);
        iterable = context.asValue(ProxyArray.fromList(javaList));
        Value iterator3 = iterable.getIterator();
        javaList.add("6");
        assertFails(() -> iterator3.getIteratorNextElement(), PolyglotException.class);
        Iterator<?> javaIterator6 = iterable.getIterator().as(Iterator.class);
        javaList.add("7");
        assertFails(() -> javaIterator6.next(), PolyglotException.class);
    }

    private static void verifyIterable(Value iterable, Object[] values, boolean endsWithUnreadableElement) {
        assertTrue(iterable.hasIterator());
        assertFalse(iterable.isIterator());
        verifyIterator(iterable.getIterator(), values, endsWithUnreadableElement);
        verifyIterator(iterable.getIterator().as(Iterator.class), values, endsWithUnreadableElement);
        verifyIterator(iterable.as(Iterable.class).iterator(), values, endsWithUnreadableElement);
    }

    private static void verifyIterator(Value iterator, Object[] values, boolean endsWithUnreadableElement) {
        assertFalse(iterator.hasIterator());
        assertTrue(iterator.isIterator());
        for (int i = 0; i < values.length; i++) {
            assertTrue(iterator.hasIteratorNextElement());
            assertTrue(iterator.hasIteratorNextElement());
            try {
                Value element = iterator.getIteratorNextElement();
                assertNotNull("Iterator should not have an element.", values[i]);
                assertEquals(values[i], element.asString());
            } catch (UnsupportedOperationException uoe) {
                assertNull("Iterator should have an element.", values[i]);
            }
        }
        if (endsWithUnreadableElement) {
            assertTrue(iterator.hasIteratorNextElement());
            assertFails(() -> iterator.getIteratorNextElement(), UnsupportedOperationException.class);
        } else {
            assertFalse(iterator.hasIteratorNextElement());
            assertFails(() -> iterator.getIteratorNextElement(), NoSuchElementException.class);
        }
    }

    private static void verifyIterator(Iterator<?> iterator, Object[] values, boolean endsWithUnreadableElement) {
        for (int i = 0; i < values.length; i++) {
            assertTrue(iterator.hasNext());
            assertTrue(iterator.hasNext());
            try {
                Object element = iterator.next();
                assertNotNull("Iterator should not have an element.", values[i]);
                assertEquals(values[i], element);
            } catch (UnsupportedOperationException uoe) {
                assertNull("Iterator should have an element.", values[i]);
            }
        }
        if (endsWithUnreadableElement) {
            assertTrue(iterator.hasNext());
            assertFails(() -> iterator.next(), UnsupportedOperationException.class);
        } else {
            assertFalse(iterator.hasNext());
            assertFails(() -> iterator.next(), NoSuchElementException.class);
        }
    }

    private static CallTarget createAST(TruffleLanguage<?> lang, Object iterable) {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int iterableSlot = builder.addSlot(FrameSlotKind.Object, "iterable", null);
        int itemSlot = builder.addSlot(FrameSlotKind.Object, "item", null);
        FrameDescriptor fd = builder.build();
        StatementNode log = new LogStatement(IteratorTestFactory.ReadVariableNodeGen.create(itemSlot));
        StatementNode main = new BlockStatement(
                        new ExpressionStatement(IteratorTestFactory.WriteVariableNodeGen.create(new ConstantNode(iterable), iterableSlot)),
                        new ForEachStatement(iterableSlot, itemSlot, log));
        return new TestRootNode(lang, fd, main).getCallTarget();
    }

    private static Context createContext(VerifyingHandler handler) {
        return Context.newBuilder().option(String.format("log.%s.level", handler.loggerName), "FINE").logHandler(handler).build();
    }

    @ExportLibrary(InteropLibrary.class)
    static class SimpleIterable implements TruffleObject {

        private final Object[] items;

        SimpleIterable(Object... items) {
            this.items = items;
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

        private final Object[] items;
        private int index;

        SimpleIterator(Object[] items) {
            this.items = items;
            this.index = 0;
        }

        @ExportMessage
        boolean isIterator() {
            return true;
        }

        @ExportMessage
        boolean hasIteratorNextElement() {
            return index < items.length;
        }

        @ExportMessage
        Object getIteratorNextElement() throws StopIterationException {
            if (!hasIteratorNextElement()) {
                throw StopIterationException.create();
            }
            return items[index++];
        }
    }

    private static final class ExecutableProxyIterableImpl implements ProxyIterable, ProxyExecutable {

        static final int UNLIMITED = -1;

        private final Object[] storage;
        private final int unreadableElementIndex;

        ExecutableProxyIterableImpl(Object[] data) {
            this(data, UNLIMITED);
        }

        ExecutableProxyIterableImpl(Object[] data, int unreadableElementIndex) {
            this.storage = data;
            this.unreadableElementIndex = unreadableElementIndex;
        }

        @Override
        public Object getIterator() {
            return new ExecutableProxyIteratorImpl(storage, unreadableElementIndex);
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

        private final Object[] storage;
        private final int unreadableElementIndex;
        private int index;

        ExecutableProxyIteratorImpl(Object[] data) {
            this(data, ExecutableProxyIterableImpl.UNLIMITED);
        }

        ExecutableProxyIteratorImpl(Object[] data, int unreadableElementIndex) {
            this.storage = data;
            this.unreadableElementIndex = unreadableElementIndex;
            this.index = 0;
        }

        @Override
        public boolean hasNext() {
            return index < storage.length;
        }

        @Override
        public Object getNext() {
            if (index >= storage.length) {
                throw new NoSuchElementException();
            }
            if (index == unreadableElementIndex) {
                throw new UnsupportedOperationException();
            } else {
                return storage[index++];
            }
        }

        @Override
        public Object execute(Value... arguments) {
            if (arguments.length != 1) {
                throw new UnsupportedOperationException();
            }
            return arguments[0];
        }
    }

    private static final class ProxyIteratorWithMembersImpl implements ProxyIterator, ProxyObject {
        private Iterator<Object> iterator;

        ProxyIteratorWithMembersImpl(Iterator<Object> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Object getNext() throws NoSuchElementException, UnsupportedOperationException {
            return iterator.next();
        }

        @Override
        public Object getMemberKeys() {
            return ProxyArray.fromArray("hasNext", "next");
        }

        @Override
        public Object getMember(String key) {
            switch (key) {
                case "hasNext":
                    return (ProxyExecutable) (a) -> hasNext();
                case "next":
                    return (ProxyExecutable) (a) -> getNext();
                default:
                    return null;
            }
        }

        @Override
        public boolean hasMember(String key) {
            switch (key) {
                case "hasNext":
                    return true;
                case "next":
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    abstract static class InteropIterator implements TruffleObject {

        @SuppressWarnings("serial")
        public static final class Stop extends AbstractTruffleException {
        }

        private static final Object STOP = new Object();
        private Object next;

        protected InteropIterator() {
        }

        protected abstract Object next() throws Stop;

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isIterator() {
            return true;
        }

        @ExportMessage
        boolean hasIteratorNextElement() {
            fetchNext();
            return next != STOP;
        }

        @ExportMessage
        Object getIteratorNextElement() throws StopIterationException {
            fetchNext();
            Object res = next;
            if (res == STOP) {
                throw StopIterationException.create();
            } else {
                next = null;
            }
            return res;
        }

        private void fetchNext() {
            if (next == null) {
                try {
                    next = next();
                } catch (Stop stop) {
                    next = STOP;
                }
            }
        }
    }

    private static final class GuestLanguageIteratorImpl extends InteropIterator {

        private final Object[] values;
        private int index;

        GuestLanguageIteratorImpl(Object[] values) {
            this.values = values;
        }

        @Override
        public Object next() throws Stop {
            if (index < values.length) {
                return values[index++];
            } else {
                throw new Stop();
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class Array implements TruffleObject {

        static final int UNLIMITED = -1;

        private final Object[] items;
        private final int unreadableElementIndex;
        @CompilationFinal private int limit;
        @CompilationFinal private Assumption limitValid;

        Array(Object[] items) {
            this(items, UNLIMITED, UNLIMITED);
        }

        Array(Object[] items, int unreadableElementIndex, int limit) {
            this.items = items;
            this.unreadableElementIndex = unreadableElementIndex;
            this.limit = limit;
            this.limitValid = Truffle.getRuntime().createAssumption();
        }

        void setLimit(int newLimit) {
            if (newLimit < UNLIMITED || newLimit > items.length) {
                throw new IllegalArgumentException(String.valueOf(newLimit));
            }
            this.limit = newLimit;
            Assumption oldAssumption = limitValid;
            limitValid = Truffle.getRuntime().createAssumption();
            oldAssumption.invalidate();
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            if (!limitValid.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            return limit == UNLIMITED ? items.length : limit;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < getArraySize() && unreadableElementIndex != index;
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

        @TruffleBoundary
        TypeError(String expected, Node location) {
            super("Type error, expected: " + expected, location);
        }
    }

    @SuppressWarnings("serial")
    static final class NonReadableElementError extends AbstractTruffleException {

        static final String MESSAGE = "Cannot read iterator next element.";

        NonReadableElementError(Node location) {
            super(MESSAGE, location);
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

    @NodeField(name = "slot", type = int.class)
    abstract static class ReadVariableNode extends ExpressionNode {

        protected abstract int getSlot();

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
    @NodeField(name = "slot", type = int.class)
    abstract static class WriteVariableNode extends ExpressionNode {

        protected abstract int getSlot();

        @Specialization
        protected Object write(VirtualFrame frame, Object value) {
            frame.getFrameDescriptor().setSlotKind(getSlot(), FrameSlotKind.Object);
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

        IterateNode(int localVariable, StatementNode repeat) {
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
                } catch (UnsupportedMessageException e) {
                    throw new NonReadableElementError(this);
                }
            }
        }
    }

    private static class ForEachStatement extends StatementNode {

        private @Child ReadVariableNode readIterable;
        private @Child IterateNode iterate;
        private @Child InteropLibrary iterables;

        ForEachStatement(int iterableVariable, int localVariable, StatementNode repeat) {
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
