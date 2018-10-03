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
package com.oracle.truffle.llvm.nodes.memory.store;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess.LLVMObjectWriteNode;
import com.oracle.truffle.llvm.runtime.nodes.factories.LLVMObjectAccessFactory;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

@NodeField(name = "vectorLength", type = int.class)
public abstract class LLVMStoreVectorNode extends LLVMStoreNodeCommon {
    @Children private volatile LLVMObjectWriteNode[] foreignWriteNodes;

    public abstract int getVectorLength();

    protected LLVMObjectWriteNode[] getForeignWriteNodes() {
        if (foreignWriteNodes == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                if (foreignWriteNodes == null) {
                    LLVMObjectWriteNode[] createdNodes = new LLVMObjectWriteNode[getVectorLength()];
                    for (int i = 0; i < createdNodes.length; i++) {
                        createdNodes[i] = (LLVMObjectWriteNode) insert((Node) LLVMObjectAccessFactory.createWrite());
                    }
                    foreignWriteNodes = createdNodes;
                }
            }
        }
        return foreignWriteNodes;
    }

    public LLVMStoreVectorNode(LLVMSourceLocation source) {
        super(source);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMDoubleVector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putDouble(currentPtr, vector.getValue(i));
            currentPtr += DOUBLE_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMDoubleVector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMFloatVector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putFloat(currentPtr, vector.getValue(i));
            currentPtr += FLOAT_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMFloatVector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI16Vector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putI16(currentPtr, vector.getValue(i));
            currentPtr += I16_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMI16Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI1Vector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putI1(currentPtr, vector.getValue(i));
            currentPtr += I1_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMI1Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI32Vector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putI32(currentPtr, vector.getValue(i));
            currentPtr += I32_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMI32Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI64Vector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putI64(currentPtr, vector.getValue(i));
            currentPtr += I64_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMI64Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMI8Vector vector) {
        assert vector.getLength() == getVectorLength();
        LLVMMemory memory = getLLVMMemoryCached();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            memory.putI8(currentPtr, vector.getValue(i));
            currentPtr += I8_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMI8Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    @ExplodeLoop
    protected void writeVector(LLVMNativePointer address, LLVMPointerVector value,
                    @Cached("createPointerStore()") LLVMPointerStoreNode write) {
        assert value.getLength() == getVectorLength();
        long currentPtr = address.asNative();
        for (int i = 0; i < getVectorLength(); i++) {
            write.executeWithTarget(currentPtr, value.getValue(i));
            currentPtr += ADDRESS_SIZE_IN_BYTES;
        }
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected void writeVectorDerefHandle(LLVMNativePointer address, LLVMPointerVector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI1Vector value) {
        assert value.getLength() == getVectorLength();
        LLVMManagedPointer currentPtr = address;
        LLVMObjectWriteNode[] writes = getForeignWriteNodes();
        for (int i = 0; i < getVectorLength(); i++) {
            writes[i].executeWrite(currentPtr.getObject(), currentPtr.getOffset(), value.getValue(i), ForeignToLLVMType.I1);
            currentPtr = currentPtr.increment(I1_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI8Vector value) {
        assert value.getLength() == getVectorLength();
        LLVMManagedPointer currentPtr = address;
        LLVMObjectWriteNode[] writes = getForeignWriteNodes();
        for (int i = 0; i < getVectorLength(); i++) {
            writes[i].executeWrite(currentPtr.getObject(), currentPtr.getOffset(), value.getValue(i), ForeignToLLVMType.I8);
            currentPtr = currentPtr.increment(I8_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI16Vector value) {
        assert value.getLength() == getVectorLength();
        LLVMManagedPointer currentPtr = address;
        LLVMObjectWriteNode[] writes = getForeignWriteNodes();
        for (int i = 0; i < getVectorLength(); i++) {
            writes[i].executeWrite(currentPtr.getObject(), currentPtr.getOffset(), value.getValue(i), ForeignToLLVMType.I16);
            currentPtr = currentPtr.increment(I16_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI32Vector value) {
        assert value.getLength() == getVectorLength();
        LLVMManagedPointer currentPtr = address;
        LLVMObjectWriteNode[] writes = getForeignWriteNodes();
        for (int i = 0; i < getVectorLength(); i++) {
            writes[i].executeWrite(currentPtr.getObject(), currentPtr.getOffset(), value.getValue(i), ForeignToLLVMType.I32);
            currentPtr = currentPtr.increment(I32_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMFloatVector value) {
        assert value.getLength() == getVectorLength();
        LLVMManagedPointer currentPtr = address;
        LLVMObjectWriteNode[] writes = getForeignWriteNodes();
        for (int i = 0; i < getVectorLength(); i++) {
            writes[i].executeWrite(currentPtr.getObject(), currentPtr.getOffset(), value.getValue(i), ForeignToLLVMType.FLOAT);
            currentPtr = currentPtr.increment(FLOAT_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMDoubleVector value) {
        assert value.getLength() == getVectorLength();
        LLVMManagedPointer currentPtr = address;
        LLVMObjectWriteNode[] writes = getForeignWriteNodes();
        for (int i = 0; i < getVectorLength(); i++) {
            writes[i].executeWrite(currentPtr.getObject(), currentPtr.getOffset(), value.getValue(i), ForeignToLLVMType.DOUBLE);
            currentPtr = currentPtr.increment(DOUBLE_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMI64Vector value) {
        assert value.getLength() == getVectorLength();
        LLVMManagedPointer currentPtr = address;
        LLVMObjectWriteNode[] writes = getForeignWriteNodes();
        for (int i = 0; i < getVectorLength(); i++) {
            writes[i].executeWrite(currentPtr.getObject(), currentPtr.getOffset(), value.getValue(i), ForeignToLLVMType.I64);
            currentPtr = currentPtr.increment(I64_SIZE_IN_BYTES);
        }
    }

    @Specialization
    @ExplodeLoop
    protected void writeVector(LLVMManagedPointer address, LLVMPointerVector value) {
        assert value.getLength() == getVectorLength();
        LLVMManagedPointer currentPtr = address;
        LLVMObjectWriteNode[] writes = getForeignWriteNodes();
        for (int i = 0; i < getVectorLength(); i++) {
            writes[i].executeWrite(currentPtr.getObject(), currentPtr.getOffset(), value.getValue(i), ForeignToLLVMType.POINTER);
            currentPtr = currentPtr.increment(ADDRESS_SIZE_IN_BYTES);
        }
    }

    protected static LLVMPointerStoreNode createPointerStore() {
        return LLVMPointerStoreNodeGen.create(null, null);
    }
}
