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
import static com.oracle.truffle.espresso.verifier.MethodVerifier.isType2;

import com.oracle.truffle.espresso.descriptors.Symbol.Name;

class StackFrame {
    final Operand[] stack;
    final int stackSize;
    final int top;
    final Operand[] locals;
    final SubroutineModificationStack subroutineModificationStack;

    // For stackMap extraction
    int lastLocal;

    StackFrame(OperandStack stack, Locals locals) {
        this.stack = stack.extract();
        this.stackSize = stack.size;
        this.top = stack.top;
        this.locals = locals.extract();
        this.subroutineModificationStack = locals.subRoutineModifications;
    }

    StackFrame(OperandStack stack, Operand[] locals) {
        this.stack = stack.extract();
        this.stackSize = stack.size;
        this.top = stack.top;
        this.locals = locals;
        this.subroutineModificationStack = null;
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
        int last = (mv.isStatic() ? -1 : 0);
        for (int i = 0; i < mv.getSig().length - 1; i++) {
            if (isType2(locals[++last])) {
                last++;
            }
        }
        this.lastLocal = last;
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
        if (size > stack.length) {
            throw new VerifyError("insufficent stack size: " + stack.length);
        }
        if (size < 0) {
            throw new VerifyError("invalid stack access: " + size);
        }
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
        procSize(isType2(kind) ? 2 : 1);
        if (size > stack.length) {
            throw new VerifyError("insufficent stack size: " + stack.length);
        }
        if (kind.getKind().isStackInt()) {
            stack[top++] = Int;
        } else {
            stack[top++] = kind;
        }
    }

    void popInt() {
        pop(Int);
    }

    Operand popRef() {
        procSize(-1);
        Operand op = stack[--top];
        if (!op.isReference()) {
            throw new VerifyError("Invalid operand. Expected a reference, found: " + op);
        }
        return op;
    }

    Operand popRef(Operand kind) {
        procSize(-(isType2(kind) ? 2 : 1));
        Operand op = stack[--top];
        if (!op.isReference()) {
            throw new VerifyError("Popped " + op + " when a reference was expected!");
        }
        if (!op.compliesWith(kind)) {
            throw new VerifyError("Type check error: " + op + " cannot be merged into " + kind);
        }
        return op;
    }

    public Operand popUninitRef(Operand kind) {
        procSize(-(isType2(kind) ? 2 : 1));
        Operand op = stack[--top];
        if (!op.compliesWith(kind)) {
            throw new VerifyError("Type check error: " + op + " cannot be merged into " + kind);
        }
        if (!op.isUninit()) {
            throw new VerifyError("Calling initialization method on already initialized reference.");
        }
        return op;
    }

    Operand popArray() {
        procSize(-1);
        Operand op = stack[--top];
        if (!(op == Null || op.isArrayType())) {
            throw new VerifyError("Invalid operand. Expected array, found: " + op);
        }
        return op;
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
        procSize(-1);
        Operand op = stack[--top];
        if (!(op.isReference() || op.isReturnAddress())) {
            throw new VerifyError(op + " on stack, required: Reference or ReturnAddress");
        }
        return op;
    }

    Operand pop(Operand k) {
        if (!k.getKind().isStackInt() || k == Int) {
            procSize((isType2(k) ? -2 : -1));
            Operand op = stack[--top];
            if (!(op.compliesWith(k))) {
                throw new VerifyError(stack[top] + " on stack, required: " + k);
            }
            return op;
        } else {
            return pop(Int);
        }
    }

    void dup() {
        procSize(1);
        if (isType2(stack[top - 1])) {
            throw new VerifyError("type 2 operand for dup.");
        }
        stack[top] = stack[top - 1];
        top++;
    }

    void pop() {
        procSize(-1);
        Operand v1 = stack[top - 1];
        if (isType2(v1)) {
            throw new VerifyError("type 2 operand for pop.");
        }
        top--;
    }

    void pop2() {
        procSize(-2);
        Operand v1 = stack[top - 1];
        if (isType2(v1)) {
            top--;
            return;
        }
        Operand v2 = stack[top - 2];
        if (isType2(v2)) {
            throw new VerifyError("type 2 second operand for pop2.");
        }
        top = top - 2;
    }

    void dupx1() {
        procSize(1);
        Operand v1 = stack[top - 1];
        if (isType2(v1) || isType2(stack[top - 2])) {
            throw new VerifyError("type 2 operand for dupx1.");
        }
        System.arraycopy(stack, top - 2, stack, top - 1, 2);
        top++;
        stack[top - 3] = v1;
    }

    void dupx2() {
        procSize(1);
        Operand v1 = stack[top - 1];
        if (isType2(v1)) {
            throw new VerifyError("type 2 first operand for dupx2.");
        }
        Operand v2 = stack[top - 2];
        if (isType2(v2)) {
            System.arraycopy(stack, top - 2, stack, top - 1, 2);
            top++;
            stack[top - 3] = v1;
        } else {
            if (isType2(stack[top - 3])) {
                throw new VerifyError("type 2 third operand for dupx2.");
            }
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
            if (isType2(stack[top - 2])) {
                throw new VerifyError("type 2 second operand for dup2.");
            }
            System.arraycopy(stack, top - 2, stack, top, 2);
            top = top + 2;
        }
    }

    void dup2x1() {
        procSize(2);
        Operand v1 = stack[top - 1];
        Operand v2 = stack[top - 2];
        if (isType2(v2)) {
            throw new VerifyError("type 2 second operand for dup2x1");
        }
        if (isType2(v1)) {
            System.arraycopy(stack, top - 2, stack, top - 1, 2);
            top++;
            stack[top - 3] = v1;
            return;
        }
        if (isType2(stack[top - 3])) {
            throw new VerifyError("type 2 third operand for dup2x1.");
        }
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

        if (b1 && b2) {
            System.arraycopy(stack, top - 2, stack, top - 1, 2);
            stack[top - 2] = v1;
            top++;
            return;
        }
        Operand v3 = stack[top - 3];
        boolean b3 = isType2(v3);
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
        boolean b4 = isType2(v4);
        if (!b1 && !b2 && !b3 && !b4) {
            System.arraycopy(stack, top - 4, stack, top - 2, 4);
            stack[top - 4] = v2;
            stack[top - 3] = v1;
            top = top + 2;
            return;
        }
        throw new VerifyError("Calling dup2x2 with operands: " + v1 + ", " + v2 + ", " + v3 + ", " + v4);

    }

    void swap() {
        Operand v1 = stack[top - 1];
        Operand v2 = stack[top - 2];
        boolean b1 = isType2(v1);
        boolean b2 = isType2(v2);
        if (!b1 && !b2) {
            stack[top - 1] = v2;
            stack[top - 2] = v1;
            return;
        }
        throw new VerifyError("Type 2 operand for SWAP");
    }

    int mergeInto(StackFrame stackFrame) {
        if (size != stackFrame.stackSize) {
            throw new VerifyError("Inconsistent stack height: " + top + " != " + stackFrame.stack.length);
        }
        int secondIndex = 0;
        for (int index = 0; index < top; index++) {
            Operand op1 = stack[index];
            Operand op2 = stackFrame.stack[secondIndex++];
            if (!op1.compliesWithInMerge(op2)) {
                return index;
            }
            if (isType2(op1) && op2 == Invalid) {
                if (stackFrame.stack[secondIndex++] != Invalid) {
                    throw new VerifyError("Inconsistent stack Map: " + op1 + " vs. " + op2 + " and " + stackFrame.stack[secondIndex - 1]);
                }
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
        if (parsedSig.length - (mv.isStatic() ? 1 : 0) > mv.getMaxLocals()) {
            throw new ClassFormatError("Too many method arguments for the number of locals !");
        }
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
        if (!op.compliesWith(expected)) {
            throw new VerifyError("Incompatible register type. Expected: " + expected + ", found: " + op);
        }
        if (isType2(expected)) {
            if (registers[index + 1] != Invalid) {
                throw new VerifyError("Loading corrupted long primitive from locals!");
            }
        }
        return op;
    }

    Operand loadRef(int index) {
        Operand op = registers[index];
        if (!op.isReference()) {
            throw new VerifyError("Incompatible register type. Expected a reference, found: " + op);
        }
        return op;
    }

    ReturnAddressOperand loadReturnAddress(int index) {
        Operand op = registers[index];
        if (!op.isReturnAddress()) {
            throw new VerifyError("Incompatible register type. Expected a ReturnAddress, found: " + op);
        }
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
