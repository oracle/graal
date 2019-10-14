/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.test.polyglot.ValueHostInteropTest.Data;

public class LanguageSPIHostInteropTest extends AbstractPolyglotTest {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @Before
    public void before() {
        setupEnv();
    }

    @Test
    public void testSystemMethod() throws InteropException {
        Object system = languageEnv.lookupHostSymbol(System.class.getName());
        Object value = INTEROP.invokeMember(system, "getProperty", "file.separator");
        assertThat(value, CoreMatchers.instanceOf(String.class));
        assertThat(value, CoreMatchers.anyOf(CoreMatchers.equalTo("/"), CoreMatchers.equalTo("\\")));

        Object getProperty = INTEROP.readMember(system, "getProperty");
        assertThat(getProperty, CoreMatchers.instanceOf(TruffleObject.class));
        assertTrue("IS_EXECUTABLE", INTEROP.isExecutable(getProperty));
        value = INTEROP.execute(getProperty, "file.separator");
        assertThat(value, CoreMatchers.instanceOf(String.class));
        assertThat(value, CoreMatchers.anyOf(CoreMatchers.equalTo("/"), CoreMatchers.equalTo("\\")));
    }

    static class TestClass {
    }

    @Test
    public void conversionToClassYieldsTheClass() {
        Object javaValue = languageEnv.asHostObject(languageEnv.asGuestValue(TestClass.class));
        Object javaSymbol = languageEnv.asHostObject(languageEnv.lookupHostSymbol(TestClass.class.getName()));

        assertTrue(javaValue instanceof Class);
        assertSame("Both class objects are the same", javaValue, javaSymbol);
    }

    @Test
    public void conversionToClassNull() {
        Object meta = languageEnv.findMetaObject(languageEnv.asGuestValue(null));
        assertSame(Void.class,
                        languageEnv.asHostObject(meta));
    }

    @Test
    public void nullAsJavaObject() {
        Object nullObject = languageEnv.asGuestValue(null);
        assertTrue(languageEnv.isHostObject(nullObject));
        assertNull(languageEnv.asHostObject(nullObject));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void assertKeysAndProperties() throws InteropException {
        Data dataObj = new Data();
        TruffleObject data = (TruffleObject) languageEnv.asGuestValue(dataObj);

        Object keys = INTEROP.getMembers(data);
        List<Object> list = context.asValue(keys).as(List.class);
        assertThat(list, CoreMatchers.hasItems("x", "y", "arr", "value", "map", "dataMap", "data", "plus"));

        Method[] objectMethods = Object.class.getMethods();
        for (Method objectMethod : objectMethods) {
            assertThat("No java.lang.Object methods", list, CoreMatchers.not(CoreMatchers.hasItem(objectMethod.getName())));
        }

        keys = INTEROP.getMembers(data, true);
        list = context.asValue(keys).as(List.class);
        for (Method objectMethod : objectMethods) {
            assertThat("java.lang.Object methods", list, CoreMatchers.hasItem(objectMethod.getName()));
        }
    }

    @Test
    public void invokeJavaLangObjectFields() throws InteropException {
        Data data = new Data();
        TruffleObject obj = (TruffleObject) languageEnv.asGuestValue(data);

        Object string = INTEROP.invokeMember(obj, "toString");
        assertTrue(string instanceof String && ((String) string).startsWith(Data.class.getName() + "@"));
        Object clazz = INTEROP.invokeMember(obj, "getClass");
        assertTrue(clazz instanceof TruffleObject && languageEnv.asHostObject(clazz) == Data.class);
        assertEquals(true, INTEROP.invokeMember(obj, "equals", obj));
        assertTrue(INTEROP.invokeMember(obj, "hashCode") instanceof Integer);

        for (String m : new String[]{"notify", "notifyAll", "wait"}) {
            assertThrowsExceptionWithCause(() -> INTEROP.invokeMember(obj, m), IllegalMonitorStateException.class);
        }
    }

    @Test
    public void indexJavaArrayWithNumberTypes() throws Exception {
        int[] a = new int[]{1, 2, 3};
        TruffleObject truffleObject = (TruffleObject) languageEnv.asGuestValue(a);

        assertEquals(2, INTEROP.readArrayElement(truffleObject, 1));
        assertEquals(2, INTEROP.readArrayElement(truffleObject, 1L));

        INTEROP.writeArrayElement(truffleObject, 1, 42);
        INTEROP.writeArrayElement(truffleObject, 1L, 42);

        assertEquals(42, INTEROP.readArrayElement(truffleObject, 1));
        assertEquals(42, INTEROP.readArrayElement(truffleObject, 1L));

    }

    @Test
    public void testAsGuestValue() {
        Object object = new Object();
        // Test that asTruffleValue() returns the same as asTruffleObject() for non-primitive types:
        assertEquals(languageEnv.asGuestValue(object), languageEnv.asGuestValue(object));

        Data data = new Data();
        Object obj = languageEnv.asGuestValue(data);
        assertEquals(obj, languageEnv.asGuestValue(data));
        // Test that asTruffleValue() returns non-wraped primitives:
        object = 42;
        assertTrue(languageEnv.asGuestValue(object) == object);
        object = (byte) 42;
        assertTrue(languageEnv.asGuestValue(object) == object);
        object = (short) 42;
        assertTrue(languageEnv.asGuestValue(object) == object);
        object = 424242424242L;
        assertTrue(languageEnv.asGuestValue(object) == object);
        object = 42.42;
        assertTrue(languageEnv.asGuestValue(object) == object);
        object = true;
        assertTrue(languageEnv.asGuestValue(object) == object);
        object = "42";
        assertTrue(languageEnv.asGuestValue(object) == object);
        object = '4';
        assertTrue(languageEnv.asGuestValue(object) == object);
        object = true;
        assertTrue(languageEnv.asGuestValue(object) == object);
    }

    @Test
    public void notUnboxable() {
        assertError(() -> INTEROP.asInt(languageEnv.asGuestValue(null)), UnsupportedMessageException.class);
        assertError(() -> INTEROP.asString(languageEnv.asGuestValue(null)), UnsupportedMessageException.class);
        assertError(() -> INTEROP.asBoolean(languageEnv.asGuestValue(null)), UnsupportedMessageException.class);
    }

    @Test
    public void testPrimitiveBoxing() throws InteropException {
        assertNumberMembers((byte) 1);
        assertNumberMembers((short) 1);
        assertNumberMembers(1);
        assertNumberMembers(1L);
        assertNumberMembers(1F);
        assertNumberMembers(1D);

        assertStringMembers("foobarbaz", languageEnv.asBoxedGuestValue("foobarbaz"));
        assertStringMembers("", languageEnv.asBoxedGuestValue(""));
        assertBooleanMembers(false, languageEnv.asBoxedGuestValue(false));
        assertBooleanMembers(true, languageEnv.asBoxedGuestValue(true));
        assertCharacterMembers('a', languageEnv.asBoxedGuestValue('a'));

    }

    private void assertNumberMembers(Number testNumber) throws InteropException {
        Object guestValue = languageEnv.asBoxedGuestValue(testNumber);
        assertTrue(guestValue instanceof TruffleObject);
        assertInvocable(testNumber.intValue(), guestValue, "intValue");
        assertInvocable(testNumber.longValue(), guestValue, "longValue");
        assertInvocable(testNumber.floatValue(), guestValue, "floatValue");
        assertInvocable(testNumber.doubleValue(), guestValue, "doubleValue");
        assertInvocable(testNumber.byteValue(), guestValue, "byteValue");
        assertInvocable(testNumber.shortValue(), guestValue, "shortValue");
        assertInvocable(true, guestValue, "equals", testNumber);
        assertInvocable(testNumber.hashCode(), guestValue, "hashCode");
        assertInvocable(testNumber.toString(), guestValue, "toString");

        assertTrue(languageEnv.isHostObject(guestValue));
        assertEquals(testNumber, languageEnv.asHostObject(guestValue));
    }

    private static void assertInvocable(Object expectedValue, Object receiver, String method, Object... args) throws InteropException {
        TruffleObject o = (TruffleObject) receiver;

        assertTrue(INTEROP.isMemberReadable(receiver, method));
        assertTrue(INTEROP.isMemberInvocable(receiver, method));
        assertFalse(INTEROP.isMemberWritable(receiver, method));
        assertFalse(INTEROP.isMemberInsertable(receiver, method));
        assertFalse(INTEROP.isMemberInternal(receiver, method));
        assertFalse(INTEROP.isMemberRemovable(receiver, method));

        Object methodObject = INTEROP.readMember(o, method);
        assertEquals(expectedValue, INTEROP.execute(methodObject, args));
        assertEquals(expectedValue, INTEROP.invokeMember(receiver, method, args));
        assertHasKey(o, method);
    }

    private static void assertHasKey(Object receiver, String key) throws InteropException {
        TruffleObject o = (TruffleObject) receiver;
        Object keys = INTEROP.getMembers(o);
        long size = INTEROP.getArraySize(keys);
        List<String> allKeys = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String search = (String) INTEROP.readArrayElement(keys, i);
            if (search.equals(key)) {
                return;
            }
            allKeys.add(search);
        }
        throw new AssertionError("Key not found " + key + ". Keys are " + allKeys);
    }

    private void assertStringMembers(String unboxValue, Object stringObject) throws InteropException {
        String string = INTEROP.asString(stringObject);
        assertEquals(unboxValue, string);

        assertInvocable(string.length(), stringObject, "length");
        if (unboxValue.length() > 0) {
            assertInvocable(string.charAt(0), stringObject, "charAt", 0);
        }
        assertInvocable(string.equals(unboxValue), stringObject, "equals", unboxValue);
        assertInvocable(string.hashCode(), stringObject, "hashCode");
        assertInvocable(string.toString(), stringObject, "toString");
        assertTrue(languageEnv.isHostObject(stringObject));
        assertEquals(string, languageEnv.asHostObject(stringObject));
    }

    private void assertBooleanMembers(Object unboxValue, Object booleanObject) throws InteropException {
        Boolean b = INTEROP.asBoolean(booleanObject);
        assertEquals(unboxValue, b);

        assertInvocable(unboxValue, booleanObject, "booleanValue");
        assertInvocable(b.equals(unboxValue), booleanObject, "equals", unboxValue);
        assertInvocable(b.compareTo((Boolean) unboxValue), booleanObject, "compareTo", unboxValue);
        assertInvocable(b.hashCode(), booleanObject, "hashCode");
        assertInvocable(b.toString(), booleanObject, "toString");

        assertTrue(languageEnv.isHostObject(booleanObject));
        assertEquals(b, languageEnv.asHostObject(booleanObject));
    }

    private void assertCharacterMembers(Character unboxValue, Object charObject) throws InteropException {
        String b = INTEROP.asString(charObject);
        assertEquals(unboxValue.toString(), b);

        assertInvocable(unboxValue, charObject, "charValue");
        assertInvocable(b.equals(unboxValue.toString()), charObject, "equals", unboxValue);
        assertInvocable(b.compareTo(unboxValue.toString()), charObject, "compareTo", unboxValue);
        assertInvocable(b.hashCode(), charObject, "hashCode");
        assertInvocable(b.toString(), charObject, "toString");

        assertTrue(languageEnv.isHostObject(charObject));
        assertEquals(unboxValue, languageEnv.asHostObject(charObject));
    }

    @Test
    public void keyInfoJavaObject() {
        Object d = languageEnv.asGuestValue(new TestJavaObject());
        assertFalse(INTEROP.isMemberExisting(d, "nnoonnee"));

        assertTrue(INTEROP.isMemberExisting(d, "aField"));
        assertTrue(INTEROP.isMemberReadable(d, "aField"));
        assertTrue(INTEROP.isMemberWritable(d, "aField"));
        assertFalse(INTEROP.isMemberInvocable(d, "aField"));
        assertFalse(INTEROP.isMemberRemovable(d, "aField"));

        assertTrue(INTEROP.isMemberExisting(d, "toString"));
        assertTrue(INTEROP.isMemberReadable(d, "toString"));
        assertFalse(INTEROP.isMemberWritable(d, "toString"));
        assertTrue(INTEROP.isMemberInvocable(d, "toString"));
        assertFalse(INTEROP.isMemberRemovable(d, "toString"));
    }

    private static void assertError(Callable<?> callable, Class<? extends Exception> exception) {
        try {
            callable.call();
            fail("Expected " + exception.getSimpleName() + " but no exception was thrown");
        } catch (Exception e) {
            assertTrue(exception.toString(), exception.isInstance(e));
        }
    }

    private void assertThrowsExceptionWithCause(Callable<?> callable, Class<? extends Exception> exception) {
        try {
            callable.call();
            fail("Expected " + exception.getSimpleName() + " but no exception was thrown");
        } catch (Exception e) {
            assertTrue(languageEnv.isHostException(e));
            assertSame(exception, languageEnv.asHostException(e).getClass());
        }
    }

    @Test
    public void testIsHostFunction() throws InteropException {
        TruffleObject system = (TruffleObject) languageEnv.lookupHostSymbol(System.class.getName());
        Object exit = INTEROP.readMember(system, "exit");
        assertTrue(exit instanceof TruffleObject);
        assertFalse(languageEnv.isHostObject(exit));
        assertTrue(languageEnv.isHostFunction(exit));

        Object out = INTEROP.readMember(system, "out");
        assertTrue(exit instanceof TruffleObject);
        assertTrue(languageEnv.isHostObject(out));
        assertFalse(languageEnv.isHostFunction(out));

        assertFalse(languageEnv.isHostFunction(system));
        assertFalse(languageEnv.isHostFunction(false));
    }

    public static final class TestJavaObject {
        public int aField = 10;
    }

}
