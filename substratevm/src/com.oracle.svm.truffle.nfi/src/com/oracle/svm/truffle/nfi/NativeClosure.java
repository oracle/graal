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

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.truffle.nfi.LibFFI.ClosureData;
import com.oracle.svm.truffle.nfi.LibFFI.NativeClosureHandle;
import com.oracle.svm.truffle.nfi.NativeAPI.NativeTruffleEnv;
import com.oracle.svm.truffle.nfi.libffi.LibFFI;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_cif;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_closure_callback;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;

/**
 * Contains the managed memory parts of `struct closure_data`.
 *
 * See truffle/src/com.oracle.truffle.nfi.native/src/closure.c.
 *
 * It is very important that this object contains no strong references to anything that might
 * indirectly reference a Truffle context or engine. See the comment in ClosureNativePointer.
 */
final class NativeClosure {

    /**
     * Replacement for `enum closure_arg_type`.
     */
    private enum ClosureArgType {
        ARG_BUFFER,
        ARG_STRING,
        ARG_OBJECT,
        ARG_SKIP;
    }

    /*
     * The references to the CallTarget and the receiver are weak because they might contain cyclic
     * references to the Truffle context. They can never actually die as long as this object is
     * alive, because there are corresponding strong references in ClosureNativePointer.
     */
    private final WeakReference<CallTarget> callTarget;
    private final WeakReference<Object> receiver;

    private final ClosureArgType[] argTypes;

    private NativeClosure(CallTarget callTarget, Object receiver, ClosureArgType[] argTypes) {
        this.callTarget = new WeakReference<>(callTarget);
        if (receiver != null) {
            this.receiver = new WeakReference<>(receiver);
        } else {
            this.receiver = null;
        }
        this.argTypes = argTypes;
    }

    /**
     * Implementation of the native `prepare_closure` function.
     */
    static Target_com_oracle_truffle_nfi_backend_libffi_ClosureNativePointer prepareClosure(Target_com_oracle_truffle_nfi_backend_libffi_LibFFIContext ctx,
                    Target_com_oracle_truffle_nfi_backend_libffi_LibFFISignature signature, CallTarget callTarget, Object receiver, ffi_closure_callback callback) {
        int envArgIdx = -1;
        Target_com_oracle_truffle_nfi_backend_libffi_LibFFIType_CachedTypeInfo[] argTypeInfo = signature.signatureInfo.getArgTypes();
        ClosureArgType[] argTypes = new ClosureArgType[argTypeInfo.length];

        for (int i = 0; i < argTypeInfo.length; i++) {
            Target_com_oracle_truffle_nfi_backend_libffi_LibFFIType_CachedTypeInfo type = argTypeInfo[i];
            if (Target_com_oracle_truffle_nfi_backend_libffi_LibFFIType_StringType.class.isInstance(type)) {
                argTypes[i] = ClosureArgType.ARG_STRING;
            } else if (Target_com_oracle_truffle_nfi_backend_libffi_LibFFIType_ObjectType.class.isInstance(type)) {
                argTypes[i] = ClosureArgType.ARG_OBJECT;
            } else if (Target_com_oracle_truffle_nfi_backend_libffi_LibFFIType_NullableType.class.isInstance(type)) {
                argTypes[i] = ClosureArgType.ARG_OBJECT;
            } else if (Target_com_oracle_truffle_nfi_backend_libffi_LibFFIType_EnvType.class.isInstance(type)) {
                argTypes[i] = ClosureArgType.ARG_SKIP;
                envArgIdx = i;
            } else {
                argTypes[i] = ClosureArgType.ARG_BUFFER;
            }
        }

        NativeClosure closure = new NativeClosure(callTarget, receiver, argTypes);
        NativeClosureHandle handle = ImageSingletons.lookup(TruffleNFISupport.class).createClosureHandle(closure);

        WordPointer codePtr = UnsafeStackValue.get(WordPointer.class);
        ClosureData data = ffi_closure_alloc(SizeOf.unsigned(ClosureData.class), codePtr);
        data.setNativeClosureHandle(handle);
        data.setIsolate(CurrentIsolate.getIsolate());

        data.setEnvArgIdx(envArgIdx);

        PointerBase code = codePtr.read();
        LibFFI.ffi_prep_closure_loc(data.ffiClosure(), WordFactory.pointer(signature.cif), callback, data, code);

        return ctx.createClosureNativePointer(data.rawValue(), code.rawValue(), callTarget, signature, receiver);
    }

    private Object call(WordPointer argPointers, Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_Pointer retBuffer) {
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
            switch (argTypes[i]) {
                case ARG_STRING:
                    CCharPointerPointer strPtr = argPointers.read(i);
                    args[i] = TruffleNFISupport.utf8ToJavaString(strPtr.read());
                    break;
                case ARG_OBJECT:
                    WordPointer objPtr = argPointers.read(i);
                    args[i] = ImageSingletons.lookup(TruffleNFISupport.class).resolveHandle(objPtr.read());
                    break;
                case ARG_BUFFER:
                    WordPointer argPtr = argPointers.read(i);
                    args[i] = new Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_Pointer(argPtr.rawValue());
                    break;
                case ARG_SKIP:
                    // nothing to do
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
            throw CompilerDirectives.shouldNotReachHere("Native closure used after free.");
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

    static final FastThreadLocalObject<Throwable> pendingException = FastThreadLocalFactory.createObject(Throwable.class, "NativeClosure.pendingException");

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    @Uninterruptible(reason = "contains prologue and epilogue for thread state transition", calleeMustBe = false)
    static void invokeClosureBufferRet(@SuppressWarnings("unused") ffi_cif cif, Pointer ret, WordPointer args, ClosureData user) {
        /* Read the C error number before transitioning into the Java state. */
        int errno = LibC.errno();

        if (user.envArgIdx() >= 0) {
            WordPointer envArgPtr = args.read(user.envArgIdx());
            NativeTruffleEnv env = envArgPtr.read();
            CEntryPointActions.enter(env.isolateThread());
        } else {
            CEntryPointActions.enterByIsolate(user.isolate());
        }

        ErrnoMirror.errnoMirror.getAddress().write(errno);

        try {
            doInvokeClosureBufferRet(ret, args, user);
        } catch (Throwable t) {
            pendingException.set(t);
        }

        errno = ErrnoMirror.errnoMirror.getAddress().read();
        CEntryPointActions.leave();
        /* Restore the C error number after being back in the Native state. */
        LibC.setErrno(errno);
    }

    private static void doInvokeClosureBufferRet(Pointer ret, WordPointer args, ClosureData user) {
        NativeClosure closure = lookup(user);
        Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_Pointer retBuffer = new Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_Pointer(ret.rawValue());
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

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    @Uninterruptible(reason = "contains prologue and epilogue for thread state transition", calleeMustBe = false)
    static void invokeClosureVoidRet(@SuppressWarnings("unused") ffi_cif cif, @SuppressWarnings("unused") WordPointer ret, WordPointer args, ClosureData user) {
        /* Read the C error number before transitioning into the Java state. */
        int errno = LibC.errno();

        if (user.envArgIdx() >= 0) {
            WordPointer envArgPtr = args.read(user.envArgIdx());
            NativeTruffleEnv env = envArgPtr.read();
            CEntryPointActions.enter(env.isolateThread());
        } else {
            CEntryPointActions.enterByIsolate(user.isolate());
        }

        ErrnoMirror.errnoMirror.getAddress().write(errno);

        try {
            doInvokeClosureVoidRet(args, user);
        } catch (Throwable t) {
            pendingException.set(t);
        }

        errno = ErrnoMirror.errnoMirror.getAddress().read();
        CEntryPointActions.leave();
        /* Restore the C error number after being back in the Native state. */
        LibC.setErrno(errno);
    }

    private static void doInvokeClosureVoidRet(WordPointer args, ClosureData user) {
        lookup(user).call(args, null);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    @Uninterruptible(reason = "contains prologue and epilogue for thread state transition", calleeMustBe = false)
    static void invokeClosureStringRet(@SuppressWarnings("unused") ffi_cif cif, WordPointer ret, WordPointer args, ClosureData user) {
        /* Read the C error number before transitioning into the Java state. */
        int errno = LibC.errno();

        if (user.envArgIdx() >= 0) {
            WordPointer envArgPtr = args.read(user.envArgIdx());
            NativeTruffleEnv env = envArgPtr.read();
            CEntryPointActions.enter(env.isolateThread());
        } else {
            CEntryPointActions.enterByIsolate(user.isolate());
        }

        ErrnoMirror.errnoMirror.getAddress().write(errno);

        try {
            doInvokeClosureStringRet(ret, args, user);
        } catch (Throwable t) {
            pendingException.set(t);
        }

        errno = ErrnoMirror.errnoMirror.getAddress().read();
        CEntryPointActions.leave();
        /* Restore the C error number after being back in the Native state. */
        LibC.setErrno(errno);
    }

    private static void doInvokeClosureStringRet(WordPointer ret, WordPointer args, ClosureData user) {
        Object retValue = lookup(user).call(args, null);
        ret.write(serializeStringRet(retValue));
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    @Uninterruptible(reason = "contains prologue and epilogue for thread state transition", calleeMustBe = false)
    static void invokeClosureObjectRet(@SuppressWarnings("unused") ffi_cif cif, WordPointer ret, WordPointer args, ClosureData user) {
        /* Read the C error number before transitioning into the Java state. */
        int errno = LibC.errno();

        if (user.envArgIdx() >= 0) {
            WordPointer envArgPtr = args.read(user.envArgIdx());
            NativeTruffleEnv env = envArgPtr.read();
            CEntryPointActions.enter(env.isolateThread());
        } else {
            CEntryPointActions.enterByIsolate(user.isolate());
        }

        ErrnoMirror.errnoMirror.getAddress().write(errno);

        try {
            doInvokeClosureObjectRet(ret, args, user);
        } catch (Throwable t) {
            pendingException.set(t);
        }

        errno = ErrnoMirror.errnoMirror.getAddress().read();
        CEntryPointActions.leave();
        /* Restore the C error number after being back in the Native state. */
        LibC.setErrno(errno);
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
