/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.collection;

public class BTree<E extends Comparable<E>> implements Pool<E> {
    private static final int BRANCHING_FACTOR = 16;

    private static class Node {
        int maxElement;
        int count;
        final boolean leaf;
        final Object[] children;

        public Node(int maxElement, boolean leaf) {
            this.maxElement = maxElement;
            this.count = 0;
            this.leaf = leaf;
            this.children = new Object[BRANCHING_FACTOR];
        }
    }

    private Node root;

    public BTree() {
        this.root = new Node(Integer.MIN_VALUE, true);
    }

    @SuppressWarnings("unchecked")
    private Node add(Node node, E x) {
        if (node.leaf) {
            if (node.count < BRANCHING_FACTOR) {
                // We are inserting into the leaf.
                //
                //   [...]
                //    | \
                // [yz.] [...]
                //  ^
                //  x
                int pos = 0;
                E cur = null;
                while ((cur = (E) node.children[pos]) != null) {
                    if (cur.compareTo(x) >= 0) {
                        break;
                    }
                }
                E last = x;
                while (last != null) {
                    node.children[pos] = last;
                    last = cur;
                    cur = pos + 1 < node.children.length ? (E) node.children[pos + 1] : null;
                    pos++;
                }
                // TODO: Update count.
                // TODO: Update maxElement.
                return null;
            } else {
                // We need to split.
                //
                //   [...]
                //    | \
                // [yzw] [...]
                //  ^
                //  x
                //
                // We create two nodes:
                //
                //       [...]
                //      /     \
                // [xy.][zw.]  [...]
                // TODO: Find mid element.
                Node sibling = new Node(node.maxElement, true);
                return sibling;
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void add(E x) {
        add(root, x);
    }

    @Override
    public E poll() {
        return null;
    }

    @Override
    public E peek() {
        return null;
    }

    @Override
    public void clear() {

    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return null;
    }

    @Override
    public int internalCapacity() {
        return 0;
    }
}
