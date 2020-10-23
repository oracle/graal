/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.initialization.AllocExternalSymbolNodeFactory.AllocExistingLocalSymbolsNodeGen.AllocExistingGlobalSymbolsNodeGen.AllocExternalFunctionNodeGen;
import com.oracle.truffle.llvm.initialization.AllocExternalSymbolNodeFactory.AllocExistingLocalSymbolsNodeGen.AllocExistingGlobalSymbolsNodeGen.AllocExternalGlobalNodeGen;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.model.GlobalSymbol;
import com.oracle.truffle.llvm.parser.model.functions.FunctionSymbol;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLocalScope;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMWriteSymbolNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMWriteSymbolNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

import java.util.ArrayList;

/**
 *
 * {@link InitializeExternalNode} initializes the symbol table for all the external symbols of this
 * module. For external functions, if they are already defined in the local scope or the global
 * scope, then the already defined symbol is placed into this function's spot in the symbol table.
 * Otherwise, an instrinc or native function is created if they exists. Similarly, for external
 * globals the local and global scope is checked first for this external global, and if it exists,
 * then the defined global symbol from the local/global scope is placed into this external global's
 * location in the symbol table.
 *
 * Initialize external and exported symbols, by populating the symbol table of every external
 * symbols of a given bitcode file.
 * <p>
 * External bitcode functions will have their entry into the symbol table be replaced with the entry
 * of it's corresponding defined function in the local scope, or the gloabl scope if the function is
 * loaded in a previous parsing phase. Otherwise an instrinic or native function will be created if
 * they are available. Similarly, external global will have their entry into the symbol table be
 * that of the corresponding defined global symbol in the local scope. If no global of such name
 * exists, a native global is created if it exists in the NFI context.
 *
 * @see InitializeScopeNode
 * @see InitializeSymbolsNode
 * @see InitializeGlobalNode
 * @see InitializeModuleNode
 * @see InitializeOverwriteNode
 */
public final class InitializeExternalNode extends LLVMNode {
    @Children AllocExternalSymbolNode[] allocExternalSymbols;
    @Children final LLVMWriteSymbolNode[] writeSymbols;

    private final NodeFactory nodeFactory;

    public InitializeExternalNode(LLVMParserResult result) {
        this.nodeFactory = result.getRuntime().getNodeFactory();
        LLVMScope fileScope = result.getRuntime().getFileScope();
        ArrayList<LLVMWriteSymbolNode> writeSymbolsList = new ArrayList<>();
        ArrayList<AllocExternalSymbolNode> allocExternaSymbolsList = new ArrayList<>();

        // Bind all functions that are not defined/resolved as either a bitcode function
        // defined in another library, an intrinsic function or a native function.
        for (FunctionSymbol symbol : result.getExternalFunctions()) {
            String name = symbol.getName();
            LLVMFunction function = fileScope.getFunction(name);
            LLVMFunctionCode functionCode = new LLVMFunctionCode(function);
            if (name.startsWith("llvm.") || name.startsWith("__builtin_") || name.equals("polyglot_get_arg") || name.equals("polyglot_get_arg_count")) {
                continue;
            }
            allocExternaSymbolsList.add(AllocExternalFunctionNodeGen.create(function, functionCode, nodeFactory));
            writeSymbolsList.add(LLVMWriteSymbolNodeGen.create(function));
        }

        for (GlobalSymbol symbol : result.getExternalGlobals()) {
            LLVMGlobal global = fileScope.getGlobalVariable(symbol.getName());
            allocExternaSymbolsList.add(AllocExternalGlobalNodeGen.create(global));
            writeSymbolsList.add(LLVMWriteSymbolNodeGen.create(global));
        }

        this.writeSymbols = writeSymbolsList.toArray(LLVMWriteSymbolNode.EMPTY);
        this.allocExternalSymbols = allocExternaSymbolsList.toArray(AllocExternalSymbolNode.EMPTY);
    }

    /*
     * (PLi): Need to be careful of native functions/globals that are not in the nfi context (i.e.
     * __xstat). Ideally they will be added to the symbol table as unresolved/undefined
     * functions/globals.
     */
    @ExplodeLoop
    public void execute(LLVMContext context, LLVMLocalScope localScope) {
        LLVMScope globalScope = context.getGlobalScope();
        LLVMIntrinsicProvider intrinsicProvider = LLVMLanguage.getLanguage().getCapability(LLVMIntrinsicProvider.class);
        NFIContextExtension nfiContextExtension = getNfiContextExtension(context);
        // functions and globals
        for (int i = 0; i < allocExternalSymbols.length; i++) {
            AllocExternalSymbolNode function = allocExternalSymbols[i];
            LLVMPointer pointer = function.execute(localScope, globalScope, intrinsicProvider, nfiContextExtension);
            // skip allocating fallbacks
            if (pointer == null) {
                continue;
            }
            writeSymbols[i].execute(pointer);
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static NFIContextExtension getNfiContextExtension(LLVMContext context) {
        return context.getContextExtensionOrNull(NFIContextExtension.class);
    }
}
