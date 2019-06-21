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
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.InvokeJavaFunctionPointer;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.JavaMethodLiteral;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectReferenceWalker;
import com.oracle.svm.core.util.VMError;

/**
 * Supports function pointers as {@linkplain ObjectReferenceWalker object reference walkers} for the
 * heap, allowing garbage collection to visit and adjust object references in unmanaged memory.
 * <p>
 * Implementation: linear probing hash table adapted from OpenJDK {@link java.util.IdentityHashMap}.
 */
public class UnmanagedReferenceWalkers extends ObjectReferenceWalker {
    @Fold
    public static UnmanagedReferenceWalkers singleton() {
        return ImageSingletons.lookup(UnmanagedReferenceWalkers.class);
    }

    public interface ObjectReferenceWalkerFunction extends CFunctionPointer {
        /**
         * The signature of a walker function. Use {@link JavaMethodLiteral} to obtain a function
         * pointer for a specific static Java method matching this signature.
         *
         * @param tag the word value specified during {@linkplain #register registration}.
         * @param visitor a visitor object that must be called for each object reference.
         */
        @InvokeJavaFunctionPointer
        void invoke(ComparableWord tag, ObjectReferenceVisitor visitor);
    }

    private final ReentrantLock lock = new ReentrantLock();
    private NonmovableArray<ComparableWord> array;
    private int count = 0;

    /**
     * Registers the specified walker function that is later invoked with the provided tag word as
     * its argument. This tag word is typically the address of a data structure in unmanaged memory.
     */
    public boolean register(ObjectReferenceWalkerFunction walker, ComparableWord tag) {
        assert !Heap.getHeap().isAllocationDisallowed();
        assert walker.notEqual(none());
        lock.lock();
        try {
            if (array.isNull()) { // lazily and only when actually used
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
    public boolean unregister(ObjectReferenceWalkerFunction walker, ComparableWord tag) {
        assert !Heap.getHeap().isAllocationDisallowed();
        assert walker.notEqual(none());
        lock.lock();
        try {
            return unregister0(walker, tag);
        } finally {
            lock.unlock();
        }
    }

    @Uninterruptible(reason = "Manipulate walkers list atomically with regard to GC.")
    private boolean register0(ObjectReferenceWalkerFunction function, ComparableWord tag) {
        if (array.isNull()) {
            array = NonmovableArrays.createWordArray(64);
        }
        int index;
        boolean resized;
        do {
            int length = NonmovableArrays.lengthOf(array);
            index = hashIndex(function, tag, length);
            while (NonmovableArrays.getWord(array, index).notEqual(none())) {
                assert !(NonmovableArrays.getWord(array, index).equal(function) &&
                                NonmovableArrays.getWord(array, index + 1).equal(tag)) : "Duplicate walkers not allowed";
                index = nextIndex(index, length);
            }
            resized = false;
            int newCount = count + 1;
            if (newCount + (newCount << 1) > length) { // enforce load factor
                resized = resize(length);
            }
        } while (resized);
        NonmovableArrays.setWord(array, index, function);
        NonmovableArrays.setWord(array, index + 1, tag);
        count++;
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean resize(int capacity) {
        final int maxCapacity = 1 << 29;
        int oldLength = NonmovableArrays.lengthOf(array);
        if (oldLength == 2 * maxCapacity) {
            VMError.guarantee(count <= maxCapacity - 1, "Maximum capacity exhausted");
            return false;
        }
        int newLength = 2 * capacity;
        if (oldLength >= newLength) {
            return false;
        }
        NonmovableArray<ComparableWord> oldArray = array;
        array = NonmovableArrays.createWordArray(newLength);
        for (int i = 0; i < oldLength; i += 2) {
            ComparableWord function = NonmovableArrays.getWord(oldArray, i);
            if (function.notEqual(none())) {
                NonmovableArrays.setWord(oldArray, i, none());
                ComparableWord tag = NonmovableArrays.getWord(oldArray, i + 1);
                int u = hashIndex(function, tag, newLength);
                while (NonmovableArrays.getWord(array, u).notEqual(none())) {
                    u = nextIndex(u, newLength);
                }
                NonmovableArrays.setWord(array, u, function);
                NonmovableArrays.setWord(array, u + 1, tag);
            }
        }
        NonmovableArrays.releaseUnmanagedArray(oldArray);
        return true;
    }

    @Uninterruptible(reason = "Manipulate walkers list atomically with regard to GC.")
    private boolean unregister0(ObjectReferenceWalkerFunction function, ComparableWord tag) {
        if (array.isNonNull()) {
            int length = NonmovableArrays.lengthOf(array);
            int index = hashIndex(function, tag, length);
            ComparableWord entryFunction = NonmovableArrays.getWord(array, index);
            while (entryFunction.notEqual(none())) {
                ComparableWord entryTag = NonmovableArrays.getWord(array, index + 1);
                if (entryFunction.equal(function) && entryTag.equal(tag)) {
                    NonmovableArrays.setWord(array, index, none());
                    NonmovableArrays.setWord(array, index + 1, none());
                    count--;
                    rehashAfterUnregisterAt(index);
                    return true;
                }
                index = nextIndex(index, length);
                entryFunction = NonmovableArrays.getWord(array, index);
            }
        }
        return false;
    }

    /** Rehashes possibly-colliding entries after deletion to preserve collision properties. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void rehashAfterUnregisterAt(int index) { // from IdentityHashMap: Knuth 6.4 Algorithm R
        int length = NonmovableArrays.lengthOf(array);
        int d = index;
        int i = nextIndex(d, length);
        ComparableWord function = NonmovableArrays.getWord(array, i);
        while (function.notEqual(none())) {
            ComparableWord tag = NonmovableArrays.getWord(array, i + 1);
            int r = hashIndex(function, tag, length);
            if ((i < r && (r <= d || d <= i)) || (r <= d && d <= i)) {
                NonmovableArrays.setWord(array, d, function);
                NonmovableArrays.setWord(array, d + 1, tag);
                NonmovableArrays.setWord(array, i, none());
                NonmovableArrays.setWord(array, i + 1, none());
                d = i;
            }
            i = nextIndex(i, length);
            function = NonmovableArrays.getWord(array, i);
        }
    }

    @Override
    public boolean walk(ObjectReferenceVisitor visitor) {
        if (array.isNonNull()) {
            int length = NonmovableArrays.lengthOf(array);
            for (int i = 0; i < length; i += 2) {
                ObjectReferenceWalkerFunction fun = (ObjectReferenceWalkerFunction) NonmovableArrays.getWord(array, i);
                if (fun.notEqual(none())) {
                    ComparableWord tag = NonmovableArrays.getWord(array, i + 1);
                    fun.invoke(tag, visitor);
                }
            }
        }
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static ComparableWord none() {
        return WordFactory.zero();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int hashIndex(ComparableWord function, ComparableWord tag, int length) {
        int h = (int) (function.rawValue() >> 32) * 31 + (int) function.rawValue();
        h = (h * 31 + (int) (tag.rawValue() >>> 32)) * 31 + (int) tag.rawValue();
        // Multiply by -127, and left-shift to use least bit as part of hash
        return ((h << 1) - (h << 8)) & (length - 1);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int nextIndex(int index, int length) {
        return (index + 2 < length) ? (index + 2) : 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void tearDown() {
        if (array.isNonNull()) {
            NonmovableArrays.releaseUnmanagedArray(array);
            array = WordFactory.nullPointer();
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
