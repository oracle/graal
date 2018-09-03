package com.oracle.truffle.espresso.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.bytecode.InterpreterToVM;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;

public class Utils {

    private static Field String_value;
    private static Constructor<String> String_copylessConstructor;

    static {
        try {
            String_value = String.class.getDeclaredField("value");
            String_value.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        try {
            String_copylessConstructor = String.class.getDeclaredConstructor(char[].class, boolean.class);
            String_copylessConstructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    private static char[] readStringValue(String s) {
        String_value.setAccessible(true);
        try {
            return (char[]) String_value.get(s);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    @CompilerDirectives.TruffleBoundary
    public static StaticObject toGuestString(EspressoContext context, String hostString) {
        if (hostString == null) {
            return StaticObject.NULL;
        }
        Klass STRING_KLASS = context.getRegistries().resolve(context.getTypeDescriptors().STRING, null);
        STRING_KLASS.initialize();
        MethodInfo constructor = STRING_KLASS.findDeclaredMethod("<init>", void.class, char[].class, boolean.class);
        StaticObject result = new StaticObjectImpl(STRING_KLASS);
        // Avoid copy.
        char[] chars = readStringValue(hostString);
        constructor.getCallTarget().call(result, chars, true);
        return result;
    }

    @CompilerDirectives.TruffleBoundary
    public static String toHostString(StaticObject guestString) {
        assert guestString != null;
        if (guestString == StaticObject.NULL) {
            return null;
        }
        FieldInfo valuesField = findDeclaredField(guestString.getKlass(), "value");
        char[] chars = (char[]) getCallerNode().getVm().getFieldObject(guestString, valuesField);
        try {
            return String_copylessConstructor.newInstance(chars, true);
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static EspressoContext getContext() {
        return getCallerNode().getMethod().getDeclaringClass().getContext();
    }

    public static EspressoRootNode getCallerNode() {
        RootCallTarget callTarget = (RootCallTarget) Truffle.getRuntime().getCallerFrame().getCallTarget();
        RootNode rootNode = callTarget.getRootNode();
        if (rootNode instanceof EspressoRootNode) {
            return (EspressoRootNode) rootNode;
        }
        // Native (intrinsics) callers are not supported.
        throw EspressoError.unimplemented();
    }

    public static InterpreterToVM getVm() {
        return getCallerNode().getVm();
    }

    public static FieldInfo findDeclaredField(Klass klass, String name) {
        return Arrays.stream(klass.getDeclaredFields()).filter(f -> name.equals(f.getName())).findAny().get();
    }

    public static Object maybeNull(Object obj) {
        return (obj == null) ? StaticObject.NULL : obj;
    }
}
