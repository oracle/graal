/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class GR31558 extends AbstractPolyglotTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    Object[] actualArguments;

    @Before
    public void before() {
        HostAccess.Builder hostAccessBuilder = HostAccess.newBuilder().//
                        allowAccessAnnotatedBy(HostAccess.Export.class).//
                        allowPublicAccess(false).//
                        allowArrayAccess(true).//
                        allowIterableAccess(true).//
                        allowIteratorAccess(true).//
                        allowImplementationsAnnotatedBy(FunctionalInterface.class);
        Context.Builder contextBuilder = Context.newBuilder();
        contextBuilder.allowHostAccess(hostAccessBuilder.build());

        setupEnv(contextBuilder, new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                String src = request.getSource().getCharacters().toString();
                RootCallTarget invokeTestApi;
                if ("testFunction".equals(src)) {
                    invokeTestApi = new RootNode(ProxyLanguage.get(null)) {
                        @Override
                        public Object execute(VirtualFrame frame) {
                            try {
                                InteropLibrary interop = InteropLibrary.getUncached();
                                Object test = frame.getArguments()[0];

                                interop.invokeMember(test, "testFunction", "hi", new ArgumentsCollectorFunction());

                                if (interop.isMemberExisting(test, "testMap")) {
                                    interop.invokeMember(test, "testFunction", "hi", new ArgumentsCollectorFunction(false, false, false, true));
                                }
                                if (interop.isMemberExisting(test, "testMapEntry")) {
                                    interop.invokeMember(test, "testMapEntry", "hi", new ArgumentsCollectorFunction(false, false, true, false));
                                }
                                if (interop.isMemberExisting(test, "testList")) {
                                    interop.invokeMember(test, "testList", "hi", new ArgumentsCollectorFunction(false, false, true, false));
                                }
                                if (interop.isMemberExisting(test, "testIterator")) {
                                    interop.invokeMember(test, "testIterator", "hi", new ArgumentsCollectorFunction(false, true, false, false));
                                }
                                if (interop.isMemberExisting(test, "testIterable")) {
                                    interop.invokeMember(test, "testIterable", "hi", new ArgumentsCollectorFunction(true, false, false, false));
                                }

                                return "success";
                            } catch (UnsupportedMessageException | UnknownIdentifierException | ArityException | UnsupportedTypeException e) {
                                CompilerDirectives.transferToInterpreter();
                                throw new AssertionError(e);
                            }
                        }
                    }.getCallTarget();
                } else {
                    throw new IllegalArgumentException(src);
                }
                return RootNode.createConstantNode(new HostExceptionTest.CatcherObject(invokeTestApi)).getCallTarget();
            }
        });
    }

    @Test
    public void testApplyArray() {
        context.eval(ProxyLanguage.ID, "testFunction").execute(new FunctionApplyTestObj());
        context.eval(ProxyLanguage.ID, "testFunction").execute(new FunctionApplyTestObjArray());
        context.eval(ProxyLanguage.ID, "testFunction").execute(new FunctionApplyTestIntArray());
        context.eval(ProxyLanguage.ID, "testFunction").execute(new FunctionApplyTestRaw());
    }

    @Test
    public void testValueAsFunction() {
        new FunctionApplyTestRaw().testFunction("hi", context.asValue(new ArgumentsCollectorFunction()).as(Function.class));
        new FunctionApplyTestObj().testFunction("hi", context.asValue(new ArgumentsCollectorFunction()).as(new TypeLiteral<Function<Object, Object>>() {
        }));
        new FunctionApplyTestObjArray().testFunction("hi", context.asValue(new ArgumentsCollectorFunction()).as(new TypeLiteral<Function<Object[], Object>>() {
        }));
        new FunctionApplyTestIntArray().testFunction("hi", context.asValue(new ArgumentsCollectorFunction()).as(new TypeLiteral<Function<int[], Object>>() {
        }));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void testValueAsCustomFunction() {
        new FunctionApplyTestObj().testFunction("hi", context.asValue(new ArgumentsCollectorFunction()).as(MyFunction.class));
        new FunctionApplyTestObj().testFunction("hi", context.asValue(new ArgumentsCollectorFunction()).as(new TypeLiteral<MyFunction<Object, Object>>() {
        }));

        Function<Object[], Object> handler = context.asValue(new ArgumentsCollectorFunction()).as(new TypeLiteral<MyFunction<Object[], Object>>() {
        });
        expectArrayArg(1, handler.apply(new String[]{"hi"}));
        expectArrayArg(2, handler.apply(new String[]{"hi", "wow"}));

        Function<int[], Object> handlerInt = context.asValue(new ArgumentsCollectorFunction()).as(new TypeLiteral<MyFunction<int[], Object>>() {
        });
        expectArrayArg(1, handlerInt.apply(new int[]{42}));
        expectArrayArg(2, handlerInt.apply(new int[]{42, 43}));
    }

    @FunctionalInterface
    interface MyFunction<T, R> extends Function<T, R> {
        R apply(T t);
    }

    private void expectArrayArg(int elementCount, Object result) {
        assertEquals(Arrays.toString(actualArguments), 1, actualArguments.length);
        assertEquals("EXECUTE", result);
        Object arg0 = actualArguments[0];
        InteropLibrary interop = InteropLibrary.getUncached();
        try {
            assertTrue(interop.asString(interop.toDisplayString(arg0)), interop.hasArrayElements(arg0));
            assertEquals(interop.asString(interop.toDisplayString(arg0)), elementCount, interop.getArraySize(arg0));
        } catch (UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private void expectArgs(int argCount, Object result) {
        assertEquals(Arrays.toString(actualArguments), argCount, actualArguments.length);
        assertEquals("EXECUTE", result);
        if (argCount == 0) {
            return;
        }
        Object arg0 = actualArguments[0];
        InteropLibrary interop = InteropLibrary.getUncached();
        try {
            assertFalse(interop.asString(interop.toDisplayString(arg0)), interop.hasArrayElements(arg0));
        } catch (UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    public class FunctionApplyTestObj {
        @HostAccess.Export
        public void testFunction(String wow, Function<Object, Object> handler) {
            assertEquals("hi", wow);
            expectArrayArg(1, handler.apply(new String[]{"hi"}));
            expectArrayArg(2, handler.apply(new String[]{"hi", "wow"}));
            expectArrayArg(1, handler.apply(Value.asValue(new String[]{"hi as value"})));
            expectArrayArg(3, handler.apply(Value.asValue(new String[]{"hi", "wow", "as value"})));
            expectArrayArg(1, handler.apply(Value.asValue(new String[]{"hi as value with as"}).as(String[].class)));
            expectArrayArg(3, handler.apply(Value.asValue(new String[]{"hi", "wow", "as value with as"}).as(String[].class)));
            expectArrayArg(1, handler.apply(new int[]{42}));
            expectArrayArg(2, handler.apply(new int[]{42, 43}));
        }

    }

    public class FunctionApplyTestObjArray {
        @HostAccess.Export
        public void testFunction(String wow, Function<Object[], Object> handler) {
            assertEquals("hi", wow);
            expectArgs(1, handler.apply(new String[]{"hi"}));
            expectArgs(2, handler.apply(new String[]{"hi", "wow"}));
        }
    }

    public class FunctionApplyTestIntArray {
        @HostAccess.Export
        public void testFunction(String wow, Function<int[], Object> handler) {
            assertEquals("hi", wow);
            expectArgs(1, handler.apply(new int[]{42}));
            expectArgs(2, handler.apply(new int[]{42, 43}));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public class FunctionApplyTestRaw {
        @HostAccess.Export
        public void testFunction(String wow, Function handler) {
            assertEquals("hi", wow);
            expectArgs(1, handler.apply(new String[]{"hi"}));
            expectArgs(2, handler.apply(new String[]{"hi", "wow"}));
        }

        @HostAccess.Export
        public void testMap(String wow, Map<Object, Object> handler) {
            testFunction(wow, (Function<Object, Object>) handler);
        }

        @HostAccess.Export
        public void testMapEntry(String wow, Map.Entry<Object, Object> handler) {
            testFunction(wow, (Function<Object, Object>) handler);
        }

        @HostAccess.Export
        public void testList(String wow, List<Object> handler) {
            testFunction(wow, (Function<Object, Object>) handler);
        }

        @HostAccess.Export
        public void testIterator(String wow, Iterator<Object> handler) {
            testFunction(wow, (Function<Object, Object>) handler);
        }

        @HostAccess.Export
        public void testIterable(String wow, Iterable<Object> handler) {
            testFunction(wow, (Function<Object, Object>) handler);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"unused", "static-method"})
    final class ArgumentsCollectorFunction implements TruffleObject {
        final boolean hasIterator;
        final boolean isIterator;
        final boolean hasArrayElements;
        final boolean hasHashEntries;

        ArgumentsCollectorFunction(boolean isIterable, boolean isIterator, boolean isList, boolean isMap) {
            this.hasIterator = isIterable;
            this.isIterator = isIterator;
            this.hasArrayElements = isList;
            this.hasHashEntries = isMap;
        }

        ArgumentsCollectorFunction() {
            this(false, false, false, false);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments) {
            actualArguments = arguments;
            return "EXECUTE";
        }

        @ExportMessage
        boolean hasIterator() {
            return hasIterator;
        }

        @ExportMessage
        boolean isIterator() {
            return isIterator;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return hasArrayElements;
        }

        @ExportMessage
        boolean hasHashEntries() {
            return hasHashEntries;
        }

        @ExportMessage
        long getHashSize() throws UnsupportedMessageException {
            if (!hasHashEntries()) {
                throw UnsupportedMessageException.create();
            }
            return 0L;
        }

        @ExportMessage
        Object getHashEntriesIterator() throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        Object readArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            if (!hasArrayElements()) {
                throw UnsupportedMessageException.create();
            }
            throw InvalidArrayIndexException.create(index);
        }

        @ExportMessage
        long getArraySize() throws UnsupportedMessageException {
            if (!hasArrayElements()) {
                throw UnsupportedMessageException.create();
            }
            return 0L;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return false;
        }

        @ExportMessage
        Object getIterator() throws UnsupportedMessageException {
            if (!hasIterator()) {
                throw UnsupportedMessageException.create();
            }
            return null;
        }

        @ExportMessage
        boolean hasIteratorNextElement() throws UnsupportedMessageException {
            if (!isIterator()) {
                throw UnsupportedMessageException.create();
            }
            return false;
        }

        @ExportMessage
        Object getIteratorNextElement() throws UnsupportedMessageException, StopIterationException {
            if (!isIterator()) {
                throw UnsupportedMessageException.create();
            }
            throw StopIterationException.create();
        }
    }
}
