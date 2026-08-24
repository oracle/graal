/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.libc;

import com.oracle.svm.hosted.LibCSpecificGuestValue;
import java.util.List;
import java.util.Locale;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.c.libc.LibCSpecific;
import com.oracle.svm.hosted.image.AbstractImage;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.GuestAnnotationAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.graal.compiler.vmaccess.ResolvedJavaPackage;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

public interface HostedLibCBase extends LibCBase {

    static HostedLibCBase singleton() {
        return (HostedLibCBase) ImageSingletons.lookup(LibCBase.class);
    }

    static boolean containsLibCAnnotation(Annotated element) {
        return GuestAnnotationAccess.getAnnotationValue(element, LibCSpecific.class) != null;
    }

    static boolean isProvidedInCurrentLibc(Annotated element) {
        LibCSpecificGuestValue targetLibC = LibCSpecificGuestValue.get(element);
        if (targetLibC == null) {
            return false;
        }
        LibCBase currentLibC = ImageSingletons.lookup(LibCBase.class);
        var currentLibCType = GuestAccess.get().lookupType(currentLibC.getClass());
        for (var libCType : targetLibC.value()) {
            if (libCType.isAssignableFrom(currentLibCType)) {
                return true;
            }
        }
        return false;
    }

    static boolean isPlatformEquivalent(Class<? extends Platform> platformClass) {
        Platform platform = ImageSingletons.lookup(Platform.class);
        // Checkstyle: allow Class.getSimpleName
        String simpleName = platformClass.getSimpleName();
        // Checkstyle: disallow Class.getSimpleName
        return simpleName.toLowerCase(Locale.ROOT).equals(platform.getOS()) || Platform.includedIn(platformClass);
    }

    /**
     * Checks if the type is provided in the current libc implementation.
     *
     * A type is regarded as provided in the current libc implementation if it is annotated and the
     * current libc implementation is listed in the annotation. If the type is not annotated, then
     * the above check is successively applied to the enclosing types, if they exist. Finally, if
     * the class is in a package, the above check is applied. If the package does not exist or is
     * not annotated, the type is regarded as provided.
     *
     * @param type Type to check if contained in the current libc implementation.
     * @return true if contained in the current libc implementation, false otherwise.
     */
    static boolean isTypeProvidedInCurrentLibc(ResolvedJavaType type) {
        ResolvedJavaType currentType = type;
        while (currentType != null) {
            if (containsLibCAnnotation(currentType)) {
                return isProvidedInCurrentLibc(currentType);
            }
            currentType = currentType.getEnclosingType();
        }
        ResolvedJavaPackage typePackage = JVMCIReflectionUtil.getPackage(type);
        if (typePackage != null) {
            return !containsLibCAnnotation(type) || isProvidedInCurrentLibc(typePackage);
        }
        return true;
    }

    static boolean isMethodProvidedInCurrentLibc(ResolvedJavaMethod method) {
        if (containsLibCAnnotation(method) && !isProvidedInCurrentLibc(method)) {
            return false;
        }
        ResolvedJavaType declaringClass = method.getDeclaringClass();
        return isTypeProvidedInCurrentLibc(declaringClass);
    }

    String getTargetCompiler();

    List<String> getAdditionalQueryCodeCompilerOptions();

    @SuppressWarnings("unused")
    default List<String> getAdditionalLinkerOptions(AbstractImage.NativeImageKind imageKind) {
        return List.of();
    }

    /**
     * Checks if static JDK libraries compiled with the target libC are mandatory for building the
     * native-image.
     *
     * This exists to support building native-images on older JDK versions as well as to support
     * special cases, like Bionic libc.
     */
    boolean requiresLibCSpecificStaticJDKLibraries();

}
