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
package com.oracle.truffle.llvm.runtime.nodes.memory.load;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadDoubleVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadFloatVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI16VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI1VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI32VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI64VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI8VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadPointerVectorNodeGen;
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
import com.oracle.truffle.llvm.runtime.vector.LLVMVector;

@NodeField(name = "vectorLength", type = int.class)
public abstract class LLVMLoadVectorNode extends LLVMLoadNode {
    protected final boolean isRecursive;

    protected LLVMLoadVectorNode() {
        this(false);
    }

    protected LLVMLoadVectorNode(boolean isRecursive) {
        this.isRecursive = isRecursive;
    }

    public abstract int getVectorLength();

    public abstract static class LLVMLoadI1VectorNode extends LLVMLoadVectorNode {
        protected LLVMLoadI1VectorNode() {
        }

        protected LLVMLoadI1VectorNode(boolean isRecursive) {
            super(isRecursive);
        }

        LLVMLoadI1VectorNode createRecursive() {
            return LLVMLoadI1VectorNodeGen.create(true, null, getVectorLength());
        }

        public abstract LLVMI1Vector executeWithTarget(LLVMManagedPointer managed);

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMI1Vector doI1VectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            boolean[] vector = new boolean[getVectorLength()];
            long basePtr = addr.asNative();
            for (int byteOffset = 0; byteOffset < (vector.length / 8) + 1; byteOffset++) {
                int b = memory.getI8(this, basePtr + byteOffset);
                for (int bitOffset = 0; bitOffset < 8 && ((byteOffset * 8) + bitOffset) < vector.length; bitOffset++) {
                    int mask = (1 << bitOffset) & 0xFF;
                    vector[(byteOffset * 8) + bitOffset] = ((b & mask) >> bitOffset) == 1;
                }
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
        protected LLVMI1Vector doI1VectorDerefHandle(LLVMNativePointer addr,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @Cached("createRecursive()") LLVMLoadI1VectorNode load) {
            return load.executeWithTarget(getReceiver.execute(addr));
        }

        @Specialization(limit = "3")
        @ExplodeLoop
        @GenerateAOT.Exclude
        protected LLVMI1Vector doForeign(LLVMManagedPointer addr,
                        @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) {
            boolean[] vector = new boolean[getVectorLength()];
            long curOffset = addr.getOffset();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = nativeRead.readI8(addr.getObject(), curOffset) != 0;
                curOffset += I1_SIZE_IN_BYTES;
            }
            return LLVMI1Vector.create(vector);
        }
    }

    public abstract static class LLVMLoadI8VectorNode extends LLVMLoadVectorNode {

        protected LLVMLoadI8VectorNode() {
        }

        protected LLVMLoadI8VectorNode(boolean isRecursive) {
            super(isRecursive);
        }

        LLVMLoadI8VectorNode createRecursive() {
            return LLVMLoadI8VectorNodeGen.create(true, null, getVectorLength());
        }

        public abstract LLVMI8Vector executeWithTarget(LLVMManagedPointer managed);

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMI8Vector doI8VectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            byte[] vector = new byte[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getI8(this, currentPtr);
                currentPtr += I8_SIZE_IN_BYTES;
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
        protected LLVMI8Vector doI8VectorDerefHandle(LLVMNativePointer addr,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @Cached("createRecursive()") LLVMLoadI8VectorNode load) {
            return load.executeWithTarget(getReceiver.execute(addr));
        }

        @Specialization(limit = "3")
        @ExplodeLoop
        @GenerateAOT.Exclude
        protected LLVMI8Vector doForeign(LLVMManagedPointer addr,
                        @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) {
            byte[] vector = new byte[getVectorLength()];
            long curOffset = addr.getOffset();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = nativeRead.readI8(addr.getObject(), curOffset);
                curOffset += I8_SIZE_IN_BYTES;
            }
            return LLVMI8Vector.create(vector);
        }
    }

    public abstract static class LLVMLoadI16VectorNode extends LLVMLoadVectorNode {

        protected LLVMLoadI16VectorNode() {
        }

        protected LLVMLoadI16VectorNode(boolean isRecursive) {
            super(isRecursive);
        }

        LLVMLoadI16VectorNode createRecursive() {
            return LLVMLoadI16VectorNodeGen.create(true, null, getVectorLength());
        }

        public abstract LLVMI16Vector executeWithTarget(LLVMManagedPointer managed);

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMI16Vector doI16VectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            short[] vector = new short[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getI16(this, currentPtr);
                currentPtr += I16_SIZE_IN_BYTES;
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
        protected LLVMI16Vector doI16VectorDerefHandle(LLVMNativePointer addr,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @Cached("createRecursive()") LLVMLoadI16VectorNode load) {
            return load.executeWithTarget(getReceiver.execute(addr));
        }

        @Specialization(limit = "3")
        @ExplodeLoop
        @GenerateAOT.Exclude
        protected LLVMI16Vector doForeign(LLVMManagedPointer addr,
                        @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) {
            short[] vector = new short[getVectorLength()];
            long curOffset = addr.getOffset();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = nativeRead.readI16(addr.getObject(), curOffset);
                curOffset += I16_SIZE_IN_BYTES;
            }
            return LLVMI16Vector.create(vector);
        }
    }

    public abstract static class LLVMLoadI32VectorNode extends LLVMLoadVectorNode {

        protected LLVMLoadI32VectorNode() {
        }

        protected LLVMLoadI32VectorNode(boolean isRecursive) {
            super(isRecursive);
        }

        LLVMLoadI32VectorNode createRecursive() {
            return LLVMLoadI32VectorNodeGen.create(true, null, getVectorLength());
        }

        public abstract LLVMI32Vector executeWithTarget(LLVMManagedPointer managed);

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMI32Vector doI32VectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            int[] vector = new int[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getI32(this, currentPtr);
                currentPtr += I32_SIZE_IN_BYTES;
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
        protected LLVMI32Vector doI32VectorDerefHandle(LLVMNativePointer addr,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @Cached("createRecursive()") LLVMLoadI32VectorNode load) {
            return load.executeWithTarget(getReceiver.execute(addr));
        }

        @Specialization(limit = "3")
        @ExplodeLoop
        @GenerateAOT.Exclude
        protected LLVMI32Vector doForeign(LLVMManagedPointer addr,
                        @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) {
            int[] vector = new int[getVectorLength()];
            long curOffset = addr.getOffset();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = nativeRead.readI32(addr.getObject(), curOffset);
                curOffset += I32_SIZE_IN_BYTES;
            }
            return LLVMI32Vector.create(vector);
        }
    }

    public abstract static class LLVMLoadI64VectorNode extends LLVMLoadVectorNode {

        protected LLVMLoadI64VectorNode() {
        }

        protected LLVMLoadI64VectorNode(boolean isRecursive) {
            super(isRecursive);
        }

        LLVMLoadI64VectorNode createRecursive() {
            return LLVMLoadI64VectorNodeGen.create(true, null, getVectorLength());
        }

        public abstract LLVMVector executeWithTarget(LLVMManagedPointer managed);

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMI64Vector doI64VectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            long[] vector = new long[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getI64(this, currentPtr);
                currentPtr += I64_SIZE_IN_BYTES;
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
        protected LLVMVector doI64VectorDerefHandle(LLVMNativePointer addr,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @Cached("createRecursive()") LLVMLoadI64VectorNode load) {
            return load.executeWithTarget(getReceiver.execute(addr));
        }

        @Specialization(limit = "3", rewriteOn = UnexpectedResultException.class)
        @ExplodeLoop
        protected LLVMI64Vector doI64Vector(LLVMManagedPointer addr,
                        @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) throws UnexpectedResultException {
            long[] vector = new long[getVectorLength()];
            long curOffset = addr.getOffset();
            int i = 0;
            try {
                for (i = 0; i < vector.length; i++) {
                    vector[i] = nativeRead.readI64(addr.getObject(), curOffset);
                    curOffset += I64_SIZE_IN_BYTES;
                }
                return LLVMI64Vector.create(vector);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                LLVMPointer[] ptrVector = new LLVMPointer[getVectorLength()];
                for (int j = 0; j < i; j++) {
                    ptrVector[j] = LLVMNativePointer.create(vector[j]);
                }
                ptrVector[i] = LLVMTypesGen.asPointer(e.getResult());

                i++;
                curOffset += I64_SIZE_IN_BYTES;

                for (; i < ptrVector.length; i++) {
                    Object obj = nativeRead.readGenericI64(addr.getObject(), curOffset);
                    curOffset += I64_SIZE_IN_BYTES;

                    if (obj instanceof Long) {
                        ptrVector[i] = LLVMNativePointer.create((long) obj);
                    } else {
                        ptrVector[i] = LLVMPointer.cast(obj);
                    }
                }
                throw new UnexpectedResultException(LLVMPointerVector.create(ptrVector));
            }
        }

        @Specialization(replaces = "doI64Vector")
        @ExplodeLoop
        protected LLVMPointerVector doPointerVector(LLVMManagedPointer addr,
                        @Cached("create(getVectorLength())") LLVMLoadPointerVectorNode load) {
            return load.executeWithTarget(addr);
        }
    }

    public abstract static class LLVMLoadPointerVectorNode extends LLVMLoadVectorNode {

        protected LLVMLoadPointerVectorNode() {
        }

        protected LLVMLoadPointerVectorNode(boolean isRecursive) {
            super(isRecursive);
        }

        static LLVMLoadPointerVectorNode create(int length) {
            return LLVMLoadPointerVectorNodeGen.create(false, null, length);
        }

        static LLVMLoadPointerVectorNode createRecursive(int length) {
            return LLVMLoadPointerVectorNodeGen.create(true, null, length);
        }

        public abstract LLVMPointerVector executeWithTarget(Object address);

        @Specialization(guards = {"!isRecursive", "!isAutoDerefHandle(addr)"})
        @ExplodeLoop
        protected LLVMPointerVector doPointerVectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            LLVMPointer[] vector = new LLVMPointer[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getPointer(this, currentPtr);
                currentPtr += ADDRESS_SIZE_IN_BYTES;
            }
            return LLVMPointerVector.create(vector);
        }

        @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
        protected LLVMPointerVector doPointerVectorDerefHandle(LLVMNativePointer addr,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @Cached("createRecursive(getVectorLength())") LLVMLoadPointerVectorNode load) {
            return load.executeWithTarget(getReceiver.execute(addr));
        }

        @Specialization(limit = "3")
        @ExplodeLoop
        @GenerateAOT.Exclude
        protected LLVMPointerVector doForeignPointers(LLVMManagedPointer addr,
                        @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) {
            LLVMPointer[] vector = new LLVMPointer[getVectorLength()];
            long curOffset = addr.getOffset();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = nativeRead.readPointer(addr.getObject(), curOffset);
                curOffset += ADDRESS_SIZE_IN_BYTES;
            }
            return LLVMPointerVector.create(vector);
        }
    }

    public abstract static class LLVMLoadFloatVectorNode extends LLVMLoadVectorNode {

        protected LLVMLoadFloatVectorNode() {
        }

        protected LLVMLoadFloatVectorNode(boolean isRecursive) {
            super(isRecursive);
        }

        LLVMLoadFloatVectorNode createRecursive() {
            return LLVMLoadFloatVectorNodeGen.create(true, null, getVectorLength());
        }

        public abstract LLVMFloatVector executeWithTarget(LLVMManagedPointer managed);

        @Specialization(guards = {"!isAutoDerefHandle(addr)"})
        @ExplodeLoop
        protected LLVMFloatVector doFloatVectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            float[] vector = new float[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getFloat(this, currentPtr);
                currentPtr += FLOAT_SIZE_IN_BYTES;
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
        protected LLVMFloatVector doFloatVectorDerefHandle(LLVMNativePointer addr,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @Cached("createRecursive()") LLVMLoadFloatVectorNode load) {
            return load.executeWithTarget(getReceiver.execute(addr));
        }

        @Specialization(limit = "3")
        @ExplodeLoop
        @GenerateAOT.Exclude
        protected LLVMFloatVector doForeign(LLVMManagedPointer addr,
                        @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) {
            float[] vector = new float[getVectorLength()];
            long curOffset = addr.getOffset();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = nativeRead.readFloat(addr.getObject(), curOffset);
                curOffset += FLOAT_SIZE_IN_BYTES;
            }
            return LLVMFloatVector.create(vector);
        }
    }

    public abstract static class LLVMLoadDoubleVectorNode extends LLVMLoadVectorNode {

        protected LLVMLoadDoubleVectorNode() {
        }

        protected LLVMLoadDoubleVectorNode(boolean isRecursive) {
            super(isRecursive);
        }

        LLVMLoadDoubleVectorNode createRecursive() {
            return LLVMLoadDoubleVectorNodeGen.create(true, null, getVectorLength());
        }

        public abstract LLVMDoubleVector executeWithTarget(LLVMManagedPointer managed);

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        @ExplodeLoop
        protected LLVMDoubleVector doDoubleVectorNative(LLVMNativePointer addr) {
            LLVMMemory memory = getLanguage().getLLVMMemory();
            double[] vector = new double[getVectorLength()];
            long currentPtr = addr.asNative();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = memory.getDouble(this, currentPtr);
                currentPtr += DOUBLE_SIZE_IN_BYTES;
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
        protected LLVMDoubleVector doDoubleVector(LLVMNativePointer addr,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,

                        @Cached("createRecursive()") LLVMLoadDoubleVectorNode load) {
            return load.executeWithTarget(getReceiver.execute(addr));
        }

        @Specialization(limit = "3")
        @ExplodeLoop
        @GenerateAOT.Exclude
        protected LLVMDoubleVector doForeign(LLVMManagedPointer addr,
                        @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) {
            double[] vector = new double[getVectorLength()];
            long curOffset = addr.getOffset();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = nativeRead.readDouble(addr.getObject(), curOffset);
                curOffset += DOUBLE_SIZE_IN_BYTES;
            }
            return LLVMDoubleVector.create(vector);
        }
    }
}
