/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.util.VMError;

// Checkstyle: stop
import sun.misc.Unsafe;
// Checkstyle: resume

/**
 * Annotated replacements to be called from uninterruptible code for methods whose source I do not
 * control, and so can not annotate.
 *
 * For each of these methods I have to inline the body of the method I am replacing. This is a
 * maintenance nightmare. Fortunately these methods are simple.
 */
public class UninterruptibleUtils {

    public static class AtomicInteger {

        private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();
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
        public boolean compareAndSet(int expected, int update) {
            return UNSAFE.compareAndSwapInt(this, VALUE_OFFSET, expected, update);
        }
    }

    public static class AtomicLong {

        private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();
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
        public long decrementAndGet() {
            return addAndGet(-1);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long getAndDecrement() {
            return getAndAdd(-1);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean compareAndSet(long expected, long update) {
            return UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, expected, update);
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
         * Creates a new AtomicWord with initial value {@link WordFactory#zero}.
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
            return WordFactory.unsigned(value.get());
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
            return WordFactory.unsigned(value.getAndSet(newValue.rawValue()));
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
            return WordFactory.unsigned(value.getAndAdd(delta.rawValue()));
        }

        /**
         * Atomically adds the given value to the current value.
         *
         * @param delta the value to add
         * @return the updated value
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public final UnsignedWord addAndGet(UnsignedWord delta) {
            return WordFactory.unsigned(value.addAndGet(delta.rawValue()));
        }

        /**
         * Atomically subtracts the given value from the current value.
         *
         * @param delta the value to add
         * @return the previous value
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public final UnsignedWord getAndSubtract(UnsignedWord delta) {
            return WordFactory.unsigned(value.getAndAdd(-delta.rawValue()));
        }

        /**
         * Atomically subtracts the given value from the current value.
         *
         * @param delta the value to add
         * @return the updated value
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public final UnsignedWord subtractAndGet(UnsignedWord delta) {
            return WordFactory.unsigned(value.addAndGet(-delta.rawValue()));
        }
    }

    public static class AtomicPointer<T extends PointerBase> {

        private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();
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
            return WordFactory.pointer(value);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void set(T newValue) {
            value = newValue.rawValue();
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean compareAndSet(T expected, T update) {
            return UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, expected.rawValue(), update.rawValue());
        }
    }

    public static class AtomicReference<T> {

        private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();
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
            return UNSAFE.compareAndSwapObject(this, VALUE_OFFSET, expected, update);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @SuppressWarnings("unchecked")
        public final T getAndSet(T newValue) {
            return (T) UNSAFE.getAndSetObject(this, VALUE_OFFSET, newValue);
        }
    }

    /** Methods like the ones from {@link java.lang.Math} but annotated as uninterruptible. */
    public static class Math {

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int min(int a, int b) {
            return (a <= b) ? a : b;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int max(int a, int b) {
            return (a >= b) ? a : b;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static long max(long a, long b) {
            return (a >= b) ? a : b;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static long abs(long a) {
            return (a < 0) ? -a : a;
        }
    }

    public static class Long {
        /** Uninterruptible version of {@link java.lang.Long#numberOfLeadingZeros(long)}. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        // Checkstyle: stop
        public static int numberOfLeadingZeros(long i) {
        // @formatter:off
            // HD, Figure 5-6
            if (i == 0)
               return 64;
           int n = 1;
           int x = (int)(i >>> 32);
           if (x == 0) { n += 32; x = (int)i; }
           if (x >>> 16 == 0) { n += 16; x <<= 16; }
           if (x >>> 24 == 0) { n +=  8; x <<=  8; }
           if (x >>> 28 == 0) { n +=  4; x <<=  4; }
           if (x >>> 30 == 0) { n +=  2; x <<=  2; }
           n -= x >>> 31;
           return n;
           // @formatter:on
        }
        // Checkstyle: resume
    }

    public static class Integer {
        // Checkstyle: stop
        /** Uninterruptible version of {@link java.lang.Integer#numberOfLeadingZeros(int)}. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @SuppressWarnings("all")
        public static int numberOfLeadingZeros(int i) {
        // @formatter:off
            // HD, Figure 5-6
            if (i == 0)
                return 32;
            int n = 1;
            if (i >>> 16 == 0) { n += 16; i <<= 16; }
            if (i >>> 24 == 0) { n +=  8; i <<=  8; }
            if (i >>> 28 == 0) { n +=  4; i <<=  4; }
            if (i >>> 30 == 0) { n +=  2; i <<=  2; }
            n -= i >>> 31;
            return n;
            // @formatter:on
        }

        /** Uninterruptible version of {@link java.lang.Integer#highestOneBit(int)}. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @SuppressWarnings("all")
        public static int highestOneBit(int i) {
        // @formatter:off
            // HD, Figure 3-1
            i |= (i >>  1);
            i |= (i >>  2);
            i |= (i >>  4);
            i |= (i >>  8);
            i |= (i >> 16);
            return i - (i >>> 1);
            // @formatter:on
        }
        // Checkstyle: resume
    }
}
