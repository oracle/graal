/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.basic.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.test.AbstractLibraryTest;

public class MergedLibraryTest extends AbstractLibraryTest {

    @ExportLibrary(InteropLibrary.class)
    static class MyObject extends DynamicObject implements TruffleObject {
        MyObject(Shape shape) {
            super(shape);
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        final boolean hasMembers() {
            return true;
        }

        @SuppressWarnings({"static-method", "unused"})
        @ExportMessage
        final Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @SuppressWarnings({"static-method", "unused"})
        @ExportMessage(name = "isMemberReadable")
        @ExportMessage(name = "isMemberModifiable")
        final boolean isMemberReadable(String member,
                        @CachedLibrary("this") DynamicObjectLibrary lib) {
            return lib.containsKey(this, member);
        }

        @ExportMessage
        final Object readMember(String member,
                        @CachedLibrary("this") DynamicObjectLibrary lib) {
            return lib.getOrDefault(this, member, 42);
        }

        @ExportMessage
        final boolean isMemberInsertable(String member,
                        @CachedLibrary("this") DynamicObjectLibrary lib) {
            return !isMemberReadable(member, lib);
        }

        @ExportMessage
        final void writeMember(String member, Object value,
                        @CachedLibrary("this") DynamicObjectLibrary lib) {
            lib.put(this, member, value);
        }
    }

    @Test
    public void testMembers() throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        Shape shape = Shape.newBuilder().build();
        DynamicObject obj = new MyObject(shape);
        InteropLibrary interop = adopt(InteropLibrary.getFactory().create(obj));
        assertTrue(interop.accepts(obj));
        assertTrue(interop.isMemberInsertable(obj, "key"));
        interop.writeMember(obj, "key", "value");
        assertTrue(interop.isMemberReadable(obj, "key"));
        assertEquals("value", interop.readMember(obj, "key"));
    }

}
