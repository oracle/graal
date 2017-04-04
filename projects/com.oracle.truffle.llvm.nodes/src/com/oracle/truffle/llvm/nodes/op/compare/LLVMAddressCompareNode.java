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

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNode;
import com.oracle.truffle.llvm.nodes.op.compare.LLVMAddressCompareNodeGen.ToComparableValueNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
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

    @NodeChild(type = LLVMExpressionNode.class)
    protected abstract static class ToComparableValue extends LLVMExpressionNode {

        protected abstract LLVMAddress executeWithTarget(Object v1);

        @Specialization
        protected LLVMAddress doAddress(LLVMAddress address) {
            return address;
        }

        @Specialization
        protected LLVMAddress doLLVMGlobalVariableDescriptor(LLVMGlobalVariableDescriptor address) {
            return address.getNativeAddress();
        }

        @Child private Node isNull = Message.IS_NULL.createNode();

        @Specialization
        protected LLVMAddress doLLVMTruffleObject(LLVMTruffleObject address) {
            if (ForeignAccess.sendIsNull(isNull, address.getObject())) {
                return LLVMAddress.fromLong(address.getOffset());
            } else {
                return LLVMAddress.fromLong(address.getObject().hashCode() + address.getOffset());
            }
        }

        @Specialization(guards = "notLLVM(address)")
        protected LLVMAddress doTruffleObject(TruffleObject address) {
            if (ForeignAccess.sendIsNull(isNull, address)) {
                return LLVMAddress.fromLong(0);
            } else {
                return LLVMAddress.fromLong(address.hashCode());
            }
        }

        @Specialization
        protected LLVMAddress doLLVMFunction(LLVMFunction address) {
            return LLVMAddress.fromLong(address.getFunctionIndex());
        }

        @Child private ToLLVMNode toLLVM = ToLLVMNode.createNode(long.class);

        @Specialization
        protected LLVMAddress doLLVMBoxedPrimitive(LLVMBoxedPrimitive address) {
            return LLVMAddress.fromLong((long) toLLVM.executeWithTarget(address.getValue()));
        }
    }

    @Child private ToComparableValue convertVal1 = ToComparableValueNodeGen.create(null);
    @Child private ToComparableValue convertVal2 = ToComparableValueNodeGen.create(null);

    @Specialization
    public boolean doCompare(Object val1, Object val2) {
        return op.compare(convertVal1.executeWithTarget(val1), convertVal2.executeWithTarget(val2));
    }

}
