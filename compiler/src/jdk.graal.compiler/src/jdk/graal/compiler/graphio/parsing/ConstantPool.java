/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graphio.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dictionary of values found in the stream. Serves also as a factory to create additional instances
 * of ConstantPool - the feature is used by subclass(es) to supply custom implementations.
 */
public class ConstantPool {
    private List<Object> data = new ArrayList<>();
    private int entriesAdded;
    public static AtomicInteger totalEntries = new AtomicInteger();

    public ConstantPool() {
    }

    /**
     * Adds an entry to the pool, potentially replacing and existing value. If index is larger than
     * constant pool size, the pool grows to accommodate the index.
     *
     * @param index entry index
     * @param obj the value
     * @param where stream position which introduced the value. For diagnostics.
     * @return value introduced to the pool.
     */
    public synchronized Object addPoolEntry(int index, Object obj, long where) {
        while (data.size() <= index) {
            data.add(null);
        }
        entriesAdded++;
        data.set(index, obj);
        return obj;
    }

    /**
     * Retrieves an entry from the pool. The index must already exist.
     *
     * @param index index to fetch
     * @param where stream position that accesses the pool. Diagnostics.
     * @return value in the pool.
     */
    public Object get(int index, long where) {
        return internalGet(index);
    }

    /**
     * The current pool size. The greatest index used plus 1.
     *
     * @return pool size.
     */
    public int size() {
        return data.size();
    }

    /**
     * Clones the current pool's data.
     *
     * @return copy of storage
     */
    protected final List<Object> snapshot() {
        return new ArrayList<>(data);
    }

    /**
     * Forks the constant pool, swaps data. Replaces this pool's data with the `replacementData`.
     * Original pool contents is returned in a new instance of ConstantPool.
     * <p/>
     * The method effectively forks the constant pool, retains some previous snapshot in <b>this
     * instance</b> (assuming there are already some references to it), and the current state is
     * returned as a new instance of ConstantPool.
     *
     * @param replacementData new data for this constant pool, preferably made by {@link #snapshot}.
     *
     * @return new ConstantPool instance with the current data.
     */
    protected final synchronized ConstantPool swap(List<Object> replacementData) {
        ConstantPool copy = create(this.data);
        this.data = replacementData;
        return copy;
    }

    /**
     * Accessor method for superclass, which just accesses the data.
     */
    protected final Object internalGet(int index) {
        return data.get(index);
    }

    /**
     * Initializes the instance with the passed data.
     *
     * @param data initial data
     */
    public ConstantPool(List<Object> data) {
        this.data = data;
    }

    /**
     * Makes a copy of the pool contents.
     *
     * @return new instance of the pool, with identical data as this instance.
     */
    public synchronized ConstantPool copy() {
        return create(new ArrayList<>(data));
    }

    /**
     * Creates a new instance of ConstantPool with the passed data. This method should be used in
     * favour of new operator, to allow custom subclasses to provide their own implementation for
     * the new ConstantPool.
     *
     * @param initialData the initial data
     * @return new instance of ConstantPool.
     */
    protected ConstantPool create(List<Object> initialData) {
        return new ConstantPool(initialData);
    }

    /**
     * Reinitializes the constant pool. May return a different fresh instance.
     *
     * @return the reinitialized instance.
     */
    public ConstantPool restart() {
        return create(new ArrayList<>());
    }

    public int getEntriesAdded() {
        return entriesAdded;
    }

    /**
     * Returns copy of the pool's entries.
     *
     * @return pool entries
     */
    public synchronized List<Object> copyData() {
        return new ArrayList<>(data);
    }
}
