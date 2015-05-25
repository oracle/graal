/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.asm.sparc;

import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.sparc.SPARC.*;
import static java.lang.String.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.sparc.*;
import com.oracle.graal.sparc.SPARC.CPUFeature;
import com.oracle.jvmci.common.*;

/**
 * This class implements an assembler that can encode most SPARC instructions.
 */
public abstract class SPARCAssembler extends Assembler {

    /**
     * Constructs an assembler for the SPARC architecture.
     *
     * @param registerConfig the register configuration used to bind {@link Register#Frame} and
     *            {@link Register#CallerFrame} to physical registers. This value can be null if this
     *            assembler instance will not be used to assemble instructions using these logical
     *            registers.
     */
    public SPARCAssembler(TargetDescription target, RegisterConfig registerConfig) {
        super(target);
    }

    public static final int CCR_ICC_SHIFT = 0;
    public static final int CCR_XCC_SHIFT = 4;
    public static final int CCR_C_SHIFT = 0;
    public static final int CCR_V_SHIFT = 1;
    public static final int CCR_Z_SHIFT = 2;
    public static final int CCR_N_SHIFT = 3;

    protected static final int OP_SHIFT = 30;
    protected static final int CBCOND_SHIFT = 28;
    protected static final int OP2_SHIFT = 22;
    protected static final int A_SHIFT = 29;

    protected static final int A_MASK = 0b0010_0000_0000_0000_0000_0000_0000_0000;
    protected static final int OP_MASK = 0b1100_0000_0000_0000_0000_0000_0000_0000;
    protected static final int CBCOND_MASK = 0b0001_0000_0000_0000_0000_0000_0000_0000; // Used for
    // distinguish CBcond and BPr instructions
    protected static final int OP2_MASK = 0b0000_0001_1100_0000_0000_0000_0000_0000;

    protected static final int DISP22_SHIFT = 0;
    protected static final int DISP22_MASK = 0b00000000001111111111111111111111;

    protected static final int DISP19_SHIFT = 0;
    protected static final int DISP19_MASK = 0b00000000000001111111111111111111;

    protected static final int D16HI_SHIFT = 20;
    protected static final int D16HI_MASK = 0b0000_0000_0011_0000_0000_0000_0000_0000;
    protected static final int D16LO_SHIFT = 0;
    protected static final int D16LO_MASK = 0b0000_0000_0000_0000_0011_1111_1111_1111;

    protected static final int D10LO_MASK = 0b0000_0000_0000_0000_0001_1111_1110_0000;
    protected static final int D10HI_MASK = 0b0000_0000_0001_1000_0000_0000_0000_0000;
    protected static final int D10LO_SHIFT = 5;
    protected static final int D10HI_SHIFT = 19;

    public enum Ops {
        // @formatter:off

        BranchOp(0b00),
        CallOp(0b01),
        ArithOp(0b10),
        LdstOp(0b11);

        // @formatter:on

        private final int value;

        private Ops(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public boolean appliesTo(int instructionWord) {
            int opShift = 30;
            return (instructionWord >>> opShift) == value;
        }
    }

    public enum Op {
        Op00(0b00),
        Op01(0b01),
        Op10(0b10),
        Op11(0b11);
        int op;

        Op(int op) {
            this.op = op;
        }
    }

    public enum Op2s {
        // @formatter:off

        Illtrap(0b000),
        Bpr    (0b011),
        Fb     (0b110),
        Fbp    (0b101),
        Br     (0b010),
        Bp     (0b001),
        Cb     (0b111),
        Sethi  (0b100);


        // @formatter:on

        private final int value;

        private Op2s(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Op2s byValue(int value) {
            for (Op2s op : values()) {
                if (op.getValue() == value) {
                    return op;
                }
            }
            return null;
        }
    }

    public enum Op3s {
        // @formatter:off

        Add(0x00, "add", Op10),
        And(0x01, "and", Op10),
        Or(0x02, "or", Op10),
        Xor(0x03, "xor", Op10),
        Sub(0x04, "sub", Op10),
        Andn(0x05, "andn", Op10),
        Orn(0x06, "orn", Op10),
        Xnor(0x07, "xnor", Op10),
        Addc(0x08, "addc", Op10),
        Mulx(0x09, "mulx", Op10),
        Umul(0x0A, "umul", Op10),
        Smul(0x0B, "smul", Op10),
        Subc(0x0C, "subc", Op10),
        Udivx(0x0D, "udivx", Op10),
        Udiv(0x0E, "udiv", Op10),
        Sdiv(0x0F, "sdiv", Op10),

        Addcc(0x10, "addcc", Op10),
        Andcc(0x11, "andcc", Op10),
        Orcc(0x12, "orcc", Op10),
        Xorcc(0x13, "xorcc", Op10),
        Subcc(0x14, "subcc", Op10),
        Andncc(0x15, "andncc", Op10),
        Orncc(0x16, "orncc", Op10),
        Xnorcc(0x17, "xnorcc", Op10),
        Addccc(0x18, "addccc", Op10),

        Umulcc(0x1A, "umulcc", Op10),
        Smulcc(0x1B, "smulcc", Op10),
        Subccc(0x1C, "subccc", Op10),
        Udivcc(0x1E, "udivcc", Op10),
        Sdivcc(0x1F, "sdivcc", Op10),

        Taddcc(0x20, "taddcc", Op10),
        Tsubcc(0x21, "tsubcc", Op10),
        Taddcctv(0x22, "taddcctv", Op10),
        Tsubcctv(0x23, "tsubcctv", Op10),
        Mulscc(0x24, "mulscc", Op10),
        Sll(0x25, "sll", Op10),
        Sllx(0x25, "sllx", Op10),
        Srl(0x26, "srl", Op10),
        Srlx(0x26, "srlx", Op10),
        Sra(0x27, "srax", Op10),
        Srax(0x27, "srax", Op10),
        Membar(0x28, "membar", Op10),

        Flushw(0x2B, "flushw", Op10),
        Movcc(0x2C, "movcc", Op10),
        Sdivx(0x2D, "sdivx", Op10),
        Popc(0x2E, "popc", Op10),
        Movr(0x2F, "movr", Op10),

        Fpop1(0b11_0100, "fpop1", Op10),
        Fpop2(0b11_0101, "fpop2", Op10),
        Impdep1(0b11_0110, "impdep1", Op10),
        Impdep2(0b11_0111, "impdep2", Op10),
        Jmpl(0x38, "jmpl", Op10),
        Rett(0x39, "rett", Op10),
        Trap(0x3a, "trap", Op10),
        Flush(0x3b, "flush", Op10),
        Save(0x3c, "save", Op10),
        Restore(0x3d, "restore", Op10),
        Retry(0x3e, "retry", Op10),


        Casa(0b111100, "casa", Op11),
        Casxa(0b111110, "casxa", Op11),
        Prefetch(0b101101, "prefetch", Op11),
        Prefetcha(0b111101, "prefetcha", Op11),

        Lduw  (0b00_0000, "lduw", Op11),
        Ldub  (0b00_0001, "ldub", Op11),
        Lduh  (0b00_0010, "lduh", Op11),
        Stw   (0b00_0100, "stw", Op11),
        Stb   (0b00_0101, "stb", Op11),
        Sth   (0b00_0110, "sth", Op11),
        Ldsw  (0b00_1000, "ldsw", Op11),
        Ldsb  (0b00_1001, "ldsb", Op11),
        Ldsh  (0b00_1010, "ldsh", Op11),
        Ldx   (0b00_1011, "ldx", Op11),
        Stx   (0b00_1110, "stx", Op11),

        Ldf   (0b10_0000, "ldf", Op11),
        Ldfsr (0b10_0001, "ldfsr", Op11),
        Ldaf  (0b10_0010, "ldaf", Op11),
        Lddf  (0b10_0011, "lddf", Op11),
        Stf   (0b10_0100, "stf", Op11),
        Stfsr (0b10_0101, "stfsr", Op11),
        Staf  (0x10_0110, "staf", Op11),
        Stdf  (0b10_0111, "stdf", Op11),

        Rd    (0b10_1000, "rd", Op10),
        Wr    (0b11_0000, "wr", Op10),
        Fcmp  (0b11_0101, "fcmp", Op10),

        Ldxa  (0b01_1011, "ldxa", Op11),
        Lduwa (0b01_0000, "lduwa", Op11),

        Tcc(0b11_1010, "tcc", Op10);

        // @formatter:on

        private final int value;
        private final String operator;
        private final Op op;

        private Op3s(int value, String name, Op op) {
            this.value = value;
            this.operator = name;
            this.op = op;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }

        public boolean appliesTo(int instructionWord) {
            return ((instructionWord >>> 19) & 0b1_1111) == value;
        }
    }

    public enum Opfs {
        // @formatter:off

        Fmovs(0b0_0000_0001, "fmovs"),
        Fmovd(0b0_0000_0010, "fmovd"),
        Fmovq(0b0_0000_0011, "fmovq"),
        Fmovscc(0b00_0001, "fmovscc"),
        Fmovdcc(0b00_0010, "fmovdcc"),
        Fnegs(0x05, "fnegs"),
        Fnegd(0x06, "fnegd"),
        Fnegq(0x07, "fnegq"),
        Fabss(0x09, "fabss"),
        Fabsd(0x0A, "fabsd"),
        Fabsq(0x0B, "fabsq"),

        // start VIS1
        Edge8cc(0x0, "edge8cc"),
        Edge8n(0x1, "edge8n"),
        Edge8lcc(0x2, "edge8lcc"),
        Edge8ln(0x3, "edge8ln"),
        Edge16cc(0x4, "edge16cc"),
        Edge16n(0x5, "edge16n"),
        Edge16lcc(0x6, "edge16lcc"),
        Edge16ln(0x7, "edge16ln"),
        Edge32cc(0x8, "edge32cc"),
        Edge32n(0x9, "edge32n"),
        Edge32lcc(0xA, "edge32lcc"),
        Edge32ln(0xB, "edge32ln"),
        Array8(0x10, "array8"),
        Array16(0x12, "array16"),
        Array32(0x14, "array32"),
        AlignAddress(0x18, "alignaddress"),
        AlignAddressLittle(0x1A, "alignaddress_little"),
        Fpcmple16(0x20, "fpcmple16"),
        Fpcmpne16(0x22, "fpcmpne16"),
        Fpcmple32(0x24, "fpcmple32"),
        Fpcmpne32(0x26, "fpcmpne32"),
        Fpcmpgt16(0x28, "fpcmpgt16"),
        Fpcmpeq16(0x2A, "fpcmpeq16"),
        Fpcmpgt32(0x2C, "fpcmpgt32"),
        Fpcmpeq32(0x2E, "fpcmpeq32"),
        Fmul8x16(0x31, "fmul8x16"),
        Fmul8x16au(0x33, "fmul8x16au"),
        Fmul8x16al(0x35, "fmul8x16al"),
        Fmul8sux16(0x36, "fmul8sux16"),
        Fmul8ulx16(0x37, "fmul8ulx16"),
        Fmuld8sux16(0x38, "fmuld8sux16"),
        Fmuld8ulx16(0x39, "fmuld8ulx16"),
        Fpack32(0x3A, "fpack32"),
        Fpack16(0x3B, "fpack16"),
        Fpackfix(0x3D, "fpackfix"),
        Faligndatag(0x48, "faligndata"),
        Fpmerge(0x4B, "fpmerge"),
        Fpadd16(0x50, "fpadd16"),
        Fpadd16s(0x51, "fpadd16s"),
        Fpadd32(0x52, "fpadd32"),
        Fpadd32s(0x53, "fpadd32s"),
        Fpsub16(0x54, "fpadd16"),
        Fpsub16s(0x55, "fpadd16s"),
        Fpsub32(0x56, "fpadd32"),
        Fpsub32s(0x57, "fpadd32s"),
        Fzerod(0x60, "fzerod"),
        Fzeros(0x61, "fzeros"),
        Fnot2d(0x66, "fnot1d"),
        Fnot2s(0x67, "fnot1s"),
        Fnot1d(0x6A, "fnot1d"),
        Fnot1s(0x6B, "fnot1s"),
        Fsrc1d(0x74, "fsrc1d"),
        Fsrc1s(0x75, "fsrc1s"),
        Fsrc2d(0x78, "fsrc2d"),
        Fsrc2s(0x79, "fsrc2s"),
        Foned(0x7E, "foned"),
        Fones(0x7F, "fones"),
        Fandd(0b0_0111_0000, "fandd"),
        Fands(0b0_0111_0001, "fands"),
        Fxord(0b0_0110_1100, "fxord"),
        Fxors(0b0_0110_1101, "fxors"),
        // end VIS1

        // start VIS2
        Bmask(0x19, "bmask"),
        Bshuffle(0x4c, "bshuffle"),
        // end VIS2 only

        // start VIS3
        Addxc(0x11, "addxc"),
        Addxccc(0x13, "addxccc"),
        Cmask8(0x1B, "cmask8"),
        Cmask16(0x1D, "cmask16"),
        Cmask32(0x1F, "cmask32"),
        Fmean16(0x40, "fmean16"),
        Fnadds(0x51, "fnadds"),
        Fnaddd(0x52, "fnaddd"),
        Fnmuls(0x59, "fnmuls"),
        Fnmuld(0x5A, "fnmuld"),
        Fnsmuld(0x79, "fnsmuld"),
        Fnhadds(0x71, "fnhadds"),
        Fnhaddd(0x72, "fnhaddd"),
        Movdtox(0x110, "movdtox"),
        Movstouw(0x111, "movstouw"),
        Movstosw(0x113, "movstosw"),
        Movxtod(0x118, "movxtod"),
        Movwtos(0b1_0001_1001, "movwtos"),
        UMulxhi(0b0_0001_0110, "umulxhi"),
        Lzcnt  (0b0_0001_0111, "lzcnt"),
        // end VIS3

        // start CAMMELLIA
        CammelliaFl(0x13C, "cammelia_fl"),
        CammelliaFli(0x13D, "cammellia_fli"),
        // end CAMMELLIA

        // start CRYPTO
        Crc32c(0x147, "crc32c"),
        // end CRYPTO

        // start OSA 2011
        Fpadd64(0x44, "fpadd64"),
        Fpsub64(0x46, "fpsub64"),
        Fpadds16(0x58, "fpadds16"),
        Fpadds16s(0x59, "fpadds16"),
        Fpadds32(0x5A, "fpadds32"),
        Fpadds32s(0x5B, "fpadds32s"),
        Fpsubs16(0x5C, "fpsubs16"),
        Fpsubs16s(0x5D, "fpsubs16s"),
        Fpsubs32(0x5E, "fpsubs32"),
        Fpsubs32s(0x5F, "fpsubs32s"),
        Fpcmpne8(0x122, "fpcmpne8"),
        Fpcmpeq8(0x12C, "fpcmpeq8"),
        // end OSA 2011

        Fadds(0x41, "fadds"),
        Faddd(0x42, "faddd"),
        Faddq(0x43, "faddq"),
        Fsubs(0x45, "fsubs"),
        Fsubd(0x46, "fsubd"),
        Fsubq(0x47, "fsubq"),
        Fmuls(0x49, "fmuls"),
        Fmuld(0x4A, "fmuld"),
        Fdivs(0x4D, "fdivs"),
        Fdivd(0x4E, "fdivd"),
        Fdivq(0x4F, "fdivq"),

        Fsqrts(0x29, "fsqrts"),
        Fsqrtd(0x2A, "fsqrtd"),
        Fsqrtq(0x2B, "fsqrtq"),

        Fsmuld(0x69, "fsmuld"),
        Fmulq(0x6B, "fmulq"),
        Fdmuldq(0x6E, "fdmulq"),

        Fstoi(0xD1, "fstoi"),
        Fdtoi(0xD2, "fdtoi"),
        Fstox(0x81, "fstox"),
        Fdtox(0x82, "fdtox"),
        Fxtos(0x84, "fxtos"),
        Fxtod(0x88, "fxtod"),
        Fxtoq(0x8C, "fxtoq"),
        Fitos(0xC4, "fitos"),
        Fdtos(0xC6, "fdtos"),
        Fitod(0xC8, "fitod"),
        Fstod(0xC9, "fstod"),
        Fitoq(0xCC, "fitoq"),


        Fcmps(0x51, "fcmps"),
        Fcmpd(0x52, "fcmpd"),
        Fcmpq(0x53, "fcmpq");

        // @formatter:on

        private final int value;
        private final String operator;

        private Opfs(int value, String op) {
            this.value = value;
            this.operator = op;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }
    }

    public enum Annul {
        ANNUL(1),
        NOT_ANNUL(0);
        public final int flag;

        Annul(int flag) {
            this.flag = flag;
        }
    }

    public enum BranchPredict {
        PREDICT_TAKEN(1),
        PREDICT_NOT_TAKEN(0);
        public final int flag;

        BranchPredict(int flag) {
            this.flag = flag;
        }
    }

    public enum MembarMask {
        // @formatter:off

        StoreStore(1 << 3, "storestore"),
        LoadStore(1 << 2, "loadstore"),
        StoreLoad(1 << 1, "storeload"),
        LoadLoad(1 << 0, "loadload"),
        Sync(1 << 6, "sync"),
        MemIssue(1 << 5, "memissue"),
        LookAside(1 << 4, "lookaside");

        // @formatter:on

        private final int value;
        private final String operator;

        private MembarMask(int value, String op) {
            this.value = value;
            this.operator = op;
        }

        public int getValue() {
            return value | 0x2000;
        }

        public String getOperator() {
            return operator;
        }
    }

    /**
     * Condition Codes to use for instruction.
     */
    public enum CC {
        // @formatter:off
        /**
         * Condition is considered as 32bit operation condition.
         */
        Icc(0b00, "icc", false),
        /**
         * Condition is considered as 64bit operation condition.
         */
        Xcc(0b10, "xcc", false),
        Ptrcc(getHostWordKind() == Kind.Long ? Xcc.getValue() : Icc.getValue(), "ptrcc", false),
        Fcc0(0b00, "fcc0", true),
        Fcc1(0b01, "fcc1", true),
        Fcc2(0b10, "fcc2", true),
        Fcc3(0b11, "fcc3", true);

        // @formatter:on

        private final int value;
        private final String operator;
        private boolean isFloat;

        private CC(int value, String op, boolean isFloat) {
            this.value = value;
            this.operator = op;
            this.isFloat = isFloat;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }

        public static CC forKind(Kind kind) {
            boolean isInt = kind == Kind.Boolean || kind == Kind.Byte || kind == Kind.Char || kind == Kind.Short || kind == Kind.Int;
            boolean isFloat = kind == Kind.Float || kind == Kind.Double;
            boolean isLong = kind == Kind.Long || kind == Kind.Object;
            assert isInt || isFloat || isLong;
            if (isLong) {
                return Xcc;
            } else if (isInt) {
                return Icc;
            } else if (isFloat) {
                return Fcc0;
            } else {
                throw JVMCIError.shouldNotReachHere();
            }
        }
    }

    public enum ConditionFlag {
        // @formatter:off

        // for FBfcc & FBPfcc instruction
        F_Never(0, "f_never"),
        F_NotEqual(1, "f_notEqual"),
        F_LessOrGreater(2, "f_lessOrGreater"),
        F_UnorderedOrLess(3, "f_unorderedOrLess"),
        F_Less(4, "f_less"),
        F_UnorderedOrGreater(5, "f_unorderedOrGreater"),
        F_Greater(6, "f_greater"),
        F_Unordered(7, "f_unordered"),
        F_Always(8, "f_always"),
        F_Equal(9, "f_equal"),
        F_UnorderedOrEqual(10, "f_unorderedOrEqual"),
        F_GreaterOrEqual(11, "f_greaterOrEqual"),
        F_UnorderedGreaterOrEqual(12, "f_unorderedGreaterOrEqual"),
        F_LessOrEqual(13, "f_lessOrEqual"),
        F_UnorderedOrLessOrEqual(14, "f_unorderedOrLessOrEqual"),
        F_Ordered(15, "f_ordered"),

        // for integers
        Never(0, "never"),
        Equal(1, "equal", true),
        Zero(1, "zero"),
        LessEqual(2, "lessEqual", true),
        Less(3, "less", true),
        LessEqualUnsigned(4, "lessEqualUnsigned", true),
        LessUnsigned(5, "lessUnsigned", true),
        CarrySet(5, "carrySet"),
        Negative(6, "negative", true),
        OverflowSet(7, "overflowSet", true),
        Always(8, "always"),
        NotEqual(9, "notEqual", true),
        NotZero(9, "notZero"),
        Greater(10, "greater", true),
        GreaterEqual(11, "greaterEqual", true),
        GreaterUnsigned(12, "greaterUnsigned", true),
        GreaterEqualUnsigned(13, "greaterEqualUnsigned", true),
        CarryClear(13, "carryClear"),
        Positive(14, "positive", true),
        OverflowClear(15, "overflowClear", true);

        // @formatter:on

        private final int value;
        private final String operator;
        private boolean forCBcond = false;

        private ConditionFlag(int value, String op) {
            this(value, op, false);
        }

        private ConditionFlag(int value, String op, boolean cbcond) {
            this.value = value;
            this.operator = op;
            this.forCBcond = cbcond;
        }

        public boolean isCBCond() {
            return forCBcond;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }

        public ConditionFlag negate() {
            //@formatter:off
            switch (this) {
                case F_Never                  : return F_Always;
                case F_Always                 : return F_Never;
                case F_NotEqual               : return F_Equal;
                case F_Equal                  : return F_NotEqual;
                case F_LessOrGreater          : return F_UnorderedOrEqual;
                case F_UnorderedOrEqual       : return F_LessOrGreater;
                case F_Less                   : return F_UnorderedGreaterOrEqual;
                case F_UnorderedGreaterOrEqual: return F_Less;
                case F_LessOrEqual            : return F_UnorderedOrGreater;
                case F_UnorderedOrGreater     : return F_LessOrEqual;
                case F_Greater                : return F_UnorderedOrLessOrEqual;
                case F_UnorderedOrLessOrEqual : return F_Greater;
                case F_GreaterOrEqual         : return F_UnorderedOrLess;
                case F_UnorderedOrLess        : return F_GreaterOrEqual;
                case F_Unordered              : return F_Ordered;
                case F_Ordered                : return F_Unordered;
                case Never                    : return Always;
                case Always                   : return Never;
                case Equal                    : return NotEqual;
                case NotEqual                 : return Equal;
                case Zero                     : return NotZero;
                case NotZero                  : return Zero;
                case LessEqual                : return Greater;
                case Greater                  : return LessEqual;
                case Less                     : return GreaterEqual;
                case GreaterEqual             : return Less;
                case LessEqualUnsigned        : return GreaterUnsigned;
                case GreaterUnsigned          : return LessEqualUnsigned;
                case LessUnsigned             : return GreaterEqualUnsigned;
                case GreaterEqualUnsigned     : return LessUnsigned;
                case CarrySet                 : return CarryClear;
                case CarryClear               : return CarrySet;
                case Negative                 : return Positive;
                case Positive                 : return Negative;
                case OverflowSet              : return OverflowClear;
                case OverflowClear            : return OverflowSet;
                default:
                    JVMCIError.unimplemented();
            }
            //@formatter:on
            return null;
        }

        public ConditionFlag mirror() {
            switch (this) {
            //@formatter:off
                case F_Less                   : return F_Greater;
                case F_Greater                : return F_Less;
                case F_LessOrEqual            : return F_GreaterOrEqual;
                case F_UnorderedGreaterOrEqual: return F_UnorderedOrLessOrEqual;
                case F_UnorderedOrGreater     : return F_UnorderedOrLess;
                case F_UnorderedOrLessOrEqual : return F_UnorderedGreaterOrEqual;
                case F_GreaterOrEqual         : return F_LessOrEqual;
                case F_UnorderedOrLess        : return F_UnorderedOrGreater;
                case LessEqual                : return GreaterEqual;
                case Greater                  : return Less;
                case Less                     : return Greater;
                case GreaterEqual             : return LessEqual;
                case LessEqualUnsigned        : return GreaterEqualUnsigned;
                case GreaterUnsigned          : return LessUnsigned;
                case LessUnsigned             : return GreaterUnsigned;
                case GreaterEqualUnsigned     : return LessEqualUnsigned;
                default:
                    return this;
                //@formatter:on
            }
        }

        public static ConditionFlag fromCondtition(CC conditionFlagsRegister, Condition cond, boolean unorderedIsTrue) {
            switch (conditionFlagsRegister) {
                case Xcc:
                case Icc:
                    switch (cond) {
                        case EQ:
                            return Equal;
                        case NE:
                            return NotEqual;
                        case BT:
                            return LessUnsigned;
                        case LT:
                            return Less;
                        case BE:
                            return LessEqualUnsigned;
                        case LE:
                            return LessEqual;
                        case AE:
                            return GreaterEqualUnsigned;
                        case GE:
                            return GreaterEqual;
                        case AT:
                            return GreaterUnsigned;
                        case GT:
                            return Greater;
                    }
                    throw JVMCIError.shouldNotReachHere("Unimplemented for: " + cond);
                case Fcc0:
                case Fcc1:
                case Fcc2:
                case Fcc3:
                    switch (cond) {
                        case EQ:
                            return unorderedIsTrue ? F_UnorderedOrEqual : F_Equal;
                        case NE:
                            return ConditionFlag.F_NotEqual;
                        case LT:
                            return unorderedIsTrue ? F_UnorderedOrLess : F_Less;
                        case LE:
                            return unorderedIsTrue ? F_UnorderedOrLessOrEqual : F_LessOrEqual;
                        case GE:
                            return unorderedIsTrue ? F_UnorderedGreaterOrEqual : F_GreaterOrEqual;
                        case GT:
                            return unorderedIsTrue ? F_UnorderedOrGreater : F_Greater;
                    }
                    throw JVMCIError.shouldNotReachHere("Unkown condition: " + cond);
            }
            throw JVMCIError.shouldNotReachHere("Unknown condition flag register " + conditionFlagsRegister);
        }
    }

    public enum RCondition {
        // @formatter:off

        Rc_z(0b001, "rc_z"),
        Rc_lez(0b010, "rc_lez"),
        Rc_lz(0b011, "rc_lz"),
        Rc_nz(0b101, "rc_nz"),
        Rc_gz(0b110, "rc_gz"),
        Rc_gez(0b111, "rc_gez"),
        Rc_last(Rc_gez.getValue(), "rc_last");

        // @formatter:on

        private final int value;
        private final String operator;

        private RCondition(int value, String op) {
            this.value = value;
            this.operator = op;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }
    }

    /**
     * Represents the <b>Address Space Identifier</b> defined in the SPARC architecture.
     */
    public enum Asi {
        // @formatter:off

        INVALID(-1),
        ASI_PRIMARY(0x80),
        ASI_PRIMARY_NOFAULT(0x82),
        ASI_PRIMARY_LITTLE(0x88),
        // Block initializing store
        ASI_ST_BLKINIT_PRIMARY(0xE2),
        // Most-Recently-Used (MRU) BIS variant
        ASI_ST_BLKINIT_MRU_PRIMARY(0xF2);

        // @formatter:on

        private final int value;

        private Asi(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public boolean isValid() {
            return value != INVALID.getValue();
        }
    }

    public enum Fcn {
        SeveralWritesAndPossiblyReads(2),
        SeveralReadsWeak(0),
        OneRead(1),
        OneWrite(3),
        Page(4),
        NearestUnifiedCache(17),
        SeveralReadsStrong(20),
        OneReadStrong(21),
        SeveralWritesAndPossiblyReadsStrong(22),
        OneWriteStrong(23);

        private final int value;

        private Fcn(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public boolean hasFeature(CPUFeature feature) {
        return ((SPARC) this.target.arch).features.contains(feature);
    }

    public static final int simm(int x, int nbits) {
        // assert_signed_range(x, nbits);
        return x & ((1 << nbits) - 1);
    }

    public static final boolean isImm(int x, int nbits) {
        // assert_signed_range(x, nbits);
        return simm(x, nbits) == x;
    }

    /**
     * Minimum value for signed immediate ranges.
     */
    public static long minSimm(long nbits) {
        return -(1L << (nbits - 1));
    }

    /**
     * Maximum value for signed immediate ranges.
     */
    public static long maxSimm(long nbits) {
        return (1L << (nbits - 1)) - 1;
    }

    /**
     * Test if imm is within signed immediate range for nbits.
     */
    public static boolean isSimm(long imm, int nbits) {
        return minSimm(nbits) <= imm && imm <= maxSimm(nbits);
    }

    public static boolean isSimm10(long imm) {
        return isSimm(imm, 10);
    }

    public static boolean isSimm11(long imm) {
        return isSimm(imm, 11);
    }

    public static boolean isSimm11(JavaConstant constant) {
        return constant.isNull() || isSimm11(constant.asLong());
    }

    public static boolean isSimm5(JavaConstant constant) {
        return constant.isNull() || isSimm(constant.asLong(), 5);
    }

    public static boolean isSimm13(int imm) {
        return isSimm(imm, 13);
    }

    public static boolean isSimm13(JavaConstant constant) {
        return constant.isNull() || isSimm13(constant.asLong());
    }

    public static boolean isSimm13(long imm) {
        return NumUtil.isInt(imm) && isSimm(imm, 13);
    }

    public static boolean isWordDisp30(long imm) {
        return isSimm(imm, 30 + 2);
    }

    public static final int hi22(int x) {
        return x >>> 10;
    }

    public static final int lo10(int x) {
        return x & ((1 << 10) - 1);
    }

    // @formatter:off
    /**
     * Instruction format for Fmt00 instructions. This abstraction is needed as it
     * makes the patching easier later on.
     * <pre>
     * | 00  |    a   | op2 |               b                         |
     * |31 30|29    25|24 22|21                                      0|
     * </pre>
     */
    // @formatter:on
    protected void fmt00(int a, int op2, int b) {
        assert isImm(a, 5) && isImm(op2, 3) && isImm(b, 22) : String.format("a: 0x%x op2: 0x%x b: 0x%x", a, op2, b);
        this.emitInt(a << 25 | op2 << 22 | b);
    }

    private void op3(Op3s op3, Opfs opf, Register rs1, Register rs2, Register rd) {
        int b = opf.value << 5 | (rs2 == null ? 0 : rs2.encoding);
        fmt(op3.op.op, rd.encoding, op3.value, rs1 == null ? 0 : rs1.encoding, b);
    }

    protected void op3(Op3s op3, Register rs1, Register rs2, Register rd) {
        int b = rs2 == null ? 0 : rs2.encoding;
        int xBit = getXBit(op3);
        fmt(op3.op.op, rd.encoding, op3.value, rs1 == null ? 0 : rs1.encoding, b | xBit);
    }

    protected void op3(Op3s op3, Register rs1, int simm13, Register rd) {
        assert isSimm13(simm13);
        int i = 1 << 13;
        int simm13WithX = simm13 | getXBit(op3);
        fmt(op3.op.op, rd.encoding, op3.value, rs1.encoding, i | simm13WithX & ((1 << 13) - 1));
    }

    // @formatter:off
    /**
     * Branch on Integer Condition Codes.
     * <pre>
     * | 00  |annul| cond| 010 |               disp22                 |
     * |31 30|29   |28 25|24 22|21                                   0|
     * </pre>
     */
    // @formatter:on
    public void bicc(ConditionFlag cond, Annul annul, Label l) {
        bcc(Op2s.Br, cond, annul, l);
    }

    // @formatter:off
    /**
     * Branch on Floating-Point Condition Codes.
     * <pre>
     * | 00  |annul| cond| 110 |               disp22                 |
     * |31 30|29   |28 25|24 22|21                                   0|
     * </pre>
     */
    // @formatter:on
    public void fbcc(ConditionFlag cond, Annul annul, Label l) {
        bcc(Op2s.Fb, cond, annul, l);
    }

    // @formatter:off
    /**
     * Branch on (Integer|Floatingpoint) Condition Codes.
     * <pre>
     * | 00  |annul| cond| op2 |               disp22                 |
     * |31 30|29   |28 25|24 22|21                                   0|
     * </pre>
     */
    // @formatter:on
    private void bcc(Op2s op2, ConditionFlag cond, Annul annul, Label l) {
        int pos = !l.isBound() ? patchUnbound(l) : (l.position() - position()) / 4;
        final int disp = 22;
        assert isSimm(pos, disp);
        pos &= (1 << disp) - 1;
        int a = (annul.flag << 4) | cond.getValue();
        fmt00(a, op2.getValue(), pos);
    }

    // @formatter:off
    /**
     * Branch on Integer Condition Codes with Prediction.
     * <pre>
     * | 00  |an|cond | 001 |cc1 2|p |           disp19               |
     * |31 30|29|28 25|24 22|21 20|19|                               0|
     * </pre>
     */
    // @formatter:on
    public void bpcc(ConditionFlag cond, Annul annul, Label l, CC cc, BranchPredict predictTaken) {
        bpcc(Op2s.Bp, cond, annul, l, cc, predictTaken);
    }

    // @formatter:off
    /**
     * Branch on Integer Condition Codes with Prediction.
     * <pre>
     * | 00  |an|cond | 101 |cc1 2|p |           disp19               |
     * |31 30|29|28 25|24 22|21 20|19|                               0|
     * </pre>
     */
    // @formatter:on
    public void fbpcc(ConditionFlag cond, Annul annul, Label l, CC cc, BranchPredict predictTaken) {
        bpcc(Op2s.Fbp, cond, annul, l, cc, predictTaken);
    }

    // @formatter:off
    /**
     * Used for fbpcc (Float) and bpcc (Integer).
     * <pre>
     * | 00  |an|cond | op2 |cc1 2|p |           disp19               |
     * |31 30|29|28 25|24 22|21 20|19|                               0|
     * </pre>
     */
    // @formatter:on
    private void bpcc(Op2s op2, ConditionFlag cond, Annul annul, Label l, CC cc, BranchPredict predictTaken) {
        int pos = !l.isBound() ? patchUnbound(l) : (l.position() - position()) / 4;
        final int disp = 19;
        assert isSimm(pos, disp);
        pos &= (1 << disp) - 1;
        int a = (annul.flag << 4) | cond.getValue();
        int b = (cc.getValue() << 20) | ((predictTaken.flag) << 19) | pos;
        fmt00(a, op2.getValue(), b);
    }

    // @formatter:off
    /**
     * Branch on Integer Register with Prediction.
     * <pre>
     * | 00  |an| 0|rcond | 011 |d16hi|p | rs1 |    d16lo             |
     * |31 30|29|28|27 25 |24 22|21 20|19|18 14|                     0|
     * </pre>
     */
    // @formatter:on
    public void bpr(RCondition cond, Annul annul, Label l, BranchPredict predictTaken, Register rs1) {
        int pos = !l.isBound() ? patchUnbound(l) : (l.position() - position()) / 4;
        final int disp = 16;
        assert isSimm(pos, disp);
        pos &= (1 << disp) - 1;
        int a = (annul.flag << 4) | cond.getValue();
        int d16hi = (pos >> 13) << 13;
        int d16lo = d16hi ^ pos;
        int b = (d16hi << 20) | (predictTaken.flag << 19) | (rs1.encoding() << 14) | d16lo;
        fmt00(a, Op2s.Bpr.getValue(), b);
    }

    private int patchUnbound(Label label) {
        label.addPatchAt(position());
        return 0;
    }

    public void cbcondw(ConditionFlag cf, Register rs1, Register rs2, Label lab) {
        cbcond(0, 0, cf, rs1, rs2.encoding, lab);
    }

    public void cbcondw(ConditionFlag cf, Register rs1, int rs2, Label lab) {
        assert isSimm(rs2, 5);
        cbcond(0, 1, cf, rs1, rs2 & ((1 << 5) - 1), lab);
    }

    public void cbcondx(ConditionFlag cf, Register rs1, Register rs2, Label lab) {
        cbcond(1, 0, cf, rs1, rs2.encoding, lab);
    }

    public void cbcondx(ConditionFlag cf, Register rs1, int rs2, Label lab) {
        assert isSimm(rs2, 5);
        cbcond(1, 1, cf, rs1, rs2 & ((1 << 5) - 1), lab);
    }

    private void cbcond(int cc2, int i, ConditionFlag cf, Register rs1, int rs2, Label l) {
        int disp10 = !l.isBound() ? patchUnbound(l) : (l.position() - position()) / 4;
        assert isSimm(disp10, 10) && isImm(rs2, 5);
        disp10 &= (1 << 10) - 1;
        final int cLo = cf.value & 0b111;
        final int cHi = cf.value >> 3;
        final int d10Lo = disp10 & ((1 << 8) - 1);
        final int d10Hi = disp10 >> 8;
        int a = cHi << 4 | 0b1000 | cLo;
        int b = cc2 << 21 | d10Hi << D10HI_SHIFT | rs1.encoding << 14 | i << 13 | d10Lo << D10LO_SHIFT | rs2;
        fmt00(a, Op2s.Bpr.value, b);
    }

    // @formatter:off
    /**
     * NOP.
     * <pre>
     * | 00  |00000| 100 |                0                    |
     * |31 30|29 25|24 22|21                                  0|
     * </pre>
     */
    // @formatter:on
    public void nop() {
        emitInt(1 << 24);
    }

    public void sethi(int imm22, Register dst) {
        fmt00(dst.encoding, Op2s.Sethi.value, imm22);
    }

    // @formatter:off
    /**
     * Instruction format for calls.
     * <pre>
     * | 01  |                      disp30                             |
     * |31 30|29                                                      0|
     * </pre>
     */
    // @formatter:on
    public void call(int disp30) {
        assert isImm(disp30, 30);
        int instr = 1 << 30;
        instr |= disp30;
        emitInt(instr);
    }

    public void add(Register rs1, Register rs2, Register rd) {
        op3(Add, rs1, rs2, rd);
    }

    public void add(Register rs1, int simm13, Register rd) {
        op3(Add, rs1, simm13, rd);
    }

    public void addc(Register rs1, Register rs2, Register rd) {
        op3(Addc, rs1, rs2, rd);
    }

    public void addc(Register rs1, int simm13, Register rd) {
        op3(Addc, rs1, simm13, rd);
    }

    public void addcc(Register rs1, Register rs2, Register rd) {
        op3(Addcc, rs1, rs2, rd);
    }

    public void addcc(Register rs1, int simm13, Register rd) {
        op3(Addcc, rs1, simm13, rd);
    }

    public void and(Register rs1, Register rs2, Register rd) {
        op3(And, rs1, rs2, rd);
    }

    public void and(Register rs1, int simm13, Register rd) {
        op3(And, rs1, simm13, rd);
    }

    public void andcc(Register rs1, Register rs2, Register rd) {
        op3(Andcc, rs1, rs2, rd);
    }

    public void andcc(Register rs1, int simm13, Register rd) {
        op3(Andcc, rs1, simm13, rd);
    }

    public void andn(Register rs1, Register rs2, Register rd) {
        op3(Andn, rs1, rs2, rd);
    }

    public void andn(Register rs1, int simm13, Register rd) {
        op3(Andn, rs1, simm13, rd);
    }

    public void andncc(Register rs1, Register rs2, Register rd) {
        op3(Andncc, rs1, rs2, rd);
    }

    public void andncc(Register rs1, int simm13, Register rd) {
        op3(Andncc, rs1, simm13, rd);
    }

    public void movwtos(Register rs2, Register rd) {
        assert isSingleFloatRegister(rd) && isCPURegister(rs2) : String.format("%s %s", rs2, rd);
        op3(Impdep1, Movwtos, null, rs2, rd);
    }

    public void umulxhi(Register rs1, Register rs2, Register rd) {
        op3(Impdep1, UMulxhi, rs1, rs2, rd);
    }

    public void fdtos(Register rs2, Register rd) {
        assert isSingleFloatRegister(rd) && isDoubleFloatRegister(rs2) : String.format("%s %s", rs2, rd);
        op3(Fpop1, Fdtos, null, rs2, rd);
    }

    public void movstouw(Register rs2, Register rd) {
        assert isSingleFloatRegister(rs2) && isCPURegister(rd) : String.format("%s %s", rs2, rd);
        op3(Impdep1, Movstosw, null, rs2, rd);
    }

    public void movstosw(Register rs2, Register rd) {
        assert isSingleFloatRegister(rs2) && isCPURegister(rd) : String.format("%s %s", rs2, rd);
        op3(Impdep1, Movstosw, null, rs2, rd);
    }

    public void movdtox(Register rs2, Register rd) {
        assert isDoubleFloatRegister(rs2) && isCPURegister(rd) : String.format("%s %s", rs2, rd);
        op3(Impdep1, Movdtox, null, rs2, rd);
    }

    public void movxtod(Register rs2, Register rd) {
        assert isCPURegister(rs2) && isDoubleFloatRegister(rd) : String.format("%s %s", rs2, rd);
        op3(Impdep1, Movxtod, null, rs2, rd);
    }

    public void fadds(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fadds, rs1, rs2, rd);
    }

    public void faddd(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Faddd, rs1, rs2, rd);
    }

    public void faddq(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Faddq, rs1, rs2, rd);
    }

    public void fdivs(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fdivs, rs1, rs2, rd);
    }

    public void fdivd(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fdivd, rs1, rs2, rd);
    }

    public void fmovs(Register rs2, Register rd) {
        op3(Fpop1, Fmovs, null, rs2, rd);
    }

    public void fmovd(Register rs2, Register rd) {
        op3(Fpop1, Fmovd, null, rs2, rd);
    }

    public void fmuls(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fmuls, rs1, rs2, rd);
    }

    public void fsmuld(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fsmuld, rs1, rs2, rd);
    }

    public void fmuld(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fmuld, rs1, rs2, rd);
    }

    public void fnegs(Register rs2, Register rd) {
        op3(Fpop1, Fnegs, null, rs2, rd);
    }

    public void fnegd(Register rs2, Register rd) {
        op3(Fpop1, Fnegd, null, rs2, rd);
    }

    /**
     * Helper method to determine if the instruction needs the X bit set.
     */
    private static int getXBit(Op3s op3) {
        switch (op3) {
            case Sllx:
            case Srax:
            case Srlx:
                return 1 << 12;
            default:
                return 0;
        }
    }

    public void fstoi(Register rs2, Register rd) {
        op3(Fpop1, Fstoi, null, rs2, rd);
    }

    public void fstox(Register rs2, Register rd) {
        op3(Fpop1, Fstox, null, rs2, rd);
    }

    public void fdtox(Register rs2, Register rd) {
        op3(Fpop1, Fdtox, null, rs2, rd);
    }

    public void fstod(Register rs2, Register rd) {
        op3(Fpop1, Fstod, null, rs2, rd);
    }

    public void fdtoi(Register rs2, Register rd) {
        op3(Fpop1, Fdtoi, null, rs2, rd);
    }

    public void fitos(Register rs2, Register rd) {
        op3(Fpop1, Fitos, null, rs2, rd);
    }

    public void fitod(Register rs2, Register rd) {
        op3(Fpop1, Fitod, null, rs2, rd);
    }

    public void fxtos(Register rs2, Register rd) {
        op3(Fpop1, Fxtos, null, rs2, rd);
    }

    public void fxtod(Register rs2, Register rd) {
        op3(Fpop1, Fxtod, null, rs2, rd);
    }

    public void fzeros(Register rd) {
        op3(Impdep1, Fzeros, null, null, rd);
    }

    public void fzerod(Register rd) {
        op3(Impdep1, Fzerod, null, null, rd);
    }

    public void flushw() {
        op3(Flushw, g0, g0, g0);
    }

    public void fsqrtd(Register rs2, Register rd) {
        op3(Fpop1, Fsqrtd, null, rs2, rd);
    }

    public void fsqrts(Register rs2, Register rd) {
        op3(Fpop1, Fsqrts, null, rs2, rd);
    }

    public void fabss(Register rs2, Register rd) {
        op3(Fpop1, Fabss, null, rs2, rd);
    }

    public void fabsd(Register rs2, Register rd) {
        op3(Fpop1, Fabsd, null, rs2, rd);
    }

    public void fsubs(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fsubs, rs1, rs2, rd);
    }

    public void fsubd(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fsubd, rs1, rs2, rd);
    }

    // @formatter:off
    /**
     * Instruction format for fcmp.
     * <pre>
     * | 10  | --- |cc1|cc0|desc |   rs1   |   opf  | rs2 |
     * |31 30|29 27|26 |25 |24 19|18     14|13     5|4   0|
     * </pre>
     */
    // @formatter:on
    public void fcmp(CC cc, Opfs opf, Register rs1, Register rs2) {
        int a = cc.value;
        int b = opf.value << 5 | rs2.encoding;
        fmt10(a, Fcmp.value, rs1.encoding, b);
    }

    // @formatter:off
    /**
     * Instruction format for most arithmetic stuff.
     * <pre>
     * |  10 | rd  | op3 | rs1 |   b   |
     * |31 30|29 25|24 19|18 14|13    0|
     * </pre>
     */
    // @formatter:on
    protected void fmt10(int rd, int op3, int rs1, int b) {
        fmt(0b10, rd, op3, rs1, b);
    }

    // @formatter:off
    /**
     * Instruction format for most arithmetic stuff.
     * <pre>
     * |  op | rd  | op3 | rs1 |   b   |
     * |31 30|29 25|24 19|18 14|13    0|
     * </pre>
     */
    // @formatter:on
    protected void fmt(int op, int rd, int op3, int rs1, int b) {
        assert isImm(rd, 5) && isImm(op3, 6) && isImm(b, 14) : String.format("rd: 0x%x op3: 0x%x b: 0x%x", rd, op3, b);
        int instr = op << 30 | rd << 25 | op3 << 19 | rs1 << 14 | b;
        emitInt(instr);
    }

    public void illtrap(int const22) {
        fmt00(0, Op2s.Illtrap.value, const22);
    }

    public void jmpl(Register rs1, Register rs2, Register rd) {
        op3(Jmpl, rs1, rs2, rd);
    }

    public void jmpl(Register rs1, int simm13, Register rd) {
        op3(Jmpl, rs1, simm13, rd);
    }

    public void fmovdcc(ConditionFlag cond, CC cc, Register rs2, Register rd) {
        fmovcc(cond, cc, rs2, rd, Fmovdcc.value);
    }

    public void fmovscc(ConditionFlag cond, CC cc, Register rs2, Register rd) {
        fmovcc(cond, cc, rs2, rd, Fmovscc.value);
    }

    private void fmovcc(ConditionFlag cond, CC cc, Register rs2, Register rd, int opfLow) {
        int opfCC = cc.value;
        int a = opfCC << 11 | opfLow << 5 | rs2.encoding;
        fmt10(rd.encoding, Fpop2.value, cond.value, a);
    }

    public void movcc(ConditionFlag conditionFlag, CC cc, Register rs2, Register rd) {
        movcc(conditionFlag, cc, 0, rs2.encoding, rd);
    }

    public void movcc(ConditionFlag conditionFlag, CC cc, int simm11, Register rd) {
        assert isSimm11(simm11);
        movcc(conditionFlag, cc, 1, simm11 & ((1 << 11) - 1), rd);
    }

    private void movcc(ConditionFlag conditionFlag, CC cc, int i, int imm, Register rd) {
        int cc01 = 0b11 & cc.value;
        int cc2 = cc.isFloat ? 0 : 1;
        int a = cc2 << 4 | conditionFlag.value;
        int b = cc01 << 11 | i << 13 | imm;
        fmt10(rd.encoding, Movcc.value, a, b);
    }

    public void mulx(Register rs1, Register rs2, Register rd) {
        op3(Mulx, rs1, rs2, rd);
    }

    public void mulx(Register rs1, int simm13, Register rd) {
        op3(Mulx, rs1, simm13, rd);
    }

    public void or(Register rs1, Register rs2, Register rd) {
        assert isCPURegister(rs1, rs2, rd) : String.format("%s %s %s", rs1, rs2, rd);
        op3(Or, rs1, rs2, rd);
    }

    public void or(Register rs1, int simm13, Register rd) {
        assert isCPURegister(rs1, rd) : String.format("%s %s", rs1, rd);
        op3(Or, rs1, simm13, rd);
    }

    public void popc(Register rs2, Register rd) {
        op3(Popc, g0, rs2, rd);
    }

    public void popc(int simm13, Register rd) {
        op3(Popc, g0, simm13, rd);
    }

    public void prefetch(SPARCAddress addr, Fcn fcn) {
        Register rs1 = addr.getBase();
        if (addr.getIndex().equals(Register.None)) {
            int dis = addr.getDisplacement();
            assert isSimm13(dis);
            fmt(Prefetch.op.op, fcn.value, Prefetch.value, rs1.encoding, 1 << 13 | dis & ((1 << 13) - 1));
        } else {
            Register rs2 = addr.getIndex();
            fmt(Prefetch.op.op, fcn.value, Prefetch.value, rs1.encoding, rs2.encoding);
        }
    }

    // A.44 Read State Register

    public void rdpc(Register rd) {
        op3(Rd, r5, g0, rd);
    }

    public void restore(Register rs1, Register rs2, Register rd) {
        op3(Restore, rs1, rs2, rd);
    }

    public static final int PC_RETURN_OFFSET = 8;

    public void save(Register rs1, Register rs2, Register rd) {
        op3(Save, rs1, rs2, rd);
    }

    public void save(Register rs1, int simm13, Register rd) {
        op3(Save, rs1, simm13, rd);
    }

    public void sdivx(Register rs1, Register rs2, Register rd) {
        op3(Sdivx, rs1, rs2, rd);
    }

    public void sdivx(Register rs1, int simm13, Register rd) {
        op3(Sdivx, rs1, simm13, rd);
    }

    public void udivx(Register rs1, Register rs2, Register rd) {
        op3(Udivx, rs1, rs2, rd);
    }

    public void udivx(Register rs1, int simm13, Register rd) {
        op3(Udivx, rs1, simm13, rd);
    }

    public void sll(Register rs1, Register rs2, Register rd) {
        op3(Sll, rs1, rs2, rd);
    }

    public void sll(Register rs1, int shcnt32, Register rd) {
        assert isImm(shcnt32, 5);
        op3(Sll, rs1, shcnt32, rd);
    }

    public void sllx(Register rs1, Register rs2, Register rd) {
        op3(Sllx, rs1, rs2, rd);
    }

    public void sllx(Register rs1, int shcnt64, Register rd) {
        assert isImm(shcnt64, 6);
        op3(Sllx, rs1, shcnt64, rd);
    }

    public void sra(Register rs1, Register rs2, Register rd) {
        op3(Sra, rs1, rs2, rd);
    }

    public void sra(Register rs1, int simm13, Register rd) {
        op3(Sra, rs1, simm13, rd);
    }

    public void srax(Register rs1, Register rs2, Register rd) {
        op3(Srax, rs1, rs2, rd);
    }

    public void srax(Register rs1, int shcnt64, Register rd) {
        assert isImm(shcnt64, 6);
        op3(Srax, rs1, shcnt64, rd);
    }

    public void srl(Register rs1, Register rs2, Register rd) {
        op3(Srl, rs1, rs2, rd);
    }

    public void srl(Register rs1, int simm13, Register rd) {
        op3(Srl, rs1, simm13, rd);
    }

    public void srlx(Register rs1, Register rs2, Register rd) {
        op3(Srlx, rs1, rs2, rd);
    }

    public void srlx(Register rs1, int shcnt64, Register rd) {
        assert isImm(shcnt64, 6);
        op3(Srlx, rs1, shcnt64, rd);
    }

    public void fandd(Register rs1, Register rs2, Register rd) {
        op3(Impdep1, Fandd, rs1, rs2, rd);
    }

    public void sub(Register rs1, Register rs2, Register rd) {
        op3(Sub, rs1, rs2, rd);
    }

    public void sub(Register rs1, int simm13, Register rd) {
        op3(Sub, rs1, simm13, rd);
    }

    public void subcc(Register rs1, Register rs2, Register rd) {
        op3(Subcc, rs1, rs2, rd);
    }

    public void subcc(Register rs1, int simm13, Register rd) {
        op3(Subcc, rs1, simm13, rd);
    }

    public void ta(int trap) {
        tcc(Icc, Always, trap);
    }

    public void tcc(CC cc, ConditionFlag flag, int trap) {
        assert isImm(trap, 8);
        int b = cc.value << 11;
        b |= 1 << 13;
        b |= trap;
        fmt10(flag.value, Op3s.Tcc.getValue(), 0, b);
    }

    public void wrccr(Register rs1, Register rs2) {
        op3(Wr, rs1, rs2, r2);
    }

    public void wrccr(Register rs1, int simm13) {
        op3(Wr, rs1, simm13, r2);
    }

    public void xor(Register rs1, Register rs2, Register rd) {
        op3(Xor, rs1, rs2, rd);
    }

    public void xor(Register rs1, int simm13, Register rd) {
        op3(Xor, rs1, simm13, rd);
    }

    public void xorcc(Register rs1, Register rs2, Register rd) {
        op3(Xorcc, rs1, rs2, rd);
    }

    public void xorcc(Register rs1, int simm13, Register rd) {
        op3(Xorcc, rs1, simm13, rd);
    }

    public void xnor(Register rs1, Register rs2, Register rd) {
        op3(Xnor, rs1, rs2, rd);
    }

    public void xnor(Register rs1, int simm13, Register rd) {
        op3(Xnor, rs1, simm13, rd);
    }

    /*
     * Load/Store
     */
    protected void ld(Op3s op3, SPARCAddress addr, Register rd, Asi asi) {
        Register rs1 = addr.getBase();
        if (!addr.getIndex().equals(Register.None)) {
            Register rs2 = addr.getIndex();
            if (asi != null) {
                int b = rs2.encoding;
                b |= asi.value << 5;
                fmt(op3.op.op, rd.encoding, op3.value, rs1.encoding, b);
            } else {
                op3(op3, rs1, rs2, rd);
            }
        } else {
            int imm = addr.getDisplacement();
            op3(op3, rs1, imm, rd);
        }
    }

    protected void ld(Op3s op3, SPARCAddress addr, Register rd) {
        ld(op3, addr, rd, null);
    }

    public void lddf(SPARCAddress src, Register dst) {
        assert isDoubleFloatRegister(dst) : dst;
        ld(Lddf, src, dst);
    }

    public void ldf(SPARCAddress src, Register dst) {
        assert isSingleFloatRegister(dst) : dst;
        ld(Ldf, src, dst);
    }

    public void lduh(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Lduh, src, dst);
    }

    public void ldsh(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Ldsh, src, dst);
    }

    public void ldub(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Ldub, src, dst);
    }

    public void ldsb(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Ldsb, src, dst);
    }

    public void lduw(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Lduw, src, dst);
    }

    public void ldsw(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Ldsw, src, dst);
    }

    public void ldx(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Ldx, src, dst);
    }

    public void ldxa(Register rs1, Register rs2, Register rd, Asi asi) {
        assert SPARC.isCPURegister(rs1, rs2, rd) : format("%s %s %s", rs1, rs2, rd);
        ld(Ldxa, new SPARCAddress(rs1, rs2), rd, asi);
    }

    public void lduwa(Register rs1, Register rs2, Register rd, Asi asi) {
        assert SPARC.isCPURegister(rs1, rs2, rd) : format("%s %s %s", rs1, rs2, rd);
        ld(Lduwa, new SPARCAddress(rs1, rs2), rd, asi);
    }

    protected void st(Op3s op3, Register rs1, SPARCAddress dest) {
        ld(op3, dest, rs1);
    }

    public void stdf(Register rd, SPARCAddress addr) {
        assert isDoubleFloatRegister(rd) : rd;
        st(Stdf, rd, addr);
    }

    public void stf(Register rd, SPARCAddress addr) {
        assert isSingleFloatRegister(rd) : rd;
        st(Stf, rd, addr);
    }

    public void stb(Register rd, SPARCAddress addr) {
        assert isCPURegister(rd) : rd;
        st(Stb, rd, addr);
    }

    public void sth(Register rd, SPARCAddress addr) {
        assert isCPURegister(rd) : rd;
        st(Sth, rd, addr);
    }

    public void stw(Register rd, SPARCAddress addr) {
        assert isCPURegister(rd) : rd;
        st(Stw, rd, addr);
    }

    public void stx(Register rd, SPARCAddress addr) {
        assert isCPURegister(rd) : rd;
        st(Stx, rd, addr);
    }

    public void membar(int barriers) {
        op3(Membar, r15, barriers, g0);
    }

    public void casa(Register rs1, Register rs2, Register rd, Asi asi) {
        ld(Casa, new SPARCAddress(rs1, rs2), rd, asi);
    }

    public void casxa(Register rs1, Register rs2, Register rd, Asi asi) {
        ld(Casxa, new SPARCAddress(rs1, rs2), rd, asi);
    }

    @Override
    public InstructionCounter getInstructionCounter() {
        return new SPARCInstructionCounter(this);
    }

    public void patchAddImmediate(int position, int simm13) {
        int inst = getInt(position);
        assert SPARCAssembler.isSimm13(simm13) : simm13;
        assert (inst >>> 30) == 0b10 : String.format("0x%x", inst);
        assert ((inst >>> 18) & 0b11_1111) == 0 : String.format("0x%x", inst);
        assert (inst & (1 << 13)) != 0 : String.format("0x%x", inst);
        inst = inst & (~((1 << 13) - 1));
        inst |= simm13 & ((1 << 12) - 1);
        emitInt(inst, position);
    }

    public void fpadd32(Register rs1, Register rs2, Register rd) {
        op3(Impdep1, Fpadd32, rs1, rs2, rd);
    }

    public boolean isCbcond(int i) {
        return (i & 0xC1C00000) == 0xC00000;
    }
}
