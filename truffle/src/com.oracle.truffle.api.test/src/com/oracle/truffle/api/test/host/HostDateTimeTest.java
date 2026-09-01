/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyDate;
import org.graalvm.polyglot.proxy.ProxyTime;
import org.graalvm.polyglot.proxy.ProxyTimeZone;
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
    public void testHostOffsetDateTime() {
        OffsetDateTime ov = OffsetDateTime.of(2025, 7, 14, 12, 34, 56, 789, ZoneOffset.ofHoursMinutes(5, 45));
        Value v = context.asValue(ov);

        assertTrue(v.isDate());
        assertEquals(v.asDate(), ov.toLocalDate());

        assertTrue(v.isTime());
        assertEquals(v.asTime(), ov.toLocalTime());

        assertTrue(v.isTimeZone());
        assertEquals(v.asTimeZone(), ov.getOffset());

        assertEquals(v.asInstant(), ov.toInstant());

        assertTrue(v.isHostObject());
        assertSame(ov, v.asHostObject());
    }

    @Test
    public void testHostOffsetTime() {
        OffsetTime ov = OffsetTime.of(12, 34, 56, 789, ZoneOffset.ofHoursMinutes(5, 45));
        Value v = context.asValue(ov);

        assertFalse(v.isDate());
        assertFails(() -> v.asDate(), ClassCastException.class);

        assertTrue(v.isTime());
        assertEquals(v.asTime(), ov.toLocalTime());

        assertTrue(v.isTimeZone());
        assertEquals(v.asTimeZone(), ov.getOffset());

        assertFails(() -> v.asInstant(), ClassCastException.class);

        assertTrue(v.isHostObject());
        assertSame(ov, v.asHostObject());
    }

    @Test
    public void testGuestDateTimeAsOffsetTypes() {
        ZonedDateTime dateTime = ZonedDateTime.of(2025, 7, 14, 12, 34, 56, 789, ZoneId.of("Europe/Berlin"));
        Value v = context.asValue(new DateTimeProxy(dateTime));

        assertEquals(dateTime.toOffsetDateTime(), v.as(OffsetDateTime.class));
        assertEquals(dateTime.toOffsetDateTime().toOffsetTime(), v.as(OffsetTime.class));
    }

    @Test
    public void testGuestTimeAsOffsetTime() {
        LocalTime time = LocalTime.of(12, 34, 56, 789);
        ZoneOffset offset = ZoneOffset.ofHoursMinutes(5, 45);
        Value v = context.asValue(new TimeProxy(time, offset));

        assertEquals(OffsetTime.of(time, offset), v.as(OffsetTime.class));
    }

    @Test
    public void testNaiveGuestValuesAsOffsetTypes() {
        Value dateTime = context.asValue(new NaiveDateTimeProxy(LocalDate.of(2025, 7, 14), LocalTime.NOON));
        Value time = context.asValue(ProxyTime.from(LocalTime.NOON));

        assertFails(() -> dateTime.as(OffsetDateTime.class), ClassCastException.class);
        assertFails(() -> time.as(OffsetTime.class), ClassCastException.class);
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

    private static final class DateTimeProxy implements ProxyDate, ProxyTime, ProxyTimeZone {

        private final LocalDate date;
        private final LocalTime time;
        private final ZoneId zone;

        DateTimeProxy(ZonedDateTime dateTime) {
            this(dateTime.toLocalDate(), dateTime.toLocalTime(), dateTime.getZone());
        }

        DateTimeProxy(LocalDate date, LocalTime time, ZoneId zone) {
            this.date = date;
            this.time = time;
            this.zone = zone;
        }

        @Override
        public LocalDate asDate() {
            return date;
        }

        @Override
        public LocalTime asTime() {
            return time;
        }

        @Override
        public ZoneId asTimeZone() {
            return zone;
        }
    }

    private static final class NaiveDateTimeProxy implements ProxyDate, ProxyTime {

        private final LocalDate date;
        private final LocalTime time;

        NaiveDateTimeProxy(LocalDate date, LocalTime time) {
            this.date = date;
            this.time = time;
        }

        @Override
        public LocalDate asDate() {
            return date;
        }

        @Override
        public LocalTime asTime() {
            return time;
        }
    }

    private static final class TimeProxy implements ProxyTime, ProxyTimeZone {

        private final LocalTime time;
        private final ZoneId zone;

        TimeProxy(LocalTime time, ZoneId zone) {
            this.time = time;
            this.zone = zone;
        }

        @Override
        public LocalTime asTime() {
            return time;
        }

        @Override
        public ZoneId asTimeZone() {
            return zone;
        }
    }

}
