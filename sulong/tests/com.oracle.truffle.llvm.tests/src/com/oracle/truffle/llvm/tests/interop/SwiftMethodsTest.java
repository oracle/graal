/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.tests.CommonTestUtils;

@RunWith(CommonTestUtils.ExcludingTruffleRunner.class)
public class SwiftMethodsTest extends InteropTestBase {

    private static Value testLibrary;

    private static Value objectCreator;
    private static Value parent;
    private static Value child;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = loadTestBitcodeValue("swiftMethodsTest.swift");

        objectCreator = testLibrary.getMember("ObjectCreator");
        parent = objectCreator.invokeMember("createParent");
        child = objectCreator.invokeMember("createChild");
    }

    @Test
    public void testMethodsBaseClass() {
        Assert.assertEquals(14, parent.invokeMember("get14"));
        Assert.assertEquals(0, Double.compare(3.5, parent.invokeMember("get3P5").asDouble()));
    }

    @Test
    public void testMethodsSubClass() {
        Assert.assertEquals(214, child.invokeMember("get14"));
        Assert.assertEquals(0, Double.compare(23.5, child.invokeMember("get3P5").asDouble()));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNonExistingMethod() {
        parent.invokeMember("methodWhichDoesNotExist");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWrongArity() {
        parent.invokeMember("get14", 14);
    }

}
