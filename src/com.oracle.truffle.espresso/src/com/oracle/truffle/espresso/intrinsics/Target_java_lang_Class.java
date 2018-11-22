/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.truffle.espresso.intrinsics;

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ClassConstant;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.InnerClassesAttribute;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.AttributeInfo;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.types.TypeDescriptor;
import com.oracle.truffle.espresso.types.TypeDescriptors;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoIntrinsics
public class Target_java_lang_Class {

    public static final String HIDDEN_METHOD_KEY = "$$method_info";
    public static final String HIDDEN_FIELD_KEY = "$$field_info";

    @Intrinsic
    public static @Type(Class.class) StaticObject getPrimitiveClass(
                    @Type(String.class) StaticObject name) {

        String hostName = MetaUtil.toInternalName(Meta.toHost(name));
        return EspressoLanguage.getCurrentContext().getRegistries().resolve(TypeDescriptors.forPrimitive(JavaKind.fromTypeString(hostName)), null).mirror();
    }

    @Intrinsic(hasReceiver = true)
    public static boolean desiredAssertionStatus(Object self) {
        return false;
    }

    @Intrinsic
    public static @Type(Class.class) StaticObject forName0(
                    @Type(String.class) StaticObject name,
                    boolean initialize,
                    @Type(ClassLoader.class) Object loader,
                    @Type(Class.class) StaticObject caller) {

        assert loader != null;
        EspressoContext context = EspressoLanguage.getCurrentContext();

        String typeDesc = Meta.toHost(name);
        if (typeDesc.contains(".")) {
            // Normalize
            // Ljava/lang/InterruptedException;
            // sun.nio.cs.UTF_8
            typeDesc = TypeDescriptor.fromJavaName(typeDesc);
        }

        try {
            Klass klass = context.getRegistries().resolve(context.getTypeDescriptors().make(typeDesc), loader == StaticObject.NULL ? null : loader);
            if (initialize) {
                meta(klass).safeInitialize();
            }
            return klass.mirror();
        } catch (NoClassDefFoundError e) {
            Meta.Klass classNotFoundExceptionKlass = context.getMeta().throwableKlass(ClassNotFoundException.class);
            StaticObject ex = classNotFoundExceptionKlass.allocateInstance();
            meta(ex).method("<init>", void.class).invokeDirect();
            // TODO(peterssen): Add class name to exception message.
            throw new EspressoException(ex);
        }
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(String.class) StaticObject getName0(@Type(Class.class) StaticObjectClass self) {
        String name = self.getMirror().getName();
        // Class name is stored in internal form.
        return EspressoLanguage.getCurrentContext().getMeta().toGuest(MetaUtil.internalNameToJava(name, true, true));
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(ClassLoader.class) StaticObject getClassLoader0(@Type(Class.class) StaticObjectClass self) {
        Object cl = self.getMirror().getClassLoader();
        // Boot class loader.
        if (cl == null) {
            return StaticObject.NULL;
        }
        // Guest-defined class loader.
        return (StaticObject) cl;
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Field[].class) StaticObject getDeclaredFields0(@Type(Class.class) StaticObjectClass self, boolean publicOnly) {
        final FieldInfo[] fields = Arrays.stream(self.getMirror().getDeclaredFields()).filter(f -> (!publicOnly || f.isPublic())).toArray(FieldInfo[]::new);

        EspressoContext context = EspressoLanguage.getCurrentContext();
        Meta meta = context.getMeta();

        Meta.Klass fieldKlass = meta.knownKlass(java.lang.reflect.Field.class);

        StaticObject arr = (StaticObject) fieldKlass.allocateArray(fields.length, i -> {
            Meta.Field f = meta(fields[i]);
            StaticObjectImpl instance = (StaticObjectImpl) fieldKlass.metaNew().fields(
                            Meta.Field.set("modifiers", f.getModifiers()),
                            Meta.Field.set("type", f.getType().rawKlass().mirror()),
                            Meta.Field.set("name", context.getStrings().intern(f.getName())),
                            Meta.Field.set("clazz", f.getDeclaringClass().rawKlass().mirror()),
                            Meta.Field.set("slot", i)).getInstance();

            instance.setHiddenField(HIDDEN_FIELD_KEY, f.rawField());

            return instance;
        });

        return arr;
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Constructor[].class) StaticObject getDeclaredConstructors0(@Type(Class.class) StaticObjectClass self, boolean publicOnly) {
        final MethodInfo[] constructors = Arrays.stream(self.getMirror().getDeclaredConstructors()).filter(m -> m.getName().equals("<init>") && (!publicOnly || m.isPublic())).toArray(
                        MethodInfo[]::new);

        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        Meta.Klass constructorKlass = meta.knownKlass(Constructor.class);

        StaticObject arr = (StaticObject) constructorKlass.allocateArray(constructors.length, i -> {
            Meta.Method m = meta(constructors[i]);

            StaticObject parameterTypes = (StaticObject) meta.CLASS.allocateArray(
                            m.getParameterCount(),
                            j -> m.getParameterTypes()[j].rawKlass().mirror());

            final Klass[] rawCheckedExceptions = m.rawMethod().getCheckedExceptions();
            StaticObjectArray checkedExceptions = (StaticObjectArray) meta.CLASS.allocateArray(rawCheckedExceptions.length, j -> rawCheckedExceptions[j].mirror());

            StaticObjectImpl constructor = (StaticObjectImpl) constructorKlass.metaNew().fields(
                            Meta.Field.set("modifiers", m.getModifiers()),
                            Meta.Field.set("clazz", m.getDeclaringClass().rawKlass().mirror()),
                            Meta.Field.set("slot", i),
                            Meta.Field.set("exceptionTypes", checkedExceptions),
                            Meta.Field.set("parameterTypes", parameterTypes))
                    .getInstance();

            constructor.setHiddenField(HIDDEN_METHOD_KEY, m.rawMethod());

            return constructor;
        });

        return arr;
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Method[].class) StaticObject getDeclaredMethods0(StaticObjectClass self, boolean publicOnly) {
        final MethodInfo[] methods = Arrays.stream(self.getMirror().getDeclaredMethods()).filter(m -> !publicOnly || m.isPublic()).toArray(
                        MethodInfo[]::new);

        EspressoContext context = EspressoLanguage.getCurrentContext();
        Meta meta = context.getMeta();
        Meta.Klass methodKlass = meta.knownKlass(Method.class);

        StaticObject arr = (StaticObject) methodKlass.allocateArray(methods.length, i -> {
            Meta.Method m = meta(methods[i]);

            StaticObject parameterTypes = (StaticObject) meta.CLASS.allocateArray(
                            m.getParameterCount(),
                            j -> m.getParameterTypes()[j].rawKlass().mirror());

            final Klass[] rawCheckedExceptions = m.rawMethod().getCheckedExceptions();
            StaticObjectArray checkedExceptions = (StaticObjectArray) meta.CLASS.allocateArray(rawCheckedExceptions.length, j -> rawCheckedExceptions[j].mirror());

            StaticObjectImpl method = (StaticObjectImpl) methodKlass.metaNew().fields(
                            Meta.Field.set("modifiers", m.getModifiers()),
                            Meta.Field.set("clazz", m.getDeclaringClass().rawKlass().mirror()),
                            Meta.Field.set("slot", i),
                            Meta.Field.set("name", context.getInterpreterToVM().intern(meta.toGuest(m.getName()))),
                            Meta.Field.set("returnType", m.getReturnType().rawKlass().mirror()),
                            Meta.Field.set("exceptionTypes", checkedExceptions),
                            Meta.Field.set("parameterTypes", parameterTypes)).getInstance();

            method.setHiddenField(HIDDEN_METHOD_KEY, m.rawMethod());
            return method;
        });

        return arr;
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Class[].class) StaticObject getInterfaces0(StaticObjectClass self) {
        final Klass[] interfaces = Arrays.stream(self.getMirror().getInterfaces()).toArray(
                        Klass[]::new);
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        Meta.Klass classKlass = meta.knownKlass(Class.class);
        StaticObject arr = (StaticObject) classKlass.allocateArray(interfaces.length, i -> interfaces[i].mirror());
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
    public static int getModifiers(@Type(Class.class) StaticObjectClass self) {
        return self.getMirror().getModifiers();
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Class.class) StaticObject getSuperclass(@Type(Class.class) StaticObjectClass self) {
        if (self.getMirror().isInterface()) {
            return StaticObject.NULL;
        }
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
    public static @Type(Class.class) StaticObject getComponentType(@Type(Class.class) StaticObjectClass self) {
        Klass comp = self.getMirror().getComponentType();
        if (comp == null) {
            return StaticObject.NULL;
        }
        return comp.mirror();
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Object[].class) StaticObject getEnclosingMethod0(StaticObjectClass self) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        InterpreterToVM vm = EspressoLanguage.getCurrentContext().getInterpreterToVM();
        if (self.getMirror() instanceof ObjectKlass) {
            EnclosingMethodAttribute enclosingMethodAttr = ((ObjectKlass) self.getMirror()).getEnclosingMethod();
            if (enclosingMethodAttr == null) {
                return StaticObject.NULL;
            }
            StaticObjectArray arr = (StaticObjectArray) meta.OBJECT.allocateArray(3);

            Klass enclosingKlass = self.getMirror().getConstantPool().classAt(enclosingMethodAttr.getClassIndex()).resolve(self.getMirror().getConstantPool(), enclosingMethodAttr.getClassIndex());

            vm.setArrayObject(enclosingKlass.mirror(), 0, arr);

            if (enclosingMethodAttr.getMethodIndex() != 0) {
                MethodInfo enclosingMethod = self.getMirror().getConstantPool().methodAt(enclosingMethodAttr.getMethodIndex()).resolve(self.getMirror().getConstantPool(),
                                enclosingMethodAttr.getMethodIndex());
                vm.setArrayObject(meta.toGuest(enclosingMethod.getName()), 1, arr);
                vm.setArrayObject(meta.toGuest(enclosingMethod.getSignature().toString()), 2, arr);
            } else {
                assert vm.getArrayObject(1, arr) == StaticObject.NULL;
                assert vm.getArrayObject(2, arr) == StaticObject.NULL;
            }
        }
        return StaticObject.NULL;
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Class.class) StaticObject getDeclaringClass0(StaticObjectClass self) {
        // Primitives and arrays are not "enclosed".
        if (!(self.getMirror() instanceof ObjectKlass)) {
            return StaticObject.NULL;
        }
        ObjectKlass k = (ObjectKlass) self.getMirror();
        Klass outerKlass = computeEnclosingClass(k);
        if (outerKlass == null) {
            return StaticObject.NULL;
        }
        return outerKlass.mirror();
    }

    /**
     * Return the enclosing class; or null for: primitives, arrays, anonymous classes (declared
     * inside methods).
     */
    private static Klass computeEnclosingClass(ObjectKlass klass) {
        InnerClassesAttribute innerClasses = klass.getInnerClasses();
        if (innerClasses == null) {
            return null;
        }

        ConstantPool pool = klass.getConstantPool();

        boolean found = false;
        boolean isMember = false;
        Klass outerKlass = null;

        for (InnerClassesAttribute.Entry entry : innerClasses.entries()) {
            if (entry.innerClassIndex != 0) {
                ClassConstant innerClassConst = pool.classAt(entry.innerClassIndex);
                TypeDescriptor innerDecriptor = innerClassConst.getTypeDescriptor(pool, entry.innerClassIndex);

                // Check decriptors/names before resolving.
                if (innerDecriptor.equals(klass.getTypeDescriptor())) {
                    Klass innerKlass = innerClassConst.resolve(pool, entry.innerClassIndex);
                    found = (innerKlass == klass);
                    if (found && entry.outerClassIndex != 0) {
                        outerKlass = pool.classAt(entry.outerClassIndex).resolve(pool, entry.outerClassIndex);
                        isMember = true;
                    }
                }
            }
            if (found)
                break;
        }

        // TODO(peterssen): Follow HotSpot implementation described below.
        // Throws an exception if outer klass has not declared k as an inner klass
        // We need evidence that each klass knows about the other, or else
        // the system could allow a spoof of an inner class to gain access rights.
        return outerKlass;
    }

    /**
     * Determines if the specified {@code Object} is assignment-compatible with the object
     * represented by this {@code Class}. This method is the dynamic equivalent of the Java language
     * {@code instanceof} operator. The method returns {@code true} if the specified {@code Object}
     * argument is non-null and can be cast to the reference type represented by this {@code Class}
     * object without raising a {@code ClassCastException.} It returns {@code false} otherwise.
     *
     * <p>
     * Specifically, if this {@code Class} object represents a declared class, this method returns
     * {@code true} if the specified {@code Object} argument is an instance of the represented class
     * (or of any of its subclasses); it returns {@code false} otherwise. If this {@code Class}
     * object represents an array class, this method returns {@code true} if the specified
     * {@code Object} argument can be converted to an object of the array class by an identity
     * conversion or by a widening reference conversion; it returns {@code false} otherwise. If this
     * {@code Class} object represents an interface, this method returns {@code true} if the class
     * or any superclass of the specified {@code Object} argument implements this interface; it
     * returns {@code false} otherwise. If this {@code Class} object represents a primitive type,
     * this method returns {@code false}.
     *
     * @param obj the object to check
     * @return true if {@code obj} is an instance of this class
     *
     * @since JDK1.1
     */
    @Intrinsic(hasReceiver = true)
    public static boolean isInstance(StaticObjectClass self, Object obj) {
        return EspressoLanguage.getCurrentContext().getInterpreterToVM().instanceOf(obj, self.getMirror());
    }

    @Intrinsic
    public static void registerNatives() {
        /* nop */
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(byte[].class) Object getRawAnnotations(StaticObjectClass self) {
        Klass klass = self.getMirror();
        if (klass instanceof ObjectKlass) {
            AttributeInfo annotations = ((ObjectKlass) klass).getRuntimeVisibleAnnotations();
            if (annotations != null) {
                return annotations.getRawInfo();
            }
        }
        return StaticObject.NULL;
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(sun.reflect.ConstantPool.class) StaticObject getConstantPool(StaticObjectClass self) {
        Klass klass = self.getMirror();
        if (klass instanceof ObjectKlass) {
            Meta meta = EspressoLanguage.getCurrentContext().getMeta();
            return meta //
                            .knownKlass(sun.reflect.ConstantPool.class) //
                            .metaNew().fields(Meta.Field.set("constantPoolOop", self)) //
                            .getInstance();

        }
        return StaticObject.NULL;
    }
}
