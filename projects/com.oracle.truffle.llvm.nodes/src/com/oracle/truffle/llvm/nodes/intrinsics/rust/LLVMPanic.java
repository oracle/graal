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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.nodes.intrinsics.rust.CommonRustTypes.StrSliceType;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.DataSpecConverter;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMPanic extends LLVMIntrinsic {

    private final PanicLocType panicLoc;

    public LLVMPanic(DataSpecConverter dataLayout) {
        this.panicLoc = PanicLocType.create(dataLayout);
    }

    @Specialization
    public Object execute(LLVMGlobalVariable panicLocVar, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        LLVMAddress addr = globalAccess.getNativeLocation(panicLocVar);
        throw panicLoc.read(addr.getVal());
    }

    private static final class PanicLocType extends RustType {

        private final StrSliceType strslice;

        @CompilationFinal private int offsetFilename = -1;
        @CompilationFinal private int offsetLineNr = -1;

        private PanicLocType(DataSpecConverter dataLayout, Type type, StrSliceType strslice) {
            super(dataLayout, type);
            this.strslice = strslice;
        }

        RustPanicException read(long address) {
            String desc = strslice.read(address);
            String filename = strslice.read(address + getOffsetFilename());
            int linenr = LLVMMemory.getI32(address + getOffsetLineNr());
            return new RustPanicException(desc, filename, linenr);
        }

        private int getOffsetFilename() {
            if (offsetFilename == -1) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.offsetFilename = getStructType().getOffsetOf(1, dataLayout);
            }
            return offsetFilename;
        }

        private int getOffsetLineNr() {
            if (offsetLineNr == -1) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.offsetLineNr = getStructType().getOffsetOf(2, dataLayout);
            }
            return offsetLineNr;
        }

        private StructureType getStructType() {
            return ((StructureType) ((PointerType) type).getElementType(0));
        }

        static PanicLocType create(DataSpecConverter dataLayout) {
            StrSliceType strslice = StrSliceType.create(dataLayout);
            Type type = new PointerType((new StructureType(false, new Type[]{strslice.getType(), strslice.getType(), PrimitiveType.I32})));
            return new PanicLocType(dataLayout, type, strslice);
        }

    }

}
