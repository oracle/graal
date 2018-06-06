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
package com.oracle.svm.jni;

import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_1;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_2;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_4;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_6;
import static com.oracle.svm.jni.nativeapi.JNIVersion.JNI_VERSION_1_8;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.NativeLibrarySupport.LibraryInitializer;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport.NativeLibrary;
import com.oracle.svm.jni.functions.JNIFunctionTables;
import com.oracle.svm.jni.nativeapi.JNIJavaVM;

public class JNILibraryLoadFeature implements Feature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        NativeLibrarySupport.singleton().registerLibraryInitializer(new JNILibraryInitializer());
    }
}

interface JNIOnLoadFunctionPointer extends CFunctionPointer {
    @InvokeCFunctionPointer
    int invoke(JNIJavaVM vm, VoidPointer reserved);
}

class JNILibraryInitializer implements LibraryInitializer {
    private static String getOnLoadName(String libName, boolean builtIn) {
        String name = "JNI_OnLoad";
        if (builtIn) {
            return name + "_" + libName;
        }
        return name;
    }

    @Override
    public boolean isBuiltinLibrary(String libName) {
        String onLoadName = getOnLoadName(libName, true);
        PointerBase onLoad = PlatformNativeLibrarySupport.singleton().findBuiltinSymbol(onLoadName);
        return onLoad.isNonNull();
    }

    @Override
    public void initialize(NativeLibrary lib) {
        String name = getOnLoadName(lib.getCanonicalIdentifier(), lib.isBuiltin());
        PointerBase symbol = lib.findSymbol(name);
        if (symbol.isNonNull()) {
            JNIOnLoadFunctionPointer onLoad = (JNIOnLoadFunctionPointer) symbol;
            int expected = onLoad.invoke(JNIFunctionTables.singleton().getGlobalJavaVM(), WordFactory.nullPointer());
            if (expected != JNI_VERSION_1_8() && expected != JNI_VERSION_1_6() && expected != JNI_VERSION_1_4() && expected != JNI_VERSION_1_2() && expected != JNI_VERSION_1_1()) {
                String message = "Unsupported JNI version 0x" + Integer.toHexString(expected) + ", required by " + lib.getCanonicalIdentifier();
                throw new UnsatisfiedLinkError(message);
            }
        }
    }
}
