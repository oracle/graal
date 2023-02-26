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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.tests.interop.CxxVirtualInheritanceFieldTestFactory.GetMemberNodeGen;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class CxxVirtualInheritanceFieldTest extends InteropTestBase {

    private static Value cppLibrary;
    private static Object cppLibraryInternal;
    private static String fieldNameToAccess;

    @BeforeClass
    public static void loadTestBitcode() {
        cppLibrary = loadTestBitcodeValue("virtualInheritanceFieldTest.cpp");
        cppLibraryInternal = loadTestBitcodeInternal("virtualInheritanceFieldTest.cpp");
    }

    @Test
    public void testFieldAccessLevel1() {
        Value a;

        a = cppLibrary.invokeMember("getA0");
        Assert.assertEquals("a0.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a0.a0=", 0L, a.getMember("a0_data").asLong());
        a.putMember("a0_data", 1000 + 0);
        Assert.assertEquals("a0.a0=", 0 + 1000, a.getMember("a0_data").asLong());

        a = cppLibrary.invokeMember("getA1");
        Assert.assertEquals("a1.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a1.a1=", 1L, a.getMember("a1_data").asLong());
        a.putMember("a1_data", 1000 + 1);
        Assert.assertEquals("a1.a1=", 1 + 1000, a.getMember("a1_data").asLong());

        a = cppLibrary.invokeMember("getA2");
        Assert.assertEquals("a2.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a2.a2=", 2L, a.getMember("a2_data").asLong());
        a.putMember("a2_data", 1000 + 2);
        Assert.assertEquals("a2.a2=", 2 + 1000, a.getMember("a2_data").asLong());

        a = cppLibrary.invokeMember("getA4");
        Assert.assertEquals("a4.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a4.a4=", 4L, a.getMember("a4_data").asLong());
        a.putMember("a4_data", 1000 + 4);
        Assert.assertEquals("a4.a4=", 4 + 1000, a.getMember("a4_data").asLong());

        a = cppLibrary.invokeMember("getA5");
        Assert.assertEquals("a5.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a5.a5=", 5L, a.getMember("a5_data").asLong());
        a.putMember("a5_data", 1000 + 5);
        Assert.assertEquals("a5.a5=", 5 + 1000, a.getMember("a5_data").asLong());

        a = cppLibrary.invokeMember("getA6");
        Assert.assertEquals("a6.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a6.a6=", 6L, a.getMember("a6_data").asLong());
        a.putMember("a6_data", 1000 + 6);
        Assert.assertEquals("a6.a6=", 6 + 1000, a.getMember("a6_data").asLong());

        a = cppLibrary.invokeMember("getA8");
        Assert.assertEquals("a8.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a8.a8=", 8L, a.getMember("a8_data").asLong());
        a.putMember("a8_data", 1000 + 8);
        Assert.assertEquals("a8.a8=", 8 + 1000, a.getMember("a8_data").asLong());

        a = cppLibrary.invokeMember("getA9");
        Assert.assertEquals("a9.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a9.a9=", 9L, a.getMember("a9_data").asLong());
        a.putMember("a9_data", 1000 + 9);
        Assert.assertEquals("a9.a9=", 9 + 1000, a.getMember("a9_data").asLong());

        a = cppLibrary.invokeMember("getA10");
        Assert.assertEquals("a10.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a10.a10=", 10L, a.getMember("a10_data").asLong());
        a.putMember("a10_data", 1000 + 10);
        Assert.assertEquals("a10.a10=", 10 + 1000, a.getMember("a10_data").asLong());

        a = cppLibrary.invokeMember("getA13");
        Assert.assertEquals("a13.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a13.a13=", 13L, a.getMember("a13_data").asLong());
        a.putMember("a13_data", 1000 + 13);
        Assert.assertEquals("a13.a13=", 13 + 1000, a.getMember("a13_data").asLong());

        a = cppLibrary.invokeMember("getA14");
        Assert.assertEquals("a14.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a14.a14=", 14L, a.getMember("a14_data").asLong());
        a.putMember("a14_data", 1000 + 14);
        Assert.assertEquals("a14.a14=", 14 + 1000, a.getMember("a14_data").asLong());

        a = cppLibrary.invokeMember("getA15");
        Assert.assertEquals("a15.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a15.a15=", 15L, a.getMember("a15_data").asLong());
        a.putMember("a15_data", 1000 + 15);
        Assert.assertEquals("a15.a15=", 15 + 1000, a.getMember("a15_data").asLong());

        a = cppLibrary.invokeMember("getA17");
        Assert.assertEquals("a17.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a17.a17=", 17L, a.getMember("a17_data").asLong());
        a.putMember("a17_data", 1000 + 17);
        Assert.assertEquals("a17.a17=", 17 + 1000, a.getMember("a17_data").asLong());

        a = cppLibrary.invokeMember("getA18");
        Assert.assertEquals("a18.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a18.a18=", 18L, a.getMember("a18_data").asLong());
        a.putMember("a18_data", 1000 + 18);
        Assert.assertEquals("a18.a18=", 18 + 1000, a.getMember("a18_data").asLong());

        a = cppLibrary.invokeMember("getA19");
        Assert.assertEquals("a19.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a19.a19=", 19L, a.getMember("a19_data").asLong());
        a.putMember("a19_data", 1000 + 19);
        Assert.assertEquals("a19.a19=", 19 + 1000, a.getMember("a19_data").asLong());

        a = cppLibrary.invokeMember("getA21");
        Assert.assertEquals("a21.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a21.a21=", 21L, a.getMember("a21_data").asLong());
        a.putMember("a21_data", 1000 + 21);
        Assert.assertEquals("a21.a21=", 21 + 1000, a.getMember("a21_data").asLong());

        a = cppLibrary.invokeMember("getA22");
        Assert.assertEquals("a22.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a22.a22=", 22L, a.getMember("a22_data").asLong());
        a.putMember("a22_data", 1000 + 22);
        Assert.assertEquals("a22.a22=", 22 + 1000, a.getMember("a22_data").asLong());

        a = cppLibrary.invokeMember("getA23");
        Assert.assertEquals("a23.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a23.a23=", 23L, a.getMember("a23_data").asLong());
        a.putMember("a23_data", 1000 + 23);
        Assert.assertEquals("a23.a23=", 23 + 1000, a.getMember("a23_data").asLong());

        a = cppLibrary.invokeMember("getA26");
        Assert.assertEquals("a26.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a26.a26=", 26L, a.getMember("a26_data").asLong());
        a.putMember("a26_data", 1000 + 26);
        Assert.assertEquals("a26.a26=", 26 + 1000, a.getMember("a26_data").asLong());

        a = cppLibrary.invokeMember("getA27");
        Assert.assertEquals("a27.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a27.a27=", 27L, a.getMember("a27_data").asLong());
        a.putMember("a27_data", 1000 + 27);
        Assert.assertEquals("a27.a27=", 27 + 1000, a.getMember("a27_data").asLong());

        a = cppLibrary.invokeMember("getA28");
        Assert.assertEquals("a28.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a28.a28=", 28L, a.getMember("a28_data").asLong());
        a.putMember("a28_data", 1000 + 28);
        Assert.assertEquals("a28.a28=", 28 + 1000, a.getMember("a28_data").asLong());

        a = cppLibrary.invokeMember("getA30");
        Assert.assertEquals("a30.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a30.a30=", 30L, a.getMember("a30_data").asLong());
        a.putMember("a30_data", 1000 + 30);
        Assert.assertEquals("a30.a30=", 30 + 1000, a.getMember("a30_data").asLong());

        a = cppLibrary.invokeMember("getA31");
        Assert.assertEquals("a31.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a31.a31=", 31L, a.getMember("a31_data").asLong());
        a.putMember("a31_data", 1000 + 31);
        Assert.assertEquals("a31.a31=", 31 + 1000, a.getMember("a31_data").asLong());

        a = cppLibrary.invokeMember("getA32");
        Assert.assertEquals("a32.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a32.a32=", 32L, a.getMember("a32_data").asLong());
        a.putMember("a32_data", 1000 + 32);
        Assert.assertEquals("a32.a32=", 32 + 1000, a.getMember("a32_data").asLong());

        a = cppLibrary.invokeMember("getA34");
        Assert.assertEquals("a34.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a34.a34=", 34L, a.getMember("a34_data").asLong());
        a.putMember("a34_data", 1000 + 34);
        Assert.assertEquals("a34.a34=", 34 + 1000, a.getMember("a34_data").asLong());

        a = cppLibrary.invokeMember("getA35");
        Assert.assertEquals("a35.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a35.a35=", 35L, a.getMember("a35_data").asLong());
        a.putMember("a35_data", 1000 + 35);
        Assert.assertEquals("a35.a35=", 35 + 1000, a.getMember("a35_data").asLong());

        a = cppLibrary.invokeMember("getA36");
        Assert.assertEquals("a36.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a36.a36=", 36L, a.getMember("a36_data").asLong());
        a.putMember("a36_data", 1000 + 36);
        Assert.assertEquals("a36.a36=", 36 + 1000, a.getMember("a36_data").asLong());

    }

    @Test
    public void testFieldAccessLevel2() {
        Value a;

        a = cppLibrary.invokeMember("getA3");
        Assert.assertEquals("a3.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a3.a3=", 3L, a.getMember("a3_data").asLong());
        a.putMember("a3_data", 1000 + 3);
        Assert.assertEquals("a3.a3=", 3 + 1000, a.getMember("a3_data").asLong());
        Assert.assertEquals("a3.a0=", 0L, a.getMember("a0_data").asLong());
        a.putMember("a0_data", 1000 + 0);
        Assert.assertEquals("a3.a0=", 0 + 1000, a.getMember("a0_data").asLong());
        Assert.assertEquals("a3.a1=", 1L, a.getMember("a1_data").asLong());
        a.putMember("a1_data", 1000 + 1);
        Assert.assertEquals("a3.a1=", 1 + 1000, a.getMember("a1_data").asLong());
        Assert.assertEquals("a3.a2=", 2L, a.getMember("a2_data").asLong());
        a.putMember("a2_data", 1000 + 2);
        Assert.assertEquals("a3.a2=", 2 + 1000, a.getMember("a2_data").asLong());

        a = cppLibrary.invokeMember("getA7");
        Assert.assertEquals("a7.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a7.a7=", 7L, a.getMember("a7_data").asLong());
        a.putMember("a7_data", 1000 + 7);
        Assert.assertEquals("a7.a7=", 7 + 1000, a.getMember("a7_data").asLong());
        Assert.assertEquals("a7.a4=", 4L, a.getMember("a4_data").asLong());
        a.putMember("a4_data", 1000 + 4);
        Assert.assertEquals("a7.a4=", 4 + 1000, a.getMember("a4_data").asLong());
        Assert.assertEquals("a7.a5=", 5L, a.getMember("a5_data").asLong());
        a.putMember("a5_data", 1000 + 5);
        Assert.assertEquals("a7.a5=", 5 + 1000, a.getMember("a5_data").asLong());
        Assert.assertEquals("a7.a6=", 6L, a.getMember("a6_data").asLong());
        a.putMember("a6_data", 1000 + 6);
        Assert.assertEquals("a7.a6=", 6 + 1000, a.getMember("a6_data").asLong());

        a = cppLibrary.invokeMember("getA11");
        Assert.assertEquals("a11.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a11.a11=", 11L, a.getMember("a11_data").asLong());
        a.putMember("a11_data", 1000 + 11);
        Assert.assertEquals("a11.a11=", 11 + 1000, a.getMember("a11_data").asLong());
        Assert.assertEquals("a11.a8=", 8L, a.getMember("a8_data").asLong());
        a.putMember("a8_data", 1000 + 8);
        Assert.assertEquals("a11.a8=", 8 + 1000, a.getMember("a8_data").asLong());
        Assert.assertEquals("a11.a9=", 9L, a.getMember("a9_data").asLong());
        a.putMember("a9_data", 1000 + 9);
        Assert.assertEquals("a11.a9=", 9 + 1000, a.getMember("a9_data").asLong());
        Assert.assertEquals("a11.a10=", 10L, a.getMember("a10_data").asLong());
        a.putMember("a10_data", 1000 + 10);
        Assert.assertEquals("a11.a10=", 10 + 1000, a.getMember("a10_data").asLong());

        a = cppLibrary.invokeMember("getA16");
        Assert.assertEquals("a16.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a16.a16=", 16L, a.getMember("a16_data").asLong());
        a.putMember("a16_data", 1000 + 16);
        Assert.assertEquals("a16.a16=", 16 + 1000, a.getMember("a16_data").asLong());
        Assert.assertEquals("a16.a13=", 13L, a.getMember("a13_data").asLong());
        a.putMember("a13_data", 1000 + 13);
        Assert.assertEquals("a16.a13=", 13 + 1000, a.getMember("a13_data").asLong());
        Assert.assertEquals("a16.a14=", 14L, a.getMember("a14_data").asLong());
        a.putMember("a14_data", 1000 + 14);
        Assert.assertEquals("a16.a14=", 14 + 1000, a.getMember("a14_data").asLong());
        Assert.assertEquals("a16.a15=", 15L, a.getMember("a15_data").asLong());
        a.putMember("a15_data", 1000 + 15);
        Assert.assertEquals("a16.a15=", 15 + 1000, a.getMember("a15_data").asLong());

        a = cppLibrary.invokeMember("getA20");
        Assert.assertEquals("a20.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a20.a20=", 20L, a.getMember("a20_data").asLong());
        a.putMember("a20_data", 1000 + 20);
        Assert.assertEquals("a20.a20=", 20 + 1000, a.getMember("a20_data").asLong());
        Assert.assertEquals("a20.a17=", 17L, a.getMember("a17_data").asLong());
        a.putMember("a17_data", 1000 + 17);
        Assert.assertEquals("a20.a17=", 17 + 1000, a.getMember("a17_data").asLong());
        Assert.assertEquals("a20.a18=", 18L, a.getMember("a18_data").asLong());
        a.putMember("a18_data", 1000 + 18);
        Assert.assertEquals("a20.a18=", 18 + 1000, a.getMember("a18_data").asLong());
        Assert.assertEquals("a20.a19=", 19L, a.getMember("a19_data").asLong());
        a.putMember("a19_data", 1000 + 19);
        Assert.assertEquals("a20.a19=", 19 + 1000, a.getMember("a19_data").asLong());

        a = cppLibrary.invokeMember("getA24");
        Assert.assertEquals("a24.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a24.a24=", 24L, a.getMember("a24_data").asLong());
        a.putMember("a24_data", 1000 + 24);
        Assert.assertEquals("a24.a24=", 24 + 1000, a.getMember("a24_data").asLong());
        Assert.assertEquals("a24.a21=", 21L, a.getMember("a21_data").asLong());
        a.putMember("a21_data", 1000 + 21);
        Assert.assertEquals("a24.a21=", 21 + 1000, a.getMember("a21_data").asLong());
        Assert.assertEquals("a24.a22=", 22L, a.getMember("a22_data").asLong());
        a.putMember("a22_data", 1000 + 22);
        Assert.assertEquals("a24.a22=", 22 + 1000, a.getMember("a22_data").asLong());
        Assert.assertEquals("a24.a23=", 23L, a.getMember("a23_data").asLong());
        a.putMember("a23_data", 1000 + 23);
        Assert.assertEquals("a24.a23=", 23 + 1000, a.getMember("a23_data").asLong());

        a = cppLibrary.invokeMember("getA29");
        Assert.assertEquals("a29.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a29.a29=", 29L, a.getMember("a29_data").asLong());
        a.putMember("a29_data", 1000 + 29);
        Assert.assertEquals("a29.a29=", 29 + 1000, a.getMember("a29_data").asLong());
        Assert.assertEquals("a29.a26=", 26L, a.getMember("a26_data").asLong());
        a.putMember("a26_data", 1000 + 26);
        Assert.assertEquals("a29.a26=", 26 + 1000, a.getMember("a26_data").asLong());
        Assert.assertEquals("a29.a27=", 27L, a.getMember("a27_data").asLong());
        a.putMember("a27_data", 1000 + 27);
        Assert.assertEquals("a29.a27=", 27 + 1000, a.getMember("a27_data").asLong());
        Assert.assertEquals("a29.a28=", 28L, a.getMember("a28_data").asLong());
        a.putMember("a28_data", 1000 + 28);
        Assert.assertEquals("a29.a28=", 28 + 1000, a.getMember("a28_data").asLong());

        a = cppLibrary.invokeMember("getA33");
        Assert.assertEquals("a33.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a33.a33=", 33L, a.getMember("a33_data").asLong());
        a.putMember("a33_data", 1000 + 33);
        Assert.assertEquals("a33.a33=", 33 + 1000, a.getMember("a33_data").asLong());
        Assert.assertEquals("a33.a30=", 30L, a.getMember("a30_data").asLong());
        a.putMember("a30_data", 1000 + 30);
        Assert.assertEquals("a33.a30=", 30 + 1000, a.getMember("a30_data").asLong());
        Assert.assertEquals("a33.a31=", 31L, a.getMember("a31_data").asLong());
        a.putMember("a31_data", 1000 + 31);
        Assert.assertEquals("a33.a31=", 31 + 1000, a.getMember("a31_data").asLong());
        Assert.assertEquals("a33.a32=", 32L, a.getMember("a32_data").asLong());
        a.putMember("a32_data", 1000 + 32);
        Assert.assertEquals("a33.a32=", 32 + 1000, a.getMember("a32_data").asLong());

        a = cppLibrary.invokeMember("getA37");
        Assert.assertEquals("a37.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a37.a37=", 37L, a.getMember("a37_data").asLong());
        a.putMember("a37_data", 1000 + 37);
        Assert.assertEquals("a37.a37=", 37 + 1000, a.getMember("a37_data").asLong());
        Assert.assertEquals("a37.a34=", 34L, a.getMember("a34_data").asLong());
        a.putMember("a34_data", 1000 + 34);
        Assert.assertEquals("a37.a34=", 34 + 1000, a.getMember("a34_data").asLong());
        Assert.assertEquals("a37.a35=", 35L, a.getMember("a35_data").asLong());
        a.putMember("a35_data", 1000 + 35);
        Assert.assertEquals("a37.a35=", 35 + 1000, a.getMember("a35_data").asLong());
        Assert.assertEquals("a37.a36=", 36L, a.getMember("a36_data").asLong());
        a.putMember("a36_data", 1000 + 36);
        Assert.assertEquals("a37.a36=", 36 + 1000, a.getMember("a36_data").asLong());
    }

    @Test
    public void testFieldAccessLevel3() {
        Value a;

        a = cppLibrary.invokeMember("getA12");
        Assert.assertEquals("a12.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a12.a12=", 12L, a.getMember("a12_data").asLong());
        a.putMember("a12_data", 1000 + 12);
        Assert.assertEquals("a12.a12=", 12 + 1000, a.getMember("a12_data").asLong());
        Assert.assertEquals("a12.a3=", 3L, a.getMember("a3_data").asLong());
        a.putMember("a3_data", 1000 + 3);
        Assert.assertEquals("a12.a3=", 3 + 1000, a.getMember("a3_data").asLong());
        Assert.assertEquals("a12.a7=", 7L, a.getMember("a7_data").asLong());
        a.putMember("a7_data", 1000 + 7);
        Assert.assertEquals("a12.a7=", 7 + 1000, a.getMember("a7_data").asLong());
        Assert.assertEquals("a12.a11=", 11L, a.getMember("a11_data").asLong());
        a.putMember("a11_data", 1000 + 11);
        Assert.assertEquals("a12.a11=", 11 + 1000, a.getMember("a11_data").asLong());
        Assert.assertEquals("a12.a0=", 0L, a.getMember("a0_data").asLong());
        a.putMember("a0_data", 1000 + 0);
        Assert.assertEquals("a12.a0=", 0 + 1000, a.getMember("a0_data").asLong());
        Assert.assertEquals("a12.a1=", 1L, a.getMember("a1_data").asLong());
        a.putMember("a1_data", 1000 + 1);
        Assert.assertEquals("a12.a1=", 1 + 1000, a.getMember("a1_data").asLong());
        Assert.assertEquals("a12.a2=", 2L, a.getMember("a2_data").asLong());
        a.putMember("a2_data", 1000 + 2);
        Assert.assertEquals("a12.a2=", 2 + 1000, a.getMember("a2_data").asLong());
        Assert.assertEquals("a12.a4=", 4L, a.getMember("a4_data").asLong());
        a.putMember("a4_data", 1000 + 4);
        Assert.assertEquals("a12.a4=", 4 + 1000, a.getMember("a4_data").asLong());
        Assert.assertEquals("a12.a5=", 5L, a.getMember("a5_data").asLong());
        a.putMember("a5_data", 1000 + 5);
        Assert.assertEquals("a12.a5=", 5 + 1000, a.getMember("a5_data").asLong());
        Assert.assertEquals("a12.a6=", 6L, a.getMember("a6_data").asLong());
        a.putMember("a6_data", 1000 + 6);
        Assert.assertEquals("a12.a6=", 6 + 1000, a.getMember("a6_data").asLong());
        Assert.assertEquals("a12.a8=", 8L, a.getMember("a8_data").asLong());
        a.putMember("a8_data", 1000 + 8);
        Assert.assertEquals("a12.a8=", 8 + 1000, a.getMember("a8_data").asLong());
        Assert.assertEquals("a12.a9=", 9L, a.getMember("a9_data").asLong());
        a.putMember("a9_data", 1000 + 9);
        Assert.assertEquals("a12.a9=", 9 + 1000, a.getMember("a9_data").asLong());
        Assert.assertEquals("a12.a10=", 10L, a.getMember("a10_data").asLong());
        a.putMember("a10_data", 1000 + 10);
        Assert.assertEquals("a12.a10=", 10 + 1000, a.getMember("a10_data").asLong());

        a = cppLibrary.invokeMember("getA25");
        Assert.assertEquals("a25.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a25.a25=", 25L, a.getMember("a25_data").asLong());
        a.putMember("a25_data", 1000 + 25);
        Assert.assertEquals("a25.a25=", 25 + 1000, a.getMember("a25_data").asLong());
        Assert.assertEquals("a25.a16=", 16L, a.getMember("a16_data").asLong());
        a.putMember("a16_data", 1000 + 16);
        Assert.assertEquals("a25.a16=", 16 + 1000, a.getMember("a16_data").asLong());
        Assert.assertEquals("a25.a20=", 20L, a.getMember("a20_data").asLong());
        a.putMember("a20_data", 1000 + 20);
        Assert.assertEquals("a25.a20=", 20 + 1000, a.getMember("a20_data").asLong());
        Assert.assertEquals("a25.a24=", 24L, a.getMember("a24_data").asLong());
        a.putMember("a24_data", 1000 + 24);
        Assert.assertEquals("a25.a24=", 24 + 1000, a.getMember("a24_data").asLong());
        Assert.assertEquals("a25.a13=", 13L, a.getMember("a13_data").asLong());
        a.putMember("a13_data", 1000 + 13);
        Assert.assertEquals("a25.a13=", 13 + 1000, a.getMember("a13_data").asLong());
        Assert.assertEquals("a25.a14=", 14L, a.getMember("a14_data").asLong());
        a.putMember("a14_data", 1000 + 14);
        Assert.assertEquals("a25.a14=", 14 + 1000, a.getMember("a14_data").asLong());
        Assert.assertEquals("a25.a15=", 15L, a.getMember("a15_data").asLong());
        a.putMember("a15_data", 1000 + 15);
        Assert.assertEquals("a25.a15=", 15 + 1000, a.getMember("a15_data").asLong());
        Assert.assertEquals("a25.a17=", 17L, a.getMember("a17_data").asLong());
        a.putMember("a17_data", 1000 + 17);
        Assert.assertEquals("a25.a17=", 17 + 1000, a.getMember("a17_data").asLong());
        Assert.assertEquals("a25.a18=", 18L, a.getMember("a18_data").asLong());
        a.putMember("a18_data", 1000 + 18);
        Assert.assertEquals("a25.a18=", 18 + 1000, a.getMember("a18_data").asLong());
        Assert.assertEquals("a25.a19=", 19L, a.getMember("a19_data").asLong());
        a.putMember("a19_data", 1000 + 19);
        Assert.assertEquals("a25.a19=", 19 + 1000, a.getMember("a19_data").asLong());
        Assert.assertEquals("a25.a21=", 21L, a.getMember("a21_data").asLong());
        a.putMember("a21_data", 1000 + 21);
        Assert.assertEquals("a25.a21=", 21 + 1000, a.getMember("a21_data").asLong());
        Assert.assertEquals("a25.a22=", 22L, a.getMember("a22_data").asLong());
        a.putMember("a22_data", 1000 + 22);
        Assert.assertEquals("a25.a22=", 22 + 1000, a.getMember("a22_data").asLong());
        Assert.assertEquals("a25.a23=", 23L, a.getMember("a23_data").asLong());
        a.putMember("a23_data", 1000 + 23);
        Assert.assertEquals("a25.a23=", 23 + 1000, a.getMember("a23_data").asLong());

        a = cppLibrary.invokeMember("getA38");
        Assert.assertEquals("a38.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a38.a38=", 38L, a.getMember("a38_data").asLong());
        a.putMember("a38_data", 1000 + 38);
        Assert.assertEquals("a38.a38=", 38 + 1000, a.getMember("a38_data").asLong());
        Assert.assertEquals("a38.a29=", 29L, a.getMember("a29_data").asLong());
        a.putMember("a29_data", 1000 + 29);
        Assert.assertEquals("a38.a29=", 29 + 1000, a.getMember("a29_data").asLong());
        Assert.assertEquals("a38.a33=", 33L, a.getMember("a33_data").asLong());
        a.putMember("a33_data", 1000 + 33);
        Assert.assertEquals("a38.a33=", 33 + 1000, a.getMember("a33_data").asLong());
        Assert.assertEquals("a38.a37=", 37L, a.getMember("a37_data").asLong());
        a.putMember("a37_data", 1000 + 37);
        Assert.assertEquals("a38.a37=", 37 + 1000, a.getMember("a37_data").asLong());
        Assert.assertEquals("a38.a26=", 26L, a.getMember("a26_data").asLong());
        a.putMember("a26_data", 1000 + 26);
        Assert.assertEquals("a38.a26=", 26 + 1000, a.getMember("a26_data").asLong());
        Assert.assertEquals("a38.a27=", 27L, a.getMember("a27_data").asLong());
        a.putMember("a27_data", 1000 + 27);
        Assert.assertEquals("a38.a27=", 27 + 1000, a.getMember("a27_data").asLong());
        Assert.assertEquals("a38.a28=", 28L, a.getMember("a28_data").asLong());
        a.putMember("a28_data", 1000 + 28);
        Assert.assertEquals("a38.a28=", 28 + 1000, a.getMember("a28_data").asLong());
        Assert.assertEquals("a38.a30=", 30L, a.getMember("a30_data").asLong());
        a.putMember("a30_data", 1000 + 30);
        Assert.assertEquals("a38.a30=", 30 + 1000, a.getMember("a30_data").asLong());
        Assert.assertEquals("a38.a31=", 31L, a.getMember("a31_data").asLong());
        a.putMember("a31_data", 1000 + 31);
        Assert.assertEquals("a38.a31=", 31 + 1000, a.getMember("a31_data").asLong());
        Assert.assertEquals("a38.a32=", 32L, a.getMember("a32_data").asLong());
        a.putMember("a32_data", 1000 + 32);
        Assert.assertEquals("a38.a32=", 32 + 1000, a.getMember("a32_data").asLong());
        Assert.assertEquals("a38.a34=", 34L, a.getMember("a34_data").asLong());
        a.putMember("a34_data", 1000 + 34);
        Assert.assertEquals("a38.a34=", 34 + 1000, a.getMember("a34_data").asLong());
        Assert.assertEquals("a38.a35=", 35L, a.getMember("a35_data").asLong());
        a.putMember("a35_data", 1000 + 35);
        Assert.assertEquals("a38.a35=", 35 + 1000, a.getMember("a35_data").asLong());
        Assert.assertEquals("a38.a36=", 36L, a.getMember("a36_data").asLong());
        a.putMember("a36_data", 1000 + 36);
        Assert.assertEquals("a38.a36=", 36 + 1000, a.getMember("a36_data").asLong());

    }

    @Test
    public void testFieldAccessLevel4() {
        Value a = cppLibrary.invokeMember("getA39");
        Assert.assertEquals("a39.b0_data", 0L, a.getMember("b0_data").asLong());
        Assert.assertEquals("a39.a39=", 39L, a.getMember("a39_data").asLong());
        a.putMember("a39_data", 1000 + 39);
        Assert.assertEquals("a39.a39=", 39 + 1000, a.getMember("a39_data").asLong());
        Assert.assertEquals("a39.a12=", 12L, a.getMember("a12_data").asLong());
        a.putMember("a12_data", 1000 + 12);
        Assert.assertEquals("a39.a12=", 12 + 1000, a.getMember("a12_data").asLong());
        Assert.assertEquals("a39.a25=", 25L, a.getMember("a25_data").asLong());
        a.putMember("a25_data", 1000 + 25);
        Assert.assertEquals("a39.a25=", 25 + 1000, a.getMember("a25_data").asLong());
        Assert.assertEquals("a39.a38=", 38L, a.getMember("a38_data").asLong());
        a.putMember("a38_data", 1000 + 38);
        Assert.assertEquals("a39.a38=", 38 + 1000, a.getMember("a38_data").asLong());
        Assert.assertEquals("a39.a3=", 3L, a.getMember("a3_data").asLong());
        a.putMember("a3_data", 1000 + 3);
        Assert.assertEquals("a39.a3=", 3 + 1000, a.getMember("a3_data").asLong());
        Assert.assertEquals("a39.a7=", 7L, a.getMember("a7_data").asLong());
        a.putMember("a7_data", 1000 + 7);
        Assert.assertEquals("a39.a7=", 7 + 1000, a.getMember("a7_data").asLong());
        Assert.assertEquals("a39.a11=", 11L, a.getMember("a11_data").asLong());
        a.putMember("a11_data", 1000 + 11);
        Assert.assertEquals("a39.a11=", 11 + 1000, a.getMember("a11_data").asLong());
        Assert.assertEquals("a39.a16=", 16L, a.getMember("a16_data").asLong());
        a.putMember("a16_data", 1000 + 16);
        Assert.assertEquals("a39.a16=", 16 + 1000, a.getMember("a16_data").asLong());
        Assert.assertEquals("a39.a20=", 20L, a.getMember("a20_data").asLong());
        a.putMember("a20_data", 1000 + 20);
        Assert.assertEquals("a39.a20=", 20 + 1000, a.getMember("a20_data").asLong());
        Assert.assertEquals("a39.a24=", 24L, a.getMember("a24_data").asLong());
        a.putMember("a24_data", 1000 + 24);
        Assert.assertEquals("a39.a24=", 24 + 1000, a.getMember("a24_data").asLong());
        Assert.assertEquals("a39.a29=", 29L, a.getMember("a29_data").asLong());
        a.putMember("a29_data", 1000 + 29);
        Assert.assertEquals("a39.a29=", 29 + 1000, a.getMember("a29_data").asLong());
        Assert.assertEquals("a39.a33=", 33L, a.getMember("a33_data").asLong());
        a.putMember("a33_data", 1000 + 33);
        Assert.assertEquals("a39.a33=", 33 + 1000, a.getMember("a33_data").asLong());
        Assert.assertEquals("a39.a37=", 37L, a.getMember("a37_data").asLong());
        a.putMember("a37_data", 1000 + 37);
        Assert.assertEquals("a39.a37=", 37 + 1000, a.getMember("a37_data").asLong());
        Assert.assertEquals("a39.a0=", 0L, a.getMember("a0_data").asLong());
        a.putMember("a0_data", 1000 + 0);
        Assert.assertEquals("a39.a0=", 0 + 1000, a.getMember("a0_data").asLong());
        Assert.assertEquals("a39.a1=", 1L, a.getMember("a1_data").asLong());
        a.putMember("a1_data", 1000 + 1);
        Assert.assertEquals("a39.a1=", 1 + 1000, a.getMember("a1_data").asLong());
        Assert.assertEquals("a39.a2=", 2L, a.getMember("a2_data").asLong());
        a.putMember("a2_data", 1000 + 2);
        Assert.assertEquals("a39.a2=", 2 + 1000, a.getMember("a2_data").asLong());
        Assert.assertEquals("a39.a4=", 4L, a.getMember("a4_data").asLong());
        a.putMember("a4_data", 1000 + 4);
        Assert.assertEquals("a39.a4=", 4 + 1000, a.getMember("a4_data").asLong());
        Assert.assertEquals("a39.a5=", 5L, a.getMember("a5_data").asLong());
        a.putMember("a5_data", 1000 + 5);
        Assert.assertEquals("a39.a5=", 5 + 1000, a.getMember("a5_data").asLong());
        Assert.assertEquals("a39.a6=", 6L, a.getMember("a6_data").asLong());
        a.putMember("a6_data", 1000 + 6);
        Assert.assertEquals("a39.a6=", 6 + 1000, a.getMember("a6_data").asLong());
        Assert.assertEquals("a39.a8=", 8L, a.getMember("a8_data").asLong());
        a.putMember("a8_data", 1000 + 8);
        Assert.assertEquals("a39.a8=", 8 + 1000, a.getMember("a8_data").asLong());
        Assert.assertEquals("a39.a9=", 9L, a.getMember("a9_data").asLong());
        a.putMember("a9_data", 1000 + 9);
        Assert.assertEquals("a39.a9=", 9 + 1000, a.getMember("a9_data").asLong());
        Assert.assertEquals("a39.a10=", 10L, a.getMember("a10_data").asLong());
        a.putMember("a10_data", 1000 + 10);
        Assert.assertEquals("a39.a10=", 10 + 1000, a.getMember("a10_data").asLong());
        Assert.assertEquals("a39.a13=", 13L, a.getMember("a13_data").asLong());
        a.putMember("a13_data", 1000 + 13);
        Assert.assertEquals("a39.a13=", 13 + 1000, a.getMember("a13_data").asLong());
        Assert.assertEquals("a39.a14=", 14L, a.getMember("a14_data").asLong());
        a.putMember("a14_data", 1000 + 14);
        Assert.assertEquals("a39.a14=", 14 + 1000, a.getMember("a14_data").asLong());
        Assert.assertEquals("a39.a15=", 15L, a.getMember("a15_data").asLong());
        a.putMember("a15_data", 1000 + 15);
        Assert.assertEquals("a39.a15=", 15 + 1000, a.getMember("a15_data").asLong());
        Assert.assertEquals("a39.a17=", 17L, a.getMember("a17_data").asLong());
        a.putMember("a17_data", 1000 + 17);
        Assert.assertEquals("a39.a17=", 17 + 1000, a.getMember("a17_data").asLong());
        Assert.assertEquals("a39.a18=", 18L, a.getMember("a18_data").asLong());
        a.putMember("a18_data", 1000 + 18);
        Assert.assertEquals("a39.a18=", 18 + 1000, a.getMember("a18_data").asLong());
        Assert.assertEquals("a39.a19=", 19L, a.getMember("a19_data").asLong());
        a.putMember("a19_data", 1000 + 19);
        Assert.assertEquals("a39.a19=", 19 + 1000, a.getMember("a19_data").asLong());
        Assert.assertEquals("a39.a21=", 21L, a.getMember("a21_data").asLong());
        a.putMember("a21_data", 1000 + 21);
        Assert.assertEquals("a39.a21=", 21 + 1000, a.getMember("a21_data").asLong());
        Assert.assertEquals("a39.a22=", 22L, a.getMember("a22_data").asLong());
        a.putMember("a22_data", 1000 + 22);
        Assert.assertEquals("a39.a22=", 22 + 1000, a.getMember("a22_data").asLong());
        Assert.assertEquals("a39.a23=", 23L, a.getMember("a23_data").asLong());
        a.putMember("a23_data", 1000 + 23);
        Assert.assertEquals("a39.a23=", 23 + 1000, a.getMember("a23_data").asLong());
        Assert.assertEquals("a39.a26=", 26L, a.getMember("a26_data").asLong());
        a.putMember("a26_data", 1000 + 26);
        Assert.assertEquals("a39.a26=", 26 + 1000, a.getMember("a26_data").asLong());
        Assert.assertEquals("a39.a27=", 27L, a.getMember("a27_data").asLong());
        a.putMember("a27_data", 1000 + 27);
        Assert.assertEquals("a39.a27=", 27 + 1000, a.getMember("a27_data").asLong());
        Assert.assertEquals("a39.a28=", 28L, a.getMember("a28_data").asLong());
        a.putMember("a28_data", 1000 + 28);
        Assert.assertEquals("a39.a28=", 28 + 1000, a.getMember("a28_data").asLong());
        Assert.assertEquals("a39.a30=", 30L, a.getMember("a30_data").asLong());
        a.putMember("a30_data", 1000 + 30);
        Assert.assertEquals("a39.a30=", 30 + 1000, a.getMember("a30_data").asLong());
        Assert.assertEquals("a39.a31=", 31L, a.getMember("a31_data").asLong());
        a.putMember("a31_data", 1000 + 31);
        Assert.assertEquals("a39.a31=", 31 + 1000, a.getMember("a31_data").asLong());
        Assert.assertEquals("a39.a32=", 32L, a.getMember("a32_data").asLong());
        a.putMember("a32_data", 1000 + 32);
        Assert.assertEquals("a39.a32=", 32 + 1000, a.getMember("a32_data").asLong());
        Assert.assertEquals("a39.a34=", 34L, a.getMember("a34_data").asLong());
        a.putMember("a34_data", 1000 + 34);
        Assert.assertEquals("a39.a34=", 34 + 1000, a.getMember("a34_data").asLong());
        Assert.assertEquals("a39.a35=", 35L, a.getMember("a35_data").asLong());
        a.putMember("a35_data", 1000 + 35);
        Assert.assertEquals("a39.a35=", 35 + 1000, a.getMember("a35_data").asLong());
        Assert.assertEquals("a39.a36=", 36L, a.getMember("a36_data").asLong());
        a.putMember("a36_data", 1000 + 36);
        Assert.assertEquals("a39.a36=", 36 + 1000, a.getMember("a36_data").asLong());

    }

    @GenerateUncached
    abstract static class GetMemberNode extends Node {
        abstract Object execute(Object a);

        @Specialization(limit = "3")
        Object doRead(Object a,
                        @CachedLibrary("a") InteropLibrary interop) {
            try {
                return interop.readMember(a, CxxVirtualInheritanceFieldTest.fieldNameToAccess);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(ex);
            }
        }
    }

    public static class TestCallNode extends RootNode {
        @Child GetMemberNode getMember = GetMemberNodeGen.create();

        public TestCallNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return getMember.execute(frame.getArguments()[0]);
        }
    }

    @Test
    public void testInternal(@Inject(TestCallNode.class) CallTarget call) throws InteropException {
        Object a = InteropLibrary.getUncached().invokeMember(cppLibraryInternal, "getA39");
        for (int i = 0; i < 40; i++) {
            CxxVirtualInheritanceFieldTest.fieldNameToAccess = "a" + i + "_data";
            Object ret = call.call(a);
            Assert.assertEquals(i, InteropLibrary.getUncached().asInt(ret));
        }

    }

    @Test
    public void testInternalA3(@Inject(TestCallNode.class) CallTarget call) throws InteropException {
        Object a = InteropLibrary.getUncached().invokeMember(cppLibraryInternal, "getA3");
        for (int i = 0; i < 4; i++) {
            CxxVirtualInheritanceFieldTest.fieldNameToAccess = "a" + i + "_data";
            Object ret = call.call(a);
            Assert.assertEquals(i, InteropLibrary.getUncached().asInt(ret));
        }

    }
}
