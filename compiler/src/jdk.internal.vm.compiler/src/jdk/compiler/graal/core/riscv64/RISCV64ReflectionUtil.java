/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.compiler.graal.core.riscv64;

import java.lang.reflect.Method;

/**
 * This class contains utility methods for accessing RISC-V JVMCI classes and fields using
 * reflection. This is needed as all JDK versions used by Graal currently do not implement JVMCI for
 * RISC-V, which causes unwanted build errors.
 */
public class RISCV64ReflectionUtil {
    public static final String archClass = "jdk.vm.ci.riscv64.RISCV64";
    public static final String featureClass = archClass + "$CPUFeature";
    public static final String flagClass = archClass + "$Flag";
    public static final String hotSpotClass = "jdk.vm.ci.hotspot.riscv64.RISCV64HotSpotRegisterConfig";

    public static Class<?> lookupClass(boolean optional, String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            if (optional) {
                return null;
            }
            throw new ReflectionUtilError(ex);
        }
    }

    public static Class<?> getArch(boolean optional) {
        return lookupClass(optional, archClass);
    }

    @SuppressWarnings("unchecked")
    public static <T> T readStaticField(Class<?> declaringClass, String fieldName) {
        try {
            return (T) declaringClass.getField(fieldName).get(null);
        } catch (ReflectiveOperationException ex) {
            throw new ReflectionUtilError(ex);
        }
    }

    @SuppressWarnings("serial")
    public static final class ReflectionUtilError extends Error {
        private ReflectionUtilError(Throwable cause) {
            super(cause);
        }
    }

    public static Method lookupMethod(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        try {
            return declaringClass.getDeclaredMethod(methodName, parameterTypes);
        } catch (ReflectiveOperationException ex) {
            throw new ReflectionUtilError(ex);
        }
    }

    public static Object invokeMethod(Method method, Object obj, Object... args) {
        try {
            return method.invoke(obj, args);
        } catch (ReflectiveOperationException ex) {
            throw new ReflectionUtilError(ex);
        }
    }
}
