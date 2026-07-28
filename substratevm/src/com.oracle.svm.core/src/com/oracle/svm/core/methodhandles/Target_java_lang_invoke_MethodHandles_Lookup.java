/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.methodhandles;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading.NoRuntimeClassLoading;
import com.oracle.svm.core.hub.RuntimeClassLoading.WithRuntimeClassLoading;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;
import com.oracle.svm.shared.util.BasedOnJDKClass;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.internal.reflect.Reflection;

@TargetClass(value = MethodHandles.class, innerClass = "Lookup")
final class Target_java_lang_invoke_MethodHandles_Lookup {
    // Checkstyle: stop
    @Delete //
    @TargetElement(onlyWith = NoRuntimeClassLoading.class, name = "LOOKASIDE_TABLE") //
    static ConcurrentHashMap<Target_java_lang_invoke_MemberName, MethodHandle> LOOKASIDE_TABLE_DELETED;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    @TargetElement(onlyWith = WithRuntimeClassLoading.class) //
    static ConcurrentHashMap<Target_java_lang_invoke_MemberName, MethodHandle> LOOKASIDE_TABLE;
    // Checkstyle: resume

    /*
     * Reset the field to avoid image build errors in case the field becomes reachable (plus the
     * hosted values would be wrong at run time anyway).
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private volatile ProtectionDomain cachedProtectionDomain;

    @SuppressWarnings({"static-method", "unused"})
    @Substitute
    private MethodHandle maybeBindCaller(Target_java_lang_invoke_MemberName method, MethodHandle mh,
                    Target_java_lang_invoke_MethodHandles_Lookup boundCaller)
                    throws IllegalAccessException {
        if (boundCaller.allowedModes == TRUSTED || !MethodHandleCallerSensitiveSupport.isCallerSensitive(method)) {
            return mh;
        }
        if ((boundCaller.lookupModes() & ORIGINAL) == 0) {
            throw new IllegalAccessException("Attempt to lookup caller-sensitive method using restricted lookup object");
        }
        return MethodHandleCallerSensitiveSupport.createWrappedMember(mh, method, boundCaller.lookupClass);
    }

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    private Class<?> lookupClass;

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    private Class<?> prevLookupClass;

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    private int allowedModes;

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    static int ORIGINAL;

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    private static int TRUSTED;

    @Alias
    native int lookupModes();

    @Substitute
    private IllegalAccessException makeAccessException(Class<?> targetClass) {
        String message = "access violation: " + targetClass;
        if (this == SubstrateUtil.cast(MethodHandles.publicLookup(), Target_java_lang_invoke_MethodHandles_Lookup.class)) {
            message += ", from public Lookup";
        } else {
            Object m = SubstrateUtil.cast(lookupClass, DynamicHub.class).getModule();
            message += ", from " + lookupClass + " (" + m + ")";
            if (prevLookupClass != null) {
                message += ", previous lookup " +
                                prevLookupClass.getName() + " (" + SubstrateUtil.cast(prevLookupClass, DynamicHub.class).getModule() + ")";
            }
        }
        return new IllegalAccessException(message);
    }

    @Delete
    @TargetElement(onlyWith = NoRuntimeClassLoading.class) //
    native MethodHandle linkMethodHandleConstant(byte refKind, Class<?> defc, String name, Object type) throws ReflectiveOperationException;
}

@BasedOnJDKClass(className = "java.lang.invoke.MethodHandleNatives")
final class MethodHandleCallerSensitiveSupport {
    private MethodHandleCallerSensitiveSupport() {
    }

    static boolean isCallerSensitive(Target_java_lang_invoke_MemberName mem) {
        if (!mem.isInvocable()) {
            return false;
        }
        if (mem.reflectAccess == null && mem.intrinsic == null) {
            Util_java_lang_invoke_MethodHandleNatives.resolve(mem, null, false);
        }
        return (mem.reflectAccess instanceof Method reflectMethod && Reflection.isCallerSensitive(reflectMethod)) || canBeCalledVirtual(mem);
    }

    private static boolean canBeCalledVirtual(Target_java_lang_invoke_MemberName mem) {
        if ("getContextClassLoader".equals(mem.name)) {
            return canBeCalledVirtual(mem, Thread.class);
        }
        return false;
    }

    private static boolean canBeCalledVirtual(Target_java_lang_invoke_MemberName symbolicRef, Class<?> definingClass) {
        Class<?> symbolicRefClass = symbolicRef.getDeclaringClass();
        if (symbolicRefClass == definingClass) {
            return true;
        }
        if (symbolicRef.isStatic() || symbolicRef.isPrivate()) {
            return false;
        }
        DynamicHub definingClassHub = DynamicHub.fromClass(definingClass);
        DynamicHub symbolicRefClassHub = DynamicHub.fromClass(symbolicRefClass);
        return isAssignableFrom(definingClassHub, symbolicRefClassHub) || symbolicRefClassHub.isInterface();
    }

    private static boolean isAssignableFrom(DynamicHub definingClassHub, DynamicHub symbolicRefClassHub) {
        if (definingClassHub == symbolicRefClassHub) {
            return true;
        }
        if (definingClassHub.isInterface()) {
            return implementsInterface(symbolicRefClassHub, definingClassHub);
        }
        for (DynamicHub current = symbolicRefClassHub.getSuperHub(); current != null; current = current.getSuperHub()) {
            if (current == definingClassHub) {
                return true;
            }
        }
        return false;
    }

    private static boolean implementsInterface(DynamicHub symbolicRefClassHub, DynamicHub definingClassHub) {
        for (DynamicHub interfaceHub : symbolicRefClassHub.getInterfaces()) {
            if (interfaceHub == definingClassHub || implementsInterface(interfaceHub, definingClassHub)) {
                return true;
            }
        }
        DynamicHub superHub = symbolicRefClassHub.getSuperHub();
        return superHub != null && implementsInterface(superHub, definingClassHub);
    }

    static MethodHandle createWrappedMember(MethodHandle target, Target_java_lang_invoke_MemberName member, Class<?> callerClass) {
        Target_java_lang_invoke_MethodHandle targetHandle = SubstrateUtil.cast(target, Target_java_lang_invoke_MethodHandle.class);
        Target_java_lang_invoke_MethodHandleImpl_WrappedMember result = new Target_java_lang_invoke_MethodHandleImpl_WrappedMember();
        result.constructor(target, target.type(), member, targetHandle.isInvokeSpecial(), callerClass);
        return SubstrateUtil.cast(result, MethodHandle.class);
    }
}
