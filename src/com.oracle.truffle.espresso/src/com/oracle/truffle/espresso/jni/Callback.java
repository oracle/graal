package com.oracle.truffle.espresso.jni;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

class Callback implements TruffleObject {

    private final int arity;
    private final Function function;

    public Callback(int arity, Function function) {
        this.arity = arity;
        this.function = function;
    }

    @CompilerDirectives.TruffleBoundary
    Object call(Object... args) {
        // if (args.length == arity) {
            Object ret = function.call(args);
            return ret;
        // } else {
            // throw ArityException.raise(arity, args.length);
        // }
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return CallbackMessageResolutionForeign.ACCESS;
    }

    public interface Function {

        Object call(Object... args);
    }
}
