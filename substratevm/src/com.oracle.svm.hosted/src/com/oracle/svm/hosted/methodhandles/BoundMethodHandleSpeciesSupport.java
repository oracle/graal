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
package com.oracle.svm.hosted.methodhandles;

import com.oracle.svm.shared.util.BasedOnJDKClass;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

/** Recreates generated bound method handle classes in the VM being analyzed. */
@BasedOnJDKClass(className = "java.lang.invoke.BoundMethodHandle")
@BasedOnJDKClass(className = "java.lang.invoke.ClassSpecializer")
@BasedOnJDKClass(className = "java.lang.invoke.ClassSpecializer", innerClass = "SpeciesData")
public final class BoundMethodHandleSpeciesSupport {
    private BoundMethodHandleSpeciesSupport() {
    }

    /** Returns the JDK's species key, or {@code null} if the type has no linked species data. */
    public static String getSpeciesKey(ResolvedJavaType type) {
        var guestAccess = GuestAccess.get();
        if (!guestAccess.elements.java_lang_invoke_BoundMethodHandle.equals(type.getSuperclass())) {
            return null;
        }
        JavaConstant speciesData = JVMCIReflectionUtil.readStaticField(type, "BMH_SPECIES");
        if (speciesData == null || speciesData.isNull()) {
            /* A pregenerated class can be loaded before the specializer links its species data. */
            return null;
        }
        var speciesDataType = guestAccess.lookupBootClassLoaderType("java.lang.invoke.ClassSpecializer$SpeciesData");
        var key = guestAccess.lookupMethod(speciesDataType, "key");
        return guestAccess.asHostString(guestAccess.invoke(key, speciesData));
    }

    /**
     * Recreates a species from its persisted key before a base-layer placeholder is published.
     * Later method handle creation then finds the same class through the specializer's cache.
     */
    public static Class<?> recreateSpecies(String species) {
        var guestAccess = GuestAccess.get();
        var constantReflection = guestAccess.getProviders().getConstantReflection();
        ResolvedJavaType boundMethodHandle = guestAccess.elements.java_lang_invoke_BoundMethodHandle;
        boundMethodHandle.initialize();
        JavaConstant specializer = JVMCIReflectionUtil.readStaticField(boundMethodHandle, "SPECIALIZER");
        var classSpecializer = guestAccess.lookupBootClassLoaderType("java.lang.invoke.ClassSpecializer");
        var findSpecies = guestAccess.lookupMethod(classSpecializer, "findSpecies", Object.class);
        JavaConstant speciesData = guestAccess.invoke(findSpecies, specializer, guestAccess.asGuestString(species));
        var speciesDataType = guestAccess.lookupBootClassLoaderType("java.lang.invoke.ClassSpecializer$SpeciesData");
        var speciesCode = guestAccess.lookupMethod(speciesDataType, "speciesCode");
        JavaConstant speciesClass = guestAccess.invoke(speciesCode, speciesData);
        return OriginalClassProvider.getJavaClass(constantReflection.asJavaType(speciesClass));
    }
}
