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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class CxxInheritedFieldsTest2 extends InteropTestBase {

    private static Value testCppLibrary;
    private static Object testCppLibraryInternal;

    @BeforeClass
    public static void loadTestBitcode() {
        testCppLibrary = loadTestBitcodeValue("inheritedFieldsTest2.cpp");
        testCppLibraryInternal = loadTestBitcodeInternal("inheritedFieldsTest2.cpp");
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

    public static class CheckB0Node extends SulongTestNode {
        public CheckB0Node() {
            super(testCppLibraryInternal, "checkB0");
        }
    }

    public static class CheckB1Node extends SulongTestNode {
        public CheckB1Node() {
            super(testCppLibraryInternal, "checkB1");
        }
    }

    public static class CheckB2Node extends SulongTestNode {
        public CheckB2Node() {
            super(testCppLibraryInternal, "checkB2");
        }
    }

    @Test
    public void testClassesInternal(@Inject(Check0Node.class) CallTarget check0,
                    @Inject(Check1Node.class) CallTarget check1,
                    @Inject(Check2Node.class) CallTarget check2,
                    @Inject(Check3Node.class) CallTarget check3,
                    @Inject(Check4Node.class) CallTarget check4) {
        check(check0.call(CxxInheritedFieldsTest2_Helpers.getA0()));
        check(check1.call(CxxInheritedFieldsTest2_Helpers.getA1()));
        check(check2.call(CxxInheritedFieldsTest2_Helpers.getA2()));
        check(check3.call(CxxInheritedFieldsTest2_Helpers.getA3()));
        check(check4.call(CxxInheritedFieldsTest2_Helpers.getA4()));
    }

    @Test
    public void testStructsInternal(@Inject(CheckB0Node.class) CallTarget check0,
                    @Inject(CheckB1Node.class) CallTarget check1,
                    @Inject(CheckB2Node.class) CallTarget check2) {
        check(check0.call(CxxInheritedFieldsTest2_Helpers.getB0()));
        check(check1.call(CxxInheritedFieldsTest2_Helpers.getB1()));
        check(check2.call(CxxInheritedFieldsTest2_Helpers.getB2()));
    }

    private static void check(Object guestReturnValue) {
        try {
            Assert.assertTrue(InteropLibrary.getUncached(guestReturnValue).asBoolean(guestReturnValue));
        } catch (UnsupportedMessageException e) {
            Assert.assertTrue(e.getMessage(), false);
        }
    }

    @Test
    public void testSuperClasses() {
        Assert.assertTrue(testCppLibrary.invokeMember("check0", CxxInheritedFieldsTest2_Helpers.getA0()).asBoolean());
        Assert.assertTrue(testCppLibrary.invokeMember("check1", CxxInheritedFieldsTest2_Helpers.getA1()).asBoolean());
        Assert.assertTrue(testCppLibrary.invokeMember("check2", CxxInheritedFieldsTest2_Helpers.getA2()).asBoolean());
        Assert.assertTrue(testCppLibrary.invokeMember("check3", CxxInheritedFieldsTest2_Helpers.getA3()).asBoolean());
        Assert.assertTrue(testCppLibrary.invokeMember("check4", CxxInheritedFieldsTest2_Helpers.getA4()).asBoolean());
    }

    @Test
    public void testSuperStructs() {
        Assert.assertTrue(testCppLibrary.invokeMember("checkB0", CxxInheritedFieldsTest2_Helpers.getB0()).asBoolean());
        Assert.assertTrue(testCppLibrary.invokeMember("checkB1", CxxInheritedFieldsTest2_Helpers.getB1()).asBoolean());
        Assert.assertTrue(testCppLibrary.invokeMember("checkB2", CxxInheritedFieldsTest2_Helpers.getB2()).asBoolean());
    }
}
