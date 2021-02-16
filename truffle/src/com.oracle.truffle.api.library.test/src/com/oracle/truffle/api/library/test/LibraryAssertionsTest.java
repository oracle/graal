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

import org.junit.Test;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.test.AbstractLibraryTest;
import com.oracle.truffle.api.test.ExpectError;

@SuppressWarnings("unused")
public class LibraryAssertionsTest extends AbstractLibraryTest {

    @GenerateLibrary(assertions = TestLibrary1Assertions.class)
    public abstract static class TestLibrary1 extends Library {

        public int foo(Object receiver, int bar) {
            return bar;
        }

    }

    @ExportLibrary(TestLibrary1.class)
    final class TestObject1 {

        @ExportMessage
        int foo(int bar) {
            return bar;
        }

    }

    @Test
    public void testAssertion() throws Exception {
        fooCalls = 0;
        boolean assertsOn = assertionsEnabled();

        int expectedCalls = 0;
        TestLibrary1 lib = createCached(TestLibrary1.class, "");
        if (assertsOn) {
            assertTrue(lib instanceof TestLibrary1Assertions);
            expectedCalls++;
        } else {
            assertFalse(lib instanceof TestLibrary1Assertions);
        }
        assertEquals(42, lib.foo("", 42));
        assertEquals(expectedCalls, fooCalls);

        lib = getUncached(TestLibrary1.class, "");
        if (assertsOn) {
            assertTrue(lib instanceof TestLibrary1Assertions);
            expectedCalls++;
        } else {
            assertFalse(lib instanceof TestLibrary1Assertions);
        }
        assertEquals(42, lib.foo("", 42));
        assertEquals(expectedCalls, fooCalls);

        if (assertsOn) {
            expectedCalls++;
        }

        ReflectionLibrary reflection = createCached(ReflectionLibrary.class, "");
        assertEquals(42, reflection.send("", Message.resolve(TestLibrary1.class, "foo"), 42));
        assertEquals(expectedCalls, fooCalls);
    }

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean assertsOn = false;
        assert (assertsOn = true);
        return assertsOn;
    }

    static int fooCalls = 0;

    static class TestLibrary1Assertions extends TestLibrary1 {

        private final TestLibrary1 delegate;

        TestLibrary1Assertions(TestLibrary1 delegate) {
            this.delegate = delegate;
        }

        @Override
        public int foo(Object receiver, int bar) {
            fooCalls++;
            return delegate.foo(receiver, bar);
        }

        @Override
        public boolean accepts(Object receiver) {
            return delegate.accepts(receiver);
        }

    }

    @ExpectError("Assertions type must not be abstract.")
    @GenerateLibrary(assertions = ErrorAssertions1.class)
    public abstract static class AssertionsErrorLibrary1 extends Library {
        public int foo(Object receiver, int bar) {
            return bar;
        }
    }

    abstract static class ErrorAssertions1 extends AssertionsErrorLibrary1 {

    }

    @ExpectError("No constructor with single delegate parameter of type AssertionsErrorLibrary2 found.")
    @GenerateLibrary(assertions = ErrorAssertions2.class)
    public abstract static class AssertionsErrorLibrary2 extends Library {
        public int foo(Object receiver, int bar) {
            return bar;
        }
    }

    static class ErrorAssertions2 extends AssertionsErrorLibrary2 {

        @Override
        public boolean accepts(Object receiver) {
            return false;
        }

    }

    @ExpectError("Assertions type must be a subclass of the library type 'AssertionsErrorLibrary3'.")
    @GenerateLibrary(assertions = ErrorAssertions3.class)
    public abstract static class AssertionsErrorLibrary3 extends Library {
        public int foo(Object receiver, int bar) {
            return bar;
        }
    }

    static class ErrorAssertions3 extends Library {

        @Override
        public boolean accepts(Object receiver) {
            return false;
        }

    }

    @ExpectError("Assertions constructor is not visible.")
    @GenerateLibrary(assertions = ErrorAssertions4.class)
    public abstract static class AssertionsErrorLibrary4 extends Library {
        public int foo(Object receiver, int bar) {
            return bar;
        }
    }

    static final class ErrorAssertions4 extends AssertionsErrorLibrary4 {

        private ErrorAssertions4(AssertionsErrorLibrary4 delegate) {
        }

        @Override
        public boolean accepts(Object receiver) {
            return false;
        }

    }

    @ExpectError("No constructor with single delegate parameter of type AssertionsErrorLibrary5 found.")
    @GenerateLibrary(assertions = ErrorAssertions5.class)
    public abstract static class AssertionsErrorLibrary5 extends Library {
        public int foo(Object receiver, int bar) {
            return bar;
        }
    }

    static class ErrorAssertions5 extends AssertionsErrorLibrary5 {

        ErrorAssertions5(Object delegate) {
        }

        @Override
        public boolean accepts(Object receiver) {
            return false;
        }

    }

    @GenerateLibrary(assertions = ArrayAssertions.class)
    public abstract static class ArrayLibrary extends Library {

        public boolean isArray(Object receiver) {
            return false;
        }

        public int read(Object receiver, int index) {
            throw new UnsupportedOperationException();
        }
    }

    static class ArrayAssertions extends ArrayLibrary {

        @Child private ArrayLibrary delegate;

        ArrayAssertions(ArrayLibrary delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isArray(Object receiver) {
            return delegate.isArray(receiver);
        }

        @Override
        public int read(Object receiver, int index) {
            int result = super.read(receiver, index);
            assert delegate.isArray(receiver) : "if read was successful the receiver must be of type array";
            return result;
        }

        @Override
        public boolean accepts(Object receiver) {
            return delegate.accepts(receiver);
        }

    }

}
