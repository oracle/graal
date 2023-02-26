/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.asm.aarch64;

import static jdk.vm.ci.aarch64.AArch64.CPU;
import static jdk.vm.ci.aarch64.AArch64.SIMD;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.code.Register;

public class AArch64ASIMDMacroAssembler extends AArch64ASIMDAssembler {
    private final AArch64MacroAssembler masm;

    public AArch64ASIMDMacroAssembler(AArch64MacroAssembler masm) {
        super(masm);
        this.masm = masm;
    }

    /**
     * Replicates a value to entirely fill an imm64.
     */
    private static long replicateValueToImm64(ElementSize eSize, long val) {
        int elementWidth = eSize.bits();
        assert elementWidth == 64 || NumUtil.isSignedNbit(elementWidth, val);

        long eVal = val & NumUtil.getNbitNumberLong(elementWidth);
        switch (eSize) {
            case Byte:
                return eVal << 56 | eVal << 48 | eVal << 40 | eVal << 32 | eVal << 24 | eVal << 16 | eVal << 8 | eVal;
            case HalfWord:
                return eVal << 48 | eVal << 32 | eVal << 16 | eVal;
            case Word:
                return eVal << 32 | eVal;
            case DoubleWord:
                return eVal;
        }

        throw GraalError.shouldNotReachHere();
    }

    /**
     * Checks whether a value, which will be placed in all elements, can be encoded as an immediate
     * operand in a vector move instruction.
     */
    public static boolean isMoveImmediate(ElementSize eSize, long imm) {
        long imm64 = replicateValueToImm64(eSize, imm);
        return ASIMDImmediateTable.isEncodable(imm64, ImmediateOp.MOVI) || ASIMDImmediateTable.isEncodable(~imm64, ImmediateOp.MVNI);
    }

    /**
     * Checks whether the provided 64-bit immediate value can be encoded within either a movi or
     * mvni vector instruction.
     */
    private void moveVI(ASIMDSize size, Register reg, long imm64) {
        if (ASIMDImmediateTable.isEncodable(imm64, ImmediateOp.MOVI)) {
            moviVI(size, reg, imm64);
        } else if (ASIMDImmediateTable.isEncodable(~imm64, ImmediateOp.MVNI)) {
            /* Moving not(not(imm64)). */
            mvniVI(size, reg, ~imm64);
        } else {
            throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * Moves an immediate value into each element of the result.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = imm</code>
     *
     * @param size register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *            ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation is
     *            performed on more than one element).
     * @param dst SIMD register.
     * @param imm value to copy into each element.
     */
    public void moveVI(ASIMDSize size, ElementSize eSize, Register dst, long imm) {
        moveVI(size, dst, replicateValueToImm64(eSize, imm));
    }

    /**
     * Checks whether a float, which will be placed in all elements, can be encoded as an immediate
     * operand in a vector move instruction.
     */
    public static boolean isMoveImmediate(float imm) {
        long imm64 = replicateValueToImm64(ElementSize.Word, Float.floatToIntBits(imm));
        return ASIMDImmediateTable.isEncodable(imm64, ImmediateOp.FMOVSP);
    }

    /**
     * Moves an immediate value into each element of the result.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = imm</code>
     *
     * @param size register size.
     * @param dst SIMD register.
     * @param imm value to copy into each element.
     */
    public void moveVI(ASIMDSize size, Register dst, float imm) {
        ElementSize eSize = ElementSize.Word;
        fmovVI(size, eSize, dst, replicateValueToImm64(eSize, Float.floatToIntBits(imm)));
    }

    /**
     * Checks whether a double, which will be placed in all elements, can be encoded as an immediate
     * operand in a vector move instruction.
     */
    public static boolean isMoveImmediate(double imm) {
        return ASIMDImmediateTable.isEncodable(Double.doubleToLongBits(imm), ImmediateOp.FMOVDP);
    }

    /**
     * Moves an immediate value into each element of the result.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = imm</code>
     */
    public void moveVI(ASIMDSize size, Register dst, double imm) {
        fmovVI(size, ElementSize.DoubleWord, dst, Double.doubleToLongBits(imm));
    }

    /**
     * Moves an immediate value into each element of the result.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = imm</code>
     */
    public void moveVV(ASIMDSize size, Register dst, Register src) {
        if (!src.equals(dst)) {
            orrVVV(size, dst, src, src);
        }
    }

    /**
     * Checks whether a 64-bit value can be encoded as an immediate operand in the given
     * instruction.
     */
    private static boolean isImmediateEncodable(ImmediateOp op, long imm64) {
        return ASIMDImmediateTable.isEncodable(imm64, op);
    }

    /**
     * Checks whether a value, which will be placed in all elements, can be encoded as an immediate
     * operand in the provided operation.
     */
    private static boolean isImmediateEncodable(ImmediateOp op, ElementSize eSize, long imm) {
        return isImmediateEncodable(op, replicateValueToImm64(eSize, imm));

    }

    /**
     * Checks whether a value, which will be placed in all elements, can be encoded as an immediate
     * operand in a vector bitwise inclusive or instruction.
     */
    public static boolean isOrrImmediate(ElementSize eSize, long imm) {
        return isImmediateEncodable(ImmediateOp.ORR, eSize, imm);
    }

    /**
     * Performs a bitwise inclusive or with the provided immediate on each element.
     *
     * <code>for i in 0..n-1 do dst[i] |= imm</code>
     */
    public void orrVI(ASIMDSize size, ElementSize eSize, Register dst, long imm) {
        long imm64 = replicateValueToImm64(eSize, imm);
        assert isImmediateEncodable(ImmediateOp.ORR, imm64);
        orrVI(size, dst, imm64);
    }

    /**
     * Checks whether a value, which will be placed in all elements, can be encoded as an immediate
     * operand in a vector bit clear instruction.
     */
    public static boolean isBicImmediate(ElementSize eSize, long imm) {
        return isImmediateEncodable(ImmediateOp.BIC, eSize, imm);
    }

    /**
     * Performs a bitwise bit clear with the provided immediate on each element.
     *
     * <code>for i in 0..n-1 do dst[i] &^= imm</code>
     */
    public void bicVI(ASIMDSize size, ElementSize eSize, Register dst, long imm) {
        long imm64 = replicateValueToImm64(eSize, imm);
        assert isImmediateEncodable(ImmediateOp.BIC, imm64);
        bicVI(size, dst, imm64);
    }

    /**
     * Performs right rotate on the provided register.
     */
    public void elementRor(ASIMDSize size, ElementSize eSize, Register dst, Register src, int rorAmt) {
        int byteRorAmt = eSize.bytes() * rorAmt;
        assert byteRorAmt >= 0 && byteRorAmt < size.bytes(); // can't perform a full rotation

        extVVV(size, dst, src, src, byteRorAmt);
    }

    /**
     * Moves an indexed SIMD element to a floating-point or general-purpose register.<br>
     *
     * <code>dst = src[index]</code>
     *
     * @param dstESize width of destination element.
     * @param srcESize width of source element.
     * @param dst Either floating-point or general-purpose register. If general-purpose, register
     *            may not be stackpointer or zero register.
     * @param src SIMD register.
     * @param index lane position of element to copy.
     */
    public void moveFromIndex(ElementSize dstESize, ElementSize srcESize, Register dst, Register src, int index) {
        assert src.getRegisterCategory().equals(SIMD);

        boolean sameWidth = dstESize == srcESize;
        int dstBits = dstESize.bits();
        if (index == 0 && sameWidth && (dstBits == 32 || dstBits == 64)) {
            masm.fmov(dstBits, dst, src);
        } else if (sameWidth && dst.getRegisterCategory().equals(CPU)) {
            umovGX(srcESize, dst, src, index);
        } else if (dst.getRegisterCategory().equals(CPU)) {
            assert !sameWidth;
            smovGX(dstESize, srcESize, dst, src, index);
        } else {
            assert dst.getRegisterCategory().equals(SIMD);
            dupSX(srcESize, dst, src, index);
        }
    }

    /**
     * Reverse the byte-order (endianess) of each element.
     *
     * @param size register size.
     * @param eSize element size.
     * @param dst SIMD register.
     * @param src SIMD register.
     */
    public void revVV(ASIMDSize size, ElementSize eSize, Register dst, Register src) {
        switch (eSize) {
            case Byte:
                // nothing to do - only 1 byte
                break;
            case HalfWord:
                masm.neon.rev16VV(size, dst, src);
                break;
            case Word:
                masm.neon.rev32VV(size, ElementSize.Byte, dst, src);
                break;
            case DoubleWord:
                masm.neon.rev64VV(size, ElementSize.Byte, dst, src);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * C7.2.200 Move vector element to another vector element.<br>
     * <p>
     * Preferred alias for insert vector element from another vector element.
     *
     * @param eSize size of value to duplicate.
     * @param dst SIMD register.
     * @param dstIdx offset of value to store.
     * @param src SIMD register.
     * @param srcIdx offset of value to duplicate.
     */
    public void movXX(ElementSize eSize, Register dst, int dstIdx, Register src, int srcIdx) {
        insXX(eSize, dst, dstIdx, src, srcIdx);
    }

    /**
     * C7.2.207 Bitwise not.<br>
     * <p>
     * Preferred alias for bitwise not (NOT).
     *
     * @param size register size.
     * @param dst SIMD register.
     * @param src SIMD register.
     */
    public void mvnVV(ASIMDSize size, Register dst, Register src) {
        notVV(size, dst, src);
    }

    /**
     * C7.2.338 Signed extend long.<br>
     * <p>
     * Preferred alias for sshll when only sign-extending the vector elements.
     *
     * @param srcESize source element size. Cannot be ElementSize.DoubleWord. The destination
     *            element size will be double this width.
     * @param dst SIMD register.
     * @param src SIMD register.
     */
    public void sxtlVV(ElementSize srcESize, Register dst, Register src) {
        sshllVVI(srcESize, dst, src, 0);
    }

    /**
     * C7.2.398 Unsigned extend long.<br>
     * <p>
     * Preferred alias for ushll when only zero-extending the vector elements.
     *
     * @param srcESize source element size. Cannot be ElementSize.DoubleWord. The destination
     *            element size will be double this width.
     * @param dst SIMD register.
     * @param src SIMD register.
     */
    public void uxtlVV(ElementSize srcESize, Register dst, Register src) {
        ushllVVI(srcESize, dst, src, 0);
    }

    /**
     * C7.2.398 Unsigned extend long.<br>
     * <p>
     * Preferred alias for ushll2 when only zero-extending the vector elements.
     *
     * @param srcESize source element size. Cannot be ElementSize.DoubleWord. The destination
     *            element size will be double this width.
     * @param dst SIMD register.
     * @param src SIMD register.
     */
    public void uxtl2VV(ElementSize srcESize, Register dst, Register src) {
        ushll2VVI(srcESize, dst, src, 0);
    }
}
