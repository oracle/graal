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

package com.oracle.svm.core.foreign;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Objects;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.util.BasedOnJDKClass;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.GuestElements;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.internal.foreign.abi.NativeEntryPoint;
import jdk.internal.foreign.abi.VMStorage;
import jdk.vm.ci.meta.JavaConstant;

@BasedOnJDKClass(NativeEntryPoint.class)
@BasedOnJDKClass(className = "jdk.internal.foreign.abi.SoftReferenceCache")
@BasedOnJDKClass(className = "jdk.internal.foreign.abi.NativeEntryPoint", innerClass = "CacheKey")
@Platforms(Platform.HOSTED_ONLY.class)
public final class NativeEntryPointHelper {

    /**
     * Extracts the information (i.e. {@link NativeEntryPointInfo}) required to create the given
     * {@link NativeEntryPoint}. This leverages the NativeEntryPoint cache (static field
     * {@code NEP_CACHE}) which stores this information as key. Unfortunately, since the cache is
     * private, it can only be extracted via guest metadata and constant access.
     *
     * @param nativeEntryPointConstant The {@link NativeEntryPoint} to extract the information for.
     * @return A {@link NativeEntryPointInfo} object encapsulating all information required to
     *         create the {@link NativeEntryPoint}.
     */
    public static NativeEntryPointInfo extractNativeEntryPointInfo(JavaConstant nativeEntryPointConstant) {
        JavaConstant cacheKeyConstant = findCacheKey(nativeEntryPointConstant);

        /*
         * In the common case, any instance of NativeEntryPoint is created via
         * 'NativeEntryPoint.make' and will therefore be added to NEP_CACHE and the keys of
         * NEP_CACHE are strongly referenced. However, we are defensive and do not fail if we
         * couldn't find the key.
         */
        if (cacheKeyConstant == null) {
            return null;
        }

        /*
         * Field 'CacheKey.abi' is ignored because the ABIDescriptor is JDK's equivalent of our
         * Substrate*RegisterConfig.
         */
        GuestAccess access = GuestAccess.get();
        GuestElements elements = access.elements;
        JavaConstant argMovesListConstant = JVMCIReflectionUtil.readInstanceField(cacheKeyConstant, elements.jdk_internal_foreign_abi_NativeEntryPoint_CacheKey, "argMoves");
        JavaConstant returnMovesListConstant = JVMCIReflectionUtil.readInstanceField(cacheKeyConstant, elements.jdk_internal_foreign_abi_NativeEntryPoint_CacheKey, "retMoves");
        JavaConstant emptyVmStorageArray = access.asArrayConstant(elements.jdk_internal_foreign_abi_VMStorage);
        VMStorage[] argMoves = access.asHostObject(VMStorage[].class,
                        access.invoke(elements.java_util_Collection_toArray_withArray, argMovesListConstant, emptyVmStorageArray));
        VMStorage[] returnMoves = access.asHostObject(VMStorage[].class,
                        access.invoke(elements.java_util_Collection_toArray_withArray, returnMovesListConstant, emptyVmStorageArray));
        MethodType methodType = access.asHostObject(MethodType.class,
                        access.invoke(elements.jdk_internal_foreign_abi_NativeEntryPoint_type, nativeEntryPointConstant));
        boolean needsReturnBuffer = JVMCIReflectionUtil.readInstanceField(cacheKeyConstant, elements.jdk_internal_foreign_abi_NativeEntryPoint_CacheKey, "needsReturnBuffer").asBoolean();
        int capturedStateMask = readCapturedStateMask(cacheKeyConstant);
        boolean needsTransition = JVMCIReflectionUtil.readInstanceField(cacheKeyConstant, elements.jdk_internal_foreign_abi_NativeEntryPoint_CacheKey, "needsTransition").asBoolean();
        boolean allowHeapAccess = Arrays.stream(argMoves).anyMatch(Objects::isNull);

        return NativeEntryPointInfo.make(argMoves, returnMoves, methodType, needsReturnBuffer, capturedStateMask, needsTransition, allowHeapAccess);
    }

    static JavaConstant findCacheKey(JavaConstant nativeEntryPointConstant) {
        GuestAccess access = GuestAccess.get();
        GuestElements elements = access.elements;
        JavaConstant nepCacheConstant = JVMCIReflectionUtil.readStaticField(elements.jdk_internal_foreign_abi_NativeEntryPoint, "NEP_CACHE");
        JavaConstant cacheConstant = JVMCIReflectionUtil.readInstanceField(nepCacheConstant, elements.jdk_internal_foreign_abi_SoftReferenceCache, "cache");
        JavaConstant entrySetConstant = access.invoke(elements.java_util_Map_entrySet, cacheConstant);
        JavaConstant entriesConstant = access.invoke(elements.java_util_Collection_toArray, entrySetConstant);
        Object[] entries = access.asHostObject(Object[].class, entriesConstant);

        // We have the value; search the key.
        for (Object entry : entries) {
            JavaConstant entryConstant = access.getSnippetReflection().forObject(entry);
            JavaConstant nodeConstant = access.invoke(elements.java_util_Map_Entry_getValue, entryConstant);
            JavaConstant refConstant = JVMCIReflectionUtil.readInstanceField(nodeConstant, elements.jdk_internal_foreign_abi_SoftReferenceCache_Node, "ref");
            if (!refConstant.isNull() && access.invoke(elements.java_lang_ref_Reference_refersTo, refConstant, nativeEntryPointConstant).asBoolean()) {
                return access.invoke(elements.java_util_Map_Entry_getKey, entryConstant);
            }
        }
        return null;
    }

    static int readCapturedStateMask(JavaConstant cacheKeyConstant) {
        return JVMCIReflectionUtil.readInstanceField(cacheKeyConstant, GuestAccess.get().elements.jdk_internal_foreign_abi_NativeEntryPoint_CacheKey, "capturedStateMask").asInt();
    }
}
