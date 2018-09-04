package com.oracle.truffle.espresso.intrinsics;

import java.lang.reflect.Constructor;

import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.runtime.Utils;

import static com.oracle.truffle.espresso.meta.Meta.meta;

@EspressoIntrinsics
public class Target_sun_reflect_NativeConstructorAccessorImpl {
    @Intrinsic
    public static Object newInstance0(
                    @Type(Constructor.class) StaticObject constructor,
                    @Type(Object[].class) StaticObject parameters) {

        assert parameters == StaticObject.NULL || (((StaticObjectArray) parameters)).getWrapped().length == 0;

        StaticObjectClass clazz = (StaticObjectClass) meta(constructor).field("clazz").get();
        String className = clazz.getMirror().getName();

        StaticObject instance = Utils.getVm().newObject(Utils.getContext().getRegistries().resolve(Utils.getContext().getTypeDescriptors().make(className), null));
        MethodInfo emptyConstructor = instance.getKlass().findDeclaredConcreteMethod("<init>", Utils.getContext().getSignatureDescriptors().make("()V"));
        emptyConstructor.getCallTarget().call(instance);
        return instance;
    }
}
