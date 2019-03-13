package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives;

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
        assert(getMethod().isStatic());
        Object[] args = frame.getArguments();
        switch(id) {
            case Target_java_lang_invoke_MethodHandleNatives._linkToInterface:
                return getMeta().linkToInterface.invokeDirect(null, args);
            case Target_java_lang_invoke_MethodHandleNatives._linkToStatic:
                return getMeta().linkToStatic.invokeDirect(null, args);
            case Target_java_lang_invoke_MethodHandleNatives._linkToVirtual:
                return getMeta().linkToVirtual.invokeDirect(null, args);
            case Target_java_lang_invoke_MethodHandleNatives._linkToSpecial:
                return getMeta().linkToSpecial.invokeDirect(null, args);
            default:
                throw EspressoError.shouldNotReachHere();
        }
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
