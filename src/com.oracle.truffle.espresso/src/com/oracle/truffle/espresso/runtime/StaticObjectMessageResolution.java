package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

/**
 * TruffleNFI asserts that arguments passed to native are interop objects, this is not a hard
 * requirement. This is a workaround for TruffleNFI checks making Espresso objects become sort of
 * `void` interop objects.
 */
@MessageResolution(receiverType = StaticObject.class)
class StaticObjectMessageResolution {
    @CanResolve
    abstract static class CanResolveStaticObject extends Node {
        boolean test(TruffleObject object) {
            return object instanceof StaticObject;
        }
    }
}
