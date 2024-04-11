/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data.serialization.lazy;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import jdk.graal.compiler.graphio.parsing.ConstantPool;

/**
 * Stream constant pool. Performs a snapshot before data is overwritten in the pool. Later the pool
 * can be forked, if necessary, leaving original undamaged data in the original instance, and
 * produces a new instance with latest data/content.
 */
class StreamPool extends ConstantPool {
    private List<Object> originalData;
    protected final BitSet itemRead = new BitSet();
    // for testing
    protected final int generation;
    private int entriesAdded;

    /**
     * The pool is immutable.
     */
    private boolean frozen;

    public StreamPool() {
        this.generation = 0;
    }

    public StreamPool(int generation, List<Object> data) {
        super(data);
        this.generation = generation;
    }

    @Override
    protected StreamPool create(List<Object> data) {
        return new StreamPool(generation + 1, data);
    }

    @Override
    public Object get(int index, long where) {
        itemRead.set(index);
        Object res = super.get(index, where);
        if (res == null) {
            throw new IllegalStateException("Pool inconsistency");
        }
        return res;
    }

    @Override
    public synchronized Object addPoolEntry(int index, Object obj, long where) {
        if (frozen) {
            throw new IllegalStateException("Pool copy is frozen");
        }
        if (size() > index) {
            if (itemRead.get(index)) {
                if (originalData == null) {
                    originalData = snapshot();
                }
                itemRead.clear();
            }
        }
        return super.addPoolEntry(index, obj, where);
    }

    @Override
    public synchronized ConstantPool copy() {
        if (frozen || originalData == null) {
            return super.copy();
        } else {
            return create(new ArrayList<>(originalData));
        }
    }

    public synchronized StreamPool forkIfNeeded() {
        totalEntries.addAndGet(entriesAdded);
        entriesAdded = 0;
        if (originalData != null) {
            ConstantPool r = swap(originalData);
            originalData = null;
            this.frozen = true;
            itemRead.clear();
            return (StreamPool) r;
        } else {
            return this;
        }
    }

    public boolean modified() {
        return originalData != null;
    }

}
