/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectReferenceWalker;
import com.oracle.svm.core.util.VMError;

/**
 * Keeps track of {@link CodeInfo} structures of runtime-compiled methods (including invalidated and
 * not yet freed ones), {@linkplain ObjectReferenceWalker walks the object references they contain},
 * and releases their memory on tear-down.
 * <p>
 * Implementation: linear probing hash table adapted from OpenJDK {@link java.util.IdentityHashMap}.
 */
class RuntimeMethodInfoMemory extends ObjectReferenceWalker {
    @Fold
    public static RuntimeMethodInfoMemory singleton() {
        return ImageSingletons.lookup(RuntimeMethodInfoMemory.class);
    }

    private final ReentrantLock lock = new ReentrantLock();
    private NonmovableArray<CodeInfo> table;
    private int count = 0;

    public boolean add(CodeInfo info) {
        assert !Heap.getHeap().isAllocationDisallowed();
        assert info.isNonNull();
        lock.lock();
        try {
            if (table.isNull()) { // lazily and only when actually used
                Heap.getHeap().getGC().registerObjectReferenceWalker(this);
            }
            return add0(info);
        } finally {
            lock.unlock();
        }
    }

    public boolean remove(CodeInfo info) {
        assert !Heap.getHeap().isAllocationDisallowed();
        assert info.isNonNull();
        lock.lock();
        try {
            return remove0(info);
        } finally {
            lock.unlock();
        }
    }

    @Uninterruptible(reason = "Manipulate walkers list atomically with regard to GC.")
    private boolean add0(CodeInfo info) {
        if (table.isNull()) {
            table = NonmovableArrays.createWordArray(32);
        }
        int index;
        boolean resized;
        do {
            int length = NonmovableArrays.lengthOf(table);
            index = hashIndex(info, length);
            while (NonmovableArrays.getWord(table, index).isNonNull()) {
                assert NonmovableArrays.getWord(table, index).notEqual(info) : "Duplicate CodeInfo";
                index = nextIndex(index, length);
            }
            resized = false;
            int newCount = count + 1;
            if (newCount + (newCount << 1) > (length << 1)) { // enforce 3/4 load factor
                resized = resize(length << 1);
            }
        } while (resized);
        NonmovableArrays.setWord(table, index, info);
        count++;
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean resize(int newLength) {
        assert SubstrateUtil.isPowerOf2(newLength);
        final int maxLength = 1 << 30;
        int oldLength = NonmovableArrays.lengthOf(table);
        if (oldLength == maxLength) {
            VMError.guarantee(count < maxLength - 1, "Maximum capacity exhausted");
            return false;
        }
        if (oldLength >= newLength) {
            return false;
        }
        NonmovableArray<CodeInfo> oldTable = table;
        table = NonmovableArrays.createWordArray(newLength);
        for (int i = 0; i < oldLength; i++) {
            CodeInfo tag = NonmovableArrays.getWord(oldTable, i);
            if (tag.isNonNull()) {
                NonmovableArrays.setWord(oldTable, i, WordFactory.zero());
                int u = hashIndex(tag, newLength);
                while (NonmovableArrays.getWord(table, u).isNonNull()) {
                    u = nextIndex(u, newLength);
                }
                NonmovableArrays.setWord(table, u, tag);
            }
        }
        NonmovableArrays.releaseUnmanagedArray(oldTable);
        return true;
    }

    @Uninterruptible(reason = "Manipulate walkers list atomically with regard to GC.")
    private boolean remove0(CodeInfo info) {
        int length = NonmovableArrays.lengthOf(table);
        int index = hashIndex(info, length);
        CodeInfo entry = NonmovableArrays.getWord(table, index);
        while (entry.isNonNull()) {
            if (entry.equal(info)) {
                NonmovableArrays.setWord(table, index, WordFactory.zero());
                count--;
                rehashAfterUnregisterAt(index);
                return true;
            }
            index = nextIndex(index, length);
            entry = NonmovableArrays.getWord(table, index);
        }
        return false;
    }

    /** Rehashes possibly-colliding entries after deletion to preserve collision properties. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void rehashAfterUnregisterAt(int index) { // from IdentityHashMap: Knuth 6.4 Algorithm R
        int length = NonmovableArrays.lengthOf(table);
        int d = index;
        int i = nextIndex(d, length);
        CodeInfo info = NonmovableArrays.getWord(table, i);
        while (info.isNonNull()) {
            int r = hashIndex(info, length);
            if ((i < r && (r <= d || d <= i)) || (r <= d && d <= i)) {
                NonmovableArrays.setWord(table, d, info);
                NonmovableArrays.setWord(table, i, WordFactory.zero());
                d = i;
            }
            i = nextIndex(i, length);
            info = NonmovableArrays.getWord(table, i);
        }
    }

    @Override
    public boolean walk(ObjectReferenceVisitor visitor) {
        if (table.isNonNull()) {
            int length = NonmovableArrays.lengthOf(table);
            for (int i = 0; i < length; i++) {
                CodeInfo info = NonmovableArrays.getWord(table, i);
                if (info.isNonNull()) {
                    RuntimeMethodInfoAccess.walkReferences(info, visitor);
                }
            }
        }
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int hashIndex(CodeInfo tag, int length) {
        int h = (int) (tag.rawValue() >>> 32) * 31 + (int) tag.rawValue();
        // Multiply by -127, and left-shift to use least bit as part of hash
        return ((h << 1) - (h << 8)) & (length - 1);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int nextIndex(int index, int length) {
        return (index + 1 < length) ? (index + 1) : 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void tearDown() {
        if (table.isNonNull()) {
            int length = NonmovableArrays.lengthOf(table);
            for (int i = 0; i < length; i++) {
                CodeInfo info = NonmovableArrays.getWord(table, i);
                if (info.isNonNull()) {
                    RuntimeMethodInfoAccess.releaseMethodInfoOnTearDown(info);
                }
            }
            NonmovableArrays.releaseUnmanagedArray(table);
            table = NonmovableArrays.nullArray();
        }
    }
}

@AutomaticFeature
class RuntimeMethodInfoMemoryFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(RuntimeMethodInfoMemory.class, new RuntimeMethodInfoMemory());
    }
}
