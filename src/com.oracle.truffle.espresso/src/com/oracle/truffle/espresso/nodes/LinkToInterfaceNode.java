package com.oracle.truffle.espresso.nodes;

import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.LinkToInterface;

import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class LinkToInterfaceNode extends MHLinkToNode {
    LinkToInterfaceNode(Method method) {
        super(method, LinkToInterface);
    }

    @Override
    protected final Object linkTo(Method target, Object[] args) {
        StaticObject receiver = (StaticObject) args[0];
        assert !receiver.getKlass().isArray();
        Method resolved = ((ObjectKlass) receiver.getKlass()).itableLookup(target.getDeclaringKlass(), target.getITableIndex());
        return callNode.call(resolved.getCallTarget(), args);
    }
}
