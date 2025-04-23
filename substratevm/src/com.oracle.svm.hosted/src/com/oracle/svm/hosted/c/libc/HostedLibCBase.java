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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.c.libc.LibCSpecific;
import com.oracle.svm.hosted.image.AbstractImage;

public interface HostedLibCBase extends LibCBase {

    static HostedLibCBase singleton() {
        return (HostedLibCBase) ImageSingletons.lookup(LibCBase.class);
    }

    static boolean containsLibCAnnotation(AnnotatedElement element) {
        return AnnotationAccess.getAnnotation(element, LibCSpecific.class) != null;
    }

    static boolean isProvidedInCurrentLibc(AnnotatedElement element) {
        LibCSpecific targetLibC = AnnotationAccess.getAnnotation(element, LibCSpecific.class);
        if (targetLibC == null) {
            return false;
        }
        LibCBase currentLibC = ImageSingletons.lookup(LibCBase.class);
        for (Class<? extends LibCBase> aClass : targetLibC.value()) {
            if (aClass.isAssignableFrom(currentLibC.getClass())) {
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
     * A type is regarded a provided in the current libc implementation if it is annotated and the
     * current libc implementation is listed in the annotation. If the type is not annotated, then
     * the above check is successively applied to the enclosing types, if they exist. Finally, if
     * the class is in a package, the above check is applied. If the package does not exist or is
     * not annotated, the type is regarded as provided.
     *
     * @param clazz Type to check if contained in the current libc implementation.
     * @return true if contained in the current libc implementation, false otherwise.
     */
    static boolean isTypeProvidedInCurrentLibc(Class<?> clazz) {
        Class<?> currentClazz = clazz;
        while (currentClazz != null) {
            if (containsLibCAnnotation(currentClazz)) {
                return isProvidedInCurrentLibc(currentClazz);
            }
            currentClazz = currentClazz.getEnclosingClass();
        }
        Package clazzPackage = clazz.getPackage();
        if (clazzPackage != null) {
            return !containsLibCAnnotation(clazz) || isProvidedInCurrentLibc(clazzPackage);
        }
        return true;
    }

    static boolean isMethodProvidedInCurrentLibc(Method method) {
        if (containsLibCAnnotation(method) && !isProvidedInCurrentLibc(method)) {
            return false;
        }
        Class<?> declaringClass = method.getDeclaringClass();
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
