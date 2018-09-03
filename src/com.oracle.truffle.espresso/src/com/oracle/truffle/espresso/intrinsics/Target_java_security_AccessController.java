package com.oracle.truffle.espresso.intrinsics;

import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

@EspressoIntrinsics
public class Target_java_security_AccessController {
    @Intrinsic
    public static Object doPrivileged(@Type(PrivilegedAction.class) StaticObject action) {
        MethodInfo runMethod = action.getKlass().findDeclaredConcreteMethod("run", Utils.getContext().getSignatureDescriptors().make("()Ljava/lang/Object;"));
        Object result = runMethod.getCallTarget().call(action);
        return result;
    }

    @Intrinsic(methodName = "doPrivileged")
    public static Object doPrivileged2(@Type(PrivilegedExceptionAction.class) StaticObject action) {
        return doPrivileged(action);
    }

    @Intrinsic
    public static @Type(AccessControlContext.class) StaticObject getStackAccessControlContext() {
        return StaticObject.NULL;
    }

    @Intrinsic(methodName = "doPrivileged")
    public static Object doPrivileged3(@Type(PrivilegedExceptionAction.class) StaticObject action, @Type(AccessControlContext.class) StaticObject context) {
        return doPrivileged(action);
    }
}
