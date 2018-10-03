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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadPointerVectorNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMRewriteException;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess.LLVMObjectReadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToPointerNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToPointerNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.nodes.factories.LLVMObjectAccessFactory;
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

@NodeField(name = "vectorLength", type = int.class)
public abstract class LLVMLoadVectorNode extends LLVMAbstractLoadNode {
    @Children private volatile LLVMObjectReadNode[] foreignReadNodes;

    public abstract int getVectorLength();

    protected LLVMObjectReadNode[] getForeignReadNodes() {
        if (foreignReadNodes == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                if (foreignReadNodes == null) {
                    LLVMObjectReadNode[] createdNodes = new LLVMObjectReadNode[getVectorLength()];
                    for (int i = 0; i < createdNodes.length; i++) {
                        createdNodes[i] = (LLVMObjectReadNode) insert((Node) LLVMObjectAccessFactory.createRead());
                    }
                    foreignReadNodes = createdNodes;
                }
            }
        }
        return foreignReadNodes;
    }

    public abstract static class LLVMLoadI1VectorNode extends LLVMLoadVectorNode {
        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMI1Vector doI1VectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLLVMMemoryCached();
            boolean[] vector = new boolean[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getI1(currentPtr);
                currentPtr += I1_SIZE_IN_BYTES;
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected LLVMI1Vector doI1VectorDerefHandle(LLVMNativePointer addr) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doForeign(LLVMManagedPointer addr) {
            boolean[] vector = new boolean[getVectorLength()];
            LLVMManagedPointer currentPtr = addr;
            LLVMObjectReadNode[] foreignReads = getForeignReadNodes();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (boolean) foreignReads[i].executeRead(currentPtr.getObject(), currentPtr.getOffset(), ForeignToLLVMType.I1);
                currentPtr = currentPtr.increment(I1_SIZE_IN_BYTES);
            }
            return LLVMI1Vector.create(vector);
        }
    }

    public abstract static class LLVMLoadI8VectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMI8Vector doI8VectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLLVMMemoryCached();
            byte[] vector = new byte[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getI8(currentPtr);
                currentPtr += I8_SIZE_IN_BYTES;
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected LLVMI8Vector doI8VectorDerefHandle(LLVMNativePointer addr) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doForeign(LLVMManagedPointer addr) {
            byte[] vector = new byte[getVectorLength()];
            LLVMManagedPointer currentPtr = addr;
            LLVMObjectReadNode[] foreignReads = getForeignReadNodes();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (byte) foreignReads[i].executeRead(currentPtr.getObject(), currentPtr.getOffset(), ForeignToLLVMType.I8);
                currentPtr = currentPtr.increment(I8_SIZE_IN_BYTES);
            }
            return LLVMI8Vector.create(vector);
        }
    }

    public abstract static class LLVMLoadI16VectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMI16Vector doI16VectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLLVMMemoryCached();
            short[] vector = new short[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getI16(currentPtr);
                currentPtr += I16_SIZE_IN_BYTES;
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected LLVMI16Vector doI16VectorDerefHandle(LLVMNativePointer addr) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doForeign(LLVMManagedPointer addr) {
            short[] vector = new short[getVectorLength()];
            LLVMManagedPointer currentPtr = addr;
            LLVMObjectReadNode[] foreignReads = getForeignReadNodes();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (short) foreignReads[i].executeRead(currentPtr.getObject(), currentPtr.getOffset(), ForeignToLLVMType.I16);
                currentPtr = currentPtr.increment(I16_SIZE_IN_BYTES);
            }
            return LLVMI16Vector.create(vector);
        }
    }

    public abstract static class LLVMLoadI32VectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMI32Vector doI32VectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLLVMMemoryCached();
            int[] vector = new int[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getI32(currentPtr);
                currentPtr += I32_SIZE_IN_BYTES;
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected LLVMI32Vector doI32VectorDerefHandle(LLVMNativePointer addr) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doForeign(LLVMManagedPointer addr) {
            int[] vector = new int[getVectorLength()];
            LLVMManagedPointer currentPtr = addr;
            LLVMObjectReadNode[] foreignReads = getForeignReadNodes();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (int) foreignReads[i].executeRead(currentPtr.getObject(), currentPtr.getOffset(), ForeignToLLVMType.I32);
                currentPtr = currentPtr.increment(I32_SIZE_IN_BYTES);
            }
            return LLVMI32Vector.create(vector);
        }
    }

    public abstract static class LLVMLoadI64VectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMI64Vector doI64VectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLLVMMemoryCached();
            long[] vector = new long[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getI64(currentPtr);
                currentPtr += I64_SIZE_IN_BYTES;
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)", rewriteOn = LLVMRewriteException.class)
        protected LLVMI64Vector doI64VectorDerefHandle(LLVMNativePointer addr) throws LLVMRewriteException {
            return doI64Vector(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected Object doPointerVectorDerefHandle(LLVMNativePointer addr,
                        @Cached("createLoadPointerVector()") LLVMLoadPointerVectorNode load) {
            return doPointerVector(getDerefHandleGetReceiverNode().execute(addr), load);
        }

        @Specialization(rewriteOn = LLVMRewriteException.class)
        @ExplodeLoop
        protected LLVMI64Vector doI64Vector(LLVMManagedPointer addr) throws LLVMRewriteException {
            try {
                long[] vector = new long[getVectorLength()];
                LLVMManagedPointer currentPtr = addr;
                LLVMObjectReadNode[] foreignReads = getForeignReadNodes();
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = LLVMTypesGen.expectLong(foreignReads[i].executeRead(currentPtr.getObject(), currentPtr.getOffset(), ForeignToLLVMType.I64));
                    currentPtr = currentPtr.increment(I64_SIZE_IN_BYTES);
                }
                return LLVMI64Vector.create(vector);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMRewriteException(e);
            }
        }

        @Specialization
        @ExplodeLoop
        protected Object doPointerVector(LLVMManagedPointer addr,
                        @Cached("createLoadPointerVector()") LLVMLoadPointerVectorNode load) {
            return load.executeWithTarget(addr);
        }

        protected LLVMLoadPointerVectorNode createLoadPointerVector() {
            CompilerAsserts.neverPartOfCompilation();
            return LLVMLoadPointerVectorNodeGen.create(null, getVectorLength());
        }
    }

    public abstract static class LLVMLoadPointerVectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMPointerVector doPointerVectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLLVMMemoryCached();
            LLVMPointer[] vector = new LLVMPointer[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getPointer(currentPtr);
                currentPtr += ADDRESS_SIZE_IN_BYTES;
            }
            return LLVMPointerVector.create(vector);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)", rewriteOn = LLVMRewriteException.class)
        protected LLVMPointerVector doPointerVectorDerefHandle(LLVMNativePointer addr) throws LLVMRewriteException {
            return doForeignPointers(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected LLVMPointerVector doMixedVectorDerefHandle(LLVMNativePointer addr,
                        @Cached("createToPointerNodes()") LLVMToPointerNode[] toPointerNodes) {
            return doForeignMixed(getDerefHandleGetReceiverNode().execute(addr), toPointerNodes);
        }

        @Specialization(rewriteOn = LLVMRewriteException.class)
        @ExplodeLoop
        protected LLVMPointerVector doForeignPointers(LLVMManagedPointer addr) throws LLVMRewriteException {
            try {
                LLVMPointer[] vector = new LLVMPointer[getVectorLength()];
                LLVMManagedPointer currentPtr = addr;
                LLVMObjectReadNode[] foreignReads = getForeignReadNodes();
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = LLVMTypesGen.expectLLVMPointer(foreignReads[i].executeRead(currentPtr.getObject(), currentPtr.getOffset(), ForeignToLLVMType.POINTER));
                    currentPtr = currentPtr.increment(ADDRESS_SIZE_IN_BYTES);
                }
                return LLVMPointerVector.create(vector);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMRewriteException(e);
            }
        }

        @Specialization
        @ExplodeLoop
        protected LLVMPointerVector doForeignMixed(LLVMManagedPointer addr,
                        @Cached("createToPointerNodes()") LLVMToPointerNode[] toPointerNodes) {
            LLVMPointer[] vector = new LLVMPointer[getVectorLength()];
            LLVMManagedPointer currentPtr = addr;
            LLVMObjectReadNode[] foreignReads = getForeignReadNodes();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = toPointerNodes[i].executeWithTarget(foreignReads[i].executeRead(currentPtr.getObject(), currentPtr.getOffset(), ForeignToLLVMType.POINTER));
                currentPtr = currentPtr.increment(ADDRESS_SIZE_IN_BYTES);
            }
            return LLVMPointerVector.create(vector);
        }

        protected LLVMToPointerNode[] createToPointerNodes() {
            CompilerAsserts.neverPartOfCompilation();
            LLVMToPointerNode[] result = new LLVMToPointerNode[getVectorLength()];
            for (int i = 0; i < result.length; i++) {
                result[i] = LLVMToPointerNodeGen.create();
            }
            return result;
        }
    }

    public abstract static class LLVMLoadFloatVectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMFloatVector doFloatVectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLLVMMemoryCached();
            float[] vector = new float[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getFloat(currentPtr);
                currentPtr += FLOAT_SIZE_IN_BYTES;
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected LLVMFloatVector doFloatVectorDerefHandle(LLVMNativePointer addr) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doForeign(LLVMManagedPointer addr) {
            float[] vector = new float[getVectorLength()];
            LLVMManagedPointer currentPtr = addr;
            LLVMObjectReadNode[] foreignReads = getForeignReadNodes();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) foreignReads[i].executeRead(currentPtr.getObject(), currentPtr.getOffset(), ForeignToLLVMType.FLOAT);
                currentPtr = currentPtr.increment(FLOAT_SIZE_IN_BYTES);
            }
            return LLVMFloatVector.create(vector);
        }
    }

    public abstract static class LLVMLoadDoubleVectorNode extends LLVMLoadVectorNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMDoubleVector doDoubleVectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLLVMMemoryCached();
            double[] vector = new double[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getDouble(currentPtr);
                currentPtr += DOUBLE_SIZE_IN_BYTES;
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected LLVMDoubleVector doDoubleVector(LLVMNativePointer addr) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doForeign(LLVMManagedPointer addr) {
            double[] vector = new double[getVectorLength()];
            LLVMManagedPointer currentPtr = addr;
            LLVMObjectReadNode[] foreignReads = getForeignReadNodes();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (double) foreignReads[i].executeRead(currentPtr.getObject(), currentPtr.getOffset(), ForeignToLLVMType.DOUBLE);
                currentPtr = currentPtr.increment(DOUBLE_SIZE_IN_BYTES);
            }
            return LLVMDoubleVector.create(vector);
        }
    }
}
