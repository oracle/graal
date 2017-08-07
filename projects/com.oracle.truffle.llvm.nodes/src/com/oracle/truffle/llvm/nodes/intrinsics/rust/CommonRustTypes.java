/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.rust;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.types.DataSpecConverter;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

final class CommonRustTypes {

    static final class StrSliceType extends RustType {

        @CompilationFinal private int lengthOffset = -1;

        private StrSliceType(DataSpecConverter dataLayout, Type type) {
            super(dataLayout, type);
        }

        @TruffleBoundary
        String read(long address) {
            long strAddr = LLVMMemory.getAddress(address).getVal();
            int strLen = LLVMMemory.getI32(address + getLengthOffset()); // long to int
            StringBuilder strBuilder = new StringBuilder();
            for (int i = 0; i < strLen; i++) {
                strBuilder.append((char) Byte.toUnsignedInt(LLVMMemory.getI8(strAddr)));
                strAddr += Byte.BYTES;
            }
            return strBuilder.toString();
        }

        private int getLengthOffset() {
            if (lengthOffset == -1) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.lengthOffset = ((StructureType) type).getOffsetOf(1, dataLayout);
            }
            return lengthOffset;
        }

        static StrSliceType create(DataSpecConverter dataLayout) {
            Type type = new StructureType(false, new Type[]{new PointerType(PrimitiveType.I8), PrimitiveType.I64});
            return new StrSliceType(dataLayout, type);
        }

    }

}
