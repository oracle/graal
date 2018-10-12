package com.oracle.truffle.espresso.jni;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.meta.EspressoError;

class Callback implements TruffleObject {

    private final int arity;
    private final Function function;

    public Callback(int arity, Function function) {
        this.arity = arity;
        this.function = function;
    }

    @CompilerDirectives.TruffleBoundary
    Object call(Object... args) {
        if (args.length == arity) {
            Object ret = function.call(args);
            return ret;
        } else {
            throw ArityException.raise(arity, args.length);
        }
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return CallbackMessageResolutionForeign.ACCESS;
    }

    public interface Function {
        Object call(Object... args);
    }

    private static Callback wrap(Object receiver, Method m) {
        return new Callback(m.getParameterCount(), args -> {
            try {
                return m.invoke(receiver, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        });
    }

    static Callback wrapStaticMethod(Class<?> clazz, String methodName, Class<?> parameterTypes) {
        Method m;
        try {
            m = clazz.getDeclaredMethod(methodName, parameterTypes);
            assert Modifier.isStatic(m.getModifiers());
        } catch (NoSuchMethodException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        return wrap(clazz, m);
    }

    static Callback wrapInstanceMethod(Object receiver, String methodName, Class<?> parameterTypes) {
        Method m;
        try {
            m = receiver.getClass().getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        return wrap(receiver, m);
    }
}
