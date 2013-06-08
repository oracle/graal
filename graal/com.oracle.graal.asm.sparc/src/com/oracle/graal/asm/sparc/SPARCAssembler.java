/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.code.Register;
import com.oracle.graal.api.code.RegisterConfig;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.asm.*;
import com.oracle.graal.hotspot.HotSpotGraalRuntime;
import com.oracle.graal.sparc.SPARC;

/**
 * This class implements an assembler that can encode most SPARC instructions.
 */
public class SPARCAssembler extends AbstractSPARCAssembler {

    // @formatter:off

    public static class Fmt1 {
        public Fmt1(SPARCAssembler masm, int op, int disp30) {
            assert op == 1;
            assert ((disp30 & 0xc0000000) == 0);

            masm.emitInt(op << 30 | disp30);
        }
    }

    public static class Fmt2a {
        public Fmt2a(SPARCAssembler masm, int op, int rd, int op2, int imm22) {
            assert op == 0;
            assert rd < 0x40;
            assert op2 < 0x8;

            masm.emitInt(op << 30 | rd << 25 | op2 << 22 | (imm22 & 0x003fffff));
        }
    }

    public static class Fmt2b {
        public Fmt2b(SPARCAssembler masm, int op, int a, int cond, int op2, int disp22) {
            assert op == 0;
            assert op == 0;
            assert cond < 0x10;
            assert op2 < 0x8;

            masm.emitInt(op << 30 | a << 29 | cond << 25 | op2 << 22 | (disp22 & 0x003fffff));
        }
    }

    public static class Fmt2c {
        public Fmt2c(SPARCAssembler masm, int op, int a, int cond, int op2, int cc, int predict, int disp19) {
            assert predict < 2;
            assert op == 0;
            assert cond < 0x10;
            assert op2 < 0x8;

            masm.emitInt(op << 30 | a << 29 | cond << 25 | op2 << 22 | cc << 20 | predict | (disp19 & 0x0007ffff));
        }
    }

    public static class Fmt2d {
        public Fmt2d(SPARCAssembler masm, int op, int a, int rcond, int op2, int d16hi, int predict, int rs1, int d16lo) {
            assert predict == 0 || predict == 1;
            assert rcond >= 0 && rcond < 0x8;
            assert op == 0;
            assert op2 >= 0 && op2 < 0x8;
            assert rs1 >= 0 && rs1 < 0x20;

            masm.emitInt(op << 30 | a << 29 | rcond << 25 | op2 << 22 | d16hi & 3 | predict << 18 | rs1 << 14 | (d16lo & 0x003fff));
        }
    }

    public static class Fmt2e {
        public Fmt2e(SPARCAssembler asm, int op, int c4lo, int cc2, int rs1, int d10lo, int regOrImmediate) {
            assert op == 0;
            assert (cc2 & 0xFFFFFFFE) == 0;
            assert c4lo >= 0 && rs1 < 0x10;
            assert rs1 >= 0 && rs1 < 0x20;
            assert (regOrImmediate & 0x1F) < 0x20;
            assert (regOrImmediate & 0xFFFFC000) == 0;
            assert (d10lo & 0xFFFFFC00) == 0;

            asm.emitInt(op << 30 | 1 << 28 | 3 << 22 | cc2 << 21 | (d10lo >> 8) << 19 | rs1 << 14 | (d10lo & 0xff) << 5 | regOrImmediate);
        }
    }

    public static class Fmt3a {
        public Fmt3a(SPARCAssembler masm, int op, int rd, int op3, int rs1, int rs2) {
            assert  op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rs2 >= 0 && rs2 < 0x20;
            assert  rd >= 0 &&  rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 |  rs1 << 14 | rs2);
        }
    }

    public static class Fmt3b {
        public Fmt3b(SPARCAssembler masm, int op, int op3, int rs1, int regOrImmediate, int rd) {
            assert  op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert rs1 >= 0 && rs1 < 0x20;
            assert  rd >= 0 &&  rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 |  rs1 << 14 | (regOrImmediate & 0x1fff));
        }
    }

    public static class Fmt3c {
        public Fmt3c(SPARCAssembler masm, int op, int op3, int rs1, int rs2) {
            assert  op == 2;
            assert op3 >= 0 && op3 < 0x40;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rs2 >= 0 && rs2 < 0x20;

            masm.emitInt(op << 30 | op3 << 19 | rs1 << 14 | rs2);
        }
    }

    public static class Fmt3d {
        public Fmt3d(SPARCAssembler masm, int op, int op3, int rs1, int simm13) {
            assert  op == 2;
            assert op3 >= 0 && op3 < 0x40;
            assert rs1 >= 0 && rs1 < 0x20;

            masm.emitInt(op << 30 | op3 << 19 | rs1 << 14 | ImmedTrue | simm13);
        }
    }

    public static class Fmt3e {
        public Fmt3e(SPARCAssembler masm, int op, int op3, int rcond, int rs1, int rs2, int rd) {
            assert  op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert rcond >= 0 && rcond < 0x8;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rs2 >= 0 && rs2 < 0x20;
            assert  rd >= 0 &&  rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 |  rs1 << 14 | rcond << 10 | rs2);
        }
    }

    public static class Fmt3f {
        public Fmt3f(SPARCAssembler masm, int op, int op3, int rcond, int rs1, int simm10, int rd) {
            assert  op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert rs1 >= 0 && rs1 < 0x20;
            assert  rd >= 0 &&  rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | ImmedTrue | rs1 << 14 | rcond << 10 | (simm10 & 0x000003ff));
        }
    }

    public static class Fmt3n {
        public Fmt3n(SPARCAssembler masm, int op, int op3, int opf, int rs2, int rd) {
            assert  op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert opf >= 0 && opf < 0x200;
            assert rs2 >= 0 && rs2 < 0x20;
            assert  rd >= 0 &&  rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | opf << 5 | rs2);
        }
    }

    public static class Fmt3p {
        public Fmt3p(SPARCAssembler masm, int op, int op3, int opf, int rs1, int rs2, int rd) {
            assert  op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert opf >= 0 && opf < 0x200;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rs2 >= 0 && rs2 < 0x20;
            assert  rd >= 0 &&  rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 |  rs1 << 14 | opf << 5 | rs2);
        }
    }

    public static class Fmt3q {
        public Fmt3q(SPARCAssembler masm, int op, int op3, int rs1, int rd) {
            assert  op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert rs1 >= 0 && rs1 < 0x20;
            assert  rd >= 0 &&  rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | rs1 << 14);
        }
    }

    public static class Fmt3r {
        public Fmt3r(SPARCAssembler masm, int op, int fcn, int op3) {
            assert  op == 23;
            assert op3 >= 0 && op3 < 0x40;
            assert fcn >= 0 && fcn < 0x40;

            masm.emitInt(op << 30 | fcn << 25 | op3 << 19);
        }
    }

    public static class Fmt4a {
        public Fmt4a(SPARCAssembler masm, int op, int op3, int cc, int rs1, int regOrImmediate, int rd) {
            assert  op == 2;
            assert rs1 >= 0 && rs1 < 0x20;
            assert  rd >= 0 &&  rd < 0x10;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | rs1 << 14 | ((cc << 11) & 0x000001800) | regOrImmediate);
        }
    }

    public static class Fmt4c {
        public Fmt4c(SPARCAssembler masm, int op, int op3, int cond, int cc, int rs2, int rd) {
            assert op == 2;
            assert op3 >= 0 && op3 < 0x40;
            assert  cc >= 0 &&  cc < 0x8;
            assert cond >= 0 && cond < 0x10;
            assert rs2 >= 0 && rs2 < 0x20;
            assert  rd >= 0 &&  rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | ((cc << 15) & 0x00040000) | cond << 14 | ((cc << 11) & 0x3) | rs2);
        }
    }

    public static class Fmt4d {
        public Fmt4d(SPARCAssembler masm, int op, int op3, int cond, int cc, int simm11, int rd) {
            assert op == 2;
            assert op3 >= 0 && op3 < 0x40;
            assert  cc >= 0 &&  cc < 0x8;
            assert cond >= 0 && cond < 0x10;
            assert  rd >= 0 &&  rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | ImmedTrue | ((cc << 15) & 0x00040000) | cond << 14 | ((cc << 11) & 0x3) | simm11 & 0x00004000);
        }
    }

    public static class Fmt5a {
        public Fmt5a(SPARCAssembler masm, int op, int op3, int op5, int rs1, int rs2, int rs3, int rd) {
            assert   op == 2;
            assert  op3 >= 0 &&  op3 < 0x40;
            assert  op5 >= 0 &&  op5 < 0x10;
            assert  rs1 >= 0 &&  rs1 < 0x20;
            assert  rs2 >= 0 &&  rs2 < 0x20;
            assert  rs3 >= 0 &&  rs3 < 0x20;
            assert  rd  >= 0 &&  rd  < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | rs1 << 14 | rs3 << 9 | op5 << 5 | rs2);
        }
    }

    public static final int ImmedTrue = 0x00002000;

    public enum Ops {
        CallOp(1),
        BranchOp(0),
        ArithOp(2),
        LdstOp(3);

        private final int value;

        private Ops(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Op2s {
        Bpr(3),
        Fb(6),
        Fbp(5),
        Br(2),
        Bp(1),
        Cb(7),
        Sethi(4);

        private final int value;

        private Op2s(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Op3s {
        Add(0x00, "add"),
        And(0x01, "and"),
        Or(0x02, "or"),
        Xor(0x03, "xor"),
        Sub(0x04, "sub"),
        Andn(0x05, "andn"),
        Orn(0x06, "orn"),
        Xnor(0x07, "xnor"),
        Addc(0x08, "addc"),
        Mulx(0x09, "mulx"),
        Umul(0x0A, "umul"),
        Smul(0x0B, "smul"),
        Subc(0x0C, "subc"),
        Udivx(0x0D, "udivx"),
        Udiv(0x0E, "udiv"),
        Sdiv(0x0F, "sdiv"),

        Addcc(0x10, "addcc"),
        Andcc(0x11, "andcc"),
        Orcc(0x12, "orcc"),
        Xorcc(0x13, "xorcc"),
        Subcc(0x14, "subcc"),
        Andncc(0x15, "andncc"),
        Orncc(0x16, "orncc"),
        Xnorcc(0x17, "xnorcc"),
        Addccc(0x18, "addccc"),
        Mulxcc(0x19, "mulxcc"),
        Umulcc(0x1A, "umulcc"),
        Smulcc(0x1B, "smulcc"),
        Subccc(0x1C0, "subccc"),
        Udivcc(0x1E, "udivcc"),
        Sdivcc(0x1F, "sdivcc"),

        Taddcc(0x20, "taddcc"),
        Tsubcc(0x21, "tsubcc"),
        Taddcctv(0x22, "taddcctv"),
        Tsubcctv(0x23, "tsubcctv"),
        Mulscc(0x24, "mulscc"),
        Sll(0x25, "sll"),
        Sllx(0x25, "sllx"),
        Srl(0x26, "srl"),
        Srlx(0x26, "srlx"),
        Sra(0x27, "srax"),
        Srax(0x27, "srax"),
        Rdreg(0x28, "rdreg"),
        Membar(0x28, "membar"),

        Flushw(0x2B, "flushw"),
        Movcc(0x2C, "movcc"),
        Sdivx(0x2D, "sdivx"),
        Popc(0x2E, "popc"),
        Movr(0x2F, "movr"),

        Sir(0x30, "sir"),
        Wrreg(0x30, "wrreg"),
        Saved(0x31, "saved"),

        Fpop1(0x34, "fpop1"),
        Fpop2(0x35, "fpop2"),
        Impdep1(0x36, "impdep1"),
        Impdep2(0x37, "impdep2"),
        Jmpl(0x38, "jmpl"),
        Rett(0x39, "rett"),
        Trap(0x3a, "trap"),
        Flush(0x3b, "flush"),
        Save(0x3c, "save"),
        Restore(0x3d, "restore"),
        Done(0x3e, "done"),
        Retry(0x3e, "retry"),

        Lduw(0x00, "lduw"),
        Ldub(0x01, "ldub"),
        Lduh(0x02, "lduh"),
        Ldd(0x03, "ldd"),
        Stw(0x04, "stw"),
        Stb(0x05, "stb"),
        Sth(0x06, "sth"),
        Std(0x07, "std"),
        Ldsw(0x08, "ldsw"),
        Ldsb(0x09, "ldsb"),
        Ldsh(0x0A, "ldsh"),
        Ldx(0x0b, "ldx"),
        Stx(0x0e, "stx"),

        Ldf(0x20, "ldf"),
        Ldfsr(0x21, "ldfsr"),
        Ldaf(0x22, "ldaf"),
        Lddf(0x23, "lddf"),
        Stf(0x24, "stf"),
        Stfsr(0x25, "stfsr"),
        Staf(0x26, "staf"),
        Stdf(0x27, "stdf");

        private final int value;
        private final String operator;

        private Op3s(int value, String op) {
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

    public enum Op5s {
        Fmadds(0x1),
        Fmaddd(0x2),
        Fmsubs(0x5),
        Fmsubd(0x6),
        Fnmsubs(0x9),
        Fnmsubd(0xA),
        Fnmadds(0xD),
        Fnmaddd(0xE);

        private final int value;

        private Op5s(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Opfs {
        Fmovs(0x01, "fmovs"),
        Fmovd(0x02, "fmovd"),
        Fmovq(0x03, "fmovq"),
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
        Fmul8x16(0x31, "fmul8x16"),
        Fmul8x16au(0x33, "fmul8x16au"),
        Fmul8x16al(0x35, "fmul8x16al"),
        Fmul8sux16(0x36, "fmul8sux16"),
        Fmul8ulx16(0x37, "fmul8ulx16"),
        Fmuld8sux16(0x38, "fmuld8sux16"),
        Fmuld8ulx16(0x39, "fmuld8ulx16"),
        Faligndatag(0x48, "faligndata"),
        Fnhadds(0x71, "fnhadds"),
        Fnhaddd(0x72, "fnhaddd"),
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
        // end VIS3

        // start CAMMELLIA
        CammelliaFl(0x13C, "cammelia_fl"),
        CammelliaFli(0x13D, "cammellia_fli"),
        // end CAMMELLIA

        // start CRYPTO
        Crc32c(0x147, "crc32c"),
        // end CRYPTO

        Fadds(0x41, "fadds"),
        Faddd(0x42, "faddd"),
        Faddq(0x43, "faddq"),
        Fsubs(0x45, "fsubs"),
        Fsubd(0x46, "fsubd"),
        Fsubq(0x47, "fsubq"),
        Fmuls(0x49, "fmuls"),
        Fmuld(0x4A, "fmuld"),
        Fdivs(0x4C, "fdivs"),
        Fdivd(0x4D, "fdivd"),
        Fdivq(0x4E, "fdivq"),

        Fsqrts(0x29, "fsqrts"),
        Fsqrtd(0x2A, "fsqrtd"),
        Fsqrtq(0x2B, "fsqrtq"),

        Fsmuld(0x69, "fsmuld"),
        Fmulq(0x6B, "fmulq"),
        Fdmuldq(0x6E, "fdmulq"),

        Fstoi(0xD1, "fstoi"),
        Fdtoi(0xD2, "fdtoi");

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

    public enum MembarMask {
        StoreStore(1 << 3, "storestore"),
        LoadStore(1 << 2, "loadstore"),
        StoreLoad(1 << 1, "storeload"),
        LoadLoad(1 << 0, "loadload"),
        Sync(1 << 6, "sync"),
        MemIssue(1 << 5, "memissue"),
        LookAside(1 << 4, "lookaside");

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

    public enum CC {
        Icc(4, "icc"),
        Xcc(6, "xcc"),
        Ptrcc(HotSpotGraalRuntime.wordKind() == Kind.Long ? Xcc.getValue() : Icc.getValue(), "ptrcc"),
        Fcc0(0, "fcc0"),
        Fcc1(1, "fcc1"),
        Fcc2(2, "fcc2"),
        Fcc3(3, "fcc3");

        private final int value;
        private final String operator;

        private CC(int value, String op) {
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

    public enum ConditionFlag {
        // for FBfcc & FBPfcc instruction

        F_Never(0, "f_never"),
        F_NotEqual(1, "f_notEqual"),
        F_NotZero(1, "f_notZero"),
        F_LessOrGreater(2, "f_lessOrGreater"),
        F_UnorderedOrLess(3, "f_unorderedOrLess"),
        F_Less(4, "f_less"),
        F_UnorderedOrGreater(5, "f_unorderedOrGreater"),
        F_Greater(6, "f_greater"),
        F_Unordered(7, "f_unordered"),
        F_Always(8, "f_always"),
        F_Equal(9, "f_equal"),
        F_Zero(9, "f_zero"),
        F_UnorderedOrEqual(10, "f_unorderedOrEqual"),
        F_GreaterOrEqual(11, "f_greaterOrEqual"),
        F_UnorderedGreaterOrEqual(12, "f_unorderedGreaterOrEqual"),
        F_LessOrEqual(13, "f_lessOrEqual"),
        F_UnorderedOrLessOrEqual(14, "f_unorderedOrLessOrEqual"),
        F_Ordered(15, "f_ordered"),

        // for integers
        Never(0, "never"),
        Equal(1, "equal"),
        Zero(1, "zero"),
        LessEqual(2, "lessEqual"),
        Less(3, "less"),
        LessEqualUnsigned(4, "lessEqualUnsigned"),
        LessUnsigned(5, "lessUnsigned"),
        CarrySet(5, "carrySet"),
        Negative(6, "negative"),
        OverflowSet(7, "overflowSet"),
        Always(8, "always"),
        NotEqual(9, "notEqual"),
        NotZero(9, "notZero"),
        Greater(10, "greater"),
        GreaterEqual(11, "greaterEqual"),
        GreaterUnsigned(12, "greaterUnsigned"),
        GreaterEqualUnsigned(13, "greaterEqualUnsigned"),
        CarryClear(13, "carryClear"),
        Positive(14, "positive"),
        OverflowClear(15, "overflowClear");

        private final int value;
        private final String operator;

        private ConditionFlag(int value, String op) {
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

    public enum RCondition {
        Rc_z(1, "rc_z"),
        Rc_lez(2, "rc_lez"),
        Rc_lz(3, "rc_lz"),
        Rc_nz(5, "rc_nz"),
        Rc_gz(6, "rc_gz"),
        Rc_gez(7, "rc_gez"),
        Rc_last(Rc_gez.getValue(), "rc_last");

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

    public static int getFloatEncoding(int reg) {
        assert reg < 32;
        return reg;
    }

    public static int getDoubleEncoding(int reg) {
            assert reg < 64 && ((reg & 1) == 0);
            // ignore v8 assertion for now
            return (reg & 0x1e) | ((reg & 0x20) >> 5);
    }

    public static int getQuadEncoding(int reg) {
            assert reg < 64 && ((reg & 3) == 0);
            // ignore v8 assertion for now
            return (reg & 0x1c) | ((reg & 0x20) >> 5);
    }

    public SPARCAssembler(TargetDescription target, @SuppressWarnings("unused") RegisterConfig registerConfig) {
        super(target);
    }

    public static final int sx1 = 0x00001000;

    public static final int simm(int x, int nbits) {
        // assert_signed_range(x, nbits);
        return x & ((1 << nbits) - 1);
    }

    private static final int max13 = ((1 << 12) - 1);
    private static final int min13 = -(1 << 12);

    public static boolean isSimm13(int src) {
        return min13 <= src && src <= max13;
    }

    public static final int hi22(int x) {
        return x >> 10;
    }

    public static final int lo10(int x) {
        return x & ((1 << 10) - 1);
    }

    @Override
    @SuppressWarnings("unused")
    public void jmp(Label l) {
         new Bpa(this, l);
    }

    public static class Add extends Fmt3b {
        public Add(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Add.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Add(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Add.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Addc extends Fmt3b {
        public Addc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Addc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Addc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Addc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Addcc extends Fmt3b {
        public Addcc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Addcc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Addcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Addcc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Addccc extends Fmt3b {
        public Addccc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Addccc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Addccc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Addccc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Addxc extends Fmt3p {
        public Addxc(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Addxc.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Addxccc extends Fmt3p {
        public Addxccc(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Addxccc.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Alignaddr extends Fmt3p {
        public Alignaddr(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.AlignAddress.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Alignaddrl extends Fmt3p {
        public Alignaddrl(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.AlignAddressLittle.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class And extends Fmt3b {
        public And(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.And.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public And(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.And.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Andcc extends Fmt3b {
        public Andcc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Andcc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Andcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Andcc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Andn extends Fmt3b {
        public Andn(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Andn.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Andn(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Andn.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Andncc extends Fmt3b {
        public Andncc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Andncc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Andncc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Andncc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Array8 extends Fmt3p {
        public Array8(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Array8.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Array16 extends Fmt3p {
        public Array16(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Array16.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Array32 extends Fmt3p {
        public Array32(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Array32.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Bmask extends Fmt3p {
        public Bmask(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS2 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Bmask.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Bpa extends Fmt2c {
        public Bpa(SPARCAssembler masm, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Always.getValue(),
                            Op2s.Bp.getValue(), CC.Icc.getValue(), 1, simmm19);
        }
        public Bpa(SPARCAssembler masm, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Always.getValue(),
                            Op2s.Bp.getValue(), CC.Icc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bpcc extends Fmt2c {
        public Bpcc(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.CarryClear.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bpcc(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.CarryClear.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bpcs extends Fmt2c {
        public Bpcs(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.CarrySet.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bpcs(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.CarrySet.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bpe extends Fmt2c {
        public Bpe(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Equal.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bpe(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Equal.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bpg extends Fmt2c {
        public Bpg(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Greater.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bpg(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Greater.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bpge extends Fmt2c {
        public Bpge(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.GreaterEqual.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bpge(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.GreaterEqual.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bpgu extends Fmt2c {
        public Bpgu(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.GreaterUnsigned.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bpgu(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.GreaterUnsigned.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bpl extends Fmt2c {
        public Bpl(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Less.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bpl(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Less.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bple extends Fmt2c {
        public Bple(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.LessEqual.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bple(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.LessEqual.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bpleu extends Fmt2c {
        public Bpleu(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.LessEqualUnsigned.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bpleu(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.LessEqualUnsigned.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bpn extends Fmt2c {
        public Bpn(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Never.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bpn(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Never.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bpne extends Fmt2c {
        public Bpne(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.NotZero.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bpne(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.NotZero.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bpneg extends Fmt2c {
        public Bpneg(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Negative.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bpneg(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Negative.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bppos extends Fmt2c {
        public Bppos(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Positive.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bppos(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.Positive.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bpvc extends Fmt2c {
        public Bpvc(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.OverflowClear.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bpvc(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.OverflowClear.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bpvs extends Fmt2c {
        public Bpvs(SPARCAssembler masm, CC cc, int simmm19) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.OverflowSet.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1, simmm19);
        }
        public Bpvs(SPARCAssembler masm, CC cc, Label label) {
            super(masm, Ops.BranchOp.getValue(), 0, ConditionFlag.OverflowSet.getValue(),
                            Op2s.Bp.getValue(), cc.getValue(), 1,
                            label.isBound() ? label.position() : patchUnbound(masm, label));
        }
    }

    public static class Bshuffle extends Fmt3p {
        public Bshuffle(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS2 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Bshuffle.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class CammelliaFl extends Fmt3p {
        public CammelliaFl(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* CAMELLIA only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.CammelliaFl.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class CammelliaFli extends Fmt3p {
        public CammelliaFli(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* CAMELLIA only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.CammelliaFli.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    private static int patchUnbound(SPARCAssembler masm, Label label) {
        label.addPatchAt(masm.codeBuffer.position());
        return 0;
    }

    public static class Cmask8 extends Fmt3n {
        public Cmask8(SPARCAssembler asm, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Cmask8.getValue(),
                            src2.encoding(), 0);
        }
    }

    public static class Cmask16 extends Fmt3n {
        public Cmask16(SPARCAssembler asm, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Cmask16.getValue(),
                            src2.encoding(), 0);
        }
    }

    public static class Cmask32 extends Fmt3n {
        public Cmask32(SPARCAssembler asm, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Cmask32.getValue(),
                            src2.encoding(), 0);
        }
    }

    public static class Crc32c extends Fmt3p {
        public Crc32c(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* CRYPTO only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Crc32c.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Cwbcc extends Fmt2e {
        public Cwbcc(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarryClear.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwbcc(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Equal.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbcs extends Fmt2e {
        public Cwbcs(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarrySet.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwbcs(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarrySet.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbe extends Fmt2e {
        public Cwbe(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarryClear.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwbe(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Equal.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbg extends Fmt2e {
        public Cwbg(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Greater.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwbg(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Greater.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbge extends Fmt2e {
        public Cwbge(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterEqual.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwbge(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterEqual.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbgu extends Fmt2e {
        public Cwbgu(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterUnsigned.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwbgu(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterUnsigned.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbl extends Fmt2e {
        public Cwbl(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Less.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwbl(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Less.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwble extends Fmt2e {
        public Cwble(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqual.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwble(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqual.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbleu extends Fmt2e {
        public Cwbleu(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqualUnsigned.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwbleu(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqualUnsigned.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbne extends Fmt2e {
        public Cwbne(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.NotEqual.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwbne(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.NotEqual.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbneg extends Fmt2e {
        public Cwbneg(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Negative.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwbneg(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Negative.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbpos extends Fmt2e {
        public Cwbpos(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Positive.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwbpos(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Positive.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbvc extends Fmt2e {
        public Cwbvc(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowClear.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwbvc(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowClear.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbvs extends Fmt2e {
        public Cwbvs(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowSet.getValue(), 0,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cwbvs(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowSet.getValue(), 0,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbcc extends Fmt2e {
        public Cxbcc(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarryClear.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxbcc(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Equal.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbcs extends Fmt2e {
        public Cxbcs(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarrySet.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxbcs(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarrySet.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbe extends Fmt2e {
        public Cxbe(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarryClear.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxbe(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Equal.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbg extends Fmt2e {
        public Cxbg(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Greater.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxbg(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Greater.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbge extends Fmt2e {
        public Cxbge(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterEqual.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxbge(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterEqual.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbgu extends Fmt2e {
        public Cxbgu(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterUnsigned.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxbgu(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterUnsigned.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbl extends Fmt2e {
        public Cxbl(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Less.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxbl(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Less.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxble extends Fmt2e {
        public Cxble(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqual.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxble(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqual.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbleu extends Fmt2e {
        public Cxbleu(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqualUnsigned.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxbleu(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqualUnsigned.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbne extends Fmt2e {
        public Cxbne(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.NotEqual.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxbne(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.NotEqual.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbneg extends Fmt2e {
        public Cxbneg(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Negative.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxbneg(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Negative.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbpos extends Fmt2e {
        public Cxbpos(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Positive.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxbpos(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Positive.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbvc extends Fmt2e {
        public Cxbvc(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowClear.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxbvc(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowClear.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbvs extends Fmt2e {
        public Cxbvs(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowSet.getValue(), 1,
                            src1.encoding(), simm10, src2.encoding());
        }
        public Cxbvs(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowSet.getValue(), 1,
                            src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Edge8cc extends Fmt3p {
        public Edge8cc(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Edge8cc.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Edge8n extends Fmt3p {
        public Edge8n(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Edge8n.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Edge8lcc extends Fmt3p {
        public Edge8lcc(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Edge8lcc.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Edge8ln extends Fmt3p {
        public Edge8ln(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Edge8ln.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Edge16cc extends Fmt3p {
        public Edge16cc(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Edge16cc.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Edge16n extends Fmt3p {
        public Edge16n(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Edge16n.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Edge16lcc extends Fmt3p {
        public Edge16lcc(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Edge16lcc.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Edge16ln extends Fmt3p {
        public Edge16ln(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Edge16ln.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Edge32cc extends Fmt3p {
        public Edge32cc(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Edge32cc.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Edge32n extends Fmt3p {
        public Edge32n(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Edge32n.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Edge32lcc extends Fmt3p {
        public Edge32lcc(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Edge32lcc.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Edge32ln extends Fmt3p {
        public Edge32ln(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Edge32ln.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fadds extends Fmt3p {
        public Fadds(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fadds.getValue(),
                    src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Faddd extends Fmt3p {
        public Faddd(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Faddd.getValue(),
                    src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Faddq extends Fmt3p {
        public Faddq(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Faddq.getValue(),
                    src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Faligndata extends Fmt3p {
        public Faligndata(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Faligndatag.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fdivs extends Fmt3p {
        public Fdivs(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fdivs.getValue(),
                    src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fdivd extends Fmt3p {
        public Fdivd(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fdivd.getValue(),
                    src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fmadds extends Fmt5a {
        public Fmadds(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fmadds.getValue(),
                            src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fmaddd extends Fmt5a {
        public Fmaddd(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fmaddd.getValue(),
                            src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fmean16 extends Fmt3p {
        public Fmean16(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Fmean16.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fmsubs extends Fmt5a {
        public Fmsubs(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fmsubs.getValue(),
                            src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fmsubd extends Fmt5a {
        public Fmsubd(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fmsubd.getValue(),
                            src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fnadds extends Fmt3p {
        public Fnadds(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fnadds.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fnaddd extends Fmt3p {
        public Fnaddd(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fnaddd.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fnmadds extends Fmt5a {
        public Fnmadds(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fnmadds.getValue(),
                            src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fnmaddd extends Fmt5a {
        public Fnmaddd(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fnmaddd.getValue(),
                            src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fnmsubs extends Fmt5a {
        public Fnmsubs(SPARCAssembler masm, Register src1, Register src2, Register src3, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fnmsubs.getValue(),
                            src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fnmsubd extends Fmt5a {
        public Fnmsubd(SPARCAssembler masm, Register src1, Register src2, Register src3, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fnmsubd.getValue(),
                            src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fmuls extends Fmt3p {
        public Fmuls(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fmuls.getValue(),
                    src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fmuld extends Fmt3p {
        public Fmuld(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fmuld.getValue(),
                    src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fmul8x16 extends Fmt3p {
        public Fmul8x16(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Fmul8x16.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fmul8x16au extends Fmt3p {
        public Fmul8x16au(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Fmul8x16.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fmul8x16al extends Fmt3p {
        public Fmul8x16al(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Fmul8x16al.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fmul8sux16 extends Fmt3p {
        public Fmul8sux16(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Fmul8sux16.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fmul8ulx16 extends Fmt3p {
        public Fmul8ulx16(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Fmul8ulx16.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fmuld8sux16 extends Fmt3p {
        public Fmuld8sux16(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Fmuld8sux16.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fmuld8ulx16 extends Fmt3p {
        public Fmuld8ulx16(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Fmuld8ulx16.getValue(),
                            src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fnegs extends Fmt3n {
        public Fnegs(SPARCAssembler masm, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fnegs.getValue(),
                  src2.encoding(), dst.encoding());
        }
    }

    public static class Fnegd extends Fmt3n {
        public Fnegd(SPARCAssembler masm, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fnegd.getValue(),
                  src2.encoding(), dst.encoding());
        }
    }

    public static class Fstoi extends Fmt3n {
        public Fstoi(SPARCAssembler masm, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fstoi.getValue(),
                  src2.encoding(), dst.encoding());
        }
    }

    public static class Fdtoi extends Fmt3n {
        public Fdtoi(SPARCAssembler masm, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fdtoi.getValue(),
                  src2.encoding(), dst.encoding());
        }
    }

    public static class Flushw extends Fmt3r {
        public Flushw(SPARCAssembler masm) {
            super(masm, Ops.ArithOp.getValue(), 0, Op3s.Flushw.getValue());
        }
    }

    public static class Fsqrtd extends Fmt3p {
        public Fsqrtd(SPARCAssembler masm, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fsqrtd.getValue(),
                  0, src2.encoding(), dst.encoding());
        }
    }


    public static class Fsubs extends Fmt3p {
        public Fsubs(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fsubs.getValue(),
                    src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fsubd extends Fmt3p {
        public Fsubd(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fsubd.getValue(),
                    src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fsubq extends Fmt3p {
        public Fsubq(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fsubq.getValue(),
                    src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Jmpl extends Fmt3b {
        public Jmpl(SPARCAssembler asm, SPARCAddress src, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Jmpl.getValue(),
                  src.getBase().encoding(), src.getDisplacement(), dst.encoding());
        }
    }

    public static class Lddf extends Fmt3b {
        public Lddf(SPARCAssembler masm, SPARCAddress src, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Lddf.getValue(),
                  src.getBase().encoding(), src.getDisplacement(), dst.encoding());
        }
    }

    public static class Ldf extends Fmt3b {
        public Ldf(SPARCAssembler masm, SPARCAddress src, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Ldf.getValue(),
                  src.getBase().encoding(), src.getDisplacement(), dst.encoding());
        }
    }

    public static class Ldsb extends Fmt3b {
        public Ldsb(SPARCAssembler masm, SPARCAddress src, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Ldsb.getValue(),
                  src.getBase().encoding(), src.getDisplacement(), dst.encoding());
        }
    }

    public static class Ldsh extends Fmt3b {
        public Ldsh(SPARCAssembler masm, SPARCAddress src, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Ldsh.getValue(),
                  src.getBase().encoding(), src.getDisplacement(), dst.encoding());
        }
    }

    public static class Ldsw extends Fmt3b {
        public Ldsw(SPARCAssembler masm, SPARCAddress src, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Ldsw.getValue(),
                  src.getBase().encoding(), src.getDisplacement(), dst.encoding());
        }
    }

    public static class Lduw extends Fmt3b {
        public Lduw(SPARCAssembler masm, SPARCAddress src, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Lduw.getValue(),
                  src.getBase().encoding(), src.getDisplacement(), dst.encoding());
        }
    }

    public static class Ldx extends Fmt3b {
        public Ldx(SPARCAssembler masm, SPARCAddress src, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Ldx.getValue(),
                  src.getBase().encoding(), src.getDisplacement(), dst.encoding());
        }
    }

    public static class Membar extends Fmt3b {
        public Membar(SPARCAssembler masm, int barriers) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Membar.getValue(),
                  SPARC.r15.encoding(), ImmedTrue | barriers, SPARC.r0.encoding());
        }
    }

    public static class Movcc extends Fmt4c {
        public Movcc(SPARCAssembler masm, ConditionFlag cond, CC cca, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Movcc.getValue(), cond.getValue(), cca.getValue(),
                  src2.encoding(), dst.encoding());
        }
        public Movcc(SPARCAssembler masm, ConditionFlag cond, CC cca, int simm11a, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Movcc.getValue(), cond.getValue(), cca.getValue(),
                  simm11a, dst.encoding());
        }
    }

    public static class Movr extends Fmt3f {
        public Movr(SPARCAssembler masm, RCondition rc,  Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Movr.getValue(), rc.getValue(),
                  src1.encoding(), src2.encoding(), dst.encoding());
        }
        public Movr(SPARCAssembler masm, RCondition rc, Register src1, int simm10, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Movr.getValue(), rc.getValue(),
                    src1.encoding(), simm10, dst.encoding());
        }
    }

    @Deprecated
    public static class Mulscc extends Fmt3b {
        @Deprecated
        public Mulscc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Mulscc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        @Deprecated
        public Mulscc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Mulscc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Mulx extends Fmt3b {
        public Mulx(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Mulx.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Mulx(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Mulx.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class NullCheck extends Fmt3b {
        public NullCheck(SPARCAssembler masm, SPARCAddress src) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Ldx.getValue(),
                            src.getBase().encoding(), src.getDisplacement(), SPARC.r0.encoding());
        }
    }

    public static class Or extends Fmt3b {
        public Or(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Or.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Or(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Or.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Orcc extends Fmt3b {
        public Orcc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Orcc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Orcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Orcc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Orn extends Fmt3b {
        public Orn(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Orn.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Orn(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Orn.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Orncc extends Fmt3b {
        public Orncc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Orncc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Orncc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Orncc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Popc extends Fmt3b {
        public Popc(SPARCAssembler masm, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Popc.getValue(), 0, simm13, dst.encoding());
        }
        public Popc(SPARCAssembler masm, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Popc.getValue(), 0, src2.encoding(), dst.encoding());
        }
    }

    // A.44 Read State Register

    @Deprecated
    public static class Rdy extends Fmt3q {
        public Rdy(SPARCAssembler masm, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rdreg.getValue(), 0, dst.encoding());
        }
    }

    public static class Rdccr extends Fmt3q {
        public Rdccr(SPARCAssembler masm, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rdreg.getValue(), 2, dst.encoding());
        }
    }

    public static class Rdasi extends Fmt3q {
        public Rdasi(SPARCAssembler masm, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rdreg.getValue(), 3, dst.encoding());
        }
    }

    public static class Rdtick extends Fmt3q {
        public Rdtick(SPARCAssembler masm, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rdreg.getValue(), 4, dst.encoding());
        }
    }

    public static class Rdpc extends Fmt3q {
        public Rdpc(SPARCAssembler masm, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rdreg.getValue(), 5, dst.encoding());
        }
    }

    public static class Rdfprs extends Fmt3q {
        public Rdfprs(SPARCAssembler masm, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rdreg.getValue(), 6, dst.encoding());
        }
    }

    public static class Restore extends Fmt3b {
        public Restore(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Restore.getValue(),
                  src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Restored extends Fmt3r {
        public Restored(SPARCAssembler asm) {
            super(asm, Ops.ArithOp.getValue(), 1, Op3s.Saved.getValue());
        }
    }

    public static class Return extends Fmt3d {
        public Return(SPARCAssembler masm, Register src1, int simm13) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rett.getValue(), src1.encoding(), simm13);
        }
        public Return(SPARCAssembler masm, Register src1, Register src2) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rett.getValue(), src1.encoding(), src2.encoding());
        }
    }

    public static class Save extends Fmt3b {
        public Save(SPARCAssembler asm, Register src1, Register src2, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Save.getValue(),
                  src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Saved extends Fmt3r {
        public Saved(SPARCAssembler asm) {
            super(asm, Ops.ArithOp.getValue(), 0, Op3s.Saved.getValue());
        }
    }

    @Deprecated
    public static class Sdiv extends Fmt3b {
        @Deprecated
        public Sdiv(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sdiv.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        @Deprecated
        public Sdiv(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sdiv.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    @Deprecated
    public static class Sdivcc extends Fmt3b {
        @Deprecated
        public Sdivcc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sdivcc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        @Deprecated
        public Sdivcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sdivcc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Sdivx extends Fmt3b {
        public Sdivx(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sdivx.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Sdivx(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sdivx.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Sethi extends Fmt2a {
        public Sethi(SPARCAssembler masm, int simm22, Register dst) {
            super(masm, Ops.BranchOp.getValue(), Op2s.Sethi.getValue(), simm22, dst.encoding());
        }
    }

    public static class Sir extends Fmt3b {
        public Sir(SPARCAssembler asm, int simm13) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Sir.getValue(),
                  SPARC.r0.encoding(), simm13, SPARC.r15.encoding());
        }
    }

    public static class Sll extends Fmt3b {
        public Sll(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sll.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Sll(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sll.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Sllx extends Fmt3b {
        public Sllx(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sllx.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Sllx(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sllx.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Smul extends Fmt3b {
        public Smul(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Smul.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Smul(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Smul.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    @Deprecated
    public static class Smulcc extends Fmt3b {
        public Smulcc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Smulcc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Smulcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Smulcc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Sra extends Fmt3b {
        public Sra(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sra.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Sra(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sra.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Srax extends Fmt3b {
        public Srax(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Srax.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Srax(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Srax.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Srl extends Fmt3b {
        public Srl(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Srl.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Srl(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Srl.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Srlx extends Fmt3b {
        public Srlx(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Srlx.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Srlx(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Srlx.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    @Deprecated
    public static class Stbar extends Fmt3b {
        public Stbar(SPARCAssembler masm) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Membar.getValue(),
                  SPARC.r15.encoding(), 0, SPARC.r0.encoding());
        }
    }

    public static class Stb extends Fmt3b {
        public Stb(SPARCAssembler masm, Register dst, SPARCAddress addr) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Stb.getValue(),
                            addr.getBase().encoding(), addr.getDisplacement(), dst.encoding());
        }
    }

    public static class Sth extends Fmt3b {
        public Sth(SPARCAssembler masm, Register dst, SPARCAddress addr) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sth.getValue(),
                            addr.getBase().encoding(), addr.getDisplacement(), dst.encoding());
        }
    }

    public static class Stw extends Fmt3b {
        public Stw(SPARCAssembler masm, Register dst, SPARCAddress addr) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Stw.getValue(),
                            addr.getBase().encoding(), addr.getDisplacement(), dst.encoding());
        }
    }

    public static class Stx extends Fmt3b {
        public Stx(SPARCAssembler masm, Register dst, SPARCAddress addr) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Stx.getValue(),
                            addr.getBase().encoding(), addr.getDisplacement(), dst.encoding());
        }
    }

    public static class Sub extends Fmt3b {
        public Sub(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sub.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Sub(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Sub.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Subc extends Fmt3b {
        public Subc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Subc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Subc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Subc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Subcc extends Fmt3b {
        public Subcc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Subcc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Subcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Subcc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Subccc extends Fmt3b {
        public Subccc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Subccc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Subccc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Subccc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Ta extends Fmt4a {
        public Ta(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.Always.getValue());
        }
        public Ta(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.Always.getValue());
        }
    }

    public static class Taddcc extends Fmt3b {
        public Taddcc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Taddcc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Taddcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Taddcc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    @Deprecated
    public static class Taddcctv extends Fmt3b {
        @Deprecated
        public Taddcctv(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Taddcctv.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        @Deprecated
        public Taddcctv(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Taddcctv.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Tcc extends Fmt4a {
        public Tcc(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.CarryClear.getValue());
        }
        public Tcc(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.CarryClear.getValue());
        }
    }

    public static class Tcs extends Fmt4a {
        public Tcs(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.CarrySet.getValue());
        }
        public Tcs(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.CarrySet.getValue());
        }
    }

    public static class Te extends Fmt4a {
        public Te(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.Equal.getValue());
        }
        public Te(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.Equal.getValue());
        }
    }

    public static class Tg extends Fmt4a {
        public Tg(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.Greater.getValue());
        }
        public Tg(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.Greater.getValue());
        }
    }

    public static class Tge extends Fmt4a {
        public Tge(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.GreaterEqual.getValue());
        }
        public Tge(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.GreaterEqual.getValue());
        }
    }

    public static class Tle extends Fmt4a {
        public Tle(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.LessEqual.getValue());
        }
        public Tle(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.LessEqual.getValue());
        }
    }

    public static class Tleu extends Fmt4a {
        public Tleu(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.LessEqualUnsigned.getValue());
        }
        public Tleu(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.LessEqualUnsigned.getValue());
        }
    }

    public static class Tn extends Fmt4a {
        public Tn(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.Never.getValue());
        }
        public Tn(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.Never.getValue());
        }
    }

    public static class Tne extends Fmt4a {
        public Tne(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.NotEqual.getValue());
        }
        public Tne(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.NotEqual.getValue());
        }
    }

    public static class Tneg extends Fmt4a {
        public Tneg(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.Negative.getValue());
        }
        public Tneg(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.Negative.getValue());
        }
    }

    public static class Tpos extends Fmt4a {
        public Tpos(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.Positive.getValue());
        }
        public Tpos(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.Positive.getValue());
        }
    }

    public static class Tsubcc extends Fmt3b {
        public Tsubcc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Tsubcc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Tsubcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Tsubcc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    @Deprecated
    public static class Tsubcctv extends Fmt3b {
        @Deprecated
        public Tsubcctv(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Tsubcctv.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        @Deprecated
        public Tsubcctv(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Tsubcctv.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Tvc extends Fmt4a {
        public Tvc(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.OverflowClear.getValue());
        }
        public Tvc(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.OverflowClear.getValue());
        }
    }

    public static class Tvs extends Fmt4a {
        public Tvs(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), trap, ConditionFlag.OverflowSet.getValue());
        }
        public Tvs(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(),
                  src1.encoding(), src2.encoding(), ConditionFlag.OverflowSet.getValue());
        }
    }

    @Deprecated
    public static class Udiv extends Fmt3b {
        @Deprecated
        public Udiv(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Udiv.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        @Deprecated
        public Udiv(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Udiv.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Udivcc extends Fmt3b {
        public Udivcc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Udivcc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Udivcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Udivcc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Udivx extends Fmt3b {
        public Udivx(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Udivx.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Udivx(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Udivx.getValue(), dst.encoding(), src2.encoding(), src1.encoding());
        }
    }

    public static class Umul extends Fmt3b {
        public Umul(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Umul.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Umul(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Umul.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Umulcc extends Fmt3b {
        public Umulcc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Umulcc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Umulcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Umulcc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    @Deprecated
    public static class Wry extends Fmt3b {
        @Deprecated
        public Wry(SPARCAssembler masm, int simm13, Register src2) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Wrreg.getValue(), 0, src2.encoding(), simm13);
        }
        @Deprecated
        public Wry(SPARCAssembler masm, Register src1, Register src2) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Wrreg.getValue(), 0, src2.encoding(), src1.encoding());
        }
    }

    public static class Wrccr extends Fmt3b {
        public Wrccr(SPARCAssembler masm, int simm13, Register src2) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Wrreg.getValue(), 2, src2.encoding(), simm13);
        }
        public Wrccr(SPARCAssembler masm, Register src1, Register src2) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Wrreg.getValue(), 2, src2.encoding(), src1.encoding());
        }
    }

    public static class Wrasi extends Fmt3b {
        public Wrasi(SPARCAssembler masm, int simm13, Register src2) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Wrreg.getValue(), 3, src2.encoding(), simm13);
        }
        public Wrasi(SPARCAssembler masm, Register src1, Register src2) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Wrreg.getValue(), 3, src2.encoding(), src1.encoding());
        }
    }

    public static class Wrfprs extends Fmt3b {
        public Wrfprs(SPARCAssembler masm, int simm13, Register src2) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Wrreg.getValue(), 6, src2.encoding(), simm13);
        }
        public Wrfprs(SPARCAssembler masm, Register src1, Register src2) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Wrreg.getValue(), 6, src2.encoding(), src1.encoding());
        }
    }

    public static class Xor extends Fmt3b {
        public Xor(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Xor.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Xor(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Xor.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Xorcc extends Fmt3b {
        public Xorcc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Xorcc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Xorcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Xorcc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Xnor extends Fmt3b {
        public Xnor(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Xnor.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Xnor(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Xnor.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }

    public static class Xnorcc extends Fmt3b {
        public Xnorcc(SPARCAssembler masm, Register src1, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Xnorcc.getValue(), src1.encoding(), simm13, dst.encoding());
        }
        public Xnorcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Xnorcc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }
    }
}
