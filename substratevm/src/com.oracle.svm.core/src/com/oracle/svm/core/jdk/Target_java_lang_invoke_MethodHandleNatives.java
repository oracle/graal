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
package com.oracle.svm.core.jdk;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

@TargetClass(className = "java.lang.invoke.MethodHandleNatives")
final class Target_java_lang_invoke_MethodHandleNatives {

    /*
     * We are defensive and handle native methods by marking them as deleted. If they are reachable,
     * the user is certainly doing something wrong. But we do not want to fail with a linking error.
     */

    @Delete
    private static native void init(Target_java_lang_invoke_MemberName self, Object ref);

    @Delete
    private static native void expand(Target_java_lang_invoke_MemberName self);

    @Delete
    private static native int getMembers(Class<?> defc, String matchName, String matchSig, int matchFlags, Class<?> caller, int skip, Target_java_lang_invoke_MemberName[] results);

    @Delete
    private static native long objectFieldOffset(Target_java_lang_invoke_MemberName self);

    @Delete
    private static native long staticFieldOffset(Target_java_lang_invoke_MemberName self);

    @Delete
    private static native Object staticFieldBase(Target_java_lang_invoke_MemberName self);

    @Delete
    private static native Object getMemberVMInfo(Target_java_lang_invoke_MemberName self);

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

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native Target_java_lang_invoke_MemberName resolve(Target_java_lang_invoke_MemberName self, Class<?> caller, boolean speculativeResolve) throws LinkageError, ClassNotFoundException;

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native void copyOutBootstrapArguments(Class<?> caller, int[] indexInfo, int start, int end, Object[] buf, int pos, boolean resolve, Object ifNotAvailable);

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native void clearCallSiteContext(Target_java_lang_invoke_MethodHandleNatives_CallSiteContext context);
}

@TargetClass(className = "java.lang.invoke.MethodHandleNatives", innerClass = "CallSiteContext", onlyWith = JDK11OrLater.class)
final class Target_java_lang_invoke_MethodHandleNatives_CallSiteContext {
}
