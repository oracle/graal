/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;

public abstract class JNIPlatformNativeLibrarySupport extends PlatformNativeLibrarySupport {

    @Platforms(InternalPlatform.PLATFORM_JNI.class)
    protected void loadJavaLibrary() {
        System.loadLibrary("java");

        Target_java_io_FileDescriptor_JNI.initIDs();
        Target_java_io_FileInputStream_JNI.initIDs();
        Target_java_io_FileOutputStream_JNI.initIDs();
        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            /*
             * Unlike JDK 8, where platform encoding is initialized from native code, in JDK 11 the
             * initialization of platform encoding is performed from Java during `System.initPhase1`
             * by `System.initProperties`. Hence, we do it here.
             *
             * Note that the value of `sun.jnu.encoding` property is determined at image build time,
             * see `SystemPropertiesSupport.HOSTED_PROPERTIES`.
             */
            try (CTypeConversion.CCharPointerHolder name = CTypeConversion.toCString(System.getProperty("sun.jnu.encoding"))) {
                initializeEncoding(CurrentIsolate.getCurrentThread(), name.get());
            }
        }
    }

    @CFunction("InitializeEncoding")
    private static native void initializeEncoding(PointerBase env, CCharPointer name);

    @Platforms(InternalPlatform.PLATFORM_JNI.class)
    protected void loadZipLibrary() {
        /*
         * On JDK 8, the zip library is loaded early during VM startup and not by individual class
         * initializers of classes that actually need the library. JDK 11 changed that behavior, the
         * zip library is properly loaded by classes that depend on it.
         *
         * Therefore, this helper method unconditionally loads the zip library for Java 8. The only
         * other alternative would be to substitute and modify all class initializers of classes
         * that depend on the zip library, which is complicated.
         */
        if (JavaVersionUtil.JAVA_SPEC == 8) {
            System.loadLibrary("zip");
        }
    }
}

@Platforms(InternalPlatform.PLATFORM_JNI.class)
@TargetClass(java.io.FileInputStream.class)
final class Target_java_io_FileInputStream_JNI {
    @Alias
    static native void initIDs();
}

@Platforms(InternalPlatform.PLATFORM_JNI.class)
@TargetClass(java.io.FileDescriptor.class)
final class Target_java_io_FileDescriptor_JNI {
    @Alias
    static native void initIDs();
}

@Platforms(InternalPlatform.PLATFORM_JNI.class)
@TargetClass(java.io.FileOutputStream.class)
final class Target_java_io_FileOutputStream_JNI {
    @Alias
    static native void initIDs();
}
