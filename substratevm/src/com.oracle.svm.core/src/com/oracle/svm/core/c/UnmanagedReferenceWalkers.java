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
package com.oracle.svm.core.c;

import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectReferenceWalker;
import com.oracle.svm.core.jdk.IdentityHashCodeSupport;
import com.oracle.svm.core.util.VMError;

/**
 * Supports {@linkplain UnmanagedObjectReferenceWalker reference walkers} that visit and can adjust
 * object references in unmanaged memory.
 * <p>
 * Implementation: linear probing hash table adapted from OpenJDK {@link java.util.IdentityHashMap}.
 */
public class UnmanagedReferenceWalkers extends ObjectReferenceWalker {
    @Fold
    public static UnmanagedReferenceWalkers singleton() {
        return ImageSingletons.lookup(UnmanagedReferenceWalkers.class);
    }

    public interface UnmanagedObjectReferenceWalker {
        /**
         * Calls the provided visitor on object references.
         *
         * @param tag the word value specified during {@linkplain #register registration}.
         * @param visitor a visitor object that must be called for each object reference.
         */
        void invoke(ComparableWord tag, ObjectReferenceVisitor visitor);
    }

    private final ReentrantLock lock = new ReentrantLock();
    private NonmovableObjectArray<UnmanagedObjectReferenceWalker> walkers;
    private NonmovableArray<ComparableWord> tags;
    private int count = 0;

    /**
     * Registers the specified walker function that is later invoked with the provided tag word as
     * its argument. This tag word is typically the address of a data structure in unmanaged memory.
     */
    public boolean register(UnmanagedObjectReferenceWalker walker, ComparableWord tag) {
        assert !Heap.getHeap().isAllocationDisallowed();
        assert walker != null;
        System.identityHashCode(walker); // trigger identity hash code assignment (interruptible)
        lock.lock();
        try {
            if (walkers.isNull()) { // lazily and only when actually used
                Heap.getHeap().getGC().registerObjectReferenceWalker(this);
            }
            return register0(walker, tag);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Unregisters a walker that was registered with the same arguments to {@link #register}.
     */
    public boolean unregister(UnmanagedObjectReferenceWalker walker, ComparableWord tag) {
        assert !Heap.getHeap().isAllocationDisallowed();
        assert walker != null;
        System.identityHashCode(walker); // trigger identity hash code assignment (interruptible)
        lock.lock();
        try {
            return unregister0(walker, tag);
        } finally {
            lock.unlock();
        }
    }

    @Uninterruptible(reason = "Manipulate walkers list atomically with regard to GC.")
    private boolean register0(UnmanagedObjectReferenceWalker walker, ComparableWord tag) {
        if (walkers.isNull()) {
            walkers = NonmovableArrays.createObjectArray(32);
            tags = NonmovableArrays.createWordArray(32);
        }
        int index;
        boolean resized;
        do {
            int length = getLength();
            index = hashIndex(walker, tag, length);
            while (NonmovableArrays.getObject(walkers, index) != null) {
                assert !(NonmovableArrays.getObject(walkers, index) == walker &&
                                NonmovableArrays.getWord(tags, index).equal(tag)) : "Duplicate walkers not allowed";
                index = nextIndex(index, length);
            }
            resized = false;
            int newCount = count + 1;
            if (newCount + (newCount << 1) > (length << 1)) { // enforce 3/4 load factor
                resized = resize(length << 1);
            }
        } while (resized);
        NonmovableArrays.setObject(walkers, index, walker);
        NonmovableArrays.setWord(tags, index, tag);
        count++;
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean resize(int newLength) {
        assert SubstrateUtil.isPowerOf2(newLength);
        final int maxLength = 1 << 30;
        int oldLength = getLength();
        if (oldLength == maxLength) {
            VMError.guarantee(count < maxLength - 1, "Maximum capacity exhausted");
            return false;
        }
        if (oldLength >= newLength) {
            return false;
        }
        NonmovableObjectArray<UnmanagedObjectReferenceWalker> oldWalkers = walkers;
        NonmovableArray<ComparableWord> oldTags = tags;
        walkers = NonmovableArrays.createObjectArray(newLength);
        tags = NonmovableArrays.createWordArray(newLength);
        for (int i = 0; i < oldLength; i++) {
            UnmanagedObjectReferenceWalker walker = NonmovableArrays.getObject(oldWalkers, i);
            if (walker != null) {
                ComparableWord tag = NonmovableArrays.getWord(oldTags, i);
                NonmovableArrays.setObject(oldWalkers, i, null);
                NonmovableArrays.setWord(oldTags, i, WordFactory.zero());
                int u = hashIndex(walker, tag, newLength);
                while (NonmovableArrays.getObject(walkers, u) != null) {
                    u = nextIndex(u, newLength);
                }
                NonmovableArrays.setObject(walkers, u, walker);
                NonmovableArrays.setWord(tags, u, tag);
            }
        }
        NonmovableArrays.releaseUnmanagedArray(oldWalkers);
        NonmovableArrays.releaseUnmanagedArray(oldTags);
        return true;
    }

    @Uninterruptible(reason = "Manipulate walkers list atomically with regard to GC.")
    private boolean unregister0(UnmanagedObjectReferenceWalker walker, ComparableWord tag) {
        if (walkers.isNonNull()) {
            int length = getLength();
            int index = hashIndex(walker, tag, length);
            UnmanagedObjectReferenceWalker entryWalker = NonmovableArrays.getObject(walkers, index);
            while (entryWalker != null) {
                ComparableWord entryTag = NonmovableArrays.getWord(tags, index);
                if (entryWalker == walker && entryTag.equal(tag)) {
                    NonmovableArrays.setObject(walkers, index, null);
                    NonmovableArrays.setWord(tags, index, WordFactory.zero());
                    count--;
                    rehashAfterUnregisterAt(index);
                    return true;
                }
                index = nextIndex(index, length);
                entryWalker = NonmovableArrays.getObject(walkers, index);
            }
        }
        return false;
    }

    /** Rehashes possibly-colliding entries after deletion to preserve collision properties. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void rehashAfterUnregisterAt(int index) { // from IdentityHashMap: Knuth 6.4 Algorithm R
        int length = getLength();
        int d = index;
        int i = nextIndex(d, length);
        UnmanagedObjectReferenceWalker walker = NonmovableArrays.getObject(walkers, i);
        while (walker != null) {
            ComparableWord tag = NonmovableArrays.getWord(tags, i);
            int r = hashIndex(walker, tag, length);
            if ((i < r && (r <= d || d <= i)) || (r <= d && d <= i)) {
                NonmovableArrays.setObject(walkers, d, walker);
                NonmovableArrays.setWord(tags, d, tag);
                NonmovableArrays.setObject(walkers, i, null);
                NonmovableArrays.setWord(tags, i, WordFactory.zero());
                d = i;
            }
            i = nextIndex(i, length);
            walker = NonmovableArrays.getObject(walkers, i);
        }
    }

    @Override
    public boolean walk(ObjectReferenceVisitor visitor) {
        if (walkers.isNonNull()) {
            int length = getLength();
            for (int i = 0; i < length; i++) {
                UnmanagedObjectReferenceWalker walker = NonmovableArrays.getObject(walkers, i);
                if (walker != null) {
                    ComparableWord tag = NonmovableArrays.getWord(tags, i);
                    walker.invoke(tag, visitor);
                }
            }
            NonmovableArrays.walkUnmanagedObjectArray(walkers, visitor);
        }
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private int getLength() {
        assert walkers.isNonNull() && tags.isNonNull() && NonmovableArrays.lengthOf(walkers) == NonmovableArrays.lengthOf(tags);
        return NonmovableArrays.lengthOf(walkers);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int hashIndex(UnmanagedObjectReferenceWalker walker, ComparableWord tag, int length) {
        int h = (IdentityHashCodeSupport.getExisting(walker) * 31 + (int) (tag.rawValue() >>> 32)) * 31 + (int) tag.rawValue();
        // Multiply by -127, and left-shift to use least bit as part of hash
        return ((h << 1) - (h << 8)) & (length - 1);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int nextIndex(int index, int length) {
        return (index + 1 < length) ? (index + 1) : 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void tearDown() {
        if (walkers.isNonNull()) {
            NonmovableArrays.releaseUnmanagedArray(walkers);
            walkers = NonmovableArrays.nullArray();
            NonmovableArrays.releaseUnmanagedArray(tags);
            tags = NonmovableArrays.nullArray();
        }
    }
}

@AutomaticFeature
class UnmanagedReferenceWalkersFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(UnmanagedReferenceWalkers.class, new UnmanagedReferenceWalkers());
    }
}
