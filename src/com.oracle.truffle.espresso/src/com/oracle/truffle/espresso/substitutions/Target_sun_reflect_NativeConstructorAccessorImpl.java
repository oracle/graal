package com.oracle.truffle.espresso.substitutions;

import java.lang.reflect.Constructor;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

@EspressoSubstitutions
public class Target_sun_reflect_NativeConstructorAccessorImpl {
    @Substitution
    public static @Host(Object.class) StaticObject newInstance0(@Host(Constructor.class) StaticObject constructor, @Host(Object[].class) StaticObject args0) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        Klass klass = ((StaticObjectClass) meta.Constructor_clazz.get(constructor)).getMirrorKlass();
        klass.initialize();
        if (klass.isArray() || klass.isPrimitive() || klass.isInterface() || klass.isAbstract()) {
            throw meta.throwEx(InstantiationException.class);
        }
        StaticObject curMethod = constructor;

        Method reflectedMethod = null;
        while (reflectedMethod == null) {
            reflectedMethod = (Method) ((StaticObjectImpl) curMethod).getHiddenField(Target_java_lang_Class.HIDDEN_METHOD_KEY);
            if (reflectedMethod == null) {
                curMethod = (StaticObject) meta.Constructor_root.get(curMethod);
            }
        }

        StaticObject instance = klass.allocateInstance();
        StaticObjectArray parameterTypes = (StaticObjectArray) meta.Constructor_parameterTypes.get(constructor);
        Target_sun_reflect_NativeMethodAccessorImpl.callMethodReflectively(meta, instance, args0, reflectedMethod, klass, parameterTypes);
        return instance;
    }
}
