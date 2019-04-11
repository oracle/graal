package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;

import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.LinkToInterface;

public class LinkToInterfaceNode extends MHLinkToNode {
    LinkToInterfaceNode(Method method) {
        super(method, LinkToInterface);
    }

    @Override
    protected final Object linkTo(Object[] args) {
        Method target = getTarget(args);
        StaticObject receiver = (StaticObject) args[0];
        target = receiver.getKlass().itableLookup(target.getDeclaringKlass(), target.getITableIndex());
        Object result = callNode.call(target.getCallTarget(), unbasic(args, target.getParsedSignature(), 0, argCount - 1, true));
        return rebasic(result, Signatures.returnType(target.getParsedSignature()));
    }
}
