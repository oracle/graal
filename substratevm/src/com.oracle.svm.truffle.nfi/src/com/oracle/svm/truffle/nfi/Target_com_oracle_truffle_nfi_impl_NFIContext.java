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
package com.oracle.svm.truffle.nfi;

import static com.oracle.svm.truffle.nfi.NativeSignature.ExecuteHelper;

import com.oracle.svm.core.snippets.KnownIntrinsics;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.truffle.nfi.NativeAPI.NativeTruffleContext;
import com.oracle.svm.truffle.nfi.NativeAPI.NativeTruffleEnv;
import com.oracle.svm.truffle.nfi.NativeSignature.CifData;
import com.oracle.svm.truffle.nfi.NativeSignature.PrepareHelper;
import com.oracle.svm.truffle.nfi.libffi.LibFFI;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_cif;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.nfi.impl.NFILanguageImpl;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@TargetClass(className = "com.oracle.truffle.nfi.impl.NFIContext", onlyWith = TruffleNFIFeature.IsEnabled.class)
final class Target_com_oracle_truffle_nfi_impl_NFIContext {

    @Alias private NFILanguageImpl language;

    // clear these fields, they will be re-filled by patchContext
    @Alias @RecomputeFieldValue(kind = Kind.Reset) private long nativeContext;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = TypeMapResetter.class) Target_com_oracle_truffle_nfi_impl_LibFFIType[] simpleTypeMap;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = TypeMapResetter.class) Target_com_oracle_truffle_nfi_impl_LibFFIType[] arrayTypeMap;

    private static class TypeMapResetter implements RecomputeFieldValue.CustomFieldValueComputer {

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            return new Target_com_oracle_truffle_nfi_impl_LibFFIType[NativeSimpleType.values().length];
        }
    }

    @Alias
    native long getNativeEnv();

    @Alias
    native Target_com_oracle_truffle_nfi_impl_ClosureNativePointer createClosureNativePointer(long nativeClosure, long codePointer, CallTarget callTarget,
                    Target_com_oracle_truffle_nfi_impl_LibFFISignature signature);

    @Alias
    native void newClosureRef(long codePointer);

    @Alias
    native void releaseClosureRef(long codePointer);

    @Alias
    native TruffleObject getClosureObject(long codePointer);

    @Alias
    protected native void initializeSimpleType(NativeSimpleType simpleType, int size, int alignment, long ffiType);

    @Substitute
    private long initializeNativeContext() {
        TruffleNFISupport support = ImageSingletons.lookup(TruffleNFISupport.class);

        NativeTruffleContext ret = UnmanagedMemory.malloc(SizeOf.get(NativeTruffleContext.class));
        ret.setContextHandle(support.createContextHandle(this));

        NFIInitialization.initializeContext(ret);
        NFIInitialization.initializeSimpleTypes(this);

        return ret.rawValue();
    }

    @Substitute
    private static void disposeNativeContext(long context) {
        TruffleNFISupport support = ImageSingletons.lookup(TruffleNFISupport.class);
        NativeTruffleContext ctx = WordFactory.pointer(context);
        support.destroyContextHandle(ctx.contextHandle());
        UnmanagedMemory.free(ctx);
    }

    @Substitute
    private static long initializeNativeEnv(long context) {
        NativeTruffleContext ctx = WordFactory.pointer(context);
        NativeTruffleEnv env = UnmanagedMemory.malloc(SizeOf.get(NativeTruffleEnv.class));
        NFIInitialization.initializeEnv(env, ctx);
        return env.rawValue();
    }

    @Substitute
    Target_com_oracle_truffle_nfi_impl_ClosureNativePointer allocateClosureObjectRet(Target_com_oracle_truffle_nfi_impl_LibFFISignature signature, CallTarget callTarget) {
        return NativeClosure.prepareClosure(this, signature, callTarget, NativeClosure.INVOKE_CLOSURE_OBJECT_RET.getFunctionPointer());
    }

    @Substitute
    Target_com_oracle_truffle_nfi_impl_ClosureNativePointer allocateClosureStringRet(Target_com_oracle_truffle_nfi_impl_LibFFISignature signature, CallTarget callTarget) {
        return NativeClosure.prepareClosure(this, signature, callTarget, NativeClosure.INVOKE_CLOSURE_STRING_RET.getFunctionPointer());
    }

    @Substitute
    Target_com_oracle_truffle_nfi_impl_ClosureNativePointer allocateClosureBufferRet(Target_com_oracle_truffle_nfi_impl_LibFFISignature signature, CallTarget callTarget) {
        return NativeClosure.prepareClosure(this, signature, callTarget, NativeClosure.INVOKE_CLOSURE_BUFFER_RET.getFunctionPointer());
    }

    @Substitute
    Target_com_oracle_truffle_nfi_impl_ClosureNativePointer allocateClosureVoidRet(Target_com_oracle_truffle_nfi_impl_LibFFISignature signature, CallTarget callTarget) {
        return NativeClosure.prepareClosure(this, signature, callTarget, NativeClosure.INVOKE_CLOSURE_VOID_RET.getFunctionPointer());
    }

    @Substitute
    @SuppressWarnings("static-method")
    long prepareSignature(Target_com_oracle_truffle_nfi_impl_LibFFIType retType, Target_com_oracle_truffle_nfi_impl_LibFFIType... args) {
        CifData data = PrepareHelper.prepareArgs(args);
        int ret = LibFFI.ffi_prep_cif(data.cif(), LibFFI.FFI_DEFAULT_ABI(), WordFactory.unsigned(args.length), WordFactory.pointer(retType.type), data.args());
        return PrepareHelper.checkRet(data, ret);
    }

    @Substitute
    @SuppressWarnings("static-method")
    long prepareSignatureVarargs(Target_com_oracle_truffle_nfi_impl_LibFFIType retType, int nFixedArgs, Target_com_oracle_truffle_nfi_impl_LibFFIType... args) {
        CifData data = PrepareHelper.prepareArgs(args);
        int ret = LibFFI.ffi_prep_cif_var(data.cif(), LibFFI.FFI_DEFAULT_ABI(), WordFactory.unsigned(nFixedArgs), WordFactory.unsigned(args.length), WordFactory.pointer(retType.type), data.args());
        return PrepareHelper.checkRet(data, ret);
    }

    @Substitute
    @TruffleBoundary
    void executeNative(long cif, long functionPointer, byte[] primArgs, int patchCount, int[] patchOffsets, Object[] objArgs, byte[] ret) {
        try (LocalNativeScope scope = TruffleNFISupport.createLocalScope(patchCount);
                        PinnedObject retBuffer = PinnedObject.create(ret)) {
            NativeTruffleContext ctx = WordFactory.pointer(nativeContext);
            LibFFI.ffi_cif ffiCif = WordFactory.pointer(cif);
            ExecuteHelper.execute(ctx, ffiCif, retBuffer.addressOfArrayElement(0), functionPointer, primArgs, patchCount, patchOffsets, objArgs, scope);
        }
    }

    @Substitute
    @TruffleBoundary
    long executePrimitive(long cif, long functionPointer, byte[] primArgs, int patchCount, int[] patchOffsets, Object[] objArgs) {
        try (LocalNativeScope scope = TruffleNFISupport.createLocalScope(patchCount)) {
            NativeTruffleContext ctx = WordFactory.pointer(nativeContext);
            ffi_cif ffiCif = WordFactory.pointer(cif);
            CLongPointer retPtr = StackValue.get(8);
            ExecuteHelper.execute(ctx, ffiCif, retPtr, functionPointer, primArgs, patchCount, patchOffsets, objArgs, scope);
            return retPtr.read();
        }
    }

    @Substitute
    @TruffleBoundary
    Object executeObject(long cif, long functionPointer, byte[] primArgs, int patchCount, int[] patchOffsets, Object[] objArgs) {
        try (LocalNativeScope scope = TruffleNFISupport.createLocalScope(patchCount)) {
            NativeTruffleContext ctx = WordFactory.pointer(nativeContext);
            ffi_cif ffiCif = WordFactory.pointer(cif);
            WordPointer retPtr = StackValue.get(8);
            ExecuteHelper.execute(ctx, ffiCif, retPtr, functionPointer, primArgs, patchCount, patchOffsets, objArgs, scope);
            return ImageSingletons.lookup(TruffleNFISupport.class).resolveHandle(retPtr.read());
        }
    }

    @Substitute
    private static void loadNFILib() {
        // do nothing, the NFI library is statically linked to the SVM image
    }

    @Substitute
    @TruffleBoundary
    static long loadLibrary(@SuppressWarnings("unused") long nativeContext, String name, int flags) {
        return TruffleNFISupport.loadLibrary(nativeContext, name, flags);
    }

    @Substitute
    @TruffleBoundary
    static void freeLibrary(long library) {
        TruffleNFISupport.freeLibrary(library);
    }

    @Substitute
    @TruffleBoundary
    TruffleObject lookupSymbol(Target_com_oracle_truffle_nfi_impl_LibFFILibrary library, String name) {
        if (ImageSingletons.lookup(TruffleNFISupport.class).errnoGetterFunctionName.equals(name)) {
            return new ErrnoMirror();
        } else {
            Target_com_oracle_truffle_nfi_impl_LibFFISymbol ret = Target_com_oracle_truffle_nfi_impl_LibFFISymbol.create(language, library, name, lookup(nativeContext, library.handle, name));
            return KnownIntrinsics.convertUnknownValue(ret, TruffleObject.class);
        }
    }

    @Substitute
    @TruffleBoundary
    static long lookup(@SuppressWarnings("unused") long nativeContext, long library, String name) {
        return TruffleNFISupport.lookup(nativeContext, library, name);
    }
}
