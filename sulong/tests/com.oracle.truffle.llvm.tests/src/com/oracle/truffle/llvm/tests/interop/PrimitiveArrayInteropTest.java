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

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.tck.TruffleRunner;
import org.graalvm.polyglot.Value;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public final class PrimitiveArrayInteropTest extends InteropTestBase {

    private static Value testLibrary;
    private static Value freeSeq;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = loadTestBitcodeValue("primitiveArrayInterop.c");
        freeSeq = testLibrary.getMember("free_seq");
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        tests.add(new Object[]{"i8", (byte) 5, (byte) 3});
        tests.add(new Object[]{"i16", (short) 5, (short) 3});
        tests.add(new Object[]{"i32", 5, 3});
        tests.add(new Object[]{"i64", 5L, 3L});
        tests.add(new Object[]{"float", 5.5f, 3.125f});
        tests.add(new Object[]{"double", 5.7, 3.1});
        return tests;
    }

    @Parameter(0) public String name;
    @Parameter(1) public Number start;
    @Parameter(2) public Number step;

    @Test
    public void testFromArray() {
        Value seq = testLibrary.getMember("alloc_seq_" + name).execute(start, step, 20);
        Assert.assertTrue("hasArrayElements", seq.hasArrayElements());
        Assert.assertEquals("arraySize", 20, seq.getArraySize());
        for (int i = 0; i < 20; i++) {
            double expected = start.doubleValue() + i * step.doubleValue();
            Assert.assertEquals("seq[" + i + "]", expected, seq.getArrayElement(i).asDouble(), 0.0);
        }
        freeSeq.execute(seq);
    }

    @Test
    public void testAsArray() {
        int[] arr = new int[15];
        int sum = 0;
        for (int i = 0; i < arr.length; i++) {
            arr[i] = 2 * i - 7;
            sum += arr[i];
        }

        Value ret = testLibrary.getMember("sum_" + name).execute(arr);
        Assert.assertEquals("sum", sum, ret.asDouble(), 0.0);
    }
}
