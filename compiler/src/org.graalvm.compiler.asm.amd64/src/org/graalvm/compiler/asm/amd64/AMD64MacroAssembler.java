/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.asm.amd64;

import static jdk.vm.ci.amd64.AMD64.k1;
import static jdk.vm.ci.amd64.AMD64.k2;
import static jdk.vm.ci.amd64.AMD64.k3;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsp;

import static org.graalvm.compiler.asm.amd64.AMD64AsmOptions.UseIncDec;
import static org.graalvm.compiler.asm.amd64.AMD64AsmOptions.UseXmmLoadAndClearUpper;
import static org.graalvm.compiler.asm.amd64.AMD64AsmOptions.UseXmmRegToRegMoveAll;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.core.common.NumUtil;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

/**
 * This class implements commonly used X86 code patterns.
 */
public class AMD64MacroAssembler extends AMD64Assembler {

    public AMD64MacroAssembler(TargetDescription target) {
        super(target);
    }

    public final void decrementq(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            subq(reg, value);
            return;
        }
        if (value < 0) {
            incrementq(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decq(reg);
        } else {
            subq(reg, value);
        }
    }

    public final void decrementq(AMD64Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            subq(dst, value);
            return;
        }
        if (value < 0) {
            incrementq(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decq(dst);
        } else {
            subq(dst, value);
        }
    }

    public void incrementq(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            addq(reg, value);
            return;
        }
        if (value < 0) {
            decrementq(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incq(reg);
        } else {
            addq(reg, value);
        }
    }

    public final void incrementq(AMD64Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            addq(dst, value);
            return;
        }
        if (value < 0) {
            decrementq(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incq(dst);
        } else {
            addq(dst, value);
        }
    }

    public final void movptr(Register dst, AMD64Address src) {
        movq(dst, src);
    }

    public final void movptr(AMD64Address dst, Register src) {
        movq(dst, src);
    }

    public final void movptr(AMD64Address dst, int src) {
        movslq(dst, src);
    }

    public final void cmpptr(Register src1, Register src2) {
        cmpq(src1, src2);
    }

    public final void cmpptr(Register src1, AMD64Address src2) {
        cmpq(src1, src2);
    }

    public final void decrementl(Register reg) {
        decrementl(reg, 1);
    }

    public final void decrementl(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            subl(reg, value);
            return;
        }
        if (value < 0) {
            incrementl(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decl(reg);
        } else {
            subl(reg, value);
        }
    }

    public final void decrementl(AMD64Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            subl(dst, value);
            return;
        }
        if (value < 0) {
            incrementl(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decl(dst);
        } else {
            subl(dst, value);
        }
    }

    public final void incrementl(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            addl(reg, value);
            return;
        }
        if (value < 0) {
            decrementl(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incl(reg);
        } else {
            addl(reg, value);
        }
    }

    public final void incrementl(AMD64Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            addl(dst, value);
            return;
        }
        if (value < 0) {
            decrementl(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incl(dst);
        } else {
            addl(dst, value);
        }
    }

    public void movflt(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        if (UseXmmRegToRegMoveAll) {
            movaps(dst, src);
        } else {
            movss(dst, src);
        }
    }

    public void movflt(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        movss(dst, src);
    }

    public void movflt(AMD64Address dst, Register src) {
        assert src.getRegisterCategory().equals(AMD64.XMM);
        movss(dst, src);
    }

    public void movdbl(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        if (UseXmmRegToRegMoveAll) {
            movapd(dst, src);
        } else {
            movsd(dst, src);
        }
    }

    public void movdbl(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        if (UseXmmLoadAndClearUpper) {
            movsd(dst, src);
        } else {
            movlpd(dst, src);
        }
    }

    public void movdbl(AMD64Address dst, Register src) {
        assert src.getRegisterCategory().equals(AMD64.XMM);
        movsd(dst, src);
    }

    /**
     * Non-atomic write of a 64-bit constant to memory. Do not use if the address might be a
     * volatile field!
     */
    public final void movlong(AMD64Address dst, long src) {
        if (NumUtil.isInt(src)) {
            AMD64MIOp.MOV.emit(this, OperandSize.QWORD, dst, (int) src);
        } else {
            AMD64Address high = new AMD64Address(dst.getBase(), dst.getIndex(), dst.getScale(), dst.getDisplacement() + 4);
            movl(dst, (int) (src & 0xFFFFFFFF));
            movl(high, (int) (src >> 32));
        }
    }

    public final void setl(ConditionFlag cc, Register dst) {
        setb(cc, dst);
        movzbl(dst, dst);
    }

    public final void setq(ConditionFlag cc, Register dst) {
        setb(cc, dst);
        movzbq(dst, dst);
    }

    public final void flog(Register dest, Register value, boolean base10) {
        if (base10) {
            fldlg2();
        } else {
            fldln2();
        }
        AMD64Address tmp = trigPrologue(value);
        fyl2x();
        trigEpilogue(dest, tmp);
    }

    public final void fsin(Register dest, Register value) {
        AMD64Address tmp = trigPrologue(value);
        fsin();
        trigEpilogue(dest, tmp);
    }

    public final void fcos(Register dest, Register value) {
        AMD64Address tmp = trigPrologue(value);
        fcos();
        trigEpilogue(dest, tmp);
    }

    public final void ftan(Register dest, Register value) {
        AMD64Address tmp = trigPrologue(value);
        fptan();
        fstp(0); // ftan pushes 1.0 in addition to the actual result, pop
        trigEpilogue(dest, tmp);
    }

    public final void fpop() {
        ffree(0);
        fincstp();
    }

    private AMD64Address trigPrologue(Register value) {
        assert value.getRegisterCategory().equals(AMD64.XMM);
        AMD64Address tmp = new AMD64Address(AMD64.rsp);
        subq(AMD64.rsp, AMD64Kind.DOUBLE.getSizeInBytes());
        movdbl(tmp, value);
        fldd(tmp);
        return tmp;
    }

    private void trigEpilogue(Register dest, AMD64Address tmp) {
        assert dest.getRegisterCategory().equals(AMD64.XMM);
        fstpd(tmp);
        movdbl(dest, tmp);
        addq(AMD64.rsp, AMD64Kind.DOUBLE.getSizeInBytes());
    }

    // @formatter:off
    // Allow typical assembly language label name scheme (L_<name>).
    // Checkstyle: stop

    /* Compress a UTF16 string which de facto is a Latin1 string into a byte array
     * representation (buffer).
     *
     *   src   (rsi) the start address of source char[] to be compressed
     *   dst   (rdi) the start address of destination byte[] vector
     *   len   (rdx) the length
     *   vtmp1 (xmm)
     *   vtmp2 (xmm)
     *   vtmp3 (xmm)
     *   vtmp4 (xmm)
     *   tmp   (gpr)
     *   res   (rax) the result code (length on success, zero otherwise)
     */
    public void charArrayCompress(Register src,   Register dst,   Register len,
                                  Register vtmp1, Register vtmp2,
                                  Register vtmp3, Register vtmp4,
                                  Register tmp,   Register res) {

        assert vtmp1.getRegisterCategory().equals(AMD64.XMM);
        assert vtmp2.getRegisterCategory().equals(AMD64.XMM);
        assert vtmp3.getRegisterCategory().equals(AMD64.XMM);
        assert vtmp4.getRegisterCategory().equals(AMD64.XMM);

        Label L_return_length = new Label();
        Label L_return_zero = new Label();
        Label L_done = new Label();
        Label L_below_threshold = new Label();

        assert len.number != res.number;

        push(len);      // Save length for return.

        if (supports(CPUFeature.AVX512BW) &&
            supports(CPUFeature.AVX512VL) &&
            supports(CPUFeature.BMI2)) {

            Label L_restore_k1_return_zero = new Label();
            Label L_avx_post_alignment = new Label();

            // If the length of the string is less than 32, we chose not to use the
            // AVX512 instructions.
            testl(len, -32);
            jcc(ConditionFlag.Zero, L_below_threshold);

            // First check whether a character is compressible (<= 0xff).
            // Create mask to test for Unicode chars inside (zmm) vector.
            movl(res, 0x00ff);
            evpbroadcastw(vtmp2, res);

            kmovq(k3, k1);      // Save k1

            testl(len, -64);
            jcc(ConditionFlag.Zero, L_avx_post_alignment);

            movl(tmp, dst);
            andl(tmp, (32 - 1));
            negl(tmp);
            andl(tmp, (32 - 1));

            // bail out when there is nothing to be done
            testl(tmp, tmp);
            jcc(ConditionFlag.Zero, L_avx_post_alignment);

            // Compute (1 << N) - 1 = ~(~0 << N), where N is the remaining number
            // of characters to process.
            movl(res, -1);
            shlxl(res, res, tmp);
            notl(res);

            kmovd(k1, res);
            evmovdqu16(vtmp1, k1, new AMD64Address(src));
            evpcmpuw(k2, k1, vtmp1, vtmp2, 2 /*le*/);
            ktestd(k2, k1);
            jcc(ConditionFlag.CarryClear, L_restore_k1_return_zero);

            evpmovwb(new AMD64Address(dst), k1, vtmp1);

            addq(src, tmp);
            addq(src, tmp);
            addq(dst, tmp);
            subl(len, tmp);

            bind(L_avx_post_alignment);
            // end of alignment
            Label L_avx512_loop_tail = new Label();

            movl(tmp, len);
            andl(tmp, -32);         // The vector count (in chars).
            jcc(ConditionFlag.Zero, L_avx512_loop_tail);
            andl(len, 32 - 1);      // The tail count (in chars).

            leaq(src, new AMD64Address(src, tmp, Scale.Times2));
            leaq(dst, new AMD64Address(dst, tmp, Scale.Times1));
            negq(tmp);

            Label L_avx512_loop = new Label();
            // Test and compress 32 chars per iteration, reading 512-bit vectors and
            // writing 256-bit compressed ditto.
            bind(L_avx512_loop);
            evmovdqu16(vtmp1, new AMD64Address(src, tmp, Scale.Times2));
            evpcmpuw(k2, vtmp1, vtmp2, 2 /*le*/);
            kortestd(k2, k2);
            jcc(ConditionFlag.CarryClear, L_restore_k1_return_zero);

            // All 32 chars in the current vector (chunk) are valid for compression,
            // write truncated byte elements to memory.
            evpmovwb(new AMD64Address(dst, tmp, Scale.Times1), vtmp1);
            addq(tmp, 32);
            jcc(ConditionFlag.NotZero, L_avx512_loop);

            bind(L_avx512_loop_tail);
            kmovq(k1, k3);      // Restore k1

            // All done if the tail count is zero.
            testl(len, len);
            jcc(ConditionFlag.Zero, L_return_length);

            // Compute (1 << N) - 1 = ~(~0 << N), where N is the remaining number
            // of characters to process.
            movl(res, -1);
            shlxl(res, res, len);
            notl(res);

            kmovd(k1, res);
            evmovdqu16(vtmp1, k1, new AMD64Address(src));
            evpcmpuw(k2, k1, vtmp1, vtmp2, 2 /*le*/);
            ktestd(k2, k1);
            jcc(ConditionFlag.CarryClear, L_restore_k1_return_zero);

            evpmovwb(new AMD64Address(dst), k1, vtmp1);

            kmovq(k1, k3);      // Restore k1
            jmp(L_return_length);

            bind(L_restore_k1_return_zero);
            kmovq(k1, k3);      // Restore k1
            jmp(L_return_zero);
        }

        if (supports(CPUFeature.SSE4_2)) {

            Label L_sse_tail = new Label();

            bind(L_below_threshold);

            movl(tmp, 0xff00ff00);  // Create mask to test for Unicode chars in vectors.

            movl(res, len);
            andl(res, -16);
            jccb(ConditionFlag.Zero, L_sse_tail);
            andl(len, 16 - 1);

            // Compress 16 chars per iteration.
            movdl(vtmp1, tmp);
            pshufd(vtmp1, vtmp1, 0);    // Store Unicode mask in 'vtmp1'.
            pxor(vtmp4, vtmp4);

            leaq(src, new AMD64Address(src, res, Scale.Times2));
            leaq(dst, new AMD64Address(dst, res, Scale.Times1));
            negq(res);

            Label L_sse_loop = new Label();
            // Test and compress 16 chars per iteration, reading 128-bit vectors and
            // writing 64-bit compressed ditto.
            bind(L_sse_loop);
            movdqu(vtmp2, new AMD64Address(src, res, Scale.Times2));        // load 1st 8 characters
            movdqu(vtmp3, new AMD64Address(src, res, Scale.Times2, 16));    // load next 8 characters
            por(vtmp4, vtmp2);
            por(vtmp4, vtmp3);
            ptest(vtmp4, vtmp1);        // Check for Unicode chars in vector.
            jcc(ConditionFlag.NotZero, L_return_zero);

            packuswb(vtmp2, vtmp3);     // Only ASCII chars; compress each to a byte.
            movdqu(new AMD64Address(dst, res, Scale.Times1), vtmp2);
            addq(res, 16);
            jcc(ConditionFlag.NotZero, L_sse_loop);

            Label L_copy_chars = new Label();
            // Test and compress another 8 chars before final tail copy.
            bind(L_sse_tail);
            movl(res, len);
            andl(res, -8);
            jccb(ConditionFlag.Zero, L_copy_chars);
            andl(len, 8 - 1);

            movdl(vtmp1, tmp);
            pshufd(vtmp1, vtmp1, 0);    // Store Unicode mask in 'vtmp1'.
            pxor(vtmp3, vtmp3);

            movdqu(vtmp2, new AMD64Address(src));
            ptest(vtmp2, vtmp1);        // Check for Unicode chars in vector.
            jccb(ConditionFlag.NotZero, L_return_zero);
            packuswb(vtmp2, vtmp3);     // Only ASCII chars; compress each to a byte.
            movq(new AMD64Address(dst), vtmp2);
            addq(src, 16);
            addq(dst, 8);

            bind(L_copy_chars);
        }

        // Compress any remaining characters using a vanilla implementation.
        testl(len, len);
        jccb(ConditionFlag.Zero, L_return_length);
        leaq(src, new AMD64Address(src, len, Scale.Times2));
        leaq(dst, new AMD64Address(dst, len, Scale.Times1));
        negq(len);

        Label L_copy_chars_loop = new Label();
        // Compress a single character per iteration.
        bind(L_copy_chars_loop);
        movzwl(res, new AMD64Address(src, len, Scale.Times2));
        testl(res, 0xff00);     // Check if Unicode character.
        jccb(ConditionFlag.NotZero, L_return_zero);
        // An ASCII character; compress to a byte.
        movb(new AMD64Address(dst, len, Scale.Times1), res);
        incrementq(len, 1);
        jcc(ConditionFlag.NotZero, L_copy_chars_loop);

        // If compression succeeded, return the length.
        bind(L_return_length);
        pop(res);
        jmpb(L_done);

        // If compression failed, return 0.
        bind(L_return_zero);
        xorl(res, res);
        addq(rsp, 8 /*wordSize*/);

        bind(L_done);
    }

    /* Inflate a Latin1 string using a byte[] array representation into a UTF16
     * string using a char[] array representation.
     *
     *   src    (rsi) the start address of source byte[] to be inflated
     *   dst    (rdi) the start address of destination char[] array
     *   len    (rdx) the length
     *   vtmp   (xmm)
     *   tmp    (gpr)
     */
    public void byteArrayInflate(Register src,  Register dst, Register len,
                                 Register vtmp, Register tmp) {

        assert vtmp.getRegisterCategory().equals(AMD64.XMM);

        Label L_done = new Label();
        Label L_below_threshold = new Label();

        assert src.number != dst.number && src.number != len.number && src.number != tmp.number;
        assert dst.number != len.number && dst.number != tmp.number;
        assert len.number != tmp.number;

        if (supports(CPUFeature.AVX512BW) &&
            supports(CPUFeature.AVX512VL) &&
            supports(CPUFeature.BMI2)) {

            // If the length of the string is less than 16, we chose not to use the
            // AVX512 instructions.
            testl(len, -16);
            jcc(ConditionFlag.Zero, L_below_threshold);

            Label L_avx512_tail = new Label();
            // Test for suitable number chunks with respect to the size of the vector
            // operation, mask off remaining number of chars (bytes) to inflate (such
            // that 'len' will always hold the number of bytes left to inflate) after
            // committing to the vector loop.
            // Adjust vector pointers to upper address bounds and inverse loop index.
            // This will keep the loop condition simple.
            //
            // NOTE: The above idiom/pattern is used in all the loops below.

            movl(tmp, len);
            andl(tmp, -32);     // The vector count (in chars).
            jccb(ConditionFlag.Zero, L_avx512_tail);
            andl(len, 32 - 1);  // The tail count (in chars).

            leaq(src, new AMD64Address(src, tmp, Scale.Times1));
            leaq(dst, new AMD64Address(dst, tmp, Scale.Times2));
            negq(tmp);

            Label L_avx512_loop = new Label();
            // Inflate 32 chars per iteration, reading 256-bit compact vectors
            // and writing 512-bit inflated ditto.
            bind(L_avx512_loop);
            evpmovzxbw(vtmp, new AMD64Address(src, tmp, Scale.Times1));
            evmovdqu16(new AMD64Address(dst, tmp, Scale.Times2), vtmp);
            addq(tmp, 32);
            jcc(ConditionFlag.NotZero, L_avx512_loop);

            bind(L_avx512_tail);
            // All done if the tail count is zero.
            testl(len, len);
            jcc(ConditionFlag.Zero, L_done);

            kmovq(k2, k1);      // Save k1

            // Compute (1 << N) - 1 = ~(~0 << N), where N is the remaining number
            // of characters to process.
            movl(tmp, -1);
            shlxl(tmp, tmp, len);
            notl(tmp);

            kmovd(k1, tmp);
            evpmovzxbw(vtmp, k1, new AMD64Address(src));
            evmovdqu16(new AMD64Address(dst), k1, vtmp);
            kmovq(k1, k2);      // Restore k1
            jmp(L_done);
        }

        if (supports(CPUFeature.SSE4_1)) {

            Label L_sse_tail = new Label();

            if (supports(CPUFeature.AVX2)) {

                Label L_avx2_tail = new Label();

                movl(tmp, len);
                andl(tmp, -16);
                jccb(ConditionFlag.Zero, L_avx2_tail);
                andl(len, 16 - 1);

                leaq(src, new AMD64Address(src, tmp, Scale.Times1));
                leaq(dst, new AMD64Address(dst, tmp, Scale.Times2));
                negq(tmp);

                Label L_avx2_loop = new Label();
                // Inflate 16 bytes (chars) per iteration, reading 128-bit compact vectors
                // and writing 256-bit inflated ditto.
                bind(L_avx2_loop);
                vpmovzxbw(vtmp, new AMD64Address(src, tmp, Scale.Times1));
                vmovdqu(new AMD64Address(dst, tmp, Scale.Times2), vtmp);
                addq(tmp, 16);
                jcc(ConditionFlag.NotZero, L_avx2_loop);

                bind(L_below_threshold);
                bind(L_avx2_tail);

                movl(tmp, len);
                andl(tmp, -8);
                jccb(ConditionFlag.Zero, L_sse_tail);
                andl(len, 8 - 1);

                // Inflate another 8 bytes before final tail copy.
                pmovzxbw(vtmp, new AMD64Address(src));
                movdqu(new AMD64Address(dst), vtmp);
                addq(src, 8);
                addq(dst, 16);

                // Fall-through to L_sse_tail.
            } else {
                // When there is no AVX2 support available, we use AVX/SSE support to
                // inflate into maximum 128-bits per operation.

                movl(tmp, len);
                andl(tmp, -8);
                jccb(ConditionFlag.Zero, L_sse_tail);
                andl(len, 8 - 1);

                leaq(src, new AMD64Address(src, tmp, Scale.Times1));
                leaq(dst, new AMD64Address(dst, tmp, Scale.Times2));
                negq(tmp);

                Label L_sse_copy_8_loop = new Label();
                // Inflate 8 bytes (chars) per iteration, reading 64-bit compact vectors
                // and writing 128-bit inflated ditto.
                bind(L_sse_copy_8_loop);
                pmovzxbw(vtmp, new AMD64Address(src, tmp, Scale.Times1));
                movdqu(new AMD64Address(dst, tmp, Scale.Times2), vtmp);
                addq(tmp, 8);
                jcc(ConditionFlag.NotZero, L_sse_copy_8_loop);

                // Fall-through to L_sse_tail.
            }

            Label L_copy_chars = new Label();

            bind(L_sse_tail);
            cmpl(len, 4);
            jccb(ConditionFlag.Less, L_copy_chars);

            movdl(vtmp, new AMD64Address(src));
            pmovzxbw(vtmp, vtmp);
            movq(new AMD64Address(dst), vtmp);
            subq(len, 4);
            addq(src, 4);
            addq(dst, 8);

            bind(L_copy_chars);
        }

        // Inflate any remaining characters (bytes) using a vanilla implementation.
        testl(len, len);
        jccb(ConditionFlag.Zero, L_done);
        leaq(src, new AMD64Address(src, len, Scale.Times1));
        leaq(dst, new AMD64Address(dst, len, Scale.Times2));
        negq(len);

        Label L_copy_chars_loop = new Label();
        // Inflate a single byte (char) per iteration.
        bind(L_copy_chars_loop);
        movzbl(tmp, new AMD64Address(src, len, Scale.Times1));
        movw(new AMD64Address(dst, len, Scale.Times2), tmp);
        incrementq(len, 1);
        jcc(ConditionFlag.NotZero, L_copy_chars_loop);

        bind(L_done);
    }

    // Checkstyle: resume

    // @formatter:on
}
