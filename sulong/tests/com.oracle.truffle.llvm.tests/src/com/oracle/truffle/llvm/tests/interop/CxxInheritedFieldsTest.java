/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
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
public class CxxInheritedFieldsTest extends InteropTestBase {

    private static Value testCppLibrary;
    private static Value createA;
    private static Value createB;
    private static Value createC;
    private static Value createD;

    @BeforeClass
    public static void loadTestBitcode() {
        testCppLibrary = loadTestBitcodeValue("inheritedFieldsTest.cpp");
        createA = testCppLibrary.getMember("prepareA");
        createB = testCppLibrary.getMember("prepareB");
        createC = testCppLibrary.getMember("prepareC");
        createD = testCppLibrary.getMember("prepareD");
    }

    @Test
    public void testBasicClassMembers() {
        Value aObj = createA.execute();
        Value bObj = createB.execute();

        Assert.assertTrue("aObj.hasMember(\"a\")", aObj.hasMember("a"));
        Assert.assertEquals(3, aObj.getMember("a").asInt());

        Assert.assertTrue("bObj.hasMember(\"b\")", bObj.hasMember("b"));
        Assert.assertEquals(4, bObj.getMember("b").asInt());
    }

    @Test
    public void testInheritedFieldsOfClass() {
        Value bObj = createB.execute();

        Assert.assertTrue("bObj.hasMember(\"a\")", bObj.hasMember("a"));
        Assert.assertEquals(3, bObj.getMember("a").asInt());
    }

    @Test
    public void testUnexistingFieldOfClass() {
        Assert.assertFalse(testCppLibrary.hasMember("abc"));
        Value aObj = createA.execute();
        Assert.assertFalse("aObj.hasMember(\"c\")", aObj.hasMember("c"));
    }

    @Test
    public void testBasicStructMembers() {
        Value cObj = createC.execute();
        Value dObj = createD.execute();

        Assert.assertTrue("cObj.hasMember(\"c\")", cObj.hasMember("c"));
        Assert.assertEquals(3, cObj.getMember("c").asInt());

        Assert.assertTrue("dObj.hasMember(\"d\")", dObj.hasMember("d"));
        Assert.assertEquals(4, dObj.getMember("d").asInt());
    }

    @Test
    public void testInheritedFieldsOfStruct() {
        Value dObj = createD.execute();

        Assert.assertTrue("dObj.hasMember(\"c\")", dObj.hasMember("c"));
        Assert.assertEquals(3, dObj.getMember("c").asInt());
    }

    @Test
    public void testUnexistingFieldOfStruct() {
        Assert.assertFalse(testCppLibrary.hasMember("abc"));
        Value cObj = createC.execute();
        Assert.assertFalse("cObj.hasMember(\"e\")", cObj.hasMember("e"));
    }
}
