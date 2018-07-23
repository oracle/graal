/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

@NodeField(name = "size", type = int.class)
public abstract class LLVMLoadVectorNode extends LLVMAbstractLoadNode {

    public abstract int getSize();

    LLVMForeignReadNode[] createForeignReads(ForeignToLLVMType type) {
        LLVMForeignReadNode[] result = new LLVMForeignReadNode[getSize()];
        for (int i = 0; i < result.length; i++) {
            result[i] = new LLVMForeignReadNode(type);
        }
        return result;
    }

    @Override
    LLVMForeignReadNode createForeignRead() {
        throw new AssertionError("should not reach here");
    }

    public abstract static class LLVMLoadI1VectorNode extends LLVMLoadVectorNode {
        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVMI1Vector doI1VectorNative(LLVMNativePointer addr) {
            return getLLVMMemoryCached().getI1Vector(addr, getSize());
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected LLVMI1Vector doI1VectorDerefHandle(LLVMNativePointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr), foreignReads);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doForeign(LLVMManagedPointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            boolean[] vector = new boolean[getSize()];
            LLVMManagedPointer currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (Boolean) foreignReads[i].execute(currentPtr);
                currentPtr = currentPtr.increment(I1_SIZE_IN_BYTES);
            }
            return LLVMI1Vector.create(vector);
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.I1);
        }
    }

    public abstract static class LLVMLoadI8VectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVMI8Vector doI8VectorNative(LLVMNativePointer addr) {
            return getLLVMMemoryCached().getI8Vector(addr, getSize());
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected LLVMI8Vector doI8VectorDerefHandle(LLVMNativePointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr), foreignReads);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doForeign(LLVMManagedPointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            byte[] vector = new byte[getSize()];
            LLVMManagedPointer currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (Byte) foreignReads[i].execute(currentPtr);
                currentPtr = currentPtr.increment(I8_SIZE_IN_BYTES);
            }
            return LLVMI8Vector.create(vector);
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.I8);
        }
    }

    public abstract static class LLVMLoadI16VectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVMI16Vector doI16VectorNative(LLVMNativePointer addr) {
            return getLLVMMemoryCached().getI16Vector(addr, getSize());
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected LLVMI16Vector doI16VectorDerefHandle(LLVMNativePointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr), foreignReads);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doForeign(LLVMManagedPointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            short[] vector = new short[getSize()];
            LLVMManagedPointer currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (Short) foreignReads[i].execute(currentPtr);
                currentPtr = currentPtr.increment(I16_SIZE_IN_BYTES);
            }
            return LLVMI16Vector.create(vector);
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.I16);
        }
    }

    public abstract static class LLVMLoadI32VectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVMI32Vector doI32VectorNative(LLVMNativePointer addr) {
            return getLLVMMemoryCached().getI32Vector(addr, getSize());
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected LLVMI32Vector doI32VectorDerefHandle(LLVMNativePointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr), foreignReads);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doForeign(LLVMManagedPointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            int[] vector = new int[getSize()];
            LLVMManagedPointer currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (Integer) foreignReads[i].execute(currentPtr);
                currentPtr = currentPtr.increment(I32_SIZE_IN_BYTES);
            }
            return LLVMI32Vector.create(vector);
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.I32);
        }
    }

    public abstract static class LLVMLoadI64VectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVMI64Vector doI64VectorNative(LLVMNativePointer addr) {
            return getLLVMMemoryCached().getI64Vector(addr, getSize());
        }

        @Specialization(guards = "isAutoDerefHandle(addr)", rewriteOn = UnexpectedResultException.class)
        protected LLVMI64Vector doI64VectorDerefHandle(LLVMNativePointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) throws UnexpectedResultException {
            return doI64Vector(getDerefHandleGetReceiverNode().execute(addr), foreignReads);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        @ExplodeLoop
        protected LLVMI64Vector doI64Vector(LLVMManagedPointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) throws UnexpectedResultException {
            long[] vector = new long[getSize()];
            LLVMManagedPointer currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = LLVMTypesGen.expectLong(foreignReads[i].execute(currentPtr));
                currentPtr = currentPtr.increment(I64_SIZE_IN_BYTES);
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMPointerVector doPointerVector(LLVMManagedPointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            try {
                LLVMPointer[] vector = new LLVMPointer[getSize()];
                LLVMManagedPointer currentPtr = addr;
                for (int i = 0; i < vector.length; i++) {
                    // TEMP (chaeubl): this is not really correct yet - the read can return pretty
                    // much any object (long, pointer,...)
                    vector[i] = LLVMTypesGen.expectLLVMPointer(foreignReads[i].execute(currentPtr));
                    currentPtr = currentPtr.increment(I64_SIZE_IN_BYTES);
                }
                return LLVMPointerVector.create(vector);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.I64);
        }
    }

    public abstract static class LLVMLoadFloatVectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVMFloatVector doFloatVectorNative(LLVMNativePointer addr) {
            return getLLVMMemoryCached().getFloatVector(addr, getSize());
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected LLVMFloatVector doFloatVectorDerefHandle(LLVMNativePointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr), foreignReads);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doForeign(LLVMManagedPointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            float[] vector = new float[getSize()];
            LLVMManagedPointer currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (Float) foreignReads[i].execute(currentPtr);
                currentPtr = currentPtr.increment(FLOAT_SIZE_IN_BYTES);
            }
            return LLVMFloatVector.create(vector);
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.FLOAT);
        }
    }

    public abstract static class LLVMLoadDoubleVectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVMDoubleVector doDoubleVectorNative(LLVMNativePointer addr) {
            return getLLVMMemoryCached().getDoubleVector(addr, getSize());
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected LLVMDoubleVector doDoubleVector(LLVMNativePointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr), foreignReads);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doForeign(LLVMManagedPointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            double[] vector = new double[getSize()];
            LLVMManagedPointer currentPtr = addr;
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (Double) foreignReads[i].execute(currentPtr);
                currentPtr = currentPtr.increment(DOUBLE_SIZE_IN_BYTES);
            }
            return LLVMDoubleVector.create(vector);
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.DOUBLE);
        }
    }

    public abstract static class LLVMLoadPointerVectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVMPointerVector doPointerVectorNative(LLVMNativePointer addr) {
            return getLLVMMemoryCached().getPointerVector(addr, getSize());
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected Object doPointerVectorDerefHandle(LLVMNativePointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr), foreignReads);
        }

        @Specialization
        @ExplodeLoop
        @SuppressWarnings("unused")
        protected Object doForeign(LLVMManagedPointer addr,
                        @Cached("createForeignReads()") LLVMForeignReadNode[] foreignReads) {
            // TODO (chaeubl): this one is more tricky as LLVMTruffleObjects can also be addresses
            throw new IllegalStateException("not yet implemented");
        }

        protected LLVMForeignReadNode[] createForeignReads() {
            return createForeignReads(ForeignToLLVMType.POINTER);
        }
    }
}
