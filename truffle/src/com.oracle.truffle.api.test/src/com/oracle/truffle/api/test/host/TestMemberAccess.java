/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.interop.ArityException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.test.host.AsCollectionsTest.ListBasedTO;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class TestMemberAccess extends ProxyLanguageEnvTest {
    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

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
                } catch (UnknownMemberException e) {
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
                    } catch (UnknownMemberException e) {
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
    public void testNullParameterJNISig() throws InteropException {
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
    public void testNullParameterSig() throws InteropException {
        Object bo = getValueFromMember("isNull(java.lang.Boolean)", asTruffleObject(null));
        assertEquals("Boolean parameter method executed", Boolean.class.getName(), bo);
        Object by = getValueFromMember("isNull(java.lang.Byte)", asTruffleObject(null));
        assertEquals("Byte parameter method executed", Byte.class.getName(), by);
        Object c = getValueFromMember("isNull(java.lang.Character)", asTruffleObject(null));
        assertEquals("Character parameter method executed", Character.class.getName(), c);
        Object f = getValueFromMember("isNull(java.lang.Float)", asTruffleObject(null));
        assertEquals("Float parameter method executed", Float.class.getName(), f);
        Object d = getValueFromMember("isNull(java.lang.Double)", asTruffleObject(null));
        assertEquals("Double parameter method executed", Double.class.getName(), d);
        Object i = getValueFromMember("isNull(java.lang.Integer)", asTruffleObject(null));
        assertEquals("Integer parameter method executed", Integer.class.getName(), i);
        Object l = getValueFromMember("isNull(java.lang.Long)", asTruffleObject(null));
        assertEquals("Long parameter method executed", Long.class.getName(), l);
        Object s = getValueFromMember("isNull(java.lang.Short)", asTruffleObject(null));
        assertEquals("Short parameter method executed", Short.class.getName(), s);
        Object st = getValueFromMember("isNull(java.lang.String)", asTruffleObject(null));
        assertEquals("String parameter method executed", String.class.getName(), st);
    }

    @Test
    public void testAmbiguousSig() throws InteropException {
        assertEquals("ambiguous(Object,String)", getValueFromMember("ambiguous(java.lang.Object,java.lang.String)", "arg1", "arg2"));
        assertEquals("ambiguous(String,Object)", getValueFromMember("ambiguous(java.lang.String,java.lang.Object)", "arg1", "arg2"));
        try {
            getValueFromMember("ambiguous", "arg1", "arg2");
            fail("should have thrown");
        } catch (UnsupportedTypeException ex) {
            // expected
        }
    }

    @Test
    public void testAmbiguousSig2() throws InteropException {
        assertEquals("ambiguous2(int,Object[])", getValueFromMember("ambiguous2(int,java.lang.Object[])", 42, new ListBasedTO(Arrays.asList(43, 44))));
        assertEquals("ambiguous2(int,int[])", getValueFromMember("ambiguous2(int,int[])", 42, new ListBasedTO(Arrays.asList(43, 44))));
        try {
            getValueFromMember("ambiguous2", 42, new ListBasedTO(Arrays.asList(43, 44)));
            fail("should have thrown");
        } catch (UnsupportedTypeException ex) {
            // expected
        }
    }

    @Test
    public void testMembers() throws Exception {
        TruffleObject testClass = asTruffleHostSymbol(TestClass.class);
        assertMembers(testClass);
    }

    @Test
    public void testMembersOnInstance() throws Exception {
        TruffleObject instance = asTruffleObject(new TestClass());
        assertMembers(instance);
    }

    @Test
    public void testPublicMembers() throws UnsupportedMessageException, InvalidArrayIndexException, UnknownMemberException, UnsupportedTypeException, ArityException {
        TruffleObject instance = asTruffleObject(new TestClass());
        assertTrue(INTEROP.hasMembers(instance));
        assertFalse(INTEROP.hasDeclaredMembers(instance));

        Set<String> expectedPublicInstanceMembers = new HashSet<>();
        Set<String> expectedPublicStaticMembers = new HashSet<>();
        for (Field f : TestClass.class.getFields()) {
            String signature = f.getType().getCanonicalName() + ' ' + f.getName();
            if (!Modifier.isStatic(f.getModifiers())) {
                expectedPublicInstanceMembers.add(signature);
            } else {
                expectedPublicStaticMembers.add(signature);
            }
        }
        for (Method m : TestClass.class.getMethods()) {
            if (m.getDeclaringClass() == Object.class) {
                continue;
            }
            String signature = getTruffleExpectedSignatureString(m);
            if (!Modifier.isStatic(m.getModifiers())) {
                expectedPublicInstanceMembers.add(signature.toString());
            } else {
                expectedPublicStaticMembers.add(signature.toString());
            }
        }
        expectedPublicStaticMembers.add("java.lang.Class class"); // The static 'class' field.
        Object instanceMembers = INTEROP.getMemberObjects(instance);
        long n = INTEROP.getArraySize(instanceMembers);
        Set<String> memberSignatures = new HashSet<>();
        for (int i = 0; i < n; i++) {
            Object m = INTEROP.readArrayElement(instanceMembers, i);
            memberSignatures.add(getTruffleInteropSignatureString(m));
        }
        Set<String> missingMembers = new HashSet<>(expectedPublicInstanceMembers);
        missingMembers.removeAll(memberSignatures);
        Set<String> extraMembers = new HashSet<>(memberSignatures);
        extraMembers.removeAll(expectedPublicInstanceMembers);
        assertEquals(missingMembers.toString(), 0, missingMembers.size());
        assertEquals(extraMembers.toString(), 0, extraMembers.size());

        assertTrue(INTEROP.hasStaticReceiver(instance));
        Object staticReceiver = INTEROP.getStaticReceiver(instance);
        Object staticMembers = INTEROP.getMemberObjects(staticReceiver);
        n = INTEROP.getArraySize(staticMembers);
        Set<String> staticSignatures = new HashSet<>();
        for (int i = 0; i < n; i++) {
            Object m = INTEROP.readArrayElement(staticMembers, i);
            staticSignatures.add(getTruffleInteropSignatureString(m));
        }
        missingMembers = new HashSet<>(expectedPublicStaticMembers);
        missingMembers.removeAll(staticSignatures);
        extraMembers = new HashSet<>(staticSignatures);
        extraMembers.removeAll(expectedPublicStaticMembers);
        assertEquals(missingMembers.toString(), 0, missingMembers.size());
        assertEquals(extraMembers.toString(), 0, extraMembers.size());

        assertFalse(INTEROP.hasStaticReceiver(staticReceiver));

        for (int i = 0; i < n; i++) {
            Object m = INTEROP.readArrayElement(instanceMembers, i);
            assertTrue(INTEROP.isMember(m));
            String simpleName = INTEROP.asString(INTEROP.getMemberSimpleName(m));
            String qualifiedName = INTEROP.asString(INTEROP.getMemberQualifiedName(m));
            assertTrue(qualifiedName, qualifiedName.endsWith(simpleName));
            assertTrue(qualifiedName + " is readable", INTEROP.isMemberReadable(instance, m));
            Object value = INTEROP.readMember(instance, m);
            Assert.assertNotNull(value);
            if (INTEROP.isMemberInvocable(instance, m)) {
                String methodName = INTEROP.asString(INTEROP.getMemberSimpleName(m));
                Object signature = INTEROP.getMemberSignature(m);
                switch (methodName) {
                    case "isNull":
                        Object paramSignatureElement = INTEROP.readArrayElement(signature, 1);
                        Object paramType = INTEROP.getSignatureElementMetaObject(paramSignatureElement);
                        String paramTypeName = INTEROP.asString(INTEROP.getMetaSimpleName(paramType));
                        switch (paramTypeName) {
                            case "Boolean":
                                value = INTEROP.invokeMember(instance, m, asTruffleObject(Boolean.TRUE));
                                assertTrue(Objects.toString(value), INTEROP.isNull(value));
                                break;
                            case "Byte":
                                value = INTEROP.invokeMember(instance, m, asTruffleObject(Byte.MAX_VALUE));
                                assertTrue(Objects.toString(value), INTEROP.isNull(value));
                                break;
                            case "Character":
                                value = INTEROP.invokeMember(instance, m, asTruffleObject(Character.MAX_VALUE));
                                assertTrue(Objects.toString(value), INTEROP.isNull(value));
                                break;
                            case "Double":
                                value = INTEROP.invokeMember(instance, m, asTruffleObject(Double.MIN_VALUE));
                                assertTrue(Objects.toString(value), INTEROP.isNull(value));
                                break;
                        }
                        if (Character.isUpperCase(paramTypeName.charAt(0))) {
                            // Boxed types
                            value = INTEROP.invokeMember(instance, m, asTruffleObject(null));
                            assertEquals("java.lang." + paramTypeName, INTEROP.asString(value));
                        }
                        break;
                    case "allTypesStaticMethod":
                    case "allTypesMethod":
                        value = INTEROP.invokeMember(instance, m, true, (byte) 1, 'a', (short) 10, 42, 1234567890123456789L, 1.12e300, 2.34f, "S");
                        break;
                    case "classAsArg":
                        value = INTEROP.invokeMember(instance, m, asTruffleObject(TestClass.class));
                        break;
                    case "isOverloaded":
                    case "ambiguous":
                    case "ambiguous2":
                    case "wait":
                    case "equals":
                        value = "?";
                        break;
                    default:
                        value = INTEROP.invokeMember(instance, m);
                }
                Assert.assertNotNull(value);
                assertTrue(qualifiedName + " is not writable", !INTEROP.isMemberWritable(instance, m));
            } else {
                assertTrue(qualifiedName + " is writable", INTEROP.isMemberWritable(instance, m));
            }
        }
    }

    private static String getTruffleExpectedSignatureString(Method m) {
        StringBuilder signature = new StringBuilder();
        Class<?> retType = m.getReturnType();
        signature.append(retType.getCanonicalName());
        signature.append(' ');
        signature.append(m.getName());
        Class<?>[] parameterTypes = m.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            signature.append(' ');
            signature.append(parameterTypes[i].getCanonicalName());
        }
        return signature.toString();
    }

    private static String getTruffleInteropSignatureString(Object member) throws UnsupportedMessageException, InvalidArrayIndexException {
        StringBuilder signature = new StringBuilder();
        assertTrue(INTEROP.hasMemberSignature(member));
        Object signatureElements = INTEROP.getMemberSignature(member);
        long n = INTEROP.getArraySize(signatureElements);
        assertTrue(Long.toString(n), n >= 1);
        Object retSignature = INTEROP.readArrayElement(signatureElements, 0);
        assertTrue(INTEROP.isSignatureElement(retSignature));
        if (INTEROP.hasSignatureElementMetaObject(retSignature)) {
            signature.append(INTEROP.asString(INTEROP.getMetaQualifiedName(INTEROP.getSignatureElementMetaObject(retSignature))));
        } else {
            signature.append(Void.class.getCanonicalName());
        }
        signature.append(' ');
        signature.append(INTEROP.getMemberSimpleName(member));
        for (long i = 1; i < n; i++) {
            signature.append(' ');
            signature.append(INTEROP.asString(INTEROP.getMetaQualifiedName(INTEROP.getSignatureElementMetaObject(INTEROP.readArrayElement(signatureElements, i)))));
        }
        return signature.toString();
    }

    @Test
    public void testDeclaredMembers() throws UnsupportedMessageException {
        TruffleObject clazz = asTruffleObject(TestClass.class);
        TruffleObject instance = asTruffleObject(new TestClass());
        assertFalse(INTEROP.hasDeclaredMembers(instance));
        assertTrue(INTEROP.hasDeclaredMembers(clazz));
        Object declaredMembers = INTEROP.getDeclaredMembers(clazz);
        long n = INTEROP.getArraySize(declaredMembers);
        assertEquals(TestClass.class.getDeclaredMethods().length + TestClass.class.getDeclaredFields().length + TestClass.class.getDeclaredClasses().length, n);
    }

    private static void assertMembers(TruffleObject obj) throws UnsupportedMessageException, InvalidArrayIndexException {
        Object members = INTEROP.getMemberObjects(obj);
        long num = INTEROP.getArraySize(members);
        assertEquals(num, (int) num);
        assertTrue(Long.toString(num), num > 0);
        Set<String> foundKeys = new HashSet<>();
        for (int i = 0; i < num; i++) {
            Object member = INTEROP.readArrayElement(members, i);
            Object key = INTEROP.getMemberSimpleName(member);
            assertTrue("Is string" + key, INTEROP.isString(key));
            String keyName = INTEROP.asString(key);
            assertEquals("No __ in " + keyName, -1, keyName.indexOf("__"));
            if (!INTEROP.isMemberInvocable(obj, member)) {
                continue;
            }
            foundKeys.add(keyName);
        }

        assertFalse(foundKeys.toString(), foundKeys.isEmpty());
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
    public void testArray() throws InteropException {
        TruffleObject arrayClass = asTruffleHostSymbol(Array.class);
        TruffleObject newInstanceMethod = (TruffleObject) INTEROP.readMember(arrayClass, (Object) "newInstance");
        int arrayLength = 2;
        TruffleObject stringArray = (TruffleObject) INTEROP.execute(newInstanceMethod, asTruffleHostSymbol(String.class), arrayLength);
        assertTrue(INTEROP.hasArrayElements(stringArray));
        INTEROP.writeArrayElement(stringArray, 0, "foo");
        INTEROP.writeArrayElement(stringArray, 1, "bar");
        assertEquals(INTEROP.readMember(stringArray, (Object) "length"), arrayLength);
        TruffleObject stringArrayCopy1 = (TruffleObject) INTEROP.invokeMember(stringArray, (Object) "clone");
        TruffleObject stringArrayCopy2 = (TruffleObject) INTEROP.execute(INTEROP.readMember(stringArray, (Object) "clone"));
        for (int i = 0; i < arrayLength; i++) {
            assertEquals(INTEROP.readArrayElement(stringArray, i), INTEROP.readArrayElement(stringArrayCopy1, i));
            assertEquals(INTEROP.readArrayElement(stringArray, i), INTEROP.readArrayElement(stringArrayCopy2, i));
        }
        INTEROP.writeArrayElement(stringArrayCopy1, 1, "waz"); // Mutate copy
        assertNotEquals(INTEROP.readArrayElement(stringArray, 1), INTEROP.readArrayElement(stringArrayCopy1, 1));
    }

    @Test
    public void testArrayOutOfBoundsAccess() throws InteropException {
        Object[] array = new Object[1];
        TruffleObject arrayObject = asTruffleObject(array);
        assertTrue(INTEROP.hasArrayElements(arrayObject));
        INTEROP.readArrayElement(arrayObject, 0);
        try {
            INTEROP.readArrayElement(arrayObject, 1);
            fail();
        } catch (InvalidArrayIndexException e) {
        }
    }

    @Test
    public void testObjectReadIndex() throws InteropException {
        TruffleObject arrayObject = asTruffleObject(new TestClass());
        try {
            INTEROP.readArrayElement(arrayObject, 0);
            fail();
        } catch (UnsupportedMessageException e) {
        }
    }

    @Test
    public void testOverloadedConstructor1() throws InteropException {
        TruffleObject testClass = asTruffleHostSymbol(TestConstructor.class);
        TruffleObject testObj;
        testObj = (TruffleObject) INTEROP.instantiate(testClass);
        assertEquals(void.class.getName(), INTEROP.readMember(testObj, (Object) "ctor"));
        testObj = (TruffleObject) INTEROP.instantiate(testClass, 42);
        assertEquals(int.class.getName(), INTEROP.readMember(testObj, (Object) "ctor"));
        testObj = (TruffleObject) INTEROP.instantiate(testClass, 4.2f);
        assertEquals(float.class.getName(), INTEROP.readMember(testObj, (Object) "ctor"));
    }

    @Test
    public void testOverloadedConstructor2() throws InteropException {
        TruffleObject testClass = asTruffleHostSymbol(TestConstructor.class);
        TruffleObject testObj;
        testObj = (TruffleObject) INTEROP.instantiate(testClass, (short) 42);
        assertEquals(int.class.getName(), INTEROP.readMember(testObj, (Object) "ctor"));
        testObj = (TruffleObject) INTEROP.instantiate(testClass, 4.2f);
        // TODO GR-38632 prioritize conversion from double to float over double to int
        // assertEquals(float.class.getName(), INTEROP.readMember(testObj, "ctor"));
    }

    @Test
    public void testOverloadedConstructor3() throws InteropException {
        TruffleObject clazz = asTruffleHostSymbol(TestConstructorException.class);
        Object testObj = INTEROP.instantiate(clazz, "test", 42);
        assertTrue(testObj instanceof TruffleObject && env.asHostObject(testObj) instanceof TestConstructorException);
        assertThrowsExceptionWithCause(() -> INTEROP.instantiate(clazz, "test"), IOException.class);
    }

    @Test
    public void testIterate() throws InteropException {
        List<Object> l = new ArrayList<>();
        l.add("one");
        l.add("two");
        TruffleObject listObject = asTruffleObject(l);
        TruffleObject itFunction = (TruffleObject) INTEROP.readMember(listObject, (Object) "iterator");
        TruffleObject it = (TruffleObject) INTEROP.execute(itFunction);
        TruffleObject hasNextFunction = (TruffleObject) INTEROP.readMember(it, (Object) "hasNext");
        List<Object> returned = new ArrayList<>();
        while ((boolean) INTEROP.execute(hasNextFunction)) {
            TruffleObject nextFunction = (TruffleObject) INTEROP.readMember(it, (Object) "next");
            Object element = INTEROP.execute(nextFunction);
            returned.add(element);
        }
        assertEquals(l.size(), returned.size());
        assertEquals(l.get(0), returned.get(0));
        assertEquals(l.get(1), returned.get(1));
    }

    @Test
    public void testMethodThrowsIOException() {
        assertThrowsExceptionWithCause(() -> getValueFromMember(TestClass2.class, "methodThrowsIOException"), IOException.class);
    }

    private void testForValue(String name, Object value) throws InteropException {
        Object o = getValueFromMember(name);
        if (value == null) {
            if (o == null || (o instanceof TruffleObject && INTEROP.isNull(o))) {
                return;
            }
        }
        assertEquals(value, o);
    }

    private Object getValueFromMember(Object member, Object... parameters) throws InteropException {
        return getValueFromMember(TestClass.class, member, parameters);
    }

    private Object getValueFromMember(Class<?> javaClazz, Object member, Object... parameters) throws InteropException {
        TruffleObject clazz = asTruffleHostSymbol(javaClazz);
        Object o = INTEROP.instantiate(clazz);
        try {
            o = INTEROP.readMember(o, member);
        } catch (UnknownMemberException e) {
            o = INTEROP.readMember(clazz, member);
        }
        if (o instanceof TruffleObject && INTEROP.isExecutable(o)) {
            o = INTEROP.execute((TruffleObject) o, parameters);
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

        public String ambiguous(Object o, String s) {
            return "ambiguous(Object,String)";
        }

        public String ambiguous(String s, Object o) {
            return "ambiguous(String,Object)";
        }

        public String ambiguous2(int x, Object[] xs) {
            return "ambiguous2(int,Object[])";
        }

        public String ambiguous2(int x, int[] xs) {
            return "ambiguous2(int,int[])";
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
