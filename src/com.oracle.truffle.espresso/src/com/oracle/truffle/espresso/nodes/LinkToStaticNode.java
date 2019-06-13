package com.oracle.truffle.espresso.nodes;

import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.LinkToStatic;

import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Method;

public class LinkToStaticNode extends MHLinkToNode {
    LinkToStaticNode(Method method) {
        super(method, LinkToStatic);
    }

    @Override
    protected final Object linkTo(Object[] args) {
        Method target = getTarget(args);
        Object result = callNode.call(target.getCallTarget(), unbasic(args, target.getParsedSignature(), 0, argCount - 1, false));
        return rebasic(result, Signatures.returnType(target.getParsedSignature()));
    }
}
