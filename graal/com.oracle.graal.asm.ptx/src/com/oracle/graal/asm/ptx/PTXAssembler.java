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

import com.oracle.graal.asm.Label;
import com.oracle.graal.api.code.Register;
import com.oracle.graal.api.code.RegisterConfig;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.meta.Constant;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.nodes.calc.Condition;
import com.oracle.graal.graph.GraalInternalError;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.Variable;

public class PTXAssembler extends AbstractPTXAssembler {

    public PTXAssembler(TargetDescription target, @SuppressWarnings("unused") RegisterConfig registerConfig) {
        super(target);
    }

    public enum ConditionOperator {
        // @formatter:off

        // Signed integer operators
        S_EQ("eq"),
        S_NE("ne"),
        S_LT("lt"),
        S_LE("le"),
        S_GT("gt"),
        S_GE("ge"),

        // Unsigned integer operators
        U_EQ("eq"),
        U_NE("ne"),
        U_LO("lo"),
        U_LS("ls"),
        U_HI("hi"),
        U_HS("hs"),

        // Bit-size integer operators
        B_EQ("eq"),
        B_NE("ne"),

        // Floating-point operators
        F_EQ("eq"),
        F_NE("ne"),
        F_LT("lt"),
        F_LE("le"),
        F_GT("gt"),
        F_GE("ge"),

        // Floating-point operators accepting NaN
        F_EQU("equ"),
        F_NEU("neu"),
        F_LTU("ltu"),
        F_LEU("leu"),
        F_GTU("gtu"),
        F_GEU("geu"),

        // Floating-point operators testing for NaN
        F_NUM("num"),
        F_NAN("nan");

        // @formatter:on

        private final String operator;

        private ConditionOperator(String op) {
            this.operator = op;
        }

        public String getOperator() {
            return operator;
        }
    }

    public static class StandardFormat {

        // Type of destination value
        protected Kind valueKind;
        protected Variable dest;
        protected Value source1;
        protected Value source2;

        public StandardFormat(Variable dst, Value src1, Value src2) {
            setDestination(dst);
            setSource1(src1);
            setSource2(src2);
            setKind(dst.getKind());
        }

        public void setKind(Kind k) {
            valueKind = k;
        }

        public void setDestination(Variable var) {
            assert var != null;
            dest = var;
            setKind(var.getKind());
        }

        public void setSource1(Value val) {
            assert val != null;
            source1 = val;
        }

        public void setSource2(Value val) {
            assert val != null;
            source2 = val;
        }

        public String typeForKind(Kind k) {
            switch (k.getTypeChar()) {
            // Boolean
                case 'z':
                    return "u8";
                    // Byte
                case 'b':
                    return "b8";
                    // Short
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

        public String emit() {
            return (typeForKind(valueKind) + emitRegister(dest, true) + emitValue(source1, true) + emitValue(source2, false) + ";");
        }

        public String emitValue(Value v, boolean comma) {
            assert v != null;

            if (isConstant(v)) {
                return (emitConstant(v, comma));
            } else {
                return (emitRegister((Variable) v, comma));
            }
        }

        public String emitRegister(Variable v, boolean comma) {
            return (" %r" + v.index + (comma ? "," : ""));
        }

        public String emitConstant(Value v, boolean comma) {
            Constant constant = (Constant) v;
            String str = null;

            switch (v.getKind().getTypeChar()) {
                case 'i':
                    str = String.valueOf((int) constant.asLong());
                    break;
                case 'f':
                    str = String.valueOf(constant.asFloat());
                    break;
                case 'j':
                    str = String.valueOf(constant.asLong());
                    break;
                case 'd':
                    str = String.valueOf(constant.asDouble());
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
            if (comma) {
                return (str + ",");
            } else {
                return str;
            }
        }
    }

    public static class LogicInstructionFormat extends StandardFormat {
        public LogicInstructionFormat(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        @Override
        public String emit() {
            String kindStr;
            switch (valueKind.getTypeChar()) {
                case 's':
                    kindStr = "b16";
                    break;
                case 'i':
                    kindStr = "b32";
                    break;
                case 'j':
                    kindStr = "b64";
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }

            return (kindStr + emitRegister(dest, true) + emitValue(source1, true) + emitValue(source2, false) + ";");
        }
    }

    public static class SingleOperandFormat {

        protected Variable dest;
        protected Value source;

        public SingleOperandFormat(Variable dst, Value src) {
            setDestination(dst);
            setSource(src);
        }

        public void setDestination(Variable var) {
            dest = var;
        }

        public void setSource(Value var) {
            source = var;
        }

        public String typeForKind(Kind k) {
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
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }

        public String emit() {
            return (typeForKind(dest.getKind()) + " " + emitVariable(dest) + ", " + emitValue(source) + ";");
        }

        public String emitValue(Value v) {
            assert v != null;

            if (isConstant(v)) {
                return (emitConstant(v));
            } else {
                return (emitVariable((Variable) v));
            }
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

        public String emitVariable(Variable v) {
            String name = v.getName();

            if (name == null) {
                return (" %r" + v.index);
            } else {
                return name;
            }
        }
    }

    public static class BinarySingleOperandFormat extends SingleOperandFormat {

        public BinarySingleOperandFormat(Variable dst, Value src) {
            super(dst, src);
        }

        @Override
        public String typeForKind(Kind k) {
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
        }
    }

    public static class ConversionFormat extends SingleOperandFormat {

        private final Kind dstKind;
        private final Kind srcKind;

        public ConversionFormat(Variable dst, Value src, Kind dstKind, Kind srcKind) {
            super(dst, src);
            this.dstKind = dstKind;
            this.srcKind = srcKind;
        }

        @Override
        public String emit() {
            return (typeForKind(dstKind) + "." + typeForKind(srcKind) + " " + emitVariable(dest) + ", " + emitValue(source) + ";");
        }
    }

    public static class LoadStoreFormat extends StandardFormat {

        protected PTXStateSpace space;

        public LoadStoreFormat(PTXStateSpace space, Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
            setStateSpace(space);
        }

        public void setStateSpace(PTXStateSpace ss) {
            space = ss;
        }

        public String emitAddress(Value var, Value val) {
            assert var instanceof Variable;
            assert val instanceof Constant;
            Constant constant = (Constant) val;
            return ("[" + ((space == PTXStateSpace.Parameter) ? emitParameter((Variable) var) : emitRegister((Variable) var, false)) + " + " + constant.asBoxedValue() + "]");
        }

        @Override
        public String emitRegister(Variable var, boolean comma) {
            return ("%r" + var.index);
        }

        public String emitParameter(Variable v) {
            return ("param" + v.index);
        }

        public String emit(boolean isLoad) {
            if (isLoad) {
                return (space.getStateName() + "." + typeForKind(valueKind) + " " + emitRegister(dest, false) + ", " + emitAddress(source1, source2) + ";");
            } else {
                return (space.getStateName() + "." + typeForKind(valueKind) + " " + emitAddress(source1, source2) + ", " + emitRegister(dest, false) + ";");
            }
        }
    }

    // Checkstyle: stop method name check
    /*
     * Emit conditional branch to target 'tgt' guarded by predicate register 'pred' whose state is
     * tested to be 'predCheck'.
     */
    public final void bra(String tgt, int pred, boolean predCheck) {
        assert pred >= 0;

        if (tgt.equals("?")) {
            Thread.dumpStack();
        }
        emitString("@" + (predCheck ? "%p" : "!%p") + pred + " " + "bra" + " " + tgt + ";");
    }

    public final void bra(String src) {
        emitString("bra " + src + ";");
    }

    public final void bra_uni(String tgt) {
        emitString("bra.uni" + " " + tgt + ";" + "");
    }

    public final void exit() {
        emitString("exit;" + " " + "");
    }

    public static class Global {

        private Kind kind;
        private String name;
        private LabelRef[] targets;

        public Global(Value val, String name, LabelRef[] targets) {
            this.kind = val.getKind();
            this.name = name;
            this.targets = targets;
        }

        private static String valueForKind(Kind k) {
            switch (k.getTypeChar()) {
                case 'i':
                    return "s32";
                case 'j':
                    return "s64";
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }

        private static String emitTargets(PTXAssembler asm, LabelRef[] refs) {
            StringBuffer sb = new StringBuffer();

            for (int i = 0; i < refs.length; i++) {
                sb.append(asm.nameOf(refs[i].label()));
                if (i < (refs.length - 1)) {
                    sb.append(", ");
                }
            }

            return sb.toString();
        }

        public void emit(PTXAssembler asm) {
            asm.emitString(".global ." + valueForKind(kind) + " " + name + "[" + targets.length + "] = " + "{ " + emitTargets(asm, targets) + " };");
        }
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

    public enum BooleanOperator {
        AND("and"), OR("or"), XOR("xor");

        private final String output;

        private BooleanOperator(String out) {
            this.output = out;
        }

        public String getOperator() {
            return output + ".";
        }
    }

    public static class Setp {

        private BooleanOperator booleanOperator;
        private ConditionOperator operator;
        private Value first, second;
        private Kind kind;
        private int predicate;

        public Setp(Condition condition, Value first, Value second, int predicateRegisterNumber) {
            setFirst(first);
            setSecond(second);
            setPredicate(predicateRegisterNumber);
            setKind();
            setConditionOperator(operatorForConditon(condition));
        }

        public Setp(Condition condition, BooleanOperator operator, Value first, Value second, int predicateRegisterNumber) {
            setFirst(first);
            setSecond(second);
            setPredicate(predicateRegisterNumber);
            setKind();
            setConditionOperator(operatorForConditon(condition));
            setBooleanOperator(operator);
        }

        public void setFirst(Value v) {
            first = v;
        }

        public void setSecond(Value v) {
            second = v;
        }

        public void setPredicate(int p) {
            predicate = p;
        }

        public void setConditionOperator(ConditionOperator co) {
            operator = co;
        }

        public void setBooleanOperator(BooleanOperator bo) {
            booleanOperator = bo;
        }

        private ConditionOperator operatorForConditon(Condition condition) {
            char typeChar = kind.getTypeChar();

            switch (typeChar) {
                case 'z':
                case 'c':
                case 'a':
                    // unsigned
                    switch (condition) {
                        case EQ:
                            return ConditionOperator.U_EQ;
                        case NE:
                            return ConditionOperator.U_NE;
                        case LT:
                            return ConditionOperator.U_LO;
                        case LE:
                            return ConditionOperator.U_LS;
                        case GT:
                            return ConditionOperator.U_HI;
                        case GE:
                            return ConditionOperator.U_HS;
                        default:
                            throw GraalInternalError.shouldNotReachHere();
                    }
                case 'b':
                case 's':
                case 'i':
                case 'j':
                    // signed
                    switch (condition) {
                        case EQ:
                            return ConditionOperator.S_EQ;
                        case NE:
                            return ConditionOperator.S_NE;
                        case LT:
                            return ConditionOperator.S_LT;
                        case LE:
                            return ConditionOperator.S_LE;
                        case GT:
                            return ConditionOperator.S_GT;
                        case GE:
                        case AE:
                            return ConditionOperator.S_GE;
                        default:
                            throw GraalInternalError.shouldNotReachHere();
                    }
                case 'f':
                case 'd':
                    // floating point - do these need to accept NaN??
                    switch (condition) {
                        case EQ:
                            return ConditionOperator.F_EQ;
                        case NE:
                            return ConditionOperator.F_NE;
                        case LT:
                            return ConditionOperator.F_LT;
                        case LE:
                            return ConditionOperator.F_LE;
                        case GT:
                            return ConditionOperator.F_GT;
                        case GE:
                            return ConditionOperator.F_GE;
                        default:
                            throw GraalInternalError.shouldNotReachHere();
                    }
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }

        public void setKind() {
            // assert isConstant(first) && isConstant(second) == false;

            if (isConstant(first)) {
                kind = second.getKind();
            } else {
                kind = first.getKind();
            }
        }

        public String emitValue(Value v) {
            assert v != null;

            if (isConstant(v)) {
                return (", " + emitConstant(v));
            } else {
                return (", " + emitVariable((Variable) v));
            }
        }

        public String typeForKind(Kind k) {
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
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
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

        public String emitVariable(Variable v) {
            return ("%r" + v.index);
        }

        public void emit(PTXAssembler asm) {

            if (booleanOperator != null) {
                // Predicates need to be objects
                asm.emitString("setp." + operator.getOperator() + "." + booleanOperator.getOperator() + typeForKind(kind) + " %p" + predicate + emitValue(first) + emitValue(second) + ", %r;");
            } else {
                asm.emitString("setp." + operator.getOperator() + "." + typeForKind(kind) + " %p" + predicate + emitValue(first) + emitValue(second) + ";");
            }
        }
    }

    @Override
    public PTXAddress makeAddress(Register base, int displacement) {
        throw GraalInternalError.shouldNotReachHere();
    }

    @Override
    public PTXAddress getPlaceholder() {
        return null;
    }

    @Override
    public void jmp(Label l) {
        String str = nameOf(l);
        if (l.equals("?")) {
            Thread.dumpStack();
        }
        bra(str);
    }

    /**
     * @param r
     */
    public void nullCheck(Register r) {
        // setp(....);
    }
}
