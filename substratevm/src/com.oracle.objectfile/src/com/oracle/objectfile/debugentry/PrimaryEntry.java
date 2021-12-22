/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debugentry;

import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;

/**
 * Tracks debug info associated with a primary method. i.e. a top level compiled method
 */
public class PrimaryEntry {
    /**
     * The primary range detailed by this object.
     */
    private Range primary;
    /**
     * Details of the class owning this range.
     */
    private ClassEntry classEntry;
    /**
     * Details of of compiled method frame size changes.
     */
    private List<DebugFrameSizeChange> frameSizeInfos;
    /**
     * Size of compiled method frame.
     */
    private int frameSize;

    public PrimaryEntry(Range primary, List<DebugFrameSizeChange> frameSizeInfos, int frameSize, ClassEntry classEntry) {
        this.primary = primary;
        this.classEntry = classEntry;
        this.frameSizeInfos = frameSizeInfos;
        this.frameSize = frameSize;
    }

    public Range getPrimary() {
        return primary;
    }

    public ClassEntry getClassEntry() {
        return classEntry;
    }

    /**
     * Returns an iterator that traverses all the callees of the primary range associated with this
     * entry. The iterator performs a depth-first pre-order traversal of the call tree.
     *
     * @return the iterator
     */
    public Iterator<Range> topDownRangeIterator() {
        return new Iterator<>() {
            final ArrayDeque<Range> workStack = new ArrayDeque<>();
            Range current = primary.getFirstCallee();

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public Range next() {
                assert hasNext();
                Range result = current;
                forward();
                return result;
            }

            private void forward() {
                Range sibling = current.getSiblingCallee();
                assert sibling == null || (current.getHi() <= sibling.getLo()) : current.getHi() + " > " + sibling.getLo();
                if (!current.isLeaf()) {
                    /* save next sibling while we process the children */
                    if (sibling != null) {
                        workStack.push(sibling);
                    }
                    current = current.getFirstCallee();
                } else if (sibling != null) {
                    current = sibling;
                } else {
                    /*
                     * Return back up to parents' siblings, use pollFirst instead of pop to return
                     * null in case the work stack is empty
                     */
                    current = workStack.pollFirst();
                }
            }
        };
    }

    /**
     * Returns an iterator that traverses the callees of the primary range associated with this
     * entry and returns only the leafs. The iterator performs a depth-first pre-order traversal of
     * the call tree returning only ranges with no callees.
     *
     * @return the iterator
     */
    public Iterator<Range> leafRangeIterator() {
        final Iterator<Range> iter = topDownRangeIterator();
        return new Iterator<>() {
            Range current = forwardLeaf(iter);

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public Range next() {
                assert hasNext();
                Range result = current;
                current = forwardLeaf(iter);
                return result;
            }

            private Range forwardLeaf(Iterator<Range> t) {
                if (t.hasNext()) {
                    Range next = t.next();
                    while (next != null && !next.isLeaf()) {
                        next = t.next();
                    }
                    return next;
                }
                return null;
            }
        };
    }

    public List<DebugFrameSizeChange> getFrameSizeInfos() {
        return frameSizeInfos;
    }

    public int getFrameSize() {
        return frameSize;
    }
}
