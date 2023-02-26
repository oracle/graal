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
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.c.function.RelocatedPointer;

import com.oracle.objectfile.ObjectFile;

/**
 * Offers a {@link ByteBuffer} with additional support for marking relocation sites within the
 * buffer for later processing.
 */
public final class RelocatableBuffer {
    private final ByteBuffer byteBuffer;
    private final SortedMap<Integer, Info> relocations;

    public RelocatableBuffer(long size, ByteOrder byteOrder) {
        int intSize = NumUtil.safeToInt(size);
        this.byteBuffer = ByteBuffer.wrap(new byte[intSize]).order(byteOrder);
        this.relocations = new TreeMap<>();
    }

    public void addRelocationWithoutAddend(int key, ObjectFile.RelocationKind relocationKind, Object targetObject) {
        relocations.put(key, new Info(relocationKind, 0L, targetObject));
    }

    public void addRelocationWithAddend(int key, ObjectFile.RelocationKind relocationKind, long addend, Object targetObject) {
        relocations.put(key, new Info(relocationKind, addend, targetObject));
    }

    public boolean hasRelocations() {
        return !relocations.isEmpty();
    }

    public Set<Map.Entry<Integer, RelocatableBuffer.Info>> getSortedRelocations() {
        return Collections.unmodifiableSet(relocations.entrySet());
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
        }

        public int getRelocationSize() {
            return ObjectFile.RelocationKind.getRelocationSize(relocationKind);
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
