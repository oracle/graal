package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;

@GenerateUncached
@NodeInfo(shortName = "class initcheck")
public abstract class InitCheck extends Node {

    protected final static int LIMIT = 1;

    public abstract void execute(ObjectKlass klass);

    @Specialization(limit = "LIMIT", guards = "cachedKlass == klass")
    void doCached(ObjectKlass klass,
                    @Cached("klass") ObjectKlass cachedKlass) {
        if (!klass.isInitialized()) {
            // Deopt loop if class initialization fails.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            cachedKlass.safeInitialize();
        }
    }

    @Specialization(replaces = "doCached")
    void doGeneric(ObjectKlass klass) {
        if (CompilerDirectives.isPartialEvaluationConstant(klass)) {
            if (!klass.isInitialized()) {
                // Deopt loop if class initialization fails.
                CompilerDirectives.transferToInterpreterAndInvalidate();
                klass.safeInitialize();
            }
        } else {
            initCheckBoundary(klass);
        }
    }

    @TruffleBoundary(allowInlining = true)
    static void initCheckBoundary(ObjectKlass klass) {
        if (!klass.isInitialized()) {
            klass.safeInitialize();
        }
    }
}
