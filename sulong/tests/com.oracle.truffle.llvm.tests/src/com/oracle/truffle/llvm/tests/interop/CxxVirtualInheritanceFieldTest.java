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
public class CxxVirtualInheritanceFieldTest extends InteropTestBase {

    private static Value testCppLibrary;

    private static Value getPolyglotCasA;
    private static Value getPolyglotC;

    @BeforeClass
    public static void loadTestBitcode() {
        testCppLibrary = loadTestBitcodeValue("virtualInheritanceFieldTest.cpp");
        getPolyglotCasA = testCppLibrary.getMember("getPolyglotCasA");
        getPolyglotC = testCppLibrary.getMember("getPolyglotC");
    }

    @Test
    public void testSimple() {
        Value c = getPolyglotC.execute();
        Assert.assertEquals(3L, c.getMember("c_data").asLong());
        Assert.assertEquals(31L, c.invokeMember("a").asLong());
        Assert.assertEquals(3L, c.invokeMember("c").asLong());
    }

    @Test
    public void testMethods() {
        Value ac = getPolyglotCasA.execute();
        Assert.assertEquals(31L, ac.invokeMember("a").asLong());
    }

    @Test
    public void testReadFields() {
        Value c = getPolyglotC.execute();
        Assert.assertEquals(3L, c.getMember("c_data").asLong());
        Assert.assertEquals(21L, c.getMember("b1_data").asLong());
        Assert.assertEquals(22L, c.getMember("b2_data").asLong());
        Assert.assertEquals(1L, c.getMember("a_data").asLong());
    }

    @Test
    public void testWriteFields() {
        Value c = getPolyglotC.execute();
        Assert.assertEquals(3L, c.getMember("c_data").asLong());
        Assert.assertEquals(21L, c.getMember("b1_data").asLong());
        Assert.assertEquals(22L, c.getMember("b2_data").asLong());
        Assert.assertEquals(1L, c.getMember("a_data").asLong());

        c.putMember("c_data", -3);
        c.putMember("b1_data", -21);
        c.putMember("b2_data", -22);
        c.putMember("a_data", -1);

        Assert.assertEquals(-3L, c.getMember("c_data").asLong());
        Assert.assertEquals(-21L, c.getMember("b1_data").asLong());
        Assert.assertEquals(-22L, c.getMember("b2_data").asLong());
        Assert.assertEquals(-1L, c.getMember("a_data").asLong());
    }

}
