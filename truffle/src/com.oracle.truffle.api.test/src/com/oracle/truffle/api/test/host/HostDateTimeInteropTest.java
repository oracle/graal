/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;

import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.interop.HeapIsolationException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public final class HostDateTimeInteropTest extends ProxyLanguageEnvTest {

    private static final InteropLibrary INTEROP = InteropLibrary.getUncached();

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void testOffsetDateTime() throws UnsupportedMessageException, HeapIsolationException {
        OffsetDateTime dateTime = OffsetDateTime.of(2025, 7, 14, 12, 34, 56, 789, ZoneOffset.ofHoursMinutes(5, 45));
        TruffleObject value = asTruffleObject(dateTime);

        assertTrue(INTEROP.isDate(value));
        assertEquals(dateTime.toLocalDate(), INTEROP.asDate(value));
        assertTrue(INTEROP.isTime(value));
        assertEquals(dateTime.toLocalTime(), INTEROP.asTime(value));
        assertTrue(INTEROP.isTimeZone(value));
        assertEquals(dateTime.getOffset(), INTEROP.asTimeZone(value));
        assertTrue(INTEROP.isInstant(value));
        assertEquals(dateTime.toInstant(), INTEROP.asInstant(value));
        assertTrue(INTEROP.isHostObject(value));
        assertSame(dateTime, INTEROP.asHostObject(value));
    }

    @Test
    public void testOffsetTime() throws UnsupportedMessageException, HeapIsolationException {
        OffsetTime time = OffsetTime.of(12, 34, 56, 789, ZoneOffset.ofHoursMinutes(5, 45));
        TruffleObject value = asTruffleObject(time);

        assertFalse(INTEROP.isDate(value));
        assertThrows(UnsupportedMessageException.class, () -> INTEROP.asDate(value));
        assertTrue(INTEROP.isTime(value));
        assertEquals(time.toLocalTime(), INTEROP.asTime(value));
        assertTrue(INTEROP.isTimeZone(value));
        assertEquals(time.getOffset(), INTEROP.asTimeZone(value));
        assertFalse(INTEROP.isInstant(value));
        assertThrows(UnsupportedMessageException.class, () -> INTEROP.asInstant(value));
        assertTrue(INTEROP.isHostObject(value));
        assertSame(time, INTEROP.asHostObject(value));
    }
}
