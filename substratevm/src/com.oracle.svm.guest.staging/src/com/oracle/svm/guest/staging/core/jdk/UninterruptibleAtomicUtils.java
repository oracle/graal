/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.core.jdk;

import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.impl.Word;

import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

import jdk.internal.misc.Unsafe;

/**
 * Atomic values whose operations can be called from uninterruptible code. The implementations
 * inline the corresponding operations because the JDK atomic classes cannot be annotated with
 * {@link Uninterruptible}.
 */
public final class UninterruptibleAtomicUtils {

    /** Prevents instantiation. */
    private UninterruptibleAtomicUtils() {
    }

    public static class AtomicBoolean {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long VALUE_OFFSET;

        static {
            try {
                VALUE_OFFSET = UNSAFE.objectFieldOffset(AtomicBoolean.class.getDeclaredField("value"));
            } catch (Throwable ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }

        private volatile boolean value;

        public AtomicBoolean(boolean value) {
            this.value = value;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean get() {
            return value;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void set(boolean newValue) {
            value = newValue;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean compareAndSet(boolean expected, boolean update) {
            return UNSAFE.compareAndSetBoolean(this, VALUE_OFFSET, expected, update);
        }
    }

    public static class AtomicInteger {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long VALUE_OFFSET;

        static {
            try {
                VALUE_OFFSET = UNSAFE.objectFieldOffset(AtomicInteger.class.getDeclaredField("value"));
            } catch (Throwable ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }

        private volatile int value;

        public AtomicInteger(int value) {
            this.value = value;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public int get() {
            return value;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void set(int newValue) {
            value = newValue;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public int incrementAndGet() {
            return UNSAFE.getAndAddInt(this, VALUE_OFFSET, 1) + 1;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public int getAndDecrement() {
            return UNSAFE.getAndAddInt(this, VALUE_OFFSET, -1);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public int decrementAndGet() {
            return UNSAFE.getAndAddInt(this, VALUE_OFFSET, -1) - 1;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public int getAndIncrement() {
            return UNSAFE.getAndAddInt(this, VALUE_OFFSET, 1);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean compareAndSet(int expected, int update) {
            return UNSAFE.compareAndSetInt(this, VALUE_OFFSET, expected, update);
        }
    }

    public static class AtomicLong {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long VALUE_OFFSET;

        static {
            try {
                VALUE_OFFSET = UNSAFE.objectFieldOffset(AtomicLong.class.getDeclaredField("value"));
            } catch (Throwable ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }

        private volatile long value;

        public AtomicLong(long value) {
            this.value = value;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long get() {
            return value;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void set(long newValue) {
            value = newValue;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long getAndSet(long newValue) {
            return UNSAFE.getAndSetLong(this, VALUE_OFFSET, newValue);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long getAndAdd(long delta) {
            return UNSAFE.getAndAddLong(this, VALUE_OFFSET, delta);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long addAndGet(long delta) {
            return getAndAdd(delta) + delta;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long incrementAndGet() {
            return addAndGet(1);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long getAndIncrement() {
            return getAndAdd(1);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long decrementAndGet() {
            return addAndGet(-1);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long getAndDecrement() {
            return getAndAdd(-1);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean compareAndSet(long expected, long update) {
            return UNSAFE.compareAndSetLong(this, VALUE_OFFSET, expected, update);
        }
    }

    /**
     * A {@link WordBase word} value that may be updated atomically. See the
     * {@link java.util.concurrent.atomic} package specification for description of the properties
     * of atomic variables.
     *
     * Similar to {@link AtomicReference}, but for {@link WordBase word} types. A dedicated
     * implementation is necessary because Object and word types cannot be mixed.
     */
    public static class AtomicWord<T extends WordBase> {

        /**
         * For simplicity, we convert the word value to a long and delegate to existing atomic
         * operations.
         */
        protected final AtomicLong value;

        /**
         * Creates a new AtomicWord with initial value {@link Word#zero}.
         */
        public AtomicWord() {
            value = new AtomicLong(0L);
        }

        /**
         * Gets the current value.
         *
         * @return the current value
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public final T get() {
            return Word.unsigned(value.get());
        }

        /**
         * Sets to the given value.
         *
         * @param newValue the new value
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public final void set(T newValue) {
            value.set(newValue.rawValue());
        }

        /**
         * Atomically sets to the given value and returns the old value.
         *
         * @param newValue the new value
         * @return the previous value
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public final T getAndSet(T newValue) {
            return Word.unsigned(value.getAndSet(newValue.rawValue()));
        }

        /**
         * Atomically sets the value to the given updated value if the current value {@code ==} the
         * expected value.
         *
         * @param expect the expected value
         * @param update the new value
         * @return {@code true} if successful. False return indicates that the actual value was not
         *         equal to the expected value.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public final boolean compareAndSet(T expect, T update) {
            return value.compareAndSet(expect.rawValue(), update.rawValue());
        }
    }

    /**
     * A {@link UnsignedWord} value that may be updated atomically. See the
     * {@link java.util.concurrent.atomic} package specification for description of the properties
     * of atomic variables.
     */
    public static class AtomicUnsigned extends AtomicWord<UnsignedWord> {

        /**
         * Atomically adds the given value to the current value.
         *
         * @param delta the value to add
         * @return the previous value
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public final UnsignedWord getAndAdd(UnsignedWord delta) {
            return Word.unsigned(value.getAndAdd(delta.rawValue()));
        }

        /**
         * Atomically adds the given value to the current value.
         *
         * @param delta the value to add
         * @return the updated value
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public final UnsignedWord addAndGet(UnsignedWord delta) {
            return Word.unsigned(value.addAndGet(delta.rawValue()));
        }

        /**
         * Atomically subtracts the given value from the current value.
         *
         * @param delta the value to add
         * @return the previous value
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public final UnsignedWord getAndSubtract(UnsignedWord delta) {
            return Word.unsigned(value.getAndAdd(-delta.rawValue()));
        }

        /**
         * Atomically subtracts the given value from the current value.
         *
         * @param delta the value to add
         * @return the updated value
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public final UnsignedWord subtractAndGet(UnsignedWord delta) {
            return Word.unsigned(value.addAndGet(-delta.rawValue()));
        }
    }

    public static class AtomicPointer<T extends PointerBase> {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long VALUE_OFFSET;

        static {
            try {
                VALUE_OFFSET = UNSAFE.objectFieldOffset(AtomicPointer.class.getDeclaredField("value"));
            } catch (Throwable ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }

        private volatile long value;

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public T get() {
            return Word.pointer(value);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void set(T newValue) {
            value = newValue.rawValue();
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean compareAndSet(T expected, T update) {
            return UNSAFE.compareAndSetLong(this, VALUE_OFFSET, expected.rawValue(), update.rawValue());
        }
    }

    public static class AtomicReference<T> {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long VALUE_OFFSET;

        static {
            try {
                VALUE_OFFSET = UNSAFE.objectFieldOffset(AtomicReference.class.getDeclaredField("value"));
            } catch (Throwable ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }

        private volatile T value;

        public AtomicReference() {
        }

        public AtomicReference(T value) {
            this.value = value;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public T get() {
            return value;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void set(T newValue) {
            value = newValue;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean compareAndSet(T expected, T update) {
            return UNSAFE.compareAndSetReference(this, VALUE_OFFSET, expected, update);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @SuppressWarnings("unchecked")
        public final T getAndSet(T newValue) {
            return (T) UNSAFE.getAndSetReference(this, VALUE_OFFSET, newValue);
        }
    }
}
