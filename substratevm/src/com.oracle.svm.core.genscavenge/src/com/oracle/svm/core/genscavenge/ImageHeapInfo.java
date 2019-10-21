/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;

/**
 * The image heap consists of multiple partitions that don't necessarily form a contiguous block of
 * memory (there can be holes in between).
 */
public class ImageHeapInfo {
    @UnknownObjectField(types = Object.class) public Object firstReadOnlyPrimitiveObject;
    @UnknownObjectField(types = Object.class) public Object lastReadOnlyPrimitiveObject;

    @UnknownObjectField(types = Object.class) public Object firstReadOnlyReferenceObject;
    @UnknownObjectField(types = Object.class) public Object lastReadOnlyReferenceObject;

    @UnknownObjectField(types = Object.class) public Object firstWritablePrimitiveObject;
    @UnknownObjectField(types = Object.class) public Object lastWritablePrimitiveObject;

    @UnknownObjectField(types = Object.class) public Object firstWritableReferenceObject;
    @UnknownObjectField(types = Object.class) public Object lastWritableReferenceObject;

    @Platforms(value = Platform.HOSTED_ONLY.class)
    public ImageHeapInfo() {
    }

    @SuppressWarnings("hiding")
    @Platforms(value = Platform.HOSTED_ONLY.class)
    public void initialize(Object firstReadOnlyPrimitiveObject, Object lastReadOnlyPrimitiveObject, Object firstReadOnlyReferenceObject, Object lastReadOnlyReferenceObject,
                    Object firstWritablePrimitiveObject, Object lastWritablePrimitiveObject, Object firstWritableReferenceObject, Object lastWritableReferenceObject) {
        this.firstReadOnlyPrimitiveObject = firstReadOnlyPrimitiveObject;
        this.lastReadOnlyPrimitiveObject = lastReadOnlyPrimitiveObject;
        this.firstReadOnlyReferenceObject = firstReadOnlyReferenceObject;
        this.lastReadOnlyReferenceObject = lastReadOnlyReferenceObject;
        this.firstWritablePrimitiveObject = firstWritablePrimitiveObject;
        this.lastWritablePrimitiveObject = lastWritablePrimitiveObject;
        this.firstWritableReferenceObject = firstWritableReferenceObject;
        this.lastWritableReferenceObject = lastWritableReferenceObject;
    }

    /*
     * Convenience methods for asking if a Pointer is in the various native image heap partitions.
     *
     * These test [first .. last] rather than [first .. last), because last is in the partition.
     * These do not test for Pointers *into* the last object in each partition, though methods would
     * be easy to write, but slower.
     */

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInReadOnlyPrimitivePartition(final Pointer ptr) {
        return Word.objectToUntrackedPointer(firstReadOnlyPrimitiveObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastReadOnlyPrimitiveObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInWritablePrimitivePartition(final Pointer ptr) {
        return Word.objectToUntrackedPointer(firstWritablePrimitiveObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastWritablePrimitiveObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInReadOnlyReferencePartition(final Pointer ptr) {
        return Word.objectToUntrackedPointer(firstReadOnlyReferenceObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastReadOnlyReferenceObject));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInWritableReferencePartition(final Pointer ptr) {
        return Word.objectToUntrackedPointer(firstWritableReferenceObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastWritableReferenceObject));
    }

    public boolean isObjectInReadOnlyPrimitivePartition(Object obj) {
        return isInReadOnlyPrimitivePartition(Word.objectToUntrackedPointer(obj));
    }

    public boolean isObjectInWritablePrimitivePartition(Object obj) {
        return isInWritablePrimitivePartition(Word.objectToUntrackedPointer(obj));
    }

    public boolean isObjectInReadOnlyReferencePartition(Object obj) {
        return isInReadOnlyReferencePartition(Word.objectToUntrackedPointer(obj));
    }

    public boolean isObjectInWritableReferencePartition(Object obj) {
        return isInWritableReferencePartition(Word.objectToUntrackedPointer(obj));
    }
}
