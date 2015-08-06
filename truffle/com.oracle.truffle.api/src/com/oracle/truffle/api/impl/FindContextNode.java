package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("rawtypes")
public final class FindContextNode<C> extends Node {
    private final Class<TruffleLanguage> languageClass;
    private @CompilerDirectives.CompilationFinal C context;
    private @CompilerDirectives.CompilationFinal Assumption oneVM;

    public FindContextNode(Class<TruffleLanguage> type) {
        this.languageClass = type;
    }

    public C executeFindContext() {
        if (context != null && oneVM.isValid()) {
            return context;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        oneVM = Accessor.oneVMAssumption();
        return context = Accessor.findContext(languageClass);
    }

    public Class<? extends TruffleLanguage> getLanguageClass() {
        return languageClass;
    }
}
