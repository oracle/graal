/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.op;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMNativePointerSupport;
import com.oracle.truffle.llvm.runtime.nodes.util.LLVMSameObjectNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMAddressEqualsNode extends LLVMAbstractCompareNode {

    public static LLVMAddressEqualsNode create() {
        return LLVMAddressEqualsNodeGen.create(null, null);
    }

    @GenerateUncached
    public abstract static class Operation extends LLVMNode {

        public abstract boolean executeWithTarget(Object a, Object b);

        @Specialization
        boolean doCompare(long a, long b) {
            return a == b;
        }

        @Specialization
        boolean doCompare(LLVMNativePointer a, LLVMNativePointer b) {
            return a.asNative() == b.asNative();
        }

        @Specialization
        boolean doCompare(Object a, Object b,
                        @Cached LLVMPointerEqualsNode equals) {
            return equals.execute(a, b);
        }
    }

    // the first two cases are redundant but much more efficient than the ones below

    @Specialization
    boolean doCompare(long a, long b) {
        return a == b;
    }

    @Specialization
    boolean doCompare(LLVMNativePointer a, LLVMNativePointer b) {
        return a.asNative() == b.asNative();
    }

    @Specialization
    boolean doCompare(Object a, Object b,
                    @Cached LLVMPointerEqualsNode equals) {
        return equals.execute(a, b);
    }

    @GenerateUncached
    public abstract static class LLVMPointerEqualsNode extends LLVMNode {

        public abstract boolean execute(Object a, Object b);

        @Specialization(guards = {"nativesA.isPointer(a)", "nativesB.isPointer(b)"}, rewriteOn = UnsupportedMessageException.class)
        boolean doPointerPointer(Object a, Object b,
                        @SuppressWarnings("unused") @Cached LLVMNativePointerSupport nativesA,
                        @SuppressWarnings("unused") @Cached LLVMNativePointerSupport nativesB) throws UnsupportedMessageException {
            return nativesA.asPointer(a) == nativesB.asPointer(b);
        }

        @Specialization(guards = {"nativesA.isPointer(a)", "nativesB.isPointer(b)"}, replaces = "doPointerPointer")
        boolean doPointerPointerException(Object a, Object b,
                        @SuppressWarnings("unused") @Cached LLVMNativePointerSupport nativesA,
                        @SuppressWarnings("unused") @Cached LLVMNativePointerSupport nativesB,
                        @Cached LLVMManagedEqualsNode managedEquals) {
            try {
                return doPointerPointer(a, b, nativesA, nativesB);
            } catch (UnsupportedMessageException ex) {
                /*
                 * Even though both say isPointer == true, one of them threw an exception in
                 * asPointer. This is the same as if one of the objects has isPointer == false.
                 */
                return doOther(a, b, nativesA, nativesB, managedEquals);
            }
        }

        @Specialization(guards = "!nativesA.isPointer(a) || !nativesB.isPointer(b)")
        boolean doOther(Object a, Object b,
                        @SuppressWarnings("unused") @Cached LLVMNativePointerSupport nativesA,
                        @SuppressWarnings("unused") @Cached LLVMNativePointerSupport nativesB,
                        @Cached LLVMManagedEqualsNode managedEquals) {
            return managedEquals.execute(a, b);
        }
    }

    @GenerateUncached
    abstract static class LLVMManagedEqualsNode extends LLVMNode {
        abstract boolean execute(Object a, Object b);

        @Specialization
        protected boolean doForeign(LLVMManagedPointer a, LLVMManagedPointer b,
                        @Cached LLVMSameObjectNode pointToSameObject) {
            return pointToSameObject.execute(a.getObject(), b.getObject()) && a.getOffset() == b.getOffset();
        }

        @Specialization(guards = "isNative(p1) || isNative(p2)")
        protected boolean doManagedNative(LLVMPointer p1, LLVMPointer p2) {
            assert LLVMManagedPointer.isInstance(p1) || LLVMManagedPointer.isInstance(p2) : "the case where both pointers are native is handled earlier, so one has to be managed";
            // one of the pointers is native, the other not, so they can't be equal
            return false;
        }

        /**
         * @param a
         * @param b
         * @see #execute(Object, Object)
         */
        @Specialization(guards = "a.getClass() != b.getClass()")
        protected boolean doDifferentType(Object a, Object b) {
            // different type, and at least one of them is managed, and not a pointer
            // these objects cannot have the same address
            return false;
        }

        protected static boolean isNative(LLVMPointer p) {
            return LLVMNativePointer.isInstance(p);
        }
    }
}
