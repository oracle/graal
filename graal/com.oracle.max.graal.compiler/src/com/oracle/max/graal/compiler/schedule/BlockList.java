/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.schedule;

import java.util.*;

import com.oracle.max.graal.nodes.*;

/**
 * The {@code BlockList} class implements a specialized list data structure for representing
 * the predecessor and successor lists of basic blocks.
 */
public class BlockList implements Iterable<MergeNode> {

    private MergeNode[] array;
    private int cursor;

    BlockList(int sizeHint) {
        if (sizeHint > 0) {
            array = new MergeNode[sizeHint];
        } else {
            array = new MergeNode[2];
        }
    }

    public void remove(int index) {
        if (index < 0 || index >= cursor) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = index; i < cursor; i++) {
            array[i] = array[i + 1];
        }
        cursor--;
    }

    public void remove(MergeNode block) {
        int j = 0;
        for (int i = 0; i < cursor; i++) {
            if (i != j) {
                array[j] = array[i];
            }
            if (array[i] != block) {
                j++;
            }
        }
        cursor = j;
    }

    public void exchange(int index1, int index2) {
        if (index1 < 0 || index1 >= cursor) {
            throw new IndexOutOfBoundsException();
        }
        if (index2 < 0 || index2 >= cursor) {
            throw new IndexOutOfBoundsException();
        }
        MergeNode t = array[index2];
        array[index2] = array[index1];
        array[index1] = t;
    }

    public void insert(int index, MergeNode block) {
        if (index < 0 || index >= cursor) {
            throw new IndexOutOfBoundsException();
        }
        growOne();
        for (int i = cursor; i > index; i--) {
            array[i] = array[i - 1];
        }
        array[cursor++] = block;
    }

    public void append(MergeNode block) {
        growOne();
        array[cursor++] = block;
    }

    public MergeNode get(int index) {
        if (index < 0 || index >= cursor) {
            throw new IndexOutOfBoundsException();
        }
        return array[index];
    }

    public void replace(MergeNode oldBlock, MergeNode newBlock) {
        for (int i = 0; i < cursor; i++) {
            if (array[i] == oldBlock) {
                array[i] = newBlock;
            }
        }
    }

    public boolean checkForSameBlock() {
        if (cursor == 0) {
            return true;
        }
        MergeNode b = array[0];
        for (int i = 1; i < cursor; i++) {
            if (array[i] != b) {
                return false;
            }
        }
        return true;
    }

    public Iterator<MergeNode> iterator() {
        return new Iter();
    }

    private void growOne() {
        if (cursor == array.length) {
            array = Arrays.copyOf(array, array.length * 3);
        }
    }

    private class Iter implements Iterator<MergeNode> {
        private int pos;

        public boolean hasNext() {
            return pos < cursor;
        }

        public MergeNode next() {
            return array[pos++];
        }

        public void remove() {
            BlockList.this.remove(pos);
        }
    }
}
