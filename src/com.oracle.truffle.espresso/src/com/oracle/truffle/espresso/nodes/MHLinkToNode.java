package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;
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
        assert (getMethod().isStatic());
        Object[] args = frame.getArguments();
        return executeLinkTo(args);
    }

    private static Object executeLinkTo(Object[] args) {
        assert args.length > 0;
        StaticObjectImpl memberName = (StaticObjectImpl) args[args.length - 1];
        assert (memberName.getKlass().getType() == Symbol.Type.MemberName);

        Method target = (Method) memberName.getHiddenField("vmtarget");
        int refKind = Target_java_lang_invoke_MethodHandleNatives.getRefKind((int)memberName.getField(memberName.getKlass().getMeta().MNflags));

        if (target.hasReceiver()) {
            StaticObject receiver = (StaticObject) args[0];
            if (refKind == Target_java_lang_invoke_MethodHandleNatives.REF_invokeVirtual) {
                if (!target.hasBytecodes()) {
                    target = receiver.getKlass().lookupMethod(target.getName(), target.getRawSignature());
                }
            }
            Object[] trueArgs = new Object[args.length - 2];
            for (int i = 1; i < args.length - 1; i++) {
                trueArgs[i - 1] = args[i];
            }
            return target.invokeDirect(receiver, trueArgs);
        } else {
            Object[] trueArgs = new Object[args.length - 1];
            for (int i = 0; i < args.length - 1; i++) {
                trueArgs[i] = args[i];
            }
            return target.invokeDirect(null, trueArgs);
        }
    }
}
