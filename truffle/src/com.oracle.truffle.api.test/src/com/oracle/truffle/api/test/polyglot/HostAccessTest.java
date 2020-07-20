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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.HostAccess.Implementable;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.tck.tests.ValueAssert;
import com.oracle.truffle.tck.tests.ValueAssert.Trait;

public class HostAccessTest {
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
        assertEquals(0, value.getMemberKeys().size());
        ValueAssert.assertValue(value, false, Trait.ARRAY_ELEMENTS, Trait.MEMBERS, Trait.HOST_OBJECT);
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

        ValueAssert.assertValue(value, false, Trait.ARRAY_ELEMENTS, Trait.MEMBERS, Trait.HOST_OBJECT);
        assertArrayAccessDisabled(context);
    }

    @Test
    public void testListAccessDisabled() {
        setupEnv(HostAccess.newBuilder().allowListAccess(false));
        assertListAccessDisabled(context);
    }

    @Test
    public void testPublicAccessNoListAccess() {
        setupEnv(HostAccess.newBuilder().allowPublicAccess(true).allowListAccess(false));
        assertListAccessDisabled(context);
    }

    private static void assertListAccessDisabled(Context context) {
        List<Integer> array = new ArrayList<>(Arrays.asList(1, 2, 3));
        Value value = context.asValue(array);
        assertSame(array, value.asHostObject());
        ValueAssert.assertValue(value, false, Trait.MEMBERS, Trait.HOST_OBJECT);
    }

    private Context context;

    private void setupEnv(HostAccess.Builder builder) {
        tearDown();
        if (builder != null) {
            builder.allowImplementationsAnnotatedBy(FunctionalInterface.class);
            builder.allowImplementationsAnnotatedBy(HostAccess.Implementable.class);
            builder.allowAccessAnnotatedBy(HostAccess.Export.class);
            setupEnv(builder.build());
        }
    }

    private void setupEnv(HostAccess access) {
        tearDown();
        context = Context.newBuilder().allowHostAccess(access).build();
    }

    @After
    public void tearDown() {
        if (context != null) {
            context.close();
            context = null;
        }
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

    static final TypeLiteral<List<TargetClass1>> TARGET_CLASS_LIST = new TypeLiteral<List<TargetClass1>>() {
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

    static final TypeLiteral<Map<String, TargetClass1>> TARGET_CLASS_MAP_STRING = new TypeLiteral<Map<String, TargetClass1>>() {
    };

    static final TypeLiteral<Map<Long, TargetClass1>> TARGET_CLASS_MAP_LONG = new TypeLiteral<Map<Long, TargetClass1>>() {
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
        Predicate<Integer> errorAccepts = new Predicate<Integer>() {
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
            assertTrue(e.asHostException() instanceof NullPointerException);
        }

        assertNull(context.asValue(42).as(int.class));
        assertEquals(43, context.asValue(43).asInt());
    }

    @Test
    public void testReturnsNull() {
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
            assertTrue(e.asHostException() instanceof NullPointerException);
        }

        assertNull(context.asValue(42).as(int.class));
        assertEquals(43, context.asValue(43).asInt());
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
}
