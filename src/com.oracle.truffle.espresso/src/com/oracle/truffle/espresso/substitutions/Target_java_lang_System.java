package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;

@EspressoSubstitutions
public class Target_java_lang_System {
    @Substitution
    public static int identityHashCode(@Type(Object.class)StaticObject self) {
        return System.identityHashCode(MetaUtil.unwrap(self));
    }

    @Substitution
    public static void arraycopy(@Type(Object.class) StaticObject src,  int  srcPos, @Type(Object.class) StaticObject dest, int destPos, int length) {
        try {
            if (src instanceof StaticObjectArray && dest instanceof StaticObjectArray) {
                System.arraycopy(((StaticObjectArray) src).unwrap(), srcPos, ((StaticObjectArray) dest).unwrap(), destPos, length);
            } else {
                assert src.getClass().isArray();
                assert dest.getClass().isArray();
                System.arraycopy(src, srcPos, dest, destPos, length);
            }
        } catch (Exception e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }
}
