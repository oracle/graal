/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.collections;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;

/**
 * An uninterruptible hashtable with a fixed size that uses chaining in case of a collision.
 */
public abstract class AbstractUninterruptibleHashtable implements UninterruptibleHashtable {
    private static final int DEFAULT_TABLE_LENGTH = 2053;

    private final NmtCategory nmtCategory;
    private final UninterruptibleEntry[] table;
    private int size;

    @Platforms(Platform.HOSTED_ONLY.class)
    public AbstractUninterruptibleHashtable(NmtCategory nmtCategory) {
        this(nmtCategory, DEFAULT_TABLE_LENGTH);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("this-escape")
    public AbstractUninterruptibleHashtable(NmtCategory nmtCategory, int primeLength) {
        this.nmtCategory = nmtCategory;
        this.table = createTable(primeLength);
        this.size = 0;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected abstract UninterruptibleEntry[] createTable(int length);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract boolean isEqual(UninterruptibleEntry a, UninterruptibleEntry b);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected UninterruptibleEntry copyToHeap(UninterruptibleEntry pointerOnStack, UnsignedWord sizeToAlloc) {
        UninterruptibleEntry pointerOnHeap = NullableNativeMemory.malloc(sizeToAlloc, nmtCategory);
        if (pointerOnHeap.isNonNull()) {
            UnmanagedMemoryUtil.copy((Pointer) pointerOnStack, (Pointer) pointerOnHeap, sizeToAlloc);
            return pointerOnHeap;
        }
        return Word.nullPointer();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void free(UninterruptibleEntry entry) {
        size--;
        NullableNativeMemory.free(entry);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected UninterruptibleEntry insertEntry(UninterruptibleEntry valueOnStack) {
        int index = Integer.remainderUnsigned(valueOnStack.getHash(), DEFAULT_TABLE_LENGTH);
        UninterruptibleEntry newEntry = copyToHeap(valueOnStack);
        if (newEntry.isNonNull()) {
            UninterruptibleEntry existingEntry = table[index];
            newEntry.setNext(existingEntry);
            table[index] = newEntry;
            size++;
            return newEntry;
        }
        return Word.nullPointer();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean remove(UninterruptibleEntry valueOnStack) {
        int index = Integer.remainderUnsigned(valueOnStack.getHash(), DEFAULT_TABLE_LENGTH);
        UninterruptibleEntry entry = table[index];
        UninterruptibleEntry prev = Word.nullPointer();
        while (entry.isNonNull()) {
            if (isEqual(valueOnStack, entry)) {
                if (prev.isNull()) {
                    table[index] = entry.getNext();
                } else {
                    prev.setNext(entry.getNext());
                }
                free(entry);
                return true;
            }
            prev = entry;
            entry = entry.getNext();
        }
        return false;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getSize() {
        return size;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UninterruptibleEntry[] getTable() {
        return table;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UninterruptibleEntry get(UninterruptibleEntry valueOnStack) {
        int index = Integer.remainderUnsigned(valueOnStack.getHash(), DEFAULT_TABLE_LENGTH);
        UninterruptibleEntry entry = table[index];
        while (entry.isNonNull()) {
            if (isEqual(valueOnStack, entry)) {
                return entry;
            }
            entry = entry.getNext();
        }
        return Word.nullPointer();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UninterruptibleEntry getOrPut(UninterruptibleEntry valueOnStack) {
        assert valueOnStack.isNonNull();

        UninterruptibleEntry entry = get(valueOnStack);
        if (entry.isNonNull()) {
            return entry;
        } else {
            return insertEntry(valueOnStack);
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean putIfAbsent(UninterruptibleEntry valueOnStack) {
        assert valueOnStack.isNonNull();

        UninterruptibleEntry existingEntry = get(valueOnStack);
        if (existingEntry.isNonNull()) {
            return false;
        } else {
            UninterruptibleEntry newEntry = insertEntry(valueOnStack);
            return newEntry.isNonNull();
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UninterruptibleEntry putNew(UninterruptibleEntry valueOnStack) {
        assert valueOnStack.isNonNull();
        assert get(valueOnStack).isNull();
        return insertEntry(valueOnStack);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void clear() {
        for (int i = 0; i < table.length; i++) {
            UninterruptibleEntry entry = table[i];
            while (entry.isNonNull()) {
                UninterruptibleEntry tmp = entry;
                entry = entry.getNext();
                free(tmp);
            }
            table[i] = Word.nullPointer();
        }
        assert size == 0 : "The table is not empty!";
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void teardown() {
        clear();
    }
}
