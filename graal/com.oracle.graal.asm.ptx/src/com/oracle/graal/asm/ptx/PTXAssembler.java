/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.asm.ptx;

import static com.oracle.graal.api.code.ValueUtil.*;

import com.oracle.graal.api.code.AbstractAddress;
import com.oracle.graal.api.code.Register;
import com.oracle.graal.api.code.RegisterConfig;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.meta.Constant;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.graph.GraalInternalError;

public class PTXAssembler extends AbstractPTXAssembler {

    public PTXAssembler(TargetDescription target, @SuppressWarnings("unused") RegisterConfig registerConfig) {
        super(target);
    }

    public static class StandardFormat {

        protected Kind valueKind;
        protected Value dest;
        protected Value source1;
        protected Value source2;
        private boolean logicInstruction = false;

        public StandardFormat(Value dst, Value src1, Value src2) {
            setDestination(dst);
            setSource1(src1);
            setSource2(src2);
            setKind(dst.getKind());

            assert valueKind == src1.getKind();
        }

        public void setKind(Kind k) {
            valueKind = k;
        }

        public void setDestination(Value v) {
            dest = v;
        }

        public void setSource1(Value v) {
            source1 = v;
        }

        public void setSource2(Value v) {
            source2 = v;
        }

        public void setLogicInstruction(boolean b) {
            logicInstruction = b;
        }

        public String typeForKind(Kind k) {
            if (logicInstruction) {
                switch (k.getTypeChar()) {
                    case 's':
                        return "b16";
                    case 'i':
                        return "b32";
                    case 'j':
                        return "b64";
                    default:
                        throw GraalInternalError.shouldNotReachHere();
                }
            } else {
                switch (k.getTypeChar()) {
                    case 'z':
                        return "u8";
                    case 'b':
                        return "s8";
                    case 's':
                        return "s16";
                    case 'c':
                        return "u16";
                    case 'i':
                        return "s32";
                    case 'f':
                        return "f32";
                    case 'j':
                        return "s64";
                    case 'd':
                        return "f64";
                    case 'a':
                        return "u64";
                    case '-':
                        return "u32";
                    default:
                        throw GraalInternalError.shouldNotReachHere();
                }
            }
        }

        public String emit() {
            return (typeForKind(valueKind) + emitRegister(dest) + emitValue(source1) + emitValue(source2) + ";");
        }

        public String emitValue(Value v) {
            assert v != null;

            if (isConstant(v)) {
                return (", " + emitConstant(v));
            } else {
                return ("," + emitRegister(v));
            }
        }

        public String emitRegister(Value v) {
            return (" %r" + asRegister(v).encoding());
        }

        public String emitConstant(Value v) {
            Constant constant = (Constant) v;

            switch (v.getKind().getTypeChar()) {
                case 'i':
                    return (String.valueOf((int) constant.asLong()));
                case 'f':
                    return (String.valueOf(constant.asFloat()));
                case 'j':
                    return (String.valueOf(constant.asLong()));
                case 'd':
                    return (String.valueOf(constant.asDouble()));
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    public static class Add extends StandardFormat {

        public Add(Value dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("add." + super.emit());
        }
    }

    public static class And extends StandardFormat {

        public And(Value dst, Value src1, Value src2) {
            super(dst, src1, src2);
            setLogicInstruction(true);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("and." + super.emit());
        }
    }

    public static class Div extends StandardFormat {

        public Div(Value dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("div." + super.emit());
        }
    }

    public static class Mul extends StandardFormat {

        public Mul(Value dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("mul.lo." + super.emit());
        }
    }

    public static class Or extends StandardFormat {

        public Or(Value dst, Value src1, Value src2) {
            super(dst, src1, src2);
            setLogicInstruction(true);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("or." + super.emit());
        }
    }

    public static class Rem extends StandardFormat {

        public Rem(Value dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("rem." + super.emit());
        }
    }

    public static class Shl extends StandardFormat {

        public Shl(Value dst, Value src1, Value src2) {
            super(dst, src1, src2);
            setLogicInstruction(true);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("shl." + super.emit());
        }
    }

    public static class Shr extends StandardFormat {

        public Shr(Value dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("shr." + super.emit());
        }
    }

    public static class Sub extends StandardFormat {

        public Sub(Value dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("sub." + super.emit());
        }
    }

    public static class Ushr extends StandardFormat {

        public Ushr(Value dst, Value src1, Value src2) {
            super(dst, src1, src2);
            setKind(Kind.Illegal);  // get around not having an Unsigned Kind
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("shr." + super.emit());
        }
    }

    public static class Xor extends StandardFormat {

        public Xor(Value dst, Value src1, Value src2) {
            super(dst, src1, src2);
            setLogicInstruction(true);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("xor." + super.emit());
        }
    }

    // Checkstyle: stop method name check
    public final void bra(String tgt, int pred) {
        emitString((pred >= 0) ? "" : ("@%p" + pred + "  ") + "bra" + " " + tgt + ";" + "");
    }

    public final void bra_uni(String tgt) {
        emitString("bra.uni" + " " + tgt + ";" + "");
    }

    public final void cvt_s32_f32(Register d, Register a) {
        emitString("cvt.s32.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_s64_f32(Register d, Register a) {
        emitString("cvt.s64.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_f64_f32(Register d, Register a) {
        emitString("cvt.f64.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_f32_f64(Register d, Register a) {
        emitString("cvt.f32.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_s32_f64(Register d, Register a) {
        emitString("cvt.s32.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_s64_f64(Register d, Register a) {
        emitString("cvt.s64.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_f32_s32(Register d, Register a) {
        emitString("cvt.f32.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_f64_s32(Register d, Register a) {
        emitString("cvt.f64.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_s8_s32(Register d, Register a) {
        emitString("cvt.s8.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_b16_s32(Register d, Register a) {
        emitString("cvt.b16.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_s64_s32(Register d, Register a) {
        emitString("cvt.s64.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_s32_s64(Register d, Register a) {
        emitString("cvt.s32.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void exit() {
        emitString("exit;" + " " + "");
    }

    public final void ld_global_b8(Register d, Register a, long immOff) {
        emitString("ld.global.b8" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_b16(Register d, Register a, long immOff) {
        emitString("ld.global.b16" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_b32(Register d, Register a, long immOff) {
        emitString("ld.global.b32" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_b64(Register d, Register a, long immOff) {
        emitString("ld.global.b64" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_u8(Register d, Register a, long immOff) {
        emitString("ld.global.u8" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_u16(Register d, Register a, long immOff) {
        emitString("ld.global.u16" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_u32(Register d, Register a, long immOff) {
        emitString("ld.global.u32" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_u64(Register d, Register a, long immOff) {
        emitString("ld.global.u64" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_s8(Register d, Register a, long immOff) {
        emitString("ld.global.s8" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_s16(Register d, Register a, long immOff) {
        emitString("ld.global.s16" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_s32(Register d, Register a, long immOff) {
        emitString("ld.global.s32" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_s64(Register d, Register a, long immOff) {
        emitString("ld.global.s64" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_f32(Register d, Register a, long immOff) {
        emitString("ld.global.f32" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_f64(Register d, Register a, long immOff) {
        emitString("ld.global.f64" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    // Load from state space to destination register
    public final void ld_from_state_space(String s, Register d, Register a, long immOff) {
        emitString("ld" + s + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    // Load return address from return parameter which is in .param state space
    public final void ld_return_address(String s, Register d, Register a, long immOff) {
        emitString("ld.param." + s + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void mov_b16(Register d, Register a) {
        emitString("mov.b16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_b32(Register d, Register a) {
        emitString("mov.b32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_b64(Register d, Register a) {
        emitString("mov.b64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_u16(Register d, Register a) {
        emitString("mov.u16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_u32(Register d, Register a) {
        emitString("mov.u32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_u64(Register d, Register a) {
        emitString("mov.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_u64(@SuppressWarnings("unused") Register d, @SuppressWarnings("unused") AbstractAddress a) {
        // emitString("mov.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_s16(Register d, Register a) {
        emitString("mov.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_s32(Register d, Register a) {
        emitString("mov.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_s64(Register d, Register a) {
        emitString("mov.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_f32(Register d, Register a) {
        emitString("mov.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_f64(Register d, Register a) {
        emitString("mov.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_b16(Register d, short b16) {
        emitString("mov.b16" + " " + "%r" + d.encoding() + ", " + b16 + ";" + "");
    }

    public final void mov_b32(Register d, int b32) {
        emitString("mov.b32" + " " + "%r" + d.encoding() + ", " + b32 + ";" + "");
    }

    public final void mov_b64(Register d, long b64) {
        emitString("mov.b64" + " " + "%r" + d.encoding() + ", " + b64 + ";" + "");
    }

    public final void mov_u16(Register d, short u16) {
        emitString("mov.u16" + " " + "%r" + d.encoding() + ", " + u16 + ";" + "");
    }

    public final void mov_u32(Register d, int u32) {
        emitString("mov.u32" + " " + "%r" + d.encoding() + ", " + u32 + ";" + "");
    }

    public final void mov_u64(Register d, long u64) {
        emitString("mov.u64" + " " + "%r" + d.encoding() + ", " + u64 + ";" + "");
    }

    public final void mov_s16(Register d, short s16) {
        emitString("mov.s16" + " " + "%r" + d.encoding() + ", " + s16 + ";" + "");
    }

    public final void mov_s32(Register d, int s32) {
        emitString("mov.s32" + " " + "%r" + d.encoding() + ", " + s32 + ";" + "");
    }

    public final void mov_s64(Register d, long s64) {
        emitString("mov.s64" + " " + "%r" + d.encoding() + ", " + s64 + ";" + "");
    }

    public final void mov_f32(Register d, float f32) {
        emitString("mov.f32" + " " + "%r" + d.encoding() + ", " + f32 + ";" + "");
    }

    public final void mov_f64(Register d, double f64) {
        emitString("mov.f64" + " " + "%r" + d.encoding() + ", " + f64 + ";" + "");
    }

    public final void neg_f32(Register d, Register a) {
        emitString("neg.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void neg_f64(Register d, Register a) {
        emitString("neg.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void neg_s16(Register d, Register a) {
        emitString("neg.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void neg_s32(Register d, Register a) {
        emitString("neg.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void neg_s64(Register d, Register a) {
        emitString("neg.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void not_s16(Register d, Register a) {
        emitString("not.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void not_s32(Register d, Register a) {
        emitString("not.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void not_s64(Register d, Register a) {
        emitString("not.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void param_8_decl(Register d, boolean lastParam) {
        emitString(".param" + " " + ".s8" + " " + d + (lastParam ? "" : ","));
    }

    public final void param_16_decl(Register d, boolean lastParam) {
        emitString(".param" + " " + ".s16" + " " + d + (lastParam ? "" : ","));
    }

    public final void param_u16_decl(Register d, boolean lastParam) {
        emitString(".param" + " " + ".s16" + " " + d + (lastParam ? "" : ","));
    }

    public final void param_32_decl(Register d, boolean lastParam) {
        emitString(".param" + " " + ".s32" + " " + d + (lastParam ? "" : ","));
    }

    public final void param_64_decl(Register d, boolean lastParam) {
        emitString(".param" + " " + ".s64" + " " + d + (lastParam ? "" : ","));
    }

    public final void popc_b32(Register d, Register a) {
        emitString("popc.b32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void popc_b64(Register d, Register a) {
        emitString("popc.b64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void ret() {
        emitString("ret;" + " " + "");
    }

    public final void ret_uni() {
        emitString("ret.uni;" + " " + "");
    }

    public final void setp_eq_f32(Register a, Register b, int p) {
        emitString("setp.eq.f32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_f32(Register a, Register b, int p) {
        emitString("setp.ne.f32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_f32(Register a, Register b, int p) {
        emitString("setp.lt.f32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_f32(Register a, Register b, int p) {
        emitString("setp.le.f32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_f32(Register a, Register b, int p) {
        emitString("setp.gt.f32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_f32(Register a, Register b, int p) {
        emitString("setp.ge.f32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_f32(float f32, Register b, int p) {
        emitString("setp.eq.f32" + " " + "%p" + p + ", " + f32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_f32(float f32, Register b, int p) {
        emitString("setp.ne.f32" + " " + "%p" + p + ", " + f32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_f32(float f32, Register b, int p) {
        emitString("setp.lt.f32" + " " + "%p" + p + ", " + f32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_f32(float f32, Register b, int p) {
        emitString("setp.le.f32" + " " + "%p" + p + ", " + f32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_f32(float f32, Register b, int p) {
        emitString("setp.gt.f32" + " " + "%p" + p + ", " + f32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_f32(float f32, Register b, int p) {
        emitString("setp.ge.f32" + " " + "%p" + p + ", " + f32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_f64(double f64, Register b, int p) {
        emitString("setp.eq.f64" + " " + "%p" + p + ", " + f64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_f64(double f64, Register b, int p) {
        emitString("setp.ne.f64" + " " + "%p" + p + ", " + f64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_f64(double f64, Register b, int p) {
        emitString("setp.lt.f64" + " " + "%p" + p + ", " + f64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_f64(double f64, Register b, int p) {
        emitString("setp.le.f64" + " " + "%p" + p + ", " + f64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_f64(double f64, Register b, int p) {
        emitString("setp.gt.f64" + " " + "%p" + p + ", " + f64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_f64(double f64, Register b, int p) {
        emitString("setp.ge.f64" + " " + "%p" + p + ", " + f64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_s64(Register a, Register b, int p) {
        emitString("setp.eq.s64" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_s64(long s64, Register b, int p) {
        emitString("setp.eq.s64" + " " + "%p" + p + ", " + s64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_s32(Register a, Register b, int p) {
        emitString("setp.eq.s32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_s32(Register a, Register b, int p) {
        emitString("setp.ne.s32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_s32(Register a, Register b, int p) {
        emitString("setp.lt.s32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_s32(Register a, Register b, int p) {
        emitString("setp.le.s32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_s32(Register a, Register b, int p) {
        emitString("setp.gt.s32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_s32(Register a, Register b, int p) {
        emitString("setp.ge.s32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_s32(Register a, int s32, int p) {
        emitString("setp.eq.s32" + " " + "%p" + p + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void setp_ne_s32(Register a, int s32, int p) {
        emitString("setp.ne.s32" + " " + "%p" + p + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void setp_lt_s32(Register a, int s32, int p) {
        emitString("setp.lt.s32" + " " + "%p" + p + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void setp_le_s32(Register a, int s32, int p) {
        emitString("setp.le.s32" + " " + "%p" + p + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void setp_gt_s32(Register a, int s32, int p) {
        emitString("setp.gt.s32" + " " + "%p" + p + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void setp_ge_s32(Register a, int s32, int p) {
        emitString("setp.ge.s32" + " " + "%p" + p + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void setp_eq_s32(int s32, Register b, int p) {
        emitString("setp.eq.s32" + " " + "%p" + p + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_s32(int s32, Register b, int p) {
        emitString("setp.ne.s32" + " " + "%p" + p + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_s32(int s32, Register b, int p) {
        emitString("setp.lt.s32" + " " + "%p" + p + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_s32(int s32, Register b, int p) {
        emitString("setp.le.s32" + " " + "%p" + p + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_s32(int s32, Register b, int p) {
        emitString("setp.gt.s32" + " " + "%p" + p + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_s32(int s32, Register b, int p) {
        emitString("setp.ge.s32" + " " + "%p" + p + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_u32(Register a, Register b, int p) {
        emitString("setp.eq.u32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_u32(Register a, Register b, int p) {
        emitString("setp.ne.u32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_u32(Register a, Register b, int p) {
        emitString("setp.lt.u32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_u32(Register a, Register b, int p) {
        emitString("setp.le.u32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_u32(Register a, Register b, int p) {
        emitString("setp.gt.u32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_u32(Register a, Register b, int p) {
        emitString("setp.ge.u32" + " " + "%p" + p + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_u32(Register a, int u32, int p) {
        emitString("setp.eq.u32" + " " + "%p" + p + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void setp_ne_u32(Register a, int u32, int p) {
        emitString("setp.ne.u32" + " " + "%p" + p + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void setp_lt_u32(Register a, int u32, int p) {
        emitString("setp.lt.u32" + " " + "%p" + p + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void setp_le_u32(Register a, int u32, int p) {
        emitString("setp.le.u32" + " " + "%p" + p + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void setp_gt_u32(Register a, int u32, int p) {
        emitString("setp.gt.u32" + " " + "%p" + p + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void setp_ge_u32(Register a, int u32, int p) {
        emitString("setp.ge.u32" + " " + "%p" + p + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void setp_eq_u32(int u32, Register b, int p) {
        emitString("setp.eq.u32" + " " + "%p" + p + ", " + u32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_u32(int u32, Register b, int p) {
        emitString("setp.ne.u32" + " " + "%p" + p + ", " + u32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_u32(int u32, Register b, int p) {
        emitString("setp.lt.u32" + " " + "%p" + p + ", " + u32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_u32(int u32, Register b, int p) {
        emitString("setp.le.u32" + " " + "%p" + p + ", " + u32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_u32(int u32, Register b, int p) {
        emitString("setp.gt.u32" + " " + "%p" + p + ", " + u32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_u32(int u32, Register b, int p) {
        emitString("setp.ge.u32" + " " + "%p" + p + ", " + u32 + ", %r" + b.encoding() + ";" + "");
    }

    // Store in global state space

    public final void st_global_b8(Register a, long immOff, Register b) {
        emitString("st.global.b8" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_b16(Register a, long immOff, Register b) {
        emitString("st.global.b16" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_b32(Register a, long immOff, Register b) {
        emitString("st.global.b32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_b64(Register a, long immOff, Register b) {
        emitString("st.global.b64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_u8(Register a, long immOff, Register b) {
        emitString("st.global.u8" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_u16(Register a, long immOff, Register b) {
        emitString("st.global.u16" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_u32(Register a, long immOff, Register b) {
        emitString("st.global.u32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_u64(Register a, long immOff, Register b) {
        emitString("st.global.u64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_s8(Register a, long immOff, Register b) {
        emitString("st.global.s8" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_s16(Register a, long immOff, Register b) {
        emitString("st.global.s16" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_s32(Register a, long immOff, Register b) {
        emitString("st.global.s32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_s64(Register a, long immOff, Register b) {
        emitString("st.global.s64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_f32(Register a, long immOff, Register b) {
        emitString("st.global.f32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_f64(Register a, long immOff, Register b) {
        emitString("st.global.f64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    // Store return value
    public final void st_global_return_value_s8(Register a, long immOff, Register b) {
        emitString("st.global.s8" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_return_value_s32(Register a, long immOff, Register b) {
        emitString("st.global.s32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_return_value_s64(Register a, long immOff, Register b) {
        emitString("st.global.s64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_return_value_f32(Register a, long immOff, Register b) {
        emitString("st.global.f32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_return_value_f64(Register a, long immOff, Register b) {
        emitString("st.global.f64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_return_value_u32(Register a, long immOff, Register b) {
        emitString("st.global.u32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_return_value_u64(Register a, long immOff, Register b) {
        emitString("st.global.u64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    @Override
    public PTXAddress makeAddress(Register base, int displacement) {
        return new PTXAddress(base, displacement);
    }

    @Override
    public PTXAddress getPlaceholder() {
        throw GraalInternalError.unimplemented("PTXAddress.getPlaceholder()");
    }
}
