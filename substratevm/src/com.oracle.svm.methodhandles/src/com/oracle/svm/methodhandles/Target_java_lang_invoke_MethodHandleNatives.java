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

import static com.oracle.svm.core.util.VMError.unsupportedFeature;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
// Checkstyle: stop
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
// Checkstyle: resume
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.reflect.target.Target_java_lang_reflect_Field;

@SuppressWarnings("unused")
@TargetClass(className = "java.lang.invoke.MethodHandleNatives", onlyWith = MethodHandlesSupported.class)
final class Target_java_lang_invoke_MethodHandleNatives {

    @Substitute
    private static void init(Target_java_lang_invoke_MemberName self, Object ref) {
        throw unsupportedFeature("MethodHandleNatives.init()");
    }

    @Substitute
    private static void expand(Target_java_lang_invoke_MemberName self) {
        throw unsupportedFeature("MethodHandleNatives.expand()");
    }

    @Delete
    private static native int getMembers(Class<?> defc, String matchName, String matchSig, int matchFlags, Class<?> caller, int skip, Target_java_lang_invoke_MemberName[] results);

    @Substitute
    private static long objectFieldOffset(Target_java_lang_invoke_MemberName self) {
        if (!self.isField() || self.isStatic()) {
            throw new InternalError("non-static field required");
        }
        try {
            return GraalUnsafeAccess.getUnsafe().objectFieldOffset(self.getDeclaringClass().getDeclaredField(self.name));
        } catch (NoSuchFieldException e) {
            throw new InternalError(e);
        }
    }

    @Substitute
    private static long staticFieldOffset(Target_java_lang_invoke_MemberName self) {
        if (!self.isField() || !self.isStatic()) {
            throw new InternalError("static field required");
        }
        return 0L;
    }

    @Substitute
    private static Object staticFieldBase(Target_java_lang_invoke_MemberName self) {
        if (self.reflectAccess == null) {
            throw new InternalError("unresolved field");
        }
        if (!self.isField() || !self.isStatic()) {
            throw new InternalError("static field required");
        }
        return SubstrateUtil.cast(self.reflectAccess, Target_java_lang_reflect_Field.class).acquireFieldAccessor(false);
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

    // JDK 8 only

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private static native Target_java_lang_invoke_MemberName resolve(Target_java_lang_invoke_MemberName self, Class<?> caller) throws LinkageError, ClassNotFoundException;

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private static native int getConstant(int which);

    // JDK 11

    @TargetElement(onlyWith = JDK11OrLater.class)
    @Substitute
    private static Target_java_lang_invoke_MemberName resolve(Target_java_lang_invoke_MemberName self, Class<?> caller, boolean speculativeResolve) throws LinkageError, ClassNotFoundException {
        if (self.reflectAccess != null) {
            return self;
        }
        Class<?> declaringClass = self.getDeclaringClass();
        if (declaringClass == null) {
            return null;
        }

        Member member = null;
        try {
            if (self.isMethod()) {
                try {
                    member = declaringClass.getDeclaredMethod(self.name, ((MethodType) self.type).parameterArray());
                } catch (NoSuchMethodException e) {
                    if (MethodHandleUtils.isPolymorphicSignatureMethod(declaringClass, self.name)) {
                        try {
                            member = declaringClass.getDeclaredMethod(self.name, Object[].class);
                        } catch (NoSuchMethodException ex) {
                            throw e;
                        }
                    } else {
                        throw e;
                    }
                }
                self.flags |= SubstrateUtil.cast(member, Method.class).getModifiers();
            } else if (self.isConstructor()) {
                member = declaringClass.getDeclaredConstructor(((MethodType) self.type).parameterArray());
                self.flags |= SubstrateUtil.cast(member, Constructor.class).getModifiers();
            } else if (self.isField()) {
                member = declaringClass.getDeclaredField(self.name);
                self.flags |= SubstrateUtil.cast(member, Field.class).getModifiers();
            }
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            if (speculativeResolve) {
                return null;
            } else {
                throw new InternalError(e);
            }
        }
        self.reflectAccess = member;
        return self;
    }

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native void copyOutBootstrapArguments(Class<?> caller, int[] indexInfo, int start, int end, Object[] buf, int pos, boolean resolve, Object ifNotAvailable);

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native void clearCallSiteContext(Target_java_lang_invoke_MethodHandleNatives_CallSiteContext context);

    @AnnotateOriginal
    static native boolean refKindIsMethod(byte refKind);

    @AnnotateOriginal
    static native String refKindName(byte refKind);
}

@TargetClass(className = "java.lang.invoke.MethodHandleNatives", innerClass = "CallSiteContext", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_MethodHandleNatives_CallSiteContext {
}

@TargetClass(className = "java.lang.invoke.InvokerBytecodeGenerator", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_InvokerBytecodeGenerator {
    @SuppressWarnings("unused")
    @Substitute
    static Target_java_lang_invoke_MemberName generateLambdaFormInterpreterEntryPoint(MethodType mt) {
        return null;
    }
}

class MethodHandleUtils {
    static final String methodHandleClass = "java.lang.invoke.MethodHandle";
    static final List<String> polymorphicSignatureMethods = Arrays.asList("invokeBasic", "linkToVirtual", "linkToStatic");

    static boolean isPolymorphicSignatureMethod(Class<?> declaringClass, String name) {
        return declaringClass.getName().equals(MethodHandleUtils.methodHandleClass) && MethodHandleUtils.polymorphicSignatureMethods.contains(name);
    }
}

@TargetClass(className = "java.lang.invoke.MethodHandleNatives", onlyWith = MethodHandlesNotSupported.class)
final class Target_java_lang_invoke_MethodHandleNatives_NotSupported {

    /*
     * We are defensive and handle native methods by marking them as deleted. If they are reachable,
     * the user is certainly doing something wrong. But we do not want to fail with a linking error.
     */

    @Delete
    private static native void init(Target_java_lang_invoke_MemberName_NotSupported self, Object ref);

    @Delete
    private static native void expand(Target_java_lang_invoke_MemberName_NotSupported self);

    @Delete
    private static native int getMembers(Class<?> defc, String matchName, String matchSig, int matchFlags, Class<?> caller, int skip, Target_java_lang_invoke_MemberName_NotSupported[] results);

    @Delete
    private static native long objectFieldOffset(Target_java_lang_invoke_MemberName_NotSupported self);

    @Delete
    private static native long staticFieldOffset(Target_java_lang_invoke_MemberName_NotSupported self);

    @Delete
    private static native Object staticFieldBase(Target_java_lang_invoke_MemberName_NotSupported self);

    @Delete
    private static native Object getMemberVMInfo(Target_java_lang_invoke_MemberName_NotSupported self);

    @Delete
    private static native void setCallSiteTargetNormal(CallSite site, MethodHandle target);

    @Delete
    private static native void setCallSiteTargetVolatile(CallSite site, MethodHandle target);

    @Delete
    private static native void registerNatives();

    @Delete
    private static native int getNamedCon(int which, Object[] name);

    // JDK 8 only

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private static native Target_java_lang_invoke_MemberName_NotSupported resolve(Target_java_lang_invoke_MemberName_NotSupported self, Class<?> caller) throws LinkageError, ClassNotFoundException;

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private static native int getConstant(int which);

    // JDK 11

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native Target_java_lang_invoke_MemberName_NotSupported resolve(Target_java_lang_invoke_MemberName_NotSupported self, Class<?> caller, boolean speculativeResolve)
                    throws LinkageError, ClassNotFoundException;

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native void copyOutBootstrapArguments(Class<?> caller, int[] indexInfo, int start, int end, Object[] buf, int pos, boolean resolve, Object ifNotAvailable);

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native void clearCallSiteContext(Target_java_lang_invoke_MethodHandleNatives_CallSiteContext context);
}
