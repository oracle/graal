/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.nfi.posix;

import static com.oracle.svm.core.posix.headers.Dlfcn.GNUExtensions.LM_ID_NEWLM;

import com.oracle.svm.core.posix.linux.libc.GLibC;
import com.oracle.svm.core.c.libc.LibCBase;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Dlfcn;
import com.oracle.svm.core.posix.headers.Dlfcn.GNUExtensions.Lmid_t;
import com.oracle.svm.core.posix.headers.Dlfcn.GNUExtensions.Lmid_tPointer;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.truffle.nfi.Target_com_oracle_truffle_nfi_impl_NFIUnsatisfiedLinkError;
import com.oracle.svm.truffle.nfi.TruffleNFISupport;
import com.oracle.truffle.api.CompilerDirectives;

@AutomaticFeature
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public final class PosixTruffleNFIFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        PosixTruffleNFISupport.initialize();
    }
}

final class PosixTruffleNFISupport extends TruffleNFISupport {

    private static final int ISOLATED_NAMESPACE_FLAG = 0x10000;
    private static final int ISOLATED_NAMESPACE_NOT_SUPPORTED_FLAG = 0;

    static int isolatedNamespaceFlag = ISOLATED_NAMESPACE_NOT_SUPPORTED_FLAG;

    static void initialize() {
        if (Platform.includedIn(Platform.LINUX.class)) {
            isolatedNamespaceFlag = LibCBase.singleton().hasIsolatedNamespaces() ? ISOLATED_NAMESPACE_FLAG : ISOLATED_NAMESPACE_NOT_SUPPORTED_FLAG;
        }
        ImageSingletons.add(TruffleNFISupport.class, new PosixTruffleNFISupport());
    }

    private PosixTruffleNFISupport() {
        super(getErrnoGetterFunctionName());
    }

    private static String getErrnoGetterFunctionName() {
        if (Platform.includedIn(Platform.LINUX.class)) {
            return "__errno_location";
        }
        if (Platform.includedIn(Platform.DARWIN.class)) {
            return "__error";
        }
        throw VMError.unsupportedFeature("unsupported platform for TruffleNFIFeature");
    }

    @Override
    protected CCharPointer strdupImpl(CCharPointer src) {
        return LibC.strdup(src);
    }

    private static PointerBase dlmopen(Lmid_t lmid, String filename, int mode) {
        try (CTypeConversion.CCharPointerHolder pathPin = CTypeConversion.toCString(filename)) {
            CCharPointer pathPtr = pathPin.get();
            return Dlfcn.GNUExtensions.dlmopen(lmid, pathPtr, mode);
        }
    }

    /**
     * A single linking namespace is created lazily and registered on the NFI context instance.
     */
    private static PointerBase loadLibraryInNamespace(long nativeContext, String name, int mode) {
        assert (mode & isolatedNamespaceFlag) == 0;
        Target_com_oracle_truffle_nfi_impl_NFIContextLinux context = //
                        KnownIntrinsics.convertUnknownValue(getContext(nativeContext), Target_com_oracle_truffle_nfi_impl_NFIContextLinux.class);

        // Double-checked locking on the NFI context instance.
        long namespaceId = context.isolatedNamespaceId;
        if (namespaceId == 0) {
            // Checkstyle: allow synchronization
            synchronized (context) {
                namespaceId = context.isolatedNamespaceId;
                if (namespaceId == 0) {
                    PointerBase handle = dlmopen(WordFactory.signed(LM_ID_NEWLM()), name, mode);
                    if (handle.equal(WordFactory.zero())) {
                        return handle;
                    }

                    Lmid_tPointer namespacePtr = StackValue.get(Lmid_tPointer.class);
                    int ret = Dlfcn.GNUExtensions.dlinfo(handle, Dlfcn.GNUExtensions.RTLD_DI_LMID(), namespacePtr);
                    if (ret != 0) {
                        CompilerDirectives.transferToInterpreter();
                        String error = PosixUtils.dlerror();
                        throw VMError.shouldNotReachHere("dlinfo failed to obtain link-map list (namespace) of '" + name + "': " + error);
                    }
                    namespaceId = namespacePtr.read().rawValue();
                    assert namespaceId != 0;
                    context.isolatedNamespaceId = namespaceId;
                    return handle;
                }
            }
        }

        // Namespace already created.
        assert namespaceId != 0;
        return dlmopen(WordFactory.signed(namespaceId), name, mode);
    }

    @Override
    protected long loadLibraryImpl(long nativeContext, String name, int flags) {
        PointerBase handle;
        if (Platform.includedIn(Platform.LINUX.class) && LibCBase.targetLibCIs(GLibC.class) && (flags & isolatedNamespaceFlag) != 0) {
            handle = loadLibraryInNamespace(nativeContext, name, flags & ~isolatedNamespaceFlag);
        } else {
            handle = PosixUtils.dlopen(name, flags);
        }
        if (handle.equal(WordFactory.zero())) {
            CompilerDirectives.transferToInterpreter();
            String error = PosixUtils.dlerror();
            throw new UnsatisfiedLinkError(error);
        }
        return handle.rawValue();
    }

    @Override
    protected void freeLibraryImpl(long library) {
        Dlfcn.dlclose(WordFactory.pointer(library));
    }

    @Override
    protected long lookupImpl(long nativeContext, long library, String name) {
        // clear previous error
        Dlfcn.dlerror();
        PlatformNativeLibrarySupport nativeLibrarySupport = PlatformNativeLibrarySupport.singleton();

        PointerBase ret;
        if (library == 0) {
            ret = nativeLibrarySupport.findBuiltinSymbol(name);
        } else {
            ret = PosixUtils.dlsym(WordFactory.pointer(library), name);
        }

        if (ret.equal(WordFactory.zero())) {
            CompilerDirectives.transferToInterpreter();
            String error = PosixUtils.dlerror();
            if (error != null) {
                throw KnownIntrinsics.convertUnknownValue(new Target_com_oracle_truffle_nfi_impl_NFIUnsatisfiedLinkError(error), UnsatisfiedLinkError.class);
            }
        }
        return ret.rawValue();
    }
}
