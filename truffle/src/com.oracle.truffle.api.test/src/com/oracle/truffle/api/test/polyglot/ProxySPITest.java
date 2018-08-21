/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyNativeObject;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;

/**
 * Testing the behavior of proxies towards languages.
 */
public class ProxySPITest extends AbstractPolyglotTest {

    static class TestFunction extends ProxyInteropObject {

        TruffleObject lastFunction;

        @Override
        public boolean isExecutable() {
            return true;
        }

        @Override
        public Object execute(Object[] arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            lastFunction = (TruffleObject) arguments[0];
            return lastFunction;
        }

    }

    @Before
    public void before() {
        setupEnv(Context.create());
    }

    private TruffleObject toInnerProxy(Proxy proxy) {
        TestFunction f = new TestFunction();
        context.asValue(f).execute(proxy);
        return f.lastFunction;
    }

    @Test
    public void testSimpleProxy() throws Throwable {
        Proxy proxyOuter = new Proxy() {
        };
        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertUnsupported(Message.EXECUTE, proxyInner);
        assertUnsupported(Message.AS_POINTER, proxyInner);
        assertUnsupported(Message.GET_SIZE, proxyInner);
        assertEmpty(Message.KEYS, proxyInner);
        assertUnsupported(Message.READ, proxyInner);
        assertUnsupported(Message.WRITE, proxyInner);
        assertUnsupported(Message.REMOVE, proxyInner);
        assertUnsupported(Message.TO_NATIVE, proxyInner);
        assertUnsupported(Message.UNBOX, proxyInner);
        assertUnsupported(Message.INVOKE, proxyInner);
        assertUnsupported(Message.EXECUTE, proxyInner);
        assertUnsupported(Message.NEW, proxyInner);
        assertEquals(false, Message.IS_BOXED, proxyInner);
        assertEquals(false, Message.IS_EXECUTABLE, proxyInner);
        assertEquals(false, Message.IS_INSTANTIABLE, proxyInner);
        assertEquals(false, Message.IS_NULL, proxyInner);
        assertEquals(false, Message.HAS_KEYS, proxyInner);
        assertEquals(false, Message.HAS_SIZE, proxyInner);
        assertEquals(false, Message.IS_POINTER, proxyInner);
        assertEquals(0, Message.KEY_INFO, proxyInner);
    }

    private static final int EXISTING_KEY = KeyInfo.READABLE | KeyInfo.MODIFIABLE | KeyInfo.REMOVABLE;
    private static final int NO_KEY = KeyInfo.INSERTABLE;

    @Test
    public void testArrayProxy() throws Throwable {

        final int size = 42;
        ProxyArray proxyOuter = new ProxyArray() {
            int[] array = new int[size];
            {
                Arrays.fill(array, 42);
            }

            public Object get(long index) {
                return array[(int) index];
            }

            public void set(long index, Value value) {
                array[(int) index] = value.asInt();
            }

            public long getSize() {
                return size;
            }
        };
        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertEquals(size, Message.GET_SIZE, proxyInner);
        for (int i = 0; i < size; i++) {
            assertEquals(42, Message.READ, proxyInner, i);
        }
        for (int i = 0; i < size; i++) {
            assertEquals(41, Message.WRITE, proxyInner, i, 41);
        }
        for (int i = 0; i < size; i++) {
            assertEquals(41, Message.READ, proxyInner, i);
        }
        assertUnknownIdentifier(Message.READ, proxyInner, 42);
        assertUnknownIdentifier(Message.READ, proxyInner, -1);
        assertUnknownIdentifier(Message.READ, proxyInner, Integer.MAX_VALUE);
        assertUnknownIdentifier(Message.READ, proxyInner, Integer.MIN_VALUE);
        assertEquals(true, Message.HAS_SIZE, proxyInner);

        assertEquals(EXISTING_KEY, Message.KEY_INFO, proxyInner, 41);
        assertEquals(NO_KEY, Message.KEY_INFO, proxyInner, 42);

        assertUnsupported(Message.EXECUTE, proxyInner);
        assertUnsupported(Message.AS_POINTER, proxyInner);
        assertEquals(false, Message.HAS_KEYS, proxyInner);
        assertEmpty(Message.KEYS, proxyInner);
        assertUnsupported(Message.READ, proxyInner, "");
        assertUnsupported(Message.WRITE, proxyInner, "");
        assertUnsupported(Message.TO_NATIVE, proxyInner);
        assertUnsupported(Message.UNBOX, proxyInner);
        assertUnsupported(Message.INVOKE, proxyInner);
        assertUnsupported(Message.EXECUTE, proxyInner);
        assertUnsupported(Message.NEW, proxyInner);
        assertEquals(false, Message.IS_BOXED, proxyInner);
        assertEquals(false, Message.IS_EXECUTABLE, proxyInner);
        assertEquals(false, Message.IS_INSTANTIABLE, proxyInner);
        assertEquals(false, Message.IS_NULL, proxyInner);
        assertEquals(false, Message.IS_POINTER, proxyInner);
        assertEquals(0, Message.KEY_INFO, proxyInner);
    }

    @Test
    public void testArrayElementRemove() throws Throwable {

        final int size = 42;
        ArrayList<Object> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(i);
        }
        ProxyArray proxyOuter = ProxyArray.fromList(list);

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertEquals(size, Message.GET_SIZE, proxyInner);
        assertEquals(true, Message.REMOVE, proxyInner, 10);
        assertEquals(size - 1, Message.GET_SIZE, proxyInner);
    }

    @Test
    public void testProxyObject() throws Throwable {

        Map<String, Object> values = new HashMap<>();
        ProxyObject proxyOuter = ProxyObject.fromMap(values);

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertEquals(true, Message.HAS_KEYS, proxyInner);
        assertEmpty(Message.KEYS, proxyInner);

        assertUnknownIdentifier(Message.READ, proxyInner, "");
        assertEquals(NO_KEY, Message.KEY_INFO, proxyInner, "");

        assertEquals(42, Message.WRITE, proxyInner, "a", 42);
        assertEquals(42, Message.READ, proxyInner, "a");
        assertEquals(EXISTING_KEY, Message.KEY_INFO, proxyInner, "a");
        assertEquals(NO_KEY, Message.KEY_INFO, proxyInner, "");

        assertUnsupported(Message.EXECUTE, proxyInner);
        assertUnsupported(Message.GET_SIZE, proxyInner);
        assertUnsupported(Message.READ, proxyInner, 0);
        assertUnsupported(Message.WRITE, proxyInner, 1);
        assertUnsupported(Message.UNBOX, proxyInner);
        assertUnsupported(Message.TO_NATIVE, proxyInner);
        assertUnsupported(Message.AS_POINTER, proxyInner);
        assertUnsupported(Message.INVOKE, proxyInner);
        assertUnsupported(Message.EXECUTE, proxyInner);
        assertUnsupported(Message.NEW, proxyInner);
        assertEquals(false, Message.IS_BOXED, proxyInner);
        assertEquals(false, Message.IS_EXECUTABLE, proxyInner);
        assertEquals(false, Message.IS_INSTANTIABLE, proxyInner);
        assertEquals(false, Message.IS_NULL, proxyInner);
        assertEquals(false, Message.HAS_SIZE, proxyInner);
        assertEquals(false, Message.IS_POINTER, proxyInner);
        assertEquals(0, Message.KEY_INFO, proxyInner);

        assertEquals(true, Message.REMOVE, proxyInner, "a");
        assertEmpty(Message.KEYS, proxyInner);
    }

    @Test
    public void testProxyObjectUnsupported() throws Throwable {

        ProxyObject proxyOuter = new ProxyObject() {

            public void putMember(String key, Value value) {
                throw new UnsupportedOperationException();
            }

            public boolean hasMember(String key) {
                return true;
            }

            public ProxyArray getMemberKeys() {
                return null;
            }

            public Object getMember(String key) {
                throw new UnsupportedOperationException();
            }
        };

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertEmpty(Message.KEYS, proxyInner);
        assertUnsupported(Message.READ, proxyInner, "");
        assertUnsupported(Message.WRITE, proxyInner, "", 42);
        assertUnsupported(Message.EXECUTE, proxyInner);
        assertUnsupported(Message.GET_SIZE, proxyInner);
        assertUnsupported(Message.READ, proxyInner, 0);
        assertUnsupported(Message.WRITE, proxyInner, 1);
        assertUnsupported(Message.UNBOX, proxyInner);
        assertUnsupported(Message.TO_NATIVE, proxyInner);
        assertUnsupported(Message.AS_POINTER, proxyInner);
        assertUnsupported(Message.INVOKE, proxyInner);
        assertUnsupported(Message.EXECUTE, proxyInner);
        assertUnsupported(Message.NEW, proxyInner);
        assertEquals(false, Message.IS_BOXED, proxyInner);
        assertEquals(false, Message.IS_EXECUTABLE, proxyInner);
        assertEquals(false, Message.IS_INSTANTIABLE, proxyInner);
        assertEquals(false, Message.IS_NULL, proxyInner);
        assertEquals(true, Message.HAS_KEYS, proxyInner);
        assertEquals(false, Message.HAS_SIZE, proxyInner);
        assertEquals(false, Message.IS_POINTER, proxyInner);
        assertEquals(0, Message.KEY_INFO, proxyInner);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testProxyPrimitive() throws Throwable {

        org.graalvm.polyglot.proxy.ProxyPrimitive proxyOuter = new org.graalvm.polyglot.proxy.ProxyPrimitive() {
            public Object asPrimitive() {
                return 42;
            }
        };

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertEquals(true, Message.IS_BOXED, proxyInner);
        assertEquals(42, Message.UNBOX, proxyInner);

        assertUnsupported(Message.EXECUTE, proxyInner);
        assertUnsupported(Message.AS_POINTER, proxyInner);
        assertUnsupported(Message.GET_SIZE, proxyInner);
        assertEmpty(Message.KEYS, proxyInner);
        assertUnsupported(Message.READ, proxyInner);
        assertUnsupported(Message.WRITE, proxyInner);
        assertUnsupported(Message.TO_NATIVE, proxyInner);
        assertUnsupported(Message.INVOKE, proxyInner);
        assertUnsupported(Message.EXECUTE, proxyInner);
        assertUnsupported(Message.NEW, proxyInner);
        assertEquals(false, Message.IS_EXECUTABLE, proxyInner);
        assertEquals(false, Message.IS_INSTANTIABLE, proxyInner);
        assertEquals(false, Message.IS_NULL, proxyInner);
        assertEquals(false, Message.HAS_KEYS, proxyInner);
        assertEquals(false, Message.HAS_SIZE, proxyInner);
        assertEquals(false, Message.IS_POINTER, proxyInner);
        assertEquals(0, Message.KEY_INFO, proxyInner);
    }

    @Test
    public void testProxyNativeObject() throws Throwable {

        ProxyNativeObject proxyOuter = new ProxyNativeObject() {
            public long asPointer() {
                return 42;
            }
        };

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertEquals(true, Message.IS_POINTER, proxyInner);
        assertEquals(42L, Message.AS_POINTER, proxyInner);

        assertUnsupported(Message.EXECUTE, proxyInner);
        assertUnsupported(Message.UNBOX, proxyInner);
        assertUnsupported(Message.GET_SIZE, proxyInner);
        assertEmpty(Message.KEYS, proxyInner);
        assertUnsupported(Message.READ, proxyInner);
        assertUnsupported(Message.WRITE, proxyInner);
        assertUnsupported(Message.TO_NATIVE, proxyInner);
        assertUnsupported(Message.UNBOX, proxyInner);
        assertUnsupported(Message.INVOKE, proxyInner);
        assertUnsupported(Message.EXECUTE, proxyInner);
        assertUnsupported(Message.NEW, proxyInner);
        assertEquals(false, Message.IS_EXECUTABLE, proxyInner);
        assertEquals(false, Message.IS_INSTANTIABLE, proxyInner);
        assertEquals(false, Message.IS_NULL, proxyInner);
        assertEquals(false, Message.HAS_KEYS, proxyInner);
        assertEquals(false, Message.HAS_SIZE, proxyInner);
        assertEquals(false, Message.IS_BOXED, proxyInner);
        assertEquals(0, Message.KEY_INFO, proxyInner);
    }

    @Test
    public void testProxyExecutable() throws Throwable {

        ProxyExecutable proxyOuter = new ProxyExecutable() {
            public Object execute(Value... t) {
                return t[0].asInt();
            }
        };
        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertEquals(true, Message.IS_EXECUTABLE, proxyInner);
        assertEquals(42, Message.EXECUTE, proxyInner, 42);
        assertUnsupported(Message.NEW, proxyInner, 42);

        assertUnsupported(Message.AS_POINTER, proxyInner);
        assertUnsupported(Message.GET_SIZE, proxyInner);
        assertEmpty(Message.KEYS, proxyInner);
        assertUnsupported(Message.READ, proxyInner);
        assertUnsupported(Message.WRITE, proxyInner);
        assertUnsupported(Message.TO_NATIVE, proxyInner);
        assertUnsupported(Message.UNBOX, proxyInner);
        assertUnsupported(Message.INVOKE, proxyInner);
        assertEquals(false, Message.IS_INSTANTIABLE, proxyInner);
        assertEquals(false, Message.IS_BOXED, proxyInner);
        assertEquals(false, Message.IS_NULL, proxyInner);
        assertEquals(false, Message.HAS_KEYS, proxyInner);
        assertEquals(false, Message.HAS_SIZE, proxyInner);
        assertEquals(false, Message.IS_POINTER, proxyInner);
        assertEquals(0, Message.KEY_INFO, proxyInner);
    }

    @Test
    public void testProxyInstantiable() throws Throwable {

        ProxyInstantiable proxyOuter = new ProxyInstantiable() {
            @Override
            public Object newInstance(Value... t) {
                return t[0].newInstance();
            }
        };

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertEquals(true, Message.IS_INSTANTIABLE, proxyInner);
        assertEquals(false, Message.IS_EXECUTABLE, proxyInner);

        assertUnsupported(Message.EXECUTE, proxyInner, 42);
        assertUnsupported(Message.AS_POINTER, proxyInner);
        assertUnsupported(Message.GET_SIZE, proxyInner);
        assertEmpty(Message.KEYS, proxyInner);
        assertUnsupported(Message.READ, proxyInner);
        assertUnsupported(Message.WRITE, proxyInner);
        assertUnsupported(Message.TO_NATIVE, proxyInner);
        assertUnsupported(Message.UNBOX, proxyInner);
        assertUnsupported(Message.INVOKE, proxyInner);
        assertEquals(false, Message.IS_BOXED, proxyInner);
        assertEquals(false, Message.IS_NULL, proxyInner);
        assertEquals(false, Message.HAS_KEYS, proxyInner);
        assertEquals(false, Message.HAS_SIZE, proxyInner);
        assertEquals(false, Message.IS_POINTER, proxyInner);
        assertEquals(0, Message.KEY_INFO, proxyInner);
    }

    @SuppressWarnings("serial")
    static class TestError extends RuntimeException {

        TestError() {
            super("Host Error");
        }

    }

    @SuppressWarnings("deprecation")
    private static class AllProxy implements ProxyArray, ProxyObject, org.graalvm.polyglot.proxy.ProxyPrimitive, ProxyNativeObject, ProxyExecutable, ProxyInstantiable {

        public Object execute(Value... t) {
            throw new TestError();
        }

        @Override
        public Object newInstance(Value... arguments) {
            throw new TestError();
        }

        public long asPointer() {
            throw new TestError();
        }

        public Object asPrimitive() {
            throw new TestError();
        }

        public Object getMember(String key) {
            throw new TestError();
        }

        public ProxyArray getMemberKeys() {
            throw new TestError();
        }

        public boolean hasMember(String key) {
            throw new TestError();
        }

        public void putMember(String key, Value value) {
            throw new TestError();
        }

        @Override
        public boolean remove(long index) {
            throw new TestError();
        }

        @Override
        public boolean removeMember(String key) {
            throw new TestError();
        }

        public Object get(long index) {
            throw new TestError();
        }

        public void set(long index, Value value) {
            throw new TestError();
        }

        public long getSize() {
            throw new TestError();
        }

    }

    @Test
    public void testProxyError() throws Throwable {

        Proxy proxyOuter = new AllProxy();

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertHostError(Message.AS_POINTER, proxyInner);
        assertHostError(Message.GET_SIZE, proxyInner);
        assertHostError(Message.KEYS, proxyInner);
        assertHostError(Message.READ, proxyInner, "");
        assertHostError(Message.READ, proxyInner, 42);
        assertHostError(Message.WRITE, proxyInner, "", 42);
        assertHostError(Message.WRITE, proxyInner, 42, 42);
        assertHostError(Message.REMOVE, proxyInner, 10);
        assertHostError(Message.UNBOX, proxyInner);
        assertHostError(Message.INVOKE, proxyInner, "");
        assertHostError(Message.EXECUTE, proxyInner);
        assertHostError(Message.NEW, proxyInner);
        assertHostError(Message.KEY_INFO, proxyInner, "");
        assertHostError(Message.KEY_INFO, proxyInner, 42);
        assertUnsupported(Message.TO_NATIVE, proxyInner);
        assertEquals(true, Message.IS_BOXED, proxyInner);
        assertEquals(true, Message.IS_EXECUTABLE, proxyInner);
        assertEquals(true, Message.IS_INSTANTIABLE, proxyInner);
        assertEquals(false, Message.IS_NULL, proxyInner);
        assertEquals(true, Message.HAS_KEYS, proxyInner);
        assertEquals(true, Message.HAS_SIZE, proxyInner);
        assertEquals(true, Message.IS_POINTER, proxyInner);
    }

    private static void assertEmpty(Message message, TruffleObject proxyInner) {
        try {
            TruffleObject values = (TruffleObject) ForeignAccess.send(message.createNode(), proxyInner);
            Assert.assertEquals(true, ForeignAccess.sendHasSize(Message.HAS_SIZE.createNode(), values));
            Assert.assertEquals(0, ((Number) ForeignAccess.sendGetSize(Message.GET_SIZE.createNode(), values)).intValue());
        } catch (InteropException e) {
            Assert.fail();
        }
    }

    private static void assertEquals(Object expected, Message message, TruffleObject proxyInner, Object... args) {
        try {
            Assert.assertEquals(expected, ForeignAccess.send(message.createNode(), proxyInner, args));
        } catch (InteropException e) {
            Assert.fail();
        }
    }

    private static void assertHostError(Message message, TruffleObject proxyInner, Object... args) {
        try {
            ForeignAccess.send(message.createNode(), proxyInner, args);
            Assert.fail();
        } catch (InteropException e) {
            Assert.fail();
        } catch (RuntimeException e) {
            if (!(e instanceof TruffleException)) {
                Assert.fail();
            }
            TruffleException te = (TruffleException) e;
            Assert.assertFalse(te.isInternalError());
            Assert.assertEquals("Host Error", ((Exception) e).getMessage());
            Assert.assertTrue(e.getCause() instanceof TestError);
        }
    }

    private static void assertUnsupported(Message message, TruffleObject proxyInner, Object... args) {
        try {
            ForeignAccess.send(message.createNode(), proxyInner, args);
            Assert.fail();
        } catch (UnsupportedMessageException e) {
        } catch (InteropException e) {
            Assert.fail();
        }
    }

    private static void assertUnknownIdentifier(Message message, TruffleObject proxyInner, Object... args) {
        try {
            ForeignAccess.send(message.createNode(), proxyInner, args);
            Assert.fail();
        } catch (UnknownIdentifierException e) {
        } catch (InteropException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

}
