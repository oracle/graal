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

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.truffle.nfi.NativeAPI.NativeTruffleContext;
import com.oracle.svm.truffle.nfi.NativeAPI.NativeTruffleEnv;
import com.oracle.svm.truffle.nfi.libffi.LibFFI;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_cif;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_type;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_type_array;
import com.oracle.svm.truffle.nfi.libffi.LibFFIHeaderDirectives;

final class NativeSignature {

    @CContext(LibFFIHeaderDirectives.class)
    @CStruct("svm_cif_data")
    public interface CifData extends PointerBase {

        @CFieldAddress
        ffi_cif cif();

        @CFieldAddress
        ffi_type_array args();
    }

    static class PrepareHelper {

        static CifData prepareArgs(int argCount, Target_com_oracle_truffle_nfi_backend_libffi_LibFFIType... args) {
            CifData data = UnmanagedMemory.malloc(SizeOf.get(CifData.class) + argCount * SizeOf.get(ffi_type_array.class));

            for (int i = 0; i < argCount; i++) {
                data.args().write(i, WordFactory.pointer(args[i].type));
            }

            return data;
        }

        static long checkRet(CifData data, int ret) {
            if (ret == LibFFI.FFI_OK()) {
                return data.rawValue();
            } else {
                UnmanagedMemory.free(data);
                return 0;
            }
        }
    }

    static class ExecuteHelper {

        static int alignUp(int index, int alignment) {
            int mask = alignment - 1;
            assert (alignment & mask) == 0 : "not a power of 2";
            return (index + mask) & ~mask;
        }

        @SuppressWarnings("try")
        static void execute(NativeTruffleContext ctx, ffi_cif cif, PointerBase ret, long functionPointer, byte[] primArgs, int patchCount, int[] patchOffsets, Object[] objArgs,
                        LocalNativeScope scope) {
            int nargs = cif.nargs();
            WordPointer argPtrs = UnmanagedMemory.malloc(nargs * SizeOf.get(WordPointer.class));
            // TODO WordPointer argPtrs = StackValue.get(nargs, SizeOf.get(WordPointer.class));

            NativeTruffleEnv env = UnsafeStackValue.get(NativeTruffleEnv.class);
            NFIInitialization.initializeEnv(env, ctx);

            try (PinnedObject primBuffer = PinnedObject.create(primArgs)) {
                Pointer prim = primBuffer.addressOfArrayElement(0);

                int primIdx = 0;
                for (int i = 0; i < nargs; i++) {
                    ffi_type type = cif.arg_types().read(i);
                    primIdx = alignUp(primIdx, type.alignment());
                    argPtrs.write(i, prim.add(primIdx));
                    primIdx += type.size().rawValue();
                }

                for (int i = 0; i < patchCount; i++) {
                    Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_TypeTag tag = getTag(patchOffsets[i]);
                    int offset = getOffset(patchOffsets[i]);
                    Object obj = objArgs[i];

                    if (tag == Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_TypeTag.OBJECT) {
                        WordBase handle = scope.createLocalHandle(obj);
                        prim.writeWord(offset, handle);
                    } else if (tag == Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_TypeTag.STRING) {
                        PointerBase strPtr = scope.pinString((String) obj);
                        prim.writeWord(offset, strPtr);
                    } else if (tag == Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_TypeTag.KEEPALIVE) {
                        // nothing to do
                    } else if (tag == Target_com_oracle_truffle_nfi_backend_libffi_NativeArgumentBuffer_TypeTag.ENV) {
                        prim.writeWord(offset, env);
                    } else {
                        // all other types are array types, all of them are treated the same by svm
                        PointerBase arrPtr = scope.pinArray(obj);
                        prim.writeWord(offset, arrPtr);
                    }
                }

                ffiCall(cif, WordFactory.pointer(functionPointer), ret, argPtrs, ErrnoMirror.errnoMirror.getAddress());

                Throwable pending = NativeClosure.pendingException.get();
                if (pending != null) {
                    NativeClosure.pendingException.set(null);
                    throw rethrow(pending);
                }
            } finally {
                UnmanagedMemory.free(argPtrs);
            }
        }

        /**
         * Invokes {@link LibFFI.NoTransitions#ffi_call}, preserving and returning its errno before
         * it is possibly altered at a safepoint.
         */
        @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode")
        private static void ffiCall(ffi_cif cif, PointerBase fn, PointerBase rvalue, WordPointer avalue, CIntPointer errnoMirror) {
            CFunctionPrologueNode.cFunctionPrologue(VMThreads.StatusSupport.STATUS_IN_NATIVE);
            doFfiCall(cif, fn, rvalue, avalue, errnoMirror);
            CFunctionEpilogueNode.cFunctionEpilogue(VMThreads.StatusSupport.STATUS_IN_NATIVE);
        }

        @Uninterruptible(reason = "In native.")
        @NeverInline("Can have only a single invoke between CFunctionPrologueNode and CFunctionEpilogueNode.")
        private static void doFfiCall(ffi_cif cif, PointerBase fn, PointerBase rvalue, WordPointer avalue, CIntPointer errnoMirror) {
            /*
             * Set / get the error number immediately before / after the ffi call. We must be
             * uninterruptible, so that no safepoint can interfere.
             */
            LibC.setErrno(errnoMirror.read());
            LibFFI.NoTransitions.ffi_call(cif, fn, rvalue, avalue);
            errnoMirror.write(LibC.errno());
        }
    }

    @SuppressWarnings({"unchecked"})
    private static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }
}
