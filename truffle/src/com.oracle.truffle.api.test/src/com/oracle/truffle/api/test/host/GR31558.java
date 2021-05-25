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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class GR31558 {
    Object[] actualArguments;

    @Test
    public void testApplyArray() {
        HostAccess.Builder builder = HostAccess.newBuilder().//
                        allowAccessAnnotatedBy(HostAccess.Export.class).//
                        allowPublicAccess(false).//
                        allowArrayAccess(true).//
                        allowIterableAccess(true).//
                        allowIteratorAccess(true).//
                        allowImplementationsAnnotatedBy(FunctionalInterface.class);
        HostAccess accessConfig = builder.build();
        Context.Builder contextBuilder = Context.newBuilder();
        contextBuilder.allowHostAccess(accessConfig);
        try (Context c = contextBuilder.build()) {
            ProxyLanguage.setDelegate(new ProxyLanguage() {
                @Override
                protected CallTarget parse(ParsingRequest request) throws Exception {
                    String src = request.getSource().getCharacters().toString();
                    RootCallTarget invokeTestApi;
                    if ("testFunction".equals(src)) {
                        invokeTestApi = Truffle.getRuntime().createCallTarget(new RootNode(ProxyLanguage.getCurrentLanguage()) {
                            @Override
                            public Object execute(VirtualFrame frame) {
                                try {
                                    InteropLibrary interop = InteropLibrary.getUncached();
                                    Object test = frame.getArguments()[0];
                                    return interop.invokeMember(test, "testFunction", "hi", new ArgumentsCollectorFunction());
                                } catch (UnsupportedMessageException | UnknownIdentifierException | ArityException | UnsupportedTypeException e) {
                                    CompilerDirectives.transferToInterpreter();
                                    throw new AssertionError(e);
                                }
                            }
                        });
                    } else {
                        throw new IllegalArgumentException(src);
                    }
                    return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(new HostExceptionTest.CatcherObject(invokeTestApi)));
                }
            });
            c.initialize(ProxyLanguage.ID);
            c.eval(ProxyLanguage.ID, "testFunction").execute(new FunctionApplyTest1());
            c.eval(ProxyLanguage.ID, "testFunction").execute(new FunctionApplyTest2());
            c.eval(ProxyLanguage.ID, "testFunction").execute(new FunctionApplyTest3());
        }
    }

    private void expectArrayArg(int elementCount, @SuppressWarnings("unused") Object ignored) {
        assertEquals(Arrays.toString(actualArguments), 1, actualArguments.length);
        Object arg0 = actualArguments[0];
        InteropLibrary interop = InteropLibrary.getUncached();
        try {
            assertTrue(interop.asString(interop.toDisplayString(arg0)), interop.hasArrayElements(arg0));
            assertEquals(interop.asString(interop.toDisplayString(arg0)), elementCount, interop.getArraySize(arg0));
        } catch (UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private void expectArgs(int argCount, @SuppressWarnings("unused") Object ignored) {
        assertEquals(Arrays.toString(actualArguments), argCount, actualArguments.length);
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

    public class FunctionApplyTest1 {
        @HostAccess.Export
        public void testFunction(String wow, Function<Object, Object> handler) {
            assertNotNull(wow);
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

    public class FunctionApplyTest2 {
        @HostAccess.Export
        public void testFunction(String wow, Function<Object[], Object> handler) {
            assertNotNull(wow);
            expectArgs(1, handler.apply(new String[]{"hi"}));
            expectArgs(2, handler.apply(new String[]{"hi", "wow"}));
        }
    }

    public class FunctionApplyTest3 {
        @HostAccess.Export
        public void testFunction(String wow, Function<int[], Object> handler) {
            assertNotNull(wow);
            expectArgs(1, handler.apply(new int[]{42}));
            expectArgs(2, handler.apply(new int[]{42, 43}));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"unused", "static-method"})
    final class ArgumentsCollectorFunction implements TruffleObject {
        ArgumentsCollectorFunction() {
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
    }
}
