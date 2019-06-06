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

/**
 * Supports function pointers as {@linkplain ObjectReferenceWalker object reference walkers} for the
 * heap, allowing garbage collection to visit and adjust object references in unmanaged memory.
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
    public void register(ObjectReferenceWalkerFunction walker, ComparableWord tag) {
        assert !Heap.getHeap().isAllocationDisallowed();
        assert walker.isNonNull();
        lock.lock();
        try {
            if (array.isNull()) { // lazily and only when actually used
                Heap.getHeap().getGC().registerObjectReferenceWalker(this);
            }
            register0(walker, tag);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Unregisters a walker that was registered with the same arguments to {@link #register}.
     */
    public boolean unregister(ObjectReferenceWalkerFunction walker, ComparableWord tag) {
        assert !Heap.getHeap().isAllocationDisallowed();
        assert walker.isNonNull();
        lock.lock();
        try {
            return unregister0(walker, tag);
        } finally {
            lock.unlock();
        }
    }

    @Uninterruptible(reason = "Manipulate walkers list atomically with regard to GC.")
    private void register0(ObjectReferenceWalkerFunction function, ComparableWord tag) {
        if (array.isNull()) {
            int capacity = 10;
            array = NonmovableArrays.createWordArray(scale(capacity));
        } else {
            if (scale(count) == NonmovableArrays.lengthOf(array)) {
                NonmovableArray<ComparableWord> oldArray = array;
                array = NonmovableArrays.createWordArray(scale(count << 1));
                NonmovableArrays.arraycopy(oldArray, 0, array, 0, scale(count));
                NonmovableArrays.releaseUnmanagedArray(oldArray);
            }
        }
        NonmovableArrays.setWord(array, scale(count), function);
        NonmovableArrays.setWord(array, scale(count) + 1, tag);
        count++;
    }

    @Uninterruptible(reason = "Manipulate walkers list atomically with regard to GC.")
    private boolean unregister0(ObjectReferenceWalkerFunction function, ComparableWord tag) {
        if (array.isNonNull()) {
            for (int i = 0; i < count; i++) {
                ObjectReferenceWalkerFunction fn = (ObjectReferenceWalkerFunction) NonmovableArrays.getWord(array, scale(i));
                if (fn.equal(function) && tag.equal(NonmovableArrays.getWord(array, 1 + scale(i)))) {
                    NonmovableArrays.arraycopy(array, scale(i + 1), array, scale(i), scale(count - 1 - i));
                    count--;
                    return true;
                }
            }
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int scale(int i) {
        return i << 1;
    }

    @Override
    public boolean walk(ObjectReferenceVisitor visitor) {
        if (array.isNonNull()) {
            for (int i = 0; i < count; i++) {
                ObjectReferenceWalkerFunction fun = (ObjectReferenceWalkerFunction) NonmovableArrays.getWord(array, scale(i));
                ComparableWord tag = NonmovableArrays.getWord(array, scale(i) + 1);
                fun.invoke(tag, visitor);
            }
        }
        return true;
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
