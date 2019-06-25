package com.oracle.truffle.espresso.substitutions;

import java.lang.reflect.Constructor;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoSubstitutions
public class Target_sun_reflect_NativeConstructorAccessorImpl {
    @Substitution
    public static @Host(Object.class) StaticObject newInstance0(@Host(Constructor.class) StaticObject constructor, @Host(Object[].class) StaticObject args0) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        Klass klass = ((StaticObject) meta.Constructor_clazz.get(constructor)).getMirrorKlass();
        klass.safeInitialize();
        if (klass.isArray() || klass.isPrimitive() || klass.isInterface() || klass.isAbstract()) {
            throw meta.throwEx(InstantiationException.class);
        }
        Method reflectedMethod = Method.getHostReflectiveConstructorRoot(constructor);
        StaticObject instance = klass.allocateInstance();
        StaticObject parameterTypes = (StaticObject) meta.Constructor_parameterTypes.get(constructor);
        Target_sun_reflect_NativeMethodAccessorImpl.callMethodReflectively(meta, instance, args0, reflectedMethod, klass, parameterTypes);
        return instance;
    }
}
