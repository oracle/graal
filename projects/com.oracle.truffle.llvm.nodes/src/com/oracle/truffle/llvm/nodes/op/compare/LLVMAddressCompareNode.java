/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.op.compare;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMAddressCompareNode extends LLVMExpressionNode {

    public enum Kind {
        ULT,
        UGT,
        UGE,
        ULE,
        SLE,
        SGT,
        SGE,
        SLT,
        EQ,
        NEQ,
    }

    public static LLVMAddressCompareNode create(Kind kind, LLVMExpressionNode l, LLVMExpressionNode r) {
        switch (kind) {
            case SLT:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.signedLessThan(val2);
                    }

                }, l, r);

            case SGE:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.signedGreaterEquals(val2);
                    }

                }, l, r);
            case SGT:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.signedGreaterThan(val2);
                    }

                }, l, r);
            case SLE:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.signedLessEquals(val2);
                    }

                }, l, r);
            case UGE:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.unsignedGreaterEquals(val2);
                    }

                }, l, r);
            case UGT:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.unsignedGreaterThan(val2);
                    }

                }, l, r);
            case ULE:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.unsignedLessEquals(val2);
                    }

                }, l, r);
            case ULT:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.unsignedLessThan(val2);
                    }

                }, l, r);

            case EQ:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.getVal() == val2.getVal();
                    }

                }, l, r);

            case NEQ:
                return LLVMAddressCompareNodeGen.create(new AddressCompare() {

                    @Override
                    public boolean compare(LLVMAddress val1, LLVMAddress val2) {
                        return val1.getVal() != val2.getVal();
                    }

                }, l, r);
            default:
                throw new AssertionError();

        }
    }

    protected abstract static class AddressCompare {

        abstract boolean compare(LLVMAddress val1, LLVMAddress val2);

        boolean compare(long val1, long val2) {
            return compare(LLVMAddress.fromLong(val1), LLVMAddress.fromLong(val2));
        }

    }

    private final AddressCompare op;

    public LLVMAddressCompareNode(AddressCompare op) {
        this.op = op;
    }

    @Specialization
    public boolean doCompare(LLVMAddress val1, LLVMAddress val2) {
        return op.compare(val1, val2);
    }

    @Specialization
    public boolean doCompare(LLVMGlobalVariableDescriptor val1, LLVMAddress val2) {
        return op.compare(val1.getNativeAddress(), val2);
    }

    @Specialization
    public boolean doCompare(LLVMAddress val1, LLVMGlobalVariableDescriptor val2) {
        return op.compare(val1, val2.getNativeAddress());
    }

    @Specialization
    public boolean doCompare(LLVMGlobalVariableDescriptor val1, LLVMGlobalVariableDescriptor val2) {
        return op.compare(val1.getNativeAddress(), val2.getNativeAddress());
    }

    @Child private Node unbox1 = Message.UNBOX.createNode();
    @Child private Node unbox2 = Message.UNBOX.createNode();
    @Child private Node isBoxed1 = Message.IS_BOXED.createNode();
    @Child private Node isBoxed2 = Message.IS_BOXED.createNode();
    @Child private ToLLVMNode toLLVM1 = ToLLVMNode.createNode(long.class);
    @Child private ToLLVMNode toLLVM2 = ToLLVMNode.createNode(long.class);

    @Specialization
    public boolean doCompare(LLVMTruffleObject val1, LLVMTruffleObject val2) {
        return internalCompare(val1.getObject(), val1.getOffset(), val2.getObject(), val2.getOffset());
    }

    private boolean internalCompare(TruffleObject to1, long offset1, TruffleObject to2, long offset2) {
        try {
            if (ForeignAccess.sendIsBoxed(isBoxed1, to1) && ForeignAccess.sendIsBoxed(isBoxed2, to2)) {
                long v1 = (long) toLLVM1.executeWithTarget(ForeignAccess.sendUnbox(unbox1, to1));
                long v2 = (long) toLLVM2.executeWithTarget(ForeignAccess.sendUnbox(unbox2, to2));
                return op.compare(v1 + offset1, v2 + offset2);
            } else {
                return op.compare(to1.hashCode() + offset1, to2.hashCode() + offset2);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @Specialization
    public boolean doCompare(LLVMTruffleObject val1, LLVMAddress val2) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Cannot compare foreign with C data: " + val1 + " " + val2);
    }

    @Specialization
    public boolean doCompare(LLVMAddress val1, LLVMTruffleObject val2) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Cannot compare foreign with C data: " + val1 + " " + val2);
    }

    @Specialization
    public boolean doCompare(LLVMTruffleObject val1, LLVMGlobalVariableDescriptor val2) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Cannot compare foreign with C data: " + val1 + " " + val2);
    }

    @Specialization
    public boolean doCompare(LLVMGlobalVariableDescriptor val1, LLVMTruffleObject val2) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Cannot compare foreign with C data: " + val1 + " " + val2);
    }

    @Specialization(guards = {"notLLVM(val1)", "notLLVM(val2)"})
    public boolean doCompare(TruffleObject val1, TruffleObject val2) {
        return internalCompare(val1, 0, val2, 0);
    }

    @Specialization(guards = {"notLLVM(val2)"})
    public boolean doCompare(LLVMAddress val1, TruffleObject val2) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Cannot compare foreign with C data: " + val1 + " " + val2);
    }

    @Specialization(guards = {"notLLVM(val1)"})
    public boolean doCompare(TruffleObject val1, LLVMAddress val2) {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Cannot compare foreign with C data: " + val1 + " " + val2);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"notLLVM(val2)"})
    public boolean doCompare(LLVMGlobalVariableDescriptor val1, TruffleObject val2) {
        return false;
    }

    @Specialization(guards = {"notLLVM(val2)"})
    public boolean doCompare(LLVMTruffleObject val1, TruffleObject val2) {
        return internalCompare(val1.getObject(), val1.getOffset(), val2, 0);
    }

    @Specialization(guards = {"notLLVM(val1)"})
    public boolean doCompare(TruffleObject val1, LLVMTruffleObject val2) {
        return internalCompare(val1, 0, val2.getObject(), val2.getOffset());
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"notLLVM(val1)"})
    public boolean doCompare(TruffleObject val1, LLVMGlobalVariableDescriptor val2) {
        return false;
    }

    @Specialization
    public boolean executeI1(LLVMFunction val1, LLVMFunction val2) {
        return op.compare(val1.getFunctionIndex(), val2.getFunctionIndex());
    }

}
