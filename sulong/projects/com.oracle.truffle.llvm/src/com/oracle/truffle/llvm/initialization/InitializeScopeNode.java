package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLocalScope;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

import java.util.ArrayList;

/**
 * Initialization node for the global scope and the local scope of the module. The scopes are
 * allocated from the symbols in the file scope of the module.
 *
 * @see InitializeSymbolsNode see Runner.InitializeGlobalNode see Runner.InitializeModuleNode see
 *      Runner.InitializeOverwriteNode see Runner.InitializeExternalNode
 */
public final class InitializeScopeNode extends LLVMNode {
    @Children final AllocScopeNode[] allocScopes;
    private final LLVMScope fileScope;

    public InitializeScopeNode(LLVMParserResult result) {
        this.fileScope = result.getRuntime().getFileScope();
        ArrayList<AllocScopeNode> allocScopesList = new ArrayList<>();
        for (LLVMSymbol symbol : fileScope.values()) {
            if (symbol.isExported()) {
                allocScopesList.add(new AllocScopeNode(symbol));
            }
        }
        this.allocScopes = allocScopesList.toArray(AllocScopeNode.EMPTY);
    }

    public void execute(LLVMContext context, LLVMLocalScope localScope) {
        synchronized (context) {
            localScope.addMissingLinkageName(fileScope);
            for (int i = 0; i < allocScopes.length; i++) {
                AllocScopeNode allocScope = allocScopes[i];
                allocScope.allocateScope(context, localScope);
            }
        }
    }

    /**
     * Allocating a symbol to the global and local scope of a module.
     */
    private static final class AllocScopeNode extends LLVMNode {

        static final AllocScopeNode[] EMPTY = {};
        final LLVMSymbol symbol;

        AllocScopeNode(LLVMSymbol symbol) {
            this.symbol = symbol;
        }

        void allocateScope(LLVMContext context, LLVMLocalScope localScope) {
            LLVMScope globalScope = context.getGlobalScope();
            LLVMSymbol exportedSymbol = globalScope.get(symbol.getName());
            if (exportedSymbol == null) {
                globalScope.register(symbol);
            }
            LLVMSymbol exportedSymbolFromLocal = localScope.get(symbol.getName());
            if (exportedSymbolFromLocal == null) {
                localScope.register(symbol);
            }
        }
    }
}
