package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives;

public class MHInvokeBasicNode extends EspressoBaseNode {

    public MHInvokeBasicNode(Method method) {
        super(method);
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        Meta meta = getMeta();
        StaticObjectImpl mh = (StaticObjectImpl) frame.getArguments()[0];
        if (meta.DirectMethodHandle.isAssignableFrom(mh.getKlass())) {
            StaticObjectImpl mname = (StaticObjectImpl) mh.getField(meta.DMHmember);
            Object fieldTarget = mname.getHiddenField("TRUE_vmtarget");
            if (fieldTarget != null) {
                // Do weird things with field accessors.
                return fieldAccessor(mname, (Field) fieldTarget, meta, frame.getArguments());
            }
        }
        StaticObjectImpl lform = (StaticObjectImpl) mh.getField(meta.form);
        StaticObjectImpl mname = (StaticObjectImpl) lform.getField(meta.vmentry);
        Method target = (Method) mname.getHiddenField("vmtarget");
        return target.invokeDirect(null, frame.getArguments());
    }

    // Do the field accesses by hand, as the UNSAFE field accessor is completely broken when invoked
    // from lambdas.
    private static Object fieldAccessor(StaticObjectImpl mname, Field field, Meta meta, Object[] args) {
        Klass klass = (Klass) mname.getHiddenField("vmtarget");
        int flags = (int) mname.getField(meta.MNflags);
        int refkind = Target_java_lang_invoke_MethodHandleNatives.getRefKind(flags);
        switch (refkind) {
            case Target_java_lang_invoke_MethodHandleNatives.REF_getField:
                assert args.length >= 2;
                return ((StaticObjectImpl) args[1]).getField(field);

            case Target_java_lang_invoke_MethodHandleNatives.REF_getStatic:
                return ((StaticObjectImpl) klass.getStatics()).getField(field);

            case Target_java_lang_invoke_MethodHandleNatives.REF_putField:
                assert args.length >= 3;
                ((StaticObjectImpl) args[1]).setField(field, args[2]);
                return StaticObject.VOID;

            case Target_java_lang_invoke_MethodHandleNatives.REF_putStatic:
                assert args.length >= 2;
                ((StaticObjectImpl) klass.getStatics()).setField(field, args[1]);
                return StaticObject.VOID;
            default:
                throw EspressoError.shouldNotReachHere("invalid MemberName");
        }
    }
}
