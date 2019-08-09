package com.oracle.truffle.espresso.nodes;

import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeSpecial;
import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.LinkToVirtual;

import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class LinkToVirtualNode extends MHLinkToNode {
    LinkToVirtualNode(Method method) {
        super(method, LinkToVirtual);
    }

    @Override
    protected final Object linkTo(Method target, Object[] args) {
        Method resolved = target;
        StaticObject receiver = (StaticObject) args[0];
        if ((target.getRefKind() != REF_invokeSpecial)) {
            resolved = receiver.getKlass().vtableLookup(target.getVTableIndex());
        }
        return callNode.call(resolved.getCallTarget(), args);
    }
}
