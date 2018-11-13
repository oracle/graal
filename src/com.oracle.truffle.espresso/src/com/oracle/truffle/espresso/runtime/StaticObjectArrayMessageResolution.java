package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = StaticObjectArray.class)
public class StaticObjectArrayMessageResolution {
    @CanResolve
    abstract static class CanResolveVoid extends Node {
        boolean test(TruffleObject object) {
            return object instanceof StaticObjectArray;
        }
    }
}
