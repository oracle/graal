package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.impl.Method;

final class Utils {

    static DirectCallNode createAndMaybeForceInline(Method.MethodVersion resolvedMethod) {
        DirectCallNode callNode = DirectCallNode.create(resolvedMethod.getCallTarget());
        if (resolvedMethod.getMethod().isForceInline()) {
            callNode.forceInlining();
        }
        return callNode;
    }
}
