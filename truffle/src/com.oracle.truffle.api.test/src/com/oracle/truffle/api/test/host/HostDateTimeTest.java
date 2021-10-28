/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import org.graalvm.polyglot.Value;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

public final class HostDateTimeTest extends AbstractPolyglotTest {

    @Before
    public void setup() {
        setupEnv();
    }

    @Test
    public void tesHostLocalDateTime() {
        LocalDateTime ov = LocalDateTime.now();
        Value v = context.asValue(ov);

        assertTrue(v.isDate());
        assertEquals(v.asDate(), ov.toLocalDate());

        assertTrue(v.isTime());
        assertEquals(v.asTime(), ov.toLocalTime());

        assertFalse(v.isTimeZone());
        assertFails(() -> v.asTimeZone(), ClassCastException.class);

        assertFails(() -> v.asInstant(), ClassCastException.class);

        assertTrue(v.isHostObject());
        assertSame(ov, v.asHostObject());
    }

    @Test
    public void testHostLocalDate() {
        LocalDate ov = LocalDate.now();
        Value v = context.asValue(ov);

        assertTrue(v.isDate());
        assertEquals(v.asDate(), ov);

        assertFalse(v.isTime());
        assertFails(() -> v.asTime(), ClassCastException.class);
        assertFalse(v.isTimeZone());
        assertFails(() -> v.asTimeZone(), ClassCastException.class);
        assertFails(() -> v.asInstant(), ClassCastException.class);

        assertTrue(v.isHostObject());
        assertSame(ov, v.asHostObject());
    }

    @Test
    public void testHostLocalTime() {
        LocalTime ov = LocalTime.now();
        Value v = context.asValue(ov);

        assertFalse(v.isDate());
        assertFails(() -> v.asDate(), ClassCastException.class);

        assertTrue(v.isTime());
        assertEquals(v.asTime(), ov);

        assertFalse(v.isTimeZone());
        assertFails(() -> v.asTimeZone(), ClassCastException.class);

        assertFails(() -> v.asInstant(), ClassCastException.class);

        assertTrue(v.isHostObject());
        assertSame(ov, v.asHostObject());
    }

    @Test
    public void testHostZonedDateTime() {
        ZonedDateTime ov = ZonedDateTime.now();
        Value v = context.asValue(ov);

        assertTrue(v.isDate());
        assertEquals(v.asDate(), ov.toLocalDate());

        assertTrue(v.isTime());
        assertEquals(v.asTime(), ov.toLocalTime());

        assertTrue(v.isTimeZone());
        assertEquals(v.asTimeZone(), ov.getZone());

        assertEquals(v.asInstant(), ov.toInstant());

        assertTrue(v.isHostObject());
        assertSame(ov, v.asHostObject());
    }

    @Test
    public void testHostDate() {
        Date ov = Date.from(Instant.now());
        Value v = context.asValue(ov);
        ZoneId utc = ZoneId.of("UTC");

        assertTrue(v.isDate());
        assertEquals(v.asDate(), ov.toInstant().atZone(utc).toLocalDate());

        assertTrue(v.isTime());
        assertEquals(v.asTime(), ov.toInstant().atZone(utc).toLocalTime());

        assertTrue(v.isTimeZone());
        assertEquals(v.asTimeZone(), utc);

        assertEquals(v.asInstant(), ov.toInstant());

        assertTrue(v.isHostObject());
        assertSame(ov, v.asHostObject());
    }

    @Test
    public void testSQLDate() {
        java.sql.Date ov = new java.sql.Date(0);
        Value v = context.asValue(ov);

        assertTrue(v.isDate());
        assertEquals(v.asDate(), ov.toLocalDate());

        assertFalse(v.isTime());
        assertFails(() -> v.asTime(), ClassCastException.class);

        assertFalse(v.isTimeZone());
        assertFails(() -> v.asTimeZone(), ClassCastException.class);

        assertFails(() -> v.asInstant(), ClassCastException.class);

        assertTrue(v.isHostObject());
        assertSame(ov, v.asHostObject());
    }

    @Test
    public void testSQLTime() {
        java.sql.Time ov = new java.sql.Time(0);
        Value v = context.asValue(ov);

        assertFalse(v.isDate());
        assertFails(() -> v.asDate(), ClassCastException.class);

        assertTrue(v.isTime());
        assertEquals(v.asTime(), ov.toLocalTime());

        assertFalse(v.isTimeZone());
        assertFails(() -> v.asTimeZone(), ClassCastException.class);

        assertFails(() -> v.asInstant(), ClassCastException.class);

        assertTrue(v.isHostObject());
        assertSame(ov, v.asHostObject());
    }

    @Test
    public void testSQLTimestamp() {
        java.sql.Timestamp ov = new java.sql.Timestamp(0);
        Value v = context.asValue(ov);
        ZoneId utc = ZoneId.of("UTC");

        assertTrue(v.isDate());
        assertEquals(v.asDate(), ov.toInstant().atZone(utc).toLocalDate());

        assertTrue(v.isTime());
        assertEquals(v.asTime(), ov.toInstant().atZone(utc).toLocalTime());

        assertTrue(v.isTimeZone());
        assertEquals(v.asTimeZone(), utc);

        assertEquals(v.asInstant(), ov.toInstant());

        assertTrue(v.isHostObject());
        assertSame(ov, v.asHostObject());
    }

    @Test
    @SuppressWarnings("serial")
    public void testCustomDateSubclass() {
        java.util.Date ov = new java.util.Date(0) {
            @Override
            public Instant toInstant() {
                return super.toInstant();
            }
        };
        Value v = context.asValue(ov);
        ZoneId utc = ZoneId.of("UTC");

        assertTrue(v.isDate());
        assertEquals(v.asDate(), ov.toInstant().atZone(utc).toLocalDate());

        assertTrue(v.isTime());
        assertEquals(v.asTime(), ov.toInstant().atZone(utc).toLocalTime());

        assertTrue(v.isTimeZone());
        assertEquals(v.asTimeZone(), utc);

        assertEquals(v.asInstant(), ov.toInstant());

        assertTrue(v.isHostObject());
        assertSame(ov, v.asHostObject());
    }

}
