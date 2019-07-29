/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.types.floating;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;

public class LLVM80BitCompareTest extends LLVM80BitTest {

    @Test
    public void testEquals() {
        assertTrue(LLVM80BitFloat.compare(zero(), zero()) == 0);
        assertTrue(LLVM80BitFloat.compare(one(), one()) == 0);
        assertTrue(LLVM80BitFloat.compare(minusOne(), minusOne()) == 0);
    }

    @Test
    public void testOrdered() {
        assertTrue(zero().isOrdered());
        assertTrue(one().isOrdered());
        assertTrue(minusOne().isOrdered());
        assertTrue(positiveInfinity().isOrdered());
        assertTrue(negativeInfinity().isOrdered());
        assertFalse(nan().isOrdered());
    }

    @Test
    public void testInfinities() {
        assertTrue(LLVM80BitFloat.compare(positiveInfinity(), positiveInfinity()) == 0);
        assertTrue(LLVM80BitFloat.compare(negativeInfinity(), positiveInfinity()) < 0);
        assertTrue(LLVM80BitFloat.compare(positiveInfinity(), negativeInfinity()) > 0);
        assertTrue(LLVM80BitFloat.compare(negativeInfinity(), negativeInfinity()) == 0);
    }

    @Test
    public void testPositiveInfinitiesNumber1() {
        assertTrue(LLVM80BitFloat.compare(positiveInfinity(), one()) > 0);
        assertTrue(LLVM80BitFloat.compare(positiveInfinity(), zero()) > 0);
        assertTrue(LLVM80BitFloat.compare(positiveInfinity(), minusOne()) > 0);
        assertTrue(LLVM80BitFloat.compare(positiveInfinity(), val(Long.MAX_VALUE)) > 0);
    }

    @Test
    public void testPositiveInfinitiesNumber2() {
        assertTrue(LLVM80BitFloat.compare(one(), positiveInfinity()) < 0);
        assertTrue(LLVM80BitFloat.compare(zero(), positiveInfinity()) < 0);
        assertTrue(LLVM80BitFloat.compare(minusOne(), positiveInfinity()) < 0);
        assertTrue(LLVM80BitFloat.compare(val(Long.MAX_VALUE), positiveInfinity()) < 0);
    }

    @Test
    public void testNegativeInfinitiesNumber1() {
        assertTrue(LLVM80BitFloat.compare(negativeInfinity(), one()) < 0);
        assertTrue(LLVM80BitFloat.compare(negativeInfinity(), zero()) < 0);
        assertTrue(LLVM80BitFloat.compare(negativeInfinity(), minusOne()) < 0);
        assertTrue(LLVM80BitFloat.compare(negativeInfinity(), val(Long.MIN_VALUE)) < 0);
    }

    @Test
    public void testNegativeInfinitiesNumber2() {
        assertTrue(LLVM80BitFloat.compare(one(), negativeInfinity()) > 0);
        assertTrue(LLVM80BitFloat.compare(zero(), negativeInfinity()) > 0);
        assertTrue(LLVM80BitFloat.compare(minusOne(), negativeInfinity()) > 0);
        assertTrue(LLVM80BitFloat.compare(val(Long.MIN_VALUE), negativeInfinity()) > 0);
    }
}
