package com.oracle.truffle.espresso.substitutions;


import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

import java.lang.invoke.MethodHandle;

@EspressoSubstitutions
public class Target_java_lang_invoke_MethodHandle {

    @Substitution(hasReceiver = true)
    public static @Host(Object.class) StaticObject invokeExact(@Host(MethodHandle.class) StaticObjectImpl self, @Host(Object[].class) StaticObject... args) {

        StaticObjectImpl lform = (StaticObjectImpl) self.getField(self.getKlass().lookupField(Name.form, Type.LambdaForm));
        StaticObjectImpl memberName = (StaticObjectImpl) lform.getField(lform.getKlass().lookupField(Name.vmentry, Type.MemberName));
        Object target = memberName.getHiddenField("vmtarget");

        if (target instanceof Method) {
            Method method = (Method)target;

        }

        throw EspressoError.unimplemented("dood");
    }

//    @Substitution(varargs = 0)
//    public static @Host(Object.class) StaticObject linkToStatic(@Host(Object.class) StaticObject... args) {
//
//
//        throw EspressoError.unimplemented("dood");
//    }
}