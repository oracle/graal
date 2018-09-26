package com.oracle.truffle.espresso.jni;

import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = Callback.class)
class CallbackMessageResolution {

    @Resolve(message = "EXECUTE")
    abstract static class ExecuteNode extends Node {
        Object access(Callback callback, Object[] arguments) {
            return callback.call(arguments);
        }
    }

    @Resolve(message = "IS_EXECUTABLE")
    abstract static class IsExecutable extends Node {

        @SuppressWarnings("unused")
        boolean access(Callback receiver) {
            return true;
        }
    }

    @CanResolve
    abstract static class CanResolveCallback extends Node {

        boolean test(TruffleObject object) {
            return object instanceof Callback;
        }
    }
}
