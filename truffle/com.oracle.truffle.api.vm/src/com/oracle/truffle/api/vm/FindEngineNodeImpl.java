package com.oracle.truffle.api.vm;

import com.oracle.truffle.api.impl.FindEngineNode;

final class FindEngineNodeImpl extends FindEngineNode {
    private PolyglotEngine one;

    FindEngineNodeImpl() {
    }

    void registerEngine(Thread usableIn, PolyglotEngine engine) {
        one = engine;
    }

    @Override
    protected Object findEngine() {
        return one;
    }

}
