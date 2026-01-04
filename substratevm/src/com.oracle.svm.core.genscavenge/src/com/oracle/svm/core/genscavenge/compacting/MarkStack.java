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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.struct.UniqueLocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.ObjectAccess;
import jdk.graal.compiler.word.Word;

/**
 * LIFO stack for objects to visit during the mark phase. Without it, recursive calls could exhaust
 * the {@linkplain StackOverflowCheck yellow zone stack space} during GC. Callers can also push
 * other kinds of values, for example indexes for object arrays to keep track of the scanned range.
 */
public final class MarkStack {
    private static final int SEGMENT_SIZE = 64 * K - /* avoid potential malloc() overallocation */ 64;

    @Fold
    static int entriesPerSegment() {
        int headerSize = SizeOf.get(Segment.class);
        assert headerSize % entrySize() == 0 : "must be aligned";
        return (SEGMENT_SIZE - headerSize) / entrySize();
    }

    @Fold
    static int entrySize() {
        return ConfigurationValues.getObjectLayout().getReferenceSize();
    }

    private Segment top;
    private int cursor;

    @Platforms(Platform.HOSTED_ONLY.class)
    public MarkStack() {
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void pushObject(Object obj) {
        assert obj != null;
        Pointer entry = pushSlot();
        ObjectAccess.writeObject(Word.nullPointer(), entry, obj);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void pushInt(int value) {
        Pointer entry = pushSlot();
        entry.writeInt(0, value);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private Pointer pushSlot() {
        if (top.isNull() || cursor == entriesPerSegment()) {
            top = allocateSegment(top);
            cursor = 0;
        }

        Pointer entry = getEntryAddress(cursor);
        cursor++;
        return entry;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Object popObject() {
        assert !isEmpty();

        Pointer entry = getEntryAddress(cursor - 1);
        Object obj = ObjectAccess.readObject(Word.nullPointer(), entry);
        assert obj != null;

        popSlot();
        return obj;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int popInt() {
        assert !isEmpty();

        Pointer entry = getEntryAddress(cursor - 1);
        int v = entry.readInt(0);

        popSlot();
        return v;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void popSlot() {
        cursor--;
        if (cursor == 0) {
            if (top.getNext().isNonNull()) { // free eagerly, use cursor==0 only if completely empty
                freeTopSegment();
            } else {
                // keep a single segment
            }
        }
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

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void freeTopSegment() {
        Segment t = top;
        top = top.getNext();
        cursor = entriesPerSegment();
        NullableNativeMemory.free(t);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private Pointer getEntryAddress(int index) {
        Pointer firstEntry = ((Pointer) top).add(SizeOf.unsigned(Segment.class));
        return firstEntry.add(Word.unsigned(index).multiply(entrySize()));
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
