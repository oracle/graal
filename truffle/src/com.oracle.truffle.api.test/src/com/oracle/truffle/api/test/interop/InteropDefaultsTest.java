/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

@SuppressWarnings("deprecation")
public class InteropDefaultsTest extends InteropLibraryBaseTest {

    public static class TestInterop1 {
    }

    @Test
    public void testBooleanDefault() throws InteropException {
        assertBoolean(true, true);
        assertBoolean(false, false);
    }

    private void assertBoolean(Object v, boolean expected) throws UnsupportedMessageException, InteropException {
        InteropLibrary library = createLibrary(InteropLibrary.class, v);
        assertTrue(library.isBoolean(v));
        assertEquals(expected, library.asBoolean(v));
        assertEquals(v.toString(), library.toDisplayString(v));

        // assert boolean
        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoBuffer(v);
        assertNoString(v);
        assertNoNumber(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);
        assertNoMetaObject(v);
        assertHasNoMetaObject(v);
        assertNoDate(v);
        assertNoTime(v);
        assertNoTimeZone(v);
        assertNoDuration(v);
        assertNoSourceLocation(v);
        assertNoLanguage(v);
        assertNoIdentity(v);
    }

    @Test
    public void testByteDefault() throws InteropException {
        assertNumber(Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber((byte) 0, true, true, true, true, true, true);
        assertNumber(Byte.MAX_VALUE, true, true, true, true, true, true);
    }

    @Test
    public void testShortDefault() throws InteropException {
        assertNumber(Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((short) (Byte.MIN_VALUE - 1), false, true, true, true, true, true);
        assertNumber((short) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber((short) 0, true, true, true, true, true, true);
        assertNumber((short) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((short) (Byte.MAX_VALUE + 1), false, true, true, true, true, true);
        assertNumber(Short.MAX_VALUE, false, true, true, true, true, true);
    }

    @Test
    public void testIntDefault() throws InteropException {
        assertNumber(Integer.MIN_VALUE, false, false, true, true, true, true);
        assertNumber(Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((int) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber(Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((int) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(0, true, true, true, true, true, true);
        assertNumber((int) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber(Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((int) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber(Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber(1 << 24, false, false, true, true, true, true);
        assertNumber((1 << 24) + 1, false, false, true, true, false, true);
        assertNumber(1 << 25, false, false, true, true, true, true);
        assertNumber(Integer.MAX_VALUE, false, false, true, true, true, true);
    }

    @Test
    public void testLongDefault() throws InteropException {
        assertNumber(Long.MIN_VALUE, false, false, false, true, true, true);
        assertNumber((long) Integer.MIN_VALUE - 1, false, false, false, true, false, true);
        assertNumber((long) Integer.MIN_VALUE, false, false, true, true, true, true);
        assertNumber((long) Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((long) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((long) Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((long) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(0L, true, true, true, true, true, true);
        assertNumber((long) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((long) Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((long) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber((long) Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber((long) Integer.MAX_VALUE, false, false, true, true, false, true);
        assertNumber(1L << 24, false, false, true, true, true, true);
        assertNumber((1L << 24) + 1, false, false, true, true, false, true);
        assertNumber(1L << 25, false, false, true, true, true, true);
        assertNumber((1L << 53) + 1, false, false, false, true, false, false);
        assertNumber(1L << 54, false, false, false, true, true, true);
        assertNumber(Long.MAX_VALUE, false, false, false, true, true, true);
    }

    @Test
    public void testFloatDefault() throws InteropException {
        assertNumber(Float.NEGATIVE_INFINITY, false, false, false, false, true, true);
        assertNumber((float) Long.MIN_VALUE, false, false, false, false, true, true);
        assertNumber((float) Integer.MIN_VALUE - 1, false, false, false, false, true, true);
        assertNumber((float) Integer.MIN_VALUE, false, false, false, false, true, true);
        assertNumber((float) Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((float) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((float) Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((float) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(-0.0f, false, false, false, false, true, true);
        assertNumber(0.0f, true, true, true, true, true, true);
        assertNumber((float) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((float) Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((float) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber((float) Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber((float) Integer.MAX_VALUE, false, false, false, false, true, true);
        assertNumber((float) Long.MAX_VALUE, false, false, false, false, true, true);
        assertNumber(Float.POSITIVE_INFINITY, false, false, false, false, true, true);
        assertNumber(Float.NaN, false, false, false, false, true, true);
        assertNumber(Float.MIN_VALUE, false, false, false, false, true, true);
        assertNumber(Float.MIN_NORMAL, false, false, false, false, true, true);
        assertNumber(Float.MAX_VALUE, false, false, false, false, true, true);
    }

    @Test
    public void testDoubleDefault() throws InteropException {
        assertNumber(Double.NEGATIVE_INFINITY, false, false, false, false, true, true);
        assertNumber((double) Long.MIN_VALUE, false, false, false, false, true, true);
        assertNumber((double) Integer.MIN_VALUE - 1, false, false, false, true, false, true);
        assertNumber((double) Integer.MIN_VALUE, false, false, true, true, true, true);
        assertNumber((double) Short.MIN_VALUE - 1, false, false, true, true, true, true);
        assertNumber((double) Short.MIN_VALUE, false, true, true, true, true, true);
        assertNumber((double) Byte.MIN_VALUE - 1, false, true, true, true, true, true);
        assertNumber((double) Byte.MIN_VALUE, true, true, true, true, true, true);
        assertNumber(-0.0d, false, false, false, false, true, true);
        assertNumber(0.0d, true, true, true, true, true, true);
        assertNumber((double) Byte.MAX_VALUE, true, true, true, true, true, true);
        assertNumber((double) Byte.MAX_VALUE + 1, false, true, true, true, true, true);
        assertNumber((double) Short.MAX_VALUE, false, true, true, true, true, true);
        assertNumber((double) Short.MAX_VALUE + 1, false, false, true, true, true, true);
        assertNumber((double) Integer.MAX_VALUE, false, false, true, true, false, true);
        assertNumber((double) Long.MAX_VALUE, false, false, false, false, true, true);
        assertNumber(Double.POSITIVE_INFINITY, false, false, false, false, true, true);
        assertNumber(Double.NaN, false, false, false, false, true, true);
        assertNumber(Double.MIN_VALUE, false, false, false, false, false, true);
        assertNumber(Double.MIN_NORMAL, false, false, false, false, false, true);
        assertNumber(Double.MAX_VALUE, false, false, false, false, false, true);
    }

    @Test
    public void testStringDefaults() throws InteropException {
        assertString("foo", "foo");
        assertString("bar", "bar");
    }

    @Test
    public void testCharacterDefaults() throws InteropException {
        assertString('a', "a");
        assertString('b', "b");
    }

    private void assertString(Object v, String expectedString) throws UnsupportedMessageException, InteropException {
        InteropLibrary library = createLibrary(InteropLibrary.class, v);
        assertTrue(library.isString(v));
        assertEquals(expectedString, library.asString(v));

        assertNoBoolean(v);
        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoBuffer(v);
        // assert string
        assertNoNumber(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);
        assertNoMetaObject(v);
        assertHasNoMetaObject(v);
        assertNoDate(v);
        assertNoTime(v);
        assertNoTimeZone(v);
        assertNoDuration(v);
        assertNoSourceLocation(v);
        assertNoLanguage(v);
        assertNoIdentity(v);
    }

    private void assertNumber(Object v, boolean supportsByte, boolean supportsShort,
                    boolean supportsInt, boolean supportsLong, boolean supportsFloat, boolean supportsDouble) throws InteropException {

        Object expectedValue = v;

        InteropLibrary l = createLibrary(InteropLibrary.class, v);
        assertTrue(l.isNumber(v));

        assertEquals(supportsByte, l.fitsInByte(v));
        assertEquals(supportsShort, l.fitsInShort(v));
        assertEquals(supportsInt, l.fitsInInt(v));
        assertEquals(supportsLong, l.fitsInLong(v));
        assertEquals(supportsFloat, l.fitsInFloat(v));
        assertEquals(supportsDouble, l.fitsInDouble(v));

        if (supportsByte) {
            assertEquals(((Number) expectedValue).byteValue(), l.asByte(v));
        } else {
            assertUnsupported(() -> l.asByte(v));
        }
        if (supportsShort) {
            assertEquals(((Number) expectedValue).shortValue(), l.asShort(v));
        } else {
            assertUnsupported(() -> l.asShort(v));
        }
        if (supportsInt) {
            assertEquals(((Number) expectedValue).intValue(), l.asInt(v));
        } else {
            assertUnsupported(() -> l.asInt(v));
        }
        if (supportsLong) {
            assertEquals(((Number) expectedValue).longValue(), l.asLong(v));
        } else {
            assertUnsupported(() -> l.asLong(v));
        }
        if (supportsFloat) {
            assertEquals(((Number) expectedValue).floatValue(), l.asFloat(v), 0);
        } else {
            assertUnsupported(() -> l.asFloat(v));
        }
        if (supportsDouble) {
            assertEquals(((Number) expectedValue).doubleValue(), l.asDouble(v), 0);
        } else {
            assertUnsupported(() -> l.asDouble(v));
        }

        assertEquals(v.toString(), l.toDisplayString(v));

        assertNoBoolean(v);
        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoBuffer(v);
        assertNoString(v);
        // assert number
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);
        assertNoMetaObject(v);
        assertHasNoMetaObject(v);
        assertNoDate(v);
        assertNoTime(v);
        assertNoTimeZone(v);
        assertNoDuration(v);
        assertNoSourceLocation(v);
        assertNoLanguage(v);
        assertNoIdentity(v);
    }

    @Test
    public void testObjectDefaults() {
        AtomicBoolean toStringInvoked = new AtomicBoolean();

        Object v = new TruffleObject() {
            @Override
            public String toString() {
                toStringInvoked.set(true);
                return super.toString();
            }
        };
        InteropLibrary l = createLibrary(InteropLibrary.class, v);
        String expectedToString = v.toString();
        toStringInvoked.set(false);
        assertEquals(expectedToString, l.toDisplayString(v));
        assertTrue(toStringInvoked.get());
        assertNoTypes(v);
    }

    public static class MetaDataLegacyObject implements TruffleObject {

        final SourceSection section;
        final Object metaObject;

        MetaDataLegacyObject(SourceSection section, Object metaObject) {
            this.section = section;
            this.metaObject = metaObject;
        }

    }

    public static class MetaDataLegacyOnlyLangauge implements TruffleObject {

        MetaDataLegacyOnlyLangauge() {
        }

    }

    private void assertNoTypes(Object v) {
        assertNoBoolean(v);
        assertNotNull(v);
        assertNoObject(v);
        assertNoArray(v);
        assertNoBuffer(v);
        assertNoString(v);
        assertNoNumber(v);
        assertNoNative(v);
        assertNotExecutable(v);
        assertNotInstantiable(v);
        assertNoMetaObject(v);
        assertHasNoMetaObject(v);
        assertNoDate(v);
        assertNoTime(v);
        assertNoTimeZone(v);
        assertNoDuration(v);
        assertNoSourceLocation(v);
        assertNoLanguage(v);
        assertNoIdentity(v);
    }

    @Test
    public void testScopeDefault() {
        Object v = new TruffleObject() {
        };
        InteropLibrary l = createLibrary(InteropLibrary.class, v);
        assertFalse(l.isScope(v));
        assertFalse(l.hasScopeParent(v));
        assertFails(() -> l.getScopeParent(v), UnsupportedMessageException.class);
    }

    @Test
    public void testExceptionDefaults() throws UnsupportedMessageException {
        Object empty = new TruffleObject() {
        };
        InteropLibrary emptyLib = createLibrary(InteropLibrary.class, empty);
        assertFalse(emptyLib.isException(empty));
        assertFalse(emptyLib.hasExceptionCause(empty));
        assertFalse(emptyLib.hasExceptionMessage(empty));
        assertFalse(emptyLib.hasExceptionStackTrace(empty));
        assertFails(() -> emptyLib.getExceptionCause(empty), UnsupportedMessageException.class);
        assertFails(() -> emptyLib.getExceptionExitStatus(empty), UnsupportedMessageException.class);
        assertFails(() -> emptyLib.isExceptionIncompleteSource(empty), UnsupportedMessageException.class);
        assertFails(() -> emptyLib.getExceptionMessage(empty), UnsupportedMessageException.class);
        assertFails(() -> emptyLib.getExceptionStackTrace(empty), UnsupportedMessageException.class);
        assertFails(() -> emptyLib.getExceptionType(empty), UnsupportedMessageException.class);

        AbstractTruffleException cause = new Exception("Cause Exception");
        String message = "Enclosing exception";
        AbstractTruffleException exception = new Exception(message, cause);
        InteropLibrary exceptionLib = createLibrary(InteropLibrary.class, exception);
        assertTrue(exceptionLib.isException(exception));
        assertTrue(exceptionLib.hasExceptionCause(exception));
        assertTrue(exceptionLib.hasExceptionMessage(exception));
        assertTrue(exceptionLib.hasExceptionStackTrace(exception));
        assertEquals(cause, exceptionLib.getExceptionCause(exception));
        assertEquals(message, exceptionLib.getExceptionMessage(exception));
        assertEquals(ExceptionType.RUNTIME_ERROR, exceptionLib.getExceptionType(exception));
        assertFalse(exceptionLib.isExceptionIncompleteSource(exception));
        assertFails(() -> exceptionLib.getExceptionExitStatus(exception), UnsupportedMessageException.class);
        exceptionLib.getExceptionStackTrace(exception);

        LegacyCatchableException legacyCatchableException = new LegacyCatchableException(message);
        InteropLibrary legacyCatchableExceptionLib = createLibrary(InteropLibrary.class, legacyCatchableException);
        assertTrue(legacyCatchableExceptionLib.isException(legacyCatchableException));
        assertFalse(legacyCatchableExceptionLib.hasExceptionCause(legacyCatchableException));
        assertTrue(legacyCatchableExceptionLib.hasExceptionMessage(legacyCatchableException));
        assertTrue(legacyCatchableExceptionLib.hasExceptionStackTrace(legacyCatchableException));
        assertFails(() -> legacyCatchableExceptionLib.getExceptionCause(legacyCatchableException), UnsupportedMessageException.class);
        assertEquals(message, legacyCatchableExceptionLib.getExceptionMessage(legacyCatchableException));
        assertEquals(ExceptionType.RUNTIME_ERROR, legacyCatchableExceptionLib.getExceptionType(legacyCatchableException));
        assertFails(() -> legacyCatchableExceptionLib.getExceptionExitStatus(legacyCatchableException), UnsupportedMessageException.class);
        assertFalse(legacyCatchableExceptionLib.isExceptionIncompleteSource(legacyCatchableException));
        legacyCatchableExceptionLib.getExceptionStackTrace(legacyCatchableException);

        LegacyUncatchableException legacyUncatchableException = new LegacyUncatchableException();
        InteropLibrary legacyUncatchableExceptionLib = createLibrary(InteropLibrary.class, legacyUncatchableException);
        assertFalse(legacyUncatchableExceptionLib.isException(legacyUncatchableException));
        assertFalse(legacyUncatchableExceptionLib.hasExceptionCause(legacyUncatchableException));
        assertFalse(legacyUncatchableExceptionLib.hasExceptionMessage(legacyUncatchableException));
        assertFalse(legacyUncatchableExceptionLib.hasExceptionStackTrace(legacyUncatchableException));
        assertFails(() -> legacyUncatchableExceptionLib.getExceptionCause(legacyUncatchableException), UnsupportedMessageException.class);
        assertFails(() -> legacyUncatchableExceptionLib.getExceptionMessage(legacyUncatchableException), UnsupportedMessageException.class);
        assertFails(() -> legacyUncatchableExceptionLib.getExceptionType(legacyUncatchableException), UnsupportedMessageException.class);
        assertFails(() -> legacyUncatchableExceptionLib.getExceptionExitStatus(legacyUncatchableException), UnsupportedMessageException.class);
        assertFails(() -> legacyUncatchableExceptionLib.isExceptionIncompleteSource(legacyUncatchableException), UnsupportedMessageException.class);
        assertFails(() -> legacyUncatchableExceptionLib.getExceptionStackTrace(legacyUncatchableException), UnsupportedMessageException.class);

        LegacyInternalError legacyInternalError = new LegacyInternalError(message);
        InteropLibrary legacyInternalErrorLib = createLibrary(InteropLibrary.class, legacyInternalError);
        assertFalse(legacyInternalErrorLib.isException(legacyInternalError));
        assertFalse(legacyInternalErrorLib.hasExceptionCause(legacyInternalError));
        assertFalse(legacyInternalErrorLib.hasExceptionMessage(legacyInternalError));
        assertFalse(legacyInternalErrorLib.hasExceptionStackTrace(legacyInternalError));
        assertFails(() -> legacyInternalErrorLib.getExceptionCause(legacyInternalError), UnsupportedMessageException.class);
        assertFails(() -> legacyInternalErrorLib.getExceptionMessage(legacyInternalError), UnsupportedMessageException.class);
        assertFails(() -> legacyInternalErrorLib.getExceptionType(legacyInternalError), UnsupportedMessageException.class);
        assertFails(() -> legacyInternalErrorLib.getExceptionExitStatus(legacyInternalError), UnsupportedMessageException.class);
        assertFails(() -> legacyInternalErrorLib.isExceptionIncompleteSource(legacyInternalError), UnsupportedMessageException.class);
        assertFails(() -> legacyInternalErrorLib.getExceptionStackTrace(legacyInternalError), UnsupportedMessageException.class);
    }

    @SuppressWarnings("serial")
    private static final class Exception extends AbstractTruffleException {

        Exception(String message) {
            super(message);
        }

        Exception(String message, Throwable cause) {
            super(message, cause, UNLIMITED_STACK_TRACE, null);
        }
    }

    @SuppressWarnings({"serial", "deprecation"})
    private static final class LegacyCatchableException extends RuntimeException implements com.oracle.truffle.api.TruffleException {

        LegacyCatchableException(String message) {
            super(message);
        }

        @Override
        public Node getLocation() {
            return null;
        }
    }

    @SuppressWarnings({"serial", "deprecation"})
    private static final class LegacyUncatchableException extends ThreadDeath implements com.oracle.truffle.api.TruffleException {

        LegacyUncatchableException() {
        }

        @Override
        public Node getLocation() {
            return null;
        }
    }

    @SuppressWarnings({"serial", "deprecation"})
    private static final class LegacyInternalError extends RuntimeException implements com.oracle.truffle.api.TruffleException {

        LegacyInternalError(String message) {
            super(message);
        }

        @Override
        public Node getLocation() {
            return null;
        }

        @Override
        public boolean isInternalError() {
            return true;
        }
    }

    @Test
    public void testIterableDefaults() throws UnsupportedMessageException {
        Object empty = new TruffleObject() {
        };
        InteropLibrary emptyLib = createLibrary(InteropLibrary.class, empty);
        assertFalse(emptyLib.hasIterator(empty));
        assertFails(() -> emptyLib.getIterator(empty), UnsupportedMessageException.class);

        Array array = new Array(1, 2, 3);
        InteropLibrary arrayLib = createLibrary(InteropLibrary.class, array);
        assertTrue(arrayLib.hasIterator(array));
        arrayLib.getIterator(array);
    }

    @Test
    public void testIteratorDefaults() throws UnsupportedMessageException, StopIterationException {
        Object empty = new TruffleObject() {
        };
        InteropLibrary emptyLib = createLibrary(InteropLibrary.class, empty);
        assertFalse(emptyLib.isIterator(empty));
        assertFails(() -> emptyLib.hasIteratorNextElement(empty), UnsupportedMessageException.class);
        assertFails(() -> emptyLib.getIteratorNextElement(empty), UnsupportedMessageException.class);

        Array array = new Array(1, 2, 3);
        InteropLibrary arrayLib = createLibrary(InteropLibrary.class, array);
        assertFalse(arrayLib.isIterator(array));
        assertFails(() -> arrayLib.hasIteratorNextElement(array), UnsupportedMessageException.class);
        assertFails(() -> arrayLib.getIteratorNextElement(array), UnsupportedMessageException.class);

        Object iterator = arrayLib.getIterator(array);
        InteropLibrary iteratorLib = createLibrary(InteropLibrary.class, iterator);
        assertTrue(iteratorLib.isIterator(iterator));
        assertTrue(iteratorLib.hasIteratorNextElement(iterator));
        iteratorLib.getIteratorNextElement(iterator);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Array implements TruffleObject {

        private final Object[] elements;

        Array(Object... elements) {
            this.elements = elements;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return elements.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < elements.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return elements[(int) index];
        }

    }

}
