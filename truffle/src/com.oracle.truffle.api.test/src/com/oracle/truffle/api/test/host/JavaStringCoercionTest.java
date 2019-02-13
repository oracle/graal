/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class JavaStringCoercionTest {
    private Context context;
    private Env env;

    @Before
    public void before() {
        context = Context.newBuilder().allowAllAccess(true).build();
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected LanguageContext createContext(Env contextEnv) {
                env = contextEnv;
                return super.createContext(contextEnv);
            }

            @Override
            protected boolean isObjectOfLanguage(Object object) {
                if (object instanceof UnboxableArrayObject) {
                    return true;
                }
                return super.isObjectOfLanguage(object);
            }

            @Override
            protected String toString(LanguageContext c, Object value) {
                if (value instanceof UnboxableArrayObject) {
                    return "UnboxableArray";
                }
                return super.toString(c, value);
            }
        });
        context.initialize(ProxyLanguage.ID);
        context.enter();
        assertNotNull(env);
    }

    @After
    public void after() {
        context.leave();
        context.close();
    }

    public static class StringConsumer1 {
        public Object call(String arg) {
            return arg;
        }
    }

    public static class StringConsumer2 {
        public Object call(List<?> arg) {
            return arg.toString() + "(" + arg.size() + "):" + new ArrayList<>(arg).toString();
        }

        public Object call(String arg) {
            return arg;
        }
    }

    @Test
    public void testStringCoercionSingleMethod() throws InteropException {
        TruffleObject api = (TruffleObject) env.asGuestValue(new StringConsumer1());
        testStringCoercion(api);
    }

    @Test
    public void testStringCoercionOverloadedMethod() throws InteropException {
        TruffleObject api = (TruffleObject) env.asGuestValue(new StringConsumer2());
        testStringCoercion(api);
    }

    @Test
    public void testPreferWrappingToStringCoercion() throws InteropException {
        Node invoke = Message.INVOKE.createNode();
        TruffleObject api = (TruffleObject) env.asGuestValue(new StringConsumer2());
        Object list = call(invoke, api, new UnboxableArrayObject(4));
        assertEquals("UnboxableArray(4):[0, 1, 2, 3]", list);
    }

    private static void testStringCoercion(TruffleObject api) throws InteropException {
        Node invoke = Message.INVOKE.createNode();
        assertEquals("ok", call(invoke, api, "ok"));
        assertEquals("42", call(invoke, api, 42));
        assertEquals("true", call(invoke, api, true));
        assertEquals("-128", call(invoke, api, Byte.MIN_VALUE));
        assertEquals("-32768", call(invoke, api, Short.MIN_VALUE));
        assertEquals("9223372036854775807", call(invoke, api, Long.MAX_VALUE));
        assertEquals("3.14", call(invoke, api, 3.14));
        assertEquals("3.14", call(invoke, api, 3.14f));
        assertEquals("NaN", call(invoke, api, Double.NaN));
        assertEquals("Infinity", call(invoke, api, Double.POSITIVE_INFINITY));
        assertEquals("-Infinity", call(invoke, api, Double.NEGATIVE_INFINITY));
        assertEquals("-0.0", call(invoke, api, -0.0));
        assertEquals("\uffff", call(invoke, api, Character.MAX_VALUE));

        assertEquals("42", call(invoke, api, new UnboxableToInt(42)));

        callUnsupported(invoke, api, new NotCoercibleObject());
    }

    private static Object call(Node invoke, TruffleObject obj, Object value) throws InteropException {
        try {
            return ForeignAccess.sendInvoke(invoke, obj, "call", value);
        } catch (UnsupportedTypeException e) {
            throw new AssertionError("String coercion failed for: " + value + " (" + (value == null ? null : value.getClass().getName()) + ")", e);
        }
    }

    private static void callUnsupported(Node invoke, TruffleObject obj, Object value) throws InteropException {
        try {
            ForeignAccess.sendInvoke(invoke, obj, "call", value);
            fail("Expected coercion to fail");
        } catch (UnsupportedTypeException e) {
        }
    }

    public static class IntConsumer {
        public Object call(int arg) {
            return arg;
        }
    }

    public static class IntegerConsumer {
        public Object call(Integer arg) {
            return arg;
        }
    }

    public static class PrimitiveConsumer {
        public Object call(int arg) {
            return arg;
        }

        public Object call(long arg) {
            return arg;
        }

        public Object call(double arg) {
            return arg;
        }

        public Object call(boolean arg) {
            return arg;
        }
    }

    public static class BoxedPrimitiveConsumer {
        public Object call(Integer arg) {
            return arg;
        }

        public Object call(Long arg) {
            return arg;
        }

        public Object call(Double arg) {
            return arg;
        }

        public Object call(Boolean arg) {
            return arg;
        }
    }

    public static class ObjectOrIntConsumer {
        public Object call(int arg) {
            return arg;
        }

        public Object call(Integer arg) {
            return arg;
        }

        public Object call(Object arg) {
            return arg;
        }
    }

    @Test
    public void testStringToPrimitiveSingleMethod() throws InteropException {
        for (Object consumer : new Object[]{new IntConsumer(), new IntegerConsumer()}) {
            Node invoke = Message.INVOKE.createNode();
            TruffleObject api = (TruffleObject) env.asGuestValue(consumer);
            assertEquals(42, call(invoke, api, "42"));
            assertEquals(42, call(invoke, api, "+42"));
            assertEquals(-42, call(invoke, api, "-42"));

            callUnsupported(invoke, api, "42garbage");
            callUnsupported(invoke, api, "2147483648");
            callUnsupported(invoke, api, "42.0");
            callUnsupported(invoke, api, " 42");
            callUnsupported(invoke, api, "42 ");
        }
    }

    @Test
    public void testStringToPrimitiveOverloadedMethod() throws InteropException {
        for (Object consumer : new Object[]{new PrimitiveConsumer(), new BoxedPrimitiveConsumer()}) {
            Node invoke = Message.INVOKE.createNode();
            TruffleObject api = (TruffleObject) env.asGuestValue(consumer);
            assertEquals(2147483648L, call(invoke, api, "2147483648"));
            assertEquals(42, call(invoke, api, "42"));
            assertEquals(42, call(invoke, api, "+42"));
            assertEquals(-42, call(invoke, api, "-42"));
            assertEquals(4.2, call(invoke, api, "4.2"));
            assertEquals(true, call(invoke, api, "true"));
            assertEquals(false, call(invoke, api, "false"));
            assertEquals(42.0, call(invoke, api, "42.0"));

            invoke = Message.INVOKE.createNode();
            assertEquals(42, call(invoke, api, "42"));
            assertEquals(true, call(invoke, api, "true"));
            invoke = Message.INVOKE.createNode();
            assertEquals(false, call(invoke, api, "false"));
            assertEquals(42, call(invoke, api, "42"));

            callUnsupported(invoke, api, "42garbage");
            callUnsupported(invoke, api, "0x42");
            callUnsupported(invoke, api, "True");
            callUnsupported(invoke, api, " 42");
            callUnsupported(invoke, api, "42 ");
        }
    }

    @Test
    public void testStringToPrimitiveLowPriority() throws InteropException {
        Node invoke = Message.INVOKE.createNode();
        TruffleObject api = (TruffleObject) env.asGuestValue(new ObjectOrIntConsumer());
        // String to int conversion would be possible, but Object overload has higher priority.
        assertEquals("42", call(invoke, api, "42"));
    }

    @MessageResolution(receiverType = NotCoercibleObject.class)
    static final class NotCoercibleObject implements TruffleObject {
        @Override
        public ForeignAccess getForeignAccess() {
            return NotCoercibleObjectForeign.ACCESS;
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof NotCoercibleObject;
        }
    }

    @MessageResolution(receiverType = UnboxableArrayObject.class)
    static final class UnboxableArrayObject implements TruffleObject {
        final int size;

        UnboxableArrayObject(int size) {
            this.size = size;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return UnboxableArrayObjectForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof UnboxableArrayObject;
        }

        @Resolve(message = "GET_SIZE")
        abstract static class ArrayGetSizeNode extends Node {
            Object access(UnboxableArrayObject obj) {
                return obj.size;
            }
        }

        @Resolve(message = "READ")
        abstract static class ArrayReadSizeNode extends Node {
            Object access(UnboxableArrayObject obj, int index) {
                if (index < 0 || index >= obj.size) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(String.valueOf(index));
                }
                return index;
            }
        }

        @Resolve(message = "IS_BOXED")
        abstract static class IsBoxedINode extends Node {
            @SuppressWarnings("unused")
            Object access(UnboxableArrayObject obj) {
                return true;
            }
        }

        @Resolve(message = "UNBOX")
        abstract static class UnboxINode extends Node {
            Object access(UnboxableArrayObject obj) {
                return obj.size;
            }
        }
    }
}
