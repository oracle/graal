package com.oracle.truffle.espresso.nodes;

import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.LinkToSpecial;

import com.oracle.truffle.espresso.impl.Method;

public class LinkToSpecialNode extends MHLinkToNode {
    LinkToSpecialNode(Method method) {
        super(method, LinkToSpecial);
    }

    @Override
    protected final Object linkTo(Method target, Object[] args) {
        return callNode.call(target.getCallTarget(), args);
    }
}
