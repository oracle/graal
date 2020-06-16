/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.HostAccess.Implementable;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;

public class ExposeToGuestTest {
    @Test
    public void byDefaultOnlyAnnotatedMethodsCanBeAccessed() {
        Context context = Context.create();
        Value readValue = context.eval("sl", "" + "function readValue(x) {\n" + "  return x.value;\n" + "}\n" + "function main() {\n" + "  return readValue;\n" + "}\n");
        Assert.assertEquals(42, readValue.execute(new ExportedValue()).asInt());
        assertPropertyUndefined("PublicValue isn't enough by default", readValue, new PublicValue());
    }

    private static void assertPropertyUndefined(String msg, Value readValue, Object value) {
        assertPropertyUndefined(msg, "value", readValue, value);
    }

    static void assertPropertyUndefined(String msg, String propName, Value readValue, Object value) {
        try {
            readValue.execute(value);
            fail(msg);
        } catch (PolyglotException ex) {
            assertEquals("Undefined property: " + propName, ex.getMessage());
        }
    }

    public static class PublicValue {
        public int value = 42;
    }

    public static class ExportedValue {
        @HostAccess.Export public int value = 42;
    }

    @Test
    public void exportingAllPublicIsEasy() {
        Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
        Value readValue = context.eval("sl", "" + "function readValue(x) {\n" + "  return x.value;\n" + "}\n" + "function main() {\n" + "  return readValue;\n" + "}\n");
        Assert.assertEquals(42, readValue.execute(new PublicValue()).asInt());
        Assert.assertEquals(42, readValue.execute(new ExportedValue()).asInt());
    }

    @Test
    public void customExportedAnnotation() {
        HostAccess accessMeConfig = HostAccess.newBuilder().allowAccessAnnotatedBy(AccessMe.class).build();
        Context context = Context.newBuilder().allowHostAccess(accessMeConfig).build();
        Value readValue = context.eval("sl", "" + "function readValue(x) {\n" + "  return x.value;\n" + "}\n" + "function main() {\n" + "  return readValue;\n" + "}\n");
        Assert.assertEquals(42, readValue.execute(new AccessibleValue()).asInt());
        assertPropertyUndefined("Default annotation isn't enough", readValue, new ExportedValue());
        assertPropertyUndefined("Public isn't enough by default", readValue, new PublicValue());
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.FIELD})
    public @interface AccessMe {
    }

    public static class AccessibleValue {
        @AccessMe public int value = 42;
    }

    @Test
    public void explicitlyEnumeratingField() throws Exception {
        HostAccess explictConfig = HostAccess.newBuilder().allowAccess(AccessibleValue.class.getField("value")).build();
        Context context = Context.newBuilder().allowHostAccess(explictConfig).build();
        Value readValue = context.eval("sl", "" + "function readValue(x) {\n" + "  return x.value;\n" + "}\n" + "function main() {\n" + "  return readValue;\n" + "}\n");
        Assert.assertEquals(42, readValue.execute(new AccessibleValue()).asInt());
        assertPropertyUndefined("Default annotation isn't enough", readValue, new ExportedValue());
        assertPropertyUndefined("Public isn't enough by default", readValue, new PublicValue());
    }

    public static class Foo<T extends Number> {
        @HostAccess.Export
        @SuppressWarnings("unused")
        public Object foo(T x) {
            return "basic foo";
        }
    }

    static class Bar extends Foo<Number> {
        @Override
        @SuppressWarnings("unused")
        public Object foo(Number x) {
            return "enhanced bar";
        }
    }

    static class PackagePrivateBar {
        @SuppressWarnings("unused")
        public Object foo(Number x) {
            fail("Never called");
            return "hidden bar";
        }
    }

    static class PrivateFoo<T extends Number> extends Foo<T> {
        @SuppressWarnings("all")
        private Object foo(Integer y) {
            fail("Never called");
            return "hidden foo";
        }
    }

    @SuppressWarnings("all")
    public static class PrivateChangedFoo<T extends Integer> extends PrivateFoo<T> {
        @HostAccess.Export
        @Override
        public Object foo(T x) {
            return "overriden foo";
        }
    }

    @Test
    public void fooBarExposedByInheritance() throws Exception {
        Context context = Context.newBuilder().allowHostAccess(HostAccess.EXPLICIT).build();
        Value readValue = context.eval("sl", "" + "function callFoo(x) {\n" + "  return x.foo(1);\n" + "}\n" + "function main() {\n" + "  return callFoo;\n" + "}\n");
        Assert.assertEquals("basic foo", readValue.execute(new Foo<>()).asString());
        Assert.assertEquals("enhanced bar", readValue.execute(new Bar()).asString());
        assertPropertyUndefined("Cannot call public method in package private class", "foo", readValue, new PackagePrivateBar());
        Assert.assertEquals("basic foo", readValue.execute(new PrivateFoo<>()).asString());
        Assert.assertEquals("overriden foo", readValue.execute(new PrivateChangedFoo<>()).asString());
    }

    @FunctionalInterface
    public interface FooInterface<T extends Number> {
        @HostAccess.Export
        Object foo(T value);
    }

    @Test
    public void functionalInterfaceCall() throws Exception {
        Context context = Context.newBuilder().allowHostAccess(HostAccess.EXPLICIT).build();
        Value readValue = context.eval("sl", "" + "function callFoo(x) {\n" + "  return x.foo(1);\n" + "}\n" + "function main() {\n" + "  return callFoo;\n" + "}\n");
        FooInterface<Number> foo = (ignore) -> "functional foo";
        Assert.assertEquals("functional foo", readValue.execute(foo).asString());
    }

    @Test
    public void listAccessAllowedInPublicHostAccess() throws Exception {
        doAccessAllowedInPublicHostAccess(true);
    }

    @Test
    public void arrayAccessAllowedInPublicHostAccess() throws Exception {
        doAccessAllowedInPublicHostAccess(false);
    }

    private static void doAccessAllowedInPublicHostAccess(boolean asList) throws Exception {
        Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
        Value readValue = context.eval("sl", "" + "function callFoo(x) {\n" + "  return x.foo(1)[0];\n" + "}\n" + "function main() {\n" + "  return callFoo;\n" + "}\n");
        boolean[] gotIn = {false};
        FooInterface<Number> foo = returnAsArrayOrList(gotIn, asList);
        final Value arrayRead = readValue.execute(foo);
        Assert.assertTrue("Foo lamda called", gotIn[0]);
        Assert.assertEquals(1, arrayRead.asInt());
    }

    @Test
    public void listAccessForbiddenInExplicit() throws Exception {
        doAccessForbiddenInExplicit(true);
    }

    @Test
    public void arrayAccessForbiddenInExplicit() throws Exception {
        doAccessForbiddenInExplicit(false);
    }

    private static void doAccessForbiddenInExplicit(boolean asList) throws Exception {
        Context context = Context.newBuilder().allowHostAccess(HostAccess.EXPLICIT).build();
        Value readValue = context.eval("sl", "" + "function callFoo(x) {\n" + "  return x.foo(1)[0];\n" + "}\n" + "function main() {\n" + "  return callFoo;\n" + "}\n");
        boolean[] gotIn = {false};
        FooInterface<Number> foo = returnAsArrayOrList(gotIn, asList);
        final Value arrayRead;
        try {
            arrayRead = readValue.execute(foo);
        } catch (Exception ex) {
            assertEquals("Expecting an exception", PolyglotException.class, ex.getClass());
            Assert.assertTrue("Foo lamda called", gotIn[0]);
            return;
        }
        fail("The read shouldn't succeed: " + arrayRead);
    }

    @Test
    public void listAccessForbiddenInManual() throws Exception {
        doAccessForbiddenInManual(true);
    }

    @Test
    public void arrayAccessForbiddenInManual() throws Exception {
        doAccessForbiddenInManual(false);
    }

    private static void doAccessForbiddenInManual(boolean asList) throws Exception {
        HostAccess config = HostAccess.newBuilder().allowAccess(FooInterface.class.getMethod("foo", Number.class)).build();
        Context context = Context.newBuilder().allowHostAccess(config).build();
        Value readValue = context.eval("sl", "" + "function callFoo(x) {\n" + "  return x.foo(1)[0];\n" + "}\n" + "function main() {\n" + "  return callFoo;\n" + "}\n");
        boolean[] gotIn = {false};
        FooInterface<Number> foo = returnAsArrayOrList(gotIn, asList);
        final Value arrayRead;
        try {
            arrayRead = readValue.execute(foo);
        } catch (Exception ex) {
            assertEquals("Expecting an exception", PolyglotException.class, ex.getClass());
            Assert.assertTrue("Foo lamda called", gotIn[0]);
            return;
        }
        fail("The read shouldn't succeed: " + arrayRead);
    }

    private static FooInterface<Number> returnAsArrayOrList(boolean[] gotIn, boolean asList) {
        FooInterface<Number> foo = (n) -> {
            gotIn[0] = true;
            if (asList) {
                return Arrays.asList(n);
            } else {
                return new Number[]{n};
            }
        };
        return foo;
    }

    public static class FieldAccess {

        public static Object staticField = "42";
        public static final Object finalField = "42";

        @Export public static Object exportedStaticField = "42";
        @Export public static final Object exportedField = "42";
    }

    @Test
    public void staticFieldAccessIsForbidden() throws InteropException {
        Context.Builder builder = Context.newBuilder();
        builder.allowHostClassLookup((c) -> c.endsWith("FieldAccess"));
        Context c = builder.build();
        c.initialize(ProxyLanguage.ID);
        c.enter();
        try {
            Object hostLookup = ProxyLanguage.getCurrentContext().getEnv().lookupHostSymbol(FieldAccess.class.getName());
            assertMember(hostLookup, "staticField", false, false);
            assertMember(hostLookup, "finalField", false, false);
            assertMember(hostLookup, "exportedStaticField", true, true);
            assertMember(hostLookup, "exportedField", true, false);
        } finally {
            c.leave();
            c.close();
        }

    }

    private static void assertMember(Object object, String member, boolean readable, boolean modifiable) throws InteropException {
        InteropLibrary interop = InteropLibrary.getFactory().getUncached();
        assertTrue(interop.hasMembers(object));
        assertEquals(readable, interop.isMemberReadable(object, member));
        assertEquals(modifiable, interop.isMemberModifiable(object, member));
        assertFalse(interop.isMemberInsertable(object, member));
        assertFalse(interop.isMemberRemovable(object, member));

        if (readable) {
            assertEquals("42", interop.readMember(object, member));
        } else {
            try {
                interop.readMember(object, member);
                fail();
            } catch (UnknownIdentifierException e) {
            }
        }
        if (modifiable) {
            interop.writeMember(object, member, "42");
        } else {
            try {
                interop.writeMember(object, member, "43");
                fail();
            } catch (UnknownIdentifierException e) {
            }
        }
        try {
            interop.removeMember(object, member);
            fail();
        } catch (UnsupportedMessageException e) {
        }
    }

    @SuppressWarnings("unused")
    public static class AllowedConstructorAccess {

        @HostAccess.Export
        public AllowedConstructorAccess(String s) {
        }

        public AllowedConstructorAccess() {
        }

        AllowedConstructorAccess(int c) {
        }

    }

    public static class DeniedConstructorAccess {

        public DeniedConstructorAccess() {
        }

    }

    @Test
    public void staticConstructorAccessIsForbidden() throws InteropException {
        Context.Builder builder = Context.newBuilder();
        builder.allowHostClassLookup((c) -> c.endsWith("ConstructorAccess"));
        Context c = builder.build();
        c.initialize(ProxyLanguage.ID);
        c.enter();
        try {
            Object allowed = ProxyLanguage.getCurrentContext().getEnv().lookupHostSymbol(AllowedConstructorAccess.class.getName());
            InteropLibrary library = InteropLibrary.getFactory().getUncached();
            assertTrue(library.isInstantiable(allowed));
            try {
                library.instantiate(allowed);
                fail();
            } catch (ArityException e) {
            }
            try {
                library.instantiate(allowed, 42);
                fail();
            } catch (UnsupportedTypeException e) {
            }
            assertNotNull(library.instantiate(allowed, "asdf"));

            Object denied = ProxyLanguage.getCurrentContext().getEnv().lookupHostSymbol(DeniedConstructorAccess.class.getName());
            assertFalse(library.isInstantiable(denied));
            try {
                library.instantiate(denied);
                fail();
            } catch (UnsupportedMessageException e) {
            }
        } finally {
            c.leave();
            c.close();
        }
    }

    interface EmptyInterface {
    }

    interface UnmarkedInterface {

        Object exported(String arg);

    }

    @Implementable
    interface MarkedInterface {

        String exported(String arg);

    }

    @FunctionalInterface
    interface MarkedFunctional {

        int f();

    }

    interface UnmarkedFunctional {

        int f();

    }

    public static class Impl {

        @Export
        public Object exported(Object arg) {
            return arg;
        }

        @Export
        public Object noArg() {
            return 42;
        }

    }

    @SuppressWarnings("unused")
    public static class Overloaded {

        @Export
        public Object overloaded(MarkedFunctional arg) {
            return "MarkedFunctional";
        }

        @Export
        public Object overloaded(MarkedInterface arg) {
            return "MarkedInterface";
        }

        @Export
        public Object overloaded(String arg) {
            return arg;
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProxyOverloads() {
        HostAccess access = HostAccess.EXPLICIT;
        try (Context c = Context.newBuilder().allowHostAccess(access).build()) {
            c.initialize(ProxyLanguage.ID);
            Value v = c.asValue(new Overloaded());
            Value arg = c.asValue(new Impl());
            try {
                v.invokeMember("overloaded", arg);
                fail();
            } catch (IllegalArgumentException e) {
                // multiple overloads
                assertTrue(e.getMessage(), e.getMessage().contains("Multiple applicable overloads"));
            }
            assertEquals("42", v.invokeMember("overloaded", "42").asString());
        }

        // disable interface proxies.
        access = HostAccess.newBuilder().allowAccessAnnotatedBy(Export.class).build();
        try (Context c = Context.newBuilder().allowHostAccess(access).build()) {
            c.initialize(ProxyLanguage.ID);
            Value v = c.asValue(new Overloaded());
            Value arg = c.asValue(new Impl());
            try {
                v.invokeMember("overloaded", arg);
                fail();
            } catch (IllegalArgumentException e) {
                // multiple overloads
                assertTrue(e.getMessage(), e.getMessage().contains("no applicable overload found"));
            }
            assertEquals("42", v.invokeMember("overloaded", "42").asString());
        }

        // disable only object proxies
        access = HostAccess.newBuilder().allowAccessAnnotatedBy(Export.class).allowImplementationsAnnotatedBy(FunctionalInterface.class).build();
        try (Context c = Context.newBuilder().allowHostAccess(access).build()) {
            c.initialize(ProxyLanguage.ID);
            Value v = c.asValue(new Overloaded());
            Value arg = c.asValue(new Impl());
            assertEquals("MarkedFunctional", v.invokeMember("overloaded", arg.getMember("noArg")).asString());
            assertEquals("42", v.invokeMember("overloaded", "42").asString());
        }

        // disable only functional proxies
        access = HostAccess.newBuilder().allowAccessAnnotatedBy(Export.class).allowImplementations(MarkedInterface.class).build();
        try (Context c = Context.newBuilder().allowHostAccess(access).build()) {
            c.initialize(ProxyLanguage.ID);
            Value v = c.asValue(new Overloaded());
            Value arg = c.asValue(new Impl());
            assertEquals("MarkedInterface", v.invokeMember("overloaded", arg).asString());
            assertEquals("42", v.invokeMember("overloaded", "42").asString());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProxyExplicit() {
        HostAccess access = HostAccess.EXPLICIT;
        try (Context c = Context.newBuilder().allowHostAccess(access).build()) {
            c.initialize(ProxyLanguage.ID);
            Value v = c.asValue(new Impl());
            Value f = v.getMember("noArg");
            assertEquals("42", v.as(MarkedInterface.class).exported("42"));
            try {
                v.as(EmptyInterface.class);
                fail();
            } catch (ClassCastException e) {
            }
            try {
                v.as(UnmarkedInterface.class);
                fail();
            } catch (ClassCastException e) {
            }

            assertEquals(42, f.as(MarkedFunctional.class).f());
            try {
                f.as(UnmarkedFunctional.class);
                fail();
            } catch (ClassCastException e) {
            }
            assertEquals(42, f.as(Function.class).apply(null));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProxyMarked() {
        HostAccess access = HostAccess.newBuilder().allowAccessAnnotatedBy(Export.class).//
                        allowImplementations(UnmarkedInterface.class).allowImplementations(UnmarkedFunctional.class).build();
        try (Context c = Context.newBuilder().allowHostAccess(access).build()) {
            c.initialize(ProxyLanguage.ID);
            Value v = c.asValue(new Impl());
            Value f = v.getMember("noArg");
            assertEquals("42", v.as(UnmarkedInterface.class).exported("42"));
            try {
                v.as(EmptyInterface.class);
                fail();
            } catch (ClassCastException e) {
            }
            try {
                v.as(MarkedInterface.class);
                fail();
            } catch (ClassCastException e) {
            }

            assertEquals(42, f.as(UnmarkedFunctional.class).f());
            try {
                f.as(MarkedFunctional.class);
                fail();
            } catch (ClassCastException e) {
            }
            assertEquals(42, f.as(Function.class).apply(null));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProxyNone() {
        HostAccess access = HostAccess.newBuilder().allowAccessAnnotatedBy(Export.class).build();
        try (Context c = Context.newBuilder().allowHostAccess(access).build()) {
            c.initialize(ProxyLanguage.ID);
            Value v = c.asValue(new Impl());
            Value f = v.getMember("exported");
            try {
                v.as(MarkedInterface.class);
                fail();
            } catch (ClassCastException e) {
            }
            try {
                v.as(EmptyInterface.class);
                fail();
            } catch (ClassCastException e) {
            }
            try {
                v.as(UnmarkedInterface.class);
                fail();
            } catch (ClassCastException e) {
            }
            try {
                f.as(MarkedFunctional.class);
                fail();
            } catch (ClassCastException e) {
            }
            try {
                f.as(UnmarkedFunctional.class);
                fail();
            } catch (ClassCastException e) {
            }
            assertEquals("42", f.as(Function.class).apply("42"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProxyManualAll() {
        HostAccess access = HostAccess.newBuilder().allowAccessAnnotatedBy(Export.class).allowAllImplementations(true).build();
        try (Context c = Context.newBuilder().allowHostAccess(access).build()) {
            c.initialize(ProxyLanguage.ID);
            Value v = c.asValue(new Impl());
            Value f = v.getMember("noArg");
            assertEquals("42", v.as(MarkedInterface.class).exported("42"));
            assertEquals("42", v.as(UnmarkedInterface.class).exported("42"));
            assertNotNull(v.as(EmptyInterface.class));

            assertEquals(42, f.as(MarkedFunctional.class).f());
            assertEquals(42, f.as(UnmarkedFunctional.class).f());
            assertEquals(42, f.as(Function.class).apply(null));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProxyAll() {
        HostAccess access = HostAccess.ALL;
        try (Context c = Context.newBuilder().allowHostAccess(access).build()) {
            c.initialize(ProxyLanguage.ID);
            Value v = c.asValue(new Impl());
            Value f = v.getMember("noArg");
            assertEquals("42", v.as(MarkedInterface.class).exported("42"));
            assertEquals("42", v.as(UnmarkedInterface.class).exported("42"));
            assertNotNull(v.as(EmptyInterface.class));

            assertEquals(42, f.as(MarkedFunctional.class).f());
            assertEquals(42, f.as(UnmarkedFunctional.class).f());
            assertEquals(42, f.as(Function.class).apply(null));
        }
    }

}
