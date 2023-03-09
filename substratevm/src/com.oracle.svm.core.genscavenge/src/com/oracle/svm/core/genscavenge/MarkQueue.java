/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.struct.UniqueLocationIdentity;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;

import jdk.graal.compiler.word.ObjectAccess;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

class MarkQueue {

    private static final int ENTRIES_PER_SEGMENT = 10_000;

    private Segment first;

    private Segment last;

    private int pushCursor = 0;

    private int popCursor = 0;

    @Platforms(Platform.HOSTED_ONLY.class)
    MarkQueue() {
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void push(Object obj) {
        assert obj != null;

        if (last.isNull()) {
            last = allocateSegment();
            first = last;
        }

        if (pushCursor == ENTRIES_PER_SEGMENT) {
            Segment seg = allocateSegment();
            last.setNext(seg);
            last = seg;
            pushCursor = 0;
        }

        UnsignedWord offset = SizeOf.unsigned(Segment.class).add(
                pushCursor * ConfigurationValues.getObjectLayout().getReferenceSize()
        );
        ObjectAccess.writeObject(last, offset, obj);

        pushCursor++;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    Object pop() {
        if (isEmpty()) {
            return null;
        }

        UnsignedWord offset = SizeOf.unsigned(Segment.class).add(
                popCursor * ConfigurationValues.getObjectLayout().getReferenceSize()
        );
        Object obj = ObjectAccess.readObject(first, offset);
        assert obj != null;

        popCursor++;
        if (popCursor == ENTRIES_PER_SEGMENT) {
            Segment next = first.getNext();
            if (next.isNonNull()) {
                ImageSingletons.lookup(UnmanagedMemorySupport.class).free(first);
                first = next;
            } else {
                pushCursor = 0;
            }
            popCursor = 0;
        }

        return obj;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    boolean isEmpty() {
        return first.isNull() || first.equal(last) && popCursor == pushCursor;
    }

    @RawStructure
    interface Segment extends PointerBase {

        @RawField
        @UniqueLocationIdentity
        Segment getNext();

        @RawField
        @UniqueLocationIdentity
        void setNext(Segment p);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Segment allocateSegment() {
        UnsignedWord size = SizeOf.unsigned(Segment.class).add(
                ENTRIES_PER_SEGMENT * ConfigurationValues.getObjectLayout().getReferenceSize()
        );
        Segment segment = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(size);
        segment.setNext(WordFactory.nullPointer());
        return segment;
    }
}
