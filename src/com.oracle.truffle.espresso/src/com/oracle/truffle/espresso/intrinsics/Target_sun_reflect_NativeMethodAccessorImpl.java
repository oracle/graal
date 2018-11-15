package com.oracle.truffle.espresso.intrinsics;

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.lang.reflect.Method;

import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

@EspressoIntrinsics
public class Target_sun_reflect_NativeMethodAccessorImpl {
    @Intrinsic
    public static Object invoke0(@Type(Method.class) StaticObject method, Object receiver, @Type(Object[].class) StaticObject args) {
        MethodInfo target = null;
        while (target == null) {
            target = (MethodInfo) ((StaticObjectImpl) method).getHiddenField("$$method_info");
            if (target == null) {
                method = (StaticObject) meta(method).declaredField("root").get();
            }
        }
        Meta.Method m = meta(target);
        return m.invokeDirect(receiver, ((StaticObjectArray) args).getWrapped());
    }
}
