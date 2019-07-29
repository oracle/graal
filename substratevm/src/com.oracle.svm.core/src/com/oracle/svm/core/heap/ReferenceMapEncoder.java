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
package com.oracle.svm.core.heap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.Set;

import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;

import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.util.ByteArrayReader;

public abstract class ReferenceMapEncoder {
    public interface OffsetIterator extends PrimitiveIterator.OfInt {
        @Override
        boolean hasNext();

        @Override
        int nextInt();

        /**
         * Returns whether the next offset that will be returned by {@link #nextInt()} refers to a
         * compressed pointer.
         *
         * @throws NoSuchElementException
         */
        boolean isNextCompressed();

        boolean isNextDerived();

        Set<Integer> getDerivedOffsets(int baseOffset);
    }

    public interface Input {
        boolean isEmpty();

        OffsetIterator getOffsets();
    }

    private final HashMap<ReferenceMapEncoder.Input, Long> usageCounts = new HashMap<>();
    protected final HashMap<ReferenceMapEncoder.Input, Long> encodings = new HashMap<>();
    protected final UnsafeArrayTypeWriter writeBuffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());

    public void add(ReferenceMapEncoder.Input input) {
        if (input == null || input.isEmpty()) {
            return;
        }
        Long oldCount = usageCounts.get(input);
        Long newCount = oldCount == null ? 1 : oldCount.longValue() + 1;
        usageCounts.put(input, newCount);
    }

    public NonmovableArray<Byte> encodeAll() {
        assert writeBuffer.getBytesWritten() == 0 : "encodeAll() must not be called multiple times";

        /*
         * Sort reference map by usage count, so that frequently used maps get smaller indices
         * (which can be encoded in fewer bytes).
         */
        List<Map.Entry<ReferenceMapEncoder.Input, Long>> sortedEntries = new ArrayList<>(usageCounts.entrySet());
        sortedEntries.sort((o1, o2) -> -Long.compare(o1.getValue(), o2.getValue()));

        encodeAll(sortedEntries);

        int length = TypeConversion.asS4(writeBuffer.getBytesWritten());
        NonmovableArray<Byte> array = NonmovableArrays.createByteArray(length);
        writeBuffer.toByteBuffer(NonmovableArrays.asByteBuffer(array));
        return array;
    }

    public long lookupEncoding(ReferenceMapEncoder.Input referenceMap) {
        if (referenceMap == null) {
            return CodeInfoQueryResult.NO_REFERENCE_MAP;
        } else if (referenceMap.isEmpty()) {
            return CodeInfoQueryResult.EMPTY_REFERENCE_MAP;
        } else {
            Long result = encodings.get(referenceMap);
            assert result != null && result.longValue() != CodeInfoQueryResult.NO_REFERENCE_MAP && result.longValue() != CodeInfoQueryResult.EMPTY_REFERENCE_MAP;
            return result.longValue();
        }
    }

    protected abstract void encodeAll(List<Entry<Input, Long>> sortedEntries);

}
