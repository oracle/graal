/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import java.util.List;
import java.util.Set;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

public abstract class JNIPlatformNativeLibrarySupport extends PlatformNativeLibrarySupport {

    public static JNIPlatformNativeLibrarySupport singleton() {
        return (JNIPlatformNativeLibrarySupport) PlatformNativeLibrarySupport.singleton();
    }

    private Set<String> javaLibrarySymbols;

    private boolean javaLibraryDynamic;

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setJavaLibrarySymbols(List<String> symbolNames) {
        javaLibrarySymbols = Set.copyOf(symbolNames);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setJavaLibraryDynamic(boolean dynamic) {
        javaLibraryDynamic = dynamic;
    }

    @Fold
    protected static boolean isJavaLibraryDynamic() {
        return singleton().javaLibraryDynamic;
    }

    /**
     * Looks up a symbol in the table generated for statically linked JNI libraries.
     */
    public static PointerBase svmFindBuiltinSymbol(String name) {
        try (CTypeConversion.CCharPointerHolder nameHolder = CTypeConversion.toCString(name)) {
            return svmFindBuiltinSymbol(nameHolder.get());
        }
    }

    @CFunction(value = "__svm_find_builtin_symbol", transition = NO_TRANSITION)
    private static native Pointer svmFindBuiltinSymbol(CCharPointer name);

    private volatile NativeLibrary dynamicJavaLibrary;

    private NativeLibrary getDynamicJavaLibrary() {
        VMError.guarantee(isJavaLibraryDynamic(), "The java library is statically linked");
        VMError.guarantee(dynamicJavaLibrary != null, "The dynamically linked java library has not been loaded");
        return dynamicJavaLibrary;
    }

    /** Looks up a symbol in the dynamically or statically linked Java library. */
    public PointerBase findJavaLibrarySymbol(String symbolName) {
        if (isJavaLibraryDynamic()) {
            NativeLibrary library = getDynamicJavaLibrary();
            assert !library.isBuiltin();
            return library.findSymbol(symbolName);
        } else if (javaLibrarySymbols.contains(symbolName)) {
            assert PlatformNativeLibrarySupport.singleton().isBuiltinNative(symbolName);
            VMError.guarantee(RuntimeClassLoading.isSupported(), "Built-in linkage should have been resolved at native image build-time.");
            return svmFindBuiltinSymbol(symbolName);
        }
        return Word.nullPointer();
    }

    @SuppressWarnings("restricted")
    @Platforms(InternalPlatform.PLATFORM_JNI.class)
    protected void loadJavaLibrary() {
        if (isJavaLibraryDynamic()) {
            VMError.guarantee(dynamicJavaLibrary == null, "The dynamically linked java library has already been loaded");
            dynamicJavaLibrary = NativeLibrarySupport.singleton().loadDynamicJavaLibrary();
        } else {
            NativeLibrarySupport.singleton().loadBuiltinJavaLibrary();
        }

        Target_java_io_FileDescriptor_JNI.initIDs();
        Target_java_io_FileInputStream_JNI.initIDs();
        Target_java_io_FileOutputStream_JNI.initIDs();

        initializeEncoding();
    }

    /**
     * Initializes the encoding used to convert from C strings to Java strings.
     *
     * The JDK C code has a {@code fastEncoding} field that determines which algorithm to use to
     * convert between C strings and Java strings; the conversion is implemented in the JDK C code,
     * instead of going through a regular JNI conversion (hence the "fast encoding" name). The
     * {@code fastEncoding} field must be initialized before the encoding can be used. We need to
     * ensure that the initialization code runs early, for the following reasons:
     *
     * The initialization is performed from Java during `System.initPhase1` by
     * `System.initProperties`. However, we do not invoke that part of the system initialization
     * because we already have many system properties pre-initialized in the image heap. So we need
     * to force initialization here.
     *
     * The choice of {@code fastEncoding} depends on the value of the {@code sun.jnu.encoding}
     * system property. This encoding is also known as the <em>platform encoding</em> or <em>JNU
     * encoding</em>. It is platform-dependent, and is mainly used for platform-dependent tasks,
     * (e.g. for file paths, or in our case, converting from C to Java Strings).
     *
     * Currently, we inherit the {@code sun.jnu.encoding} system property from image build time (see
     * {@link SystemPropertiesSupport}), i.e., we do not allow it to be specified at run time and
     * (more importantly) also do not look at environment variables to determine the encoding.
     */
    private void initializeEncoding() {
        try (CTypeConversion.CCharPointerHolder name = CTypeConversion.toCString(System.getProperty("sun.jnu.encoding"))) {
            if (isJavaLibraryDynamic()) {
                PointerBase initializeEncoding = getDynamicJavaLibrary().findSymbol("InitializeEncoding");
                VMError.guarantee(initializeEncoding.isNonNull(), "Missing InitializeEncoding in the java library");
                ((InitializeEncodingFunctionPointer) initializeEncoding).invoke(CurrentIsolate.getCurrentThread(), name.get());
            } else {
                nativeInitializeEncoding(CurrentIsolate.getCurrentThread(), name.get());
            }
        }
    }

    @CFunction("InitializeEncoding")
    private static native void nativeInitializeEncoding(PointerBase env, CCharPointer name);

    private interface InitializeEncodingFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(PointerBase env, CCharPointer name);
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

@Platforms({Platform.DARWIN.class, Platform.LINUX.class})
@CLibrary("z")
class ZLib {
}
