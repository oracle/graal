package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

public class MHLinkToNode extends EspressoBaseNode {
    final int argCount;
    final int id;

    public MHLinkToNode(Method method, int id) {
        super(method);
        this.id = id;
        this.argCount = Signatures.parameterCount(getMethod().getParsedSignature(), false);
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        assert (getMethod().isStatic());
        Object[] args = frame.getArguments();
        return executeLinkTo(args);
    }

    private static Object executeLinkTo(Object[] args) {
        assert args.length > 0;
        StaticObjectImpl memberName = (StaticObjectImpl) args[args.length - 1];
        assert (memberName.getKlass().getType() == Symbol.Type.MemberName);

        Method target = (Method) memberName.getHiddenField("vmtarget");
        if (target.hasReceiver()) {
            StaticObject receiver = (StaticObject) args[0];
            Object[] trueArgs = new Object[args.length - 2];
            for (int i = 1; i < trueArgs.length; i++) {
                trueArgs[i] = args[i];
            }
            return target.invokeDirect(receiver, trueArgs);
        } else {
            Object[] trueArgs = new Object[args.length - 1];
            for (int i = 0; i < trueArgs.length; i++) {
                trueArgs[i] = args[i];
            }
            return target.invokeDirect(null, trueArgs);
        }
    }
}
