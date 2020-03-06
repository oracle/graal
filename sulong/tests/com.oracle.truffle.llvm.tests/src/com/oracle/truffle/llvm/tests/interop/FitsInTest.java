/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collection;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class FitsInTest extends InteropTestBase {

    private static Value testFitsIn;

    @BeforeClass
    public static void loadTestBitcode() {
        Value testLibrary = loadTestBitcodeValue("fitsIn.c");
        testFitsIn = testLibrary.getMember("test_fits_in");
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        tests.add(new Object[]{(byte) 42});
        tests.add(new Object[]{(short) 42});
        tests.add(new Object[]{(short) 8472});
        tests.add(new Object[]{42});
        tests.add(new Object[]{8472});
        tests.add(new Object[]{1000000});
        tests.add(new Object[]{42L});
        tests.add(new Object[]{8472L});
        tests.add(new Object[]{1000000L});
        tests.add(new Object[]{2000000000000L});
        tests.add(new Object[]{5.0f});
        tests.add(new Object[]{5.3f});
        tests.add(new Object[]{Float.MIN_VALUE});
        tests.add(new Object[]{Float.MAX_VALUE});
        tests.add(new Object[]{5.0d});
        tests.add(new Object[]{5.3d});
        tests.add(new Object[]{Double.MIN_VALUE});
        tests.add(new Object[]{Double.MAX_VALUE});
        tests.add(new Object[]{"string"});
        return tests;
    }

    @Parameter(0) public Object value;

    @Test
    public void checkNumbers() {
        Value v = runWithPolyglot.getPolyglotContext().asValue(value);
        int ret = testFitsIn.execute(v).asInt();

        Assert.assertEquals("fits_in_i8", v.fitsInByte(), (ret & 1) != 0);
        Assert.assertEquals("fits_in_i16", v.fitsInShort(), (ret & 2) != 0);
        Assert.assertEquals("fits_in_i32", v.fitsInInt(), (ret & 4) != 0);
        Assert.assertEquals("fits_in_i64", v.fitsInLong(), (ret & 8) != 0);
        Assert.assertEquals("fits_in_float", v.fitsInFloat(), (ret & 16) != 0);
        Assert.assertEquals("fits_in_double", v.fitsInDouble(), (ret & 32) != 0);
    }
}
