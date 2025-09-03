/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c.locale;

import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.util.BasedOnJDKFile;

@CContext(LocaleDirectives.class)
@CLibrary(value = "libchelper", requireStatic = true, dependsOn = "java")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+23/src/java.base/unix/native/libjava/locale_str.h")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+13/src/java.base/windows/native/libjava/locale_str.h")
class LocaleCHelper {
    // Checkstyle: stop
    @CConstant
    static native int SVM_LOCALE_INITIALIZATION_SUCCEEDED();

    @CConstant
    static native int SVM_LOCALE_INITIALIZATION_OUT_OF_MEMORY();
    // Checkstyle: resume

    /**
     * This method changes the process-wide locale settings and should therefore only be called
     * during early startup. Calling it at a later point in time is unsafe and may result in
     * crashes.
     *
     * @return {@link #SVM_LOCALE_INITIALIZATION_SUCCEEDED} or
     *         {@link #SVM_LOCALE_INITIALIZATION_OUT_OF_MEMORY}.
     */
    @CFunction(value = "svm_initialize_locale", transition = NO_TRANSITION)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+7/src/java.base/unix/native/libjava/java_props_md.c#L71-L359")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+17/src/java.base/unix/native/libjava/java_props_md.c#L436-L460")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+21/src/java.base/windows/native/libjava/java_props_md.c#L263-L721")
    static native int initializeLocale();

    @CFunction(value = "svm_get_locale", transition = NO_TRANSITION)
    static native LocaleProps getLocale();

    @CStruct(value = "svm_locale_props_t")
    interface LocaleProps extends PointerBase {
        @CField(value = "display_language")
        CCharPointer displayLanguage();

        @CField(value = "display_script")
        CCharPointer displayScript();

        @CField(value = "display_country")
        CCharPointer displayCountry();

        @CField(value = "display_variant")
        CCharPointer displayVariant();

        @CField(value = "format_language")
        CCharPointer formatLanguage();

        @CField(value = "format_script")
        CCharPointer formatScript();

        @CField(value = "format_country")
        CCharPointer formatCountry();

        @CField(value = "format_variant")
        CCharPointer formatVariant();
    }
}
