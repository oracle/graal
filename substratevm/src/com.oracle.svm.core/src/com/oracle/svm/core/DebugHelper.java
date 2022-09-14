/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.util.function.BooleanSupplier;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMThreads;

/**
 * All {@link CEntryPoint} methods in here can be directly called from a debugger.
 *
 * Example usage native-image GDB debug session on AMD64:
 *
 * <pre>
 * (gdb) b 'java.io.PrintStream::write'
 * Thread 1 "hellojava" hit Breakpoint 2, java.io.PrintStream::write(byte [] *, int, int) (this=0x7ffff7a2e4e0, buf=0x7ffff790b8f0, off=0, len=27)
 * (gdb) call svm_dbg_print_obj($r15, 0x7ffff7a2e4e0)
 * is an object of type java.io.PrintStream
 * </pre>
 */
public class DebugHelper {
    static class PointerDebugHelper {
        @Uninterruptible(reason = "Called with a raw pointer.")
        @CEntryPoint(name = "svm_dbg_ptr_isInImageHeap", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static boolean isInImageHeap(@SuppressWarnings("unused") IsolateThread thread, Pointer ptr) {
            return Heap.getHeap().isInImageHeap(ptr);
        }

        @Uninterruptible(reason = "Called with a raw pointer.")
        @CEntryPoint(name = "svm_dbg_ptr_isObject", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static boolean isObject(@SuppressWarnings("unused") IsolateThread thread, Pointer ptr) {
            ObjectHeader header = Heap.getHeap().getObjectHeader();
            return header.pointsToObjectHeader(ptr);
        }
    }

    static class HubDebugHelper {
        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_hub_getLayoutEncoding", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static int getLayoutEncoding(@SuppressWarnings("unused") IsolateThread thread, Pointer hubPtr) {
            DynamicHub hub = (DynamicHub) hubPtr.toObject();
            return hub.getLayoutEncoding();
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_hub_getArrayElementSize", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static int getArrayElementSize(@SuppressWarnings("unused") IsolateThread thread, Pointer hubPtr) {
            DynamicHub hub = (DynamicHub) hubPtr.toObject();
            return DebugHelper.getArrayElementSize(hub);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_hub_getArrayBaseOffset", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static int getArrayBaseOffset(@SuppressWarnings("unused") IsolateThread thread, Pointer hubPtr) {
            DynamicHub hub = (DynamicHub) hubPtr.toObject();
            return DebugHelper.getArrayBaseOffset(hub);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_hub_isArray", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static boolean isArray(@SuppressWarnings("unused") IsolateThread thread, Pointer hubPtr) {
            DynamicHub hub = (DynamicHub) hubPtr.toObject();
            return DebugHelper.isArray(hub);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_hub_isPrimitiveArray", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static boolean isPrimitiveArray(@SuppressWarnings("unused") IsolateThread thread, Pointer hubPtr) {
            DynamicHub hub = (DynamicHub) hubPtr.toObject();
            return DebugHelper.isPrimitiveArray(hub);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_hub_isObjectArray", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static boolean isObjectArray(@SuppressWarnings("unused") IsolateThread thread, Pointer hubPtr) {
            DynamicHub hub = (DynamicHub) hubPtr.toObject();
            return DebugHelper.isObjectArray(hub);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_hub_isInstance", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static boolean isInstance(@SuppressWarnings("unused") IsolateThread thread, Pointer hubPtr) {
            DynamicHub hub = (DynamicHub) hubPtr.toObject();
            return DebugHelper.isInstance(hub);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_hub_isReference", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static boolean isReference(@SuppressWarnings("unused") IsolateThread thread, Pointer hubPtr) {
            DynamicHub hub = (DynamicHub) hubPtr.toObject();
            return DebugHelper.isReference(hub);
        }
    }

    static class ObjectDebugHelper {
        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_obj_getHub", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static long getHub(@SuppressWarnings("unused") IsolateThread thread, Pointer objPtr) {
            Object obj = objPtr.toObject();
            return Word.objectToUntrackedPointer(KnownIntrinsics.readHub(obj)).rawValue();
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_obj_getObjectSize", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static long getObjectSize(@SuppressWarnings("unused") IsolateThread thread, Pointer objPtr) {
            Object obj = objPtr.toObject();
            return LayoutEncoding.getSizeFromObject(obj).rawValue();
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_obj_getArrayElementSize", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static int getArrayElementSize(@SuppressWarnings("unused") IsolateThread thread, Pointer objPtr) {
            Object obj = objPtr.toObject();
            DynamicHub hub = KnownIntrinsics.readHub(obj);
            return DebugHelper.getArrayElementSize(hub);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_obj_getArrayBaseOffset", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static long getArrayBaseOffset(@SuppressWarnings("unused") IsolateThread thread, Pointer objPtr) {
            Object obj = objPtr.toObject();
            DynamicHub hub = KnownIntrinsics.readHub(obj);
            return DebugHelper.getArrayBaseOffset(hub);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_obj_isArray", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static boolean isArray(@SuppressWarnings("unused") IsolateThread thread, Pointer objPtr) {
            Object obj = objPtr.toObject();
            DynamicHub hub = KnownIntrinsics.readHub(obj);
            return DebugHelper.isArray(hub);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_obj_isPrimitiveArray", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static boolean isPrimitiveArray(@SuppressWarnings("unused") IsolateThread thread, Pointer objPtr) {
            Object obj = objPtr.toObject();
            DynamicHub hub = KnownIntrinsics.readHub(obj);
            return DebugHelper.isPrimitiveArray(hub);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_obj_isObjectArray", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static boolean isObjectArray(@SuppressWarnings("unused") IsolateThread thread, Pointer objPtr) {
            Object obj = objPtr.toObject();
            DynamicHub hub = KnownIntrinsics.readHub(obj);
            return DebugHelper.isObjectArray(hub);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_obj_isInstance", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static boolean isInstance(@SuppressWarnings("unused") IsolateThread thread, Pointer objPtr) {
            Object obj = objPtr.toObject();
            DynamicHub hub = KnownIntrinsics.readHub(obj);
            return DebugHelper.isInstance(hub);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_obj_isReference", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static boolean isReference(@SuppressWarnings("unused") IsolateThread thread, Pointer objPtr) {
            Object obj = objPtr.toObject();
            DynamicHub hub = KnownIntrinsics.readHub(obj);
            return DebugHelper.isReference(hub);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_obj_uncompress", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static long uncompressObjectPointer(@SuppressWarnings("unused") IsolateThread thread, Pointer compressedPtr) {
            return Word.objectToUntrackedPointer(ReferenceAccess.singleton().uncompressReference(compressedPtr)).rawValue();
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_obj_compress", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static long compressObjectPointer(@SuppressWarnings("unused") IsolateThread thread, Pointer objPtr) {
            Object obj = objPtr.toObject();
            return ReferenceAccess.singleton().getCompressedRepresentation(obj).rawValue();
        }
    }

    static class StringDebugHelper {
        @Uninterruptible(reason = "Called with a raw object pointer.", calleeMustBe = false)
        @CEntryPoint(name = "svm_dbg_string_length", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static int getStringLength(@SuppressWarnings("unused") IsolateThread thread, Pointer strPtr) {
            String str = (String) strPtr.toObject();
            return str.length();
        }
    }

    static class DiagnosticDebugHelper {
        @Uninterruptible(reason = "Called with a raw object pointer.", calleeMustBe = false)
        @CEntryPoint(name = "svm_dbg_print_hub", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static void printHub(@SuppressWarnings("unused") IsolateThread thread, Pointer hubPtr) {
            DynamicHub hub = (DynamicHub) hubPtr.toObject();
            Log.log().string(hub.getName()).newline();
        }

        @Uninterruptible(reason = "Called with a raw object pointer.")
        @CEntryPoint(name = "svm_dbg_print_obj", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static void printObject(@SuppressWarnings("unused") IsolateThread thread, Pointer objPtr) {
            SubstrateDiagnostics.printObjectInfo(Log.log(), objPtr);
        }

        @Uninterruptible(reason = "Called with a raw object pointer.", calleeMustBe = false)
        @CEntryPoint(name = "svm_dbg_print_string", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static void printString(@SuppressWarnings("unused") IsolateThread thread, Pointer strPtr) {
            String str = (String) strPtr.toObject();
            Log.log().string(str).newline();
        }

        @Uninterruptible(reason = "Just to keep the verification happy.", calleeMustBe = false)
        @CEntryPoint(name = "svm_dbg_print_fatalErrorDiagnostics", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static void printFatalErrorDiagnostics(@SuppressWarnings("unused") IsolateThread thread, Pointer sp, CodePointer ip) {
            SubstrateDiagnostics.printFatalError(Log.log(), sp, ip, WordFactory.nullPointer(), false);
        }

        @Uninterruptible(reason = "Just to keep the verification happy.", calleeMustBe = false)
        @CEntryPoint(name = "svm_dbg_print_locationInfo", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class, epilogue = NoEpilogue.class)
        public static void printLocationInfo(@SuppressWarnings("unused") IsolateThread thread, Pointer mem) {
            SubstrateDiagnostics.printLocationInfo(Log.log(), mem, true, true);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getArrayElementSize(DynamicHub hub) {
        if (hub.isArray()) {
            return LayoutEncoding.getArrayIndexScale(hub.getLayoutEncoding());
        }
        return -1;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getArrayBaseOffset(DynamicHub hub) {
        if (hub.isArray()) {
            return LayoutEncoding.getArrayIndexScale(hub.getLayoutEncoding());
        }
        return -1;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isArray(DynamicHub hub) {
        return LayoutEncoding.isArray(hub.getLayoutEncoding());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isPrimitiveArray(DynamicHub hub) {
        return LayoutEncoding.isPrimitiveArray(hub.getLayoutEncoding());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isObjectArray(DynamicHub hub) {
        return LayoutEncoding.isObjectArray(hub.getLayoutEncoding());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isInstance(DynamicHub hub) {
        return LayoutEncoding.isPureInstance(hub.getLayoutEncoding());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isReference(DynamicHub hub) {
        return hub.isReferenceInstanceClass();
    }

    private static class SetThreadAndHeapBasePrologue implements CEntryPointOptions.Prologue {
        @Uninterruptible(reason = "prologue")
        public static void enter(IsolateThread thread) {
            WriteCurrentVMThreadNode.writeCurrentVMThread(thread);
            if (SubstrateOptions.SpawnIsolates.getValue()) {
                CEntryPointSnippets.setHeapBase(VMThreads.IsolateTL.get());
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static class IncludeDebugHelperMethods implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return SubstrateOptions.IncludeDebugHelperMethods.getValue();
        }
    }
}
