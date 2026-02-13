/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util;

import java.lang.reflect.Proxy;
import java.util.Objects;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.vmaccess.VMAccess;
import jdk.internal.loader.ClassLoaders;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class contains common {@linkplain ResolvedJavaType guest types} used throughout the code
 * base. They are statically looked up via the {@code lookup*Type} methods from {@link VMAccess}.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class GuestTypes {

    private static final GuestTypes INSTANCE = new GuestTypes();

    public static GuestTypes get() {
        return INSTANCE;
    }

    private GuestTypes() {
    }

    public final ResolvedJavaType java_lang_Class = lookupType(Class.class);
    public final ResolvedJavaType java_lang_reflect_Proxy = lookupType(Proxy.class);
    public final ResolvedJavaType jdk_internal_loader_ClassLoaders = lookupType(ClassLoaders.class);

    public final ResolvedJavaType Uninterruptible = lookupType("com.oracle.svm.guest.staging.Uninterruptible");
    public final ResolvedJavaType CFunction = lookupType(CFunction.class);
    public final ResolvedJavaType InvokeCFunctionPointer = lookupType(InvokeCFunctionPointer.class);
    public final ResolvedJavaType InternalVMMethod = lookupType("com.oracle.svm.guest.staging.jdk.InternalVMMethod");

    private static ResolvedJavaType lookupType(Class<?> clazz) {
        ResolvedJavaType type = GuestAccess.get().lookupType(clazz);
        if (type == null) {
            throw new GraalError("Unable to find type for class " + clazz.getName());
        }
        return type;
    }

    private static ResolvedJavaType lookupType(String className) {
        Objects.requireNonNull(className, "className must not be null");
        ResolvedJavaType type = GuestAccess.get().lookupType(className);
        if (type == null) {
            throw new GraalError("Unable to find type for class name " + className);
        }
        return type;
    }
}
