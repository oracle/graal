/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import java.util.IdentityHashMap;
import java.util.function.Function;
import org.graalvm.nativeimage.hosted.Feature.DuringSetupAccess;

/**
 * Fields that contain native pointers can not be part of the image heap, because the native
 * pointers refer to allocations from the image builder, and are not valid anymore at runtime.
 */
public final class NativeObjectReplacer implements Function<Object, Object> {

    private final IdentityHashMap<Class<?>, Object> disallowedClasses;

    NativeObjectReplacer(DuringSetupAccess access) {
        disallowedClasses = new IdentityHashMap<>(16);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.ClosureNativePointer"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.ClosureNativePointer$NativeDestructor"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.LibFFILibrary"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.LibFFISignature"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.LibFFISymbol"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.LibFFIType$ArrayType"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.LibFFIType$ClosureType"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.LibFFIType$EnvType"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.LibFFIType$NullableType"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.LibFFIType$ObjectType"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.LibFFIType$SimpleType"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.LibFFIType$StringType"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.LibFFIType$VoidType"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.NativeAllocation$FreeDestructor"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.NativePointer"), Boolean.FALSE);
        disallowedClasses.put(access.findClassByName("com.oracle.truffle.nfi.impl.NativeString"), Boolean.FALSE);
    }

    @Override
    public Object apply(Object obj) {
        if (disallowedClasses.containsKey(obj.getClass())) {
            throw new UnsupportedFeatureException(String.format("Native object (%s) stored in pre-initialized context.", obj.getClass().getSimpleName()));
        }
        return obj;
    }
}
