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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.test.ExpectError;

public class ExportLibraryTest {

    @GenerateLibrary
    @DefaultExport(PrimitiveInt.class)
    public abstract static class TestLibrary extends Library {

        public abstract Object m0(Object arg);

    }

    @ExpectError("Primitive receiver types are not supported yet.")
    @ExportLibrary(value = TestLibrary.class, receiverType = int.class)
    public static class PrimitiveInt {
        @ExportMessage
        static Object m0(int receiver) {
            return receiver;
        }
    }

    abstract static class NoLibrary extends Library {
    }

    @ExpectError("Class 'NoLibrary' is not a library annotated with @GenerateLibrary.")
    @ExportLibrary(NoLibrary.class)
    static class ExportsTestObjectError1 {

    }

    @ExportLibrary(TestLibrary.class)
    @ExpectError("The exported type must not be private. Increase visibility to resolve this.")
    private static class ExportsTestObjectError2 {
        @SuppressWarnings("static-method")
        @ExportMessage
        final Object m0() {
            return null;
        }
    }

    @ExportLibrary(TestLibrary.class)
    static class ExportsTestObjectError3 {
        @SuppressWarnings("static-method")
        @ExpectError("The exported method must not be private. Increase visibility to resolve this.")
        @ExportMessage
        private Object m0() {
            return null;
        }
    }

    @ExportLibrary(TestLibrary.class)
    static class ExportsTestObjectError4 {
        @SuppressWarnings("static-method")
        @ExportMessage
        @ExpectError("The exported method must not be private. Increase visibility to resolve this.")
        private Object m0(@SuppressWarnings("unused") @Cached("null") Object foo) {
            return null;
        }
    }

    @ExportLibrary(TestLibrary.class)
    static class ExportsTestObjectError5 {
        @ExpectError("Exported message node class must not be private.")
        @ExportMessage
        private static class M0 {
        }
    }

    @ExpectError("Using explicit receiver types is only supported for default exports or types that export DynamicDispatchLibrary.%n" +
                    "To resolve this use one of the following strategies:%n" +
                    "  - Make the receiver type implicit by applying '@ExportLibrary(TestLibrary.class)' to the receiver type 'PrimitiveInt' instead.%n" +
                    "  - Declare a default export on the 'TestLibrary' library with '@DefaultExport(TestReceiver.class)'%n" +
                    "  - Enable default exports with service providers using @GenerateLibrary(defaultExportLookupEnabled=true) on the library and specify an export priority%n" +
                    "  - Enable dynamic dispatch by annotating the receiver type with '@ExportLibrary(DynamicDispatchLibrary.class)'.")
    @ExportLibrary(value = TestLibrary.class, receiverType = PrimitiveInt.class)
    static class TestReceiver {
        @ExportMessage
        static class M0 {
        }
    }

}
