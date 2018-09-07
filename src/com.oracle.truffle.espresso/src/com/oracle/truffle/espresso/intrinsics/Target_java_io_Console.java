package com.oracle.truffle.espresso.intrinsics;

import java.io.Console;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

@EspressoIntrinsics
public class Target_java_io_Console {

    enum ConsoleFunctions {
        ISTTY("istty"),
        ENCODING("encoding"),
        ECHO("echo", boolean.class);

        ConsoleFunctions(String name, Class<?>... parameterTypes) {
            try {
                this.method = Console.class.getDeclaredMethod(name, parameterTypes);
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
    public static boolean istty() {
        return (boolean) ConsoleFunctions.ISTTY.invokeStatic();
    }

    @Intrinsic
    public static @Type(String.class) StaticObject encoding() {
        return Utils.getContext().getMeta().toGuest((String) ConsoleFunctions.ENCODING.invokeStatic());
    }

    @Intrinsic
    public static boolean echo(boolean on) {
        // throws IOException;
        return (boolean) ConsoleFunctions.ECHO.invokeStatic(on);
    }
}
