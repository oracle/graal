/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.cri.xir;

import static com.oracle.max.cri.xir.CiXirAssembler.XirOp.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CiAddress.*;
import com.oracle.graal.api.meta.*;

/**
 * Represents an assembler that allows a client such as the runtime system to
 * create {@link XirTemplate XIR templates}.
 */
public abstract class CiXirAssembler {

    protected XirOperand resultOperand;
    protected boolean allocateResultOperand;

    protected final List<XirInstruction> instructions = new ArrayList<>();
    protected final List<XirLabel> labels = new ArrayList<>(5);
    protected final List<XirParameter> parameters = new ArrayList<>(5);
    protected final List<XirTemp> temps = new ArrayList<>(5);
    protected final List<XirConstant> constants = new ArrayList<>(5);
    protected final List<XirMark> marks = new ArrayList<>(5);

    protected int outgoingStackSize = 0;

    /**
     * Increases by one for every {@link XirOperand operand} created.
     */
    protected int variableCount;

    /**
     * Marks the assembly complete.
     */
    protected boolean finished = true;

    protected final CiTarget target;

    public CiXirAssembler(CiTarget target) {
        this.target = target;
    }

    public static class RuntimeCallInformation {
        public final Object target;
        public final boolean useInfoAfter;

        public RuntimeCallInformation(Object target, boolean useInfoAfter) {
            this.target = target;
            this.useInfoAfter = useInfoAfter;
        }
    }

    /**
     * Represents additional address calculation information.
     */
    public static final class AddressAccessInformation {

        /**
         * The scaling factor for the scaled-index part of an address computation.
         */
        public final Scale scale;

        /**
         * The constant byte-sized displacement part of an address computation.
         */
        public final int disp;

        /**
         * Determines if the memory access through the address can trap.
         */
        public final boolean canTrap;

        private AddressAccessInformation(boolean canTrap) {
            this.canTrap = canTrap;
            this.scale = Scale.Times1;
            this.disp = 0;
        }

        private AddressAccessInformation(boolean canTrap, int disp) {
            this.canTrap = canTrap;
            this.scale = Scale.Times1;
            this.disp = disp;
        }

        private AddressAccessInformation(boolean canTrap, int disp, Scale scale) {
            this.canTrap = canTrap;
            this.scale = scale;
            this.disp = disp;
        }
    }

    /**
     * A label that is the target of a control flow instruction.
     */
    public static final class XirLabel {
        public static final String TrueSuccessor = "TrueSuccessor";
        public static final String FalseSuccessor = "FalseSuccessor";
        public final String name;
        public final int index;
        /**
         * If {@code true} the label is to an instruction in the fast path sequence, otherwise to the slow path.
         */
        public final boolean inline;

        private XirLabel(String name, int index, boolean inline) {
            this.name = name;
            this.index = index;
            this.inline = inline;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Tagging interface that indicates that an {@link XirOperand} is a constant.
     */
    public interface XirConstantOperand {
        int getIndex();
    }

    public static final XirOperand VOID = null;

    /**
     * Operands for {@link XirInstruction instructions}.
     * There are three basic variants, {@link XirConstant constant}, {@link XirParameter parameter} and {@link XirTemp}.
     */
    public abstract static class XirOperand {

        public final RiKind kind;

        /**
         * Unique id in range {@code 0} to {@link #variableCount variableCount - 1}.
         */
        public final int index;

        /**
         * Value whose {@link #toString()} method provides a name for this operand.
         */
        public final Object name;

        public XirOperand(CiXirAssembler asm, Object name, RiKind kind) {
            this.kind = kind;
            this.name = name;
            this.index = asm.variableCount++;
        }

        @Override
        public String toString() {
            return String.valueOf(name);
        }

        public String detailedToString() {

            StringBuffer sb = new StringBuffer();

            sb.append(name);
            sb.append('$');
            sb.append(kind.typeChar);
            return sb.toString();
        }
    }

    /**
     * Parameters to {@link XirTemplate templates}.
     */
    public static class XirParameter extends XirOperand {
        /**
         * Unique id in range {@code 0} to {@code parameters.Size()  - 1}.
         */
        public final int parameterIndex;

        public final boolean canBeConstant;

        XirParameter(CiXirAssembler asm, String name, RiKind kind, boolean canBeConstant) {
            super(asm, name, kind);
            this.parameterIndex = asm.parameters.size();
            this.canBeConstant = canBeConstant;
            asm.parameters.add(this);
        }

    }

    public static class XirConstantParameter extends XirParameter implements XirConstantOperand {
        XirConstantParameter(CiXirAssembler asm, String name, RiKind kind) {
            super(asm, name, kind, true);
        }

        public int getIndex() {
            return index;
        }
    }

    public static class XirVariableParameter extends XirParameter {
        XirVariableParameter(CiXirAssembler asm, String name, RiKind kind, boolean canBeConstant) {
            super(asm, name, kind, canBeConstant);
        }
    }

    public static class XirConstant extends XirOperand implements XirConstantOperand {
        public final Constant value;

        XirConstant(CiXirAssembler asm, Constant value) {
            super(asm, value, value.kind);
            this.value = value;
        }

        public int getIndex() {
            return index;
        }
    }

    public static class XirTemp extends XirOperand {
        public final boolean reserve;

        XirTemp(CiXirAssembler asm, String name, RiKind kind, boolean reserve) {
            super(asm, name, kind);
            this.reserve = reserve;
        }
    }

    public static class XirRegister extends XirTemp {
        public final Value register;

        XirRegister(CiXirAssembler asm, String name, CiRegisterValue register, boolean reserve) {
            super(asm, name, register.kind, reserve);
            this.register = register;
        }
    }

    /**
     * Start a new assembly with no initial {@link #resultOperand result operand}.
     */
    public void restart() {
        reset();
        resultOperand = null;
    }

    /**
     * Start a new assembly with a {@link #resultOperand result operand} of type {@code kind}.
     * @param kind the result kind
     * @return an {@code XirOperand} for the result operand
     */
    public XirOperand restart(RiKind kind) {
        reset();
        resultOperand = new XirTemp(this, "result", kind, true);
        allocateResultOperand = true;
        return resultOperand;
    }

    /**
     * Reset the state of the class to the initial conditions to facilitate a new assembly.
     */
    private void reset() {
        assert finished : "must be finished before!";
        variableCount = 0;
        allocateResultOperand = false;
        finished = false;
        instructions.clear();
        labels.clear();
        parameters.clear();
        temps.clear();
        constants.clear();
        marks.clear();
        outgoingStackSize = 0;
    }

    /**
     * Represents an XIR instruction, characterized by an {@link XirOp operation}, a {@link RiKind kind}, an optional {@link XirOperand result}, a variable number of {@link XirOperand arguments},
     * and some optional instruction-specific state. The {@link #x}, {@link #y} and {@link #z} methods are convenient ways to access the first, second and third
     * arguments, respectively. Only the {@link XirOp#CallStub} and {@link XirOp#CallRuntime} instructions can have more than three arguments.
     *
     */
    public static final class XirInstruction {
        /**
         * The {@link RiKind kind} of values the instruction operates on.
         */
        public final RiKind kind;
        /**
         * The {@link XirOp operation}.
         */
        public final XirOp op;
        /**
         * The result, if any.
         */
        public final XirOperand result;
        /**
         * The arguments.
         */
        public final XirOperand[] arguments;
        /**
         * Arbitrary additional data associated with the instruction.
         */
        public final Object extra;

        public XirInstruction(RiKind kind, XirOp op, XirOperand result, XirOperand... arguments) {
            this(kind, null, op, result, arguments);
        }

        public XirInstruction(RiKind kind, Object extra, XirOp op, XirOperand result, XirOperand... arguments) {
            this.extra = extra;
            this.kind = kind;
            this.op = op;
            this.result = result;
            this.arguments = arguments;
        }

        public XirOperand x() {
            assert arguments.length > 0 : "no x operand for this instruction";
            return arguments[0];
        }

        public XirOperand y() {
            assert arguments.length > 1 : "no y operand for this instruction";
            return arguments[1];
        }

        public XirOperand z() {
            assert arguments.length > 2 : "no z operand for this instruction";
            return arguments[2];
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();

            if (result != null) {
                sb.append(result.toString());
                sb.append(" = ");
            }

            sb.append(op.name());

            if (kind != RiKind.Void) {
                sb.append('$');
                sb.append(kind.typeChar);
            }

            if (arguments != null && arguments.length > 0) {
                sb.append("(");

                for (int i = 0; i < arguments.length; i++) {
                    if (i != 0) {
                        sb.append(", ");
                    }
                    sb.append(arguments[i]);
                }

                sb.append(")");
            }

            if (extra != null) {
                sb.append(" ");
                sb.append(extra);
            }

            return sb.toString();
        }
    }

    /**
     * These marks let the RiXirGenerator mark positions in the generated native code and bring them in relationship with on another.
     * This is necessary for code patching, etc.
     */
    public static class XirMark {
        public final XirMark[] references;
        public final Object id;

        // special mark used to refer to the actual call site of an invoke
        public static final XirMark CALLSITE = new XirMark(null);

        public XirMark(Object id, XirMark... references) {
            this.id = id;
            this.references = references;
        }
    }

    /**
     * The set of opcodes for XIR instructions.
     * {@link XirInstruction} defines {@code x}, {@code y} and {@code z} as the first, second and third arguments, respectively.
     * We use these mnemonics, plus {@code args} for the complete set of arguments, {@code r} for the result, and {@code extra}
     * for the instruction-specific extra data, in the opcode specifications. Note that the opcodes that operate on values do not directly
     * specify the size (kind) of the data operated on;  this is is encoded in {@link XirInstruction#kind}.
     * Note: If the instruction kind differs from the argument/result kinds, the behavior is undefined.
     *
     */
    public enum XirOp {
        /**
         * Move {@code x} to {@code r}.
         */
        Mov,
        /**
         * Add {@code y} to {@code x} and put the result in {@code r}.
         */
        Add,
        /**
         * Subtract {@code y} from {@code x} and put the result in {@code r}.
         */
        Sub,
        /**
         * Divide {@code y} by {@code x} and put the result in {@code r}.
         */
        Div,
        /**
         * Multiply {@code y} by {@code x} and put the result in {@code r}.
         */
        Mul,
        /**
         * {@code y} modulus {@code x} and put the result in {@code r}.
         */
        Mod,
        /**
         * Shift  {@code y} left by {@code x} and put the result in {@code r}.
         */
        Shl,
        /**
         * Arithmetic shift  {@code y} right by {@code x} and put the result in {@code r}.
         */
        Sar,
        /**
         * Shift  {@code y} right by {@code x} and put the result in {@code r}.
         */
        Shr,
        /**
         * And {@code y} by {@code x} and put the result in {@code r}.
         */
        And,
        /**
         * Or {@code y} by {@code x} and put the result in {@code r}.
         */
        Or,
        /**
         * Exclusive Or {@code y} by {@code x} and put the result in {@code r}.
         */
        Xor,
        /**
         * Null check on {@code x}.
         */
        NullCheck,
        /**
         * Load value at address {@code x} and put the result in {@code r}.
         */
        PointerLoad,
        /**
         * Store {@code y} at address {@code x}.
         */
        PointerStore,
        /**
         * Load value at an effective address defined by base {@code x} and either a scaled index {@code y} plus displacement
         * or an offset {@code y} and put the result in {@code r}.
         */
        PointerLoadDisp,
        /**
         * Load an effective address defined by base {@code x} and either a scaled index {@code y} plus displacement
         * or an offset {@code y} and put the result in {@code r}.
         */
        LoadEffectiveAddress,
        /**
         * Store {@code z} at address defined by base {@code x} and index {@code y}.
         */
        PointerStoreDisp,
        /**
         * Repeat move from {@code x} to {@code y} using {@code z} words.
         */
        RepeatMoveWords,
        /**
         * Repeat move from {@code x} to {@code y} using {@code z} words.
         */
        RepeatMoveBytes,
        /**
         * Compare value at at address {@code x} with value in {@code y} and store value {@code z} at address {@code x}
         * if it was equal to {@code y}.
         */
        PointerCAS,
        /**
         * Call the {@link RiMethod} defined by {@code extra}  with {@code args} and put the result in {@code r}.
         */
        CallRuntime,
        /**
         * Transfer control to the instruction at the {@link XirLabel label} identified by {@code extra}.
         */
        Jmp,
       /**
         * If {@code x == y}, transfer control to the instruction at the {@link XirLabel label} identified by {@code extra}.
         */
        Jeq,
        /**
         * If {@code x != y}, transfer control to the instruction at the {@link XirLabel label} identified by {@code extra}.
         */
        Jneq,
        /**
         * If {@code x > y}, transfer control to the instruction at the {@link XirLabel label} identified by {@code extra}.
         */
        Jgt,
        /**
         * If {@code x >= y}, transfer control to the instruction at the {@link XirLabel label} identified by {@code extra}.
         */
        Jgteq,
        /**
         * If {@code x unsigned >= y}, transfer control to the instruction at the {@link XirLabel label} identified by {@code extra}.
         */
        Jugteq,
        /**
         * If {@code x < y}, transfer control to the instruction at the {@link XirLabel label} identified by {@code extra}.
         */
        Jlt,
        /**
         * If {@code x <= y}, transfer control to the instruction at the {@link XirLabel label} identified by {@code extra}.
         */
        Jlteq,
        /**
         * Decreases the input by one and jumps to the target if the input is not 0.
         */
        DecAndJumpNotZero,
        /**
         * If bit designated by {@code z} at effective address defined by base {@code x} and offset {@code y}
         * is set transfer control to the instruction at the {@link XirLabel label} identified by {@code extra}.
         */
        Jbset,
        /**
         * Bind the {@link XirLabel label} identified by {@code extra} to the current instruction and update any references to it.
         * A label may be bound more than once to the same location.
         */
        Bind,
        /**
         * Record a safepoint.
         */
        Safepoint,
        /**
         * Pushes a value onto the stack.
         */
        Push,
        /**
         * Pops a value from the stack.
         */
        Pop,
        /**
         * Marks a position in the generated native code.
         */
        Mark,
        /**
         * Load instruction pointer of the next instruction in a destination register.
         */
        Here,
        /**
         * Inserts nop instructions, with the given size in bytes.
         */
        Nop,
        /**
         * This instruction should never be reached, this is useful for debugging purposes.
         */
         ShouldNotReachHere
    }

    public/*private*/ void append(XirInstruction xirInstruction) {
        assert !finished : "no instructions can be added to finished template";
        instructions.add(xirInstruction);
    }

    public XirLabel createInlineLabel(String name) {
        final XirLabel result = new XirLabel(name, this.labels.size(), true);
        labels.add(result);
        return result;
    }

    public XirLabel createOutOfLineLabel(String name) {
        final XirLabel result = new XirLabel(name, this.labels.size(), false);
        labels.add(result);
        return result;
    }

    public void mov(XirOperand result, XirOperand a) {
        append(new XirInstruction(result.kind, Mov, result, a));
    }

    public void add(XirOperand result, XirOperand a, XirOperand b) {
        append(new XirInstruction(result.kind, Add, result, a, b));
    }

    public void sub(XirOperand result, XirOperand a, XirOperand b) {
        append(new XirInstruction(result.kind, Sub, result, a, b));
    }

    public void div(XirOperand result, XirOperand a, XirOperand b) {
        append(new XirInstruction(result.kind, Div, result, a, b));
    }

    public void mul(XirOperand result, XirOperand a, XirOperand b) {
        append(new XirInstruction(result.kind, Mul, result, a, b));
    }

    public void mod(XirOperand result, XirOperand a, XirOperand b) {
        append(new XirInstruction(result.kind, Mod, result, a, b));
    }

    public void shl(XirOperand result, XirOperand a, XirOperand b) {
        append(new XirInstruction(result.kind, Shl, result, a, b));
    }

    public void shr(XirOperand result, XirOperand a, XirOperand b) {
        append(new XirInstruction(result.kind, Shr, result, a, b));
    }

    public void and(XirOperand result, XirOperand a, XirOperand b) {
        append(new XirInstruction(result.kind, And, result, a, b));
    }

    public void or(XirOperand result, XirOperand a, XirOperand b) {
        append(new XirInstruction(result.kind, Or, result, a, b));
    }

    public void xor(XirOperand result, XirOperand a, XirOperand b) {
        append(new XirInstruction(result.kind, Xor, result, a, b));
    }

    public void nullCheck(XirOperand pointer) {
        append(new XirInstruction(RiKind.Object, NullCheck, VOID, pointer));
    }

    public void pload(RiKind kind, XirOperand result, XirOperand pointer, boolean canTrap) {
        append(new XirInstruction(kind, canTrap, PointerLoad, result, pointer));
    }

    public void pstore(RiKind kind, XirOperand pointer, XirOperand value, boolean canTrap) {
        append(new XirInstruction(kind, canTrap, PointerStore, null, pointer, value));
    }

    public void pload(RiKind kind, XirOperand result, XirOperand pointer, XirOperand offset, boolean canTrap) {
        append(new XirInstruction(kind, new AddressAccessInformation(canTrap), PointerLoadDisp, result, pointer, offset));
    }

    public void pstore(RiKind kind, XirOperand pointer, XirOperand offset, XirOperand value, boolean canTrap) {
        append(new XirInstruction(kind, new AddressAccessInformation(canTrap), PointerStoreDisp, VOID, pointer, offset, value));
    }

    public void pload(RiKind kind, XirOperand result, XirOperand pointer, XirOperand index, int disp, Scale scale,  boolean canTrap) {
        append(new XirInstruction(kind, new AddressAccessInformation(canTrap, disp, scale), PointerLoadDisp, result, pointer, index));
    }

    public void lea(XirOperand result, XirOperand pointer, XirOperand index, int disp, Scale scale) {
        append(new XirInstruction(target.wordKind, new AddressAccessInformation(false, disp, scale), LoadEffectiveAddress, result, pointer, index));
    }

    public void repmov(XirOperand src, XirOperand dest, XirOperand length) {
        append(new XirInstruction(target.wordKind, null, RepeatMoveWords, null, src, dest, length));
    }

    public void here(XirOperand dst) {
        append(new XirInstruction(target.wordKind, null, Here, dst));
    }

    public void repmovb(XirOperand src, XirOperand dest, XirOperand length) {
        append(new XirInstruction(target.wordKind, null, RepeatMoveBytes, null, src, dest, length));
    }

    public void pstore(RiKind kind, XirOperand pointer, XirOperand index, XirOperand value, int disp, Scale scale, boolean canTrap) {
        append(new XirInstruction(kind, new AddressAccessInformation(canTrap, disp, scale), PointerStoreDisp, VOID, pointer, index, value));
    }

    public void pcas(RiKind kind, XirOperand result, XirOperand pointer, XirOperand newValue, XirOperand oldValue) {
        append(new XirInstruction(kind, null, PointerCAS, result, pointer, newValue, oldValue));
    }

    public void jmp(XirLabel l) {
        append(new XirInstruction(RiKind.Void, l, Jmp, null));
    }

    public void decAndJumpNotZero(XirLabel l, XirOperand val) {
        append(new XirInstruction(RiKind.Void, l, DecAndJumpNotZero, null, val));
    }

    public void jmpRuntime(Object rt) {
        append(new XirInstruction(RiKind.Void, rt, Jmp, null));
    }

    public void jeq(XirLabel l, XirOperand a, XirOperand b) {
        jcc(Jeq, l, a, b);
    }

    private void jcc(XirOp op, XirLabel l, XirOperand a, XirOperand b) {
        append(new XirInstruction(RiKind.Void, l, op, null, a, b));
    }

    public void jneq(XirLabel l, XirOperand a, XirOperand b) {
        jcc(Jneq, l, a, b);
    }

    public void jgt(XirLabel l, XirOperand a, XirOperand b) {
        jcc(Jgt, l, a, b);
    }

    public void jgteq(XirLabel l, XirOperand a, XirOperand b) {
        jcc(Jgteq, l, a, b);
    }

    public void jugteq(XirLabel l, XirOperand a, XirOperand b) {
        jcc(Jugteq, l, a, b);
    }

    public void jlt(XirLabel l, XirOperand a, XirOperand b) {
        jcc(Jlt, l, a, b);
    }

    public void jlteq(XirLabel l, XirOperand a, XirOperand b) {
        jcc(Jlteq, l, a, b);
    }

    public void jbset(XirLabel l, XirOperand a, XirOperand b, XirOperand c) {
        append(new XirInstruction(RiKind.Void, l, Jbset, null, a, b, c));
    }

    public void bindInline(XirLabel l) {
        assert l.inline;
        append(new XirInstruction(RiKind.Void, l, Bind, null));
    }

    public void bindOutOfLine(XirLabel l) {
        assert !l.inline;
        append(new XirInstruction(RiKind.Void, l, Bind, null));
    }

    public void safepoint() {
        append(new XirInstruction(RiKind.Void, null, Safepoint, null));
    }

    public void push(XirOperand value) {
        append(new XirInstruction(RiKind.Void, Push, VOID, value));
    }

    public void pop(XirOperand result) {
        append(new XirInstruction(result.kind, Pop, result));
    }

    public XirMark mark(Object id, XirMark... references) {
        XirMark mark = new XirMark(id, references);
        marks.add(mark);
        append(new XirInstruction(RiKind.Void, mark, Mark, null));
        return mark;
    }

    public void nop(int size) {
        append(new XirInstruction(RiKind.Void, size, Nop, null));
    }

    public void shouldNotReachHere() {
        append(new XirInstruction(RiKind.Void, null, ShouldNotReachHere, null));
    }

    public void shouldNotReachHere(String message) {
        append(new XirInstruction(RiKind.Void, message, ShouldNotReachHere, null));
    }

    public void callRuntime(Object rt, XirOperand result, XirOperand... args) {
        callRuntime(rt, result, false, args);
    }

    public void callRuntime(Object rt, XirOperand result, boolean useInfoAfter, XirOperand... args) {
        RiKind resultKind = result == null ? RiKind.Void : result.kind;
        append(new XirInstruction(resultKind, new RuntimeCallInformation(rt, useInfoAfter), CallRuntime, result, args));
    }

    /**
     * Terminates the assembly, checking invariants, in particular that {@link resultOperand} is set, and setting {@link #finished} to {@code true}.
     */
    private void end() {
        assert !finished : "template may only be finished once!";
        assert resultOperand != null : "result operand should be set";
        finished = true;
    }

    /**
     * Creates an {@link XirVariableParameter variable input parameter}  of given name and {@link RiKind kind}.
     * @param name a name for the parameter
     * @param kind the parameter kind
     * @return the  {@link XirVariableParameter}
     */
    public XirVariableParameter createInputParameter(String name, RiKind kind, boolean canBeConstant) {
        assert !finished;
        return new XirVariableParameter(this, name, kind, canBeConstant);
    }

    public XirVariableParameter createInputParameter(String name, RiKind kind) {
        return createInputParameter(name, kind, false);
    }

    /**
     * Creates an {@link XirConstantParameter constant input parameter}  of given name and {@link RiKind kind}.
     * @param name a name for the parameter
     * @param kind the parameter kind
     * @return the  {@link XirConstantParameter}
     */
    public XirConstantParameter createConstantInputParameter(String name, RiKind kind) {
        assert !finished;
        return new XirConstantParameter(this, name, kind);
    }

    public XirConstant createConstant(Constant constant) {
        assert !finished;
        XirConstant temp = new XirConstant(this, constant);
        constants.add(temp);
        return temp;
    }

    public XirOperand createTemp(String name, RiKind kind) {
        assert !finished;
        XirTemp temp = new XirTemp(this, name, kind, true);
        temps.add(temp);
        return temp;
    }

    public XirOperand createRegister(String name, RiKind kind, CiRegister register) {
        return createRegister(name, kind, register, false);
    }

    public XirOperand createRegisterTemp(String name, RiKind kind, CiRegister register) {
        return createRegister(name, kind, register, true);
    }

    private XirOperand createRegister(String name, RiKind kind, CiRegister register, boolean reserve) {
        assert !finished;
        XirRegister fixed = new XirRegister(this, name, register.asValue(kind), reserve);
        temps.add(fixed);
        return fixed;
    }

    public XirParameter getParameter(String name) {
        for (XirParameter param : parameters) {
            if (param.name.toString().equals(name)) {
                return param;
            }
        }
        throw new IllegalArgumentException("no parameter: " + name);
    }

    public XirTemp getTemp(String name) {
        for (XirTemp temp : temps) {
            if (temp.name.toString().equals(name)) {
                return temp;
            }
        }
        throw new IllegalArgumentException("no temp: " + name);
    }

    public XirConstant i(int v) {
        return createConstant(Constant.forInt(v));
    }

    public XirConstant l(long v) {
        return createConstant(Constant.forLong(v));
    }

    public XirConstant b(boolean v) {
        return createConstant(Constant.forBoolean(v));
    }

    public XirConstant o(Object obj) {
        return createConstant(Constant.forObject(obj));
    }

    public void reserveOutgoingStack(int size) {
        outgoingStackSize = Math.max(outgoingStackSize, size);
    }

    /**
     * Finishes the assembly of a non-stub template, providing the {@link #resultOperand} and constructs the {@link XirTemplate}.
     * @param result the {@link XirOperand} to be set as the {@link #resultOperand}
     * @param name the name of the template
     * @return the generated template
     */
    public XirTemplate finishTemplate(XirOperand result, String name) {
        assert this.resultOperand == null;
        assert result != null;
        this.resultOperand = result;
        final XirTemplate template = buildTemplate(name, false);
        end();
        return template;
    }

    /**
     * Finishes the assembly of a non-stub template and constructs the {@link XirTemplate}.
     * @param name the name of the template
     * @return the generated template
     */
    public XirTemplate finishTemplate(String name) {
        final XirTemplate template = buildTemplate(name, false);
        end();
        return template;
    }

    /**
     * Finishes the assembly of a {@link XirTemplate.GlobalFlags#GLOBAL_STUB stub} and constructs the {@link XirTemplate}.
     * @param name the name of the template
     * @return the generated template
     */
    public XirTemplate finishStub(String name) {
        final XirTemplate template = buildTemplate(name, true);
        end();
        return template;
    }

    /**
     * Builds the {@link XirTemplate} from the assembly state in this object.
     * The actual assembly is dependent on the target architecture and implemented
     * in a concrete subclass.
     * @param name the name of the template
     * @param isStub {@code true} if the template represents a {@link XirTemplate.GlobalFlags#GLOBAL_STUB stub}
     * @return the generated template
     */
    protected abstract XirTemplate buildTemplate(String name, boolean isStub);

    public abstract CiXirAssembler copy();

}
