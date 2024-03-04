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
package com.oracle.svm.core.jvmti.utils;

import java.util.NoSuchElementException;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.nmt.NmtCategory;

import jdk.graal.compiler.word.Word;

public final class NonmovableMaps {
    private static final UninterruptibleUtils.AtomicLong runtimeArraysInExistence = new UninterruptibleUtils.AtomicLong(0);
    private static final OutOfMemoryError OUT_OF_MEMORY_ERROR = new OutOfMemoryError("Could not allocate nonmovable array");
    private static final NoSuchElementException NO_SUCH_ELEMENT_EXCEPTION = new NoSuchElementException("element is not in the map");

    private static final int ARRAY_FULL = -1;
    private static final int ARRAY_GROWTH_FACTOR = 2;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <K extends ComparableWord, V extends ComparableWord> NonmovableMap<K, V> createMap(int initialCapacity, NmtCategory nmtCategory) {
        assert initialCapacity > 0;
        NonmovableMap<K, V> map = ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(WordFactory.unsigned(SizeOf.get(NonmovableMap.class)));
        if (map.isNull()) {
            throw OUT_OF_MEMORY_ERROR;
        }
        NonmovableArray<K> keyArray = NonmovableArrays.createWordArray(initialCapacity, nmtCategory);
        NonmovableArray<V> valueArray = NonmovableArrays.createWordArray(initialCapacity, nmtCategory);
        if (keyArray.isNull() || valueArray.isNull()) {
            throw OUT_OF_MEMORY_ERROR;
        }
        map.setKeyArray(keyArray);
        map.setValueArray(valueArray);
        map.setNextAvailableIndex(0);
        map.setCurrentSize(initialCapacity);
        runtimeArraysInExistence.incrementAndGet();
        return map;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <K extends ComparableWord, V extends ComparableWord> void releaseMap(NonmovableMap<K, V> map) {
        NonmovableArrays.releaseUnmanagedArray(map.getKeyArray());
        NonmovableArrays.releaseUnmanagedArray(map.getValueArray());
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(map);
        assert map.isNull() || runtimeArraysInExistence.getAndDecrement() > 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <K extends ComparableWord, V extends ComparableWord> void fillValuesWithDefault(NonmovableMap<K, V> map, V defaultValue, int start, int end) {
        assert map.isNonNull();
        assert map.getCurrentSize() > end;
        for (int i = start; i < end; i++) {
            NonmovableArrays.setWord(map.getValueArray(), i, defaultValue);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <K extends ComparableWord, V extends ComparableWord> void put(NonmovableMap<K, V> map, K key, V value) {
        put(map, key, value, true);
    }

    // increase the size if at full capacity
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <K extends ComparableWord, V extends ComparableWord> void put(NonmovableMap<K, V> map, K key, V value, boolean growIfFull) {
        if (map.getCurrentSize() == ARRAY_FULL) {
            if (growIfFull) {
                growAndFillMap(map, WordFactory.nullPointer());
            } else {
                return;
            }
        }
        NonmovableArray<K> keyArray = map.getKeyArray();
        NonmovableArray<V> valueArray = map.getValueArray();
        assert valueArray.isNonNull() && keyArray.isNonNull();

        int nextAvailableIndex = map.getNextAvailableIndex();
        NonmovableArrays.setWord(keyArray, nextAvailableIndex, key);
        NonmovableArrays.setWord(valueArray, nextAvailableIndex, value);
        map.setNextAvailableIndex(nextAvailableIndex + 1);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <K extends ComparableWord, V extends ComparableWord> boolean contains(NonmovableMap<K, V> map, K key) {
        int filledMapSize = map.getNextAvailableIndex();
        int i = 0;
        Word current = WordFactory.zero();
        while (i < filledMapSize && current.notEqual(key)) {
            current = (Word) NonmovableArrays.getWord(map.getKeyArray(), i);
            i++;
        }
        return !(i == map.getCurrentSize() && current.notEqual(key));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <K extends ComparableWord, V extends ComparableWord> V get(NonmovableMap<K, V> map, K key) {
        int filledMapSize = map.getNextAvailableIndex();
        int i = 0;
        Word current = WordFactory.zero();
        while (i < filledMapSize && (current = (Word) NonmovableArrays.getWord(map.getKeyArray(), i)).notEqual(key)) {
            i++;
        }
        if (i == map.getCurrentSize() && current.notEqual(key)) {
            throw NO_SUCH_ELEMENT_EXCEPTION;
        }
        return NonmovableArrays.getWord(map.getValueArray(), i);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static <K extends ComparableWord, V extends ComparableWord> void growAndFillMap(NonmovableMap<K, V> map, V defaultValue) {
        int oldSize = map.getCurrentSize();
        growMap(map, NmtCategory.JVMTI);
        fillValuesWithDefault(map, defaultValue, oldSize, map.getCurrentSize());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static <K extends ComparableWord, V extends ComparableWord> void growMap(NonmovableMap<K, V> map, NmtCategory nmtCategory) {
        int oldSize = map.getCurrentSize();
        int newMapSize = oldSize * ARRAY_GROWTH_FACTOR;

        NonmovableArray<K> keyArray = map.getKeyArray();
        NonmovableArray<V> valueArray = map.getValueArray();

        assert valueArray.isNonNull() && keyArray.isNonNull();

        NonmovableArray<K> newKeyArray = NonmovableArrays.createWordArray(newMapSize, nmtCategory);
        NonmovableArrays.arraycopy(keyArray, 0, newKeyArray, 0, oldSize);
        NonmovableArrays.releaseUnmanagedArray(keyArray);

        NonmovableArray<V> newValueArray = NonmovableArrays.createWordArray(newMapSize, nmtCategory);
        NonmovableArrays.arraycopy(valueArray, 0, newValueArray, 0, oldSize);
        NonmovableArrays.releaseUnmanagedArray(valueArray);

        map.setKeyArray(keyArray);
        map.setValueArray(newValueArray);
        map.setNextAvailableIndex(oldSize);
        map.setCurrentSize(newMapSize);
    }

}
