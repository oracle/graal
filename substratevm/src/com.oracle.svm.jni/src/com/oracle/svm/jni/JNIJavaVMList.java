/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni;

import static org.graalvm.word.LocationIdentity.ANY_LOCATION;
import static org.graalvm.word.WordFactory.nullPointer;
import static org.graalvm.word.WordFactory.unsigned;
import static org.graalvm.word.WordFactory.zero;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.jni.nativeapi.JNIJavaVM;
import com.oracle.svm.jni.nativeapi.JNIJavaVMPointer;

/**
 * A process-global, lock-free list of JavaVM pointers. Implemented as arrays in native memory which
 * are linked together, using compare-and-set operations for modifications. This data structure
 * never shrinks.
 */
public class JNIJavaVMList {
    /* @formatter:off
     *
     * HEAD  -->  +------------------------+
     *            | capacity: UnsignedWord |
     *            | [0]: JavaVM            |
     *            | [1]: JavaVM            |
     *            | ...                    |
     *            | [capacity-1]: JavaVM   |
     *            | next: Pointer          |  -->  +----------+
     *            +------------------------+       | capacity |
     *                                             | ...      |
     *                                             | next     |  -->  null
     *                                             +----------+
     * @formatter:on
     */

    private static final UnsignedWord INITIAL_CAPACITY = unsigned(8);
    private static final CGlobalData<Pointer> HEAD = CGlobalDataFactory.createWord((Pointer) nullPointer());

    /** Insert a new entry at an arbitrary location. */
    public static void addJavaVM(JNIJavaVM newEntry) {
        final UnsignedWord wordSize = SizeOf.unsigned(WordPointer.class);
        Pointer nextPointer = HEAD.get();
        UnsignedWord capacity = zero();
        for (;;) {
            Pointer p = nextPointer.readWord(0);
            if (p.isNull()) { // No empty slots, create new array
                UnsignedWord newCapacity = capacity.notEqual(0) ? capacity.multiply(2) : INITIAL_CAPACITY;
                Pointer newArray = UnmanagedMemory.calloc(newCapacity.add(2 /* capacity and next */).multiply(wordSize));
                newArray.writeWord(0, newCapacity);
                newArray.writeWord(wordSize, newEntry);
                p = nextPointer.compareAndSwapWord(0, nullPointer(), newArray, ANY_LOCATION);
                if (p.equal(nullPointer())) {
                    return;
                }
                // Another thread already created and linked a new array, continue in that array
                UnmanagedMemory.free(newArray);
            }
            capacity = p.readWord(0);
            p = p.add(wordSize);
            UnsignedWord end = p.add(capacity.multiply(wordSize));
            while (p.belowThan(end)) {
                JNIJavaVM entry = p.readWord(0);
                if (entry.isNull() && p.logicCompareAndSwapWord(0, nullPointer(), newEntry, ANY_LOCATION)) {
                    return;
                }
                p = p.add(wordSize);
            }
            nextPointer = p;
        }
    }

    /** Remove an entry. */
    public static boolean removeJavaVM(JNIJavaVM javavm) {
        WordPointer p = HEAD.get().readWord(0);
        while (p.isNonNull()) {
            Word capacity = p.read(0);
            for (Word i = unsigned(1); i.belowOrEqual(capacity); i = i.add(1)) {
                JNIJavaVM entry = p.read(i);
                if (entry.equal(javavm)) {
                    p.write(i, nullPointer());
                    return true;
                }
            }
            p = p.read(capacity.add(1)); // next
        }
        return false;
    }

    /** Gather non-null entries in a buffer and provide the total number of non-null entries. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void gather(JNIJavaVMPointer buffer, int bufferLength, CIntPointer totalCountPointer) {
        int totalCount = 0;
        WordPointer p = HEAD.get().readWord(0);
        while (p.isNonNull()) {
            Word capacity = p.read(0);
            for (Word i = unsigned(1); i.belowOrEqual(capacity); i = i.add(1)) {
                JNIJavaVM entry = p.read(i);
                if (entry.isNonNull()) {
                    if (totalCount < bufferLength) {
                        buffer.write(totalCount, entry);
                    }
                    totalCount++;
                }
            }
            p = p.read(capacity.add(1)); // next
        }
        totalCountPointer.write(totalCount);
    }
}
