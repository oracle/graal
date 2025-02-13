/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.word.Word;

public class Isolates {
    public static final String IMAGE_HEAP_BEGIN_SYMBOL_NAME = "__svm_heap_begin";
    public static final String IMAGE_HEAP_END_SYMBOL_NAME = "__svm_heap_end";
    public static final String IMAGE_HEAP_RELOCATABLE_BEGIN_SYMBOL_NAME = "__svm_heap_relocatable_begin";
    public static final String IMAGE_HEAP_RELOCATABLE_END_SYMBOL_NAME = "__svm_heap_relocatable_end";
    public static final String IMAGE_HEAP_A_RELOCATABLE_POINTER_SYMBOL_NAME = "__svm_a_relocatable_pointer";
    public static final String IMAGE_HEAP_WRITABLE_BEGIN_SYMBOL_NAME = "__svm_heap_writable_begin";
    public static final String IMAGE_HEAP_WRITABLE_END_SYMBOL_NAME = "__svm_heap_writable_end";
    public static final String IMAGE_HEAP_WRITABLE_PATCHED_BEGIN_SYMBOL_NAME = "__svm_heap_writable_patched_begin";
    public static final String IMAGE_HEAP_WRITABLE_PATCHED_END_SYMBOL_NAME = "__svm_heap_writable_patched_end";

    /*
     * The values that are stored in the image heap symbols are either unaligned or at most aligned
     * to the build-time page size. When using these values at run-time (e.g., for changing the
     * memory access protection of the image heap), it may be necessary to round them to a multiple
     * of the run-time page size.
     */
    public static final CGlobalData<Word> IMAGE_HEAP_BEGIN = CGlobalDataFactory.forSymbol(IMAGE_HEAP_BEGIN_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_END = CGlobalDataFactory.forSymbol(IMAGE_HEAP_END_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_RELOCATABLE_BEGIN = CGlobalDataFactory.forSymbol(IMAGE_HEAP_RELOCATABLE_BEGIN_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_RELOCATABLE_END = CGlobalDataFactory.forSymbol(IMAGE_HEAP_RELOCATABLE_END_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_A_RELOCATABLE_POINTER = CGlobalDataFactory.forSymbol(IMAGE_HEAP_A_RELOCATABLE_POINTER_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_WRITABLE_BEGIN = CGlobalDataFactory.forSymbol(IMAGE_HEAP_WRITABLE_BEGIN_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_WRITABLE_END = CGlobalDataFactory.forSymbol(IMAGE_HEAP_WRITABLE_END_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_WRITABLE_PATCHED_BEGIN = CGlobalDataFactory.forSymbol(IMAGE_HEAP_WRITABLE_PATCHED_BEGIN_SYMBOL_NAME);
    public static final CGlobalData<Word> IMAGE_HEAP_WRITABLE_PATCHED_END = CGlobalDataFactory.forSymbol(IMAGE_HEAP_WRITABLE_PATCHED_END_SYMBOL_NAME);
    public static final CGlobalData<Pointer> ISOLATE_COUNTER = CGlobalDataFactory.createWord((WordBase) Word.unsigned(1));

    /* Only used if SpawnIsolates is disabled. */
    private static final CGlobalData<Pointer> SINGLE_ISOLATE_ALREADY_CREATED = CGlobalDataFactory.createWord();

    private static long startTimeNanos;
    private static long initDoneTimeMillis;
    private static long isolateId = -1;

    /**
     * Indicates if the current isolate is the first isolate in this process. If so, it can be
     * responsible for taking certain initialization steps (and, symmetrically, shutdown steps).
     * Such steps can be installing signals handlers or initializing built-in libraries that are
     * explicitly or implicitly shared between the isolates of the process (for example, because
     * they have a single native state that does not distinguish between isolates).
     */
    public static boolean isCurrentFirst() {
        VMError.guarantee(isolateId >= 0);
        return isolateId == 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void assignIsolateId(boolean isFirstIsolate) {
        if (isFirstIsolate) {
            isolateId = 0;
        } else {
            long nextId;
            long currentId;
            Pointer currentIdPointer = ISOLATE_COUNTER.get();
            do {
                currentId = currentIdPointer.readLong(0);
                nextId = currentId + 1;
            } while (!currentIdPointer.logicCompareAndSwapLong(0, currentId, nextId, NamedLocationIdentity.OFF_HEAP_LOCATION));
            isolateId = currentId;
            VMError.guarantee(isolateId > 0);
        }
    }

    public static void assignStartTime() {
        assert startTimeNanos == 0 : startTimeNanos;
        assert initDoneTimeMillis == 0 : initDoneTimeMillis;
        startTimeNanos = System.nanoTime();
        initDoneTimeMillis = TimeUtils.currentTimeMillis();
    }

    /** Epoch-based timestamp. If possible, {@link #getStartTimeNanos()} should be used instead. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long getInitDoneTimeMillis() {
        assert initDoneTimeMillis != 0;
        return initDoneTimeMillis;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long getUptimeMillis() {
        assert startTimeNanos != 0;
        return TimeUtils.millisSinceNanos(startTimeNanos);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long getStartTimeNanos() {
        assert startTimeNanos != 0;
        return startTimeNanos;
    }

    /**
     * Gets an identifier for the current isolate that is guaranteed to be unique for the first
     * {@code 2^64 - 1} isolates in the process.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long getIsolateId() {
        assert isolateId >= 0;
        return isolateId;
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    public static int checkIsolate(Isolate isolate) {
        return isolate.isNull() ? CEntryPointErrors.NULL_ARGUMENT : CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    public static int create(WordPointer isolatePointer, IsolateArguments arguments) {
        if (!SubstrateOptions.SpawnIsolates.getValue()) {
            if (!SINGLE_ISOLATE_ALREADY_CREATED.get().logicCompareAndSwapWord(0, Word.zero(), Word.signed(1), NamedLocationIdentity.OFF_HEAP_LOCATION)) {
                return CEntryPointErrors.SINGLE_ISOLATE_ALREADY_CREATED;
            }
        }

        WordPointer heapBasePointer = StackValue.get(WordPointer.class);
        int result = CommittedMemoryProvider.get().initialize(heapBasePointer, arguments);
        if (result != CEntryPointErrors.NO_ERROR) {
            return result;
        }

        Isolate isolate = heapBasePointer.read();
        result = checkIsolate(isolate);
        if (result != CEntryPointErrors.NO_ERROR) {
            isolatePointer.write(Word.nullPointer());
            return result;
        }

        isolatePointer.write(isolate);

        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static PointerBase getHeapBase(Isolate isolate) {
        return isolate;
    }
}
