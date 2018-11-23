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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.DynamicDispatch;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.ExpectError;

@RunWith(Parameterized.class)
@SuppressWarnings("unused")
public class AcceptsTest extends AbstractLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED);
    }

    static class NonDispatch {

    }

    static class Dispatch implements DynamicDispatch {
        private final Class<?> dispatch;

        Dispatch(Class<?> dispatch) {
            this.dispatch = dispatch;
        }

        public Class<?> dispatch() {
            return dispatch;
        }
    }

    @GenerateLibrary
    abstract static class DispatchLibrary extends Library {

        public String m0(Object receiver) {
            return "m0";
        }

    }

    @ExpectError("The annotated type 'DispatchError1' is not specified using @DefaultExport in the library 'DispatchLibrary'. Using explicit receiver classes is only supported for default exports or receiver types that implement DynamicDispatch.")
    @ExportLibrary(value = DispatchLibrary.class, receiverClass = NonDispatch.class)
    static class DispatchError1 {
    }

    @ExpectError("@ExportLibrary cannot be used on types that implement DynamicDispatch. They are mutually exclusive.")
    @ExportLibrary(DispatchLibrary.class)
    static class DispatchError2 implements DynamicDispatch {

        public Class<?> dispatch() {
            return null;
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class DispatchValid1 {

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            assertSame(receiver.dispatch, DispatchValid1.class);
            return "d";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class DispatchValid2 {

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            assertSame(receiver.dispatch, DispatchValid2.class);
            return "d";
        }
    }

    @Test
    public void testNullDispatch() {
        Dispatch d = new Dispatch(null);
        assertAssertionError(() -> createLibrary(DispatchLibrary.class, d));
    }

    @Test
    public void testValidAccepts() {
        Dispatch d1 = new Dispatch(DispatchValid1.class);
        Dispatch d2 = new Dispatch(DispatchValid2.class);
        Dispatch d3 = new Dispatch(CustomAccepts1.class);
        Dispatch d4 = new Dispatch(CustomAccepts2.class);
        Dispatch d5 = new Dispatch(CustomAccepts3.class);

        List<Object> ds = Arrays.asList(d1, d2, d3, d4, d5);

        for (Object d : ds) {
            DispatchLibrary l = createLibrary(DispatchLibrary.class, d);
            assertTrue(l.accepts(d));

            for (Object otherDispatch : ds) {
                if (otherDispatch != d) {
                    assertFalse(l.accepts(otherDispatch));
                    assertAssertionError(() -> l.m0(d));
                }
            }
            assertEquals("d", l.m0(d));
            assertAssertionError(() -> l.m0(""));
        }
    }

    @Test
    public void testInvalidAccepts() {
        Dispatch d1 = new Dispatch(InvalidAccepts1.class);
        Dispatch d2 = new Dispatch(InvalidAccepts2.class);
        Dispatch d3 = new Dispatch(InvalidAccepts3.class);
        InvalidAccepts4 d4 = new InvalidAccepts4();
        Dispatch d5 = new Dispatch(InvalidAccepts5.class);

        List<Object> values = Arrays.asList(d1, d2, d3, d4, d5);
        for (Object d : values) {
            DispatchLibrary l = createLibrary(DispatchLibrary.class, d);
            assertAssertionError(() -> l.accepts(d));
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class CustomAccepts1 {

        @ExportMessage
        static boolean accepts(Object receiver) {
            return receiver instanceof Dispatch && ((Dispatch) receiver).dispatch == CustomAccepts1.class;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            return "d";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class CustomAccepts2 {

        @ExportMessage
        static boolean accepts(Dispatch receiver) {
            return receiver.dispatch == CustomAccepts2.class;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            return "d";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class CustomAccepts3 {

        @ExportMessage
        static boolean accepts(Object receiver, @Cached("receiver.getClass()") Class<?> receiverClass) {
            return receiver instanceof Dispatch && ((Dispatch) receiver).dispatch == CustomAccepts3.class;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            return "d";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class InvalidAccepts1 {

        @ExportMessage
        static boolean accepts(Object receiver) {
            return receiver instanceof Dispatch;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            assertSame(receiver.dispatch, DispatchValid1.class);
            return "d1";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class InvalidAccepts2 {

        @ExportMessage
        static boolean accepts(Object receiver) {
            return true;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            assertSame(receiver.dispatch, DispatchValid1.class);
            return "d1";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class InvalidAccepts3 {

        @ExportMessage
        static boolean accepts(Dispatch receiver) {
            return true;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            assertSame(receiver.dispatch, DispatchValid1.class);
            return "d1";
        }
    }

    @ExportLibrary(DispatchLibrary.class)
    static class InvalidAccepts4 {

        @ExportMessage
        static boolean accepts(Object receiver) {
            return true;
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") InvalidAccepts4 receiver) {
            return "d1";
        }
    }

    @ExportLibrary(value = DispatchLibrary.class, receiverClass = Dispatch.class)
    static class InvalidAccepts5 {

        @ExportMessage
        static class AcceptsNode extends Node {
            @Specialization
            static boolean accepts(Dispatch receiver) {
                return true;
            }
        }

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            return "d1";
        }
    }

    private static void assertAssertionError(Runnable r) {
        try {
            r.run();
        } catch (AssertionError e) {
            return;
        }
        fail();
    }

    @GenerateLibrary
    @DefaultExport(DefaultDispatchReceiver1.class)
    abstract static class DefaultDispatchLibrary1 extends Library {

        public String m0(Object receiver) {
            return "default";
        }

    }

    interface NonDispatchInterface {

    }

    @ExportLibrary(value = DefaultDispatchLibrary1.class, receiverClass = NonDispatchInterface.class)
    abstract static class DefaultDispatchReceiver1 extends Library {

        @ExportMessage
        static String m0(NonDispatchInterface receiver) {
            return "nonDispatchInterface";
        }
    }

    @ExportLibrary(DefaultDispatchLibrary1.class)
    static class SimpleDispatchReceiver implements NonDispatchInterface {

        @ExportMessage
        final String m0() {
            return "simpleDispatch";
        }
    }

    @ExportLibrary(value = DefaultDispatchLibrary1.class, receiverClass = Dispatch.class)
    static class DefaultDispatchReceiver {

        @ExportMessage
        static String m0(@SuppressWarnings("unused") Dispatch receiver) {
            return "customDispatch";
        }
    }

    @Test
    public void testDefaultDispatchObject() {
        Object defaultValue = new Object();
        DefaultDispatchLibrary1 library = createLibrary(DefaultDispatchLibrary1.class, defaultValue);
        assertEquals("default", library.m0(defaultValue));
        assertAssertionError(() -> library.m0(new SimpleDispatchReceiver()));
    }

    @Test
    public void testDefaultDispatchNonDispatchInterface() {
        Object value = new NonDispatchInterface() {
        };
        DefaultDispatchLibrary1 library = createLibrary(DefaultDispatchLibrary1.class, value);
        assertEquals("nonDispatchInterface", library.m0(value));
        assertAssertionError(() -> library.m0(new SimpleDispatchReceiver()));
    }

}
