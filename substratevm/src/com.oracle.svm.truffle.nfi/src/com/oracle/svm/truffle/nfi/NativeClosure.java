/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.truffle.nfi.Target_com_oracle_truffle_nfi_impl_NativeArgumentBuffer_TypeTag.getOffset;
import static com.oracle.svm.truffle.nfi.Target_com_oracle_truffle_nfi_impl_NativeArgumentBuffer_TypeTag.getTag;
import static com.oracle.svm.truffle.nfi.TruffleNFISupport.ErrnoMirrorContext;
import static com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_closure_alloc;

import java.nio.ByteBuffer;

import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.truffle.nfi.LibFFI.ClosureData;
import com.oracle.svm.truffle.nfi.LibFFI.NativeClosureHandle;
import com.oracle.svm.truffle.nfi.libffi.LibFFI;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_arg;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_cif;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_closure_callback;
import com.oracle.truffle.api.CallTarget;

final class NativeClosure {

    private final CallTarget callTarget;
    private final Target_com_oracle_truffle_nfi_impl_LibFFISignature signature;
    private final int skippedArgCount;

    private NativeClosure(CallTarget callTarget, Target_com_oracle_truffle_nfi_impl_LibFFISignature signature) {
        this.callTarget = callTarget;
        this.signature = signature;

        int skipped = 0;
        for (Object type : signature.getArgTypes()) {
            if (Target_com_oracle_truffle_nfi_impl_LibFFIType_EnvType.class.isInstance(type)) {
                skipped++;
            }
        }
        this.skippedArgCount = skipped;
    }

    private ByteBuffer createRetBuffer(PointerBase buffer) {
        Target_com_oracle_truffle_nfi_impl_LibFFIType retType = signature.getRetType();
        int size = retType.size;
        if (size < SizeOf.get(ffi_arg.class)) {
            size = SizeOf.get(ffi_arg.class);
        }
        return CTypeConversion.asByteBuffer(buffer, size);
    }

    static Target_com_oracle_truffle_nfi_impl_ClosureNativePointer prepareClosure(Target_com_oracle_truffle_nfi_impl_NFIContext ctx,
                    Target_com_oracle_truffle_nfi_impl_LibFFISignature signature, CallTarget callTarget, ffi_closure_callback callback) {
        NativeClosure closure = new NativeClosure(callTarget, signature);
        NativeClosureHandle handle = ImageSingletons.lookup(TruffleNFISupport.class).createClosureHandle(closure);

        WordPointer codePtr = StackValue.get(WordPointer.class);
        ClosureData data = ffi_closure_alloc(SizeOf.unsigned(ClosureData.class), codePtr);
        data.setNativeClosureHandle(handle);
        data.setIsolate(CurrentIsolate.getIsolate());

        PointerBase code = codePtr.read();
        LibFFI.ffi_prep_closure_loc(data.ffiClosure(), WordFactory.pointer(signature.cif), callback, data, code);

        return ctx.createClosureNativePointer(data.rawValue(), code.rawValue(), callTarget, signature);
    }

    private Object call(WordPointer argPointers, ByteBuffer retBuffer) {
        Target_com_oracle_truffle_nfi_impl_LibFFIType[] argTypes = signature.getArgTypes();
        int length = argTypes.length - skippedArgCount;
        if (retBuffer != null) {
            length++;
        }

        int argIdx = 0;
        Object[] args = new Object[length];
        for (int i = 0; i < argTypes.length; i++) {
            Object type = argTypes[i];
            if (Target_com_oracle_truffle_nfi_impl_LibFFIType_StringType.class.isInstance(type)) {
                CCharPointerPointer argPtr = argPointers.read(i);
                args[argIdx++] = TruffleNFISupport.utf8ToJavaString(argPtr.read());
            } else if (Target_com_oracle_truffle_nfi_impl_LibFFIType_ObjectType.class.isInstance(type)) {
                WordPointer argPtr = argPointers.read(i);
                args[argIdx++] = ImageSingletons.lookup(TruffleNFISupport.class).resolveHandle(argPtr.read());
            } else if (Target_com_oracle_truffle_nfi_impl_LibFFIType_NullableType.class.isInstance(type)) {
                WordPointer argPtr = argPointers.read(i);
                args[argIdx++] = ImageSingletons.lookup(TruffleNFISupport.class).resolveHandle(argPtr.read());
            } else if (Target_com_oracle_truffle_nfi_impl_LibFFIType_EnvType.class.isInstance(type)) {
                // skip
            } else {
                WordPointer argPtr = argPointers.read(i);
                args[argIdx++] = CTypeConversion.asByteBuffer(argPtr, argTypes[i].size);
            }
        }

        if (retBuffer != null) {
            args[argIdx] = retBuffer;
        }

        return callTarget.call(args);
    }

    private static NativeClosure lookup(ClosureData data) {
        return ImageSingletons.lookup(TruffleNFISupport.class).resolveClosureHandle(data.nativeClosureHandle());
    }

    private static PointerBase serializeStringRet(Object retValue) {
        if (retValue == null) {
            return WordFactory.zero();
        } else if (retValue instanceof Target_com_oracle_truffle_nfi_impl_NativeString) {
            Target_com_oracle_truffle_nfi_impl_NativeString nativeString = (Target_com_oracle_truffle_nfi_impl_NativeString) retValue;
            return WordFactory.pointer(nativeString.nativePointer);
        } else if (retValue instanceof String) {
            byte[] utf8 = TruffleNFISupport.javaStringToUtf8((String) retValue);
            try (PinnedObject pinned = PinnedObject.create(utf8)) {
                CCharPointer source = pinned.addressOfArrayElement(0);
                return TruffleNFISupport.strdup(source);
            }
        } else {
            // unsupported type
            return WordFactory.zero();
        }
    }

    static final FastThreadLocalObject<Throwable> pendingException = FastThreadLocalFactory.createObject(Throwable.class);

    static class SetPendingExceptionHandler {
        static void handle(Throwable t) {
            pendingException.set(t);
        }
    }

    @SuppressWarnings("try")
    @CEntryPoint(exceptionHandler = SetPendingExceptionHandler.class)
    @CEntryPointOptions(prologue = EnterClosureDataIsolatePrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void invokeClosureBufferRet(@SuppressWarnings("unused") ffi_cif cif, Pointer ret, WordPointer args, ClosureData user) {
        try (ErrnoMirrorContext mirror = new ErrnoMirrorContext()) {
            NativeClosure closure = lookup(user);
            ByteBuffer retBuffer = closure.createRetBuffer(ret);
            Target_com_oracle_truffle_nfi_impl_LibFFIClosure_RetPatches patches = (Target_com_oracle_truffle_nfi_impl_LibFFIClosure_RetPatches) closure.call(args, retBuffer);

            if (patches != null) {
                for (int i = 0; i < patches.count; i++) {
                    Target_com_oracle_truffle_nfi_impl_NativeArgumentBuffer_TypeTag tag = getTag(patches.patches[i]);
                    int offset = getOffset(patches.patches[i]);
                    Object obj = patches.objects[i];

                    if (tag == Target_com_oracle_truffle_nfi_impl_NativeArgumentBuffer_TypeTag.OBJECT) {
                        WordBase handle = ImageSingletons.lookup(TruffleNFISupport.class).createGlobalHandle(obj);
                        ret.writeWord(offset, handle);
                    } else if (tag == Target_com_oracle_truffle_nfi_impl_NativeArgumentBuffer_TypeTag.STRING) {
                        ret.writeWord(offset, serializeStringRet(obj));
                    } else {
                        // nothing to do
                    }
                }
            }
        }
    }

    @SuppressWarnings("try")
    @CEntryPoint(exceptionHandler = SetPendingExceptionHandler.class)
    @CEntryPointOptions(prologue = EnterClosureDataIsolatePrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void invokeClosureVoidRet(@SuppressWarnings("unused") ffi_cif cif, @SuppressWarnings("unused") WordPointer ret, WordPointer args, ClosureData user) {
        try (ErrnoMirrorContext mirror = new ErrnoMirrorContext()) {
            lookup(user).call(args, null);
        }
    }

    @SuppressWarnings("try")
    @CEntryPoint(exceptionHandler = SetPendingExceptionHandler.class)
    @CEntryPointOptions(prologue = EnterClosureDataIsolatePrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void invokeClosureStringRet(@SuppressWarnings("unused") ffi_cif cif, WordPointer ret, WordPointer args, ClosureData user) {
        try (ErrnoMirrorContext mirror = new ErrnoMirrorContext()) {
            Object retValue = lookup(user).call(args, null);
            ret.write(serializeStringRet(retValue));
        }
    }

    @SuppressWarnings("try")
    @CEntryPoint(exceptionHandler = SetPendingExceptionHandler.class)
    @CEntryPointOptions(prologue = EnterClosureDataIsolatePrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void invokeClosureObjectRet(@SuppressWarnings("unused") ffi_cif cif, WordPointer ret, WordPointer args, ClosureData user) {
        try (ErrnoMirrorContext mirror = new ErrnoMirrorContext()) {
            Object obj = lookup(user).call(args, null);
            if (obj == null) {
                ret.write(WordFactory.zero());
            } else {
                TruffleObjectHandle handle = ImageSingletons.lookup(TruffleNFISupport.class).createGlobalHandle(obj);
                ret.write(handle);
            }
        }
    }

    static class EnterClosureDataIsolatePrologue {
        static void enter(ClosureData user) {
            CEntryPointActions.enterIsolate(user.isolate());
        }
    }

    static final CEntryPointLiteral<ffi_closure_callback> INVOKE_CLOSURE_BUFFER_RET = CEntryPointLiteral.create(NativeClosure.class, "invokeClosureBufferRet",
                    ffi_cif.class, Pointer.class, WordPointer.class, ClosureData.class);

    static final CEntryPointLiteral<ffi_closure_callback> INVOKE_CLOSURE_VOID_RET = CEntryPointLiteral.create(NativeClosure.class, "invokeClosureVoidRet",
                    ffi_cif.class, WordPointer.class, WordPointer.class, ClosureData.class);

    static final CEntryPointLiteral<ffi_closure_callback> INVOKE_CLOSURE_STRING_RET = CEntryPointLiteral.create(NativeClosure.class, "invokeClosureStringRet",
                    ffi_cif.class, WordPointer.class, WordPointer.class, ClosureData.class);

    static final CEntryPointLiteral<ffi_closure_callback> INVOKE_CLOSURE_OBJECT_RET = CEntryPointLiteral.create(NativeClosure.class, "invokeClosureObjectRet",
                    ffi_cif.class, WordPointer.class, WordPointer.class, ClosureData.class);
}
