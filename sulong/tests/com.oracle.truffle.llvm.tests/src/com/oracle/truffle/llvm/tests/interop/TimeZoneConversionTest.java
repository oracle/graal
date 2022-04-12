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

import java.time.ZoneId;

import com.oracle.truffle.llvm.tests.interop.TimeConversionTest.TimeLibrary;
import com.oracle.truffle.tck.TruffleRunner;

import org.graalvm.polyglot.Value;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TruffleRunner.class)
public class TimeZoneConversionTest extends InteropTestBase {

    public static class ZoneIdLibrary {
        private final Value lib = loadTestBitcodeValue("timeZoneConversion.c");

        Value zurichZone() {
            return lib.getMember("zurichZone").execute();
        }

        String stringFromZone(Object v) {
            return lib.getMember("stringFromZone").execute(v).asString();
        }
    }

    private static TimeLibrary timeLibrary;
    private static ZoneIdLibrary zoneIdLibrary;

    @BeforeClass
    public static void loadTestBitcode() {
        timeLibrary = new TimeLibrary();
        zoneIdLibrary = new ZoneIdLibrary();
    }

    @Test
    public void test() {
        Value v = zoneIdLibrary.zurichZone();
        assertTrue(v.isTimeZone());
        assertEquals("Europe/Zurich", v.asTimeZone().getId());
    }

    @Test
    public void testFromJavaZone() {
        ZoneId zone = ZoneId.of("Europe/Berlin");
        assertEquals("Europe/Berlin", zoneIdLibrary.stringFromZone(zone));
    }

    @Test
    public void testFromCZone() {
        Value v = zoneIdLibrary.zurichZone();
        assertEquals("Europe/Zurich", zoneIdLibrary.stringFromZone(v));
    }

    @Test
    public void testFromCInstant() {
        Value v = timeLibrary.getTime();
        assertEquals("UTC", zoneIdLibrary.stringFromZone(v));
    }
}
