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

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;
import com.oracle.truffle.api.test.ExpectError;

@RunWith(Parameterized.class)
@SuppressWarnings("unused")
public class DefaultExportTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED, TestRun.DISPATCHED_CACHED, TestRun.DISPATCHED_UNCACHED);
    }

    public static class ValidClass {

    }

    public static final class ValidSubClass extends ValidClass {

    }

    @ExportLibrary(value = DefaultLibrary.class, receiverType = ValidClass.class)
    static final class ValidImpl {
        @ExportMessage
        static int abstractMethod(ValidClass receiver) {
            return 43;
        }
    }

    @GenerateLibrary
    @DefaultExport(IntegerImpl.class)
    @DefaultExport(ValidImpl.class)
    @DefaultExport(PrimitiveArrayExport.class)
    public abstract static class DefaultLibrary extends Library {

        public int foo(@SuppressWarnings("unused") Object receiver) {
            return 42;
        }

        public abstract int abstractMethod(Object receiver);

    }

    @ExportLibrary(value = DefaultLibrary.class, receiverType = Integer.class)
    static class IntegerImpl {

        @ExportMessage
        static int foo(Integer receiver) {
            return receiver;
        }

        @ExportMessage
        static int abstractMethod(Integer receiver) {
            return receiver;
        }
    }

    @ExportLibrary(DefaultLibrary.class)
    static final class Custom {

        final int value;

        Custom(int value) {
            this.value = value;
        }

        @ExportMessage
        int foo() {
            return value;
        }

        @ExportMessage
        int abstractMethod() {
            return value;
        }

    }

    @ExportLibrary(value = DefaultLibrary.class, receiverType = int[].class)
    static class PrimitiveArrayExport {

        @ExportMessage
        static int abstractMethod(int[] receiver) {
            return receiver.length;
        }

    }

    @Test
    public void testDefaultImpl() {
        assertEquals(42, createLibrary(DefaultLibrary.class, 42.d).foo(42d));
        assertEquals(41, createLibrary(DefaultLibrary.class, 41).foo(41));
        assertEquals(40, createLibrary(DefaultLibrary.class, new Custom(40)).foo(new Custom(40)));

        try {
            createLibrary(DefaultLibrary.class, 42.d).abstractMethod(42d);
            Assert.fail();
        } catch (AbstractMethodError e) {
        }

        assertEquals(41, createLibrary(DefaultLibrary.class, 41).abstractMethod(41));
        assertEquals(40, createLibrary(DefaultLibrary.class, new Custom(40)).abstractMethod(new Custom(40)));

        assertEquals(43, createLibrary(DefaultLibrary.class, new ValidClass()).abstractMethod(new ValidClass()));
        assertEquals(43, createLibrary(DefaultLibrary.class, new ValidSubClass()).abstractMethod(new ValidSubClass()));

        int[] testArray = new int[42];
        assertEquals(42, createLibrary(DefaultLibrary.class, testArray).abstractMethod(testArray));
    }

    public static final class OtherClass {

    }

    @ExportLibrary(value = DefaultErrorLibrary2.class, receiverType = OtherClass.class)
    @ExpectError("Library specification DefaultErrorLibrary2 has errors. Please resolve them first.")
    static class UnreachableDefault {

        @ExportMessage
        static int abstractMethod(OtherClass receiver) {
            return 0;
        }

    }

    @GenerateLibrary
    @DefaultExport(UnreachableDefault.class)
    @DefaultExport(UnreachableDefault.class)
    @ExpectError("The receiver type 'OtherClass' of the export 'UnreachableDefault' is not reachable. It is shadowed by receiver type 'OtherClass' of export 'UnreachableDefault'.")
    public abstract static class DefaultErrorLibrary2 extends Library {

        public abstract int abstractMethod(Object receiver);

    }

    @ExportLibrary(value = DefaultErrorLibrary4.class, receiverType = Object.class)
    @ExpectError("Library specification DefaultErrorLibrary4 has errors. Please resolve them first.")
    static class ObjectDefault4 {

        @ExportMessage
        static int abstractMethod(Object receiver) {
            return 0;
        }

    }

    @ExportLibrary(value = DefaultErrorLibrary4.class, receiverType = String.class)
    @ExpectError("Library specification DefaultErrorLibrary4 has errors. Please resolve them first.")
    static class StringDefault4 {

        @ExportMessage
        static int abstractMethod(String receiver) {
            return 0;
        }

    }

    @GenerateLibrary
    @DefaultExport(ObjectDefault4.class)
    @DefaultExport(StringDefault4.class)
    @ExpectError("The receiver type 'String' of the export 'StringDefault4' is not reachable. It is shadowed by receiver type 'Object' of export 'ObjectDefault4'.")
    public abstract static class DefaultErrorLibrary4 extends Library {

        public int abstractMethod(Object receiver) {
            return 42;
        }

    }

    @ExportLibrary(value = DefaultErrorLibrary3.class, receiverType = OtherClass.class)
    @ExpectError("The following message(s) of library DefaultErrorLibrary3 are abstract%")
    abstract static class AbstractDefault {

    }

    @GenerateLibrary
    @DefaultExport(AbstractDefault.class)
    public abstract static class DefaultErrorLibrary3 extends Library {

        public abstract int abstractMethod(Object receiver);

    }

    @ExportLibrary(value = DefaultLibrary.class)
    @ExpectError("The following message(s) of library DefaultLibrary%")
    static final class DefaultExportError1 {

    }

    @ExpectError("Using explicit receiver types is only supported%")
    @ExportLibrary(value = DefaultLibrary.class, receiverType = Integer.class)
    static class DefaultExportError2 {

        @ExpectError("Exported methods with explicit receiver must be static.")
        @ExportMessage
        int abstractMethod(Integer receiverObject) {
            return 42;
        }
    }

    @ExpectError("Using explicit receiver types is only supported%")
    @ExportLibrary(value = DefaultLibrary.class, receiverType = Integer.class)
    static class DefaultExportError3 {

        @ExportMessage
        @ExpectError("Invalid exported type. Expected 'Integer' but was 'Object'. %")
        static int abstractMethod(Object receiverObject) {
            return 42;
        }
    }

}
