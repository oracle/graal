package com.oracle.truffle.espresso.nodes;

import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.LinkToStatic;

import com.oracle.truffle.espresso.impl.Method;

public class LinkToStaticNode extends MHLinkToNode {
    LinkToStaticNode(Method method) {
        super(method, LinkToStatic);
    }

    @Override
    protected final Object linkTo(Method target, Object[] args) {
        return callNode.call(target.getCallTarget(), args);
    }
}
