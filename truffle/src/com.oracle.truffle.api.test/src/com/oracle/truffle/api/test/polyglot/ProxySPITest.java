/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyDate;
import org.graalvm.polyglot.proxy.ProxyDuration;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstant;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyNativeObject;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.proxy.ProxyTime;
import org.graalvm.polyglot.proxy.ProxyTimeZone;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * Testing the behavior of proxies towards languages.
 */
public class ProxySPITest extends AbstractPolyglotTest {

    static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class ProxyTestFunction implements TruffleObject {

        TruffleObject lastFunction;

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments) {
            lastFunction = (TruffleObject) arguments[0];
            return lastFunction;
        }

    }

    @Before
    public void before() {
        setupEnv(Context.create());
    }

    private TruffleObject toInnerProxy(Proxy proxy) {
        ProxyTestFunction f = new ProxyTestFunction();
        context.asValue(f).execute(proxy);
        return f.lastFunction;
    }

    @Test
    public void testSimpleProxy() throws Throwable {
        Proxy proxyOuter = new Proxy() {
        };
        Object proxyInner = toInnerProxy(proxyOuter);

        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.asPointer(proxyInner));
        assertUnsupported(() -> INTEROP.getArraySize(proxyInner));
        assertUnsupported(() -> INTEROP.getMembers(proxyInner));
        assertUnsupported(() -> INTEROP.readMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.writeMember(proxyInner, "", ""));
        assertUnsupported(() -> INTEROP.removeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.readArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.removeArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.writeArrayElement(proxyInner, 0, ""));
        INTEROP.toNative(proxyInner);
        assertUnsupported(() -> INTEROP.invokeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.instantiate(proxyInner));

        assertFalse(INTEROP.isNumber(proxyInner));
        assertFalse(INTEROP.isExecutable(proxyInner));
        assertFalse(INTEROP.isInstantiable(proxyInner));
        assertFalse(INTEROP.isNull(proxyInner));
        assertFalse(INTEROP.hasMembers(proxyInner));
        assertFalse(INTEROP.hasArrayElements(proxyInner));
        assertFalse(INTEROP.isPointer(proxyInner));
    }

    @Test
    public void testArrayProxy() throws Throwable {

        final long size = 42;
        ProxyArray proxyOuter = new ProxyArray() {
            int[] array = new int[(int) size];
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
        Object proxyInner = toInnerProxy(proxyOuter);

        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.asPointer(proxyInner));
        assertUnsupported(() -> INTEROP.getMembers(proxyInner));
        assertUnsupported(() -> INTEROP.readMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.writeMember(proxyInner, "", ""));
        assertUnsupported(() -> INTEROP.removeMember(proxyInner, ""));
        INTEROP.toNative(proxyInner);
        assertUnsupported(() -> INTEROP.invokeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.instantiate(proxyInner));

        assertFalse(INTEROP.isNumber(proxyInner));
        assertFalse(INTEROP.isExecutable(proxyInner));
        assertFalse(INTEROP.isInstantiable(proxyInner));
        assertFalse(INTEROP.isNull(proxyInner));
        assertFalse(INTEROP.hasMembers(proxyInner));
        assertFalse(INTEROP.isPointer(proxyInner));

        assertTrue(INTEROP.hasArrayElements(proxyInner));

        assertEquals(size, INTEROP.getArraySize(proxyInner));
        for (int i = 0; i < size; i++) {
            assertEquals(42, INTEROP.readArrayElement(proxyInner, i));
        }
        for (int i = 0; i < size; i++) {
            INTEROP.writeArrayElement(proxyInner, i, 41);
        }
        for (int i = 0; i < size; i++) {
            assertEquals(41, INTEROP.readArrayElement(proxyInner, i));
        }

        assertInvalidArrayIndex(() -> INTEROP.readArrayElement(proxyInner, 42));
        assertInvalidArrayIndex(() -> INTEROP.readArrayElement(proxyInner, -1));
        assertInvalidArrayIndex(() -> INTEROP.readArrayElement(proxyInner, Integer.MAX_VALUE));
        assertInvalidArrayIndex(() -> INTEROP.readArrayElement(proxyInner, Integer.MIN_VALUE));

        assertTrue(INTEROP.isArrayElementReadable(proxyInner, 41));
        assertTrue(INTEROP.isArrayElementModifiable(proxyInner, 41));
        assertTrue(INTEROP.isArrayElementRemovable(proxyInner, 41));
        assertFalse(INTEROP.isArrayElementInsertable(proxyInner, 41));

        assertFalse(INTEROP.isArrayElementReadable(proxyInner, 42));
        assertFalse(INTEROP.isArrayElementModifiable(proxyInner, 42));
        assertFalse(INTEROP.isArrayElementRemovable(proxyInner, 42));
        assertTrue(INTEROP.isArrayElementInsertable(proxyInner, 42));
    }

    @Test
    public void testArrayElementRemove() throws Throwable {
        final int size = 42;
        ArrayList<Object> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(i);
        }
        ProxyArray proxyOuter = ProxyArray.fromList(list);
        Object proxyInner = toInnerProxy(proxyOuter);

        assertTrue(INTEROP.hasArrayElements(proxyInner));
        assertEquals(size, INTEROP.getArraySize(proxyInner));
        INTEROP.removeArrayElement(proxyInner, 10);
        assertEquals(size - 1, INTEROP.getArraySize(proxyInner));
    }

    @Test
    public void testProxyObject() throws Throwable {
        Map<String, Object> values = new HashMap<>();
        ProxyObject proxyOuter = ProxyObject.fromMap(values);

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.asPointer(proxyInner));
        assertUnsupported(() -> INTEROP.getArraySize(proxyInner));
        assertUnsupported(() -> INTEROP.readArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.removeArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.writeArrayElement(proxyInner, 0, ""));
        INTEROP.toNative(proxyInner);
        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.instantiate(proxyInner));

        assertFalse(INTEROP.isNumber(proxyInner));
        assertFalse(INTEROP.isExecutable(proxyInner));
        assertFalse(INTEROP.isInstantiable(proxyInner));
        assertFalse(INTEROP.isNull(proxyInner));
        assertFalse(INTEROP.hasArrayElements(proxyInner));
        assertFalse(INTEROP.isPointer(proxyInner));

        assertTrue(INTEROP.hasMembers(proxyInner));
        assertEmpty(INTEROP.getMembers(proxyInner));

        INTEROP.writeMember(proxyInner, "a", 42);
        assertEquals(42, INTEROP.readMember(proxyInner, "a"));
        assertUnsupported(() -> INTEROP.invokeMember(proxyInner, "a"));

        assertTrue(INTEROP.isMemberReadable(proxyInner, "a"));
        assertTrue(INTEROP.isMemberModifiable(proxyInner, "a"));
        assertTrue(INTEROP.isMemberRemovable(proxyInner, "a"));
        assertFalse(INTEROP.isMemberInsertable(proxyInner, "a"));
        assertFalse(INTEROP.isMemberInvocable(proxyInner, "a"));
        assertFalse(INTEROP.isMemberInternal(proxyInner, "a"));

        assertFalse(INTEROP.isMemberReadable(proxyInner, ""));
        assertFalse(INTEROP.isMemberModifiable(proxyInner, ""));
        assertFalse(INTEROP.isMemberRemovable(proxyInner, ""));
        assertTrue(INTEROP.isMemberInsertable(proxyInner, ""));
        assertFalse(INTEROP.isMemberInvocable(proxyInner, ""));
        assertFalse(INTEROP.isMemberInternal(proxyInner, ""));

        INTEROP.removeMember(proxyInner, "a");
        assertEmpty(INTEROP.getMembers(proxyInner));
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

        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.asPointer(proxyInner));
        assertUnsupported(() -> INTEROP.getArraySize(proxyInner));
        assertEmpty(INTEROP.getMembers(proxyInner));
        assertUnsupported(() -> INTEROP.readMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.writeMember(proxyInner, "", ""));
        assertUnsupported(() -> INTEROP.removeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.readArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.removeArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.writeArrayElement(proxyInner, 0, ""));
        INTEROP.toNative(proxyInner);
        assertUnsupported(() -> INTEROP.invokeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.instantiate(proxyInner));
        assertUnsupported(() -> INTEROP.asPointer(proxyInner));

        assertFalse(INTEROP.isNumber(proxyInner));
        assertFalse(INTEROP.isExecutable(proxyInner));
        assertFalse(INTEROP.isInstantiable(proxyInner));
        assertFalse(INTEROP.isNull(proxyInner));
        assertTrue(INTEROP.hasMembers(proxyInner));
        assertFalse(INTEROP.hasArrayElements(proxyInner));
        assertFalse(INTEROP.isPointer(proxyInner));
    }

    @Test
    public void testProxyNativeObject() throws Throwable {

        ProxyNativeObject proxyOuter = new ProxyNativeObject() {
            public long asPointer() {
                return 42;
            }
        };

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.getArraySize(proxyInner));
        assertUnsupported(() -> INTEROP.getMembers(proxyInner));
        assertUnsupported(() -> INTEROP.readMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.writeMember(proxyInner, "", ""));
        assertUnsupported(() -> INTEROP.removeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.readArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.removeArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.writeArrayElement(proxyInner, 0, ""));
        INTEROP.toNative(proxyInner);
        assertUnsupported(() -> INTEROP.invokeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.instantiate(proxyInner));

        assertFalse(INTEROP.isNumber(proxyInner));
        assertFalse(INTEROP.isExecutable(proxyInner));
        assertFalse(INTEROP.isInstantiable(proxyInner));
        assertFalse(INTEROP.isNull(proxyInner));
        assertFalse(INTEROP.hasMembers(proxyInner));
        assertFalse(INTEROP.hasArrayElements(proxyInner));

        assertTrue(INTEROP.isPointer(proxyInner));
        assertEquals(42L, INTEROP.asPointer(proxyInner));
    }

    @Test
    public void testProxyExecutable() throws Throwable {

        ProxyExecutable proxyOuter = new ProxyExecutable() {
            public Object execute(Value... t) {
                return t[0].asInt();
            }
        };
        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertUnsupported(() -> INTEROP.asPointer(proxyInner));
        assertUnsupported(() -> INTEROP.getArraySize(proxyInner));
        assertUnsupported(() -> INTEROP.getMembers(proxyInner));
        assertUnsupported(() -> INTEROP.readMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.writeMember(proxyInner, "", ""));
        assertUnsupported(() -> INTEROP.removeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.readArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.removeArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.writeArrayElement(proxyInner, 0, ""));
        INTEROP.toNative(proxyInner);
        assertUnsupported(() -> INTEROP.invokeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.instantiate(proxyInner));

        assertFalse(INTEROP.isNumber(proxyInner));
        assertFalse(INTEROP.isInstantiable(proxyInner));
        assertFalse(INTEROP.isNull(proxyInner));
        assertFalse(INTEROP.hasMembers(proxyInner));
        assertFalse(INTEROP.hasArrayElements(proxyInner));
        assertFalse(INTEROP.isPointer(proxyInner));

        assertTrue(INTEROP.isExecutable(proxyInner));
        assertEquals(42, INTEROP.execute(proxyInner, 42));
    }

    @Test
    public void testProxyInstantiable() throws Throwable {

        ProxyInstantiable proxyOuter = new ProxyInstantiable() {
            @Override
            public Object newInstance(Value... t) {
                return t[0].asInt();
            }
        };

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.asPointer(proxyInner));
        assertUnsupported(() -> INTEROP.getArraySize(proxyInner));
        assertUnsupported(() -> INTEROP.getMembers(proxyInner));
        assertUnsupported(() -> INTEROP.readMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.writeMember(proxyInner, "", ""));
        assertUnsupported(() -> INTEROP.removeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.readArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.removeArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.writeArrayElement(proxyInner, 0, ""));
        INTEROP.toNative(proxyInner);
        assertUnsupported(() -> INTEROP.invokeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.execute(proxyInner));

        assertFalse(INTEROP.isNumber(proxyInner));
        assertFalse(INTEROP.isExecutable(proxyInner));
        assertFalse(INTEROP.isNull(proxyInner));
        assertFalse(INTEROP.hasMembers(proxyInner));
        assertFalse(INTEROP.hasArrayElements(proxyInner));
        assertFalse(INTEROP.isPointer(proxyInner));

        assertTrue(INTEROP.isInstantiable(proxyInner));
        assertEquals(42, INTEROP.instantiate(proxyInner, 42));
    }

    @Test
    public void testProxyDate() throws Throwable {
        LocalDate date = LocalDate.now();
        ProxyDate proxyOuter = new ProxyDate() {
            public LocalDate asDate() {
                return date;
            }
        };

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.instantiate(proxyInner));
        assertUnsupported(() -> INTEROP.asPointer(proxyInner));
        assertUnsupported(() -> INTEROP.getArraySize(proxyInner));
        assertUnsupported(() -> INTEROP.getMembers(proxyInner));
        assertUnsupported(() -> INTEROP.readMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.writeMember(proxyInner, "", ""));
        assertUnsupported(() -> INTEROP.removeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.readArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.removeArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.writeArrayElement(proxyInner, 0, ""));
        INTEROP.toNative(proxyInner);
        assertUnsupported(() -> INTEROP.invokeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.asTime(proxyInner));
        assertUnsupported(() -> INTEROP.asTimeZone(proxyInner));
        assertUnsupported(() -> INTEROP.asDuration(proxyInner));

        assertFalse(INTEROP.isNumber(proxyInner));
        assertFalse(INTEROP.isInstantiable(proxyInner));
        assertFalse(INTEROP.isExecutable(proxyInner));
        assertFalse(INTEROP.isNull(proxyInner));
        assertFalse(INTEROP.hasMembers(proxyInner));
        assertFalse(INTEROP.hasArrayElements(proxyInner));
        assertFalse(INTEROP.isPointer(proxyInner));
        assertFalse(INTEROP.isTime(proxyInner));
        assertFalse(INTEROP.isTimeZone(proxyInner));
        assertFalse(INTEROP.isDuration(proxyInner));

        assertTrue(INTEROP.isDate(proxyInner));
        assertEquals(date, INTEROP.asDate(proxyInner));
    }

    @Test
    public void testProxyTime() throws Throwable {
        LocalTime time = LocalTime.now();
        ProxyTime proxyOuter = new ProxyTime() {
            public LocalTime asTime() {
                return time;
            }
        };

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.instantiate(proxyInner));
        assertUnsupported(() -> INTEROP.asPointer(proxyInner));
        assertUnsupported(() -> INTEROP.getArraySize(proxyInner));
        assertUnsupported(() -> INTEROP.getMembers(proxyInner));
        assertUnsupported(() -> INTEROP.readMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.writeMember(proxyInner, "", ""));
        assertUnsupported(() -> INTEROP.removeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.readArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.removeArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.writeArrayElement(proxyInner, 0, ""));
        INTEROP.toNative(proxyInner);
        assertUnsupported(() -> INTEROP.invokeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.asDate(proxyInner));
        assertUnsupported(() -> INTEROP.asTimeZone(proxyInner));
        assertUnsupported(() -> INTEROP.asDuration(proxyInner));

        assertFalse(INTEROP.isNumber(proxyInner));
        assertFalse(INTEROP.isInstantiable(proxyInner));
        assertFalse(INTEROP.isExecutable(proxyInner));
        assertFalse(INTEROP.isNull(proxyInner));
        assertFalse(INTEROP.hasMembers(proxyInner));
        assertFalse(INTEROP.hasArrayElements(proxyInner));
        assertFalse(INTEROP.isPointer(proxyInner));
        assertFalse(INTEROP.isDate(proxyInner));
        assertFalse(INTEROP.isTimeZone(proxyInner));
        assertFalse(INTEROP.isDuration(proxyInner));

        assertTrue(INTEROP.isTime(proxyInner));
        assertEquals(time, INTEROP.asTime(proxyInner));
    }

    @Test
    public void testProxyTimeZone() throws Throwable {
        ZoneId zone = ZoneId.of("UTC+1");
        ProxyTimeZone proxyOuter = new ProxyTimeZone() {

            public ZoneId asTimeZone() {
                return zone;
            }
        };

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.instantiate(proxyInner));
        assertUnsupported(() -> INTEROP.asPointer(proxyInner));
        assertUnsupported(() -> INTEROP.getArraySize(proxyInner));
        assertUnsupported(() -> INTEROP.getMembers(proxyInner));
        assertUnsupported(() -> INTEROP.readMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.writeMember(proxyInner, "", ""));
        assertUnsupported(() -> INTEROP.removeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.readArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.removeArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.writeArrayElement(proxyInner, 0, ""));
        INTEROP.toNative(proxyInner);
        assertUnsupported(() -> INTEROP.invokeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.asDate(proxyInner));
        assertUnsupported(() -> INTEROP.asTime(proxyInner));
        assertUnsupported(() -> INTEROP.asDuration(proxyInner));

        assertFalse(INTEROP.isNumber(proxyInner));
        assertFalse(INTEROP.isInstantiable(proxyInner));
        assertFalse(INTEROP.isExecutable(proxyInner));
        assertFalse(INTEROP.isNull(proxyInner));
        assertFalse(INTEROP.hasMembers(proxyInner));
        assertFalse(INTEROP.hasArrayElements(proxyInner));
        assertFalse(INTEROP.isPointer(proxyInner));
        assertFalse(INTEROP.isDate(proxyInner));
        assertFalse(INTEROP.isTime(proxyInner));
        assertFalse(INTEROP.isDuration(proxyInner));

        assertTrue(INTEROP.isTimeZone(proxyInner));
        assertEquals(zone, INTEROP.asTimeZone(proxyInner));
    }

    @Test
    public void testProxyInstant() throws Throwable {
        Instant instant = Instant.now();
        ProxyInstant proxyOuter = new ProxyInstant() {
            public Instant asInstant() {
                return instant;
            }
        };

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.instantiate(proxyInner));
        assertUnsupported(() -> INTEROP.asPointer(proxyInner));
        assertUnsupported(() -> INTEROP.getArraySize(proxyInner));
        assertUnsupported(() -> INTEROP.getMembers(proxyInner));
        assertUnsupported(() -> INTEROP.readMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.writeMember(proxyInner, "", ""));
        assertUnsupported(() -> INTEROP.removeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.readArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.removeArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.writeArrayElement(proxyInner, 0, ""));
        INTEROP.toNative(proxyInner);
        assertUnsupported(() -> INTEROP.invokeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.asDuration(proxyInner));

        assertFalse(INTEROP.isNumber(proxyInner));
        assertFalse(INTEROP.isInstantiable(proxyInner));
        assertFalse(INTEROP.isExecutable(proxyInner));
        assertFalse(INTEROP.isNull(proxyInner));
        assertFalse(INTEROP.hasMembers(proxyInner));
        assertFalse(INTEROP.hasArrayElements(proxyInner));
        assertFalse(INTEROP.isPointer(proxyInner));
        assertFalse(INTEROP.isDuration(proxyInner));

        assertTrue(INTEROP.isTimeZone(proxyInner));
        assertTrue(INTEROP.isTime(proxyInner));
        assertTrue(INTEROP.isDate(proxyInner));
        assertTrue(INTEROP.isInstant(proxyInner));
        assertEquals(instant.atZone(ZoneId.of("UTC")).toLocalDate(), INTEROP.asDate(proxyInner));
        assertEquals(instant.atZone(ZoneId.of("UTC")).toLocalTime(), INTEROP.asTime(proxyInner));
        assertEquals(ZoneId.of("UTC"), INTEROP.asTimeZone(proxyInner));
        assertEquals(instant, INTEROP.asInstant(proxyInner));
    }

    @Test
    public void testProxyDuration() throws Throwable {
        Duration duration = Duration.ofMillis(100);
        ProxyDuration proxyOuter = new ProxyDuration() {

            public Duration asDuration() {
                return duration;
            }
        };

        TruffleObject proxyInner = toInnerProxy(proxyOuter);

        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.instantiate(proxyInner));
        assertUnsupported(() -> INTEROP.asPointer(proxyInner));
        assertUnsupported(() -> INTEROP.getArraySize(proxyInner));
        assertUnsupported(() -> INTEROP.getMembers(proxyInner));
        assertUnsupported(() -> INTEROP.readMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.writeMember(proxyInner, "", ""));
        assertUnsupported(() -> INTEROP.removeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.readArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.removeArrayElement(proxyInner, 0));
        assertUnsupported(() -> INTEROP.writeArrayElement(proxyInner, 0, ""));
        INTEROP.toNative(proxyInner);
        assertUnsupported(() -> INTEROP.invokeMember(proxyInner, ""));
        assertUnsupported(() -> INTEROP.execute(proxyInner));
        assertUnsupported(() -> INTEROP.asDate(proxyInner));
        assertUnsupported(() -> INTEROP.asTime(proxyInner));
        assertUnsupported(() -> INTEROP.asTimeZone(proxyInner));

        assertFalse(INTEROP.isNumber(proxyInner));
        assertFalse(INTEROP.isInstantiable(proxyInner));
        assertFalse(INTEROP.isExecutable(proxyInner));
        assertFalse(INTEROP.isNull(proxyInner));
        assertFalse(INTEROP.hasMembers(proxyInner));
        assertFalse(INTEROP.hasArrayElements(proxyInner));
        assertFalse(INTEROP.isPointer(proxyInner));
        assertFalse(INTEROP.isDate(proxyInner));
        assertFalse(INTEROP.isTime(proxyInner));
        assertFalse(INTEROP.isTimeZone(proxyInner));

        assertTrue(INTEROP.isDuration(proxyInner));
        assertEquals(duration, INTEROP.asDuration(proxyInner));
    }

    static class Invalid0 implements ProxyDuration, ProxyDate {

        public LocalDate asDate() {
            return LocalDate.now();
        }

        public Duration asDuration() {
            return Duration.ofMillis(100);
        }

    }

    @Test
    public void testInvalidCombination0() throws Throwable {
        Invalid0 proxyOuter = new Invalid0();
        TruffleObject proxyInner = toInnerProxy(proxyOuter);
        assertInvalidState(() -> INTEROP.isDate(proxyInner));
        assertInvalidState(() -> INTEROP.isDuration(proxyInner));
        assertInvalidState(() -> INTEROP.asDuration(proxyInner));
        assertInvalidState(() -> INTEROP.asDate(proxyInner));
    }

    static class Invalid1 implements ProxyDate, ProxyTimeZone {

        public ZoneId asTimeZone() {
            return ZoneId.of("UTC");
        }

        public LocalDate asDate() {
            return LocalDate.now();
        }

    }

    @Test
    public void testInvalidCombination1() throws Throwable {
        Invalid1 proxyOuter = new Invalid1();
        TruffleObject proxyInner = toInnerProxy(proxyOuter);
        assertInvalidState(() -> INTEROP.asDate(proxyInner));
        assertInvalidState(() -> INTEROP.asTimeZone(proxyInner));
        assertInvalidState(() -> INTEROP.isDate(proxyInner));
        assertInvalidState(() -> INTEROP.isTimeZone(proxyInner));
    }

    static class Invalid2 implements ProxyTime, ProxyTimeZone {

        public ZoneId asTimeZone() {
            return ZoneId.of("US/Pacific");
        }

        public LocalTime asTime() {
            return LocalTime.now();
        }

    }

    @Test
    public void testInvalidCombination2() throws Throwable {
        Invalid2 proxyOuter = new Invalid2();
        TruffleObject proxyInner = toInnerProxy(proxyOuter);
        assertInvalidState(() -> INTEROP.asTime(proxyInner));
        assertInvalidState(() -> INTEROP.asTimeZone(proxyInner));
        assertInvalidState(() -> INTEROP.isTime(proxyInner));
        assertInvalidState(() -> INTEROP.isTimeZone(proxyInner));
    }

    @SuppressWarnings("serial")
    static class TestError extends RuntimeException {

        TestError() {
            super("Host Error");
        }

    }

    @SuppressWarnings("deprecation")
    private static class AllProxy implements ProxyArray, ProxyObject, ProxyNativeObject, ProxyExecutable, ProxyInstantiable {

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

        assertHostError(() -> INTEROP.execute(proxyInner));
        assertHostError(() -> INTEROP.asPointer(proxyInner));
        assertHostError(() -> INTEROP.getArraySize(proxyInner));
        assertHostError(() -> INTEROP.getMembers(proxyInner));
        assertHostError(() -> INTEROP.readMember(proxyInner, ""));
        assertHostError(() -> INTEROP.writeMember(proxyInner, "", ""));
        assertHostError(() -> INTEROP.removeMember(proxyInner, ""));
        assertHostError(() -> INTEROP.readArrayElement(proxyInner, 0));
        assertHostError(() -> INTEROP.removeArrayElement(proxyInner, 0));
        assertHostError(() -> INTEROP.writeArrayElement(proxyInner, 0, ""));
        INTEROP.toNative(proxyInner);
        assertHostError(() -> INTEROP.invokeMember(proxyInner, ""));
        assertHostError(() -> INTEROP.execute(proxyInner));
        assertHostError(() -> INTEROP.instantiate(proxyInner));

        assertHostError(() -> INTEROP.isMemberReadable(proxyInner, ""));
        assertHostError(() -> INTEROP.isMemberModifiable(proxyInner, ""));
        assertHostError(() -> INTEROP.isMemberInsertable(proxyInner, ""));
        assertHostError(() -> INTEROP.isMemberRemovable(proxyInner, ""));
        assertHostError(() -> INTEROP.isMemberInvocable(proxyInner, ""));
        assertFalse(INTEROP.isMemberInternal(proxyInner, ""));

        assertHostError(() -> INTEROP.isArrayElementReadable(proxyInner, 0L));
        assertHostError(() -> INTEROP.isArrayElementModifiable(proxyInner, 0L));
        assertHostError(() -> INTEROP.isArrayElementInsertable(proxyInner, 0L));
        assertHostError(() -> INTEROP.isArrayElementRemovable(proxyInner, 0L));

        assertFalse(INTEROP.isNumber(proxyInner));
        assertTrue(INTEROP.isExecutable(proxyInner));
        assertTrue(INTEROP.isInstantiable(proxyInner));
        assertFalse(INTEROP.isNull(proxyInner));
        assertTrue(INTEROP.hasMembers(proxyInner));
        assertTrue(INTEROP.hasArrayElements(proxyInner));
        assertTrue(INTEROP.isPointer(proxyInner));
    }

    private static void assertEmpty(Object proxyInner) {
        try {
            assertTrue(INTEROP.hasArrayElements(proxyInner));
            assertEquals(0L, INTEROP.getArraySize(proxyInner));
        } catch (InteropException e) {
            Assert.fail();
        }
    }

    private void assertHostError(InteropCallable r) {
        try {
            r.call();
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

    private static void assertInvalidState(InteropCallable r) {
        boolean valid = true;
        try {
            r.call();
            valid = false;
        } catch (InteropException e) {
            Assert.fail();
        } catch (AssertionError e) {
        }
        if (!valid) {
            throw new AssertionError("assertion expected");
        }
    }

    interface InteropCallable {

        void call() throws InteropException;

    }

    private static void assertUnsupported(InteropCallable r) throws Exception {
        try {
            r.call();
            Assert.fail();
        } catch (UnsupportedMessageException e) {
        } catch (InteropException e) {
            throw e;
        }
    }

    private static void assertInvalidArrayIndex(InteropCallable r) throws Exception {
        try {
            r.call();
            Assert.fail();
        } catch (InvalidArrayIndexException e) {
        } catch (InteropException e) {
            throw e;
        }
    }

}
