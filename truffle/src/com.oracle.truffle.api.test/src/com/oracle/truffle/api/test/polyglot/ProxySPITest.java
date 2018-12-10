/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

    private void assertHostError(Message message, TruffleObject proxyInner, Object... args) {
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
            Assert.assertTrue(languageEnv.asHostException(e) instanceof TestError);
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
