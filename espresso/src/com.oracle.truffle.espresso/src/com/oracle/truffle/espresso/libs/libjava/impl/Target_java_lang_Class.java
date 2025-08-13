/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs.libjava.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.Validation;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions(value = Class.class, group = LibJava.class)
public final class Target_java_lang_Class {

    // Already in regular substitutions:
    /*-
    public static @JavaType(Class.class) StaticObject getSuperclass(@JavaType(Class.class) StaticObject self, @Inject Meta meta) {
        return self.getMirrorKlass(meta).getSuperKlass().mirror();
    }
    
    public static boolean isInterface(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_IsInterface(self);
    }
    
    public static boolean isArray(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_IsArrayClass(self);
    }
    
    public static boolean isHidden(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_IsHiddenClass(self);
    }
    
    public static boolean isPrimitive(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_IsPrimitiveClass(self);
    }
    */

    @Substitution
    public static void registerNatives() {
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject initClassName(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_InitClassName(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class[].class) StaticObject getInterfaces0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetClassInterfaces(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Object.class) StaticObject getSigners(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetClassSigners(self, ctx);
    }

    @Substitution(hasReceiver = true)
    public static void setSigners(@JavaType(Class.class) StaticObject self, @JavaType(Object[].class) StaticObject signers, @Inject EspressoContext ctx) {
        ctx.getVM().JVM_SetClassSigners(self, signers);
    }

    @Substitution(hasReceiver = true)
    public static int getModifiers(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetClassModifiers(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Field[].class) StaticObject getDeclaredFields0(@JavaType(Class.class) StaticObject self, boolean publicOnly, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetClassDeclaredFields(self, publicOnly);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Method[].class) StaticObject getDeclaredMethods0(@JavaType(Class.class) StaticObject self, boolean publicOnly, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetClassDeclaredMethods(self, publicOnly);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Constructor[].class) StaticObject getDeclaredConstructors0(@JavaType(Class.class) StaticObject self, boolean publicOnly, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetClassDeclaredConstructors(self, publicOnly);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(ProtectionDomain.class) StaticObject getProtectionDomain0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetProtectionDomain(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class.class) StaticObject getDeclaredClasses0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetDeclaredClasses(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class.class) StaticObject getDeclaringClass0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetDeclaringClass(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getSimpleBinaryName0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetSimpleBinaryName(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getGenericSignature0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetClassSignature(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(byte[].class) StaticObject getRawAnnotations(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetClassAnnotations(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "Lsun/reflect/ConstantPool;") StaticObject getConstantPool(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetClassConstantPool(self);
    }

    @Substitution(hasReceiver = true)
    public static boolean desiredAssertionStatus0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_DesiredAssertionStatus(StaticObject.NULL, self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Object.class) StaticObject getEnclosingMethod0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx, @Inject EspressoLanguage lang) {
        return ctx.getVM().JVM_GetEnclosingMethodInfo(self, lang);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(byte[].class) StaticObject getRawTypeAnnotations(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetClassTypeAnnotations(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class.class) StaticObject getNestHost0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetNestHost(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class.class) StaticObject getNestMembers0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetNestMembers(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Record[].class) StaticObject getRecordComponents0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetRecordComponents(self);
    }

    @Substitution(hasReceiver = true)
    public static boolean isRecord0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_IsRecord(self);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class.class) StaticObject getPermittedSubclasses0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetPermittedSubclasses(self);
    }

    @Substitution(hasReceiver = true)
    // Method no longer exists in 26.
    public static int getClassAccessFlagsRaw0(@JavaType(Class.class) StaticObject self, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetClassAccessFlags(self);
    }

    @Substitution
    @TruffleBoundary
    public static @JavaType(Class.class) StaticObject getPrimitiveClass(@JavaType(Target_java_lang_String.class) StaticObject name, @Inject EspressoContext ctx) {
        String hostString = ctx.getMeta().toHostString(name);
        if (hostString == null) {
            ctx.getMeta().throwNullPointerException();
        }
        return VM.findPrimitiveClass(ctx.getMeta(), hostString);
    }

    @Substitution
    @TruffleBoundary
    public static @JavaType(Class.class) StaticObject forName0(
                    @JavaType(Target_java_lang_String.class) StaticObject name,
                    boolean initialize,
                    @JavaType(ClassLoader.class) StaticObject loader,
                    @JavaType(Class.class) StaticObject caller,
                    @Inject EspressoContext ctx) {
        String clname = ctx.getMeta().toHostString(name);
        if (clname == null) {
            throw ctx.getMeta().throwNullPointerException();
        }
        if (clname.contains("/")) {
            throw ctx.getMeta().throwExceptionWithMessage(ctx.getMeta().java_lang_ClassNotFoundException, name);
        }
        clname = clname.replace(".", "/");

        if (!Validation.validClassNameEntry(ByteSequence.create(clname))) {
            throw ctx.getMeta().throwExceptionWithMessage(ctx.getMeta().java_lang_ClassNotFoundException, name);
        }
        return ctx.getVM().findClassFromCaller(ctx.getTypes().fromClassGetName(clname), initialize, loader, caller);
    }
}
