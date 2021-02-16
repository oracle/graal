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

import com.oracle.truffle.api.CompilerDirectives;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

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
public class DefaultExportExternalTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED, TestRun.DISPATCHED_CACHED, TestRun.DISPATCHED_UNCACHED);
    }

    @Test
    public void testDefaultLibrary1() {
        Object receiver = new TestReceiver1();
        assertEquals("export2_m0", createLibrary(DefaultExternal1Library.class, receiver).m0(receiver));

        receiver = new TestReceiver2();
        assertEquals("implicit_m0", createLibrary(DefaultExternal1Library.class, receiver).m0(receiver));
    }

    @GenerateLibrary(defaultExportLookupEnabled = true)
    @DefaultExport(TestReceiver1Export0.class)
    public abstract static class DefaultExternal1Library extends Library {

        public String m0(@SuppressWarnings("unused") Object receiver) {
            return "default_m0";
        }
    }

    public static final class TestReceiver1 {
    }

    @ExportLibrary(value = DefaultExternal1Library.class, receiverType = TestReceiver1.class)
    public static class TestReceiver1Export0 {
        @ExportMessage
        static String m0(@SuppressWarnings("unused") TestReceiver1 receiver) {
            return "export0_m0";
        }
    }

    @ExportLibrary(value = DefaultExternal1Library.class, receiverType = TestReceiver1.class, priority = 1)
    public static class TestReceiverExport1 {
        @ExportMessage
        static String m0(@SuppressWarnings("unused") TestReceiver1 receiver) {
            return "export1_m0";
        }
    }

    @ExportLibrary(value = DefaultExternal1Library.class, receiverType = TestReceiver1.class, priority = 2)
    public static class TestReceiverExport2 {
        @ExportMessage
        static String m0(@SuppressWarnings("unused") TestReceiver1 receiver) {
            return "export2_m0";
        }
    }

    @ExportLibrary(DefaultExternal1Library.class)
    public static final class TestReceiver2 {

        @SuppressWarnings("static-method")
        @ExportMessage
        String m0() {
            return "implicit_m0";
        }
    }

    @Test
    public void testDefaultReceiver3() {
        Object receiver = new TestReceiver3();
        assertEquals("export1_m0", createLibrary(DefaultExternal1Library.class, receiver).m0(receiver));

        receiver = new TestReceiver3Super();
        assertEquals("export2_m0", createLibrary(DefaultExternal1Library.class, receiver).m0(receiver));

        receiver = new TestReceiver3SuperSuper();
        assertEquals("export3_m0", createLibrary(DefaultExternal1Library.class, receiver).m0(receiver));
    }

    public static class TestReceiver3 extends TestReceiver3Super {
    }

    public static class TestReceiver3Super extends TestReceiver3SuperSuper {
    }

    public static class TestReceiver3SuperSuper {
    }

    @ExportLibrary(value = DefaultExternal1Library.class, receiverType = TestReceiver3.class, priority = 2)
    public static class TestReceiver3Export1 {
        @ExportMessage
        static String m0(@SuppressWarnings("unused") TestReceiver3 receiver) {
            return "export1_m0";
        }
    }

    @ExportLibrary(value = DefaultExternal1Library.class, receiverType = TestReceiver3Super.class, priority = 1)
    public static class TestReceiver3Export2 {
        @ExportMessage
        static String m0(@SuppressWarnings("unused") TestReceiver3Super receiver) {
            return "export2_m0";
        }
    }

    @ExportLibrary(value = DefaultExternal1Library.class, receiverType = TestReceiver3SuperSuper.class, priority = -1)
    public static class TestReceiver3Export3 {
        @ExportMessage
        static String m0(@SuppressWarnings("unused") TestReceiver3SuperSuper receiver) {
            return "export3_m0";
        }
    }

    @ExpectError("The priority property must be set for default exports based on service providers. See @ExportLibrary(priority=...) for details.")
    @ExportLibrary(value = DefaultExternal1Library.class, receiverType = TestReceiver1.class)
    public static class ErrorExport1 {
        @ExportMessage
        static String m0(@SuppressWarnings("unused") TestReceiver1 receiver) {
            throw new AssertionError();
        }
    }

    @ExportLibrary(value = DefaultExportErrorLibrary1.class, receiverType = Object.class)
    public static class ObjectExport {
        @ExportMessage
        static String m0(@SuppressWarnings("unused") Object receiver) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError();
        }
    }

    @GenerateLibrary(defaultExportLookupEnabled = true)
    @DefaultExport(ObjectExport.class)
    public abstract static class DefaultExportErrorLibrary1 extends Library {

        public String m0(@SuppressWarnings("unused") Object receiver) {
            return "default_m0";
        }
    }

    @ExpectError("The provided export receiver type 'TestReceiver1' is not reachable with the given priority. " +
                    "The 'DefaultExportErrorLibrary1' library specifies @DefaultExport(ObjectExport) which has receiver type 'Object' and that shadows this export. " +
                    "Increase the priority to a positive integer to resolve this.")
    @ExportLibrary(value = DefaultExportErrorLibrary1.class, receiverType = TestReceiver1.class, priority = -1)
    public static class ErrorExport2 {
        @ExportMessage
        static String m0(@SuppressWarnings("unused") TestReceiver1 receiver) {
            throw new AssertionError();
        }
    }

    @ExpectError("The set priority must be either positive or negative, but must not be 0.")
    @ExportLibrary(value = DefaultExportErrorLibrary1.class, receiverType = TestReceiver1.class, priority = 0)
    public static class ErrorExport3 {
        @ExportMessage
        static String m0(@SuppressWarnings("unused") TestReceiver1 receiver) {
            throw new AssertionError();
        }
    }

}
