package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

@EspressoSubstitutions
public final class Target_sun_reflect_NativeMethodAccessorImpl {
    @Substitution
    public static Object invoke0(@Host(java.lang.reflect.Method.class) StaticObject method, @Host(Object.class) StaticObject receiver, @Host(Object[].class) StaticObject args) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        StaticObject curMethod = method;
        Method target = null;
        while (target == null) {
            target = (Method) ((StaticObjectImpl) curMethod).getHiddenField("$$method_info");
            if (target == null) {
                curMethod = (StaticObject) meta.Method_root.get(curMethod);
            }
        }
        Method m = target;

        return m.invokeDirect(receiver,
                        StaticObject.isNull(args)
                                        ? new Object[0]
                                        : ((StaticObjectArray) args).unwrap());
    }
}
