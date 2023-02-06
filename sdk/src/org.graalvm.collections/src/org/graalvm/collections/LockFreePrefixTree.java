/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.collections;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static java.lang.Integer.numberOfTrailingZeros;

/**
 * Thread-safe and lock-free prefix-tree implementation in which keys are sequences of 64-bit
 * values, and the values are 64-bit values. The LockFreePrefixTree supports the same operations as
 * the PrefixTree as follows:
 * <p>
 * The LockFreePrefixTree supports a single operation {@code root}, which returns the root node. The
 * nodes support the following operations: {@code at} to obtain a child node, {@code value} to
 * obtain the value at the current node, {@code setValue} to atomically set the value, and
 * {@code incValue} to atomically increment the value.
 * <p>
 *
 * The LockFreePrefix tree represents a Tree of nodes of class{@code Node}, with each node having a
 * key and an atomic reference array of children. The underlying {@code children} structure is
 * represented as a LinearArray if the number of children is under a threshold, and represented by a
 * hash table once the threshold is reached.
 *
 * Any additions or accesses to the datastructure are done using the {@code at} function. The
 * {@code at} function takes a key value as a parameter and either returns an already existing node
 * or inserts a new node and returns it. The function may cause the underlying AtomicReferenceArray
 * to grow in size, either with {@code tryResizeLinear} or {@code tryResizeHash}. Insertion of new
 * nodes is always done with the CAS operation, to ensure atomic updates and guarantee the progress
 * of at least a single thread in the execution. Additionally, any growth operations occur
 * atomically, as we perform a CAS with the reference to the Array to a new, freshly allocated array
 * object.
 *
 * @since 22.3
 */
public class LockFreePrefixTree {
    /**
     * @since 22.3
     */
    public static class Node extends AtomicLong {

        private static final long serialVersionUID = -1L;

        private static final class LinearChildren extends AtomicReferenceArray<Node> {

            private static final long serialVersionUID = -1L;

            LinearChildren(int length) {
                super(length);
            }
        }

        private static final class HashChildren extends AtomicReferenceArray<Node> {

            private static final long serialVersionUID = -1L;

            HashChildren(int length) {
                super(length);
            }
        }

        private static final class FrozenNode extends Node {

            private static final long serialVersionUID = -1L;

            FrozenNode() {
                super(-1);
            }
        }

        private static final FrozenNode FROZEN_NODE = new FrozenNode();

        // Requires: INITIAL_HASH_NODE_SIZE >= MAX_LINEAR_NODE_SIZE
        // otherwise we have an endless loop
        private static final int INITIAL_LINEAR_NODE_SIZE = 2;

        private static final int INITIAL_HASH_NODE_SIZE = 16;

        private static final int MAX_LINEAR_NODE_SIZE = 8;
        private static final int MAX_HASH_SKIPS = 10;

        @SuppressWarnings("rawtypes") private static final AtomicReferenceFieldUpdater<Node, AtomicReferenceArray> CHILDREN_UPDATER = AtomicReferenceFieldUpdater.newUpdater(Node.class,
                        AtomicReferenceArray.class, "children");

        /**
         * The 64-bit of the node. This field should not be changed after the node is inserted into
         * the data structure. It is not final to allow pooling nodes, and specifying the key after
         * they are allocated on the heap. It is not volatile, because it must be set before the
         * insertion into the concurrent data structure -- the concurrent data structure thus
         * establishes a happens-before relationship between the write to this field and the other
         * threads that read this field.
         */
        private long key;
        private volatile AtomicReferenceArray<Node> children;

        private Node(long key) {
            this.key = key;
        }

        /**
         * This constructor variant is only meant when nodes are preallocated and pooled.
         */
        private Node() {
            this.key = 0;
        }

        /**
         * @return The value of the {@link Node}
         * @since 22.3
         */
        public long value() {
            return get();
        }

        private long getKey() {
            return this.key;
        }

        /**
         * Set the value for the {@link Node}.
         * 
         * @param value the new value.
         * @since 22.3
         */
        public void setValue(long value) {
            set(value);
        }

        /**
         * Increment value.
         * 
         * @return newly incremented value of the {@link Node}.
         *
         * @since 22.3
         */
        public long incValue() {
            return incrementAndGet();
        }

        /**
         * Atomically does the bitwise-or on the current value.
         *
         * @param pattern a bit pattern to do bitwise-or with
         * @return the value immediately after the bitwise-or operation
         */
        public long bitwiseOrValue(long pattern) {
            while (true) {
                long oldValue = get();
                long newValue = oldValue | pattern;
                if (compareAndSet(oldValue, newValue)) {
                    return newValue;
                }
            }
        }

        /**
         * Get existing (or create if missing) child with the given key.
         *
         * May return {@code null} if the operation cannot complete, for example, due to inability
         * to allocate nodes.
         *
         * @param childKey the key of the child.
         * @return The child with the given childKey, or {@code null} if cannot complete.
         * @since 22.3
         */
        @SuppressWarnings("unchecked")
        public Node at(Allocator allocator, long childKey) {
            try {
                ensureChildren(allocator);
                while (true) {
                    AtomicReferenceArray<Node> children0 = readChildren();
                    if (children0 instanceof LinearChildren) {
                        // Find first empty slot.
                        Node newChild = getOrAddLinear(allocator, childKey, children0);
                        if (newChild != null) {
                            return newChild;
                        } else {
                            // Children array is full, we need to resize.
                            tryResizeLinear(allocator, children0);
                        }
                    } else {
                        // children0 instanceof HashChildren.
                        Node newChild = getOrAddHash(allocator, childKey, children0);
                        if (newChild != null) {
                            return newChild;
                        } else {
                            // Case for growth: the MAX_HASH_SKIPS have been exceeded.
                            tryResizeHash(allocator, children0);
                        }
                    }
                }
            } catch (FailedAllocationException e) {
                return null;
            }
        }

        // Postcondition: if return value is null, then no subsequent mutations will be done on the
        // array object ( the children array is full)
        private Node getOrAddLinear(Allocator allocator, long childKey, AtomicReferenceArray<Node> childrenArray) {
            for (int i = 0; i < childrenArray.length(); i++) {
                Node child = read(childrenArray, i);
                if (child == null) {
                    Node newChild = allocator.newNode(childKey);
                    if (cas(childrenArray, i, null, newChild)) {
                        return newChild;
                    } else {
                        // We need to check if the failed CAS was due to another thread inserting
                        // this childKey.
                        Node child1 = read(childrenArray, i);
                        if (child1.getKey() == childKey) {
                            return child1;
                        } else {
                            continue;
                        }
                    }
                } else if (child.getKey() == childKey) {
                    return child;
                }
            }
            // Array is full, triggers resize.
            return null;
        }

        // Precondition: childrenArray is full.
        private void tryResizeLinear(Allocator allocator, AtomicReferenceArray<Node> childrenArray) {
            AtomicReferenceArray<Node> newChildrenArray;
            if (childrenArray.length() < MAX_LINEAR_NODE_SIZE) {
                newChildrenArray = allocator.newLinearChildren(2 * childrenArray.length());
                for (int i = 0; i < childrenArray.length(); i++) {
                    Node toCopy = read(childrenArray, i);
                    write(newChildrenArray, i, toCopy);
                }
            } else {
                newChildrenArray = allocator.newHashChildren(INITIAL_HASH_NODE_SIZE);
                for (int i = 0; i < childrenArray.length(); i++) {
                    Node toCopy = read(childrenArray, i);
                    addChildToLocalHash(toCopy, newChildrenArray);
                }
            }
            CHILDREN_UPDATER.compareAndSet(this, childrenArray, newChildrenArray);
        }

        private static Node getOrAddHash(Allocator allocator, long childKey, AtomicReferenceArray<Node> hashTable) {
            int index = hash(childKey) % hashTable.length();
            int skips = 0;
            while (true) {
                Node node0 = read(hashTable, index);
                if (node0 == null) {
                    Node newNode = allocator.newNode(childKey);
                    if (cas(hashTable, index, null, newNode)) {
                        return newNode;
                    } else {
                        // Rechecks same index spot if the node has been inserted by other thread.
                        continue;
                    }
                } else if (node0 != FROZEN_NODE && node0.getKey() == childKey) {
                    return node0;
                }
                index = (index + 1) % hashTable.length();
                skips++;
                if (skips > MAX_HASH_SKIPS) {
                    // Returning null triggers hash growth.
                    return null;
                }
            }
        }

        // This method can only get called in the grow hash function, or when converting from linear
        // to hash, meaning it is only exposed to a SINGLE thread
        // Precondition: reachable from exactly one thread
        private static void addChildToLocalHash(Node node, AtomicReferenceArray<Node> hashTable) {
            int index = hash(node.getKey()) % hashTable.length();
            while (read(hashTable, index) != null) {
                index = (index + 1) % hashTable.length();
            }
            write(hashTable, index, node);
        }

        private void tryResizeHash(Allocator allocator, AtomicReferenceArray<Node> children0) {
            freezeHash(children0);
            // All elements of children0 are non-null => ensures no updates are made to old children
            // while we are copying to new children.
            AtomicReferenceArray<Node> newChildrenHash = allocator.newHashChildren(2 * children0.length());
            for (int i = 0; i < children0.length(); i++) {
                Node toCopy = read(children0, i);
                if (toCopy != FROZEN_NODE) {
                    addChildToLocalHash(toCopy, newChildrenHash);
                }
            }
            casChildren(children0, newChildrenHash);
        }

        // Postcondition: Forall element in childrenHash => element != null.
        private static void freezeHash(AtomicReferenceArray<Node> childrenHash) {
            for (int i = 0; i < childrenHash.length(); i++) {
                if (read(childrenHash, i) == null) {
                    cas(childrenHash, i, null, FROZEN_NODE);
                }
            }
        }

        private static boolean cas(AtomicReferenceArray<Node> childrenArray, int i, Node expected, Node updated) {
            return childrenArray.compareAndSet(i, expected, updated);
        }

        private static Node read(AtomicReferenceArray<Node> childrenArray, int i) {
            return childrenArray.get(i);
        }

        private static void write(AtomicReferenceArray<Node> childrenArray, int i, Node newNode) {
            childrenArray.set(i, newNode);
        }

        private void ensureChildren(Allocator allocator) {
            if (readChildren() == null) {
                AtomicReferenceArray<Node> newChildren = allocator.newLinearChildren(INITIAL_LINEAR_NODE_SIZE);
                casChildren(null, newChildren);
            }
        }

        private boolean casChildren(AtomicReferenceArray<Node> expected, AtomicReferenceArray<Node> updated) {
            return CHILDREN_UPDATER.compareAndSet(this, expected, updated);
        }

        private AtomicReferenceArray<Node> readChildren() {
            return children;
        }

        private static int hash(long key) {
            long v = key * 0x9e3775cd9e3775cdL;
            v = Long.reverseBytes(v);
            v = v * 0x9e3775cd9e3775cdL;
            return 0x7fff_ffff & (int) (v ^ (v >> 32));
        }

        private <C> void topDown(C currentContext, BiFunction<C, Long, C> createContext, BiConsumer<C, Long> consumeValue) {
            AtomicReferenceArray<Node> childrenSnapshot = readChildren();
            consumeValue.accept(currentContext, get());
            if (childrenSnapshot == null) {
                return;
            }
            for (int i = 0; i < childrenSnapshot.length(); i++) {
                Node child = read(childrenSnapshot, i);
                if (child != null && child != FROZEN_NODE) {
                    long childKey = child.getKey();
                    C extendedContext = createContext.apply(currentContext, childKey);
                    child.topDown(extendedContext, createContext, consumeValue);
                }
            }
        }

        /**
         * @since 22.3
         */
        @Override
        public String toString() {
            return "Node<" + value() + ">";
        }
    }

    private Allocator allocator;
    private Node root;

    /**
     * Create new {@link LockFreePrefixTree} with root being a Node with key 0.
     * 
     * @since 22.3
     */
    public LockFreePrefixTree(Allocator allocator) {
        this.allocator = allocator;
        this.root = allocator.newNode(0);
    }

    public Allocator allocator() {
        return allocator;
    }

    /**
     * The root node of the tree.
     *
     * @return the root of the tree
     *
     * @since 22.3
     */
    public Node root() {
        return root;
    }

    /**
     * Traverse the tree top-down while maintaining a context.
     * 
     * The context is a generic data structure corresponding to the depth of the traversal, i.e.
     * given the initialContext and a createContext function, a new context is created for each
     * visited child using the createContext function, starting with initialContext.
     * 
     * @param initialContext The context for the root of the tree
     * @param createContext A function defining how the context for children is created
     * @param consumeValue A function that consumes the nodes value
     * @param <C> The type of the context
     *
     * @since 22.3
     */
    public <C> void topDown(C initialContext, BiFunction<C, Long, C> createContext, BiConsumer<C, Long> consumeValue) {
        root.topDown(initialContext, createContext, consumeValue);
    }

    /**
     * Exception that denotes that an allocation failed.
     */
    public static class FailedAllocationException extends RuntimeException {
        private static final long serialVersionUID = -1L;

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    /**
     * Policy for allocating objects of the lock-free prefix tree.
     */
    public static abstract class Allocator {
        private static final FailedAllocationException FAILED_ALLOCATION_EXCEPTION = new FailedAllocationException();

        /**
         * Allocates a new Node object.
         */
        public abstract Node newNode(long key);

        /**
         * Allocates a new reference array of child nodes stored linearly.
         */
        public abstract Node.LinearChildren newLinearChildren(int length);

        /**
         * Allocates a new reference array of child nodes stored as a hash table.
         */
        public abstract Node.HashChildren newHashChildren(int length);

        public abstract void shutdown();
    }

    /**
     * Allocator that allocates objects directly on the managed heap.
     */
    public static class HeapAllocator extends Allocator {
        @Override
        public Node newNode(long key) {
            return new Node(key);
        }

        @Override
        public Node.LinearChildren newLinearChildren(int length) {
            return new Node.LinearChildren(length);
        }

        @Override
        public Node.HashChildren newHashChildren(int length) {
            return new Node.HashChildren(length);
        }

        @Override
        public void shutdown() {
        }
    }

    /**
     * Allocator that internally maintains several pools of preallocated objects, and allocates
     * objects from those pools. This allocator is guaranteed not to allocate any objects inside the
     * invocations to its methods.
     *
     * To ensure that the internal pools have sufficiently many preallocated objects, this allocator
     * has a housekeeping thread that periodically wakes up, allocates objects and inserts them into
     * the pools. This allocator tracks the requests that failed since the last housekeeping
     * session, and the housekeeping thread will strive to accomodate requests that have not been
     * fulfilled since the last housekeeping session (i.e. it will preallocate those types of
     * additional objects whose allocation request previously failed, and it will allocate at least
     * as many objects as there were previous failed allocation requests).
     *
     * This implementation only allows allocating {@link Node.LinearChildren} and
     * {@link Node.HashChildren} arrays whose size is a power of 2 (because
     * {@link LockFreePrefixTree} only ever allocates arrays that are a power of 2).
     */
    public static class ObjectPoolingAllocator extends Allocator {
        private static final int MIN_HOUSEKEEPING_PERIOD_MILLIS = 4;
        private static final int DEFAULT_HOUSEKEEPING_PERIOD_MILLIS = 72;
        private static final int CHILDREN_POOL_COUNT = 32;
        private static final int INITIAL_NODE_PREALLOCATION_COUNT = 2048;
        private static final int INITIAL_LINEAR_CHILDREN_PREALLOCATION_COUNT = 2048;
        private static final int INITIAL_HASH_CHILDREN_PREALLOCATION_COUNT = 128;
        private static final int EXPECTED_MAX_HASH_NODE_SIZE = 512;
        private static final int PREALLOCATION_MULTIPLIER = 2;

        private final LockFreePool<Node> nodePool;
        private final LockFreePool<Node.LinearChildren>[] linearChildrenPool;
        private final LockFreePool<Node.HashChildren>[] hashChildrenPool;
        private final AtomicInteger missedNodePoolRequestCount;
        private final AtomicIntegerArray missedLinearChildrenRequestCounts;
        private final AtomicIntegerArray missedHashChildrenRequestCounts;
        private final HousekeepingThread housekeepingThread;

        public ObjectPoolingAllocator() {
            this(DEFAULT_HOUSEKEEPING_PERIOD_MILLIS);
        }

        public ObjectPoolingAllocator(int housekeepingPeriodMillis) {
            this.nodePool = createNodePool();
            this.linearChildrenPool = createLinearChildrenPool();
            this.hashChildrenPool = createHashChildrenPool();
            this.missedNodePoolRequestCount = new AtomicInteger(0);
            this.missedLinearChildrenRequestCounts = new AtomicIntegerArray(32);
            this.missedHashChildrenRequestCounts = new AtomicIntegerArray(32);
            this.housekeepingThread = new HousekeepingThread(housekeepingPeriodMillis);
            this.housekeepingThread.start();
        }

        private static LockFreePool<Node> createNodePool() {
            LockFreePool<Node> pool = new LockFreePool<>();
            for (int i = 0; i < INITIAL_NODE_PREALLOCATION_COUNT; i++) {
                pool.add(new Node());
            }
            return pool;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static LockFreePool<Node.LinearChildren>[] createLinearChildrenPool() {
            LockFreePool<Node.LinearChildren>[] pools = new LockFreePool[CHILDREN_POOL_COUNT];
            for (int sizeClass = 0; sizeClass < pools.length; sizeClass++) {
                pools[sizeClass] = new LockFreePool<>();
                if (sizeClass >= numberOfTrailingZeros(Node.INITIAL_LINEAR_NODE_SIZE) && sizeClass <= numberOfTrailingZeros(Node.MAX_LINEAR_NODE_SIZE)) {
                    // Preallocate size classes that we know will be needed.
                    for (int i = 0; i < INITIAL_LINEAR_CHILDREN_PREALLOCATION_COUNT; i++) {
                        pools[sizeClass].add(new Node.LinearChildren(1 << sizeClass));
                    }
                }
            }
            return pools;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static LockFreePool<Node.HashChildren>[] createHashChildrenPool() {
            LockFreePool<Node.HashChildren>[] pools = new LockFreePool[CHILDREN_POOL_COUNT];
            for (int sizeClass = 0; sizeClass < pools.length; sizeClass++) {
                pools[sizeClass] = new LockFreePool<>();
                if (sizeClass >= numberOfTrailingZeros(Node.INITIAL_HASH_NODE_SIZE) && sizeClass <= numberOfTrailingZeros(EXPECTED_MAX_HASH_NODE_SIZE)) {
                    // Preallocate size classes that are most likely to be allocated.
                    for (int i = 0; i < INITIAL_HASH_CHILDREN_PREALLOCATION_COUNT; i++) {
                        pools[sizeClass].add(new Node.HashChildren(1 << sizeClass));
                    }
                }
            }
            return pools;
        }

        @Override
        public Node newNode(long key) {
            Node obj = nodePool.get();
            if (obj != null) {
                obj.key = key;
                return obj;
            } else {
                missedNodePoolRequestCount.incrementAndGet();
                throw Allocator.FAILED_ALLOCATION_EXCEPTION;
            }
        }

        @Override
        public Node.LinearChildren newLinearChildren(int length) {
            checkPowerOfTwo(length);
            int sizeClass = Integer.numberOfTrailingZeros(length);
            Node.LinearChildren obj = linearChildrenPool[sizeClass].get();
            if (obj != null) {
                return obj;
            } else {
                missedLinearChildrenRequestCounts.incrementAndGet(sizeClass);
                throw Allocator.FAILED_ALLOCATION_EXCEPTION;
            }
        }

        @Override
        public Node.HashChildren newHashChildren(int length) {
            checkPowerOfTwo(length);
            int sizeClass = Integer.numberOfTrailingZeros(length);
            Node.HashChildren obj = hashChildrenPool[sizeClass].get();
            if (obj != null) {
                return obj;
            } else {
                missedHashChildrenRequestCounts.incrementAndGet(sizeClass);
                throw Allocator.FAILED_ALLOCATION_EXCEPTION;
            }
        }

        @Override
        public void shutdown() {
            housekeepingThread.isEnabled.set(false);
            try {
                housekeepingThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting for housekeeping thread shutdown.", e);
            }
        }

        private static void checkPowerOfTwo(int length) {
            if (Integer.bitCount(length) != 1) {
                throw new UnsupportedOperationException("Cannot allocate length that is not a power of 2: " + length);
            }
        }

        private class HousekeepingThread extends Thread {
            private final int defaultHousekeepingPeriodMillis;
            private int nextHousekeepingPeriodMillis;
            private final AtomicBoolean isEnabled;

            HousekeepingThread(int defaultHousekeepingPeriodMillis) {
                setDaemon(true);
                this.defaultHousekeepingPeriodMillis = defaultHousekeepingPeriodMillis;
                this.nextHousekeepingPeriodMillis = MIN_HOUSEKEEPING_PERIOD_MILLIS;
                this.isEnabled = new AtomicBoolean(true);
            }

            private void housekeep() {
                int count = missedNodePoolRequestCount.get();
                if (count > 0) {
                    int growthEstimate = Math.max(INITIAL_NODE_PREALLOCATION_COUNT, count * PREALLOCATION_MULTIPLIER);
                    for (int i = 0; i < growthEstimate; i++) {
                        nodePool.add(new Node());
                    }
                    nextHousekeepingPeriodMillis = MIN_HOUSEKEEPING_PERIOD_MILLIS;
                    missedNodePoolRequestCount.set(0);
                }
                for (int sizeClass = 0; sizeClass < linearChildrenPool.length; sizeClass++) {
                    count = missedLinearChildrenRequestCounts.get(sizeClass);
                    if (count > 0) {
                        int growthEstimate = Math.max(INITIAL_LINEAR_CHILDREN_PREALLOCATION_COUNT, count * PREALLOCATION_MULTIPLIER);
                        for (int i = 0; i < growthEstimate; i++) {
                            linearChildrenPool[sizeClass].add(new Node.LinearChildren(1 << sizeClass));
                        }
                        nextHousekeepingPeriodMillis = MIN_HOUSEKEEPING_PERIOD_MILLIS;
                        missedLinearChildrenRequestCounts.set(sizeClass, 0);
                    }
                }
                for (int sizeClass = 0; sizeClass < hashChildrenPool.length; sizeClass++) {
                    count = missedHashChildrenRequestCounts.get(sizeClass);
                    if (count > 0) {
                        int growthEstimate;
                        if (sizeClass < Integer.numberOfTrailingZeros(EXPECTED_MAX_HASH_NODE_SIZE)) {
                            // Since these nodes are larger, and requested less frequently, we do
                            // not use the preallocation multiplier.
                            growthEstimate = Math.max(INITIAL_HASH_CHILDREN_PREALLOCATION_COUNT, count);
                        } else {
                            // We expect that the case of allocating very wide nodes is rare.
                            growthEstimate = count;
                        }
                        for (int i = 0; i < growthEstimate; i++) {
                            hashChildrenPool[sizeClass].add(new Node.HashChildren(1 << sizeClass));
                        }
                        nextHousekeepingPeriodMillis = MIN_HOUSEKEEPING_PERIOD_MILLIS;
                        missedHashChildrenRequestCounts.set(sizeClass, 0);
                    }
                }
            }

            @Override
            public void run() {
                while (isEnabled.get()) {
                    try {
                        synchronized (this) {
                            System.out.println("Sleeping... " + nextHousekeepingPeriodMillis + " ms.");
                            this.wait(nextHousekeepingPeriodMillis);
                            // Decrease housekeeping period up to maximum using an exponential backoff.
                            nextHousekeepingPeriodMillis = Math.min(defaultHousekeepingPeriodMillis, nextHousekeepingPeriodMillis * 2);
                        }
                        housekeep();
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Allocator's housekeeping thread was interrupted.", e);
                    }
                }
            }
        }
    }
}
