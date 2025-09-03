/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Contains code to support Bytecode DSL interpreters. This code should not be used directly by
 * language implementations.
 *
 * @since 24.2
 */
public final class BytecodeSupport {

    private BytecodeSupport() {
        // no instances
    }

    /**
     * Special list to weakly keep track of clones. Assumes all operations run under a lock. Do not
     * use directly. We deliberately do not use an memory intensive event queue here, we might leave
     * around a few empty references here and there.
     *
     * @since 24.2
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final class CloneReferenceList<T> {

        private WeakReference<T>[] references = new WeakReference[4];
        private int size;

        /**
         * Adds a new reference to the list. Note references cannot be removed.
         *
         * @since 24.2
         */
        public void add(T reference) {
            if (size >= references.length) {
                resize();
            }
            references[size++] = new WeakReference<>(reference);
        }

        private void resize() {
            cleanup();
            if (size >= references.length) {
                references = Arrays.copyOf(references, references.length * 2);
            }
        }

        /**
         * Walks all references contained in the list.
         *
         * @since 24.2
         */
        public void forEach(Consumer<T> forEach) {
            boolean needsCleanup = false;
            for (int index = 0; index < size; index++) {
                T ref = references[index].get();
                if (ref != null) {
                    forEach.accept(ref);
                } else {
                    needsCleanup = true;
                }
            }
            if (needsCleanup) {
                cleanup();
            }
        }

        private void cleanup() {
            WeakReference<T>[] refs = this.references;
            int newIndex = 0;
            int oldSize = this.size;
            for (int oldIndex = 0; oldIndex < oldSize; oldIndex++) {
                WeakReference<T> ref = refs[oldIndex];
                T referent = ref.get();
                if (referent != null) {
                    if (newIndex != oldIndex) {
                        refs[newIndex] = ref;
                    }
                    newIndex++;
                }
            }
            Arrays.fill(refs, newIndex, oldSize, null);
            size = newIndex;
        }

    }

    /**
     * Specialized reusable and allocation-free buffer for constants.
     *
     * The first {@value #HASH_THRESHOLD} literals are stored linearly in {@code constants[]}. Once
     * that threshold is crossed, all literals are also entered into an open-address hash table
     * ({@code keys[]/values[]}) so that subsequent look-ups run in amortised O(1) without
     * allocating any {@link java.util.Map.Entry} objects.
     *
     * After the client has finished emitting a method it must call {@link #materialize()} to obtain
     * an immutable snapshot of the constant pool for that method. The buffer can then be filled
     * again without further allocations. Call {@link #clear()} at the end of using this buffer if
     * you want to release the object references or down-size the backing arrays.
     *
     * <strong>Not thread-safe.</strong>
     *
     * Intended for use in generated code. Do not use directly.
     *
     * @since 25.0
     */
    public static final class ConstantsBuffer {

        /**
         * Number of constants are contained within the constants array without going to a map.
         */
        private static final int HASH_THRESHOLD = 8;
        private static final int EMPTY = -1;
        private static final Object[] EMPTY_ARRAY = new Object[0];
        private static final int INITIAL_CAPACITY = 32;
        private static final int MAX_CAPACITY = 512;

        private Object[] constants;
        private int size;
        private Object[] keys;
        private int[] values;
        private int maxSize;

        public ConstantsBuffer() {
            initialize(INITIAL_CAPACITY);
        }

        private void initialize(int capacity) {
            assert this.size == 0;
            this.constants = new Object[capacity];
            initializeMap(capacity);
        }

        private void initializeMap(int capacity) {
            this.keys = new Object[capacity];
            this.values = initIntArray(capacity, EMPTY);
        }

        /**
         * Inserts {@code constant} (non-{@code null}) and returns its pool index. If an equal
         * constant is already present, its existing index is returned.
         *
         * @throws NullPointerException if {@code constant} is {@code null}
         * @since 25.0
         */
        public int add(Object c) {
            Objects.requireNonNull(c);

            int s = this.size;
            int result = fastAdd(c, s);
            if (result != EMPTY) {
                return result;
            }

            result = mapGet(c);
            if (result != EMPTY) {
                return result;
            }

            Object[] consts = this.constants;
            if (s >= consts.length) {
                consts = this.constants = Arrays.copyOf(consts, consts.length * 2);
            }
            result = size++;
            consts[result] = c;
            mapPut(c, result);
            return result;
        }

        /**
         * Inserts a constant without value and returns its index. The added null values are
         * intended to be later patched after {@link #materialize() materialization}. The client
         * must ensure that the reserved null slots do not contain duplicates already contained in
         * the materialized constants array.
         *
         * @since 25.0
         */
        public int addNull() {
            int index = size++;
            Object[] consts = this.constants;
            if (index >= consts.length) {
                this.constants = Arrays.copyOf(consts, consts.length * 2);
            }
            if (index == HASH_THRESHOLD) {
                fillMapFromConsts();
            }

            return index;
        }

        /**
         * Copies the internal {@code constants[]} array and returns it. All auxiliary data
         * structures are reset so that the buffer is ready for the next use.
         *
         * Note that this implementation deliberately does not clear all references contained in the
         * buffer. If all references should be cleared, then {@link #clear()} should be called after
         * materialization.
         *
         * @return a freshly allocated array of exactly {@code size()} elements, never
         *         <code>null</code>.
         * @since 25.0
         */
        public Object[] materialize() {
            if (this.size == 0) {
                return EMPTY_ARRAY;
            }
            int s = size;
            Object[] array = Arrays.copyOf(constants, s);
            this.maxSize = Math.max(s, this.maxSize);

            if (s >= HASH_THRESHOLD) {
                Arrays.fill(keys, null);
                Arrays.fill(values, EMPTY);
            }
            this.size = 0;
            return array;
        }

        public Object[] create() {
            if (this.size == 0) {
                return EMPTY_ARRAY;
            }
            return Arrays.copyOf(constants, size);
        }

        /**
         * Clears all object references held in this buffer. If the pool had grown beyond
         * {@value #MAX_CAPACITY} constants its internal arrays are replaced by new arrays of that
         * bounded size; otherwise the existing arrays are reused.
         *
         * <p>
         * The buffer must be {@linkplain #materialize() materialised} before it can be cleared.
         * </p>
         *
         * @throws IllegalStateException if {@link #materialize()} has not been called since the
         *             last insertion
         *
         * @since 25.0
         */
        public void clear() {
            if (this.size != 0) {
                throw new IllegalStateException("Cannot clear if it was not materialized first.");
            }
            if (this.maxSize > MAX_CAPACITY) {
                // reinitialize the entire buffer to avoid keeping huge
                // constant arrays in memory
                initialize(MAX_CAPACITY);
            } else {
                Arrays.fill(this.constants, 0, this.maxSize, null);
            }
            this.maxSize = 0;
        }

        private int fastAdd(Object c, int s) {
            if (s >= HASH_THRESHOLD) {
                return EMPTY;
            }
            Object[] consts = this.constants;
            assert consts.length >= HASH_THRESHOLD;

            for (int i = 0; i < s; i++) {
                Object d = consts[i];
                if (d == null) {
                    continue;
                }
                if (d == c || d.equals(c)) {
                    return i;
                }
            }
            if (s < HASH_THRESHOLD - 1) {
                int index = size++;
                consts[index] = c;
                return index;
            }
            fillMapFromConsts();
            return EMPTY;
        }

        private void fillMapFromConsts() {
            Object[] consts = this.constants;
            for (int i = 0; i < this.size; i++) {
                Object c = consts[i];
                if (c != null) {
                    mapPut(c, i);
                }
            }
        }

        private int mapGet(Object key) {
            int mask = this.keys.length - 1;
            int i = hash(key) & mask;
            while (true) {
                int v = this.values[i];
                if (v == EMPTY) {
                    return EMPTY;
                }
                if (this.keys[i].equals(key)) {
                    return v;  // hit
                }
                i = (i + 1) & mask;
            }
        }

        private void mapPut(Object key, int value) {
            int mapSize = this.keys.length;
            int s = this.size;
            // grow at 66 % load
            if (mapSize < (s + (s >> 1))) {
                rehash(this.keys.length << 1);
                mapSize = this.keys.length;
            }
            int mask = mapSize - 1;
            int i = hash(key) & mask;
            while (this.values[i] != EMPTY) {
                i = (i + 1) & mask;
            }
            this.keys[i] = key;
            this.values[i] = value;
        }

        private void rehash(int newCap) {
            initializeMap(newCap);
            fillMapFromConsts();
        }

        private static int hash(Object o) {
            int h = o.hashCode();
            return (h ^ (h >>> 16));
        }

        private static int[] initIntArray(int n, int val) {
            int[] a = new int[n];
            Arrays.fill(a, val);
            return a;
        }
    }

}
