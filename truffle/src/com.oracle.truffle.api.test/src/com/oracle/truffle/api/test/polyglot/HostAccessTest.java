/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.test.polyglot.ValueAssert.Trait;

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

        try (Context c = Context.newBuilder().allowHostAccess(config).build()) {
            Value readValue = c.eval("sl", "" +
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
    }

    @Test
    public void banAccessToEquals() throws Exception {
        // @formatter:off
        HostAccess config = HostAccess.newBuilder().
            allowPublicAccess(true).
            denyAccess(Object.class, false).
            build();
        // @formatter:on

        try (Context c = Context.newBuilder().allowHostAccess(config).build()) {
            Value readValue = c.eval("sl", "" +
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
    }

    @Test
    public void banAccessToLoadClass() throws Exception {
        // @formatter:off
        HostAccess config = HostAccess.newBuilder().
            allowPublicAccess(true).
            denyAccess(ClassLoader.class).
            build();
        // @formatter:on

        try (Context c = Context.newBuilder().allowHostAccess(config).build()) {
            Value loadClass = c.eval("sl", "" +
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
    }

    @Test
    public void banAccessToOverwrittenLoadClass() throws Exception {
        // @formatter:off
        HostAccess config = HostAccess.newBuilder().
            allowPublicAccess(true).
            denyAccess(ClassLoader.class).
            build();
        // @formatter:on

        try (Context c = Context.newBuilder().allowHostAccess(config).build()) {
            Value loadClass = c.eval("sl", "" +
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
    }

    @Test
    public void publicCanAccessObjectEquals() throws Exception {
        HostAccess config = HostAccess.ALL;

        try (Context c = Context.newBuilder().allowHostAccess(config).build()) {
            Value readValue = c.eval("sl", "" +
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
    }

    @Test
    public void inheritFromPublic() throws Exception {
        HostAccess config = HostAccess.newBuilder().allowPublicAccess(true).build();

        try (Context c = Context.newBuilder().allowHostAccess(config).build()) {
            Value readValue = c.eval("sl", "" +
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
        HostAccess hostAccess = HostAccess.newBuilder().allowArrayAccess(true).build();
        try (Context context = Context.newBuilder().allowHostAccess(hostAccess).build()) {
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
    }

    @Test
    public void testArrayAccessDisabled() {
        HostAccess hostAccess = HostAccess.newBuilder().allowArrayAccess(false).build();
        try (Context context = Context.newBuilder().allowHostAccess(hostAccess).build()) {
            assertArrayAccessDisabled(context);
        }
    }

    @Test
    public void testPublicAccessNoArrayAccess() {
        HostAccess hostAccess = HostAccess.newBuilder().allowPublicAccess(true).build();
        try (Context context = Context.newBuilder().allowHostAccess(hostAccess).build()) {
            assertArrayAccessDisabled(context);
        }
    }

    private static void assertArrayAccessDisabled(Context context) {
        int[] array = new int[]{1, 2, 3};
        Value value = context.asValue(array);
        assertSame(array, value.asHostObject());
        ValueAssert.assertValue(value, false, Trait.MEMBERS, Trait.HOST_OBJECT);
    }

    @Test
    public void testListAccessEnabled() {
        HostAccess hostAccess = HostAccess.newBuilder().allowListAccess(true).build();
        try (Context context = Context.newBuilder().allowHostAccess(hostAccess).build()) {
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
    }

    @Test
    public void testListAccessDisabled() {
        HostAccess hostAccess = HostAccess.newBuilder().allowListAccess(false).build();
        try (Context context = Context.newBuilder().allowHostAccess(hostAccess).build()) {
            assertListAccessDisabled(context);
        }
    }

    @Test
    public void testPublicAccessNoListAccess() {
        HostAccess hostAccess = HostAccess.newBuilder().allowPublicAccess(true).allowListAccess(false).build();
        try (Context context = Context.newBuilder().allowHostAccess(hostAccess).build()) {
            assertListAccessDisabled(context);
        }
    }

    private static void assertListAccessDisabled(Context context) {
        List<Integer> array = new ArrayList<>(Arrays.asList(1, 2, 3));
        Value value = context.asValue(array);
        assertSame(array, value.asHostObject());
        ValueAssert.assertValue(value, false, Trait.MEMBERS, Trait.HOST_OBJECT);
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
