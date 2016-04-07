package com.oracle.truffle.api.vm.contextproto;

import java.util.Arrays;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;

final class ContextStore {

    @CompilationFinal Object[] store;
    @CompilationFinal private Assumption storeStable = Truffle.getRuntime().createAssumption("context store stable");

    public ContextStore(int capacity) {
        this.store = new Object[capacity]; // initial language capacity
    }

    public Object getContext(int index) {
        if (!storeStable.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            storeStable = Truffle.getRuntime().createAssumption();
        }
        return store[index];
    }

    public void setContext(int languageId, Object context) {
        if (languageId >= store.length) {
            store = Arrays.copyOf(store, store.length << 2);
        }
        store[languageId] = context;
        storeStable.invalidate();
    }

}
