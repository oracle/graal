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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

public class TestMemberAccess extends ProxyLanguageEnvTest {

    private final Node newNode = Message.NEW.createNode();
    private final Node executeNode = Message.EXECUTE.createNode();
    private final Node unboxNode = Message.UNBOX.createNode();
    private final Node isBoxedNode = Message.IS_BOXED.createNode();
    private final Node isNullNode = Message.IS_NULL.createNode();
    private final Node isExecutableNode = Message.IS_EXECUTABLE.createNode();
    private final Node readNode = Message.READ.createNode();
    private final Node keysNode = Message.KEYS.createNode();
    private final Node keyInfoNode = Message.KEY_INFO.createNode();

    @Test
    public void testFields() throws IllegalAccessException, InteropException {
        TestClass t = new TestClass();
        Field[] fields = t.getClass().getDeclaredFields();
        for (Field f : fields) {
            f.setAccessible(true);
            String name = f.getName();
            if (name.startsWith("field")) {
                boolean isPublic = (f.getModifiers() & Modifier.PUBLIC) != 0;
                boolean wasUIE = false;
                try {
                    testForValue(name, f.get(t));
                } catch (UnknownIdentifierException e) {
                    if (isPublic) {
                        throw e;
                    }
                    wasUIE = true;
                }
                if (!isPublic && !wasUIE) {
                    fail("expected UnknownIdentifierException when accessing field: " + name);
                }
            }
        }
    }

    @Test
    public void testMethods() throws InvocationTargetException, IllegalAccessException, InteropException {
        TestClass t = new TestClass();
        Method[] methods = t.getClass().getDeclaredMethods();
        for (Method m : methods) {
            if (m.getParameterCount() == 0) {
                m.setAccessible(true);
                String name = m.getName();
                if (name.startsWith("method")) {
                    boolean isPublic = (m.getModifiers() & Modifier.PUBLIC) != 0;
                    boolean wasUIE = false;
                    try {
                        testForValue(name, m.invoke(t));
                    } catch (UnknownIdentifierException e) {
                        if (isPublic) {
                            throw e;
                        }
                        wasUIE = true;
                    }
                    if (!isPublic && !wasUIE) {
                        fail("expected UnknownIdentifierException when accessing method: " + name);
                    }
                }
            }
        }
    }

    @Test
    public void testAllTypes() throws InteropException {
        getValueForAllTypesMethod("allTypesMethod");
        getValueForAllTypesMethod("allTypesStaticMethod");
    }

    @Test
    public void testNullParameter() throws InteropException {
        Object bo = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Boolean_2", asTruffleObject(null));
        assertEquals("Boolean parameter method executed", Boolean.class.getName(), bo);
        Object by = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Byte_2", asTruffleObject(null));
        assertEquals("Byte parameter method executed", Byte.class.getName(), by);
        Object c = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Character_2", asTruffleObject(null));
        assertEquals("Character parameter method executed", Character.class.getName(), c);
        Object f = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Float_2", asTruffleObject(null));
        assertEquals("Float parameter method executed", Float.class.getName(), f);
        Object d = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Double_2", asTruffleObject(null));
        assertEquals("Double parameter method executed", Double.class.getName(), d);
        Object i = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Integer_2", asTruffleObject(null));
        assertEquals("Integer parameter method executed", Integer.class.getName(), i);
        Object l = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Long_2", asTruffleObject(null));
        assertEquals("Long parameter method executed", Long.class.getName(), l);
        Object s = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Short_2", asTruffleObject(null));
        assertEquals("Short parameter method executed", Short.class.getName(), s);
        Object st = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_String_2", asTruffleObject(null));
        assertEquals("String parameter method executed", String.class.getName(), st);
    }

    @Test
    public void testKeysAndInternalKeys() throws Exception {
        TruffleObject testClass = asTruffleHostSymbol(TestClass.class);
        assertKeys(testClass);
    }

    @Test
    public void testKeysAndInternalKeysOnInstance() throws Exception {
        TruffleObject instance = asTruffleObject(new TestClass());
        assertKeys(instance);
    }

    private void assertKeys(TruffleObject obj) throws UnsupportedMessageException {
        List<?> keys = asJavaObject(List.class, ForeignAccess.sendKeys(keysNode, obj));
        Set<String> foundKeys = new HashSet<>();
        for (Object key : keys) {
            assertTrue("Is string" + key, key instanceof String);
            String keyName = (String) key;
            assertEquals("No __ in " + keyName, -1, keyName.indexOf("__"));
            int info = ForeignAccess.sendKeyInfo(keyInfoNode, obj, keyName);
            if (!KeyInfo.isInvocable(info)) {
                continue;
            }
            foundKeys.add(keyName);
        }

        Set<String> foundInternalKeys = new HashSet<>();
        List<?> internalKeys = asJavaObject(List.class, ForeignAccess.sendKeys(keysNode, obj, true));
        for (Object key : internalKeys) {
            assertTrue("Is string" + key, key instanceof String);
            String keyName = (String) key;
            int info = ForeignAccess.sendKeyInfo(keyInfoNode, obj, keyName);

            if (!keyName.contains("__")) {
                if (!KeyInfo.isInvocable(info)) {
                    continue;
                }
                assertTrue("Not internal: " + keyName, !KeyInfo.isInternal(info) || isObjectMethodName(keyName));
                boolean found = foundKeys.remove(keyName);
                assertTrue("Non-internal key has been listed before: " + keyName, found || isObjectMethodName(keyName));
            } else {
                assertTrue("Internal: " + keyName, KeyInfo.isInternal(info));
                foundInternalKeys.add(keyName);
            }
            assertTrue("Is invocable " + keyName, KeyInfo.isInvocable(info));
        }

        assertTrue("All normal keys listed in internal mode too: " + foundKeys, foundKeys.isEmpty());

        assertTrue("Unexpected internal keys: " + foundInternalKeys, foundInternalKeys.isEmpty());
    }

    private static boolean isObjectMethodName(String name) {
        switch (name) {
            case "notify":
            case "notifyAll":
            case "wait":
            case "hashCode":
            case "equals":
            case "toString":
            case "getClass":
                return true;
            default:
                return false;
        }
    }

    @Test
    public void testOverloaded1() throws InteropException {
        assertEquals("boolean", getValueFromMember("isOverloaded", true));
    }

    @Test
    public void testOverloaded2() throws InteropException {
        assertEquals(Boolean.class.getName(), getValueFromMember(TestClass2.class, "isOverloaded", Boolean.TRUE));
    }

    @Test
    public void testOverloaded3() throws InteropException {
        assertEquals(byte.class.getName(), getValueFromMember(TestClass2.class, "isOverloaded", Byte.MAX_VALUE));
    }

    @Test
    public void testOverloaded4() throws InteropException {
        assertEquals(Byte.class.getName(), getValueFromMember("isOverloaded", Byte.valueOf(Byte.MAX_VALUE)));
    }

    @Test
    public void testOverloaded5() throws InteropException {
        assertEquals(char.class.getName(), getValueFromMember("isOverloaded", 'a'));
    }

    @Test
    public void testOverloaded6() throws InteropException {
        assertEquals(Character.class.getName(), getValueFromMember(TestClass2.class, "isOverloaded", Character.valueOf('a')));
    }

    @Test
    public void testOverloaded7() throws InteropException {
        assertEquals(float.class.getName(), getValueFromMember(TestClass2.class, "isOverloaded", Float.MAX_VALUE));
    }

    @Test
    public void testOverloaded8() throws InteropException {
        assertEquals(Float.class.getName(), getValueFromMember("isOverloaded", Float.valueOf(Float.MAX_VALUE)));
    }

    @Test
    public void testOverloaded9() throws InteropException {
        assertEquals(double.class.getName(), getValueFromMember(TestClass2.class, "isOverloaded", Double.MAX_VALUE));
    }

    @Test
    public void testOverloaded10() throws InteropException {
        assertEquals(Double.class.getName(), getValueFromMember("isOverloaded", Double.valueOf(Double.MAX_VALUE)));
    }

    @Test
    public void testOverloaded11() throws InteropException {
        assertEquals(int.class.getName(), getValueFromMember(TestClass2.class, "isOverloaded", Integer.MAX_VALUE));
    }

    @Test
    public void testOverloaded12() throws InteropException {
        assertEquals(Integer.class.getName(), getValueFromMember("isOverloaded", Integer.MAX_VALUE));
    }

    @Test
    public void testOverloaded13() throws InteropException {
        assertEquals(long.class.getName(), getValueFromMember(TestClass2.class, "isOverloaded", Long.MAX_VALUE));
    }

    @Test
    public void testOverloaded14() throws InteropException {
        assertEquals(Long.class.getName(), getValueFromMember("isOverloaded", Long.MAX_VALUE));
    }

    @Test
    public void testOverloaded15() throws InteropException {
        assertEquals(short.class.getName(), getValueFromMember(TestClass2.class, "isOverloaded", Short.MAX_VALUE));
    }

    @Test
    public void testOverloaded16() throws InteropException {
        assertEquals(Short.class.getName(), getValueFromMember("isOverloaded", Short.MAX_VALUE));
    }

    @Test
    public void testOverloaded17() throws InteropException {
        assertEquals(String.class.getName(), getValueFromMember("isOverloaded", "testString"));
    }

    @Test
    public void testClassAsArg() throws InteropException {
        TruffleObject clazz = asTruffleHostSymbol(String.class);
        assertEquals(String.class.getName(), getValueFromMember("classAsArg", clazz));
    }

    @Test
    public void testNewArray() throws InteropException {
        TruffleObject arrayClass = asTruffleHostSymbol(Array.class);
        TruffleObject newInstanceMethod = (TruffleObject) ForeignAccess.send(readNode, arrayClass, "newInstance");
        TruffleObject stringArray = (TruffleObject) ForeignAccess.sendExecute(executeNode, newInstanceMethod, asTruffleHostSymbol(String.class), 2);
        assertTrue(HostInteropTest.isArray(stringArray));
    }

    @Test
    public void testArrayOutOfBoundsAccess() throws InteropException {
        Object[] array = new Object[1];
        TruffleObject arrayObject = asTruffleObject(array);
        assertTrue(HostInteropTest.isArray(arrayObject));
        ForeignAccess.sendRead(readNode, arrayObject, 0);
        try {
            ForeignAccess.sendRead(readNode, arrayObject, 1);
            fail();
        } catch (UnknownIdentifierException e) {
        }
    }

    @Test
    public void testObjectReadIndex() throws InteropException {
        TruffleObject arrayObject = asTruffleObject(new TestClass());
        try {
            ForeignAccess.sendRead(readNode, arrayObject, 0);
            fail();
        } catch (UnsupportedMessageException e) {
        }
    }

    @Test
    public void testOverloadedConstructor1() throws InteropException {
        TruffleObject testClass = asTruffleHostSymbol(TestConstructor.class);
        TruffleObject testObj;
        testObj = (TruffleObject) ForeignAccess.sendNew(newNode, testClass);
        assertEquals(void.class.getName(), ForeignAccess.sendRead(readNode, testObj, "ctor"));
        testObj = (TruffleObject) ForeignAccess.sendNew(newNode, testClass, 42);
        assertEquals(int.class.getName(), ForeignAccess.sendRead(readNode, testObj, "ctor"));
        testObj = (TruffleObject) ForeignAccess.sendNew(newNode, testClass, 4.2f);
        assertEquals(float.class.getName(), ForeignAccess.sendRead(readNode, testObj, "ctor"));
    }

    @Test
    public void testOverloadedConstructor2() throws InteropException {
        TruffleObject testClass = asTruffleHostSymbol(TestConstructor.class);
        TruffleObject testObj;
        testObj = (TruffleObject) ForeignAccess.sendNew(newNode, testClass, (short) 42);
        assertEquals(int.class.getName(), ForeignAccess.sendRead(readNode, testObj, "ctor"));
        testObj = (TruffleObject) ForeignAccess.sendNew(newNode, testClass, 4.2f);
        // TODO prioritize conversion from double to float over double to int
        // assertEquals(float.class.getName(), ForeignAccess.sendRead(readNode, testObj, "ctor"));
    }

    @Test
    public void testOverloadedConstructor3() throws InteropException {
        TruffleObject clazz = asTruffleHostSymbol(TestConstructorException.class);
        Object testObj = ForeignAccess.sendNew(Message.NEW.createNode(), clazz, "test", 42);
        assertTrue(testObj instanceof TruffleObject && env.asHostObject(testObj) instanceof TestConstructorException);
        HostInteropTest.assertThrowsExceptionWithCause(() -> ForeignAccess.sendNew(Message.NEW.createNode(), clazz, "test"), IOException.class);
    }

    @Test
    public void testIterate() throws InteropException {
        List<Object> l = new ArrayList<>();
        l.add("one");
        l.add("two");
        TruffleObject listObject = asTruffleObject(l);
        TruffleObject itFunction = (TruffleObject) ForeignAccess.sendRead(readNode, listObject, "iterator");
        TruffleObject it = (TruffleObject) ForeignAccess.sendExecute(executeNode, itFunction);
        TruffleObject hasNextFunction = (TruffleObject) ForeignAccess.sendRead(readNode, it, "hasNext");
        List<Object> returned = new ArrayList<>();
        while ((boolean) ForeignAccess.sendExecute(executeNode, hasNextFunction)) {
            TruffleObject nextFunction = (TruffleObject) ForeignAccess.sendRead(readNode, it, "next");
            Object element = ForeignAccess.sendExecute(executeNode, nextFunction);
            returned.add(element);
        }
        assertEquals(l.size(), returned.size());
        assertEquals(l.get(0), returned.get(0));
        assertEquals(l.get(1), returned.get(1));
    }

    @Test
    public void testMethodThrowsIOException() {
        HostInteropTest.assertThrowsExceptionWithCause(() -> getValueFromMember(TestClass2.class, "methodThrowsIOException"), IOException.class);
    }

    private void testForValue(String name, Object value) throws InteropException {
        Object o = getValueFromMember(name);
        if (value == null) {
            if (o == null || (o instanceof TruffleObject && ForeignAccess.sendIsNull(isNullNode, (TruffleObject) o))) {
                return;
            }
        }
        assertEquals(value, o);
    }

    private Object getValueFromMember(String name, Object... parameters) throws InteropException {
        return getValueFromMember(TestClass.class, name, parameters);
    }

    private Object getValueFromMember(Class<?> javaClazz, String name, Object... parameters) throws InteropException {
        TruffleObject clazz = asTruffleHostSymbol(javaClazz);
        Object o = ForeignAccess.sendNew(newNode, clazz);
        try {
            o = ForeignAccess.sendRead(readNode, (TruffleObject) o, name);
        } catch (UnknownIdentifierException e) {
            o = ForeignAccess.sendRead(readNode, clazz, name);
        }
        if (o instanceof TruffleObject && ForeignAccess.sendIsExecutable(isExecutableNode, (TruffleObject) o)) {
            o = ForeignAccess.sendExecute(executeNode, (TruffleObject) o, parameters);
        }
        if (o instanceof TruffleObject && ForeignAccess.sendIsBoxed(isBoxedNode, (TruffleObject) o)) {
            o = ForeignAccess.sendUnbox(unboxNode, (TruffleObject) o);
        }
        return o;
    }

    private void getValueForAllTypesMethod(String method) throws InteropException {
        boolean bo = true;
        byte bt = 127;
        char c = 'a';
        short sh = 1234;
        int i = Integer.MAX_VALUE;
        long l = Long.MAX_VALUE;
        double d = Double.MAX_VALUE;
        float f = Float.MAX_VALUE;
        String s = "testString";
        Object o = getValueFromMember(method, new Object[]{bo, bt, c, sh, i, l, d, f, s});
        assertEquals("" + bo + bt + c + sh + i + l + d + f + s, o);
    }

    @SuppressWarnings("unused")
    public static class TestClass {

        public static boolean fieldStaticBoolean = true;
        public static byte fieldStaticByte = Byte.MAX_VALUE;
        public static char fieldStaticChar = 'a';
        public static short fieldStaticShort = Short.MAX_VALUE;
        public static int fieldStaticInteger = Integer.MAX_VALUE;
        public static long fieldStaticLong = Long.MAX_VALUE;
        public static double fieldStaticDouble = 1.1;
        public static float fieldStaticFloat = 1.1f;
        public static String fieldStaticString = "a static string";

        public boolean fieldBoolean = true;
        public byte fieldByte = Byte.MAX_VALUE;
        public char fieldChar = 'a';
        public short fieldShort = Short.MAX_VALUE;
        public int fieldInteger = Integer.MAX_VALUE;
        public long fieldLong = Long.MAX_VALUE;
        public double fieldDouble = 1.1;
        public float fieldFloat = 1.1f;
        public String fieldString = "a string";

        public static Boolean fieldStaticBooleanObject = true;
        public static Byte fieldStaticByteObject = Byte.MAX_VALUE;
        public static Character fieldStaticCharObject = 'a';
        public static Short fieldStaticShortObject = Short.MAX_VALUE;
        public static Integer fieldStaticIntegerObject = Integer.MAX_VALUE;
        public static Long fieldStaticLongObject = Long.MAX_VALUE;
        public static Double fieldStaticDoubleObject = Double.MAX_VALUE;
        public static Float fieldStaticFloatObject = Float.MAX_VALUE;

        public Boolean fieldBooleanObject = true;
        public Byte fieldByteObject = Byte.MAX_VALUE;
        public Character fieldCharObject = 'a';
        public Short fieldShortObject = Short.MAX_VALUE;
        public Integer fieldIntegerObject = Integer.MAX_VALUE;
        public Long fieldLongObject = Long.MAX_VALUE;
        public Double fieldDoubleObject = Double.MAX_VALUE;
        public Float fieldFloatObject = Float.MAX_VALUE;

        public static Double fieldStaticNaNObject = Double.NaN;
        public static double fieldStaticNaN = Double.NaN;

        private static String fieldPrivateStaticString = "private static string";
        protected static String fieldProtectedStaticString = "protected static string";
        static String fieldPackageStaticString = "package static string";
        private String fieldPrivateString = "private string";
        protected String fieldProtectedString = "protected string";
        String fieldPackageString = "package string";

        private static int fieldPrivateStaticInt = Integer.MAX_VALUE;
        protected static int fieldProtectedStaticInt = Integer.MAX_VALUE;
        static int fieldPackageStaticInt = Integer.MAX_VALUE;
        private int fieldPrivateInt = Integer.MAX_VALUE;
        protected int fieldProtectedInt = Integer.MAX_VALUE;
        int fieldPackageInt = Integer.MAX_VALUE;

        public static boolean methodStaticBoolean() {
            return true;
        }

        public static byte methodStaticByte() {
            return Byte.MAX_VALUE;
        }

        public static char methodStaticChar() {
            return 'a';
        }

        public static short methodStaticShort() {
            return Short.MAX_VALUE;
        }

        public static int methodStaticInteger() {
            return Integer.MAX_VALUE;
        }

        public static long methodStaticLong() {
            return Long.MAX_VALUE;
        }

        public static double methodStaticDouble() {
            return Double.MAX_VALUE;
        }

        public static float methodStaticFloat() {
            return Float.MAX_VALUE;
        }

        public static String methodStaticString() {
            return "a static string";
        }

        public boolean methodBoolean() {
            return true;
        }

        public byte methodByte() {
            return Byte.MAX_VALUE;
        }

        public char methodChar() {
            return 'a';
        }

        public short methodShort() {
            return Short.MAX_VALUE;
        }

        public int methodInteger() {
            return Integer.MAX_VALUE;
        }

        public long methodLong() {
            return Long.MAX_VALUE;
        }

        public double methodDouble() {
            return Double.MAX_VALUE;
        }

        public float methodFloat() {
            return Float.MAX_VALUE;
        }

        public String methodString() {
            return "string";
        }

        public static Boolean methodStaticBooleanObject() {
            return true;
        }

        public static Byte methodStaticByteObject() {
            return Byte.MAX_VALUE;
        }

        public static Character methodStaticCharObject() {
            return 'a';
        }

        public static Short methodStaticShortObject() {
            return Short.MAX_VALUE;
        }

        public static Integer methodStaticIntegerObject() {
            return Integer.MAX_VALUE;
        }

        public static Long methodStaticLongObject() {
            return Long.MAX_VALUE;
        }

        public static Double methodStaticDoubleObject() {
            return Double.MAX_VALUE;
        }

        public static Float methodStaticFloatObject() {
            return Float.MAX_VALUE;
        }

        public Boolean methodBooleanObject() {
            return true;
        }

        public Byte methodByteObject() {
            return Byte.MAX_VALUE;
        }

        public Character methodCharObject() {
            return 'a';
        }

        public Short methodShortObject() {
            return Short.MAX_VALUE;
        }

        public Integer methodIntegerObject() {
            return Integer.MAX_VALUE;
        }

        public Long methodLongObject() {
            return Long.MAX_VALUE;
        }

        public Double methodDoubleObject() {
            return Double.MAX_VALUE;
        }

        public Float methodFloatObject() {
            return Float.MAX_VALUE;
        }

        public static Object methodStaticReturnsNull() {
            return null;
        }

        public Object methodReturnsNull() {
            return null;
        }

        private static String methodPrivateStaticString() {
            return fieldPrivateStaticString;
        }

        protected static String methodProtectedStaticString() {
            return fieldProtectedStaticString;
        }

        static String methodPackageStaticString() {
            return fieldPackageStaticString;
        }

        private String methodPrivateString() {
            return fieldPrivateString;
        }

        protected String methodProtectedString() {
            return fieldProtectedString;
        }

        String methodPackageString() {
            return fieldPackageString;
        }

        private static int methodPrivateStaticInt() {
            return fieldPrivateStaticInt;
        }

        protected static int methodProtectedStaticInt() {
            return fieldProtectedStaticInt;
        }

        static int methodPackageStaticInt() {
            return fieldPackageStaticInt;
        }

        private int methodPrivateInt() {
            return fieldPrivateInt;
        }

        protected int methodProtectedInt() {
            return fieldProtectedInt;
        }

        int methodPackageInt() {
            return fieldPackageInt;
        }

        public static Object fieldStaticNullObject = null;
        public Object fieldNullObject = null;

        public String allTypesMethod(boolean bo, byte bt, char ch, short sh, int in, long lo, double db, float fl, String st) {
            return "" + bo + bt + ch + sh + in + lo + db + fl + st;
        }

        public static Object allTypesStaticMethod(boolean bo, byte bt, char ch, short sh, int in, long lo, double db, float fl, String st) {
            return "" + bo + bt + ch + sh + in + lo + db + fl + st;
        }

        public String isNull(Boolean b) {
            return b == null ? Boolean.class.getName() : null;
        }

        public String isNull(Byte b) {
            return b == null ? Byte.class.getName() : null;
        }

        public String isNull(Character c) {
            return c == null ? Character.class.getName() : null;
        }

        public String isNull(Double d) {
            return d == null ? Double.class.getName() : null;
        }

        public String isNull(Float f) {
            return f == null ? Float.class.getName() : null;
        }

        public String isNull(Integer i) {
            return i == null ? Integer.class.getName() : null;
        }

        public String isNull(Long l) {
            return l == null ? Long.class.getName() : null;
        }

        public String isNull(Short s) {
            return s == null ? Short.class.getName() : null;
        }

        public String isNull(String s) {
            return s == null ? String.class.getName() : null;
        }

        public String isNull(boolean b) {
            throw new IllegalStateException("should not reach here");
        }

        public String isNull(byte b) {
            throw new IllegalStateException("should not reach here");
        }

        public String isNull(char c) {
            throw new IllegalStateException("should not reach here");
        }

        public String isNull(double d) {
            throw new IllegalStateException("should not reach here");
        }

        public String isNull(float f) {
            throw new IllegalStateException("should not reach here");
        }

        public String isNull(int i) {
            throw new IllegalStateException("should not reach here");
        }

        public String isNull(long l) {
            throw new IllegalStateException("should not reach here");
        }

        public String isNull(short s) {
            throw new IllegalStateException("should not reach here");
        }

        public String isOverloaded(boolean b) {
            return boolean.class.getName();
        }

        public String isOverloaded(Byte b) {
            return Byte.class.getName();
        }

        public String isOverloaded(char c) {
            return char.class.getName();
        }

        public String isOverloaded(Double d) {
            return Double.class.getName();
        }

        public String isOverloaded(Float f) {
            return Float.class.getName();
        }

        public String isOverloaded(Short c) {
            return Short.class.getName();
        }

        public String isOverloaded(String s) {
            return String.class.getName();
        }

        public String isOverloaded(Integer i) {
            return Integer.class.getName();
        }

        public String isOverloaded(Long l) {
            return Long.class.getName();
        }

        public String classAsArg(Class<?> c) {
            return c.getName();
        }
    }

    @SuppressWarnings({"unused", "static-method"})
    public static final class TestClass2 {
        public String isOverloaded(Integer c) {
            return Integer.class.getName();
        }

        public String isOverloaded(long l) {
            return long.class.getName();
        }

        public String isOverloaded(Character c) {
            return Character.class.getName();
        }

        public String isOverloaded(double l) {
            return double.class.getName();
        }

        public String isOverloaded(Boolean b) {
            return Boolean.class.getName();
        }

        public String isOverloaded(byte b) {
            return byte.class.getName();
        }

        public String isOverloaded(float f) {
            return float.class.getName();
        }

        public String isOverloaded(int c) {
            return int.class.getName();
        }

        public String isOverloaded(Long l) {
            return Long.class.getName();
        }

        public String isOverloaded(short c) {
            return short.class.getName();
        }

        public void methodThrowsIOException() throws IOException {
            throw new IOException();
        }
    }

    @SuppressWarnings({"unused"})
    public static final class TestConstructor {
        private int i;
        private float f;
        public String ctor;

        public TestConstructor() {
            this.ctor = void.class.getName();
        }

        public TestConstructor(int i) {
            this.i = i;
            this.ctor = int.class.getName();
        }

        public TestConstructor(float f) {
            this.f = f;
            this.ctor = float.class.getName();
        }
    }

    @SuppressWarnings({"unused"})
    public static class TestConstructorException {
        public TestConstructorException(String s) throws IOException {
            throw new IOException();
        }

        public TestConstructorException(String s, int i) {
        }
    }

    public interface CallUnderscore {
        // Checkstyle: stop
        Object create__Lcom_oracle_truffle_api_interop_java_test_Test_1Underscore_2();

        Object copy__Lcom_oracle_truffle_api_interop_java_test_Test_1Underscore_2Lcom_oracle_truffle_api_interop_java_test_Test_1Underscore_2(Object orig);
        // Checkstyle: resume
    }
}
