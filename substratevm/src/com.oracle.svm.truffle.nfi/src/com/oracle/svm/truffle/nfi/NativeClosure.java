/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.truffle.nfi.Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_TypeTag.getOffset;
import static com.oracle.svm.truffle.nfi.Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_TypeTag.getTag;
import static com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_closure_alloc;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

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

import com.oracle.svm.core.CErrorNumber;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.truffle.nfi.LibFFI.ClosureData;
import com.oracle.svm.truffle.nfi.LibFFI.NativeClosureHandle;
import com.oracle.svm.truffle.nfi.libffi.LibFFI;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_arg;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_cif;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_closure_callback;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;

final class NativeClosure {

    /*
     * Weak to break reference cycles via the global ObjectHandles table in TruffleNFISupport. Will
     * never actually die as long as this object is alive. See comment in ClosureNativePointer.
     */
    private final WeakReference<CallTarget> callTarget;
    private final WeakReference<Object> receiver;

    private final Target_com_oracle_truffle_nfi_backend_libffi_LibFFISignature signature;

    private NativeClosure(CallTarget callTarget, Object receiver, Target_com_oracle_truffle_nfi_backend_libffi_LibFFISignature signature) {
        this.callTarget = new WeakReference<>(callTarget);
        if (receiver != null) {
            this.receiver = new WeakReference<>(receiver);
        } else {
            this.receiver = null;
        }
        this.signature = signature;
    }

    private ByteBuffer createRetBuffer(PointerBase buffer) {
        Target_com_oracle_truffle_nfi_backend_libffi_LibFFIType_CachedTypeInfo retType = signature.signatureInfo.getRetType();
        int size = retType.size;
        if (size < SizeOf.get(ffi_arg.class)) {
            size = SizeOf.get(ffi_arg.class);
        }
        return CTypeConversion.asByteBuffer(buffer, size);
    }

    static Target_com_oracle_truffle_nfi_backend_libffi_ClosureNativePointer prepareClosure(Target_com_oracle_truffle_nfi_backend_libffi_LibFFIContext ctx,
                    Target_com_oracle_truffle_nfi_backend_libffi_LibFFISignature signature, CallTarget callTarget, Object receiver, ffi_closure_callback callback) {
        NativeClosure closure = new NativeClosure(callTarget, receiver, signature);
        NativeClosureHandle handle = ImageSingletons.lookup(TruffleNFISupport.class).createClosureHandle(closure);

        WordPointer codePtr = StackValue.get(WordPointer.class);
        ClosureData data = ffi_closure_alloc(SizeOf.unsigned(ClosureData.class), codePtr);
        data.setNativeClosureHandle(handle);
        data.setIsolate(CurrentIsolate.getIsolate());

        PointerBase code = codePtr.read();
        LibFFI.ffi_prep_closure_loc(data.ffiClosure(), WordFactory.pointer(signature.cif), callback, data, code);

        return ctx.createClosureNativePointer(data.rawValue(), code.rawValue(), callTarget, signature, receiver);
    }

    private Object call(WordPointer argPointers, ByteBuffer retBuffer) {
        Target_com_oracle_truffle_nfi_backend_libffi_LibFFIType_CachedTypeInfo[] argTypes = signature.signatureInfo.getArgTypes();
        int length = argTypes.length;
        if (receiver != null) {
            length++;
        }
        if (retBuffer != null) {
            length++;
        }

        Object[] args = new Object[length];
        int i;
        for (i = 0; i < argTypes.length; i++) {
            Object type = argTypes[i];
            if (Target_com_oracle_truffle_nfi_backend_libffi_LibFFIType_StringType.class.isInstance(type)) {
                CCharPointerPointer argPtr = argPointers.read(i);
                args[i] = TruffleNFISupport.utf8ToJavaString(argPtr.read());
            } else if (Target_com_oracle_truffle_nfi_backend_libffi_LibFFIType_ObjectType.class.isInstance(type)) {
                WordPointer argPtr = argPointers.read(i);
                args[i] = ImageSingletons.lookup(TruffleNFISupport.class).resolveHandle(argPtr.read());
            } else if (Target_com_oracle_truffle_nfi_backend_libffi_LibFFIType_NullableType.class.isInstance(type)) {
                WordPointer argPtr = argPointers.read(i);
                args[i] = ImageSingletons.lookup(TruffleNFISupport.class).resolveHandle(argPtr.read());
            } else if (Target_com_oracle_truffle_nfi_backend_libffi_LibFFIType_EnvType.class.isInstance(type)) {
                // skip
            } else {
                WordPointer argPtr = argPointers.read(i);
                args[i] = CTypeConversion.asByteBuffer(argPtr, argTypes[i].size);
            }
        }

        if (receiver != null) {
            args[i++] = receiver.get();
        }
        if (retBuffer != null) {
            args[i++] = retBuffer;
        }

        CallTarget c = callTarget.get();
        if (c == null) {
            /*
             * Can never happen: The CallTarget is kept alive by the ClosureNativePointer object,
             * and when the ClosureNativePointer object is GCed, the native stub that calls this
             * code is freed. We still need to check to make findbugs happy.
             */
            CompilerDirectives.shouldNotReachHere("Native closure used after free.");
        }
        return c.call(args);
    }

    private static NativeClosure lookup(ClosureData data) {
        return ImageSingletons.lookup(TruffleNFISupport.class).resolveClosureHandle(data.nativeClosureHandle());
    }

    private static PointerBase serializeStringRet(Object retValue) {
        if (retValue == null) {
            return WordFactory.zero();
        } else if (retValue instanceof Target_com_oracle_truffle_nfi_backend_libffi_NativeString) {
            Target_com_oracle_truffle_nfi_backend_libffi_NativeString nativeString = (Target_com_oracle_truffle_nfi_backend_libffi_NativeString) retValue;
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

    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    @Uninterruptible(reason = "contains prologue and epilogue for thread state transition", calleeMustBe = false)
    static void invokeClosureBufferRet(@SuppressWarnings("unused") ffi_cif cif, Pointer ret, WordPointer args, ClosureData user) {
        /* Read the C error number before transitioning into the Java state. */
        int errno = CErrorNumber.getCErrorNumber();
        CEntryPointActions.enterIsolate(user.isolate());
        ErrnoMirror.errnoMirror.getAddress().write(errno);

        try {
            doInvokeClosureBufferRet(ret, args, user);
        } catch (Throwable t) {
            pendingException.set(t);
        }

        errno = ErrnoMirror.errnoMirror.getAddress().read();
        CEntryPointActions.leave();
        /* Restore the C error number after being back in the Native state. */
        CErrorNumber.setCErrorNumber(errno);
    }

    private static void doInvokeClosureBufferRet(Pointer ret, WordPointer args, ClosureData user) {
        NativeClosure closure = lookup(user);
        ByteBuffer retBuffer = closure.createRetBuffer(ret);
        Target_com_oracle_truffle_nfi_backend_libffi_LibFFIClosure_RetPatches patches = (Target_com_oracle_truffle_nfi_backend_libffi_LibFFIClosure_RetPatches) closure.call(args, retBuffer);

        if (patches != null) {
            for (int i = 0; i < patches.count; i++) {
                Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_TypeTag tag = getTag(patches.patches[i]);
                int offset = getOffset(patches.patches[i]);
                Object obj = patches.objects[i];

                if (tag == Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_TypeTag.OBJECT) {
                    WordBase handle = ImageSingletons.lookup(TruffleNFISupport.class).createGlobalHandle(obj);
                    ret.writeWord(offset, handle);
                } else if (tag == Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_TypeTag.STRING) {
                    ret.writeWord(offset, serializeStringRet(obj));
                } else {
                    // nothing to do
                }
            }
        }
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    @Uninterruptible(reason = "contains prologue and epilogue for thread state transition", calleeMustBe = false)
    static void invokeClosureVoidRet(@SuppressWarnings("unused") ffi_cif cif, @SuppressWarnings("unused") WordPointer ret, WordPointer args, ClosureData user) {
        /* Read the C error number before transitioning into the Java state. */
        int errno = CErrorNumber.getCErrorNumber();
        CEntryPointActions.enterIsolate(user.isolate());
        ErrnoMirror.errnoMirror.getAddress().write(errno);

        try {
            doInvokeClosureVoidRet(args, user);
        } catch (Throwable t) {
            pendingException.set(t);
        }

        errno = ErrnoMirror.errnoMirror.getAddress().read();
        CEntryPointActions.leave();
        /* Restore the C error number after being back in the Native state. */
        CErrorNumber.setCErrorNumber(errno);
    }

    private static void doInvokeClosureVoidRet(WordPointer args, ClosureData user) {
        lookup(user).call(args, null);
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    @Uninterruptible(reason = "contains prologue and epilogue for thread state transition", calleeMustBe = false)
    static void invokeClosureStringRet(@SuppressWarnings("unused") ffi_cif cif, WordPointer ret, WordPointer args, ClosureData user) {
        /* Read the C error number before transitioning into the Java state. */
        int errno = CErrorNumber.getCErrorNumber();
        CEntryPointActions.enterIsolate(user.isolate());
        ErrnoMirror.errnoMirror.getAddress().write(errno);

        try {
            doInvokeClosureStringRet(ret, args, user);
        } catch (Throwable t) {
            pendingException.set(t);
        }

        errno = ErrnoMirror.errnoMirror.getAddress().read();
        CEntryPointActions.leave();
        /* Restore the C error number after being back in the Native state. */
        CErrorNumber.setCErrorNumber(errno);
    }

    private static void doInvokeClosureStringRet(WordPointer ret, WordPointer args, ClosureData user) {
        Object retValue = lookup(user).call(args, null);
        ret.write(serializeStringRet(retValue));
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    @Uninterruptible(reason = "contains prologue and epilogue for thread state transition", calleeMustBe = false)
    static void invokeClosureObjectRet(@SuppressWarnings("unused") ffi_cif cif, WordPointer ret, WordPointer args, ClosureData user) {
        /* Read the C error number before transitioning into the Java state. */
        int errno = CErrorNumber.getCErrorNumber();
        CEntryPointActions.enterIsolate(user.isolate());
        ErrnoMirror.errnoMirror.getAddress().write(errno);

        try {
            doInvokeClosureObjectRet(ret, args, user);
        } catch (Throwable t) {
            pendingException.set(t);
        }

        errno = ErrnoMirror.errnoMirror.getAddress().read();
        CEntryPointActions.leave();
        /* Restore the C error number after being back in the Native state. */
        CErrorNumber.setCErrorNumber(errno);
    }

    private static void doInvokeClosureObjectRet(WordPointer ret, WordPointer args, ClosureData user) {
        Object obj = lookup(user).call(args, null);
        if (obj == null) {
            ret.write(WordFactory.zero());
        } else {
            TruffleObjectHandle handle = ImageSingletons.lookup(TruffleNFISupport.class).createGlobalHandle(obj);
            ret.write(handle);
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
