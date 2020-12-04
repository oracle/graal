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
package com.oracle.svm.methodhandles;

import static com.oracle.svm.core.util.VMError.unimplemented;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
// Checkstyle: stop
import java.lang.reflect.Method;
// Checkstyle: resume

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(value = MethodHandles.class, innerClass = "Lookup", onlyWith = MethodHandlesSupported.class)
final class Target_java_lang_invoke_MethodHandles_Lookup {
    @Delete("invokespecial is not supported for method handles")
    public native MethodHandle findSpecial(Class<?> refc, String name, MethodType type,
                    Class<?> specialCaller) throws NoSuchMethodException, IllegalAccessException;

    @Delete("invokespecial is not supported for method handles")
    public native MethodHandle unreflectSpecial(Method m, Class<?> specialCaller) throws IllegalAccessException;

    @Delete("invokespecial is not supported for method handles")
    public native MethodHandle bind(Object receiver, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException;

    @SuppressWarnings("static-method")
    @Substitute
    public Class<?> defineClass(@SuppressWarnings("unused") byte[] bytes) {
        throw unimplemented("Defining new classes at runtime is not supported");
    }

    @SuppressWarnings({"static-method", "unused"})
    @Substitute
    private MethodHandle maybeBindCaller(Target_java_lang_invoke_MemberName method, MethodHandle mh,
                    Class<?> boundCallerClass)
                    throws IllegalAccessException {
        /* Binding the caller triggers the generation of an invoker */
        return mh;
    }
}

@TargetClass(value = MethodHandleProxies.class, onlyWith = MethodHandlesSupported.class)
final class Target_java_lang_invoke_MethodHandleProxies {
    @Delete("invokespecial is not supported for method handles")
    public static native <T> T asInterfaceInstance(Class<T> intfc, MethodHandle target);
}
