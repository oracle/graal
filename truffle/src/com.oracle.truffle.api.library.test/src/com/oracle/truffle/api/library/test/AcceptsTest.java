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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;
import com.oracle.truffle.api.test.ExpectError;

@RunWith(Parameterized.class)
@SuppressWarnings("unused")
public class AcceptsTest extends AbstractParametrizedLibraryTest {

    //

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED);
    }

    @GenerateLibrary
    abstract static class AcceptsTestLibrary extends Library {
        public String m0(Object receiver) {
            return "m0";
        }
    }

    @ExportLibrary(value = AcceptsTestLibrary.class)
    static class CustomAccepts1 {

        @ExportMessage
        static boolean accepts(CustomAccepts1 receiver) {
            return true;
        }

        @ExportMessage
        String m0() {
            return "CustomAccepts1_m0";
        }
    }

    @Test
    public void testCustomAccepts1() {
        Object value = new CustomAccepts1();
        AcceptsTestLibrary lib = createLibrary(AcceptsTestLibrary.class, value);
        assertTrue(lib.accepts(value));
        assertTrue(lib.accepts(new CustomAccepts1()));
    }

    @ExportLibrary(value = AcceptsTestLibrary.class)
    static class CustomAccepts2 {

        @ExportMessage
        static boolean accepts(CustomAccepts2 receiver, @Cached("receiver") Object cachedReceiver) {
            return receiver == cachedReceiver;
        }

        @ExportMessage
        String m0() {
            return "CustomAccepts2_m0";
        }
    }

    @Test
    public void testCustomAccepts2() {
        Object value = new CustomAccepts2();
        AcceptsTestLibrary lib = createLibrary(AcceptsTestLibrary.class, value);
        assertTrue(lib.accepts(value));
        if (run == TestRun.CACHED) {
            assertFalse(lib.accepts(new CustomAccepts2()));
        } else {
            assertTrue(lib.accepts(new CustomAccepts2()));
        }
    }

    @ExportLibrary(value = AcceptsTestLibrary.class)
    static class InvalidAccepts1 {

        @ExportMessage
        static boolean accepts(InvalidAccepts1 receiver) {
            return false;
        }

        @ExportMessage
        String m0() {
            return "InvalidAccepts1_m0";
        }
    }

    @Test
    public void testInvalidAccepts1() {
        Object value = new InvalidAccepts1();
        if (!run.isCached()) {
            // the assertion only works with uncached libraries because verifying it might
            // cause side-effects.
            assertAssertionError(() -> createLibrary(AcceptsTestLibrary.class, value));
        }
    }

    @ExportLibrary(value = AcceptsTestLibrary.class)
    static class ErrorAccepts1 {

        @ExportMessage
        // invalid receiver type
        @ExpectError("Invalid parameter type. Expected 'ErrorAccepts1' but was 'Object'. %")
        static boolean accepts(Object receiver) {
            return true;
        }

        @ExportMessage
        String m0() {
            return "InvalidAccepts1_m0";
        }
    }

}
