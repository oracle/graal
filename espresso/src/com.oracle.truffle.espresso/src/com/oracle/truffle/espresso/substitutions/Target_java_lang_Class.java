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

import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions
public final class Target_java_lang_Class {

    @Substitution
    public static @JavaType(Class.class) StaticObject getPrimitiveClass(@JavaType(String.class) StaticObject name,
                    @Inject Meta meta) {
        String hostName = meta.toHostString(name);
        return VM.findPrimitiveClass(meta, hostName);
    }

    @Substitution
    public static boolean desiredAssertionStatus0(@JavaType(Class.class) StaticObject clazz, @Inject Meta meta) {
        return meta.getVM().JVM_DesiredAssertionStatus(null, clazz);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getName0(@JavaType(Class.class) StaticObject self,
                    @Inject Meta meta) {
        Klass klass = self.getMirrorKlass();
        // Conversion from internal form.
        String externalName = klass.getExternalName();
        // Class names must be interned.
        StaticObject guestString = meta.toGuestString(externalName);
        return meta.getStrings().intern(guestString);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject initClassName(@JavaType(Class.class) StaticObject self,
                    @Inject Meta meta) {
        return meta.getVM().JVM_InitClassName(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getSimpleBinaryName0(@JavaType(Class.class) StaticObject self,
                    @Inject Meta meta) {
        return meta.getVM().JVM_GetSimpleBinaryName(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(ClassLoader.class) StaticObject getClassLoader0(@JavaType(Class.class) StaticObject self) {
        return self.getMirrorKlass().getDefiningClassLoader();
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(java.lang.reflect.Field[].class) StaticObject getDeclaredFields0(@JavaType(Class.class) StaticObject self, boolean publicOnly,
                    @Inject Meta meta) {
        return meta.getVM().JVM_GetClassDeclaredFields(self, publicOnly);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Constructor[].class) StaticObject getDeclaredConstructors0(@JavaType(Class.class) StaticObject self, boolean publicOnly,
                    @Inject Meta meta) {
        return meta.getVM().JVM_GetClassDeclaredConstructors(self, publicOnly);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(java.lang.reflect.Method[].class) StaticObject getDeclaredMethods0(@JavaType(Class.class) StaticObject self, boolean publicOnly,
                    @Inject Meta meta) {
        return meta.getVM().JVM_GetClassDeclaredMethods(self, publicOnly);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class[].class) StaticObject getInterfaces0(@JavaType(Class.class) StaticObject self,
                    @Inject Meta meta) {
        return meta.getVM().JVM_GetClassInterfaces(self);
    }

    @Substitution(hasReceiver = true)
    public static boolean isPrimitive(@JavaType(Class.class) StaticObject self) {
        return VM.JVM_IsPrimitiveClass(self);
    }

    @Substitution(hasReceiver = true)
    public static boolean isInterface(@JavaType(Class.class) StaticObject self) {
        return VM.JVM_IsInterface(self);
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
        return VM.JVM_IsArrayClass(self);
    }

    @Substitution(hasReceiver = true)
    public static boolean isHidden(@JavaType(Class.class) StaticObject self) {
        return VM.JVM_IsHiddenClass(self);
    }

    @Substitution(hasReceiver = true)
    public static boolean isRecord(@JavaType(Class.class) StaticObject self) {
        return VM.JVM_IsRecord(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "[Ljava/lang/reflect/RecordComponent;") StaticObject getRecordComponents0(@JavaType(Class.class) StaticObject self,
                    @Inject Meta meta) {
        return meta.getVM().JVM_GetRecordComponents(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class[].class) StaticObject getPermittedSubclasses0(@JavaType(Class.class) StaticObject self,
                    @Inject Meta meta) {
        return meta.getVM().JVM_GetPermittedSubclasses(self);
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
    public static @JavaType(Object[].class) StaticObject getEnclosingMethod0(@JavaType(Class.class) StaticObject self, @Inject Meta meta) {
        return meta.getVM().JVM_GetEnclosingMethodInfo(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class.class) StaticObject getDeclaringClass0(@JavaType(Class.class) StaticObject self) {
        return VM.JVM_GetDeclaringClass(self);
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
                    @Inject Meta meta) {
        return meta.getVM().JVM_GetProtectionDomain(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class.class) StaticObject getNestHost0(@JavaType(Class.class) StaticObject self) {
        return VM.JVM_GetNestHost(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class[].class) StaticObject getNestMembers0(@JavaType(Class.class) StaticObject self,
                    @Inject Meta meta) {
        return meta.getVM().JVM_GetNestMembers(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(byte[].class) StaticObject getRawAnnotations(@JavaType(Class.class) StaticObject self) {
        return VM.JVM_GetClassAnnotations(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(byte[].class) StaticObject getRawTypeAnnotations(@JavaType(Class.class) StaticObject self) {
        return VM.JVM_GetClassTypeAnnotations(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getGenericSignature0(@JavaType(Class.class) StaticObject self,
                    @Inject Meta meta) {
        return meta.getVM().JVM_GetClassSignature(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class[].class) StaticObject getDeclaredClasses0(@JavaType(Class.class) StaticObject self,
                    @Inject Meta meta) {
        return meta.getVM().JVM_GetDeclaredClasses(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Object[].class) StaticObject getSigners(@JavaType(Class.class) StaticObject self,
                    @Inject Meta meta) {
        return meta.getVM().JVM_GetClassSigners(self);
    }

    @Substitution(hasReceiver = true)
    public static void setSigners(@JavaType(Class.class) StaticObject self, @JavaType(Object[].class) StaticObject signers,
                    @Inject Meta meta) {
        meta.getVM().JVM_SetClassSigners(self, signers);
    }
}
