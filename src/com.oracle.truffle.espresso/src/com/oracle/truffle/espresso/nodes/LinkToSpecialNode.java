package com.oracle.truffle.espresso.nodes;

import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.LinkToSpecial;

import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Method;

public class LinkToSpecialNode extends MHLinkToNode {
    LinkToSpecialNode(Method method) {
        super(method, LinkToSpecial);
    }

    @Override
    protected final Object linkTo(Object[] args) {
        Method target = getTarget(args);
        Object result = callNode.call(target.getCallTarget(), unbasic(args, target.getParsedSignature(), 0, argCount - 1, true));
        return rebasic(result, Signatures.returnType(target.getParsedSignature()));
    }
}
