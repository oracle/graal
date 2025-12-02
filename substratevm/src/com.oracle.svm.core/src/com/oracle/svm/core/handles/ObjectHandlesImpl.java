/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.handles;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.WordBase;
import com.oracle.svm.core.config.ConfigurationValues;
import jdk.graal.compiler.word.Word;
import org.graalvm.word.WordFactory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * This class implements {@link ObjectHandle word}-sized integer handles that refer to Java objects.
 * {@link #create(Object) Creating}, {@link #get(ObjectHandle) dereferencing} and
 * {@link #destroy(ObjectHandle) destroying} handles is thread-safe and the handles themselves are
 * valid across threads.
 * <p>
 * The implementation uses a variable number of object arrays, in which each array element
 * represents a handle. The array element's index determines the handle's integer value, and the
 * element's stored value is the referenced object. Creating a handle entails finding a {@code null}
 * array element and using compare-and-set to write the referenced object into it, avoiding a
 * heavy-weight lock. If there are no {@code null} elements in the existing arrays, an additional
 * array is created. This array has twice the capacity of the previous array, which plays a
 * significant role in how indexing is implemented.
 */
public final class ObjectHandlesImpl implements ObjectHandles {

    private static final int MAX_FIRST_BUCKET_CAPACITY = 1024;

    static { // must be a power of 2 for the arithmetic below to work
        assert Integer.lowestOneBit(MAX_FIRST_BUCKET_CAPACITY) == MAX_FIRST_BUCKET_CAPACITY;
    }

    private final SignedWord rangeMin;
    private final SignedWord rangeMax;
    private final SignedWord nullHandle;

    private final Pointer[] buckets;
    private final int[] capacities;
    private volatile long unusedHandleSearchIndex = 0;

    public ObjectHandlesImpl() {
        this(Word.signed(1), Word.signed(Long.MAX_VALUE), Word.signed(0));
    }

    public ObjectHandlesImpl(SignedWord rangeMin, SignedWord rangeMax, SignedWord nullHandle) {
        assert rangeMin.lessThan(rangeMax) && (rangeMax.rawValue() - rangeMin.rawValue()) >= 0 : "rangeMin < rangeMax and range must fit in positive long range";
        assert nullHandle.lessThan(rangeMin) || nullHandle.greaterThan(rangeMax) : "null handle must not be part of range";
        this.rangeMin = rangeMin;
        this.rangeMax = rangeMax;
        this.nullHandle = nullHandle;

        long maxIndex = toIndex(rangeMax);
        int lastBucketIndex = getBucketIndex(maxIndex);
        int lastBucketCapacity = getIndexInBucket(maxIndex) + 1;
        buckets = new Pointer[lastBucketIndex + 1];
        capacities = new int[lastBucketIndex + 1];
        int firstBucketCapacity = MAX_FIRST_BUCKET_CAPACITY;
        if (lastBucketIndex == 0) { // if our range is small, we may have only a single small bucket
            firstBucketCapacity = lastBucketCapacity;
        }
        capacities[0] = firstBucketCapacity;

        Pointer nullPtr = WordFactory.nullPointer();
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = nullPtr;
        }
    }

    public interface BucketCallback {
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void visitBucket(Pointer bucketAddress, int capacity);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void scanAllBuckets(BucketCallback callback) {
        for (int i = 0; i < buckets.length; i++) {
            Pointer bucket = getBucket(i);
            if (bucket.isNull()) {
                continue;
            }
            callback.visitBucket(bucket, capacities[i]);
        }
    }

    public boolean isInRange(ObjectHandle handle) {
        return handle.rawValue() >= rangeMin.rawValue() && handle.rawValue() <= rangeMax.rawValue();
    }

    private static long toIndex(int bucketIndex, int indexInBucket) {
        int bucketBit = MAX_FIRST_BUCKET_CAPACITY << bucketIndex;
        long displacedIndex = bucketBit + indexInBucket;
        return displacedIndex - MAX_FIRST_BUCKET_CAPACITY;
    }

    private ObjectHandle toHandle(int bucketIndex, int indexInBucket) {
        long index = toIndex(bucketIndex, indexInBucket);
        return (ObjectHandle) rangeMin.add(Word.signed(index));
    }

    private long toIndex(WordBase handle) {
        return handle.rawValue() - rangeMin.rawValue();
    }

    private static int getBucketIndex(long index) {
        long displacedIndex = MAX_FIRST_BUCKET_CAPACITY + index;
        return Long.numberOfLeadingZeros(MAX_FIRST_BUCKET_CAPACITY) - Long.numberOfLeadingZeros(displacedIndex);
    }

    private static int getIndexInBucket(long index) {
        long displacedIndex = MAX_FIRST_BUCKET_CAPACITY + index;
        long bucketBit = Long.highestOneBit(displacedIndex);
        return Math.toIntExact(bucketBit ^ displacedIndex);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private Pointer getBucket(int bucketIndex) {
        // buckets[i] is changed only once from null to its final value: try without volatile first
        Pointer bucket = buckets[bucketIndex];
        return bucket;
    }

    private static int wordSize() {
        return ConfigurationValues.getTarget().wordSize;
    }

    private static Pointer slotPointer(Pointer bucket, int indexInBucket) {
        return bucket.add(indexInBucket * wordSize());
    }

    private static Object readSlot(Pointer bucket, int indexInBucket) {
        return slotPointer(bucket, indexInBucket).readObject(0);
    }

    private static void writeSlot(Pointer bucket, int indexInBucket, Object value) {
        slotPointer(bucket, indexInBucket).writeObject(0, value);
    }

    private static boolean casSlot(Pointer bucket, int indexInBucket, Object expected, Object value) {
        return slotPointer(bucket, indexInBucket).logicCompareAndSwapObject(0, expected, value, LocationIdentity.ANY_LOCATION);
    }

    @Override
    public ObjectHandle create(Object obj) {
        /*
         * Creates a new object handle by starting a search for a null array element at the location
         * where the last handle was created. When a null array element is found, tries to
         * compare-and-swap the object into it. If all buckets have been searched without success
         * (wrapping around the entire bucket space at least once), creates a new bucket unless
         * another thread has done so in the mean time or the space is exhausted.
         */
        if (obj == null) {
            return (ObjectHandle) nullHandle;
        }

        if (buckets[0].isNull()) {
            synchronized (this) {
                if (buckets[0].isNull()) {
                    Pointer p = NullableNativeMemory.calloc(capacities[0] * wordSize(), NmtCategory.JNI);
                    for (int i = 0; i < capacities[0]; i++) {
                        writeSlot(p, i, null);
                    }
                    buckets[0] = p;
                }
            }
        }

        outer: for (;;) {
            long startIndex = unusedHandleSearchIndex;
            int startBucketIndex = getBucketIndex(startIndex);
            int startIndexInBucket = getIndexInBucket(startIndex);

            int bucketIndex = startBucketIndex;
            int indexInBucket = startIndexInBucket;
            int lastExistingBucketIndex = -1;
            Pointer bucket = getBucket(bucketIndex);

            for (;;) {
                while (indexInBucket < capacities[bucketIndex]) {
                    Object current = readSlot(bucket, indexInBucket);
                    if (current == null) {
                        if (casSlot(bucket, indexInBucket, null, obj)) {
                            int newSearchIndexInBucket = (indexInBucket + 1 < capacities[bucketIndex]) ? (indexInBucket + 1) : indexInBucket;
                            unusedHandleSearchIndex = toIndex(bucketIndex, newSearchIndexInBucket);
                            // (if the next index is in another bucket, we let the next create()
                            // figure it out)
                            return toHandle(bucketIndex, indexInBucket);
                        }
                    }

                    indexInBucket++;
                    if (bucketIndex == startBucketIndex && indexInBucket == startIndexInBucket) {
                        // no empty slot found, create another bucket
                        assert lastExistingBucketIndex != -1;
                        long maxIndex = toIndex(rangeMax);
                        if (lastExistingBucketIndex == getBucketIndex(maxIndex)) {
                            throw new IllegalStateException("Handle space exhausted");
                        }

                        int newBucketIndex = lastExistingBucketIndex + 1;
                        if (!getBucket(newBucketIndex).isNull()) {
                            continue outer; // start over: another thread has created a new bucket
                        }

                        int newBucketCapacity = (MAX_FIRST_BUCKET_CAPACITY << newBucketIndex);
                        if (newBucketIndex == getBucketIndex(maxIndex)) {
                            // last bucket may be smaller
                            newBucketCapacity = getIndexInBucket(maxIndex) + 1;
                        }

                        Pointer newBucket = NullableNativeMemory.calloc(newBucketCapacity * wordSize(), NmtCategory.JNI);
                        for (int i = 0; i < capacities[newBucketIndex]; i++) {
                            writeSlot(newBucket, i, null);
                        }
                        writeSlot(newBucket, 0, obj);

                        synchronized (this) {
                            if (buckets[newBucketIndex].isNull()) {
                                buckets[newBucketIndex] = newBucket;
                                capacities[newBucketIndex] = newBucketCapacity;
                                unusedHandleSearchIndex = toIndex(newBucketIndex, 1);
                                return toHandle(newBucketIndex, 0);
                            }
                        }

                        NullableNativeMemory.free(newBucket);
                        continue outer;
                    }
                }

                bucketIndex++;
                bucket = getBucket(bucketIndex);
                if (bucket.isNull()) {
                    lastExistingBucketIndex = bucketIndex - 1;
                    bucketIndex = 0;
                    bucket = getBucket(bucketIndex);
                }
                indexInBucket = 0;
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(ObjectHandle handle) {
        Object obj = doGet(handle);
        return (T) obj;
    }

    private Object doGet(ObjectHandle handle) {
        if (handle.equal(nullHandle)) {
            return null;
        }
        if (!isInRange(handle)) {
            throw new IllegalArgumentException("Invalid handle");
        }

        long index = toIndex(handle);
        int bucketIndex = getBucketIndex(index);
        int indexInBucket = getIndexInBucket(index);

        Pointer bucket = getBucket(bucketIndex);

        if (bucket.isNull()) {
            throw new IllegalStateException("Bucket not allocated");
        }

        return readSlot(bucket, indexInBucket);
    }

    @Override
    public void destroy(ObjectHandle handle) {
        if (handle.equal(nullHandle)) {
            return;
        }
        if (!isInRange(handle)) {
            throw new IllegalArgumentException("Invalid handle");
        }

        long index = toIndex(handle);
        int bucketIndex = getBucketIndex(index);
        int indexInBucket = getIndexInBucket(index);

        Pointer bucket = getBucket(bucketIndex);
        if (bucket.isNull()) {
            throw new IllegalStateException("Bucket not allocated");
        }

        writeSlot(bucket, indexInBucket, null);
    }


    public long computeCurrentCount() {
        long count = 0;
        int bucketIndex = 0;
        Pointer bucket = getBucket(bucketIndex);
        while (!bucket.isNull()) {
            for (int i = 0; i < capacities[bucketIndex]; i++) {
                if (readSlot(bucket, i) != null) {
                    count++;
                }
            }
            bucketIndex++;
            bucket = getBucket(bucketIndex);
        }
        return count;
    }

    public long computeCurrentCapacity() {
        long capacity = 0;
        int bucketIndex = 0;
        Pointer bucket = getBucket(bucketIndex);
        while (!bucket.isNull()) {
            capacity += capacities[bucketIndex];
            bucketIndex++;
            bucket = getBucket(bucketIndex);
        }
        return capacity;
    }
}
