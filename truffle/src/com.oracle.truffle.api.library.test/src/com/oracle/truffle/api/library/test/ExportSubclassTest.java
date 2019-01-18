/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.test.ExpectError;

public class ExportSubclassTest extends AbstractLibraryTest {
    @GenerateLibrary
    @SuppressWarnings("unused")
    public abstract static class ExportSubclassLibrary extends Library {

        public String m0(Object receiver) {
            return "m0_default";
        }

        public abstract String m1(Object receiver);
    }

    @ExportLibrary(ExportSubclassLibrary.class)
    static class BaseClass {

        @ExportMessage
        String m1() {
            return "uncached_base_m1";
        }

        protected String m1Cached() {
            return "cached_base_m1";
        }

        @ExportMessage
        static class M1 {
            @Specialization
            static String doDefault(BaseClass receiver) {
                return receiver.m1Cached();
            }
        }

        // directly inheritec to SubClass1 and SubClass2
        @ExportMessage
        String m0() {
            return "uncached_base_m0";
        }

        @ExportMessage
        static class M0 {
            @Specialization
            static String doDefault(@SuppressWarnings("unused") BaseClass receiver) {
                return "cached_base_m0";
            }
        }
    }

    // subclass that re-exports
    @ExportLibrary(ExportSubclassLibrary.class)
    static class SubClass1 extends BaseClass {
        @Override
        @ExportMessage
        final String m1() {
            return "uncached_sub1_m1";
        }

        @ExportMessage
        static class M1 {
            @Specialization
            static String doDefault(@SuppressWarnings("unused") SubClass1 receiver) {
                return "cached_sub1_m1";
            }
        }

    }

    // subclass that does not re-export
    static class SubClass2 extends BaseClass {
        @Override
        final String m1() {
            return "uncached_sub2_m1";
        }

        @Override
        protected String m1Cached() {
            return "cached_sub2_m1";
        }
    }

    @Test
    public void testSubclass() {
        for (int i = 0; i < 4; i++) {
            ExportSubclassLibrary lib = createCachedDispatch(ExportSubclassLibrary.class, i);
            String prefix;
            prefix = i >= 1 ? "cached" : "uncached";
            assertEquals(prefix + "_base_m1", lib.m1(new BaseClass()));
            prefix = i >= 2 ? "cached" : "uncached";
            assertEquals(prefix + "_sub1_m1", lib.m1(new SubClass1()));
            prefix = i >= 3 ? "cached" : "uncached";
            assertEquals(prefix + "_sub2_m1", lib.m1(new SubClass2()));

            lib = createCachedDispatch(ExportSubclassLibrary.class, i);
            prefix = i >= 1 ? "cached" : "uncached";
            assertEquals(prefix + "_base_m0", lib.m0(new BaseClass()));
            prefix = i >= 2 ? "cached" : "uncached";
            assertEquals(prefix + "_base_m0", lib.m0(new SubClass1()));
            prefix = i >= 3 ? "cached" : "uncached";
            assertEquals(prefix + "_base_m0", lib.m0(new SubClass2()));
        }
    }

    @ExportLibrary(ExportSubclassLibrary.class)
    static class ErrorRedirectionBaseClass {
        @ExportMessage
        @ExpectError("Expected parameter count 1 for exported message, but was 0.%")
        static final String m1() {
            return null;
        }
    }

    @ExpectError("Message redirected from element com.oracle.truffle.api.library.test.ExportSubclassTest.ErrorRedirectionBaseClass.m1():\n" +
                    "Expected parameter count 1 for exported message, but was 0. Expected signature:%")
    @ExportLibrary(ExportSubclassLibrary.class)
    static class ErrorRedirectionSubClass extends ErrorRedirectionBaseClass {
    }

}
