package com.oracle.truffle.espresso.nodes;

import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.LinkToInterface;

import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class LinkToInterfaceNode extends MHLinkToNode {
    LinkToInterfaceNode(Method method) {
        super(method, LinkToInterface);
    }

    @Override
    protected final Object linkTo(Object[] args) {
        Method target = getTarget(args);
        StaticObject receiver = (StaticObject) args[0];
        assert !receiver.getKlass().isArray();
        target = ((ObjectKlass) receiver.getKlass()).itableLookup(target.getDeclaringKlass(), target.getITableIndex());
        Object result = callNode.call(target.getCallTarget(), unbasic(args, target.getParsedSignature(), 0, argCount - 1, true));
        return rebasic(result, Signatures.returnType(target.getParsedSignature()));
    }
}
