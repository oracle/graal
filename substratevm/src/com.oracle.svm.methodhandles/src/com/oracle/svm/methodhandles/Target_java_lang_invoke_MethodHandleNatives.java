/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.methodhandles;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.unimplemented;
import static com.oracle.svm.core.util.VMError.unsupportedFeature;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;
import com.oracle.svm.core.jdk.JDK11OrEarlier;
import com.oracle.svm.core.jdk.JDK17OrLater;
import com.oracle.svm.reflect.target.Target_java_lang_reflect_Field;

import sun.invoke.util.VerifyAccess;

/**
 * Native Image implementation of the parts of the JDK method handles engine implemented in C++. We
 * try to stay as close as the original implementation unless required by some design decision.
 */
@SuppressWarnings("unused")
@TargetClass(className = "java.lang.invoke.MethodHandleNatives")
final class Target_java_lang_invoke_MethodHandleNatives {

    /*
     * MemberName native constructor. We need to resolve the actual type and flags of the member and
     * specify its invocation type if needed.
     */
    @Substitute
    private static void init(Target_java_lang_invoke_MemberName self, Object ref) {
        Member member = (Member) ref;
        Object type;
        int flags;
        byte refKind;
        if (member instanceof Field) {
            Field field = (Field) member;
            type = field.getType();
            flags = Target_java_lang_invoke_MethodHandleNatives_Constants.MN_IS_FIELD | field.getModifiers();
            /* The code calling this expects a getter, and will change it to a setter if needed */
            refKind = Modifier.isStatic(field.getModifiers()) ? Target_java_lang_invoke_MethodHandleNatives_Constants.REF_getStatic
                            : Target_java_lang_invoke_MethodHandleNatives_Constants.REF_getField;
        } else if (member instanceof Method) {
            Method method = (Method) member;
            Object[] typeInfo = new Object[2];
            typeInfo[0] = method.getReturnType();
            typeInfo[1] = method.getParameterTypes();
            type = typeInfo;
            int mods = method.getModifiers();
            flags = Target_java_lang_invoke_MethodHandleNatives_Constants.MN_IS_METHOD | mods;
            if (Modifier.isStatic(mods)) {
                refKind = Target_java_lang_invoke_MethodHandleNatives_Constants.REF_invokeStatic;
            } else if (Modifier.isInterface(mods)) {
                refKind = Target_java_lang_invoke_MethodHandleNatives_Constants.REF_invokeInterface;
            } else {
                refKind = Target_java_lang_invoke_MethodHandleNatives_Constants.REF_invokeVirtual;
            }
        } else if (member instanceof Constructor) {
            Constructor<?> constructor = (Constructor<?>) member;
            Object[] typeInfo = new Object[2];
            typeInfo[0] = void.class;
            typeInfo[1] = constructor.getParameterTypes();
            type = typeInfo;
            flags = Target_java_lang_invoke_MethodHandleNatives_Constants.MN_IS_CONSTRUCTOR | constructor.getModifiers();
            refKind = Target_java_lang_invoke_MethodHandleNatives_Constants.REF_newInvokeSpecial;
        } else {
            throw new InternalError("unknown member type: " + member.getClass());
        }
        flags |= refKind << Target_java_lang_invoke_MethodHandleNatives_Constants.MN_REFERENCE_KIND_SHIFT;

        self.init(member.getDeclaringClass(), member.getName(), type, flags);
        self.reflectAccess = (Member) ref;
    }

    @Substitute
    private static void expand(Target_java_lang_invoke_MemberName self) {
        throw unsupportedFeature("MethodHandleNatives.expand()");
    }

    @Delete
    private static native int getMembers(Class<?> defc, String matchName, String matchSig, int matchFlags, Class<?> caller, int skip, Target_java_lang_invoke_MemberName[] results);

    @Substitute
    private static long objectFieldOffset(Target_java_lang_invoke_MemberName self) {
        if (self.reflectAccess == null && self.intrinsic == null) {
            throw new InternalError("unresolved field");
        }
        if (!self.isField() || self.isStatic()) {
            throw new InternalError("non-static field required");
        }

        /* Intrinsic arguments are not accessed through their offset. */
        if (self.intrinsic != null) {
            return -1L;
        }
        int offset = SubstrateUtil.cast(self.reflectAccess, Target_java_lang_reflect_Field.class).offset;
        if (offset == -1) {
            throw unsupportedFeature("Trying to access field " + self.reflectAccess + " without registering it as unsafe accessed.");
        }
        return offset;
    }

    @Substitute
    private static long staticFieldOffset(Target_java_lang_invoke_MemberName self) {
        if (self.reflectAccess == null && self.intrinsic == null) {
            throw new InternalError("unresolved field");
        }
        if (!self.isField() || !self.isStatic()) {
            throw new InternalError("static field required");
        }
        /* Intrinsic arguments are not accessed through their offset. */
        if (self.intrinsic != null) {
            return -1L;
        }
        int offset = SubstrateUtil.cast(self.reflectAccess, Target_java_lang_reflect_Field.class).offset;
        if (offset == -1) {
            throw unsupportedFeature("Trying to access field " + self.reflectAccess + " without registering it as unsafe accessed.");
        }
        return offset;
    }

    @Substitute
    private static Object staticFieldBase(Target_java_lang_invoke_MemberName self) {
        if (self.reflectAccess == null) {
            throw new InternalError("unresolved field");
        }
        if (!self.isField() || !self.isStatic()) {
            throw new InternalError("static field required");
        }
        return ((Field) self.reflectAccess).getType().isPrimitive() ? StaticFieldsSupport.getStaticPrimitiveFields() : StaticFieldsSupport.getStaticObjectFields();
    }

    @Substitute
    private static Object getMemberVMInfo(Target_java_lang_invoke_MemberName self) {
        throw unsupportedFeature("MethodHandleNatives.getMemberVMInfo()");
    }

    @Delete
    private static native void setCallSiteTargetNormal(CallSite site, MethodHandle target);

    @Delete
    private static native void setCallSiteTargetVolatile(CallSite site, MethodHandle target);

    @Delete
    private static native void registerNatives();

    @Delete
    private static native int getNamedCon(int which, Object[] name);

    // JDK 11

    @Substitute
    @TargetElement(onlyWith = JDK11OrEarlier.class)
    static Target_java_lang_invoke_MemberName resolve(Target_java_lang_invoke_MemberName self, Class<?> caller, boolean speculativeResolve) throws LinkageError, ClassNotFoundException {
        return Util_java_lang_invoke_MethodHandleNatives.resolve(self, caller, speculativeResolve);
    }

    @Delete
    private static native void copyOutBootstrapArguments(Class<?> caller, int[] indexInfo, int start, int end, Object[] buf, int pos, boolean resolve, Object ifNotAvailable);

    @Substitute
    private static void clearCallSiteContext(Target_java_lang_invoke_MethodHandleNatives_CallSiteContext context) {
        throw unimplemented("CallSiteContext not supported");
    }

    @AnnotateOriginal
    static native boolean refKindIsMethod(byte refKind);

    @AnnotateOriginal
    static native String refKindName(byte refKind);

    // JDK 17

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    static Target_java_lang_invoke_MemberName resolve(Target_java_lang_invoke_MemberName self, Class<?> caller, int lookupMode, boolean speculativeResolve)
                    throws LinkageError, ClassNotFoundException {
        Class<?> declaringClass = self.getDeclaringClass();
        Target_java_lang_invoke_MemberName resolved = Util_java_lang_invoke_MethodHandleNatives.resolve(self, caller, speculativeResolve);
        assert resolved == null || resolved.reflectAccess != null || resolved.intrinsic != null;
        if (resolved != null && resolved.reflectAccess != null &&
                        !Util_java_lang_invoke_MethodHandleNatives.verifyAccess(declaringClass, resolved.reflectAccess.getDeclaringClass(), resolved.reflectAccess.getModifiers(), caller,
                                        lookupMode)) {
            throw new IllegalAccessError(resolved + " is not accessible from " + caller);
        }
        return resolved;
    }
}

/**
 * The method handles API looks up methods and fields in a diffent way than the reflection API. The
 * specified member is searched in the given declaring class and its superclasses (like
 * {@link Class#getMethod(String, Class[])}) but including private members (like
 * {@link Class#getDeclaredMethod(String, Class[])}). We solve this by recursively looking up the
 * declared methods of the declaring class and its superclasses.
 */
final class Util_java_lang_invoke_MethodHandleNatives {

    static Method lookupMethod(Class<?> declaringClazz, String name, Class<?>[] parameterTypes) throws NoSuchMethodException {
        return lookupMethod(declaringClazz, name, parameterTypes, null);
    }

    private static Method lookupMethod(Class<?> declaringClazz, String name, Class<?>[] parameterTypes, NoSuchMethodException originalException) throws NoSuchMethodException {
        try {
            return declaringClazz.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            Class<?> superClass = declaringClazz.getSuperclass();
            NoSuchMethodException newOriginalException = originalException == null ? e : originalException;
            if (superClass == null) {
                throw newOriginalException;
            } else {
                return lookupMethod(superClass, name, parameterTypes, newOriginalException);
            }
        }
    }

    static Field lookupField(Class<?> declaringClazz, String name) throws NoSuchFieldException {
        return lookupField(declaringClazz, name, null);
    }

    private static Field lookupField(Class<?> declaringClazz, String name, NoSuchFieldException originalException) throws NoSuchFieldException {
        try {
            return declaringClazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            Class<?> superClass = declaringClazz.getSuperclass();
            NoSuchFieldException newOriginalException = originalException == null ? e : originalException;
            if (superClass == null) {
                throw newOriginalException;
            } else {
                return lookupField(superClass, name, newOriginalException);
            }
        }
    }

    @SuppressWarnings("unused")
    public static Target_java_lang_invoke_MemberName resolve(Target_java_lang_invoke_MemberName self, Class<?> caller, boolean speculativeResolve)
                    throws LinkageError, ClassNotFoundException {
        if (self.reflectAccess != null) {
            return self;
        }
        Class<?> declaringClass = self.getDeclaringClass();
        if (declaringClass == null) {
            return null;
        }

        /* Intrinsic methods */
        self.intrinsic = MethodHandleIntrinsicImpl.resolve(self);
        if (self.intrinsic != null) {
            self.flags |= ((MethodHandleIntrinsicImpl) self.intrinsic).variant.flags;
            return self;
        }

        /* Fill the member through reflection */
        try {
            if (self.isMethod()) {
                Class<?>[] parameterTypes = self.getMethodType().parameterArray();
                Method method = Util_java_lang_invoke_MethodHandleNatives.lookupMethod(declaringClass, self.name, parameterTypes);
                if (method.getReturnType() != self.getMethodType().returnType()) {
                    /* Method handle lookup also checks return type */
                    throw new NoSuchMethodException(SubstrateUtil.cast(declaringClass, DynamicHub.class).methodToString(self.name, parameterTypes));
                }
                self.reflectAccess = method;
                self.flags |= method.getModifiers();
            } else if (self.isConstructor()) {
                Constructor<?> constructor = declaringClass.getDeclaredConstructor(self.getMethodType().parameterArray());
                self.reflectAccess = constructor;
                self.flags |= constructor.getModifiers();
            } else if (self.isField()) {
                Field field = Util_java_lang_invoke_MethodHandleNatives.lookupField(declaringClass, self.name);
                if (field.getType() != self.getFieldType()) {
                    /* Method handle lookup also checks field type */
                    throw new NoSuchFieldException(declaringClass.getName() + "." + self.name);
                }
                self.reflectAccess = field;
                self.flags |= field.getModifiers();
            }
            return self;
        } catch (NoSuchMethodException e) {
            if (speculativeResolve) {
                return null;
            } else {
                throw new NoSuchMethodError(e.getMessage());
            }
        } catch (NoSuchFieldException e) {
            if (speculativeResolve) {
                return null;
            } else {
                throw new NoSuchFieldError(e.getMessage());
            }
        }
    }

    private static Method verifyAccess;

    static boolean verifyAccess(Class<?> refc, Class<?> defc, int mods, Class<?> lookupClass, int allowedModes) {
        assert JavaVersionUtil.JAVA_SPEC >= 17;
        if (verifyAccess == null) {
            try {
                verifyAccess = VerifyAccess.class.getDeclaredMethod("isMemberAccessible", Class.class, Class.class, int.class, Class.class, Class.class, int.class);
            } catch (NoSuchMethodException e) {
                throw shouldNotReachHere();
            }
        }
        try {
            return (boolean) verifyAccess.invoke(null, refc, defc, mods, lookupClass, null, allowedModes);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new GraalError(e);
        }
    }
}

@TargetClass(className = "java.lang.invoke.MethodHandleNatives", innerClass = "Constants")
final class Target_java_lang_invoke_MethodHandleNatives_Constants {
    // Checkstyle: stop
    @Alias static int MN_IS_METHOD;
    @Alias static int MN_IS_CONSTRUCTOR;
    @Alias static int MN_IS_FIELD;
    @Alias static int MN_IS_TYPE;
    @Alias static int MN_CALLER_SENSITIVE;
    @Alias static int MN_REFERENCE_KIND_SHIFT;
    @Alias static int MN_REFERENCE_KIND_MASK;
    // The SEARCH_* bits are not for MN.flags but for the matchFlags argument of MHN.getMembers:
    @Alias static int MN_SEARCH_SUPERCLASSES;
    @Alias static int MN_SEARCH_INTERFACES;

    /**
     * Constant pool reference-kind codes, as used by CONSTANT_MethodHandle CP entries.
     */
    @Alias static byte REF_NONE;  // null value
    @Alias static byte REF_getField;
    @Alias static byte REF_getStatic;
    @Alias static byte REF_putField;
    @Alias static byte REF_putStatic;
    @Alias static byte REF_invokeVirtual;
    @Alias static byte REF_invokeStatic;
    @Alias static byte REF_invokeSpecial;
    @Alias static byte REF_newInvokeSpecial;
    @Alias static byte REF_invokeInterface;
    @Alias static byte REF_LIMIT;
    // Checkstyle: resume
}

@TargetClass(className = "java.lang.invoke.MethodHandleNatives", innerClass = "CallSiteContext")
final class Target_java_lang_invoke_MethodHandleNatives_CallSiteContext {
}
