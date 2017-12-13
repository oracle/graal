/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnsafeAccess;
import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * Annotated replacements to be called from uninterruptible code for methods whose source I do not
 * control, and so can not annotate.
 *
 * For each of these methods I have to inline the body of the method I am replacing. This is a
 * maintenance nightmare. Fortunately these methods are simple.
 */
public class UninterruptibleUtils {

    public static class AtomicInteger {

        /**
         * A mutable int value holder. I am not using anything <em>atomic</em> about the holder, but
         * I can not use a vanilla {@link Integer} because that would be immutable.
         */
        private final java.util.concurrent.atomic.AtomicInteger intHolder;

        public AtomicInteger(int value) {
            this.intHolder = new java.util.concurrent.atomic.AtomicInteger(value);
        }

        @Uninterruptible(reason = "Uninterruptible inline expansion")
        public int get() {
            final long valueOffset = Target_java_util_concurrent_atomic_AtomicInteger.valueOffset;
            return UnsafeAccess.UNSAFE.getInt(intHolder, valueOffset);
        }

        @Uninterruptible(reason = "Uninterruptible inline expansion")
        public void set(int newValue) {
            final long valueOffset = Target_java_util_concurrent_atomic_AtomicInteger.valueOffset;
            UnsafeAccess.UNSAFE.putInt(intHolder, valueOffset, newValue);
        }

        @Uninterruptible(reason = "Uninterruptible inline expansion")
        public int incrementAndGet() {
            final long valueOffset = Target_java_util_concurrent_atomic_AtomicInteger.valueOffset;
            final int previous = UnsafeAccess.UNSAFE.getAndAddInt(intHolder, valueOffset, 1);
            // This sum may be out of date by the time the caller gets it, but that could have
            // happened with incrementAndGet, too.
            return (previous + 1);
        }

        @Uninterruptible(reason = "Uninterruptible inline expansion")
        public int decrementAndGet() {
            final long valueOffset = Target_java_util_concurrent_atomic_AtomicInteger.valueOffset;
            final int previous = UnsafeAccess.UNSAFE.getAndAddInt(intHolder, valueOffset, -1);
            // This result may be out of date by the time the caller gets it, but that could have
            // happened with decrementAndGet, too.
            return (previous - 1);
        }

        @Uninterruptible(reason = "Uninterruptible inline expansion")
        public boolean compareAndSet(int expected, int update) {
            final long valueOffset = Target_java_util_concurrent_atomic_AtomicInteger.valueOffset;
            return UnsafeAccess.UNSAFE.compareAndSwapInt(intHolder, valueOffset, expected, update);
        }
    }

    public static class AtomicPointer<T extends PointerBase> {

        /**
         * Store the PointerBase field in a mutable long value holder. I am not using anything
         * <em>atomic</em> about the holder, but I can not use a vanilla {@link Long} because that
         * would be immutable.
         */
        private final java.util.concurrent.atomic.AtomicLong longHolder;

        public AtomicPointer() {
            this.longHolder = new AtomicLong();
        }

        @SuppressWarnings("unchecked")
        @Uninterruptible(reason = "Called from uninterruptible code.")
        public T get() {
            final long valueOffset = Target_java_util_concurrent_atomic_AtomicLong.valueOffset;
            final long valueLong = UnsafeAccess.UNSAFE.getLong(longHolder, valueOffset);
            return (T) WordFactory.unsigned(valueLong);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public void set(T newValue) {
            final long valueOffset = Target_java_util_concurrent_atomic_AtomicLong.valueOffset;
            final long valueLong = newValue.rawValue();
            UnsafeAccess.UNSAFE.putLong(longHolder, valueOffset, valueLong);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public boolean compareAndSet(T expected, T update) {
            final long valueOffset = Target_java_util_concurrent_atomic_AtomicLong.valueOffset;
            final long expectedLong = expected.rawValue();
            final long updateLong = update.rawValue();
            return UnsafeAccess.UNSAFE.compareAndSwapLong(longHolder, valueOffset, expectedLong, updateLong);
        }
    }

    public static class AtomicReference<T> {

        /**
         * A mutable AtomicReference value holder. I am not using anything <em>atomic</em> about the
         * holder, I need a holder that I can use with
         * {@link sun.misc.Unsafe#compareAndSwapObject(Object, long, Object, Object)}.
         */
        private final java.util.concurrent.atomic.AtomicReference<T> referenceHolder;

        public AtomicReference(T value) {
            this.referenceHolder = new java.util.concurrent.atomic.AtomicReference<>(value);
        }

        @Uninterruptible(reason = "Uninterruptible inline expansion")
        public T get() {
            final long valueOffset = Target_java_util_concurrent_atomic_AtomicReference.valueOffset;
            @SuppressWarnings("unchecked")
            final T result = (T) UnsafeAccess.UNSAFE.getObject(referenceHolder, valueOffset);
            return result;
        }

        @Uninterruptible(reason = "Uninterruptible inline expansion")
        public void set(T newValue) {
            final long valueOffset = Target_java_util_concurrent_atomic_AtomicReference.valueOffset;
            UnsafeAccess.UNSAFE.putObject(referenceHolder, valueOffset, newValue);
        }

        @Uninterruptible(reason = "Uninterruptible inline expansion")
        public boolean compareAndSet(T expected, T update) {
            final long valueOffset = Target_java_util_concurrent_atomic_AtomicReference.valueOffset;
            return UnsafeAccess.UNSAFE.compareAndSwapObject(referenceHolder, valueOffset, expected, update);
        }
    }

    /** Methods like the ones from {@link java.lang.Math} but annotated as uninterruptible. */
    public static class Math {

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static int min(int a, int b) {
            return (a <= b) ? a : b;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static int max(int a, int b) {
            return (a >= b) ? a : b;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static long max(long a, long b) {
            return (a >= b) ? a : b;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static long abs(long a) {
            return (a < 0) ? -a : a;
        }
    }

    public static class Long {
        /** Uninterruptible version of {@link java.lang.Long#numberOfLeadingZeros(long)}. */
        @Uninterruptible(reason = "Called from uninterruptible code.")
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
        @Uninterruptible(reason = "Called from uninterruptible code.")
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
        @Uninterruptible(reason = "Called from uninterruptible code.")
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
