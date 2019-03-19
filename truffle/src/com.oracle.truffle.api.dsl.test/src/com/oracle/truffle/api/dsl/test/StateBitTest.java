/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.test.StateBitTestFactory.Test16BitsNodeGen;
import com.oracle.truffle.api.dsl.test.StateBitTestFactory.Test32BitsNodeGen;
import com.oracle.truffle.api.dsl.test.StateBitTestFactory.Test64BitsNodeGen;
import com.oracle.truffle.api.nodes.Node;

public class StateBitTest {

    abstract static class Test16Bits extends Node {
        abstract Object execute(Object arg);

        @Specialization(guards = "arg == 0")
        int s0(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 1")
        int s1(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 2")
        int s2(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 3")
        int s3(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 4")
        int s4(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 5")
        int s5(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 6")
        int s6(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 7")
        int s7(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 8")
        int s8(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 9")
        int s9(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 10")
        int s10(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 11")
        int s11(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 12")
        int s12(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 13")
        int s13(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 14")
        int s14(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 15")
        int s15(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }
    }

    abstract static class Test32Bits extends Test16Bits {

        @Specialization(guards = "arg == 16")
        int s16(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 17")
        int s17(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 18")
        int s18(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 19")
        int s19(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 20")
        int s20(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 21")
        int s21(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 22")
        int s22(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 23")
        int s23(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 24")
        int s24(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 25")
        int s25(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 26")
        int s26(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 27")
        int s27(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 28")
        int s28(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 29")
        int s29(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 30")
        int s30(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 31")
        int s31(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

    }

    abstract static class Test64Bits extends Test32Bits {

        @Specialization(guards = "arg == 32")
        int s32(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 33")
        int s33(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 34")
        int s34(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 35")
        int s35(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 36")
        int s36(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 37")
        int s37(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 38")
        int s38(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 39")
        int s39(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 40")
        int s40(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 41")
        int s41(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 42")
        int s42(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 43")
        int s43(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 44")
        int s44(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 45")
        int s45(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 46")
        int s46(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 47")
        int s47(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 48")
        int s48(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 49")
        int s49(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 50")
        int s50(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 51")
        int s51(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 52")
        int s52(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 53")
        int s53(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 54")
        int s54(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 55")
        int s55(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 56")
        int s56(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 57")
        int s57(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 58")
        int s58(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 59")
        int s59(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 60")
        int s60(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 61")
        int s61(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 62")
        int s62(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

        @Specialization(guards = "arg == 63")
        int s63(int arg, @Exclusive @Cached("arg") int cachedArg) {
            assertEquals(arg, cachedArg);
            return arg;
        }

    }

    @Test
    public void test16() throws NoSuchFieldException, SecurityException {
        Test16Bits node = Test16BitsNodeGen.create();
        for (int i = 0; i < 16; i++) {
            node.execute(i);
        }
        assertEquals(int.class, node.getClass().getDeclaredField("state_").getType());
    }

    @Test
    public void test32() throws NoSuchFieldException, SecurityException {
        Test32Bits node = Test32BitsNodeGen.create();
        for (int i = 0; i < 32; i++) {
            node.execute(i);
        }
        assertEquals(int.class, node.getClass().getDeclaredField("state_").getType());
    }

    @Test
    public void test64() throws NoSuchFieldException, SecurityException {
        Test64Bits node = Test64BitsNodeGen.create();
        for (int i = 0; i < 64; i++) {
            node.execute(i);
        }
        assertEquals(long.class, node.getClass().getDeclaredField("state_").getType());
    }

}
