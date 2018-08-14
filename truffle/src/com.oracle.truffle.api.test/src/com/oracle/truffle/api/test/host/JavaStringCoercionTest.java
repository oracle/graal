/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
        TruffleObject api = (TruffleObject) env.asGuestValue(new StringConsumer2());
        Object list = call(api, new UnboxableArrayObject(4));
        assertEquals("UnboxableArray(4):[0, 1, 2, 3]", list);
    }

    private static void testStringCoercion(TruffleObject api) throws InteropException {
        assertEquals("ok", call(api, "ok"));
        assertEquals("42", call(api, 42));
        assertEquals("true", call(api, true));
        assertEquals("-128", call(api, Byte.MIN_VALUE));
        assertEquals("-32768", call(api, Short.MIN_VALUE));
        assertEquals("9223372036854775807", call(api, Long.MAX_VALUE));
        assertEquals("3.14", call(api, 3.14));
        assertEquals("3.14", call(api, 3.14f));
        assertEquals("NaN", call(api, Double.NaN));
        assertEquals("Infinity", call(api, Double.POSITIVE_INFINITY));
        assertEquals("-Infinity", call(api, Double.NEGATIVE_INFINITY));
        assertEquals("\uffff", call(api, Character.MAX_VALUE));

        assertEquals("42", call(api, new UnboxableToInt(42)));

        try {
            ForeignAccess.sendInvoke(Message.INVOKE.createNode(), api, "call", new NotCoercibleObject());
            fail("Expected String coercion to fail");
        } catch (UnsupportedTypeException e) {
        }
    }

    private static Object call(TruffleObject obj, Object value) throws InteropException {
        try {
            return ForeignAccess.sendInvoke(Message.INVOKE.createNode(), obj, "call", value);
        } catch (UnsupportedTypeException e) {
            throw new AssertionError("String coercion failed for: " + value + " (" + (value == null ? null : value.getClass().getName()) + ")", e);
        }
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
