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
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import com.oracle.truffle.tck.TruffleRunner;

import org.graalvm.polyglot.Value;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TruffleRunner.class)
public class TimeConversionTest extends InteropTestBase {

    public static class TimeLibrary {
        private final Value timeLibrary = loadTestBitcodeValue("timeConversionTest.c");

        public Value getTime() {
            return timeLibrary.getMember("getTime").execute();
        }

        public boolean isDate(Object v) {
            return timeLibrary.getMember("isDate").execute(v).asBoolean();
        }

        public boolean isTime(Object v) {
            return timeLibrary.getMember("isTime").execute(v).asBoolean();
        }

        public boolean isTimeZone(Object v) {
            return timeLibrary.getMember("isTimeZone").execute(v).asBoolean();
        }

        public boolean isInstant(Object v) {
            return timeLibrary.getMember("isInstant").execute(v).asBoolean();
        }

        public String ascTime(Object o) {
            return timeLibrary.getMember("ascTime").execute(o).asString();
        }

        public long epoch(Object o) {
            return timeLibrary.getMember("epoch").execute(o).asLong();
        }

    }

    public static final long JAVA_TIME = 1640249985;
    public static final long C_TIME = 1640250895;

    private static TimeLibrary lib;

    private static Instant javaTime() {
        return Instant.ofEpochSecond(TimeConversionTest.JAVA_TIME);
    }

    @BeforeClass
    public static void loadTestBitcode() {
        lib = new TimeLibrary();
    }

    @Test
    public void identifyCTime() {
        Value i = lib.getTime();
        assertTrue(i.isDate());
        assertTrue(i.isTime());
        assertTrue(i.isTimeZone());
        assertTrue(i.isInstant());
    }

    @Test
    public void identifyCTimeLib() {
        Value v = lib.getTime();
        assertTrue(lib.isDate(v));
        assertTrue(lib.isTime(v));
        assertTrue(lib.isTimeZone(v));
        assertTrue(lib.isInstant(v));
    }

    @Test
    public void identifyJavaTimeLib() {
        Instant i = javaTime();
        assertTrue(lib.isDate(i));
        assertTrue(lib.isTime(i));
        assertTrue(lib.isTimeZone(i));
        assertTrue(lib.isInstant(i));
    }

    @Test
    public void testGetTime() {
        Instant i = lib.getTime().asInstant();
        assertEquals(C_TIME, i.getEpochSecond());
    }

    @Test
    public void testPrintJavaTime() {
        String str = lib.ascTime(javaTime());
        assertEquals("Thu Dec 23 08:59:45 2021\n", str);
    }

    @Test
    public void testPrintCTime() {
        String str = lib.ascTime(lib.getTime());
        assertEquals("Thu Dec 23 09:14:55 2021\n", str);
    }

    @Test
    public void ctimeSeconds() {
        long seconds = lib.epoch(lib.getTime());
        assertEquals(C_TIME, seconds);
    }

    @Test
    public void javaSeconds() {
        long seconds = lib.epoch(javaTime());
        assertEquals(JAVA_TIME, seconds);
    }
}
