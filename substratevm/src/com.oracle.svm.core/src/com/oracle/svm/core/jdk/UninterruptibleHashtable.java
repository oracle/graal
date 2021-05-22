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
package com.oracle.svm.core.jdk;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.locks.VMMutex;

/**
 * An uninterruptible hashtable with a fixed size that uses chaining in case of a collision.
 */
public abstract class UninterruptibleHashtable<T extends UninterruptibleEntry<T>> {
    private static final int DEFAULT_TABLE_LENGTH = 2053;

    private final T[] table;
    private final VMMutex mutex;

    private long nextId;
    private int size;

    @Platforms(Platform.HOSTED_ONLY.class)
    public UninterruptibleHashtable() {
        this(DEFAULT_TABLE_LENGTH);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public UninterruptibleHashtable(int primeLength) {
        this.table = createTable(primeLength);
        this.mutex = new VMMutex();
        this.size = 0;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected abstract T[] createTable(int length);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract boolean isEqual(T a, T b);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract T copyToHeap(T valueOnStack);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract void free(T t);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void clear() {
        for (int i = 0; i < table.length; i++) {
            T entry = table[i];
            while (entry.isNonNull()) {
                T tmp = entry;
                entry = entry.getNext();
                free(tmp);
            }
            table[i] = WordFactory.nullPointer();
        }
        size = 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setSize(int size) {
        this.size = size;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void teardown() {
        clear();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getSize() {
        return size;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long add(T valueOnStack) {
        assert valueOnStack.isNonNull();

        mutex.lockNoTransition();
        try {
            // Try to find the entry in the hashtable
            int index = Integer.remainderUnsigned(valueOnStack.getHash(), DEFAULT_TABLE_LENGTH);
            T entry = table[index];
            while (entry.isNonNull()) {
                if (isEqual(valueOnStack, entry)) {
                    return entry.getId();
                }
                entry = entry.getNext();
            }

            return insertEntry(index, valueOnStack);
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private long insertEntry(int index, T valueOnStack) {
        T newEntry = copyToHeap(valueOnStack);
        if (newEntry.isNonNull()) {
            long id = ++nextId;
            T existingEntry = table[index];
            newEntry.setNext(existingEntry);
            newEntry.setId(id);
            table[index] = newEntry;
            size++;
            return id;
        }
        return 0L;
    }

    public T[] getTable() {
        return table;
    }
}
