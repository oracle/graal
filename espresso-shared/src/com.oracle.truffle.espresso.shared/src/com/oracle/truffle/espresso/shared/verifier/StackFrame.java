/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.espresso.shared.verifier;

import static com.oracle.truffle.espresso.shared.verifier.MethodVerifier.failVerify;
import static com.oracle.truffle.espresso.shared.verifier.MethodVerifier.formatGuarantee;
import static com.oracle.truffle.espresso.shared.verifier.MethodVerifier.verifyGuarantee;

import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.RuntimeAccess;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

class StackFrame<R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>>
                implements StackMapFrameParser.FrameState<StackFrame<R, C, M, F>, MethodVerifier<R, C, M, F>> {
    final MethodVerifier<R, C, M, F> mv;
    final Operand<R, C, M, F>[] stack;
    final int stackSize;
    final int top;
    final Operand<R, C, M, F>[] locals;
    final SubroutineModificationStack subroutineModificationStack;

    StackFrame(MethodVerifier<R, C, M, F> mv, OperandStack<R, C, M, F> stack, Locals<R, C, M, F> locals) {
        this.mv = mv;
        this.stack = stack.extract();
        this.stackSize = stack.size;
        this.top = stack.top;
        this.locals = locals.extract();
        this.subroutineModificationStack = locals.subRoutineModifications;
    }

    StackFrame(MethodVerifier<R, C, M, F> mv, OperandStack<R, C, M, F> stack, Operand<R, C, M, F>[] locals) {
        this(mv, stack, locals, null);
    }

    StackFrame(MethodVerifier<R, C, M, F> mv, OperandStack<R, C, M, F> stack, Operand<R, C, M, F>[] locals, SubroutineModificationStack sms) {
        this.mv = mv;
        this.stack = stack.extract();
        this.stackSize = stack.size;
        this.top = stack.top;
        this.locals = locals;
        this.subroutineModificationStack = sms;
    }

    StackFrame(MethodVerifier<R, C, M, F> mv) {
        this(mv, new OperandStack<>(mv, mv.getMaxStack()), new Locals<>(mv));
    }

    StackFrame(MethodVerifier<R, C, M, F> mv, Operand<R, C, M, F>[] stack, int stackSize, int top, Operand<R, C, M, F>[] locals) {
        this.mv = mv;
        this.stack = stack;
        this.stackSize = stackSize;
        this.top = top;
        this.locals = locals;
        this.subroutineModificationStack = null;
    }

    StackFrame(MethodVerifier<R, C, M, F> mv, Operand<R, C, M, F>[] stack, int stackSize, int top, Operand<R, C, M, F>[] locals, SubroutineModificationStack sms) {
        this.mv = mv;
        this.stack = stack;
        this.stackSize = stackSize;
        this.top = top;
        this.locals = locals;
        this.subroutineModificationStack = sms;
    }

    OperandStack<R, C, M, F> extractStack(int maxStack) {
        OperandStack<R, C, M, F> res = new OperandStack<>(this.mv, maxStack);
        System.arraycopy(stack, 0, res.stack, 0, top);
        res.size = stackSize;
        res.top = top;
        return res;
    }

    Locals<R, C, M, F> extractLocals() {
        Locals<R, C, M, F> newLocals = new Locals<>(mv, locals.clone());
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
    public StackFrame<R, C, M, F> sameNoStack() {
        return new StackFrame<>(mv, Operand.emptyArray(), 0, 0, locals);
    }

    @Override
    public StackFrame<R, C, M, F> sameLocalsWith1Stack(VerificationTypeInfo vfi, MethodVerifier<R, C, M, F> verifier) {
        Operand<R, C, M, F> op = verifier.getOperandFromVerificationType(vfi);
        formatGuarantee(op.slots() <= verifier.getMaxStack(), "Stack map entry requires more stack than allowed by maxStack.");
        OperandStack<R, C, M, F> newStack = new OperandStack<>(verifier, 2);
        newStack.push(op);
        return new StackFrame<>(mv, newStack, locals);
    }

    @Override
    public StackMapFrameParser.FrameAndLocalEffect<StackFrame<R, C, M, F>, MethodVerifier<R, C, M, F>> chop(int chop, int lastLocal, MethodVerifier<R, C, M, F> verifier) {
        Operand<R, C, M, F>[] newLocals = locals.clone();
        int pos = lastLocal;
        for (int i = 0; i < chop; i++) {
            formatGuarantee(pos >= 0, "Chop frame entry chops more locals than existing.");
            Operand<R, C, M, F> op = newLocals[pos];
            if (op.isTopOperand() && (pos > 0)) {
                if (newLocals[pos - 1].isType2()) {
                    pos--;
                }
            }
            newLocals[pos] = verifier.invalidOp;
            pos--;
        }
        return new StackMapFrameParser.FrameAndLocalEffect<>(new StackFrame<>(mv, Operand.emptyArray(), 0, 0, newLocals), pos - lastLocal);
    }

    @Override
    public StackMapFrameParser.FrameAndLocalEffect<StackFrame<R, C, M, F>, MethodVerifier<R, C, M, F>> append(VerificationTypeInfo[] vfis, MethodVerifier<R, C, M, F> verifier, int lastLocal) {
        verifyGuarantee(vfis.length > 0, "Empty Append Frame in the StackmapTable");
        Operand<R, C, M, F>[] newLocals = locals.clone();
        int pos = lastLocal;
        for (VerificationTypeInfo vti : vfis) {
            Operand<R, C, M, F> op = verifier.getOperandFromVerificationType(vti);
            verifier.setLocal(newLocals, op, ++pos, "Append frame entry in stack map appends more locals than allowed.");
            if (op.isType2()) {
                verifier.setLocal(newLocals, verifier.invalidOp, ++pos, "Append frame entry in stack map appends more locals than allowed.");
            }
        }
        return new StackMapFrameParser.FrameAndLocalEffect<>(new StackFrame<>(mv, Operand.emptyArray(), 0, 0, newLocals), pos - lastLocal);
    }
}

final class OperandStack<R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
    final MethodVerifier<R, C, M, F> mv;
    final Operand<R, C, M, F>[] stack;

    int top;
    int size;

    @SuppressWarnings({"unchecked", "rawtypes"})
    OperandStack(MethodVerifier<R, C, M, F> mv, int maxStack) {
        this.mv = mv;
        this.stack = new Operand[maxStack];
        this.top = 0;
        this.size = 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Operand<R, C, M, F>[] extract() {
        Operand<R, C, M, F>[] result = new Operand[top];
        System.arraycopy(stack, 0, result, 0, top);
        return result;
    }

    void procSize(int modif) {
        size += modif;
        verifyGuarantee(size <= stack.length, "insufficent stack size: " + stack.length);
        verifyGuarantee(size >= 0, "invalid stack access: " + size);
    }

    void pushInt() {
        push(mv.intOp);
    }

    void pushFloat() {
        push(mv.floatOp);
    }

    void pushDouble() {
        push(mv.doubleOp);
    }

    void pushLong() {
        push(mv.longOp);
    }

    void push(Operand<R, C, M, F> kind) {
        procSize(kind.slots());
        if (kind.getKind().isStackInt()) {
            stack[top++] = mv.intOp;
        } else {
            stack[top++] = kind;
        }
    }

    private Operand<R, C, M, F> popAny() {
        verifyGuarantee(top > 0, "Popping an empty stack");
        Operand<R, C, M, F> op = stack[--top];
        procSize(-op.slots());
        return op;
    }

    Operand<R, C, M, F> popRef() {
        Operand<R, C, M, F> op = popAny();
        verifyGuarantee(op.isReference(), "Invalid operand. Expected a reference, found: " + op);
        return op;
    }

    Operand<R, C, M, F> popRef(Operand<R, C, M, F> kind) {
        Operand<R, C, M, F> op = popRef();
        verifyGuarantee(op.compliesWith(kind, mv), "Type check error: " + op + " cannot be merged into " + kind);
        return op;
    }

    public Operand<R, C, M, F> popUninitRef(Operand<R, C, M, F> kind) {
        Operand<R, C, M, F> op = popRef(kind);
        verifyGuarantee(op.isUninit(), "Calling initialization method on already initialized reference.");
        return op;
    }

    Operand<R, C, M, F> popArray() {
        Operand<R, C, M, F> op = popRef();
        verifyGuarantee(op == mv.nullOp || op.isArrayType(), "Invalid operand. Expected array, found: " + op);
        return op;
    }

    void popInt() {
        pop(mv.intOp);
    }

    void popFloat() {
        pop(mv.floatOp);
    }

    void popDouble() {
        pop(mv.doubleOp);
    }

    void popLong() {
        pop(mv.longOp);
    }

    Operand<R, C, M, F> popObjOrRA() {
        Operand<R, C, M, F> op = popAny();
        verifyGuarantee(op.isReference() || op.isReturnAddress(), op + " on stack, required: Reference or ReturnAddress");
        return op;
    }

    Operand<R, C, M, F> pop(Operand<R, C, M, F> k) {
        if (!k.getKind().isStackInt() || k == mv.intOp) {
            Operand<R, C, M, F> op = popAny();
            verifyGuarantee(op.compliesWith(k, mv), op + " on stack, required: " + k);
            return op;
        } else {
            return pop(mv.intOp);
        }
    }

    void dup() {
        procSize(1);
        Operand<R, C, M, F> v = stack[top - 1];
        verifyGuarantee(!v.isType2(), "type 2 operand for dup.");
        verifyGuarantee(!v.isTopOperand(), "dup of Top type.");
        stack[top] = v;
        top++;
    }

    void pop() {
        procSize(-1);
        Operand<R, C, M, F> v = stack[top - 1];
        verifyGuarantee(!v.isType2(), "type 2 operand for pop.");
        verifyGuarantee(!v.isTopOperand(), "dup2x2 of Top type.");
        top--;
    }

    void pop2() {
        procSize(-2);
        Operand<R, C, M, F> v1 = stack[top - 1];
        verifyGuarantee(!v1.isTopOperand(), "dup2x2 of Top type.");
        if (v1.isType2()) {
            top--;
            return;
        }
        Operand<R, C, M, F> v2 = stack[top - 2];
        verifyGuarantee(!v2.isTopOperand(), "dup2x2 of Top type.");
        verifyGuarantee(!v2.isType2(), "type 2 second operand for pop2.");
        top = top - 2;
    }

    void dupx1() {
        procSize(1);
        Operand<R, C, M, F> v1 = stack[top - 1];
        Operand<R, C, M, F> v2 = stack[top - 2];
        verifyGuarantee(!v1.isType2() && !v2.isType2(), "type 2 operand for dupx1.");
        verifyGuarantee(!v1.isTopOperand() && !v2.isTopOperand(), "dupx1 of Top type.");
        System.arraycopy(stack, top - 2, stack, top - 1, 2);
        top++;
        stack[top - 3] = v1;
    }

    void dupx2() {
        procSize(1);
        Operand<R, C, M, F> v1 = stack[top - 1];
        Operand<R, C, M, F> v2 = stack[top - 2];
        verifyGuarantee(!v1.isType2(), "type 2 first operand for dupx2.");
        verifyGuarantee(!v1.isTopOperand() && !v2.isTopOperand(), "dupx2 of Top type.");
        if (v2.isType2()) {
            System.arraycopy(stack, top - 2, stack, top - 1, 2);
            top++;
            stack[top - 3] = v1;
        } else {
            Operand<R, C, M, F> v3 = stack[top - 3];
            verifyGuarantee(!v3.isType2(), "type 2 third operand for dupx2.");
            verifyGuarantee(!v3.isTopOperand(), "dupx2 of Top type.");
            System.arraycopy(stack, top - 3, stack, top - 2, 3);
            top++;
            stack[top - 4] = v1;
        }
    }

    void dup2() {
        procSize(2);
        Operand<R, C, M, F> v1 = stack[top - 1];
        if (v1.isType2()) {
            stack[top] = v1;
            top++;
        } else {
            Operand<R, C, M, F> v2 = stack[top - 2];
            verifyGuarantee(!v2.isType2(), "type 2 second operand for dup2.");
            verifyGuarantee(!v1.isTopOperand() && !v2.isTopOperand(), "dup2 of Top type.");
            System.arraycopy(stack, top - 2, stack, top, 2);
            top = top + 2;
        }
    }

    void dup2x1() {
        procSize(2);
        Operand<R, C, M, F> v1 = stack[top - 1];
        Operand<R, C, M, F> v2 = stack[top - 2];
        verifyGuarantee(!v2.isType2(), "type 2 second operand for dup2x1");
        verifyGuarantee(!v2.isTopOperand() && !v1.isTopOperand(), "dup2x1 of Top type.");
        if (v1.isType2()) {
            System.arraycopy(stack, top - 2, stack, top - 1, 2);
            top++;
            stack[top - 3] = v1;
            return;
        }
        Operand<R, C, M, F> v3 = stack[top - 3];
        verifyGuarantee(!v3.isType2(), "type 2 third operand for dup2x1.");
        verifyGuarantee(!v3.isTopOperand(), "dup2x1 of Top type.");
        System.arraycopy(stack, top - 3, stack, top - 1, 3);
        top = top + 2;
        stack[top - 5] = v2;
        stack[top - 4] = v1;
    }

    void dup2x2() {
        procSize(2);
        Operand<R, C, M, F> v1 = stack[top - 1];
        Operand<R, C, M, F> v2 = stack[top - 2];
        boolean b1 = v1.isType2();
        boolean b2 = v2.isType2();

        verifyGuarantee(!v1.isTopOperand() && !v2.isTopOperand(), "dup2x2 of Top type.");

        if (b1 && b2) {
            System.arraycopy(stack, top - 2, stack, top - 1, 2);
            stack[top - 2] = v1;
            top++;
            return;
        }
        Operand<R, C, M, F> v3 = stack[top - 3];
        boolean b3 = v3.isType2();
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
        Operand<R, C, M, F> v4 = stack[top - 4];
        verifyGuarantee(!v4.isTopOperand(), "dup2x2 of Top type.");
        boolean b4 = v4.isType2();
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
        Operand<R, C, M, F> v1 = stack[top - 1];
        Operand<R, C, M, F> v2 = stack[top - 2];
        verifyGuarantee(!v1.isType2() && !v2.isType2(), "Type 2 operand for SWAP");
        verifyGuarantee(!v1.isTopOperand() && !v2.isTopOperand(), "swap of Top type.");
        stack[top - 1] = v2;
        stack[top - 2] = v1;
    }

    int mergeInto(StackFrame<R, C, M, F> stackFrame) {
        verifyGuarantee(size == stackFrame.stackSize, "Inconsistent stack height: " + size + " != " + stackFrame.stackSize);
        int secondIndex = 0;
        for (int index = 0; index < top; index++) {
            Operand<R, C, M, F> op1 = stack[index];
            Operand<R, C, M, F> op2 = stackFrame.stack[secondIndex++];
            if (!op1.compliesWithInMerge(op2, mv)) {
                return index;
            }
            if (op1.isType2() && op2.isTopOperand()) {
                verifyGuarantee(stackFrame.stack[secondIndex++].isTopOperand(), "Inconsistent stack Map: " + op1 + " vs. " + op2 + " and " + stackFrame.stack[secondIndex - 1]);
            }

        }
        return -1;
    }

    Operand<R, C, M, F> initUninit(UninitReferenceOperand<R, C, M, F> toInit) {
        Operand<R, C, M, F> init = toInit.init();
        for (int i = 0; i < top; i++) {
            if (stack[i].isUninit() && ((UninitReferenceOperand<R, C, M, F>) stack[i]).newBCI == toInit.newBCI) {
                stack[i] = init;
            }
        }
        return init;
    }
}

final class Locals<R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
    final MethodVerifier<R, C, M, F> mv;
    Operand<R, C, M, F>[] registers;

    // Created an inherited in the verifier.
    // Will stay null in most cases.
    SubroutineModificationStack subRoutineModifications;

    @SuppressWarnings({"unchecked", "rawtypes"})
    Locals(MethodVerifier<R, C, M, F> mv) {
        this.mv = mv;
        Operand<R, C, M, F>[] parsedSig = mv.getOperandSig(mv.getSig());
        int sigSize = mv.isStatic() ? 0 : 1;
        for (int i = 0; i < parsedSig.length - 1; i++) {
            Operand<R, C, M, F> op = parsedSig[i];
            sigSize += op.isType2() ? 2 : 1;
        }
        formatGuarantee(sigSize <= mv.getMaxLocals(), "Too many method arguments for the number of locals !");
        this.registers = new Operand[mv.getMaxLocals()];
        int index = 0;
        if (!mv.isStatic()) {
            if (ParserNames._init_.equals(mv.getMethodName())) {
                registers[index++] = new UninitReferenceOperand<>(mv.getThisKlass());
            } else {
                registers[index++] = new ReferenceOperand<>(mv.getThisKlass());
            }
        }
        for (int i = 0; i < parsedSig.length - 1; i++) {
            Operand<R, C, M, F> op = parsedSig[i];
            if (op.getKind().isStackInt()) {
                registers[index++] = mv.intOp;
            } else {
                registers[index++] = op;
            }
            if (op.isType2()) {
                registers[index++] = mv.invalidOp;
            }
        }
        for (; index < mv.getMaxLocals(); index++) {
            registers[index] = mv.invalidOp;
        }
    }

    Locals(MethodVerifier<R, C, M, F> mv, Operand<R, C, M, F>[] registers) {
        this.mv = mv;
        this.registers = registers;
    }

    Operand<R, C, M, F>[] extract() {
        return registers.clone();
    }

    Operand<R, C, M, F> load(int index, Operand<R, C, M, F> expected) {
        Operand<R, C, M, F> op = registers[index];
        verifyGuarantee(op.compliesWith(expected, mv), "Incompatible register type. Expected: " + expected + ", found: " + op);
        if (expected.isType2()) {
            verifyGuarantee(registers[index + 1].isTopOperand(), "Loading corrupted long primitive from locals!");
        }
        return op;
    }

    Operand<R, C, M, F> loadRef(int index) {
        Operand<R, C, M, F> op = registers[index];
        verifyGuarantee(op.isReference(), "Incompatible register type. Expected a reference, found: " + op);
        return op;
    }

    ReturnAddressOperand<R, C, M, F> loadReturnAddress(int index) {
        Operand<R, C, M, F> op = registers[index];
        verifyGuarantee(op.isReturnAddress(), "Incompatible register type. Expected a ReturnAddress, found: " + op);
        return (ReturnAddressOperand<R, C, M, F>) op;
    }

    void store(int index, Operand<R, C, M, F> op) {
        boolean subRoutine = subRoutineModifications != null;
        registers[index] = op;
        if (subRoutine) {
            subRoutineModifications.subRoutineModifications[index] = true;
        }
        if (index >= 1) {
            if (registers[index - 1].isType2()) {
                registers[index - 1] = mv.invalidOp;
                if (subRoutine) {
                    subRoutineModifications.subRoutineModifications[index - 1] = true;
                }
            }
        }
        if (op.isType2()) {
            registers[index + 1] = mv.invalidOp;
            if (subRoutine) {
                subRoutineModifications.subRoutineModifications[index + 1] = true;
            }
        }
    }

    int mergeInto(StackFrame<R, C, M, F> frame) {
        assert registers.length == frame.locals.length;
        Operand<R, C, M, F>[] frameLocals = frame.locals;

        for (int i = 0; i < registers.length; i++) {
            if (!registers[i].compliesWithInMerge(frameLocals[i], mv)) {
                return i;
            }
        }
        return -1;
    }

    void initUninit(UninitReferenceOperand<R, C, M, F> toInit, Operand<R, C, M, F> stackOp) {
        for (int i = 0; i < registers.length; i++) {
            if ((registers[i].isUninit() && ((UninitReferenceOperand<R, C, M, F>) registers[i]).newBCI == toInit.newBCI)) {
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
