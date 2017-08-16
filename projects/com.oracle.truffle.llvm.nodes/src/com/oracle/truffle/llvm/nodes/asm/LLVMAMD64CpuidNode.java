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
package com.oracle.truffle.llvm.nodes.asm;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI32RegisterNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild("level")
public abstract class LLVMAMD64CpuidNode extends LLVMExpressionNode {
    public static final String BRAND = "Sulong"; // at most 48 characters
    public static final String VENDOR_ID = "SulongLLVM64"; // exactly 12 characters

    public static final int[] BRAND_I32 = getI32(BRAND, 12);
    public static final int[] VENDOR_ID_I32 = getI32(VENDOR_ID, 3);

    private static int[] getI32(String s, int len) {
        CompilerAsserts.neverPartOfCompilation();
        int[] i32 = new int[len];
        for (int i = 0; i < len; i++) {
            byte b1 = getI8(s, i * 4);
            byte b2 = getI8(s, i * 4 + 1);
            byte b3 = getI8(s, i * 4 + 2);
            byte b4 = getI8(s, i * 4 + 3);
            i32[i] = Byte.toUnsignedInt(b1) | Byte.toUnsignedInt(b2) << 8 | Byte.toUnsignedInt(b3) << 16 | Byte.toUnsignedInt(b4) << 24;
        }
        return i32;
    }

    private static byte getI8(String s, int offset) {
        CompilerAsserts.neverPartOfCompilation();
        if (offset >= s.length()) {
            return 0;
        } else {
            return (byte) s.charAt(offset);
        }
    }

    @Child private LLVMAMD64WriteI32RegisterNode eax;
    @Child private LLVMAMD64WriteI32RegisterNode ebx;
    @Child private LLVMAMD64WriteI32RegisterNode ecx;
    @Child private LLVMAMD64WriteI32RegisterNode edx;

    // FN=1: EDX
    public static final int TSC = 1 << 4;
    // FN=1: ECX
    public static final int RDRND = 1 << 30;
    // FN=7/0: EBX
    public static final int RDSEED = 1 << 18;
    // FN=80000001h: EDX
    public static final int LM = (1 << 29);
    // FN=80000001h: ECX
    public static final int LAHF_LM = 1;

    public LLVMAMD64CpuidNode(LLVMAMD64WriteI32RegisterNode eax, LLVMAMD64WriteI32RegisterNode ebx, LLVMAMD64WriteI32RegisterNode ecx, LLVMAMD64WriteI32RegisterNode edx) {
        this.eax = eax;
        this.ebx = ebx;
        this.ecx = ecx;
        this.edx = edx;
    }

    @Specialization
    public Object execute(VirtualFrame frame, int level) {
        int a;
        int b;
        int c;
        int d;
        switch (level) {
            case 0:
                // Get Vendor ID/Highest Function Parameter
                a = 7; // max supported function
                b = VENDOR_ID_I32[0];
                d = VENDOR_ID_I32[1];
                c = VENDOR_ID_I32[2];
                break;
            case 1:
                // Processor Info and Feature Bits
                // EAX:
                // 3:0 - Stepping
                // 7:4 - Model
                // 11:8 - Family
                // 13:12 - Processor Type
                // 19:16 - Extended Model
                // 27:20 - Extended Family
                a = 0;
                b = 0;
                c = RDRND;
                d = TSC;
                break;
            case 7:
                // Extended Features (FIXME: assumption is ECX=0)
                a = 0;
                b = RDSEED;
                c = 0;
                d = 0;
                break;
            case 0x80000000:
                // Get Highest Extended Function Supported
                a = 0x80000004;
                b = 0;
                c = 0;
                d = 0;
                break;
            case 0x80000001:
                // Extended Processor Info and Feature Bits
                a = 0;
                b = 0;
                c = LAHF_LM;
                d = LM;
                break;
            case 0x80000002:
                // Processor Brand String
                a = BRAND_I32[0];
                b = BRAND_I32[1];
                c = BRAND_I32[2];
                d = BRAND_I32[3];
                break;
            case 0x80000003:
                // Processor Brand String
                a = BRAND_I32[4];
                b = BRAND_I32[5];
                c = BRAND_I32[6];
                d = BRAND_I32[7];
                break;
            case 0x80000004:
                // Processor Brand String
                a = BRAND_I32[8];
                b = BRAND_I32[9];
                c = BRAND_I32[10];
                d = BRAND_I32[11];
                break;
            default:
                // Fallback: bits cleared = feature(s) not available
                a = 0;
                b = 0;
                c = 0;
                d = 0;
        }
        eax.execute(frame, a);
        ebx.execute(frame, b);
        ecx.execute(frame, c);
        edx.execute(frame, d);
        return null;
    }
}
