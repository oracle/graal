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
        this.target = (Method)memberName.getHiddenField("vmtarget");
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
            dst[i+start] = src[i + from];
        }
    }


//    @ExplodeLoop
//    private static void copyOfRange(Object[] src, int from, Object[] dst, int start, final int length) {
//        assert (src.length >= from + length && dst.length >= start + length);
//        for (int i = 0; i < length; ++i) {
//            dst[i+start] = src[i + from];
//        }
//    }

//    private static Object executeInvoke(StaticObjectImpl self, Object[] args) {
//        Object[] fullArgs;
//        Object target;
//        Meta meta = self.getKlass().getMeta();
//        StaticObjectImpl memberName;
//        if (meta.DirectMethodHandle.isAssignableFrom(self.getKlass())) {
//            memberName = (StaticObjectImpl) self.getField(meta.DMHmember);
//            target = memberName.getHiddenField("vmtarget");
//            fullArgs = args;
//        } else {
//            StaticObjectImpl lform = (StaticObjectImpl) self.getField(meta.form);
//            memberName = (StaticObjectImpl) lform.getField(meta.vmentry);
//            target = memberName.getHiddenField("vmtarget");
//            int arity = (int)lform.getField(meta.arity);
//            Object[] lambdaArgs = new Object[arity];
//            int i = 0;
//            lambdaArgs[i++] = self;
//            for (Object arg : args) {
//                lambdaArgs[i++] = arg;
//            }
//            fullArgs = lambdaArgs;
//            return meta.interpretWithArguments.invokeDirect(lform, lambdaArgs);
//        }
//
//        if (target instanceof Method) {
//            Method method = (Method) target;
//            return method.invokeDirect(self, fullArgs);
//        } else { // Field getter/setter
//            Klass klass = (Klass) target;
//            Field field = (Field) memberName.getHiddenField("TRUE_vmtarget");
//            int flags = (int) memberName.getField(meta.MNflags);
//            int refkind = Target_java_lang_invoke_MethodHandleNatives.getRefKind(flags);
//            switch (refkind) {
//                case Target_java_lang_invoke_MethodHandleNatives.REF_getField:
//                    assert args.length >= 1;
//                    return ((StaticObjectImpl) args[0]).getField(field);
//
//                case Target_java_lang_invoke_MethodHandleNatives.REF_getStatic:
//                    return ((StaticObjectImpl) klass.getStatics()).getField(field);
//
//                case Target_java_lang_invoke_MethodHandleNatives.REF_putField:
//                    assert args.length >= 2;
//                    ((StaticObjectImpl) args[0]).setField(field, args[1]);
//                    return StaticObject.VOID;
//
//                case Target_java_lang_invoke_MethodHandleNatives.REF_putStatic:
//                    assert args.length >= 1;
//                    ((StaticObjectImpl) klass.getStatics()).setField(field, args[0]);
//                    return StaticObject.VOID;
//
//                default:
//                    throw EspressoError.shouldNotReachHere("invalid MemberName");
//            }
//        }
//    }
}
