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
package com.oracle.truffle.api.interop.java.test;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

// Checkstyle: stop line length check
@SuppressWarnings("all")
public class TestMemberAccess {

    private final String TEST_CLASS = TestClass.class.getName();

    private final Node CREATE_NEW_NODE = Message.createNew(0).createNode();
    private final Node EXEC_NODE = Message.createExecute(0).createNode();
    private final Node UNBOX_NODE = Message.UNBOX.createNode();
    private final Node IS_BOXED_NODE = Message.IS_BOXED.createNode();
    private final Node IS_NULL_NODE = Message.IS_NULL.createNode();
    private final Node IS_EXEC_NODE = Message.IS_EXECUTABLE.createNode();
    private final Node READ_NODE = Message.READ.createNode();
    private final Node KEYS_NODE = Message.KEYS.createNode();
    private final Node KEY_INFO_NODE = Message.KEY_INFO.createNode();

    @Test
    public void testFields() throws IllegalArgumentException, IllegalAccessException, ClassNotFoundException, UnsupportedTypeException, ArityException, UnsupportedMessageException, InteropException {
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
    public void testMethods()
                    throws InvocationTargetException, IllegalAccessException, ClassNotFoundException, UnsupportedTypeException, ArityException, UnsupportedMessageException, InteropException {
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
    public void testAllTypes() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        getValueForAllTypesMethod("allTypesMethod");
        getValueForAllTypesMethod("allTypesStaticMethod");
    }

    @Test
    public void testNullParameter() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        Object bo = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Boolean_2", JavaInterop.asTruffleObject(null));
        assertEquals("Boolean parameter method executed", Boolean.class.getName(), bo);
        Object by = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Byte_2", JavaInterop.asTruffleObject(null));
        assertEquals("Byte parameter method executed", Byte.class.getName(), by);
        Object c = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Character_2", JavaInterop.asTruffleObject(null));
        assertEquals("Character parameter method executed", Character.class.getName(), c);
        Object f = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Float_2", JavaInterop.asTruffleObject(null));
        assertEquals("Float parameter method executed", Float.class.getName(), f);
        Object d = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Double_2", JavaInterop.asTruffleObject(null));
        assertEquals("Double parameter method executed", Double.class.getName(), d);
        Object i = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Integer_2", JavaInterop.asTruffleObject(null));
        assertEquals("Integer parameter method executed", Integer.class.getName(), i);
        Object l = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Long_2", JavaInterop.asTruffleObject(null));
        assertEquals("Long parameter method executed", Long.class.getName(), l);
        Object s = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_Short_2", JavaInterop.asTruffleObject(null));
        assertEquals("Short parameter method executed", Short.class.getName(), s);
        Object st = getValueFromMember("isNull__Ljava_lang_String_2Ljava_lang_String_2", JavaInterop.asTruffleObject(null));
        assertEquals("String parameter method executed", String.class.getName(), st);
    }

    @Test
    public void testKeysAndInternalKeys() throws Exception {
        TruffleObject testClass = JavaInterop.asTruffleObject(TestClass.class);
        assertKeys(testClass);
    }

    @Test
    public void testKeysAndInternalKeysOnInstance() throws Exception {
        TruffleObject instance = JavaInterop.asTruffleObject(new TestClass());
        assertKeys(instance);
    }

    @Test
    public void testUnderscoreKeys() throws Exception {
        TruffleObject testClass = JavaInterop.asTruffleObject(Test_Underscore.class);
        assertKeys(testClass);

        CallUnderscore call = JavaInterop.asJavaObject(CallUnderscore.class, testClass);

        Object obj = call.create__Lcom_oracle_truffle_api_interop_java_test_Test_1Underscore_2();
        assertNotNull("An object created", obj);
        assertTrue("Instance of my class", obj instanceof Test_Underscore);

        Object copy = call.copy__Lcom_oracle_truffle_api_interop_java_test_Test_1Underscore_2Lcom_oracle_truffle_api_interop_java_test_Test_1Underscore_2(obj);
        assertNotNull("An object copied", copy);
        assertTrue("Instance of my class again", copy instanceof Test_Underscore);

        assertEquals(obj, copy);
    }

    private void assertKeys(TruffleObject obj) throws UnsupportedMessageException {
        List<?> keys = JavaInterop.asJavaObject(List.class, ForeignAccess.sendKeys(KEYS_NODE, obj));
        Set<String> foundKeys = new HashSet<>();
        for (Object key : keys) {
            assertTrue("Is string" + key, key instanceof String);
            String keyName = (String) key;
            assertEquals("No __ in " + keyName, -1, keyName.indexOf("__"));
            int info = ForeignAccess.sendKeyInfo(KEY_INFO_NODE, obj, keyName);
            if (!KeyInfo.isInvocable(info)) {
                continue;
            }
            foundKeys.add(keyName);
        }

        int count = foundKeys.size();

        Set<String> foundInternalKeys = new HashSet<>();
        List<?> internalKeys = JavaInterop.asJavaObject(List.class, ForeignAccess.sendKeys(KEYS_NODE, obj, true));
        for (Object key : internalKeys) {
            assertTrue("Is string" + key, key instanceof String);
            String keyName = (String) key;
            int info = ForeignAccess.sendKeyInfo(KEY_INFO_NODE, obj, keyName);

            if (!keyName.contains("__")) {
                if (!KeyInfo.isInvocable(info)) {
                    continue;
                }
                assertFalse("Not internal: " + keyName, KeyInfo.isInternal(info));
                boolean found = foundKeys.remove(keyName);
                assertTrue("Non-internal key has been listed before: " + keyName, found);
            } else {
                assertTrue("Internal: " + keyName, KeyInfo.isInternal(info));
                foundInternalKeys.add(keyName);
            }
            assertTrue("Is invocable " + keyName, KeyInfo.isInvocable(info));
        }

        assertTrue("All normal keys listed in internal mode too: " + foundKeys, foundKeys.isEmpty());

        assertTrue("More than " + count + " real internals: " + foundInternalKeys, foundInternalKeys.size() >= count);
    }

    @Test
    public void testOverloaded1() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals("boolean", getValueFromMember("isOverloaded", true));
    }

    @Test
    public void testOverloaded2() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals(Boolean.class.getName(), getValueFromMember(TestClass2.class, "isOverloaded", Boolean.TRUE));
    }

    @Test
    public void testOverloaded3() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals("byte", getValueFromMember(TestClass2.class, "isOverloaded", Byte.MAX_VALUE));
    }

    @Test
    public void testOverloaded4() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals(Byte.class.getName(), getValueFromMember("isOverloaded", new Byte(Byte.MAX_VALUE)));
    }

    @Test
    public void testOverloaded5() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals("char", getValueFromMember("isOverloaded", 'a'));
    }

    @Test
    public void testOverloaded6() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals(Character.class.getName(), getValueFromMember(TestClass2.class, "isOverloaded", new Character('a')));
    }

    @Test
    public void testOverloaded7() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals("float", getValueFromMember(TestClass2.class, "isOverloaded", Float.MAX_VALUE));
    }

    @Test
    public void testOverloaded8() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals(Float.class.getName(), getValueFromMember("isOverloaded", new Float(Float.MAX_VALUE)));
    }

    @Test
    public void testOverloaded9() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals("double", getValueFromMember(TestClass2.class, "isOverloaded", Double.MAX_VALUE));
    }

    @Test
    public void testOverloaded10() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals(Double.class.getName(), getValueFromMember("isOverloaded", new Double(Double.MAX_VALUE)));
    }

    @Test
    public void testOverloaded11() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals("int", getValueFromMember("isOverloaded", Integer.MAX_VALUE));
    }

    @Test
    public void testOverloaded12() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals(Integer.class.getName(), getValueFromMember(TestClass2.class, "isOverloaded", new Integer(Integer.MAX_VALUE)));
    }

    @Test
    public void testOverloaded13() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals("long", getValueFromMember("isOverloaded", Long.MAX_VALUE));
    }

    @Test
    public void testOverloaded14() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals(Long.class.getName(), getValueFromMember(TestClass2.class, "isOverloaded", new Long(Long.MAX_VALUE)));
    }

    @Test
    public void testOverloaded15() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals("short", getValueFromMember(TestClass2.class, "isOverloaded", Short.MAX_VALUE));
    }

    @Test
    public void testOverloaded16() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals(Short.class.getName(), getValueFromMember("isOverloaded", new Short(Short.MAX_VALUE)));
    }

    @Test
    public void testOverloaded17() throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        assertEquals(String.class.getName(), getValueFromMember("isOverloaded", "testString"));
    }

    @Test
    public void testClassAsArg() throws ClassNotFoundException, InteropException {
        TruffleObject clazz = JavaInterop.asTruffleObject(String.class);
        assertEquals(String.class.getName(), getValueFromMember("classAsArg", clazz));
    }

    @Test
    public void testNewArray() throws ClassNotFoundException, InteropException {
        TruffleObject arrayClass = JavaInterop.asTruffleObject(Array.class);
        TruffleObject newInstanceMethod = (TruffleObject) ForeignAccess.send(READ_NODE, arrayClass, "newInstance");
        TruffleObject stringArray = (TruffleObject) ForeignAccess.sendExecute(EXEC_NODE, newInstanceMethod, JavaInterop.asTruffleObject(String.class), 2);
        assertTrue(JavaInterop.isArray(stringArray));
    }

    private void testForValue(String name, Object value) throws ClassNotFoundException, UnsupportedTypeException, ArityException, UnsupportedMessageException, InteropException {
        Object o = getValueFromMember(name);
        if (value == null) {
            if (o == null || (o instanceof TruffleObject && ForeignAccess.sendIsNull(IS_NULL_NODE, (TruffleObject) o))) {
                return;
            }
        }
        assertEquals(value, o);
    }

    private Object getValueFromMember(String name, Object... parameters) throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        return getValueFromMember(Class.forName(TEST_CLASS), name, parameters);
    }

    private Object getValueFromMember(Class<?> javaClazz, String name, Object... parameters) throws ClassNotFoundException, UnsupportedTypeException, InteropException {
        TruffleObject clazz = JavaInterop.asTruffleObject(javaClazz);
        Object o = ForeignAccess.sendNew(CREATE_NEW_NODE, clazz);
        try {
            o = ForeignAccess.sendRead(READ_NODE, (TruffleObject) o, name);
        } catch (UnknownIdentifierException e) {
            o = ForeignAccess.sendRead(READ_NODE, clazz, name);
        }
        if (ForeignAccess.sendIsExecutable(IS_EXEC_NODE, (TruffleObject) o)) {
            o = ForeignAccess.sendExecute(EXEC_NODE, (TruffleObject) o, parameters);
        }
        if (o instanceof TruffleObject && ForeignAccess.sendIsBoxed(IS_BOXED_NODE, (TruffleObject) o)) {
            o = ForeignAccess.sendUnbox(UNBOX_NODE, (TruffleObject) o);
        }
        return o;
    }

    private void getValueForAllTypesMethod(String method) throws ClassNotFoundException, InteropException {
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
            return "boolean";
        }

        public String isOverloaded(Byte b) {
            return Byte.class.getName();
        }

        public String isOverloaded(char c) {
            return "char";
        }

        public String isOverloaded(Double l) {
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

        public String classAsArg(Class<?> c) {
            return c.getName();
        }

    }

    public static final class TestClass2 {
        public String isOverloaded(Integer c) {
            return Integer.class.getName();
        }

        public String isOverloaded(long l) {
            return "long";
        }

        public String isOverloaded(Character c) {
            return Character.class.getName();
        }

        public String isOverloaded(double l) {
            return "double";
        }

        public String isOverloaded(Boolean b) {
            return Boolean.class.getName();
        }

        public String isOverloaded(byte b) {
            return "byte";
        }

        public String isOverloaded(float f) {
            return "float";
        }

        public String isOverloaded(int c) {
            return "int";
        }

        public String isOverloaded(Long l) {
            return Long.class.getName();
        }

        public String isOverloaded(short c) {
            return "short";
        }
    }

    public static interface CallUnderscore {
        public Object create__Lcom_oracle_truffle_api_interop_java_test_Test_1Underscore_2();

        public Object copy__Lcom_oracle_truffle_api_interop_java_test_Test_1Underscore_2Lcom_oracle_truffle_api_interop_java_test_Test_1Underscore_2(Object orig);
    }
}
