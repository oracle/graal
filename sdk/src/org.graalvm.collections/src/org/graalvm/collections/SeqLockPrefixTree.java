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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Thread-safe prefix-tree implementation in which keys are sequences of 64-bit values, and the
 * values are 64-bit values.
 * <p>
 * The prefix tree supports a single operation {@code root}, which returns the root node. The nodes
 * support the following operations: {@code at} to obtain a child node, {@code value} to obtain the
 * value at the current node, {@code setValue} to atomically set the value and {@code incValue} to
 * atomically increment the value.
 * <p>
 * The prefix tree is implemented as follows. The tree points to the root node. Each node points to
 * a set of child nodes, where each child is associated with a key. Each node additionally holds an
 * arity, which is the number of children, and the 64-bit value of that node.
 * <p>
 * The set of child nodes can be represented as {@code null} if the set is empty, an array-list if
 * the the set is small, or a hash table if the set is large. In all cases, the keys and the child
 * nodes are kept in separate arrays.
 * <p>
 * The {@code at} operation, which takes a node and a key, and returns the corresponding child node,
 * deserves an additional explanation. This operation creates an existing child associated with the
 * key, or atomically creates a new child, if there was no child for that key.
 * <p>
 * The {@code at} operation is implemented as follows. There is a fast-path, which executes when the
 * child node already exists, and there are no concurrent modifications at that node, and the
 * slow-path, which executes when the child does not exist or when there is a concurrent
 * modification. To ensure that the slow path does not obtain a monitor, the fast-path relies on a
 * <i>seqlock</i>. This is a lightweight read-write lock that consists of a single 64-bit counter,
 * whose value is even when the lock is in the read mode, and odd when the lock is in the write
 * mode. The lock can be held by any number of readers, but at most 1 writer at any time.
 * <p>
 * In the read-mode, the reader must verify that the value of the lock is even and that it did not
 * change from the point when the read started until the point when the read ended, and additionally
 * takes care that reading an invalid state does not crash the execution. If the read fails for
 * either of these reasons, the reader proceeds to the write-mode. In the write-mode, the reader or
 * the writer enters a heavy lock (i.e. monitor) and then increments the seqlock's value by one,
 * does the modification, and then increments the seqlock's value by one to make it even again. The
 * volatile access semantics of the seqlock's value, along with the fact that the node's data only
 * "grows" over time, are the properties that ensure the correctness of this implementation.
 *
 * @since 22.3
 */
public class SeqLockPrefixTree {
    private static final int INITIAL_LINEAR_NODE_SIZE = 3;
    private static final int INITIAL_HASH_NODE_SIZE = 16;
    private static final int MAX_LINEAR_NODE_SIZE = 6;
    private static final long EMPTY_KEY = 0L;
    private static final double HASH_NODE_LOAD_FACTOR = 0.5;

    interface Visitor<R> {
        R visit(Node n, List<R> childResults);
    }

    /**
     * @since 22.3
     */
    public static final class Node extends AtomicLong {

        private static final long serialVersionUID = -1L;

        private volatile long seqlock;
        private volatile long[] keys;
        private volatile Node[] children;
        private volatile int arity;

        private Node() {
            this.seqlock = 0L;
            this.keys = null;
            this.children = null;
            this.arity = 0;
        }

        /**
         * @return The value of the {@link LockFreePrefixTree.Node}
         * @since 22.3
         */
        public long value() {
            return get();
        }

        /**
         * Increment value.
         *
         * @return newly incremented value of the {@link LockFreePrefixTree.Node}.
         *
         * @since 22.3
         */
        public long incValue() {
            return incrementAndGet();
        }

        /**
         * Set the value for the {@link LockFreePrefixTree.Node}.
         *
         * @param value the new value.
         * @since 22.3
         */
        public void setValue(long value) {
            set(value);
        }

        /**
         * Get existing (or create if missing) child with the given key.
         *
         * @param key the key of the child.
         * @return The child with the given childKey.
         * @since 22.3
         */
        public Node at(long key) {
            if (key == EMPTY_KEY) {
                throw new IllegalArgumentException("Key in the prefix tree cannot be 0.");
            }
            Node child = findChildLockFree(key);
            return child != null ? child : tryAddChild(key);
        }

        /**
         * @return the value of the seqlock.
         *
         * @since 22.3
         */
        public long seqlockValue() {
            return seqlock;
        }

        private Node findChildLockFree(long key) {
            final long seqlockStart = seqlock;
            if ((seqlockStart & 1) == 1) {
                // A modification is in progress.
                return null;
            }
            final long[] keysSnapshot = keys;
            final Node[] childrenSnapshot = children;
            Node child = findChild(keysSnapshot, childrenSnapshot, key);
            final long seqlockEnd = seqlock;
            if (seqlockStart != seqlockEnd) {
                // The search was interleaved with a modification.
                return null;
            }
            return child;
        }

        private static Node findChild(long[] keysSnapshot, Node[] childrenSnapshot, long key) {
            if (keysSnapshot == null || childrenSnapshot == null) {
                // No children were fully added yet.
                return null;
            }
            if (keysSnapshot.length != childrenSnapshot.length) {
                // Snapshot is invalid. There must be a modification in progress.
                return null;
            }
            if (keysSnapshot.length <= MAX_LINEAR_NODE_SIZE) {
                // Do a linear search to find a matching child.
                for (int i = 0; i < keysSnapshot.length; i++) {
                    final long curkey = keysSnapshot[i];
                    if (curkey == key) {
                        return childrenSnapshot[i];
                    } else if (curkey == EMPTY_KEY) {
                        break;
                    }
                }
            } else {
                int index = hash(key) % keysSnapshot.length;
                while (true) {
                    long curkey = keysSnapshot[index];
                    if (curkey == key) {
                        return childrenSnapshot[index];
                    } else if (curkey == EMPTY_KEY) {
                        break;
                    }
                    index = (index + 1) % keysSnapshot.length;
                }
            }
            return null;
        }

        @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "in synchronized method")
        private synchronized Node tryAddChild(long key) {
            // Child addition must start by re-checking if the key is present,
            // to avoid a race condition.
            Node child;
            if (keys != null) {
                child = findChild(keys, children, key);
                if (child != null) {
                    return child;
                }
            }

            // Grab seqlock.
            // Note: we do not need to grab the seqlock earlier,
            // because modifications can happen only after this point.
            seqlock = seqlock + 1;
            try {
                if (keys == null) {
                    keys = new long[INITIAL_LINEAR_NODE_SIZE];
                    children = new Node[INITIAL_LINEAR_NODE_SIZE];
                }

                // If the child still does not exist, enter a new one.
                child = new Node();
                if (keys.length <= MAX_LINEAR_NODE_SIZE) {
                    addChildToLinearNode(key, child);
                } else {
                    addChildToHashNode(key, child);
                }

                return child;
            } finally {
                // Release seqlock.
                seqlock = seqlock + 1;
            }
        }

        @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "called from synchronized tryAddChild")
        private void addChildToLinearNode(long key, Node child) {
            if (arity == keys.length) {
                if (arity == MAX_LINEAR_NODE_SIZE) {
                    convertToHashNode();
                    addChildToHashNode(key, child);
                    return;
                }
                // Otherwise, double the array size.
                long[] nkeys = new long[2 * keys.length];
                Node[] nchildren = new Node[2 * children.length];
                for (int i = 0; i < keys.length; i++) {
                    nkeys[i] = keys[i];
                    nchildren[i] = children[i];
                }
                keys = nkeys;
                children = nchildren;
            }
            keys[arity] = key;
            children[arity] = child;
            arity++;
        }

        private void convertToHashNode() {
            long[] oldKeys = keys;
            Node[] oldChildren = children;
            int oldArity = arity;
            keys = new long[INITIAL_HASH_NODE_SIZE];
            children = new Node[INITIAL_HASH_NODE_SIZE];
            arity = 0;
            for (int i = 0; i < oldArity; i++) {
                addChildToHashNode(oldKeys[i], oldChildren[i]);
            }
        }

        private void addChildToHashNode(long key, Node child) {
            if (mustGrowHash()) {
                growHash();
            }
            addChildToNonFullHashNode(key, child);
        }

        @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "called indirectly from synchronized tryAddChild")
        private void addChildToNonFullHashNode(long key, Node child) {
            int index = hash(key) % keys.length;
            while (keys[index] != EMPTY_KEY) {
                index = (index + 1) % keys.length;
            }
            keys[index] = key;
            children[index] = child;
            arity++;
        }

        private boolean mustGrowHash() {
            return ((double) arity) / keys.length > HASH_NODE_LOAD_FACTOR;
        }

        private void growHash() {
            long[] oldKeys = keys;
            Node[] oldChildren = children;
            keys = new long[2 * oldKeys.length];
            children = new Node[2 * oldChildren.length];
            arity = 0;
            for (int i = 0; i < oldKeys.length; i++) {
                long key = oldKeys[i];
                if (key != EMPTY_KEY) {
                    Node child = oldChildren[i];
                    addChildToNonFullHashNode(key, child);
                }
            }
        }

        private static int hash(long key) {
            long v = key * 0x9e3775cd9e3775cdL;
            v = Long.reverseBytes(v);
            v = v * 0x9e3775cd9e3775cdL;
            return 0x7fff_ffff & (int) (v ^ (v >> 32));
        }

        @SuppressWarnings("unused")
        private synchronized <R> R bottomUp(Visitor<R> visitor) {
            List<R> results = new ArrayList<>();
            Node[] childrenSnapshot = children;
            for (int i = 0; i < childrenSnapshot.length; i++) {
                if (childrenSnapshot[i] != null) {
                    results.add(childrenSnapshot[i].bottomUp(visitor));
                }
            }
            return visitor.visit(this, results);
        }

        /**
         * Traverse the tree top-down while maintaining a context.
         *
         * The context is a generic data structure corresponding to the depth of the traversal, i.e.
         * given the currentContext and a createContext function, a new context is created for each
         * visited child using the createContext function, starting with initialContext.
         *
         * @param currentContext The context for the root of the tree
         * @param createContext A function defining how the context for children is created
         * @param consumeValue A function that consumes the nodes value
         * @param <C> The type of the context
         *
         * @since 22.3
         */
        @SuppressWarnings("unused")
        public synchronized <C> void topDown(C currentContext, BiFunction<C, Long, C> createContext, BiConsumer<C, Long> consumeValue) {
            Node[] childrenSnapshot = children;
            long[] keysSnapshot = keys;
            consumeValue.accept(currentContext, get());

            if (childrenSnapshot == null) {
                return;
            }

            for (int i = 0; i < childrenSnapshot.length; i++) {
                Node child = childrenSnapshot[i];
                if (child != null) {
                    long key = keysSnapshot[i];
                    C extendedContext = createContext.apply(currentContext, key);
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

    private final Node root;

    /**
     * Create new {@link SeqLockPrefixTree} with root being a Node with key 0.
     *
     * @since 22.3
     */
    public SeqLockPrefixTree() {
        this.root = new Node();
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
}
