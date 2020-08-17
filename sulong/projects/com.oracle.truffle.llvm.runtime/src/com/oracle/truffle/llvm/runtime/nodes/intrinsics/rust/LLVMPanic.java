/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.rust;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMExitException;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMPanic extends LLVMIntrinsic {

    protected PanicLocType createPanicLocation() {
        LLVMFunctionStartNode startNode = (LLVMFunctionStartNode) getRootNode();
        DataLayout dataSpecConverter = startNode.getDatalayout();
        return PanicLocType.create(dataSpecConverter);
    }

    @Specialization
    protected Object doOp(LLVMPointer panicLocVar,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                    @Cached("createPanicLocation()") PanicLocType panicLoc,
                    @CachedLanguage LLVMLanguage language) {
        LLVMNativePointer pointer = toNative.executeWithTarget(panicLocVar);
        throw panicLoc.read(language.getLLVMMemory(), pointer.asNative(), this);
    }

    static final class PanicLocType {
        private static final int EXIT_CODE_PANIC = 101;

        private final StrSliceType strslice;
        private final long offsetFilename;
        private final long offsetLineNr;

        private PanicLocType(DataLayout dataLayout, Type type, StrSliceType strslice) {
            this.strslice = strslice;
            StructureType structureType = (StructureType) ((PointerType) type).getElementType(0);
            try {
                this.offsetFilename = structureType.getOffsetOf(1, dataLayout);
                this.offsetLineNr = structureType.getOffsetOf(2, dataLayout);
            } catch (TypeOverflowException e) {
                // should not reach here
                throw new AssertionError(e);
            }
        }

        @TruffleBoundary
        LLVMExitException read(LLVMMemory memory, long address, Node location) {
            String desc = strslice.read(memory, address);
            String filename = strslice.read(memory, address + offsetFilename);
            int linenr = memory.getI32(null, address + offsetLineNr);
            System.err.printf("thread '%s' panicked at '%s', %s:%d%n", Thread.currentThread().getName(), desc, filename, linenr);
            System.err.print("note: No backtrace available");
            return LLVMExitException.exit(EXIT_CODE_PANIC, location);
        }

        static PanicLocType create(DataLayout dataLayout) {
            CompilerAsserts.neverPartOfCompilation();
            StrSliceType strslice = StrSliceType.create(dataLayout);
            Type type = new PointerType((StructureType.createUnnamed(false, strslice.getType(), strslice.getType(), PrimitiveType.I32)));
            return new PanicLocType(dataLayout, type, strslice);
        }
    }

    private static final class StrSliceType {

        private final long lengthOffset;
        private final Type type;

        private StrSliceType(DataLayout dataLayout, Type type) {
            try {
                this.lengthOffset = ((StructureType) type).getOffsetOf(1, dataLayout);
            } catch (TypeOverflowException e) {
                // should not reach here
                throw new AssertionError(e);
            }
            this.type = type;
        }

        @TruffleBoundary
        String read(LLVMMemory memory, long address) {
            long strAddr = memory.getPointer(null, address).asNative();
            int strLen = memory.getI32(null, address + lengthOffset);
            StringBuilder strBuilder = new StringBuilder();
            for (int i = 0; i < strLen; i++) {
                strBuilder.append((char) Byte.toUnsignedInt(memory.getI8(null, strAddr)));
                strAddr += Byte.BYTES;
            }
            return strBuilder.toString();
        }

        public Type getType() {
            return type;
        }

        static StrSliceType create(DataLayout dataLayout) {
            Type type = StructureType.createUnnamed(false, new PointerType(PrimitiveType.I8), PrimitiveType.I64);
            return new StrSliceType(dataLayout, type);
        }
    }
}
