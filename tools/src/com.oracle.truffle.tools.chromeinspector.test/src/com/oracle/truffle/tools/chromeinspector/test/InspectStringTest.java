/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.tools.chromeinspector.test;

import java.util.concurrent.Future;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Test of String values.
 */
public class InspectStringTest extends AbstractFunctionValueTest {

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    // CheckStyle: stop line length check

    @Test
    public void testAsString() throws Exception {
        Future<?> run = runWith(new TestStringObject(false));

        tester.sendMessage("{\"id\":5,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"Test String\",\"type\":\"string\",\"value\":\"Test String\"},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":5}\n"));
        tester.sendMessage("{\"id\":6,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"3\"}}");
        // Have no special properties of its own, a "next" function is provided among object properties, when available.
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":6}\n"));

        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        run.get();
        tester.finish();
    }

    @Test
    public void testAsStringWithMembers() throws Exception {
        Future<?> run = runWith(new TestStringObject(true));

        tester.sendMessage("{\"id\":5,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"Test String\",\"className\":\"Object\",\"type\":\"string\",\"value\":\"Test String\",\"objectId\":\"3\"},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":5}\n"));
        tester.sendMessage("{\"id\":6,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"3\"}}");
        // Have no special properties of its own, a "next" function is provided among object properties, when available.
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[],\"internalProperties\":[]},\"id\":6}\n"));

        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        run.get();
        tester.finish();
    }

    @Test
    public void testTruffleString() throws Exception {
        Future<?> run = runWith(TruffleString.fromJavaStringUncached("Test Truffle String", TruffleString.Encoding.UTF_8));

        tester.sendMessage("{\"id\":5,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"Test Truffle String\",\"type\":\"string\",\"value\":\"Test Truffle String\"},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":5}\n"));
        tester.sendMessage("{\"id\":6,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"3\"}}");
        // Have no special properties of its own, a "next" function is provided among object properties, when available.
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":6}\n"));

        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        run.get();
        tester.finish();
    }

    // @formatter:on
    // CheckStyle: resume line length check

    @ExportLibrary(InteropLibrary.class)
    public static final class TestStringObject implements TruffleObject {

        private final boolean hasMembers;

        TestStringObject(boolean hasMembers) {
            this.hasMembers = hasMembers;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        public boolean isString() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        public String asString() {
            return "Test String";
        }

        @ExportMessage
        @SuppressWarnings({"static-method", "unused"})
        public String toDisplayString(boolean allowSideEffects) {
            return "Display Value";
        }

        @ExportMessage
        public boolean hasMembers() {
            return hasMembers;
        }

        @ExportMessage
        @SuppressWarnings({"static-method", "unused"})
        Object getMembers(boolean includeInternal) {
            return new Members();
        }

    }

    @ExportLibrary(InteropLibrary.class)
    public static final class Members implements TruffleObject {

        @ExportMessage
        @SuppressWarnings("static-method")
        public boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            throw InvalidArrayIndexException.create(index);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        long getArraySize() {
            return 0L;
        }

        @ExportMessage
        @SuppressWarnings({"static-method", "unused"})
        boolean isArrayElementReadable(long index) {
            return false;
        }
    }
}
