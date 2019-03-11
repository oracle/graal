package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;

public class MHInvokeBasicNode extends EspressoBaseNode {
    public MHInvokeBasicNode(Method method) {
        super(method);
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        throw EspressoError.unimplemented();
    }
}
