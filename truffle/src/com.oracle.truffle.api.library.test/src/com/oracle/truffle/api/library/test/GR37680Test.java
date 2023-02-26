/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;

public class GR37680Test extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED, TestRun.DISPATCHED_CACHED, TestRun.DISPATCHED_UNCACHED);
    }

    @Test
    public void test() throws UnsupportedMessageException, UnsupportedTypeException, InvalidArrayIndexException {
        MultiplexArray value = new MultiplexArray(new MultiplexArray(new Object(), 3), 3);
        InteropLibrary lib = createLibrary(InteropLibrary.class, value);
        lib.writeArrayElement(value, 0, 42);
        assertEquals(42, value.values[0]);
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"static-method", "unused"})
    static class MultiplexArray implements TruffleObject {

        final Object delegate;
        final Object[] values;

        MultiplexArray(Object delegate, int size) {
            this.delegate = delegate;
            this.values = new Object[size];
        }

        @ExportMessage
        public boolean hasArrayElements() {
            return true;
        }

        @ExportMessage(limit = "3")
        public void writeArrayElement(long index, Object value,
                        // receiver library
                        @CachedLibrary("this") InteropLibrary thisLib,
                        // merged library
                        @CachedLibrary("this.delegate") InteropLibrary delegateLib,
                        // dynamic library (requires uncached)
                        @CachedLibrary("value") InteropLibrary valueLib) throws UnsupportedMessageException, UnsupportedTypeException, InvalidArrayIndexException {
            values[(int) index] = value;
            if (delegateLib.hasArrayElements(this.delegate)) {
                delegateLib.writeArrayElement(this.delegate, index, value);
            }
            if (valueLib.hasArrayElements(value)) {
                valueLib.writeArrayElement(value, index, value);
            }
        }

        @ExportMessage
        final Object readArrayElement(long index) {
            return null;
        }

        @ExportMessage
        final long getArraySize() {
            return values.length;
        }

        @ExportMessage
        final boolean isArrayElementReadable(long index) {
            return index < values.length;
        }

        @ExportMessage
        final boolean isArrayElementModifiable(long index) {
            return index < values.length;
        }

        @ExportMessage
        final boolean isArrayElementInsertable(long index) {
            return false;
        }
    }

}
