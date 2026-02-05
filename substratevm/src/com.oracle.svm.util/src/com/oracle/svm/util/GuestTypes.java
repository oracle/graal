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

import java.util.Objects;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.vmaccess.VMAccess;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class contains common {@linkplain ResolvedJavaType guest types} used throughout the code
 * base. They are statically looked up via the {@code lookup*Type} methods from {@link VMAccess}.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class GuestTypes {

    public static final ResolvedJavaType UNINTERRUPTIBLE_TYPE = lookupType("com.oracle.svm.guest.staging.Uninterruptible");
    public static final ResolvedJavaType C_FUNCTION_TYPE = lookupType(CFunction.class);
    public static final ResolvedJavaType INVOKE_C_FUNCTION_POINTER_TYPE = lookupType(InvokeCFunctionPointer.class);
    public static final ResolvedJavaType INTERNAL_VM_METHOD_TYPE = lookupType("com.oracle.svm.guest.staging.jdk.InternalVMMethod");

    private static ResolvedJavaType lookupType(Class<?> clazz) {
        ResolvedJavaType type = GraalAccess.getVMAccess().getProviders().getMetaAccess().lookupJavaType(clazz);
        if (type == null) {
            throw new GraalError("Unable to find type for class " + clazz.getName());
        }
        return type;
    }

    private static ResolvedJavaType lookupType(String className) {
        Objects.requireNonNull(className, "className must not be null");
        ResolvedJavaType type = GraalAccess.getVMAccess().lookupAppClassLoaderType(className);
        if (type == null) {
            throw new GraalError("Unable to find type for class name " + className);
        }
        return type;
    }

    private GuestTypes() {
    }

}
