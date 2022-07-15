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

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackFrameVisitor;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.Utf8;
import jdk.internal.misc.Unsafe;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * All {@link CEntryPoint} methods in here can be directly called from a debugger.
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
    public static class IdeDebugHelper {
        /**
         * Convert instance of java.lang.String to utf8 c-string.
         * @param thread
         * @param ptr pointer to instance of java.lang.String
         * @return c-string
         */
        @CEntryPoint(name = "svm_dbg_string_to_utf8", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static CCharPointer stringToUtf8(@SuppressWarnings("unused") IsolateThread thread, Pointer ptr) {
            Object obj = ptr.toObject();
            return CTypeConversion.toCString((String) obj).get();
        }

        /**
         * Creates instance of java.lang.String from c-string.
         * @param thread
         * @param ptr c-string
         * @return instance java.lang.String
         */
        @CEntryPoint(name = "svm_dbg_utf8_to_string", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static Object utf8ToString(@SuppressWarnings("unused") IsolateThread thread, CCharPointer ptr) {
            return Utf8.utf8ToString(ptr);
        }

        /**
         * Create instance of an array.
         * @param thread
         * @param signature signature of array type.
         * @param length size of array
         * @return pointer to the array.
         */
        @CEntryPoint(name = "svm_dbg_create_array", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static Object createArray(@SuppressWarnings("unused") IsolateThread thread, CCharPointer signature, int length) {
            String strSignatura = Utf8.utf8ToString(signature);
            Log
                    .log()
                    .string("signatura: " + strSignatura)
                    .character('\n');
            if (strSignatura.startsWith("[")) {
                if (strSignatura.startsWith("[L") && strSignatura.endsWith(";")) {
                    Log
                            .log()
                            .string("object array: " + strSignatura.substring(2, strSignatura.length() - 1))
                            .character('\n');
                    return new Object[length];
                } else {
                    char typeLetter = strSignatura.charAt(1);
                    Log
                            .log()
                            .string("type: " + typeLetter)
                            .character('\n');
                    switch (typeLetter) {
                        case 'Z':
                            return new boolean[length];
                        case 'B':
                            return new byte[length];
                        case 'C':
                            return new char[length];
                        case 'S':
                            return new short[length];
                        case 'I':
                            return new int[length];
                        case 'J':
                            return new long[length];
                        case 'F':
                            return new float[length];
                        case 'D':
                            return new double[length];
                        default:
                            break;
                    }
                }
            }
            return null;
        }

        /**
         * Set array's element with index position.
         * @param thread
         * @param ptrArray pointer to array object.
         * @param csignature type of array.
         * @param index index of element.
         * @param value value to assign.
         */
        @CEntryPoint(name = "svm_dbg_array_set", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static void arraySetValue(@SuppressWarnings("unused") IsolateThread thread,
                                         Pointer ptrArray,
                                         CCharPointer csignature,
                                         int index,
                                         Pointer value) {
            Object obj = ptrArray.toObject();
            String strSignatura = Utf8.utf8ToString(csignature);
            Log
                    .log()
                    .string("signatura: " + strSignatura)
                    .character('\n');
            if (strSignatura.startsWith("[")) {
                if (strSignatura.startsWith("[L") && strSignatura.endsWith(";")) {
                    Log
                            .log()
                            .string("object array: " + strSignatura.substring(2, strSignatura.length() - 1))
                            .character('\n');

                    ((Object[]) obj)[index] = value.toObject();
                } else {
                    char typeLetter = strSignatura.charAt(1);
                    Log
                            .log()
                            .string("type: " + typeLetter)
                            .character('\n');
                    switch (typeLetter) {
                        case 'Z':
                            ((boolean[]) obj)[index] = value.rawValue() != 0;
                            return;
                        case 'B':
                            ((byte[]) obj)[index] = (byte) value.rawValue();
                            return;
                        case 'C':
                            ((char[]) obj)[index] = (char) value.rawValue();
                            return;
                        case 'S':
                            ((short[]) obj)[index] = (short) value.rawValue();
                            return;
                        case 'I':
                            ((int[]) obj)[index] = (int) value.rawValue();
                            return;
                        case 'J':
                            ((long[]) obj)[index] = value.rawValue();
                            return;
                        case 'F':
                            ((float[]) obj)[index] = (float) value.rawValue();
                            return;
                        case 'D':
                            ((double[]) obj)[index] = (double) value.rawValue();
                            return;
                        default:
                            break;
                    }
                }
            }
        }

        /**
         * Returns index element of array.
         * @param thread
         * @param ptrArray pointer to array.
         * @param csignature type of array
         * @param index index of desired element.
         * @return pointer to element.
         */
        @CEntryPoint(name = "svm_dbg_array_get", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static long arrayGetValue(@SuppressWarnings("unused") IsolateThread thread,
                                         Pointer ptrArray,
                                         CCharPointer csignature,
                                         int index) {
            Object obj = ptrArray.toObject();
            String strSignatura = Utf8.utf8ToString(csignature);
            Log
                    .log()
                    .string("signatura: " + strSignatura)
                    .character('\n');
            if (strSignatura.startsWith("[")) {
                if (strSignatura.startsWith("[L") && strSignatura.endsWith(";")) {
                    Log
                            .log()
                            .string("object array: " + strSignatura.substring(2, strSignatura.length() - 1))
                            .character('\n');
                    return Word.objectToUntrackedPointer(((Object[]) obj)[index]).rawValue();
                } else {
                    char typeLetter = strSignatura.charAt(1);
                    Log
                            .log()
                            .string("type: " + typeLetter)
                            .character('\n');
                    switch (typeLetter) {
                        case 'Z':
                            return ((boolean[]) obj)[index] ? 1 : 0;
                        case 'B':
                            return ((byte[]) obj)[index];
                        case 'C':
                            return ((char[]) obj)[index];
                        case 'S':
                            return ((short[]) obj)[index];
                        case 'I':
                            return ((int[]) obj)[index];
                        case 'J':
                            return ((long[]) obj)[index];
                        case 'F':
                            return (long) ((float[]) obj)[index];
                        case 'D':
                            return (long) ((double[]) obj)[index];
                        default:
                            break;
                    }
                }
            }
            return 0;
        }

        /**
         * Get classloader from the stack.
         * @param thread
         * @return pointer to instance classloader.
         */
        @NeverInline("make compiler happy")
        @CEntryPoint(name = "svm_dbg_classloader", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static ClassLoader getClassloaderFromStack(@SuppressWarnings("unused") IsolateThread thread) {
            Pointer sp = KnownIntrinsics.readStackPointer();
            int frame[] = {0};
            ClassLoader classLoaders[] = {null};
            JavaStackWalker.walkCurrentThread(sp, new JavaStackFrameVisitor() {
                @Override
                public boolean visitFrame(FrameInfoQueryResult frameInfo) {

                    Log.log()
                            .string(": frame-").unsigned(++frame[0]).string(": ");
                    Class<?> clazz = frameInfo.getSourceClass();
                    if (clazz != null) {
                        Log.log().string("className: ").string(clazz.getName());
                        classLoaders[0] = clazz.getClassLoader();
                        Log.log().string(", classLoader: ")
                                .string(classLoaders[0] != null ? classLoaders[0].getName() : "null");
                    }
                    Log.log().string(", location:")
                            .string(frameInfo.getSourceMethodName())
                            .string(" (")
                            .string(frameInfo.getSourceFileName())
                            .string(": ")
                            .unsigned(frameInfo.getSourceLineNumber())
                            .string(")")
                            .newline();
                    return false;
                }
            });

            return classLoaders[0];
        }

        /**
         * Returns loaded class by name for given classloader.
         * @param thread
         * @param ptrClassLoader classloader which loaded named class.
         * @param cClassName name of desired class.
         * @return pointer to class object.
         */
        @CEntryPoint(name = "svm_dbg_class_for_name", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static Object getClassForName(@SuppressWarnings("unused") IsolateThread thread, Pointer ptrClassLoader, CCharPointer cClassName) {
            Object classLoader = ptrClassLoader.toObject();
            Log.log().string("getClassForName: ").string(cClassName).newline();
            String className = Utf8.utf8ToString(cClassName);
            Optional<Class<?>> opt = Heap.getHeap().getLoadedClasses().stream().filter((it) -> {
                return it.getName().equals(className);
            }).findFirst();
            return opt.orElse(null);
        }

        /**
         * Allocates uninitialized instance of given class.
         * @param thread
         * @param ptrClass pointer to class.
         * @return returns uninitialized instance.
         */
        @CEntryPoint(name = "svm_dbg_allocate_object", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static Object getAllocateObject(@SuppressWarnings("unused") IsolateThread thread, Pointer ptrClass) {
            Object classObject = ptrClass.toObject();
            try {
                return Unsafe.getUnsafe().allocateInstance((Class)classObject);
            } catch (InstantiationException e) {
                return null;
            }
        }

        /**
         * Pin object.
         * @param thread
         * @param ptrObj pointer to object to pin.
         * @return "pin object" for given object, should be passed svm_dbg_unpin_object.
         */
        @CEntryPoint(name = "svm_dbg_pin_object", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static Object pinObject(@SuppressWarnings("unused") IsolateThread thread, Pointer ptrObj) {
            Object obj = ptrObj.toObject();
            return PinnedObject.create(obj);
        }

        /**
         * Unpin object.
         * @param thread
         * @param ptrObj "pin object" @see svm_dbg_pin_object
         */
        @CEntryPoint(name = "svm_dbg_unpin_object", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static void unPinObject(@SuppressWarnings("unused") IsolateThread thread, Pointer ptrObj) {
            PinnedObject obj = (PinnedObject)ptrObj.toObject();
            obj.close();
        }

        /**
         * Returns modifier for given class.
         * @param thread
         * @param ptrClass pointer to class.
         * @return integer representation for class's modifier.
         */
        @CEntryPoint(name = "svm_dbg_class_modifier", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static int classGetModifier(@SuppressWarnings("unused") IsolateThread thread, Pointer ptrClass) {
            Class<?> clazz = (Class<?>) ptrClass.toObject();
            DynamicHub hub = DynamicHub.fromClass(clazz);
            return hub.getModifiers();
        }

        /**
         * Returns array of fields' descriptors.
         * @param thread
         * @param ptrClass pointer of class.
         * @return array of fields descriptors in format name:modifier:type or name:modifier, depends on how much information
         * Graal VM can provide.
         */
        @CEntryPoint(name = "svm_dbg_class_fields", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static String[] classGetFields(@SuppressWarnings("unused") IsolateThread thread, Pointer ptrClass) {
            Class<?> clazz = (Class<?>)ptrClass.toObject();
            DynamicHub hub = DynamicHub.fromClass(clazz);
            Field[] fields = hub.getReachableFields();
            String[] strings = new String[fields.length];
            for (int i = 0; i < fields.length; ++i) {
                StringBuilder builder = new StringBuilder();
                builder.append(fields[i].getName())
                        .append(':')
                        .append(fields[i].getModifiers());
                Class<?> type = fields[i].getType();
                Log.log().string(fields[i].getName()).character(':')
                        .hex(fields[i].getModifiers())
                        .character(':');
                if (type != null) {
                    builder.append(":").append(toSignature(type));
                    Log.log().string(":").string(type.getTypeName());
                }
                Log.log().newline();
                strings[i] = builder.toString();
            }
            return strings;
        }

        private static String toSignature(Class<?> type) {
            if(type.isArray()){
                return "[" + toSignature(type.getComponentType());
            }
            if (type.isPrimitive()) {
                if (type == boolean.class)
                    return "Z";
                if (type == byte.class)
                    return "B";
                if (type == char.class)
                    return "C";
                if (type == short.class)
                    return "S";
                if (type == int.class)
                    return "I";
                if (type == long.class)
                    return "J";
                if (type == float.class)
                    return "F";
                if (type == double.class)
                    return "D";
                if (type == void.class)
                    return "V";
            }
            else
                return "L" + type.getName() + ";";
            return null;
        }

        /**
         * Returns array of methods' descriptors.
         * @param thread
         * @param ptrClass pointer of class.
         * @return array of fields descriptors in format name:modifier:signature or name:modifier, depends on how much information
         * Graal VM can provide.
         */
        @CEntryPoint(name = "svm_dbg_class_methods", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static String[] classGetMethods(@SuppressWarnings("unused") IsolateThread thread, Pointer ptrClass) {
            Class<?> clazz = (Class<?>)ptrClass.toObject();
            DynamicHub hub = DynamicHub.fromClass(clazz);
            Method[] methods = hub.getReachableMethods();
            Constructor<?>[] constructors = hub.getReachableConstructors();
            String[] strings = new String[methods.length + constructors.length];
            for (int i = 0; i < methods.length; ++i) {
                strings[i] = createMethodEntry(
                        methods[i].getName(),
                        methods[i].getModifiers(),
                        methods[i].getReturnType(),
                        methods[i].getParameterTypes());
            }
            for (int i = 0; i < constructors.length; ++i) {
                int index = methods.length + i;
                strings[index] = createMethodEntry(
                        "<init>",
                        constructors[i].getModifiers(),
                        void.class,
                        constructors[i].getParameterTypes());
            }
            return strings;
        }

        /**
         * Returns super class for given class.
         * @param thread
         * @param ptrClass pointer to class.
         * @return pointer to super class.
         */
        @CEntryPoint(name = "svm_dbg_class_super", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static String classGetSuper(@SuppressWarnings("unused") IsolateThread thread, Pointer ptrClass) {
            Class<?> clazz = (Class<?>) ptrClass.toObject();
            Log.log().string("svm_dbg_class_super:").string(clazz.getName()).newline();
            DynamicHub hub = DynamicHub.fromClass(clazz);
            return hub.getSuperHub().getName();
        }

        /**
         * Returns interfaces implemented by given class.
         * @param thread
         * @param ptrClass pointer to class.
         * @return array of interfaces names.
         */
        @CEntryPoint(name = "svm_dbg_class_interfaces", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static String[] classGetInterfaces(@SuppressWarnings("unused") IsolateThread thread, Pointer ptrClass) {
            Class<?> clazz = (Class<?>) ptrClass.toObject();
            DynamicHub hub = DynamicHub.fromClass(clazz);
            DynamicHub[] interfaces = hub.getInterfaces();
            String[] strings = new String[interfaces.length];
            for (int i = 0; i < interfaces.length; ++i) {
                strings[i] = interfaces[i].getName();
            }
            return strings;
        }

        /**
         * Returns pointers to java null.
         * @param thread
         * @return pointer to java null.
         */
        @CEntryPoint(name = "svm_dbg_null", include = IncludeDebugHelperMethods.class, publishAs = Publish.SymbolOnly)
        @CEntryPointOptions(prologue = SetThreadAndHeapBasePrologue.class)
        public static Object getNull(@SuppressWarnings("unused") IsolateThread thread) {
            return null;
        }
        private static String createMethodEntry(String name, int modifiers, Class<?> returnType, Class<?>[] parameters) {
            StringBuilder builder = new StringBuilder();
            builder.append(name)
                    .append(':')
                    .append(modifiers);
            Log.log().string(name).character(':')
                    .hex(modifiers);
            if (returnType != null && parameters != null) {
                String parameterSignature = Arrays.stream(parameters).map(c -> {
                    return toSignature(c);
                }).collect(Collectors.joining());
                builder.append(":")
                        .append('(')
                        .append(parameterSignature)
                        .append(')')
                        .append(toSignature(returnType));
                Log.log()
                        .string(":")
                        .character('(')
                        .string(parameterSignature)
                        .character(')')
                        .string(toSignature(returnType));
            }
            Log.log().newline();
            String s = builder.toString();
            return s;
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
