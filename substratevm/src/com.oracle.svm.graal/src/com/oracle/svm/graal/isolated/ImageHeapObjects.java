/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

/**
 * Functionality for referring to an image heap object using its isolate-independent location.
 */
public final class ImageHeapObjects {
    @Uninterruptible(reason = "Called from uninterruptible code.")
    static boolean isInImageHeap(Object obj) {
        return obj == null || Heap.getHeap().isInImageHeap(obj);
    }

    /**
     * Provides the image heap location of the specified image heap object that is independent of a
     * specific isolate. Java {@code null} becomes {@link WordFactory#nullPointer() NULL}.
     */
    @SuppressWarnings("unchecked")
    public static <T> ImageHeapRef<T> ref(T t) {
        if (t == null) {
            return WordFactory.nullPointer();
        }
        VMError.guarantee(isInImageHeap(t));
        Word result = Word.objectToUntrackedPointer(t);
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            result = result.subtract(KnownIntrinsics.heapBase());
        }
        return (ImageHeapRef<T>) result;
    }

    /**
     * Provides the object instance in the current isolate at the given image heap location.
     * {@link WordFactory#nullPointer() NULL} becomes Java {@code null}.
     */
    public static <T> T deref(ImageHeapRef<T> ref) {
        if (ref.equal(WordFactory.nullPointer())) {
            return null;
        }
        Pointer objectAddress = (Pointer) ref;
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            objectAddress = objectAddress.add(KnownIntrinsics.heapBase());
        }
        Object obj = KnownIntrinsics.convertUnknownValue(objectAddress.toObject(), Object.class);
        VMError.guarantee(Heap.getHeap().isInImageHeap(obj));

        @SuppressWarnings("unchecked")
        T result = (T) obj;
        return result;
    }

    private ImageHeapObjects() {
    }
}
