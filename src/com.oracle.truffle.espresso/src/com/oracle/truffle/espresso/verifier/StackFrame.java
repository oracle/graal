package com.oracle.truffle.espresso.verifier;

import static com.oracle.truffle.espresso.verifier.MethodVerifier.Double;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.Float;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.Int;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.Invalid;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.Long;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.Null;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.isType2;

import com.oracle.truffle.espresso.descriptors.Symbol;

class StackFrame {
    final Operand[] stack;
    final int stackSize;
    final int top;
    final Operand[] locals;
    int lastLocal;

    StackFrame(Stack stack, Locals locals) {
        this.stack = stack.extract();
        this.stackSize = stack.size;
        this.top = stack.top;
        this.locals = locals.extract();
    }

    StackFrame(Stack stack, Operand[] locals) {
        this.stack = stack.extract();
        this.stackSize = stack.size;
        this.top = stack.top;
        this.locals = locals;
    }

    StackFrame(MethodVerifier mv) {
        this(new Stack(mv.getMaxStack()), new Locals(mv));
        int last = (mv.isStatic() ? -1 : 0);
        for (int i = 0; i < mv.getSig().length - 1; i++) {
            if (isType2(locals[++last])) {
                last++;
            }
        }
        this.lastLocal = last;
    }

    public StackFrame(Operand[] stack, int stackSize, int top, Operand[] locals) {
        this.stack = stack;
        this.stackSize = stackSize;
        this.top = top;
        this.locals = locals;
    }

    Stack extractStack(int maxStack) {
        Stack res = new Stack(maxStack);
        System.arraycopy(stack, 0, res.stack, 0, top);
        res.size = stackSize;
        res.top = top;
        return res;
    }

    Locals extractLocals() {
        return new Locals(locals.clone());
    }
}

class Stack {
    final Operand[] stack;

    int top;
    int size;

    Stack(int maxStack) {
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
            if (!kind.isReference() || kind.isArrayType() || !kind.getKlass().isInterface()) {
                // a class not implementing an interface is a ClassFormatError at *Runtime*
                throw new VerifyError("Type check error: " + op + " cannot be merged into " + kind);
            }
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
        if (top != stackFrame.stack.length) {
            throw new VerifyError("Inconsistent stack height: " + top + " != " + stackFrame.stack.length);
        }
        for (int i = 0; i < top; i++) {
            if (!stack[i].compliesWith(stackFrame.stack[i])) {
                return i;
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
//
// abstract class Locals {
// abstract Operand[] extract();
//
// abstract Operand load(int index, Operand expected);
//
// abstract Operand loadRef(int index);
//
// abstract ReturnAddressOperand loadReturnAddress(int index);
//
// abstract void store(int index, Operand op);
//
// abstract int mergeInto(StackFrame frame);
//
// abstract void initUninit(UninitReferenceOperand toInit, Operand stackOp);
// }

class Locals {
    Operand[] registers;

    Locals(MethodVerifier mv) {
        Operand[] parsedSig = mv.getOperandSig(mv.getSig());
        if (parsedSig.length - (mv.isStatic() ? 1 : 0) > mv.getMaxLocals()) {
            throw new ClassFormatError("Too many method arguments for the number of locals !");
        }
        this.registers = new Operand[mv.getMaxLocals()];
        int index = 0;
        if (!mv.isStatic()) {
            if (mv.getMethodName() == Symbol.Name.INIT) {
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
        registers[index] = op;
        if (isType2(op)) {
            registers[index + 1] = Invalid;
        }
    }

    int mergeInto(StackFrame frame) {
        assert registers.length == frame.locals.length;
        Operand[] frameLocals = frame.locals;

        for (int i = 0; i < registers.length; i++) {
            if (!registers[i].compliesWith(frameLocals[i])) {
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
//
// class SubroutineLocals extends Locals {
// final Locals locals;
// Operand[] registers;
//
// SubroutineLocals(Locals l, int maxLocals) {
// this.locals = l;
// this.registers = new Operand[maxLocals];
// }
//
// @Override
// Operand[] extract() {
// Operand[] res = new Operand[registers.length];
// for (int i = 0; i < registers.length; i++) {
// if (registers[i] != null) {
// res[i] = registers[i];
// } else {
// res[i] = locals.load(i, Invalid);
// }
// }
// return res;
// }
//
// @Override
// Operand load(int index, Operand expected) {
// Operand op = registers[index];
// if (op != null) {
// if (!op.compliesWith(expected)) {
// throw new VerifyError("Incompatible register type. Expected: " + expected + ", found: " + op);
// }
// if (isType2(expected)) {
// if (registers[index + 1] != Invalid) {
// throw new VerifyError("Loading corrupted long primitive from locals!");
// }
// }
// return op;
// }
// return locals.load(index, expected);
// }
//
// @Override
// Operand loadRef(int index) {
// Operand op = registers[index];
// if (op != null) {
// if (!op.isReference()) {
// throw new VerifyError("Incompatible register type. Expected a reference, found: " + op);
// }
// return op;
// }
// return locals.loadRef(index);
// }
//
// @Override
// ReturnAddressOperand loadReturnAddress(int index) {
// Operand op = registers[index];
// if (op != null) {
// if (!op.isReturnAddress()) {
// throw new VerifyError("Incompatible register type. Expected a ReturnAddress, found: " + op);
// }
// return (ReturnAddressOperand) op;
// }
// return locals.loadReturnAddress(index);
// }
//
// @Override
// void store(int index, Operand op) {
// registers[index] = op;
// if (isType2(op)) {
// registers[index + 1] = Invalid;
// }
// }
//
// @Override
// int mergeInto(StackFrame frame) {
// assert registers.length == frame.locals.length;
// Operand[] frameLocals = frame.locals;
//
// for (int i = 0; i < registers.length; i++) {
// Operand op = registers[i];
// if (op == null) {
// op = locals.load(i, Invalid);
// }
// if (!op.compliesWith(frameLocals[i])) {
// return i;
// }
// }
// return -1;
// }
//
// @Override
// void initUninit(UninitReferenceOperand toInit, Operand stackOp) {
// for (int i = 0; i < registers.length; i++) {
// Operand op = registers[i];
// if (op == null) {
// op = locals.load(i, Invalid);
// }
// if ((op.isUninit() && ((UninitReferenceOperand) op).newBCI == toInit.newBCI)) {
// registers[i] = stackOp;
// }
// }
// }
// }
