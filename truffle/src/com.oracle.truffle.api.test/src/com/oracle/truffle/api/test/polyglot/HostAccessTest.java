/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage.evalTestLanguage;
import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.HostAccess.Builder;
import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.HostAccess.Implementable;
import org.graalvm.polyglot.HostAccess.MutableTargetMapping;
import org.graalvm.polyglot.HostAccess.TargetMappingPrecedence;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.NullObject;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import com.oracle.truffle.tck.tests.ValueAssert;
import com.oracle.truffle.tck.tests.ValueAssert.Trait;

public class HostAccessTest extends AbstractHostAccessTest {

    public static final String INSTANTIATION_FAILED = "Instantiation failed";
    public static final String RETURNED_STRING = "Returned string";

    public static class OK {
        public int value = 42;
    }

    public static class Ban {
        public int value = 24;
    }

    @Test
    public void usefulToStringExplicit() {
        assertEquals("HostAccess.EXPLICIT", HostAccess.EXPLICIT.toString());
    }

    @Test
    public void usefulToStringPublic() {
        assertEquals("HostAccess.ALL", HostAccess.ALL.toString());
    }

    @Test
    public void usefulToStringNone() {
        assertEquals("HostAccess.NONE", HostAccess.NONE.toString());
    }

    @Test
    public void constantsCanBeCopied() {
        verifyObjectImpl(HostAccess.NONE);
        verifyObjectImpl(HostAccess.EXPLICIT);
        verifyObjectImpl(HostAccess.SCOPED);
        verifyObjectImpl(HostAccess.ALL);
    }

    public static class MyEquals {

        @Override
        public boolean equals(Object arg0) {
            return arg0 != null && getClass() == arg0.getClass();
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }
    }

    @Test
    public void banAccessToReflection() throws Exception {
        // @formatter:off
        HostAccess config = HostAccess.newBuilder().
            allowPublicAccess(true).
            denyAccess(Class.class).
            denyAccess(Method.class).
            denyAccess(Field.class).
            denyAccess(Proxy.class).
            denyAccess(Object.class, false).
            build();
        // @formatter:on
        setupEnv(config);

        Value readValue = context.eval("sl", "" +
                        "function readValue(x, y) {\n" +
                        "  return x.equals(y);\n" +
                        "}\n" +
                        "function main() {\n" +
                        "  return readValue;\n" +
                        "}\n");

        MyEquals myEquals = new MyEquals();
        assertTrue("MyEquals.equals method is accessible", readValue.execute(myEquals, myEquals).asBoolean());

        Value res;
        try {
            res = readValue.execute(new Object());
        } catch (PolyglotException ex) {
            return;
        }
        fail("expecting no result: " + res);
    }

    @Test
    public void banAccessToEquals() throws Exception {
        // @formatter:off
        HostAccess config = HostAccess.newBuilder().
            allowPublicAccess(true).
            denyAccess(Object.class, false).
            build();
        // @formatter:on
        setupEnv(config);
        Value readValue = context.eval("sl", "" +
                        "function readValue(x, y) {\n" +
                        "  return x.equals(y);\n" +
                        "}\n" +
                        "function main() {\n" +
                        "  return readValue;\n" +
                        "}\n");

        MyEquals myEquals = new MyEquals();
        assertTrue("MyEquals.equals method is accessible", readValue.execute(myEquals, myEquals).asBoolean());

        Value res;
        try {
            res = readValue.execute(new Object());
        } catch (PolyglotException ex) {
            return;
        }
        fail("expecting no result: " + res);
    }

    @Test
    public void banAccessToLoadClass() throws Exception {
        // @formatter:off
        HostAccess config = HostAccess.newBuilder().
            allowPublicAccess(true).
            denyAccess(ClassLoader.class).
            build();
        // @formatter:on

        setupEnv(config);
        Value loadClass = context.eval("sl", "" +
                        "function loadClass(x) {\n" +
                        "  return x.loadClass(\"java.lang.String\");\n" +
                        "}\n" +
                        "function main() {\n" +
                        "  return loadClass;\n" +
                        "}\n");

        URLClassLoader loader = new URLClassLoader(new URL[0]);

        Value res;
        try {
            res = loadClass.execute(loader);
        } catch (PolyglotException ex) {
            return;
        }
        fail("expecting no result: " + res);
    }

    @Test
    public void banAccessToOverwrittenLoadClass() throws Exception {
        // @formatter:off
        HostAccess config = HostAccess.newBuilder().
            allowPublicAccess(true).
            denyAccess(ClassLoader.class).
            build();
        // @formatter:on

        setupEnv(config);
        Value loadClass = context.eval("sl", "" +
                        "function loadClass(x) {\n" +
                        "  return x.loadClass(\"java.lang.String\");\n" +
                        "}\n" +
                        "function main() {\n" +
                        "  return loadClass;\n" +
                        "}\n");

        URLClassLoader loader = new MyClassLoader(new URL[0]);

        Value res;
        try {
            res = loadClass.execute(loader);
        } catch (PolyglotException ex) {
            return;
        }
        fail("expecting no result: " + res);
    }

    @Test
    public void publicCanAccessObjectEquals() throws Exception {
        setupEnv(HostAccess.ALL);
        Value readValue = context.eval("sl", "" +
                        "function readValue(x, y) {\n" +
                        "  return x.equals(y);\n" +
                        "}\n" +
                        "function main() {\n" +
                        "  return readValue;\n" +
                        "}\n");
        assertFalse("Cannot read equals 1", readValue.execute(new Object(), new Object()).asBoolean());
        Object same = new Object();
        assertTrue("Cannot read equals 2", readValue.execute(same, same).asBoolean());
    }

    @Test
    public void inheritFromPublic() throws Exception {
        setupEnv(HostAccess.newBuilder().allowPublicAccess(true));
        Value readValue = context.eval("sl", "" +
                        "function readValue(x, y) {\n" +
                        "  return x.equals(y);\n" +
                        "}\n" +
                        "function main() {\n" +
                        "  return readValue;\n" +
                        "}\n");
        assertFalse("Cannot read equals 1", readValue.execute(new Object(), new Object()).asBoolean());
        Object same = new Object();
        assertTrue("Cannot read equals 2", readValue.execute(same, same).asBoolean());
    }

    @Test
    public void useOneHostAccessByTwoContexts() throws Exception {
        HostAccess config = HostAccess.newBuilder().allowAccess(OK.class.getField("value")).build();

        try (
                        Context c1 = Context.newBuilder().allowHostAccess(config).build();
                        Context c2 = Context.newBuilder().allowHostAccess(config).build()) {
            assertAccess(c1);
            assertAccess(c2);
        }
    }

    private static void assertAccess(Context context) {
        Value readValue = context.eval("sl", "" +
                        "function readValue(x) {\n" +
                        "  return x.value;\n" +
                        "}\n" +
                        "function main() {\n" +
                        "  return readValue;\n" +
                        "}\n");
        assertEquals(42, readValue.execute(new OK()).asInt());
        ExposeToGuestTest.assertPropertyUndefined("public isn't enough by default", "value", readValue, new Ban());
    }

    @Test
    public void onlyOneHostAccessPerEngine() throws Exception {
        Engine shared = Engine.create();

        HostAccess config = HostAccess.newBuilder().allowAccess(OK.class.getField("value")).build();
        Context c1 = Context.newBuilder().engine(shared).allowHostAccess(config).build();
        Context.Builder builder = Context.newBuilder().engine(shared).allowHostAccess(HostAccess.ALL);
        try {
            builder.build();
            fail();
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Found different host access configuration for a context with a shared engine."));
        }
        c1.close();
    }

    @Test
    public void testArrayAccessEnabled() {
        setupEnv(HostAccess.newBuilder().allowArrayAccess(true));
        int[] array = new int[]{1, 2, 3};
        Value value = context.asValue(array);
        assertTrue(value.hasArrayElements());
        assertEquals(3, value.getArraySize());
        assertEquals(1, value.getArrayElement(0).asInt());
        assertEquals(2, value.getArrayElement(1).asInt());
        assertEquals(3, value.getArrayElement(2).asInt());
        value.setArrayElement(2, 42);
        assertEquals(42, value.getArrayElement(2).asInt());
        assertEquals(42, array[2]);
        assertSame(array, value.asHostObject());
        array[2] = 43;
        assertEquals(43, value.getArrayElement(2).asInt());
        try {
            value.removeArrayElement(2);
            fail();
        } catch (UnsupportedOperationException e) {
        }
        assertEquals(2 /* arr.length and arr.clone(). */, value.getMemberKeys().size());
        ValueAssert.assertValue(value, false, Trait.ARRAY_ELEMENTS, Trait.ITERABLE, Trait.MEMBERS, Trait.HOST_OBJECT);
    }

    @Test
    public void testArrayAccessDisabled() {
        setupEnv(HostAccess.newBuilder().allowArrayAccess(false));
        assertArrayAccessDisabled(context);
    }

    @Test
    public void testPublicAccessNoArrayAccess() {
        setupEnv(HostAccess.newBuilder().allowPublicAccess(true));
        assertArrayAccessDisabled(context);
    }

    private static void assertArrayAccessDisabled(Context context) {
        int[] array = new int[]{1, 2, 3};
        Value value = context.asValue(array);
        assertSame(array, value.asHostObject());
        ValueAssert.assertValue(value, false, Trait.MEMBERS, Trait.HOST_OBJECT);
    }

    @Test
    public void testBufferAccessEnabled() {
        setupEnv(HostAccess.newBuilder().allowBufferAccess(true));
        assertBufferAccessEnabled(context);
    }

    @Test
    public void testBufferAccessEnabledHostAccessCloned() {
        HostAccess hostAccess = HostAccess.newBuilder().allowBufferAccess(true).build();
        setupEnv(HostAccess.newBuilder(hostAccess));
        assertBufferAccessEnabled(context);
    }

    private static void assertBufferAccessEnabled(Context context) {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.put((byte) 42);
        Value value = context.asValue(buffer);
        assertTrue(value.hasBufferElements());
        assertTrue(value.isBufferWritable());
        assertEquals(2, value.getBufferSize());
        assertEquals(42, value.readBufferByte(0));
        value.writeBufferByte(1, (byte) 24);
        assertEquals(24, value.readBufferByte(1));
        ValueAssert.assertValue(value, false, Trait.BUFFER_ELEMENTS, Trait.MEMBERS, Trait.HOST_OBJECT);
    }

    @Test
    public void testBufferAccessDisabled() {
        setupEnv(HostAccess.newBuilder().allowBufferAccess(false));
        ByteBuffer buffer = ByteBuffer.allocate(2);
        Value value = context.asValue(buffer);
        assertSame(buffer, value.asHostObject());
        ValueAssert.assertValue(value, false, Trait.MEMBERS, Trait.HOST_OBJECT);
    }

    /*
     * Test for GR-32346.
     */
    @Test
    public void testBuilderCannotChangeMembersAndTargetMappingsOfHostAccess() throws Exception {
        // Set up hostAccess
        Builder builder = HostAccess.newBuilder();
        builder.allowAccess(OK.class.getField("value"));
        builder.targetTypeMapping(Value.class, String.class, (v) -> v.isString(), (v) -> "foo");
        HostAccess hostAccess = builder.build();

        // Try to change members or targetMappings through child builder
        Builder childBuilder = HostAccess.newBuilder(hostAccess);
        childBuilder.allowAccess(Ban.class.getField("value"));
        childBuilder.targetTypeMapping(Value.class, Integer.class, null, (v) -> 42);

        // Ensure hostAccess has not been altered by child builder
        try (Context c = Context.newBuilder().allowHostAccess(hostAccess).build()) {
            assertAccess(c);
            assertEquals("foo", c.asValue("a string").as(String.class));
            assertEquals(123, (int) c.asValue(123).as(Integer.class));
        }
    }

    @Test
    public void testListAccessEnabled() {
        setupEnv(HostAccess.newBuilder().allowListAccess(true));
        List<Integer> array = new ArrayList<>(Arrays.asList(1, 2, 3));
        Value value = context.asValue(array);
        assertTrue(value.hasArrayElements());
        assertEquals(3, value.getArraySize());
        assertEquals(1, value.getArrayElement(0).asInt());
        assertEquals(2, value.getArrayElement(1).asInt());
        assertEquals(3, value.getArrayElement(2).asInt());
        value.setArrayElement(2, 42);
        assertEquals(42, value.getArrayElement(2).asInt());
        assertEquals(42, (int) array.get(2));

        array.set(2, 43);
        assertEquals(43, value.getArrayElement(2).asInt());

        value.removeArrayElement(2);
        assertEquals(2, value.getArraySize());
        assertSame(array, value.asHostObject());

        assertEquals(0, value.getMemberKeys().size());

        ValueAssert.assertValue(value, false, Trait.ARRAY_ELEMENTS, Trait.ITERABLE, Trait.MEMBERS, Trait.HOST_OBJECT);
        assertArrayAccessDisabled(context);
    }

    @Test
    public void testListAccessDisabled() {
        setupEnv(HostAccess.newBuilder().allowListAccess(false));
        assertListAccessDisabled(context, false);
    }

    @Test
    public void testIterableAccessEnabled() {
        setupEnv(HostAccess.newBuilder().allowIterableAccess(true));
        Iterable<Integer> iterable = new IterableImpl<>(1, 2, 3);
        Value value = context.asValue(iterable);
        assertTrue(value.hasIterator());
        Value iterator = value.getIterator();
        assertTrue(iterator.hasIteratorNextElement());
        assertEquals(1, iterator.getIteratorNextElement().asInt());
        assertTrue(iterator.hasIteratorNextElement());
        assertEquals(2, iterator.getIteratorNextElement().asInt());
        assertTrue(iterator.hasIteratorNextElement());
        assertEquals(3, iterator.getIteratorNextElement().asInt());
        assertFalse(iterator.hasIteratorNextElement());
        assertSame(iterable, value.asHostObject());
        assertEquals(0, value.getMemberKeys().size());
        ValueAssert.assertValue(value, false, Trait.ITERABLE, Trait.MEMBERS, Trait.HOST_OBJECT);
        assertListAccessDisabled(context, true);
        assertArrayAccessDisabled(context);
    }

    @Test
    public void testIterableAccessDisabled() {
        setupEnv(HostAccess.newBuilder().allowIterableAccess(false));
        assertIterableAccessDisabled(context);
    }

    @Test
    public void testIteratorAccessEnabled() {
        setupEnv(HostAccess.newBuilder().allowIteratorAccess(true));
        Iterator<Integer> iterator = new IteratorImpl<>(1, 2, 3);
        Value value = context.asValue(iterator);
        assertTrue(value.hasIteratorNextElement());
        assertEquals(1, value.getIteratorNextElement().asInt());
        assertTrue(value.hasIteratorNextElement());
        assertEquals(2, value.getIteratorNextElement().asInt());
        assertTrue(value.hasIteratorNextElement());
        assertEquals(3, value.getIteratorNextElement().asInt());
        assertFalse(value.hasIteratorNextElement());
        assertSame(iterator, value.asHostObject());
        assertEquals(0, value.getMemberKeys().size());
        ValueAssert.assertValue(value, false, Trait.ITERATOR, Trait.MEMBERS, Trait.HOST_OBJECT);
        assertIterableAccessDisabled(context);
        assertListAccessDisabled(context, false);
        assertArrayAccessDisabled(context);
    }

    @Test
    public void testMapAccessEnabled() {
        setupEnv(HostAccess.newBuilder().allowMapAccess(true));
        Map<Integer, String> map = new HashMap<>();
        map.put(1, Integer.toBinaryString(1));
        map.put(2, Integer.toBinaryString(2));
        Value value = context.asValue(map);
        assertTrue(value.hasHashEntries());
        assertTrue(value.hasHashEntries());
        assertEquals(2, value.getHashSize());
        assertEquals(Integer.toBinaryString(1), value.getHashValue(1).asString());
        assertEquals(Integer.toBinaryString(2), value.getHashValue(2).asString());
        value.putHashEntry(2, "");
        assertEquals("", value.getHashValue(2).asString());
        assertEquals("", map.get(2));
        value.removeHashEntry(2);
        assertEquals(1, value.getHashSize());
        assertSame(map, value.asHostObject());
        Value entriesIteratorIterator = value.getHashEntriesIterator();
        assertTrue(entriesIteratorIterator.isIterator());
        assertTrue(entriesIteratorIterator.hasIteratorNextElement());
        Value entry = entriesIteratorIterator.getIteratorNextElement();
        assertTrue(entry.hasArrayElements());
        assertEquals(1, entry.getArrayElement(0).asInt());
        assertEquals(Integer.toBinaryString(1), entry.getArrayElement(1).asString());
        assertEquals(0, value.getMemberKeys().size());
        ValueAssert.assertValue(value, false, Trait.HASH, Trait.MEMBERS, Trait.HOST_OBJECT);
        assertArrayAccessDisabled(context);
    }

    @Test
    public void testMapAccessDisabled() {
        setupEnv(HostAccess.newBuilder().allowMapAccess(false));
        assertMapAccessDisabled(context);
    }

    @Test
    public void testIteratorAccessDisabled() {
        setupEnv(HostAccess.newBuilder().allowIteratorAccess(false));
        assertIteratorAccessDisabled(context);
    }

    @Test
    public void testPublicAccessNoListAccess() {
        setupEnv(HostAccess.newBuilder().allowPublicAccess(true).allowListAccess(false));
        assertListAccessDisabled(context, false);
    }

    private static void assertListAccessDisabled(Context context, boolean iterableAccess) {
        List<Integer> array = new ArrayList<>(Arrays.asList(1, 2, 3));
        Value value = context.asValue(array);
        assertSame(array, value.asHostObject());
        List<Trait> expectedTypes = new ArrayList<>(Arrays.asList(Trait.MEMBERS, Trait.HOST_OBJECT));
        if (iterableAccess) {
            expectedTypes.add(Trait.ITERABLE);
        }
        ValueAssert.assertValue(value, false, expectedTypes.toArray(new Trait[expectedTypes.size()]));
    }

    private static void assertIterableAccessDisabled(Context context) {
        Iterable<Integer> iterable = new IterableImpl<>(1, 2, 3);
        Value value = context.asValue(iterable);
        assertSame(iterable, value.asHostObject());
        ValueAssert.assertValue(value, false, Trait.MEMBERS, Trait.HOST_OBJECT);
    }

    private static void assertIteratorAccessDisabled(Context context) {
        Iterator<Integer> iterator = new IteratorImpl<>(1, 2, 3);
        Value value = context.asValue(iterator);
        assertSame(iterator, value.asHostObject());
        ValueAssert.assertValue(value, false, Trait.MEMBERS, Trait.HOST_OBJECT);
    }

    private static void assertMapAccessDisabled(Context context) {
        Map<Integer, String> map = Collections.singletonMap(1, "string");
        Value value = context.asValue(map);
        assertSame(map, value.asHostObject());
        ValueAssert.assertValue(value, false, Trait.MEMBERS, Trait.HOST_OBJECT);
    }

    public static final class TargetClass1 {

        private final Object o;

        TargetClass1(Object o) {
            this.o = o;
        }

    }

    public static final class TargetClass2 {

        TargetClass2(@SuppressWarnings("unused") Object o) {
        }

    }

    @SuppressWarnings("static-method")
    public static final class MultiMethod1 {
        @Export
        public TargetClass1 m(TargetClass1 arg0) {
            return arg0;
        }

        @Export
        public TargetClass2 m(TargetClass2 arg0) {
            return arg0;
        }
    }

    @SuppressWarnings("static-method")
    public static final class MultiMethod2 {
        @Export
        public Object m(@SuppressWarnings("unused") TargetClass1 arg0, Object arg1) {
            return arg1;
        }

        @Export
        public String m(@SuppressWarnings("unused") TargetClass1 arg0, String arg1) {
            return arg1;
        }
    }

    @SuppressWarnings("static-method")
    public static final class SingleMethod {
        @Export
        public TargetClass1 m(TargetClass1 arg0) {
            return arg0;
        }

    }

    @Test
    public void testConverterOverloads() {
        HostAccess.Builder builder;
        Value methods;

        // test single method
        builder = HostAccess.newBuilder();
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.equals("42"), (v) -> new TargetClass1(v));
        setupEnv(builder);
        methods = context.asValue(new SingleMethod());
        assertEquals(TargetClass1.class, methods.invokeMember("m", "42").asHostObject().getClass());
        // should fail
        try {
            methods.invokeMember("m", "43");
            fail();
        } catch (IllegalArgumentException e) {
        }
        // should work
        TargetClass1 o = methods.invokeMember("m", "42").asHostObject();
        assertEquals("42", o.o);

        // test non ambiguous methods
        builder = HostAccess.newBuilder();
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.equals("42"), (v) -> new TargetClass1(v));
        builder.targetTypeMapping(String.class, TargetClass2.class, (v) -> v.equals("43"), (v) -> new TargetClass2(v));
        setupEnv(builder);
        methods = context.asValue(new MultiMethod1());
        assertEquals(TargetClass1.class, methods.invokeMember("m", "42").asHostObject().getClass());
        assertEquals(TargetClass2.class, methods.invokeMember("m", "43").asHostObject().getClass());

        // test ambiguous methods
        builder = HostAccess.newBuilder();
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.equals("42"), (v) -> new TargetClass1(v));
        builder.targetTypeMapping(String.class, TargetClass2.class, (v) -> v.equals("42"), (v) -> new TargetClass2(v));
        setupEnv(builder);
        methods = context.asValue(new MultiMethod1());
        try {
            methods.invokeMember("m", "42");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Multiple applicable overloads found"));
        }

        builder = HostAccess.newBuilder();
        builder.targetTypeMapping(String.class, TargetClass1.class, null, (v) -> new TargetClass1(v));
        setupEnv(builder);
        methods = context.asValue(new MultiMethod2());
        assertEquals("42", methods.invokeMember("m", "42", "42").asString());
        assertEquals(42, methods.invokeMember("m", "42", 42).asInt());
    }

    @Test
    public void testConverterArray() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        builder.allowArrayAccess(true);
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.equals("42"), (v) -> new TargetClass1(v));
        setupEnv(builder);

        TargetClass1[] array = context.asValue(new String[]{"42", "42"}).as(TargetClass1[].class);
        assertEquals(2, array.length);
        assertEquals("42", array[0].o);
        assertEquals("42", array[1].o);

        try {
            context.asValue(new String[]{"42", "43"}).as(TargetClass1[].class);
            fail();
        } catch (ClassCastException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("Cannot convert '43'"));
        }

        builder = HostAccess.newBuilder();
        builder.allowArrayAccess(true);
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.equals("42"), (v) -> new TargetClass1(v));
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.startsWith("42"), (v) -> new TargetClass1(v));
        setupEnv(builder);

        array = context.asValue(new String[]{"42", "422"}).as(TargetClass1[].class);
        assertEquals(2, array.length);
        assertEquals("42", array[0].o);
        assertEquals("422", array[1].o);

    }

    static final TypeLiteral<List<TargetClass1>> TARGET_CLASS_LIST = new TypeLiteral<>() {
    };

    @Test
    public void testConverterList() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        builder.allowArrayAccess(true);
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.equals("42"), (v) -> new TargetClass1(v));
        setupEnv(builder);

        List<TargetClass1> list = context.asValue(new String[]{"42", "42"}).as(TARGET_CLASS_LIST);
        assertEquals(2, list.size());
        assertEquals("42", list.get(0).o);
        assertEquals("42", list.get(1).o);

        list = context.asValue(new String[]{"42", "43"}).as(TARGET_CLASS_LIST);
        try {
            list.get(1);
            fail();
        } catch (ClassCastException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("Cannot convert '43'"));
        }

        builder = HostAccess.newBuilder();
        builder.allowArrayAccess(true);
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.equals("42"), (v) -> new TargetClass1(v));
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.startsWith("42"), (v) -> new TargetClass1(v));
        setupEnv(builder);

        list = context.asValue(new String[]{"42", "422"}).as(TARGET_CLASS_LIST);
        assertEquals(2, list.size());
        assertEquals("42", list.get(0).o);
        assertEquals("422", list.get(1).o);
    }

    static final TypeLiteral<Map<String, TargetClass1>> TARGET_CLASS_MAP_STRING = new TypeLiteral<>() {
    };

    static final TypeLiteral<Map<Long, TargetClass1>> TARGET_CLASS_MAP_LONG = new TypeLiteral<>() {
    };

    @Test
    public void testConverterMapLong() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        builder.allowArrayAccess(true);
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.equals("42"), (v) -> new TargetClass1(v));
        setupEnv(builder);

        Map<Long, TargetClass1> map = context.asValue(new String[]{"42", "42"}).as(TARGET_CLASS_MAP_LONG);
        assertEquals(2, map.size());
        assertEquals("42", map.get(0L).o);
        assertEquals("42", map.get(1L).o);

        map = context.asValue(new String[]{"42", "43"}).as(TARGET_CLASS_MAP_LONG);
        try {
            map.get(1L);
            fail();
        } catch (ClassCastException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("Cannot convert '43'"));
        }

        builder = HostAccess.newBuilder();
        builder.allowArrayAccess(true);
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.equals("42"), (v) -> new TargetClass1(v));
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.startsWith("42"), (v) -> new TargetClass1(v));
        setupEnv(builder);

        map = context.asValue(new String[]{"42", "422"}).as(TARGET_CLASS_MAP_LONG);
        assertEquals(2, map.size());
        assertEquals("42", map.get(0L).o);
        assertEquals("422", map.get(1L).o);
    }

    public static class ConverterMapTestObject {

        @Export public String f0;
        @Export public String f1;

        ConverterMapTestObject(String f0, String f1) {
            this.f0 = f0;
            this.f1 = f1;
        }

    }

    @Test
    public void testConverterMapString() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v != null && v.equals("42"), (v) -> new TargetClass1(v));
        builder.targetTypeMapping(TargetClass1.class, String.class, (v) -> v != null && v.o.equals("42"), (v) -> (String) v.o);
        setupEnv(builder);

        ConverterMapTestObject testObj = new ConverterMapTestObject("42", "42");
        Map<String, TargetClass1> map = context.asValue(testObj).as(TARGET_CLASS_MAP_STRING);
        assertEquals(2, map.size());
        assertEquals("42", map.get("f0").o);
        assertEquals("42", map.get("f1").o);
        try {
            map.put("f0", new TargetClass1("43"));
            fail();
        } catch (ClassCastException e) {
        }
        testObj.f0 = null;
        map.put("f0", new TargetClass1("42"));
        assertEquals("42", testObj.f0);

        map = context.asValue(new ConverterMapTestObject("42", "43")).as(TARGET_CLASS_MAP_STRING);
        assertEquals("42", map.get("f0").o);
        try {
            map.get("f1");
            fail();
        } catch (ClassCastException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("Cannot convert '43'"));
        }

        builder = HostAccess.newBuilder();
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.equals("42"), (v) -> new TargetClass1(v));
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.startsWith("42"), (v) -> new TargetClass1(v));
        setupEnv(builder);

        map = context.asValue(new ConverterMapTestObject("42", "422")).as(TARGET_CLASS_MAP_STRING);
        assertEquals(2, map.size());
        assertEquals("42", map.get("f0").o);
        assertEquals("422", map.get("f1").o);
    }

    /*
     * Referenced in proxys.json
     */
    @Implementable
    public interface ConverterProxy {

        TargetClass1 f0();

        TargetClass1 f1();

    }

    @Test
    public void testConverterProxy() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v != null && v.equals("42"), (v) -> new TargetClass1(v));
        builder.targetTypeMapping(TargetClass1.class, String.class, (v) -> v != null && v.o.equals("42"), (v) -> (String) v.o);
        setupEnv(builder);

        ConverterMapTestObject testObj = new ConverterMapTestObject("42", "42");
        ConverterProxy map = context.asValue(testObj).as(ConverterProxy.class);
        assertEquals("42", map.f0().o);
        assertEquals("42", map.f1().o);
        assertEquals("42", testObj.f0);

        map = context.asValue(new ConverterMapTestObject("42", "43")).as(ConverterProxy.class);
        assertEquals("42", map.f0().o);
        try {
            map.f1();
            fail();
        } catch (ClassCastException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("Cannot convert '43'"));
        }

        builder = HostAccess.newBuilder();
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.equals("42"), (v) -> new TargetClass1(v));
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v.startsWith("42"), (v) -> new TargetClass1(v));
        setupEnv(builder);

        map = context.asValue(new ConverterMapTestObject("42", "422")).as(ConverterProxy.class);
        assertEquals("42", map.f0().o);
        assertEquals("422", map.f1().o);
    }

    /*
     * Referenced in proxys.json
     */
    @FunctionalInterface
    public interface ConverterFunction {

        TargetClass1 m(String arg0);

    }

    @Test
    public void testConverterFunctional() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        builder.allowPublicAccess(true);
        builder.targetTypeMapping(String.class, TargetClass1.class, (v) -> v != null && v.equals("42"), (v) -> new TargetClass1(v));
        builder.targetTypeMapping(TargetClass1.class, String.class, (v) -> v != null && v.o.equals("42"), (v) -> (String) v.o);
        setupEnv(builder);

        Value identity = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                return arguments[0];
            }
        });
        ConverterFunction f = identity.as(ConverterFunction.class);
        assertEquals("42", f.m("42").o);
    }

    public static class OverloadedFunctionalMethod implements Function<String, Object> {
        @Export
        @Override
        public Object apply(String arg0) {
            return arg0;
        }

        @Export
        public Object apply(int intArg) {
            return intArg;
        }

        @Export
        public Object apply(@SuppressWarnings("unused") String arg0, Object arg1) {
            return arg1;
        }
    }

    @Test
    public void testOverloadedFunctionalMethod() throws Exception {
        setupEnv(HostAccess.EXPLICIT);

        Value function = context.asValue(new OverloadedFunctionalMethod());
        function.execute("ok");
        // when calling a functional interface implementation, only dispatch to implementations of
        // the single abstract method and not other methods with the same name in the subclass.
        assertFails(() -> function.execute(42), PolyglotException.class, e -> {
            assertTrue(e.toString(), e.isHostException());
            assertTrue(e.asHostException().toString(), e.asHostException() instanceof ClassCastException);
        });
        assertFails(() -> function.execute("ok", "not ok"), IllegalArgumentException.class);

        // of course, this does not apply when invoking "apply" as a method.
        assertEquals("ok", function.invokeMember("apply", "ok").asString());
        assertEquals(42, function.invokeMember("apply", 42).asInt());
        assertEquals("also ok", function.invokeMember("apply", "ok", "also ok").asString());
    }

    static class CountingPredicate implements Predicate<Value> {

        final String targetString;
        int count = 0;

        CountingPredicate(String targetString) {
            this.targetString = targetString;
        }

        @Override
        public boolean test(Value t) {
            count++;
            return t.isString() && t.asString().equals(targetString);
        }
    }

    static class CountingConverter implements Function<Value, String> {

        int count = 0;

        public String apply(Value t) {
            count++;
            return t.asString();
        }
    }

    @Test
    public void testMappingInvocations() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        CountingPredicate accepts0 = new CountingPredicate("42");
        CountingConverter converter0 = new CountingConverter();
        builder.targetTypeMapping(Value.class, String.class, accepts0, converter0);
        CountingPredicate accepts1 = new CountingPredicate("43");
        CountingConverter converter1 = new CountingConverter();
        builder.targetTypeMapping(Value.class, String.class, accepts1, converter1);
        setupEnv(builder);

        context.asValue("42").as(String.class);
        assertEquals(1, accepts0.count);
        assertEquals(1, converter0.count);
        assertEquals(0, accepts1.count);
        assertEquals(0, converter1.count);

        context.asValue("43").as(String.class);
        assertEquals(2, accepts0.count);
        assertEquals(1, converter0.count);
        assertEquals(1, accepts1.count);
        assertEquals(1, converter1.count);

        context.asValue("44").as(String.class);
        assertEquals(3, accepts0.count);
        assertEquals(1, converter0.count);
        assertEquals(2, accepts1.count);
        assertEquals(1, converter1.count);
    }

    @Test
    public void testAcceptsError() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        builder.allowPublicAccess(true);
        final IllegalArgumentException error = new IllegalArgumentException();
        Predicate<Integer> errorAccepts = new Predicate<>() {
            public boolean test(Integer t) {
                error.initCause(new RuntimeException());
                throw error;
            }
        };
        builder.targetTypeMapping(Integer.class, String.class, errorAccepts, (v) -> {
            throw new AssertionError();
        });
        setupEnv(builder);
        Value v = context.asValue(42);
        try {
            v.as(String.class);
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.isHostException());
            assertSame(error, e.asHostException());
        }
    }

    @Test
    public void testConverterError() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        builder.allowPublicAccess(true);
        final IllegalArgumentException error = new IllegalArgumentException();
        builder.targetTypeMapping(Integer.class, String.class, (v) -> v.equals(42), (v) -> {
            throw error;
        });
        setupEnv(builder);
        Value v = context.asValue(42);
        try {
            v.as(String.class);
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.isHostException());
            assertSame(error, e.asHostException());
        }
    }

    @Test
    public void testConverterClassCast() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        builder.allowPublicAccess(true);
        ClassCastException cce = new ClassCastException("foo");
        builder.targetTypeMapping(Integer.class, String.class, (v) -> v.equals(42), (v) -> {
            throw cce;
        });
        setupEnv(builder);
        Value v = context.asValue(42);
        try {
            v.as(String.class);
            fail();
        } catch (ClassCastException e) {
            assertEquals("foo", e.getMessage());
            // we must not allow normal class cast exceptions to go through.
            assertNotSame(e, cce);
        }
    }

    public static class PassPrimitive {
        @Export
        public int f0(int arg0) {
            return arg0;
        }
    }

    @Test
    public void testConverterReturnsNull() {
        /*
         * HotSpot uses GuestToHostCodeCache#methodHandleHostInvoke and SVM uses
         * GuestToHostCodeCache#reflectionHostInvoke. These two methods throw a different exception
         * when a null value is passed as a method int argument. The former throws
         * NullPointerException during automatic unboxing, the latter throws
         * IllegalArgumentException.
         */
        HostAccess.Builder builder = HostAccess.newBuilder();
        builder.targetTypeMapping(Integer.class, Integer.class, (v) -> v.equals(42), (v) -> {
            return null;
        });
        setupEnv(builder);
        try {
            context.asValue(new PassPrimitive()).invokeMember("f0", 42);
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.isHostException());
            assertTrue(TruffleTestAssumptions.isAOT() ? e.asHostException() instanceof IllegalArgumentException : e.asHostException() instanceof NullPointerException);
        }

        assertNull(context.asValue(42).as(int.class));
        assertEquals(43, context.asValue(43).asInt());
    }

    @Test
    public void testTargetOrderStrict() {
        setupEnv(HostAccess.newBuilder().targetTypeMapping(Integer.class, Integer.class, null,
                        (v) -> 42, TargetMappingPrecedence.HIGHEST));
        assertEquals(42, (int) context.asValue(41).as(int.class));

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Integer.class, Integer.class, null,
                        (v) -> 42, TargetMappingPrecedence.HIGH));
        assertEquals(42, (int) context.asValue(41).as(int.class));

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Integer.class, Integer.class, null,
                        (v) -> 41, TargetMappingPrecedence.LOW));
        assertEquals(42, (int) context.asValue(42).as(int.class));

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Integer.class, Integer.class, null,
                        (v) -> 41, TargetMappingPrecedence.LOWEST));
        assertEquals(42, (int) context.asValue(42).as(int.class));
    }

    @Test
    public void testTargetOrderLoose() {
        setupEnv(HostAccess.newBuilder().targetTypeMapping(Integer.class, Character.class, null,
                        (v) -> (char) 42, TargetMappingPrecedence.HIGHEST));
        assertEquals((char) 42, (char) context.asValue(41).as(char.class));

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Integer.class, Character.class, null,
                        (v) -> (char) 42, TargetMappingPrecedence.HIGH));
        assertEquals((char) 42, (char) context.asValue(41).as(char.class));

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Integer.class, Character.class, null,
                        (v) -> (char) 42, TargetMappingPrecedence.LOW));
        assertEquals((char) 42, (char) context.asValue(41).as(char.class));

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Integer.class, Character.class, null,
                        (v) -> (char) 41, TargetMappingPrecedence.LOWEST));
        assertEquals((char) 42, (char) context.asValue(42).as(char.class));
    }

    static class TestProxyExecutable implements ProxyExecutable {

        public Object execute(Value... arguments) {
            return "OriginalFunction";
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTargetOrderFunctionProxy() {
        Function<Void, String> convertedFunction = (a) -> "ConvertedFunction";

        setupEnv(HostAccess.ALL);
        assertEquals("OriginalFunction", context.asValue(new TestProxyExecutable()).as(Function.class).apply(null));

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Value.class, Function.class, null,
                        (v) -> convertedFunction, TargetMappingPrecedence.HIGHEST));
        assertEquals("ConvertedFunction", context.asValue(new TestProxyExecutable()).as(Function.class).apply(null));

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Value.class, Function.class, null,
                        (v) -> convertedFunction, TargetMappingPrecedence.HIGH));
        assertEquals("ConvertedFunction", context.asValue(new TestProxyExecutable()).as(Function.class).apply(null));

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Value.class, Function.class, null,
                        (v) -> convertedFunction, TargetMappingPrecedence.LOW));
        assertEquals("ConvertedFunction", context.asValue(new TestProxyExecutable()).as(Function.class).apply(null));

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Value.class, Function.class, null,
                        (v) -> convertedFunction, TargetMappingPrecedence.LOWEST));
        assertEquals("OriginalFunction", context.asValue(new TestProxyExecutable()).as(Function.class).apply(null));
    }

    public static class NoCoercion {

    }

    /*
     * Referenced in proxys.json
     */
    @Implementable
    public interface TestInterface {

        String test();

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTargetOrderObjectProxy() {
        Map<String, Object> originalValues = new HashMap<>();
        originalValues.put("test", "OriginalObject");
        ProxyObject original = ProxyObject.fromMap(originalValues);

        TestInterface converted = new TestInterface() {
            public String test() {
                return "ConvertedObject";
            }
        };

        setupEnv(HostAccess.ALL);
        assertEquals("OriginalObject", context.asValue(original).as(TestInterface.class).test());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Value.class, TestInterface.class, null,
                        (v) -> converted, TargetMappingPrecedence.HIGHEST));
        assertEquals("ConvertedObject", context.asValue(original).as(TestInterface.class).test());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Value.class, TestInterface.class, null,
                        (v) -> converted, TargetMappingPrecedence.HIGH));
        assertEquals("ConvertedObject", context.asValue(original).as(TestInterface.class).test());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Value.class, TestInterface.class, null,
                        (v) -> converted, TargetMappingPrecedence.LOW));
        assertEquals("ConvertedObject", context.asValue(original).as(TestInterface.class).test());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Value.class, TestInterface.class, null,
                        (v) -> converted, TargetMappingPrecedence.LOWEST));
        assertEquals("OriginalObject", context.asValue(original).as(TestInterface.class).test());

    }

    public static class HostProxyObject {

        @Export
        public String test() {
            return "OriginalObject";
        }

    }

    @Test
    public void testTargetOrderHostProxy() {
        HostProxyObject original = new HostProxyObject();

        TestInterface converted = new TestInterface() {
            public String test() {
                return "ConvertedObject";
            }
        };

        setupEnv(HostAccess.ALL);
        assertEquals("OriginalObject", context.asValue(original).as(TestInterface.class).test());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Value.class, TestInterface.class, null,
                        (v) -> converted, TargetMappingPrecedence.HIGHEST));
        assertEquals("ConvertedObject", context.asValue(original).as(TestInterface.class).test());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Value.class, TestInterface.class, null,
                        (v) -> converted, TargetMappingPrecedence.HIGH));
        assertEquals("ConvertedObject", context.asValue(original).as(TestInterface.class).test());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Value.class, TestInterface.class, null,
                        (v) -> converted, TargetMappingPrecedence.LOW));
        assertEquals("ConvertedObject", context.asValue(original).as(TestInterface.class).test());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Value.class, TestInterface.class, null,
                        (v) -> converted, TargetMappingPrecedence.LOWEST));
        assertEquals("OriginalObject", context.asValue(original).as(TestInterface.class).test());

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTargetOrderNoCoercion() {
        NoCoercion noCoercion = new NoCoercion();

        setupEnv(HostAccess.ALL);

        assertFails(() -> context.asValue("").as(NoCoercion.class), ClassCastException.class);

        setupEnv(HostAccess.newBuilder().targetTypeMapping(String.class, NoCoercion.class, null,
                        (v) -> noCoercion, TargetMappingPrecedence.HIGHEST));
        assertEquals(noCoercion, context.asValue("").as(NoCoercion.class));

        setupEnv(HostAccess.newBuilder().targetTypeMapping(String.class, NoCoercion.class, null,
                        (v) -> noCoercion, TargetMappingPrecedence.HIGH));
        assertEquals(noCoercion, context.asValue("").as(NoCoercion.class));

        setupEnv(HostAccess.newBuilder().targetTypeMapping(String.class, NoCoercion.class, null,
                        (v) -> noCoercion, TargetMappingPrecedence.LOW));
        assertEquals(noCoercion, context.asValue("").as(NoCoercion.class));

        setupEnv(HostAccess.newBuilder().targetTypeMapping(String.class, NoCoercion.class, null,
                        (v) -> noCoercion, TargetMappingPrecedence.LOWEST));
        assertEquals(noCoercion, context.asValue("").as(NoCoercion.class));
    }

    @SuppressWarnings("unused")
    public static class LooseOverload1 {

        @Export
        public String m(int s) {
            return "int";
        }

        @Export
        public String m(List<Object> s) {
            return "list";
        }

    }

    @Test
    public void testLooseOverloadAssertion1() {
        setupEnv(HostAccess.ALL);
        assertFails(() -> context.asValue(new LooseOverload1()).invokeMember("m", (char) 42), IllegalArgumentException.class);
    }

    @SuppressWarnings("unused")
    public static class LooseOverload2 {

        @Export
        public String m(char s) {
            return "int";
        }

        @Export
        public String m(List<Object> s) {
            return "list";
        }

    }

    @Test
    public void testOverloadAssertion2() {
        setupEnv(HostAccess.ALL);
        assertEquals("int", context.asValue(new LooseOverload2()).invokeMember("m", 42).asString());
    }

    @SuppressWarnings("unused")
    public static class OverloadPrecedenceStrict {

        @Export
        public String m(String s) {
            return "String";
        }

        @Export
        public String m(int s) {
            return "int";
        }

    }

    @Test
    public void testConverterOverloadPrecedenceStrict() {
        OverloadPrecedenceStrict obj = new OverloadPrecedenceStrict();

        setupEnv(HostAccess.newBuilder().targetTypeMapping(String.class, Integer.class, null,
                        (v) -> Integer.parseInt(v), TargetMappingPrecedence.HIGHEST));
        assertEquals("int", context.asValue(obj).invokeMember("m", "42").asString());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(String.class, Integer.class, null,
                        (v) -> Integer.parseInt(v), TargetMappingPrecedence.HIGH));
        assertFails(() -> context.asValue(obj).invokeMember("m", "42"), IllegalArgumentException.class);

        setupEnv(HostAccess.newBuilder().targetTypeMapping(String.class, Integer.class, null,
                        (v) -> Integer.parseInt(v), TargetMappingPrecedence.LOW));
        assertEquals("String", context.asValue(obj).invokeMember("m", "42").asString());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(String.class, Integer.class, null,
                        (v) -> Integer.parseInt(v), TargetMappingPrecedence.LOWEST));
        assertEquals("String", context.asValue(obj).invokeMember("m", "42").asString());
    }

    @SuppressWarnings("unused")
    public static class OverloadPrecedenceLoose {

        @Export
        public String m(char s) {
            return "int";
        }

        @Export
        public String m(List<Object> s) {
            return "list";
        }
    }

    @Test
    public void testConverterOverloadPrecedenceLoose() {
        OverloadPrecedenceLoose obj = new OverloadPrecedenceLoose();

        setupEnv(HostAccess.ALL);
        assertEquals("int", context.asValue(obj).invokeMember("m", (char) 42).asString());
        assertEquals("int", context.asValue(obj).invokeMember("m", 42).asString());
        assertEquals("list", context.asValue(obj).invokeMember("m", ProxyArray.fromArray()).asString());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Integer.class, List.class, null,
                        (v) -> Arrays.asList(v), TargetMappingPrecedence.HIGHEST));
        assertEquals("list", context.asValue(obj).invokeMember("m", 42).asString());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Integer.class, List.class, null,
                        (v) -> Arrays.asList(v), TargetMappingPrecedence.HIGH));
        assertEquals("list", context.asValue(obj).invokeMember("m", 42).asString());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Integer.class, List.class, null,
                        (v) -> Arrays.asList(v), TargetMappingPrecedence.LOW));
        assertEquals("list", context.asValue(obj).invokeMember("m", 42).asString());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Integer.class, List.class, null,
                        (v) -> Arrays.asList(v), TargetMappingPrecedence.LOWEST));
        assertEquals("int", context.asValue(obj).invokeMember("m", 42).asString());
    }

    @SuppressWarnings("unused")
    public static class OverloadPrecedenceFunctionProxy {

        @Export
        public String m(Function<Value, Value> s) {
            return "function";
        }

        @Export
        public String m(Runnable s) {
            return "runnable";
        }
    }

    @Test
    public void testConverterOverloadPrecedenceFunctionProxy() {
        OverloadPrecedenceFunctionProxy obj = new OverloadPrecedenceFunctionProxy();

        ProxyExecutable f = new ProxyExecutable() {

            @Override
            public Object execute(Value... arguments) {
                return 42;
            }
        };

        setupEnv(HostAccess.ALL);
        assertEquals("function", context.asValue(obj).invokeMember("m", f).asString());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Function.class, Runnable.class, null,
                        (v) -> null, TargetMappingPrecedence.HIGHEST));
        assertEquals("runnable", context.asValue(obj).invokeMember("m", f).asString());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Function.class, Runnable.class, null,
                        (v) -> null, TargetMappingPrecedence.HIGH));
        assertEquals("runnable", context.asValue(obj).invokeMember("m", f).asString());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Function.class, Runnable.class, null,
                        (v) -> null, TargetMappingPrecedence.LOW));
        assertFails(() -> context.asValue(obj).invokeMember("m", f), IllegalArgumentException.class);

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Function.class, Runnable.class, null,
                        (v) -> null, TargetMappingPrecedence.LOWEST));
        assertEquals("function", context.asValue(obj).invokeMember("m", f).asString());
    }

    @Implementable
    public interface TestObjectProxy1 {

        String test();

    }

    @Implementable
    public interface TestObjectProxy2 {

        String test();

    }

    @SuppressWarnings("unused")
    public static class OverloadPrecedenceObjectProxy {

        @Export
        public String m(TestObjectProxy1 s) {
            return "proxy1";
        }

        @Export
        public String m(TestObjectProxy2 s) {
            return "proxy2";
        }
    }

    @Test
    public void testConverterOverloadPrecedenceObjectProxy() {
        Map<String, Object> originalValues = new HashMap<>();
        originalValues.put("test", "OriginalObject");
        ProxyObject arg = ProxyObject.fromMap(originalValues);

        OverloadPrecedenceObjectProxy obj = new OverloadPrecedenceObjectProxy();

        setupEnv(HostAccess.ALL);
        assertFails(() -> context.asValue(obj).invokeMember("m", arg), IllegalArgumentException.class);

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Map.class, TestObjectProxy2.class, null,
                        (v) -> null, TargetMappingPrecedence.HIGHEST));
        assertEquals("proxy2", context.asValue(obj).invokeMember("m", arg).asString());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Map.class, TestObjectProxy2.class, null,
                        (v) -> null, TargetMappingPrecedence.HIGH));
        assertEquals("proxy2", context.asValue(obj).invokeMember("m", arg).asString());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Map.class, TestObjectProxy2.class, null,
                        (v) -> null, TargetMappingPrecedence.LOW));
        assertEquals("proxy2", context.asValue(obj).invokeMember("m", arg).asString());

        setupEnv(HostAccess.newBuilder().targetTypeMapping(Map.class, TestObjectProxy2.class, null,
                        (v) -> null, TargetMappingPrecedence.LOWEST));
        assertFails(() -> context.asValue(obj).invokeMember("m", arg), IllegalArgumentException.class);
    }

    @SuppressWarnings("unused")
    public static class OverloadPrecedenceAmbiguous {

        @Export
        public String m(String s) {
            return "string";
        }

        @Export
        public String m(int s) {
            return "int";
        }
    }

    /*
     * Test for the example described in the target type mapping javadoc.
     */
    @Test
    public void testConverterOverloadPrecedenceExample() {
        OverloadPrecedenceAmbiguous obj = new OverloadPrecedenceAmbiguous();

        setupEnv(HostAccess.newBuilder().//
                        targetTypeMapping(String.class, Integer.class, null, (v) -> 42, TargetMappingPrecedence.HIGHEST));
        assertEquals("int", context.asValue(obj).invokeMember("m", "").asString());

        setupEnv(HostAccess.newBuilder().//
                        targetTypeMapping(String.class, Integer.class, null, (v) -> 42, TargetMappingPrecedence.HIGH));
        assertFails(() -> context.asValue(obj).invokeMember("m", ""), IllegalArgumentException.class);

        setupEnv(HostAccess.newBuilder().//
                        targetTypeMapping(String.class, Integer.class, null, (v) -> 42, TargetMappingPrecedence.LOW));
        assertEquals("string", context.asValue(obj).invokeMember("m", "").asString());

        setupEnv(HostAccess.newBuilder().//
                        targetTypeMapping(String.class, Integer.class, null, (v) -> 42, TargetMappingPrecedence.LOWEST));
        assertEquals("string", context.asValue(obj).invokeMember("m", "").asString());
    }

    @Test
    public void testConverterOverloadPrecedenceAmbiguous2() {
        Map<String, Object> originalValues = new HashMap<>();
        originalValues.put("test", "OriginalObject");

        OverloadPrecedenceAmbiguous obj = new OverloadPrecedenceAmbiguous();

        setupEnv(HostAccess.ALL);
        assertEquals("string", context.asValue(obj).invokeMember("m", "").asString());
        assertEquals("int", context.asValue(obj).invokeMember("m", 42).asString());

        setupEnv(HostAccess.newBuilder().//
                        targetTypeMapping(String.class, Integer.class, null, (v) -> 42, TargetMappingPrecedence.HIGHEST).//
                        targetTypeMapping(String.class, String.class, null, (v) -> "42", TargetMappingPrecedence.HIGHEST));
        assertFails(() -> context.asValue(obj).invokeMember("m", ""), IllegalArgumentException.class);

        setupEnv(HostAccess.newBuilder().//
                        targetTypeMapping(String.class, Integer.class, null, (v) -> 42, TargetMappingPrecedence.HIGH).//
                        targetTypeMapping(String.class, String.class, null, (v) -> "42", TargetMappingPrecedence.HIGH));
        assertFails(() -> context.asValue(obj).invokeMember("m", ""), IllegalArgumentException.class);

        setupEnv(HostAccess.newBuilder().//
                        targetTypeMapping(String.class, Integer.class, null, (v) -> 42, TargetMappingPrecedence.LOW).//
                        targetTypeMapping(String.class, String.class, null, (v) -> "42", TargetMappingPrecedence.LOW));
        assertEquals("string", context.asValue(obj).invokeMember("m", "").asString());
        assertEquals("int", context.asValue(obj).invokeMember("m", 42).asString());

        setupEnv(HostAccess.newBuilder().//
                        targetTypeMapping(String.class, Integer.class, null, (v) -> 42, TargetMappingPrecedence.LOWEST).//
                        targetTypeMapping(String.class, String.class, null, (v) -> "42", TargetMappingPrecedence.LOWEST));
        assertEquals("string", context.asValue(obj).invokeMember("m", "").asString());
        assertEquals("int", context.asValue(obj).invokeMember("m", 42).asString());
    }

    @SuppressWarnings("unused")
    public static class VarArgsFunctionPrecedence {

        @Export
        public String m(String name, Function<Integer, Integer> func, Object... args) {
            func.apply(42);
            return "function";
        }

        @Export
        public String m(String name, Object... args) {
            return "object";
        }
    }

    @Test
    public void testVarArgsFunctionPrecedence() {
        VarArgsFunctionPrecedence obj = new VarArgsFunctionPrecedence();

        ProxyExecutable f = arguments -> 42;

        setupEnv(HostAccess.ALL);
        // both overloads are applicable but the more specific Function overload is preferred
        assertEquals("function", context.asValue(obj).invokeMember("m", "dummy", f).asString());
        assertEquals("function", context.asValue(obj).invokeMember("m", "dummy", f, 1, 2, 3).asString());

        assertEquals("object", context.asValue(obj).invokeMember("m", "dummy").asString());
        assertEquals("object", context.asValue(obj).invokeMember("m", "dummy", "dummy").asString());
    }

    @SuppressWarnings("unused")
    public static class ListVsArray {

        @Export
        public String m(List<Object> list) {
            return "list";
        }

        @Export
        public String m(Object[] array) {
            return "array";
        }
    }

    @Test
    public void testListVsArray() {
        ListVsArray obj = new ListVsArray();

        ProxyArray a = ProxyArray.fromArray(4, 5, 6);

        setupEnv(HostAccess.ALL);
        // both overloads are applicable but List is preferred over array types.
        assertEquals("list", context.asValue(obj).invokeMember("m", a).asString());
    }

    @Test
    public void testPassNullValue() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        AtomicInteger invoked = new AtomicInteger();
        builder.targetTypeMapping(Value.class, Integer.class, (v) -> {
            assertTrue(v.isNull());
            invoked.incrementAndGet();
            return v.fitsInInt();
        }, (v) -> {
            throw new AssertionError();
        });
        setupEnv(builder);
        assertNull(context.asValue(null).as(Integer.class));
        assertEquals(1, invoked.get());
    }

    @Test
    public void testPassNullObject() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        AtomicInteger invoked = new AtomicInteger();
        builder.targetTypeMapping(Integer.class, Integer.class, (v) -> {
            assertNull(v);
            invoked.incrementAndGet();
            return true;
        }, (v) -> {
            return null;
        });
        setupEnv(builder);
        assertNull(context.asValue(null).as(Integer.class));
        assertEquals(1, invoked.get());
    }

    /*
     * Test for GR-15593.
     */
    @Test
    @Ignore
    public void testRecursion() {
        HostAccess.Builder builder = HostAccess.newBuilder();
        builder.targetTypeMapping(Value.class, Integer.class, (v) -> {
            return true;
        }, (v) -> {
            return v.as(Integer.class);
        });

        setupEnv(builder);
        try {
            assertNull(context.asValue(42).as(Integer.class));
            fail();
        } catch (PolyglotException e) {
            // which type of exception ends up here depends on where the stack overflow happens
            if (e.isGuestException()) {
                assertTrue(e.isInternalError());
            } else {
                assertTrue(e.isHostException());
                assertTrue(e.asHostException() instanceof StackOverflowError);
            }
        }
    }

    public static class TestIdentity {
        public void foo() {
        }
    }

    public interface TestIdentityMapping {
        void foo();
    }

    @Test
    public void testIdentity() {
        setupEnv(HostAccess.EXPLICIT);

        Context c1 = this.context;
        Context c2 = Context.create();

        TestIdentity v0 = new TestIdentity();
        TestIdentity v1 = new TestIdentity();

        assertFalse(c1.asValue(v0).equals(c1.asValue(v1)));
        assertFalse(c1.asValue(v0).equals(c2.asValue(v1)));
        assertTrue(c1.asValue(v0).equals(c1.asValue(v0)));
        assertTrue(c1.asValue(v1).equals(c2.asValue(v1)));

        c2.close();
    }

    @Test
    public void testInterfaceMethodExportsWithInheritance() {
        setupEnv(HostAccess.newBuilder(HostAccess.EXPLICIT).allowAccessInheritance(true).build());

        Value point;
        point = context.asValue(new BarePoint());
        assertEquals(42, point.invokeMember("x").asInt());
        assertEquals(43, point.invokeMember("y").asInt());
        assertEquals(44, point.invokeMember("z").asInt());

        point = context.asValue(new AnnotatedPoint());
        assertEquals(42, point.invokeMember("x").asInt());
        assertEquals(43, point.invokeMember("y").asInt());
        assertEquals(44, point.invokeMember("z").asInt());

        point = context.asValue(new PrivatePoint());
        assertEquals(42, point.invokeMember("x").asInt());
        assertEquals(43, point.invokeMember("y").asInt());
        assertEquals(44, point.invokeMember("z").asInt());
    }

    @Test
    public void testInterfaceMethodExportsWithNoInheritance() {
        setupEnv(HostAccess.newBuilder(HostAccess.EXPLICIT).allowAccessInheritance(false).build());

        Value point;
        point = context.asValue(new BarePoint());
        assertFalse(point.hasMember("x"));
        assertFalse(point.hasMember("y"));
        assertFalse(point.canInvokeMember("x"));
        assertFalse(point.canInvokeMember("y"));
        assertEquals(44, point.invokeMember("z").asInt());

        point = context.asValue(new AnnotatedPoint());
        assertEquals(42, point.invokeMember("x").asInt());
        assertEquals(43, point.invokeMember("y").asInt());
        assertEquals(44, point.invokeMember("z").asInt());

        point = context.asValue(new PrivatePoint());
        assertFalse(point.canInvokeMember("x"));
        assertFalse(point.canInvokeMember("y"));
        assertEquals(44, point.invokeMember("z").asInt());
    }

    public interface PointInterface {
        @Export
        int x();

        @Export
        int y();

        @Export
        default int z() {
            return 44;
        }
    }

    public static class BarePoint implements PointInterface {
        @Override
        public int x() {
            return 42;
        }

        @Override
        public int y() {
            return 43;
        }
    }

    public static class AnnotatedPoint implements PointInterface {
        @Export
        @Override
        public int x() {
            return 42;
        }

        @Export
        @Override
        public int y() {
            return 43;
        }
    }

    static class PrivatePoint implements PointInterface {
        @Export
        @Override
        public int x() {
            return 42;
        }

        public int y() {
            return 43;
        }
    }

    /**
     * Referenced in {@code proxys.json}.
     */
    public interface ProxiedPoint {
        /** Not exported. */
        default int w() {
            return 41;
        }

        @Export
        int x();

        @Export
        default int y() {
            return 43;
        }

        @Export
        default int z() {
            return 44;
        }
    }

    /**
     * Referenced in {@code proxys.json}.
     */
    public interface ProxiedPoint2 extends ProxiedPoint {
        @Export
        @Override
        int x();

        /** Exported in {@link ProxiedPoint} but not in {@link ProxiedPoint2}. */
        @Override
        int z();
    }

    @Test
    public void testProxyInterfaceMethodExportInheritance() {
        setupEnv(HostAccess.newBuilder(HostAccess.EXPLICIT).allowAccessInheritance(false).build());

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InvocationHandler invocationHandler = (proxy, method, args) -> {
            if (method.getName().equals("x")) {
                return 42;
            } else if (method.getName().equals("y")) {
                return 43;
            } else if (method.getName().equals("z")) {
                return 44;
            } else {
                throw new IllegalArgumentException(method.getName());
            }
        };

        Object proxy = Proxy.newProxyInstance(classLoader, new Class<?>[]{ProxiedPoint.class}, invocationHandler);
        Value point;
        point = context.asValue(proxy);
        assertFalse(point.canInvokeMember("w"));
        assertFalse(point.canInvokeMember("x"));
        assertFalse(point.canInvokeMember("y"));
        assertFalse(point.canInvokeMember("z"));

        Object proxy2 = Proxy.newProxyInstance(classLoader, new Class<?>[]{ProxiedPoint2.class}, invocationHandler);
        point = context.asValue(proxy2);
        assertFalse(point.canInvokeMember("w"));
        assertFalse(point.canInvokeMember("x"));
        assertFalse(point.canInvokeMember("y"));
        assertFalse(point.canInvokeMember("z"));

        setupEnv(HostAccess.newBuilder(HostAccess.EXPLICIT).allowAccessInheritance(true).build());

        point = context.asValue(proxy);
        assertFalse(point.canInvokeMember("w"));
        assertEquals(42, point.invokeMember("x").asInt());
        assertEquals(43, point.invokeMember("y").asInt());
        assertEquals(44, point.invokeMember("z").asInt());

        point = context.asValue(proxy2);
        assertFalse(point.canInvokeMember("w"));
        assertEquals(42, point.invokeMember("x").asInt());
        // @Export on ProxiedPoint.x is inherited
        assertEquals(43, point.invokeMember("y").asInt());
        assertEquals(44, point.invokeMember("z").asInt());
    }

    @FunctionalInterface
    public interface LambdaInterface {
        /** Not exported. */
        default int w() {
            return 41;
        }

        @Export
        int x();

        @Export
        default int y() {
            return 43;
        }

        @Export
        default int z() {
            return 44;
        }
    }

    @FunctionalInterface
    public interface LambdaInterface2 extends LambdaInterface {
        @Export
        @Override
        int x();

        /** Exported in {@link LambdaInterface} but not in {@link LambdaInterface2}. */
        @Override
        default int z() {
            return 44;
        }
    }

    @Test
    public void testLambdaInterfaceMethodExportInheritance() {
        setupEnv(HostAccess.newBuilder(HostAccess.EXPLICIT).allowAccessInheritance(false).build());

        LambdaInterface lambda = () -> 42;
        Value point;
        point = context.asValue(lambda);
        assertFalse(point.canInvokeMember("w"));
        assertFalse(point.canInvokeMember("x"));
        assertEquals(43, point.invokeMember("y").asInt());
        assertEquals(44, point.invokeMember("z").asInt());

        LambdaInterface2 lambda2 = () -> 42;
        point = context.asValue(lambda2);
        assertFalse(point.canInvokeMember("w"));
        assertFalse(point.canInvokeMember("x"));
        assertEquals(43, point.invokeMember("y").asInt());
        assertFalse(point.canInvokeMember("z"));

        setupEnv(HostAccess.newBuilder(HostAccess.EXPLICIT).allowAccessInheritance(true).build());

        point = context.asValue(lambda);
        assertFalse(point.canInvokeMember("w"));
        assertEquals(42, point.invokeMember("x").asInt());
        assertEquals(43, point.invokeMember("y").asInt());
        assertEquals(44, point.invokeMember("z").asInt());

        point = context.asValue(lambda2);
        assertFalse(point.canInvokeMember("w"));
        assertEquals(42, point.invokeMember("x").asInt());
        assertEquals(43, point.invokeMember("y").asInt());
        assertEquals(44, point.invokeMember("z").asInt());
    }

    public static class MyClassLoader extends URLClassLoader {
        public MyClassLoader(URL[] urls) {
            super(urls);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return super.loadClass(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            return super.loadClass(name, resolve);
        }
    }

    private static final class IteratorImpl<T> implements Iterator<T> {

        private final T[] values;
        private int index;

        @SuppressWarnings("unchecked")
        IteratorImpl(T... values) {
            this.values = values;
        }

        @Override
        public boolean hasNext() {
            return index < values.length;
        }

        @Override
        public T next() {
            return values[index++];
        }
    }

    private static final class IterableImpl<T> implements Iterable<T> {

        private final T[] values;

        @SuppressWarnings("unchecked")
        IterableImpl(T... values) {
            this.values = values;
        }

        @Override
        public Iterator<T> iterator() {
            return new IteratorImpl<>(values);
        }
    }

    public abstract static class NoArgumentConstructorTestClass {
        public NoArgumentConstructorTestClass() {
        }

        public String returnString() {
            return RETURNED_STRING;
        }
    }

    public static class NoArgumentConstructorTestSubClass extends NoArgumentConstructorTestClass {
    }

    public abstract static class ArgumentConstructorTestClass {
        private final String str;

        public ArgumentConstructorTestClass(String str) {
            this.str = str;
        }

        public String returnString() {
            return str;
        }
    }

    public static class ArgumentConstructorTestSubClass extends ArgumentConstructorTestClass {
        public ArgumentConstructorTestSubClass(String str) {
            super(str);
        }
    }

    @TruffleLanguage.Registration
    static class NoArgAbstractClassInstantiationTestLanguage extends AbstractExecutableTestLanguage {

        @Override
        @CompilerDirectives.TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            try {
                Object classObj = env.lookupHostSymbol(NoArgumentConstructorTestClass.class.getName());
                interop.instantiate(classObj);
                fail();
            } catch (UnsupportedMessageException e) {
                return INSTANTIATION_FAILED;
            }
            return null;
        }
    }

    @TruffleLanguage.Registration
    static class NoArgSubclassInstantiationTestLanguage extends AbstractExecutableTestLanguage {

        @Override
        @CompilerDirectives.TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object classObj = env.lookupHostSymbol(NoArgumentConstructorTestSubClass.class.getName());
            Object obj = interop.instantiate(classObj);
            return interop.invokeMember(obj, "returnString");
        }
    }

    @TruffleLanguage.Registration
    static class ArgAbstractClassInstantiationTestLanguage extends AbstractExecutableTestLanguage {

        @Override
        @CompilerDirectives.TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            try {
                Object classObj = env.lookupHostSymbol(ArgumentConstructorTestClass.class.getName());
                interop.instantiate(classObj, RETURNED_STRING);
                fail();
            } catch (UnsupportedMessageException e) {
                return INSTANTIATION_FAILED;
            }
            return null;
        }
    }

    @TruffleLanguage.Registration
    static class ArgSubclassInstantiationTestLanguage extends AbstractExecutableTestLanguage {

        @Override
        @CompilerDirectives.TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object classObj = env.lookupHostSymbol(ArgumentConstructorTestSubClass.class.getName());
            Object obj = interop.instantiate(classObj, RETURNED_STRING);
            return interop.invokeMember(obj, "returnString");
        }
    }

    @TruffleLanguage.Registration
    static class NoArgAdapterInstantiationTestLanguage extends AbstractExecutableTestLanguage {

        @Override
        @CompilerDirectives.TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object classObj = env.lookupHostSymbol(NoArgumentConstructorTestClass.class.getName());
            classObj = env.createHostAdapter(new Object[]{classObj});
            Object obj = interop.instantiate(classObj, NullObject.SINGLETON);
            return interop.invokeMember(obj, "returnString");
        }
    }

    @TruffleLanguage.Registration
    static class ArgAdapterInstantiationTestLanguage extends AbstractExecutableTestLanguage {

        @Override
        @CompilerDirectives.TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object classObj = env.lookupHostSymbol(ArgumentConstructorTestClass.class.getName());
            classObj = env.createHostAdapter(new Object[]{classObj});
            Object obj = interop.instantiate(classObj, RETURNED_STRING, NullObject.SINGLETON);
            return interop.invokeMember(obj, "returnString");
        }
    }

    @Test
    public void testNoArgAbstractClassInstantiation() {
        try (Context c = Context.newBuilder().allowAllAccess(true).build()) {
            assertEquals(INSTANTIATION_FAILED, evalTestLanguage(c, NoArgAbstractClassInstantiationTestLanguage.class, "no argument constructor abstract class failure").asString());
        }
    }

    @Test
    public void testNoArgSubclassInstantiation() {
        try (Context c = Context.newBuilder().allowAllAccess(true).build()) {
            assertEquals(RETURNED_STRING, evalTestLanguage(c, NoArgSubclassInstantiationTestLanguage.class, "no argument constructor sub class success").asString());
        }
    }

    @Test
    public void testArgAbstractClassInstantiation() {
        try (Context c = Context.newBuilder().allowAllAccess(true).build()) {
            assertEquals(INSTANTIATION_FAILED, evalTestLanguage(c, ArgAbstractClassInstantiationTestLanguage.class, "argument constructor abstract class failure").asString());
        }
    }

    @Test
    public void testArgSubclassInstantiation() {
        try (Context c = Context.newBuilder().allowAllAccess(true).build()) {
            assertEquals(RETURNED_STRING, evalTestLanguage(c, ArgSubclassInstantiationTestLanguage.class, "argument constructor sub class success").asString());
        }
    }

    @Test
    public void testNoArgAdapterInstantiation() {
        TruffleTestAssumptions.assumeNotAOT();
        try (Context c = Context.newBuilder().allowAllAccess(true).build()) {
            assertEquals(RETURNED_STRING, evalTestLanguage(c, NoArgAdapterInstantiationTestLanguage.class, "no argument constructor adapter success").asString());
        }
    }

    @Test
    public void testArgAdapterInstantiation() {
        TruffleTestAssumptions.assumeNotAOT();
        try (Context c = Context.newBuilder().allowAllAccess(true).build()) {
            assertEquals(RETURNED_STRING, evalTestLanguage(c, ArgAdapterInstantiationTestLanguage.class, "argument constructor adapter success").asString());
        }
    }

    @Test
    public void testHostFunctionDisplayName() {
        try (Context cxt = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowHostClassLookup((s) -> true).build()) {
            assertEquals(BigInteger.class.getName() + ".valueOf", cxt.asValue(BigInteger.class).getMember("static").getMember("valueOf").toString());
            assertEquals(Class.class.getName() + ".getName", cxt.asValue(BigInteger.class).getMember("getName").toString());
            assertEquals(BigInteger.class.getName() + ".add", cxt.asValue(BigInteger.ZERO).getMember("add").toString());
            assertEquals(int[].class.getName() + ".clone", cxt.asValue(new int[0]).getMember("clone").toString());
        }
    }

    public static class StringMapTestObject {

        public String s0;
        public String s1;

        StringMapTestObject(String s0, String s1) {
            this.s0 = s0;
            this.s1 = s1;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class Hash implements TruffleObject {
        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasHashEntries() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        long getHashSize() {
            return 0;
        }

        @SuppressWarnings({"static-method", "unused"})
        @ExportMessage(name = "isHashEntryReadable")
        @ExportMessage(name = "isHashEntryModifiable")
        @ExportMessage(name = "isHashEntryRemovable")
        boolean isHashEntryExisting(Object key) {
            return false;
        }

        @SuppressWarnings({"static-method", "unused"})
        @ExportMessage
        boolean isHashEntryInsertable(Object key) {
            return false;
        }

        @SuppressWarnings({"static-method", "unused"})
        @ExportMessage
        Object readHashValue(Object key) throws UnknownKeyException, UnsupportedMessageException {
            throw UnknownKeyException.create(key);
        }

        @SuppressWarnings("unused")
        @ExportMessage
        void writeHashEntry(Object key, Object value) throws UnsupportedMessageException {

        }

        @SuppressWarnings("unused")
        @ExportMessage
        void removeHashEntry(Object key) throws UnknownKeyException {
        }

        @SuppressWarnings({"static-method", "unused"})
        @ExportMessage
        Object getHashEntriesIterator() throws UnsupportedMessageException {
            return null;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Executable implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @SuppressWarnings({"static-method", "unused"})
        @ExportMessage
        Object execute(Object[] arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return null;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Members implements TruffleObject {
        Array members = new Array();

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @SuppressWarnings({"static-method", "unused"})
        @ExportMessage
        Object getMembers(boolean includeInternal) {
            return members;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Array implements TruffleObject {

        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean hasArrayElements() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object readArrayElement(long index) throws InvalidArrayIndexException {
            throw InvalidArrayIndexException.create(index);
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public long getArraySize() {
            return 0;
        }

        @SuppressWarnings({"static-method", "unused"})
        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return false;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class IteratorTO implements TruffleObject {

        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isIterator() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasIteratorNextElement() {
            return false;
        }

        @SuppressWarnings({"static-method", "unused"})
        @ExportMessage
        Object getIteratorNextElement() throws StopIterationException {
            return null;
        }

    }

    /*
     * Referenced in proxys.json
     */
    @FunctionalInterface
    interface FuncInterface {
        void execute();
    }

    /*
     * Referenced in proxys.json
     */
    interface HostMembers {
    }

    @Test
    public void testMutableObjectMapping() {
        for (MutableTargetMapping m : MutableTargetMapping.values()) {
            testMutableObjectMappingEnabled(m);
            testMutableObjectMappingDisabled(m);
        }
    }

    public void testMutableObjectMappingDisabled(MutableTargetMapping mapping) {
        MutableTargetMapping[] allowed = new MutableTargetMapping[MutableTargetMapping.values().length - 1];
        int i = 0;
        for (MutableTargetMapping m : MutableTargetMapping.values()) {
            if (m != mapping) {
                allowed[i++] = m;
            }
        }
        setupEnv(HostAccess.newBuilder(HostAccess.ALL).allowMutableTargetMappings(allowed).build());

        Array array = new Array();
        StringMapTestObject stringMap = new StringMapTestObject("a", "b");

        switch (mapping) {
            case ARRAY_TO_JAVA_LIST:
                Value v1 = context.asValue(array);
                assertFails(() -> v1.as(List.class), ClassCastException.class);
                break;
            case ITERABLE_TO_JAVA_ITERABLE:
                Value v2 = context.asValue(array);
                assertFails(() -> v2.as(Iterable.class), ClassCastException.class);
                break;
            case ITERATOR_TO_JAVA_ITERATOR:
                IteratorTO it = new IteratorTO();
                Value v3 = context.asValue(it);
                assertFails(() -> v3.as(Iterator.class), ClassCastException.class);
                break;
            case HASH_TO_JAVA_MAP:
                Hash hash = new Hash();
                Value v4 = context.asValue(hash);
                assertFails(() -> v4.as(Map.class), ClassCastException.class);
                break;
            case MEMBERS_TO_JAVA_MAP:
                Value v5 = context.asValue(stringMap);
                assertFails(() -> v5.as(Map.class), ClassCastException.class);
                break;
            case EXECUTABLE_TO_JAVA_INTERFACE:
                Value v6 = context.asValue(new Executable());
                assertFails(() -> v6.as(FuncInterface.class), ClassCastException.class);
                break;
            case MEMBERS_TO_JAVA_INTERFACE:
                Value v7 = context.asValue(new Members());
                assertFails(() -> v7.as(HostMembers.class), ClassCastException.class);
                break;
            default:
                fail("No test for target mapping " + mapping);
        }
    }

    private void testMutableObjectMappingEnabled(MutableTargetMapping mapping) {
        setupEnv(HostAccess.newBuilder(HostAccess.ALL).allowMutableTargetMappings(mapping).build());
        Array array = new Array();
        StringMapTestObject stringMap = new StringMapTestObject("a", "b");
        Value v = null;
        switch (mapping) {
            case ARRAY_TO_JAVA_LIST:
                v = context.asValue(array);
                assertNotNull(v.as(List.class));
                break;
            case ITERABLE_TO_JAVA_ITERABLE:
                v = context.asValue(array);
                assertNotNull(v.as(Iterable.class));
                break;
            case ITERATOR_TO_JAVA_ITERATOR:
                IteratorTO it = new IteratorTO();
                v = context.asValue(it);
                assertNotNull(v.as(Iterator.class));
                break;
            case HASH_TO_JAVA_MAP:
                Hash hash = new Hash();
                v = context.asValue(hash);
                assertNotNull(v.as(Map.class));
                break;
            case MEMBERS_TO_JAVA_MAP:
                v = context.asValue(stringMap);
                assertNotNull(v.as(Map.class));
                break;
            case EXECUTABLE_TO_JAVA_INTERFACE:
                v = context.asValue(new Executable());
                assertNotNull(v.as(FuncInterface.class));
                break;
            case MEMBERS_TO_JAVA_INTERFACE:
                v = context.asValue(new Members());
                assertNotNull(v.as(HostMembers.class));
                break;
            default:
                fail("No test for target mapping " + mapping);
        }
    }

    /**
     * Target type mappings with lower precedence than default are still applied even if mutable
     * default mappings are disabled.
     */

    @Test
    public void testMutableObjectWithCustomTargetType() {
        for (MutableTargetMapping m : MutableTargetMapping.values()) {
            testMutableObjectWithCustomTargetType(m, TargetMappingPrecedence.HIGHEST, true, false);
            testMutableObjectWithCustomTargetType(m, TargetMappingPrecedence.HIGH, true, false);
            testMutableObjectWithCustomTargetType(m, TargetMappingPrecedence.LOW, true, false);
            testMutableObjectWithCustomTargetType(m, TargetMappingPrecedence.LOWEST, true, true);
            testMutableObjectWithCustomTargetType(m, TargetMappingPrecedence.HIGHEST, false, false);
            testMutableObjectWithCustomTargetType(m, TargetMappingPrecedence.HIGH, false, false);
            testMutableObjectWithCustomTargetType(m, TargetMappingPrecedence.LOW, false, false);
            testMutableObjectWithCustomTargetType(m, TargetMappingPrecedence.LOWEST, false, false);
        }
    }

    private void testMutableObjectWithCustomTargetType(MutableTargetMapping mapping, TargetMappingPrecedence precedence, boolean defaultEnabled, boolean defaultApplied) {
        HostAccess.Builder habuilder = HostAccess.newBuilder(HostAccess.ALL);
        if (!defaultEnabled) {
            habuilder.allowMutableTargetMappings();
        }
        Value v;
        switch (mapping) {
            case ARRAY_TO_JAVA_LIST:
                habuilder = setupTargetTypeMapping(habuilder, List.class, precedence);
                setupEnv(habuilder.build());
                v = context.asValue(new Array());
                assertTargetType(List.class, v, defaultApplied);
                break;
            case ITERABLE_TO_JAVA_ITERABLE:
                habuilder = setupTargetTypeMapping(habuilder, Iterable.class, precedence);
                setupEnv(habuilder.build());
                v = context.asValue(new Array());
                assertTargetType(Iterable.class, v, defaultApplied);
                break;
            case ITERATOR_TO_JAVA_ITERATOR:
                habuilder = setupTargetTypeMapping(habuilder, Iterator.class, precedence);
                setupEnv(habuilder.build());
                v = context.asValue(new IteratorTO());
                assertTargetType(Iterator.class, v, defaultApplied);
                break;
            case HASH_TO_JAVA_MAP:
                habuilder = setupTargetTypeMapping(habuilder, Map.class, precedence);
                setupEnv(habuilder.build());
                v = context.asValue(new Hash());
                assertTargetType(Map.class, v, defaultApplied);
                break;
            case MEMBERS_TO_JAVA_MAP:
                habuilder = setupTargetTypeMapping(habuilder, Map.class, precedence);
                setupEnv(habuilder.build());
                v = context.asValue(new StringMapTestObject("a", "b"));
                assertTargetType(Map.class, v, defaultApplied);
                break;
            case EXECUTABLE_TO_JAVA_INTERFACE:
                habuilder = setupTargetTypeMapping(habuilder, FuncInterface.class, precedence);
                setupEnv(habuilder.build());
                v = context.asValue(new Executable());
                assertTargetType(FuncInterface.class, v, defaultApplied);
                break;
            case MEMBERS_TO_JAVA_INTERFACE:
                habuilder = setupTargetTypeMapping(habuilder, HostMembers.class, precedence);
                setupEnv(habuilder.build());
                v = context.asValue(new Members());
                assertTargetType(HostMembers.class, v, defaultApplied);
                break;
            default:
                fail("No test for target mapping " + mapping);
        }
    }

    private static final Map<?, ?> customMap = Map.of("replaced", "a");
    private static final List<?> customList = List.of("replaced");
    private static final Iterable<?> customIterable = new Iterable<>() {
        public Iterator<Object> iterator() {
            return customIterator;
        }
    };
    private static final Iterator<Object> customIterator = new Iterator<>() {
        public boolean hasNext() {
            return false;
        }

        public Object next() {
            return null;
        }
    };
    private static final FuncInterface customFunctional = new FuncInterface() {
        public void execute() {
        }
    };
    private static final HostMembers customHostMembers = new HostMembers() {
    };

    private static HostAccess.Builder setupTargetTypeMapping(HostAccess.Builder builder, Class<?> c, TargetMappingPrecedence precedence) {
        if (c == Map.class) {
            return builder.targetTypeMapping(Value.class, Map.class, null, (s) -> {
                return customMap;
            }, precedence);
        }
        if (c == List.class) {
            return builder.targetTypeMapping(Value.class, List.class, null, (s) -> {
                return customList;
            }, precedence);
        }
        if (c == Iterable.class) {
            return builder.targetTypeMapping(Value.class, Iterable.class, null, (s) -> {
                return customIterable;
            }, precedence);
        }
        if (c == Iterator.class) {
            return builder.targetTypeMapping(Value.class, Iterator.class, null, (s) -> {
                return customIterator;
            }, precedence);
        }
        if (c == FuncInterface.class) {
            return builder.targetTypeMapping(Value.class, FuncInterface.class, null, (s) -> {
                return customFunctional;
            }, precedence);
        }
        if (c == HostMembers.class) {
            return builder.targetTypeMapping(Value.class, HostMembers.class, null, (s) -> {
                return customHostMembers;
            }, precedence);
        }
        fail("No target type mapping for " + c.getName());
        return null;
    }

    private static void assertTargetType(Class<?> c, Value v, boolean defaultApplied) {
        Object customTarget = null;
        if (c == Map.class) {
            customTarget = customMap;
        } else if (c == List.class) {
            customTarget = customList;
        } else if (c == Iterable.class) {
            customTarget = customIterable;
        } else if (c == Iterator.class) {
            customTarget = customIterator;
        } else if (c == FuncInterface.class) {
            customTarget = customFunctional;
        } else if (c == HostMembers.class) {
            customTarget = customHostMembers;
        } else {
            fail("No target type assert for " + c.getName());
        }

        if (defaultApplied) {
            assertNotEquals(customTarget, v.as(c));
        } else {
            assertEquals(customTarget, v.as(c));
        }
    }
}
