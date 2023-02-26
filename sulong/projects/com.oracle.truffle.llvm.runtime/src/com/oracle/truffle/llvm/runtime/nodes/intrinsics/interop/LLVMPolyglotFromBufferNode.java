/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Buffer;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(value = "buffer", type = LLVMExpressionNode.class)
@NodeChild(value = "length", type = LLVMExpressionNode.class)
public abstract class LLVMPolyglotFromBufferNode extends LLVMExpressionNode {
    protected final boolean isWritable;

    protected LLVMPolyglotFromBufferNode(boolean isWritable) {
        this.isWritable = isWritable;
    }

    @Specialization
    public LLVMPointer doAsBuffer(LLVMNativePointer pointer, long length) {
        return pointer.export(new Buffer(isWritable, length));
    }

    @Specialization(guards = "foreignsLib.isForeign(pointer)", limit = "3")
    @GenerateAOT.Exclude
    public Object doManagedPointer(LLVMManagedPointer pointer, long length,
                    @Cached LLVMAsForeignNode foreign,
                    @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib,
                    @CachedLibrary(limit = "3") InteropLibrary interop,
                    @Cached BranchProfile exception1,
                    @Cached BranchProfile exception2,
                    @Cached BranchProfile exception3) {

        Object buffer = foreign.execute(pointer);
        try {
            if (!interop.hasBufferElements(buffer)) {
                return unsupported(pointer, length);
            } else if (isWritable && !interop.isBufferWritable(buffer)) {
                exception1.enter();
                throw new LLVMPolyglotException(this, "Buffer is read-only.");
            } else if (length > interop.getBufferSize(buffer)) {
                exception2.enter();
                throw new LLVMPolyglotException(this, "Buffer has length '%d', but at least '%d' bytes are required.", interop.getBufferSize(buffer), length);
            }
            return pointer;
        } catch (UnsupportedMessageException ex) {
            exception3.enter();
            return unsupported(pointer, length);
        }
    }

    @Fallback
    public Object unsupported(@SuppressWarnings("unused") Object buffer, @SuppressWarnings("unused") Object length) {
        throw new LLVMPolyglotException(this, "Function argument is not a buffer");
    }

    public static LLVMPolyglotFromBufferNode create(boolean isWritable, LLVMExpressionNode buffer, LLVMExpressionNode length) {
        return LLVMPolyglotFromBufferNodeGen.create(isWritable, buffer, length);
    }
}
