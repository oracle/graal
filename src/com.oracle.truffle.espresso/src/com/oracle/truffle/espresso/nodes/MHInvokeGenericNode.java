package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;

public class MHInvokeGenericNode extends EspressoBaseNode {
    final int argCount;

    public MHInvokeGenericNode(Method method) {
        super(method);
        this.argCount = Signatures.parameterCount(getMethod().getParsedSignature(), false);
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        Object receiver;
        Object[] args;
        if (getMethod().hasReceiver()) {
            receiver = frame.getArguments()[0];
            args = copyOfRange(frame.getArguments(), 1, argCount + 1);
        } else { // Should never happen !!!
            receiver = null;
            args = frame.getArguments();
        }
        return getMeta().invoke.invokeDirect(receiver, new StaticObjectArray(getMeta().Object_array, args));
    }

    @ExplodeLoop
    private static Object[] copyOfRange(Object[] src, int from, int toExclusive) {
        int len = toExclusive - from;
        Object[] dst = new Object[len];
        for (int i = 0; i < len; ++i) {
            dst[i] = src[i + from];
        }
        return dst;
    }
}
