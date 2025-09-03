/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.libs.libjava.impl;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.jni.JniVersion;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions(type = "Ljdk/internal/loader/NativeLibraries;")
public final class Target_jdk_internal_loader_NativeLibraries {

    private static final String JNI_ONLOAD = "JNI_OnLoad";
    private static final NativeSignature JNI_ONLOAD_SIGNATURE = NativeSignature.create(NativeType.INT, NativeType.POINTER, NativeType.POINTER);

    private static final String JNI_ONUNLOAD = "JNI_OnUnLoad";
    private static final NativeSignature JNI_ONUNLOAD_SIGNATURE = NativeSignature.create(NativeType.INT, NativeType.POINTER, NativeType.POINTER);

    @Substitution
    @SuppressWarnings("unused")
    public static @JavaType(String.class) StaticObject findBuiltinLib(@JavaType(String.class) StaticObject name, @Inject EspressoContext ctx, @Inject Meta meta) {
        if (ctx.getNativeAccess().isBuiltIn(meta.toHostString(name))) {
            return name;
        }
        return StaticObject.NULL;
    }

    @Substitution
    @TruffleBoundary
    public static boolean load(@JavaType(internalName = "Ljdk/internal/loader/NativeLibraries$NativeLibraryImpl;") StaticObject impl,
                    @JavaType(String.class) StaticObject name,
                    boolean isBuiltin,
                    boolean throwExceptionIfFail,
                    @Inject EspressoContext ctx) {
        if (StaticObject.isNull(name)) {
            return false;
        }
        Meta meta = ctx.getMeta();
        String hostName = meta.toHostString(name);
        VM vm = ctx.getVM();
        if (isBuiltin && !ctx.getNativeAccess().isBuiltIn(hostName)) {
            return false;
        }
        // Load the library.
        TruffleObject handle = vm.JVM_LoadLibrary(hostName, throwExceptionIfFail);
        if (InteropLibrary.getUncached().isNull(handle)) {
            return false;
        }
        // Lookup JNI_OnLoad
        int jniVersion;
        long nativePtr = NativeUtils.interopAsPointer(handle);
        long onLoadHandle = vm.findLibraryEntry(nativePtr, JNI_ONLOAD);
        if (onLoadHandle == 0) {
            jniVersion = JniVersion.JNI_VERSION_1_1.version();
        } else {
            // Call JNI_OnLoad
            TruffleObject onLoad = vm.getFunction(onLoadHandle);
            TruffleObject boundOnLoad = ctx.getNativeAccess().bindSymbol(onLoad, JNI_ONLOAD_SIGNATURE);
            try {
                jniVersion = InteropLibrary.getUncached().asInt(InteropLibrary.getUncached().execute(boundOnLoad, vm.getJavaVM(), RawPointer.nullInstance()));
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }
        if (!vm.JVM_IsSupportedJNIVersion(jniVersion)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_UnsatisfiedLinkError, "unsupported JNI version 0x" + Integer.toString(jniVersion, 2) + " required by " + hostName);
        }
        meta.jdk_internal_loader_NativeLibraries$NativeLibraryImpl_handle.setLong(impl, nativePtr);
        meta.jdk_internal_loader_NativeLibraries$NativeLibraryImpl_jniVersion.setInt(impl, jniVersion);
        return true;
    }

    @Substitution
    @TruffleBoundary
    public static void unload(@JavaType(String.class) StaticObject name,
                    boolean isBuiltin,
                    long address,
                    @Inject EspressoContext ctx) {
        if (StaticObject.isNull(name)) {
            return;
        }
        if (isBuiltin) {
            return;
        }
        VM vm = ctx.getVM();
        // Lookup JNI_OnUnLoad
        long onUnLoadHandle = vm.findLibraryEntry(address, JNI_ONUNLOAD);
        if (onUnLoadHandle != 0) {
            // Call JNI_OnUnLoad
            TruffleObject onUnLoad = vm.getFunction(onUnLoadHandle);
            TruffleObject boundOnUnLoad = ctx.getNativeAccess().bindSymbol(onUnLoad, JNI_ONUNLOAD_SIGNATURE);
            try {
                InteropLibrary.getUncached().asInt(InteropLibrary.getUncached().execute(boundOnUnLoad, vm.getJavaVM(), RawPointer.nullInstance()));
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
            vm.JVM_UnloadLibrary(RawPointer.create(address));
        }
    }

    @Substitution
    @TruffleBoundary
    public static long findEntry0(long handle, @JavaType(String.class) StaticObject name,
                    @Inject EspressoContext ctx) {
        return ctx.getVM().findLibraryEntry(handle, ctx.getMeta().toHostString(name));
    }

}
