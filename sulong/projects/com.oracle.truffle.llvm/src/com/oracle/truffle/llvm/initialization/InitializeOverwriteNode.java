/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.initialization.AllocExternalSymbolNodeFactory.AllocExistingLocalSymbolsNodeGen;
import com.oracle.truffle.llvm.initialization.AllocExternalSymbolNodeFactory.AllocExistingLocalSymbolsNodeGen.AllocExistingGlobalSymbolsNodeGen.AllocExternalGlobalNodeGen;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.model.GlobalSymbol;
import com.oracle.truffle.llvm.parser.model.functions.FunctionSymbol;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMScopeChain;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen.RTLDFlags;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

import java.util.ArrayList;

/**
 *
 * The aim of {@link InitializeOverwriteNode} is to identify which defined symbols will be resolved
 * to their corresponding symbol in the local scope when they are called. If they resolve to the
 * symbol in the local scope then this symbol from the local scope is place into this defined
 * symbol's location in the symbol table. This means the local and global scope is no longer
 * required for symbol resolution, and everything is done simply by looking up the symbol in the
 * file scope.
 *
 * Overwriting for defined symbols that will be resolved to the local scope instead of the file
 * scope. If a defined symbol is required to be access via the local scope instead of it's file
 * scope, then that symbol's entry in the symbol table will be that of the defined symbol from the
 * local scope.
 *
 * @see InitializeSymbolsNode
 * @see InitializeGlobalNode
 * @see InitializeModuleNode
 * @see InitializeExternalNode
 */
public final class InitializeOverwriteNode extends LLVMNode {

    @Children private final AllocExternalSymbolNode[] allocExternalSymbols;
    @CompilationFinal(dimensions = 1) private final LLVMSymbol[] symbols;

    public InitializeOverwriteNode(LLVMParserResult result) {
        ArrayList<LLVMSymbol> symbolsList = new ArrayList<>();
        ArrayList<AllocExternalSymbolNode> allocExternaSymbolsList = new ArrayList<>();
        LLVMScope fileScope = result.getRuntime().getFileScope();

        // Rewrite all overridable functions and globals in the filescope from their respective
        // function/global in the localscope.
        for (FunctionSymbol symbol : result.getDefinedFunctions()) {
            if (symbol.isOverridable()) {
                LLVMFunction function = fileScope.getFunction(symbol.getName());
                // Functions are overwritten by functions from the localScope
                symbolsList.add(function);
                allocExternaSymbolsList.add(AllocExistingLocalSymbolsNodeGen.create(function));
            }
        }
        for (GlobalSymbol symbol : result.getDefinedGlobals()) {
            // Cannot override the reserved symbols CONSTRUCTORS_VARNAME and
            // DECONSTRUCTORS_VARNAME
            if (symbol.isOverridable() && !symbol.isIntrinsicGlobalVariable()) {
                LLVMGlobal global = fileScope.getGlobalVariable(symbol.getName());
                // Globals are overwritten by (non-hidden) global symbol of the same name in the
                // globalscope
                symbolsList.add(global);
                allocExternaSymbolsList.add(AllocExternalGlobalNodeGen.create(global));
            }
        }
        this.symbols = symbolsList.toArray(LLVMSymbol.EMPTY);
        this.allocExternalSymbols = allocExternaSymbolsList.toArray(AllocExternalSymbolNode.EMPTY);
    }

    @ExplodeLoop
    public void execute(LLVMContext context, LLVMScopeChain localScope, RTLDFlags rtldFlags) {
        LLVMScopeChain globalScope = context.getGlobalScopeChain();
        for (int i = 0; i < allocExternalSymbols.length; i++) {
            AllocExternalSymbolNode allocSymbol = allocExternalSymbols[i];
            LLVMPointer pointer = allocSymbol.execute(localScope, globalScope, null, null, context, rtldFlags);
            // skip allocating fallbacks
            if (pointer == null) {
                continue;
            }
            context.initializeSymbol(symbols[i], pointer);
        }
    }
}
