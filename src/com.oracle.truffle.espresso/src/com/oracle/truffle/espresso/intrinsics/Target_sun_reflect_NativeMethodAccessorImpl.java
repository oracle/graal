package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

import java.lang.reflect.Method;

import static com.oracle.truffle.espresso.meta.Meta.meta;

@EspressoIntrinsics
public class Target_sun_reflect_NativeMethodAccessorImpl {
    @Intrinsic
    public static Object invoke0(@Type(Method.class) StaticObject method, @Type(Object.class) StaticObject receiver, @Type(Object[].class) StaticObject args) {
        StaticObject curMethod = method;
        MethodInfo target = null;
        while (target == null) {
            target = (MethodInfo) ((StaticObjectImpl) curMethod).getHiddenField("$$method_info");
            if (target == null) {
                curMethod = (StaticObject) meta(curMethod).declaredField("root").get();
            }
        }
        Meta.Method m = meta(target);

        return m.invokeDirect(receiver,
                        StaticObject.isNull(args)
                                        ? new Object[0]
                                        : ((StaticObjectArray) args).unwrap());
    }
}
