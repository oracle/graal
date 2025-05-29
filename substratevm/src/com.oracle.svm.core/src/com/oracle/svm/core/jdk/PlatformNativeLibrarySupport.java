/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.VMError;

public abstract class PlatformNativeLibrarySupport {

    public static final String[] defaultBuiltInLibraries = {
                    "java",
                    "nio",
                    "net"
    };

    private static final String[] defaultBuiltInPkgNatives = {
                    "com_sun_demo_jvmti_hprof",
                    "com_sun_java_util_jar_pack",
                    "com_sun_net_ssl",
                    "com_sun_nio_file",
                    "com_sun_security_cert_internal_x509",
                    "java_io",
                    "java_lang",
                    "java_math",
                    "java_net",
                    "java_nio",
                    "java_security",
                    "java_text",
                    "java_time",
                    "java_util",
                    "javax_net",
                    "javax_script",
                    "javax_security",
                    "jdk_internal_io",
                    "jdk_internal_jimage",
                    "jdk_internal_misc",
                    "jdk_internal_org",
                    "jdk_internal_platform",
                    "jdk_internal_util",
                    "jdk_internal_vm",
                    "jdk_net",
                    "sun_invoke",
                    "sun_launcher",
                    "sun_misc",
                    "sun_net",
                    "sun_nio",
                    "sun_reflect",
                    "sun_text",
                    "sun_util",

                    /* SVM Specific packages */
                    "com_oracle_svm_core_jdk"
    };

    private static final String[] defaultBuiltInPkgNativesBlocklist;

    static {
        ArrayList<String> blocklist = new ArrayList<>();
        Collections.addAll(blocklist,
                        "sun_security_krb5_SCDynamicStoreConfig_getKerberosConfig",
                        "sun_security_krb5_Config_getWindowsDirectory",
                        "jdk_internal_org_jline_terminal_impl_jna_win_Kernel32Impl",
                        "jdk_internal_misc_ScopedMemoryAccess_closeScope0",
                        "jdk_internal_misc_ScopedMemoryAccess_registerNatives",
                        "java_lang_invoke_VarHandle_weakCompareAndSetPlain",
                        "java_lang_invoke_VarHandle_weakCompareAndSetRelease",
                        "java_lang_invoke_VarHandle_getAndBitwiseAndAcquire",
                        "java_lang_invoke_VarHandle_getVolatile",
                        "java_lang_invoke_VarHandle_compareAndSet",
                        "java_lang_invoke_VarHandle_compareAndExchangeRelease",
                        "java_lang_invoke_VarHandle_getAndAddRelease",
                        "java_lang_invoke_VarHandle_getAndBitwiseOr",
                        "java_lang_invoke_VarHandle_getOpaque",
                        "java_lang_invoke_VarHandle_compareAndExchangeAcquire",
                        "java_lang_invoke_VarHandle_getAndBitwiseXorAcquire",
                        "java_lang_invoke_VarHandle_get",
                        "java_lang_invoke_VarHandle_setRelease",
                        "java_lang_invoke_VarHandle_setVolatile",
                        "java_lang_invoke_VarHandle_getAndBitwiseOrRelease",
                        "java_lang_invoke_VarHandle_getAndBitwiseAnd",
                        "java_lang_invoke_VarHandle_getAndBitwiseXorRelease",
                        "java_lang_invoke_VarHandle_weakCompareAndSet",
                        "java_lang_invoke_VarHandle_getAndSetRelease",
                        "java_lang_invoke_VarHandle_weakCompareAndSetAcquire",
                        "java_lang_invoke_VarHandle_setOpaque",
                        "java_lang_invoke_VarHandle_getAndBitwiseAndRelease",
                        "java_lang_invoke_VarHandle_getAndAdd",
                        "java_lang_invoke_VarHandle_getAndBitwiseXor",
                        "java_lang_invoke_VarHandle_getAndAddAcquire",
                        "java_lang_invoke_VarHandle_getAndSet",
                        "java_lang_invoke_VarHandle_getAndBitwiseOrAcquire",
                        "java_lang_invoke_VarHandle_set",
                        "java_lang_invoke_VarHandle_compareAndExchange",
                        "java_lang_invoke_VarHandle_getAcquire",
                        "java_lang_invoke_VarHandle_getAndSetAcquire",
                        "java_nio_MappedMemoryUtils_load0",
                        "java_nio_MappedMemoryUtils_unload0",
                        "java_nio_MappedMemoryUtils_isLoaded0",
                        "java_nio_MappedMemoryUtils_force0");
        defaultBuiltInPkgNativesBlocklist = blocklist.toArray(new String[0]);
    }

    public static PlatformNativeLibrarySupport singleton() {
        return ImageSingletons.lookup(PlatformNativeLibrarySupport.class);
    }

    protected PlatformNativeLibrarySupport() {
        builtInPkgNatives = new ArrayList<>();
        if (Platform.includedIn(InternalPlatform.PLATFORM_JNI.class)) {
            builtInPkgNatives.addAll(Arrays.asList(defaultBuiltInPkgNatives));
        }
    }

    /**
     * Determines if a library which has <em>not</em> been
     * {@linkplain NativeLibrarySupport#preregisterUninitializedBuiltinLibrary pre-registered}
     * during image generation is a built-in library.
     */
    public boolean isBuiltinLibrary(@SuppressWarnings("unused") String name) {
        return false;
    }

    private List<String> builtInPkgNatives;
    private boolean builtInPkgNativesSealed;

    public void addBuiltinPkgNativePrefix(String name) {
        if (builtInPkgNativesSealed) {
            throw VMError.shouldNotReachHere("Cannot register any more packages as built-ins because information has already been used.");
        }
        builtInPkgNatives.add(name);
    }

    public boolean isBuiltinPkgNative(String name) {
        builtInPkgNativesSealed = true;

        String commonPrefix = "Java_";
        if (name.startsWith(commonPrefix)) {
            String strippedName = name.substring(commonPrefix.length());
            for (String str : defaultBuiltInPkgNativesBlocklist) {
                if (strippedName.startsWith(str)) {
                    return false;
                }
            }
            for (String str : builtInPkgNatives) {
                if (strippedName.startsWith(str)) {
                    return true;
                }
            }
        }
        return false;
    }

    public interface NativeLibrary {

        String getCanonicalIdentifier();

        boolean isBuiltin();

        boolean load();

        boolean unload();

        boolean isLoaded();

        PointerBase findSymbol(String name);
    }

    public abstract NativeLibrary createLibrary(String canonical, boolean builtIn);

    public abstract PointerBase findBuiltinSymbol(String name);

    /**
     * Initializes built-in libraries during isolate creation.
     *
     * @see Isolates#isCurrentFirst()
     */
    public abstract boolean initializeBuiltinLibraries();
}

@AutomaticallyRegisteredFeature
class PlatformNativeLibrarySupportFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (Platform.includedIn(InternalPlatform.PLATFORM_JNI.class)) {
            for (String libName : PlatformNativeLibrarySupport.defaultBuiltInLibraries) {
                NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary(libName);
            }
        }
    }
}
