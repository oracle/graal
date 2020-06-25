/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.jni.functions.JNIFunctionTables;
import com.oracle.svm.jni.nativeapi.JNIJavaVM;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_1;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_2;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_4;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_6;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_8;

interface JNIOnLoadFunctionPointer extends CFunctionPointer {
    @InvokeCFunctionPointer
    int invoke(JNIJavaVM vm, VoidPointer reserved);
}

public class JNILibraryInitializer implements NativeLibrarySupport.LibraryInitializer {

    private final EconomicMap<String, CGlobalData<PointerBase>> onLoadCGlobalDataMap = EconomicMap.create(Equivalence.IDENTITY);

    public static String getOnLoadName(String libName, boolean isBuiltIn) {
        String name = "JNI_OnLoad";
        if (isBuiltIn) {
            return name + "_" + libName;
        }
        return name;
    }

    private static void callOnLoadFunction(String libName, PointerBase onLoadFunction) {
        if (onLoadFunction.isNonNull()) {
            JNIOnLoadFunctionPointer onLoad = (JNIOnLoadFunctionPointer) onLoadFunction;
            int expected = onLoad.invoke(JNIFunctionTables.singleton().getGlobalJavaVM(), WordFactory.nullPointer());
            checkSupportedJNIVersion(libName, expected);
        }
    }

    private static void checkSupportedJNIVersion(String libName, int expected) {
        if (expected != JNI_VERSION_1_8() && expected != JNI_VERSION_1_6() && expected != JNI_VERSION_1_4() && expected != JNI_VERSION_1_2() && expected != JNI_VERSION_1_1()) {
            String message = "Unsupported JNI version 0x" + Integer.toHexString(expected) + ", required by " + libName;
            throw new UnsatisfiedLinkError(message);
        }
    }

    public boolean fillCGlobalDataMap(Collection<String> staticLibNames) {
        List<String> libsWithOnLoad = Arrays.asList("net", "java", "nio", "zip", "sunec", "jaas", "sctp", "extnet", "j2gss", "j2pkcs11", "j2pcsc", "prefs", "verify");
        // TODO: This check should be removed when all static libs will have JNI_OnLoad function
        ArrayList<String> localStaticLibNames = new ArrayList<>(staticLibNames);
        localStaticLibNames.retainAll(libsWithOnLoad);
        boolean mapIsChanged = false;
        for (String libName : localStaticLibNames) {
            if (!onLoadCGlobalDataMap.containsKey(libName)) {
                CGlobalData<PointerBase> onLoadCGlobalData = CGlobalDataFactory.forSymbol(getOnLoadName(libName, true), true);
                onLoadCGlobalDataMap.put(libName, onLoadCGlobalData);
                mapIsChanged = true;
            }
        }
        return mapIsChanged;
    }

    @Override
    public boolean isBuiltinLibrary(String libName) {
        if (PlatformNativeLibrarySupport.singleton().isBuiltinLibrary(libName)) {
            return true;
        }
        String onLoadName = getOnLoadName(libName, true);
        PointerBase onLoad = PlatformNativeLibrarySupport.singleton().findBuiltinSymbol(onLoadName);
        return onLoad.isNonNull();
    }

    @Override
    public void initialize(PlatformNativeLibrarySupport.NativeLibrary lib) {
        String libName = lib.getCanonicalIdentifier();
        PointerBase onLoadFunction;
        if (lib.isBuiltin()) {
            onLoadFunction = getOnLoadSymbolAddress(libName);
            if (onLoadFunction.isNull()) {
                /*
                 * If pointer for static library not found, try to initialize library as shared
                 */
                String symbolName = getOnLoadName(libName, true);
                onLoadFunction = lib.findSymbol(symbolName);
            }
        } else {
            String symbolName = getOnLoadName(libName, false);
            onLoadFunction = lib.findSymbol(symbolName);
        }
        callOnLoadFunction(libName, onLoadFunction);
    }

    private PointerBase getOnLoadSymbolAddress(String libName) {
        CGlobalData<PointerBase> symbol = onLoadCGlobalDataMap.get(libName);
        return symbol == null ? WordFactory.nullPointer() : symbol.get();
    }
}
