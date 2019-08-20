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
package com.oracle.svm.truffle.nfi.windows;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.windows.WindowsUtils;
import com.oracle.svm.core.windows.headers.LibC;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinBase.HMODULE;
import com.oracle.svm.truffle.nfi.Target_com_oracle_truffle_nfi_impl_NFIUnsatisfiedLinkError;
import com.oracle.svm.truffle.nfi.TruffleNFISupport;
import com.oracle.truffle.api.CompilerDirectives;

@AutomaticFeature
@Platforms(Platform.WINDOWS.class)
public final class WindowsTruffleNFIFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        WindowsTruffleNFISupport.initialize();
    }
}

final class WindowsTruffleNFISupport extends TruffleNFISupport {
    static void initialize() {
        ImageSingletons.add(TruffleNFISupport.class, new WindowsTruffleNFISupport());
    }

    private WindowsTruffleNFISupport() {
        super("_errno");
    }

    @Override
    protected CCharPointer strdupImpl(CCharPointer src) {
        return LibC.strdup(src);
    }

    @Override
    protected long loadLibraryImpl(long nativeContext, String name, int flags) {
        String dllPath = name;
        CTypeConversion.CCharPointerHolder dllpathPin = CTypeConversion.toCString(dllPath);
        CCharPointer dllPathPtr = dllpathPin.get();
        /*
         * WinBase.SetDllDirectoryA(dllpathPtr); CCharPointerHolder pathPin =
         * CTypeConversion.toCString(path); CCharPointer pathPtr = pathPin.get();
         */
        HMODULE dlhandle = WinBase.LoadLibraryA(dllPathPtr);
        if (dlhandle.isNull()) {
            CompilerDirectives.transferToInterpreter();
            throw new UnsatisfiedLinkError(WindowsUtils.lastErrorString(dllPath));
        }
        return dlhandle.rawValue();
    }

    @Override
    protected void freeLibraryImpl(long library) {
        WinBase.FreeLibrary(WordFactory.pointer(library));
    }

    @Override
    protected long lookupImpl(long nativeContext, long library, String name) {
        // clear previous error
        // Dlfcn.dlerror();
        PlatformNativeLibrarySupport nativeLibrarySupport = PlatformNativeLibrarySupport.singleton();

        PointerBase ret;
        if (library == 0) {
            ret = nativeLibrarySupport.findBuiltinSymbol(name);
        } else {
            try (CTypeConversion.CCharPointerHolder symbol = CTypeConversion.toCString(name)) {
                ret = WinBase.GetProcAddress(WordFactory.pointer(library), symbol.get());
            }
        }

        if (ret.isNull()) {
            CompilerDirectives.transferToInterpreter();
            throw KnownIntrinsics.convertUnknownValue(new Target_com_oracle_truffle_nfi_impl_NFIUnsatisfiedLinkError(WindowsUtils.lastErrorString(name)), UnsatisfiedLinkError.class);
        }
        return ret.rawValue();
    }
}
