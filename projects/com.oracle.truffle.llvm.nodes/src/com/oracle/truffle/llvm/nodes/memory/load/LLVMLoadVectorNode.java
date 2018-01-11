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
package com.oracle.truffle.llvm.nodes.memory.load;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMAddressVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeField(name = "size", type = int.class)
public abstract class LLVMLoadVectorNode extends LLVMLoadNode {

    public abstract int getSize();

    LLVMForeignReadNode[] createForeignReads(ForeignToLLVMType type, int readSize) {
        LLVMForeignReadNode[] result = new LLVMForeignReadNode[getSize()];
        for (int i = 0; i < result.length; i++) {
            result[i] = new LLVMForeignReadNode(type, readSize);
        }
        return result;
    }

    public abstract static class LLVMLoadI1VectorNode extends LLVMLoadVectorNode {
        @Specialization
        protected LLVMI1Vector doI1Vector(LLVMAddress addr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getI1Vector(addr, getSize());
        }

        @Specialization
        protected LLVMI1Vector doI1Vector(VirtualFrame frame, LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getI1Vector(globalAccess.executeWithTarget(frame, addr), getSize());
        }

        @Specialization
        protected LLVMI1Vector doForeign(VirtualFrame frame, LLVMTruffleObject addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            boolean[] vector = new boolean[getSize()];
            LLVMTruffleObject currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (Boolean) foreignReads[i].execute(frame, currentPtr);
                currentPtr = currentPtr.increment(I1_SIZE_IN_BYTES, currentPtr.getType());
            }
            return LLVMI1Vector.create(vector);
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.I1, I1_SIZE_IN_BYTES);
        }
    }

    public abstract static class LLVMLoadI8VectorNode extends LLVMLoadVectorNode {
        @Specialization
        protected LLVMI8Vector doI8Vector(VirtualFrame frame, LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getI8Vector(globalAccess.executeWithTarget(frame, addr), getSize());
        }

        @Specialization
        protected LLVMI8Vector doI8Vector(LLVMAddress addr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getI8Vector(addr, getSize());
        }

        @Specialization
        protected LLVMI8Vector doForeign(VirtualFrame frame, LLVMTruffleObject addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            byte[] vector = new byte[getSize()];
            LLVMTruffleObject currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (Byte) foreignReads[i].execute(frame, currentPtr);
                currentPtr = currentPtr.increment(I8_SIZE_IN_BYTES, currentPtr.getType());
            }
            return LLVMI8Vector.create(vector);
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.I8, I8_SIZE_IN_BYTES);
        }
    }

    public abstract static class LLVMLoadI16VectorNode extends LLVMLoadVectorNode {
        @Specialization
        protected LLVMI16Vector doI16Vector(VirtualFrame frame, LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getI16Vector(globalAccess.executeWithTarget(frame, addr), getSize());
        }

        @Specialization
        protected LLVMI16Vector doI16Vector(LLVMAddress addr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getI16Vector(addr, getSize());
        }

        @Specialization
        protected LLVMI16Vector doForeign(VirtualFrame frame, LLVMTruffleObject addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            short[] vector = new short[getSize()];
            LLVMTruffleObject currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (Short) foreignReads[i].execute(frame, currentPtr);
                currentPtr = currentPtr.increment(I16_SIZE_IN_BYTES, currentPtr.getType());
            }
            return LLVMI16Vector.create(vector);
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.I16, I16_SIZE_IN_BYTES);
        }
    }

    public abstract static class LLVMLoadI32VectorNode extends LLVMLoadVectorNode {
        @Specialization
        protected LLVMI32Vector doI32Vector(VirtualFrame frame, LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getI32Vector(globalAccess.executeWithTarget(frame, addr), getSize());
        }

        @Specialization
        protected LLVMI32Vector doI32Vector(LLVMAddress addr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getI32Vector(addr, getSize());
        }

        @Specialization
        protected LLVMI32Vector doForeign(VirtualFrame frame, LLVMTruffleObject addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            int[] vector = new int[getSize()];
            LLVMTruffleObject currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (Integer) foreignReads[i].execute(frame, currentPtr);
                currentPtr = currentPtr.increment(I32_SIZE_IN_BYTES, currentPtr.getType());
            }
            return LLVMI32Vector.create(vector);
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.I32, I32_SIZE_IN_BYTES);
        }
    }

    public abstract static class LLVMLoadI64VectorNode extends LLVMLoadVectorNode {
        @Specialization
        protected LLVMI64Vector doI64Vector(VirtualFrame frame, LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getI64Vector(globalAccess.executeWithTarget(frame, addr), getSize());
        }

        @Specialization
        protected LLVMI64Vector doI64Vector(LLVMAddress addr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getI64Vector(addr, getSize());
        }

        @Specialization
        protected LLVMI64Vector doForeign(VirtualFrame frame, LLVMTruffleObject addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            long[] vector = new long[getSize()];
            LLVMTruffleObject currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (Long) foreignReads[i].execute(frame, currentPtr);
                currentPtr = currentPtr.increment(I64_SIZE_IN_BYTES, currentPtr.getType());
            }
            return LLVMI64Vector.create(vector);
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.I64, I64_SIZE_IN_BYTES);
        }
    }

    public abstract static class LLVMLoadFloatVectorNode extends LLVMLoadVectorNode {
        @Specialization
        protected LLVMFloatVector doFloatVector(VirtualFrame frame, LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getFloatVector(globalAccess.executeWithTarget(frame, addr), getSize());
        }

        @Specialization
        protected LLVMFloatVector doFloatVector(LLVMAddress addr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getFloatVector(addr, getSize());
        }

        @Specialization
        protected LLVMFloatVector doForeign(VirtualFrame frame, LLVMTruffleObject addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            float[] vector = new float[getSize()];
            LLVMTruffleObject currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (Float) foreignReads[i].execute(frame, currentPtr);
                currentPtr = currentPtr.increment(FLOAT_SIZE_IN_BYTES, currentPtr.getType());
            }
            return LLVMFloatVector.create(vector);
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.FLOAT, FLOAT_SIZE_IN_BYTES);
        }
    }

    public abstract static class LLVMLoadDoubleVectorNode extends LLVMLoadVectorNode {
        @Specialization
        protected LLVMDoubleVector doDoubleVector(VirtualFrame frame, LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getDoubleVector(globalAccess.executeWithTarget(frame, addr), getSize());
        }

        @Specialization
        protected LLVMDoubleVector doDoubleVector(LLVMAddress addr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getDoubleVector(addr, getSize());
        }

        @Specialization
        protected LLVMDoubleVector doForeign(VirtualFrame frame, LLVMTruffleObject addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            double[] vector = new double[getSize()];
            LLVMTruffleObject currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (Double) foreignReads[i].execute(frame, currentPtr);
                currentPtr = currentPtr.increment(DOUBLE_SIZE_IN_BYTES, currentPtr.getType());
            }
            return LLVMDoubleVector.create(vector);
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.DOUBLE, DOUBLE_SIZE_IN_BYTES);
        }
    }

    public abstract static class LLVMLoadAddressVectorNode extends LLVMLoadVectorNode {
        @Specialization
        protected LLVMAddressVector doAddressVector(VirtualFrame frame, LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getAddressVector(globalAccess.executeWithTarget(frame, addr), getSize());
        }

        @Specialization
        protected LLVMAddressVector doAddressVector(LLVMAddress addr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getAddressVector(addr, getSize());
        }

        @Specialization
        @SuppressWarnings("unused")
        protected Object doForeign(VirtualFrame frame, LLVMTruffleObject addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            // TODO (chaeubl): this one is more tricky as LLVMTruffleObjects can also be addresses
            throw new IllegalStateException("not yet implemented");
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.POINTER, ADDRESS_SIZE_IN_BYTES);
        }
    }
}
