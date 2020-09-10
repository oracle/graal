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
public class CxxVTableTest2 extends InteropTestBase {

    private static Value testCppLibrary;
    private static Value preparePolyglotA;
    private static Value preparePolyglotBasA;

    @BeforeClass
    public static void loadTestBitcode() {
        testCppLibrary = loadTestBitcodeValue("vtableTest2.cpp");
        preparePolyglotA = testCppLibrary.getMember("preparePolyglotA");
        preparePolyglotBasA = testCppLibrary.getMember("preparePolyglotBasA");
    }

    @Test
    public void testVirtualMethodsHiddenSubclass() {
        // checks if (A* a=new B())->foo is calling B::foo
        Value a = preparePolyglotBasA.execute();
        int foo1Result = a.invokeMember("foo1").asInt();
        int foo2Result = a.invokeMember("foo2").asInt();
        Assert.assertEquals(11, foo1Result);
        Assert.assertEquals(12, foo2Result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNonExistingVirtualMethod() {
        Value a = preparePolyglotBasA.execute();
        a.invokeMember("foo3").asInt();
    }

    @Test
    public void testVirtualMethodsParentOnly() {
        // checks if (A* a=new A())->foo is calling A::foo
        Value a = preparePolyglotA.execute();
        int foo1Result = a.invokeMember("foo1").asInt();
        int foo2Result = a.invokeMember("foo2").asInt();
        Assert.assertEquals(1, foo1Result);
        Assert.assertEquals(2, foo2Result);
    }

}
