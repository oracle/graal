/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.nodes.op.LLVMPointerCompareNode.LLVMPointToSameObjectNode;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.library.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMAddressEqualsNode extends LLVMAbstractCompareNode {

    @Specialization(guards = {"libA.isPointer(a)", "libB.isPointer(b)"}, limit = "8", rewriteOn = UnsupportedMessageException.class)
    protected boolean doPointerPointer(Object a, Object b,
                    @CachedLibrary("a") LLVMNativeLibrary libA,
                    @CachedLibrary("b") LLVMNativeLibrary libB) throws UnsupportedMessageException {
        return libA.asPointer(a) == libB.asPointer(b);
    }

    @Specialization(guards = {"libA.isPointer(a)", "libB.isPointer(b)"}, limit = "8")
    protected boolean doPointerPointerException(Object a, Object b,
                    @CachedLibrary("a") LLVMNativeLibrary libA,
                    @CachedLibrary("b") LLVMNativeLibrary libB,
                    @Cached LLVMManagedEqualsNode managedEquals) {
        try {
            return doPointerPointer(a, b, libA, libB);
        } catch (UnsupportedMessageException ex) {
            // even though both say isPointer == true, one of them threw an exception in asPointer
            // this is the same as if one of the objects has isPointer == false
            return doOther(a, b, libA, libB, managedEquals);
        }
    }

    @Specialization(guards = "!libA.isPointer(a) || !libB.isPointer(b)", limit = "8")
    protected boolean doOther(Object a, Object b,
                    @SuppressWarnings("unused") @CachedLibrary("a") LLVMNativeLibrary libA,
                    @SuppressWarnings("unused") @CachedLibrary("b") LLVMNativeLibrary libB,
                    @Cached LLVMManagedEqualsNode managedEquals) {
        return managedEquals.execute(a, b);
    }

    abstract static class LLVMManagedEqualsNode extends LLVMNode {
        abstract boolean execute(Object a, Object b);

        @Specialization
        protected boolean doForeign(LLVMManagedPointer a, LLVMManagedPointer b,
                        @Cached LLVMPointToSameObjectNode pointToSameObject) {
            return pointToSameObject.execute(a, b) && a.getOffset() == b.getOffset();
        }

        @Specialization
        protected boolean doVirtual(LLVMVirtualAllocationAddress v1, LLVMVirtualAllocationAddress v2) {
            return v1.getObject() == v2.getObject() && v1.getOffset() == v2.getOffset();
        }

        @Specialization(guards = "isNative(p1) || isNative(p2)")
        protected boolean doManagedNative(LLVMPointer p1, LLVMPointer p2) {
            assert LLVMManagedPointer.isInstance(p1) || LLVMManagedPointer.isInstance(p2) : "the case where both pointers are native is handled earlier, so one has to be managed";
            // one of the pointers is native, the other not, so they can't be equal
            return false;
        }

        @Specialization(guards = "a.getClass() != b.getClass()")
        @SuppressWarnings("unused")
        protected boolean doDifferentType(Object a, Object b) {
            // different type, and at least one of them is managed, and not a pointer
            // these objects can not have the same address
            return false;
        }

        protected boolean isNative(LLVMPointer p) {
            return LLVMNativePointer.isInstance(p);
        }
    }
}
