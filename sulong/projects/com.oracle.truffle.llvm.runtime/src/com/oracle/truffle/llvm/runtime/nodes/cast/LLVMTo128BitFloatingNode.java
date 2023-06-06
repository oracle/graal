/*
 * Copyright (c) 2023, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMIVarBitLarge;
import com.oracle.truffle.llvm.runtime.floating.LLVM128BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMTo128BitFloatingNode extends LLVMExpressionNode {

    protected final boolean isRecursive;

    protected LLVMTo128BitFloatingNode() {
        this(false);
    }

    protected LLVMTo128BitFloatingNode(boolean isRecursive) {
        this.isRecursive = isRecursive;
    }

    protected abstract LLVM128BitFloat executeWith(long value);

    protected LLVMTo128BitFloatingNode createRecursive() {
        throw new IllegalStateException("abstract node LLVMTo128BitFloatingNode used");
    }

    @Specialization(guards = "!isRecursive")
    protected LLVM128BitFloat doPointer(LLVMPointer from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                    @Cached("createRecursive()") LLVMTo128BitFloatingNode recursive) {
        long ptr = toNative.executeWithTarget(from).asNative();
        return recursive.executeWith(ptr);
    }

    public abstract static class LLVMSignedCastToLLVM128BitFloatNode extends LLVMTo128BitFloatingNode {

        protected LLVMSignedCastToLLVM128BitFloatNode() {
        }

        protected LLVMSignedCastToLLVM128BitFloatNode(boolean isRecursive) {
            super(isRecursive);
        }

        @Override
        protected LLVMTo128BitFloatingNode createRecursive() {
            return LLVMTo128BitFloatingNodeGen.LLVMSignedCastToLLVM128BitFloatNodeGen.create(true, null);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(long from) {
            return LLVM128BitFloat.fromLong(from);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(double from) {
            return LLVM128BitFloat.fromDouble(from);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(int from) {
            return LLVM128BitFloat.fromInt(from);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(float from) {
            return LLVM128BitFloat.fromFloat(from);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(LLVM128BitFloat from) {
            return from;
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(LLVMIVarBitLarge from) {
            return LLVM128BitFloat.fromBytesBigEndian(from.getBytes());
        }
    }

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMUnsignedCastToLLVM128BitFloatNode extends LLVMTo128BitFloatingNode {

        protected LLVMUnsignedCastToLLVM128BitFloatNode() {
        }

        protected LLVMUnsignedCastToLLVM128BitFloatNode(boolean isRecursive) {
            super(isRecursive);
        }

        @Override
        protected LLVMTo128BitFloatingNode createRecursive() {
            return LLVMTo128BitFloatingNodeGen.LLVMUnsignedCastToLLVM128BitFloatNodeGen.create(true, null);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(float from) {
            return LLVM128BitFloat.fromFloat(from);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(double from) {
            return LLVM128BitFloat.fromDouble(from);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(LLVM128BitFloat from) {
            return from;
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(LLVMIVarBitLarge from) {
            return LLVM128BitFloat.fromBytesBigEndian(from.getBytes());
        }
    }
}
