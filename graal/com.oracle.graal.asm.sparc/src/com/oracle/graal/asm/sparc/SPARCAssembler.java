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

import com.oracle.graal.api.code.*;
import com.oracle.graal.sparc.*;

/**
 * This class implements an assembler that can encode most SPARC instructions.
 */
public class SPARCAssembler extends AbstractSPARCAssembler {

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
        Rdreg((0x27 << 19) & 0x01F80000, "rdreg"),
        Membar((0x27 << 19) & 0x01F80000, "membar");

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

    @SuppressWarnings("unused")
    public SPARCAssembler(TargetDescription target) {
        super(target);
        // TODO Auto-generated constructor stub
        SPARC sparc;
    }

    public static final int rs1(int val) {
        return val;
    }

    public static final int rs2(int val) {
        return val;
    }

    public static final int rd(int val) {
        return val;
    }

    public static final int sx1 = 0x00001000;

    public static final int simm(int x, int nbits) {
        // assert_signed_range(x, nbits);
        return x & ((1 << nbits) - 1);
    }

    public final void add(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Add.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void add(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Add.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void addcc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Addcc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void addcc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Addcc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void addc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Addc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void addc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Addc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void addccc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Addccc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void addccc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Addccc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void and(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.And.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void and(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.And.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void andcc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Andcc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void andcc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Andcc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void andn(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Andn.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void andn(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Andn.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void andncc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Andncc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void andncc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Andncc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void mulscc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Mulscc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void mulscc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Mulscc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void mulx(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Mulx.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void mulx(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Mulx.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void or(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Or.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void or(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Or.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void orcc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Orcc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void orcc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Orcc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void orn(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Orn.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void orn(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Orn.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void orncc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Orncc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void orncc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Orncc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void rdy(Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Rdreg.getValue() | rd(dst.encoding()));
    }

    // A.44 Read State Register

    public final void rdccr(Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Rdreg.getValue() | rd(dst.encoding()) | 0x00008000);
    }

    public final void rdasi(Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Rdreg.getValue() | rd(dst.encoding()) | 0x0000C000);
    }

    public final void rdtick(Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Rdreg.getValue() | rd(dst.encoding()) | 0x00010000);
    }

    public final void rdpc(Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Rdreg.getValue() | rd(dst.encoding()) | 0x00014000);
    }

    public final void rdfprs(Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Rdreg.getValue() | rd(dst.encoding()) | 0x00018000);
    }

    @Deprecated
    public final void sdiv(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Sdiv.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    @Deprecated
    public final void sdiv(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Sdiv.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    @Deprecated
    public final void sdivcc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Sdivcc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    @Deprecated
    public final void sdivcc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Sdivcc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void sll(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Sll.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void sll(Register src1, int imm5a, Register dst) {
        assert imm5a < 0x40;
        emitInt(Ops.ArithOp.getValue() | Op3s.Sll.getValue() | rs1(src1.encoding()) | ImmedTrue | imm5a | rd(dst.encoding()));
    }

    public final void sllx(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Sllx.getValue() | sx1 | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void sllx(Register src1, int imm5a, Register dst) {
        assert imm5a < 0x40;
        emitInt(Ops.ArithOp.getValue() | Op3s.Sllx.getValue() | sx1 | rs1(src1.encoding()) | ImmedTrue | imm5a | rd(dst.encoding()));
    }

    public final void smul(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Smul.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void smul(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Smul.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void smulcc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Smulcc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void smulcc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Smulcc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void sra(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Sra.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void sra(Register src1, int imm5a, Register dst) {
        assert imm5a < 0x40;
        emitInt(Ops.ArithOp.getValue() | Op3s.Sra.getValue() | rs1(src1.encoding()) | ImmedTrue | imm5a | rd(dst.encoding()));
    }

    public final void srax(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Srax.getValue() | sx1 | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void srax(Register src1, int imm5a, Register dst) {
        assert imm5a < 0x40;
        emitInt(Ops.ArithOp.getValue() | Op3s.Srax.getValue() | sx1 | rs1(src1.encoding()) | ImmedTrue | imm5a | rd(dst.encoding()));
    }

    public final void srl(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Srl.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void srl(Register src1, int imm5a, Register dst) {
        assert imm5a < 0x40;
        emitInt(Ops.ArithOp.getValue() | Op3s.Srl.getValue() | rs1(src1.encoding()) | ImmedTrue | imm5a | rd(dst.encoding()));
    }

    public final void srlx(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Srlx.getValue() | sx1 | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void srlx(Register src1, int imm5a, Register dst) {
        assert imm5a < 0x40;
        emitInt(Ops.ArithOp.getValue() | Op3s.Srlx.getValue() | sx1 | rs1(src1.encoding()) | ImmedTrue | imm5a | rd(dst.encoding()));
    }

    public final void sub(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Sub.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void sub(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Sub.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void subcc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Subcc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void subcc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Subcc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void subc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Subc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void subc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Subc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void subccc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Subccc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void subccc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Subccc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void taddcc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Taddcc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void taddcc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Taddcc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void taddcctv(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Taddcctv.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void taddcctv(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Taddcctv.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void tsubcc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Tsubcc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void tsubcc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Tsubcc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void tsubcctv(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Tsubcctv.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void tsubcctv(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Tsubcctv.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    @Deprecated
    public final void udiv(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Udiv.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    @Deprecated
    public final void udiv(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Udiv.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    @Deprecated
    public final void udivcc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Udivcc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    @Deprecated
    public final void udivcc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Udivcc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void udivx(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Udivx.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void udivx(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Udivx.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void umul(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Umul.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void umul(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Umul.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void umulcc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Umulcc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void umulcc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Umulcc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void xor(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Xor.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void xor(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Xor.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void xorcc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Xorcc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void xorcc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Xorcc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void xnor(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Xnor.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void xnor(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Xnor.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

    public final void xnorcc(Register src1, Register src2, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Xnorcc.getValue() | rs1(src1.encoding()) | rs2(src2.encoding()) | rd(dst.encoding()));
    }

    public final void xnorcc(Register src1, int simm13, Register dst) {
        emitInt(Ops.ArithOp.getValue() | Op3s.Xnorcc.getValue() | rs1(src1.encoding()) | ImmedTrue | simm(simm13, 13) | rd(dst.encoding()));
    }

}
