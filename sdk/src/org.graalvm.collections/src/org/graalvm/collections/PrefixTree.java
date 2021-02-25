/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Prefix tree implementation in which keys are sequences of 64-bit values,
 * and the values are 64-bit values.
 */
public class PrefixTree {
    private static final int INITIAL_LINEAR_NODE_SIZE = 3;
    private static final int INITIAL_HASH_NODE_SIZE = 16;
    private static final int MAX_LINEAR_NODE_SIZE = 6;
    private static final long EMPTY_KEY = 0L;
    private static final double HASH_NODE_LOAD_FACTOR = 0.5;

    public static class Node {
        private long[] keys;
        private Node[] children;
        private int arity;
        private long value;

        private Node() {
            this.keys = new long[INITIAL_LINEAR_NODE_SIZE];
            this.children = new Node[INITIAL_LINEAR_NODE_SIZE];
            this.arity = 0;
            this.value = 0L;
        }

        public long value() {
            return value;
        }

        public void incValue() {
            this.value++;
        }

        public void setValue(long value) {
            this.value = value;
        }

        public Node at(long key) {
            if (key == EMPTY_KEY) {
                throw new IllegalArgumentException("Key in the prefix tree cannot be 0.");
            }
            if (keys.length <= MAX_LINEAR_NODE_SIZE) {
                // Do a linear search to find a matching child.
                for (int i = 0; i < arity; i++) {
                    final long curkey = keys[i];
                    if (curkey == key) {
                        return children[i];
                    }
                }

                // No child was found, so insert a new child.
                return addChild(key);
            } else {
                int index = hash(key) % keys.length;
                Node child = null;
                while (true) {
                    long curkey = keys[index];
                    if (curkey == EMPTY_KEY) {
                        break;
                    } else if (curkey == key) {
                        child = children[index];
                        break;
                    }
                    index = (index + 1) % keys.length;
                }
                if (child == null) {
                    child = addChild(key);
                }
                return child;
            }
        }

        private Node addChild(long key) {
            Node child = new Node();
            if (keys.length <= MAX_LINEAR_NODE_SIZE) {
                addChildToLinearNode(key, child);
            } else {
                addChildToHashNode(key, child);
            }
            return child;
        }

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
            keys = new long[2 * keys.length];
            children = new Node[2 * children.length];
            arity = 0;
            for (int i = 0; i < oldKeys.length; i++) {
                long key = oldKeys[i];
                if (key != EMPTY_KEY) {
                    Node child = oldChildren[i];
                    addChildToNonFullHashNode(key, child);
                }
            }
        }

        private int hash(long key) {
            long v = key * 0x9e3775cd9e3775cdL;
            v = Long.reverseBytes(v);
            v = v * 0x9e3775cd9e3775cdL;
            return 0x7fff_ffff & (int) (v ^ (v >> 32));
        }
    }

    private Node root;

    public PrefixTree() {
        this.root = new Node();
    }

    public Node root() {
        return root;
    }
}
