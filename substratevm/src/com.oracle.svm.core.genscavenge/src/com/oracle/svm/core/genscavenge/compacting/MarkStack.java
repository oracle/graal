/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.compacting;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static jdk.vm.ci.code.CodeUtil.K;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.struct.UniqueLocationIdentity;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.ObjectAccess;

/**
 * LIFO stack for objects to visit during the mark phase. Without it, recursive calls could exhaust
 * the {@linkplain com.oracle.svm.core.stack.StackOverflowCheck yellow zone stack space} during GC.
 */
public final class MarkStack {
    private static final int SEGMENT_SIZE = 64 * K - /* avoid potential malloc() overallocation */ 64;

    @Fold
    static int entriesPerSegment() {
        return (SEGMENT_SIZE - SizeOf.get(Segment.class)) / ConfigurationValues.getObjectLayout().getReferenceSize();
    }

    private Segment top;
    private int cursor;

    @Platforms(Platform.HOSTED_ONLY.class)
    public MarkStack() {
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void push(Object obj) {
        assert obj != null;

        if (top.isNull() || cursor == entriesPerSegment()) {
            top = allocateSegment(top);
            cursor = 0;
        }

        UnsignedWord offset = getOffsetAtIndex(cursor);
        ObjectAccess.writeObject(top, offset, obj);
        cursor++;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Object pop() {
        assert !isEmpty();

        cursor--;
        UnsignedWord offset = getOffsetAtIndex(cursor);
        Object obj = ObjectAccess.readObject(top, offset);

        assert obj != null;

        if (cursor == 0) {
            if (top.getNext().isNonNull()) { // free eagerly, use cursor==0 only if completely empty
                Segment t = top;
                top = top.getNext();
                cursor = entriesPerSegment();
                NullableNativeMemory.free(t);
            } else {
                // keep a single segment
            }
        }

        return obj;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isEmpty() {
        assert cursor != 0 || top.isNull() || top.getNext().isNull() : "should see cursor == 0 only with a single segment (or none)";
        return top.isNull() || cursor == 0;
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
    private static Segment allocateSegment(Segment next) {
        UnsignedWord size = Word.unsigned(SEGMENT_SIZE);
        Segment segment = NullableNativeMemory.malloc(size, NmtCategory.GC);
        VMError.guarantee(segment.isNonNull(), "Could not allocate mark stack memory: malloc() returned null.");
        segment.setNext(next);
        return segment;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord getOffsetAtIndex(int index) {
        int refSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        return Word.unsigned(index).multiply(refSize).add(SizeOf.unsigned(Segment.class));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void tearDown() {
        if (top.isNonNull()) {
            assert top.getNext().isNull();
            NullableNativeMemory.free(top);
            top = Word.nullPointer();
        }
    }
}
