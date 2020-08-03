package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.model.GlobalSymbol;
import com.oracle.truffle.llvm.parser.model.functions.FunctionSymbol;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
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
 * @see InitializeSymbolsNode see Runner.InitializeGlobalNode see Runner.InitializeModuleNode see
 *      Runner.InitializeOverwriteNode
 */
public final class InitializeExternalNode extends LLVMNode {
    @Child LLVMWriteSymbolNode writeSymbols;
    @Children AllocExternalSymbolNode[] allocExternalSymbols;
    private final NodeFactory nodeFactory;

    public InitializeExternalNode(LLVMParserResult result) {
        this.nodeFactory = result.getRuntime().getNodeFactory();
        LLVMScope fileScope = result.getRuntime().getFileScope();
        ArrayList<AllocExternalSymbolNode> allocExternaSymbolsList = new ArrayList<>();

        // Bind all functions that are not defined/resolved as either a bitcode function
        // defined in another library, an intrinsic function or a native function.
        for (FunctionSymbol symbol : result.getExternalFunctions()) {
            String name = symbol.getName();
            LLVMFunction function = fileScope.getFunction(name);
            if (name.startsWith("llvm.") || name.startsWith("__builtin_") || name.equals("polyglot_get_arg") || name.equals("polyglot_get_arg_count")) {
                continue;
            }
            allocExternaSymbolsList.add(AllocExternalFunctionNodeGen.create(function, nodeFactory));
        }

        for (GlobalSymbol symbol : result.getExternalGlobals()) {
            LLVMGlobal global = fileScope.getGlobalVariable(symbol.getName());
            allocExternaSymbolsList.add(AllocExternalGlobalNodeGen.create(global));
        }

        this.writeSymbols = LLVMWriteSymbolNodeGen.create();
        this.allocExternalSymbols = allocExternaSymbolsList.toArray(AllocExternalSymbolNode.EMPTY);
    }

    /*
     * (PLi): Need to be careful of native functions/globals that are not in the nfi context (i.e.
     * __xstat). Ideally they will be added to the symbol table as unresolved/undefined
     * functions/globals.
     */
    @ExplodeLoop
    public void execute(LLVMContext context, int id) {
        LLVMScope globalScope = context.getGlobalScope();
        LLVMIntrinsicProvider intrinsicProvider = LLVMLanguage.getLanguage().getCapability(LLVMIntrinsicProvider.class);
        NFIContextExtension nfiContextExtension = getNfiContextExtension(context);

        synchronized (context) {
            // functions and globals
            for (int i = 0; i < allocExternalSymbols.length; i++) {
                AllocExternalSymbolNode function = allocExternalSymbols[i];
                LLVMLocalScope localScope = context.getLocalScope(id);
                LLVMPointer pointer = function.execute(localScope, globalScope, intrinsicProvider, nfiContextExtension);
                // skip allocating fallbacks
                if (pointer == null) {
                    continue;
                }
                writeSymbols.execute(pointer, function.symbol);
            }
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static NFIContextExtension getNfiContextExtension(LLVMContext context) {
        return context.getContextExtensionOrNull(NFIContextExtension.class);
    }
}
