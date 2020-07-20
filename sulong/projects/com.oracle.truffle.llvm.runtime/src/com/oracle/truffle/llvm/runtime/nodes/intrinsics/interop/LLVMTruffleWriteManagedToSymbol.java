/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMReplaceSymbolNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMReplaceSymbolNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNode.AttachInteropTypeNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.AttachInteropTypeNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * Replaces a symbol variable with a different object. This does not change the value stored in the
 * global, but it modifies the symbol's content in the symbol table. This is an expensive operation,
 * and it will only influence future lookups of the global variable address, so that existing
 * pointers to the global variable will remain unchanged.
 */
@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMTruffleWriteManagedToSymbol extends LLVMIntrinsic {

    @Child AttachInteropTypeNode attachType = AttachInteropTypeNodeGen.create();
    @Child LLVMReplaceSymbolNode globalReplace = LLVMReplaceSymbolNodeGen.create();

    @TruffleBoundary
    @Specialization
    protected Object write(LLVMPointer address, Object value,
                    @CachedContext(LLVMLanguage.class) LLVMContext ctx) {

        /*
         * The list of symbols should be all global symbols or all function symbols more over, the
         * list of symbols should all be pointing to the same value or function code, and they
         * should all have the same name.
         */
        List<LLVMSymbol> symbols = ctx.removeSymbolReverseMap(address);

        if (symbols == null) {
            throw new LLVMPolyglotException(this, "First argument to truffle_assign_managed must be a pointer to a symbol.");
        }

        Object newValue = value;
        boolean allGlobals = symbols.get(0).isGlobalVariable();

        /*
         * The interop type of the global symbol has to be attached to the new object that's
         * replacing the global. This is done by creating a LLVMTypedForeignObject wrapping it
         * around the new object with the global's interop type.
         */
        if (allGlobals) {
            newValue = attachType.execute(value, symbols.get(0).asGlobalVariable().getInteropType(ctx));
        }

        /*
         * Every symbol in the symbol list should point to the same value even if they are stored in
         * different locations in the symbol table.
         */
        for (LLVMSymbol symbol : symbols) {
            if (allGlobals) {
                assert symbol.isGlobalVariable();
                globalReplace.execute(LLVMPointer.cast(newValue), symbol);
            } else {
                assert symbol.isFunction();
                globalReplace.execute(LLVMPointer.cast(newValue), symbol);
            }
        }

        ctx.registerSymbolReverseMap(symbols, LLVMPointer.cast(value));
        return newValue;
    }
}
