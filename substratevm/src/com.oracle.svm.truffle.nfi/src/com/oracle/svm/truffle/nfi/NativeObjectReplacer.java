/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.nfi;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.function.Function;

import org.graalvm.nativeimage.hosted.Feature.DuringSetupAccess;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ClassUtil;

/**
 * Fields that contain native pointers can not be part of the image heap, because the native
 * pointers refer to allocations from the image builder, and are not valid anymore at runtime.
 */
public final class NativeObjectReplacer implements Function<Object, Object> {

    private final IdentityHashMap<Class<?>, Object> disallowedClasses;

    private final Class<?> nativePointer;
    private final Field nativePointerField;

    NativeObjectReplacer(DuringSetupAccess access) {
        disallowedClasses = new IdentityHashMap<>(16);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.backend.libffi.ClosureNativePointer"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.backend.libffi.ClosureNativePointer$NativeDestructor"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.backend.libffi.LibFFILibrary"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.backend.libffi.LibFFISignature"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.backend.libffi.LibFFISymbol"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.backend.libffi.LibFFIType"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.backend.libffi.NativeAllocation$FreeDestructor"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.backend.libffi.NativeString"), Boolean.FALSE);

        nativePointer = access.findClassByName("com.oracle.truffle.nfi.backend.libffi.NativePointer");
        disallowedClasses.put(nativePointer, Boolean.FALSE);

        try {
            nativePointerField = nativePointer.getSuperclass().getDeclaredField("nativePointer");
            nativePointerField.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    @Override
    public Object apply(Object obj) {
        if (obj.getClass() == nativePointer) {
            long ptr;
            try {
                ptr = nativePointerField.getLong(obj);
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
            if (ptr == 0) {
                // special case: the NULL pointer can be safely stored
                return obj;
            }
        }
        if (disallowedClasses.containsKey(obj.getClass())) {
            throw new UnsupportedFeatureException(String.format("Native object (%s) stored in pre-initialized context.", ClassUtil.getUnqualifiedName(obj.getClass())));
        }
        return obj;
    }
}
