package com.oracle.truffle.espresso.intrinsics;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

@EspressoIntrinsics
public class Target_java_lang_Package {

    enum PackageFunctions {
        GET_SYSTEM_PACKAGE0("getSystemPackage0", String.class);

        PackageFunctions(String name, Class<?>... parameterTypes) {
            try {
                this.method = Package.class.getDeclaredMethod(name, parameterTypes);
                this.method.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        private final Method method;

        public Method getMethod() {
            return method;
        }

        public Object invokeStatic(Object... args) {
            try {
                return getMethod().invoke(null, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Intrinsic
    public static @Type(String.class) Object getSystemPackage0(@Type(String.class) StaticObject name) {
        String result = (String) PackageFunctions.GET_SYSTEM_PACKAGE0.invokeStatic(Meta.toHost(name));
        return Utils.getContext().getMeta().toGuest(result);
    }
}
