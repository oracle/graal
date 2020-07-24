/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.test.ExportMethodTest.ExportsTestLibrary4;
import com.oracle.truffle.api.test.AbstractLibraryTest;
import com.oracle.truffle.api.test.ExpectError;

@SuppressWarnings({"unused", "hiding"})
public class ExportNodeTest extends AbstractLibraryTest {

    @GenerateLibrary
    abstract static class ExportNodeLibrary1 extends Library {

        public void foo(Object receiver) {

        }
    }

    // valid DSL node with explicit execute
    @ExportLibrary(ExportNodeLibrary1.class)
    static class ExportNodeTestObject1 {

        int uncachedCalled = 0;
        int nodeCalled = 0;

        @ExportMessage
        void foo(@Cached(value = "0", uncached = "1") int cached) {
            if (cached == 0) {
                nodeCalled++;
            } else {
                uncachedCalled++;
            }
        }

    }

    @Test
    public void testObject1() {
        ExportNodeTestObject1 o = new ExportNodeTestObject1();
        ExportNodeLibrary1 cached = createCached(ExportNodeLibrary1.class, o);
        cached.foo(o);
        assertEquals(1, o.nodeCalled);
        assertEquals(0, o.uncachedCalled);

        cached.foo(o);
        assertEquals(2, o.nodeCalled);
        assertEquals(0, o.uncachedCalled);

        o = new ExportNodeTestObject1();
        ExportNodeLibrary1 uncached = getUncached(ExportNodeLibrary1.class, o);
        uncached.foo(o);
        assertEquals(1, o.uncachedCalled);
        assertEquals(0, o.nodeCalled);

    }

    // binding any static method should be valid
    // methods in the enclosing class should not be bound if available in the inner class
    @ExportLibrary(ExportNodeLibrary1.class)
    static class ExportNodeTestObject3 {

        int cachedExecute = 0;

        static boolean guard0() {
            return false;
        }

        static boolean guard1 = false;

        static int limit = 0;

        static int limit() {
            return 0;
        }

        static Object cache0 = null;

        static Object cache1() {
            throw new AssertionError();
        }

        @ExportMessage
        abstract static class Foo {

            static boolean guard0() {
                return true;
            }

            static boolean guard1 = true;

            static int limit = 42;

            static int limit() {
                return 42;
            }

            static Object cache0 = null;

            static Object cache1() {
                return null;
            }

            @Specialization(guards = {"guard0()", "guard1", "!receiver.equals(c0)"}, limit = " limit()")
            static void s0(ExportNodeTestObject3 receiver,
                            @Cached("cache0") Object c0, //
                            @Cached(value = "cache1()", allowUncached = true) Object c1) {
                receiver.cachedExecute++;
            }

            @Specialization(guards = {"guard0()", "guard1", "!receiver.equals(c0)"}, limit = " limit")
            static void s1(ExportNodeTestObject3 receiver,
                            @Cached("cache0") Object c0, //
                            @Cached(value = "cache1()", allowUncached = true) Object c1) {
            }
        }
    }

    @Test
    public void testObject3() {
        ExportNodeTestObject3 obj = new ExportNodeTestObject3();
        ExportNodeLibrary1 lib = createCached(ExportNodeLibrary1.class, obj);
        lib.foo(obj); // should not lead to unsupported operation.
        assertEquals(1, obj.cachedExecute);
    }

    // binding any static method in the enclosing class should be valid
    // -> FooNode has an implicit static import of the enclosing object.
    @ExportLibrary(ExportNodeLibrary1.class)
    static class ExportNodeTestObject4 {

        int cachedExecute = 0;

        static boolean guard0() {
            return true;
        }

        static boolean guard1 = true;

        static int limit = 42;

        static int limit() {
            return 42;
        }

        static Object cache0 = null;

        static Object cache1() {
            return null;
        }

        @ExportMessage
        abstract static class Foo {

            @Specialization(guards = {"guard0()", "guard1", "!receiver.equals(c0)"}, limit = " limit()")
            static void s0(ExportNodeTestObject4 receiver,
                            @Cached("cache0") Object c0, //
                            @Cached(value = "cache1()", allowUncached = true) Object c1) {
                receiver.cachedExecute++;
            }

            @Specialization(guards = {"guard0()", "guard1", "!receiver.equals(c0)"}, limit = " limit")
            static void s1(ExportNodeTestObject4 receiver,
                            @Cached("cache0") Object c0, //
                            @Cached(value = "cache1()", allowUncached = true) Object c1) {
            }
        }
    }

    @Test
    public void testObject4() {
        ExportNodeTestObject4 obj = new ExportNodeTestObject4();
        ExportNodeLibrary1 lib = createCached(ExportNodeLibrary1.class, obj);
        lib.foo(obj); // should not lead to unsupported operation.
        assertEquals(1, obj.cachedExecute);
    }

    @GenerateLibrary
    public abstract static class MultiNodeExportLibrary extends Library {

        public String m0(Object receiver, String argument) {
            return "is0";
        }

        public String m1(Object receiver, String argument) {
            return "is1";
        }

        public String m2(Object receiver, String argument) {
            return "is2";
        }

        public boolean incompatible0(Object receiver, int argument) {
            return false;
        }

        public boolean incompatible1(Object receiver, Object argument) {
            return false;
        }
    }

    @Test
    public void testMultiExport() {
        List<Object> caching = Arrays.asList(new MultiExportMethod2(), new MultiExportMethod4(), new MultiExportMethod5());
        List<Object> uncached = Arrays.asList(new MultiExportMethod1(), new MultiExportMethod3());

        MultiNodeExportLibrary lib = createCachedDispatch(MultiNodeExportLibrary.class, caching.size() + uncached.size());
        for (Object v : caching) {
            // test that caching implementations share the cache between m0, m1 and m2
            assertEquals("42", lib.m0(v, "42"));
            assertEquals("42", lib.m1(v, "43"));
            assertEquals("42", lib.m2(v, "44"));
        }
        for (Object v : uncached) {
            assertEquals("42", lib.m0(v, "42"));
            assertEquals("43", lib.m1(v, "43"));
            assertEquals("44", lib.m2(v, "44"));
        }
    }

    // forgot ExportMessage
    @ExportLibrary(MultiNodeExportLibrary.class)
    @SuppressWarnings("static-method")
    static class MultiExportMethod1 {

        @ExportMessage(name = "m0")
        @ExportMessage(name = "m1")
        @ExportMessage(name = "m2")
        final String is0(String arg) {
            return arg;
        }

    }

    @ExportLibrary(MultiNodeExportLibrary.class)
    @SuppressWarnings("static-method")
    static class MultiExportMethod2 {

        @ExportMessage(name = "m0")
        @ExportMessage(name = "m1")
        @ExportMessage(name = "m2")
        final String is0(String arg, @Exclusive @Cached("arg") String cachedArg) {
            return cachedArg;
        }

    }

    @ExportLibrary(MultiNodeExportLibrary.class)
    @SuppressWarnings("static-method")
    static class MultiExportMethod3 {

        @ExportMessage(name = "m0")
        @ExportMessage(name = "m1")
        @ExportMessage(name = "m2")
        static class M {
            @Specialization
            static String m(MultiExportMethod3 receiver, String arg) {
                return arg;
            }
        }

    }

    @ExportLibrary(MultiNodeExportLibrary.class)
    @SuppressWarnings("static-method")
    static class MultiExportMethod4 {

        @ExportMessage(name = "m0")
        @ExportMessage(name = "m1")
        @ExportMessage(name = "m2")
        static class M {
            @Specialization
            static String m(MultiExportMethod4 receiver, String arg, @Shared("group") @Cached("arg") String cachedArg) {
                return cachedArg;
            }
        }

    }

    // use inline cache to export multiple nodes. state should be cached.
    @ExportLibrary(MultiNodeExportLibrary.class)
    @SuppressWarnings("static-method")
    static class MultiExportMethod5 {

        @ExportMessage(name = "m0")
        @ExportMessage(name = "m1")
        @ExportMessage(name = "m2")
        static class M {
            @Specialization(guards = "receiver == cachedReceiver")
            static String m(MultiExportMethod5 receiver, String arg, @Exclusive @Cached("arg") String cachedArg,
                            @Exclusive @Cached("receiver") MultiExportMethod5 cachedReceiver) {
                return cachedArg;
            }
        }

    }

    @Test
    public void testExportFallback() {
        ExportFallback fallback = new ExportFallback();
        MultiNodeExportLibrary cachedLib = createCached(MultiNodeExportLibrary.class, fallback);
        assertEquals("s0", cachedLib.m0(fallback, ExportFallback.TEST_ARG));
        assertEquals("f0", cachedLib.m0(fallback, ExportFallback.TEST_ARG + "_"));
        assertEquals("s0", cachedLib.m1(fallback, ExportFallback.TEST_ARG));
        assertEquals("f0", cachedLib.m1(fallback, ExportFallback.TEST_ARG + "_"));
    }

    // use inline cache to export multiple nodes. state should be cached.
    @ExportLibrary(MultiNodeExportLibrary.class)
    @SuppressWarnings("static-method")
    static class ExportFallback {

        static final String TEST_ARG = "testArg";

        @ExportMessage
        static class M0 {
            @Specialization(guards = "TEST_ARG.equals(arg)")
            static String s0(ExportFallback receiver, String arg) {
                return "s0";
            }

            @Fallback
            static String f0(ExportFallback receiver, String arg) {
                return "f0";
            }
        }

        @ExportMessage
        static class M1 {

            @Specialization(guards = "TEST_ARG.equals(arg)")
            static String s0(ExportFallback receiver, String arg) {
                return "s0";
            }

            @Fallback
            static String f0(ExportFallback receiver, String arg) {
                return "f0";
            }
        }

    }

    @Test
    public void testWeakReference() {
        WeakReferenceNodeTest weak = new WeakReferenceNodeTest();
        MultiNodeExportLibrary cachedLib = createCached(MultiNodeExportLibrary.class, weak);
        assertEquals("s0", cachedLib.m0(weak, "arg"));
    }

    @ExportLibrary(MultiNodeExportLibrary.class)
    public static final class WeakReferenceNodeTest {

        @ExportMessage
        static class M0 {

            @Specialization(guards = "object == cachedObject", limit = "1")
            @TruffleBoundary
            static String s0(@SuppressWarnings("unused") WeakReferenceNodeTest object,
                            String arg,
                            @Cached(value = "object", weak = true) WeakReferenceNodeTest cachedObject) {
                assertNotNull(cachedObject);
                return "s0";
            }

        }

    }

    // forgot ExportMessage
    @ExportLibrary(ExportNodeLibrary1.class)
    @ExpectError("The method has the same name 'Foo' as a message in the exported library ExportNodeLibrary1. " +
                    "Did you forget to export it? " +
                    "Use @ExportMessage to export the message, @Ignore to ignore this warning, rename the method or reduce the visibility of the method to private to resolve this warning.")
    static class TestObjectError1 {

        static class Foo {

        }
    }

    // no message found
    @ExportLibrary(ExportNodeLibrary1.class)
    @ExpectError("No message 'foo2' found for library ExportNodeLibrary1. Did you mean 'foo'?")
    static class TestObjectError2 {

        @ExportMessage
        static class Foo2 {

        }

    }

    // node class must be visible
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError3 {

        @ExportMessage
        @ExpectError("Exported message node class must not be private.")
        private static class Foo {

            static Foo create() {
                return null;
            }
        }

    }

    // node class must be static
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError4 {

        @ExpectError("Inner message node class must be static.")
        @ExportMessage
        class Foo {
        }

    }

    // node constructor must be visible
    @ExportLibrary(ExportNodeLibrary1.class)
    static final class TestObjectError5 {

        @ExpectError("At least one constructor must be non-private.")
        @ExportMessage
        static final class Foo {

            private Foo() {
            }

            @Specialization
            static void foo(TestObjectError5 receiver) {
            }

        }
    }

    // not accessible execute method
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError7 {

        @ExpectError("An @ExportMessage annotated class must have at least one method with @Specialization annotation.%")
        @ExportMessage
        static class Foo {

            private void execute(TestObjectError7 receiver) {
            }
        }

    }

    // abstract class but not DSL node
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError8 {

        @ExpectError("An @ExportMessage annotated class must not declare any visible methods starting with 'execute'.%")
        @ExportMessage
        abstract static class Foo {

            abstract void execute(TestObjectError8 receiver);
        }

    }

    // invalid execute signature
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError9 {

        @ExportMessage
        @ExpectError("An @ExportMessage annotated class must not declare any visible methods starting with 'execute'.%")
        static class Foo {

            void execute(TestObjectError9 receiver, int param) {
            }
        }

    }

    // invalid receiver type
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError10 {

        @ExportMessage
        static class Foo {

            @ExpectError("Method signature (Object) does not match to the expected signature: %")
            @Specialization
            void doFoo(Object receiver) {
            }
        }

    }

    // ambiguous execute methods
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError11 {

        @ExportMessage
        static class Foo {

            @Specialization
            static void doFoo1(TestObjectError11 receiver) {
            }

            @ExpectError("Specialization is not reachable. It is shadowed by doFoo1(TestObjectError11).")
            @Specialization
            static void doFoo2(TestObjectError11 receiver) {
            }

        }

    }

    // invalid DSL node with wrong receiver type
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError12 {

        @ExportMessage
        abstract static class Foo {

            @ExpectError("Method signature (Object) does not match to the expected signature:%")
            @Specialization
            void doFoo(Object receiver) {
            }
        }

    }

    // uncached version could not be generated for simple cached fields.
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError13 {

        @ExportMessage
        abstract static class Foo {

            @Specialization
            static void doFoo(TestObjectError13 receiver,
                            @ExpectError("Failed to generate code for @GenerateUncached: The specialization uses @Cached without valid uncached expression. " +
                                            "Error parsing expression 'getUncached()': The method getUncached is undefined for the enclosing scope.. " +
                                            "To resolve this specify the uncached or allowUncached attribute in @Cached.") @Cached("nonTrivalInitializer(receiver)") TestObjectError13 cachedReceiver) {
            }

            static TestObjectError13 nonTrivalInitializer(TestObjectError13 v) {
                return v;
            }

        }

    }

    // @Cached must not bind instance variables
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError14 {

        @ExportMessage
        @ExpectError("Failed to generate code for @GenerateUncached: The node must not declare any instance variables. Found instance variable Foo.instanceVar. Remove instance variable to resolve this.")
        abstract static class Foo {

            Object instanceVar;

            @Specialization
            static void doFoo(TestObjectError14 receiver,
                            @ExpectError("@ExportMessage annotated nodes must only refer to static cache initializer methods or fields. %") //
                            @Cached("instanceVar") Object c) {
            }
        }

    }

    // @Cached must not bind instance methods
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError15 {

        @ExportMessage
        abstract static class Foo {

            Object instanceMethod() {
                return null;
            }

            @Specialization
            static void doFoo(TestObjectError15 receiver,
                            @ExpectError("@ExportMessage annotated nodes must only refer to static cache initializer methods or fields. %") //
                            @Cached(value = "instanceMethod()", allowUncached = true) Object c) {
            }
        }

    }

    // guards must not bind instance fields
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError16 {

        @ExportMessage
        @ExpectError("Failed to generate code for @GenerateUncached: The node must not declare any instance variables. Found instance variable Foo.guard. Remove instance variable to resolve this.")
        abstract static class Foo {

            boolean guard;

            @ExpectError("@ExportMessage annotated nodes must only refer to static guard methods or fields. Add a static modifier to the bound guard method or field to resolve this.")
            @Specialization(guards = "guard")
            static void doFoo(TestObjectError16 receiver) {
            }
        }

    }

    // guards must not bind instance methods
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError17 {

        @ExportMessage
        abstract static class Foo {

            boolean guard() {
                return false;
            }

            @ExpectError("@ExportMessage annotated nodes must only refer to static guard methods or fields. Add a static modifier to the bound guard method or field to resolve this.")
            @Specialization(guards = "guard()")
            static void doFoo(TestObjectError17 receiver) {
            }
        }

    }

    // limit must not bind instance variables
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError18 {

        @ExportMessage
        abstract static class Foo {

            int limit = 42;

            @ExpectError("@ExportMessage annotated nodes must only refer to static limit initializer methods or fields. %")
            @Specialization(guards = "receiver == cache", limit = "limit")
            static void doFoo(TestObjectError18 receiver, @Cached("receiver") TestObjectError18 cache) {
            }
        }

    }

    // limit must not bind instance methods
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError19 {

        @ExportMessage
        abstract static class Foo {

            int limit() {
                return 42;
            }

            @ExpectError("@ExportMessage annotated nodes must only refer to static limit initializer methods or fields. %")
            @Specialization(guards = "receiver == cache", limit = "limit()")
            static void doFoo(TestObjectError19 receiver, @Cached("receiver") TestObjectError19 cache) {
            }
        }

    }

    // binding private static methods should not be possible
    @ExportLibrary(ExportNodeLibrary1.class)
    static class TestObjectError20 {

        private static boolean guard() {
            return true;
        }

        @ExportMessage
        abstract static class Foo {

            @ExpectError("Error parsing expression 'guard()': The method guard is undefined for the enclosing scope.")
            @Specialization(guards = "guard()")
            static void doFoo(TestObjectError20 receiver) {
            }
        }
    }

    @ExportLibrary(ExportsTestLibrary4.class)
    abstract static class TestObjectError21 {

        @ExpectError("An @ExportMessage annotated class must have at least one method with @Specialization annotation. %")
        @ExportMessage
        static class IntArg {
        }
    }

    @ExportLibrary(ExportsTestLibrary4.class)
    abstract static class TestObjectError22 {

        @ExportMessage
        static class IntArg {
            @Specialization
            static int doDefault(TestObjectError22 receiver, int arg) {
                return 0;
            }
        }
    }

}
