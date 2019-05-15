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
package com.oracle.truffle.llvm.runtime.global;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@ExportLibrary(InteropLibrary.class)
public final class LLVMGlobalContainer implements LLVMObjectAccess, LLVMInternalTruffleObject {

    private long address;
    private Object contents;

    public LLVMGlobalContainer() {
        contents = 0L;
    }

    public Object get() {
        return contents;
    }

    public void set(Object value) {
        contents = value;
    }

    @ExportMessage
    public boolean isPointer() {
        return address != 0;
    }

    @ExportMessage
    public long asPointer() throws UnsupportedMessageException {
        if (isPointer()) {
            return address;
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    public long getAddress() {
        return address;
    }

    @SuppressWarnings("static-method")
    public int getSize() {
        return 1;
    }

    @TruffleBoundary
    @ExportMessage
    public void toNative(@Cached LLVMToNativeNode toNative) {
        if (address == 0) {
            LLVMMemory memory = LLVMLanguage.getLanguage().getCapability(LLVMMemory.class);
            LLVMNativePointer pointer = memory.allocateMemory(8);
            address = pointer.asNative();
            long value;
            if (contents instanceof Number) {
                value = ((Number) contents).longValue();
            } else {
                value = toNative.executeWithTarget(contents).asNative();
            }
            memory.putI64(pointer, value);
        }
    }

    @Override
    public LLVMObjectReadNode createReadNode() {
        return getNodeFactory().createGlobalContainerReadNode();
    }

    @Override
    public LLVMObjectWriteNode createWriteNode() {
        return getNodeFactory().createGlobalContainerWriteNode();
    }

    public void dispose() {
        if (address != 0) {
            LLVMMemory memory = LLVMLanguage.getLanguage().getCapability(LLVMMemory.class);
            memory.free(address);
            address = 0;
        }
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return String.format("LLVMGlobalContainer (address = 0x%x, contents = %s)", address, contents);
    }

    private static NodeFactory getNodeFactory() {
        return LLVMLanguage.getLanguage().getNodeFactory();
    }
}
