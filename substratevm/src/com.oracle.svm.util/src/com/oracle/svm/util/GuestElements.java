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

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Objects;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.vmaccess.VMAccess;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class contains common guest elements ({@link ResolvedJavaType}, {@link ResolvedJavaMethod},
 * {@link ResolvedJavaField}) used throughout the code base. They are looked up via the
 * {@code lookup*} methods from {@link VMAccess}.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class GuestElements {

    private static final GuestElements INSTANCE = new GuestElements();

    public static GuestElements get() {
        return INSTANCE;
    }

    private GuestElements() {
    }

    public final ResolvedJavaType java_lang_Boolean = lookupType(Boolean.class);
    public final ResolvedJavaMethod java_lang_Boolean_valueOf = lookupMethod(java_lang_Boolean, "valueOf", boolean.class);

    public final ResolvedJavaType java_lang_Byte = lookupType(Byte.class);
    public final ResolvedJavaMethod java_lang_Byte_valueOf = lookupMethod(java_lang_Byte, "valueOf", byte.class);

    public final ResolvedJavaType java_lang_Character = lookupType(Character.class);
    public final ResolvedJavaMethod java_lang_Character_valueOf = lookupMethod(java_lang_Character, "valueOf", char.class);

    public final ResolvedJavaType java_lang_Integer = lookupType(Integer.class);
    public final ResolvedJavaMethod java_lang_Integer_valueOf = lookupMethod(java_lang_Integer, "valueOf", int.class);

    public final ResolvedJavaType java_lang_Short = lookupType(Short.class);
    public final ResolvedJavaMethod java_lang_Short_valueOf = lookupMethod(java_lang_Short, "valueOf", short.class);

    public final ResolvedJavaType java_lang_Long = lookupType(Long.class);
    public final ResolvedJavaMethod java_lang_Long_valueOf = lookupMethod(java_lang_Long, "valueOf", long.class);

    public final ResolvedJavaType java_lang_Float = lookupType(Float.class);
    public final ResolvedJavaMethod java_lang_Float_valueOf = lookupMethod(java_lang_Float, "valueOf", float.class);

    public final ResolvedJavaType java_lang_Double = lookupType(Double.class);
    public final ResolvedJavaMethod java_lang_Double_valueOf = lookupMethod(java_lang_Double, "valueOf", double.class);

    public final ResolvedJavaType java_lang_Class = lookupType(Class.class);
    public final ResolvedJavaMethod java_lang_Class_getResourceAsStream = lookupMethod(java_lang_Class, "getResourceAsStream", String.class);

    public final ResolvedJavaType java_lang_reflect_Field = lookupType(Field.class);
    public final ResolvedJavaMethod java_lang_reflect_Field_setAccessible = lookupMethod(java_lang_reflect_Field, "setAccessible", boolean.class);
    public final ResolvedJavaMethod java_lang_reflect_Field_set = lookupMethod(java_lang_reflect_Field, "set", Object.class, Object.class);

    public final ResolvedJavaType java_lang_reflect_Proxy = lookupType(Proxy.class);
    public final ResolvedJavaType jdk_internal_loader_ClassLoaders = lookupType("jdk.internal.loader.ClassLoaders");

    public final ResolvedJavaType java_io_InputStream = lookupType(InputStream.class);
    public final ResolvedJavaMethod java_io_Input_Stream_readAllBytesMethod = lookupMethod(java_io_InputStream, "readAllBytes");

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

    private static ResolvedJavaMethod lookupMethod(ResolvedJavaType type, String name, Class<?>... parameterTypes) {
        var method = JVMCIReflectionUtil.getUniqueDeclaredMethod(GuestAccess.get().getProviders().getMetaAccess(), type, name, parameterTypes);
        if (method == null) {
            throw new GraalError("Unable to find type for class " + type.toClassName());
        }
        return method;
    }
}
