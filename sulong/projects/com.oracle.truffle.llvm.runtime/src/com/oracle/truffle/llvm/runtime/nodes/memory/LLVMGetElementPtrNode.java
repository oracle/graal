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
package com.oracle.truffle.llvm.runtime.nodes.memory;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.interop.LLVMNegatedForeignObject;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMGetElementPtrNodeGen.LLVMIncrementPointerNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
@NodeField(type = long.class, name = "typeWidth")
@NodeField(type = Type.class, name = "targetType")
public abstract class LLVMGetElementPtrNode extends LLVMExpressionNode {

    @Child private LLVMIncrementPointerNode incrementNode = LLVMIncrementPointerNodeGen.create();

    public abstract long getTypeWidth();

    public abstract Type getTargetType();

    @Specialization
    protected LLVMPointer longIncrement(LLVMPointer addr, long val) {
        long incr = getTypeWidth() * val;
        return incrementNode.executeWithTarget(addr, incr);
    }

    @Specialization
    protected LLVMPointer longIncrement(LLVMPointer addr, LLVMNativePointer val) {
        long incr = getTypeWidth() * val.asNative();
        return incrementNode.executeWithTarget(addr, incr);
    }

    @Specialization
    protected LLVMPointer intIncrement(LLVMPointer addr, int val) {
        long incr = getTypeWidth() * val;
        return incrementNode.executeWithTarget(addr, incr);
    }

    protected static boolean isNegated(Object obj, Object negatedObj) {
        if (negatedObj instanceof LLVMNegatedForeignObject) {
            return ((LLVMNegatedForeignObject) negatedObj).getForeign() == obj;
        } else {
            return false;
        }
    }

    @Specialization(guards = "isNegated(addr.getObject(), val.getObject())")
    protected LLVMPointer pointerDiff(LLVMManagedPointer addr, LLVMManagedPointer val) {
        return LLVMNativePointer.create(addr.getOffset() + val.getOffset());
    }

    @Specialization(guards = "isNegated(val.getObject(), addr.getObject())")
    protected LLVMPointer pointerDiffRev(LLVMManagedPointer addr, LLVMManagedPointer val) {
        return LLVMNativePointer.create(val.getOffset() + addr.getOffset());
    }

    @Specialization
    protected LLVMPointerVector doPointer(LLVMPointerVector val1, LLVMI64Vector val2) {
        LLVMPointer[] result = new LLVMPointer[val1.getLength()];
        for (int i = 0; i < result.length; i++) {
            result[i] = longIncrement(val1.getValue(i), val2.getValue(i));
        }
        return LLVMPointerVector.create(result);
    }

    public abstract static class LLVMIncrementPointerNode extends LLVMNode {

        public abstract LLVMPointer executeWithTarget(LLVMPointer addr, int val);

        public abstract LLVMPointer executeWithTarget(LLVMPointer addr, long val);

        public abstract LLVMPointer executeWithTarget(LLVMPointer addr, Object val);

        @Specialization
        protected LLVMPointer doPointer(LLVMPointer addr, int incr) {
            return addr.increment(incr);
        }

        @Specialization
        protected LLVMPointer doPointer(LLVMPointer addr, long incr) {
            return addr.increment(incr);
        }
    }
}
