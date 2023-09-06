/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.truffle.llvm.tests.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.oracle.truffle.llvm.tests.interop.TimeConversionTest.TimeLibrary;
import com.oracle.truffle.tck.TruffleRunner;

import org.graalvm.polyglot.Value;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TruffleRunner.class)
public class TmStructConversionTest extends InteropTestBase {

    public static class TmStructLibrary {
        private final Value lib = loadTestBitcodeValue("tmStructConversionTest.c");

        Value gmTimeOfInstant(Object v) {
            return lib.getMember("gmTimeOfInstant").execute(v);
        }

        Value gmTimeOfValue(Object v) {
            return lib.getMember("gmTimeOfValue").execute(v);
        }

        String printDateTime(Object v) {
            return lib.getMember("printDateTime").execute(v).asString();
        }

        String recastPolyglotValue(Object v) {
            return lib.invokeMember("recastPolyglotValue", v).asString();
        }

        String printDateTimeCast(Object v) {
            return lib.getMember("printDateTimeCast").execute(v).asString();
        }

        String printAscTime(Object v) {
            return lib.getMember("printAscTime").execute(v).asString();
        }

    }

    private static TimeLibrary timeLibrary;
    private static TmStructLibrary tmStructLibrary;

    @BeforeClass
    public static void loadTestBitcode() {
        timeLibrary = new TimeLibrary();
        tmStructLibrary = new TmStructLibrary();
    }

    public Instant javaTime() {
        return Instant.ofEpochSecond(TimeConversionTest.JAVA_TIME);
    }

    @Test
    public void gmTimeOfInstantTest() {
        Value v = tmStructLibrary.gmTimeOfInstant(javaTime());
        assertTrue(v.isDate());
        assertTrue(v.isTime());
        LocalDate date = v.asDate();
        assertEquals("2021-12-23", date.toString());
        LocalTime time = v.asTime();
        assertEquals("08:59:45", time.toString());
    }

    @Test
    public void gmTimeOfValueTest() {
        Value v = tmStructLibrary.gmTimeOfInstant(timeLibrary.getTime());
        assertTrue(v.isDate());
        assertTrue(v.isTime());
        LocalDate date = v.asDate();
        assertEquals("2021-12-23", date.toString());
        LocalTime time = v.asTime();
        assertEquals("09:14:55", time.toString());
    }

    @Test
    public void printAscInstantTest() {
        String ascTime = tmStructLibrary.printAscTime(javaTime());
        assertEquals("Wed Dec 23 08:59:45 2021\n", ascTime);
    }

    @Test
    public void printAscDateTest() {
        String ascTime = tmStructLibrary.printAscTime(LocalDate.ofInstant(javaTime(), ZoneId.of("UTC")));
        assertEquals("Wed Dec 23 00:00:00 2021\n", ascTime);
    }

    @Test
    public void printAscCInstantTest() {
        String ascTime = tmStructLibrary.printAscTime(timeLibrary.getTime());
        assertEquals("Wed Dec 23 09:14:55 2021\n", ascTime);
    }

    @Test
    public void printDateTimeTest() {
        Value ctime = timeLibrary.getTime();
        String s = tmStructLibrary.printDateTime(ctime);
        assertEquals("time: 09:14:55", s);
        s = tmStructLibrary.printDateTimeCast(ctime);
        assertEquals("time: 09:14:55", s);
    }

    @Ignore
    @Test
    public void recastPolyglotValueTest() {
        Value ctime = timeLibrary.getTime();
        String s = tmStructLibrary.recastPolyglotValue(ctime);
        assertEquals("time: 09:14:55", s);
    }

    @Test
    public void identifyCTime() {
        Value i = tmStructLibrary.gmTimeOfInstant(timeLibrary.getTime());
        assertTrue(timeLibrary.isTime(i));
        assertTrue(timeLibrary.isDate(i));
        assertFalse(timeLibrary.isTimeZone(i));
        assertFalse(timeLibrary.isInstant(i));
    }

    @Test
    public void identifyJavaDate() {
        LocalDate date = LocalDate.ofInstant(javaTime(), ZoneId.of("UTC"));
        assertTrue(timeLibrary.isDate(date));
        assertFalse(timeLibrary.isTime(date));
        assertFalse(timeLibrary.isTimeZone(date));
        assertFalse(timeLibrary.isInstant(date));
    }

    @Test
    public void identifyJavaTime() {
        LocalTime time = LocalTime.ofInstant(javaTime(), ZoneId.of("UTC"));
        assertFalse(timeLibrary.isDate(time));
        assertTrue(timeLibrary.isTime(time));
        assertFalse(timeLibrary.isTimeZone(time));
        assertFalse(timeLibrary.isInstant(time));
    }
}
