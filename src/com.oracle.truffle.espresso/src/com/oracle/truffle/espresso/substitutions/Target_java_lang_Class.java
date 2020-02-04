/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.attributes.InnerClassesAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.classfile.constantpool.NameAndTypeConstant;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.descriptors.Validation;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
public final class Target_java_lang_Class {
    @Substitution
    public static @Host(Class.class) StaticObject getPrimitiveClass(
                    @Host(String.class) StaticObject name) {

        String hostName = Meta.toHostString(name);

        Meta meta = name.getKlass().getMeta();
        switch (hostName) {
            case "boolean":
                return meta._boolean.mirror();
            case "byte":
                return meta._byte.mirror();
            case "char":
                return meta._char.mirror();
            case "short":
                return meta._short.mirror();
            case "int":
                return meta._int.mirror();
            case "float":
                return meta._float.mirror();
            case "double":
                return meta._double.mirror();
            case "long":
                return meta._long.mirror();
            case "void":
                return meta._void.mirror();
            default:
                throw meta.throwExWithMessage(meta.java_lang_ClassNotFoundException, name);
        }
    }

    @TruffleBoundary
    @Substitution
    public static boolean desiredAssertionStatus0(@Host(Class.class) StaticObject clazz) {
        if (StaticObject.isNull(clazz.getMirrorKlass().getDefiningClassLoader())) {
            return EspressoOptions.EnableSystemAssertions.getValue(EspressoLanguage.getCurrentContext().getEnv().getOptions());
        }
        return EspressoOptions.EnableAssertions.getValue(EspressoLanguage.getCurrentContext().getEnv().getOptions());
    }

    // TODO(peterssen): Remove substitution, use JVM_FindClassFromCaller.
    @TruffleBoundary
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
            throw meta.throwExWithMessage(meta.java_lang_NullPointerException, name);
        }

        String hostName = Meta.toHostString(name);
        if (hostName.indexOf('/') >= 0) {
            throw meta.throwExWithMessage(meta.java_lang_ClassNotFoundException, name);
        }

        hostName = hostName.replace('.', '/');
        if (!hostName.startsWith("[")) {
            // Possible ambiguity, force "L" type: "void" -> Lvoid; "B" -> LB;
            hostName = "L" + hostName + ";";
        }

        if (!Validation.validTypeDescriptor(ByteSequence.create(hostName), false)) {
            throw meta.throwExWithMessage(meta.java_lang_ClassNotFoundException, name);
        }

        Symbol<Type> type = meta.getTypes().fromClassGetName(hostName);

        try {
            Klass klass = null;
            if (Types.isArray(type)) {
                klass = meta.resolveSymbol(type, loader);
            } else {
                klass = meta.loadKlass(type, loader);
            }

            if (klass == null) {
                throw meta.throwExWithMessage(meta.java_lang_ClassNotFoundException, name);
            }

            if (initialize) {
                klass.safeInitialize();
            }
            return klass.mirror();
        } catch (EspressoException e) {
            throw e;
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            System.err.println("Host exception happened in Class.forName: " + e);
            throw e;
        }
    }

    @Substitution(hasReceiver = true)
    public static @Host(String.class) StaticObject getName0(@Host(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        String name = klass.getType().toString();
        // Conversion from internal form.
        String externalName = MetaUtil.internalNameToJava(name, true, true);

        // Reflection relies on anonymous classes including a '/' on the name, to avoid generating
        // (invalid) fast method accessors. See
        // sun.reflect.misc.ReflectUtil#isVMAnonymousClass(Class<?>).
        if (klass.isAnonymous()) {
            // A small improvement over HotSpot here, which uses the class identity hash code.
            externalName += "/" + klass.getId(); // VM.JVM_IHashCode(self);
        }

        // Class names must be interned.
        Meta meta = klass.getMeta();
        StaticObject guestString = meta.toGuestString(externalName);
        return internString(meta, guestString);
    }

    @TruffleBoundary
    private static StaticObject internString(Meta meta, StaticObject guestString) {
        return meta.getStrings().intern(guestString);
    }

    @Substitution(hasReceiver = true)
    public static @Host(ClassLoader.class) StaticObject getClassLoader0(@Host(Class.class) StaticObject self) {
        return self.getMirrorKlass().getDefiningClassLoader();
    }

    @Substitution(hasReceiver = true)
    public static @Host(java.lang.reflect.Field[].class) StaticObject getDeclaredFields0(@Host(Class.class) StaticObject self, boolean publicOnly) {

        // TODO(peterssen): From Hostpot: 4496456 We need to filter out
        // java.lang.Throwable.backtrace.

        ArrayList<Field> collectedMethods = new ArrayList<>();
        Klass klass = self.getMirrorKlass();
        /*
         * Hotspot does class linking at this point, and JCK tests for it (out of specs). Comply by
         * doing verification, which, at this point, is the only thing left from linking we need to
         * do.
         */
        klass.verify();
        for (Field f : klass.getDeclaredFields()) {
            if (!publicOnly || f.isPublic()) {
                collectedMethods.add(f);
            }
        }
        final Field[] fields = collectedMethods.toArray(Field.EMPTY_ARRAY);

        EspressoContext context = self.getKlass().getContext();
        Meta meta = context.getMeta();

        // TODO(peterssen): Cache guest j.l.reflect.Field constructor.
        // Calling the constructor is just for validation, manually setting the fields would be
        // faster.
        Method fieldInit = meta.java_lang_reflect_Field.lookupDeclaredMethod(Name._init_, context.getSignatures().makeRaw(Type._void,
                        /* declaringClass */ Type.java_lang_Class,
                        /* name */ Type.java_lang_String,
                        /* type */ Type.java_lang_Class,
                        /* modifiers */ Type._int,
                        /* slot */ Type._int,
                        /* signature */ Type.java_lang_String,
                        /* annotations */ Type._byte_array));

        StaticObject fieldsArray = meta.java_lang_reflect_Field.allocateArray(fields.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                final Field f = fields[i];
                StaticObject instance = meta.java_lang_reflect_Field.allocateInstance();

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
        ArrayList<Method> collectedMethods = new ArrayList<>();
        Klass klass = self.getMirrorKlass();
        /*
         * Hotspot does class linking at this point, and JCK tests for it (out of specs). Comply by
         * doing verification, which, at this point, is the only thing left from linking we need to
         * do.
         */
        klass.verify();
        for (Method m : klass.getDeclaredConstructors()) {
            if (Name._init_.equals(m.getName()) && (!publicOnly || m.isPublic())) {
                collectedMethods.add(m);
            }
        }
        final Method[] constructors = collectedMethods.toArray(Method.EMPTY_ARRAY);

        EspressoContext context = self.getKlass().getContext();
        Meta meta = context.getMeta();

        // TODO(peterssen): Cache guest j.l.reflect.Constructor constructor.
        // Calling the constructor is just for validation, manually setting the fields would be
        // faster.
        Method constructorInit = meta.java_lang_reflect_Constructor.lookupDeclaredMethod(Name._init_, context.getSignatures().makeRaw(Type._void,
                        /* declaringClass */ Type.java_lang_Class,
                        /* parameterTypes */ Type.java_lang_Class_array,
                        /* checkedExceptions */ Type.java_lang_Class_array,
                        /* modifiers */ Type._int,
                        /* slot */ Type._int,
                        /* signature */ Type.java_lang_String,
                        /* annotations */ Type._byte_array,
                        /* parameterAnnotations */ Type._byte_array));

        StaticObject arr = meta.java_lang_reflect_Constructor.allocateArray(constructors.length, new IntFunction<StaticObject>() {
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
                StaticObject parameterTypes = meta.java_lang_Class.allocateArray(
                                m.getParameterCount(),
                                new IntFunction<StaticObject>() {
                                    @Override
                                    public StaticObject apply(int j) {
                                        return rawParameterKlasses[j].mirror();
                                    }
                                });

                final Klass[] rawCheckedExceptions = m.getCheckedExceptions();
                StaticObject checkedExceptions = meta.java_lang_Class.allocateArray(rawCheckedExceptions.length, new IntFunction<StaticObject>() {
                    @Override
                    public StaticObject apply(int j) {
                        return rawCheckedExceptions[j].mirror();
                    }
                });

                SignatureAttribute signatureAttribute = (SignatureAttribute) m.getAttribute(Name.Signature);
                StaticObject genericSignature = StaticObject.NULL;
                if (signatureAttribute != null) {
                    String sig = m.getConstantPool().symbolAt(signatureAttribute.getSignatureIndex(), "signature").toString();
                    genericSignature = meta.toGuestString(sig);
                }

                StaticObject instance = meta.java_lang_reflect_Constructor.allocateInstance();
                constructorInit.invokeDirect(
                                /* this */ instance,
                                /* declaringKlass */ m.getDeclaringKlass().mirror(),
                                /* parameterTypes */ parameterTypes,
                                /* checkedExceptions */ checkedExceptions,
                                /* modifiers */ m.getMethodModifiers(),
                                /* slot */ i, // TODO(peterssen): Fill method slot.
                                /* signature */ genericSignature,

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
        ArrayList<Method> collectedMethods = new ArrayList<>();
        Klass klass = self.getMirrorKlass();
        /*
         * Hotspot does class linking at this point, and JCK tests for it (out of specs). Comply by
         * doing verification, which, at this point, is the only thing left from linking we need to
         * do.
         */
        klass.verify();
        for (Method m : klass.getDeclaredMethods()) {
            if ((!publicOnly || m.isPublic()) &&
                            // Filter out <init> and <clinit> from reflection.
                            !Name._init_.equals(m.getName()) && !Name._clinit_.equals(m.getName())) {
                collectedMethods.add(m);
            }
        }
        final Method[] methods = collectedMethods.toArray(Method.EMPTY_ARRAY);

        EspressoContext context = self.getKlass().getContext();
        Meta meta = context.getMeta();

        // TODO(peterssen): Cache guest j.l.reflect.Method constructor.
        // Calling the constructor is just for validation, manually setting the fields would
        // be faster.
        Method methodInit = meta.java_lang_reflect_Method.lookupDeclaredMethod(Name._init_, context.getSignatures().makeRaw(Type._void,
                        /* declaringClass */ Type.java_lang_Class,
                        /* name */ Type.java_lang_String,
                        /* parameterTypes */ Type.java_lang_Class_array,
                        /* returnType */ Type.java_lang_Class,
                        /* checkedExceptions */ Type.java_lang_Class_array,
                        /* modifiers */ Type._int,
                        /* slot */ Type._int,
                        /* signature */ Type.java_lang_String,
                        /* annotations */ Type._byte_array,
                        /* parameterAnnotations */ Type._byte_array,
                        /* annotationDefault */ Type._byte_array));

        StaticObject arr = meta.java_lang_reflect_Method.allocateArray(methods.length, new IntFunction<StaticObject>() {
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
                StaticObject parameterTypes = meta.java_lang_Class.allocateArray(
                                m.getParameterCount(),
                                new IntFunction<StaticObject>() {
                                    @Override
                                    public StaticObject apply(int j) {
                                        return rawParameterKlasses[j].mirror();
                                    }
                                });

                final Klass[] rawCheckedExceptions = m.getCheckedExceptions();
                StaticObject checkedExceptions = meta.java_lang_Class.allocateArray(rawCheckedExceptions.length, new IntFunction<StaticObject>() {
                    @Override
                    public StaticObject apply(int j) {
                        return rawCheckedExceptions[j].mirror();
                    }
                });

                SignatureAttribute signatureAttribute = (SignatureAttribute) m.getAttribute(Name.Signature);
                StaticObject genericSignature = StaticObject.NULL;
                if (signatureAttribute != null) {
                    String sig = m.getConstantPool().symbolAt(signatureAttribute.getSignatureIndex(), "signature").toString();
                    genericSignature = meta.toGuestString(sig);
                }

                StaticObject instance = meta.java_lang_reflect_Method.allocateInstance();

                methodInit.invokeDirect(
                                /* this */ instance,
                                /* declaringClass */ m.getDeclaringKlass().mirror(),
                                /* name */ context.getStrings().intern(m.getName()),
                                /* parameterTypes */ parameterTypes,
                                /* returnType */ m.resolveReturnKlass().mirror(),
                                /* checkedExceptions */ checkedExceptions,
                                /* modifiers */ m.getMethodModifiers(),
                                /* slot */ i, // TODO(peterssen): Fill method slot.
                                /* signature */ genericSignature,

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
    public static @Host(Class[].class) StaticObject getInterfaces0(@Host(Class.class) StaticObject self) {
        final Klass[] superInterfaces = self.getMirrorKlass().getInterfaces();

        Meta meta = self.getKlass().getMeta();
        StaticObject instance = meta.java_lang_Class.allocateArray(superInterfaces.length, new IntFunction<StaticObject>() {
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
        return self.getMirrorKlass().getClassModifiers();
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
    public static @Host(Object[].class) StaticObject getEnclosingMethod0(@Host(Class.class) StaticObject self) {

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
            StaticObject arr = meta.java_lang_Object.allocateArray(3);
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

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static @Host(Class.class) StaticObject getDeclaringClass0(@Host(Class.class) StaticObject self) {
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
            if (found) {
                break;
            }
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
    public static @Host(ProtectionDomain.class) StaticObject getProtectionDomain0(@Host(Class.class) StaticObject self) {
        StaticObject pd = (StaticObject) self.getHiddenField(self.getKlass().getMeta().HIDDEN_PROTECTION_DOMAIN);
        // The protection domain is not always set e.g. bootstrap (classloader) classes.
        return pd == null ? StaticObject.NULL : pd;
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
    public static @Host(byte[].class) StaticObject getRawTypeAnnotations(@Host(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass instanceof ObjectKlass) {
            Attribute annotations = ((ObjectKlass) klass).getAttribute(Name.RuntimeVisibleTypeAnnotations);
            if (annotations != null) {
                return StaticObject.wrap(annotations.getData());
            }
        }
        return StaticObject.NULL;
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static @Host(typeName = "Lsun/reflect/ConstantPool;") StaticObject getConstantPool(@Host(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass.isArray() || klass.isPrimitive()) {
            // No constant pool for arrays and primitives.
            return StaticObject.NULL;
        }
        Meta meta = self.getKlass().getMeta();
        StaticObject cp = new StaticObject(meta.sun_reflect_ConstantPool);
        cp.setField(meta.sun_reflect_ConstantPool_constantPoolOop, self);
        return cp;
    }

    @Substitution(hasReceiver = true)
    public static @Host(String.class) StaticObject getGenericSignature0(@Host(Class.class) StaticObject self) {
        if (self.getMirrorKlass() instanceof ObjectKlass) {
            ObjectKlass klass = (ObjectKlass) self.getMirrorKlass();
            SignatureAttribute signature = (SignatureAttribute) klass.getAttribute(Name.Signature);
            if (signature != null) {
                String sig = klass.getConstantPool().symbolAt(signature.getSignatureIndex(), "signature").toString();
                return klass.getMeta().toGuestString(sig);
            }
        }
        return StaticObject.NULL;
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static @Host(Class[].class) StaticObject getDeclaredClasses0(@Host(Class.class) StaticObject self) {
        Meta meta = self.getKlass().getMeta();
        Klass klass = self.getMirrorKlass();
        if (klass.isPrimitive() || klass.isArray()) {
            return meta.java_lang_Class.allocateArray(0);
        }
        ObjectKlass instanceKlass = (ObjectKlass) klass;
        InnerClassesAttribute innerClasses = (InnerClassesAttribute) instanceKlass.getAttribute(InnerClassesAttribute.NAME);

        if (innerClasses == null || innerClasses.entries().isEmpty()) {
            return meta.java_lang_Class.allocateArray(0);
        }

        RuntimeConstantPool pool = instanceKlass.getConstantPool();
        List<Klass> innerKlasses = new ArrayList<>();

        for (InnerClassesAttribute.Entry entry : innerClasses.entries()) {
            if (entry.innerClassIndex != 0 && entry.outerClassIndex != 0) {
                // Check to see if the name matches the class we're looking for
                // before attempting to find the class.
                Symbol<Name> outerDescriptor = pool.classAt(entry.outerClassIndex).getName(pool);

                // Check decriptors/names before resolving.
                if (outerDescriptor.equals(instanceKlass.getName())) {
                    Klass outerKlass = pool.resolvedKlassAt(instanceKlass, entry.outerClassIndex);
                    if (outerKlass == instanceKlass) {
                        Klass innerKlass = pool.resolvedKlassAt(instanceKlass, entry.innerClassIndex);
                        // HotSpot:
                        // Throws an exception if outer klass has not declared k as
                        // an inner klass
                        // Reflection::check_for_inner_class(k, inner_klass, true, CHECK_NULL);
                        // TODO(peterssen): The check in HotSpot is redundant.
                        innerKlasses.add(innerKlass);
                    }
                }
            }
        }

        return meta.java_lang_Class.allocateArray(innerKlasses.size(), new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int index) {
                return innerKlasses.get(index).mirror();
            }
        });
    }

    @Substitution(hasReceiver = true)
    public static @Host(Object[].class) StaticObject getSigners(@Host(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass.isPrimitive()) {
            return StaticObject.NULL;
        }
        Meta meta = self.getKlass().getMeta();
        StaticObject signersArray = (StaticObject) self.getHiddenField(meta.HIDDEN_SIGNERS);
        if (signersArray == null || StaticObject.isNull(signersArray)) {
            return StaticObject.NULL;
        }
        return signersArray.copy();
    }

    @Substitution(hasReceiver = true)
    public static void setSigners(@Host(Class.class) StaticObject self, @Host(Object[].class) StaticObject signers) {
        Klass klass = self.getMirrorKlass();
        if (!klass.isPrimitive() && !klass.isArray()) {
            Meta meta = self.getKlass().getMeta();
            self.setHiddenField(meta.HIDDEN_SIGNERS, signers);
        }
    }

}
