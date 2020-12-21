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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class CxxInheritedFieldsTest2 extends InteropTestBase {

    private static Value testCppLibrary;
    private static Object testCppLibraryInternal;
    private static CxxInheritedFieldsTest2_Helpers helpers;

    @BeforeClass
    public static void loadTestBitcode() {
        testCppLibrary = loadTestBitcodeValue("inheritedFieldsTest2.cpp");
        testCppLibraryInternal = loadTestBitcodeInternal("inheritedFieldsTest2.cpp");
        helpers = new CxxInheritedFieldsTest2_Helpers();
    }

    public static class Check0Node extends SulongTestNode {
        public Check0Node() {
            super(testCppLibraryInternal, "check0");
        }
    }

    public static class Check1Node extends SulongTestNode {
        public Check1Node() {
            super(testCppLibraryInternal, "check1");
        }
    }

    public static class Check2Node extends SulongTestNode {
        public Check2Node() {
            super(testCppLibraryInternal, "check2");
        }
    }

    public static class Check3Node extends SulongTestNode {
        public Check3Node() {
            super(testCppLibraryInternal, "check3");
        }
    }

    public static class Check4Node extends SulongTestNode {
        public Check4Node() {
            super(testCppLibraryInternal, "check4");
        }
    }

    @Test
    public void testInternal(@Inject(Check0Node.class) CallTarget check0,
                    @Inject(Check1Node.class) CallTarget check1,
                    @Inject(Check2Node.class) CallTarget check2,
                    @Inject(Check3Node.class) CallTarget check3,
                    @Inject(Check4Node.class) CallTarget check4) {
        check(check0.call(toGuestValue(helpers.getA0())));
        check(check1.call(toGuestValue(helpers.getA1())));
        check(check2.call(toGuestValue(helpers.getA2())));
        check(check3.call(toGuestValue(helpers.getA3())));
        check(check4.call(toGuestValue(helpers.getA4())));
    }

    private static void check(Object guestReturnValue) {
        try {
            Assert.assertTrue(InteropLibrary.getUncached(guestReturnValue).asBoolean(guestReturnValue));
        } catch (UnsupportedMessageException e) {
            Assert.assertTrue(e.getMessage(), false);
        }
    }

    private Object toGuestValue(Object hostValue) {
        return runWithPolyglot.getTruffleTestEnv().asGuestValue(hostValue);
    }

    @Test
    public void test() {
        Assert.assertTrue(testCppLibrary.invokeMember("check0", helpers.getA0()).asBoolean());
        Assert.assertTrue(testCppLibrary.invokeMember("check1", helpers.getA1()).asBoolean());
        Assert.assertTrue(testCppLibrary.invokeMember("check2", helpers.getA2()).asBoolean());
        Assert.assertTrue(testCppLibrary.invokeMember("check3", helpers.getA3()).asBoolean());
        Assert.assertTrue(testCppLibrary.invokeMember("check4", helpers.getA4()).asBoolean());
    }
}
