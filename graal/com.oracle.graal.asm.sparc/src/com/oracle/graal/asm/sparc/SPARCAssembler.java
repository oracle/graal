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
import com.oracle.graal.hotspot.HotSpotGraalRuntime;

/**
 * This class implements an assembler that can encode most SPARC instructions.
 */
public class SPARCAssembler extends AbstractSPARCAssembler {

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
        public Fmt3b(SPARCAssembler masm, int op, int rd, int op3, int rs1, int simm13) {
            assert  op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert rs1 >= 0 && rs1 < 0x20;
            assert  rd >= 0 &&  rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 |  rs1 << 14 | (simm13 & 0x1fff));
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

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 |  rs1 << 14 | rcond << 10 | (simm10 & 0x000003ff));
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

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 |  rs1 << 14);
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

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | ((cc << 15) & 0x00040000) | cond << 14 | ((cc << 11) & 0x3) | simm11 & 0x00004000);
        }
    }

    public static final int ImmedTrue = 0x00002000;

    public enum Ops {
        CallOp(0x40000000),
        BranchOp(0x00000000),
        ArithOp(0x80000000),
        LdstOp(0xC0000000);

        private final int value;

        private Ops(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Op3s {
        Add((0x00 << 19) & 0x01F80000, "add"),
        And((0x01 << 19) & 0x01F80000, "and"),
        Or((0x02 << 19) & 0x01F80000, "or"),
        Xor((0x03 << 19) & 0x01F80000, "xor"),
        Sub((0x04 << 19) & 0x01F80000, "sub"),
        Andn((0x05 << 19) & 0x01F80000, "andn"),
        Orn((0x06 << 19) & 0x01F80000, "orn"),
        Xnor((0x07 << 19) & 0x01F80000, "xnor"),
        Addc((0x08 << 19) & 0x01F80000, "addc"),
        Mulx((0x09 << 19) & 0x01F80000, "mulx"),
        Umul((0x0A << 19) & 0x01F80000, "umul"),
        Smul((0x0B << 19) & 0x01F80000, "smul"),
        Subc((0x0C << 19) & 0x01F80000, "subc"),
        Udivx((0x0D << 19) & 0x01F80000, "udivx"),
        Udiv((0x0E << 19) & 0x01F80000, "udiv"),
        Sdiv((0x0F << 19) & 0x01F80000, "sdiv"),

        Addcc((0x10 << 19) & 0x01F80000, "addcc"),
        Andcc((0x11 << 19) & 0x01F80000, "andcc"),
        Orcc((0x12 << 19) & 0x01F80000, "orcc"),
        Xorcc((0x13 << 19) & 0x01F80000, "xorcc"),
        Subcc((0x14 << 19) & 0x01F80000, "subcc"),
        Andncc((0x15 << 19) & 0x01F80000, "andncc"),
        Orncc((0x16 << 19) & 0x01F80000, "orncc"),
        Xnorcc((0x17 << 19) & 0x01F80000, "xnorcc"),
        Addccc((0x18 << 19) & 0x01F80000, "addccc"),
        Mulxcc((0x19 << 19) & 0x01F80000, "mulxcc"),
        Umulcc((0x1A << 19) & 0x01F80000, "umulcc"),
        Smulcc((0x1B << 19) & 0x01F80000, "smulcc"),
        Subccc((0x1C << 19) & 0x01F80000, "subccc"),
        Udivcc((0x1E << 19) & 0x01F80000, "udivcc"),
        Sdivcc((0x1F << 19) & 0x01F80000, "sdivcc"),

        Taddcc((0x20 << 19) & 0x01F80000, "taddcc"),
        Tsubcc((0x21 << 19) & 0x01F80000, "tsubcc"),
        Taddcctv((0x22 << 19) & 0x01F80000, "taddcctv"),
        Tsubcctv((0x23 << 19) & 0x01F80000, "tsubcctv"),
        Mulscc((0x23 << 19) & 0x01F80000, "mulscc"),
        Sll((0x25 << 19) & 0x01F80000, "sll"),
        Sllx((0x25 << 19) & 0x01F80000, "sllx"),
        Srl((0x26 << 19) & 0x01F80000, "srl"),
        Srlx((0x26 << 19) & 0x01F80000, "srlx"),
        Sra((0x27 << 19) & 0x01F80000, "srax"),
        Srax((0x27 << 19) & 0x01F80000, "srax"),
        Rdreg((0x28 << 19) & 0x01F80000, "rdreg"),
        Membar((0x28 << 19) & 0x01F80000, "membar"),

        Flushw((0x2B << 19) & 0x01F80000, "flushw"),
        Movcc((0x2C << 19) & 0x01F80000, "movcc"),
        Sdivx((0x2D << 19) & 0x01F80000, "sdivx"),
        Popc((0x2E << 19) & 0x01F80000, "popc"),
        Movr((0x2F << 19) & 0x01F80000, "movr"),

        Sir((0x30 << 19) & 0x01F80000, "sir"),
        Wrreg((0x30 << 19) & 0x01F80000, "wrreg"),
        Saved((0x31 << 19) & 0x01F80000, "saved"),

        Fpop1((0x34 << 19) & 0x01F80000, "fpop1"),
        Fpop2((0x35 << 19) & 0x01F80000, "fpop2"),
        Impdep1((0x36 << 19) & 0x01F80000, "impdep1"),
        Impdep2((0x37 << 19) & 0x01F80000, "impdep2"),
        Jmpl((0x38 << 19) & 0x01F80000, "jmpl"),
        Rett((0x39 << 19) & 0x01F80000, "rett"),
        Trap((0x3a << 19) & 0x01F80000, "trap"),
        Flush((0x3b << 19) & 0x01F80000, "flush"),
        Save((0x3c << 19) & 0x01F80000, "save"),
        Restore((0x3d << 19) & 0x01F80000, "restore"),
        Done((0x3e << 19) & 0x01F80000, "done"),
        Retry((0x3e << 19) & 0x01F80000, "retry");

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

    public enum Opfs {
        Fadds((0x41 << 5) & 0x00003FE0, "fadds"),
        Faddd((0x42 << 5) & 0x00003FE0, "faddd"),
        Faddq((0x43 << 5) & 0x00003FE0, "faddq"),
        Fsubs((0x45 << 5) & 0x00003FE0, "fsubs"),
        Fsubd((0x46 << 5) & 0x00003FE0, "fsubd"),
        Fsubq((0x47 << 5) & 0x00003FE0, "fsubq");

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

    public enum Condition {
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

        private Condition(int value, String op) {
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

    private static int fcn(int val) {
        assert val < 0x20;
        return (val << 25);
    }

    public static class Add extends Fmt3b {
        public Add(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Add.getValue(), src2.encoding(), simm13);
        }
        public Add(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Add.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Addc extends Fmt3b {
        public Addc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Addc.getValue(), src2.encoding(), simm13);
        }
        public Addc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Addc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Addcc extends Fmt3b {
        public Addcc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Addcc.getValue(), src2.encoding(), simm13);
        }
        public Addcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Addcc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Addccc extends Fmt3b {
        public Addccc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Addccc.getValue(), src2.encoding(), simm13);
        }
        public Addccc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Addccc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class And extends Fmt3b {
        public And(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.And.getValue(), src2.encoding(), simm13);
        }
        public And(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.And.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Andcc extends Fmt3b {
        public Andcc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Andcc.getValue(), src2.encoding(), simm13);
        }
        public Andcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Andcc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Andn extends Fmt3b {
        public Andn(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Andn.getValue(), src2.encoding(), simm13);
        }
        public Andn(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Andn.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Andncc extends Fmt3b {
        public Andncc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Andncc.getValue(), src2.encoding(), simm13);
        }
        public Andncc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Andncc.getValue(), src2.encoding(), src1.encoding());
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

    public final void flushw() {
        emitInt(Ops.ArithOp.getValue() | Op3s.Flushw.getValue());
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

    public static class Membar extends Fmt3b {
        public Membar(SPARCAssembler masm, MembarMask mask) {
            super(masm, Ops.ArithOp.getValue(), 0, Op3s.Membar.getValue(), 0xf, mask.getValue());
        }
    }

    public static class Movcc extends Fmt4c {
        public Movcc(SPARCAssembler masm, Condition cond, CC cca, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Movcc.getValue(), cond.getValue(), cca.getValue(),
                  src2.encoding(), dst.encoding());
        }
        public Movcc(SPARCAssembler masm, Condition cond, CC cca, int simm11a, Register dst) {
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
        public Mulscc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Mulscc.getValue(), src2.encoding(), simm13);
        }
        @Deprecated
        public Mulscc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Mulscc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Mulx extends Fmt3b {
        public Mulx(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Mulx.getValue(), src2.encoding(), simm13);
        }
        public Mulx(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Mulx.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Or extends Fmt3b {
        public Or(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Or.getValue(), src2.encoding(), simm13);
        }
        public Or(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Or.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Orcc extends Fmt3b {
        public Orcc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Orcc.getValue(), src2.encoding(), simm13);
        }
        public Orcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Orcc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Orn extends Fmt3b {
        public Orn(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Orn.getValue(), src2.encoding(), simm13);
        }
        public Orn(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Orn.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Orncc extends Fmt3b {
        public Orncc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Orncc.getValue(), src2.encoding(), simm13);
        }
        public Orncc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Orncc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Popc extends Fmt3b {
        public Popc(SPARCAssembler masm, int simm13, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Popc.getValue(), 0, simm13);
        }
        public Popc(SPARCAssembler masm, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Popc.getValue(), 0, src2.encoding());
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

    public final void restored() {
        emitInt(Ops.ArithOp.getValue() | Op3s.Saved.getValue() | fcn(1));
    }

    public final void saved() {
        emitInt(Ops.ArithOp.getValue() | Op3s.Saved.getValue() | fcn(0));
    }

    @Deprecated
    public static class Sdiv extends Fmt3b {
        @Deprecated
        public Sdiv(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sdiv.getValue(), src2.encoding(), simm13);
        }
        @Deprecated
        public Sdiv(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sdiv.getValue(), src2.encoding(), src1.encoding());
        }
    }

    @Deprecated
    public static class Sdivcc extends Fmt3b {
        @Deprecated
        public Sdivcc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sdivcc.getValue(), src2.encoding(), simm13);
        }
        @Deprecated
        public Sdivcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sdivcc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Sdivx extends Fmt3b {
        public Sdivx(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sdivx.getValue(), src2.encoding(), simm13);
        }
        public Sdivx(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sdivx.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public final void sir(int simm13a) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Sir.getValue() | ImmedTrue | simm(simm13a, 13));
    }

    public static class Sll extends Fmt3b {
        public Sll(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sll.getValue(), src2.encoding(), simm13);
        }
        public Sll(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sll.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Sllx extends Fmt3b {
        public Sllx(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sllx.getValue(), src2.encoding(), simm13);
        }
        public Sllx(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sllx.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Smul extends Fmt3b {
        public Smul(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Smul.getValue(), src2.encoding(), simm13);
        }
        public Smul(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Smul.getValue(), src2.encoding(), src1.encoding());
        }
    }

    @Deprecated
    public static class Smulcc extends Fmt3b {
        public Smulcc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Smulcc.getValue(), src2.encoding(), simm13);
        }
        public Smulcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Smulcc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Sra extends Fmt3b {
        public Sra(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sra.getValue(), src2.encoding(), simm13);
        }
        public Sra(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sra.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Srax extends Fmt3b {
        public Srax(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Srax.getValue(), src2.encoding(), simm13);
        }
        public Srax(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Srax.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Srl extends Fmt3b {
        public Srl(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Srl.getValue(), src2.encoding(), simm13);
        }
        public Srl(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Srl.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Srlx extends Fmt3b {
        public Srlx(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Srlx.getValue(), src2.encoding(), simm13);
        }
        public Srlx(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Srlx.getValue(), src2.encoding(), src1.encoding());
        }
    }

    @Deprecated
    public final void stbar() {
        emitInt(Ops.ArithOp.getValue() | Op3s.Membar.getValue() | 0x0003C000);
    }

    public static class Sub extends Fmt3b {
        public Sub(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sub.getValue(), src2.encoding(), simm13);
        }
        public Sub(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Sub.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Subc extends Fmt3b {
        public Subc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Subc.getValue(), src2.encoding(), simm13);
        }
        public Subc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Subc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Subcc extends Fmt3b {
        public Subcc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Subcc.getValue(), src2.encoding(), simm13);
        }
        public Subcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Subcc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Subccc extends Fmt3b {
        public Subccc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Subccc.getValue(), src2.encoding(), simm13);
        }
        public Subccc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Subccc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Taddcc extends Fmt3b {
        public Taddcc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Taddcc.getValue(), src2.encoding(), simm13);
        }
        public Taddcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Taddcc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    @Deprecated
    public static class Taddcctv extends Fmt3b {
        @Deprecated
        public Taddcctv(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Taddcctv.getValue(), src2.encoding(), simm13);
        }
        @Deprecated
        public Taddcctv(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Taddcctv.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Tsubcc extends Fmt3b {
        public Tsubcc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Tsubcc.getValue(), src2.encoding(), simm13);
        }
        public Tsubcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Tsubcc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    @Deprecated
    public static class Tsubcctv extends Fmt3b {
        @Deprecated
        public Tsubcctv(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Tsubcctv.getValue(), src2.encoding(), simm13);
        }
        @Deprecated
        public Tsubcctv(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Tsubcctv.getValue(), src2.encoding(), src1.encoding());
        }
    }

    @Deprecated
    public static class Udiv extends Fmt3b {
        @Deprecated
        public Udiv(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Udiv.getValue(), src2.encoding(), simm13);
        }
        @Deprecated
        public Udiv(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Udiv.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Udivcc extends Fmt3b {
        public Udivcc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Udivcc.getValue(), src2.encoding(), simm13);
        }
        public Udivcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Udivcc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Udivx extends Fmt3b {
        public Udivx(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Udivx.getValue(), src2.encoding(), simm13);
        }
        public Udivx(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Udivx.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Umul extends Fmt3b {
        public Umul(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Umul.getValue(), src2.encoding(), simm13);
        }
        public Umul(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Umul.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Umulcc extends Fmt3b {
        public Umulcc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Umulcc.getValue(), src2.encoding(), simm13);
        }
        public Umulcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Umulcc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    @Deprecated
    public static class Wry extends Fmt3b {
        @Deprecated
        public Wry(SPARCAssembler masm, int simm13, Register src2) {
            super(masm, Ops.ArithOp.getValue(), 0, Op3s.Wrreg.getValue(), src2.encoding(), simm13);
        }
        @Deprecated
        public Wry(SPARCAssembler masm, Register src1, Register src2) {
            super(masm, Ops.ArithOp.getValue(), 0, Op3s.Wrreg.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Wrccr extends Fmt3b {
        public Wrccr(SPARCAssembler masm, int simm13, Register src2) {
            super(masm, Ops.ArithOp.getValue(), 2, Op3s.Wrreg.getValue(), src2.encoding(), simm13);
        }
        public Wrccr(SPARCAssembler masm, Register src1, Register src2) {
            super(masm, Ops.ArithOp.getValue(), 2, Op3s.Wrreg.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Wrasi extends Fmt3b {
        public Wrasi(SPARCAssembler masm, int simm13, Register src2) {
            super(masm, Ops.ArithOp.getValue(), 3, Op3s.Wrreg.getValue(), src2.encoding(), simm13);
        }
        public Wrasi(SPARCAssembler masm, Register src1, Register src2) {
            super(masm, Ops.ArithOp.getValue(), 3, Op3s.Wrreg.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Wrfprs extends Fmt3b {
        public Wrfprs(SPARCAssembler masm, int simm13, Register src2) {
            super(masm, Ops.ArithOp.getValue(), 6, Op3s.Wrreg.getValue(), src2.encoding(), simm13);
        }
        public Wrfprs(SPARCAssembler masm, Register src1, Register src2) {
            super(masm, Ops.ArithOp.getValue(), 6, Op3s.Wrreg.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Xor extends Fmt3b {
        public Xor(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Xor.getValue(), src2.encoding(), simm13);
        }
        public Xor(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Xor.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Xorcc extends Fmt3b {
        public Xorcc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Xorcc.getValue(), src2.encoding(), simm13);
        }
        public Xorcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Xorcc.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Xnor extends Fmt3b {
        public Xnor(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Xnor.getValue(), src2.encoding(), simm13);
        }
        public Xnor(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Xnor.getValue(), src2.encoding(), src1.encoding());
        }
    }

    public static class Xnorcc extends Fmt3b {
        public Xnorcc(SPARCAssembler masm, int simm13, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Xnorcc.getValue(), src2.encoding(), simm13);
        }
        public Xnorcc(SPARCAssembler masm, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), dst.encoding(), Op3s.Xnorcc.getValue(), src2.encoding(), src1.encoding());
        }
    }
}
