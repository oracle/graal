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
package com.oracle.truffle.llvm.nodes.op;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.op.LLVMAddressCompareNodeGen.LLVMAddressEQNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMAddressCompareNodeGen.LLVMAddressNEQNodeGen;
import com.oracle.truffle.llvm.nodes.op.LLVMAddressCompareNodeGen.ToComparableValueNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

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

    public static LLVMExpressionNode create(Kind kind, LLVMExpressionNode l, LLVMExpressionNode r) {
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
                return LLVMAddressEQNodeGen.create(l, r);
            case NEQ:
                return LLVMAddressNEQNodeGen.create(l, r);
            default:
                throw new AssertionError();

        }
    }

    protected abstract static class AddressCompare {

        abstract boolean compare(LLVMAddress val1, LLVMAddress val2);

    }

    private final AddressCompare op;

    public LLVMAddressCompareNode(AddressCompare op) {
        this.op = op;
    }

    @NodeChild(type = LLVMExpressionNode.class)
    protected abstract static class ToComparableValue extends LLVMExpressionNode {

        protected abstract LLVMAddress executeWithTarget(Object v1);

        @Specialization
        protected LLVMAddress doAddress(long address) {
            return LLVMAddress.fromLong(address);
        }

        @Specialization
        protected LLVMAddress doAddress(LLVMAddress address) {
            return address;
        }

        @Specialization
        protected LLVMAddress doLLVMGlobalVariableDescriptor(LLVMGlobalVariable address, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            return globalAccess.getNativeLocation(address);
        }

        @Specialization
        protected LLVMAddress doManagedMalloc(LLVMVirtualAllocationAddress address) {
            if (address.isNull()) {
                return LLVMAddress.fromLong(address.getOffset());
            } else {
                return LLVMAddress.fromLong(getHashCode(address.getObject()) + address.getOffset());
            }
        }

        @Child private Node isNull = Message.IS_NULL.createNode();

        @Specialization
        protected LLVMAddress doLLVMTruffleObject(LLVMTruffleObject address) {
            if (ForeignAccess.sendIsNull(isNull, address.getObject())) {
                return LLVMAddress.fromLong(address.getOffset());
            } else {
                return LLVMAddress.fromLong(getHashCode(address.getObject()) + address.getOffset());
            }
        }

        @TruffleBoundary
        private static int getHashCode(Object address) {
            return address.hashCode();
        }

        @Specialization
        protected LLVMAddress doLLVMFunction(LLVMFunction address) {
            return LLVMAddress.fromLong(address.getFunctionPointer());
        }

        @Child private ForeignToLLVM toLLVM = ForeignToLLVM.create(ForeignToLLVMType.I64);

        @Specialization
        protected LLVMAddress doLLVMBoxedPrimitive(LLVMBoxedPrimitive address) {
            return LLVMAddress.fromLong((long) toLLVM.executeWithTarget(address.getValue()));
        }
    }

    @Child private ToComparableValue convertVal1 = ToComparableValueNodeGen.create(null);
    @Child private ToComparableValue convertVal2 = ToComparableValueNodeGen.create(null);

    @Specialization
    public boolean doGenericCompare(Object val1, Object val2) {
        return op.compare(convertVal1.executeWithTarget(val1), convertVal2.executeWithTarget(val2));
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    abstract static class LLVMAddressEQNode extends LLVMExpressionNode {
        protected boolean isNullAddress(LLVMAddress a) {
            return a.getVal() == 0;
        }

        protected ToComparableValue create() {
            return ToComparableValueNodeGen.create(null);
        }

        @Specialization(guards = {"isNullAddress(val2)"})
        @SuppressWarnings("unused")
        public boolean globalEQNull(LLVMGlobalVariable val1, LLVMAddress val2) {
            return false;
        }

        @Specialization(guards = {"isNullAddress(val1)"})
        @SuppressWarnings("unused")
        public boolean globalEQNull(LLVMAddress val1, LLVMGlobalVariable val2) {
            return false;
        }

        @Specialization
        public boolean globalEQ(LLVMGlobalVariable val1, LLVMGlobalVariable val2) {
            return val1 == val2;
        }

        protected boolean isSpecialCase(Object val1, Object val2) {
            boolean c1 = val1 instanceof LLVMGlobalVariable && val2 instanceof LLVMAddress && isNullAddress((LLVMAddress) val2);
            boolean c2 = val1 instanceof LLVMAddress && val2 instanceof LLVMGlobalVariable && isNullAddress((LLVMAddress) val1);
            boolean c3 = val1 instanceof LLVMGlobalVariable && val2 instanceof LLVMGlobalVariable;
            return c1 || c2 || c3;
        }

        @Specialization(guards = {"!isSpecialCase(val1, val2)"})
        public boolean doGenericCompare(Object val1, Object val2, @Cached("create()") ToComparableValue convertVal1, @Cached("create()") ToComparableValue convertVal2) {
            return convertVal1.executeWithTarget(val1).getVal() == convertVal2.executeWithTarget(val2).getVal();
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    abstract static class LLVMAddressNEQNode extends LLVMExpressionNode {
        protected boolean isNullAddress(LLVMAddress a) {
            return a.getVal() == 0;
        }

        protected ToComparableValue create() {
            return ToComparableValueNodeGen.create(null);
        }

        @Specialization(guards = {"isNullAddress(val2)"})
        @SuppressWarnings("unused")
        public boolean globalNEQNull(LLVMGlobalVariable val1, LLVMAddress val2) {
            return true;
        }

        @Specialization(guards = {"isNullAddress(val1)"})
        @SuppressWarnings("unused")
        public boolean globalNEQNull(LLVMAddress val1, LLVMGlobalVariable val2) {
            return true;
        }

        @Specialization
        public boolean globalNEQ(LLVMGlobalVariable val1, LLVMGlobalVariable val2) {
            return val1 != val2;
        }

        protected boolean isSpecialCase(Object val1, Object val2) {
            boolean c1 = val1 instanceof LLVMGlobalVariable && val2 instanceof LLVMAddress && isNullAddress((LLVMAddress) val2);
            boolean c2 = val1 instanceof LLVMAddress && val2 instanceof LLVMGlobalVariable && isNullAddress((LLVMAddress) val1);
            boolean c3 = val1 instanceof LLVMGlobalVariable && val2 instanceof LLVMGlobalVariable;
            return c1 || c2 || c3;
        }

        @Specialization(guards = {"!isSpecialCase(val1, val2)"})
        public boolean doGenericCompare(Object val1, Object val2, @Cached("create()") ToComparableValue convertVal1, @Cached("create()") ToComparableValue convertVal2) {
            return convertVal1.executeWithTarget(val1).getVal() != convertVal2.executeWithTarget(val2).getVal();
        }
    }

}
