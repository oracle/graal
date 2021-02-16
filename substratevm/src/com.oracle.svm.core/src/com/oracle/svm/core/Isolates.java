/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.os.CommittedMemoryProvider;

public class Isolates {
    public static final String IMAGE_HEAP_BEGIN_SYMBOL_NAME = "__svm_heap_begin";
    public static final String IMAGE_HEAP_END_SYMBOL_NAME = "__svm_heap_end";
    public static final String IMAGE_HEAP_RELOCATABLE_BEGIN_SYMBOL_NAME = "__svm_heap_relocatable_begin";
    public static final String IMAGE_HEAP_RELOCATABLE_END_SYMBOL_NAME = "__svm_heap_relocatable_end";
    public static final String IMAGE_HEAP_A_RELOCATABLE_POINTER_SYMBOL_NAME = "__svm_a_relocatable_pointer";
    public static final String IMAGE_HEAP_WRITABLE_BEGIN_SYMBOL_NAME = "__svm_heap_writable_begin";
    public static final String IMAGE_HEAP_WRITABLE_END_SYMBOL_NAME = "__svm_heap_writable_end";

    public static final CGlobalData<Word> IMAGE_HEAP_BEGIN = CGlobalDataFactory.forSymbol(IMAGE_HEAP_BEGIN_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_END = CGlobalDataFactory.forSymbol(IMAGE_HEAP_END_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_RELOCATABLE_BEGIN = CGlobalDataFactory.forSymbol(IMAGE_HEAP_RELOCATABLE_BEGIN_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_RELOCATABLE_END = CGlobalDataFactory.forSymbol(IMAGE_HEAP_RELOCATABLE_END_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_A_RELOCATABLE_POINTER = CGlobalDataFactory.forSymbol(IMAGE_HEAP_A_RELOCATABLE_POINTER_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_WRITABLE_BEGIN = CGlobalDataFactory.forSymbol(IMAGE_HEAP_WRITABLE_BEGIN_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_WRITABLE_END = CGlobalDataFactory.forSymbol(IMAGE_HEAP_WRITABLE_END_SYMBOL_NAME);

    @Uninterruptible(reason = "Thread state not yet set up.")
    public static int checkSanity(Isolate isolate) {
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            return isolate.isNull() ? CEntryPointErrors.NULL_ARGUMENT : CEntryPointErrors.NO_ERROR;
        } else {
            return isolate.equal(CEntryPointSetup.SINGLE_ISOLATE_SENTINEL) ? CEntryPointErrors.NO_ERROR : CEntryPointErrors.UNINITIALIZED_ISOLATE;
        }
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    public static int create(WordPointer isolatePointer, CEntryPointCreateIsolateParameters parameters) {
        int result = CommittedMemoryProvider.get().initialize(isolatePointer, parameters);
        if (result != CEntryPointErrors.NO_ERROR) {
            return result;
        }
        result = checkSanity(isolatePointer.read());
        if (result != CEntryPointErrors.NO_ERROR) {
            isolatePointer.write(WordFactory.nullPointer());
            return result;
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Thread state not yet set up.", mayBeInlined = true)
    public static PointerBase getHeapBase(Isolate isolate) {
        if (!SubstrateOptions.SpawnIsolates.getValue()) {
            return IMAGE_HEAP_BEGIN.get();
        }
        return isolate;
    }

    @Uninterruptible(reason = "Tear-down in progress.")
    public static int tearDownCurrent() {
        freeUnmanagedMemory();
        Heap.getHeap().tearDown();
        return CommittedMemoryProvider.get().tearDown();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void freeUnmanagedMemory() {
        CodeInfoTable.tearDown();
        NonmovableArrays.tearDown();
    }
}
