package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives;

public class MHInvokeGenericNode extends EspressoBaseNode {
    final int argCount;

    public MHInvokeGenericNode(Method method) {
        super(method);
        this.argCount = Signatures.parameterCount(getMethod().getParsedSignature(), false);
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        StaticObjectImpl receiver;
        Object[] args;
        assert (getMethod().hasReceiver());
        receiver = (StaticObjectImpl) frame.getArguments()[0];
        args = copyOfRange(frame.getArguments(), 1, argCount + 1);
        return executeInvoke(receiver, args);
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

    private static Object executeInvoke(StaticObjectImpl self, Object[] args) {
        Object[] fullArgs;
        Object target;
        Meta meta = self.getKlass().getMeta();
        StaticObjectImpl memberName;
        if (meta.DirectMethodHandle.isAssignableFrom(self.getKlass())) {
            memberName = (StaticObjectImpl) self.getField(meta.DMHmember);
            target = memberName.getHiddenField("vmtarget");
            fullArgs = args;
        } else {
            StaticObjectImpl lform = (StaticObjectImpl) self.getField(meta.form);
            memberName = (StaticObjectImpl) lform.getField(meta.vmentry);
            target = memberName.getHiddenField("vmtarget");
            fullArgs = new Object[args.length + 1];
            int i = 0;
            fullArgs[i++] = self;
            for (Object arg : args) {
                fullArgs[i++] = arg;
            }
        }

        if (target instanceof Method) {
            Method method = (Method) target;
            return method.invokeDirect(self, fullArgs);
        } else { // Field getter/setter
            Klass klass = (Klass) target;
            Field field = (Field) memberName.getHiddenField("TRUE_vmtarget");
            int flags = (int) memberName.getField(meta.MNflags);
            int refkind = Target_java_lang_invoke_MethodHandleNatives.getRefKind(flags);
            switch (refkind) {
                case Target_java_lang_invoke_MethodHandleNatives.REF_getField:
                    assert args.length>=1;
                    return ((StaticObjectImpl)args[0]).getField(field);

                case Target_java_lang_invoke_MethodHandleNatives.REF_getStatic:
                    return ((StaticObjectImpl)klass.getStatics()).getField(field);

                case Target_java_lang_invoke_MethodHandleNatives.REF_putField:
                    assert args.length>=2;
                    ((StaticObjectImpl)args[0]).setField(field, args[1]);
                    return StaticObject.VOID;

                case Target_java_lang_invoke_MethodHandleNatives.REF_putStatic:
                    assert args.length>=1;
                    ((StaticObjectImpl)klass.getStatics()).setField(field, args[0]);
                    return StaticObject.VOID;

                default:
                    throw EspressoError.shouldNotReachHere("invalid MemberName");
            }
        }
    }
}
