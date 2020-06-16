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

import java.lang.ref.WeakReference;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.word.SignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

//Checkstyle: stop
import sun.misc.Unsafe;
// Checkstyle: resume

/**
 * This class implements {@link ObjectHandle word}-sized integer handles that refer to Java objects.
 * {@link #create(Object) Creating}, {@link #get(ObjectHandle) dereferencing} and
 * {@link #destroy(ObjectHandle) destroying} handles is thread-safe and the handles themselves are
 * valid across threads. This class also supports weak handles, with which the referenced object may
 * be garbage-collected, after which {@link #get(ObjectHandle)} returns {@code null}. Still, weak
 * handles must also be {@link #destroyWeak(ObjectHandle) explicitly destroyed} to reclaim their
 * handle value.
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
    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    /** Private subclass to distinguish from regular handles to {@link WeakReference} objects. */
    private static final class HandleWeakReference<T> extends WeakReference<T> {
        HandleWeakReference(T referent) {
            super(referent);
        }
    }

    private static final int MAX_FIRST_BUCKET_CAPACITY = 1024;
    static { // must be a power of 2 for the arithmetic below to work
        assert Integer.lowestOneBit(MAX_FIRST_BUCKET_CAPACITY) == MAX_FIRST_BUCKET_CAPACITY;
    }

    private final SignedWord rangeMin;
    private final SignedWord rangeMax;
    private final SignedWord nullHandle;

    private final Object[][] buckets;
    private volatile long unusedHandleSearchIndex = 0;

    public ObjectHandlesImpl() {
        this(WordFactory.signed(1), WordFactory.signed(Long.MAX_VALUE), WordFactory.signed(0));
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
        buckets = new Object[lastBucketIndex + 1][];
        int firstBucketCapacity = MAX_FIRST_BUCKET_CAPACITY;
        if (lastBucketIndex == 0) { // if our range is small, we may have only a single small bucket
            firstBucketCapacity = lastBucketCapacity;
        }
        buckets[0] = new Object[firstBucketCapacity];
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
        return (ObjectHandle) rangeMin.add(WordFactory.signed(index));
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
        return UNSAFE.arrayBaseOffset(Object[].class) + index * UNSAFE.arrayIndexScale(Object[].class);
    }

    private Object[] getBucket(int bucketIndex) {
        // buckets[i] is changed only once from null to its final value: try without volatile first
        Object[] bucket = buckets[bucketIndex];
        if (bucket == null) {
            bucket = (Object[]) UNSAFE.getObjectVolatile(buckets, getObjectArrayByteOffset(bucketIndex));
        }
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
            Object[] bucket = getBucket(bucketIndex);
            for (;;) {
                while (indexInBucket < bucket.length) {
                    if (bucket[indexInBucket] == null) {
                        if (UNSAFE.compareAndSwapObject(bucket, getObjectArrayByteOffset(indexInBucket), null, obj)) {
                            int newSearchIndexInBucket = (indexInBucket + 1 < bucket.length) ? (indexInBucket + 1) : indexInBucket;
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
                        Object[] newBucket = new Object[newBucketCapacity];
                        UNSAFE.putObjectVolatile(newBucket, getObjectArrayByteOffset(0), obj);
                        if (UNSAFE.compareAndSwapObject(buckets, getObjectArrayByteOffset(newBucketIndex), null, newBucket)) {
                            unusedHandleSearchIndex = toIndex(newBucketIndex, 1);
                            return toHandle(newBucketIndex, 0);
                        }
                        // start over: another thread has raced us to create another bucket and won
                        continue outer;
                    }
                }

                bucketIndex++;
                bucket = getBucket(bucketIndex);
                if (bucket == null) {
                    lastExistingBucketIndex = bucketIndex - 1;
                    bucketIndex = 0;
                    bucket = getBucket(bucketIndex);
                }
                indexInBucket = 0;
            }
        }
    }

    public ObjectHandle createWeak(Object obj) {
        return create(new HandleWeakReference<>(obj));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(ObjectHandle handle) {
        Object obj = doGet(handle);
        if (obj instanceof HandleWeakReference) {
            obj = ((HandleWeakReference<T>) obj).get();
        }
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
        Object[] bucket = getBucket(getBucketIndex(index));
        if (bucket == null) {
            throw new IllegalArgumentException("Invalid handle");
        }
        int indexInBucket = getIndexInBucket(index);
        return UNSAFE.getObjectVolatile(bucket, getObjectArrayByteOffset(indexInBucket));
    }

    public boolean isWeak(ObjectHandle handle) {
        return (doGet(handle) instanceof HandleWeakReference);
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
        Object[] bucket = getBucket(getBucketIndex(index));
        if (bucket == null) {
            throw new IllegalArgumentException("Invalid handle");
        }
        int indexInBucket = getIndexInBucket(index);
        UNSAFE.putOrderedObject(bucket, getObjectArrayByteOffset(indexInBucket), null);
    }

    public void destroyWeak(ObjectHandle handle) {
        destroy(handle);
    }

    public long computeCurrentCount() {
        long count = 0;
        int bucketIndex = 0;
        Object[] bucket = getBucket(bucketIndex);
        while (bucket != null) {
            for (int i = 0; i < bucket.length; i++) {
                if (bucket[i] != null) {
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
        Object[] bucket = getBucket(bucketIndex);
        while (bucket != null) {
            capacity += bucket.length;
            bucketIndex++;
            bucket = getBucket(bucketIndex);
        }
        return capacity;
    }
}
