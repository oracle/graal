/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleManagedMalloc.ManagedMallocObject;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemSetNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class NativeMemSetNode extends LLVMMemSetNode {

    protected static final long MAX_JAVA_LEN = 256;

    @CompilationFinal private boolean inJava = true;

    @Specialization
    protected void memset(LLVMNativePointer address, byte value, long length,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        if (inJava) {
            if (length <= MAX_JAVA_LEN) {
                long current = address.asNative();
                long i64ValuesToWrite = length >> 3;
                if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i64ValuesToWrite > 0)) {
                    long v16 = ((long) value) << 8 | ((long) value & 0xFF);
                    long v32 = v16 << 16 | v16;
                    long v64 = v32 << 32 | v32;

                    for (long i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i64ValuesToWrite); i++) {
                        memory.putI64(current, v64);
                        current += 8;
                    }
                }

                long i8ValuesToWrite = length & 0x07;
                for (long i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i8ValuesToWrite); i++) {
                    memory.putI8(current, value);
                    current++;
                }
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                inJava = false;
            }
        }

        nativeMemSet(memory, address, value, length);
    }

    @SuppressWarnings("deprecation")
    private static void nativeMemSet(LLVMMemory memory, LLVMNativePointer address, byte value, long length) {
        memory.memset(address, length, value);
    }

    @Specialization(guards = {"!isManagedMallocObject(object)"})
    protected void memset(LLVMManagedPointer object, byte value, long length,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        memset(globalAccess.executeWithTarget(object), value, length, memory);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isManagedMallocObject(object)", "value == 0"})
    protected void memset(LLVMManagedPointer object, byte value, long length) {
        assert length % ADDRESS_SIZE_IN_BYTES == 0;

        final ManagedMallocObject obj = (ManagedMallocObject) object.getObject();
        int arrayOffset = (int) (object.getOffset() / ADDRESS_SIZE_IN_BYTES);
        for (int i = 0; i < length / ADDRESS_SIZE_IN_BYTES; i++) {
            obj.set(arrayOffset + i, LLVMNativePointer.createNull());
        }
    }

    protected boolean isManagedMallocObject(LLVMManagedPointer object) {
        return object.getObject() instanceof ManagedMallocObject;
    }
}
