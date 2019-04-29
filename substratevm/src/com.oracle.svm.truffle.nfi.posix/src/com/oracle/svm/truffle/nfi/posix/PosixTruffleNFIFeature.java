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
package com.oracle.svm.truffle.nfi.posix;

import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Dlfcn;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.truffle.nfi.Target_com_oracle_truffle_nfi_impl_NFIUnsatisfiedLinkError;
import com.oracle.truffle.api.CompilerDirectives;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.truffle.nfi.TruffleNFISupport;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

@AutomaticFeature
@Platforms({InternalPlatform.LINUX_AND_JNI.class, InternalPlatform.DARWIN_AND_JNI.class})
public final class PosixTruffleNFIFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        PosixTruffleNFISupport.initialize();
    }
}

final class PosixTruffleNFISupport extends TruffleNFISupport {
    static void initialize() {
        ImageSingletons.add(TruffleNFISupport.class, new PosixTruffleNFISupport());
    }

    private PosixTruffleNFISupport() {
        super(getErrnoGetterFunctionName());
    }

    private static String getErrnoGetterFunctionName() {
        if (Platform.includedIn(InternalPlatform.LINUX_AND_JNI.class)) {
            return "__errno_location";
        }
        if (Platform.includedIn(InternalPlatform.DARWIN_AND_JNI.class)) {
            return "__error";
        }
        throw VMError.unsupportedFeature("unsupported platform for TruffleNFIFeature");
    }

    @Override
    protected CCharPointer strdupImpl(CCharPointer src) {
        return LibC.strdup(src);
    }

    @Override
    protected long loadLibraryImpl(long nativeContext, String name, int flags) {
        PointerBase ret = PosixUtils.dlopen(name, flags);
        if (ret.equal(WordFactory.zero())) {
            CompilerDirectives.transferToInterpreter();
            String error = PosixUtils.dlerror();
            throw new UnsatisfiedLinkError(error);
        }
        return ret.rawValue();
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
