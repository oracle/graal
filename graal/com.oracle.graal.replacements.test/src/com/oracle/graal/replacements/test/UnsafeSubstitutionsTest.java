/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.replacements.test;

import static com.oracle.graal.compiler.common.UnsafeAccess.*;
import static com.oracle.graal.replacements.UnsafeSubstitutions.*;

import java.lang.reflect.*;
import java.util.concurrent.atomic.*;

import org.junit.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.replacements.*;

/**
 * Tests the VM independent {@link UnsafeSubstitutions}.
 */
public class UnsafeSubstitutionsTest extends MethodSubstitutionTest {

    private static Object executeVarargsSafe(InstalledCode code, Object... args) {
        try {
            return code.executeVarargs(args);
        } catch (InvalidInstalledCodeException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object invokeSafe(Method method, Object receiver, Object... args) {
        method.setAccessible(true);
        try {
            return method.invoke(receiver, args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public void testSubstitution(String testMethodName, Class<?> holder, String methodName, Class<?>[] parameterTypes, Object receiver, Object[] args1, Object[] args2) {
        Method originalMethod = getMethod(holder, methodName, parameterTypes);
        Method testMethod = getMethod(testMethodName);

        // Force compilation
        InstalledCode code = getCode(getMetaAccess().lookupJavaMethod(testMethod), parse(testMethod));
        assert code != null;

        // Verify that the original method and the substitution produce the same value
        {
            Object expected = invokeSafe(originalMethod, receiver, args1);
            Object actual = invokeSafe(testMethod, null, args2);
            assertEquals(expected, actual);
        }

        // Verify that the generated code and the original produce the same value
        {
            Object expected = invokeSafe(originalMethod, receiver, args1);
            Object actual = executeVarargsSafe(code, args2);
            assertEquals(expected, actual);
        }
    }

    static long off(Object o, String name) {
        try {
            return unsafe.objectFieldOffset(o.getClass().getDeclaredField(name));
        } catch (Exception e) {
            Assert.fail(e.toString());
            return 0L;
        }
    }

    static class Foo {

        boolean z;
        byte b;
        short s;
        char c;
        int i;
        long l;
        float f;
        double d;
        Object o;

        void testGet(Field field, long offset, String getName, Object value) throws Exception {
            field.set(this, value);
            Method m1 = Unsafe.class.getDeclaredMethod(getName, Object.class, long.class);
            Method m2 = UnsafeSubstitutions.class.getDeclaredMethod(getName, Object.class, Object.class, long.class);
            Object expected = m1.invoke(unsafe, this, offset);
            Object actual = m2.invoke(null, unsafe, this, offset);
            Assert.assertEquals(expected, actual);
        }

        void testDirect(Field field, long offset, String type, Object value) throws Exception {
            if (type.equals("Boolean") || type.equals("Object")) {
                // No direct memory access for these types
                return;
            }

            long address = unsafe.allocateMemory(offset + 16);

            String getName = "get" + type;
            String putName = "put" + type;
            Method m1 = Unsafe.class.getDeclaredMethod(putName, long.class, field.getType());
            Method m2 = Unsafe.class.getDeclaredMethod(getName, long.class);

            Method m3 = UnsafeSubstitutions.class.getDeclaredMethod(putName, Object.class, long.class, field.getType());
            Method m4 = UnsafeSubstitutions.class.getDeclaredMethod(getName, Object.class, long.class);

            m1.invoke(unsafe, address + offset, value);
            Object expected = m2.invoke(unsafe, address + offset);

            m3.invoke(null, unsafe, address + offset, value);
            Object actual = m4.invoke(null, unsafe, address + offset);

            unsafe.freeMemory(address);
            Assert.assertEquals(expected, actual);
        }

        void testPut(Field field, long offset, String putName, Object value) throws Exception {
            Object initialValue = field.get(new Foo());
            field.set(this, initialValue);

            try {
                Method m1 = Unsafe.class.getDeclaredMethod(putName, Object.class, long.class, field.getType());
                Method m2 = UnsafeSubstitutions.class.getDeclaredMethod(putName, Object.class, Object.class, long.class, field.getType());
                m1.invoke(unsafe, this, offset, value);
                Object expected = field.get(this);
                m2.invoke(null, unsafe, this, offset, value);
                Object actual = field.get(this);
                Assert.assertEquals(expected, actual);
            } catch (NoSuchMethodException e) {
                if (!putName.startsWith("putOrdered")) {
                    throw e;
                }
            }
        }

        void test(String fieldName, String typeSuffix, Object value) {
            try {
                Field field = Foo.class.getDeclaredField(fieldName);
                long offset = unsafe.objectFieldOffset(field);
                testGet(field, offset, "get" + typeSuffix, value);
                testGet(field, offset, "get" + typeSuffix + "Volatile", value);
                testPut(field, offset, "put" + typeSuffix, value);
                testPut(field, offset, "put" + typeSuffix + "Volatile", value);
                testPut(field, offset, "putOrdered" + typeSuffix, value);
                testDirect(field, offset, typeSuffix, value);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    @Test
    public void testUnsafeSubstitutions() throws Exception {
        test("unsafeCompareAndSwapInt");
        test("unsafeCompareAndSwapLong");
        test("unsafeCompareAndSwapObject");

        test("unsafeGetBoolean");
        test("unsafeGetByte");
        test("unsafeGetShort");
        test("unsafeGetChar");
        test("unsafeGetInt");
        test("unsafeGetLong");
        test("unsafeGetFloat");
        test("unsafeGetDouble");
        test("unsafeGetObject");

        test("unsafePutBoolean");
        test("unsafePutByte");
        test("unsafePutShort");
        test("unsafePutChar");
        test("unsafePutInt");
        test("unsafePutFloat");
        test("unsafePutDouble");
        test("unsafePutObject");

        test("unsafeDirectMemoryRead");
        test("unsafeDirectMemoryWrite");

        AtomicInteger a1 = new AtomicInteger(42);
        AtomicInteger a2 = new AtomicInteger(42);
        assertEquals(unsafe.compareAndSwapInt(a1, off(a1, "value"), 42, 53), compareAndSwapInt(unsafe, a2, off(a2, "value"), 42, 53));
        assertEquals(a1.get(), a2.get());

        AtomicLong l1 = new AtomicLong(42);
        AtomicLong l2 = new AtomicLong(42);
        assertEquals(unsafe.compareAndSwapLong(l1, off(l1, "value"), 42, 53), compareAndSwapLong(unsafe, l2, off(l2, "value"), 42, 53));
        assertEquals(l1.get(), l2.get());

        AtomicReference<String> o1 = new AtomicReference<>("42");
        AtomicReference<String> o2 = new AtomicReference<>("42");
        assertEquals(unsafe.compareAndSwapObject(o1, off(o1, "value"), "42", "53"), compareAndSwapObject(unsafe, o2, off(o2, "value"), "42", "53"));
        assertEquals(o1.get(), o2.get());

        Foo f1 = new Foo();
        f1.test("z", "Boolean", Boolean.TRUE);
        f1.test("b", "Byte", Byte.MIN_VALUE);
        f1.test("s", "Short", Short.MAX_VALUE);
        f1.test("c", "Char", '!');
        f1.test("i", "Int", 1010010);
        f1.test("f", "Float", -34.5F);
        f1.test("l", "Long", 99999L);
        f1.test("d", "Double", 1234.5678D);
        f1.test("o", "Object", "object");
    }

    @SuppressWarnings("all")
    public static boolean unsafeCompareAndSwapInt(Unsafe unsafe, Object obj, long offset) {
        return unsafe.compareAndSwapInt(obj, offset, 0, 1);
    }

    @SuppressWarnings("all")
    public static boolean unsafeCompareAndSwapLong(Unsafe unsafe, Object obj, long offset) {
        return unsafe.compareAndSwapLong(obj, offset, 0, 1);
    }

    @SuppressWarnings("all")
    public static boolean unsafeCompareAndSwapObject(Unsafe unsafe, Object obj, long offset) {
        return unsafe.compareAndSwapObject(obj, offset, null, new Object());
    }

    @SuppressWarnings("all")
    public static boolean unsafeGetBoolean(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getBoolean(obj, offset) && unsafe.getBooleanVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static int unsafeGetByte(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getByte(obj, offset) + unsafe.getByteVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static int unsafeGetShort(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getShort(obj, offset) + unsafe.getShortVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static int unsafeGetChar(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getChar(obj, offset) + unsafe.getCharVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static int unsafeGetInt(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getInt(obj, offset) + unsafe.getIntVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static long unsafeGetLong(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getLong(obj, offset) + unsafe.getLongVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static float unsafeGetFloat(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getFloat(obj, offset) + unsafe.getFloatVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static double unsafeGetDouble(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getDouble(obj, offset) + unsafe.getDoubleVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static boolean unsafeGetObject(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getObject(obj, offset) == unsafe.getObjectVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static void unsafePutBoolean(Unsafe unsafe, Object obj, long offset, boolean value) {
        unsafe.putBoolean(obj, offset, value);
        unsafe.putBooleanVolatile(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutByte(Unsafe unsafe, Object obj, long offset, byte value) {
        unsafe.putByte(obj, offset, value);
        unsafe.putByteVolatile(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutShort(Unsafe unsafe, Object obj, long offset, short value) {
        unsafe.putShort(obj, offset, value);
        unsafe.putShortVolatile(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutChar(Unsafe unsafe, Object obj, long offset, char value) {
        unsafe.putChar(obj, offset, value);
        unsafe.putCharVolatile(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutInt(Unsafe unsafe, Object obj, long offset, int value) {
        unsafe.putInt(obj, offset, value);
        unsafe.putIntVolatile(obj, offset, value);
        unsafe.putOrderedInt(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutLong(Unsafe unsafe, Object obj, long offset, long value) {
        unsafe.putLong(obj, offset, value);
        unsafe.putLongVolatile(obj, offset, value);
        unsafe.putOrderedLong(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutFloat(Unsafe unsafe, Object obj, long offset, float value) {
        unsafe.putFloat(obj, offset, value);
        unsafe.putFloatVolatile(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutDouble(Unsafe unsafe, Object obj, long offset, double value) {
        unsafe.putDouble(obj, offset, value);
        unsafe.putDoubleVolatile(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutObject(Unsafe unsafe, Object obj, long offset, Object value) {
        unsafe.putObject(obj, offset, value);
        unsafe.putObjectVolatile(obj, offset, value);
        unsafe.putOrderedObject(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static double unsafeDirectMemoryRead(Unsafe unsafe, long address) {
        // Unsafe.getBoolean(long) and Unsafe.getObject(long) do not exist
        return unsafe.getByte(address) + unsafe.getShort(address) + unsafe.getChar(address) + unsafe.getInt(address) + unsafe.getLong(address) + unsafe.getFloat(address) + unsafe.getDouble(address);
    }

    @SuppressWarnings("all")
    public static void unsafeDirectMemoryWrite(Unsafe unsafe, long address, byte value) {
        // Unsafe.putBoolean(long) and Unsafe.putObject(long) do not exist
        unsafe.putByte(address, value);
        unsafe.putShort(address, value);
        unsafe.putChar(address, (char) value);
        unsafe.putInt(address, value);
        unsafe.putLong(address, value);
        unsafe.putFloat(address, value);
        unsafe.putDouble(address, value);
    }

    @Test
    public void testGetAndAddInt() throws Exception {
        Foo f1 = new Foo();
        Foo f2 = new Foo();
        long offset = off(f1, "i");
        Class<?>[] parameterTypes = new Class<?>[]{Object.class, long.class, int.class};
        for (int delta = Integer.MAX_VALUE - 10; delta < Integer.MAX_VALUE; delta++) {
            Object[] args1 = new Object[]{f1, offset, delta};
            Object[] args2 = new Object[]{f2, offset, delta};
            testSubstitution("getAndAddInt", Unsafe.class, "getAndAddInt", parameterTypes, unsafe, args1, args2);
        }
    }

    public static int getAndAddInt(Object obj, long offset, int delta) {
        return unsafe.getAndAddInt(obj, offset, delta);
    }

    @Test
    public void testGetAndAddLong() throws Exception {
        Foo f1 = new Foo();
        Foo f2 = new Foo();
        long offset = off(f1, "l");
        Class<?>[] parameterTypes = new Class<?>[]{Object.class, long.class, long.class};
        for (long delta = Long.MAX_VALUE - 10; delta < Long.MAX_VALUE; delta++) {
            Object[] args1 = new Object[]{f1, offset, delta};
            Object[] args2 = new Object[]{f2, offset, delta};
            testSubstitution("getAndAddLong", Unsafe.class, "getAndAddLong", parameterTypes, unsafe, args1, args2);
        }
    }

    public static long getAndAddLong(Object obj, long offset, long delta) {
        return unsafe.getAndAddLong(obj, offset, delta);
    }

    @Test
    public void testGetAndSetInt() throws Exception {
        Foo f1 = new Foo();
        Foo f2 = new Foo();
        long offset = off(f1, "i");
        Class<?>[] parameterTypes = new Class<?>[]{Object.class, long.class, int.class};
        for (int delta = Integer.MAX_VALUE - 10; delta < Integer.MAX_VALUE; delta++) {
            Object[] args1 = new Object[]{f1, offset, delta};
            Object[] args2 = new Object[]{f2, offset, delta};
            testSubstitution("getAndSetInt", Unsafe.class, "getAndSetInt", parameterTypes, unsafe, args1, args2);
        }
    }

    public static int getAndSetInt(Object obj, long offset, int newValue) {
        return unsafe.getAndSetInt(obj, offset, newValue);
    }

    @Test
    public void testGetAndSetLong() throws Exception {
        Foo f1 = new Foo();
        Foo f2 = new Foo();
        long offset = off(f1, "l");
        Class<?>[] parameterTypes = new Class<?>[]{Object.class, long.class, long.class};
        for (long newValue = Long.MAX_VALUE - 10; newValue < Long.MAX_VALUE; newValue++) {
            Object[] args1 = new Object[]{f1, offset, newValue};
            Object[] args2 = new Object[]{f2, offset, newValue};
            testSubstitution("getAndSetLong", Unsafe.class, "getAndSetLong", parameterTypes, unsafe, args1, args2);
        }
    }

    public static long getAndSetLong(Object obj, long offset, long newValue) {
        return unsafe.getAndSetLong(obj, offset, newValue);
    }

    @Test
    public void testGetAndSetObject() throws Exception {
        Foo f1 = new Foo();
        Foo f2 = new Foo();
        long offset = off(f1, "o");
        Class<?>[] parameterTypes = new Class<?>[]{Object.class, long.class, Object.class};
        for (long i = 0; i < 10; i++) {
            Object o = new Object();
            Object[] args1 = new Object[]{f1, offset, o};
            Object[] args2 = new Object[]{f2, offset, o};
            testSubstitution("getAndSetObject", Unsafe.class, "getAndSetObject", parameterTypes, unsafe, args1, args2);
            System.gc();
        }
    }

    public static Object getAndSetObject(Object obj, long offset, Object newValue) {
        return unsafe.getAndSetObject(obj, offset, newValue);
    }

}
