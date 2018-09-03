package com.oracle.truffle.espresso.intrinsics;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import com.oracle.truffle.espresso.bytecode.InterpreterToVM;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.runtime.Utils;
import com.oracle.truffle.espresso.types.TypeDescriptor;
import com.oracle.truffle.espresso.types.TypeDescriptors;

@EspressoIntrinsics
public class Target_java_lang_Class {
    @Intrinsic
    public static @Type(Class.class) StaticObject getPrimitiveClass(
                    @Type(String.class) StaticObject name) {



        String hostName = MetaUtil.toInternalName(Utils.toHostString(name));
        return Utils.getContext().getRegistries().resolve(TypeDescriptors.forPrimitive(JavaKind.fromTypeString(hostName)), null).mirror();
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Object[].class) StaticObject getEnclosingMethod0(Object self) {
        return StaticObject.NULL;
    }

    @Intrinsic(hasReceiver = true)
    public static boolean desiredAssertionStatus(Object self) {
        return true;
    }

    @Intrinsic
    public static @Type(Class.class) StaticObject forName0(
                    @Type(String.class) StaticObject name,
                    boolean initialize,
                    @Type(ClassLoader.class) StaticObject loader,
                    @Type(Class.class) StaticObject caller) {

        EspressoContext context = Utils.getContext();

        String typeDesc = Utils.toHostString(name);
        if (typeDesc.contains(".")) {
            // Normalize
            // Ljava/lang/InterruptedException;
            // sun.nio.cs.UTF_8
            typeDesc = TypeDescriptor.fromJavaName(typeDesc);
        }

        try {
            return context.getRegistries().resolve(context.getTypeDescriptors().make(typeDesc), null).mirror();
        } catch (NoClassDefFoundError e) {

            Klass classNotFoundExceptionKlass = context.getRegistries().resolve(context.getTypeDescriptors().make("Ljava/lang/ClassNotFoundException;"), null);

            StaticObject instance = Utils.getVm().newObject(classNotFoundExceptionKlass);
            throw new EspressoException(instance);
        }
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(String.class) StaticObject getName0(
                    @Type(Class.class) StaticObjectClass self) {
        return Utils.toGuestString(Utils.getContext(), self.getMirror().getName().toString());
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(ClassLoader.class) StaticObject getClassLoader0(@Type(Class.class) StaticObject self) {
        Object cl = self.getKlass().getClassLoader();
        if (cl == null) {
            return StaticObject.NULL;
        }
        // TODO(peterssen): Classloader defined in the guest.
        return (StaticObject) cl;
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Field[].class) StaticObject getDeclaredFields0(@Type(Class.class) StaticObjectClass self, boolean publicOnly) {
        FieldInfo[] fields = self.getMirror().getDeclaredFields();
        if (publicOnly) {
            fields = Arrays.stream(fields).filter(f -> Modifier.isPublic(f.getFlags())).toArray(FieldInfo[]::new);
        }

        EspressoContext context = Utils.getContext();
        Klass FIELD_KLASS = context.getRegistries().resolve(context.getTypeDescriptors().make("Ljava/lang/reflect/Field;"), null);

        InterpreterToVM vm = Utils.getVm();
        StaticObject arr = vm.newArray(FIELD_KLASS, fields.length);
        for (int i = 0; i < fields.length; ++i) {
            StaticObjectImpl f = (StaticObjectImpl) vm.newObject(FIELD_KLASS);

            vm.setFieldInt(fields[i].getFlags(), f, Utils.findDeclaredField(FIELD_KLASS, "modifiers"));

            vm.setFieldObject(
                            context.getRegistries().resolve(fields[i].getType(), null).mirror(),
                            f,
                            Utils.findDeclaredField(FIELD_KLASS, "type"));

            vm.setFieldObject(vm.intern(Utils.toGuestString(context, fields[i].getName())),
                            f,
                            Utils.findDeclaredField(FIELD_KLASS, "name"));

            vm.setFieldObject(fields[i].getDeclaringClass().mirror(), f, Utils.findDeclaredField(FIELD_KLASS,
                            "clazz"));

            vm.setFieldInt(i,
                            f,
                            Utils.findDeclaredField(FIELD_KLASS, "slot"));

            vm.setArrayObject(f, i, arr);
        }

        return arr;
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Constructor[].class) StaticObject getDeclaredConstructors0(@Type(Class.class) StaticObjectClass self, boolean publicOnly) {
        MethodInfo[] constructors = self.getMirror().getDeclaredConstructors();
        constructors = Arrays.stream(constructors).filter(m -> m.getName().equals("<init>")).toArray(MethodInfo[]::new);

        if (publicOnly) {
            constructors = Arrays.stream(constructors).filter(f -> Modifier.isPublic(f.getModifiers())).toArray(MethodInfo[]::new);
        }

        EspressoContext context = Utils.getContext();
        Klass CONSTRUCTOR_KLASS = context.getRegistries().resolve(
                        context.getTypeDescriptors().make("Ljava/lang/reflect/Constructor;"),
                        null);

        InterpreterToVM vm = Utils.getVm();
        StaticObject arr = vm.newArray(CONSTRUCTOR_KLASS, constructors.length);

        for (int i = 0; i < constructors.length; ++i) {
            StaticObjectImpl obj = (StaticObjectImpl) vm.newObject(CONSTRUCTOR_KLASS);

            vm.setFieldInt(constructors[i].getModifiers(), obj, Utils.findDeclaredField(CONSTRUCTOR_KLASS,
                            "modifiers"));
            vm.setFieldObject(constructors[i].getDeclaringClass().mirror(), obj,
                            Utils.findDeclaredField(CONSTRUCTOR_KLASS, "clazz"));

            vm.setFieldInt(i, obj, Utils.findDeclaredField(CONSTRUCTOR_KLASS, "slot"));

            {
                int parameterCount = constructors[i].getParameterTypes().length;
                StaticObject parameterTypes = vm.newArray(context.getRegistries().resolve(context.getTypeDescriptors().CLASS, null),
                                parameterCount);
                // Fill parameterTypes
                for (int j = 0; j < parameterCount; ++j) {
                    vm.setArrayObject(constructors[i].getParameterTypes()[j].mirror(), j, parameterTypes);
                }
                vm.setFieldObject(parameterTypes, obj, Utils.findDeclaredField(CONSTRUCTOR_KLASS, "parameterTypes"));
            }

            // TODO(peterssen): Set parameter types.

            vm.setArrayObject(obj, i, arr);
        }
        return arr;
    }

    @Intrinsic(hasReceiver = true)
    public static boolean isPrimitive(@Type(Class.class) StaticObjectClass self) {
        return self.getMirror().isPrimitive();
    }

    @Intrinsic(hasReceiver = true)
    public static boolean isInterface(@Type(Class.class) StaticObjectClass self) {
        return self.getMirror().isInterface();
    }

    @Intrinsic(hasReceiver = true)
    public static boolean isAssignableFrom(@Type(Class.class) StaticObjectClass self, @Type(Class.class) StaticObjectClass cls) {
        Klass c = cls.getMirror();
        while (c != null) {
            if (c == self.getMirror()) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    @Intrinsic(hasReceiver = true)
    public static int getModifiers(@Type(Class.class) StaticObjectClass self) {
        return self.getMirror().getModifiers();
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Class.class) StaticObject getSuperclass(@Type(Class.class) StaticObjectClass self) {
        Klass superclass = self.getMirror().getSuperclass();
        if (superclass == null) {
            return StaticObject.NULL;
        }
        return superclass.mirror();
    }

    @Intrinsic(hasReceiver = true)
    public static boolean isArray(@Type(Class.class) StaticObjectClass self) {
        return self.getMirror().isArray();
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Class.class) Object getComponentType(@Type(Class.class) StaticObjectClass self) {
        Klass comp = self.getMirror().getComponentType();
        if (comp == null) {
            return StaticObject.NULL;
        }
        return comp.mirror();
    }

    @Intrinsic
    public static void registerNatives() {
        /* nop */
    }
}
