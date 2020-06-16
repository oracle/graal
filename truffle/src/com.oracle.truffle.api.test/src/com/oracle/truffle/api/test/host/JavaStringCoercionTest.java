/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.test.examples.TargetMappings;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class JavaStringCoercionTest extends AbstractPolyglotTest {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @Before
    public void before() {
        HostAccess access = TargetMappings.enableStringCoercions(HostAccess.newBuilder().allowPublicAccess(true)).build();
        setupEnv(Context.newBuilder().allowHostAccess(access).allowAllAccess(true).build(),
                        ProxyLanguage.setDelegate(new ProxyLanguage() {

                            @Override
                            protected boolean isObjectOfLanguage(Object object) {
                                if (object instanceof UnboxableArrayObject) {
                                    return true;
                                }
                                return super.isObjectOfLanguage(object);
                            }

                            @Override
                            protected String toString(LanguageContext c, Object value) {
                                if (value instanceof UnboxableArrayObject) {
                                    return "UnboxableArray";
                                }
                                return super.toString(c, value);
                            }
                        }));
    }

    public static class StringConsumer1 {
        public Object call(String arg) {
            return arg;
        }
    }

    public static class StringConsumer2 {
        public Object call(List<?> arg) {
            return arg.toString() + "(" + arg.size() + "):" + new ArrayList<>(arg).toString();
        }

        public Object call(String arg) {
            return arg;
        }
    }

    @Test
    public void testStringCoercionSingleMethod() throws InteropException {
        TruffleObject api = (TruffleObject) languageEnv.asGuestValue(new StringConsumer1());
        testStringCoercion(api);
    }

    @Test
    public void testStringCoercionOverloadedMethod() throws InteropException {
        TruffleObject api = (TruffleObject) languageEnv.asGuestValue(new StringConsumer2());
        testStringCoercion(api);
    }

    @Test
    public void testPreferWrappingToStringCoercion() throws InteropException {
        TruffleObject api = (TruffleObject) languageEnv.asGuestValue(new StringConsumer2());
        Object list = call(api, new UnboxableArrayObject(4));
        assertEquals("4", list);
    }

    private static void testStringCoercion(TruffleObject api) throws InteropException {
        assertEquals("ok", call(api, "ok"));
        assertEquals("42", call(api, 42));
        assertEquals("true", call(api, true));
        assertEquals("-128", call(api, Byte.MIN_VALUE));
        assertEquals("-32768", call(api, Short.MIN_VALUE));
        assertEquals("9223372036854775807", call(api, Long.MAX_VALUE));
        assertEquals("3.14", call(api, 3.14));
        assertEquals("3.14", call(api, 3.14f));
        assertEquals("NaN", call(api, Double.NaN));
        assertEquals("Infinity", call(api, Double.POSITIVE_INFINITY));
        assertEquals("-Infinity", call(api, Double.NEGATIVE_INFINITY));
        assertEquals("-0.0", call(api, -0.0));
        assertEquals("\uffff", call(api, Character.MAX_VALUE));

        assertEquals("42", call(api, new UnboxableToInt(42)));

        callUnsupported(api, new NotCoercibleObject());
    }

    private static Object call(TruffleObject obj, Object value) throws InteropException {
        try {
            return INTEROP.invokeMember(obj, "call", value);
        } catch (UnsupportedTypeException e) {
            throw new AssertionError("String coercion failed for: " + value + " (" + (value == null ? null : value.getClass().getName()) + ")", e);
        }
    }

    private static void callUnsupported(TruffleObject obj, Object value) throws InteropException {
        try {
            INTEROP.invokeMember(obj, "call", value);
            fail("Expected coercion to fail");
        } catch (UnsupportedTypeException e) {
        }
    }

    public static class IntConsumer {
        public Object call(int arg) {
            return arg;
        }
    }

    public static class IntegerConsumer {
        public Object call(Integer arg) {
            return arg;
        }
    }

    public static class PrimitiveConsumer {
        public Object call(int arg) {
            return arg;
        }

        public Object call(long arg) {
            return arg;
        }

        public Object call(double arg) {
            return arg;
        }

        public Object call(boolean arg) {
            return arg;
        }
    }

    public static class BoxedPrimitiveConsumer {
        public Object call(Integer arg) {
            return arg;
        }

        public Object call(Long arg) {
            return arg;
        }

        public Object call(Double arg) {
            return arg;
        }

        public Object call(Boolean arg) {
            return arg;
        }
    }

    public static class ObjectOrIntConsumer {
        public Object call(int arg) {
            return arg;
        }

        public Object call(Integer arg) {
            return arg;
        }

        public Object call(Object arg) {
            return arg;
        }
    }

    @Test
    public void testStringToPrimitiveSingleMethod() throws InteropException {
        for (Object consumer : new Object[]{new IntConsumer(), new IntegerConsumer()}) {
            TruffleObject api = (TruffleObject) languageEnv.asGuestValue(consumer);
            assertEquals(42, call(api, "42"));
            assertEquals(42, call(api, "+42"));
            assertEquals(-42, call(api, "-42"));

            callUnsupported(api, "42garbage");
            callUnsupported(api, "2147483648");
            callUnsupported(api, "42.0");
            callUnsupported(api, " 42");
            callUnsupported(api, "42 ");
        }
    }

    @Test
    public void testStringToPrimitiveOverloadedMethod() throws InteropException {
        for (Object consumer : new Object[]{new PrimitiveConsumer(), new BoxedPrimitiveConsumer()}) {
            TruffleObject api = (TruffleObject) languageEnv.asGuestValue(consumer);
            assertEquals(2147483648L, call(api, "2147483648"));
            assertEquals(42, call(api, "42"));
            assertEquals(42, call(api, "+42"));
            assertEquals(-42, call(api, "-42"));
            assertEquals(4.2, call(api, "4.2"));
            assertEquals(true, call(api, "true"));
            assertEquals(false, call(api, "false"));
            assertEquals(42.0, call(api, "42.0"));

            assertEquals(42, call(api, "42"));
            assertEquals(true, call(api, "true"));
            assertEquals(false, call(api, "false"));
            assertEquals(42, call(api, "42"));

            callUnsupported(api, "42garbage");
            callUnsupported(api, "0x42");
            callUnsupported(api, "True");
            callUnsupported(api, " 42");
            callUnsupported(api, "42 ");
        }
    }

    @Test
    public void testStringToPrimitiveLowPriority() throws InteropException {
        TruffleObject api = (TruffleObject) languageEnv.asGuestValue(new ObjectOrIntConsumer());
        // String to int conversion would be possible, but Object overload has higher priority.
        assertEquals(42, call(api, "42"));
    }

    static final class NotCoercibleObject implements TruffleObject {

    }

    @SuppressWarnings("unused")
    @ExportLibrary(InteropLibrary.class)
    static final class UnboxableArrayObject implements TruffleObject {
        final int value;

        UnboxableArrayObject(int size) {
            this.value = size;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        Object readArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            if (index < 0 || index >= value) {
                CompilerDirectives.transferToInterpreter();
                throw InvalidArrayIndexException.create(index);
            }
            return index;
        }

        @ExportMessage
        long getArraySize() throws UnsupportedMessageException {
            return value;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < value;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isNumber() {
            return true;
        }

        @ExportMessage
        boolean fitsInByte(@CachedLibrary("this.value") InteropLibrary delegate) {
            return delegate.fitsInByte(value);
        }

        @ExportMessage
        boolean fitsInShort(@CachedLibrary("this.value") InteropLibrary delegate) {
            return delegate.fitsInShort(value);
        }

        @ExportMessage
        boolean fitsInInt(@CachedLibrary("this.value") InteropLibrary delegate) {
            return delegate.fitsInInt(value);
        }

        @ExportMessage
        boolean fitsInLong(@CachedLibrary("this.value") InteropLibrary delegate) {
            return delegate.fitsInLong(value);
        }

        @ExportMessage
        boolean fitsInFloat(@CachedLibrary("this.value") InteropLibrary delegate) {
            return delegate.fitsInFloat(value);
        }

        @ExportMessage
        boolean fitsInDouble(@CachedLibrary("this.value") InteropLibrary delegate) {
            return delegate.fitsInDouble(value);
        }

        @ExportMessage
        byte asByte(@CachedLibrary("this.value") InteropLibrary delegate) throws UnsupportedMessageException {
            return delegate.asByte(value);
        }

        @ExportMessage
        short asShort(@CachedLibrary("this.value") InteropLibrary delegate) throws UnsupportedMessageException {
            return delegate.asShort(value);
        }

        @ExportMessage
        int asInt(@CachedLibrary("this.value") InteropLibrary delegate) throws UnsupportedMessageException {
            return delegate.asInt(value);
        }

        @ExportMessage
        long asLong(@CachedLibrary("this.value") InteropLibrary delegate) throws UnsupportedMessageException {
            return delegate.asLong(value);
        }

        @ExportMessage
        float asFloat(@CachedLibrary("this.value") InteropLibrary delegate) throws UnsupportedMessageException {
            return delegate.asFloat(value);
        }

        @ExportMessage
        double asDouble(@CachedLibrary("this.value") InteropLibrary delegate) throws UnsupportedMessageException {
            return delegate.asDouble(value);
        }

    }
}
