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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.vector.LLVMAddressVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFunctionVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMStoreVectorNode extends LLVMStoreNodeCommon {

    private final int vectorLength;

    public LLVMStoreVectorNode(int vectorLength) {
        super(null);
        this.vectorLength = vectorLength;
    }

    public LLVMStoreVectorNode(LLVMSourceLocation sourceLocation, VectorType type) {
        super(sourceLocation);
        this.vectorLength = type.getNumberOfElements();
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    protected Object writeVector(LLVMAddress address, LLVMDoubleVector value) {
        getLLVMMemoryCached().putVector(address, value, vectorLength);
        return null;
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected Object writeVectorDerefHandle(LLVMAddress address, LLVMDoubleVector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
        return null;
    }

    @Specialization
    protected Object writeVector(LLVMGlobal address, LLVMDoubleVector value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        getLLVMMemoryCached().putVector(globalAccess.executeWithTarget(address), value, vectorLength);
        return null;
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    protected Object writeVector(LLVMAddress address, LLVMFloatVector value) {
        getLLVMMemoryCached().putVector(address, value, vectorLength);
        return null;
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected Object writeVectorDerefHandle(LLVMAddress address, LLVMFloatVector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
        return null;
    }

    @Specialization
    protected Object writeVector(LLVMGlobal address, LLVMFloatVector value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        getLLVMMemoryCached().putVector(globalAccess.executeWithTarget(address), value, vectorLength);
        return null;
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    protected Object writeVector(LLVMAddress address, LLVMI16Vector value) {
        getLLVMMemoryCached().putVector(address, value, vectorLength);
        return null;
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected Object writeVectorDerefHandle(LLVMAddress address, LLVMI16Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
        return null;
    }

    @Specialization
    protected Object writeVector(LLVMGlobal address, LLVMI16Vector value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        getLLVMMemoryCached().putVector(globalAccess.executeWithTarget(address), value, vectorLength);
        return null;
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    protected Object writeVector(LLVMAddress address, LLVMI1Vector value) {
        getLLVMMemoryCached().putVector(address, value, vectorLength);
        return null;
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected Object writeVectorDerefHandle(LLVMAddress address, LLVMI1Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
        return null;
    }

    @Specialization
    protected Object writeVector(LLVMGlobal address, LLVMI1Vector value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        getLLVMMemoryCached().putVector(globalAccess.executeWithTarget(address), value, vectorLength);
        return null;
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    protected Object writeVector(LLVMAddress address, LLVMI32Vector value) {
        getLLVMMemoryCached().putVector(address, value, vectorLength);
        return null;
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected Object writeVectorDerefHandle(LLVMAddress address, LLVMI32Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
        return null;
    }

    @Specialization
    protected Object writeVector(LLVMGlobal address, LLVMI32Vector value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        getLLVMMemoryCached().putVector(globalAccess.executeWithTarget(address), value, vectorLength);
        return null;
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    protected Object writeVector(LLVMAddress address, LLVMI64Vector value) {
        getLLVMMemoryCached().putVector(address, value, vectorLength);
        return null;
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected Object writeVectorDerefHandle(LLVMAddress address, LLVMI64Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
        return null;
    }

    @Specialization
    protected Object writeVector(LLVMGlobal address, LLVMI64Vector value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        getLLVMMemoryCached().putVector(globalAccess.executeWithTarget(address), value, vectorLength);
        return null;
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    protected Object writeVector(LLVMAddress address, LLVMI8Vector value) {
        getLLVMMemoryCached().putVector(address, value, vectorLength);
        return null;
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected Object writeVectorDerefHandle(LLVMAddress address, LLVMI8Vector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
        return null;
    }

    @Specialization
    protected Object writeVector(LLVMGlobal address, LLVMI8Vector value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        getLLVMMemoryCached().putVector(globalAccess.executeWithTarget(address), value, vectorLength);
        return null;
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    protected Object writeVector(LLVMAddress address, LLVMAddressVector value) {
        getLLVMMemoryCached().putVector(address, value, vectorLength);
        return null;
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected Object writeVectorDerefHandle(LLVMAddress address, LLVMAddressVector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
        return null;
    }

    @Specialization
    protected Object writeVector(LLVMGlobal address, LLVMAddressVector value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        getLLVMMemoryCached().putVector(globalAccess.executeWithTarget(address), value, vectorLength);
        return null;
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    protected Object writeVector(LLVMAddress address, LLVMFunctionVector value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        getLLVMMemoryCached().putVector(address, value, vectorLength, globalAccess);
        return null;
    }

    @Specialization(guards = "isAutoDerefHandle(address)")
    protected Object writeVectorDerefHandle(LLVMAddress address, LLVMFunctionVector value) {
        writeVector(getDerefHandleGetReceiverNode().execute(address), value);
        return null;
    }

    @Specialization
    protected Object writeVector(LLVMGlobal address, LLVMFunctionVector value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        getLLVMMemoryCached().putVector(globalAccess.executeWithTarget(address), value, vectorLength, globalAccess);
        return null;
    }

    @Specialization(guards = "address.isNative()")
    protected Object doOp(LLVMTruffleObject address, Object value,
                    @Cached("createRecursive()") LLVMStoreVectorNode recursive) {
        return recursive.executeWithTarget(address.asNative(), value);
    }

    LLVMStoreVectorNode createRecursive() {
        return LLVMStoreVectorNodeGen.create(vectorLength, null, null);
    }

    @Specialization(guards = "address.isManaged()")
    @ExplodeLoop
    protected Object writeVector(LLVMTruffleObject address, LLVMI1Vector value) {
        assert value.getLength() == vectorLength;
        LLVMTruffleObject currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode().execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(I1_SIZE_IN_BYTES);
        }
        return null;
    }

    @Specialization(guards = "address.isManaged()")
    @ExplodeLoop
    protected Object writeVector(LLVMTruffleObject address, LLVMI8Vector value) {
        assert value.getLength() == vectorLength;
        LLVMTruffleObject currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode().execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(I8_SIZE_IN_BYTES);
        }
        return null;
    }

    @Specialization(guards = "address.isManaged()")
    @ExplodeLoop
    protected Object writeVector(LLVMTruffleObject address, LLVMI16Vector value) {
        assert value.getLength() == vectorLength;
        LLVMTruffleObject currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode().execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(I16_SIZE_IN_BYTES);
        }
        return null;
    }

    @Specialization(guards = "address.isManaged()")
    @ExplodeLoop
    protected Object writeVector(LLVMTruffleObject address, LLVMI32Vector value) {
        assert value.getLength() == vectorLength;
        LLVMTruffleObject currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode().execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(I32_SIZE_IN_BYTES);
        }
        return null;
    }

    @Specialization(guards = "address.isManaged()")
    @ExplodeLoop
    protected Object writeVector(LLVMTruffleObject address, LLVMFloatVector value) {
        assert value.getLength() == vectorLength;
        LLVMTruffleObject currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode().execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(FLOAT_SIZE_IN_BYTES);
        }
        return null;
    }

    @Specialization(guards = "address.isManaged()")
    @ExplodeLoop
    protected Object writeVector(LLVMTruffleObject address, LLVMDoubleVector value) {
        assert value.getLength() == vectorLength;
        LLVMTruffleObject currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode().execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(DOUBLE_SIZE_IN_BYTES);
        }
        return null;
    }

    @Specialization(guards = "address.isManaged()")
    @ExplodeLoop
    protected Object writeVector(LLVMTruffleObject address, LLVMI64Vector value) {
        assert value.getLength() == vectorLength;
        LLVMTruffleObject currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode().execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(I64_SIZE_IN_BYTES);
        }
        return null;
    }

    @Specialization(guards = "address.isManaged()")
    @ExplodeLoop
    protected Object writeVector(LLVMTruffleObject address, LLVMAddressVector value) {
        assert value.getLength() == vectorLength;
        LLVMTruffleObject currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode().execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(ADDRESS_SIZE_IN_BYTES);
        }
        return null;
    }

    @Specialization(guards = "address.isManaged()")
    @ExplodeLoop
    protected Object writeVector(LLVMTruffleObject address, LLVMFunctionVector value) {
        assert value.getLength() == vectorLength;
        LLVMTruffleObject currentPtr = address;
        for (int i = 0; i < vectorLength; i++) {
            getForeignWriteNode().execute(currentPtr, value.getValue(i));
            currentPtr = currentPtr.increment(I64_SIZE_IN_BYTES);
        }
        return null;
    }
}
