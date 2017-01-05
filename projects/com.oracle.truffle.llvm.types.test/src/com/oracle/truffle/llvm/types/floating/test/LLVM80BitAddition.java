/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.types.floating.test;

import static org.junit.Assert.assertEquals;

import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;

@Ignore("do not support addition yet: fix other floating point bugs first")
public class LLVM80BitAddition extends LLVM80BitTest {

    @Test
    public void testZero() {
        assertEquals(0, zero().add(zero()).getIntValue());
    }

    @Test
    public void testMinusPlusOne() {
        assertEquals(0, val(-1).add(val(1)).getIntValue());
    }

    @Test
    public void testPlusMinusPositiveResult1() {
        assertEquals(1, val(-4).add(val(5)).getIntValue());
    }

    @Test
    public void testPlusMinusZeroResult1() {
        assertEquals(0, val(-4).add(val(4)).getIntValue());
    }

    @Test
    public void testPlusMinusNegativeResult1() {
        assertEquals(-1, val(-6).add(val(5)).getIntValue());
    }

    @Test
    public void testPlusMinusPositiveResult2() {
        assertEquals(1, val(5).add(val(-4)).getIntValue());
    }

    @Test
    public void testPlusMinusZeroResult2() {
        assertEquals(0, val(5).add(val(-5)).getIntValue());
    }

    @Test
    public void testPlusMinusNegativeResult2() {
        assertEquals(-1, val(5).add(val(-6)).getIntValue());
    }

    @Test
    public void test1() {
        LLVM80BitFloat result = val(4).add(val(9));
        assertEquals(13, result.getIntValue());
    }

    @Test
    public void testInfinity() {
        assertEquals(positiveInfinity(), positiveInfinity().add(positiveInfinity()).getIntValue());
    }

}
