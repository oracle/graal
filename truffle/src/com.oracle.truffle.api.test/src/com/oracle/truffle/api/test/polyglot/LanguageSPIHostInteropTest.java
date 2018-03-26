/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ValueHostInteropTest.ArrayTruffleObject;
import com.oracle.truffle.api.test.polyglot.ValueHostInteropTest.Data;

public class LanguageSPIHostInteropTest {

    private Context context;
    private Env env;

    @Before
    public void before() {
        context = Context.newBuilder().allowAllAccess(true).build();
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected LanguageContext createContext(Env contextEnv) {
                env = contextEnv;
                return super.createContext(contextEnv);
            }
        });
        context.initialize(ProxyLanguage.ID);
        context.enter();
        assertNotNull(env);
    }

    @After
    public void after() {
        context.leave();
        context.close();
    }

    @Test
    public void testSystemMethod() throws InteropException {
        TruffleObject system = (TruffleObject) env.lookupHostSymbol(System.class.getName());
        Object value = ForeignAccess.sendInvoke(Message.createInvoke(1).createNode(), system, "getProperty", "file.separator");
        assertThat(value, CoreMatchers.instanceOf(String.class));
        assertThat(value, CoreMatchers.anyOf(CoreMatchers.equalTo("/"), CoreMatchers.equalTo("\\")));

        Object getProperty = ForeignAccess.sendRead(Message.READ.createNode(), system, "getProperty");
        assertThat(getProperty, CoreMatchers.instanceOf(TruffleObject.class));
        assertTrue("IS_EXECUTABLE", ForeignAccess.sendIsExecutable(Message.IS_EXECUTABLE.createNode(), (TruffleObject) getProperty));
        value = ForeignAccess.sendExecute(Message.createExecute(1).createNode(), (TruffleObject) getProperty, "file.separator");
        assertThat(value, CoreMatchers.instanceOf(String.class));
        assertThat(value, CoreMatchers.anyOf(CoreMatchers.equalTo("/"), CoreMatchers.equalTo("\\")));
    }

    static class TestClass {
    }

    @Test
    public void conversionToClassYieldsTheClass() {
        Object javaValue = env.asHostObject(env.asGuestValue(TestClass.class));
        Object javaSymbol = env.asHostObject(env.lookupHostSymbol(TestClass.class.getName()));

        assertTrue(javaValue instanceof Class);
        assertSame("Both class objects are the same", javaValue, javaSymbol);
    }

    @Test
    public void conversionToClassNull() {
        Object meta = env.findMetaObject(env.asGuestValue(null));
        assertSame(Void.class,
                        env.asHostObject(meta));
    }

    @Test
    public void nullAsJavaObject() {
        Object nullObject = env.asGuestValue(null);
        assertTrue(env.isHostObject(nullObject));
        assertNull(env.asHostObject(nullObject));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void assertKeysAndProperties() {
        Data dataObj = new Data();
        TruffleObject data = (TruffleObject) env.asGuestValue(dataObj);

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
        TruffleObject obj = (TruffleObject) env.asGuestValue(data);

        Object string = ForeignAccess.sendInvoke(Message.createInvoke(0).createNode(), obj, "toString");
        assertTrue(string instanceof String && ((String) string).startsWith(Data.class.getName() + "@"));
        Object clazz = ForeignAccess.sendInvoke(Message.createInvoke(0).createNode(), obj, "getClass");
        assertTrue(clazz instanceof TruffleObject && env.asHostObject(clazz) == Data.class);
        assertEquals(true, ForeignAccess.sendInvoke(Message.createInvoke(1).createNode(), obj, "equals", obj));
        assertTrue(ForeignAccess.sendInvoke(Message.createInvoke(0).createNode(), obj, "hashCode") instanceof Integer);

        for (String m : new String[]{"notify", "notifyAll", "wait"}) {
            assertThrowsExceptionWithCause(() -> ForeignAccess.sendInvoke(Message.createInvoke(0).createNode(), obj, m), IllegalMonitorStateException.class);
        }
    }

    @Test
    public void indexJavaArrayWithNumberTypes() throws Exception {
        int[] a = new int[]{1, 2, 3};
        TruffleObject truffleObject = (TruffleObject) env.asGuestValue(a);

        assertEquals(2, ForeignAccess.sendRead(Message.READ.createNode(), truffleObject, 1));
        assertEquals(2, ForeignAccess.sendRead(Message.READ.createNode(), truffleObject, 1.0));
        assertEquals(2, ForeignAccess.sendRead(Message.READ.createNode(), truffleObject, 1L));

        ForeignAccess.sendWrite(Message.WRITE.createNode(), truffleObject, 1, 42);
        ForeignAccess.sendWrite(Message.WRITE.createNode(), truffleObject, 1.0, 42);
        ForeignAccess.sendWrite(Message.WRITE.createNode(), truffleObject, 1L, 42);

        assertEquals(42, ForeignAccess.sendRead(Message.READ.createNode(), truffleObject, 1));
        assertEquals(42, ForeignAccess.sendRead(Message.READ.createNode(), truffleObject, 1.0));
        assertEquals(42, ForeignAccess.sendRead(Message.READ.createNode(), truffleObject, 1L));

    }

    @Test
    public void testAsGuestValue() {
        Object object = new Object();
        // Test that asTruffleValue() returns the same as asTruffleObject() for non-primitive types:
        assertEquals(env.asGuestValue(object), env.asGuestValue(object));

        Data data = new Data();
        Object obj = env.asGuestValue(data);
        assertEquals(obj, env.asGuestValue(data));
        // Test that asTruffleValue() returns non-wraped primitives:
        object = 42;
        assertTrue(env.asGuestValue(object) == object);
        object = (byte) 42;
        assertTrue(env.asGuestValue(object) == object);
        object = (short) 42;
        assertTrue(env.asGuestValue(object) == object);
        object = 424242424242L;
        assertTrue(env.asGuestValue(object) == object);
        object = 42.42;
        assertTrue(env.asGuestValue(object) == object);
        object = true;
        assertTrue(env.asGuestValue(object) == object);
        object = "42";
        assertTrue(env.asGuestValue(object) == object);
        object = '4';
        assertTrue(env.asGuestValue(object) == object);
        object = true;
        assertTrue(env.asGuestValue(object) == object);
    }

    @Test
    public void notUnboxable() {
        Node unboxNode = Message.UNBOX.createNode();
        assertError(() -> ForeignAccess.sendUnbox(unboxNode, (TruffleObject) env.asGuestValue(null)), UnsupportedMessageException.class);
        assertError(() -> ForeignAccess.sendUnbox(unboxNode, (TruffleObject) env.asGuestValue(new Object())), UnsupportedMessageException.class);
        assertError(() -> ForeignAccess.sendUnbox(unboxNode, (TruffleObject) env.asGuestValue(Object.class)), UnsupportedMessageException.class);
    }

    @Test
    public void keyInfoDefaults() {
        TruffleObject noKeys = new NoKeysObject();
        int keyInfo = getKeyInfo(noKeys, "p1");
        assertEquals(0, keyInfo);

        TruffleObject nkio = new NoKeyInfoObject();
        keyInfo = getKeyInfo(nkio, "p1");
        assertEquals(0b111, keyInfo);
        keyInfo = getKeyInfo(nkio, "p6");
        assertEquals(0b111, keyInfo);
        keyInfo = getKeyInfo(nkio, "p7");
        assertEquals(0, keyInfo);
    }

    @Test
    public void keyInfo() {
        TruffleObject ipobj = new InternalPropertiesObject(-1, -1, 0, 0);
        int keyInfo = getKeyInfo(ipobj, "p1");
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertTrue(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        assertFalse(KeyInfo.isInternal(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p6");
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertTrue(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        assertFalse(KeyInfo.isInternal(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p7");
        assertEquals(0, keyInfo);

        ipobj = new InternalPropertiesObject(0b0100010, 0b0100100, 0b0011000, 0);
        keyInfo = getKeyInfo(ipobj, "p1");
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertFalse(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p2");
        assertFalse(KeyInfo.isReadable(keyInfo));
        assertTrue(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p3");
        assertFalse(KeyInfo.isReadable(keyInfo));
        assertFalse(KeyInfo.isWritable(keyInfo));
        assertTrue(KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p4");
        assertFalse(KeyInfo.isReadable(keyInfo));
        assertFalse(KeyInfo.isWritable(keyInfo));
        assertTrue(KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p5");
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertTrue(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p6");
        assertFalse(KeyInfo.isReadable(keyInfo));
        assertFalse(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        keyInfo = getKeyInfo(ipobj, "p7");
        assertEquals(0, keyInfo);

        TruffleObject aobj = new ArrayTruffleObject(100);
        testArrayObject(aobj, 100);
        aobj = (TruffleObject) env.asGuestValue(new String[]{"A", "B", "C", "D"});
        testArrayObject(aobj, 4);
    }

    private static void testArrayObject(TruffleObject array, int length) {
        int keyInfo;
        for (int i = 0; i < length; i++) {
            keyInfo = getKeyInfo(array, i);
            assertTrue(KeyInfo.isReadable(keyInfo));
            assertTrue(KeyInfo.isWritable(keyInfo));
            assertFalse(KeyInfo.isInvocable(keyInfo));
            assertFalse(KeyInfo.isInternal(keyInfo));
            keyInfo = getKeyInfo(array, (long) i);
            assertTrue(KeyInfo.isReadable(keyInfo));
            keyInfo = getKeyInfo(array, (double) i);
            assertTrue(KeyInfo.isReadable(keyInfo));
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
    public void internalKeys() throws ClassCastException, IllegalStateException, PolyglotException, UnsupportedMessageException {
        // All non-internal
        InternalPropertiesObject ipobj = new InternalPropertiesObject(0);
        checkInternalKeys(ipobj, "[p1, p2, p3, p4, p5, p6]");
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
        // All internal
        ipobj = new InternalPropertiesObject(-1);
        checkInternalKeys(ipobj, "[]");
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
        // Combinations:
        ipobj = new InternalPropertiesObject(0b1101000);
        checkInternalKeys(ipobj, "[p1, p2, p4]");
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p2")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p3")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p4")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p5")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
        ipobj = new InternalPropertiesObject(0b1001110);
        checkInternalKeys(ipobj, "[p4, p5]");
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p3")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p4")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p5")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
        ipobj = new InternalPropertiesObject(0b0101010);
        checkInternalKeys(ipobj, "[p2, p4, p6]");
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p1")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p2")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p3")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p4")));
        assertTrue(KeyInfo.isInternal(getKeyInfo(ipobj, "p5")));
        assertFalse(KeyInfo.isInternal(getKeyInfo(ipobj, "p6")));
    }

    @SuppressWarnings({"rawtypes"})
    private void checkInternalKeys(Object map, String nonInternalKeys) throws ClassCastException, IllegalStateException, PolyglotException, UnsupportedMessageException {
        List mapWithInternalKeys = context.asValue(keys(map, true)).as(List.class);
        List mapWithoutInternalKeys = context.asValue(keys(map, false)).as(List.class);
        assertEquals(nonInternalKeys, mapWithoutInternalKeys.toString());
        assertEquals("[p1, p2, p3, p4, p5, p6]", mapWithInternalKeys.toString());
    }

    private static TruffleObject keys(Object value, boolean includeInternal) throws UnsupportedMessageException {
        return ForeignAccess.sendKeys(Message.KEYS.createNode(), (TruffleObject) value, includeInternal);
    }

    @Test
    public void keyInfoJavaObject() {
        TruffleObject d = (TruffleObject) env.asGuestValue(new TestJavaObject());
        int keyInfo = getKeyInfo(d, "nnoonnee");
        assertFalse(KeyInfo.isExisting(keyInfo));
        keyInfo = getKeyInfo(d, "aField");
        assertTrue(KeyInfo.isExisting(keyInfo));
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertTrue(KeyInfo.isWritable(keyInfo));
        assertFalse(KeyInfo.isInvocable(keyInfo));
        assertFalse(KeyInfo.isRemovable(keyInfo));
        keyInfo = getKeyInfo(d, "toString");
        assertTrue(KeyInfo.isExisting(keyInfo));
        assertTrue(KeyInfo.isReadable(keyInfo));
        assertFalse(KeyInfo.isWritable(keyInfo));
        assertTrue(KeyInfo.isInvocable(keyInfo));
        assertFalse(KeyInfo.isRemovable(keyInfo));
    }

    private static TruffleObject sendKeys(TruffleObject receiver) {
        return sendKeys(receiver, false);
    }

    private static TruffleObject sendKeys(TruffleObject receiver, boolean includeInternal) {
        final Node keysNode = Message.KEYS.createNode();
        try {
            return ForeignAccess.sendKeys(keysNode, receiver, includeInternal);
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
            assertTrue(env.isHostException(e));
            assertSame(exception, env.asHostException(e).getClass());
        }
    }

    private static boolean hasKeys(TruffleObject foreignObject) {
        return ForeignAccess.sendHasKeys(Message.HAS_KEYS.createNode(), foreignObject);
    }

    private static int getKeyInfo(TruffleObject foreignObject, Object propertyName) {
        return ForeignAccess.sendKeyInfo(Message.KEY_INFO.createNode(), foreignObject, propertyName);
    }

    static final class NoKeysObject implements TruffleObject {

        @Override
        public ForeignAccess getForeignAccess() {
            return NoKeysObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof NoKeysObject;
        }

        @MessageResolution(receiverType = NoKeysObject.class)
        static final class NoKeysObjectMessageResolution {
            // no messages defined, defaults only
        }
    }

    static final class DefaultHasKeysObject implements TruffleObject {

        @Override
        public ForeignAccess getForeignAccess() {
            return ForeignAccess.create(new ForeignAccess.Factory() {
                @Override
                public boolean canHandle(TruffleObject obj) {
                    return obj instanceof DefaultHasKeysObject;
                }

                @Override
                public CallTarget accessMessage(Message message) {
                    if (Message.HAS_KEYS.equals(message)) {
                        return null;
                    }
                    if (Message.KEYS.equals(message)) {
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
        public ForeignAccess getForeignAccess() {
            return HasKeysObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof HasKeysObject;
        }

        @MessageResolution(receiverType = HasKeysObject.class)
        static final class HasKeysObjectMessageResolution {

            @Resolve(message = "HAS_KEYS")
            public abstract static class HasKeysNode extends Node {

                public Object access(HasKeysObject receiver) {
                    return receiver.hasKeys;
                }
            }
        }
    }

    static class StringArray extends ProxyInteropObject {

        private final String[] array;

        StringArray(String[] array) {
            this.array = array;
        }

        @Override
        public int keyInfo(Number key) {
            if (key.intValue() < array.length && key.intValue() >= 0) {
                return KeyInfo.READABLE;
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
        public ForeignAccess getForeignAccess() {
            return PropertiesVisibilityObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof InternalPropertiesObject;
        }

        @MessageResolution(receiverType = InternalPropertiesObject.class)
        static final class PropertiesVisibilityObjectMessageResolution {

            @Resolve(message = "HAS_KEYS")
            public abstract static class HasKeysNode extends Node {

                public Object access(InternalPropertiesObject receiver) {
                    assert receiver != null;
                    return true;
                }
            }

            @Resolve(message = "KEYS")
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

            @Resolve(message = "KEY_INFO")
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
                    int info = KeyInfo.NONE;
                    if (readable) {
                        info |= KeyInfo.READABLE;
                    }
                    if (writable) {
                        info |= KeyInfo.MODIFIABLE;
                    }
                    if (invocable) {
                        info |= KeyInfo.INVOCABLE;
                    }
                    if (internal) {
                        info |= KeyInfo.INTERNAL;
                    }
                    return info;
                }
            }
        }
    }

    static final class NoKeyInfoObject implements TruffleObject {

        @Override
        public ForeignAccess getForeignAccess() {
            return NoKeyInfoObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof NoKeyInfoObject;
        }

        @MessageResolution(receiverType = NoKeyInfoObject.class)
        static final class NoKeyInfoObjectMessageResolution {
            // KEYS defined only, using default KEY_INFO
            @Resolve(message = "KEYS")
            public abstract static class PropertiesKeysOnlyNode extends Node {

                public Object access(NoKeyInfoObject receiver) {
                    assert receiver != null;
                    return new StringArray(new String[]{"p1", "p2", "p3", "p4", "p5", "p6"});
                }
            }
        }
    }

}
