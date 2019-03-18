package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

public class MHInvokeGenericNode extends EspressoBaseNode {
    final int argCount;
    final StaticObjectImpl mname;
    final StaticObject appendix;
    final Method target;

    public MHInvokeGenericNode(Method method, StaticObjectImpl memberName, StaticObject appendix) {
        super(method);
        this.argCount = Signatures.parameterCount(getMethod().getParsedSignature(), false);
        this.mname = memberName;
        this.appendix = appendix;
        this.target = (Method) memberName.getHiddenField("vmtarget");
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        Object[] args = new Object[argCount + 2];
        assert (getMethod().hasReceiver());
        args[0] = frame.getArguments()[0];
        copyOfRange(frame.getArguments(), 1, args, 1, argCount);
        args[args.length - 1] = appendix;
        return target.invokeDirect(null, args);
    }

    @ExplodeLoop
    private static void copyOfRange(Object[] src, int from, Object[] dst, int start, final int length) {
        assert (src.length >= from + length && dst.length >= start + length);
        for (int i = 0; i < length; ++i) {
            dst[i + start] = src[i + from];
        }
    }
}
