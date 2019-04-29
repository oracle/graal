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
package com.oracle.truffle.api.test.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.test.polyglot.ProxyLegacyInteropObject;

@SuppressWarnings("deprecation")
public class InteropDefaultsTest extends InteropLibraryBaseTest {

    public static class TestInterop1 {
    }

    @Test
    public void testBooleanDefault() throws InteropException {
        assertBoolean(true, true);
        assertBoolean(false, false);
    }

    private void assertBoolean(Object v, boolean expected) throws UnsupportedMessageException, InteropException {
        InteropLibrary library = createLibrary(InteropLibrary.class, v);
        assertTrue(library.isBoolean(v));
        assertEquals(expected, library.asBoolean(v));

        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoNumber(v);
        assertNoString(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);

        if (!(v instanceof LegacyBoxedPrimitive)) {
            assertBoolean(new LegacyBoxedPrimitive(v), expected);
        }
    }

    @Test
    public void testByteDefault() throws InteropException {
        assertNumber(Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber((byte) 0, true, true, true, true, true, true);
        assertNumber(Byte.MAX_VALUE, true, true, true, true, true, true);
    }

    @Test
    public void testShortDefault() throws InteropException {
        assertNumber(Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((short) (Byte.MIN_VALUE - 1), false, true, true, true, true, true);
        assertNumber((short) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber((short) 0, true, true, true, true, true, true);
        assertNumber((short) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((short) (Byte.MAX_VALUE + 1), false, true, true, true, true, true);
        assertNumber(Short.MAX_VALUE, false, true, true, true, true, true);
    }

    @Test
    public void testIntDefault() throws InteropException {
        assertNumber(Integer.MIN_VALUE, false, false, true, true, false, true);
        assertNumber(Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((int) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber(Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((int) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(0, true, true, true, true, true, true);
        assertNumber((int) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber(Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((int) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber(Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber(Integer.MAX_VALUE, false, false, true, true, false, true);
    }

    @Test
    public void testLongDefault() throws InteropException {
        assertNumber(Long.MIN_VALUE, false, false, false, true, false, false);
        assertNumber((long) Integer.MIN_VALUE - 1, false, false, false, true, false, true);
        assertNumber((long) Integer.MIN_VALUE, false, false, true, true, false, true);
        assertNumber((long) Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((long) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((long) Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((long) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(0L, true, true, true, true, true, true);
        assertNumber((long) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((long) Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((long) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber((long) Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber((long) Integer.MAX_VALUE, false, false, true, true, false, true);
        assertNumber(Long.MAX_VALUE, false, false, false, true, false, false);
    }

    @Test
    public void testFloatDefault() throws InteropException {
        assertNumber(Float.NEGATIVE_INFINITY, false, false, false, false, true, true);
        assertNumber((float) Long.MIN_VALUE, false, false, false, false, true, true);
        assertNumber((float) Integer.MIN_VALUE - 1, false, false, false, false, true, true);
        assertNumber((float) Integer.MIN_VALUE, false, false, false, false, true, true);
        assertNumber((float) Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((float) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((float) Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((float) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(-0.0f, false, false, false, false, true, true);
        assertNumber(0.0f, true, true, true, true, true, true);
        assertNumber((float) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((float) Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((float) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber((float) Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber((float) Integer.MAX_VALUE, false, false, false, false, true, true);
        assertNumber((float) Long.MAX_VALUE, false, false, false, false, true, true);
        assertNumber(Float.POSITIVE_INFINITY, false, false, false, false, true, true);
        assertNumber(Float.NaN, false, false, false, false, true, true);
        assertNumber(Float.MIN_VALUE, false, false, false, false, true, true);
        assertNumber(Float.MIN_NORMAL, false, false, false, false, true, true);
        assertNumber(Float.MAX_VALUE, false, false, false, false, true, true);
    }

    @Test
    public void testDoubleDefault() throws InteropException {
        assertNumber(Double.NEGATIVE_INFINITY, false, false, false, false, true, true);
        assertNumber((double) Long.MIN_VALUE, false, false, false, false, true, true);
        assertNumber((double) Integer.MIN_VALUE - 1, false, false, false, true, false, true);
        assertNumber((double) Integer.MIN_VALUE, false, false, true, true, true, true);
        assertNumber((double) Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((double) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((double) Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((double) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(-0.0d, false, false, false, false, true, true);
        assertNumber(0.0d, true, true, true, true, true, true);
        assertNumber((double) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((double) Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((double) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber((double) Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber((double) Integer.MAX_VALUE, false, false, true, true, false, true);
        assertNumber((double) Long.MAX_VALUE, false, false, false, false, true, true);
        assertNumber(Double.POSITIVE_INFINITY, false, false, false, false, true, true);
        assertNumber(Double.NaN, false, false, false, false, true, true);
        assertNumber(Double.MIN_VALUE, false, false, false, false, false, true);
        assertNumber(Double.MIN_NORMAL, false, false, false, false, false, true);
        assertNumber(Double.MAX_VALUE, false, false, false, false, false, true);
    }

    @Test
    public void testStringDefaults() throws InteropException {
        assertString("foo", "foo");
        assertString("bar", "bar");
    }

    @Test
    public void testCharacterDefaults() throws InteropException {
        assertString('a', "a");
        assertString('b', "b");
    }

    private void assertString(Object v, String expectedString) throws UnsupportedMessageException, InteropException {
        InteropLibrary library = createLibrary(InteropLibrary.class, v);
        assertTrue(library.isString(v));
        assertEquals(expectedString, library.asString(v));

        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoNumber(v);
        assertNoBoolean(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);

        if (!(v instanceof LegacyBoxedPrimitive)) {
            assertString(new LegacyBoxedPrimitive(v), expectedString);
        }
    }

    private static class ArrayDefaults extends ProxyLegacyInteropObject {

        private List<Object> array = new ArrayList<>();

        private boolean hasSize = true;
        private int readCalls;
        private int writeCalls;
        private int removeCalls;
        private int keyInfo;

        ArrayDefaults(List<Object> array) {
            this.array = array;
        }

        @Override
        public Object read(Number key) throws UnsupportedMessageException, UnknownIdentifierException {
            readCalls++;
            int index = key.intValue();
            if (index < 0 || index >= array.size()) {
                throw UnknownIdentifierException.create(String.valueOf(index));
            }
            return array.get(index);
        }

        @Override
        public Object write(Number key, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
            writeCalls++;
            int index = key.intValue();
            if (index < 0 || index >= array.size()) {
                throw UnknownIdentifierException.create(String.valueOf(index));
            }
            return array.set(index, value);
        }

        @Override
        public boolean remove(Number key) throws UnsupportedMessageException, UnknownIdentifierException {
            removeCalls++;
            int index = key.intValue();
            if (index < 0 || index >= array.size()) {
                throw UnknownIdentifierException.create(String.valueOf(index));
            }
            Object result = array.remove(index);
            return result != null;
        }

        @Override
        public int getSize() {
            return array.size();
        }

        @SuppressWarnings("deprecation")
        @Override
        public int keyInfo(Number key) {
            if (keyInfo == 0 && key.intValue() >= 0 && key.intValue() < array.size()) {
                return com.oracle.truffle.api.interop.KeyInfo.MODIFIABLE | com.oracle.truffle.api.interop.KeyInfo.READABLE | com.oracle.truffle.api.interop.KeyInfo.REMOVABLE;
            }
            return keyInfo;
        }

        @Override
        public boolean hasSize() {
            return hasSize;
        }

    }

    private static class ObjectDefaults extends ProxyLegacyInteropObject {

        private Map<String, Object> members = new HashMap<>();

        private int readCalls = 0;
        private int writeCalls = 0;
        private int hasKeysCalls = 0;
        private int removeCalls = 0;
        private int invokeCalls = 0;
        private boolean hasKeys = true;

        private int keyInfo;

        @Override
        public boolean hasKeys() {
            hasKeysCalls++;
            return hasKeys;
        }

        @Override
        public Object read(String key) throws UnsupportedMessageException, UnknownIdentifierException {
            readCalls++;
            return members.get(key);
        }

        @Override
        public Object write(String key, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
            writeCalls++;
            members.put(key, value);
            return true;
        }

        @Override
        public boolean remove(String key) throws UnsupportedMessageException, UnknownIdentifierException {
            removeCalls++;
            members.remove(key);
            return true;
        }

        @Override
        public Object invoke(String key, Object[] arguments) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
            invokeCalls++;
            return "invoked " + key + Arrays.toString(arguments);
        }

        @Override
        public Object keys() throws UnsupportedMessageException {
            return new ArrayDefaults(Arrays.asList(members.keySet().toArray()));
        }

        @Override
        public int keyInfo(String key) {
            if (keyInfo == -1) {
                if (members.containsKey(key)) {
                    return com.oracle.truffle.api.interop.KeyInfo.READABLE | com.oracle.truffle.api.interop.KeyInfo.MODIFIABLE | com.oracle.truffle.api.interop.KeyInfo.REMOVABLE |
                                    com.oracle.truffle.api.interop.KeyInfo.INVOCABLE;
                } else {
                    return com.oracle.truffle.api.interop.KeyInfo.INSERTABLE;
                }
            }
            return keyInfo;
        }
    }

    @Test
    public void testObjectDefaults() throws InteropException {
        ObjectDefaults v = new ObjectDefaults();
        InteropLibrary library = createLibrary(InteropLibrary.class, v);

        assertEquals(0, v.hasKeysCalls);
        assertTrue(library.hasMembers(v));
        assertEquals(1, v.hasKeysCalls);
        v.hasKeys = false;
        assertFalse(library.hasMembers(v));
        v.hasKeys = true;

        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.READABLE;
        assertTrue(library.isMemberReadable(v, ""));
        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.NONE;
        assertFalse(library.isMemberReadable(v, ""));

        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.INSERTABLE;
        assertTrue(library.isMemberInsertable(v, ""));
        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.NONE;
        assertFalse(library.isMemberInsertable(v, ""));

        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.INTERNAL;
        assertTrue(library.isMemberInternal(v, ""));
        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.NONE;
        assertFalse(library.isMemberInternal(v, ""));

        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.MODIFIABLE;
        assertTrue(library.isMemberModifiable(v, ""));
        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.NONE;
        assertFalse(library.isMemberModifiable(v, ""));

        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.INVOCABLE;
        assertTrue(library.isMemberInvocable(v, ""));
        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.NONE;
        assertFalse(library.isMemberInvocable(v, ""));

        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.REMOVABLE;
        assertTrue(library.isMemberRemovable(v, ""));
        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.NONE;
        assertFalse(library.isMemberRemovable(v, ""));

        v.keyInfo = -1;

        library.writeMember(v, "foo", "bar");
        assertEquals(1, v.writeCalls);
        assertTrue(v.members.containsKey("foo"));
        assertEquals("bar", v.members.get("foo"));

        Object result = library.readMember(v, "foo");
        assertEquals(1, v.readCalls);
        assertEquals("bar", result);

        Object identifiers = library.getMembers(v);
        InteropLibrary arrayLibrary = createLibrary(InteropLibrary.class, identifiers);
        assertEquals("foo", arrayLibrary.readArrayElement(identifiers, 0));
        assertEquals(1, arrayLibrary.getArraySize(identifiers));

        library.removeMember(v, "foo");
        assertEquals(1, v.removeCalls);
        assertTrue(v.members.isEmpty());

        // member needs to be invocable for invoke to succeed.
        library.writeMember(v, "foo", "bar");

        assertEquals("invoked foo[]", library.invokeMember(v, "foo"));
        assertEquals("invoked foo[a0]", library.invokeMember(v, "foo", "a0"));
        assertEquals("invoked foo[a0, a1]", library.invokeMember(v, "foo", "a0", "a1"));
        assertEquals(3, v.invokeCalls);

        assertNotNull(v);
        assertNoString(v);
        assertNoArray(v);
        assertNoNumber(v);
        assertNoBoolean(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);
    }

    @Test
    public void testArrayDefaults() throws InteropException {
        List<Object> array = new ArrayList<>();
        array.add("foo");
        array.add("bar");
        ArrayDefaults v = new ArrayDefaults(array);
        InteropLibrary library = createLibrary(InteropLibrary.class, v);

        assertTrue(library.hasArrayElements(v));
        v.hasSize = false;
        assertFalse(library.hasArrayElements(v));
        v.hasSize = true;

        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.READABLE;
        assertTrue(library.isArrayElementReadable(v, 0));
        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.MODIFIABLE;
        assertFalse(library.isArrayElementReadable(v, 0));

        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.INSERTABLE;
        assertTrue(library.isArrayElementInsertable(v, 0));
        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.MODIFIABLE;
        assertFalse(library.isArrayElementInsertable(v, 0));

        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.MODIFIABLE;
        assertTrue(library.isArrayElementModifiable(v, 0));
        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.READABLE;
        assertFalse(library.isArrayElementModifiable(v, 0));

        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.REMOVABLE;
        assertTrue(library.isArrayElementRemovable(v, 0));
        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.READABLE;
        assertFalse(library.isArrayElementRemovable(v, 0));

        v.keyInfo = com.oracle.truffle.api.interop.KeyInfo.NONE;
        library.writeArrayElement(v, 0, "baz");
        assertEquals(1, v.writeCalls);
        assertEquals("baz", v.array.get(0));

        assertEquals("baz", library.readArrayElement(v, 0));
        assertEquals(1, v.readCalls);

        assertEquals(2, v.array.size());

        library.removeArrayElement(v, 1);
        assertEquals(1, v.removeCalls);
        assertEquals(1, v.array.size());

        assertEquals(1, library.getArraySize(v));

        try {
            library.readArrayElement(v, 3);
            fail();
        } catch (InvalidArrayIndexException e) {
            assertEquals(3, e.getInvalidIndex());
        }

        try {
            library.writeArrayElement(v, -1, "");
            fail();
        } catch (InvalidArrayIndexException e) {
            assertEquals(-1, e.getInvalidIndex());
        }

        try {
            library.removeArrayElement(v, 3);
            fail();
        } catch (InvalidArrayIndexException e) {
            assertEquals(3, e.getInvalidIndex());
        }

        assertNotNull(v);
        assertNoString(v);
        assertNoObject(v);
        assertNoNumber(v);
        assertNoBoolean(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);
    }

    private static class ExecutableDefaults extends ProxyLegacyInteropObject {

        boolean executable = true;

        @Override
        public boolean isExecutable() {
            return executable;
        }

        @Override
        @TruffleBoundary
        public Object execute(Object[] args) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return Arrays.toString(args);
        }

    }

    @Test
    public void testExecutableDefaults() throws InteropException {
        ExecutableDefaults v = new ExecutableDefaults();
        InteropLibrary library = createLibrary(InteropLibrary.class, v);

        assertTrue(library.isExecutable(v));
        v.executable = false;
        assertFalse(library.isExecutable(v));
        v.executable = true;

        assertEquals("[]", library.execute(v));
        assertEquals("[foo]", library.execute(v, "foo"));
        assertEquals("[foo, bar]", library.execute(v, "foo", "bar"));

        assertNotNull(v);
        assertNoString(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoNumber(v);
        assertNoBoolean(v);
        assertNoNative(v);
        assertNotInstantiable(v);
    }

    private static class InstantiableDefaults extends ProxyLegacyInteropObject {

        boolean instantiable = true;

        @Override
        public boolean isInstantiable() {
            return instantiable;
        }

        @Override
        @TruffleBoundary
        public Object newInstance(Object[] args) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return Arrays.toString(args);
        }

    }

    @Test
    public void testInstantiableDefaults() throws InteropException {
        InstantiableDefaults v = new InstantiableDefaults();
        InteropLibrary library = createLibrary(InteropLibrary.class, v);

        assertTrue(library.isInstantiable(v));
        v.instantiable = false;
        assertFalse(library.isInstantiable(v));
        v.instantiable = true;

        assertEquals("[]", library.instantiate(v));
        assertEquals("[foo]", library.instantiate(v, "foo"));
        assertEquals("[foo, bar]", library.instantiate(v, "foo", "bar"));

        assertNotNull(v);
        assertNoString(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoNumber(v);
        assertNoBoolean(v);
        assertNoNative(v);
        assertNotExecutable(v);
    }

    private static class NativeDefaults extends ProxyLegacyInteropObject {

        boolean isPointer = true;

        long pointer;
        Object toNative = this;

        @Override
        public boolean isPointer() {
            return isPointer;
        }

        @Override
        public long asPointer() throws UnsupportedMessageException {
            return pointer;
        }

        @Override
        public Object toNative() throws UnsupportedMessageException {
            return toNative;
        }
    }

    @Test
    public void testNativeDefaults() throws InteropException {
        NativeDefaults v = new NativeDefaults();
        InteropLibrary library = createLibrary(InteropLibrary.class, v);

        assertTrue(library.isPointer(v));
        v.isPointer = false;
        assertFalse(library.isPointer(v));
        v.isPointer = true;

        v.pointer = 42;
        assertEquals(42, library.asPointer(v));
        library.toNative(v);

        assertNotNull(v);
        assertNoString(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoNumber(v);
        assertNoBoolean(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);
    }

    private static class LegacyValueDefaults extends ProxyLegacyInteropObject {

        boolean isNull = true;

        @Override
        public boolean isNull() {
            return isNull;
        }

    }

    @Test
    public void testValueDefaults() {
        LegacyValueDefaults v = new LegacyValueDefaults();
        InteropLibrary library = createLibrary(InteropLibrary.class, v);

        assertTrue(library.isNull(v));
        v.isNull = false;
        assertFalse(library.isNull(v));
        v.isNull = true;

        assertNoString(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoNumber(v);
        assertNoNative(v);
        assertNoBoolean(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);
    }

    private void assertNumber(Object v, boolean supportsByte, boolean supportsShort,
                    boolean supportsInt, boolean supportsLong, boolean supportsFloat, boolean supportsDouble) throws InteropException {

        Object expectedValue = v;
        if (v instanceof LegacyBoxedPrimitive) {
            expectedValue = ((LegacyBoxedPrimitive) v).value;
        }

        InteropLibrary l = createLibrary(InteropLibrary.class, v);
        assertTrue(l.isNumber(v));

        assertEquals(supportsByte, l.fitsInByte(v));
        assertEquals(supportsShort, l.fitsInShort(v));
        assertEquals(supportsInt, l.fitsInInt(v));
        assertEquals(supportsLong, l.fitsInLong(v));
        assertEquals(supportsFloat, l.fitsInFloat(v));
        assertEquals(supportsDouble, l.fitsInDouble(v));

        if (supportsByte) {
            assertEquals(((Number) expectedValue).byteValue(), l.asByte(v));
        } else {
            assertUnsupported(() -> l.asByte(v));
        }
        if (supportsShort) {
            assertEquals(((Number) expectedValue).shortValue(), l.asShort(v));
        } else {
            assertUnsupported(() -> l.asShort(v));
        }
        if (supportsInt) {
            assertEquals(((Number) expectedValue).intValue(), l.asInt(v));
        } else {
            assertUnsupported(() -> l.asInt(v));
        }
        if (supportsLong) {
            assertEquals(((Number) expectedValue).longValue(), l.asLong(v));
        } else {
            assertUnsupported(() -> l.asLong(v));
        }
        if (supportsFloat) {
            assertEquals(((Number) expectedValue).floatValue(), l.asFloat(v), 0);
        } else {
            assertUnsupported(() -> l.asFloat(v));
        }
        if (supportsDouble) {
            assertEquals(((Number) expectedValue).doubleValue(), l.asDouble(v), 0);
        } else {
            assertUnsupported(() -> l.asDouble(v));
        }

        assertNoBoolean(v);
        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoString(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);

        if (!(v instanceof LegacyBoxedPrimitive)) {
            assertNumber(new LegacyBoxedPrimitive(v), supportsByte, supportsShort, supportsInt, supportsLong, supportsFloat, supportsDouble);
        }
    }

    static class LegacyBoxedPrimitive extends ProxyLegacyInteropObject {
        Object value;

        LegacyBoxedPrimitive(Object v) {
            this.value = v;
        }

        @Override
        public boolean isBoxed() {
            return true;
        }

        @Override
        public Object unbox() throws UnsupportedMessageException {
            return value;
        }
    }

}
