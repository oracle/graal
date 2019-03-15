package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

// Currently unused.
public class MHInvokeBasicNode extends EspressoBaseNode {
    private final int argCount;


    public MHInvokeBasicNode(Method method) {
        super(method);
        this.argCount = method.getParameterCount();
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        Meta meta = getMeta();
        StaticObjectImpl mh = (StaticObjectImpl)frame.getArguments()[0];
        StaticObjectImpl lform = (StaticObjectImpl)mh.getField(meta.form);
        StaticObjectImpl mname = (StaticObjectImpl)lform.getField(meta.vmentry);
        Method target = (Method) mname.getHiddenField("vmtarget");
        return target.invokeDirect(null, frame.getArguments());
    }
}
