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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;

public class CachedLibrarySharingTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED, TestRun.DISPATCHED_CACHED, TestRun.DISPATCHED_UNCACHED);
    }

    @GenerateLibrary
    @SuppressWarnings("unused")
    public abstract static class LibrarySharing1 extends Library {

        public String m0(Object receiver) {
            return "m0_1";
        }

        public String m1(Object receiver) {
            return "m1_1";
        }

        public String m2(Object receiver) {
            return "m2_1";
        }
    }

    @GenerateLibrary
    @SuppressWarnings("unused")
    public abstract static class LibrarySharing2 extends Library {

        public String m0(Object receiver) {
            return "m0_2";
        }

        public String m1(Object receiver) {
            return "m1_2";
        }

        public String m2(Object receiver) {
            return "m2_2";
        }
    }

    @ExportLibrary(LibrarySharing1.class)
    public static class LibrarySharingThis1 {
        @ExportMessage
        String m0(@CachedLibrary("this") LibrarySharing1 lib) {
            return lib.m2(this);
        }

        @ExportMessage
        String m1(@CachedLibrary("this") LibrarySharing1 lib) {
            return lib.m2(this);
        }
    }

    @Test
    public void testSharingThis() {
        LibrarySharingThis1 obj = new LibrarySharingThis1();
        LibrarySharing1 lib = createLibrary(LibrarySharing1.class, obj);
        assertEquals("m2_1", lib.m0(obj));
        assertEquals("m2_1", lib.m1(obj));
        assertEquals("m2_1", lib.m2(obj));

        if (!run.isDispatched()) {
            assertFalse(lib.accepts(new LibraryObject2(new LibraryObject1())));
            assertFalse(lib.accepts(new LibraryObject1()));
            assertTrue(lib.accepts(new LibrarySharingThis1()));
        }
    }

    @ExportLibrary(LibrarySharing1.class)
    public static final class LibraryObject1 {

        @ExportMessage
        String m0(@CachedLibrary("this") LibrarySharing2 lib) {
            return lib.m0(this);
        }

        @ExportMessage
        static class M1 {
            @Specialization
            static String m1(LibraryObject1 receiver, @CachedLibrary("receiver") LibrarySharing2 lib) {
                return lib.m1(receiver);
            }
        }

    }

    @Test
    public void testLibraryObject1() {
        LibraryObject1 obj = new LibraryObject1();
        LibrarySharing1 lib = createLibrary(LibrarySharing1.class, obj);
        assertEquals("m0_2", lib.m0(obj));
        assertEquals("m1_2", lib.m1(obj));
        assertEquals("m2_1", lib.m2(obj));

        if (!run.isDispatched()) {
            assertFalse(lib.accepts(new LibraryObject2(new LibraryObject1())));
            assertFalse(lib.accepts(new LibrarySharingThis1()));
            assertTrue(lib.accepts(new LibraryObject1()));
        }

    }

    @ExportLibrary(LibrarySharing1.class)
    public static final class LibraryObject2 {

        final Object delegate;

        LibraryObject2(Object delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        String m0(@CachedLibrary("this.delegate") LibrarySharing2 lib) {
            return lib.m0(delegate);
        }

        @ExportMessage
        static class M1 {
            @Specialization
            static String m1(LibraryObject2 receiver, @CachedLibrary("receiver.delegate") LibrarySharing2 lib) {
                return lib.m1(receiver.delegate);
            }
        }

    }

    @Test
    public void testLibraryObject2() {
        LibraryObject2 obj = new LibraryObject2(new LibraryObject1());
        LibrarySharing1 lib = createLibrary(LibrarySharing1.class, obj);
        assertEquals("m0_2", lib.m0(obj));
        assertEquals("m1_2", lib.m1(obj));
        assertEquals("m2_1", lib.m2(obj));

        if (!run.isDispatched()) {
            assertFalse(lib.accepts(new LibraryObject1()));
            assertFalse(lib.accepts(new LibrarySharingThis1()));
            assertAssertionError(() -> lib.m0(new LibraryObject1()));
            assertAssertionError(() -> lib.m0(new LibrarySharingThis1()));
            assertAssertionError(() -> lib.m1(new LibraryObject1()));
            assertAssertionError(() -> lib.m1(new LibrarySharingThis1()));

            if (run.isCached()) {
                assertFalse(lib.accepts(new LibraryObject2(new Object())));
            } else {
                assertTrue(lib.accepts(new LibraryObject2(new Object())));
            }
            assertTrue(lib.accepts(new LibraryObject2(new LibraryObject1())));
        }

    }

    @ExportLibrary(LibrarySharing1.class)
    public static final class LibraryObject3 {

        final Object d0;
        final Object d1;
        final Object d2;

        LibraryObject3(Object d0, Object d1, Object d2) {
            this.d0 = d0;
            this.d1 = d1;
            this.d2 = d2;
        }

        @ExportMessage
        String m0(@CachedLibrary("this.d0") LibrarySharing2 lib0,
                        @CachedLibrary("this.d1") LibrarySharing2 lib1,
                        @CachedLibrary("this.d2") LibrarySharing2 lib2) {
            String r0 = lib0.m0(d0);
            String r1 = lib1.m0(d1);
            String r2 = lib2.m0(d2);
            assertEquals(r0, r1);
            assertEquals(r1, r2);
            return r2;
        }
    }

    @Test
    public void testLibraryObject3() {
        LibraryObject3 obj = new LibraryObject3(new LibraryObject1(),
                        new LibraryObject1(), new LibraryObject1());
        LibrarySharing1 lib = createLibrary(LibrarySharing1.class, obj);
        assertEquals("m0_2", lib.m0(obj));
    }
}
