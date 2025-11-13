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
    private static final int wordSize = ConfigurationValues.getTarget().wordSize;
    static { // must be a power of 2 for the arithmetic below to work
        assert Integer.lowestOneBit(MAX_FIRST_BUCKET_CAPACITY) == MAX_FIRST_BUCKET_CAPACITY;
    }

    private final SignedWord rangeMin;
    private final SignedWord rangeMax;
    private final SignedWord nullHandle;

    private final WordPointer[] buckets;
    private final int[] bucketCapacities;
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
        buckets = new WordPointer[lastBucketIndex + 1];
        bucketCapacities = new int[lastBucketIndex + 1];
        int firstBucketCapacity = MAX_FIRST_BUCKET_CAPACITY;

        if (lastBucketIndex == 0) { // if our range is small, we may have only a single small bucket
            firstBucketCapacity = lastBucketCapacity;
        }

        Pointer bucketBasePtr = NullableNativeMemory.malloc(Word.unsigned(firstBucketCapacity * wordSize), NmtCategory.JNI);
        buckets[0] = (WordPointer) bucketBasePtr;
        bucketCapacities[0] = firstBucketCapacity;
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

    private static long getObjectArrayByteOffset(int index) {
        return (long) index * wordSize;
    }

    private WordPointer getBucket(int bucketIndex) {
        // buckets[i] is changed only once from null to its final value: try without volatile first
        WordPointer bucket = buckets[bucketIndex];

        return bucket;
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

        outer: for (;;) {
            long startIndex = unusedHandleSearchIndex;
            int startBucketIndex = getBucketIndex(startIndex);
            int startIndexInBucket = getIndexInBucket(startIndex);

            int bucketIndex = startBucketIndex;
            int indexInBucket = startIndexInBucket;
            int lastExistingBucketIndex = -1;
            Pointer bucket = (Pointer) getBucket(bucketIndex);
            int bucketCapacity = bucketCapacities[bucketIndex];

            for (;;) {
                while (indexInBucket < bucketCapacity) {
                    long offset = getObjectArrayByteOffset(bucketIndex);
                    Object currentObj = bucket.readObject(Word.unsigned(offset));
                    if (currentObj.equals(nullHandle)) {
                        if (bucket.logicCompareAndSwapObject(Word.unsigned(offset), null, obj, LocationIdentity.ANY_LOCATION)) {
                            int newSearchIndexInBucket = (indexInBucket + 1 < bucketCapacity) ? (indexInBucket + 1) : indexInBucket;
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
                        if (getBucket(newBucketIndex) != null) {
                            continue outer; // start over: another thread has created a new bucket
                        }
                        int newBucketCapacity = (MAX_FIRST_BUCKET_CAPACITY << newBucketIndex);
                        if (newBucketIndex == getBucketIndex(maxIndex)) {
                            // last bucket may be smaller
                            newBucketCapacity = getIndexInBucket(maxIndex) + 1;
                        }
                        Pointer newBucketBasePtr = NullableNativeMemory.malloc(Word.unsigned(newBucketCapacity * wordSize), NmtCategory.JNI);
                        newBucketBasePtr.writeObject(Word.unsigned(0), obj);

                        buckets[indexInBucket] = (WordPointer) newBucketBasePtr;
                        bucketCapacities[indexInBucket] = newBucketCapacity;

//                        long newBucketOffset = getObjectArrayByteOffset(newBucketIndex);
//                        if (bucketsBasePtr.logicCompareAndSwapObject(Word.unsigned(newBucketOffset), null, newBucket, LocationIdentity.ANY_LOCATION)) {
                            unusedHandleSearchIndex = toIndex(newBucketIndex, 1);
                            return toHandle(newBucketIndex, 0);
//                        }
                        // start over: another thread has raced us to create another bucket and won
//                        continue outer;
                    }
                }

                bucketIndex++;
                bucket = (Pointer)getBucket(bucketIndex);
                if (bucket == null) {
                    lastExistingBucketIndex = bucketIndex - 1;
                    bucketIndex = 0;
                    bucket = (Pointer)getBucket(bucketIndex);
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
        WordPointer bucket = getBucket(getBucketIndex(index));
        if (bucket.isNull()) {
            throw new IllegalArgumentException("Invalid handle");
        }
        int indexInBucket = getIndexInBucket(index);
        return ((Pointer)bucket).readObject(Word.unsigned(getObjectArrayByteOffset(indexInBucket)));
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
        WordPointer bucket = getBucket(getBucketIndex(index));
        if (bucket.isNull()) {
            throw new IllegalArgumentException("Invalid handle");
        }
        int indexInBucket = getIndexInBucket(index);
        ((Pointer)bucket).writeObject(Word.unsigned(getObjectArrayByteOffset(indexInBucket)), null);
    }


    public long computeCurrentCount() {
        long count = 0;
        int bucketIndex = 0;
        long offset = 0;
        Object currentObj;
        WordPointer bucket = getBucket(bucketIndex);
        while (!bucket.isNull()) {
            for (int i = 0; i < bucketCapacities[bucketIndex]; i++) {
                offset = getObjectArrayByteOffset(i);
                currentObj = ((Pointer)bucket).readObject(Word.unsigned(offset));
                if (!currentObj.equals(nullHandle)) {
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
        WordPointer bucket = getBucket(bucketIndex);
        while (!bucket.isNull()) {
            capacity += bucketCapacities[bucketIndex];
            bucketIndex++;
            bucket = getBucket(bucketIndex);
        }
        return capacity;
    }
}