/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.internal.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.llvm.runtime.LLVMIVarBitSmall;

public class LLVMIVarBitSmallTest {
    @Test
    public void unpackUnsignedLong() {
        LLVMIVarBitSmall v = new LLVMIVarBitSmall(40, 0x00FF_FFFF_FF00L);
        assertEquals(0x00FF_FFFF_FF00L, v.getZeroExtendedLongValue());
    }

    @Test
    public void unpackSignedLong() {
        LLVMIVarBitSmall v = new LLVMIVarBitSmall(40, 0x00FF_FFFF_FF00L);

        assertTrue("v.getLongValue() is negative", v.getLongValue() < 0);
        assertEquals(0xFFFF_FFFF_FFFF_FF00L, v.getLongValue());
    }

    @Test
    public void arithMeticShiftRight() {
        LLVMIVarBitSmall v = new LLVMIVarBitSmall(40, 0x00FF_FFFF_FC00L).arithmeticRightShift(new LLVMIVarBitSmall(32, 10));
        assertEquals(-1L, v.getLongValue());
    }
}
