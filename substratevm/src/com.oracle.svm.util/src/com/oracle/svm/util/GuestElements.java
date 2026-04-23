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
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;

import com.oracle.svm.shared.singletons.ImageSingletonsSupportImpl;

import jdk.graal.compiler.vmaccess.VMAccess;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class contains common guest elements ({@link ResolvedJavaType}, {@link ResolvedJavaMethod},
 * {@link ResolvedJavaField}) used throughout the code base. They are looked up via the
 * {@code lookup*} methods from {@link VMAccess}. Use the static {@link GuestAccess#elements()}
 * method or the {@link GuestAccess#elements} instance field to retrieve an instance of this class.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public abstract sealed class GuestElements permits GuestAccess.GuestElementsImpl {

    // Checkstyle: stop field name check
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
    public final ResolvedJavaMethod java_lang_Class_getClassLoader = lookupMethod(java_lang_Class, "getClassLoader");
    public final ResolvedJavaMethod java_lang_Class_getResourceAsStream = lookupMethod(java_lang_Class, "getResourceAsStream", String.class);

    public final ResolvedJavaType java_lang_ClassLoader = lookupType(ClassLoader.class);
    public final ResolvedJavaMethod java_lang_ClassLoader_getName = lookupMethod(java_lang_ClassLoader, "getName");

    public final ResolvedJavaType java_lang_Object = lookupType(Object.class);
    public final ResolvedJavaMethod java_lang_Object_clone = lookupMethod(java_lang_Object, "clone");

    public final ResolvedJavaType java_lang_Throwable = lookupType(Throwable.class);
    public final ResolvedJavaMethod java_lang_Throwable_init_String_Throwable_boolean_boolean = JVMCIReflectionUtil.getDeclaredConstructor(java_lang_Throwable,
                    lookupType(String.class), java_lang_Throwable, lookupType(boolean.class), lookupType(boolean.class));

    public final ResolvedJavaType java_lang_ref_Reference = lookupType(Reference.class);
    public final ResolvedJavaMethod java_lang_ref_Reference_refersTo = lookupMethod(java_lang_ref_Reference, "refersTo", Object.class);

    public final ResolvedJavaType java_lang_reflect_Field = lookupType(Field.class);
    public final ResolvedJavaMethod java_lang_reflect_Field_setAccessible = lookupMethod(java_lang_reflect_Field, "setAccessible", boolean.class);
    public final ResolvedJavaMethod java_lang_reflect_Field_set = lookupMethod(java_lang_reflect_Field, "set", Object.class, Object.class);

    public final ResolvedJavaType java_lang_System = lookupType(System.class);
    public final ResolvedJavaMethod java_lang_System_arraycopy = lookupMethod(java_lang_System, "arraycopy", Object.class, int.class, Object.class, int.class, int.class);

    public final ResolvedJavaType java_lang_reflect_Proxy = lookupType(Proxy.class);
    public final ResolvedJavaType jdk_internal_loader_ClassLoaders = lookupType("jdk.internal.loader.ClassLoaders");

    public final ResolvedJavaType java_io_InputStream = lookupType(InputStream.class);
    public final ResolvedJavaMethod java_io_Input_Stream_readAllBytesMethod = lookupMethod(java_io_InputStream, "readAllBytes");

    public final ResolvedJavaType java_util_Collection = lookupType(Collection.class);
    public final ResolvedJavaMethod java_util_Collection_toArray = lookupMethod(java_util_Collection, "toArray");
    public final ResolvedJavaMethod java_util_Collection_toArray_withArray = lookupMethod(java_util_Collection, "toArray", Object[].class);

    public final ResolvedJavaType java_util_Map = lookupType(Map.class);
    public final ResolvedJavaMethod java_util_Map_entrySet = lookupMethod(java_util_Map, "entrySet");

    public final ResolvedJavaType java_util_Map_Entry = lookupType("java.util.Map$Entry");
    public final ResolvedJavaMethod java_util_Map_Entry_getKey = lookupMethod(java_util_Map_Entry, "getKey");
    public final ResolvedJavaMethod java_util_Map_Entry_getValue = lookupMethod(java_util_Map_Entry, "getValue");

    public final ResolvedJavaType java_nio_ByteOrder = lookupType(ByteOrder.class);

    public final ResolvedJavaType java_util_Objects = lookupType(Objects.class);
    public final ResolvedJavaMethod java_util_Objects_deepEquals = lookupMethod(java_util_Objects, "deepEquals", Object.class, Object.class);

    public final ResolvedJavaType jdk_internal_foreign_abi_NativeEntryPoint = lookupType("jdk.internal.foreign.abi.NativeEntryPoint");
    public final ResolvedJavaMethod jdk_internal_foreign_abi_NativeEntryPoint_type = lookupMethod(jdk_internal_foreign_abi_NativeEntryPoint, "type");
    public final ResolvedJavaType jdk_internal_foreign_abi_SoftReferenceCache = lookupType("jdk.internal.foreign.abi.SoftReferenceCache");
    public final ResolvedJavaType jdk_internal_foreign_abi_NativeEntryPoint_CacheKey = lookupType("jdk.internal.foreign.abi.NativeEntryPoint$CacheKey");
    public final ResolvedJavaType jdk_internal_foreign_abi_SoftReferenceCache_Node = lookupType("jdk.internal.foreign.abi.SoftReferenceCache$Node");
    public final ResolvedJavaType jdk_internal_foreign_abi_VMStorage = lookupType("jdk.internal.foreign.abi.VMStorage");

    public final ResolvedJavaType Uninterruptible = lookupType("com.oracle.svm.shared.Uninterruptible");
    public final ResolvedJavaType CFunction = lookupType(CFunction.class);
    public final ResolvedJavaType InvokeCFunctionPointer = lookupType(InvokeCFunctionPointer.class);
    public final ResolvedJavaType InternalVMMethod = lookupType("com.oracle.svm.guest.staging.jdk.InternalVMMethod");

    public final ResolvedJavaType ImageSingletons = lookupType(ImageSingletons.class);
    public final ResolvedJavaMethod ImageSingletons_add = lookupMethod(ImageSingletons, "add", Class.class, Object.class);

    public final ResolvedJavaType HostedManagement = lookupType(ImageSingletonsSupportImpl.HostedManagement.class);
    public final ResolvedJavaMethod HostedManagement_install = lookupMethod(HostedManagement, "install");
    // Checkstyle: resume field name check

    public final Set<ResolvedJavaMethod> abstractMemorySegmentGetSetMethods = computeAbstractMemorySegmentGetSetMethods();

    protected abstract ResolvedJavaType lookupType(Class<?> clazz);

    protected abstract ResolvedJavaType lookupType(String className);

    protected abstract ResolvedJavaMethod lookupMethod(ResolvedJavaType type, String name, Class<?>... parameterTypes);

    private Set<ResolvedJavaMethod> computeAbstractMemorySegmentGetSetMethods() {
        ResolvedJavaType abstractMemorySegment = lookupType("jdk.internal.foreign.AbstractMemorySegmentImpl");
        ResolvedJavaType longType = lookupType(long.class);

        Set<ResolvedJavaMethod> roots = new HashSet<>();
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive() && kind != JavaKind.Void) {
                ResolvedJavaType valueLayoutType = lookupType("java.lang.foreign.ValueLayout$Of" + kind.name());
                addGetSetMethods(roots, abstractMemorySegment, valueLayoutType, longType, lookupType(kind.toJavaClass()));
            }
        }

        ResolvedJavaType addressLayoutType = lookupType("java.lang.foreign.AddressLayout");
        ResolvedJavaType memorySegmentType = lookupType("java.lang.foreign.MemorySegment");
        addGetSetMethods(roots, abstractMemorySegment, addressLayoutType, longType, memorySegmentType);
        return Collections.unmodifiableSet(roots);
    }

    private static void addGetSetMethods(Set<ResolvedJavaMethod> intrinsificationRoots, ResolvedJavaType abstractMemorySegment, ResolvedJavaType valueLayoutType, ResolvedJavaType longType,
                    ResolvedJavaType carrierType) {
        intrinsificationRoots.add(JVMCIReflectionUtil.getUniqueDeclaredMethod(abstractMemorySegment, "get", valueLayoutType, longType));
        intrinsificationRoots.add(JVMCIReflectionUtil.getUniqueDeclaredMethod(abstractMemorySegment, "getAtIndex", valueLayoutType, longType));
        intrinsificationRoots.add(JVMCIReflectionUtil.getUniqueDeclaredMethod(abstractMemorySegment, "set", valueLayoutType, longType, carrierType));
        intrinsificationRoots.add(JVMCIReflectionUtil.getUniqueDeclaredMethod(abstractMemorySegment, "setAtIndex", valueLayoutType, longType, carrierType));
    }
}
