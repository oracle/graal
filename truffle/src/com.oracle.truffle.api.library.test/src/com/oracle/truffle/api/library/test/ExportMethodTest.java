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
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.test.ExportNodeTest.MultiNodeExportLibrary;
import com.oracle.truffle.api.library.test.otherPackage.OtherPackageNode;
import com.oracle.truffle.api.library.test.otherPackage.OtherPackageNode.InnerDSLNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.AbstractLibraryTest;
import com.oracle.truffle.api.test.ExpectError;

@SuppressWarnings({"unused", "static-method"})
public class ExportMethodTest extends AbstractLibraryTest {

    @GenerateLibrary
    public abstract static class ExportsTestLibrary1 extends Library {
        public String foo(@SuppressWarnings("unused") Object receiver, int arg) {
            return "bar";
        }
    }

    @GenerateLibrary
    public abstract static class ExportsTestLibrary2 extends Library {
        public abstract String foo(Object receiver, double arg);
    }

    @GenerateLibrary
    public abstract static class ExportsTestLibrary3 extends Library {
        public String foo1(Object receiver) {
            return "foo1";
        }

        public String foo2(Object receiver) {
            return "foo2";
        }

        public String foo3(Object receiver) {
            return "foo3";
        }

        public String otherMessage(Object receiver) {
            return "otherMessage";
        }
    }

    interface TestInterface {
    }

    static class TestClass {
    }

    @GenerateLibrary
    public abstract static class ExportsTestLibrary4 extends Library {
        public int intArg(Object receiver, int arg) {
            return arg;
        }

        public TestInterface interfaceArg(Object receiver, TestInterface arg) {
            return arg;
        }

        public TestClass classArg(Object receiver, TestClass arg) {
            return arg;
        }

        public int multiArg(Object receiver, int intArg, TestClass clazz) {
            return intArg;
        }

        public Object varArgsObject(Object receiver, Object... args) {
            return args[0];
        }

        public int varArgsInt(Object receiver, int... args) {
            return args[0];
        }
    }

    @GenerateLibrary
    public abstract static class ExportsTestLibrary5 extends Library {
        public int intArg(CharSequence receiver, int arg) {
            return arg;
        }
    }

    @ExportLibrary(ExportsTestLibrary1.class)
    static final class ExportsTestObject1 {

        @ExportMessage
        String foo(int arg) {
            return "foo";
        }
    }

    @Test
    public void test() {
        ExportsTestObject1 o = new ExportsTestObject1();
        assertEquals("foo", getUncached(ExportsTestLibrary1.class, o).foo(o, 42));
    }

    private static class TestSubInterface implements TestInterface {

    }

    private static class TestSubClass extends TestClass {

    }

    @Test
    public void testExportsObject2() {
        ExportsTestObject2 obj = new ExportsTestObject2();
        TestSubInterface subInterface = new TestSubInterface();
        assertSame(subInterface, createCached(ExportsTestLibrary4.class, obj).interfaceArg(obj, subInterface));
        TestSubClass subClass = new TestSubClass();
        assertSame(subClass, createCached(ExportsTestLibrary4.class, obj).classArg(obj, subClass));
    }

    // allow covariant return types in exports
    @ExportLibrary(ExportsTestLibrary4.class)
    static final class ExportsTestObject2 {

        @ExportMessage
        TestSubInterface interfaceArg(TestInterface arg) {
            return (TestSubInterface) arg;
        }

        @ExportMessage
        TestSubClass classArg(TestClass arg) {
            return (TestSubClass) arg;
        }
    }

    @GenerateUncached
    abstract static class CachedTestNode extends Node {

        abstract String execute();

        @Specialization(rewriteOn = ArithmeticException.class)
        static String s0() throws ArithmeticException {
            return "cached";
        }

        @Specialization(replaces = "s0")
        static String s1() {
            return "uncached";
        }

    }

    // export varargs as non-varargs
    @ExportLibrary(ExportsTestLibrary4.class)
    static final class ExportsInnerDSLNode {

        @ExportMessage
        public Object varArgsObject(Object[] args, @Cached InnerDSLNode node) {
            return args[0];
        }

    }

    // export varargs as non-varargs
    @ExportLibrary(ExportsTestLibrary4.class)
    static final class ExportsTestVarArgs {

        @ExportMessage
        public Object varArgsObject(Object[] args, @Cached OtherPackageNode.InnerDSLNode node) {
            return args[0];
        }

    }

    @Test
    public void testExportsObject3() {
        ExportsTestVarArgs obj = new ExportsTestVarArgs();
        assertEquals(42, createCached(ExportsTestLibrary4.class, obj).varArgsObject(obj, 42));
        assertEquals(42, getUncached(ExportsTestLibrary4.class, obj).varArgsObject(obj, 42));
    }

    // export method as static method.
    @ExportLibrary(ExportsTestLibrary1.class)
    static final class ExportsTestStaticMethod {

        @ExportMessage
        public static String foo(ExportsTestStaticMethod receiver, int arg) {
            return "foo";
        }

    }

    @Test
    public void testExportsStaticMethod() {
        ExportsTestStaticMethod obj = new ExportsTestStaticMethod();
        assertEquals("foo", createCached(ExportsTestLibrary1.class, obj).foo(obj, 42));
        assertEquals("foo", getUncached(ExportsTestLibrary1.class, obj).foo(obj, 42));
    }

    // test implicit receiver + CachedNode
    @ExportLibrary(ExportsTestLibrary1.class)
    static final class ExportsTestInstanceWithCachedNode {

        @ExportMessage
        public String foo(int arg, @Cached CachedTestNode node) {
            return node.execute();
        }
    }

    @Test
    public void testExportsInstanceWithCachedNode() {
        ExportsTestInstanceWithCachedNode obj = new ExportsTestInstanceWithCachedNode();
        assertEquals("cached", adoptNode(createCached(ExportsTestLibrary1.class, obj)).get().foo(obj, 42));
        assertEquals("uncached", getUncached(ExportsTestLibrary1.class, obj).foo(obj, 42));
    }

    // test static receiver + cached node
    @ExportLibrary(ExportsTestLibrary1.class)
    static final class ExportsTestStaticWithCachedNode {

        @ExportMessage
        public static String foo(ExportsTestStaticWithCachedNode receiver, int arg, @Cached CachedTestNode node) {
            return node.execute();
        }
    }

    @Test
    public void testExportsStaticWithCachedNode() {
        ExportsTestStaticWithCachedNode obj = new ExportsTestStaticWithCachedNode();
        assertEquals("cached", createCached(ExportsTestLibrary1.class, obj).foo(obj, 42));
        assertEquals("uncached", getUncached(ExportsTestLibrary1.class, obj).foo(obj, 42));
    }

    @ExportLibrary(ExportsTestLibrary1.class)
    static final class ExportsTestInstanceWithLibrary {

        static final Object DELEGATE = new ExportsTestStaticWithCachedNode();

        @ExportMessage
        String foo(int arg, @CachedLibrary("DELEGATE") ExportsTestLibrary1 lib) {
            return lib.foo(DELEGATE, arg);
        }
    }

    @Test
    public void testExportsInstanceWithLibrary() {
        ExportsTestInstanceWithLibrary obj = new ExportsTestInstanceWithLibrary();
        assertEquals("cached", createCached(ExportsTestLibrary1.class, obj).foo(obj, 42));
        assertEquals("uncached", getUncached(ExportsTestLibrary1.class, obj).foo(obj, 42));
    }

    @ExportLibrary(ExportsTestLibrary1.class)
    static final class ExportsTestStaticWithLibrary {

        static final Object DELEGATE = new ExportsTestStaticWithCachedNode();

        @ExportMessage
        public static String foo(ExportsTestStaticWithLibrary receiver, int arg, @CachedLibrary("DELEGATE") ExportsTestLibrary1 lib) {
            return lib.foo(DELEGATE, 42);
        }
    }

    @Test
    public void testExportsStaticWithLibrary() {
        ExportsTestStaticWithLibrary obj = new ExportsTestStaticWithLibrary();
        assertEquals("cached", createCached(ExportsTestLibrary1.class, obj).foo(obj, 42));
        assertEquals("uncached", getUncached(ExportsTestLibrary1.class, obj).foo(obj, 42));
    }

    @Test
    public void testWeakReference() {
        WeakReferenceMethodTest weak = new WeakReferenceMethodTest();
        MultiNodeExportLibrary cachedLib = createCached(MultiNodeExportLibrary.class, weak);
        assertEquals("s0", cachedLib.m0(weak, "arg"));
    }

    @ExportLibrary(MultiNodeExportLibrary.class)
    public static final class WeakReferenceMethodTest {

        @ExportMessage
        @TruffleBoundary
        String m0(String arg,
                        @Cached(value = "this", weak = true) WeakReferenceMethodTest cachedObject) {
            assertNotNull(cachedObject);
            return "s0";
        }

    }

    abstract static class NoLibrary extends Library {
    }

    @ExportLibrary(ExportsTestLibrary1.class)
    static final class ExportsWithCachedBindsToThis {

        static final Object DELEGATE = new ExportsTestStaticWithCachedNode();

        @ExportMessage
        public String foo(int arg,
                        @Cached("this") ExportsWithCachedBindsToThis lib) {
            return "foo";
        }
    }

    @ExportLibrary(ExportsTestLibrary1.class)
    static final class ExportsWithCachedBindsToReceiverMethod {

        static final Object DELEGATE = new ExportsTestStaticWithCachedNode();

        @ExportMessage
        public String foo(int arg,
                        @Cached(value = "this.boundMethod()", allowUncached = true) String lib) {
            return "foo";
        }

        String boundMethod() {
            return "boundMethod";
        }
    }

    @ExportLibrary(ExportsTestLibrary1.class)
    @ExpectError("Class 'com.oracle.truffle.api.library.test.ExportMethodTest.NoLibrary' is not a library annotated with @GenerateLibrary.")
    static final class ExportsTestObjectError2 {

        @ExportMessage(library = NoLibrary.class)
        String foo() {
            return "foo";
        }

    }

    @ExportLibrary(ExportsTestLibrary1.class)
    @ExpectError("No message 'invalidName' found for library ExportsTestLibrary1.")
    static class ExportsTestObjectError3 {

        @ExportMessage
        String invalidName() {
            return "foo";
        }

    }

    @ExportLibrary(ExportsTestLibrary1.class)
    @ExpectError("No message 'invalidName' found for library ExportsTestLibrary1.")
    static class ExportsTestObjectError4 {

        @ExportMessage(name = "invalidName")
        String foo() {
            return "foo";
        }
    }

    @ExpectError("The following message(s) of library ExportsTestLibrary2 are abstract and must be exported %")
    @ExportLibrary(ExportsTestLibrary2.class)
    static class ExportsTestObjectError5 {

    }

    @ExportLibrary(ExportsTestLibrary1.class)
    @ExportLibrary(ExportsTestLibrary2.class)
    @ExpectError({"The following message(s) of library ExportsTestLibrary2 are abstract and must be exported using:%",
                    "The message name 'foo' is ambiguous for libraries ExportsTestLibrary1 and ExportsTestLibrary2. Disambiguate the library by specifying the library explicitely using " +
                                    "@ExportMessage(library=Library.class)."})
    static class ExportsTestObjectError6 {

        @ExportMessage
        String foo() {
            return "foo";
        }
    }

    @ExpectError("No libraries exported. Use @ExportLibrary(MyLibrary.class) on the enclosing type to export libraries.")
    static class ExportsTestObjectError7 {

        @ExportMessage
        String foo() {
            return "foo";
        }
    }

    @ExportLibrary(ExportsTestLibrary3.class)
    @ExpectError("No message 'foo' found for library ExportsTestLibrary3. Did you mean 'foo1', 'foo2', 'foo3'?")
    static class ExportsTestObjectError8 {
        @ExportMessage
        String foo() {
            return "foo";
        }
    }

    @ExportLibrary(ExportsTestLibrary3.class)
    @ExpectError("The method has the same name 'foo1' as a message in the exported library ExportsTestLibrary3. Did you forget to export it? Use @ExportMessage to export the message, @Ignore to " +
                    "ignore this warning, rename the method or reduce the visibility of the method to private to resolve this warning.")
    static class ExportsTestObjectError9 {

        String foo1() {
            return "foo1";
        }

    }

    @ExportLibrary(ExportsTestLibrary3.class)
    static class ExportsTestObjectError10 {
        @ExportMessage.Ignore
        String foo1() {
            return "foo1";
        }
    }

    @ExportLibrary(ExportsTestLibrary4.class)
    static class ExportsTestObjectError11 {

        // wrong primitive type
        @ExportMessage
        @ExpectError("Invalid parameter type. Expected 'int' but was 'double'.%")
        public int intArg(double arg) {
            return 42;
        }

        // wront class type
        @ExportMessage
        @ExpectError("Invalid parameter type. Expected 'TestClass' but was 'Object'.%")
        public TestClass classArg(Object arg) {
            return (TestClass) arg;
        }

        // wrong return type
        @ExportMessage
        @ExpectError("Invalid exported return type.%")
        public Object interfaceArg(TestInterface arg) {
            return arg;
        }

        // wrong multiple types
        @ExportMessage
        @ExpectError({"Invalid parameter type. Expected 'int' but was 'byte'.%"})
        public int multiArg(byte intArg, String arg) {
            return intArg;
        }

        // missing arg
        @ExpectError("Expected parameter count 1 for exported message, but was 0.%")
        @ExportMessage
        public int varArgsInt() {
            return 42;
        }

    }

    @ExportLibrary(ExportsTestLibrary5.class)
    @ExpectError("Type ExportsTestObjectError12 is not compatible with the receiver type 'CharSequence' of exported library 'ExportsTestLibrary5'. Inhert from type 'CharSequence' to resolve this.")
    static class ExportsTestObjectError12 {
        @ExportMessage
        public int intArg(int arg) {
            return 42;
        }
    }

    @ExportLibrary(ExportsTestLibrary1.class)
    static class ExportTestObjectError13 {
        @ExportMessage
        public String foo(int arg,
                        @ExpectError("Error parsing expression 'create()': The method create is undefined for the enclosing scope.")//
                        @Cached Node node) {
            return "42";
        }
    }

    @ExportLibrary(ExportsTestLibrary1.class)
    static class ExportTestObjectError14 {
        @ExportMessage
        public String foo(int arg,
                        @ExpectError("Invalid library type Node. Library is not a subclass of Library.")//
                        @CachedLibrary("this") Node node) {
            return "42";
        }
    }

    @ExportLibrary(ExportsTestLibrary4.class)
    static class ExportsTestObjectCorrect11 {
        @ExportMessage
        public int intArg(int arg) {
            return 42;
        }

        @ExportMessage
        public TestClass classArg(TestClass arg) {
            return arg;
        }

        @ExportMessage
        public TestInterface interfaceArg(TestInterface arg) {
            return arg;
        }

        @ExportMessage
        public int multiArg(int intArg, TestClass arg) {
            return intArg;
        }

        @ExportMessage
        public int varArgsInt(int... args) {
            return 42;
        }

        @ExportMessage
        public Object varArgsObject(Object... args) {
            return 43;
        }
    }

    @ExpectError("No libraries exported. Use @ExportLibrary(MyLibrary.class) on the enclosing type to export libraries.")
    static class ExportsTestObjectError15 {
        @ExportMessage
        public int intArg(int arg) {
            return 42;
        }
    }

    abstract static class DSLNode extends Node {

        abstract Object execute(Object arg);

        @Specialization
        String s0(int arg) {
            return "s0";
        }
    }

    @ExportLibrary(ExportsTestLibrary4.class)
    static class ExportsTestObjectError16 {
        @ExportMessage
        public int intArg(int arg,
                        @ExpectError("Failed to generate code for @GenerateUncached: %") //
                        @Cached DSLNode node) {
            return 42;
        }
    }

    @ExpectError("@ExportLibrary is not supported for interfaces at the moment.")
    @ExportLibrary(ExportsTestLibrary4.class)
    interface ExportsTestObjectError18 {

        @ExportMessage
        default int intArg(int arg) {
            return 42;
        }

    }

    @ExportLibrary(ExportsTestLibrary4.class)
    abstract static class ExportsTestObjectError19 {

        @ExportMessage
        public int intArg(int arg) {
            return 42;
        }

    }

}
