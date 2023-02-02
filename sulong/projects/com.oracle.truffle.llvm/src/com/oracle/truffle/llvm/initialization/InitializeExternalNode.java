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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.model.functions.FunctionSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMScopeChain;
import com.oracle.truffle.llvm.runtime.LLVMThreadLocalSymbol;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen;
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
 * @see InitializeSymbolsNode
 * @see InitializeGlobalNode
 * @see InitializeModuleNode
 * @see InitializeOverwriteNode
 */
public final class InitializeExternalNode extends LLVMNode {
    @CompilationFinal(dimensions = 1) private final LLVMFunction[] functions;
    @CompilationFinal(dimensions = 1) private final LLVMGlobal[] globals;
    @CompilationFinal(dimensions = 1) private final LLVMThreadLocalSymbol[] threadLocals;
    @Child private AllocExternalSymbolNode allocExternalSymbol;

    public InitializeExternalNode(LLVMParserResult result) {
        allocExternalSymbol = new AllocExternalSymbolNode(result);
        LLVMScope fileScope = result.getRuntime().getFileScope();
        ArrayList<LLVMFunction> functionList = new ArrayList<>();
        ArrayList<LLVMGlobal> globalList = new ArrayList<>();
        ArrayList<LLVMThreadLocalSymbol> threadLocalList = new ArrayList<>();

        // Bind all functions that are not defined/resolved as either a bitcode function
        // defined in another library, an intrinsic function or a native function.
        for (FunctionSymbol symbol : result.getExternalFunctions()) {
            String name = symbol.getName();
            LLVMFunction function = fileScope.getFunction(name);
            if (name.startsWith("llvm.") || name.startsWith("__builtin_") || name.equals("polyglot_get_arg") || name.equals("polyglot_get_arg_count")) {
                continue;
            }
            functionList.add(function);
        }

        for (GlobalVariable symbol : result.getExternalGlobals()) {
            if (symbol.isThreadLocal()) {
                threadLocalList.add(fileScope.getThreadLocalVariable(symbol.getName()));
            } else {
                globalList.add(fileScope.getGlobalVariable(symbol.getName()));
            }
        }

        this.functions = functionList.toArray(LLVMFunction.EMPTY);
        this.globals = globalList.toArray(LLVMGlobal.EMPTY);
        this.threadLocals = threadLocalList.toArray(LLVMThreadLocalSymbol.EMPTY);
    }

    /*
     * (PLi): Need to be careful of native functions/globals that are not in the nfi context (i.e.
     * __xstat). Ideally they will be added to the symbol table as unresolved/undefined
     * functions/globals.
     */
    public void execute(LLVMContext context, LLVMScopeChain localScope, LLVMDLOpen.RTLDFlags rtldFlags) {
        LLVMScopeChain globalScope = context.getGlobalScopeChain();
        LLVMIntrinsicProvider intrinsicProvider = getLanguage().getCapability(LLVMIntrinsicProvider.class);
        NativeContextExtension nativeContextExtension = getNativeContextExtension(context);

        for (LLVMGlobal global : globals) {
            LLVMPointer pointer = allocExternalSymbol.execute(localScope, globalScope, nativeContextExtension, context, rtldFlags, global);
            if (pointer != null) {
                context.initializeSymbol(global, pointer);
            }
        }

        for (LLVMThreadLocalSymbol symbol : threadLocals) {
            LLVMPointer pointer = allocExternalSymbol.execute(localScope, globalScope, context, rtldFlags, symbol);
            if (pointer != null) {
                context.initializeSymbol(symbol, pointer);
            }
        }

        for (LLVMFunction function : functions) {
            LLVMPointer pointer = allocExternalSymbol.execute(localScope, globalScope, intrinsicProvider, nativeContextExtension, context, rtldFlags, function);
            if (pointer != null) {
                context.initializeSymbol(function, pointer);
            }
        }
    }

    @TruffleBoundary
    private static NativeContextExtension getNativeContextExtension(LLVMContext context) {
        return context.getContextExtensionOrNull(NativeContextExtension.class);
    }
}
