package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

public class MHInvokeBasicNode extends EspressoBaseNode {

    public MHInvokeBasicNode(Method method) {
        super(method);
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        Meta meta = getMeta();
        StaticObjectImpl mh = (StaticObjectImpl) frame.getArguments()[0];
        StaticObjectImpl lform = (StaticObjectImpl) mh.getField(meta.form);
        StaticObjectImpl mname = (StaticObjectImpl) lform.getField(meta.vmentry);
        Method target = (Method) mname.getHiddenField("vmtarget");
        return target.invokeDirect(null, frame.getArguments());
    }
}
