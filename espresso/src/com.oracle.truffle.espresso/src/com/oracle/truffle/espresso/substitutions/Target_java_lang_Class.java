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
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.attributes.InnerClassesAttribute;
import com.oracle.truffle.espresso.classfile.attributes.PermittedSubclassesAttribute;
import com.oracle.truffle.espresso.classfile.attributes.RecordAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.classfile.constantpool.NameAndTypeConstant;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.descriptors.Validation;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoExitException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions
public final class Target_java_lang_Class {
    @Substitution
    public static void registerNatives() {
        /* nop */
    }

    @Substitution(methodName = "getPrimitiveClass")
    abstract static class GetPrimitiveClass extends SubstitutionNode {
        abstract @JavaType(Class.class) StaticObject execute(@JavaType(String.class) StaticObject name);

        @Specialization
        @JavaType(Class.class)
        StaticObject withContext(@JavaType(String.class) StaticObject name,
                        @Bind("getContext()") EspressoContext context) {
            Meta meta = context.getMeta();
            String hostName = meta.toHostString(name);
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
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassNotFoundException, name);
            }
        }
    }

    @TruffleBoundary
    @Substitution
    public static boolean desiredAssertionStatus0(@JavaType(Class.class) StaticObject clazz, @InjectMeta Meta meta) {
        if (StaticObject.isNull(clazz.getMirrorKlass().getDefiningClassLoader())) {
            return EspressoOptions.EnableSystemAssertions.getValue(meta.getContext().getEnv().getOptions());
        }
        return EspressoOptions.EnableAssertions.getValue(meta.getContext().getEnv().getOptions());
    }

    // TODO(peterssen): Remove substitution, use JVM_FindClassFromCaller.
    @Substitution
    public static @JavaType(Class.class) StaticObject forName0(
                    @JavaType(String.class) StaticObject name,
                    boolean initialize,
                    @JavaType(ClassLoader.class) StaticObject loader,
                    @JavaType(Class.class) StaticObject caller,
                    @InjectMeta Meta meta) {

        assert loader != null;
        if (StaticObject.isNull(name)) {
            throw meta.throwNullPointerException();
        }

        String hostName = meta.toHostString(name);
        if (hostName.indexOf('/') >= 0) {
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassNotFoundException, name);
        }

        hostName = hostName.replace('.', '/');
        if (!hostName.startsWith("[")) {
            // Possible ambiguity, force "L" type: "void" -> Lvoid; "B" -> LB;
            hostName = "L" + hostName + ";";
        }

        if (!Validation.validTypeDescriptor(ByteSequence.create(hostName), false)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassNotFoundException, name);
        }

        Symbol<Type> type = meta.getTypes().fromClassGetName(hostName);

        try {
            Klass klass;
            if (Types.isPrimitive(type)) {
                klass = null;
            } else {
                StaticObject protectionDomain = StaticObject.isNull(caller) ? StaticObject.NULL : caller.getMirrorKlass().protectionDomain();
                klass = meta.resolveSymbolOrNull(type, loader, protectionDomain);
            }

            if (klass == null) {
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassNotFoundException, name);
            }

            if (initialize) {
                klass.safeInitialize();
            }
            return klass.mirror();
        } catch (EspressoException | EspressoExitException e) {
            throw e;
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            meta.getContext().getLogger().log(Level.WARNING, "Host exception happened in Class.forName: {}", e);
            throw e;
        }
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getName0(@JavaType(Class.class) StaticObject self,
                    @InjectMeta Meta meta) {
        Klass klass = self.getMirrorKlass();
        // Conversion from internal form.
        String externalName = klass.getExternalName();
        // Class names must be interned.
        StaticObject guestString = meta.toGuestString(externalName);
        return internString(meta, guestString);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getSimpleBinaryName0(@JavaType(Class.class) StaticObject self,
                    @InjectMeta Meta meta) {
        Klass k = self.getMirrorKlass();
        if (k.isPrimitive() || k.isArray()) {
            return StaticObject.NULL;
        }
        ObjectKlass klass = (ObjectKlass) k;
        RuntimeConstantPool pool = klass.getConstantPool();
        InnerClassesAttribute inner = klass.getInnerClasses();
        for (InnerClassesAttribute.Entry entry : inner.entries()) {
            int innerClassIndex = entry.innerClassIndex;
            if (innerClassIndex != 0) {
                if (pool.classAt(innerClassIndex).getName(pool) == klass.getName()) {
                    if (pool.resolvedKlassAt(k, innerClassIndex) == k) {
                        if (entry.innerNameIndex != 0) {
                            Symbol<Name> innerName = pool.symbolAt(entry.innerNameIndex);
                            return meta.toGuestString(innerName);
                        } else {
                            break;
                        }
                    }
                }
            }
        }
        return StaticObject.NULL;
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject initClassName(@JavaType(Class.class) StaticObject self,
                    @InjectMeta Meta meta) {
        return getName0(self, meta);
    }

    @TruffleBoundary
    private static StaticObject internString(Meta meta, StaticObject guestString) {
        return meta.getStrings().intern(guestString);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(ClassLoader.class) StaticObject getClassLoader0(@JavaType(Class.class) StaticObject self) {
        return self.getMirrorKlass().getDefiningClassLoader();
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(java.lang.reflect.Field[].class) StaticObject getDeclaredFields0(@JavaType(Class.class) StaticObject self, boolean publicOnly,
                    @InjectMeta Meta meta) {

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

        EspressoContext context = meta.getContext();

        // TODO(peterssen): Cache guest j.l.reflect.Field constructor.
        // Calling the constructor is just for validation, manually setting the fields would be
        // faster.
        Method fieldInit;
        if (meta.getJavaVersion().java15OrLater()) {
            fieldInit = meta.java_lang_reflect_Field.lookupDeclaredMethod(Name._init_, context.getSignatures().makeRaw(Type._void,
                            /* declaringClass */ Type.java_lang_Class,
                            /* name */ Type.java_lang_String,
                            /* type */ Type.java_lang_Class,
                            /* modifiers */ Type._int,
                            /* trustedFinal */ Type._boolean,
                            /* slot */ Type._int,
                            /* signature */ Type.java_lang_String,
                            /* annotations */ Type._byte_array));
        } else {
            fieldInit = meta.java_lang_reflect_Field.lookupDeclaredMethod(Name._init_, context.getSignatures().makeRaw(Type._void,
                            /* declaringClass */ Type.java_lang_Class,
                            /* name */ Type.java_lang_String,
                            /* type */ Type.java_lang_Class,
                            /* modifiers */ Type._int,
                            /* slot */ Type._int,
                            /* signature */ Type.java_lang_String,
                            /* annotations */ Type._byte_array));
        }
        StaticObject fieldsArray = meta.java_lang_reflect_Field.allocateReferenceArray(fields.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                final Field f = fields[i];
                StaticObject instance = meta.java_lang_reflect_Field.allocateInstance();

                Attribute rawRuntimeVisibleAnnotations = f.getAttribute(Name.RuntimeVisibleAnnotations);
                StaticObject runtimeVisibleAnnotations = rawRuntimeVisibleAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleAnnotations.getData(), meta)
                                : StaticObject.NULL;

                Attribute rawRuntimeVisibleTypeAnnotations = f.getAttribute(Name.RuntimeVisibleTypeAnnotations);
                StaticObject runtimeVisibleTypeAnnotations = rawRuntimeVisibleTypeAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleTypeAnnotations.getData(), meta)
                                : StaticObject.NULL;
                if (meta.getJavaVersion().java15OrLater()) {
                    fieldInit.invokeDirect(
                                    /* this */ instance,
                                    /* declaringKlass */ f.getDeclaringKlass().mirror(),
                                    /* name */ context.getStrings().intern(f.getName()),
                                    /* type */ f.resolveTypeKlass().mirror(),
                                    /* modifiers */ f.getModifiers(),
                                    /* trustedFinal */ f.isTrustedFinal(),
                                    /* slot */ f.getSlot(),
                                    /* signature */ meta.toGuestString(f.getGenericSignature()),
                                    // FIXME(peterssen): Fill annotations bytes.
                                    /* annotations */ runtimeVisibleAnnotations);
                } else {
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
                }
                meta.HIDDEN_FIELD_KEY.setHiddenObject(instance, f);
                meta.HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.setHiddenObject(instance, runtimeVisibleTypeAnnotations);
                return instance;
            }
        });

        return fieldsArray;
    }

    // TODO(tg): inject constructor calltarget.
    @Substitution(hasReceiver = true)
    public static @JavaType(Constructor[].class) StaticObject getDeclaredConstructors0(@JavaType(Class.class) StaticObject self, boolean publicOnly,
                    @InjectMeta Meta meta) {
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

        EspressoContext context = meta.getContext();

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

        StaticObject arr = meta.java_lang_reflect_Constructor.allocateReferenceArray(constructors.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                final Method m = constructors[i];

                Attribute rawRuntimeVisibleAnnotations = m.getAttribute(Name.RuntimeVisibleAnnotations);
                StaticObject runtimeVisibleAnnotations = rawRuntimeVisibleAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleAnnotations.getData(), meta)
                                : StaticObject.NULL;

                Attribute rawRuntimeVisibleParameterAnnotations = m.getAttribute(Name.RuntimeVisibleParameterAnnotations);
                StaticObject runtimeVisibleParameterAnnotations = rawRuntimeVisibleParameterAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleParameterAnnotations.getData(), meta)
                                : StaticObject.NULL;

                Attribute rawRuntimeVisibleTypeAnnotations = m.getAttribute(Name.RuntimeVisibleTypeAnnotations);
                StaticObject runtimeVisibleTypeAnnotations = rawRuntimeVisibleTypeAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleTypeAnnotations.getData(), meta)
                                : StaticObject.NULL;

                final Klass[] rawParameterKlasses = m.resolveParameterKlasses();
                StaticObject parameterTypes = meta.java_lang_Class.allocateReferenceArray(
                                m.getParameterCount(),
                                new IntFunction<StaticObject>() {
                                    @Override
                                    public StaticObject apply(int j) {
                                        return rawParameterKlasses[j].mirror();
                                    }
                                });

                final Klass[] rawCheckedExceptions = m.getCheckedExceptions();
                StaticObject checkedExceptions = meta.java_lang_Class.allocateReferenceArray(rawCheckedExceptions.length, new IntFunction<StaticObject>() {
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

                meta.HIDDEN_CONSTRUCTOR_KEY.setHiddenObject(instance, m);
                meta.HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.setHiddenObject(instance, runtimeVisibleTypeAnnotations);

                return instance;
            }
        });

        return arr;
    }

    // TODO(tg): inject constructor calltarget.
    @Substitution(hasReceiver = true)
    public static @JavaType(java.lang.reflect.Method[].class) StaticObject getDeclaredMethods0(@JavaType(Class.class) StaticObject self, boolean publicOnly,
                    @InjectMeta Meta meta) {
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

        return meta.java_lang_reflect_Method.allocateReferenceArray(methods.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                return methods[i].makeMirror();
            }
        });

    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class[].class) StaticObject getInterfaces0(@JavaType(Class.class) StaticObject self,
                    @InjectMeta Meta meta) {
        final Klass[] superInterfaces = self.getMirrorKlass().getInterfaces();

        StaticObject instance = meta.java_lang_Class.allocateReferenceArray(superInterfaces.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                return superInterfaces[i].mirror();
            }
        });

        return instance;
    }

    @Substitution(hasReceiver = true)
    public static boolean isPrimitive(@JavaType(Class.class) StaticObject self) {
        return self.getMirrorKlass().isPrimitive();
    }

    @Substitution(hasReceiver = true)
    public static boolean isInterface(@JavaType(Class.class) StaticObject self) {
        return self.getMirrorKlass().isInterface();
    }

    @Substitution(hasReceiver = true)
    public static int getModifiers(@JavaType(Class.class) StaticObject self) {
        return self.getMirrorKlass().getClassModifiers();
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class.class) StaticObject getSuperclass(@JavaType(Class.class) StaticObject self) {
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
    public static boolean isArray(@JavaType(Class.class) StaticObject self) {
        return self.getMirrorKlass().isArray();
    }

    @Substitution(hasReceiver = true)
    public static boolean isHidden(@JavaType(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass instanceof ObjectKlass) {
            return ((ObjectKlass) klass).isHidden();
        }
        return false;
    }

    @Substitution(hasReceiver = true)
    public static boolean isRecord(@JavaType(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass instanceof ObjectKlass) {
            return ((ObjectKlass) klass).isRecord();
        }
        return false;
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(internalName = "[Ljava/lang/reflect/RecordComponent;") StaticObject getRecordComponents0(@JavaType(Class.class) StaticObject self,
                    @InjectMeta Meta meta) {
        Klass k = self.getMirrorKlass();
        if (!(k instanceof ObjectKlass)) {
            return StaticObject.NULL;
        }
        ObjectKlass klass = (ObjectKlass) k;
        RecordAttribute record = (RecordAttribute) klass.getAttribute(RecordAttribute.NAME);
        if (record == null) {
            return StaticObject.NULL;
        }
        RecordAttribute.RecordComponentInfo[] components = record.getComponents();
        return meta.java_lang_reflect_RecordComponent.allocateReferenceArray(components.length, (i) -> components[i].toGuestComponent(meta, klass));
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(Class[].class) StaticObject getPermittedSubclasses0(@JavaType(Class.class) StaticObject self,
                    @InjectMeta Meta meta) {
        Klass k = self.getMirrorKlass();
        if (!(k instanceof ObjectKlass)) {
            return StaticObject.NULL;
        }
        ObjectKlass klass = (ObjectKlass) k;
        if (!klass.isSealed()) {
            return StaticObject.NULL;
        }
        char[] classes = ((PermittedSubclassesAttribute) klass.getAttribute(PermittedSubclassesAttribute.NAME)).getClasses();
        StaticObject[] permittedSubclasses = new StaticObject[classes.length];
        RuntimeConstantPool pool = klass.getConstantPool();
        int nClasses = 0;
        for (int index : classes) {
            Klass permitted;
            try {
                permitted = pool.resolvedKlassAt(klass, index);
            } catch (EspressoException e) {
                /* Suppress and continue */
                continue;
            }
            if (permitted instanceof ObjectKlass) {
                permittedSubclasses[nClasses++] = permitted.mirror();
            }
        }
        if (nClasses == permittedSubclasses.length) {
            return StaticObject.createArray(meta.java_lang_Class_array, permittedSubclasses);
        }
        return meta.java_lang_Class.allocateReferenceArray(nClasses, (i) -> permittedSubclasses[i]);
    }

    @Substitution(hasReceiver = true, versionFilter = VersionFilter.Java8OrEarlier.class)
    public static @JavaType(Class.class) StaticObject getComponentType(@JavaType(Class.class) StaticObject self) {
        if (self.getMirrorKlass().isArray()) {
            Klass componentType = ((ArrayKlass) self.getMirrorKlass()).getComponentType();
            return componentType.mirror();
        }
        return StaticObject.NULL;
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Object[].class) StaticObject getEnclosingMethod0(@JavaType(Class.class) StaticObject self, @InjectMeta Meta meta) {
        InterpreterToVM vm = meta.getInterpreterToVM();
        if (self.getMirrorKlass() instanceof ObjectKlass) {
            ObjectKlass klass = (ObjectKlass) self.getMirrorKlass();
            EnclosingMethodAttribute enclosingMethodAttr = klass.getEnclosingMethod();
            if (enclosingMethodAttr == null) {
                return StaticObject.NULL;
            }
            int classIndex = enclosingMethodAttr.getClassIndex();
            if (classIndex == 0) {
                return StaticObject.NULL;
            }
            StaticObject arr = meta.java_lang_Object.allocateReferenceArray(3);
            RuntimeConstantPool pool = klass.getConstantPool();
            Klass enclosingKlass = pool.resolvedKlassAt(klass, classIndex);

            vm.setArrayObject(enclosingKlass.mirror(), 0, arr);

            int methodIndex = enclosingMethodAttr.getMethodIndex();
            if (methodIndex != 0) {
                NameAndTypeConstant nmt = pool.nameAndTypeAt(methodIndex);
                StaticObject name = meta.toGuestString(nmt.getName(pool));
                StaticObject desc = meta.toGuestString(nmt.getDescriptor(pool));

                vm.setArrayObject(name, 1, arr);
                vm.setArrayObject(desc, 2, arr);
            }

            return arr;
        }
        return StaticObject.NULL;
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class.class) StaticObject getDeclaringClass0(@JavaType(Class.class) StaticObject self) {
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
    public static boolean isInstance(@JavaType(Class.class) StaticObject self, @JavaType(Object.class) StaticObject obj) {
        return InterpreterToVM.instanceOf(obj, self.getMirrorKlass());
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(ProtectionDomain.class) StaticObject getProtectionDomain0(@JavaType(Class.class) StaticObject self,
                    @InjectMeta Meta meta) {
        StaticObject pd = (StaticObject) meta.HIDDEN_PROTECTION_DOMAIN.getHiddenObject(self);
        // The protection domain is not always set e.g. bootstrap (classloader) classes.
        return pd == null ? StaticObject.NULL : pd;
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class.class) StaticObject getNestHost0(@JavaType(Class.class) StaticObject self) {
        return VM.JVM_GetNestHost(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class[].class) StaticObject getNestMembers0(@JavaType(Class.class) StaticObject self,
                    @InjectMeta Meta meta) {
        return meta.getVM().JVM_GetNestMembers(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(byte[].class) StaticObject getRawAnnotations(@JavaType(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass instanceof ObjectKlass) {
            Attribute annotations = ((ObjectKlass) klass).getAttribute(Name.RuntimeVisibleAnnotations);
            if (annotations != null) {
                Meta meta = klass.getMeta();
                return StaticObject.wrap(annotations.getData(), meta);
            }
        }
        return StaticObject.NULL;
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(byte[].class) StaticObject getRawTypeAnnotations(@JavaType(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass instanceof ObjectKlass) {
            Attribute annotations = ((ObjectKlass) klass).getAttribute(Name.RuntimeVisibleTypeAnnotations);
            if (annotations != null) {
                Meta meta = klass.getMeta();
                return StaticObject.wrap(annotations.getData(), meta);
            }
        }
        return StaticObject.NULL;
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "Lsun/reflect/ConstantPool;") StaticObject getConstantPool(@JavaType(Class.class) StaticObject self,
                    @InjectMeta Meta meta) {
        Klass klass = self.getMirrorKlass();
        if (klass.isArray() || klass.isPrimitive()) {
            // No constant pool for arrays and primitives.
            return StaticObject.NULL;
        }
        StaticObject cp = InterpreterToVM.newObject(meta.sun_reflect_ConstantPool, false);
        meta.sun_reflect_ConstantPool_constantPoolOop.setObject(cp, self);
        return cp;
    }

    @Substitution(hasReceiver = true, methodName = "getConstantPool")
    public static @JavaType(internalName = "Ljdk/internal/reflect/ConstantPool;") StaticObject getConstantPool11(@JavaType(Class.class) StaticObject self,
                    @InjectMeta Meta meta) {
        return getConstantPool(self, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getGenericSignature0(@JavaType(Class.class) StaticObject self,
                    @InjectMeta Meta meta) {
        if (self.getMirrorKlass() instanceof ObjectKlass) {
            ObjectKlass klass = (ObjectKlass) self.getMirrorKlass();
            SignatureAttribute signature = (SignatureAttribute) klass.getAttribute(Name.Signature);
            if (signature != null) {
                String sig = klass.getConstantPool().symbolAt(signature.getSignatureIndex(), "signature").toString();
                return meta.toGuestString(sig);
            }
        }
        return StaticObject.NULL;
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class[].class) StaticObject getDeclaredClasses0(@JavaType(Class.class) StaticObject self,
                    @InjectMeta Meta meta) {
        Klass klass = self.getMirrorKlass();
        if (klass.isPrimitive() || klass.isArray()) {
            return meta.java_lang_Class.allocateReferenceArray(0);
        }
        ObjectKlass instanceKlass = (ObjectKlass) klass;
        InnerClassesAttribute innerClasses = (InnerClassesAttribute) instanceKlass.getAttribute(InnerClassesAttribute.NAME);

        if (innerClasses == null || innerClasses.entries().length == 0) {
            return meta.java_lang_Class.allocateReferenceArray(0);
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

        return meta.java_lang_Class.allocateReferenceArray(innerKlasses.size(), new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int index) {
                return innerKlasses.get(index).mirror();
            }
        });
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Object[].class) StaticObject getSigners(@JavaType(Class.class) StaticObject self,
                    @InjectMeta Meta meta) {
        Klass klass = self.getMirrorKlass();
        if (klass.isPrimitive()) {
            return StaticObject.NULL;
        }
        StaticObject signersArray = (StaticObject) meta.HIDDEN_SIGNERS.getHiddenObject(self);
        if (signersArray == null || StaticObject.isNull(signersArray)) {
            return StaticObject.NULL;
        }
        return signersArray.copy();
    }

    @Substitution(hasReceiver = true)
    public static void setSigners(@JavaType(Class.class) StaticObject self, @JavaType(Object[].class) StaticObject signers,
                    @InjectMeta Meta meta) {
        Klass klass = self.getMirrorKlass();
        if (!klass.isPrimitive() && !klass.isArray()) {
            meta.HIDDEN_SIGNERS.setHiddenObject(self, signers);
        }
    }
}
