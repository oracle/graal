/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.tck.TruffleRunner;

@RunWith(TruffleRunner.class)
public class CxxVTableTest3 extends InteropTestBase {

    private static Value testCppLibrary;

    private static Value getA1;
    private static Value getA2;
    private static Value getA3;

    private static Value consA1;
    private static Value consA2;
    private static Value consA3;
    private static Value consImpl;

    @BeforeClass
    public static void loadTestBitcode() {
        testCppLibrary = loadTestBitcodeValue("vtableTest3.cpp");
        getA1 = testCppLibrary.getMember("getA1");
        getA2 = testCppLibrary.getMember("getA2");
        getA3 = testCppLibrary.getMember("getA3");
        consA1 = testCppLibrary.getMember("A1");
        consA2 = testCppLibrary.getMember("A2");
        consA3 = testCppLibrary.getMember("A3");
        consImpl = testCppLibrary.getMember("Impl");
    }

    @Test
    public void testSameClass() {
        Assert.assertEquals(1, consA1.newInstance().invokeMember("a1").asInt());
        Assert.assertEquals(2, consA2.newInstance().invokeMember("a2").asInt());
        Assert.assertEquals(3, consA3.newInstance().invokeMember("a3").asInt());
        Assert.assertEquals(10, consImpl.newInstance().invokeMember("impl").asInt());
    }

    @Test
    public void testSimpleVirtuality() {
        Assert.assertEquals(11, getA1.execute().invokeMember("a1").asInt());
    }

    @Test
    public void testVirtuality() {
        Assert.assertEquals(12, getA2.execute().invokeMember("a2").asInt());
        Assert.assertEquals(3, getA3.execute().invokeMember("a3").asInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNonExisting() {
        getA2.execute().invokeMember("a3");
    }
}
