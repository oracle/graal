/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Consumer;

import com.oracle.truffle.api.CompilerDirectives;

import sun.misc.Unsafe;

/**
 * Contains code to support Truffle operation interpreters. This code should not be used directly by
 * language implementations.
 *
 * @since 24.2
 */
public final class BytecodeSupport {
    private static final int MAX_PROFILE_COUNT = 0x3fffffff;

    static final Unsafe UNSAFE = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            // Fast path when we are trusted.
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            // Slow path when we are not trusted.
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }

    private BytecodeSupport() {
        // no instances
    }

    /**
     * Allocates memory to track branch profiles.
     *
     * @since 24.2
     */
    public static int[] allocateBranchProfiles(int numProfiles) {
        // Representation: [t1, f1, t2, f2, ..., tn, fn]
        return new int[numProfiles * 2];
    }

    /**
     * Increments a branch profile.
     *
     * @since 24.2
     */
    public static boolean profileBranch(int[] branchProfiles, int profileIndex, boolean condition) {
        int t = UNSAFE.getInt(branchProfiles, Unsafe.ARRAY_INT_BASE_OFFSET + (profileIndex * 2) * Unsafe.ARRAY_INT_INDEX_SCALE);
        int f = UNSAFE.getInt(branchProfiles, Unsafe.ARRAY_INT_BASE_OFFSET + (profileIndex * 2 + 1) * Unsafe.ARRAY_INT_INDEX_SCALE);
        boolean val = condition;
        if (val) {
            if (t == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (f == 0) {
                // Make this branch fold during PE
                val = true;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (t < MAX_PROFILE_COUNT) {
                    UNSAFE.putInt(branchProfiles, Unsafe.ARRAY_INT_BASE_OFFSET + (profileIndex * 2) * Unsafe.ARRAY_INT_INDEX_SCALE, t + 1);
                }
            }
        } else {
            if (f == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (t == 0) {
                // Make this branch fold during PE
                val = false;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (t < MAX_PROFILE_COUNT) {
                    UNSAFE.putInt(branchProfiles, Unsafe.ARRAY_INT_BASE_OFFSET + (profileIndex * 2 + 1) * Unsafe.ARRAY_INT_INDEX_SCALE, f + 1);
                }
            }
        }
        if (CompilerDirectives.inInterpreter()) {
            // no branch probability calculation in the interpreter
            return val;
        } else {
            int sum = t + f;
            return CompilerDirectives.injectBranchProbability((double) t / (double) sum, val);
        }
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

}
