/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;

import com.oracle.svm.core.util.BasedOnJDKFile;

@CLibrary(value = "libchelper", requireStatic = true, dependsOn = "java")
public class LibCHelper {
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native CCharPointerPointer getEnviron();

    @CFunction(transition = Transition.TO_NATIVE)
    // Checkstyle: stop
    public static native CCharPointer SVM_FindJavaTZmd(CCharPointer tzMappings, int length);
    // Checkstyle: start

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+13/src/java.base/unix/native/libjava/locale_str.h")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+13/src/java.base/windows/native/libjava/locale_str.h")
    public static class Locale {
        @CFunction(transition = Transition.TO_NATIVE)
        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+17/src/java.base/unix/native/libjava/java_props_md.c#L93-L357")
        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+24/src/java.base/windows/native/libjava/java_props_md.c#L321-L713")
        public static native CCharPointerPointer parseDisplayLocale();

        @CFunction(transition = Transition.TO_NATIVE)
        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+17/src/java.base/unix/native/libjava/java_props_md.c#L93-L357")
        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+24/src/java.base/windows/native/libjava/java_props_md.c#L321-L713")
        public static native CCharPointerPointer parseFormatLocale();
    }
}
