/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.nodes.memory.LLVMAddressGetElementPtrNode.LLVMIncrementPointerNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMAddressGetElementPtrNodeGen.LLVMIncrementPointerNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMAddressDirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI32LoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadNode;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMAddressStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.VoidType;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
@NodeField(type = int.class, name = "numberExplicitArguments")
public abstract class LLVMX86_64BitVACopy extends LLVMBuiltin {

    @Child private LLVMIncrementPointerNode pointerArithmeticStructInit;
    @Child private LLVMStoreNode gpOffsetStore;
    @Child private LLVMStoreNode fpOffsetStore;
    @Child private LLVMStoreNode overflowArgAreaStore;
    @Child private LLVMStoreNode regSaveAreaStore;

    @Child private LLVMLoadNode gpOffsetLoad;
    @Child private LLVMLoadNode fpOffsetLoad;
    @Child private LLVMLoadNode overflowArgAreaLoad;
    @Child private LLVMLoadNode regSaveAreaLoad;

    public LLVMX86_64BitVACopy() {
        this.pointerArithmeticStructInit = LLVMIncrementPointerNodeGen.create();
        this.gpOffsetStore = LLVMI32StoreNodeGen.create();
        this.fpOffsetStore = LLVMI32StoreNodeGen.create();
        this.overflowArgAreaStore = LLVMAddressStoreNodeGen.create(new PointerType(VoidType.INSTANCE));
        this.regSaveAreaStore = LLVMAddressStoreNodeGen.create(new PointerType(VoidType.INSTANCE));

        this.gpOffsetLoad = LLVMI32LoadNodeGen.create();
        this.fpOffsetLoad = LLVMI32LoadNodeGen.create();
        this.overflowArgAreaLoad = LLVMAddressDirectLoadNodeGen.create();
        this.regSaveAreaLoad = LLVMAddressDirectLoadNodeGen.create();
    }

    private void setGPOffset(VirtualFrame frame, Object address, int value) {
        Object p = pointerArithmeticStructInit.executeWithTarget(address, X86_64BitVarArgs.GP_OFFSET, PrimitiveType.I32);
        gpOffsetStore.executeWithTarget(frame, p, value);
    }

    private void setFPOffset(VirtualFrame frame, Object address, int value) {
        Object p = pointerArithmeticStructInit.executeWithTarget(address, X86_64BitVarArgs.FP_OFFSET, PrimitiveType.I32);
        fpOffsetStore.executeWithTarget(frame, p, value);
    }

    private void setOverflowArgArea(VirtualFrame frame, Object address, Object value) {
        Object p = pointerArithmeticStructInit.executeWithTarget(address, X86_64BitVarArgs.OVERFLOW_ARG_AREA, new PointerType(VoidType.INSTANCE));
        overflowArgAreaStore.executeWithTarget(frame, p, value);
    }

    private void setRegSaveArea(VirtualFrame frame, Object address, Object value) {
        Object p = pointerArithmeticStructInit.executeWithTarget(address, X86_64BitVarArgs.REG_SAVE_AREA, new PointerType(VoidType.INSTANCE));
        regSaveAreaStore.executeWithTarget(frame, p, value);
    }

    private int getGPOffset(VirtualFrame frame, Object address) {
        Object p = pointerArithmeticStructInit.executeWithTarget(address, X86_64BitVarArgs.GP_OFFSET, PrimitiveType.I32);
        return (int) gpOffsetLoad.executeWithTarget(frame, p);
    }

    private int getFPOffset(VirtualFrame frame, Object address) {
        Object p = pointerArithmeticStructInit.executeWithTarget(address, X86_64BitVarArgs.FP_OFFSET, PrimitiveType.I32);
        return (int) fpOffsetLoad.executeWithTarget(frame, p);
    }

    private Object getOverflowArgArea(VirtualFrame frame, Object address) {
        Object p = pointerArithmeticStructInit.executeWithTarget(address, X86_64BitVarArgs.OVERFLOW_ARG_AREA, new PointerType(VoidType.INSTANCE));
        return overflowArgAreaLoad.executeWithTarget(frame, p);
    }

    private Object getRegSaveArea(VirtualFrame frame, Object address) {
        Object p = pointerArithmeticStructInit.executeWithTarget(address, X86_64BitVarArgs.REG_SAVE_AREA, new PointerType(VoidType.INSTANCE));
        return regSaveAreaLoad.executeWithTarget(frame, p);
    }

    public abstract int getNumberExplicitArguments();

    @Specialization
    public Object executeVoid(VirtualFrame frame, LLVMGlobalVariable dest, LLVMGlobalVariable source, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess1,
                    @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess2) {
        return executeVoid(frame, globalAccess1.getNativeLocation(dest), globalAccess2.getNativeLocation(source));
    }

    @Specialization
    public Object executeVoid(VirtualFrame frame, Object dest, Object source) {

        /*
         * COPY THIS: typedef struct { unsigned int gp_offset; unsigned int fp_offset; void
         * *overflow_arg_area; void *reg_save_area; } va_list[1];
         */
        setGPOffset(frame, dest, getGPOffset(frame, source));
        setFPOffset(frame, dest, getFPOffset(frame, source));
        setOverflowArgArea(frame, dest, getOverflowArgArea(frame, source));
        setRegSaveArea(frame, dest, getRegSaveArea(frame, source));

        return null;
    }

}
