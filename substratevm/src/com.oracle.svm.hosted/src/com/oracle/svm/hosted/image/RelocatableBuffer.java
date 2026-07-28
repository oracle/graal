/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.image;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.ObjIntConsumer;

import org.graalvm.nativeimage.c.function.RelocatedPointer;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.graal.code.CGlobalDataBasePointer;
import com.oracle.svm.core.meta.MethodRef;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.code.site.Reference;

/**
 * Offers a {@link ByteBuffer} with additional support for marking relocation sites within the
 * buffer for later processing.
 */
public final class RelocatableBuffer {
    private final ByteBuffer byteBuffer;
    private final NavigableMap<Integer, Info> relocations;

    public RelocatableBuffer(long size, ByteOrder byteOrder) {
        int intSize = NumUtil.safeToInt(size);
        this.byteBuffer = ByteBuffer.wrap(new byte[intSize]).order(byteOrder);
        this.relocations = new TreeMap<>();
    }

    public void addRelocationWithoutAddend(int offset, ObjectFile.RelocationKind relocationKind, Object targetObject) {
        addRelocationWithAddend(offset, relocationKind, 0, targetObject);
    }

    public void addRelocationWithAddend(int offset, ObjectFile.RelocationKind relocationKind, long addend, Object targetObject) {
        Info info = new Info(relocationKind, addend, targetObject);
        assert checkNoOverlaps(offset, info);
        Info existing = relocations.put(offset, info);
        VMError.guarantee(existing == null, "Offset %d already has relocation %s when inserting: %s", offset, existing, info);
    }

    private boolean checkNoOverlaps(int offset, Info info) {
        Map.Entry<Integer, Info> previous = relocations.floorEntry(offset);
        if (previous != null) {
            checkNoOverlap(previous.getKey(), previous.getValue(), offset, info);
        }
        Map.Entry<Integer, Info> next = relocations.higherEntry(offset);
        if (next != null) {
            checkNoOverlap(offset, info, next.getKey(), next.getValue());
        }
        return true;
    }

    private static void checkNoOverlap(int firstOffset, Info firstInfo, int secondOffset, Info secondInfo) {
        assert firstOffset <= secondOffset;
        int firstSize = firstInfo.getRelocationSize();
        assert (long) firstOffset + firstSize <= secondOffset : String.format("Relocation %s at offset %d overlaps with relocation %s at offset %d", firstInfo, firstOffset, secondInfo, secondOffset);
    }

    public boolean hasRelocations() {
        return !relocations.isEmpty();
    }

    public void forEachRelocation(ObjIntConsumer<Info> action) {
        relocations.forEach((offset, info) -> action.accept(info, offset));
    }

    public byte[] getBackingArray() {
        return byteBuffer.array();
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public static final class Info {
        private final ObjectFile.RelocationKind relocationKind;
        private final long addend;
        /**
         * The referenced object on the heap. If this is an instance of a {@link RelocatedPointer},
         * than the relocation is not treated as a data relocation but has a special meaning, e.g. a
         * code (text section) or constants (rodata section) relocation.
         */
        private final Object targetObject;

        Info(ObjectFile.RelocationKind kind, long addend, Object targetObject) {
            this.relocationKind = kind;
            this.addend = addend;
            this.targetObject = targetObject;

            /* Sanity check for allowed groups of target objects. */
            assert targetObject instanceof Reference || targetObject instanceof MethodRef || targetObject instanceof CGlobalDataBasePointer || targetObject instanceof ImageHeapConstant : targetObject;
        }

        public int getRelocationSize() {
            return relocationKind.getRelocationSize();
        }

        public ObjectFile.RelocationKind getRelocationKind() {
            return relocationKind;
        }

        public long getAddend() {
            return addend;
        }

        public Object getTargetObject() {
            return targetObject;
        }

        @Override
        public String toString() {
            return "RelocatableBuffer.Info(targetObject=" + targetObject + " relocationKind=" + relocationKind + " addend=" + addend + ")";
        }
    }
}
