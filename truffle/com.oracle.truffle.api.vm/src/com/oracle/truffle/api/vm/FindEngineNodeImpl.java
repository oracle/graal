package com.oracle.truffle.api.vm;

import com.oracle.truffle.api.impl.FindEngineNode;

final class FindEngineNodeImpl extends FindEngineNode {
    private PolyglotEngine one;

    FindEngineNodeImpl() {
    }

    void registerEngine(Thread usableIn, PolyglotEngine engine) {
        if (one == engine) {
            return;
        }
        if (one != null) {
            throw new IllegalStateException("There already is an engine!");
        }
        one = engine;
    }

    void unregisterEngine(Thread wasUsedIn, PolyglotEngine engine) {
    }

    void disposeEngine(Thread wasUsedIn, PolyglotEngine engine) {
        if (one == engine) {
            one = null;
        }
    }

    @Override
    protected Object findEngine() {
        return one;
    }

}
