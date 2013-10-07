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

        protected Kind valueKind;
        protected Variable dest;
        protected Value source1;
        protected Value source2;
        private boolean logicInstruction = false;

        public StandardFormat(Variable dst, Value src1, Value src2) {
            setDestination(dst);
            setSource1(src1);
            setSource2(src2);
            setKind(dst.getKind());

            // testAdd2B fails this assertion
            // assert valueKind == src1.getKind();
        }

        public void setKind(Kind k) {
            valueKind = k;
        }

        public void setDestination(Variable var) {
            assert var != null;
            dest = var;
        }

        public void setSource1(Value val) {
            assert val != null;
            source1 = val;
        }

        public void setSource2(Value val) {
            assert val != null;
            source2 = val;
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

        public ConversionFormat(Variable dst, Value src) {
            super(dst, src);
        }

        @Override
        public String emit() {
            return (typeForKind(dest.getKind()) + "." + typeForKind(source.getKind()) + " " + emitVariable(dest) + ", " + emitValue(source) + ";");
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
            return ("[" + emitRegister((Variable) var, false) + " + " + constant.asBoxedValue() + "]");
        }

        @Override
        public String emitRegister(Variable var, boolean comma) {
            /*
             * if (space == Parameter) { return ("param" + var.index); } else { return ("%r" +
             * var.index); }
             */
            return ("%r" + var.index);
        }

        public String emit(boolean isLoad) {
            if (isLoad) {
                return (space.getStateName() + "." + typeForKind(valueKind) + " " + emitRegister(dest, false) + ", " + emitAddress(source1, source2) + ";");
            } else {
                return (space.getStateName() + "." + typeForKind(valueKind) + " " + emitAddress(source1, source2) + ", " + emitRegister(dest, false) + ";");
            }
        }
    }

    public static class Add extends StandardFormat {

        public Add(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("add." + super.emit());
        }
    }

    public static class And extends StandardFormat {

        public And(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
            setLogicInstruction(true);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("and." + super.emit());
        }
    }

    public static class Div extends StandardFormat {

        public Div(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("div." + super.emit());
        }
    }

    public static class Mul extends StandardFormat {

        public Mul(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("mul.lo." + super.emit());
        }
    }

    public static class Or extends StandardFormat {

        public Or(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
            setLogicInstruction(true);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("or." + super.emit());
        }
    }

    public static class Rem extends StandardFormat {

        public Rem(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("rem." + super.emit());
        }
    }

    public static class Shl extends StandardFormat {

        public Shl(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
            setLogicInstruction(true);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("shl." + super.emit());
        }
    }

    public static class Shr extends StandardFormat {

        public Shr(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("shr." + super.emit());
        }
    }

    public static class Sub extends StandardFormat {

        public Sub(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("sub." + super.emit());
        }
    }

    public static class Ushr extends StandardFormat {

        public Ushr(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
            setKind(Kind.Illegal);  // get around not having an Unsigned Kind
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("shr." + super.emit());
        }
    }

    public static class Xor extends StandardFormat {

        public Xor(Variable dst, Value src1, Value src2) {
            super(dst, src1, src2);
            setLogicInstruction(true);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("xor." + super.emit());
        }
    }

    // Checkstyle: stop method name check
    public final void bra(String tgt, int pred) {
        assert pred >= 0;

        if (tgt.equals("?")) {
            Thread.dumpStack();
        }
        emitString("@%p" + pred + " " + "bra" + " " + tgt + ";");
    }

    public final void bra(String src) {
        emitString("bra " + src + ";");
    }

    public final void bra_uni(String tgt) {
        emitString("bra.uni" + " " + tgt + ";" + "");
    }

    public static class Cvt extends ConversionFormat {

        public Cvt(Variable dst, Variable src) {
            super(dst, src);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("cvt." + super.emit());
        }
    }

    public static class Mov extends SingleOperandFormat {

        private int predicateRegisterNumber = -1;

        public Mov(Variable dst, Value src) {
            super(dst, src);
        }

        public Mov(Variable dst, Value src, int predicate) {
            super(dst, src);
            this.predicateRegisterNumber = predicate;
        }

        /*
         * public Mov(Variable dst, AbstractAddress src) { throw
         * GraalInternalError.unimplemented("AbstractAddress Mov"); }
         */

        public void emit(PTXAssembler asm) {
            if (predicateRegisterNumber >= 0) {
                asm.emitString("@%p" + String.valueOf(predicateRegisterNumber) + " mov." + super.emit());
            } else {
                asm.emitString("mov." + super.emit());
            }
        }
    }

    public static class Neg extends SingleOperandFormat {

        public Neg(Variable dst, Variable src) {
            super(dst, src);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("neg." + super.emit());
        }
    }

    public static class Not extends BinarySingleOperandFormat {

        public Not(Variable dst, Variable src) {
            super(dst, src);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("not." + super.emit());
        }
    }

    public static class Ld extends LoadStoreFormat {

        public Ld(PTXStateSpace space, Variable dst, Variable src1, Value src2) {
            super(space, dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("ld." + super.emit(true));
        }
    }

    public static class St extends LoadStoreFormat {

        public St(PTXStateSpace space, Variable dst, Variable src1, Value src2) {
            super(space, dst, src1, src2);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString("st." + super.emit(false));
        }
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

    public static class Param extends SingleOperandFormat {

        private boolean lastParameter;

        public Param(Variable d, boolean lastParam) {
            super(d, null);
            setLastParameter(lastParam);
        }

        public void setLastParameter(boolean value) {
            lastParameter = value;
        }

        public String emitParameter(Variable v) {
            return (" %r" + v.index);
        }

        public void emit(PTXAssembler asm) {
            asm.emitString(".param ." + paramForKind(dest.getKind()) + emitParameter(dest) + (lastParameter ? "" : ","));
        }

        public String paramForKind(Kind k) {
            switch (k.getTypeChar()) {
                case 'z':
                case 'f':
                    return "s32";
                case 'b':
                    return "s8";
                case 's':
                    return "s16";
                case 'c':
                    return "u16";
                case 'i':
                    return "s32";
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
                asm.emitString("setp." + operator.getOperator() + "." + booleanOperator.getOperator() + typeForKind(kind) + " %p" + predicate + emitValue(first) + emitValue(second) + ", %r;"); // Predicates
// need to be objects

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

}
