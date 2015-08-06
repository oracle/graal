package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public final class FindContextNode<C> extends Node {
    private final Class<TruffleLanguage<C>> type;
    private @CompilerDirectives.CompilationFinal C context; 
    private @CompilerDirectives.CompilationFinal Assumption oneVM;

    public FindContextNode(Class<TruffleLanguage<C>> type) {
        this.type = type;
    }

    public C executeFindContext(VirtualFrame frame) {
        if (context != null && oneVM.isValid()) {
            return context;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        oneVM = Accessor.oneVMAssumption();
        return context = Accessor.findContext(type);
    }

    public Class<? extends TruffleLanguage> type() {
        return type;
    }
}
