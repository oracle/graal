/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.verifier;

import static com.oracle.truffle.espresso.verifier.MethodVerifier.Double;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.Float;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.Int;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.Invalid;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.Long;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.Null;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.failVerify;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.formatGuarantee;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.isType2;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.verifyGuarantee;

import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.meta.EspressoError;

class StackFrame implements StackMapFrameParser.FrameState {
    final Operand[] stack;
    final int stackSize;
    final int top;
    final Operand[] locals;
    final SubroutineModificationStack subroutineModificationStack;

    StackFrame(OperandStack stack, Locals locals) {
        this.stack = stack.extract();
        this.stackSize = stack.size;
        this.top = stack.top;
        this.locals = locals.extract();
        this.subroutineModificationStack = locals.subRoutineModifications;
    }

    StackFrame(OperandStack stack, Operand[] locals) {
        this(stack, locals, null);
    }

    StackFrame(OperandStack stack, Operand[] locals, SubroutineModificationStack sms) {
        this.stack = stack.extract();
        this.stackSize = stack.size;
        this.top = stack.top;
        this.locals = locals;
        this.subroutineModificationStack = sms;
    }

    StackFrame(MethodVerifier mv) {
        this(new OperandStack(mv.getMaxStack()), new Locals(mv));
    }

    StackFrame(Operand[] stack, int stackSize, int top, Operand[] locals) {
        this.stack = stack;
        this.stackSize = stackSize;
        this.top = top;
        this.locals = locals;
        this.subroutineModificationStack = null;
    }

    StackFrame(Operand[] stack, int stackSize, int top, Operand[] locals, SubroutineModificationStack sms) {
        this.stack = stack;
        this.stackSize = stackSize;
        this.top = top;
        this.locals = locals;
        this.subroutineModificationStack = sms;
    }

    OperandStack extractStack(int maxStack) {
        OperandStack res = new OperandStack(maxStack);
        System.arraycopy(stack, 0, res.stack, 0, top);
        res.size = stackSize;
        res.top = top;
        return res;
    }

    Locals extractLocals() {
        Locals newLocals = new Locals(locals.clone());
        newLocals.subRoutineModifications = subroutineModificationStack;
        return newLocals;
    }

    void mergeSubroutines(SubroutineModificationStack other) {
        if (subroutineModificationStack == null) {
            return;
        }
        if (other == subroutineModificationStack) {
            return;
        }
        subroutineModificationStack.merge(other);
    }

    @Override
    public StackFrame sameNoStack() {
        return new StackFrame(Operand.EMPTY_ARRAY, 0, 0, locals);
    }

    @Override
    public StackFrame sameLocalsWith1Stack(VerificationTypeInfo vfi, StackMapFrameParser.FrameBuilder<?> builder) {
        if (builder instanceof MethodVerifier verifier) {
            Operand op = verifier.getOperandFromVerificationType(vfi);
            formatGuarantee(op.slots() <= verifier.getMaxStack(), "Stack map entry requires more stack than allowed by maxStack.");
            OperandStack newStack = new OperandStack(2);
            newStack.push(op);
            return new StackFrame(newStack, locals);
        }
        throw EspressoError.shouldNotReachHere();
    }

    @Override
    public StackMapFrameParser.FrameAndLocalEffect chop(int chop, int lastLocal) {
        Operand[] newLocals = locals.clone();
        int pos = lastLocal;
        for (int i = 0; i < chop; i++) {
            formatGuarantee(pos >= 0, "Chop frame entry chops more locals than existing.");
            Operand op = newLocals[pos];
            if (op.isTopOperand() && (pos > 0)) {
                if (isType2(newLocals[pos - 1])) {
                    pos--;
                }
            }
            newLocals[pos] = Invalid;
            pos--;
        }
        return new StackMapFrameParser.FrameAndLocalEffect(new StackFrame(Operand.EMPTY_ARRAY, 0, 0, newLocals), pos - lastLocal);
    }

    @Override
    public StackMapFrameParser.FrameAndLocalEffect append(VerificationTypeInfo[] vfis, StackMapFrameParser.FrameBuilder<?> builder, int lastLocal) {
        if (builder instanceof MethodVerifier verifier) {
            verifyGuarantee(vfis.length > 0, "Empty Append Frame in the StackmapTable");
            Operand[] newLocals = locals.clone();
            int pos = lastLocal;
            for (VerificationTypeInfo vti : vfis) {
                Operand op = verifier.getOperandFromVerificationType(vti);
                MethodVerifier.setLocal(newLocals, op, ++pos, "Append frame entry in stack map appends more locals than allowed.");
                if (isType2(op)) {
                    MethodVerifier.setLocal(newLocals, Invalid, ++pos, "Append frame entry in stack map appends more locals than allowed.");
                }
            }
            return new StackMapFrameParser.FrameAndLocalEffect(new StackFrame(Operand.EMPTY_ARRAY, 0, 0, newLocals), pos - lastLocal);
        }
        throw EspressoError.shouldNotReachHere();
    }
}

final class OperandStack {
    final Operand[] stack;

    int top;
    int size;

    OperandStack(int maxStack) {
        this.stack = new Operand[maxStack];
        this.top = 0;
        this.size = 0;
    }

    public Operand[] extract() {
        Operand[] result = new Operand[top];
        System.arraycopy(stack, 0, result, 0, top);
        return result;
    }

    void procSize(int modif) {
        size += modif;
        verifyGuarantee(size <= stack.length, "insufficent stack size: " + stack.length);
        verifyGuarantee(size >= 0, "invalid stack access: " + size);
    }

    void pushInt() {
        push(Int);
    }

    void pushFloat() {
        push(Float);
    }

    void pushDouble() {
        push(Double);
    }

    void pushLong() {
        push(Long);
    }

    void push(Operand kind) {
        procSize(kind.slots());
        if (kind.getKind().isStackInt()) {
            stack[top++] = Int;
        } else {
            stack[top++] = kind;
        }
    }

    private Operand popAny() {
        verifyGuarantee(top > 0, "Popping an empty stack");
        Operand op = stack[--top];
        procSize(-op.slots());
        return op;
    }

    Operand popRef() {
        Operand op = popAny();
        verifyGuarantee(op.isReference(), "Invalid operand. Expected a reference, found: " + op);
        return op;
    }

    Operand popRef(Operand kind) {
        Operand op = popRef();
        verifyGuarantee(op.compliesWith(kind), "Type check error: " + op + " cannot be merged into " + kind);
        return op;
    }

    public Operand popUninitRef(Operand kind) {
        Operand op = popRef(kind);
        verifyGuarantee(op.isUninit(), "Calling initialization method on already initialized reference.");
        return op;
    }

    Operand popArray() {
        Operand op = popRef();
        verifyGuarantee(op == Null || op.isArrayType(), "Invalid operand. Expected array, found: " + op);
        return op;
    }

    void popInt() {
        pop(Int);
    }

    void popFloat() {
        pop(Float);
    }

    void popDouble() {
        pop(Double);
    }

    void popLong() {
        pop(Long);
    }

    Operand popObjOrRA() {
        Operand op = popAny();
        verifyGuarantee(op.isReference() || op.isReturnAddress(), op + " on stack, required: Reference or ReturnAddress");
        return op;
    }

    Operand pop(Operand k) {
        if (!k.getKind().isStackInt() || k == Int) {
            Operand op = popAny();
            verifyGuarantee(op.compliesWith(k), op + " on stack, required: " + k);
            return op;
        } else {
            return pop(Int);
        }
    }

    void dup() {
        procSize(1);
        Operand v = stack[top - 1];
        verifyGuarantee(!isType2(v), "type 2 operand for dup.");
        verifyGuarantee(!v.isTopOperand(), "dup of Top type.");
        stack[top] = v;
        top++;
    }

    void pop() {
        procSize(-1);
        Operand v = stack[top - 1];
        verifyGuarantee(!isType2(v), "type 2 operand for pop.");
        verifyGuarantee(!v.isTopOperand(), "dup2x2 of Top type.");
        top--;
    }

    void pop2() {
        procSize(-2);
        Operand v1 = stack[top - 1];
        verifyGuarantee(!v1.isTopOperand(), "dup2x2 of Top type.");
        if (isType2(v1)) {
            top--;
            return;
        }
        Operand v2 = stack[top - 2];
        verifyGuarantee(!v2.isTopOperand(), "dup2x2 of Top type.");
        verifyGuarantee(!isType2(v2), "type 2 second operand for pop2.");
        top = top - 2;
    }

    void dupx1() {
        procSize(1);
        Operand v1 = stack[top - 1];
        Operand v2 = stack[top - 2];
        verifyGuarantee(!isType2(v1) && !isType2(v2), "type 2 operand for dupx1.");
        verifyGuarantee(!v1.isTopOperand() && !v2.isTopOperand(), "dupx1 of Top type.");
        System.arraycopy(stack, top - 2, stack, top - 1, 2);
        top++;
        stack[top - 3] = v1;
    }

    void dupx2() {
        procSize(1);
        Operand v1 = stack[top - 1];
        Operand v2 = stack[top - 2];
        verifyGuarantee(!isType2(v1), "type 2 first operand for dupx2.");
        verifyGuarantee(!v1.isTopOperand() && !v2.isTopOperand(), "dupx2 of Top type.");
        if (isType2(v2)) {
            System.arraycopy(stack, top - 2, stack, top - 1, 2);
            top++;
            stack[top - 3] = v1;
        } else {
            Operand v3 = stack[top - 3];
            verifyGuarantee(!isType2(v3), "type 2 third operand for dupx2.");
            verifyGuarantee(!v3.isTopOperand(), "dupx2 of Top type.");
            System.arraycopy(stack, top - 3, stack, top - 2, 3);
            top++;
            stack[top - 4] = v1;
        }
    }

    void dup2() {
        procSize(2);
        Operand v1 = stack[top - 1];
        if (isType2(v1)) {
            stack[top] = v1;
            top++;
        } else {
            Operand v2 = stack[top - 2];
            verifyGuarantee(!isType2(v2), "type 2 second operand for dup2.");
            verifyGuarantee(!v1.isTopOperand() && !v2.isTopOperand(), "dup2 of Top type.");
            System.arraycopy(stack, top - 2, stack, top, 2);
            top = top + 2;
        }
    }

    void dup2x1() {
        procSize(2);
        Operand v1 = stack[top - 1];
        Operand v2 = stack[top - 2];
        verifyGuarantee(!isType2(v2), "type 2 second operand for dup2x1");
        verifyGuarantee(!v2.isTopOperand() && !v1.isTopOperand(), "dup2x1 of Top type.");
        if (isType2(v1)) {
            System.arraycopy(stack, top - 2, stack, top - 1, 2);
            top++;
            stack[top - 3] = v1;
            return;
        }
        Operand v3 = stack[top - 3];
        verifyGuarantee(!isType2(v3), "type 2 third operand for dup2x1.");
        verifyGuarantee(!v3.isTopOperand(), "dup2x1 of Top type.");
        System.arraycopy(stack, top - 3, stack, top - 1, 3);
        top = top + 2;
        stack[top - 5] = v2;
        stack[top - 4] = v1;
    }

    void dup2x2() {
        procSize(2);
        Operand v1 = stack[top - 1];
        Operand v2 = stack[top - 2];
        boolean b1 = isType2(v1);
        boolean b2 = isType2(v2);

        verifyGuarantee(!v1.isTopOperand() && !v2.isTopOperand(), "dup2x2 of Top type.");

        if (b1 && b2) {
            System.arraycopy(stack, top - 2, stack, top - 1, 2);
            stack[top - 2] = v1;
            top++;
            return;
        }
        Operand v3 = stack[top - 3];
        boolean b3 = isType2(v3);
        verifyGuarantee(!v3.isTopOperand(), "dup2x2 of Top type.");
        if (!b1 && !b2 && b3) {
            System.arraycopy(stack, top - 3, stack, top - 1, 3);
            stack[top - 3] = v2;
            stack[top - 2] = v1;
            top = top + 2;
            return;
        }
        if (b1 && !b2 && !b3) {
            System.arraycopy(stack, top - 3, stack, top - 2, 3);
            stack[top - 3] = v1;
            top++;
            return;
        }
        Operand v4 = stack[top - 4];
        verifyGuarantee(!v4.isTopOperand(), "dup2x2 of Top type.");
        boolean b4 = isType2(v4);
        if (!b1 && !b2 && !b3 && !b4) {
            System.arraycopy(stack, top - 4, stack, top - 2, 4);
            stack[top - 4] = v2;
            stack[top - 3] = v1;
            top = top + 2;
            return;
        }
        throw failVerify("Calling dup2x2 with operands: " + v1 + ", " + v2 + ", " + v3 + ", " + v4);

    }

    void swap() {
        Operand v1 = stack[top - 1];
        Operand v2 = stack[top - 2];
        verifyGuarantee(!isType2(v1) && !isType2(v2), "Type 2 operand for SWAP");
        verifyGuarantee(!v1.isTopOperand() && !v2.isTopOperand(), "swap of Top type.");
        stack[top - 1] = v2;
        stack[top - 2] = v1;
    }

    int mergeInto(StackFrame stackFrame) {
        verifyGuarantee(size == stackFrame.stackSize, "Inconsistent stack height: " + size + " != " + stackFrame.stackSize);
        int secondIndex = 0;
        for (int index = 0; index < top; index++) {
            Operand op1 = stack[index];
            Operand op2 = stackFrame.stack[secondIndex++];
            if (!op1.compliesWithInMerge(op2)) {
                return index;
            }
            if (isType2(op1) && op2.isTopOperand()) {
                verifyGuarantee(stackFrame.stack[secondIndex++].isTopOperand(), "Inconsistent stack Map: " + op1 + " vs. " + op2 + " and " + stackFrame.stack[secondIndex - 1]);
            }

        }
        return -1;
    }

    Operand initUninit(UninitReferenceOperand toInit) {
        Operand init = toInit.init();
        for (int i = 0; i < top; i++) {
            if (stack[i].isUninit() && ((UninitReferenceOperand) stack[i]).newBCI == toInit.newBCI) {
                stack[i] = init;
            }
        }
        return init;
    }
}

final class Locals {
    Operand[] registers;

    // Created an inherited in the verifier.
    // Will stay null in most cases.
    SubroutineModificationStack subRoutineModifications;

    Locals(MethodVerifier mv) {
        Operand[] parsedSig = mv.getOperandSig(mv.getSig());
        int sigSize = mv.isStatic() ? 0 : 1;
        for (int i = 0; i < parsedSig.length - 1; i++) {
            Operand op = parsedSig[i];
            sigSize += isType2(op) ? 2 : 1;
        }
        formatGuarantee(sigSize <= mv.getMaxLocals(), "Too many method arguments for the number of locals !");
        this.registers = new Operand[mv.getMaxLocals()];
        int index = 0;
        if (!mv.isStatic()) {
            if (Name._init_.equals(mv.getMethodName())) {
                registers[index++] = new UninitReferenceOperand(mv.getThisKlass(), mv.getThisKlass());
            } else {
                registers[index++] = new ReferenceOperand(mv.getThisKlass(), mv.getThisKlass());
            }
        }
        for (int i = 0; i < parsedSig.length - 1; i++) {
            Operand op = parsedSig[i];
            if (op.getKind().isStackInt()) {
                registers[index++] = Int;
            } else {
                registers[index++] = op;
            }
            if (isType2(op)) {
                registers[index++] = Invalid;
            }
        }
        for (; index < mv.getMaxLocals(); index++) {
            registers[index] = Invalid;
        }
    }

    Locals(Operand[] registers) {
        this.registers = registers;
    }

    Operand[] extract() {
        return registers.clone();
    }

    Operand load(int index, Operand expected) {
        Operand op = registers[index];
        verifyGuarantee(op.compliesWith(expected), "Incompatible register type. Expected: " + expected + ", found: " + op);
        if (isType2(expected)) {
            verifyGuarantee(registers[index + 1].isTopOperand(), "Loading corrupted long primitive from locals!");
        }
        return op;
    }

    Operand loadRef(int index) {
        Operand op = registers[index];
        verifyGuarantee(op.isReference(), "Incompatible register type. Expected a reference, found: " + op);
        return op;
    }

    ReturnAddressOperand loadReturnAddress(int index) {
        Operand op = registers[index];
        verifyGuarantee(op.isReturnAddress(), "Incompatible register type. Expected a ReturnAddress, found: " + op);
        return (ReturnAddressOperand) op;
    }

    void store(int index, Operand op) {
        boolean subRoutine = subRoutineModifications != null;
        registers[index] = op;
        if (subRoutine) {
            subRoutineModifications.subRoutineModifications[index] = true;
        }
        if (index >= 1 && isType2(registers[index - 1])) {
            registers[index - 1] = Invalid;
            if (subRoutine) {
                subRoutineModifications.subRoutineModifications[index - 1] = true;
            }
        }
        if (isType2(op)) {
            registers[index + 1] = Invalid;
            if (subRoutine) {
                subRoutineModifications.subRoutineModifications[index + 1] = true;
            }
        }
    }

    int mergeInto(StackFrame frame) {
        assert registers.length == frame.locals.length;
        Operand[] frameLocals = frame.locals;

        for (int i = 0; i < registers.length; i++) {
            if (!registers[i].compliesWithInMerge(frameLocals[i])) {
                return i;
            }
        }
        return -1;
    }

    void initUninit(UninitReferenceOperand toInit, Operand stackOp) {
        for (int i = 0; i < registers.length; i++) {
            if ((registers[i].isUninit() && ((UninitReferenceOperand) registers[i]).newBCI == toInit.newBCI)) {
                registers[i] = stackOp;
            }
        }
    }
}

final class SubroutineModificationStack {
    SubroutineModificationStack next;
    boolean[] subRoutineModifications;
    int jsrBCI;
    int depth;

    SubroutineModificationStack(SubroutineModificationStack next, boolean[] subRoutineModifications, int bci) {
        this.next = next;
        if (next == null) {
            depth = 1;
        } else {
            depth = 1 + next.depth();
        }
        this.subRoutineModifications = subRoutineModifications;
        this.jsrBCI = bci;
    }

    static SubroutineModificationStack copy(SubroutineModificationStack tocopy) {
        if (tocopy == null) {
            return null;
        }
        return new SubroutineModificationStack(tocopy.next, tocopy.subRoutineModifications.clone(), tocopy.jsrBCI);
    }

    public void merge(SubroutineModificationStack other) {
        assert other.subRoutineModifications.length == subRoutineModifications.length;
        for (int i = 0; i < subRoutineModifications.length; i++) {
            if (other.subRoutineModifications[i] && !subRoutineModifications[i]) {
                subRoutineModifications[i] = true;
            }
        }
    }

    public int depth() {
        return depth;
    }
}
