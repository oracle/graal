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

import org.graalvm.polyglot.PolyglotException;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ValueHostInteropTest.ArrayTruffleObject;
import com.oracle.truffle.api.test.polyglot.ValueHostInteropTest.Data;

/**
 * To be removed {@link ForeignAccess} deprecated API.
 */
@SuppressWarnings("deprecation")
public class LanguageSPIHostInteropLegacyTest extends AbstractPolyglotTest {

    @Before
    public void before() {
        setupEnv();
    }

    @Test
    public void testSystemMethod() throws InteropException {
        TruffleObject system = (TruffleObject) languageEnv.lookupHostSymbol(System.class.getName());
        Object value = com.oracle.truffle.api.interop.ForeignAccess.sendInvoke(com.oracle.truffle.api.interop.Message.INVOKE.createNode(), system, "getProperty", "file.separator");
        assertThat(value, CoreMatchers.instanceOf(String.class));
        assertThat(value, CoreMatchers.anyOf(CoreMatchers.equalTo("/"), CoreMatchers.equalTo("\\")));

        Object getProperty = com.oracle.truffle.api.interop.ForeignAccess.sendRead(com.oracle.truffle.api.interop.Message.READ.createNode(), system, "getProperty");
        assertThat(getProperty, CoreMatchers.instanceOf(TruffleObject.class));
        assertTrue("IS_EXECUTABLE", com.oracle.truffle.api.interop.ForeignAccess.sendIsExecutable(com.oracle.truffle.api.interop.Message.IS_EXECUTABLE.createNode(), (TruffleObject) getProperty));
        value = com.oracle.truffle.api.interop.ForeignAccess.sendExecute(com.oracle.truffle.api.interop.Message.EXECUTE.createNode(), (TruffleObject) getProperty, "file.separator");
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
    public void assertKeysAndProperties() {
        Data dataObj = new Data();
        TruffleObject data = (TruffleObject) languageEnv.asGuestValue(dataObj);

        TruffleObject keys = sendKeys(data);
        List<Object> list = context.asValue(keys).as(List.class);
        assertThat(list, CoreMatchers.hasItems("x", "y", "arr", "value", "map", "dataMap", "data", "plus"));

        Method[] objectMethods = Object.class.getMethods();
        for (Method objectMethod : objectMethods) {
            assertThat("No java.lang.Object methods", list, CoreMatchers.not(CoreMatchers.hasItem(objectMethod.getName())));
        }

        keys = sendKeys(data, true);
        list = context.asValue(keys).as(List.class);
        for (Method objectMethod : objectMethods) {
            assertThat("java.lang.Object methods", list, CoreMatchers.hasItem(objectMethod.getName()));
        }
    }

    @Test
    public void invokeJavaLangObjectFields() throws InteropException {
        Data data = new Data();
        TruffleObject obj = (TruffleObject) languageEnv.asGuestValue(data);

        Object string = com.oracle.truffle.api.interop.ForeignAccess.sendInvoke(com.oracle.truffle.api.interop.Message.INVOKE.createNode(), obj, "toString");
        assertTrue(string instanceof String && ((String) string).startsWith(Data.class.getName() + "@"));
        Object clazz = com.oracle.truffle.api.interop.ForeignAccess.sendInvoke(com.oracle.truffle.api.interop.Message.INVOKE.createNode(), obj, "getClass");
        assertTrue(clazz instanceof TruffleObject && languageEnv.asHostObject(clazz) == Data.class);
        assertEquals(true, com.oracle.truffle.api.interop.ForeignAccess.sendInvoke(com.oracle.truffle.api.interop.Message.INVOKE.createNode(), obj, "equals", obj));
        assertTrue(com.oracle.truffle.api.interop.ForeignAccess.sendInvoke(com.oracle.truffle.api.interop.Message.INVOKE.createNode(), obj, "hashCode") instanceof Integer);

        for (String m : new String[]{"notify", "notifyAll", "wait"}) {
            assertThrowsExceptionWithCause(() -> com.oracle.truffle.api.interop.ForeignAccess.sendInvoke(com.oracle.truffle.api.interop.Message.INVOKE.createNode(), obj, m),
                            IllegalMonitorStateException.class);
        }
    }

    @Test
    public void indexJavaArrayWithNumberTypes() throws Exception {
        int[] a = new int[]{1, 2, 3};
        TruffleObject truffleObject = (TruffleObject) languageEnv.asGuestValue(a);

        assertEquals(2, com.oracle.truffle.api.interop.ForeignAccess.sendRead(com.oracle.truffle.api.interop.Message.READ.createNode(), truffleObject, 1));
        assertEquals(2, com.oracle.truffle.api.interop.ForeignAccess.sendRead(com.oracle.truffle.api.interop.Message.READ.createNode(), truffleObject, 1.0));
        assertEquals(2, com.oracle.truffle.api.interop.ForeignAccess.sendRead(com.oracle.truffle.api.interop.Message.READ.createNode(), truffleObject, 1L));

        com.oracle.truffle.api.interop.ForeignAccess.sendWrite(com.oracle.truffle.api.interop.Message.WRITE.createNode(), truffleObject, 1, 42);
        com.oracle.truffle.api.interop.ForeignAccess.sendWrite(com.oracle.truffle.api.interop.Message.WRITE.createNode(), truffleObject, 1.0, 42);
        com.oracle.truffle.api.interop.ForeignAccess.sendWrite(com.oracle.truffle.api.interop.Message.WRITE.createNode(), truffleObject, 1L, 42);

        assertEquals(42, com.oracle.truffle.api.interop.ForeignAccess.sendRead(com.oracle.truffle.api.interop.Message.READ.createNode(), truffleObject, 1));
        assertEquals(42, com.oracle.truffle.api.interop.ForeignAccess.sendRead(com.oracle.truffle.api.interop.Message.READ.createNode(), truffleObject, 1.0));
        assertEquals(42, com.oracle.truffle.api.interop.ForeignAccess.sendRead(com.oracle.truffle.api.interop.Message.READ.createNode(), truffleObject, 1L));

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
        Node unboxNode = com.oracle.truffle.api.interop.Message.UNBOX.createNode();
        assertError(() -> com.oracle.truffle.api.interop.ForeignAccess.sendUnbox(unboxNode, (TruffleObject) languageEnv.asGuestValue(null)), UnsupportedMessageException.class);
        assertError(() -> com.oracle.truffle.api.interop.ForeignAccess.sendUnbox(unboxNode, (TruffleObject) languageEnv.asGuestValue(new Object())), UnsupportedMessageException.class);
        assertError(() -> com.oracle.truffle.api.interop.ForeignAccess.sendUnbox(unboxNode, (TruffleObject) languageEnv.asGuestValue(Object.class)), UnsupportedMessageException.class);
    }

    @Test
    public void keyInfoDefaults() {
        TruffleObject noKeys = new NoKeysObject();
        int keyInfo = getKeyInfo(noKeys, "p1");
        assertEquals(0, keyInfo);

        TruffleObject nkio = new NoKeyInfoObject();
        keyInfo = getKeyInfo(nkio, "p1");
        assertEquals(0b110, keyInfo);
        keyInfo = getKeyInfo(nkio, "p6");
        assertEquals(0b110, keyInfo);
        keyInfo = getKeyInfo(nkio, "p7");
        assertEquals(0, keyInfo);
    }

    @Test
    public void keyInfo() {
        TruffleObject ipobj = new InternalPropertiesObject(-1, -1, 0, 0);
        int keyInfo = getKeyInfo(ipobj, "p1");
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isWritable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInvocable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p6");
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isWritable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInvocable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p7");
        assertEquals(0, keyInfo);

        ipobj = new InternalPropertiesObject(0b0100010, 0b0100100, 0b0011000, 0);
        keyInfo = getKeyInfo(ipobj, "p1");
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isWritable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p2");
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isWritable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p3");
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isWritable(keyInfo));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p4");
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isWritable(keyInfo));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p5");
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isWritable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p6");
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isWritable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p7");
        assertEquals(0, keyInfo);

        TruffleObject aobj = new ArrayTruffleObject(100);
        testArrayObject(aobj, 100);
        aobj = (TruffleObject) languageEnv.asGuestValue(new String[]{"A", "B", "C", "D"});
        testArrayObject(aobj, 4);
    }

    private static void testArrayObject(TruffleObject array, int length) {
        int keyInfo;
        for (int i = 0; i < length; i++) {
            keyInfo = getKeyInfo(array, i);
            assertTrue(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
            assertTrue(com.oracle.truffle.api.interop.KeyInfo.isWritable(keyInfo));
            assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInvocable(keyInfo));
            assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(keyInfo));
            keyInfo = getKeyInfo(array, (long) i);
            assertTrue(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
            keyInfo = getKeyInfo(array, (double) i);
            assertTrue(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
        }
        assertEquals(0, getKeyInfo(array, length));
        assertEquals(0, getKeyInfo(array, 1.12));
        assertEquals(0, getKeyInfo(array, -1));
        assertEquals(0, getKeyInfo(array, 10L * length));
        assertEquals(0, getKeyInfo(array, Double.NEGATIVE_INFINITY));
        assertEquals(0, getKeyInfo(array, Double.NaN));
        assertEquals(0, getKeyInfo(array, Double.POSITIVE_INFINITY));
    }

    @Test
    public void testHasKeysDefaults() {
        TruffleObject noKeys = new NoKeysObject();
        assertFalse(hasKeys(noKeys));
        TruffleObject keysObj = new DefaultHasKeysObject();
        assertTrue(hasKeys(keysObj));
    }

    @Test
    public void testHasKeys() {
        TruffleObject hasKeysObj = new HasKeysObject(true);
        assertTrue(hasKeys(hasKeysObj));
        hasKeysObj = new HasKeysObject(false);
        assertFalse(hasKeys(hasKeysObj));
    }

    @Test
    public void testPrimitiveBoxing() {
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

    private void assertNumberMembers(Number testNumber) {
        Object guestValue = languageEnv.asBoxedGuestValue(testNumber);
        assertTrue(guestValue instanceof TruffleObject);
        Number number = (Number) unbox(guestValue);
        assertEquals(testNumber.byteValue(), number);
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

    private static void assertInvocable(Object expectedValue, Object receiver, String method, Object... args) {
        TruffleObject o = (TruffleObject) receiver;
        int keyInfo = getKeyInfo(o, method);
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInvocable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isWritable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInsertable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isRemovable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInsertable(keyInfo));

        Object methodObject = read(o, method);
        assertEquals(expectedValue, execute(methodObject, args));
        assertEquals(expectedValue, invoke(receiver, method, args));
        assertHasKey(o, method);
    }

    private static void assertHasKey(Object receiver, String key) {
        TruffleObject o = (TruffleObject) receiver;
        Object keys = sendKeys(o);
        int size = getSize(keys);
        List<String> allKeys = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String search = (String) read(keys, i);
            if (search.equals(key)) {
                return;
            }
            allKeys.add(search);
        }
        throw new AssertionError("Key not found " + key + ". Keys are " + allKeys);
    }

    private void assertStringMembers(String unboxValue, Object stringObject) {
        String string = (String) unbox(stringObject);
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

    private void assertBooleanMembers(Object unboxValue, Object booleanObject) {
        Boolean b = (Boolean) unbox(booleanObject);
        assertEquals(unboxValue, b);

        assertInvocable(unboxValue, booleanObject, "booleanValue");
        assertInvocable(b.equals(unboxValue), booleanObject, "equals", unboxValue);
        assertInvocable(b.compareTo((Boolean) unboxValue), booleanObject, "compareTo", unboxValue);
        assertInvocable(b.hashCode(), booleanObject, "hashCode");
        assertInvocable(b.toString(), booleanObject, "toString");

        assertTrue(languageEnv.isHostObject(booleanObject));
        assertEquals(b, languageEnv.asHostObject(booleanObject));
    }

    private void assertCharacterMembers(Character unboxValue, Object charObject) {
        String b = (String) unbox(charObject);
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
    public void internalKeys() throws ClassCastException, IllegalStateException, PolyglotException, UnsupportedMessageException {
        // All non-internal
        InternalPropertiesObject ipobj = new InternalPropertiesObject(0);
        checkInternalKeys(ipobj, "[p1, p2, p3, p4, p5, p6]");
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
        // All internal
        ipobj = new InternalPropertiesObject(-1);
        checkInternalKeys(ipobj, "[]");
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
        // Combinations:
        ipobj = new InternalPropertiesObject(0b1101000);
        checkInternalKeys(ipobj, "[p1, p2, p4]");
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p2")));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p3")));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p4")));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p5")));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
        ipobj = new InternalPropertiesObject(0b1001110);
        checkInternalKeys(ipobj, "[p4, p5]");
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p3")));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p4")));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p5")));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
        ipobj = new InternalPropertiesObject(0b0101010);
        checkInternalKeys(ipobj, "[p2, p4, p6]");
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p2")));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p3")));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p4")));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p5")));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
    }

    private void checkInternalKeys(Object map, String nonInternalKeys) throws ClassCastException, IllegalStateException, PolyglotException, UnsupportedMessageException {
        List<?> mapWithInternalKeys = context.asValue(keys(map, true)).as(List.class);
        List<?> mapWithoutInternalKeys = context.asValue(keys(map, false)).as(List.class);
        assertEquals(nonInternalKeys, new ArrayList<>(mapWithoutInternalKeys).toString());
        assertEquals("[p1, p2, p3, p4, p5, p6]", new ArrayList<>(mapWithInternalKeys).toString());
    }

    private static TruffleObject keys(Object value, boolean includeInternal) throws UnsupportedMessageException {
        return com.oracle.truffle.api.interop.ForeignAccess.sendKeys(com.oracle.truffle.api.interop.Message.KEYS.createNode(), (TruffleObject) value, includeInternal);
    }

    private static Object read(Object value, int nindex) {
        try {
            return com.oracle.truffle.api.interop.ForeignAccess.sendRead(com.oracle.truffle.api.interop.Message.READ.createNode(), (TruffleObject) value, nindex);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static Object unbox(Object value) {
        try {
            return com.oracle.truffle.api.interop.ForeignAccess.sendUnbox(com.oracle.truffle.api.interop.Message.UNBOX.createNode(), (TruffleObject) value);
        } catch (UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static int getSize(Object value) {
        try {
            return ((Number) com.oracle.truffle.api.interop.ForeignAccess.sendGetSize(com.oracle.truffle.api.interop.Message.GET_SIZE.createNode(), (TruffleObject) value)).intValue();
        } catch (UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void keyInfoJavaObject() {
        TruffleObject d = (TruffleObject) languageEnv.asGuestValue(new TestJavaObject());
        int keyInfo = getKeyInfo(d, "nnoonnee");
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isExisting(keyInfo));
        keyInfo = getKeyInfo(d, "aField");
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isExisting(keyInfo));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isWritable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isInvocable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isRemovable(keyInfo));
        keyInfo = getKeyInfo(d, "toString");
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isExisting(keyInfo));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isReadable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isWritable(keyInfo));
        assertTrue(com.oracle.truffle.api.interop.KeyInfo.isInvocable(keyInfo));
        assertFalse(com.oracle.truffle.api.interop.KeyInfo.isRemovable(keyInfo));
    }

    private static TruffleObject sendKeys(TruffleObject receiver) {
        return sendKeys(receiver, false);
    }

    private static TruffleObject sendKeys(TruffleObject receiver, boolean includeInternal) {
        final Node keysNode = com.oracle.truffle.api.interop.Message.KEYS.createNode();
        try {
            return com.oracle.truffle.api.interop.ForeignAccess.sendKeys(keysNode, receiver, includeInternal);
        } catch (InteropException ex) {
            throw ex.raise();
        }
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

    private static boolean hasKeys(TruffleObject foreignObject) {
        return com.oracle.truffle.api.interop.ForeignAccess.sendHasKeys(com.oracle.truffle.api.interop.Message.HAS_KEYS.createNode(), foreignObject);
    }

    private static int getKeyInfo(TruffleObject foreignObject, Object propertyName) {
        return com.oracle.truffle.api.interop.ForeignAccess.sendKeyInfo(com.oracle.truffle.api.interop.Message.KEY_INFO.createNode(), foreignObject, propertyName);
    }

    private static Object read(TruffleObject foreignObject, Object propertyName) {
        try {
            return com.oracle.truffle.api.interop.ForeignAccess.sendRead(com.oracle.truffle.api.interop.Message.READ.createNode(), foreignObject, propertyName);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static Object invoke(Object foreignObject, String propertyName, Object... args) {
        try {
            return com.oracle.truffle.api.interop.ForeignAccess.sendInvoke(com.oracle.truffle.api.interop.Message.INVOKE.createNode(), (TruffleObject) foreignObject, propertyName, args);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException | UnknownIdentifierException e) {
            throw new AssertionError(e);
        }
    }

    private static Object execute(Object foreignObject, Object... args) {
        try {
            return com.oracle.truffle.api.interop.ForeignAccess.sendExecute(com.oracle.truffle.api.interop.Message.EXECUTE.createNode(), (TruffleObject) foreignObject, args);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testIsHostFunction() {
        TruffleObject system = (TruffleObject) languageEnv.lookupHostSymbol(System.class.getName());
        Object exit = read(system, "exit");
        assertTrue(exit instanceof TruffleObject);
        assertFalse(languageEnv.isHostObject(exit));
        assertTrue(languageEnv.isHostFunction(exit));

        Object out = read(system, "out");
        assertTrue(exit instanceof TruffleObject);
        assertTrue(languageEnv.isHostObject(out));
        assertFalse(languageEnv.isHostFunction(out));

        assertFalse(languageEnv.isHostFunction(system));
        assertFalse(languageEnv.isHostFunction(false));
    }

    static final class NoKeysObject implements TruffleObject {

        @Override
        public com.oracle.truffle.api.interop.ForeignAccess getForeignAccess() {
            return NoKeysObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof NoKeysObject;
        }

        @com.oracle.truffle.api.interop.MessageResolution(receiverType = NoKeysObject.class)
        static final class NoKeysObjectMessageResolution {
            // no messages defined, defaults only
        }
    }

    static final class DefaultHasKeysObject implements TruffleObject {

        @Override
        public com.oracle.truffle.api.interop.ForeignAccess getForeignAccess() {
            return com.oracle.truffle.api.interop.ForeignAccess.create(new com.oracle.truffle.api.interop.ForeignAccess.Factory() {
                @Override
                public boolean canHandle(TruffleObject obj) {
                    return obj instanceof DefaultHasKeysObject;
                }

                @Override
                public CallTarget accessMessage(com.oracle.truffle.api.interop.Message message) {
                    if (com.oracle.truffle.api.interop.Message.HAS_KEYS.equals(message)) {
                        return null;
                    }
                    if (com.oracle.truffle.api.interop.Message.KEYS.equals(message)) {
                        return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(new ArrayTruffleObject(1)));
                    }
                    return null;
                }
            });
        }

    }

    static final class HasKeysObject implements TruffleObject {

        private final boolean hasKeys;

        HasKeysObject(boolean hasKeys) {
            this.hasKeys = hasKeys;
        }

        @Override
        public com.oracle.truffle.api.interop.ForeignAccess getForeignAccess() {
            return HasKeysObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof HasKeysObject;
        }

        @com.oracle.truffle.api.interop.MessageResolution(receiverType = HasKeysObject.class)
        static final class HasKeysObjectMessageResolution {

            @com.oracle.truffle.api.interop.Resolve(message = "HAS_KEYS")
            public abstract static class HasKeysNode extends Node {

                public Object access(HasKeysObject receiver) {
                    return receiver.hasKeys;
                }
            }
        }
    }

    static class StringArray extends ProxyLegacyInteropObject {

        private final String[] array;

        StringArray(String[] array) {
            this.array = array;
        }

        @Override
        public int keyInfo(Number key) {
            if (key.intValue() < array.length && key.intValue() >= 0) {
                return com.oracle.truffle.api.interop.KeyInfo.READABLE;
            }
            return super.keyInfo(key);
        }

        @Override
        public Object read(Number key) throws UnsupportedMessageException, UnknownIdentifierException {
            return array[key.intValue()];
        }

        @Override
        public boolean hasSize() {
            return true;
        }

        @Override
        public int getSize() {
            return array.length;
        }

    }

    public static final class TestJavaObject {
        public int aField = 10;
    }

    static final class InternalPropertiesObject implements TruffleObject {

        private final int rBits;    // readable
        private final int wBits;    // writable
        private final int iBits;    // invocable
        private final int nBits;    // internal

        /**
         * @param iBits bits at property number indexes, where '1' means internal, '0' means
         *            non-internal.
         */
        InternalPropertiesObject(int iBits) {
            this(-1, -1, -1, iBits);
        }

        InternalPropertiesObject(int rBits, int wBits, int iBits, int nBits) {
            this.rBits = rBits;
            this.wBits = wBits;
            this.iBits = iBits;
            this.nBits = nBits;
        }

        @Override
        public com.oracle.truffle.api.interop.ForeignAccess getForeignAccess() {
            return PropertiesVisibilityObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof InternalPropertiesObject;
        }

        @com.oracle.truffle.api.interop.MessageResolution(receiverType = InternalPropertiesObject.class)
        static final class PropertiesVisibilityObjectMessageResolution {

            @com.oracle.truffle.api.interop.Resolve(message = "HAS_KEYS")
            public abstract static class HasKeysNode extends Node {

                public Object access(InternalPropertiesObject receiver) {
                    assert receiver != null;
                    return true;
                }
            }

            @com.oracle.truffle.api.interop.Resolve(message = "KEYS")
            public abstract static class KeysNode extends Node {

                public Object access(InternalPropertiesObject receiver, boolean includeInternal) {
                    assert receiver != null;
                    if (includeInternal) {
                        return new StringArray(new String[]{"p1", "p2", "p3", "p4", "p5", "p6"});
                    } else {
                        List<String> propertyNames = new ArrayList<>();
                        for (int i = 1; i <= 6; i++) {
                            if ((receiver.nBits & (1 << i)) == 0) {
                                propertyNames.add("p" + i);
                            }
                        }
                        return new StringArray(propertyNames.toArray(new String[0]));
                    }
                }
            }

            @com.oracle.truffle.api.interop.Resolve(message = "KEY_INFO")
            public abstract static class KeyInfoNode extends Node {

                public int access(InternalPropertiesObject receiver, String propertyName) {
                    if (propertyName.length() != 2 || propertyName.charAt(0) != 'p' || !Character.isDigit(propertyName.charAt(1))) {
                        return 0;
                    }
                    int d = Character.digit(propertyName.charAt(1), 10);
                    if (d > 6) {
                        return 0;
                    }
                    boolean readable = (receiver.rBits & (1 << d)) > 0;
                    boolean writable = (receiver.wBits & (1 << d)) > 0;
                    boolean invocable = (receiver.iBits & (1 << d)) > 0;
                    boolean internal = (receiver.nBits & (1 << d)) > 0;
                    int info = com.oracle.truffle.api.interop.KeyInfo.NONE;
                    if (readable) {
                        info |= com.oracle.truffle.api.interop.KeyInfo.READABLE;
                    }
                    if (writable) {
                        info |= com.oracle.truffle.api.interop.KeyInfo.MODIFIABLE;
                    }
                    if (invocable) {
                        info |= com.oracle.truffle.api.interop.KeyInfo.INVOCABLE;
                    }
                    if (internal) {
                        info |= com.oracle.truffle.api.interop.KeyInfo.INTERNAL;
                    }
                    return info;
                }
            }
        }
    }

    static final class NoKeyInfoObject implements TruffleObject {

        @Override
        public com.oracle.truffle.api.interop.ForeignAccess getForeignAccess() {
            return NoKeyInfoObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof NoKeyInfoObject;
        }

        @com.oracle.truffle.api.interop.MessageResolution(receiverType = NoKeyInfoObject.class)
        static final class NoKeyInfoObjectMessageResolution {
            // KEYS defined only, using default KEY_INFO
            @com.oracle.truffle.api.interop.Resolve(message = "KEYS")
            public abstract static class PropertiesKeysOnlyNode extends Node {

                public Object access(NoKeyInfoObject receiver) {
                    assert receiver != null;
                    return new StringArray(new String[]{"p1", "p2", "p3", "p4", "p5", "p6"});
                }
            }
        }
    }

}
