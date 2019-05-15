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

package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.InnerClassesAttribute;
import com.oracle.truffle.espresso.classfile.NameAndTypeConstant;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.SignatureAttribute;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

import java.lang.reflect.Constructor;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.function.IntFunction;
import java.util.function.Predicate;

@EspressoSubstitutions
public final class Target_java_lang_Class {
    @Substitution
    public static @Host(Class.class) StaticObject getPrimitiveClass(
                    @Host(String.class) StaticObject name) {

        String hostName = MetaUtil.toInternalName(Meta.toHostString(name));
        return EspressoLanguage.getCurrentContext().getRegistries().loadKlassWithBootClassLoader(JavaKind.fromTypeString(hostName).getType()).mirror();
    }

    @Substitution(hasReceiver = true)
    public static boolean desiredAssertionStatus(@Host(Class.class) StaticObject self) {
        if (StaticObject.isNull(self.getMirrorKlass().getDefiningClassLoader())) {
            return EspressoOptions.EnableSystemAssertions.getValue(EspressoLanguage.getCurrentContext().getEnv().getOptions());
        }
        return EspressoOptions.EnableAssertions.getValue(EspressoLanguage.getCurrentContext().getEnv().getOptions());
    }

    // TODO(peterssen): Remove substitution, use JVM_FindClassFromCaller.
    @Substitution
    public static @Host(Class.class) StaticObject forName0(
                    @Host(String.class) StaticObject name,
                    boolean initialize,
                    @Host(ClassLoader.class) StaticObject loader,
                    @SuppressWarnings("unused") @Host(Class.class) StaticObject caller) {

        assert loader != null;
        EspressoContext context = EspressoLanguage.getCurrentContext();
        Meta meta = context.getMeta();
        if (StaticObject.isNull(name)) {
            throw meta.throwExWithMessage(meta.ClassNotFoundException, name);
        }

        try {
            Klass klass = context.getRegistries().loadKlass(context.getTypes().fromClassGetName(Meta.toHostString(name)), loader);

            if (klass == null) {
                throw meta.throwExWithMessage(meta.ClassNotFoundException, name);
            }

            if (initialize) {
                klass.safeInitialize();
            }
            return klass.mirror();
        } catch (EspressoException e) {
            throw e;
        } catch (Throwable e) {
            throw meta.throwExWithMessage(meta.ClassNotFoundException, name);
        }
    }

    @Substitution(hasReceiver = true)
    public static @Host(String.class) StaticObject getName0(@Host(Class.class) StaticObject self) {
        String name = self.getMirrorKlass().getType().toString();
        // Conversion from internal form.
        return EspressoLanguage.getCurrentContext().getMeta().toGuestString(MetaUtil.internalNameToJava(name, true, true));
    }

    @Substitution(hasReceiver = true)
    public static @Host(ClassLoader.class) StaticObject getClassLoader0(@Host(Class.class) StaticObject self) {
        return self.getMirrorKlass().getDefiningClassLoader();
    }

    @Substitution(hasReceiver = true)
    public static @Host(java.lang.reflect.Field[].class) StaticObject getDeclaredFields0(@Host(Class.class) StaticObject self, boolean publicOnly) {

        // TODO(peterssen): From Hostpot: 4496456 We need to filter out
        // java.lang.Throwable.backtrace.

        final Field[] fields = Arrays.stream(self.getMirrorKlass().getDeclaredFields()).filter(new Predicate<Field>() {
            @Override
            public boolean test(Field f) {
                return (!publicOnly || f.isPublic());
            }
        }).toArray(new IntFunction<Field[]>() {
            @Override
            public Field[] apply(int value) {
                return new Field[value];
            }
        });

        EspressoContext context = self.getKlass().getContext();
        Meta meta = context.getMeta();

        // TODO(peterssen): Cache guest j.l.reflect.Field constructor.
        // Calling the constructor is just for validation, manually setting the fields would be
        // faster.
        Method fieldInit = meta.Field.lookupDeclaredMethod(Name.INIT, context.getSignatures().makeRaw(Type._void,
                        /* declaringClass */ Type.Class,
                        /* name */ Type.String,
                        /* type */ Type.Class,
                        /* modifiers */ Type._int,
                        /* slot */ Type._int,
                        /* signature */ Type.String,
                        /* annotations */ Type._byte_array));

        StaticObject fieldsArray = meta.Field.allocateArray(fields.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                final Field f = fields[i];
                StaticObject instance = meta.Field.allocateInstance();

                Attribute rawRuntimeVisibleAnnotations = f.getAttribute(Name.RuntimeVisibleAnnotations);
                StaticObject runtimeVisibleAnnotations = rawRuntimeVisibleAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleAnnotations.getData())
                                : StaticObject.NULL;

                Attribute rawRuntimeVisibleTypeAnnotations = f.getAttribute(Name.RuntimeVisibleTypeAnnotations);
                StaticObject runtimeVisibleTypeAnnotations = rawRuntimeVisibleTypeAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleTypeAnnotations.getData())
                                : StaticObject.NULL;

                fieldInit.invokeDirect(
                                /* this */ instance,
                                /* declaringKlass */ f.getDeclaringKlass().mirror(),
                                /* name */ context.getStrings().intern(f.getName()),
                                /* type */ f.resolveTypeKlass().mirror(),
                                /* modifiers */ f.getModifiers(),
                                /* slot */ f.getSlot(),
                                /* signature */ meta.toGuestString(f.getGenericSignature()),
                                // FIXME(peterssen): Fill annotations bytes.
                                /* annotations */ runtimeVisibleAnnotations);
                instance.setHiddenField(meta.HIDDEN_FIELD_KEY, f);
                instance.setHiddenField(meta.HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS, runtimeVisibleTypeAnnotations);
                return instance;
            }
        });

        return fieldsArray;
    }

    @Substitution(hasReceiver = true)
    public static @Host(Constructor[].class) StaticObject getDeclaredConstructors0(@Host(Class.class) StaticObject self, boolean publicOnly) {
        final Method[] constructors = Arrays.stream(self.getMirrorKlass().getDeclaredConstructors()).filter(new Predicate<Method>() {
            @Override
            public boolean test(Method m) {
                return Name.INIT.equals(m.getName()) && (!publicOnly || m.isPublic());
            }
        }).toArray(new IntFunction<Method[]>() {
            @Override
            public Method[] apply(int value) {
                return new Method[value];
            }
        });

        EspressoContext context = self.getKlass().getContext();
        Meta meta = context.getMeta();

        // TODO(peterssen): Cache guest j.l.reflect.Constructor constructor.
        // Calling the constructor is just for validation, manually setting the fields would be
        // faster.
        Method constructorInit = meta.Constructor.lookupDeclaredMethod(Name.INIT, context.getSignatures().makeRaw(Type._void,
                        /* declaringClass */ Type.Class,
                        /* parameterTypes */ Type.Class_array,
                        /* checkedExceptions */ Type.Class_array,
                        /* modifiers */ Type._int,
                        /* slot */ Type._int,
                        /* signature */ Type.String,
                        /* annotations */ Type._byte_array,
                        /* parameterAnnotations */ Type._byte_array));

        StaticObject arr = meta.Constructor.allocateArray(constructors.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                final Method m = constructors[i];

                Attribute rawRuntimeVisibleAnnotations = m.getAttribute(Name.RuntimeVisibleAnnotations);
                StaticObject runtimeVisibleAnnotations = rawRuntimeVisibleAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleAnnotations.getData())
                                : StaticObject.NULL;

                Attribute rawRuntimeVisibleParameterAnnotations = m.getAttribute(Name.RuntimeVisibleParameterAnnotations);
                StaticObject runtimeVisibleParameterAnnotations = rawRuntimeVisibleParameterAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleParameterAnnotations.getData())
                                : StaticObject.NULL;

                Attribute rawRuntimeVisibleTypeAnnotations = m.getAttribute(Name.RuntimeVisibleTypeAnnotations);
                StaticObject runtimeVisibleTypeAnnotations = rawRuntimeVisibleTypeAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleTypeAnnotations.getData())
                                : StaticObject.NULL;

                final Klass[] rawParameterKlasses = m.resolveParameterKlasses();
                StaticObject parameterTypes = meta.Class.allocateArray(
                                m.getParameterCount(),
                                new IntFunction<StaticObject>() {
                                    @Override
                                    public StaticObject apply(int j) {
                                        return rawParameterKlasses[j].mirror();
                                    }
                                });

                final Klass[] rawCheckedExceptions = m.getCheckedExceptions();
                StaticObject checkedExceptions = meta.Class.allocateArray(rawCheckedExceptions.length, new IntFunction<StaticObject>() {
                    @Override
                    public StaticObject apply(int j) {
                        return rawCheckedExceptions[j].mirror();
                    }
                });

                StaticObject instance = meta.Constructor.allocateInstance();
                constructorInit.invokeDirect(
                                /* this */ instance,
                                /* declaringKlass */ m.getDeclaringKlass().mirror(),
                                /* parameterTypes */ parameterTypes,
                                /* checkedExceptions */ checkedExceptions,
                                /* modifiers */ m.getModifiers(),
                                /* slot */ i, // TODO(peterssen): Fill method slot.
                                /* signature */ meta.toGuestString(m.getRawSignature().toString()),

                                // FIXME(peterssen): Fill annotations bytes.
                                /* annotations */ runtimeVisibleAnnotations,
                                /* parameterAnnotations */ runtimeVisibleParameterAnnotations);

                instance.setHiddenField(meta.HIDDEN_CONSTRUCTOR_KEY, m);
                instance.setHiddenField(meta.HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS, runtimeVisibleTypeAnnotations);

                return instance;
            }
        });

        return arr;
    }

    @Substitution(hasReceiver = true)
    public static @Host(java.lang.reflect.Method[].class) StaticObject getDeclaredMethods0(@Host(Class.class) StaticObject self, boolean publicOnly) {

        final Method[] methods = Arrays.stream(self.getMirrorKlass().getDeclaredMethods()).filter(new Predicate<Method>() {
            @Override
            public boolean test(Method m) {
                return (!publicOnly || m.isPublic()) &&
                                // Filter out <init> and <clinit> from reflection.
                                !Name.INIT.equals(m.getName()) && !Name.CLINIT.equals(m.getName());
            }
        }).toArray(
                        new IntFunction<Method[]>() {
                            @Override
                            public Method[] apply(int value) {
                                return new Method[value];
                            }
                        });
        EspressoContext context = self.getKlass().getContext();
        Meta meta = context.getMeta();

        // TODO(peterssen): Cache guest j.l.reflect.Method constructor.
        // Calling the constructor is just for validation, manually setting the fields would
        // be faster.
        Method methodInit = meta.Method.lookupDeclaredMethod(Name.INIT, context.getSignatures().makeRaw(Type._void,
                        /* declaringClass */ Type.Class,
                        /* name */ Type.String,
                        /* parameterTypes */ Type.Class_array,
                        /* returnType */ Type.Class,
                        /* checkedExceptions */ Type.Class_array,
                        /* modifiers */ Type._int,
                        /* slot */ Type._int,
                        /* signature */ Type.String,
                        /* annotations */ Type._byte_array,
                        /* parameterAnnotations */ Type._byte_array,
                        /* annotationDefault */ Type._byte_array));

        StaticObject arr = meta.Method.allocateArray(methods.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                Method m = methods[i];
                Attribute rawRuntimeVisibleAnnotations = m.getAttribute(Name.RuntimeVisibleAnnotations);
                StaticObject runtimeVisibleAnnotations = rawRuntimeVisibleAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleAnnotations.getData())
                                : StaticObject.NULL;

                Attribute rawRuntimeVisibleParameterAnnotations = m.getAttribute(Name.RuntimeVisibleParameterAnnotations);
                StaticObject runtimeVisibleParameterAnnotations = rawRuntimeVisibleParameterAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleParameterAnnotations.getData())
                                : StaticObject.NULL;

                Attribute rawRuntimeVisibleTypeAnnotations = m.getAttribute(Name.RuntimeVisibleTypeAnnotations);
                StaticObject runtimeVisibleTypeAnnotations = rawRuntimeVisibleTypeAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleTypeAnnotations.getData())
                                : StaticObject.NULL;

                Attribute rawAnnotationDefault = m.getAttribute(Name.AnnotationDefault);
                StaticObject annotationDefault = rawAnnotationDefault != null
                                ? StaticObject.wrap(rawAnnotationDefault.getData())
                                : StaticObject.NULL;
                final Klass[] rawParameterKlasses = m.resolveParameterKlasses();
                StaticObject parameterTypes = meta.Class.allocateArray(
                                m.getParameterCount(),
                                new IntFunction<StaticObject>() {
                                    @Override
                                    public StaticObject apply(int j) {
                                        return rawParameterKlasses[j].mirror();
                                    }
                                });

                final Klass[] rawCheckedExceptions = m.getCheckedExceptions();
                StaticObject checkedExceptions = meta.Class.allocateArray(rawCheckedExceptions.length, new IntFunction<StaticObject>() {
                    @Override
                    public StaticObject apply(int j) {
                        return rawCheckedExceptions[j].mirror();
                    }
                });

                StaticObject instance = meta.Method.allocateInstance();
                methodInit.invokeDirect(
                                /* this */ instance,
                                /* declaringClass */ m.getDeclaringKlass().mirror(),
                                /* name */ context.getStrings().intern(m.getName()),
                                /* parameterTypes */ parameterTypes,
                                /* returnType */ m.resolveReturnKlass().mirror(),
                                /* checkedExceptions */ checkedExceptions,
                                /* modifiers */ m.getModifiers(),
                                /* slot */ i, // TODO(peterssen): Fill method slot.
                                /* signature */ meta.toGuestString(m.getRawSignature().toString()),

                                // FIXME(peterssen): Fill annotations bytes.
                                /* annotations */ runtimeVisibleAnnotations,
                                /* parameterAnnotations */ runtimeVisibleParameterAnnotations,
                                /* annotationDefault */ annotationDefault);

                instance.setHiddenField(meta.HIDDEN_METHOD_KEY, m);
                instance.setHiddenField(meta.HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS, runtimeVisibleTypeAnnotations);
                return instance;
            }
        });

        return arr;

    }

    @Substitution(hasReceiver = true)
    public static @Host(Class[].class) StaticObject getInterfaces0(StaticObject self) {
        final Klass[] superInterfaces = self.getMirrorKlass().getInterfaces();

        Meta meta = self.getKlass().getMeta();
        StaticObject instance = meta.Class.allocateArray(superInterfaces.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                return superInterfaces[i].mirror();
            }
        });

        return instance;
    }

    @Substitution(hasReceiver = true)
    public static boolean isPrimitive(@Host(Class.class) StaticObject self) {
        return self.getMirrorKlass().isPrimitive();
    }

    @Substitution(hasReceiver = true)
    public static boolean isInterface(@Host(Class.class) StaticObject self) {
        return self.getMirrorKlass().isInterface();
    }

    @Substitution(hasReceiver = true)
    public static int getModifiers(@Host(Class.class) StaticObject self) {
        return self.getMirrorKlass().getModifiers();
    }

    @Substitution(hasReceiver = true)
    public static @Host(Class.class) StaticObject getSuperclass(@Host(Class.class) StaticObject self) {
        if (self.getMirrorKlass().isInterface()) {
            return StaticObject.NULL;
        }
        Klass superclass = self.getMirrorKlass().getSuperKlass();
        if (superclass == null) {
            return StaticObject.NULL;
        }
        return superclass.mirror();
    }

    @Substitution(hasReceiver = true)
    public static boolean isArray(@Host(Class.class) StaticObject self) {
        return self.getMirrorKlass().isArray();
    }

    @Substitution(hasReceiver = true)
    public static @Host(Class.class) StaticObject getComponentType(@Host(Class.class) StaticObject self) {
        Klass comp = self.getMirrorKlass().getComponentType();
        if (comp == null) {
            return StaticObject.NULL;
        }
        return comp.mirror();
    }

    @Substitution(hasReceiver = true)
    public static @Host(Object[].class) StaticObject getEnclosingMethod0(StaticObject self) {

        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        InterpreterToVM vm = meta.getInterpreterToVM();
        if (self.getMirrorKlass() instanceof ObjectKlass) {
            ObjectKlass klass = (ObjectKlass) self.getMirrorKlass();
            EnclosingMethodAttribute enclosingMethodAttr = klass.getEnclosingMethod();
            if (enclosingMethodAttr == null) {
                return StaticObject.NULL;
            }
            if (enclosingMethodAttr.getMethodIndex() == 0) {
                return StaticObject.NULL;
            }
            StaticObject arr = meta.Object.allocateArray(3);
            RuntimeConstantPool pool = klass.getConstantPool();
            Klass enclosingKlass = pool.resolvedKlassAt(klass, enclosingMethodAttr.getClassIndex());

            vm.setArrayObject(enclosingKlass.mirror(), 0, arr);

            NameAndTypeConstant nmt = pool.nameAndTypeAt(enclosingMethodAttr.getMethodIndex());
            StaticObject name = meta.toGuestString(nmt.getName(pool));
            StaticObject desc = meta.toGuestString(nmt.getDescriptor(pool));

            vm.setArrayObject(name, 1, arr);
            vm.setArrayObject(desc, 2, arr);

            return arr;
        }
        return StaticObject.NULL;
    }

    @Substitution(hasReceiver = true)
    public static @Host(Class.class) StaticObject getDeclaringClass0(StaticObject self) {
        // Primitives and arrays are not "enclosed".
        if (!(self.getMirrorKlass() instanceof ObjectKlass)) {
            return StaticObject.NULL;
        }
        ObjectKlass k = (ObjectKlass) self.getMirrorKlass();
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
        InnerClassesAttribute innerClasses = (InnerClassesAttribute) klass.getAttribute(InnerClassesAttribute.NAME);
        if (innerClasses == null) {
            return null;
        }

        RuntimeConstantPool pool = klass.getConstantPool();

        boolean found = false;
        Klass outerKlass = null;

        for (InnerClassesAttribute.Entry entry : innerClasses.entries()) {
            if (entry.innerClassIndex != 0) {
                Symbol<Name> innerDescriptor = pool.classAt(entry.innerClassIndex).getName(pool);

                // Check decriptors/names before resolving.
                if (innerDescriptor.equals(klass.getName())) {
                    Klass innerKlass = pool.resolvedKlassAt(klass, entry.innerClassIndex);
                    found = (innerKlass == klass);
                    if (found && entry.outerClassIndex != 0) {
                        outerKlass = pool.resolvedKlassAt(klass, entry.outerClassIndex);
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
    @Substitution(hasReceiver = true)
    public static boolean isInstance(@Host(Class.class) StaticObject self, @Host(Object.class) StaticObject obj) {
        return InterpreterToVM.instanceOf(obj, self.getMirrorKlass());
    }

    @Substitution
    public static void registerNatives() {
        /* nop */
    }

    @Substitution(hasReceiver = true)
    public static @Host(ProtectionDomain.class) StaticObject getProtectionDomain0(@SuppressWarnings("unused") StaticObject self) {
        return StaticObject.NULL;
    }

    @Substitution(hasReceiver = true)
    public static @Host(byte[].class) StaticObject getRawAnnotations(@Host(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass instanceof ObjectKlass) {
            Attribute annotations = ((ObjectKlass) klass).getAttribute(Name.RuntimeVisibleAnnotations);
            if (annotations != null) {
                return StaticObject.wrap(annotations.getData());
            }
        }
        return StaticObject.NULL;
    }

    @Substitution(hasReceiver = true)
    public static @Host(sun.reflect.ConstantPool.class) StaticObject getConstantPool(@Host(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass instanceof ObjectKlass) {
            Klass cpKlass = klass.getMeta().knownKlass(sun.reflect.ConstantPool.class);
            Field constantPoolOop = cpKlass.lookupDeclaredField(Name.constantPoolOop, Type.Object);
            StaticObject cp = cpKlass.allocateInstance();
            constantPoolOop.set(cp, self);
            return cp;
        }
        // No constant pool for arrays and primitives.
        return StaticObject.NULL;
    }

    @Substitution(hasReceiver = true)
    public static @Host(String.class) StaticObject getGenericSignature0(@Host(Class.class) StaticObject self) {
        if (self.getMirrorKlass() instanceof ObjectKlass) {
            ObjectKlass klass = (ObjectKlass) self.getMirrorKlass();
            SignatureAttribute signature = (SignatureAttribute) klass.getAttribute(Name.Signature);
            if (signature != null) {
                String sig = klass.getConstantPool().utf8At(signature.getSignatureIndex(), "signature").toString();
                return klass.getMeta().toGuestString(sig);
            }
        }
        return StaticObject.NULL;
    }

    @Substitution(hasReceiver = true)
    public static @Host(Class[].class) StaticObject getDeclaredClasses0(@Host(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();

        if (klass.isPrimitive() || !klass.isArray()) {
            return new StaticObject(klass.getMeta().Class_Array, new StaticObject[0]);
        }

        throw EspressoError.unimplemented();
    }

    @Substitution(hasReceiver = true)
    public static @Host(Object[].class) StaticObject getSigners(StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass.isPrimitive()) {
            return StaticObject.NULL;
        }
        Meta meta = self.getKlass().getMeta();
        StaticObject signersArray = (StaticObject) self.getHiddenField(meta.HIDDEN_SIGNERS);
        if (signersArray == null|| StaticObject.isNull(signersArray)) {
            return StaticObject.NULL;
        }
        return signersArray.copy();
    }

    @Substitution(hasReceiver = true)
    public static void setSigners(StaticObject self, @Host(Object[].class) StaticObject signers) {
        Klass klass = self.getMirrorKlass();
        if (!klass.isPrimitive() && !klass.isArray()) {
            Meta meta = self.getKlass().getMeta();
            self.setHiddenField(meta.HIDDEN_SIGNERS, signers);
        }
    }

}
