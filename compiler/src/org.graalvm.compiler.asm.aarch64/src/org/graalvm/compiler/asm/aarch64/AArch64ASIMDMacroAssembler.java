package org.graalvm.compiler.asm.aarch64;

import jdk.vm.ci.code.Register;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.GraalError;

import static jdk.vm.ci.aarch64.AArch64.CPU;
import static jdk.vm.ci.aarch64.AArch64.SIMD;

public class AArch64ASIMDMacroAssembler extends AArch64ASIMDAssembler {
    private final AArch64MacroAssembler masm;

    public AArch64ASIMDMacroAssembler(AArch64MacroAssembler masm) {
        super(masm);
        this.masm = masm;
    }

    /**
     * Performs right rotate on the provided register.
     */
    public void elementRor(ASIMDSize size, ElementSize eSize, Register dst, Register src, int rorAmt) {
        int byteRorAmt = eSize.bytes() * rorAmt;
        assert byteRorAmt >= 0 && byteRorAmt < size.bytes(); // can't perform a full rotation

        ext(size, dst, src, src, byteRorAmt);
    }

    /**
     * Moves a SIMD element to with a floating-point or general-purpose register.<br>
     *
     * <code>dst = src[index]</code>
     *
     * @param eSize width of element.
     * @param dst   Either floating-point or general-purpose register. If general-purpose,
     *              register may not be stackpointer or zero register.
     * @param src   SIMD register.
     * @param index lane position of element to copy.
     */
    public void moveScalar(ElementSize eSize, Register dst, Register src, int index) {
        if (index == 0) {
            masm.fmov(eSize.bits(), dst, src);
        } else if (dst.getRegisterCategory().equals(CPU)) {
            assert src.getRegisterCategory().equals(SIMD);
            umovGeneral(eSize, dst, src, index);
        } else {
            assert src.getRegisterCategory().equals(SIMD) && dst.getRegisterCategory().equals(SIMD);
            dupScalar(eSize, dst, src, index);
        }
    }

    /**
     * Replicates a value numRepeats times within a 64-bit value.
     */
    private static long replicateValue(long val, ElementSize eSize, int numRepeats) {
        int elementWidth = eSize.bits();
        long result = 0;
        for (int i = 0; i < numRepeats; i++) {
            result = result << elementWidth | (val & NumUtil.getNbitNumberLong(elementWidth));
        }
        return result;
    }

    /**
     * Checks whether a value, which will be placed in all elements, can be encoded as an
     * immediate operand in a vector move instruction.
     */
    public static boolean isMoveImmediate(ElementSize eSize, long imm) {
        int numRepeats = 64 / eSize.bits();
        long imm64 = replicateValue(imm, eSize, numRepeats);
        return ASIMDImmediateTable.isEncodable(imm64, ImmediateOp.MOVI) || ASIMDImmediateTable.isEncodable(~imm64, ImmediateOp.MVNI);
    }

    /**
     * Checks whether a float, which will be placed in all elements, can be encoded as an
     * immediate operand in a vector move instruction.
     */
    public static boolean isMoveImmediate(float imm) {
        long imm64 = replicateValue(Float.floatToIntBits(imm), ElementSize.Word, 2);
        return ASIMDImmediateTable.isEncodable(imm64, ImmediateOp.FMOVSP);
    }

    /**
     * Checks whether a double, which will be placed in all elements, can be encoded as an
     * immediate operand in a vector move instruction.
     */
    public static boolean isMoveImmediate(double imm) {
        return ASIMDImmediateTable.isEncodable(Double.doubleToLongBits(imm), ImmediateOp.FMOVDP);
    }

    /**
     * Checks whether the provided 64-bit immediate value can be encoded within either a movi or
     * mvni vector instruction.
     */
    private void moveVector(ASIMDSize size, Register reg, long imm64) {
        if (ASIMDImmediateTable.isEncodable(imm64, ImmediateOp.MOVI)) {
            moviVector(size, reg, imm64);
        } else if (ASIMDImmediateTable.isEncodable(~imm64, ImmediateOp.MVNI)) {
            /* Moving not(not(imm64)). */
            mvniVector(size, reg, ~imm64);
        } else {
            throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * Moves an immediate value into each element of the result.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = imm</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param imm   value to copy into each element.
     */
    public void moveVector(ASIMDSize size, Register dst, ElementSize eSize, long imm) {
        int numRepeats = 64 / eSize.bits();
        moveVector(size, dst, replicateValue(imm, eSize, numRepeats));
    }

    /**
     * Moves an immediate value into each element of the result.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = imm</code>
     *
     * @param size register size.
     * @param dst  SIMD register.
     * @param imm  value to copy into each element.
     */
    public void moveVector(ASIMDSize size, Register dst, float imm) {
        fmovVector(size, dst, imm);
    }

    /**
     * Moves an immediate value into each element of the result.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = imm</code>
     */
    public void moveVector(ASIMDSize size, Register dst, double imm) {
        fmovVector(size, dst, imm);
    }

    /**
     * Moves an immediate value into each element of the result.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = imm</code>
     */
    public void moveVector(ASIMDSize size, Register dst, Register src) {
        if (src != dst) {
            orrVector(size, dst, src, src);
        }
    }

    /**
     * Checks whether a 64-bit value can be encoded as an immediate operand in a vector bitwise
     * inclusive or instruction.
     */
    public static boolean isOrrImmediate(long imm64) {
        return ASIMDImmediateTable.isEncodable(imm64, ImmediateOp.ORR);
    }

    /**
     * Checks whether a value, which will be placed in all elements, can be encoded as an
     * immediate operand in a vector bitwise inclusive or instruction.
     */
    public static boolean isOrrImmediate(ElementSize eSize, long imm) {
        int numRepeats = 64 / eSize.bits();
        return isOrrImmediate(replicateValue(imm, eSize, numRepeats));
    }

    /**
     * Performs a bitwise inclusive or with the provided immediate on each element.
     *
     * <code>for i in 0..n-1 do dst[i] |= imm</code>
     */
    public void orrVector(ASIMDSize size, ElementSize eSize, Register dst, long imm) {
        int numRepeats = 64 / eSize.bits();
        long imm64 = replicateValue(imm, eSize, numRepeats);
        assert isOrrImmediate(imm64);
        orrVector(size, dst, imm64);
    }

    /**
     * Checks whether a 64-bit value, can be encoded as an immediate operand in a vector bit
     * clear instruction.
     */
    public static boolean isBicImmediate(long imm64) {
        return ASIMDImmediateTable.isEncodable(imm64, ImmediateOp.BIC);
    }

    /**
     * Checks whether a value, which will be placed in all elements, can be encoded as an
     * immediate operand in a vector bit clear instruction.
     */
    public static boolean isBicImmediate(ElementSize eSize, long imm) {
        int numRepeats = 64 / eSize.bits();
        return isBicImmediate(replicateValue(imm, eSize, numRepeats));
    }

    /**
     * Performs a bitwise bit clear with the provided immediate on each element.
     *
     * <code>for i in 0..n-1 do dst[i] &^= imm</code>
     */
    public void bicVector(ASIMDSize size, ElementSize eSize, Register dst, long imm) {
        int numRepeats = 64 / eSize.bits();
        long imm64 = replicateValue(imm, eSize, numRepeats);
        assert isBicImmediate(imm64);
        bicVector(size, dst, imm64);
    }

    /**
     * C7.2.207 Bitwise not.<br>
     * <p>
     * Preferred alias for bitwise not (NOT).
     *
     * @param size register size.
     * @param dst  SIMD register.
     * @param src  SIMD register.
     */
    public void mvnVector(ASIMDSize size, Register dst, Register src) {
        notVector(size, dst, src);
    }

    /**
     * C7.2.338 Signed extend long.<br>
     * <p>
     * Preferred alias for sshll when only sign-extending the vector elements.
     *
     * @param srcESize source element size. Cannot be ElementSize.DoubleWord. The destination
     *                 element size will be double this width.
     * @param dst      SIMD register.
     * @param src      SIMD register.
     */
    public void sxtlVector(ElementSize srcESize, Register dst, Register src) {
        sshllVector(srcESize, dst, src, 0);
    }

    /**
     * C7.2.398 Unsigned extend long.<br>
     * <p>
     * Preferred alias for ushll when only zero-extending the vector elements.
     *
     * @param srcESize source element size. Cannot be ElementSize.DoubleWord. The destination
     *                 element size will be double this width.
     * @param dst      SIMD register.
     * @param src      SIMD register.
     */
    public void uxtlVector(ElementSize srcESize, Register dst, Register src) {
        ushllVector(srcESize, dst, src, 0);
    }
}
