package com.oracle.truffle.espresso.substitutions;


import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

import java.lang.invoke.MethodHandle;

@EspressoSubstitutions
public class Target_java_lang_invoke_MethodHandle {

//    @Substitution(hasReceiver = true)
//    public static @Host(Object.class) Object invokeExact(@Host(MethodHandle.class) StaticObjectImpl self, @Host(typeName = "[Ljava/lang/Object;") StaticObjectArray args) {
//        return executeInvoke(self, args);
//    }
//
//    @Substitution(hasReceiver = true)
//    public static @Host(Object.class) Object invoke(@Host(MethodHandle.class) StaticObjectImpl self, @Host(typeName = "[Ljava/lang/Object;") StaticObjectArray args) {
//
//        return executeInvoke(self, args);
//        return self.getKlass().getMeta().invokeWithArguments.invokeDirect(self, args);
//    }
//
//    @Substitution(hasReceiver = true)
//    public static @Host(Object.class) Object invokeBasic(@Host(MethodHandle.class) StaticObjectImpl self, @Host(typeName = "[Ljava/lang/Object;") StaticObjectArray args) {
//        return executeInvoke(self, args);
//    }
//
//    @Substitution
//    public static @Host(Object.class) Object linkToInterface(@Host(typeName = "[Ljava/lang/Object;") StaticObject args) {
//        return executeLinkTo(args);
//    }
//
//    @Substitution
//    public static @Host(Object.class) Object linkToStatic(@Host(typeName = "[Ljava/lang/Object;") StaticObject args) {
//        return executeLinkTo(args);
//    }
//
//    @Substitution
//    public static @Host(Object.class) Object linkToVirtual(@Host(typeName = "[Ljava/lang/Object;") StaticObject args) {
//        return executeLinkTo(args);
//    }
//
//    @Substitution
//    public static @Host(Object.class) Object linkToSpecial(@Host(typeName = "[Ljava/lang/Object;") StaticObject args) {
//        return executeLinkTo(args);
//    }

//    private static Object executeInvoke(StaticObjectImpl self, StaticObjectArray args) {
//        Object[] fullArgs;
//        Object target;
//        if (self.getKlass().getType() == Type.DirectMethodHandle) {
//            StaticObjectImpl memberName = (StaticObjectImpl)self.getField(self.getKlass().lookupField(Name.member, Type.MemberName));
//            target = memberName.getHiddenField("vmtarget");
//            fullArgs = args.unwrap();
//        } else {
//            StaticObjectImpl lform = (StaticObjectImpl) self.getField(self.getKlass().lookupField(Name.form, Type.LambdaForm));
//            StaticObjectImpl memberName = (StaticObjectImpl) lform.getField(lform.getKlass().lookupField(Name.vmentry, Type.MemberName));
//            target = memberName.getHiddenField("vmtarget");
//            fullArgs = new Object[args.length() + 1];
//            int i = 0;
//            fullArgs[i++] = self;
//            for(Object arg: (Object[])args.unwrap()) {
//                fullArgs[i++] = arg;
//            }
//        }
//
//        if (target instanceof Method) {
//            Method method = (Method)target;
//            return method.invokeDirect(self, fullArgs);
//        } else {
//            throw EspressoError.unimplemented("dood");
//        }
//    }
//
//    private static Object executeLinkTo(StaticObject _args) {
//        StaticObjectArray args = (StaticObjectArray) _args;
//        assert(args != StaticObject.NULL && args.length() > 0);
//        assert(args.get(args.length() - 1) instanceof StaticObjectImpl);
//        StaticObjectImpl memberName = args.get(args.length() - 1);
//        assert (memberName.getKlass().getType() == Type.MemberName);
//
//        Method target = (Method) memberName.getHiddenField("vmtarget");
//        if (target.hasReceiver()) {
//            StaticObject receiver = args.get(0);
//            Object[] trueArgs = new Object[args.length() - 2];
//            for (int i = 1; i < trueArgs.length; i++) {
//                trueArgs[i] = args.get(i);
//            }
//            return target.invokeDirect(receiver, trueArgs);
//        } else {
//            Object[] trueArgs = new Object[args.length() - 1];
//            for (int i = 0; i < trueArgs.length; i++) {
//                trueArgs[i] = args.get(i);
//            }
//            return target.invokeDirect(null, trueArgs);
//        }
//    }
}