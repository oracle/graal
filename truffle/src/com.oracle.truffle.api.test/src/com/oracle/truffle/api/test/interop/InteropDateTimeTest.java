/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

public final class InteropDateTimeTest extends InteropLibraryBaseTest {

    @ExportLibrary(InteropLibrary.class)
    static class Defaults implements TruffleObject {

    }

    @Test
    public void testDefaults() {
        Defaults o = new Defaults();
        InteropLibrary library = createLibrary(InteropLibrary.class, o);
        assertFalse(library.isDate(o));
        assertFalse(library.isTime(o));
        assertFalse(library.isTimeZone(o));
        assertFalse(library.isDuration(o));

        assertFails(() -> library.asInstant(o), UnsupportedMessageException.class);
        assertFails(() -> library.asDate(o), UnsupportedMessageException.class);
        assertFails(() -> library.asTime(o), UnsupportedMessageException.class);
        assertFails(() -> library.asTimeZone(o), UnsupportedMessageException.class);
        assertFails(() -> library.asDuration(o), UnsupportedMessageException.class);
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static class InvalidCombinationTest implements TruffleObject {

        final boolean missingTime;

        InvalidCombinationTest(boolean missingTime) {
            this.missingTime = missingTime;
        }

        @ExportMessage
        final boolean isDate() {
            return missingTime;
        }

        @ExportMessage
        final boolean isTime() {
            return !missingTime;
        }

        @ExportMessage
        final boolean isTimeZone() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        final ZoneId asTimeZone() {
            return ZoneId.of("UTC");
        }

        @ExportMessage
        @TruffleBoundary
        final LocalDate asDate() {
            return LocalDate.now();
        }

        @ExportMessage
        final LocalTime asTime() throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @Test
    public void testInvalidCombination() {
        testInvalidCombination(new InvalidCombinationTest(true));
        testInvalidCombination(new InvalidCombinationTest(false));
    }

    private void testInvalidCombination(Object o) {
        InteropLibrary library = createLibrary(InteropLibrary.class, o);
        assertFails(() -> library.isDate(o), AssertionError.class);
        assertFails(() -> library.isTime(o), AssertionError.class);
        assertFails(() -> library.isTimeZone(o), AssertionError.class);
        assertFails(() -> library.asInstant(o), UnsupportedMessageException.class);
        assertFails(() -> library.asDate(o), AssertionError.class);
        assertFails(() -> library.asTimeZone(o), AssertionError.class);
        assertFails(() -> library.asTime(o), AssertionError.class);
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    public static class InstantDefault implements TruffleObject {

        final LocalDateTime dateTime = LocalDateTime.now();

        @ExportMessage
        protected final boolean isDate() {
            return true;
        }

        @ExportMessage
        protected final boolean isTime() {
            return true;
        }

        @ExportMessage
        protected final boolean isTimeZone() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        protected final ZoneId asTimeZone() {
            return ZoneId.of("UTC");
        }

        @ExportMessage
        @TruffleBoundary
        protected final LocalDate asDate() {
            return dateTime.toLocalDate();
        }

        @ExportMessage
        protected final LocalTime asTime() {
            return dateTime.toLocalTime();
        }

    }

    @Test
    public void testInstantDefault() throws InteropException {
        InstantDefault o = new InstantDefault();
        InteropLibrary library = createLibrary(InteropLibrary.class, o);

        assertTrue(library.isDate(o));
        assertTrue(library.isTime(o));
        assertTrue(library.isTimeZone(o));
        assertTrue(library.isDate(o));
        assertEquals(o.dateTime.toLocalDate(), library.asDate(o));
        assertEquals(o.dateTime.toLocalTime(), library.asTime(o));
        assertEquals(ZoneId.of("UTC"), library.asTimeZone(o));
        assertEquals(o.dateTime.atZone(ZoneId.of("UTC")).toInstant(), library.asInstant(o));
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static class InvalidInstant extends InstantDefault {

        final Instant invalidInstant = super.dateTime.plusHours(42).atZone(ZoneId.of("UTC")).toInstant();

        @ExportMessage
        final Instant asInstant() {
            return invalidInstant;
        }

    }

    @Test
    public void testInvalidInstant() throws InteropException {
        InstantDefault o = new InvalidInstant();
        InteropLibrary library = createLibrary(InteropLibrary.class, o);

        assertTrue(library.isDate(o));
        assertTrue(library.isTime(o));
        assertTrue(library.isTimeZone(o));
        assertTrue(library.isDate(o));
        assertEquals(o.dateTime.toLocalDate(), library.asDate(o));
        assertEquals(o.dateTime.toLocalTime(), library.asTime(o));
        assertEquals(ZoneId.of("UTC"), library.asTimeZone(o));
        assertFails(() -> library.asInstant(o), AssertionError.class);
    }

    @Test
    public void testInvalidDuration() throws InteropException {
        InstantDefault o = new InvalidInstant();
        InteropLibrary library = createLibrary(InteropLibrary.class, o);

        assertTrue(library.isDate(o));
        assertTrue(library.isTime(o));
        assertTrue(library.isTimeZone(o));
        assertTrue(library.isDate(o));
        assertEquals(o.dateTime.toLocalDate(), library.asDate(o));
        assertEquals(o.dateTime.toLocalTime(), library.asTime(o));
        assertEquals(ZoneId.of("UTC"), library.asTimeZone(o));
        assertFails(() -> library.asInstant(o), AssertionError.class);
    }

}
