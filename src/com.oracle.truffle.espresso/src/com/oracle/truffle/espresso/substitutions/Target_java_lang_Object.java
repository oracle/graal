package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoSubstitutions
public class Target_java_lang_Object {
    @Substitution(hasReceiver = true)
    public static int hashCode(@Type(Object.class) StaticObject self) {
        return System.identityHashCode(MetaUtil.unwrap(self));
    }

    @Substitution(hasReceiver = true)
    public static @Type(Class.class) StaticObject getClass(@Type(Object.class) StaticObject self) {
        return self.getKlass().mirror();
    }

    @Substitution(hasReceiver = true, methodName = "<init>")
    public static void init(@Type(Object.class) StaticObject self) {
        /* nop */
    }
}
